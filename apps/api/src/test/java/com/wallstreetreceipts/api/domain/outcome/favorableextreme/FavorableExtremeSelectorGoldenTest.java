package com.wallstreetreceipts.api.domain.outcome.favorableextreme;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityRequest;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolver;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.FavorableExtreme;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.FavorableExtremeField;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.Resolved;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.SelectionEvidence;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution.UnavailableReason;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation.BoundaryType;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation.WindowCoverageCompleteness;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FullWindowHighLowObservation.WindowPriceField;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Correction;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis.Original;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.horizon.TradingSession;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.routing.CalculatorSideRouting;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence.TargetDisposition.Present;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityRequest;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution.ReadyForWindowEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolver;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

class FavorableExtremeSelectorGoldenTest {

    private static final String POLICY_HASH =
            "e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463";
    private static final String HORIZON_HASH =
            "550087efe7ddf2ba31974c89c2740ab79df986eefef48919c32c56a3232f8dc1";
    private static final String CALENDAR_ID = "calendar-xnas";
    private static final String CATALOG_REVISION = "calendar-revision-9";
    private static final String ASSET_ID = "asset-nvda";
    private static final String VENUE_ID = "venue-xnas";
    private static final String SOURCE_ID = "source-window-bars";
    private static final String SOURCE_REVISION = "source-revision-4";
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant OPEN = Instant.parse("2026-08-20T13:30:00Z");
    private static final Instant BASIS_TIME = Instant.parse("2026-08-20T15:00:00Z");
    private static final Instant CLOSE = Instant.parse("2026-08-20T20:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-08-20T20:01:00Z");
    private static final EndpointPriceAdjustmentBasis REQUIRED_ADJUSTMENT =
            EndpointPriceAdjustmentBasis
                    .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;

    @Test
    void canonicalDefinitionHasExactBytesIndependentHashAndDefensiveReads()
            throws Exception {
        var policy = policy();
        byte[] bytes = policy.canonicalDefinitionUtf8();

        assertThat(bytes).hasSize(4633);
        assertThat(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(POLICY_HASH)
                .isEqualTo(policy.definitionHash());
        assertThat(policy.canonicalDefinition())
                .contains("observation.time>basis.eventTime&&observation.time<=endpointSession.closesAt")
                .contains("\"rawAggregation\":\"ABSENT\"")
                .contains("\"rawObservationVerification\":\"ABSENT\"")
                .contains("\"endpointCloseFallback\":\"ABSENT\"")
                .contains("PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR")
                .contains("EXACT_CAUSAL_WINDOW_SESSION_UNION");

        bytes[0] = (byte) '!';
        assertThat(policy.canonicalDefinitionUtf8()[0]).isEqualTo((byte) '{');
    }

    @Test
    void exactFileRecordEnumAndEndpointFirewallSurfacesAreStable() throws Exception {
        Path packagePath = Path.of(
                "src/main/java/com/wallstreetreceipts/api/domain/outcome/favorableextreme");
        try (var files = Files.list(packagePath)) {
            assertThat(files.filter(path -> path.toString().endsWith(".java"))
                    .map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly(
                            "FavorableExtremePolicyVersion.java",
                            "FavorableExtremeRequest.java",
                            "FavorableExtremeResolution.java",
                            "FavorableExtremeSelector.java",
                            "FullWindowHighLowObservation.java",
                            "WindowPriceBinding.java");
        }

        assertRecordComponents(WindowPriceBinding.class,
                "bindingId:String", "bindingRevision:String", "assetId:String",
                "primaryVenueId:String", "currency:Currency", "priceSourceId:String",
                "priceSourceRevision:String", "availableAt:Instant",
                "capturedAt:Instant", "provenanceId:String");
        assertRecordComponents(FullWindowHighLowObservation.class,
                "observationId:String", "providerEventId:String", "basis:OutcomeBasis",
                "horizon:OutcomeHorizon", "assetId:String", "venueId:String",
                "currency:Currency", "priceSourceId:String",
                "priceSourceRevision:String", "provenanceId:String",
                "calendarId:String", "catalogRevision:String",
                "orderedSessionIds:List", "lowerBound:Instant",
                "lowerBoundType:BoundaryType", "upperBound:Instant",
                "upperBoundType:BoundaryType", "priceField:WindowPriceField",
                "coverageCompleteness:WindowCoverageCompleteness",
                "adjustmentBasis:EndpointPriceAdjustmentBasis",
                "corporateActionContinuity:CorporateActionContinuity",
                "availableAt:Instant", "capturedAt:Instant",
                "windowHigh:BigDecimal", "windowLow:BigDecimal");
        assertRecordComponents(FavorableExtremeRequest.class,
                "policyVersion:FavorableExtremePolicyVersion",
                "readyEligibility:ReadyForWindowEvidence",
                "binding:WindowPriceBinding", "candidates:List");
        assertRecordComponents(FavorableExtremeResolution.ResolutionContext.class,
                "policyVersion:FavorableExtremePolicyVersion",
                "policyDefinitionHash:String",
                "readyEligibility:ReadyForWindowEvidence");
        assertRecordComponents(SelectionEvidence.class,
                "binding:WindowPriceBinding", "knownCandidates:List");
        assertRecordComponents(FavorableExtreme.class,
                "field:FavorableExtremeField", "value:BigDecimal");
        assertRecordComponents(Resolved.class, "context:ResolutionContext",
                "evidence:SelectionEvidence", "favorableExtreme:FavorableExtreme");
        assertRecordComponents(Unavailable.class, "context:ResolutionContext",
                "evidence:SelectionEvidence", "reason:UnavailableReason");

        assertThat(FavorableExtremeResolution.class.isSealed()).isTrue();
        assertThat(Arrays.stream(FavorableExtremeResolution.class
                .getPermittedSubclasses()).map(Class::getSimpleName).toList())
                .containsExactlyInAnyOrder("Resolved", "Unavailable");
        assertThat(FavorableExtremePolicyVersion.values()).containsExactly(policy());
        assertThat(BoundaryType.values()).containsExactly(
                BoundaryType.EXCLUSIVE, BoundaryType.INCLUSIVE, BoundaryType.UNKNOWN);
        assertThat(WindowPriceField.values()).containsExactly(
                WindowPriceField
                        .PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR,
                WindowPriceField.INDICATIVE_OR_OTHER);
        assertThat(WindowCoverageCompleteness.values()).containsExactly(
                WindowCoverageCompleteness.EXACT_CAUSAL_WINDOW_SESSION_UNION,
                WindowCoverageCompleteness.PARTIAL_OR_UNKNOWN);
        assertThat(UnavailableReason.values()).containsExactly(
                UnavailableReason.TARGET_ADJUSTMENT_BASIS_UNSUPPORTED,
                UnavailableReason.BINDING_NOT_KNOWN_AS_OF,
                UnavailableReason.BINDING_ASSET_MISMATCH,
                UnavailableReason.BINDING_PRIMARY_VENUE_MISMATCH,
                UnavailableReason.BINDING_CURRENCY_MISMATCH,
                UnavailableReason.OBSERVATION_MISSING_AS_OF,
                UnavailableReason.BASIS_MISMATCH,
                UnavailableReason.HORIZON_MISMATCH,
                UnavailableReason.ASSET_MISMATCH,
                UnavailableReason.PRIMARY_VENUE_MISMATCH,
                UnavailableReason.CURRENCY_MISMATCH,
                UnavailableReason.SOURCE_MISMATCH,
                UnavailableReason.CATALOG_MISMATCH,
                UnavailableReason.SESSION_WINDOW_MISMATCH,
                UnavailableReason.LOWER_BOUND_MISMATCH,
                UnavailableReason.UPPER_BOUND_MISMATCH,
                UnavailableReason.BOUNDARY_CONVENTION_MISMATCH,
                UnavailableReason.PRICE_FIELD_MISMATCH,
                UnavailableReason.WINDOW_COMPLETENESS_UNAVAILABLE,
                UnavailableReason.ADJUSTMENT_BASIS_MISMATCH,
                UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE,
                UnavailableReason.OBSERVATION_AMBIGUOUS);

        String productionSource;
        try (var files = Files.walk(packagePath)) {
            productionSource = files.filter(path -> path.toString().endsWith(".java"))
                    .map(FavorableExtremeSelectorGoldenTest::readText)
                    .reduce("", (left, right) -> left + right);
        }
        assertThat(productionSource)
                .doesNotContain("EndpointPriceObservation")
                .doesNotContain("EndpointPriceResolution")
                .doesNotContain("EndpointPriceField")
                .doesNotContain("EndpointPriceSelector")
                .doesNotContain("OFFICIAL_REGULAR_SESSION_CLOSE")
                .doesNotContain("TargetHitCalculator")
                .doesNotContain("TargetHitInput");
    }

    @ParameterizedTest
    @EnumSource(value = CallDirection.class, names = {
            "STRONG_BULLISH", "BULLISH", "BEARISH", "STRONG_BEARISH"})
    void selectsHighForBullishAndLowForBearishWithoutCallingCalculator(
            CallDirection direction) {
        ReadyForWindowEvidence ready = ready(
                new Original("call-1", BASIS_TIME), direction, REQUIRED_ADJUSTMENT,
                OutcomeHorizon.D1, List.of(session("session-1", OPEN, CLOSE)), AS_OF);
        FullWindowHighLowObservation observation = exactCandidate(
                ready, "observation-1");

        Resolved result = (Resolved) FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(observation)));

        boolean bullish = direction == CallDirection.BULLISH
                || direction == CallDirection.STRONG_BULLISH;
        assertThat(result.evidence().binding()).isEqualTo(exactBinding(ready));
        assertThat(result.evidence().knownCandidates())
                .containsExactly(observation);
        assertThat(result.favorableExtreme().field()).isEqualTo(
                bullish ? FavorableExtremeField.HIGH : FavorableExtremeField.LOW);
        assertThat(result.favorableExtreme().value()).isEqualTo(
                bullish ? observation.windowHigh() : observation.windowLow());
        assertThat(result.context().readyEligibility()).isSameAs(ready);
        assertThat(result.context().policyDefinitionHash()).isEqualTo(POLICY_HASH);
    }

    @Test
    void correctionBasisRemainsAnIndependentExactWindowIdentity() {
        OutcomeBasis correction = new Correction(
                "call-1", "revision-2", BASIS_TIME.plusSeconds(300));
        ReadyForWindowEvidence ready = ready(
                correction, CallDirection.BEARISH, REQUIRED_ADJUSTMENT,
                OutcomeHorizon.D1, List.of(session("session-1", OPEN, CLOSE)), AS_OF);
        FullWindowHighLowObservation candidate = exactCandidate(
                ready, "correction-window");

        Resolved result = (Resolved) FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(candidate)));

        assertThat(candidate.basis()).isSameAs(correction);
        assertThat(candidate.lowerBound()).isEqualTo(correction.eventTime());
        assertThat(result.favorableExtreme())
                .isEqualTo(new FavorableExtreme(
                        FavorableExtremeField.LOW, candidate.windowLow()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("causalBoundaryScenarios")
    void exactCausalWindowAlwaysUsesBasisExclusiveAndEndpointInclusive(
            String name,
            OutcomeBasis basis,
            TradingSession selectedSession,
            Instant evaluationAsOf) {
        ReadyForWindowEvidence ready = ready(
                basis, CallDirection.BULLISH, REQUIRED_ADJUSTMENT,
                OutcomeHorizon.D1, List.of(selectedSession), evaluationAsOf);
        FullWindowHighLowObservation candidate = exactCandidate(
                ready, "causal-" + name);

        assertThat(FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(candidate))))
                .isInstanceOf(Resolved.class);
        assertThat(candidate.lowerBound()).isEqualTo(basis.eventTime());
        assertThat(candidate.lowerBoundType()).isEqualTo(BoundaryType.EXCLUSIVE);
        assertThat(candidate.upperBound()).isEqualTo(selectedSession.closesAt());
        assertThat(candidate.upperBoundType()).isEqualTo(BoundaryType.INCLUSIVE);
    }

    @Test
    void everyNamedHorizonRequiresEveryExactSessionIdInSourceOrder() {
        for (OutcomeHorizon horizon : OutcomeHorizon.values()) {
            OutcomeBasis namedBasis = new Original(
                    "call-" + horizon.name().toLowerCase(Locale.ROOT), BASIS_TIME);
            List<TradingSession> namedSessions = sessionsFor(horizon);
            Instant namedAsOf = namedSessions.getLast().closesAt().plusSeconds(60);
            ReadyForWindowEvidence namedReady = ready(
                    namedBasis, CallDirection.BULLISH, REQUIRED_ADJUSTMENT,
                    horizon, namedSessions, namedAsOf);
            FullWindowHighLowObservation namedExact = exactCandidate(
                    namedReady, "named-" + horizon.name().toLowerCase(Locale.ROOT));

            assertThat(namedExact.orderedSessionIds()).containsExactlyElementsOf(
                    namedSessions.stream().map(TradingSession::sessionId).toList());
            assertThat(FavorableExtremeSelector.select(request(
                    namedReady, exactBinding(namedReady), List.of(namedExact))))
                    .isInstanceOf(Resolved.class);
        }

        OutcomeBasis basis = new Original("call-w1-missing", BASIS_TIME);
        List<TradingSession> sessions = sessionsFor(OutcomeHorizon.W1);
        Instant evaluationAsOf = sessions.getLast().closesAt().plusSeconds(60);
        ReadyForWindowEvidence ready = ready(
                basis, CallDirection.BULLISH, REQUIRED_ADJUSTMENT,
                OutcomeHorizon.W1, sessions, evaluationAsOf);
        FullWindowHighLowObservation missingMiddle = candidate(
                ready, "w1-missing", null, null,
                Set.of(), List.of("s1", "s2", "s4", "s5"),
                new BigDecimal("240.5000"), new BigDecimal("170.250"));
        assertUnavailable(FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(missingMiddle))),
                UnavailableReason.SESSION_WINDOW_MISMATCH);
    }

    @Test
    void nullAndEitherFutureBindingTimestampProduceEqualCompleteResults() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FavorableExtremeResolution nullBinding = FavorableExtremeSelector.select(
                request(ready, null, List.of(exactCandidate(ready, "known"))));
        Instant evaluationAsOf = ready.context().evaluationAsOf();
        WindowPriceBinding futureAvailable = binding(
                ready, evaluationAsOf.plusNanos(1_000),
                evaluationAsOf.plusNanos(2_000), BindingFault.NONE);
        WindowPriceBinding futureCaptured = binding(
                ready, BASIS_TIME, evaluationAsOf.plusNanos(1_000), BindingFault.NONE);

        assertThat(FavorableExtremeSelector.select(
                request(ready, futureAvailable, List.of(exactCandidate(ready, "known")))))
                .isEqualTo(nullBinding);
        assertThat(FavorableExtremeSelector.select(
                request(ready, futureCaptured, List.of(exactCandidate(ready, "known")))))
                .isEqualTo(nullBinding);
        Unavailable unavailable = (Unavailable) nullBinding;
        assertThat(unavailable.reason())
                .isEqualTo(UnavailableReason.BINDING_NOT_KNOWN_AS_OF);
        assertThat(unavailable.evidence().binding()).isNull();
        assertThat(unavailable.evidence().knownCandidates()).isEmpty();
    }

    @Test
    void futureExactWrongAndDuplicateCandidatesAreInvisibleToAllReasoning() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        WindowPriceBinding binding = exactBinding(ready);
        FavorableExtremeResolution empty = FavorableExtremeSelector.select(
                request(ready, binding, List.of()));
        Instant futureAvailable = ready.context().evaluationAsOf().plusNanos(1_000);
        Instant futureCaptured = ready.context().evaluationAsOf().plusNanos(2_000);
        FullWindowHighLowObservation futureExact = candidate(
                ready, "future-exact", futureAvailable, futureCaptured,
                Set.of(), null, new BigDecimal("240.5000"),
                new BigDecimal("170.250"));
        FullWindowHighLowObservation futureWrong = candidate(
                ready, "future-wrong", futureAvailable, futureCaptured,
                EnumSet.allOf(CandidateFault.class), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));
        FullWindowHighLowObservation capturedOnlyInFuture = candidate(
                ready, "future-captured", ready.context().evaluationAsOf(),
                futureAvailable, Set.of(CandidateFault.CURRENCY), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));

        assertThat(FavorableExtremeSelector.select(
                request(ready, binding, List.of(futureExact))))
                .isEqualTo(empty);
        assertThat(FavorableExtremeSelector.select(
                request(ready, binding, List.of(futureWrong, futureWrong))))
                .isEqualTo(empty);
        assertThat(FavorableExtremeSelector.select(
                request(ready, binding, List.of(capturedOnlyInFuture))))
                .isEqualTo(empty);
        assertUnavailable(empty, UnavailableReason.OBSERVATION_MISSING_AS_OF);

        FullWindowHighLowObservation known = exactCandidate(ready, "known");
        assertThat(FavorableExtremeSelector.select(
                request(ready, binding, List.of(known, futureWrong))))
                .isEqualTo(FavorableExtremeSelector.select(
                        request(ready, binding, List.of(known))));
    }

    @Test
    void exactPitTimestampEqualityIsVisible() {
        ReadyForWindowEvidence base = defaultReady(CallDirection.BULLISH);
        Instant asOf = base.context().evaluationAsOf();
        WindowPriceBinding binding = binding(
                base, asOf, asOf, BindingFault.NONE);
        FullWindowHighLowObservation observation = candidate(
                base, "boundary", asOf, asOf, Set.of(), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));

        assertThat(FavorableExtremeSelector.select(
                request(base, binding, List.of(observation))))
                .isInstanceOf(Resolved.class);
    }

    @ParameterizedTest
    @EnumSource(value = BindingFault.class, names = {"ASSET", "VENUE", "CURRENCY"})
    void bindingIdentityGatesAreExactAndClearLaterCandidates(BindingFault fault) {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        WindowPriceBinding wrong = binding(
                ready, BASIS_TIME, BASIS_TIME, fault);
        UnavailableReason expected = switch (fault) {
            case ASSET -> UnavailableReason.BINDING_ASSET_MISMATCH;
            case VENUE -> UnavailableReason.BINDING_PRIMARY_VENUE_MISMATCH;
            case CURRENCY -> UnavailableReason.BINDING_CURRENCY_MISMATCH;
            case NONE -> throw new AssertionError();
        };

        Unavailable result = (Unavailable) FavorableExtremeSelector.select(
                request(ready, wrong, List.of(exactCandidate(ready, "later"))));

        assertThat(result.reason()).isEqualTo(expected);
        assertThat(result.evidence().binding()).isSameAs(wrong);
        assertThat(result.evidence().knownCandidates()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(CandidateFault.class)
    void everyKnownCandidateMismatchUsesItsExactReason(CandidateFault fault) {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FullWindowHighLowObservation wrong = candidate(
                ready, "wrong-" + fault, null, null, Set.of(fault), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));

        Unavailable result = (Unavailable) FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(wrong)));

        assertThat(result.reason()).isEqualTo(fault.reason);
        assertThat(result.evidence().knownCandidates()).containsExactly(wrong);
    }

    @Test
    void mismatchPrecedenceIsCandidateOrderIndependentAndPoisonsValidEvidence() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FullWindowHighLowObservation exact = exactCandidate(ready, "exact");
        FullWindowHighLowObservation laterFault = candidate(
                ready, "currency", null, null, Set.of(CandidateFault.CURRENCY), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));
        FullWindowHighLowObservation earlierFault = candidate(
                ready, "basis", null, null, Set.of(CandidateFault.BASIS), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));

        var firstOrder = FavorableExtremeSelector.select(request(
                ready, exactBinding(ready), List.of(laterFault, earlierFault, exact)));
        var reverseOrder = FavorableExtremeSelector.select(request(
                ready, exactBinding(ready), List.of(exact, earlierFault, laterFault)));

        assertUnavailable(firstOrder, UnavailableReason.BASIS_MISMATCH);
        assertUnavailable(reverseOrder, UnavailableReason.BASIS_MISMATCH);
    }

    @Test
    void duplicateExactCandidateIsAmbiguousWithoutDeduplication() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FullWindowHighLowObservation exact = exactCandidate(ready, "duplicate");

        Unavailable result = (Unavailable) FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(exact, exact)));

        assertThat(result.reason()).isEqualTo(UnavailableReason.OBSERVATION_AMBIGUOUS);
        assertThat(result.evidence().knownCandidates()).containsExactly(exact, exact);

        FullWindowHighLowObservation distinctEqual = exactCandidate(
                ready, "duplicate");
        assertThat(distinctEqual).isEqualTo(exact).isNotSameAs(exact);
        Unavailable equalResult = (Unavailable) FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(exact, distinctEqual)));
        assertThat(equalResult.reason())
                .isEqualTo(UnavailableReason.OBSERVATION_AMBIGUOUS);
        assertThat(equalResult.evidence().knownCandidates())
                .containsExactly(exact, distinctEqual);
    }

    @Test
    void unsupportedTargetAdjustmentWinsBeforeBindingAndObservationEvidence() {
        ReadyForWindowEvidence ready = ready(
                new Original("call-unsupported", BASIS_TIME), CallDirection.BULLISH,
                EndpointPriceAdjustmentBasis.DIVIDEND_OR_TOTAL_RETURN_ADJUSTED,
                OutcomeHorizon.D1, List.of(session("session-1", OPEN, CLOSE)), AS_OF);
        WindowPriceBinding futureWrong = binding(
                ready, AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000),
                BindingFault.ASSET);

        Unavailable result = (Unavailable) FavorableExtremeSelector.select(
                request(ready, futureWrong, List.of()));

        assertThat(result.reason())
                .isEqualTo(UnavailableReason.TARGET_ADJUSTMENT_BASIS_UNSUPPORTED);
        assertThat(result.evidence())
                .isEqualTo(new SelectionEvidence(null, List.of()));
    }

    @Test
    void inputCollectionsAreDefensivelyCopied() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FullWindowHighLowObservation candidate = exactCandidate(ready, "copy");
        var mutableCandidates = new ArrayList<>(List.of(candidate));
        FavorableExtremeRequest request = request(
                ready, exactBinding(ready), mutableCandidates);
        mutableCandidates.clear();
        assertThat(request.candidates()).containsExactly(candidate);

        var sessionIds = new ArrayList<>(List.of("session-1"));
        FullWindowHighLowObservation withMutableSessions = candidate(
                ready, "sessions-copy", null, null, Set.of(), sessionIds,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));
        sessionIds.set(0, "mutated");
        assertThat(withMutableSessions.orderedSessionIds())
                .containsExactly("session-1");
        assertThatThrownBy(() -> withMutableSessions.orderedSessionIds().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void observationAndBindingConstructorsRejectMalformedEvidence() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FullWindowHighLowObservation exact = exactCandidate(ready, "limits");
        assertThatThrownBy(() -> new WindowPriceBinding(
                " binding", "rev", ASSET_ID, VENUE_ID, USD,
                SOURCE_ID, SOURCE_REVISION, BASIS_TIME, BASIS_TIME, "prov"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WindowPriceBinding(
                "binding", "rev", ASSET_ID, VENUE_ID, USD,
                SOURCE_ID, SOURCE_REVISION, BASIS_TIME, BASIS_TIME.minusSeconds(1),
                "prov"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                ready, "low-over-high", null, null, Set.of(), null,
                new BigDecimal("10"), new BigDecimal("11")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                ready, "scale-13", null, null, Set.of(), null,
                new BigDecimal("1.0000000000001"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                ready, "duplicate-session", null, null, Set.of(),
                List.of("session-1", "session-1"),
                new BigDecimal("2"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                ready, "zero", null, null, Set.of(), null,
                BigDecimal.ZERO, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> candidate(
                ready, " identity", null, null, Set.of(), null,
                new BigDecimal("2"), BigDecimal.ONE))
                .isInstanceOf(IllegalArgumentException.class);

        BigDecimal numericMaximum = new BigDecimal(
                "99999999999999999999999999.999999999999");
        BigDecimal numericMinimum = new BigDecimal("0.000000000001");
        FullWindowHighLowObservation exactNumericBoundary = copyObservation(
                exact, exact.lowerBound(), exact.upperBound(), exact.availableAt(),
                exact.capturedAt(), numericMaximum, numericMinimum);
        assertThat(exactNumericBoundary.windowHigh()).isSameAs(numericMaximum);
        assertThat(exactNumericBoundary.windowLow()).isSameAs(numericMinimum);
        assertThatThrownBy(() -> copyObservation(
                exact, exact.lowerBound(), exact.upperBound(), exact.availableAt(),
                exact.capturedAt(),
                new BigDecimal("100000000000000000000000000.000000000000"),
                numericMinimum))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copyObservation(
                exact, exact.upperBound(), exact.upperBound(), exact.availableAt(),
                exact.capturedAt(), exact.windowHigh(), exact.windowLow()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copyObservation(
                exact, exact.lowerBound(), exact.upperBound(),
                exact.upperBound().minusNanos(1_000), exact.capturedAt(),
                exact.windowHigh(), exact.windowLow()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copyObservation(
                exact, exact.lowerBound(), exact.upperBound(),
                exact.availableAt().plusNanos(1), exact.capturedAt(),
                exact.windowHigh(), exact.windowLow()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> copyObservation(
                exact, exact.lowerBound(), exact.upperBound(), exact.availableAt(),
                exact.availableAt().minusNanos(1_000),
                exact.windowHigh(), exact.windowLow()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void directResultConstructorsRejectContradictoryOrFutureEvidence() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        FavorableExtremeRequest request = request(
                ready, exactBinding(ready), List.of(exactCandidate(ready, "exact")));
        Resolved resolved = (Resolved) FavorableExtremeSelector.select(request);

        assertThatThrownBy(() -> new FavorableExtremeResolution.ResolutionContext(
                policy(), "wrong-hash", ready))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Resolved(
                resolved.context(), resolved.evidence(),
                new FavorableExtreme(
                        FavorableExtremeField.LOW,
                        resolved.evidence().knownCandidates().getFirst().windowLow())))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Unavailable(
                resolved.context(), resolved.evidence(),
                UnavailableReason.CURRENCY_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class);

        FullWindowHighLowObservation future = candidate(
                ready, "future", AS_OF.plusNanos(1_000), AS_OF.plusNanos(2_000),
                Set.of(), null, new BigDecimal("2"), BigDecimal.ONE);
        assertThatThrownBy(() -> new Unavailable(
                resolved.context(),
                new SelectionEvidence(exactBinding(ready), List.of(future)),
                UnavailableReason.OBSERVATION_AMBIGUOUS))
                .isInstanceOf(IllegalArgumentException.class);

        WindowPriceBinding wrongBinding = binding(
                ready, BASIS_TIME, BASIS_TIME, BindingFault.ASSET);
        assertThatThrownBy(() -> new Unavailable(
                resolved.context(),
                new SelectionEvidence(wrongBinding,
                        List.of(exactCandidate(ready, "must-clear"))),
                UnavailableReason.BINDING_ASSET_MISMATCH))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void equalHighAndLowIsValidAndOriginalDecimalScaleIsPreserved() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.BULLISH);
        BigDecimal unchanged = new BigDecimal("200.5000");
        FullWindowHighLowObservation candidate = candidate(
                ready, "flat", null, null, Set.of(), null, unchanged, unchanged);

        Resolved result = (Resolved) FavorableExtremeSelector.select(
                request(ready, exactBinding(ready), List.of(candidate)));

        assertThat(result.favorableExtreme().value()).isSameAs(candidate.windowHigh());
        assertThat(result.favorableExtreme().value().scale()).isEqualTo(4);
    }

    @Test
    void selectionIsIndependentOfLocaleTimezoneAndPriorCalls() {
        ReadyForWindowEvidence ready = defaultReady(CallDirection.STRONG_BEARISH);
        FavorableExtremeRequest request = request(
                ready, exactBinding(ready), List.of(exactCandidate(ready, "replay")));
        FavorableExtremeResolution expected = FavorableExtremeSelector.select(request);
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.JAPAN);
            TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));
            FavorableExtremeSelector.select(request(
                    defaultReady(CallDirection.BULLISH), null, List.of()));
            assertThat(FavorableExtremeSelector.select(request)).isEqualTo(expected);
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
    }

    private static Stream<Arguments> causalBoundaryScenarios() {
        TradingSession sameDay = session("same-day", OPEN, CLOSE);
        TradingSession nextDay = session(
                "next-day", Instant.parse("2026-08-21T13:30:00Z"),
                Instant.parse("2026-08-21T20:00:00Z"));
        return Stream.of(
                Arguments.of("pre-open",
                        new Original("pre-open", OPEN.minusSeconds(900)), sameDay,
                        CLOSE.plusSeconds(60)),
                Arguments.of("exact-open",
                        new Original("exact-open", OPEN), sameDay,
                        CLOSE.plusSeconds(60)),
                Arguments.of("intraday",
                        new Original("intraday", BASIS_TIME), sameDay,
                        CLOSE.plusSeconds(60)),
                Arguments.of("exact-prior-close",
                        new Original("exact-close", CLOSE), nextDay,
                        nextDay.closesAt().plusSeconds(60)),
                Arguments.of("strict-gap",
                        new Original("gap", CLOSE.plusSeconds(3_600)), nextDay,
                        nextDay.closesAt().plusSeconds(60)));
    }

    private static ReadyForWindowEvidence defaultReady(CallDirection direction) {
        return ready(
                new Original("call-1", BASIS_TIME), direction, REQUIRED_ADJUSTMENT,
                OutcomeHorizon.D1, List.of(session("session-1", OPEN, CLOSE)), AS_OF);
    }

    private static ReadyForWindowEvidence ready(
            OutcomeBasis basis,
            CallDirection direction,
            EndpointPriceAdjustmentBasis targetAdjustment,
            OutcomeHorizon horizon,
            List<TradingSession> sessions,
            Instant evaluationAsOf) {
        var horizonContext = new SessionCloseHorizonResolution.ResolutionContext(
                SessionCloseHorizonPolicyVersion
                        .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1,
                HORIZON_HASH,
                basis,
                horizon,
                sessions.size(),
                CALENDAR_ID,
                CATALOG_REVISION);
        var window = new SessionCloseHorizonResolution.ResolvedSessionWindow(
                horizonContext, sessions, sessions.getLast());
        var horizonResolution = new SessionCloseHorizonResolution.Resolved(window);
        var terms = new BasisForecastTermsEvidence(
                "terms-" + basis.callId(), basis, ASSET_ID, direction,
                new Present(new BigDecimal("235"), USD, null),
                "provider-analyst", "provider-event-terms",
                basis.eventTime(), basis.eventTime(), "provenance-terms");
        var route = CalculatorSideRouting.route(CallDirectionPolarityResolver.resolve(
                new CallDirectionPolarityRequest(
                        CallDirectionPolarityPolicyVersion
                                .COLLAPSE_STRONG_DIRECTIONS_NEUTRAL_NON_DIRECTIONAL_V1,
                        direction)));
        var target = new TargetPriceEvidence(
                "target-" + basis.callId(), basis, ASSET_ID, VENUE_ID, USD,
                targetAdjustment, new BigDecimal("235"), basis.eventTime(),
                basis.eventTime(), "provenance-target");
        var catalog = new CatalogPointInTimeEvidence(
                CALENDAR_ID, CATALOG_REVISION, "source-calendar", "revision-9",
                basis.eventTime(), basis.eventTime(), "provenance-calendar");
        var request = new TargetEligibilityRequest(
                TargetEligibilityPolicyVersion
                        .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1,
                horizonResolution, terms, route, target, catalog, evaluationAsOf);
        return (ReadyForWindowEvidence) TargetEligibilityResolver.resolve(request);
    }

    private static FavorableExtremeRequest request(
            ReadyForWindowEvidence ready,
            WindowPriceBinding binding,
            List<FullWindowHighLowObservation> candidates) {
        return new FavorableExtremeRequest(policy(), ready, binding, candidates);
    }

    private static WindowPriceBinding exactBinding(ReadyForWindowEvidence ready) {
        return binding(
                ready,
                ready.evidence().termsEvidence().basis().eventTime(),
                ready.evidence().termsEvidence().basis().eventTime(),
                BindingFault.NONE);
    }

    private static WindowPriceBinding binding(
            ReadyForWindowEvidence ready,
            Instant availableAt,
            Instant capturedAt,
            BindingFault fault) {
        var target = ready.evidence().targetEvidence();
        return new WindowPriceBinding(
                "binding-window", "binding-revision-1",
                fault == BindingFault.ASSET ? "wrong-asset" : target.assetId(),
                fault == BindingFault.VENUE ? "wrong-venue" : target.primaryVenueId(),
                fault == BindingFault.CURRENCY ? Currency.getInstance("EUR")
                        : target.currency(),
                SOURCE_ID, SOURCE_REVISION, availableAt, capturedAt,
                "provenance-binding");
    }

    private static FullWindowHighLowObservation exactCandidate(
            ReadyForWindowEvidence ready, String observationId) {
        return candidate(
                ready, observationId, null, null, Set.of(), null,
                new BigDecimal("240.5000"), new BigDecimal("170.250"));
    }

    private static FullWindowHighLowObservation copyObservation(
            FullWindowHighLowObservation source,
            Instant lowerBound,
            Instant upperBound,
            Instant availableAt,
            Instant capturedAt,
            BigDecimal windowHigh,
            BigDecimal windowLow) {
        return new FullWindowHighLowObservation(
                source.observationId(), source.providerEventId(), source.basis(),
                source.horizon(), source.assetId(), source.venueId(), source.currency(),
                source.priceSourceId(), source.priceSourceRevision(),
                source.provenanceId(), source.calendarId(), source.catalogRevision(),
                source.orderedSessionIds(), lowerBound, source.lowerBoundType(),
                upperBound, source.upperBoundType(), source.priceField(),
                source.coverageCompleteness(), source.adjustmentBasis(),
                source.corporateActionContinuity(), availableAt, capturedAt,
                windowHigh, windowLow);
    }

    private static FullWindowHighLowObservation candidate(
            ReadyForWindowEvidence ready,
            String observationId,
            Instant suppliedAvailableAt,
            Instant suppliedCapturedAt,
            Set<CandidateFault> faults,
            List<String> suppliedSessionIds,
            BigDecimal high,
            BigDecimal low) {
        var window = ((SessionCloseHorizonResolution.Resolved)
                ready.context().horizonResolution()).window();
        var context = window.context();
        var binding = exactBinding(ready);
        Instant endpoint = window.endpointSession().closesAt();
        Instant availableAt = suppliedAvailableAt == null
                ? endpoint : suppliedAvailableAt;
        Instant capturedAt = suppliedCapturedAt == null
                ? availableAt.plusSeconds(30) : suppliedCapturedAt;
        List<String> sessionIds = suppliedSessionIds == null
                ? window.sessions().stream().map(TradingSession::sessionId).toList()
                : suppliedSessionIds;
        if (faults.contains(CandidateFault.SESSION)) {
            sessionIds = List.of("wrong-session");
        }
        OutcomeHorizon wrongHorizon = context.horizon() == OutcomeHorizon.D1
                ? OutcomeHorizon.W1 : OutcomeHorizon.D1;
        return new FullWindowHighLowObservation(
                observationId,
                "provider-event-" + observationId,
                faults.contains(CandidateFault.BASIS)
                        ? new Original("wrong-call", context.basis().eventTime())
                        : context.basis(),
                faults.contains(CandidateFault.HORIZON)
                        ? wrongHorizon : context.horizon(),
                faults.contains(CandidateFault.ASSET)
                        ? "wrong-asset" : binding.assetId(),
                faults.contains(CandidateFault.VENUE)
                        ? "wrong-venue" : binding.primaryVenueId(),
                faults.contains(CandidateFault.CURRENCY)
                        ? Currency.getInstance("EUR") : binding.currency(),
                faults.contains(CandidateFault.SOURCE)
                        ? "wrong-source" : binding.priceSourceId(),
                faults.contains(CandidateFault.SOURCE)
                        ? "wrong-source-revision" : binding.priceSourceRevision(),
                "provenance-window-" + observationId,
                faults.contains(CandidateFault.CATALOG)
                        ? "wrong-calendar" : CALENDAR_ID,
                faults.contains(CandidateFault.CATALOG)
                        ? "wrong-catalog" : CATALOG_REVISION,
                sessionIds,
                faults.contains(CandidateFault.LOWER)
                        ? context.basis().eventTime().plusNanos(1_000)
                        : context.basis().eventTime(),
                faults.contains(CandidateFault.BOUNDARY)
                        ? BoundaryType.INCLUSIVE : BoundaryType.EXCLUSIVE,
                faults.contains(CandidateFault.UPPER)
                        ? endpoint.minusNanos(1_000) : endpoint,
                BoundaryType.INCLUSIVE,
                faults.contains(CandidateFault.FIELD)
                        ? WindowPriceField.INDICATIVE_OR_OTHER
                        : WindowPriceField
                                .PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR,
                faults.contains(CandidateFault.COMPLETENESS)
                        ? WindowCoverageCompleteness.PARTIAL_OR_UNKNOWN
                        : WindowCoverageCompleteness
                                .EXACT_CAUSAL_WINDOW_SESSION_UNION,
                faults.contains(CandidateFault.ADJUSTMENT)
                        ? EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER
                        : REQUIRED_ADJUSTMENT,
                faults.contains(CandidateFault.CONTINUITY)
                        ? CorporateActionContinuity.MERGER
                        : CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS,
                availableAt,
                capturedAt,
                high,
                low);
    }

    private static List<TradingSession> sessionsFor(OutcomeHorizon horizon) {
        int sessionCount = SessionCloseHorizonPolicyVersion
                .STRICTLY_AFTER_BASIS_EVENT_SESSION_CLOSE_V1
                .sessionCount(horizon);
        Instant firstOpen = OPEN;
        return Stream.iterate(0, value -> value + 1).limit(sessionCount)
                .map(index -> session(
                        "s" + (index + 1),
                        firstOpen.plusSeconds(index * 86_400L),
                        firstOpen.plusSeconds(index * 86_400L + 23_400L)))
                .toList();
    }

    private static TradingSession session(
            String sessionId, Instant opensAt, Instant closesAt) {
        return new TradingSession(sessionId, opensAt, closesAt);
    }

    private static FavorableExtremePolicyVersion policy() {
        return FavorableExtremePolicyVersion
                .POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1;
    }

    private static void assertUnavailable(
            FavorableExtremeResolution result, UnavailableReason reason) {
        assertThat(result).isInstanceOfSatisfying(Unavailable.class,
                unavailable -> assertThat(unavailable.reason()).isEqualTo(reason));
    }

    private static void assertRecordComponents(Class<?> type, String... expected) {
        assertThat(type.isRecord()).as(type.getSimpleName()).isTrue();
        assertThat(Arrays.stream(type.getRecordComponents())
                .map(component -> component.getName() + ":"
                        + component.getType().getSimpleName())
                .toList()).containsExactly(expected);
    }

    private static String readText(Path path) {
        try {
            return Files.readString(path);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private enum BindingFault {
        NONE,
        ASSET,
        VENUE,
        CURRENCY
    }

    private enum CandidateFault {
        BASIS(UnavailableReason.BASIS_MISMATCH),
        HORIZON(UnavailableReason.HORIZON_MISMATCH),
        ASSET(UnavailableReason.ASSET_MISMATCH),
        VENUE(UnavailableReason.PRIMARY_VENUE_MISMATCH),
        CURRENCY(UnavailableReason.CURRENCY_MISMATCH),
        SOURCE(UnavailableReason.SOURCE_MISMATCH),
        CATALOG(UnavailableReason.CATALOG_MISMATCH),
        SESSION(UnavailableReason.SESSION_WINDOW_MISMATCH),
        LOWER(UnavailableReason.LOWER_BOUND_MISMATCH),
        UPPER(UnavailableReason.UPPER_BOUND_MISMATCH),
        BOUNDARY(UnavailableReason.BOUNDARY_CONVENTION_MISMATCH),
        FIELD(UnavailableReason.PRICE_FIELD_MISMATCH),
        COMPLETENESS(UnavailableReason.WINDOW_COMPLETENESS_UNAVAILABLE),
        ADJUSTMENT(UnavailableReason.ADJUSTMENT_BASIS_MISMATCH),
        CONTINUITY(UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE);

        private final UnavailableReason reason;

        CandidateFault(UnavailableReason reason) {
            this.reason = reason;
        }
    }
}
