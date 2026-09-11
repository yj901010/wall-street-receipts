package com.wallstreetreceipts.api.application.scoring;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.horizon.SessionCloseHorizonRequest;
import com.wallstreetreceipts.api.domain.outcome.observation.CatalogPointInTimeEvidence;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceBinding;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.pricepair.BasisPriceObservation;
import com.wallstreetreceipts.api.domain.outcome.pricepair.PricePairAdjustmentEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeteligibility.BasisForecastTermsEvidence;
import com.wallstreetreceipts.api.domain.outcome.targeterror.TargetPriceEvidence;

/** Caller-supplied synthetic evidence, not an authenticated provider or database snapshot. */
public record EndpointScoringInput(
        DataMode dataMode,
        SessionCloseHorizonRequest horizon,
        CatalogPointInTimeEvidence catalogEvidence,
        EndpointPriceBinding binding,
        Instant evaluationAsOf,
        BasisForecastTermsEvidence terms,
        List<EndpointPriceObservation> endpointCandidates,
        List<BasisPriceObservation> basisCandidates,
        List<PricePairAdjustmentEvidence> adjustmentCandidates,
        TargetPriceEvidence targetEvidence) {

    public EndpointScoringInput {
        if (dataMode != DataMode.DEMO) throw new IllegalArgumentException("Only explicit DEMO scoring is supported");
        Objects.requireNonNull(horizon, "horizon");
        Objects.requireNonNull(catalogEvidence, "catalogEvidence");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(terms, "terms");
        PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        if (horizon.policyVersion() != EndpointScoringMethodology.HORIZON
                || !horizon.basis().equals(terms.basis()) || !binding.assetId().equals(terms.assetId())
                || terms.availableAt().isAfter(evaluationAsOf) || terms.capturedAt().isAfter(evaluationAsOf)) {
            throw new IllegalArgumentException("Forecast terms must identify the same PIT-visible basis and asset");
        }
        // This first integration supports identity target normalization only. No implicit FX/split conversion.
        if (targetEvidence != null) {
            if (!(terms.targetDisposition() instanceof BasisForecastTermsEvidence.TargetDisposition.Present present)
                    || !targetEvidence.basis().equals(terms.basis()) || !targetEvidence.assetId().equals(terms.assetId())
                    || !targetEvidence.currency().equals(present.sourceTargetCurrency())
                    || targetEvidence.target().compareTo(present.sourceTarget()) != 0) {
                throw new IllegalArgumentException("Target evidence must preserve the explicit source target terms");
            }
        }
        endpointCandidates = boundedCopy(endpointCandidates);
        basisCandidates = boundedCopy(basisCandidates);
        adjustmentCandidates = boundedCopy(adjustmentCandidates);
        if (horizon.catalog().orderedSessions().size() > 4096) throw new IllegalArgumentException("Catalog too large");
    }

    private static <T> List<T> boundedCopy(List<T> values) {
        Objects.requireNonNull(values, "candidate list");
        if (values.size() > 4096) throw new IllegalArgumentException("Candidate list too large");
        return List.copyOf(values); // Missing evidence is an empty list, never an invented price.
    }
}
