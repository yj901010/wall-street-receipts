package com.wallstreetreceipts.api.domain.outcome.assetreturn;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;

/** One complete price-pair resolution supplied to the signed return leaf. */
public record AssetReturnInput(
        AssetReturnPolicyVersion policyVersion,
        AssetReturnPricePairResolution pricePairResolution) {

    private static final String REQUIRED_PRICE_PAIR_POLICY_HASH =
            "895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887";

    public AssetReturnInput {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != AssetReturnPolicyVersion.SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the asset-return V1 policy");
        }
        Objects.requireNonNull(pricePairResolution,
                "pricePairResolution must not be null");
        var pairContext = pairContext(pricePairResolution);
        if (pairContext.policyVersion()
                != AssetReturnPricePairPolicyVersion
                        .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1
                || !pairContext.policyDefinitionHash()
                        .equals(REQUIRED_PRICE_PAIR_POLICY_HASH)) {
            throw new IllegalArgumentException(
                    "pricePairResolution must use the required price-pair V1 policy");
        }
    }

    private static AssetReturnPricePairResolution.ResolutionContext pairContext(
            AssetReturnPricePairResolution resolution) {
        return switch (resolution) {
            case AssetReturnPricePairResolution.Resolved resolved -> resolved.context();
            case AssetReturnPricePairResolution.Unavailable unavailable -> unavailable.context();
        };
    }
}
