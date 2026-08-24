package com.wallstreetreceipts.api.domain.outcome.targethitorchestration;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.favorableextreme.FavorableExtremeResolution;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution;

/** Complete supplied leaf resolutions for one target-hit composition. */
public record TargetHitOrchestrationRequest(
        TargetHitOrchestrationPolicyVersion policyVersion,
        TargetEligibilityResolution eligibilityResolution,
        FavorableExtremeResolution favorableExtremeResolution) {

    private static final String REQUIRED_ELIGIBILITY_HASH =
            "a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465";
    private static final String REQUIRED_FAVORABLE_EXTREME_HASH =
            "e3a0e93030c8f09ae5398bf6df0f2e28eec14b0a31f5bea240fc78f2412c2463";

    public TargetHitOrchestrationRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != TargetHitOrchestrationPolicyVersion
                        .POINT_IN_TIME_TARGET_HIT_ORCHESTRATION_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the target-hit orchestration V1 policy");
        }
        Objects.requireNonNull(eligibilityResolution,
                "eligibilityResolution must not be null");
        var eligibilityContext = eligibilityContext(eligibilityResolution);
        if (eligibilityContext.policyVersion()
                != TargetEligibilityPolicyVersion
                        .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1
                || !REQUIRED_ELIGIBILITY_HASH.equals(
                        eligibilityContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "eligibilityResolution must use the required eligibility V1 policy");
        }

        if (eligibilityResolution
                instanceof TargetEligibilityResolution.ReadyForWindowEvidence ready) {
            Objects.requireNonNull(favorableExtremeResolution,
                    "ready eligibility requires a favorableExtremeResolution");
            var favorableContext = favorableContext(favorableExtremeResolution);
            if (favorableContext.policyVersion()
                    != FavorableExtremePolicyVersion
                            .POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1
                    || !REQUIRED_FAVORABLE_EXTREME_HASH.equals(
                            favorableContext.policyDefinitionHash())) {
                throw new IllegalArgumentException(
                        "favorableExtremeResolution must use the required V1 policy");
            }
            if (!favorableContext.readyEligibility().equals(ready)) {
                throw new IllegalArgumentException(
                        "favorableExtremeResolution must preserve the exact ready eligibility");
            }
        } else if (favorableExtremeResolution != null) {
            throw new IllegalArgumentException(
                    "non-ready eligibility must not carry a favorableExtremeResolution");
        }
    }

    private static TargetEligibilityResolution.ResolutionContext eligibilityContext(
            TargetEligibilityResolution resolution) {
        return switch (resolution) {
            case TargetEligibilityResolution.ReadyForWindowEvidence ready ->
                ready.context();
            case TargetEligibilityResolution.Pending pending -> pending.context();
            case TargetEligibilityResolution.NotApplicable notApplicable ->
                notApplicable.context();
            case TargetEligibilityResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }

    private static FavorableExtremeResolution.ResolutionContext favorableContext(
            FavorableExtremeResolution resolution) {
        return switch (resolution) {
            case FavorableExtremeResolution.Resolved resolved -> resolved.context();
            case FavorableExtremeResolution.Unavailable unavailable ->
                unavailable.context();
        };
    }
}
