package com.wallstreetreceipts.api.domain.outcome.pricepair;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;

/** Independent point-in-time evidence that binds one basis price to one endpoint basis. */
public record PricePairAdjustmentEvidence(
        String adjustmentEvidenceId,
        String providerEventId,
        OutcomeBasis basis,
        String assetId,
        String primaryVenueId,
        Currency currency,
        String adjustmentSourceId,
        String adjustmentSourceRevision,
        String provenanceId,
        String basisObservationId,
        String basisProviderEventId,
        String endpointObservationId,
        String endpointProviderEventId,
        Instant coverageStartsAt,
        Instant coverageEndsAt,
        EndpointPriceAdjustmentBasis adjustmentBasis,
        CorporateActionContinuity corporateActionContinuity,
        Instant availableAt,
        Instant capturedAt) {

    public PricePairAdjustmentEvidence {
        requireCanonicalText(adjustmentEvidenceId, "adjustmentEvidenceId");
        requireCanonicalText(providerEventId, "providerEventId");
        Objects.requireNonNull(basis, "basis must not be null");
        requireCanonicalText(assetId, "assetId");
        requireCanonicalText(primaryVenueId, "primaryVenueId");
        Objects.requireNonNull(currency, "currency must not be null");
        requireCanonicalText(adjustmentSourceId, "adjustmentSourceId");
        requireCanonicalText(adjustmentSourceRevision, "adjustmentSourceRevision");
        requireCanonicalText(provenanceId, "provenanceId");
        requireCanonicalText(basisObservationId, "basisObservationId");
        requireCanonicalText(basisProviderEventId, "basisProviderEventId");
        requireCanonicalText(endpointObservationId, "endpointObservationId");
        requireCanonicalText(endpointProviderEventId, "endpointProviderEventId");
        PersistentInstant.requireMicrosecondPrecision(coverageStartsAt,
                "coverageStartsAt");
        PersistentInstant.requireMicrosecondPrecision(coverageEndsAt, "coverageEndsAt");
        Objects.requireNonNull(adjustmentBasis, "adjustmentBasis must not be null");
        Objects.requireNonNull(corporateActionContinuity,
                "corporateActionContinuity must not be null");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        if (coverageEndsAt.isBefore(coverageStartsAt)) {
            throw new IllegalArgumentException(
                    "coverageEndsAt must not precede coverageStartsAt");
        }
        if (availableAt.isBefore(coverageEndsAt)) {
            throw new IllegalArgumentException(
                    "availableAt must not precede coverageEndsAt");
        }
        if (capturedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException("capturedAt must not precede availableAt");
        }
    }

    private static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(field + " must be nonblank and trimmed");
        }
    }
}
