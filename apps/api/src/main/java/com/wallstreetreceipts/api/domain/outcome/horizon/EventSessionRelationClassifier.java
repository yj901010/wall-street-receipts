package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AfterCatalog;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AtClose;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AtOpen;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AtTouchingBoundary;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.BeforeCatalog;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.BetweenSessions;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.EmptyCatalog;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.InsideSession;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.RelationContext;

/**
 * Classifies an event by comparing its instant with explicit session entries.
 *
 * <p>The classifier preserves source order and performs no local-calendar,
 * timezone, duration, observation, horizon, or anchor selection.</p>
 */
public final class EventSessionRelationClassifier {

    private EventSessionRelationClassifier() {
    }

    public static EventSessionRelation classify(EventSessionRelationRequest request) {
        Objects.requireNonNull(request, "request must not be null");

        TradingSessionCatalog catalog = request.catalog();
        RelationContext context = new RelationContext(
                request.policyVersion(),
                catalog.calendarId(),
                catalog.revision(),
                request.eventTime());
        List<TradingSession> sessions = catalog.orderedSessions();
        if (sessions.isEmpty()) {
            return new EmptyCatalog(context);
        }

        Instant eventTime = request.eventTime();
        for (int index = 0; index < sessions.size(); index++) {
            TradingSession session = sessions.get(index);
            if (eventTime.isBefore(session.opensAt())) {
                if (index == 0) {
                    return new BeforeCatalog(context, session);
                }
                return new BetweenSessions(context, sessions.get(index - 1), session);
            }
            if (eventTime.equals(session.opensAt())) {
                return new AtOpen(context, session);
            }
            if (eventTime.isBefore(session.closesAt())) {
                return new InsideSession(context, session);
            }
            if (eventTime.equals(session.closesAt())) {
                if (index + 1 < sessions.size()) {
                    TradingSession followingSession = sessions.get(index + 1);
                    if (eventTime.equals(followingSession.opensAt())) {
                        return new AtTouchingBoundary(context, session, followingSession);
                    }
                }
                return new AtClose(context, session);
            }
        }

        return new AfterCatalog(context, sessions.getLast());
    }
}
