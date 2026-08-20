package com.wallstreetreceipts.api.domain.outcome.calculation;

import java.math.BigDecimal;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult.Available;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult.Unavailable;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitResult.UnavailableReason;

/**
 * Deterministic target-hit comparison over already-selected, normalized inputs.
 *
 * <p>The comparison is inclusive and performs no rounding: bullish forecasts
 * hit when the window high is at least the target; bearish forecasts hit when
 * the window low is at most the target. Horizon/window selection,
 * corporate-action adjustment, and currency normalization are upstream
 * responsibilities.</p>
 */
public final class TargetHitCalculator {

    private TargetHitCalculator() {
    }

    public static TargetHitResult calculate(TargetHitInput input) {
        Objects.requireNonNull(input, "input must not be null");

        if (input.target() == null && input.favorableExtreme() == null) {
            return new Unavailable(UnavailableReason.TARGET_AND_FAVORABLE_EXTREME_MISSING);
        }
        if (input.target() == null) {
            return new Unavailable(UnavailableReason.TARGET_MISSING);
        }
        if (input.favorableExtreme() == null) {
            return new Unavailable(UnavailableReason.FAVORABLE_EXTREME_MISSING);
        }

        BigDecimal favorableExtreme = input.favorableExtreme();
        int comparison = favorableExtreme.compareTo(input.target());
        boolean targetHit = switch (input.side()) {
            case BULLISH -> comparison >= 0;
            case BEARISH -> comparison <= 0;
        };
        return new Available(targetHit);
    }
}
