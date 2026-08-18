package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.market.MarketQuote;

final class FixtureMarketQuoteMapper {

    private FixtureMarketQuoteMapper() {
    }

    static MarketQuote toCanonical(FixtureMarketQuoteDto source, Instant processingTime) {
        return new MarketQuote(
                source.symbol(),
                new BigDecimal(source.price()),
                Currency.getInstance(source.currency()),
                Instant.parse(source.observedAt()),
                processingTime,
                DataMode.DEMO);
    }
}
