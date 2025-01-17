package org.consensusj.bitcoinj.signing;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.script.Script;

/**
 * Raw, immutable data for a transaction output
 */
public interface TransactionOutputData {
    String networkId();
    Coin amount();
    Script script();

    default TransactionOutput toMutableOutput() {
        return new TransactionOutput(NetworkParameters.fromID(networkId()), null, amount(), script().getProgram());
    }
}
