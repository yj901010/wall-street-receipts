package com.wallstreetreceipts.api.domain.outcome.pricepair;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPricePolicyVersion;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;

/** One complete basis/endpoint price pair or explicit point-in-time unavailability. */
public sealed interface AssetReturnPricePairResolution
        permits AssetReturnPricePairResolution.Resolved,
        AssetReturnPricePairResolution.Unavailable {

    enum UnavailableReason {
        BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
        BASIS_PRICE_MISSING_AS_OF,
        ENDPOINT_PRICE_UNAVAILABLE,
        BASIS_MISMATCH,
        ASSET_MISMATCH,
        PRIMARY_VENUE_MISMATCH,
        CURRENCY_MISMATCH,
        PRICE_SOURCE_MISMATCH,
        OBSERVED_AT_MISMATCH,
        PRICE_FIELD_MISMATCH,
        BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH,
        BASIS_PRICE_CONTINUITY_UNAVAILABLE,
        BASIS_PRICE_AMBIGUOUS,
        ADJUSTMENT_EVIDENCE_MISSING_AS_OF,
        ADJUSTMENT_OUTCOME_BASIS_MISMATCH,
        ADJUSTMENT_ASSET_MISMATCH,
        ADJUSTMENT_PRIMARY_VENUE_MISMATCH,
        ADJUSTMENT_CURRENCY_MISMATCH,
        BASIS_OBSERVATION_LINK_MISMATCH,
        ENDPOINT_OBSERVATION_LINK_MISMATCH,
        ADJUSTMENT_COVERAGE_MISMATCH,
        ADJUSTMENT_PRICE_BASIS_MISMATCH,
        ADJUSTMENT_CONTINUITY_UNAVAILABLE,
        ADJUSTMENT_EVIDENCE_AMBIGUOUS
    }

    record ResolutionContext(
            AssetReturnPricePairPolicyVersion policyVersion,
            String policyDefinitionHash,
            EndpointPriceResolution endpointPriceResolution) {

        public ResolutionContext {
            Objects.requireNonNull(policyVersion, "policyVersion must not be null");
            Objects.requireNonNull(policyDefinitionHash,
                    "policyDefinitionHash must not be null");
            Objects.requireNonNull(endpointPriceResolution,
                    "endpointPriceResolution must not be null");
            if (policyVersion
                    != AssetReturnPricePairPolicyVersion
                            .SOURCE_RECORDED_BASIS_EVENT_TO_OFFICIAL_ENDPOINT_PRICE_PAIR_V1) {
                throw new IllegalArgumentException(
                        "policyVersion must be the asset-return price-pair V1 policy");
            }
            if (!policyVersion.definitionHash().equals(policyDefinitionHash)) {
                throw new IllegalArgumentException(
                        "policyDefinitionHash must match policyVersion");
            }
            var endpointContext = endpointContext(endpointPriceResolution);
            if (endpointContext.policyVersion()
                    != EndpointPricePolicyVersion
                            .OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1
                    || !"37e37aba9302d77366cef4129f77a82b7ccb2f1937bfffc0315ea8d0bc6b1f76"
                            .equals(endpointContext.policyDefinitionHash())) {
                throw new IllegalArgumentException(
                        "endpointPriceResolution must use the required endpoint-price V1 policy");
            }
        }
    }

    /**
     * Locally coherent selected pair evidence. Only the selector attests request
     * membership, PIT filtering, precedence, and cardinality.
     */
    record Resolved(
            ResolutionContext context,
            BasisPriceObservation basisObservation,
            PricePairAdjustmentEvidence adjustmentEvidence)
            implements AssetReturnPricePairResolution {

        public Resolved {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(basisObservation,
                    "basisObservation must not be null");
            Objects.requireNonNull(adjustmentEvidence,
                    "adjustmentEvidence must not be null");
            if (!(context.endpointPriceResolution()
                    instanceof EndpointPriceResolution.Resolved endpointResolved)) {
                throw new IllegalArgumentException(
                        "resolved price pair requires a resolved endpoint price");
            }
            validateResolvedEvidence(context, basisObservation, adjustmentEvidence,
                    endpointResolved);
        }
    }

    record Unavailable(
            ResolutionContext context,
            UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason)
            implements AssetReturnPricePairResolution {

        public Unavailable {
            Objects.requireNonNull(context, "context must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
            boolean carriesEndpointReason = reason == UnavailableReason
                    .BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE
                    || reason == UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE;
            if (carriesEndpointReason) {
                if (!(context.endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable endpointUnavailable)) {
                    throw new IllegalArgumentException(
                            "endpoint unavailability requires an unavailable endpoint price");
                }
                if (endpointReason != endpointUnavailable.reason()) {
                    throw new IllegalArgumentException(
                            "endpointReason must preserve the exact endpoint reason");
                }
            } else {
                if (endpointReason != null) {
                    throw new IllegalArgumentException(
                            "non-endpoint unavailability requires a null endpointReason");
                }
                if (!(context.endpointPriceResolution()
                        instanceof EndpointPriceResolution.Resolved)) {
                    throw new IllegalArgumentException(
                            "price-pair evidence unavailability requires a resolved endpoint price");
                }
            }
        }
    }

    private static void validateResolvedEvidence(
            ResolutionContext context,
            BasisPriceObservation basisObservation,
            PricePairAdjustmentEvidence adjustmentEvidence,
            EndpointPriceResolution.Resolved endpointResolved) {
        var endpointContext = endpointResolved.context();
        var binding = endpointContext.binding();
        var basis = endpointContext.horizonResolution().window().context().basis();
        var endpointObservation = endpointResolved.observation();
        Instant evaluationAsOf = endpointContext.evaluationAsOf();

        if (basisObservation.availableAt().isAfter(evaluationAsOf)
                || basisObservation.capturedAt().isAfter(evaluationAsOf)
                || adjustmentEvidence.availableAt().isAfter(evaluationAsOf)
                || adjustmentEvidence.capturedAt().isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException(
                    "resolved price-pair evidence must be known by evaluationAsOf");
        }
        if (!basisObservation.basis().equals(basis)
                || !basisObservation.assetId().equals(binding.assetId())
                || !basisObservation.venueId().equals(binding.primaryVenueId())
                || !basisObservation.currency().equals(binding.currency())
                || !basisObservation.priceSourceId().equals(binding.priceSourceId())
                || !basisObservation.priceSourceRevision()
                        .equals(binding.priceSourceRevision())
                || !basisObservation.observedAt().equals(basis.eventTime())
                || basisObservation.priceField()
                        != BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE
                || basisObservation.adjustmentBasis()
                        != requiredAdjustmentBasis()
                || basisObservation.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS) {
            throw new IllegalArgumentException(
                    "basisObservation must exactly match the price-pair context");
        }
        if (!adjustmentEvidence.basis().equals(basis)
                || !adjustmentEvidence.assetId().equals(binding.assetId())
                || !adjustmentEvidence.primaryVenueId().equals(binding.primaryVenueId())
                || !adjustmentEvidence.currency().equals(binding.currency())
                || !adjustmentEvidence.basisObservationId()
                        .equals(basisObservation.observationId())
                || !adjustmentEvidence.basisProviderEventId()
                        .equals(basisObservation.providerEventId())
                || !adjustmentEvidence.endpointObservationId()
                        .equals(endpointObservation.observationId())
                || !adjustmentEvidence.endpointProviderEventId()
                        .equals(endpointObservation.providerEventId())
                || !adjustmentEvidence.coverageStartsAt()
                        .equals(basisObservation.observedAt())
                || !adjustmentEvidence.coverageEndsAt()
                        .equals(endpointObservation.observedAt())
                || adjustmentEvidence.adjustmentBasis() != requiredAdjustmentBasis()
                || adjustmentEvidence.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS) {
            throw new IllegalArgumentException(
                    "adjustmentEvidence must exactly bind the selected price pair");
        }
    }

    private static EndpointPriceAdjustmentBasis requiredAdjustmentBasis() {
        return EndpointPriceAdjustmentBasis
                .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution resolution) {
        return switch (resolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
    }
}
