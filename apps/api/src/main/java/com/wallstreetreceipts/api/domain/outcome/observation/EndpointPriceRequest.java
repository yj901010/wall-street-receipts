package com.wallstreetreceipts.api.domain.outcome.observation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonResolution.Resolved;

/** Explicit inputs for one point-in-time endpoint-close selection. */
public record EndpointPriceRequest(
        EndpointPricePolicyVersion policyVersion,
        Resolved horizonResolution,
        CatalogPointInTimeEvidence catalogEvidence,
        EndpointPriceBinding binding,
        Instant evaluationAsOf,
        List<EndpointPriceObservation> candidates) {

    public EndpointPriceRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        if (policyVersion
                != EndpointPricePolicyVersion.OFFICIAL_PRIMARY_VENUE_CLOSE_SPLIT_ADJUSTED_V1) {
            throw new IllegalArgumentException(
                    "policyVersion must be the endpoint-price V1 policy");
        }
        Objects.requireNonNull(horizonResolution, "horizonResolution must not be null");
        Objects.requireNonNull(catalogEvidence, "catalogEvidence must not be null");
        Objects.requireNonNull(binding, "binding must not be null");
        PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        Objects.requireNonNull(candidates, "candidates must not be null");
        for (EndpointPriceObservation candidate : candidates) {
            Objects.requireNonNull(candidate, "candidates must not contain null");
        }
        candidates = List.copyOf(candidates);
    }
}
