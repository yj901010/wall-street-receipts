package com.wallstreetreceipts.api.domain.outcome.observation;

import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/**
 * Point-in-time asset, primary-venue, scoring/target-currency, and price-source
 * binding. The currency is the required comparison currency; V1 performs no FX.
 */
public record EndpointPriceBinding(
        String bindingId,
        String bindingRevision,
        String assetId,
        String primaryVenueId,
        Currency currency,
        String priceSourceId,
        String priceSourceRevision,
        Instant availableAt,
        Instant capturedAt,
        String provenanceId) {

    public EndpointPriceBinding {
        requireCanonicalText(bindingId, "bindingId");
        requireCanonicalText(bindingRevision, "bindingRevision");
        requireCanonicalText(assetId, "assetId");
        requireCanonicalText(primaryVenueId, "primaryVenueId");
        Objects.requireNonNull(currency, "currency must not be null");
        requireCanonicalText(priceSourceId, "priceSourceId");
        requireCanonicalText(priceSourceRevision, "priceSourceRevision");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireCanonicalText(provenanceId, "provenanceId");
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
