package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.Incomplete;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.IncompleteReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolvedSessionWindow;

/** Resolves fixed named horizons directly from explicit session closes. */
public final class SessionCloseHorizonResolver {

    private SessionCloseHorizonResolver() {
    }

    public static SessionCloseHorizonResolution resolve(SessionCloseHorizonRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        int sessionCount = request.policyVersion().sessionCount(request.horizon());
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.basis(),
                request.horizon(),
                sessionCount,
                request.catalog().calendarId(),
                request.catalog().revision());
        List<TradingSession> orderedSessions = request.catalog().orderedSessions();

        int firstEligibleIndex = firstSessionClosingAfter(
                orderedSessions, request.basis().eventTime());
        if (firstEligibleIndex < 0) {
            return new Incomplete(context, IncompleteReason.FIRST_ELIGIBLE_SESSION_MISSING);
        }

        int availableSessions = orderedSessions.size() - firstEligibleIndex;
        if (sessionCount > availableSessions) {
            return new Incomplete(
                    context, IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING);
        }

        int endExclusive = firstEligibleIndex + sessionCount;
        List<TradingSession> selectedSessions = List.copyOf(
                orderedSessions.subList(firstEligibleIndex, endExclusive));
        TradingSession endpointSession = selectedSessions.getLast();
        return new Resolved(new ResolvedSessionWindow(
                context, selectedSessions, endpointSession));
    }

    private static int firstSessionClosingAfter(
            List<TradingSession> sessions,
            Instant basisEventTime) {
        for (int index = 0; index < sessions.size(); index++) {
            if (sessions.get(index).closesAt().isAfter(basisEventTime)) {
                return index;
            }
        }
        return -1;
    }
}
