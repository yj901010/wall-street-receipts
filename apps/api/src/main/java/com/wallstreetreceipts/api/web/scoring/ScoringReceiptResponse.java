package com.wallstreetreceipts.api.web.scoring;

import java.time.*;
import java.util.*;
import com.wallstreetreceipts.api.application.scoring.ScoringReceiptService.Verified;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution;
import com.wallstreetreceipts.api.domain.outcome.assetreturn.AssetReturnResult;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration.DirectionalWinOrchestrationResolution;
import com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness.DirectionalWinReadinessResolution;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;
import com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness.TargetErrorReadinessResolution;

/** Explicit partial projection. Decimal strings are ratios, not percentage points. */
public record ScoringReceiptResponse(
        UUID receiptId, String callId, String snapshotId, String snapshotProvenanceId, String snapshotRole,
        String basisRevisionId, Integer basisRevisionSequence, String basisEventTimeUtc,
        String methodologyId, String methodologyVersion, String methodologyDefinitionHash,
        String inputFingerprint, String ledgerFingerprint, String evaluationAsOfUtc, String evaluationAsOfKst,
        String recordedAtUtc, String recordedAtKst, String dataMode, String scope, boolean dataComplete,
        String source, String termsProvenanceId, String horizon, Metric assetReturn, Metric directionalWin, Metric targetError) {
    public record Metric(String state, String decimalValue, Boolean booleanValue, List<String> reasons) {
        public Metric { reasons = List.copyOf(reasons); }
    }
    public static ScoringReceiptResponse from(Verified verified) {
        var row = verified.stored(); var receipt = verified.evaluation();
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
        return new ScoringReceiptResponse(row.receiptId(), row.callId(), row.snapshotId(), verified.snapshotProvenanceId(),
                "ORIGINAL_CALL_CONTEXT_ONLY_NOT_PRICE_SOURCE", row.basisRevisionId(), row.basisRevisionSequence(), receipt.input().horizon().basis().eventTime().toString(),
                row.methodologyId(), row.methodologyVersion(), row.methodologyDefinitionHash(), row.inputFingerprint(), row.ledgerFingerprint(),
                row.evaluationAsOf().toString(), kst(row.evaluationAsOf()), row.recordedAt().toString(), kst(row.recordedAt()),
                "DEMO", "PARTIAL_ENDPOINT", false, "PERSISTED_DEMO_INPUT_REPLAY", receipt.input().terms().provenanceId(),
                receipt.input().horizon().horizon().name(), asset, direction, target);
    }
    private static String kst(Instant time) { return time.atOffset(ZoneOffset.ofHours(9)).toString(); }
    private static Metric absent(String state, Object... reasons) {
        return new Metric(state, null, null, Arrays.stream(reasons).filter(Objects::nonNull).map(Object::toString).toList());
    }
}
