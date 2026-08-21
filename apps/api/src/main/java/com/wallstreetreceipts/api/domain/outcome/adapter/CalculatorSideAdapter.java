package com.wallstreetreceipts.api.domain.outcome.adapter;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinSide;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.DirectionalSide;

/**
 * Mechanical translation of an already-resolved directional side into each
 * calculator's side vocabulary.
 *
 * <p>This adapter does not decide direction eligibility, accept a
 * non-directional policy result, invoke a calculator, or attest any horizon,
 * observation, or point-in-time input.</p>
 */
public final class CalculatorSideAdapter {

    private CalculatorSideAdapter() {
    }

    /** Translates the common directional side for target-hit calculation. */
    public static TargetHitSide toTargetHitSide(DirectionalSide side) {
        Objects.requireNonNull(side, "side must not be null");
        return switch (side) {
            case BULLISH -> TargetHitSide.BULLISH;
            case BEARISH -> TargetHitSide.BEARISH;
        };
    }

    /** Translates the common directional side for directional-win calculation. */
    public static DirectionalWinSide toDirectionalWinSide(DirectionalSide side) {
        Objects.requireNonNull(side, "side must not be null");
        return switch (side) {
            case BULLISH -> DirectionalWinSide.BULLISH;
            case BEARISH -> DirectionalWinSide.BEARISH;
        };
    }
}
