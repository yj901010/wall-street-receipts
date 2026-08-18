package com.wallstreetreceipts.api.domain.outcome;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.market.DataMode;

/**
 * An append-only evaluation of one immutable analyst-call forecast basis.
 */
public record CallOutcome(
        String outcomeId,
        String schemaVersion,
        String callId,
        OutcomeHorizon horizon,
        String basisRevisionId,
        String cancellationRevisionId,
        String snapshotId,
        String methodologyId,
        String methodologyVersion,
        String methodologyDefinitionHash,
        String inputFingerprint,
        int sequenceNumber,
        String supersedesOutcomeId,
        OutcomeEvaluationStatus evaluationStatus,
        OutcomeReasonCode reasonCode,
        Instant eventTime,
        Instant processingTime,
        BigDecimal assetReturn,
        BigDecimal benchmarkReturn,
        BigDecimal sectorReturn,
        BigDecimal alpha,
        BigDecimal sectorAlpha,
        BigDecimal mfe,
        BigDecimal mae,
        Boolean targetHit,
        Boolean directionalWin,
        BigDecimal targetError,
        boolean dataComplete,
        DataMode dataMode,
        Instant capturedAt,
        String provenanceId) {

    public CallOutcome {
        ScoringMethodology.requireIdentifier(outcomeId, "outcomeId");
        if (!"1.0.0".equals(schemaVersion)) {
            throw new IllegalArgumentException("schemaVersion must be 1.0.0");
        }
        ScoringMethodology.requireIdentifier(callId, "callId");
        Objects.requireNonNull(horizon, "horizon must not be null");
        if (basisRevisionId != null) {
            ScoringMethodology.requireIdentifier(basisRevisionId, "basisRevisionId");
        }
        if (cancellationRevisionId != null) {
            ScoringMethodology.requireIdentifier(cancellationRevisionId, "cancellationRevisionId");
        }
        if (snapshotId != null) {
            ScoringMethodology.requireIdentifier(snapshotId, "snapshotId");
        }
        ScoringMethodology.requireIdentifier(methodologyId, "methodologyId");
        ScoringMethodology.requireMethodologyVersion(methodologyVersion);
        ScoringMethodology.requireHash(methodologyDefinitionHash, "methodologyDefinitionHash");
        ScoringMethodology.requireHash(inputFingerprint, "inputFingerprint");
        Objects.requireNonNull(evaluationStatus, "evaluationStatus must not be null");
        Objects.requireNonNull(eventTime, "eventTime must not be null");
        Objects.requireNonNull(processingTime, "processingTime must not be null");
        Objects.requireNonNull(dataMode, "dataMode must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
        PersistentInstant.requireMicrosecondPrecision(eventTime, "eventTime");
        PersistentInstant.requireMicrosecondPrecision(processingTime, "processingTime");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        ScoringMethodology.requireIdentifier(provenanceId, "provenanceId");

        assetReturn = canonicalDecimal(assetReturn, "assetReturn");
        benchmarkReturn = canonicalDecimal(benchmarkReturn, "benchmarkReturn");
        sectorReturn = canonicalDecimal(sectorReturn, "sectorReturn");
        alpha = canonicalDecimal(alpha, "alpha");
        sectorAlpha = canonicalDecimal(sectorAlpha, "sectorAlpha");
        mfe = canonicalDecimal(mfe, "mfe");
        mae = canonicalDecimal(mae, "mae");
        targetError = canonicalDecimal(targetError, "targetError");

        if (sequenceNumber < 1) {
            throw new IllegalArgumentException("sequenceNumber must be at least 1");
        }
        if ((sequenceNumber == 1) != (supersedesOutcomeId == null)) {
            throw new IllegalArgumentException("only the first outcome may omit supersedesOutcomeId");
        }
        if (supersedesOutcomeId != null) {
            ScoringMethodology.requireIdentifier(supersedesOutcomeId, "supersedesOutcomeId");
        }
        if (processingTime.isBefore(eventTime)) {
            throw new IllegalArgumentException("processingTime must not precede eventTime");
        }
        if (capturedAt.isBefore(processingTime)) {
            throw new IllegalArgumentException("capturedAt must not precede processingTime");
        }
        validateEvaluationState(
                evaluationStatus, reasonCode, cancellationRevisionId, dataComplete,
                assetReturn, benchmarkReturn, sectorReturn, alpha, sectorAlpha, mfe, mae,
                targetHit, directionalWin, targetError);
        if (targetError != null && targetError.signum() < 0) {
            throw new IllegalArgumentException("targetError must not be negative");
        }
    }

    private static BigDecimal canonicalDecimal(BigDecimal value, String field) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        if (normalized.signum() == 0) {
            return BigDecimal.ZERO;
        }
        try {
            BigDecimal storageValue = normalized.setScale(12, RoundingMode.UNNECESSARY);
            if (storageValue.precision() > 38) {
                throw new IllegalArgumentException(field + " exceeds NUMERIC(38,12) precision");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " exceeds NUMERIC(38,12) scale", exception);
        }
        return normalized.scale() < 0 ? normalized.setScale(0) : normalized;
    }

    private static void validateEvaluationState(
            OutcomeEvaluationStatus status,
            OutcomeReasonCode reason,
            String cancellationRevisionId,
            boolean complete,
            BigDecimal assetReturn,
            BigDecimal benchmarkReturn,
            BigDecimal sectorReturn,
            BigDecimal alpha,
            BigDecimal sectorAlpha,
            BigDecimal mfe,
            BigDecimal mae,
            Boolean targetHit,
            Boolean directionalWin,
            BigDecimal targetError) {
        OutcomeReasonCode expectedReason = switch (status) {
            case PENDING -> OutcomeReasonCode.HORIZON_NOT_REACHED;
            case CALCULATED -> null;
            case INCOMPLETE -> OutcomeReasonCode.HORIZON_DATA_MISSING;
            case EXCLUDED -> OutcomeReasonCode.CALL_CANCELLED;
        };
        if (reason != expectedReason) {
            throw new IllegalArgumentException("reasonCode does not match evaluationStatus");
        }
        if ((status == OutcomeEvaluationStatus.CALCULATED) != complete) {
            throw new IllegalArgumentException("only CALCULATED outcomes may be data complete");
        }
        if ((status == OutcomeEvaluationStatus.EXCLUDED) != (cancellationRevisionId != null)) {
            throw new IllegalArgumentException(
                    "cancellationRevisionId is required exactly for EXCLUDED outcomes");
        }
        if (status == OutcomeEvaluationStatus.EXCLUDED
                && (assetReturn != null || benchmarkReturn != null || sectorReturn != null
                || alpha != null || sectorAlpha != null || mfe != null || mae != null
                || targetHit != null || directionalWin != null || targetError != null)) {
            throw new IllegalArgumentException("an EXCLUDED outcome must not contain calculated metrics");
        }
    }
}
