package com.wallstreetreceipts.api.domain.outcome.favorableextreme;

import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.TargetEligibilityResolution;

/** Explicit evidence for one PIT favorable-extreme selection. */
public record FavorableExtremeRequest(
        FavorableExtremePolicyVersion policyVersion,
        TargetEligibilityResolution.ReadyForWindowEvidence readyEligibility,
        WindowPriceBinding binding,
        List<FullWindowHighLowObservation> candidates) {

    static final String REQUIRED_ELIGIBILITY_HASH =
            "a6b4c9f4e4d29b5f1a9b0c300e2d7b9505318c708dfb0ad0e88f71324cf65465";

    public FavorableExtremeRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != FavorableExtremePolicyVersion
                        .POINT_IN_TIME_ATTESTED_CAUSAL_WINDOW_HIGH_LOW_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the favorable-extreme V1 policy");
        }
        Objects.requireNonNull(readyEligibility,
                "readyEligibility must not be null");
        var eligibilityContext = readyEligibility.context();
        if (eligibilityContext.policyVersion()
                != TargetEligibilityPolicyVersion
                        .POINT_IN_TIME_TARGET_HIT_INPUT_READINESS_V1
                || !REQUIRED_ELIGIBILITY_HASH.equals(
                        eligibilityContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "readyEligibility must use the required target-eligibility V1 policy");
        }
        Objects.requireNonNull(candidates, "candidates must not be null");
        for (FullWindowHighLowObservation candidate : candidates) {
            Objects.requireNonNull(candidate,
                    "candidates must not contain null");
        }
        candidates = List.copyOf(candidates);
    }
}
