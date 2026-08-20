package com.wallstreetreceipts.api.domain.outcome.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Explicit inputs for one target-hit comparison.
 *
 * <p>The caller is responsible for selecting the correct point-in-time window
 * and supplying same-currency values that were already normalized and adjusted
 * for corporate actions. This record does not verify those upstream facts and
 * does not select a horizon or market-data observation. The caller supplies the
 * selected window high as {@code favorableExtreme} for a bullish side and the
 * selected window low for a bearish side. This type does not validate that
 * selection.</p>
 */
public record TargetHitInput(
        TargetHitSide side,
        BigDecimal target,
        BigDecimal favorableExtreme) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    public TargetHitInput {
        Objects.requireNonNull(side, "side must not be null");
        requireNullablePositiveNumeric(target, "target");
        requireNullablePositiveNumeric(favorableExtreme, "favorableExtreme");
    }

    private static void requireNullablePositiveNumeric(BigDecimal value, String field) {
        if (value == null) {
            return;
        }
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
}
