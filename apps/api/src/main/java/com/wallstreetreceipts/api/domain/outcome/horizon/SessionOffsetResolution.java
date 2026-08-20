package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/**
 * Schedule-only resolution of an explicit anchor plus a session-count offset.
 *
 * <p>These results do not claim that a price observation exists. A ready
 * result means only that the endpoint session has closed by the supplied
 * evaluation instant.</p>
 */
public sealed interface SessionOffsetResolution
        permits SessionOffsetResolution.Ready,
        SessionOffsetResolution.Pending,
        SessionOffsetResolution.Incomplete {

    /** The endpoint session exists but has not closed at evaluation time. */
    enum PendingReason {
        ENDPOINT_NOT_REACHED
    }

    /** The explicit schedule cannot resolve the requested endpoint. */
    enum IncompleteReason {
        ANCHOR_SESSION_MISSING,
        ENDPOINT_SESSION_MISSING
    }

    /** Immutable policy and catalog lineage echoed by every resolution. */
    record ResolutionContext(
            SessionOffsetPolicyVersion policyVersion,
            String calendarId,
            String catalogRevision,
            String anchorSessionId,
            int sessionCount,
            Instant evaluationAsOf) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            requireCanonicalText(calendarId, "calendarId");
            requireCanonicalText(catalogRevision, "catalogRevision");
            requireCanonicalText(anchorSessionId, "anchorSessionId");
            if (sessionCount <= 0) {
                throw new IllegalArgumentException("sessionCount must be positive");
            }
            PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        }
    }

    /**
     * The anchor plus exactly {@code context.sessionCount()} post-anchor
     * sessions. The endpoint is the final entry in {@code sessions}.
     */
    record ResolvedSessionWindow(
            ResolutionContext context,
            TradingSession anchorSession,
            List<TradingSession> sessions,
            TradingSession endpointSession) {

        public ResolvedSessionWindow {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(anchorSession, "anchorSession must not be null");
            Objects.requireNonNull(sessions, "sessions must not be null");
            for (TradingSession session : sessions) {
                Objects.requireNonNull(session, "sessions must not contain null");
            }
            sessions = List.copyOf(sessions);
            Objects.requireNonNull(endpointSession, "endpointSession must not be null");

            if (!anchorSession.sessionId().equals(context.anchorSessionId())) {
                throw new IllegalArgumentException(
                        "anchorSession must match context.anchorSessionId");
            }
            if (sessions.size() != context.sessionCount()) {
                throw new IllegalArgumentException(
                        "sessions must contain exactly context.sessionCount entries");
            }
            if (sessions.isEmpty()) {
                throw new IllegalArgumentException("sessions must not be empty");
            }

            Set<String> sessionIds = new HashSet<>();
            sessionIds.add(anchorSession.sessionId());
            TradingSession previous = anchorSession;
            for (TradingSession session : sessions) {
                if (!sessionIds.add(session.sessionId())) {
                    throw new IllegalArgumentException(
                            "anchorSession and sessions must contain unique sessionId values");
                }
                if (session.opensAt().isBefore(previous.closesAt())) {
                    throw new IllegalArgumentException(
                            "sessions must follow the anchor in chronological nonoverlapping order");
                }
                previous = session;
            }
            if (!endpointSession.equals(sessions.getLast())) {
                throw new IllegalArgumentException(
                        "endpointSession must equal the final sessions entry");
            }
        }
    }

    /** The requested endpoint has closed by the supplied evaluation instant. */
    record Ready(ResolvedSessionWindow window) implements SessionOffsetResolution {
        public Ready {
            Objects.requireNonNull(window, "window must not be null");
            if (window.context().evaluationAsOf().isBefore(window.endpointSession().closesAt())) {
                throw new IllegalArgumentException(
                        "ready window endpoint must be closed by evaluationAsOf");
            }
        }
    }

    /** The endpoint is known but has not closed by the supplied evaluation instant. */
    record Pending(
            ResolvedSessionWindow window,
            PendingReason reason) implements SessionOffsetResolution {

        public Pending {
            Objects.requireNonNull(window, "window must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (!window.context().evaluationAsOf().isBefore(window.endpointSession().closesAt())) {
                throw new IllegalArgumentException(
                        "pending window endpoint must be after evaluationAsOf");
            }
        }
    }

    /** The requested endpoint cannot be identified from the supplied schedule. */
    record Incomplete(
            ResolutionContext context,
            IncompleteReason reason) implements SessionOffsetResolution {

        public Incomplete {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
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
