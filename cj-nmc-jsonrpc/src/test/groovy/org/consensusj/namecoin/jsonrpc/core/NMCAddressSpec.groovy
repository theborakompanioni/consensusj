package org.consensusj.namecoin.jsonrpc.core

import org.bitcoinj.core.Address
import org.bitcoinj.core.DumpedPrivateKey
import org.bitcoinj.core.ECKey
import org.bitcoinj.core.LegacyAddress
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script
import spock.lang.Specification

/**
 * Tests of creating Namecoin addresses and conversion between BTC and NMC
 * Note: This should be carefully reviewed before you assume it is correct for use with real addresses/keys!
 * These tests were ported from some earlier work with Litecoin and the prefixes and logic have not been double-checked.
 */
class NMCAddressSpec extends Specification {
    static final mainNetParams = MainNetParams.get()
    static final nmcNetParams = NMCMainNetParams.get()
    static final NotSoPrivatePrivateKey = new BigInteger(1, '180cb41c7c600be951b5d3d0a7334acc7506173875834f7a6c4c786a28fcbb19'.decodeHex());
    static final nmcPrefixRange = 'M'..'N'

    def "Using NMCMainNetParams we can generate an NMC address from an ECKey"() {
        given: "A randomly generated ECKey"
        def key = new ECKey()

        when: "We generate an NMC address from it"
        def nmcAddress = Address.fromKey(nmcNetParams, key, Script.ScriptType.P2PKH)
        def firstChar = nmcAddress.toString().charAt(0)

        then: "It begins with 'M' or 'N'"
        firstChar in nmcPrefixRange
    }

    def "Using NMCMainNetParams we can generate an NMC address with the Address constructor"() {
        given: "A randomly generated ECKey"
        def key = new ECKey()

        when: "We construct an NMC address from it"
        def nmcAddress = LegacyAddress.fromPubKeyHash(nmcNetParams, key.pubKeyHash)
        def firstChar = nmcAddress.toString().charAt(0)

        then: "It begins with an 'M' or 'N'"
        firstChar in nmcPrefixRange
    }

    def "We can create a BTC address from an NMC address"() {
        given: "An NMC address"
        def nmcAddress = LegacyAddress.fromPubKeyHash(nmcNetParams, new ECKey().pubKeyHash)

        when: "We generate a BTC address from it"
        def btcAddress = LegacyAddress.fromPubKeyHash(mainNetParams, nmcAddress.hash)

        then: "It begins with a '1'"
        btcAddress.toString().charAt(0) == '1' as char
    }

    def "Using NMCMainNetParams we can generate an NMC address from a known EC private Key"() {
        given: "A randomly generated ECKey"
        def key = ECKey.fromPrivate(NotSoPrivatePrivateKey)

        when: "We generate an NMC address from it"
        def nmcAddress = Address.fromKey(nmcNetParams, key, Script.ScriptType.P2PKH)
        def firstChar = nmcAddress.toString().charAt(0)

        then: "It begins with an 'N' and looks correct"
        firstChar == 'N' as char
        nmcAddress.toString() == "N5mz2ejuwgiqUjB9Fk4Uh3z6bUNbQ3mYdH"
    }

    def "We can create a BTC address from a known NMC address"() {
        given: "An NMC address"
        def nmcAddress = LegacyAddress.fromPubKeyHash(nmcNetParams, ECKey.fromPrivate(NotSoPrivatePrivateKey).pubKeyHash)

        when: "We generate a BTC address from it"
        def btcAddress = LegacyAddress.fromPubKeyHash(mainNetParams, nmcAddress.hash)

        then: "It begins with a '1'"
        btcAddress.toString().charAt(0) == '1' as char
        btcAddress.toString() == "1ACcq1Ew2JdGxBvdyvjuUXqBsEyYUujcku"
    }

    def "We can create a BTC address from an exported NMC private key"() {

        given: "An exported private key in WIF format"
        // Private Key WIF  -- Namecoin (unlike Litecoin?) uses same format as Bitcoin
        def nmcPrivKeyString = "5JAHPeEsCHHm9xB51LvJW11bbGdu7yVWeaAtLJ8nac3odmqmyTx"

        when:
        def nmcPrivateKey = DumpedPrivateKey.fromBase58(nmcNetParams, nmcPrivKeyString)
        def key = nmcPrivateKey.getKey()
        def nmcAddress = Address.fromKey(nmcNetParams, key, Script.ScriptType.P2PKH)
        def btcAddress = Address.fromKey(mainNetParams, key, Script.ScriptType.P2PKH)
        def nmcExportKey = key.getPrivateKeyEncoded(nmcNetParams)
        def btcExportKey = key.getPrivateKeyEncoded(mainNetParams)

        then:
        nmcAddress.toString() == "NBJC6raC4rfxAA7KTgXwFAgV2PMercURwH"
        nmcExportKey.toString() == nmcPrivKeyString
        btcExportKey.toString() == nmcPrivKeyString
        btcAddress.toString() == "1FipuD5D9UaPdcrpBsDN2eXaJ9xbo8wsMi"
    }

}