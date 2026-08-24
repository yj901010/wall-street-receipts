package com.wallstreetreceipts.api.domain.outcome.observation;

import java.time.Instant;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** Source and point-in-time identity of the supplied calendar revision. */
public record CatalogPointInTimeEvidence(
        String calendarId,
        String catalogRevision,
        String sourceId,
        String sourceRevision,
        Instant availableAt,
        Instant capturedAt,
        String provenanceId) {

    public CatalogPointInTimeEvidence {
        requireCanonicalText(calendarId, "calendarId");
        requireCanonicalText(catalogRevision, "catalogRevision");
        requireCanonicalText(sourceId, "sourceId");
        requireCanonicalText(sourceRevision, "sourceRevision");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireCanonicalText(provenanceId, "provenanceId");
        if (capturedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException("capturedAt must not precede availableAt");
        }
    }

    private static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
    }
}
