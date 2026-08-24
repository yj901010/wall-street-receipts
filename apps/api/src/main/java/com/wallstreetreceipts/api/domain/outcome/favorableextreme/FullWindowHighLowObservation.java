package com.wallstreetreceipts.api.domain.outcome.favorableextreme;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.CorporateActionContinuity;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;

/** One provider-attested exact causal-window high/low candidate. */
public record FullWindowHighLowObservation(
        String observationId,
        String providerEventId,
        OutcomeBasis basis,
        OutcomeHorizon horizon,
        String assetId,
        String venueId,
        Currency currency,
        String priceSourceId,
        String priceSourceRevision,
        String provenanceId,
        String calendarId,
        String catalogRevision,
        List<String> orderedSessionIds,
        Instant lowerBound,
        BoundaryType lowerBoundType,
        Instant upperBound,
        BoundaryType upperBoundType,
        WindowPriceField priceField,
        WindowCoverageCompleteness coverageCompleteness,
        EndpointPriceAdjustmentBasis adjustmentBasis,
        CorporateActionContinuity corporateActionContinuity,
        Instant availableAt,
        Instant capturedAt,
        BigDecimal windowHigh,
        BigDecimal windowLow) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    public enum BoundaryType {
        EXCLUSIVE,
        INCLUSIVE,
        UNKNOWN
    }

    public enum WindowPriceField {
        PRIMARY_VENUE_REGULAR_SESSION_CAUSAL_WINDOW_HIGH_LOW_PAIR,
        INDICATIVE_OR_OTHER
    }

    public enum WindowCoverageCompleteness {
        EXACT_CAUSAL_WINDOW_SESSION_UNION,
        PARTIAL_OR_UNKNOWN
    }

    public FullWindowHighLowObservation {
        requireCanonicalText(observationId, "observationId");
        requireCanonicalText(providerEventId, "providerEventId");
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(horizon, "horizon must not be null");
        requireCanonicalText(assetId, "assetId");
        requireCanonicalText(venueId, "venueId");
        Objects.requireNonNull(currency, "currency must not be null");
        requireCanonicalText(priceSourceId, "priceSourceId");
        requireCanonicalText(priceSourceRevision, "priceSourceRevision");
        requireCanonicalText(provenanceId, "provenanceId");
        requireCanonicalText(calendarId, "calendarId");
        requireCanonicalText(catalogRevision, "catalogRevision");
        Objects.requireNonNull(orderedSessionIds,
                "orderedSessionIds must not be null");
        Set<String> uniqueSessionIds = new HashSet<>();
        for (String sessionId : orderedSessionIds) {
            requireCanonicalText(sessionId, "orderedSessionIds entry");
            if (!uniqueSessionIds.add(sessionId)) {
                throw new IllegalArgumentException(
                        "orderedSessionIds must contain unique values");
            }
        }
        orderedSessionIds = List.copyOf(orderedSessionIds);
        if (orderedSessionIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "orderedSessionIds must not be empty");
        }
        PersistentInstant.requireMicrosecondPrecision(lowerBound, "lowerBound");
        Objects.requireNonNull(lowerBoundType, "lowerBoundType must not be null");
        PersistentInstant.requireMicrosecondPrecision(upperBound, "upperBound");
        Objects.requireNonNull(upperBoundType, "upperBoundType must not be null");
        Objects.requireNonNull(priceField, "priceField must not be null");
        Objects.requireNonNull(coverageCompleteness,
                "coverageCompleteness must not be null");
        Objects.requireNonNull(adjustmentBasis,
                "adjustmentBasis must not be null");
        Objects.requireNonNull(corporateActionContinuity,
                "corporateActionContinuity must not be null");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requirePositiveNumeric(windowHigh, "windowHigh");
        requirePositiveNumeric(windowLow, "windowLow");
        if (!lowerBound.isBefore(upperBound)) {
            throw new IllegalArgumentException(
                    "lowerBound must be before upperBound");
        }
        if (availableAt.isBefore(upperBound)) {
            throw new IllegalArgumentException(
                    "availableAt must not precede upperBound");
        }
        if (capturedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException(
                    "capturedAt must not precede availableAt");
        }
        if (windowLow.compareTo(windowHigh) > 0) {
            throw new IllegalArgumentException(
                    "windowLow must not exceed windowHigh");
        }
    }

    private static void requirePositiveNumeric(BigDecimal value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        if (value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        try {
            BigDecimal storageValue = value.setScale(
                    STORAGE_SCALE, RoundingMode.UNNECESSARY);
            if (storageValue.precision() > STORAGE_PRECISION) {
                throw new IllegalArgumentException(
                        field + " exceeds NUMERIC(38,12) precision");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    field + " exceeds NUMERIC(38,12) scale", exception);
        }
    }

    private static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    field + " must be nonblank and trimmed");
        }
    }
}
