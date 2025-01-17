package org.consensusj.bitcoin.json.pojo;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Sha256Hash;

/**
 * Result of `gettxoutsetinfo`
 */
public class TxOutSetInfo {
    private final int height;
    private final Sha256Hash bestBlock;
    private final long transactions;
    private final long txOuts;
    private final long bogoSize;
    private final Sha256Hash hashSerialized2;
    private final long diskSize;
    private final Coin totalAmount;


    public TxOutSetInfo(@JsonProperty("height")             int         height,
                        @JsonProperty("bestblock")          Sha256Hash  bestBlock,
                        @JsonProperty("transactions")       long        transactions,
                        @JsonProperty("txouts")             long        txOuts,
                        @JsonProperty("bogosize")           long        bogoSize,
                        @JsonProperty("hash_serialized_2")  Sha256Hash  hashSerialized2,
                        @JsonProperty("disk_size")          long        diskSize,
                        @JsonProperty("total_amount")       Coin        totalAmount) {
        this.height = height;
        this.bestBlock = bestBlock;
        this.transactions = transactions;
        this.txOuts = txOuts;
        this.bogoSize = bogoSize;
        this.hashSerialized2 = hashSerialized2;
        this.diskSize = diskSize;
        this.totalAmount = totalAmount;
    }

    public int getHeight() {
        return height;
    }

    public Sha256Hash getBestBlock() {
        return bestBlock;
    }

    public long getTransactions() {
        return transactions;
    }

    public long getTxOuts() {
        return txOuts;
    }

    public long getBogoSize() {
        return bogoSize;
    }

    public Sha256Hash getHashSerialized2() {
        return hashSerialized2;
    }

    public long getDiskSize() {
        return diskSize;
    }

    public Coin getTotalAmount() {
        return totalAmount;
    }
}
