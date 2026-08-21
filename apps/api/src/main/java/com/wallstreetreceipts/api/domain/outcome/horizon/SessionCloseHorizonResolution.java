package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;

/**
 * Schedule-only named-horizon resolution over an explicit session catalog.
 *
 * <p>A resolved endpoint does not claim that a price, bar, or calculated
 * outcome exists and carries no evaluation-as-of readiness state. Public
 * result constructors attest only local invariants; catalog membership,
 * first-N eligibility, and incomplete-reason correctness are resolver-owned
 * attestations.</p>
 */
public sealed interface SessionCloseHorizonResolution
        permits SessionCloseHorizonResolution.Resolved,
        SessionCloseHorizonResolution.Incomplete {

    /** The supplied explicit schedule cannot resolve the requested window. */
    enum IncompleteReason {
        FIRST_ELIGIBLE_SESSION_MISSING,
        HORIZON_ENDPOINT_SESSION_MISSING
    }

    /** Exact policy, basis, named horizon, and catalog identity for replay. */
    record ResolutionContext(
            SessionCloseHorizonPolicyVersion policyVersion,
            String policyDefinitionHash,
            OutcomeBasis basis,
            OutcomeHorizon horizon,
            int sessionCount,
            String calendarId,
            String catalogRevision) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(basis, "basis must not be null");
            Objects.requireNonNull(horizon, "horizon must not be null");
            requireCanonicalText(calendarId, "calendarId");
            requireCanonicalText(catalogRevision, "catalogRevision");

            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            if (sessionCount != policyVersion.sessionCount(horizon)) {
                throw new IllegalArgumentException(
                        "sessionCount must match the named horizon policy");
            }
        }
    }

    /**
     * The exact N eligible sessions, including D1 and the named endpoint.
     *
     * <p>This public constructor attests only the local shape, ordering, and
     * strict first-close invariants available in its arguments. Only
     * {@link SessionCloseHorizonResolver} attests that the entries belong to
     * the supplied catalog and are its first N eligible sessions.</p>
     */
    record ResolvedSessionWindow(
            ResolutionContext context,
            List<TradingSession> sessions,
            TradingSession endpointSession) {

        public ResolvedSessionWindow {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(sessions, "sessions must not be null");
            for (TradingSession session : sessions) {
                Objects.requireNonNull(session, "sessions must not contain null");
            }
            sessions = List.copyOf(sessions);
            Objects.requireNonNull(endpointSession, "endpointSession must not be null");

            if (sessions.size() != context.sessionCount()) {
                throw new IllegalArgumentException(
                        "sessions must contain exactly context.sessionCount entries");
            }
            if (sessions.isEmpty()) {
                throw new IllegalArgumentException("sessions must not be empty");
            }
            if (!endpointSession.equals(sessions.getLast())) {
                throw new IllegalArgumentException(
                        "endpointSession must equal the final sessions entry");
            }
            if (!sessions.getFirst().closesAt().isAfter(context.basis().eventTime())) {
                throw new IllegalArgumentException(
                        "first session close must be strictly after basis eventTime");
            }

            Set<String> sessionIds = new HashSet<>();
            TradingSession previous = null;
            for (TradingSession session : sessions) {
                if (!sessionIds.add(session.sessionId())) {
                    throw new IllegalArgumentException(
                            "sessions must contain unique sessionId values");
                }
                if (previous != null && session.opensAt().isBefore(previous.closesAt())) {
                    throw new IllegalArgumentException(
                            "sessions must be chronological and nonoverlapping");
                }
                previous = session;
            }
        }
    }

    /** The explicit schedule contains the complete named-horizon window. */
    record Resolved(ResolvedSessionWindow window)
            implements SessionCloseHorizonResolution {

        public Resolved {
            Objects.requireNonNull(window, "window must not be null");
        }
    }

    /** The explicit schedule lacks the first eligible close or named endpoint. */
    record Incomplete(
            ResolutionContext context,
            IncompleteReason reason) implements SessionCloseHorizonResolution {

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
