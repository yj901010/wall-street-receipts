package com.wallstreetreceipts.api.domain.outcome.targethitreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targethitorchestration
        .TargetHitOrchestrationResolution;

/** One complete supplied target-hit orchestration result to classify. */
public record TargetHitReadinessRequest(
        TargetHitReadinessPolicyVersion policyVersion,
        TargetHitOrchestrationResolution sourceResult) {

    private static final String REQUIRED_SOURCE_HASH =
            "b91bf68958e42ad003b80973c74f9acc2dad8e4629f6a1905798df98aa8b5348";

    public TargetHitReadinessRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != TargetHitReadinessPolicyVersion
                        .SUPPLIED_LEAF_TARGET_HIT_READINESS_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the target-hit readiness V1 policy");
        }
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        var sourceContext = switch (sourceResult) {
            case TargetHitOrchestrationResolution.Available available ->
                    available.context();
            case TargetHitOrchestrationResolution.Pending pending ->
                    pending.context();
            case TargetHitOrchestrationResolution.NotApplicable notApplicable ->
                    notApplicable.context();
            case TargetHitOrchestrationResolution.EligibilityUnavailable unavailable ->
                    unavailable.context();
            case TargetHitOrchestrationResolution.FavorableExtremeUnavailable unavailable ->
                    unavailable.context();
        };
        if (sourceContext.policyVersion()
                != TargetHitOrchestrationPolicyVersion
                        .POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1
                || !REQUIRED_SOURCE_HASH.equals(
                        sourceContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "sourceResult must use the required target-hit orchestration V1 policy");
        }
    }
}
