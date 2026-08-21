package com.wallstreetreceipts.api.domain.outcome.horizon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Correction;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Original;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.Incomplete;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.IncompleteReason;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.ResolvedSessionWindow;

class SessionCloseHorizonResolverGoldenTest {

    private static final String CALENDAR_ID = "demo-explicit-us-equity-sessions";
    private static final String CATALOG_REVISION = "schedule-revision-2026-08-21";
    private static final Instant FIRST_OPEN = Instant.parse("2026-01-01T00:00:00Z");
    private static final String CANONICAL_DEFINITION =
            "{\"policyVersion\":\"STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1\","
            + "\"lineageMode\":\"ORIGINAL_AND_EACH_VALID_CORRECTION\","
            + "\"originalEventField\":\"call.eventTime\","
            + "\"correctionEventField\":\"correction.eventTime\","
            + "\"cancellationBasisAllowed\":false,"
            + "\"eligibleSessionPredicate\":\"session.closesAt>basis.eventTime\","
            + "\"eligibleSessionOrder\":\"SUPPLIED_CATALOG_ORDER\","
            + "\"windowSelection\":\"FIRST_N_ELIGIBLE\","
            + "\"endpointSelection\":\"NTH_ELIGIBLE\","
            + "\"firstEligibleMissingReason\":\"FIRST_ELIGIBLE_SESSION_MISSING\","
            + "\"horizonEndpointMissingReason\":"
            + "\"HORIZON_ENDPOINT_SESSION_MISSING\","
            + "\"readinessState\":\"ABSENT\","
            + "\"sessionCounts\":{\"D1\":1,\"W1\":5,\"M1\":21,"
            + "\"M3\":63,\"M6\":126,\"Y1\":252}}";
    private static final String DEFINITION_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";

    @ParameterizedTest(name = "{0} resolves the first {1} eligible closes")
    @MethodSource("namedHorizonVectors")
    void resolvesEveryNamedHorizonFromTheFirstSessionClosingStrictlyAfterTheBasis(
            OutcomeHorizon horizon,
            int expectedSessionCount) {
        List<TradingSession> sessions = explicitSessions(252);
        Original basis = new Original(
                "call-original-1", FIRST_OPEN.plusSeconds(30 * 60));
        SessionCloseHorizonRequest request = request(basis, horizon, catalog(sessions));

        Resolved result = (Resolved) SessionCloseHorizonResolver.resolve(request);

        assertThat(result.window().sessions())
                .hasSize(expectedSessionCount)
                .startsWith(sessions.getFirst())
                .endsWith(sessions.get(expectedSessionCount - 1));
        assertThat(result.window().endpointSession().sessionId())
                .isEqualTo(sessionId(expectedSessionCount));
        assertThat(result.window().context()).isEqualTo(new ResolutionContext(
                policy(),
                DEFINITION_HASH,
                basis,
                horizon,
                expectedSessionCount,
                CALENDAR_ID,
                CATALOG_REVISION));
    }

    private static Stream<Arguments> namedHorizonVectors() {
        return Stream.of(
                Arguments.of(OutcomeHorizon.D1, 1),
                Arguments.of(OutcomeHorizon.W1, 5),
                Arguments.of(OutcomeHorizon.M1, 21),
                Arguments.of(OutcomeHorizon.M3, 63),
                Arguments.of(OutcomeHorizon.M6, 126),
                Arguments.of(OutcomeHorizon.Y1, 252));
    }

    @ParameterizedTest(name = "{0} with only N-1={1} sessions is incomplete")
    @MethodSource("namedHorizonShortageVectors")
    void reportsTheExactShortageReasonForEveryNamedHorizon(
            OutcomeHorizon horizon,
            int publishedSessionCount,
            IncompleteReason expectedReason) {
        Original basis = new Original(
                "call-short-" + horizon,
                FIRST_OPEN.plusSeconds(30 * 60));
        SessionCloseHorizonRequest request = request(
                basis,
                horizon,
                catalog(explicitSessions(publishedSessionCount)));

        SessionCloseHorizonResolution result = SessionCloseHorizonResolver.resolve(request);

        assertThat(result).isEqualTo(new Incomplete(context(request), expectedReason));
        assertThat(result).isNotInstanceOf(Resolved.class);
    }

    private static Stream<Arguments> namedHorizonShortageVectors() {
        return Stream.of(
                Arguments.of(
                        OutcomeHorizon.D1,
                        0,
                        IncompleteReason.FIRST_ELIGIBLE_SESSION_MISSING),
                Arguments.of(
                        OutcomeHorizon.W1,
                        4,
                        IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING),
                Arguments.of(
                        OutcomeHorizon.M1,
                        20,
                        IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING),
                Arguments.of(
                        OutcomeHorizon.M3,
                        62,
                        IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING),
                Arguments.of(
                        OutcomeHorizon.M6,
                        125,
                        IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING),
                Arguments.of(
                        OutcomeHorizon.Y1,
                        251,
                        IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("strictBoundaryVectors")
    void usesTheDirectStrictlyAfterClosePredicateAtEveryBoundary(
            String scenario,
            Instant basisEventTime,
            String expectedFirstSessionId) {
        TradingSessionCatalog catalog = catalog(explicitSessions(3));
        Original basis = new Original("call-boundary", basisEventTime);

        Resolved result = (Resolved) SessionCloseHorizonResolver.resolve(
                request(basis, OutcomeHorizon.D1, catalog));

        assertThat(result.window().sessions())
                .extracting(TradingSession::sessionId)
                .as(scenario)
                .containsExactly(expectedFirstSessionId);
        assertThat(result.window().endpointSession())
                .isEqualTo(result.window().sessions().getFirst());
    }

    private static Stream<Arguments> strictBoundaryVectors() {
        Instant firstClose = FIRST_OPEN.plusSeconds(60 * 60);
        Instant secondOpen = FIRST_OPEN.plusSeconds(2 * 60 * 60);
        return Stream.of(
                Arguments.of(
                        "before the catalog resolves its first published session",
                        FIRST_OPEN.minusNanos(1_000),
                        sessionId(1)),
                Arguments.of(
                        "the exact session open resolves the current session",
                        FIRST_OPEN,
                        sessionId(1)),
                Arguments.of(
                        "inside a session resolves the current session",
                        FIRST_OPEN.plusSeconds(30 * 60),
                        sessionId(1)),
                Arguments.of(
                        "one microsecond before close resolves the current session",
                        firstClose.minusNanos(1_000),
                        sessionId(1)),
                Arguments.of(
                        "the exact close skips the just-closed session",
                        firstClose,
                        sessionId(2)),
                Arguments.of(
                        "one microsecond after close resolves the next explicit session",
                        firstClose.plusNanos(1_000),
                        sessionId(2)),
                Arguments.of(
                        "an event in a catalog gap resolves the next explicit session",
                        firstClose.plusSeconds(30 * 60),
                        sessionId(2)),
                Arguments.of(
                        "the exact next open resolves that opening session",
                        secondOpen,
                        sessionId(2)));
    }

    @Test
    void touchingCloseAndOpenAndItsFirstInteriorMicrosecondResolveTheOpeningSession() {
        TradingSession closing = new TradingSession(
                "touching-1", FIRST_OPEN, FIRST_OPEN.plusSeconds(60 * 60));
        TradingSession opening = new TradingSession(
                "touching-2",
                closing.closesAt(),
                closing.closesAt().plusSeconds(60 * 60));
        TradingSessionCatalog catalog = catalog(List.of(closing, opening));

        for (Instant basisEventTime : List.of(
                closing.closesAt(),
                closing.closesAt().plusNanos(1_000))) {
            Original basis = new Original("call-touching", basisEventTime);
            Resolved result = (Resolved) SessionCloseHorizonResolver.resolve(
                    request(basis, OutcomeHorizon.D1, catalog));

            assertThat(result.window().sessions()).containsExactly(opening);
            assertThat(result.window().endpointSession()).isEqualTo(opening);
        }
    }

    @Test
    void countsOnlyPublishedSessionsAcrossWeekendLikeGapsSaturdayAndEarlyClose() {
        TradingSession friday = session(
                "session-friday", "2026-03-06T14:30:00Z", "2026-03-06T21:00:00Z");
        TradingSession explicitSaturday = session(
                "session-saturday", "2026-03-07T15:00:00Z", "2026-03-07T16:00:00Z");
        TradingSession mondayAfterGap = session(
                "session-monday", "2026-03-09T13:30:00Z", "2026-03-09T20:00:00Z");
        TradingSession irregularEarlyClose = session(
                "session-early-close", "2026-03-10T13:30:00Z", "2026-03-10T17:00:00Z");
        TradingSession thursdayAfterOmittedDay = session(
                "session-thursday", "2026-03-12T13:30:00Z", "2026-03-12T20:00:00Z");
        TradingSessionCatalog catalog = catalog(List.of(
                friday,
                explicitSaturday,
                mondayAfterGap,
                irregularEarlyClose,
                thursdayAfterOmittedDay));

        Resolved week = (Resolved) SessionCloseHorizonResolver.resolve(request(
                new Original("call-explicit-week", Instant.parse("2026-03-06T16:00:00Z")),
                OutcomeHorizon.W1,
                catalog));
        Resolved weekendGap = (Resolved) SessionCloseHorizonResolver.resolve(request(
                new Original("call-weekend-gap", Instant.parse("2026-03-08T12:00:00Z")),
                OutcomeHorizon.D1,
                catalog));
        Resolved saturdayNextEligible = (Resolved) SessionCloseHorizonResolver.resolve(request(
                new Original("call-saturday-next", friday.closesAt()),
                OutcomeHorizon.D1,
                catalog));
        Resolved beforeEarlyClose = (Resolved) SessionCloseHorizonResolver.resolve(request(
                new Original(
                        "call-before-early-close",
                        irregularEarlyClose.closesAt().minusNanos(1_000)),
                OutcomeHorizon.D1,
                catalog));
        Resolved atEarlyClose = (Resolved) SessionCloseHorizonResolver.resolve(request(
                new Original("call-at-early-close", irregularEarlyClose.closesAt()),
                OutcomeHorizon.D1,
                catalog));

        assertThat(week.window().sessions()).containsExactly(
                friday,
                explicitSaturday,
                mondayAfterGap,
                irregularEarlyClose,
                thursdayAfterOmittedDay);
        assertThat(week.window().endpointSession()).isEqualTo(thursdayAfterOmittedDay);
        assertThat(week.window().sessions())
                .extracting(TradingSession::sessionId)
                .contains("session-saturday", "session-early-close")
                .doesNotContain("session-wednesday");
        assertThat(weekendGap.window().endpointSession()).isEqualTo(mondayAfterGap);
        assertThat(saturdayNextEligible.window().endpointSession())
                .isEqualTo(explicitSaturday);
        assertThat(beforeEarlyClose.window().endpointSession())
                .isEqualTo(irregularEarlyClose);
        assertThat(atEarlyClose.window().endpointSession())
                .isEqualTo(thursdayAfterOmittedDay);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("firstEligibleMissingVectors")
    void reportsWhenTheCatalogHasNoSessionClosingAfterTheBasis(
            String scenario,
            TradingSessionCatalog catalog,
            Instant basisEventTime) {
        SessionCloseHorizonRequest request = request(
                new Original("call-first-missing", basisEventTime),
                OutcomeHorizon.D1,
                catalog);

        SessionCloseHorizonResolution result = SessionCloseHorizonResolver.resolve(request);

        assertThat(result).as(scenario).isEqualTo(new Incomplete(
                context(request),
                IncompleteReason.FIRST_ELIGIBLE_SESSION_MISSING));
        assertThat(result).isNotInstanceOf(Resolved.class);
    }

    private static Stream<Arguments> firstEligibleMissingVectors() {
        List<TradingSession> sessions = explicitSessions(2);
        Instant lastClose = sessions.getLast().closesAt();
        return Stream.of(
                Arguments.of(
                        "an empty explicit catalog cannot supply D1",
                        catalog(List.of()),
                        FIRST_OPEN),
                Arguments.of(
                        "the exact last published close skips that session",
                        catalog(sessions),
                        lastClose),
                Arguments.of(
                        "an event after the last published close has no eligible session",
                        catalog(sessions),
                        lastClose.plusNanos(1_000)));
    }

    @Test
    void reportsWhenD1ExistsButTheNamedEndpointIsNotPublished() {
        TradingSessionCatalog shortCatalog = catalog(explicitSessions(4));
        SessionCloseHorizonRequest request = request(
                new Original("call-endpoint-missing", FIRST_OPEN.plusSeconds(30 * 60)),
                OutcomeHorizon.W1,
                shortCatalog);

        SessionCloseHorizonResolution result = SessionCloseHorizonResolver.resolve(request);

        assertThat(result).isEqualTo(new Incomplete(
                context(request),
                IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING));
        Incomplete incomplete = (Incomplete) result;
        assertThat(incomplete.context().sessionCount()).isEqualTo(5);
        assertThat(incomplete.context().policyDefinitionHash()).isEqualTo(DEFINITION_HASH);
        assertThat(result).isNotInstanceOf(Resolved.class);
    }

    @Test
    void originalAndEachCallerValidatedCorrectionResolveAsSeparatePreservedLineages() {
        List<TradingSession> sessions = explicitSessions(6);
        TradingSessionCatalog catalog = catalog(sessions);
        Original original = new Original(
                "call-lineage", FIRST_OPEN.plusSeconds(30 * 60));
        Correction firstCorrection = new Correction(
                "call-lineage", "revision-1", sessions.getFirst().closesAt());
        Correction secondCorrection = new Correction(
                "call-lineage", "revision-2", sessions.get(2).closesAt());

        Resolved originalResult = resolveD1(original, catalog);
        Resolved firstCorrectionResult = resolveD1(firstCorrection, catalog);
        Resolved secondCorrectionResult = resolveD1(secondCorrection, catalog);
        Resolved originalReplay = resolveD1(original, catalog);

        assertThat(originalResult).isEqualTo(originalReplay);
        assertThat(originalResult.window().endpointSession()).isEqualTo(sessions.get(0));
        assertThat(firstCorrectionResult.window().endpointSession()).isEqualTo(sessions.get(1));
        assertThat(secondCorrectionResult.window().endpointSession()).isEqualTo(sessions.get(3));
        assertThat(originalResult.window().context().basis()).isSameAs(original);
        assertThat(firstCorrectionResult.window().context().basis())
                .isSameAs(firstCorrection)
                .isNotEqualTo(secondCorrection);
        assertThat(secondCorrectionResult.window().context().basis())
                .isSameAs(secondCorrection);
        assertThat(original.basisRevisionId()).isNull();
        assertThat(firstCorrection.basisRevisionId()).isEqualTo("revision-1");
        assertThat(secondCorrection.basisRevisionId()).isEqualTo("revision-2");
    }

    @Test
    void canonicalPolicyDefinitionHasStableExactUtf8BytesAndIndependentSha256()
            throws NoSuchAlgorithmException {
        SessionCloseHorizonPolicyVersion policy = policy();

        byte[] firstRead = policy.canonicalDefinitionUtf8();
        String independentlyCalculatedHash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8)));

        assertThat(policy.canonicalDefinition()).isEqualTo(CANONICAL_DEFINITION);
        assertThat(firstRead)
                .hasSize(633)
                .containsExactly(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8));
        assertThat(independentlyCalculatedHash).isEqualTo(DEFINITION_HASH);
        assertThat(policy.definitionHash()).isEqualTo(DEFINITION_HASH);

        firstRead[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8())
                .containsExactly(CANONICAL_DEFINITION.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void defensivelyCopiesCatalogAndResolvedWindowAndReplaysDeterministically() {
        List<TradingSession> mutableSource = new ArrayList<>(explicitSessions(5));
        List<TradingSession> before = List.copyOf(mutableSource);
        TradingSessionCatalog catalog = new TradingSessionCatalog(
                CALENDAR_ID, CATALOG_REVISION, mutableSource);
        SessionCloseHorizonRequest request = request(
                new Original("call-immutable", FIRST_OPEN.plusSeconds(30 * 60)),
                OutcomeHorizon.W1,
                catalog);

        SessionCloseHorizonResolution first = SessionCloseHorizonResolver.resolve(request);
        SessionCloseHorizonResolution replay = SessionCloseHorizonResolver.resolve(request);
        mutableSource.clear();

        assertThat(first).isEqualTo(replay);
        assertThat(catalog.orderedSessions()).containsExactlyElementsOf(before);
        ResolvedSessionWindow window = ((Resolved) first).window();
        assertThatThrownBy(() -> catalog.orderedSessions().add(before.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> window.sessions().add(before.getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void resultDoesNotDependOnJvmDefaultLocaleOrTimeZone() {
        TradingSessionCatalog catalog = catalog(explicitSessions(5));
        SessionCloseHorizonRequest request = request(
                new Original("call-jvm-defaults", FIRST_OPEN.plusSeconds(30 * 60)),
                OutcomeHorizon.W1,
                catalog);
        SessionCloseHorizonResolution baseline = SessionCloseHorizonResolver.resolve(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Kiritimati"));

            assertThat(SessionCloseHorizonResolver.resolve(request)).isEqualTo(baseline);
        } finally {
            TimeZone.setDefault(originalTimeZone);
            Locale.setDefault(originalLocale);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBasisVectors")
    void rejectsInvalidOriginalOrCorrectionBasis(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOfAny(IllegalArgumentException.class, NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidBasisVectors() {
        Instant validEventTime = FIRST_OPEN;
        Instant subMicrosecond = Instant.parse("2026-01-01T00:00:00.000000001Z");
        return Stream.of(
                invalid("original null call ID",
                        () -> new Original(null, validEventTime), "callId"),
                invalid("original untrimmed call ID",
                        () -> new Original(" call", validEventTime), "callId"),
                invalid("original null event time",
                        () -> new Original("call-1", null), "eventTime"),
                invalid("original sub-microsecond event time",
                        () -> new Original("call-1", subMicrosecond),
                        "eventTime must not exceed microsecond precision"),
                invalid("correction null revision ID",
                        () -> new Correction("call-1", null, validEventTime),
                        "basisRevisionId"),
                invalid("correction null call ID",
                        () -> new Correction(null, "revision-1", validEventTime),
                        "callId"),
                invalid("correction blank call ID",
                        () -> new Correction(" ", "revision-1", validEventTime),
                        "callId"),
                invalid("correction blank revision ID",
                        () -> new Correction("call-1", " ", validEventTime),
                        "basisRevisionId"),
                invalid("correction null event time",
                        () -> new Correction("call-1", "revision-1", null),
                        "eventTime"),
                invalid("correction sub-microsecond event time",
                        () -> new Correction("call-1", "revision-1", subMicrosecond),
                        "eventTime must not exceed microsecond precision"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRequestVectors")
    void rejectsInvalidResolutionRequests(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        assertThatThrownBy(construction)
                .as(scenario)
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining(expectedMessage);
    }

    private static Stream<Arguments> invalidRequestVectors() {
        Original basis = new Original("call-request", FIRST_OPEN);
        TradingSessionCatalog catalog = catalog(explicitSessions(1));
        return Stream.of(
                invalid("null policy", () -> new SessionCloseHorizonRequest(
                        null, basis, OutcomeHorizon.D1, catalog), "policyVersion"),
                invalid("null basis", () -> new SessionCloseHorizonRequest(
                        policy(), null, OutcomeHorizon.D1, catalog), "basis"),
                invalid("null horizon", () -> new SessionCloseHorizonRequest(
                        policy(), basis, null, catalog), "horizon"),
                invalid("null catalog", () -> new SessionCloseHorizonRequest(
                        policy(), basis, OutcomeHorizon.D1, null), "catalog"),
                invalid("null resolver request",
                        () -> SessionCloseHorizonResolver.resolve(null), "request"));
    }

    @Test
    void publicResolutionConstructorsEnforcePolicyAndWindowInvariants() {
        List<TradingSession> sessions = explicitSessions(5);
        Original basis = new Original("call-invariants", FIRST_OPEN.plusSeconds(30 * 60));
        SessionCloseHorizonRequest request = request(
                basis, OutcomeHorizon.W1, catalog(sessions));
        ResolutionContext validContext = context(request);

        assertThatThrownBy(() -> new ResolutionContext(
                policy(), "0".repeat(64), basis, OutcomeHorizon.W1, 5,
                CALENDAR_ID, CATALOG_REVISION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("policyDefinitionHash");
        assertThatThrownBy(() -> new ResolutionContext(
                policy(), DEFINITION_HASH, basis, OutcomeHorizon.W1, 4,
                CALENDAR_ID, CATALOG_REVISION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sessionCount");
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                validContext, sessions.subList(0, 4), sessions.get(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly context.sessionCount");
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                validContext, sessions, sessions.get(3)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpointSession");
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                validContext,
                List.of(sessions.get(1), sessions.get(2), sessions.get(3),
                        sessions.get(4), sessions.get(0)),
                sessions.get(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chronological and nonoverlapping");
        TradingSession duplicateIdAtEndpoint = new TradingSession(
                sessions.getFirst().sessionId(),
                sessions.getLast().opensAt(),
                sessions.getLast().closesAt());
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                validContext,
                List.of(sessions.get(0), sessions.get(1), sessions.get(2),
                        sessions.get(3), duplicateIdAtEndpoint),
                duplicateIdAtEndpoint))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unique sessionId");
        Original exactFirstCloseBasis = new Original(
                "call-first-close-invariant", sessions.getFirst().closesAt());
        ResolutionContext exactFirstCloseContext = context(request(
                exactFirstCloseBasis, OutcomeHorizon.W1, catalog(sessions)));
        assertThatThrownBy(() -> new ResolvedSessionWindow(
                exactFirstCloseContext, sessions, sessions.getLast()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly after basis eventTime");
        assertThatThrownBy(() -> new Resolved(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("window");
        assertThatThrownBy(() -> new Incomplete(validContext, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("reason");
    }

    @Test
    void policyBasisAndResultSurfacesRemainClosedAndScheduleOnly() {
        assertThat(SessionCloseHorizonPolicyVersion.values()).containsExactly(policy());
        assertThatThrownBy(() -> policy().sessionCount(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("horizon");
        assertThat(OutcomeBasis.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Original.class, Correction.class);
        assertThat(SessionCloseHorizonResolution.class.getPermittedSubclasses())
                .containsExactlyInAnyOrder(Resolved.class, Incomplete.class);
        assertThat(IncompleteReason.values()).containsExactly(
                IncompleteReason.FIRST_ELIGIBLE_SESSION_MISSING,
                IncompleteReason.HORIZON_ENDPOINT_SESSION_MISSING);
        assertRecordComponents(Original.class,
                "callId:String", "eventTime:Instant");
        assertRecordComponents(Correction.class,
                "callId:String", "basisRevisionId:String", "eventTime:Instant");
        assertRecordComponents(SessionCloseHorizonRequest.class,
                "policyVersion:SessionCloseHorizonPolicyVersion",
                "basis:OutcomeBasis",
                "horizon:OutcomeHorizon",
                "catalog:TradingSessionCatalog");
        assertRecordComponents(ResolutionContext.class,
                "policyVersion:SessionCloseHorizonPolicyVersion",
                "policyDefinitionHash:String",
                "basis:OutcomeBasis",
                "horizon:OutcomeHorizon",
                "sessionCount:int",
                "calendarId:String",
                "catalogRevision:String");
        assertRecordComponents(ResolvedSessionWindow.class,
                "context:ResolutionContext",
                "sessions:List",
                "endpointSession:TradingSession");
        assertRecordComponents(Resolved.class, "window:ResolvedSessionWindow");
        assertRecordComponents(Incomplete.class,
                "context:ResolutionContext", "reason:IncompleteReason");
        assertThat(Arrays.stream(SessionCloseHorizonRequest.class.getRecordComponents()))
                .extracting(component -> component.getName().toLowerCase(Locale.ROOT))
                .noneMatch(name -> name.contains("evaluationasof")
                        || name.contains("price")
                        || name.contains("return"));
    }

    private static Resolved resolveD1(OutcomeBasis basis, TradingSessionCatalog catalog) {
        return (Resolved) SessionCloseHorizonResolver.resolve(
                request(basis, OutcomeHorizon.D1, catalog));
    }

    private static SessionCloseHorizonPolicyVersion policy() {
        return SessionCloseHorizonPolicyVersion.STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1;
    }

    private static SessionCloseHorizonRequest request(
            OutcomeBasis basis,
            OutcomeHorizon horizon,
            TradingSessionCatalog catalog) {
        return new SessionCloseHorizonRequest(policy(), basis, horizon, catalog);
    }

    private static ResolutionContext context(SessionCloseHorizonRequest request) {
        return new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.basis(),
                request.horizon(),
                request.policyVersion().sessionCount(request.horizon()),
                request.catalog().calendarId(),
                request.catalog().revision());
    }

    private static TradingSessionCatalog catalog(List<TradingSession> sessions) {
        return new TradingSessionCatalog(CALENDAR_ID, CATALOG_REVISION, sessions);
    }

    private static List<TradingSession> explicitSessions(int count) {
        List<TradingSession> sessions = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            Instant opensAt = FIRST_OPEN.plusSeconds(index * 2L * 60L * 60L);
            sessions.add(new TradingSession(
                    sessionId(index + 1),
                    opensAt,
                    opensAt.plusSeconds(60 * 60)));
        }
        return List.copyOf(sessions);
    }

    private static String sessionId(int oneBasedIndex) {
        return String.format(Locale.ROOT, "session-%03d", oneBasedIndex);
    }

    private static TradingSession session(String sessionId, String opensAt, String closesAt) {
        return new TradingSession(sessionId, Instant.parse(opensAt), Instant.parse(closesAt));
    }

    private static void assertRecordComponents(Class<?> recordType, String... expected) {
        assertThat(Arrays.stream(recordType.getRecordComponents()))
                .extracting(component -> component.getName()
                        + ":" + component.getType().getSimpleName())
                .containsExactly(expected);
    }

    private static Arguments invalid(
            String scenario,
            ThrowingCallable construction,
            String expectedMessage) {
        return Arguments.of(scenario, construction, expectedMessage);
    }
}
