package org.consensusj.exchange

import spock.lang.Ignore

@Ignore("this is really an integration test")
class CoinbaseProXChangeRateProviderSpec extends AbstractXChangeRateProviderSpec {
    @Override
    BaseXChangeExchangeRateProvider createProvider() {
        return new BaseXChangeExchangeRateProvider("org.knowm.xchange.coinbasepro.CoinbaseProExchange", null, "BTC/USD")
    }
}