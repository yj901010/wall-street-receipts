package com.wallstreetreceipts.api.domain.outcome.pricepair;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.pricepair.AssetReturnPricePairResolution.UnavailableReason;

/** Pure selection of one replayable basis/endpoint price pair known as of evaluation. */
public final class AssetReturnPricePairSelector {

    private AssetReturnPricePairSelector() {
    }

    public static AssetReturnPricePairResolution select(AssetReturnPricePairRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.endpointPriceResolution());
        var endpointContext = endpointContext(request.endpointPriceResolution());
        Instant evaluationAsOf = endpointContext.evaluationAsOf();

        List<BasisPriceObservation> knownBasisCandidates = request.basisCandidates()
                .stream()
                .filter(candidate -> known(candidate.availableAt(), candidate.capturedAt(),
                        evaluationAsOf))
                .toList();
        if (knownBasisCandidates.isEmpty()
                && request.endpointPriceResolution()
                        instanceof EndpointPriceResolution.Unavailable endpointUnavailable) {
            return unavailable(context,
                    UnavailableReason.BASIS_AND_ENDPOINT_PRICE_UNAVAILABLE,
                    endpointUnavailable.reason());
        }
        if (knownBasisCandidates.isEmpty()) {
            return unavailable(context, UnavailableReason.BASIS_PRICE_MISSING_AS_OF, null);
        }
        if (request.endpointPriceResolution()
                instanceof EndpointPriceResolution.Unavailable endpointUnavailable) {
            return unavailable(context, UnavailableReason.ENDPOINT_PRICE_UNAVAILABLE,
                    endpointUnavailable.reason());
        }

        EndpointPriceResolution.Resolved endpointResolved =
                (EndpointPriceResolution.Resolved) request.endpointPriceResolution();
        var binding = endpointResolved.context().binding();
        var basis = endpointResolved.context().horizonResolution().window().context().basis();
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                !candidate.basis().equals(basis))) {
            return unavailable(context, UnavailableReason.BASIS_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                !candidate.assetId().equals(binding.assetId()))) {
            return unavailable(context, UnavailableReason.ASSET_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                !candidate.venueId().equals(binding.primaryVenueId()))) {
            return unavailable(context, UnavailableReason.PRIMARY_VENUE_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                !candidate.currency().equals(binding.currency()))) {
            return unavailable(context, UnavailableReason.CURRENCY_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                !candidate.priceSourceId().equals(binding.priceSourceId())
                || !candidate.priceSourceRevision().equals(binding.priceSourceRevision()))) {
            return unavailable(context, UnavailableReason.PRICE_SOURCE_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                !candidate.observedAt().equals(basis.eventTime()))) {
            return unavailable(context, UnavailableReason.OBSERVED_AT_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                candidate.priceField()
                        != BasisPriceField.SOURCE_RECORDED_BASIS_EVENT_PRICE)) {
            return unavailable(context, UnavailableReason.PRICE_FIELD_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                candidate.adjustmentBasis() != requiredAdjustmentBasis())) {
            return unavailable(context,
                    UnavailableReason.BASIS_PRICE_ADJUSTMENT_BASIS_MISMATCH, null);
        }
        if (knownBasisCandidates.stream().anyMatch(candidate ->
                candidate.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)) {
            return unavailable(context,
                    UnavailableReason.BASIS_PRICE_CONTINUITY_UNAVAILABLE, null);
        }
        if (knownBasisCandidates.size() > 1) {
            return unavailable(context, UnavailableReason.BASIS_PRICE_AMBIGUOUS, null);
        }
        BasisPriceObservation basisObservation = knownBasisCandidates.getFirst();

        List<PricePairAdjustmentEvidence> knownAdjustmentCandidates =
                request.adjustmentCandidates().stream()
                        .filter(candidate -> known(candidate.availableAt(), candidate.capturedAt(),
                                evaluationAsOf))
                        .toList();
        if (knownAdjustmentCandidates.isEmpty()) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_EVIDENCE_MISSING_AS_OF, null);
        }
        var endpointObservation = endpointResolved.observation();
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.basis().equals(basis))) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_OUTCOME_BASIS_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.assetId().equals(binding.assetId()))) {
            return unavailable(context, UnavailableReason.ADJUSTMENT_ASSET_MISMATCH,
                    null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.primaryVenueId().equals(binding.primaryVenueId()))) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_PRIMARY_VENUE_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.currency().equals(binding.currency()))) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_CURRENCY_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.basisObservationId().equals(basisObservation.observationId())
                || !candidate.basisProviderEventId()
                        .equals(basisObservation.providerEventId()))) {
            return unavailable(context,
                    UnavailableReason.BASIS_OBSERVATION_LINK_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.endpointObservationId()
                        .equals(endpointObservation.observationId())
                || !candidate.endpointProviderEventId()
                        .equals(endpointObservation.providerEventId()))) {
            return unavailable(context,
                    UnavailableReason.ENDPOINT_OBSERVATION_LINK_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                !candidate.coverageStartsAt().equals(basisObservation.observedAt())
                || !candidate.coverageEndsAt().equals(endpointObservation.observedAt()))) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_COVERAGE_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                candidate.adjustmentBasis() != requiredAdjustmentBasis())) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_PRICE_BASIS_MISMATCH, null);
        }
        if (knownAdjustmentCandidates.stream().anyMatch(candidate ->
                candidate.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_CONTINUITY_UNAVAILABLE, null);
        }
        if (knownAdjustmentCandidates.size() > 1) {
            return unavailable(context,
                    UnavailableReason.ADJUSTMENT_EVIDENCE_AMBIGUOUS, null);
        }
        return new AssetReturnPricePairResolution.Resolved(
                context, basisObservation, knownAdjustmentCandidates.getFirst());
    }

    private static AssetReturnPricePairResolution unavailable(
            ResolutionContext context,
            UnavailableReason reason,
            EndpointPriceResolution.UnavailableReason endpointReason) {
        return new AssetReturnPricePairResolution.Unavailable(
                context, reason, endpointReason);
    }

    private static EndpointPriceAdjustmentBasis requiredAdjustmentBasis() {
        return EndpointPriceAdjustmentBasis
                .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED;
    }

    private static boolean known(Instant availableAt, Instant capturedAt,
            Instant evaluationAsOf) {
        return !availableAt.isAfter(evaluationAsOf)
                && !capturedAt.isAfter(evaluationAsOf);
    }

    private static EndpointPriceResolution.ResolutionContext endpointContext(
            EndpointPriceResolution resolution) {
        return switch (resolution) {
            case EndpointPriceResolution.Resolved resolved -> resolved.context();
            case EndpointPriceResolution.Unavailable unavailable -> unavailable.context();
        };
    }
}
