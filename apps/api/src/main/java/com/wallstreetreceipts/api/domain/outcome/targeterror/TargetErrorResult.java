package com.wallstreetreceipts.api.domain.outcome.targeterror;

import java.math.BigDecimal;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;

/** A calculated target-error ratio or explicit evidence of unavailability. */
public sealed interface TargetErrorResult
        permits TargetErrorResult.Available, TargetErrorResult.Unavailable {

    enum UnavailableReason {
        TARGET_MISSING_AS_OF,
        ENDPOINT_PRICE_UNAVAILABLE,
        TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE,
        BASIS_MISMATCH,
        ASSET_MISMATCH,
        PRIMARY_VENUE_MISMATCH,
        CURRENCY_MISMATCH,
        ADJUSTMENT_BASIS_MISMATCH,
        OUTPUT_NOT_REPRESENTABLE
    }

    /** Policy identity plus endpoint evidence; target evidence is never echoed. */
    record CalculationContext(
            TargetErrorPolicyVersion policyVersion,
            String policyDefinitionHash,
            EndpointPriceResolution endpointPriceResolution) {

        public CalculationContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(endpointPriceResolution,
                    "endpointPriceResolution must not be null");
            if (policyVersion
                    != TargetErrorPolicyVersion.ACTUAL_DENOMINATOR_SCALE_12_HALF_EVEN_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the target-error V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            validateEndpointPolicy(endpointPriceResolution);
        }
    }

    /**
     * Locally valid scale-12 output. Only TargetErrorCalculator attests that
     * the value was produced by the versioned formula.
     */
    record Available(
            CalculationContext context,
            BigDecimal targetError) implements TargetErrorResult {

        public Available {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(targetError, "targetError must not be null");
            if (!(context.endpointPriceResolution()
                    instanceof EndpointPriceResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "available target error requires a resolved endpoint price");
            }
            if (targetError.signum() < 0
                    || targetError.scale() != 12
                    || targetError.precision() > 38) {
                throw new IllegalArgumentException(
                        "targetError must be nonnegative scale-12 NUMERIC(38,12)");
            }
        }
    }

    /**
     * Locally consistent reason shape. Only TargetErrorCalculator attests that
     * this is the correct reason for the complete input evidence.
     */
    record Unavailable(
            CalculationContext context,
            UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason)
            implements TargetErrorResult {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            boolean endpointRelated = reason == UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE
                    || reason == UnavailableReason.TARGET_AND_ENDPOINT_PRICE_UNAVAILABLE;
            if (endpointRelated != (endpointReason != null)) {
                throw new IllegalArgumentException(
                        "endpointReason is required exactly for endpoint-related unavailability");
            }
            if (endpointRelated) {
                if (!(context.endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable endpointUnavailable)
                        || endpointUnavailable.reason() != endpointReason) {
                    throw new IllegalArgumentException(
                            "endpointReason must match endpointPriceResolution");
                }
            } else if (!(context.endpointPriceResolution()
                    instanceof EndpointPriceResolution.Resolved)) {
                throw new IllegalArgumentException(
                        "non-endpoint unavailability requires a resolved endpoint price");
            }
        }
    }

    private static void validateEndpointPolicy(EndpointPriceResolution endpointResolution) {
        var endpointContext = switch (endpointResolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
        if (endpointContext.policyVersion()
                != EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1
                || !"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76"
                        .equals(endpointContext.policyDefinitionHash())) {
            throw new IllegalArgumentException(
                    "endpointPriceResolution must use the required endpoint-price V1 policy");
        }
    }
}
