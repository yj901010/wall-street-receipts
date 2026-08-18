package com.wallstreetreceipts.api.domain.outcome;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.market.DataMode;

class CallOutcomeDomainTest {

    private static final String DEFINITION_HASH = "a".repeat(64);
    private static final String INPUT_HASH = "b".repeat(64);
    private static final Instant EVENT_TIME = Instant.parse("2026-08-11T20:00:00Z");

    @Test
    void calculatedOutcomeIsTheOnlyCompleteStateAndHasNoReason() {
        CallOutcome calculated = outcome(
                OutcomeEvaluationStatus.CALCULATED, null, true, null, null, null);

        assertThat(calculated.dataComplete()).isTrue();
        assertThat(calculated.reasonCode()).isNull();
        assertThatThrownBy(() -> outcome(
                OutcomeEvaluationStatus.CALCULATED,
                OutcomeReasonCode.HORIZON_DATA_MISSING,
                true,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode");
    }

    @Test
    void nonCalculatedStatesHaveExactReasonAndAreIncomplete() {
        assertThat(outcome(
                OutcomeEvaluationStatus.PENDING,
                OutcomeReasonCode.HORIZON_NOT_REACHED,
                false,
                null,
                null,
                null).reasonCode()).isEqualTo(OutcomeReasonCode.HORIZON_NOT_REACHED);
        assertThat(outcome(
                OutcomeEvaluationStatus.INCOMPLETE,
                OutcomeReasonCode.HORIZON_DATA_MISSING,
                false,
                null,
                null,
                null).reasonCode()).isEqualTo(OutcomeReasonCode.HORIZON_DATA_MISSING);
        assertThat(outcome(
                OutcomeEvaluationStatus.EXCLUDED,
                OutcomeReasonCode.CALL_CANCELLED,
                false,
                null,
                null,
                null).reasonCode()).isEqualTo(OutcomeReasonCode.CALL_CANCELLED);

        assertThatThrownBy(() -> outcome(
                OutcomeEvaluationStatus.PENDING,
                OutcomeReasonCode.HORIZON_DATA_MISSING,
                false,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reasonCode");
        assertThatThrownBy(() -> outcome(
                OutcomeEvaluationStatus.INCOMPLETE,
                OutcomeReasonCode.HORIZON_DATA_MISSING,
                true,
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("data complete");
    }

    @Test
    void excludedOutcomeCannotCarryMetricsOrBooleanResults() {
        assertThatThrownBy(() -> outcome(
                OutcomeEvaluationStatus.EXCLUDED,
                OutcomeReasonCode.CALL_CANCELLED,
                false,
                new BigDecimal("0.1"),
                true,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not contain calculated metrics");
    }

    @Test
    void cancellationEvidenceIsRequiredExactlyForExcludedOutcomes() {
        CallOutcome excluded = outcome(
                OutcomeEvaluationStatus.EXCLUDED,
                OutcomeReasonCode.CALL_CANCELLED,
                false,
                null,
                null,
                null);
        assertThat(excluded.cancellationRevisionId()).isEqualTo("demo-call-revision-002");

        assertThatThrownBy(() -> copyWithCancellation(excluded, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancellationRevisionId");
        assertThatThrownBy(() -> copyWithCancellation(
                outcome(OutcomeEvaluationStatus.PENDING, OutcomeReasonCode.HORIZON_NOT_REACHED,
                        false, null, null, null),
                "demo-call-revision-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cancellationRevisionId");
    }

    @Test
    void decimalMetricsAreCanonicalAndExactlyRepresentableAsNumeric38Scale12() {
        CallOutcome normalized = outcome(
                OutcomeEvaluationStatus.CALCULATED, null, true,
                new BigDecimal("0.1234567890120"), null,
                new BigDecimal("99999999999999999999999999.999999999999"));
        assertThat(normalized.assetReturn()).isEqualByComparingTo("0.123456789012");
        assertThat(normalized.assetReturn().scale()).isEqualTo(12);

        assertThatThrownBy(() -> outcome(
                OutcomeEvaluationStatus.CALCULATED, null, true,
                new BigDecimal("0.1234567890123"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scale");
        assertThatThrownBy(() -> outcome(
                OutcomeEvaluationStatus.CALCULATED, null, true,
                new BigDecimal("999999999999999999999999999"), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("precision");
    }

    @Test
    void persistedOutcomeAndMethodologyTimesRejectSubMicrosecondPrecision() {
        CallOutcome outcome = outcome(
                OutcomeEvaluationStatus.PENDING, OutcomeReasonCode.HORIZON_NOT_REACHED,
                false, null, null, null);
        assertThatThrownBy(() -> copy(
                outcome, INPUT_HASH, 1, null, EVENT_TIME.plusNanos(100),
                EVENT_TIME.plusSeconds(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");

        assertThatThrownBy(() -> new ScoringMethodology(
                "standard-call-outcome", "1.0.0", "1.0.0", DEFINITION_HASH,
                MethodologyStatus.MODEL_ONLY, EVENT_TIME.plusNanos(100), DataMode.DEMO,
                EVENT_TIME.plusSeconds(1), "test-provenance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
    }

    @Test
    void hashesTargetErrorTimeAndRootLineageAreStrict() {
        assertThatThrownBy(() -> copy(
                outcome(OutcomeEvaluationStatus.CALCULATED, null, true, null, null, null),
                "short", 1, null, EVENT_TIME, EVENT_TIME.plusSeconds(1), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("inputFingerprint");
        assertThatThrownBy(() -> copy(
                outcome(OutcomeEvaluationStatus.CALCULATED, null, true, null, null, null),
                INPUT_HASH, 2, null, EVENT_TIME, EVENT_TIME.plusSeconds(1), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first outcome");
        assertThatThrownBy(() -> copy(
                outcome(OutcomeEvaluationStatus.CALCULATED, null, true, null, null, null),
                INPUT_HASH, 1, null, EVENT_TIME.plusSeconds(2), EVENT_TIME.plusSeconds(1), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTime");
        assertThatThrownBy(() -> copy(
                outcome(OutcomeEvaluationStatus.CALCULATED, null, true, null, null, null),
                INPUT_HASH, 1, null, EVENT_TIME, EVENT_TIME.plusSeconds(1), new BigDecimal("-0.01")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("targetError");
    }

    @Test
    void methodologyDefinitionIsVersionedAndCapturedAfterItsEffectiveTime() {
        ScoringMethodology methodology = new ScoringMethodology(
                "standard-call-outcome", "1.0.0", "1.0.0", DEFINITION_HASH,
                MethodologyStatus.MODEL_ONLY, EVENT_TIME, DataMode.DEMO,
                EVENT_TIME.plusSeconds(1), "test-provenance");
        assertThat(methodology.definitionHash()).isEqualTo(DEFINITION_HASH);

        assertThatThrownBy(() -> new ScoringMethodology(
                "standard-call-outcome", "invalid:version", "1.0.0", DEFINITION_HASH,
                MethodologyStatus.MODEL_ONLY, EVENT_TIME, DataMode.DEMO,
                EVENT_TIME.plusSeconds(1), "test-provenance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("methodologyVersion");
        assertThatThrownBy(() -> new ScoringMethodology(
                "standard-call-outcome", "1.0.0", "1.0.0", DEFINITION_HASH,
                MethodologyStatus.MODEL_ONLY, EVENT_TIME.plusSeconds(1), DataMode.DEMO,
                EVENT_TIME, "test-provenance"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt");
    }

    private static CallOutcome outcome(
            OutcomeEvaluationStatus status,
            OutcomeReasonCode reason,
            boolean complete,
            BigDecimal assetReturn,
            Boolean targetHit,
            BigDecimal targetError) {
        return new CallOutcome(
                "test-outcome", "1.0.0", "demo-call-001", OutcomeHorizon.D1,
                null,
                status == OutcomeEvaluationStatus.EXCLUDED ? "demo-call-revision-002" : null,
                "market-snapshot-demo-001", "standard-call-outcome", "1.0.0",
                DEFINITION_HASH, INPUT_HASH, 1, null, status, reason,
                EVENT_TIME, EVENT_TIME.plusSeconds(1), assetReturn, null, null, null, null,
                null, null, targetHit, null, targetError, complete, DataMode.DEMO,
                EVENT_TIME.plusSeconds(2), "test-provenance");
    }

    private static CallOutcome copy(
            CallOutcome source,
            String inputFingerprint,
            int sequenceNumber,
            String supersedesOutcomeId,
            Instant eventTime,
            Instant processingTime,
            BigDecimal targetError) {
        return new CallOutcome(
                source.outcomeId(), source.schemaVersion(), source.callId(), source.horizon(),
                source.basisRevisionId(), source.cancellationRevisionId(), source.snapshotId(), source.methodologyId(),
                source.methodologyVersion(), source.methodologyDefinitionHash(), inputFingerprint,
                sequenceNumber, supersedesOutcomeId, source.evaluationStatus(), source.reasonCode(),
                eventTime, processingTime, source.assetReturn(), source.benchmarkReturn(),
                source.sectorReturn(), source.alpha(), source.sectorAlpha(), source.mfe(), source.mae(),
                source.targetHit(), source.directionalWin(), targetError, source.dataComplete(), source.dataMode(),
                processingTime.plusSeconds(1), source.provenanceId());
    }

    private static CallOutcome copyWithCancellation(CallOutcome source, String cancellationRevisionId) {
        return new CallOutcome(
                source.outcomeId(), source.schemaVersion(), source.callId(), source.horizon(),
                source.basisRevisionId(), cancellationRevisionId, source.snapshotId(), source.methodologyId(),
                source.methodologyVersion(), source.methodologyDefinitionHash(), source.inputFingerprint(),
                source.sequenceNumber(), source.supersedesOutcomeId(), source.evaluationStatus(), source.reasonCode(),
                source.eventTime(), source.processingTime(), source.assetReturn(), source.benchmarkReturn(),
                source.sectorReturn(), source.alpha(), source.sectorAlpha(), source.mfe(), source.mae(),
                source.targetHit(), source.directionalWin(), source.targetError(), source.dataComplete(),
                source.dataMode(), source.capturedAt(), source.provenanceId());
    }
}
