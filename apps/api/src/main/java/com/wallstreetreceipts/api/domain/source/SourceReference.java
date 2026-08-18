package com.wallstreetreceipts.api.domain.source;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.market.DataMode;

public record SourceReference(
        String id,
        SourceDocument document,
        Integer page,
        Long startMs,
        Long endMs,
        String extractedFragment,
        BigDecimal extractionConfidence,
        boolean verified,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public SourceReference {
        requireText(id, "id");
        Objects.requireNonNull(document, "document must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        requireText(provenanceId, "provenanceId");

        if (page != null && page <= 0) {
            throw new IllegalArgumentException("page must be positive");
        }
        if ((startMs == null) != (endMs == null)) {
            throw new IllegalArgumentException("startMs and endMs must be supplied together");
        }
        if (startMs != null && (startMs < 0 || endMs <= startMs)) {
            throw new IllegalArgumentException("invalid source timecode range");
        }
        if (extractionConfidence != null
                && (extractionConfidence.signum() < 0 || extractionConfidence.compareTo(BigDecimal.ONE) > 0)) {
            throw new IllegalArgumentException("extractionConfidence must be between zero and one");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
