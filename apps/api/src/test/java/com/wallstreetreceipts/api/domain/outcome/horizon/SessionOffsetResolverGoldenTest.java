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

import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.Incomplete;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.IncompleteReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.Pending;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.PendingReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.Ready;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionOffsetResolution.ResolvedSessionWindow;

class SessionOffsetResolverGoldenTest {

    private static final String CALENDAR_ID = "demo-us-equity-sessions";
    private static final String CATALOG_REVISION = "schedule-revision-2026-03";
    private static final TradingSession FRIDAY = session(
            "session-2026-03-06",
            "2026-03-06T14:30:00Z",
            "2026-03-06T21:00:00Z");
    private static final TradingSession DST_MONDAY = session(
            "session-2026-03-09",
            "2026-03-09T13:30:00Z",
            "2026-03-09T20:00:00Z");
    private static final TradingSession EXPLICIT_SATURDAY = session(
            "session-2026-03-07",
            "2026-03-07T15:00:00Z",
            "2026-03-07T16:00:00Z");
    private static final TradingSession EARLY_CLOSE_TUESDAY = session(
            "session-2026-03-10",
            "2026-03-10T13:30:00Z",
            "2026-03-10T17:00:00Z");
    private static final TradingSession THURSDAY_AFTER_GAP = session(
            "session-2026-03-12",
            "2026-03-12T13:30:00Z",
            "2026-03-12T20:00:00Z");
    private static final TradingSession FRIDAY_AFTER_GAP = session(
            "session-2026-03-13",
            "2026-03-13T13:30:00Z",
            "2026-03-13T20:00:00Z");

    @Test
    void resolvesExactlyTheNextNSessionsExcludingTheCallerSelectedAnchor() {
        TradingSessionCatalog catalog = catalog(
                FRIDAY, DST_MONDAY, EARLY_CLOSE_TUESDAY, THURSDAY_AFTER_GAP);
        SessionOffsetRequest request = request(
                FRIDAY.sessionId(), 2, EARLY_CLOSE_TUESDAY.closesAt(), catalog);

        SessionOffsetResolution result = SessionOffsetResolver.resolve(request);

        assertThat(result).isEqualTo(new Ready(new ResolvedSessionWindow(
                context(request),
                FRIDAY,
                List.of(DST_MONDAY, EARLY_CLOSE_TUESDAY),
                EARLY_CLOSE_TUESDAY)));
        Ready ready = (Ready) result;
        assertThat(ready.window().sessions())
                .containsExactly(DST_MONDAY, EARLY_CLOSE_TUESDAY)
                .doesNotContain(FRIDAY);
        assertThat(ready.window().endpointSession()).isEqualTo(EARLY_CLOSE_TUESDAY);
    }

    @Test
    void countsExplicitCatalogEntriesWithoutGuessingWeekendDstHolidayOrEarlyCloseRules() {
        TradingSessionCatalog catalog = catalog(
                FRIDAY, DST_MONDAY, EARLY_CLOSE_TUESDAY, THURSDAY_AFTER_GAP);
        SessionOffsetRequest request = request(
                DST_MONDAY.sessionId(), 2, THURSDAY_AFTER_GAP.closesAt(), catalog);

        Ready result = (Ready) SessionOffsetResolver.resolve(request);

        assertThat(result.window().sessions())
                .containsExactly(EARLY_CLOSE_TUESDAY, THURSDAY_AFTER_GAP);
        assertThat(result.window().endpointSession()).isEqualTo(THURSDAY_AFTER_GAP);
    }

    @Test
    void sessionCountFiveCountsAnExplicitSaturdayButDoesNotInventAnOmittedWeekday() {
        List<TradingSession> source = new ArrayList<>(List.of(
                FRIDAY,
                EXPLICIT_SATURDAY,
                DST_MONDAY,
                EARLY_CLOSE_TUESDAY,
                THURSDAY_AFTER_GAP,
                FRIDAY_AFTER_GAP));
        List<TradingSession> before = List.copyOf(source);
        TradingSessionCatalog catalog = new TradingSessionCatalog(
                CALENDAR_ID, CATALOG_REVISION, source);
        SessionOffsetRequest request = request(
                FRIDAY.sessionId(), 5, FRIDAY_AFTER_GAP.closesAt(), catalog);

        Ready result = (Ready) SessionOffsetResolver.resolve(request);

        assertThat(result.window().sessions()).containsExactly(
                EXPLICIT_SATURDAY,
                DST_MONDAY,
                EARLY_CLOSE_TUESDAY,
                THURSDAY_AFTER_GAP,
                FRIDAY_AFTER_GAP);
        assertThat(result.window().sessions())
                .extracting(TradingSession::sessionId)
                .contains("session-2026-03-07")
                .doesNotContain("session-2026-03-11");
        assertThat(result.window().endpointSession()).isEqualTo(FRIDAY_AFTER_GAP);
        assertThat(source).containsExactlyElementsOf(before);
    }

    @Test
    void resultDoesNotDependOnJvmDefaultLocaleOrTimeZone() {
        TradingSessionCatalog catalog = catalog(
                FRIDAY, DST_MONDAY, EARLY_CLOSE_TUESDAY);
        SessionOffsetRequest request = request(
                FRIDAY.sessionId(), 2, EARLY_CLOSE_TUESDAY.closesAt(), catalog);
        SessionOffsetResolution baseline = SessionOffsetResolver.resolve(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            assertThat(SessionOffsetResolver.resolve(request)).isEqualTo(baseline);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointEvaluationVectors")
    void distinguishesPendingFromReadyAtTheExactEndpointClose(
            String scenario,
            Instant evaluationAsOf,
            Class<? extends SessionOffsetResolution> expectedType) {
        TradingSessionCatalog catalog = catalog(FRIDAY, DST_MONDAY);
        SessionOffsetRequest request = request(
                FRIDAY.sessionId(), 1, evaluationAsOf, catalog);

        SessionOffsetResolution result = SessionOffsetResolver.resolve(request);

        assertThat(result).as(scenario).isInstanceOf(expectedType);
        ResolvedSessionWindow window;
        if (result instanceof Pending pending) {
            assertThat(pending.reason()).isEqualTo(PendingReason.ENDPOINT_NOT_REACHED);
            window = pending.window();
        } else {
            window = ((Ready) result).window();
        }
        assertThat(window.anchorSession()).isEqualTo(FRIDAY);
        assertThat(window.sessions()).containsExactly(DST_MONDAY).doesNotContain(FRIDAY);
        assertThat(window.endpointSession()).isEqualTo(DST_MONDAY);
    }

    private static Stream<Arguments> endpointEvaluationVectors() {
        return Stream.of(
                Arguments.of(
                        "one microsecond before endpoint close remains pending",
                        Instant.parse("2026-03-09T19:59:59.999999Z"),
                        Pending.class),
                Arguments.of(
                        "endpoint close is inclusive and ready",
                        DST_MONDAY.closesAt(),
                        Ready.class),
                Arguments.of(
                        "one microsecond after endpoint close remains ready",
                        Instant.parse("2026-03-09T20:00:00.000001Z"),
                        Ready.class));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("incompleteVectors")
    void reportsWhyTheExplicitScheduleCannotResolveAnEndpoint(
            String scenario,
            SessionOffsetRequest request,
            IncompleteReason expectedReason) {
        SessionOffsetResolution result = SessionOffsetResolver.resolve(request);

        assertThat(result).as(scenario).isEqualTo(new Incomplete(
                context(request), expectedReason));
        assertThat(result).isNotInstanceOf(Pending.class).isNotInstanceOf(Ready.class);
    }

    private static Stream<Arguments> incompleteVectors() {
        TradingSessionCatalog populated = catalog(FRIDAY, DST_MONDAY);
        TradingSessionCatalog empty = catalog();
        return Stream.of(
                Arguments.of(
                        "unknown anchor is incomplete",
                        request("session-not-published", 1, DST_MONDAY.closesAt(), populated),
                        IncompleteReason.ANCHOR_SESSION_MISSING),
                Arguments.of(
                        "empty catalog cannot supply the anchor",
                        request(FRIDAY.sessionId(), 1, DST_MONDAY.closesAt(), empty),
                        IncompleteReason.ANCHOR_SESSION_MISSING),
                Arguments.of(
                        "known last anchor has no endpoint",
                        request(DST_MONDAY.sessionId(), 1, DST_MONDAY.closesAt(), populated),
                        IncompleteReason.ENDPOINT_SESSION_MISSING),
                Arguments.of(
                        "partially available requested range has no endpoint",
                        request(FRIDAY.sessionId(), 2, DST_MONDAY.closesAt(), populated),
                        IncompleteReason.ENDPOINT_SESSION_MISSING));
    }

    @Test
    void rejectsAnUnrepresentableRangeWithoutIntegerOverflow() {
        TradingSessionCatalog catalog = catalog(FRIDAY, DST_MONDAY);
        SessionOffsetRequest request = request(
                FRIDAY.sessionId(), Integer.MAX_VALUE, DST_MONDAY.closesAt(), catalog);

        assertThat(SessionOffsetResolver.resolve(request)).isEqualTo(new Incomplete(
                context(request), IncompleteReason.ENDPOINT_SESSION_MISSING));
    }

    @Test
    void defensivelyCopiesTheCatalogAndResolvedWindowWithoutMutatingInput() {
        List<TradingSession> mutableSource = new ArrayList<>(
                List.of(FRIDAY, DST_MONDAY, EARLY_CLOSE_TUESDAY));
        List<TradingSession> before = List.copyOf(mutableSource);
        TradingSessionCatalog catalog = new TradingSessionCatalog(
                CALENDAR_ID, CATALOG_REVISION, mutableSource);
        SessionOffsetRequest request = request(
                FRIDAY.sessionId(), 2, EARLY_CLOSE_TUESDAY.closesAt(), catalog);

        SessionOffsetResolution first = SessionOffsetResolver.resolve(request);
        SessionOffsetResolution replay = SessionOffsetResolver.resolve(request);
        mutableSource.clear();

        assertThat(first).isEqualTo(replay);
        assertThat(catalog.orderedSessions()).containsExactlyElementsOf(before);
        assertThat(request.catalog()).isSameAs(catalog);
        ResolvedSessionWindow window = ((Ready) first).window();
        assertThatThrownBy(() -> catalog.orderedSessions().add(THURSDAY_AFTER_GAP))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> window.sessions().add(THURSDAY_AFTER_GAP))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidSessionVectors")
    void rejectsInvalidTradingSessions(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidSessionVectors() {
        Instant open = Instant.parse("2026-03-06T14:30:00Z");
        Instant close = Instant.parse("2026-03-06T21:00:00Z");
        return Stream.of(
                invalid("null session ID", () -> new TradingSession(null, open, close), "sessionId"),
                invalid("blank session ID", () -> new TradingSession(" ", open, close), "sessionId"),
                invalid("untrimmed session ID", () -> new TradingSession(" session-1", open, close),
                        "sessionId"),
                invalid("null open", () -> new TradingSession("session-1", null, close), "opensAt"),
                invalid("null close", () -> new TradingSession("session-1", open, null), "closesAt"),
                invalid("sub-microsecond open", () -> new TradingSession(
                        "session-1", Instant.parse("2026-03-06T14:30:00.000000001Z"), close),
                        "opensAt must not exceed microsecond precision"),
                invalid("sub-microsecond close", () -> new TradingSession(
                        "session-1", open, Instant.parse("2026-03-06T21:00:00.000000001Z")),
                        "closesAt must not exceed microsecond precision"),
                invalid("zero duration", () -> new TradingSession("session-1", open, open),
                        "opensAt must be before closesAt"),
                invalid("negative duration", () -> new TradingSession("session-1", close, open),
                        "opensAt must be before closesAt"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCatalogVectors")
    void rejectsMalformedOrAmbiguousExplicitCatalogs(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidCatalogVectors() {
        TradingSession duplicateMondayId = session(
                DST_MONDAY.sessionId(),
                "2026-03-10T13:30:00Z",
                "2026-03-10T20:00:00Z");
        TradingSession overlap = session(
                "session-overlap",
                "2026-03-09T19:00:00Z",
                "2026-03-09T21:00:00Z");
        List<TradingSession> withNull = new ArrayList<>();
        withNull.add(FRIDAY);
        withNull.add(null);
        return Stream.of(
                invalid("null calendar ID", () -> new TradingSessionCatalog(
                        null, CATALOG_REVISION, List.of()), "calendarId"),
                invalid("blank calendar ID", () -> new TradingSessionCatalog(
                        " ", CATALOG_REVISION, List.of()), "calendarId"),
                invalid("untrimmed revision", () -> new TradingSessionCatalog(
                        CALENDAR_ID, CATALOG_REVISION + " ", List.of()), "revision"),
                invalid("null sessions", () -> new TradingSessionCatalog(
                        CALENDAR_ID, CATALOG_REVISION, null), "orderedSessions"),
                invalid("null session entry", () -> new TradingSessionCatalog(
                        CALENDAR_ID, CATALOG_REVISION, withNull), "null"),
                invalid("duplicate session ID", () -> new TradingSessionCatalog(
                        CALENDAR_ID, CATALOG_REVISION, List.of(FRIDAY, DST_MONDAY, duplicateMondayId)),
                        "unique sessionId"),
                invalid("reversed order", () -> new TradingSessionCatalog(
                        CALENDAR_ID, CATALOG_REVISION, List.of(DST_MONDAY, FRIDAY)),
                        "chronological and nonoverlapping"),
                invalid("overlapping sessions", () -> new TradingSessionCatalog(
                        CALENDAR_ID, CATALOG_REVISION, List.of(FRIDAY, DST_MONDAY, overlap)),
                        "chronological and nonoverlapping"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestVectors")
    void rejectsInvalidResolutionInstructions(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidRequestVectors() {
        TradingSessionCatalog catalog = catalog(FRIDAY, DST_MONDAY);
        Instant asOf = DST_MONDAY.closesAt();
        return Stream.of(
                invalid("null policy", () -> new SessionOffsetRequest(
                        null, FRIDAY.sessionId(), 1, asOf, catalog), "policyVersion"),
                invalid("null anchor", () -> new SessionOffsetRequest(
                        policy(), null, 1, asOf, catalog), "anchorSessionId"),
                invalid("blank anchor", () -> new SessionOffsetRequest(
                        policy(), " ", 1, asOf, catalog), "anchorSessionId"),
                invalid("zero count", () -> new SessionOffsetRequest(
                        policy(), FRIDAY.sessionId(), 0, asOf, catalog), "sessionCount"),
                invalid("negative count", () -> new SessionOffsetRequest(
                        policy(), FRIDAY.sessionId(), -1, asOf, catalog), "sessionCount"),
                invalid("null evaluation instant", () -> new SessionOffsetRequest(
                        policy(), FRIDAY.sessionId(), 1, null, catalog), "evaluationAsOf"),
                invalid("sub-microsecond evaluation instant", () -> new SessionOffsetRequest(
                        policy(), FRIDAY.sessionId(), 1,
                        Instant.parse("2026-03-09T20:00:00.000000001Z"), catalog),
                        "evaluationAsOf must not exceed microsecond precision"),
                invalid("null catalog", () -> new SessionOffsetRequest(
                        policy(), FRIDAY.sessionId(), 1, asOf, null), "catalog"),
                invalid("null resolver request", () -> SessionOffsetResolver.resolve(null), "request"));
    }

    @Test
    void publicResultConstructorsEnforceTheirStateAndWindowInvariants() {
        TradingSessionCatalog catalog = catalog(FRIDAY, DST_MONDAY);
        SessionOffsetRequest pendingRequest = request(
                FRIDAY.sessionId(), 1,
                Instant.parse("2026-03-09T19:59:59.999999Z"), catalog);
        ResolvedSessionWindow pendingWindow = new ResolvedSessionWindow(
                context(pendingRequest), FRIDAY, List.of(DST_MONDAY), DST_MONDAY);
        SessionOffsetRequest readyRequest = request(
                FRIDAY.sessionId(), 1, DST_MONDAY.closesAt(), catalog);
        ResolvedSessionWindow readyWindow = new ResolvedSessionWindow(
                context(readyRequest), FRIDAY, List.of(DST_MONDAY), DST_MONDAY);

        assertThatThrownBy(() -> new Ready(pendingWindow))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ready window endpoint");
        assertThatThrownBy(() -> new Pending(readyWindow, PendingReason.ENDPOINT_NOT_REACHED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pending window endpoint");
        assertThatThrownBy(() -> new Pending(pendingWindow, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> new Incomplete(context(readyRequest), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                context(readyRequest), DST_MONDAY, List.of(DST_MONDAY), DST_MONDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anchorSession");
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                context(readyRequest), FRIDAY, List.of(DST_MONDAY), FRIDAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpointSession");
    }

    @Test
    void policyAndRecordSurfacesRemainClosedAndEvidenceOnly() {
        assertThat(SessionOffsetPolicyVersion.values()).containsExactly(
                SessionOffsetPolicyVersion.EXPLICIT_ANCHOR_SESSION_COUNT_V1);
        assertThat(PendingReason.values()).containsExactly(PendingReason.ENDPOINT_NOT_REACHED);
        assertThat(IncompleteReason.values()).containsExactly(
                IncompleteReason.ANCHOR_SESSION_MISSING,
                IncompleteReason.ENDPOINT_SESSION_MISSING);
        assertThat(SessionOffsetResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Ready.class, Pending.class, Incomplete.class);
        assertRecordComponents(TradingSession.class,
                "sessionId:String", "opensAt:Instant", "closesAt:Instant");
        assertRecordComponents(TradingSessionCatalog.class,
                "calendarId:String", "revision:String", "orderedSessions:List");
        assertRecordComponents(SessionOffsetRequest.class,
                "policyVersion:SessionOffsetPolicyVersion",
                "anchorSessionId:String",
                "sessionCount:int",
                "evaluationAsOf:Instant",
                "catalog:TradingSessionCatalog");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:SessionOffsetPolicyVersion",
                "calendarId:String",
                "catalogRevision:String",
                "anchorSessionId:String",
                "sessionCount:int",
                "evaluationAsOf:Instant");
        assertRecordComponents(ResolvedSessionWindow.class,
                "context:ResolutionContext",
                "anchorSession:TradingSession",
                "sessions:List",
                "endpointSession:TradingSession");
        assertRecordComponents(Ready.class, "window:ResolvedSessionWindow");
        assertRecordComponents(Pending.class,
                "window:ResolvedSessionWindow", "reason:PendingReason");
        assertRecordComponents(Incomplete.class,
                "context:ResolutionContext", "reason:IncompleteReason");
    }

    private static void assertRecordComponents(Class<?> recordType, String... expected) {
        assertThat(Arrays.stream(recordType.getRecordComponents()))
                .extracting(component -> component.getName()
                        + ":" + component.getType().getSimpleName())
                .containsExactly(expected);
    }

    private static SessionOffsetPolicyVersion policy() {
        return SessionOffsetPolicyVersion.EXPLICIT_ANCHOR_SESSION_COUNT_V1;
    }

    private static TradingSessionCatalog catalog(TradingSession... sessions) {
        return new TradingSessionCatalog(
                CALENDAR_ID, CATALOG_REVISION, List.of(sessions));
    }

    private static SessionOffsetRequest request(
            String anchorSessionId,
            int sessionCount,
            Instant evaluationAsOf,
            TradingSessionCatalog catalog) {
        return new SessionOffsetRequest(
                policy(), anchorSessionId, sessionCount, evaluationAsOf, catalog);
    }

    private static ResolutionContext context(SessionOffsetRequest request) {
        return new ResolutionContext(
                request.policyVersion(),
                request.catalog().calendarId(),
                request.catalog().revision(),
                request.anchorSessionId(),
                request.sessionCount(),
                request.evaluationAsOf());
    }

    private static TradingSession session(String sessionId, String opensAt, String closesAt) {
        return new TradingSession(sessionId, Instant.parse(opensAt), Instant.parse(closesAt));
    }

    private static Arguments invalid(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        return Arguments.of(scenario, construction, expectedMessage);
    }
}
