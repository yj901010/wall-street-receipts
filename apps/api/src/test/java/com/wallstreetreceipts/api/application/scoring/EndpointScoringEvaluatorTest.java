package com.wallstreetreceipts.api.application.scoring;

import static org.assertj.core.api.Assertions.*;
import static com.wallstreetreceipts.api.application.scoring.EndpointScoringFixture.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration.DirectionalWinOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness.DirectionalWinReadinessResolution;
import com.wallstreetreceipts.api.domain.outcome.horizon.*;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.TargetErrorReadinessResolution;

class EndpointScoringEvaluatorTest {
    private final EndpointScoringEvaluator evaluator = new EndpointScoringEvaluator();

    @ParameterizedTest @CsvSource({
        "BULLISH,100,120,150,0.2,true,0.25", "STRONG_BULLISH,100,80,100,-0.2,false,0.25",
        "BEARISH,100,80,100,-0.2,true,0.25", "STRONG_BEARISH,100,120,150,0.2,false,0.25",
        "BULLISH,100,100,100,0,false,0", "BEARISH,100,100,100,0,false,0",
        "BULLISH,3,4,5,0.333333333333,true,0.25", "BEARISH,3,1,2,-0.666666666667,true,1"})
    void rawInputsExecuteExistingCalculatorsWithGoldenDecimalResults(CallDirection direction, String basis, String endpoint,
            String target, String expectedReturn, boolean expectedWin, String expectedError) {
        var input = input(direction, basis, endpoint, target);
        var receipt = evaluator.evaluate(input);
        var shared = (DirectionalWinReadinessResolution.Settled) receipt.sharedAssetReturnAndDirectionalWin().orElseThrow();
        var source = (DirectionalWinOrchestrationResolution.Available) shared.sourceResolution();
        assertThat(source.assetReturnResult().assetReturn()).isEqualByComparingTo(expectedReturn);
        assertThat(source.directionalWinResult().directionalWin()).isEqualTo(expectedWin);
        var error = (TargetErrorReadinessResolution.Settled) receipt.targetError().orElseThrow();
        assertThat(((TargetErrorResult.Available) error.sourceResult()).targetError()).isEqualByComparingTo(expectedError);
        var pair = source.assetReturnResult().context().pricePairResolution();
        var pairContext = ((AssetReturnPricePairResolution.Resolved) pair).context();
        assertThat(((TargetErrorResult.Available) error.sourceResult()).context().endpointPriceResolution())
                .isSameAs(pairContext.endpointPriceResolution()); // One endpoint feeds both leaves.
        assertThat(receipt.input()).isSameAs(input);
        assertThat(receipt.dataComplete()).isFalse();
        assertThat(receipt.methodologyId()).isEqualTo("wsr-demo-endpoint-preview");
        assertThat(receipt.methodologyVersion()).isEqualTo("1.0.0");
    }

    @Test void neutralPreservesAvailableReturnWithoutManufacturingALoss() {
        var receipt = evaluator.evaluate(input(CallDirection.NEUTRAL, "100", "120", "150"));
        var shared = (DirectionalWinReadinessResolution.Settled) receipt.sharedAssetReturnAndDirectionalWin().orElseThrow();
        var source = (DirectionalWinOrchestrationResolution.NotApplicable) shared.sourceResolution();
        assertThat(((AssetReturnResult.Available) source.assetReturnResult()).assetReturn()).isEqualByComparingTo("0.2");
        assertThat(receipt.dataComplete()).isFalse();
    }

    @Test void endpointNotReachedPreservesBothExactAwaitingChains() {
        var receipt = evaluator.evaluate(edit(input(), "evaluationAsOf", CLOSE.minusSeconds(1)));
        assertThat(receipt.sharedAssetReturnAndDirectionalWin().orElseThrow()).isInstanceOf(DirectionalWinReadinessResolution.AwaitingEndpoint.class);
        assertThat(receipt.targetError().orElseThrow()).isInstanceOf(TargetErrorReadinessResolution.AwaitingEndpoint.class);
    }

    @ParameterizedTest @ValueSource(strings = {"endpointCandidates", "basisCandidates", "adjustmentCandidates"})
    void missingEvidenceNeverProducesAReturnOrDirectionalBoolean(String field) {
        var receipt = evaluator.evaluate(edit(input(), field, List.of()));
        var shared = (DirectionalWinReadinessResolution.EvidenceUnavailable) receipt.sharedAssetReturnAndDirectionalWin().orElseThrow();
        assertThat(shared.sourceResolution()).isInstanceOf(DirectionalWinOrchestrationResolution.AssetReturnUnavailable.class);
        assertThat(receipt.dataComplete()).isFalse();
    }

    @Test void lateCapturedEndpointIsNotUsedEvenIfItsObservationTimeIsOld() {
        var input = input();
        var late = edit(input.endpointCandidates().getFirst(), "capturedAt", AS_OF.plusSeconds(1));
        var receipt = evaluator.evaluate(edit(input, "endpointCandidates", List.of(late)));
        var error = (TargetErrorReadinessResolution.EvidenceUnavailable) receipt.targetError().orElseThrow();
        assertThat(((TargetErrorResult.Unavailable) error.sourceResult()).endpointReason()).isEqualTo(EndpointPriceResolution.UnavailableReason.OBSERVATION_MISSING_AS_OF);
    }

    @Test void ambiguousPricesAndMissingTargetStayTypedUnavailable() {
        var input = input();
        var conflicting = edit(input.endpointCandidates().getFirst(), "observationId", "another-endpoint");
        var receipt = evaluator.evaluate(edit(input, "endpointCandidates", List.of(input.endpointCandidates().getFirst(), conflicting)));
        assertThat(receipt.sharedAssetReturnAndDirectionalWin().orElseThrow()).isInstanceOf(DirectionalWinReadinessResolution.EvidenceUnavailable.class);
        var missing = evaluator.evaluate(edit(input, "targetEvidence", null));
        assertThat(missing.sharedAssetReturnAndDirectionalWin().orElseThrow()).isInstanceOf(DirectionalWinReadinessResolution.Settled.class);
        var error = (TargetErrorReadinessResolution.EvidenceUnavailable) missing.targetError().orElseThrow();
        assertThat(((TargetErrorResult.Unavailable) error.sourceResult()).reason()).isEqualTo(TargetErrorResult.UnavailableReason.TARGET_MISSING_AS_OF);
    }

    @Test void incompleteScheduleIsPreservedWithoutInventingAnEndpointOrMetric() {
        var input = input();
        var receipt = evaluator.evaluate(edit(input, "horizon", edit(input.horizon(), "horizon", OutcomeHorizon.W1)));
        assertThat(receipt.horizon()).isInstanceOf(SessionCloseHorizonResolution.Incomplete.class);
        assertThat(receipt.sharedAssetReturnAndDirectionalWin()).isEmpty();
        assertThat(receipt.targetError()).isEmpty();
        assertThat(receipt.dataComplete()).isFalse();
    }

    @ParameterizedTest @EnumSource(value = DataMode.class, names = "DEMO", mode = EnumSource.Mode.EXCLUDE)
    void realDataModesCannotEnterThePreview(DataMode mode) {
        assertThatThrownBy(() -> edit(input(), "dataMode", mode)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void sourceTermsMustMatchAndNoImplicitTargetConversionIsPermitted() {
        var input = input();
        assertThatThrownBy(() -> edit(input, "terms", edit(input.terms(), "assetId", "other-asset"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edit(input, "terms", edit(input.terms(), "basis", new OutcomeBasis.Original("other-call", BASIS)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edit(input, "terms", edit(input.terms(), "capturedAt", AS_OF.plusSeconds(1)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edit(input, "targetEvidence", edit(input.targetEvidence(), "target", new BigDecimal("151")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edit(input, "terms", edit(input.terms(), "targetDisposition", new BasisForecastTermsEvidence.TargetDisposition.Absent()))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> edit(input, "evaluationAsOf", AS_OF.plusNanos(1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void syntheticCorrectionBasisIsIndependentAndPreservedNotLedgerAuthenticated() {
        var original = input();
        var basis = new OutcomeBasis.Correction("demo-call", "demo-correction", BASIS);
        var corrected = new EndpointScoringInput(DataMode.DEMO, edit(original.horizon(), "basis", basis), original.catalogEvidence(),
                original.binding(), AS_OF, edit(original.terms(), "basis", basis), original.endpointCandidates(),
                List.of(edit(original.basisCandidates().getFirst(), "basis", basis)),
                List.of(edit(original.adjustmentCandidates().getFirst(), "basis", basis)), edit(original.targetEvidence(), "basis", basis));
        var receipt = evaluator.evaluate(corrected);
        assertThat(receipt.inputFingerprint()).isNotEqualTo(evaluator.evaluate(original).inputFingerprint());
        assertThat(receipt.input().terms().basis()).isEqualTo(basis);
        assertThat(receipt.sharedAssetReturnAndDirectionalWin().orElseThrow()).isInstanceOf(DirectionalWinReadinessResolution.Settled.class);
    }

    @Test void inputListsAndReceiptsAreImmutableAndReplayIsThreadSafe() throws Exception {
        var input = input();
        var candidates = new ArrayList<>(input.endpointCandidates());
        var copied = edit(input, "endpointCandidates", candidates);
        candidates.clear();
        assertThat(copied.endpointCandidates()).hasSize(1);
        assertThatThrownBy(() -> copied.endpointCandidates().clear()).isInstanceOf(UnsupportedOperationException.class);
        var first = evaluator.evaluate(copied);
        try (var workers = Executors.newFixedThreadPool(4)) {
            var tasks = new ArrayList<java.util.concurrent.Future<EndpointScoringEvaluator.Receipt>>();
            for (int i = 0; i < 24; i++) tasks.add(workers.submit(() -> evaluator.evaluate(copied)));
            for (var task : tasks) {
                var replay = task.get();
                assertThat(replay.inputFingerprint()).isEqualTo(first.inputFingerprint());
                assertThat(replay.sharedAssetReturnAndDirectionalWin()).isEqualTo(first.sharedAssetReturnAndDirectionalWin());
                assertThat(replay.targetError()).isEqualTo(first.targetError());
            }
        }
    }

    @Test void fingerprintIsStableAcrossDecimalScaleLocaleAndTimezone() {
        var expected = evaluator.evaluate(input()).inputFingerprint();
        assertThat(evaluator.evaluate(input(CallDirection.BULLISH, "100.000", "120.00", "150.0")).inputFingerprint()).isEqualTo(expected);
        var locale = Locale.getDefault(); var zone = TimeZone.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR")); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
            assertThat(evaluator.evaluate(input()).inputFingerprint()).isEqualTo(expected);
        } finally { Locale.setDefault(locale); TimeZone.setDefault(zone); }
    }
}
