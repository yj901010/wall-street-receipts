package com.wallstreetreceipts.api.domain.outcome.assetreturn;

import java.math.BigDecimal;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairPolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution;

/** A signed asset price-return ratio or explicit unavailability. */
public sealed interface AssetReturnResult
        permits AssetReturnResult.Available, AssetReturnResult.Unavailable {

    enum UnavailableReason {
        PRICE_PAIR_UNAVAILABLE,
        OUTPUT_NOT_REPRESENTABLE
    }

    record CalculationContext(
            AssetReturnPolicyVersion policyVersion,
            String policyDefinitionHash,
            AssetReturnPricePairResolution pricePairResolution) {

        public CalculationContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(pricePairResolution,
                    "pricePairResolution must not be null");
            if (policyVersion
                    != AssetReturnPolicyVersion
                            .SIGNED_BASIS_DENOMINATOR_SCALE_12_HALF_EVEN_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the asset-return V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            var pairContext = pairContext(pricePairResolution);
            if (pairContext.policyVersion()
                    != AssetReturnPricePairPolicyVersion
                            .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1
                    || !"895e4bc97ebb3a92b80f2c58e2d28abb94440eeca963046ee755fa98825f4887"
                            .equals(pairContext.policyDefinitionHash())) {
                throw new IllegalArgumentException(
                        "pricePairResolution must use the required price-pair V1 policy");
            }
        }
    }

    record Available(
            CalculationContext context,
            BigDecimal assetReturn) implements AssetReturnResult {

        public Available {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(assetReturn, "assetReturn must not be null");
            if (!(context.pricePairResolution()
                    instanceof AssetReturnPricePairResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "available asset return requires a resolved price pair");
            }
            if (assetReturn.compareTo(BigDecimal.ONE.negate()) < 0
                    || assetReturn.scale() != 12
                    || assetReturn.precision() > 38) {
                throw new IllegalArgumentException(
                        "assetReturn must be signed scale-12 NUMERIC(38,12) at least -1");
            }
        }
    }

    record Unavailable(
            CalculationContext context,
            UnavailableReason reason,
            AssetReturnPricePairResolution.UnavailableReason pricePairReason)
            implements AssetReturnResult {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            if (reason == UnavailableReason.PRICE_PAIR_UNAVAILABLE) {
                if (!(context.pricePairResolution()
                        instanceof AssetReturnPricePairResolution.Unavailable pairUnavailable)) {
                    throw new IllegalArgumentException(
                            "price-pair unavailability requires an unavailable price pair");
                }
                if (pricePairReason != pairUnavailable.reason()) {
                    throw new IllegalArgumentException(
                            "pricePairReason must preserve the exact price-pair reason");
                }
            } else {
                if (pricePairReason != null) {
                    throw new IllegalArgumentException(
                            "output unavailability requires a null pricePairReason");
                }
                if (!(context.pricePairResolution()
                        instanceof AssetReturnPricePairResolution.Resolved)) {
                    throw new IllegalArgumentException(
                            "output unavailability requires a resolved price pair");
                }
            }
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
