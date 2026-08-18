package com.wallstreetreceipts.api.infrastructure.provider.fixture;

import java.time.Clock;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.wallstreetreceipts.api.application.port.out.MarketDataProvider;
import com.wallstreetreceipts.api.domain.market.MarketQuote;

@Component
@ConditionalOnProperty(name = "app.providers.market", havingValue = "fixture", matchIfMissing = true)
public final class FixtureMarketDataProvider implements MarketDataProvider {

    private static final String PROVIDER_NAME = "fixture";

    private final Clock clock;
    private final Map<String, FixtureMarketQuoteDto> quotes = Map.of(
            "SPX", new FixtureMarketQuoteDto("SPX", "5278.52", "USD", "2026-08-12T06:00:00Z"),
            "NVDA", new FixtureMarketQuoteDto("NVDA", "183.42", "USD", "2026-08-12T06:00:00Z"));

    public FixtureMarketDataProvider(Clock clock) {
        this.clock = clock;
    }

    @Override
    public Optional<MarketQuote> latestQuote(String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return Optional.empty();
        }

        FixtureMarketQuoteDto providerDto = quotes.get(assetId.trim().toUpperCase(Locale.ROOT));
        return Optional.ofNullable(providerDto)
                .map(source -> FixtureMarketQuoteMapper.toCanonical(source, clock.instant()));
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }
}
