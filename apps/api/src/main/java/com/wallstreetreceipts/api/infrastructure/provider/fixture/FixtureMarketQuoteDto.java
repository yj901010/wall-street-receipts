package com.wallstreetreceipts.api.infrastructure.provider.fixture;

record FixtureMarketQuoteDto(
        String symbol,
        String price,
        String currency,
        String observedAt) {
}
