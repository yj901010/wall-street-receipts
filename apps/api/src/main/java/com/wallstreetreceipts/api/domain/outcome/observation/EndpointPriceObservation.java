package com.wallstreetreceipts.api.domain.outcome.observation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** One immutable, provider-identified candidate endpoint-price observation. */
public record EndpointPriceObservation(
        String observationId,
        String providerEventId,
        String assetId,
        String venueId,
        Currency currency,
        String priceSourceId,
        String priceSourceRevision,
        String provenanceId,
        String calendarId,
        String catalogRevision,
        String sessionId,
        EndpointPriceField priceField,
        EndpointPriceAdjustmentBasis adjustmentBasis,
        CorporateActionContinuity corporateActionContinuity,
        Instant observedAt,
        Instant availableAt,
        Instant capturedAt,
        BigDecimal price) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    public EndpointPriceObservation {
        requireCanonicalText(observationId, "observationId");
        requireCanonicalText(providerEventId, "providerEventId");
        requireCanonicalText(assetId, "assetId");
        requireCanonicalText(venueId, "venueId");
        Objects.requireNonNull(currency, "currency must not be null");
        requireCanonicalText(priceSourceId, "priceSourceId");
        requireCanonicalText(priceSourceRevision, "priceSourceRevision");
        requireCanonicalText(provenanceId, "provenanceId");
        requireCanonicalText(calendarId, "calendarId");
        requireCanonicalText(catalogRevision, "catalogRevision");
        requireCanonicalText(sessionId, "sessionId");
        Objects.requireNonNull(priceField, "priceField must not be null");
        Objects.requireNonNull(adjustmentBasis, "adjustmentBasis must not be null");
        Objects.requireNonNull(corporateActionContinuity,
                "corporateActionContinuity must not be null");
        PersistentInstant.requireMicrosecondPrecision(observedAt, "observedAt");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requirePositiveNumeric(price, "price");
        if (availableAt.isBefore(observedAt)) {
            throw new IllegalArgumentException("availableAt must not precede observedAt");
        }
        if (capturedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException("capturedAt must not precede availableAt");
        }
    }

    private static void requirePositiveNumeric(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        try {
            BigDecimal storageValue = value.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
            if (storageValue.precision() > STORAGE_PRECISION) {
                throw new IllegalArgumentException(field + " exceeds NUMERIC(38,12) precision");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(field + " exceeds NUMERIC(38,12) scale", exception);
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
