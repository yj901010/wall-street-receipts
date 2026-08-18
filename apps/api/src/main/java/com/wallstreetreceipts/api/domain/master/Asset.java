package com.wallstreetreceipts.api.domain.master;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.market.DataMode;

public record Asset(
        String id,
        AssetType type,
        String canonicalName,
        String ticker,
        Currency primaryCurrency,
        boolean active,
        DataMode dataMode,
        Instant effectiveAt,
        Instant capturedAt,
        String provenanceId) {

    public Asset {
        requireText(id, "id");
        Objects.requireNonNull(type, "type must not be null");
        requireText(canonicalName, "canonicalName");
        Objects.requireNonNull(primaryCurrency, "primaryCurrency must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        PersistentInstant.requireMicrosecondPrecision(effectiveAt, "effectiveAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireText(provenanceId, "provenanceId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
