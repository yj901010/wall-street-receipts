package com.wallstreetreceipts.api.domain.call;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.source.SourceReference;

/**
 * Append-only correction or cancellation event for an original analyst call.
 */
public record AnalystCallRevision(
        String id,
        String schemaVersion,
        String callId,
        String supersedesRevisionId,
        int sequenceNumber,
        String provider,
        String providerEventId,
        AnalystCallRevisionType type,
        Instant eventTime,
        Instant processingTime,
        CorrectedCallTerms correctedTerms,
        String reason,
        SourceReference sourceReference,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

    public AnalystCallRevision {
        requireIdentifier(id, "id");
        if (!"1.0.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 1.0.0");
        }
        requireIdentifier(callId, "callId");
        requireTextWithMax(provider, "provider", 100);
        requireTextWithMax(providerEventId, "providerEventId", 256);
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        Objects.requireNonNull(processingTime, "processingTime must not be null");
        requireTextWithMax(reason, "reason", 2000);
        Objects.requireNonNull(sourceReference, "sourceReference must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        PersistentInstant.requireMicrosecondPrecision(eventTime, "eventTime");
        PersistentInstant.requireMicrosecondPrecision(processingTime, "processingTime");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireIdentifier(provenanceId, "provenanceId");

        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be at least 1");
        }
        if ((sequenceNumber == 1) != (supersedesRevisionId == null)) {
            throw new IllegalArgumentException(
                    "only the first revision may omit supersedesRevisionId");
        }
        if (supersedesRevisionId != null) {
            requireIdentifier(supersedesRevisionId, "supersedesRevisionId");
        }
        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException("capturedAt must not precede processingTime");
        }
        if (type == AnalystCallRevisionType.CORRECTION && correctedTerms == null) {
            throw new IllegalArgumentException("a correction requires complete corrected terms");
        }
        if (type == AnalystCallRevisionType.CANCELLATION && correctedTerms != null) {
            throw new IllegalArgumentException("a cancellation must not carry corrected terms");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireTextWithMax(String value, String field, int maxLength) {
        requireText(value, field);
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must not exceed " + maxLength + " characters");
        }
    }

    private static void requireIdentifier(String value, String field) {
        if (value == null || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " is not a valid opaque identifier");
        }
    }
}
