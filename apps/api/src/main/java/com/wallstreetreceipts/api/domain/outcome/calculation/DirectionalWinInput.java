package com.wallstreetreceipts.api.domain.outcome.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Explicit inputs for one directional-win comparison.
 *
 * <p>The caller is responsible for selecting the point-in-time horizon return
 * and interpreting the forecast side. This record does not calculate a return,
 * select a horizon, or map a richer call direction.</p>
 */
public record DirectionalWinInput(
        DirectionalWinSide side,
        BigDecimal assetReturn) {

    private static final int STORAGE_SCALE = 12;
    private static final int STORAGE_PRECISION = 38;

    public DirectionalWinInput {
        Objects.requireNonNull(side, "side must not be null");
        requireNullableNumeric(assetReturn);
    }

    private static void requireNullableNumeric(BigDecimal value) {
        if (value == null) {
            return;
        }
        try {
            BigDecimal storageValue = value.setScale(STORAGE_SCALE, RoundingMode.UNNECESSARY);
            if (storageValue.precision() > STORAGE_PRECISION) {
                throw new IllegalArgumentException("assetReturn exceeds NUMERIC(38,12) precision");
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("assetReturn exceeds NUMERIC(38,12) scale", exception);
        }
    }
}
