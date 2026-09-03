package com.wallstreetreceipts.api.domain.outcome.horizon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AfterCatalog;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AtClose;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AtOpen;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.AtTouchingBoundary;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.BeforeCatalog;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.BetweenSessions;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.EmptyCatalog;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.InsideSession;
import com.wallstreetreceipts.api.domain.outcome.horizon.EventSessionRelation.RelationContext;

class EventSessionRelationClassifierGoldenTest {

    private static final String CALENDAR_ID = "demo-explicit-session-catalog";
    private static final String CATALOG_REVISION = "schedule-revision-2026-03";
    private static final TradingSession FRIDAY = session(
            "session-2026-03-06",
            "2026-03-06T14:30:00Z",
            "2026-03-06T21:00:00Z");
    private static final TradingSession EXPLICIT_SATURDAY = session(
            "session-2026-03-07",
            "2026-03-07T15:00:00Z",
            "2026-03-07T16:00:00Z");
    private static final TradingSession DST_MONDAY = session(
            "session-2026-03-09",
            "2026-03-09T13:30:00Z",
            "2026-03-09T20:00:00Z");
    private static final TradingSession EARLY_CLOSE_TUESDAY = session(
            "session-2026-03-10",
            "2026-03-10T13:30:00Z",
            "2026-03-10T17:00:00Z");
    private static final TradingSession TOUCHING_FIRST = session(
            "session-touching-001",
            "2026-03-11T13:00:00Z",
            "2026-03-11T14:00:00Z");
    private static final TradingSession TOUCHING_SECOND = session(
            "session-touching-002",
            "2026-03-11T14:00:00Z",
            "2026-03-11T15:00:00Z");

    @ParameterizedTest(name = "{0}")
    @MethodSource("boundaryGoldenVectors")
    void classifiesEveryExplicitBoundaryWithoutSelectingAnAnchor(
            String scenario,
            EventSessionRelationRequest request,
            EventSessionRelation expected) {
        EventSessionRelation result = EventSessionRelationClassifier.classify(request);

        assertThat(result).as(scenario).isEqualTo(expected);
    }

    private static Stream<Arguments> boundaryGoldenVectors() {
        TradingSessionCatalog empty = catalog();
        TradingSessionCatalog single = catalog(FRIDAY);
        TradingSessionCatalog fridayMonday = catalog(FRIDAY, DST_MONDAY);
        TradingSessionCatalog withSaturday = catalog(FRIDAY, EXPLICIT_SATURDAY, DST_MONDAY);
        TradingSessionCatalog earlyClose = catalog(DST_MONDAY, EARLY_CLOSE_TUESDAY);
        TradingSessionCatalog touching = catalog(TOUCHING_FIRST, TOUCHING_SECOND);

        EventSessionRelationRequest emptyRequest = request(
                "2026-03-06T14:30:00Z", empty);
        EventSessionRelationRequest beforeRequest = request(
                "2026-03-06T14:29:59.999999Z", single);
        EventSessionRelationRequest openRequest = request(
                "2026-03-06T14:30:00Z", single);
        EventSessionRelationRequest openPlusMicroRequest = request(
                "2026-03-06T14:30:00.000001Z", single);
        EventSessionRelationRequest insideRequest = request(
                "2026-03-06T18:00:00Z", single);
        EventSessionRelationRequest closeMinusMicroRequest = request(
                "2026-03-06T20:59:59.999999Z", single);
        EventSessionRelationRequest closeRequest = request(
                "2026-03-06T21:00:00Z", single);
        EventSessionRelationRequest closePlusMicroGapRequest = request(
                "2026-03-06T21:00:00.000001Z", fridayMonday);
        EventSessionRelationRequest nextOpenRequest = request(
                "2026-03-09T13:30:00Z", fridayMonday);
        EventSessionRelationRequest afterRequest = request(
                "2026-03-06T21:00:00.000001Z", single);
        EventSessionRelationRequest touchingRequest = request(
                "2026-03-11T14:00:00Z", touching);
        EventSessionRelationRequest touchingMinusMicroRequest = request(
                "2026-03-11T13:59:59.999999Z", touching);
        EventSessionRelationRequest touchingPlusMicroRequest = request(
                "2026-03-11T14:00:00.000001Z", touching);
        EventSessionRelationRequest fridayMondayGapRequest = request(
                "2026-03-08T12:00:00Z", fridayMonday);
        EventSessionRelationRequest saturdayRequest = request(
                "2026-03-07T15:30:00Z", withSaturday);
        EventSessionRelationRequest earlyCloseRequest = request(
                "2026-03-10T17:00:00Z", earlyClose);

        return Stream.of(
                vector("empty catalog", emptyRequest,
                        new EmptyCatalog(context(emptyRequest))),
                vector("one microsecond before first open", beforeRequest,
                        new BeforeCatalog(context(beforeRequest), FRIDAY)),
                vector("exact session open", openRequest,
                        new AtOpen(context(openRequest), FRIDAY)),
                vector("one microsecond after open is inside", openPlusMicroRequest,
                        new InsideSession(context(openPlusMicroRequest), FRIDAY)),
                vector("ordinary session interior", insideRequest,
                        new InsideSession(context(insideRequest), FRIDAY)),
                vector("one microsecond before close is inside", closeMinusMicroRequest,
                        new InsideSession(context(closeMinusMicroRequest), FRIDAY)),
                vector("exact session close", closeRequest,
                        new AtClose(context(closeRequest), FRIDAY)),
                vector("one microsecond after close is a supplied gap", closePlusMicroGapRequest,
                        new BetweenSessions(context(closePlusMicroGapRequest), FRIDAY, DST_MONDAY)),
                vector("exact next open belongs to its open boundary", nextOpenRequest,
                        new AtOpen(context(nextOpenRequest), DST_MONDAY)),
                vector("one microsecond after the catalog end", afterRequest,
                        new AfterCatalog(context(afterRequest), FRIDAY)),
                vector("shared close and open retain both sides", touchingRequest,
                        new AtTouchingBoundary(
                                context(touchingRequest), TOUCHING_FIRST, TOUCHING_SECOND)),
                vector("one microsecond before shared boundary remains inside closing session",
                        touchingMinusMicroRequest,
                        new InsideSession(context(touchingMinusMicroRequest), TOUCHING_FIRST)),
                vector("one microsecond after shared boundary is inside opening session",
                        touchingPlusMicroRequest,
                        new InsideSession(context(touchingPlusMicroRequest), TOUCHING_SECOND)),
                vector("Friday to Monday remains one explicit gap", fridayMondayGapRequest,
                        new BetweenSessions(
                                context(fridayMondayGapRequest), FRIDAY, DST_MONDAY)),
                vector("an explicitly supplied Saturday is a session", saturdayRequest,
                        new InsideSession(context(saturdayRequest), EXPLICIT_SATURDAY)),
                vector("an explicit early close remains an exact close", earlyCloseRequest,
                        new AtClose(context(earlyCloseRequest), EARLY_CLOSE_TUESDAY)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestVectors")
    void rejectsNullOrNonPersistentClassificationInputs(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidRequestVectors() {
        TradingSessionCatalog catalog = catalog(FRIDAY);
        return Stream.of(
                invalid("null policy", () -> new EventSessionRelationRequest(
                        null, FRIDAY.opensAt(), catalog), "policyVersion"),
                invalid("null event time", () -> new EventSessionRelationRequest(
                        policy(), null, catalog), "eventTime"),
                invalid("sub-microsecond event time", () -> new EventSessionRelationRequest(
                        policy(), Instant.parse("2026-03-06T14:30:00.000000001Z"), catalog),
                        "eventTime must not exceed microsecond precision"),
                invalid("null catalog", () -> new EventSessionRelationRequest(
                        policy(), FRIDAY.opensAt(), null), "catalog"),
                invalid("null classifier request", () ->
                        EventSessionRelationClassifier.classify(null), "request"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidContextVectors")
    void rejectsMalformedPublicRelationContexts(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidContextVectors() {
        Instant eventTime = FRIDAY.opensAt();
        return Stream.of(
                invalid("null context policy", () -> new RelationContext(
                        null, CALENDAR_ID, CATALOG_REVISION, eventTime), "policyVersion"),
                invalid("blank calendar ID", () -> new RelationContext(
                        policy(), " ", CATALOG_REVISION, eventTime), "calendarId"),
                invalid("untrimmed calendar ID", () -> new RelationContext(
                        policy(), CALENDAR_ID + " ", CATALOG_REVISION, eventTime), "calendarId"),
                invalid("blank catalog revision", () -> new RelationContext(
                        policy(), CALENDAR_ID, " ", eventTime), "catalogRevision"),
                invalid("null context event time", () -> new RelationContext(
                        policy(), CALENDAR_ID, CATALOG_REVISION, null), "eventTime"),
                invalid("sub-microsecond context event time", () -> new RelationContext(
                        policy(), CALENDAR_ID, CATALOG_REVISION,
                        Instant.parse("2026-03-06T14:30:00.000000001Z")),
                        "eventTime must not exceed microsecond precision"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidPublicRelationVectors")
    void publicRelationRecordsRejectContradictoryTemporalClaims(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidPublicRelationVectors() {
        RelationContext atOpen = context(request("2026-03-06T14:30:00Z", catalog(FRIDAY)));
        RelationContext inside = context(request("2026-03-06T18:00:00Z", catalog(FRIDAY)));
        RelationContext atClose = context(request("2026-03-06T21:00:00Z", catalog(FRIDAY)));
        RelationContext gap = context(request(
                "2026-03-08T12:00:00Z", catalog(FRIDAY, DST_MONDAY)));
        RelationContext touching = context(request(
                "2026-03-11T14:00:00Z", catalog(TOUCHING_FIRST, TOUCHING_SECOND)));
        return Stream.of(
                invalid("empty relation requires context", () -> new EmptyCatalog(null), "context"),
                invalid("before relation rejects equality", () ->
                        new BeforeCatalog(atOpen, FRIDAY), "before-catalog"),
                invalid("open relation rejects an interior instant", () ->
                        new AtOpen(inside, FRIDAY), "at-open"),
                invalid("inside relation rejects open equality", () ->
                        new InsideSession(atOpen, FRIDAY), "inside-session"),
                invalid("inside relation rejects close equality", () ->
                        new InsideSession(atClose, FRIDAY), "inside-session"),
                invalid("close relation rejects an interior instant", () ->
                        new AtClose(inside, FRIDAY), "at-close"),
                invalid("touching relation requires both exact boundaries", () ->
                        new AtTouchingBoundary(touching, FRIDAY, TOUCHING_SECOND),
                        "touching-boundary"),
                invalid("touching relation requires distinct IDs", () ->
                        new AtTouchingBoundary(touching, TOUCHING_FIRST, new TradingSession(
                                TOUCHING_FIRST.sessionId(),
                                TOUCHING_SECOND.opensAt(),
                                TOUCHING_SECOND.closesAt())), "distinct sessionId"),
                invalid("between relation rejects previous close equality", () ->
                        new BetweenSessions(atClose, FRIDAY, DST_MONDAY), "between-sessions"),
                invalid("between relation rejects next open equality", () ->
                        new BetweenSessions(context(request(
                                "2026-03-09T13:30:00Z", catalog(FRIDAY, DST_MONDAY))),
                                FRIDAY, DST_MONDAY), "between-sessions"),
                invalid("between relation requires distinct IDs", () ->
                        new BetweenSessions(gap, FRIDAY, new TradingSession(
                                FRIDAY.sessionId(),
                                DST_MONDAY.opensAt(),
                                DST_MONDAY.closesAt())), "distinct sessionId"),
                invalid("after relation rejects close equality", () ->
                        new AfterCatalog(atClose, FRIDAY), "after-catalog"),
                invalid("session-bearing relation rejects null session", () ->
                        new AtOpen(atOpen, null), "session"));
    }

    @Test
    void touchingClassificationIsImmutableReplayableAndIndependentOfJvmDefaults() {
        List<TradingSession> mutableSource = new ArrayList<>(
                List.of(TOUCHING_FIRST, TOUCHING_SECOND));
        List<TradingSession> before = List.copyOf(mutableSource);
        TradingSessionCatalog catalog = new TradingSessionCatalog(
                CALENDAR_ID, CATALOG_REVISION, mutableSource);
        EventSessionRelationRequest request = request("2026-03-11T14:00:00Z", catalog);
        EventSessionRelation baseline = EventSessionRelationClassifier.classify(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        assertThat(baseline).isEqualTo(new AtTouchingBoundary(
                context(request), TOUCHING_FIRST, TOUCHING_SECOND));
        assertThat(new AtClose(context(request), TOUCHING_FIRST)).isNotEqualTo(baseline);
        assertThat(new AtOpen(context(request), TOUCHING_SECOND)).isNotEqualTo(baseline);

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));
            mutableSource.clear();

            assertThat(EventSessionRelationClassifier.classify(request)).isEqualTo(baseline);
            assertThat(request.catalog().orderedSessions()).containsExactlyElementsOf(before);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }

    @Test
    void policyRequestContextAndSealedVariantsRemainClosed() {
        assertThat(EventSessionRelationPolicyVersion.values()).containsExactly(
                EventSessionRelationPolicyVersion.EXPLICIT_SESSION_BOUNDARY_RELATION_V1);
        assertThat(EventSessionRelation.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(
                        EmptyCatalog.class,
                        BeforeCatalog.class,
                        AtOpen.class,
                        InsideSession.class,
                        AtClose.class,
                        AtTouchingBoundary.class,
                        BetweenSessions.class,
                        AfterCatalog.class);
        assertRecordComponents(EventSessionRelationRequest.class,
                "policyVersion:EventSessionRelationPolicyVersion",
                "eventTime:Instant",
                "catalog:TradingSessionCatalog");
        assertRecordComponents(RelationContext.class,
                "policyVersion:EventSessionRelationPolicyVersion",
                "calendarId:String",
                "catalogRevision:String",
                "eventTime:Instant");
        assertRecordComponents(EmptyCatalog.class, "context:RelationContext");
        assertRecordComponents(BeforeCatalog.class,
                "context:RelationContext", "firstSession:TradingSession");
        assertRecordComponents(AtOpen.class,
                "context:RelationContext", "session:TradingSession");
        assertRecordComponents(InsideSession.class,
                "context:RelationContext", "session:TradingSession");
        assertRecordComponents(AtClose.class,
                "context:RelationContext", "session:TradingSession");
        assertRecordComponents(AtTouchingBoundary.class,
                "context:RelationContext",
                "closingSession:TradingSession",
                "openingSession:TradingSession");
        assertRecordComponents(BetweenSessions.class,
                "context:RelationContext",
                "previousSession:TradingSession",
                "nextSession:TradingSession");
        assertRecordComponents(AfterCatalog.class,
                "context:RelationContext", "lastSession:TradingSession");
    }

    private static void assertRecordComponents(Class<?> recordType, String... expected) {
        assertThat(Arrays.stream(recordType.getRecordComponents()))
                .extracting(component -> component.getName()
                        + ":" + component.getType().getSimpleName())
                .containsExactly(expected);
    }

    private static EventSessionRelationPolicyVersion policy() {
        return EventSessionRelationPolicyVersion.EXPLICIT_SESSION_BOUNDARY_RELATION_V1;
    }

    private static TradingSessionCatalog catalog(TradingSession... sessions) {
        return new TradingSessionCatalog(
                CALENDAR_ID, CATALOG_REVISION, List.of(sessions));
    }

    private static EventSessionRelationRequest request(
            String eventTime,
            TradingSessionCatalog catalog) {
        return new EventSessionRelationRequest(policy(), Instant.parse(eventTime), catalog);
    }

    private static RelationContext context(EventSessionRelationRequest request) {
        return new RelationContext(
                request.policyVersion(),
                request.catalog().calendarId(),
                request.catalog().revision(),
                request.eventTime());
    }

    private static TradingSession session(String sessionId, String opensAt, String closesAt) {
        return new TradingSession(sessionId, Instant.parse(opensAt), Instant.parse(closesAt));
    }

    private static Arguments vector(
            String scenario,
            EventSessionRelationRequest request,
            EventSessionRelation expected) {
        return Arguments.of(scenario, request, expected);
    }

    private static Arguments invalid(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        return Arguments.of(scenario, construction, expectedMessage);
    }
}
