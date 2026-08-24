package com.wallstreetreceipts.api.domain.outcome.targeterrorreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetErrorResult;

/** One complete supplied target-error result to classify for readiness. */
public record TargetErrorReadinessRequest(
        TargetErrorReadinessPolicyVersion policyVersion,
        TargetErrorResult sourceResult) {

    private static final String REQUIRED_SOURCE_HASH =
            "31ca30555549f670e3c22d98ead16f7a02bfad198f36532effaf4a4b6931d074";

    public TargetErrorReadinessRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != TargetErrorReadinessPolicyVersion
                        .SUPPLIED_LEAF_TARGET_ERROR_READINESS_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the target-error readiness V1 policy");
        }
        Objects.requireNonNull(sourceResult, "sourceResult must not be null");
        var sourceContext = switch (sourceResult) {
            case TargetErrorResult.Available available -> available.context();
            case TargetErrorResult.Unavailable unavailable -> unavailable.context();
        };
        if (sourceContext.policyVersion()
                != TargetErrorPolicyVersion
                        .ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1
                || !REQUIRED_SOURCE_HASH.equals(
                        sourceContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "sourceResult must use the required target-error V1 policy");
        }
    }
}
