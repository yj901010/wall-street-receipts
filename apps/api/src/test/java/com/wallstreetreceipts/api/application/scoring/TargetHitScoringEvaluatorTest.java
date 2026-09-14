package com.wallstreetreceipts.api.application.scoring;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.*;
import com.wallstreetreceipts.api.domain.outcome.observation.*;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.*;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration.*;
import com.wallstreetreceipts.api.domain.outcome.targethitreadiness.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import static com.wallstreetreceipts.api.application.scoring.TargetHitScoringFixture.source;
import static org.junit.jupiter.api.Assertions.*;

class TargetHitScoringEvaluatorTest {
    private final TargetHitScoringEvaluator evaluator = new TargetHitScoringEvaluator();

    @ParameterizedTest @CsvSource({
        "BULLISH,110,112,80,true,HIGH", "BULLISH,120,103,80,false,HIGH",
        "BULLISH,110,110,80,true,HIGH", "BULLISH,110,109.999999999999,80,false,HIGH",
        "BEARISH,170,200,168,true,LOW", "BEARISH,170,200,170,true,LOW",
        "BEARISH,170,200,170.000000000001,false,LOW",
        "STRONG_BULLISH,110,112,80,true,HIGH", "STRONG_BEARISH,170,200,168,true,LOW"
    })
    void exactComparisonAndPolarity(CallDirection side, String target, String high, String low,
            boolean hit, FavorableExtremeResolution.FavorableExtremeField field) {
        var input = TargetHitScoringFixture.input(EndpointScoringFixture.input(side, "100", "120", target), high, low);
        var receipt = evaluator.evaluate(input);
        var available = assertInstanceOf(TargetHitOrchestrationResolution.Available.class, source(receipt));
        assertEquals(hit, available.targetHitResult().targetHit());
        assertEquals(field, available.favorableExtremeResolution().favorableExtreme().field());
        var observation = input.windowCandidates().getFirst();
        assertSame(field == FavorableExtremeResolution.FavorableExtremeField.HIGH ? observation.windowHigh() : observation.windowLow(),
                available.favorableExtremeResolution().favorableExtreme().value());
        assertSame(observation, available.favorableExtremeResolution().evidence().knownCandidates().getFirst());
        assertSame(receipt.comparative().endpoint().horizon(),
                available.favorableExtremeResolution().context().readyEligibility().context().horizonResolution());
        assertFalse(receipt.dataComplete());
    }

    @Test void preservesWholeComparativeProfileAndSixthMeaningHasIndependentEvidence() {
        var input = TargetHitScoringFixture.input();
        var actual = evaluator.evaluate(input);
        var old = new ComparativeScoringEvaluator().evaluate(input.comparative());
        assertSame(input, actual.input());
        assertSame(input.comparative(), actual.comparative().input());
        assertEquals(old.inputFingerprint(), actual.comparative().inputFingerprint());
        assertEquals(old.endpoint().sharedAssetReturnAndDirectionalWin(), actual.comparative().endpoint().sharedAssetReturnAndDirectionalWin());
        assertEquals(old.endpoint().targetError(), actual.comparative().endpoint().targetError());
        assertEquals(old.benchmarkReturn(), actual.comparative().benchmarkReturn());
        assertEquals(old.sectorReturn(), actual.comparative().sectorReturn());
        assertNotEquals(old.inputFingerprint(), actual.inputFingerprint());
        assertEquals(TargetHitScoringMethodology.ID, actual.methodologyId());
        assertEquals(TargetHitScoringMethodology.VERSION, actual.methodologyVersion());
        assertEquals(TargetHitScoringMethodology.definitionHash(), actual.methodologyDefinitionHash());
        var missingPrices = edit(input.comparative().endpoint(), "endpointCandidates", List.of());
        missingPrices = edit(missingPrices, "basisCandidates", List.of());
        assertInstanceOf(TargetHitOrchestrationResolution.Available.class,
                source(evaluator.evaluate(TargetHitScoringFixture.withEndpoint(input, missingPrices))));
        var b = edit(input.comparative().benchmark(), "referenceIndexCandidates", List.of());
        var c = edit(input.comparative(), "benchmark", b);
        assertInstanceOf(TargetHitOrchestrationResolution.Available.class, source(evaluator.evaluate(edit(input, "comparative", c))));
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, 1})
    void microsecondMaturityBoundary(long micros) {
        var input = TargetHitScoringFixture.input();
        var e = edit(input.comparative().endpoint(), "evaluationAsOf", CLOSE.plusNanos(micros * 1000));
        var receipt = evaluator.evaluate(TargetHitScoringFixture.withEndpoint(input, e));
        if (micros < 0) {
            assertInstanceOf(TargetHitReadinessResolution.AwaitingEndpoint.class, receipt.targetHit());
            var pending = assertInstanceOf(TargetHitOrchestrationResolution.Pending.class, source(receipt));
            assertEquals(TargetEligibilityResolution.PendingReason.HORIZON_NOT_REACHED_AS_OF, pending.eligibilityResolution().reason());
        } else assertInstanceOf(TargetHitOrchestrationResolution.Available.class, source(receipt));
    }

    @ParameterizedTest @CsvSource({"NEUTRAL,false,NON_DIRECTIONAL", "BULLISH,true,TARGET_ABSENT", "NEUTRAL,true,TARGET_ABSENT_AND_NON_DIRECTIONAL"})
    void permanentNaSurvivesMissingScheduleAndConflictingWindow(CallDirection side, boolean absent,
            TargetEligibilityResolution.NotApplicableReason reason) {
        var e = EndpointScoringFixture.input(side, "100", "120", "150");
        if (absent) {
            e = edit(e, "targetEvidence", null);
            e = edit(e, "terms", edit(e.terms(), "targetDisposition", new BasisForecastTermsEvidence.TargetDisposition.Absent()));
        }
        e = edit(e, "horizon", edit(e.horizon(), "catalog", new TradingSessionCatalog("demo-calendar", "r1", List.of())));
        var input = TargetHitScoringFixture.input(e, "160", "80");
        input = edit(input, "windowCandidates", List.of(edit(input.windowCandidates().getFirst(), "assetId", "wrong-asset")));
        var receipt = evaluator.evaluate(input);
        assertTrue(receipt.comparative().benchmarkReturn().isEmpty());
        assertInstanceOf(TargetHitReadinessResolution.Settled.class, receipt.targetHit());
        assertEquals(reason, assertInstanceOf(TargetHitOrchestrationResolution.NotApplicable.class,
                source(receipt)).eligibilityResolution().reason());
    }

    @Test void unavailableSchedulePreservesTypedSourceAndNoWindowSelection() {
        var input = TargetHitScoringFixture.input();
        var e = input.comparative().endpoint();
        e = edit(e, "horizon", edit(e.horizon(), "catalog", new TradingSessionCatalog("demo-calendar", "r1", List.of())));
        var receipt = evaluator.evaluate(TargetHitScoringFixture.withEndpoint(input, e));
        var unavailable = assertInstanceOf(TargetHitOrchestrationResolution.EligibilityUnavailable.class, source(receipt));
        assertEquals(TargetEligibilityResolution.UnavailableReason.FIRST_ELIGIBLE_SESSION_MISSING, unavailable.eligibilityResolution().reason());
        assertSame(receipt.comparative().endpoint().horizon(), unavailable.eligibilityResolution().context().horizonResolution());
        assertInstanceOf(TargetHitReadinessResolution.EvidenceUnavailable.class, receipt.targetHit());
    }

    @Test void missingTargetIsNotAnAbsentTargetAndDoesNotEraseComparativeReturns() {
        var input = TargetHitScoringFixture.input();
        var e = edit(input.comparative().endpoint(), "targetEvidence", null);
        var receipt = evaluator.evaluate(TargetHitScoringFixture.withEndpoint(input, e));
        assertEquals(TargetEligibilityResolution.UnavailableReason.TARGET_EVIDENCE_NOT_KNOWN_AS_OF,
                assertInstanceOf(TargetHitOrchestrationResolution.EligibilityUnavailable.class, source(receipt)).eligibilityResolution().reason());
        assertEquals(evaluator.evaluate(input).comparative().benchmarkReturn(), receipt.comparative().benchmarkReturn());
        assertEquals(evaluator.evaluate(input).comparative().sectorReturn(), receipt.comparative().sectorReturn());
    }

    @Test void targetDateIsNotSilentlyReinterpreted() {
        var input = TargetHitScoringFixture.input();
        var e = input.comparative().endpoint();
        e = edit(e, "terms", edit(e.terms(), "targetDisposition",
                new BasisForecastTermsEvidence.TargetDisposition.Present(new BigDecimal("150"), USD, LocalDate.of(2026, 2, 1))));
        var receipt = evaluator.evaluate(TargetHitScoringFixture.withEndpoint(input, e));
        assertEquals(TargetEligibilityResolution.UnavailableReason.TARGET_DATE_SEMANTICS_UNSUPPORTED,
                assertInstanceOf(TargetHitOrchestrationResolution.EligibilityUnavailable.class, source(receipt)).eligibilityResolution().reason());
    }

    @Test void futureAndDuplicateCandidatesRetainOriginalSelectorRules() {
        var input = TargetHitScoringFixture.input();
        var observation = input.windowCandidates().getFirst();
        var future = edit(edit(observation, "assetId", "future-wrong-asset"), "capturedAt", AS_OF.plusNanos(1000));
        var supplied = edit(input, "windowCandidates", List.of(observation, future));
        var available = assertInstanceOf(TargetHitOrchestrationResolution.Available.class, source(evaluator.evaluate(supplied)));
        assertEquals(List.of(observation), available.favorableExtremeResolution().evidence().knownCandidates());
        assertNotEquals(evaluator.evaluate(input).inputFingerprint(), evaluator.evaluate(supplied).inputFingerprint());
        assertEquals(FavorableExtremeResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF,
                windowFailure(edit(input, "windowCandidates", List.of(future))).reason());
        assertEquals(FavorableExtremeResolution.UnavailableReason.OBSERVATION_AMBIGUOUS,
                windowFailure(edit(input, "windowCandidates", List.of(observation, observation))).reason());
        var poison = edit(observation, "assetId", "demo-wrong-asset");
        assertEquals(FavorableExtremeResolution.UnavailableReason.ASSET_MISMATCH,
                windowFailure(edit(input, "windowCandidates", List.of(observation, poison))).reason());
    }

    @Test void absentAndFutureBindingsRemainUnavailableNotMisses() {
        var input = TargetHitScoringFixture.input();
        assertEquals(FavorableExtremeResolution.UnavailableReason.BINDING_NOT_KNOWN_AS_OF,
                windowFailure(edit(input, "windowBinding", null)).reason());
        var future = edit(input.windowBinding(), "capturedAt", AS_OF.plusNanos(1000));
        assertEquals(FavorableExtremeResolution.UnavailableReason.BINDING_NOT_KNOWN_AS_OF,
                windowFailure(edit(input, "windowBinding", future)).reason());
        assertNull(windowFailure(edit(input, "windowBinding", future)).evidence().binding());
        assertEquals(FavorableExtremeResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF,
                windowFailure(edit(input, "windowCandidates", List.of())).reason());
    }

    @ParameterizedTest @MethodSource("invalidCandidates")
    void exactVisibleWindowFailuresAreNeverPrefiltered(String field, Object value, String reason) {
        var input = TargetHitScoringFixture.input();
        var wrong = edit(input.windowCandidates().getFirst(), field, value);
        var failure = windowFailure(edit(input, "windowCandidates", List.of(wrong)));
        assertEquals(reason, failure.reason().name());
        assertSame(wrong, failure.evidence().knownCandidates().getFirst());
    }
    static Stream<Arguments> invalidCandidates() {
        return Stream.of(
            Arguments.of("basis", new OutcomeBasis.Original("different-call", BASIS), "BASIS_MISMATCH"),
            Arguments.of("horizon", OutcomeHorizon.W1, "HORIZON_MISMATCH"),
            Arguments.of("assetId", "other", "ASSET_MISMATCH"),
            Arguments.of("venueId", "other", "PRIMARY_VENUE_MISMATCH"),
            Arguments.of("currency", Currency.getInstance("EUR"), "CURRENCY_MISMATCH"),
            Arguments.of("priceSourceRevision", "r2", "SOURCE_MISMATCH"),
            Arguments.of("catalogRevision", "r2", "CATALOG_MISMATCH"),
            Arguments.of("orderedSessionIds", List.of("another-session"), "SESSION_WINDOW_MISMATCH"),
            Arguments.of("lowerBound", BASIS.minusNanos(1000), "LOWER_BOUND_MISMATCH"),
            Arguments.of("upperBound", CLOSE.minusNanos(1000), "UPPER_BOUND_MISMATCH"),
            Arguments.of("lowerBoundType", FullWindowHighLowObservation.BoundaryType.INCLUSIVE, "BOUNDARY_CONVENTION_MISMATCH"),
            Arguments.of("upperBoundType", FullWindowHighLowObservation.BoundaryType.EXCLUSIVE, "BOUNDARY_CONVENTION_MISMATCH"),
            Arguments.of("priceField", FullWindowHighLowObservation.WindowPriceField.INDICATIVE_OR_OTHER, "PRICE_FIELD_MISMATCH"),
            Arguments.of("coverageCompleteness", FullWindowHighLowObservation.WindowCoverageCompleteness.PARTIAL_OR_UNKNOWN, "WINDOW_COMPLETENESS_UNAVAILABLE"),
            Arguments.of("adjustmentBasis", EndpointPriceAdjustmentBasis.UNADJUSTED_OR_OTHER, "ADJUSTMENT_BASIS_MISMATCH"),
            Arguments.of("corporateActionContinuity", CorporateActionContinuity.MERGER, "CORPORATE_ACTION_CONTINUITY_UNAVAILABLE"));
    }

    @Test void correctionBasisIsPreservedAndOldAggregateCannotBeReused() {
        var e = EndpointScoringFixture.input();
        var correction = new OutcomeBasis.Correction("demo-call", "demo-correction", BASIS);
        e = new EndpointScoringInput(e.dataMode(), edit(e.horizon(), "basis", correction), e.catalogEvidence(), e.binding(), e.evaluationAsOf(),
                edit(e.terms(), "basis", correction), e.endpointCandidates(),
                e.basisCandidates(),
                e.adjustmentCandidates(), edit(e.targetEvidence(), "basis", correction));
        var input = TargetHitScoringFixture.input(e, "160", "80");
        var receipt = evaluator.evaluate(input);
        var ready = assertInstanceOf(TargetHitOrchestrationResolution.Available.class, source(receipt))
                .favorableExtremeResolution().context().readyEligibility();
        assertEquals(correction, ready.evidence().termsEvidence().basis());
        assertEquals(FavorableExtremeResolution.UnavailableReason.BASIS_MISMATCH,
                windowFailure(edit(input, "windowCandidates", TargetHitScoringFixture.input().windowCandidates())).reason());
    }

    @Test void explicitMultiSessionUnionIsRequiredWithoutDailyBarFallback() {
        var e = EndpointScoringFixture.input();
        var sessions = java.util.stream.IntStream.range(0, 5).mapToObj(i -> new TradingSession(
                "demo-session-" + i, BASIS.minusSeconds(1800).plusSeconds(i * 86400L), CLOSE.plusSeconds(i * 86400L))).toList();
        var catalog = new TradingSessionCatalog("demo-calendar", "r1", sessions);
        e = edit(e, "horizon", new SessionCloseHorizonRequest(EndpointScoringMethodology.HORIZON,
                e.horizon().basis(), OutcomeHorizon.W1, catalog));
        var end = sessions.getLast().closesAt();
        e = edit(e, "evaluationAsOf", end);
        var input = TargetHitScoringFixture.input(e, "160", "80");
        var observation = input.windowCandidates().getFirst();
        observation = edit(observation, "capturedAt", end);
        observation = edit(observation, "availableAt", end);
        observation = edit(observation, "upperBound", end);
        observation = edit(observation, "orderedSessionIds", sessions.stream().map(TradingSession::sessionId).toList());
        input = edit(input, "windowCandidates", List.of(observation));
        assertInstanceOf(TargetHitOrchestrationResolution.Available.class, source(evaluator.evaluate(input)));
        assertEquals(FavorableExtremeResolution.UnavailableReason.SESSION_WINDOW_MISMATCH,
                windowFailure(edit(input, "windowCandidates", List.of(edit(observation, "orderedSessionIds",
                        observation.orderedSessionIds().subList(1, 5))))).reason());
    }

    @ParameterizedTest @CsvSource({"assetId,other,BINDING_ASSET_MISMATCH", "primaryVenueId,other,BINDING_PRIMARY_VENUE_MISMATCH"})
    void wrongWindowBindingIsAnExplicitFailure(String field, String value, String reason) {
        var input = TargetHitScoringFixture.input();
        assertEquals(reason, windowFailure(edit(input, "windowBinding", edit(input.windowBinding(), field, value))).reason().name());
        assertEquals(FavorableExtremeResolution.UnavailableReason.BINDING_CURRENCY_MISMATCH,
                windowFailure(edit(input, "windowBinding", edit(input.windowBinding(), "currency", Currency.getInstance("EUR")))).reason());
    }

    @Test void identityGateAndRepeatability() {
        var input = TargetHitScoringFixture.input();
        var first = evaluator.evaluate(input);
        assertEquals(first.targetHit(), evaluator.evaluate(input).targetHit());
        assertEquals(first.inputFingerprint(), evaluator.evaluate(input).inputFingerprint());
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(ComparativeScoringMethodology.ID,
                TargetHitScoringMethodology.VERSION, TargetHitScoringMethodology.definitionHash(), input));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(TargetHitScoringMethodology.ID,
                "2.0.0", TargetHitScoringMethodology.definitionHash(), input));
        assertThrows(IllegalArgumentException.class, () -> evaluator.evaluate(TargetHitScoringMethodology.ID,
                TargetHitScoringMethodology.VERSION, "0".repeat(64), input));
        assertThrows(NullPointerException.class, () -> evaluator.evaluate(null));
    }

    private FavorableExtremeResolution.Unavailable windowFailure(TargetHitScoringInput input) {
        var receipt = evaluator.evaluate(input);
        assertInstanceOf(TargetHitReadinessResolution.EvidenceUnavailable.class, receipt.targetHit());
        return assertInstanceOf(TargetHitOrchestrationResolution.FavorableExtremeUnavailable.class, source(receipt)).favorableExtremeResolution();
    }
}
