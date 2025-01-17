package org.consensusj.bitcoin.rx.jsonrpc.service;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.processors.BehaviorProcessor;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import org.bitcoinj.core.Sha256Hash;
import org.consensusj.bitcoin.json.pojo.ChainTip;
import org.consensusj.bitcoin.json.pojo.TxOutSetInfo;
import org.consensusj.bitcoin.rx.jsonrpc.RxBitcoinClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service to return a stream of TxOutSetInfos for a given blockchain/client.
 * Updates are triggered on new blocks (via {@link org.consensusj.bitcoin.rx.ChainTipService} a cache
 * is used to prevent fetches of TxOutSetInfos already fetched (e.g. on blockchain re-org) and
 * {@code MAX_OUTSTANDING_CALLS} is used to prevent overloading the server (e.g. during blockchain sync)
 */
public class TxOutSetService implements Closeable {
    private static final Logger log = LoggerFactory.getLogger(TxOutSetService.class);
    private final RxBitcoinClient client;
    private final FlowableProcessor<TxOutSetInfo> txOutSetProcessor = BehaviorProcessor.create();
    private final int CACHE_BLOCK_DEPTH = 1;
    private final int MAX_OUTSTANDING_CALLS = 2;
    private final AtomicInteger outstandingCalls = new AtomicInteger(0);
    private final ConcurrentHashMap<Sha256Hash, TxOutSetInfo> txOutSetCache = new ConcurrentHashMap<>();
    private Disposable txOutSetSubscription;
    private CompletableFuture<TxOutSetInfo> lastCall;

    public TxOutSetService(RxBitcoinClient client) {
        this.client = client;
    }

    private synchronized void start() {
        if (txOutSetSubscription == null) {
            txOutSetSubscription = client
                    .chainTipPublisher()
                    .doOnNext(this::onNewBlock)
                    .flatMapSingle(this::fetchSingle)
                    .subscribe(txOutSetProcessor::onNext, txOutSetProcessor::onError, txOutSetProcessor::onComplete);
        }
    }

    public Flowable<TxOutSetInfo> getTxOutSetPublisher() {
        start();
        return txOutSetProcessor;
    }
    
    private void onNewBlock(ChainTip tip) {
        // Remove all cache entries with txOutSetInfo.height < tip.height - CACHE_BLOCK_DEPTH;
        log.info("TxOutSetCache: checking {} entries for entries more than {} blocks old", txOutSetCache.size(), CACHE_BLOCK_DEPTH);
        txOutSetCache.values().removeIf(txOutSetInfo -> {
            boolean remove = txOutSetInfo.getHeight() < (tip.getHeight() - CACHE_BLOCK_DEPTH);
            if (remove) {
                log.info("Removing TxOutSetInfo For {}/{}", txOutSetInfo.getHeight(), txOutSetInfo.getBestBlock());
            }
            return remove;
        });
    }

    private CompletableFuture<TxOutSetInfo> fetchCache(ChainTip tip) {
        TxOutSetInfo cached = txOutSetCache.get(tip.getHash());
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        } else {
            return fetch(tip);
        }
    }

    private CompletableFuture<TxOutSetInfo> fetch(ChainTip tip) {
        if (outstandingCalls.intValue() >= MAX_OUTSTANDING_CALLS) {
            // TODO: Use a synthesized totalCoins (e.g. lastValue + blockreward * delta_blocks)
            log.info("Re-using last call because {} outstanding calls. ", MAX_OUTSTANDING_CALLS);
        } else {
            int calls = outstandingCalls.incrementAndGet();
            log.info("Fetching TxOutSetInfo For {}/{} ({} outstanding calls)", tip.getHeight(), tip.getHash(), calls);
            lastCall = client.supplyAsync(client::getTxOutSetInfo)
                    .whenComplete(this::fetchCompletion);
        }
        return lastCall;
    }

    private void fetchCompletion(TxOutSetInfo info, Throwable t) {
        int calls = outstandingCalls.decrementAndGet();
        if (info != null) {
            log.info("Completion result for: {}/{} ({} outstanding calls)", info.getHeight(), info.getBestBlock(), calls);
            txOutSetCache.put(info.getBestBlock(), info);
        } else {
            log.info("Completion throwable: {} ({} outstanding calls)", t, calls);
        }
    }
    
    private Single<TxOutSetInfo> fetchSingle(ChainTip tip) {
        return Single.defer(() -> Single.fromCompletionStage(fetchCache(tip)));
    }
    
    @Override
    public void close()  {
        if (txOutSetSubscription != null) {
            txOutSetSubscription.dispose();
        }
    }
}
