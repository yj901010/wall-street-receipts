package com.wallstreetreceipts.api.domain.outcome.observation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution.ResolutionContext;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceResolution.UnavailableReason;

/** Pure selection of one request-scoped official endpoint close known as of evaluation. */
public final class EndpointPriceSelector {

    private EndpointPriceSelector() {
    }

    public static EndpointPriceResolution select(EndpointPriceRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        ResolutionContext context = context(request);
        Instant evaluationAsOf = request.evaluationAsOf();

        if (isFuture(request.catalogEvidence().availableAt(), evaluationAsOf)
                || isFuture(request.catalogEvidence().capturedAt(), evaluationAsOf)) {
            return unavailable(context, UnavailableReason.CATALOG_NOT_KNOWN_AS_OF);
        }
        var horizonContext = request.horizonResolution().window().context();
        if (!request.catalogEvidence().calendarId().equals(horizonContext.calendarId())
                || !request.catalogEvidence().catalogRevision()
                        .equals(horizonContext.catalogRevision())) {
            return unavailable(context, UnavailableReason.CATALOG_EVIDENCE_MISMATCH);
        }
        if (isFuture(request.binding().availableAt(), evaluationAsOf)
                || isFuture(request.binding().capturedAt(), evaluationAsOf)) {
            return unavailable(context, UnavailableReason.BINDING_NOT_KNOWN_AS_OF);
        }
        if (request.horizonResolution().window().endpointSession().closesAt()
                .isAfter(evaluationAsOf)) {
            return unavailable(context, UnavailableReason.ENDPOINT_NOT_REACHED_AS_OF);
        }

        List<EndpointPriceObservation> knownCandidates = request.candidates().stream()
                .filter(candidate -> !isFuture(candidate.availableAt(), evaluationAsOf))
                .filter(candidate -> !isFuture(candidate.capturedAt(), evaluationAsOf))
                .toList();
        if (knownCandidates.isEmpty()) {
            return unavailable(context, UnavailableReason.OBSERVATION_MISSING_AS_OF);
        }

        EndpointPriceBinding binding = request.binding();
        CatalogPointInTimeEvidence catalog = request.catalogEvidence();
        var endpoint = request.horizonResolution().window().endpointSession();
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.assetId().equals(binding.assetId()))) {
            return unavailable(context, UnavailableReason.ASSET_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.venueId().equals(binding.primaryVenueId()))) {
            return unavailable(context, UnavailableReason.PRIMARY_VENUE_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.currency().equals(binding.currency()))) {
            return unavailable(context, UnavailableReason.CURRENCY_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.priceSourceId().equals(binding.priceSourceId())
                || !candidate.priceSourceRevision().equals(binding.priceSourceRevision()))) {
            return unavailable(context, UnavailableReason.SOURCE_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.calendarId().equals(catalog.calendarId())
                || !candidate.catalogRevision().equals(catalog.catalogRevision()))) {
            return unavailable(context, UnavailableReason.CATALOG_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.sessionId().equals(endpoint.sessionId()))) {
            return unavailable(context, UnavailableReason.SESSION_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                !candidate.observedAt().equals(endpoint.closesAt()))) {
            return unavailable(context, UnavailableReason.OBSERVED_AT_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.priceField() != EndpointPriceField.OFFICIAL_REGULAR_SESSION_CLOSE)) {
            return unavailable(context, UnavailableReason.PRICE_FIELD_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.adjustmentBasis()
                        != EndpointPriceAdjustmentBasis
                                .SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED)) {
            return unavailable(context, UnavailableReason.ADJUSTMENT_BASIS_MISMATCH);
        }
        if (knownCandidates.stream().anyMatch(candidate ->
                candidate.corporateActionContinuity()
                        != CorporateActionContinuity.SPLIT_REVERSE_SPLIT_CONTINUOUS)) {
            return unavailable(context,
                    UnavailableReason.CORPORATE_ACTION_CONTINUITY_UNAVAILABLE);
        }
        if (knownCandidates.size() > 1) {
            return unavailable(context, UnavailableReason.OBSERVATION_AMBIGUOUS);
        }
        return new EndpointPriceResolution.Resolved(context, knownCandidates.getFirst());
    }

    private static ResolutionContext context(EndpointPriceRequest request) {
        return new ResolutionContext(
                request.policyVersion(),
                request.policyVersion().definitionHash(),
                request.horizonResolution(),
                request.catalogEvidence(),
                request.binding(),
                request.evaluationAsOf());
    }

    private static EndpointPriceResolution unavailable(
            ResolutionContext context,
            UnavailableReason reason) {
        return new EndpointPriceResolution.Unavailable(context, reason);
    }

    private static boolean isFuture(Instant value, Instant evaluationAsOf) {
        return value.isAfter(evaluationAsOf);
    }
}
