package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable, revision-labelled schedule supplied by the caller.
 *
 * <p>Sessions must already be in canonical chronological order. The catalog
 * validates that order and rejects overlaps; it never sorts or manufactures a
 * missing session.</p>
 */
public record TradingSessionCatalog(
        String calendarId,
        String revision,
        List<TradingSession> orderedSessions) {

    public TradingSessionCatalog {
        requireCanonicalText(calendarId, "calendarId");
        requireCanonicalText(revision, "revision");
        Objects.requireNonNull(orderedSessions, "orderedSessions must not be null");
        for (TradingSession session : orderedSessions) {
            Objects.requireNonNull(session, "orderedSessions must not contain null");
        }
        orderedSessions = List.copyOf(orderedSessions);

        Set<String> sessionIds = new HashSet<>();
        TradingSession previous = null;
        for (TradingSession session : orderedSessions) {
            if (!sessionIds.add(session.sessionId())) {
                throw new IllegalArgumentException(
                        "orderedSessions must contain unique sessionId values");
            }
            if (previous != null && session.opensAt().isBefore(previous.closesAt())) {
                throw new IllegalArgumentException(
                        "orderedSessions must be chronological and nonoverlapping");
            }
            previous = session;
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
