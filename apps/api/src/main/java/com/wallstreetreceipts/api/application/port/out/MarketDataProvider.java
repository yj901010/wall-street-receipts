package com.wallstreetreceipts.api.application.port.out;

import java.util.Optional;

import com.wallstreetreceipts.api.domain.market.MarketQuote;

public interface MarketDataProvider {

    Optional<MarketQuote> latestQuote(String assetId);

    String providerName();
}
