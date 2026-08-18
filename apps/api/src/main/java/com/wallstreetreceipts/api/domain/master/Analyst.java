package com.wallstreetreceipts.api.domain.master;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.market.DataMode;

public record Analyst(
        String id,
        String canonicalName,
        boolean active,
        DataMode dataMode,
        Instant effectiveAt,
        Instant capturedAt,
        String provenanceId) {

    public Analyst {
        requireText(id, "id");
        requireText(canonicalName, "canonicalName");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(effectiveAt, "effectiveAt must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        requireText(provenanceId, "provenanceId");
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
