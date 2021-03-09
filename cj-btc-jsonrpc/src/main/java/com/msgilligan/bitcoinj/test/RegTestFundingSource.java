package com.msgilligan.bitcoinj.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.msgilligan.bitcoinj.json.pojo.NetworkInfo;
import com.msgilligan.bitcoinj.rpc.BitcoinExtendedClient;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.ScriptException;
import org.consensusj.jsonrpc.JsonRpcException;
import com.msgilligan.bitcoinj.json.pojo.Outpoint;
import com.msgilligan.bitcoinj.json.pojo.SignedRawTransaction;
import com.msgilligan.bitcoinj.json.pojo.UnspentOutput;
import com.msgilligan.bitcoinj.json.conversion.BitcoinMath;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FundingSource using RegTest mining, BitcoinExtendedClient (with getRegTestMiningAddress),
 * and the server's default wallet for accumulating coins.
 */
public class RegTestFundingSource implements FundingSource {
    final Integer defaultMaxConf = 9999999;
    private static final Logger log = LoggerFactory.getLogger(RegTestFundingSource.class);
    protected BitcoinExtendedClient client;
    
    /**
     * Prior to Bitcoin Core 0.19, the second parameter of sendRawTransaction
     * was a boolean, not an integer containing maxFees
     */
    private boolean serverHasSendRawWithMaxFees = true;

    public RegTestFundingSource(BitcoinExtendedClient client) {
        this.client = client;
    }

    /**
     * Check for and internally handle Bitcoin Core pre 0.19
     * (Note that Omni Core 0.8.x is based on Bitcoin Core 0.18)
     * @return `true` (if legacy), `false` (if modern), `null` (if error)
     * @deprecated This method will be removed in a future release and Bitcoin Core 0.19+ will be required
     */
    @Deprecated
    public Boolean checkForLegacyBitcoinCore() {
        Boolean isLegacy = null;
        try {
            NetworkInfo networkInfo = client.getNetworkInfo();
            isLegacy = networkInfo.getVersion() < 190000;
        } catch (IOException e) {
            log.error("Exception: ", e);
        }
        if (isLegacy != null && isLegacy) {
            serverHasSendRawWithMaxFees = false;
        }
        return isLegacy;
    }

    /**
     * Generate blocks and fund an address with requested amount of BTC
     *
     * TODO: Improve performance. Can we mine multiple blocks with a single RPC?
     * TODO: Use client.generateToAddress() directly rather than through client.generateBlocks()
     * If we use `toAddress` as the destination of generateToAddress(), we
     * can skip the generation and sending of the the raw transaction below.
     *
     * @param toAddress Address to fund with BTC
     * @param requestedBtc Amount of BTC to "mine" and send (minimum ending balance of toAddress?)
     * @return The hash of transaction that provided the funds.
     */
    @Override
    public Sha256Hash requestBitcoin(Address toAddress, Coin requestedBtc) throws JsonRpcException, IOException {
        log.warn("requestBitcoin requesting {}", requestedBtc);
        NetworkParameters netParams = client.getNetParams(); // Should always be RegTest, but lets be flexible
        if (requestedBtc.value > NetworkParameters.MAX_MONEY.value) {
            throw new IllegalArgumentException("request exceeds MAX_MONEY");
        }

        // Newly mined coins need to mature to be spendable
        final int minCoinAge = netParams.getSpendableCoinbaseDepth(); // 100
        if (client.getBlockCount() < minCoinAge) {
            client.generateBlocks(minCoinAge - client.getBlockCount());
        }

        // Collect CoinBase outputs until we have have gathered enough satoshis
        // TODO: We may need to return ourselves change and/or keep more of a wallet
        // to make this more efficient given that RegTest mining reward halves so quickly
        long amountGatheredSoFar = 0;
        ArrayList<Outpoint> inputs = new ArrayList<>();

        while (amountGatheredSoFar < requestedBtc.value) {
            client.generateBlocks(1);

            // TODO: We may be skipping some coinbaseTxs here from blocks that were generated directly
            // as part of tests. We either need to save our place in the chain or keep a mining wallet
            int blockIndex = client.getBlockCount() - minCoinAge;

            log.info("Gathering funds from block {}", blockIndex);
            Block block = client.getBlock(blockIndex);
            Sha256Hash coinbaseTx = block.getTransactions().get(0).getTxId();

            Transaction tx = client.getRawTransaction(coinbaseTx);
            TransactionOutput txOut = tx.getOutput(0);

            Address outAddress;
            try {
                outAddress = txOut.getScriptPubKey().getToAddress(netParams);
            } catch (ScriptException se) {
                log.warn("Can't get address for txOut: {}", txOut);
                outAddress = null;
            }

            // txout is empty, if output was already spent
            if (txOut.getValue().value > 0 && outAddress != null && outAddress.equals(client.getRegTestMiningAddress())) {
                log.warn("txout = {}, value = {}", txOut, txOut.getValue().value);

                amountGatheredSoFar += txOut.getValue().value;
                inputs.add(new Outpoint(coinbaseTx, 0));
            }
            log.warn("amountGatheredSoFar = {} ({} inputs)", BitcoinMath.satoshiToBtc(amountGatheredSoFar).toPlainString(), inputs.size());
        }

        // Don't care about change, we mine it anyway (but this is wasteful given regtest halving rate)
        String unsignedTxHex = client.createRawTransaction(inputs, Collections.singletonMap(toAddress, requestedBtc));
        SignedRawTransaction signingResult = client.signRawTransactionWithWallet(unsignedTxHex);

        log.info("SigningResult: {}", signingResult);
        assert signingResult.isComplete();

        String signedTxHex = signingResult.getHex();
        Sha256Hash txid = sendRawTransactionUnlimitedFees(signedTxHex);

        return txid;
    }

    /**
     * Create an address and fund it with bitcoin
     *
     * @param amount
     * @return Newly created address with the requested amount of bitcoin
     */
    @Override
    public Address createFundedAddress(Coin amount) throws Exception {
        Address address = client.getNewAddress();
        requestBitcoin(address, amount);
        return address;
    }

    /**
     * Create everything needed to assemble a custom transaction
     * @param amount Amount of BTC to be available on new address
     * @return An address, private key, and list of unspent outputs
     * @throws JsonRpcException
     * @throws IOException
     */
    public TransactionIngredients createIngredients(Coin amount) throws JsonRpcException, IOException {
        TransactionIngredients ingredients = new TransactionIngredients();
        Address address = client.getNewAddress();
        requestBitcoin(address, amount);
        ingredients.address = address;
        ingredients.privateKey = client.dumpPrivKey(address);
        ingredients.outPoints = client.listUnspentOutPoints(address);
        return ingredients;
    }

    @Override
    public void fundingSourceMaintenance() {
        try {
            consolidateCoins();
        } catch (JsonRpcException e) {
            log.error("exception: ", e);
            throw new RuntimeException(e);
        } catch (IOException e) {
            log.error("exception: ", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Collects *all* unspent outputs and spends the whole amount minus `stdRelayTxFee`, which is sent
     * to a new address, as fee, to sweep dust and to minimize the number of unspent outputs, to avoid creating too
     * large transactions. No new block is generated afterwards.
     *
     * Can be used in cleanupSpec() methods of integration tests.
     *
     * @see <a href="https://github.com/OmniLayer/OmniJ/issues/50">Issue #50 on GitHub</a>
     */
    void consolidateCoins() throws JsonRpcException, IOException {
        // Get all UTXOs in the servers wallet
        List<UnspentOutput> unspentOutputs = client.listUnspent(1, defaultMaxConf);

        // Check if the amount is large enough to be worth consolidating
        Coin amountIn = sumUnspentOutputs(unspentOutputs);
        if (amountIn.value < client.stdRelayTxFee.value) {
            log.debug("Amount not enough to consolidate");
            return;
        }

        // Gather inputs
        List<Outpoint> inputs = unspentOutputsToOutpoints(unspentOutputs);

        // No receiver, just spend most of it as fee (!)
        Map<Address,Coin> outputs = Collections.singletonMap(client.getNewAddress(), client.stdRelayTxFee);

        String unsignedTxHex = client.createRawTransaction(inputs, outputs);
        SignedRawTransaction signingResult = client.signRawTransactionWithWallet(unsignedTxHex);

        boolean complete = signingResult.isComplete();
        if (!complete) {
            log.error("Unable to complete signing on consolidate coins transaction.");
            JsonNode signingResultJson = client.getMapper().valueToTree(signingResult);
            log.error("SigningResult: {}", signingResultJson.toPrettyString());
        }
        assert complete;

        String signedTxHex = signingResult.getHex();
        Sha256Hash txid = sendRawTransactionUnlimitedFees(signedTxHex);
        log.info("Consolidating transaction sent, txid = {}", txid);
    }

    private List<Outpoint> unspentOutputsToOutpoints(List<UnspentOutput> unspentOutputs) {
        return unspentOutputs.stream()
                .map(output -> new Outpoint(output.getTxid(), output.getVout()))   // map from UnspentOutput to Outpoint
                .collect(Collectors.toList());
    }

    private Coin sumUnspentOutputs(List<UnspentOutput> unspentOutputs) {
        return Coin.valueOf(unspentOutputs.stream().mapToLong(output -> output.getAmount().value).sum());
    }

    private Sha256Hash sendRawTransactionUnlimitedFees(String hexTx) throws IOException {
        Sha256Hash txid;
        if (serverHasSendRawWithMaxFees) {
            txid = client.sendRawTransaction(hexTx, Coin.ZERO);
        } else {
            txid = client.sendRawTransaction(hexTx, true);
        }
        return txid;
    }
}
