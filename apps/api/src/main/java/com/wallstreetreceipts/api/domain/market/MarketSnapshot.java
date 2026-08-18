package com.wallstreetreceipts.api.domain.market;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public record MarketSnapshot(
        String id,
        String callId,
        String assetId,
        Instant eventTime,
        Instant processingTime,
        BigDecimal assetPrice,
        BigDecimal spx,
        BigDecimal ndx,
        BigDecimal vix,
        BigDecimal treasury2y,
        BigDecimal treasury10y,
        BigDecimal realYield,
        BigDecimal dxy,
        BigDecimal wti,
        BigDecimal gold,
        BigDecimal volatility,
        BigDecimal distanceFrom52WeekHigh,
        BigDecimal distanceFromAth,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public MarketSnapshot {
        requireText(id, "id");
        requireText(callId, "callId");
        requireText(assetId, "assetId");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        Objects.requireNonNull(processingTime, "processingTime must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        requireText(provenanceId, "provenanceId");

        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
        if (assetPrice != null && assetPrice.signum() <= 0) {
            throw new IllegalArgumentException("assetPrice must be positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
