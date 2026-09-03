package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/**
 * A structural relation between an event instant and an explicit session list.
 *
 * <p>No variant selects an anchor, infers a calendar entry, or claims that a
 * market observation exists.</p>
 */
public sealed interface EventSessionRelation
        permits EventSessionRelation.EmptyCatalog,
        EventSessionRelation.BeforeCatalog,
        EventSessionRelation.AtOpen,
        EventSessionRelation.InsideSession,
        EventSessionRelation.AtClose,
        EventSessionRelation.AtTouchingBoundary,
        EventSessionRelation.BetweenSessions,
        EventSessionRelation.AfterCatalog {

    /** Immutable policy and catalog lineage echoed by every relation. */
    record RelationContext(
            EventSessionRelationPolicyVersion policyVersion,
            String calendarId,
            String catalogRevision,
            Instant eventTime) {

        public RelationContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            requireCanonicalText(calendarId, "calendarId");
            requireCanonicalText(catalogRevision, "catalogRevision");
            PersistentInstant.requireMicrosecondPrecision(eventTime, "eventTime");
        }
    }

    /** The supplied catalog contains no sessions. */
    record EmptyCatalog(RelationContext context) implements EventSessionRelation {
        public EmptyCatalog {
            Objects.requireNonNull(context, "context must not be null");
        }
    }

    /** The event precedes the first supplied session open. */
    record BeforeCatalog(
            RelationContext context,
            TradingSession firstSession) implements EventSessionRelation {

        public BeforeCatalog {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(firstSession, "firstSession must not be null");
            if (!context.eventTime().isBefore(firstSession.opensAt())) {
                throw new IllegalArgumentException(
                        "before-catalog eventTime must precede firstSession.opensAt");
            }
        }
    }

    /** The event is exactly at a supplied session open. */
    record AtOpen(
            RelationContext context,
            TradingSession session) implements EventSessionRelation {

        public AtOpen {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(session, "session must not be null");
            if (!context.eventTime().equals(session.opensAt())) {
                throw new IllegalArgumentException(
                        "at-open eventTime must equal session.opensAt");
            }
        }
    }

    /** The event is strictly inside a supplied session. */
    record InsideSession(
            RelationContext context,
            TradingSession session) implements EventSessionRelation {

        public InsideSession {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(session, "session must not be null");
            Instant eventTime = context.eventTime();
            if (!eventTime.isAfter(session.opensAt())
                    || !eventTime.isBefore(session.closesAt())) {
                throw new IllegalArgumentException(
                        "inside-session eventTime must be after open and before close");
            }
        }
    }

    /** The event is exactly at a supplied session close. */
    record AtClose(
            RelationContext context,
            TradingSession session) implements EventSessionRelation {

        public AtClose {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(session, "session must not be null");
            if (!context.eventTime().equals(session.closesAt())) {
                throw new IllegalArgumentException(
                        "at-close eventTime must equal session.closesAt");
            }
        }
    }

    /**
     * The event is simultaneously at one session close and the following
     * session open. Both sides are retained; neither is selected as an anchor.
     */
    record AtTouchingBoundary(
            RelationContext context,
            TradingSession closingSession,
            TradingSession openingSession) implements EventSessionRelation {

        public AtTouchingBoundary {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(closingSession, "closingSession must not be null");
            Objects.requireNonNull(openingSession, "openingSession must not be null");
            Instant eventTime = context.eventTime();
            if (!eventTime.equals(closingSession.closesAt())
                    || !eventTime.equals(openingSession.opensAt())) {
                throw new IllegalArgumentException(
                        "touching-boundary eventTime must equal close and following open");
            }
            if (closingSession.sessionId().equals(openingSession.sessionId())) {
                throw new IllegalArgumentException(
                        "touching-boundary sessions must have distinct sessionId values");
            }
        }
    }

    /** The event is strictly between two supplied sessions. */
    record BetweenSessions(
            RelationContext context,
            TradingSession previousSession,
            TradingSession nextSession) implements EventSessionRelation {

        public BetweenSessions {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(previousSession, "previousSession must not be null");
            Objects.requireNonNull(nextSession, "nextSession must not be null");
            Instant eventTime = context.eventTime();
            if (!eventTime.isAfter(previousSession.closesAt())
                    || !eventTime.isBefore(nextSession.opensAt())) {
                throw new IllegalArgumentException(
                        "between-sessions eventTime must be after previous close and before next open");
            }
            if (previousSession.sessionId().equals(nextSession.sessionId())) {
                throw new IllegalArgumentException(
                        "between-sessions entries must have distinct sessionId values");
            }
        }
    }

    /** The event follows the last supplied session close. */
    record AfterCatalog(
            RelationContext context,
            TradingSession lastSession) implements EventSessionRelation {

        public AfterCatalog {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(lastSession, "lastSession must not be null");
            if (!context.eventTime().isAfter(lastSession.closesAt())) {
                throw new IllegalArgumentException(
                        "after-catalog eventTime must follow lastSession.closesAt");
            }
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
