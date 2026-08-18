package com.wallstreetreceipts.api.domain.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

public record MarketQuote(
        String assetId,
        BigDecimal price,
        Currency currency,
        Instant eventTime,
        Instant processingTime,
        DataMode dataMode) {

    public MarketQuote {
        if (assetId == null || assetId.isBlank()) {
            throw new IllegalArgumentException("assetId must not be blank");
        }
        Objects.requireNonNull(price, "price must not be null");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        Objects.requireNonNull(processingTime, "processingTime must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");

        if (price.signum() <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
    }
}
