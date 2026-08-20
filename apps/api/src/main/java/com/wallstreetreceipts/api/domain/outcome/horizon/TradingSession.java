package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/**
 * One caller-supplied trading session from an explicit schedule.
 *
 * <p>This type does not infer exchange calendars, holidays, early closes, or
 * daylight-saving transitions.</p>
 */
public record TradingSession(
        String sessionId,
        Instant opensAt,
        Instant closesAt) {

    public TradingSession {
        requireCanonicalText(sessionId, "sessionId");
        PersistentInstant.requireMicrosecondPrecision(opensAt, "opensAt");
        PersistentInstant.requireMicrosecondPrecision(closesAt, "closesAt");
        if (!opensAt.isBefore(closesAt)) {
            throw new IllegalArgumentException("opensAt must be before closesAt");
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
