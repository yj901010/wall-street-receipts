package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/**
 * Explicit instructions for resolving the next {@code sessionCount} sessions.
 *
 * <p>The anchor is caller-selected. It is not inferred from an analyst-call
 * timestamp and is not part of the returned post-anchor session window.</p>
 */
public record SessionOffsetRequest(
        SessionOffsetPolicyVersion policyVersion,
        String anchorSessionId,
        int sessionCount,
        Instant evaluationAsOf,
        TradingSessionCatalog catalog) {

    public SessionOffsetRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        requireCanonicalText(anchorSessionId, "anchorSessionId");
        if (sessionCount <= 0) {
            throw new IllegalArgumentException("sessionCount must be positive");
        }
        PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        Objects.requireNonNull(catalog, "catalog must not be null");
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
