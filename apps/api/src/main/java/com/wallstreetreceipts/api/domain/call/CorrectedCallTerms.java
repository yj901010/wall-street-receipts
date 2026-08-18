package com.wallstreetreceipts.api.domain.call;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;

/**
 * Complete replacement terms carried by a correction event. Nullable values
 * intentionally mean that the corrected call has no value for that term.
 */
public record CorrectedCallTerms(
        CallDirection direction,
        String originalRating,
        BigDecimal previousTarget,
        BigDecimal target,
        Currency currency,
        LocalDate targetDate) {

    public CorrectedCallTerms {
        Objects.requireNonNull(direction, "direction must not be null");
        requirePositive(previousTarget, "previousTarget");
        requirePositive(target, "target");
        if ((previousTarget != null || target != null) && currency == null) {
            throw new IllegalArgumentException("currency is required when a corrected target is present");
        }
        if (originalRating != null && originalRating.length() > 200) {
            throw new IllegalArgumentException("originalRating must not exceed 200 characters");
        }
    }

    private static void requirePositive(BigDecimal value, String field) {
        if (value != null && value.signum() <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
    }
}
