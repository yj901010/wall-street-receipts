package com.wallstreetreceipts.api.domain.outcome.calculation;

import java.math.BigDecimal;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult.Available;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinResult.UnavailableReason;

/**
 * Deterministic directional-win comparison over an already-calculated return.
 *
 * <p>Bullish forecasts win only for a strictly positive return and bearish
 * forecasts win only for a strictly negative return. Exact zero is a miss for
 * both sides. This calculation performs no arithmetic or rounding.</p>
 */
public final class DirectionalWinCalculator {

    private DirectionalWinCalculator() {
    }

    public static DirectionalWinResult calculate(DirectionalWinInput input) {
        Objects.requireNonNull(input, "input must not be null");

        if (input.assetReturn() == null) {
            return new Unavailable(UnavailableReason.ASSET_RETURN_MISSING);
        }

        int comparison = input.assetReturn().compareTo(BigDecimal.ZERO);
        boolean directionalWin = switch (input.side()) {
            case BULLISH -> comparison > 0;
            case BEARISH -> comparison < 0;
        };
        return new Available(directionalWin);
    }
}
