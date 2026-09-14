package com.wallstreetreceipts.api.web.scoring;

import java.time.*;
import java.util.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkassignment.BenchmarkAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturn.BenchmarkReturnResult;
import com.wallstreetreceipts.api.domain.outcome.benchmarkreturnreadiness.BenchmarkReturnReadinessResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorassignment.SectorAssignmentResolution;
import com.wallstreetreceipts.api.domain.outcome.sectorreferencepair.*;
import com.wallstreetreceipts.api.domain.outcome.sectorreturn.SectorReturnResult;
import com.wallstreetreceipts.api.domain.outcome.sectorreturnreadiness.SectorReturnReadinessResolution;
import com.wallstreetreceipts.api.application.scoring.ComparativeScoringReceiptService.Verified;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration.DirectionalWinOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness.DirectionalWinReadinessResolution;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.TargetErrorReadinessResolution;

/** Explicit partial projection. Decimal strings are ratios, not percentage points. */
public record ComparativeScoringReceiptResponse(
        UUID receiptId, String callId, String snapshotId, String snapshotProvenanceId, String snapshotRole,
        String basisRevisionId, Integer basisRevisionSequence, String basisEventTimeUtc,
        String methodologyId, String methodologyVersion, String methodologyDefinitionHash,
        String inputFingerprint, String ledgerFingerprint, String evaluationAsOfUtc, String evaluationAsOfKst,
        String recordedAtUtc, String recordedAtKst, String dataMode, String scope, boolean dataComplete,
        String source, String termsProvenanceId, String horizon, Metric assetReturn, Metric directionalWin, Metric targetError,
        Metric benchmarkReturn, Metric sectorReturn, ReferenceEvidence benchmarkEvidence, ReferenceEvidence sectorEvidence) {
    public record Metric(String state, String decimalValue, Boolean booleanValue, List<String> reasons) {
        public Metric { reasons = List.copyOf(reasons); }
    }
    public static ComparativeScoringReceiptResponse from(Verified verified) {
        var row = verified.stored(); var receipt = verified.evaluation().endpoint();
        Metric asset, direction, target;
        if (receipt.horizon() instanceof SessionCloseHorizonResolution.Incomplete incomplete) {
            asset = direction = target = absent("UNAVAILABLE", incomplete.reason());
        } else {
            var shared = receipt.sharedAssetReturnAndDirectionalWin().orElseThrow();
            var orchestration = switch (shared) {
                case DirectionalWinReadinessResolution.Settled s -> s.sourceResolution();
                case DirectionalWinReadinessResolution.AwaitingEndpoint s -> s.sourceResolution();
                case DirectionalWinReadinessResolution.EvidenceUnavailable s -> s.sourceResolution();
            };
            var assetResult = switch (orchestration) {
                case DirectionalWinOrchestrationResolution.Available s -> s.assetReturnResult();
                case DirectionalWinOrchestrationResolution.NotApplicable s -> s.assetReturnResult();
                case DirectionalWinOrchestrationResolution.AssetReturnUnavailable s -> s.assetReturnResult();
            };
            asset = switch (assetResult) {
                case AssetReturnResult.Available a -> new Metric("AVAILABLE", a.assetReturn().toPlainString(), null, List.of());
                case AssetReturnResult.Unavailable u -> absent(shared instanceof DirectionalWinReadinessResolution.AwaitingEndpoint ? "PENDING" : "UNAVAILABLE",
                        u.reason(), u.pricePairReason(), u.context().pricePairResolution() instanceof AssetReturnPricePairResolution.Unavailable p ? p.endpointReason() : null);
            };
            direction = switch (orchestration) {
                case DirectionalWinOrchestrationResolution.Available a -> new Metric("AVAILABLE", null, a.directionalWinResult().directionalWin(), List.of());
                case DirectionalWinOrchestrationResolution.NotApplicable ignored -> absent("NOT_APPLICABLE", "NEUTRAL_DIRECTION");
                case DirectionalWinOrchestrationResolution.AssetReturnUnavailable ignored -> new Metric(asset.state(), null, null, asset.reasons());
            };
            var readiness = receipt.targetError().orElseThrow();
            var targetResult = switch (readiness) {
                case TargetErrorReadinessResolution.Settled s -> s.sourceResult();
                case TargetErrorReadinessResolution.AwaitingEndpoint s -> s.sourceResult();
                case TargetErrorReadinessResolution.EvidenceUnavailable s -> s.sourceResult();
            };
            target = switch (targetResult) {
                case TargetErrorResult.Available a -> new Metric("AVAILABLE", a.targetError().toPlainString(), null, List.of());
                case TargetErrorResult.Unavailable u -> absent(readiness instanceof TargetErrorReadinessResolution.AwaitingEndpoint ? "PENDING" : "UNAVAILABLE", u.reason(), u.endpointReason());
            };
        }
        return new ComparativeScoringReceiptResponse(row.receiptId(), row.callId(), row.snapshotId(), verified.snapshotProvenanceId(),
                "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE", row.basisRevisionId(), row.basisRevisionSequence(), receipt.input().horizon().basis().eventTime().toString(),
                row.methodologyId(), row.methodologyVersion(), row.methodologyDefinitionHash(), row.inputFingerprint(), row.ledgerFingerprint(),
                row.evaluationAsOf().toString(), kst(row.evaluationAsOf()), row.recordedAt().toString(), kst(row.recordedAt()),
                "DEMO", "PARTIAL_COMPARATIVE", false, "PERSISTED_DEMO_INPUT_REPLAY", receipt.input().terms().provenanceId(),
                receipt.input().horizon().horizon().name(), asset, direction, target,
                verified.evaluation().benchmarkReturn().map(ComparativeScoringReceiptResponse::benchmark).orElse(asset),
                verified.evaluation().sectorReturn().map(ComparativeScoringReceiptResponse::sector).orElse(asset),
                verified.evaluation().benchmarkReturn().map(ComparativeScoringReceiptResponse::benchmarkEvidence).orElse(null),
                verified.evaluation().sectorReturn().map(ComparativeScoringReceiptResponse::sectorEvidence).orElse(null));
    }
    private static String kst(Instant time) { return time.atOffset(ZoneOffset.ofHours(9)).toString(); }
    private static Metric absent(String state, Object... reasons) {
        return new Metric(state, null, null, Arrays.stream(reasons).filter(Objects::nonNull).map(Object::toString).toList());
    }

    /** Only selected reference evidence, never rejected/future raw input candidates. */
    public record ReferenceEvidence(String bindingId, String bindingProvenanceId, String sourceBindingRole, String sourceBindingId,
            String referenceAssetId, String providerId, String indexId, String definitionRevision, String currency,
            String calendarId, String calendarRevision, Level basisLevel, Level endpointLevel,
            String continuityEvidenceId, String continuityProvenanceId) {}
    public record Level(String observationId, String providerEventId, String value, String observedAtUtc,
            String sourceId, String sourceRevision, String provenanceId) {}

    private static BenchmarkReturnResult benchmarkResult(BenchmarkReturnReadinessResolution readiness) {
        return switch (readiness) {
            case BenchmarkReturnReadinessResolution.Settled r -> r.sourceResult();
            case BenchmarkReturnReadinessResolution.AwaitingEndpoint r -> r.sourceResult();
            case BenchmarkReturnReadinessResolution.EvidenceUnavailable r -> r.sourceResult();
        };
    }
    private static Metric benchmark(BenchmarkReturnReadinessResolution readiness) {
        return switch (benchmarkResult(readiness)) {
            case BenchmarkReturnResult.Available r -> new Metric("AVAILABLE", r.benchmarkReturn().toPlainString(), null, List.of());
            case BenchmarkReturnResult.NotApplicable r -> absent("NOT_APPLICABLE",
                    ((BenchmarkAssignmentResolution.NotApplicable) ((BenchmarkReferenceLevelPairResolution.NotApplicable)
                            r.context().referenceLevelPairResolution()).context().assignmentResolution()).reason());
            case BenchmarkReturnResult.AssignmentUnavailable r -> absent("UNAVAILABLE", "ASSIGNMENT_UNAVAILABLE",
                    ((BenchmarkAssignmentResolution.Unavailable) ((BenchmarkReferenceLevelPairResolution.AssignmentUnavailable)
                            r.context().referenceLevelPairResolution()).context().assignmentResolution()).reason());
            case BenchmarkReturnResult.EndpointAnchorUnavailable r -> absent("UNAVAILABLE", "ENDPOINT_ANCHOR_UNAVAILABLE",
                    ((BenchmarkReferenceLevelPairResolution.EndpointAnchorUnavailable) r.context().referenceLevelPairResolution()).reason());
            case BenchmarkReturnResult.EvidenceUnavailable r -> absent(
                    readiness instanceof BenchmarkReturnReadinessResolution.AwaitingEndpoint ? "PENDING" : "UNAVAILABLE",
                    ((BenchmarkReferenceLevelPairResolution.EvidenceUnavailable) r.context().referenceLevelPairResolution()).reason());
            case BenchmarkReturnResult.OutputUnavailable r -> absent("UNAVAILABLE", r.reason());
        };
    }
    private static ReferenceEvidence benchmarkEvidence(BenchmarkReturnReadinessResolution readiness) {
        var pair = switch (benchmarkResult(readiness)) {
            case BenchmarkReturnResult.Available r -> r.context().referenceLevelPairResolution();
            case BenchmarkReturnResult.OutputUnavailable r -> r.context().referenceLevelPairResolution();
            default -> null;
        };
        if (!(pair instanceof BenchmarkReferenceLevelPairResolution.Resolved resolved)) return null;
        var ref = resolved.referenceIndexEvidence();
        var start = resolved.basisLevelObservation(); var end = resolved.endpointLevelObservation();
        var continuity = resolved.divisorContinuityEvidence();
        return new ReferenceEvidence(ref.referenceIndexEvidenceId(), ref.provenanceId(), "BENCHMARK_ASSIGNMENT", ref.assignmentEvidenceId(),
                ref.benchmarkAssetId(), ref.referenceProviderId(), ref.referenceIndexId(), ref.referenceIndexDefinitionRevision(),
                ref.currency().getCurrencyCode(), ref.calendarId(), ref.calendarRevision(),
                new Level(start.observationId(), start.providerEventId(), start.level().toPlainString(), start.observedAt().toString(),
                        start.levelSourceId(), start.levelSourceRevision(), start.provenanceId()),
                new Level(end.observationId(), end.providerEventId(), end.level().toPlainString(), end.observedAt().toString(),
                        end.levelSourceId(), end.levelSourceRevision(), end.provenanceId()),
                continuity.continuityEvidenceId(), continuity.provenanceId());
    }

    private static SectorReturnResult sectorResult(SectorReturnReadinessResolution readiness) {
        return switch (readiness) {
            case SectorReturnReadinessResolution.Settled r -> r.sourceResult();
            case SectorReturnReadinessResolution.AwaitingEndpoint r -> r.sourceResult();
            case SectorReturnReadinessResolution.EvidenceUnavailable r -> r.sourceResult();
        };
    }
    private static Metric sector(SectorReturnReadinessResolution readiness) {
        return switch (sectorResult(readiness)) {
            case SectorReturnResult.Available r -> new Metric("AVAILABLE", r.sectorReturn().toPlainString(), null, List.of());
            case SectorReturnResult.NotApplicable r -> absent("NOT_APPLICABLE",
                    ((SectorAssignmentResolution.NotApplicable) ((SectorReferenceLevelPairResolution.NotApplicable)
                            r.context().referenceLevelPairResolution()).context().assignmentResolution()).reason());
            case SectorReturnResult.AssignmentUnavailable r -> absent("UNAVAILABLE", "ASSIGNMENT_UNAVAILABLE",
                    ((SectorAssignmentResolution.Unavailable) ((SectorReferenceLevelPairResolution.AssignmentUnavailable)
                            r.context().referenceLevelPairResolution()).context().assignmentResolution()).reason());
            case SectorReturnResult.EndpointAnchorUnavailable r -> absent("UNAVAILABLE", "ENDPOINT_ANCHOR_UNAVAILABLE",
                    ((SectorReferenceLevelPairResolution.EndpointAnchorUnavailable) r.context().referenceLevelPairResolution()).reason());
            case SectorReturnResult.EvidenceUnavailable r -> absent(
                    readiness instanceof SectorReturnReadinessResolution.AwaitingEndpoint ? "PENDING" : "UNAVAILABLE",
                    ((SectorReferenceLevelPairResolution.EvidenceUnavailable) r.context().referenceLevelPairResolution()).reason());
            case SectorReturnResult.OutputUnavailable r -> absent("UNAVAILABLE", r.reason());
        };
    }
    private static ReferenceEvidence sectorEvidence(SectorReturnReadinessResolution readiness) {
        var pair = switch (sectorResult(readiness)) {
            case SectorReturnResult.Available r -> r.context().referenceLevelPairResolution();
            case SectorReturnResult.OutputUnavailable r -> r.context().referenceLevelPairResolution();
            default -> null;
        };
        if (!(pair instanceof SectorReferenceLevelPairResolution.Resolved resolved)) return null;
        var ref = resolved.referenceIndexEvidence();
        var start = resolved.basisLevelObservation(); var end = resolved.endpointLevelObservation();
        var continuity = resolved.divisorContinuityEvidence();
        return new ReferenceEvidence(ref.referenceIndexEvidenceId(), ref.provenanceId(), "SECTOR_MAPPING", ref.mappingEvidenceId(),
                ref.referenceAssetId(), ref.referenceProviderId(), ref.referenceIndexId(), ref.referenceIndexDefinitionRevision(),
                ref.currency().getCurrencyCode(), ref.calendarId(), ref.calendarRevision(),
                new Level(start.observationId(), start.providerEventId(), start.level().toPlainString(), start.observedAt().toString(),
                        start.levelSourceId(), start.levelSourceRevision(), start.provenanceId()),
                new Level(end.observationId(), end.providerEventId(), end.level().toPlainString(), end.observedAt().toString(),
                        end.levelSourceId(), end.levelSourceRevision(), end.provenanceId()),
                continuity.continuityEvidenceId(), continuity.provenanceId());
    }
}
