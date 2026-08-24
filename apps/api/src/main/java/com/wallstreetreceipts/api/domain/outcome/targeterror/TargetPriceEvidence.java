package com.wallstreetreceipts.api.domain.outcome.targeterror;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;
import com.wallstreetreceipts.api.domain.outcome.observation.EndpointPriceAdjustmentBasis;

/** One source-traceable target normalized to an explicit comparison basis. */
public record TargetPriceEvidence(
        String targetEvidenceId,
        OutcomeBasis basis,
        String assetId,
        String primaryVenueId,
        Currency currency,
        EndpointPriceAdjustmentBasis adjustmentBasis,
        BigDecimal target,
        Instant availableAt,
        Instant capturedAt,
        String provenanceId) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    public TargetPriceEvidence {
        requireCanonicalText(targetEvidenceId, "targetEvidenceId");
        Objects.requireNonNull(basis, "basis must not be null");
        requireCanonicalText(assetId, "assetId");
        requireCanonicalText(primaryVenueId, "primaryVenueId");
        Objects.requireNonNull(currency, "currency must not be null");
        Objects.requireNonNull(adjustmentBasis, "adjustmentBasis must not be null");
        requirePositiveNumeric(target, "target");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireCanonicalText(provenanceId, "provenanceId");
        if (availableAt.isBefore(basis.eventTime())) {
            throw new IllegalArgumentException(
                    "availableAt must not precede basis.eventTime");
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
