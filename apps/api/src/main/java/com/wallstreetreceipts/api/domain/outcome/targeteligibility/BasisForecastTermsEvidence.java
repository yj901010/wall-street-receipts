package com.wallstreetreceipts.api.domain.outcome.targeteligibility;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.outcome.horizon.OutcomeBasis;

/** Complete source terms for one immutable original or correction basis. */
public record BasisForecastTermsEvidence(
        String termsEvidenceId,
        OutcomeBasis basis,
        String assetId,
        CallDirection direction,
        TargetDisposition targetDisposition,
        String provider,
        String providerEventId,
        Instant availableAt,
        Instant capturedAt,
        String provenanceId) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    /** Complete source-recorded target presence without null overloading. */
    public sealed interface TargetDisposition
            permits TargetDisposition.Present, TargetDisposition.Absent {

        /** Exact source target terms, distinct from later normalized evidence. */
        record Present(
                BigDecimal sourceTarget,
                Currency sourceTargetCurrency,
                LocalDate targetDate) implements TargetDisposition {

            public Present {
                requirePositiveNumeric(sourceTarget, "sourceTarget");
                Objects.requireNonNull(sourceTargetCurrency,
                        "sourceTargetCurrency must not be null");
            }
        }

        /** Complete source terms explicitly contain no target. */
        record Absent() implements TargetDisposition {
        }
    }

    public BasisForecastTermsEvidence {
        requireCanonicalText(termsEvidenceId, "termsEvidenceId");
        Objects.requireNonNull(basis, "basis must not be null");
        requireCanonicalText(assetId, "assetId");
        Objects.requireNonNull(direction, "direction must not be null");
        Objects.requireNonNull(targetDisposition, "targetDisposition must not be null");
        requireCanonicalText(provider, "provider");
        requireCanonicalText(providerEventId, "providerEventId");
        PersistentInstant.requireMicrosecondPrecision(availableAt, "availableAt");
        PersistentInstant.requireMicrosecondPrecision(capturedAt, "capturedAt");
        requireCanonicalText(provenanceId, "provenanceId");
        if (availableAt.isBefore(basis.eventTime())) {
            throw new IllegalArgumentException(
                    "availableAt must not precede basis.eventTime");
        }
        if (capturedAt.isBefore(availableAt)) {
            throw new IllegalArgumentException(
                    "capturedAt must not precede availableAt");
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
