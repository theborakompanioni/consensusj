package org.consensusj.bitcoinj.signing

import org.bitcoinj.core.Address
import org.bitcoinj.core.Coin
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.SegwitAddress
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionInput
import org.bitcoinj.core.TransactionOutput
import org.bitcoinj.params.TestNet3Params
import org.bitcoinj.script.Script
import org.bitcoinj.script.ScriptBuilder
import org.bitcoinj.script.ScriptException
import org.bitcoinj.wallet.DeterministicSeed
import spock.lang.Specification

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Base Specification for testing with a BipStandardDeterministicKeyChain
 */
abstract class DeterministicKeychainBaseSpec extends Specification {
    public static final String mnemonicString = "panda diary marriage suffer basic glare surge auto scissors describe sell unique"
    public static final Instant creationInstant = LocalDate.of(2019, 4, 10).atStartOfDay().toInstant(ZoneOffset.UTC)

    static DeterministicSeed setupTestSeed() {
        return new DeterministicSeed(mnemonicString, null, "", creationInstant.getEpochSecond());
    }

    static SigningRequest createTestSigningRequest(Address toAddress, Address changeAddress) {
        // This is actually the first transaction received by the 0'th change address in our "panda diary" keychain.
        NetworkParameters netParams = TestNet3Params.get()
        Transaction parentTx = firstChangeTransaction()
        TransactionOutput utxo = parentTx.getOutput(1)
        Coin utxoAmount = utxo.value

        Coin txAmount = 0.01.btc
        Coin changeAmount = 0.20990147.btc

        TransactionInputDataImpl input = new TransactionInputDataImpl(netParams.id, parentTx.txId.bytes, utxo.index, utxoAmount.toSat(), utxo.scriptBytes)
        List<TransactionInputDataImpl> inputs = List.of(input)
        List<TransactionOutputData> outputs = List.of(
                new TransactionOutputAddress(txAmount.value, toAddress),
                new TransactionOutputAddress(changeAmount.value, changeAddress)
        )
        return new DefaultSigningRequest(netParams, inputs, outputs)
    }

    /**
     * Verify that a transaction correctly spends the input specified by index. Throws {@link ScriptException}
     * if verification fails.
     *
     * @param tx The transaction to verify
     * @param inputIndex The input to verify
     * @param fromAddr The address we are trying to spend funds from
     * @throws ScriptException If {@code scriptSig#correctlySpends} fails with exception
     */
    static void correctlySpendsInput(Transaction tx, int inputIndex, Address fromAddr) throws ScriptException {
        if (!fromAddr instanceof SegwitAddress) {
            // TODO: Implement for SegWit, too
            Script scriptPubKey = ScriptBuilder.createOutputScript(fromAddr)
            TransactionInput input = tx.getInputs().get(inputIndex)
            input.getScriptSig()
                    .correctlySpends(tx, inputIndex, null, input.value, scriptPubKey, Script.ALL_VERIFY_FLAGS);
        }
    }

    protected static Transaction firstChangeTransaction() {
        final byte[] change_tx_bytes = "0100000001cc652689b217db0cec03cab18a629437a0f1e308db9ee30b934b6989be50641f000000006b4830450221008f9abcda51669dc501a68d2778e2fc33f25d62d74ec791b2776733e14c39aba502201f79445d6eb4364ae83a0579ee0414abe285df034a5e42d0e15938f3f4861c91012102878641346f6ccfa4ed0a50f1786bfbd1891ff200b4c040040a804abc2c5ad69affffffff0240420f00000000001976a9145ab93563a289b74c355a9b9258b86f12bb84affb88acafe34f01000000001976a9149b1077b9d102fcc105e99a906cfd34285928b03e88ac00000000".decodeHex()
        return new Transaction(TestNet3Params.get(), change_tx_bytes)
    }
}
