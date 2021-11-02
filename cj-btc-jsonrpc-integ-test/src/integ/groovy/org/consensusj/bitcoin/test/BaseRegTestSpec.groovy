package org.consensusj.bitcoin.test

import org.consensusj.bitcoin.rpc.BitcoinExtendedClient
import org.bitcoinj.params.RegTestParams
import org.consensusj.jsonrpc.groovy.Loggable
import org.consensusj.bitcoin.rpc.RpcURI
import org.consensusj.bitcoin.test.BTCTestSupport
import org.consensusj.bitcoin.test.RegTestFundingSource
import org.bitcoinj.core.Coin
import spock.lang.Specification
import org.consensusj.bitcoin.rpc.test.TestServers


/**
 * Abstract Base class for Spock tests of Bitcoin Core in RegTest mode
 */
abstract class BaseRegTestSpec extends Specification implements BTCTestSupport, Loggable {
    static final Coin minBTCForTests = 50.btc
    static final private TestServers testServers = TestServers.instance
    static final protected String rpcTestUser = testServers.rpcTestUser
    static final protected String rpcTestPassword = testServers.rpcTestPassword;
    private static BitcoinExtendedClient INSTANCE;

    static BitcoinExtendedClient getClientInstance() {
        // We use a shared client for RegTest integration tests, because we want a single value for regTestMiningAddress
        if (INSTANCE == null) {
            INSTANCE = new BitcoinExtendedClient(RegTestParams.get(), RpcURI.defaultRegTestURI, rpcTestUser, rpcTestPassword)
        }
        return INSTANCE;
    }
    
    // Initializer to set up trait properties, Since Spock doesn't allow constructors
    {
        client = getClientInstance()
        serverReady()
        fundingSource = new RegTestFundingSource(client)
    }

    void setupSpec() {
        serverReady()

        // Make sure we have enough test coins
        // Do we really need to keep doing this now that most tests
        // explicitly fund their addresses?
        while (client.getBalance() < minBTCForTests) {
            // Mine blocks until we have some coins to spend
            client.generateBlocks(1)
        }
    }

    /**
     * Clean up after all tests in spec have run.
     */
    void cleanupSpec() {
        // Spend almost all coins as fee, to sweep dust
        consolidateCoins()
    }

}