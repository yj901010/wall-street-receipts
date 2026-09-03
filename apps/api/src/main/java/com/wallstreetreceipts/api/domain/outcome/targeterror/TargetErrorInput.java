package com.wallstreetreceipts.api.domain.outcome.targeterror;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;

/** Explicit endpoint resolution and nullable point-in-time target evidence. */
public record TargetErrorInput(
        TargetErrorPolicyVersion policyVersion,
        EndpointPriceResolution endpointPriceResolution,
        TargetPriceEvidence targetEvidence) {

    private static final String REQUIRED_ENDPOINT_POLICY_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";

    public TargetErrorInput {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(endpointPriceResolution,
                "endpointPriceResolution must not be null");
        if (policyVersion
                != TargetErrorPolicyVersion.ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the target-error V1 policy");
        }
        var endpointContext = switch (endpointPriceResolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
        if (endpointContext.policyVersion()
                != EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1
                || !REQUIRED_ENDPOINT_POLICY_HASH.equals(
                        endpointContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "endpointPriceResolution must use the required endpoint-price V1 policy");
        }
    }
}
