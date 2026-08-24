package com.wallstreetreceipts.api.domain.outcome.pricepair;

import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;

/** Explicit point-in-time evidence supplied for one asset-return price pair. */
public record AssetReturnPricePairRequest(
        AssetReturnPricePairPolicyVersion policyVersion,
        EndpointPriceResolution endpointPriceResolution,
        List<BasisPriceObservation> basisCandidates,
        List<PricePairAdjustmentEvidence> adjustmentCandidates) {

    private static final String REQUIRED_ENDPOINT_POLICY_HASH =
            "37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76";

    public AssetReturnPricePairRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != AssetReturnPricePairPolicyVersion
                        .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the asset-return price-pair V1 policy");
        }
        Objects.requireNonNull(endpointPriceResolution,
                "endpointPriceResolution must not be null");
        var endpointContext = endpointContext(endpointPriceResolution);
        if (endpointContext.policyVersion()
                != EndpointPricePolicyVersion
                        .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1
                || !endpointContext.policyDefinitionHash()
                        .equals(REQUIRED_ENDPOINT_POLICY_HASH)) {
            throw new IllegalArgumentException(
                    "endpointPriceResolution must use the required endpoint-price V1 policy");
        }
        basisCandidates = immutableNonNullCopy(basisCandidates, "basisCandidates");
        adjustmentCandidates = immutableNonNullCopy(
                adjustmentCandidates, "adjustmentCandidates");
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution resolution) {
        return switch (resolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
    }

    private static <T> List<T> immutableNonNullCopy(List<T> values, String field) {
        Objects.requireNonNull(values, field + " must not be null");
        for (T value : values) {
            Objects.requireNonNull(value, field + " must not contain null");
        }
        return List.copyOf(values);
    }
}
