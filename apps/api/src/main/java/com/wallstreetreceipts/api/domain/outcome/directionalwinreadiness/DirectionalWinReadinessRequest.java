package com.wallstreetreceipts.api.domain.outcome.directionalwinreadiness;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.directionalwinorchestration
        .DirectionalWinOrchestrationResolution;

/** One complete supplied directional-win result to classify for readiness. */
public record DirectionalWinReadinessRequest(
        DirectionalWinReadinessPolicyVersion policyVersion,
        DirectionalWinOrchestrationResolution sourceResolution) {

    private static final String REQUIRED_SOURCE_HASH =
            "51429c7601d4807162855f08c680d1e6bb7895f87fc108e141e5ad3a3ab25bcb";

    public DirectionalWinReadinessRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != DirectionalWinReadinessPolicyVersion
                        .SUPPLIED_LEAF_DIRECTIONAL_WIN_READINESS_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the directional-win readiness V1 policy");
        }
        Objects.requireNonNull(sourceResolution,
                "sourceResolution must not be null");

        var sourceContext = switch (sourceResolution) {
            case DirectionalWinOrchestrationResolution.Available available ->
                available.context();
            case DirectionalWinOrchestrationResolution.NotApplicable notApplicable ->
                notApplicable.context();
            case DirectionalWinOrchestrationResolution.AssetReturnUnavailable unavailable ->
                unavailable.context();
        };
        if (sourceContext.policyVersion()
                != DirectionalWinOrchestrationPolicyVersion
                        .SUPPLIED_LEAF_DIRECTIONAL_WIN_ORCHESTRATION_V1
                || !REQUIRED_SOURCE_HASH.equals(
                        sourceContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "sourceResolution must use the required directional-win orchestration V1 policy");
        }
    }
}
