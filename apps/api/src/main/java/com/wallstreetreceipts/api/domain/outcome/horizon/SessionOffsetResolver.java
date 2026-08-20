package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.Incomplete;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.IncompleteReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.Pending;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.PendingReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.Ready;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.ResolvedSessionWindow;

/**
 * Resolves an explicit caller-selected anchor against an immutable schedule.
 *
 * <p>This calculator counts catalog entries only. It does not select the
 * anchor, infer exchange-calendar behavior, inspect observations or prices, or
 * publish an outcome.</p>
 */
public final class SessionOffsetResolver {

    private SessionOffsetResolver() {
    }

    public static SessionOffsetResolution resolve(SessionOffsetRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        TradingSessionCatalog catalog = request.catalog();
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                catalog.calendarId(),
                catalog.revision(),
                request.anchorSessionId(),
                request.sessionCount(),
                request.evaluationAsOf());

        List<TradingSession> orderedSessions = catalog.orderedSessions();
        int anchorIndex = findAnchorIndex(orderedSessions, request.anchorSessionId());
        if (anchorIndex < 0) {
            return new Incomplete(context, IncompleteReason.ANCHOR_SESSION_MISSING);
        }

        int sessionsAfterAnchor = orderedSessions.size() - anchorIndex - 1;
        if (request.sessionCount() > sessionsAfterAnchor) {
            return new Incomplete(context, IncompleteReason.ENDPOINT_SESSION_MISSING);
        }

        int firstSelectedIndex = Math.addExact(anchorIndex, 1);
        int endExclusive = Math.addExact(firstSelectedIndex, request.sessionCount());
        List<TradingSession> selectedSessions = List.copyOf(
                orderedSessions.subList(firstSelectedIndex, endExclusive));
        TradingSession anchorSession = orderedSessions.get(anchorIndex);
        TradingSession endpointSession = selectedSessions.getLast();
        ResolvedSessionWindow window = new ResolvedSessionWindow(
                context, anchorSession, selectedSessions, endpointSession);

        if (request.evaluationAsOf().isBefore(endpointSession.closesAt())) {
            return new Pending(window, PendingReason.ENDPOINT_NOT_REACHED);
        }
        return new Ready(window);
    }

    private static int findAnchorIndex(List<TradingSession> sessions, String anchorSessionId) {
        for (int index = 0; index < sessions.size(); index++) {
            if (sessions.get(index).sessionId().equals(anchorSessionId)) {
                return index;
            }
        }
        return -1;
    }
}
