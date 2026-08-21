package com.wallstreetreceipts.api.domain.outcome.routing;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.adapter.CalculatorSideAdapter;
import com.wallstreetreceipts.api.domain.outcome.calculation.DirectionalWinSide;
import com.wallstreetreceipts.api.domain.outcome.calculation.TargetHitSide;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.Directional;
import com.wallstreetreceipts.api.domain.outcome.direction.CallDirectionPolarityResolution.NonDirectional;

/**
 * Preserves one closed polarity result as either exact calculator-side evidence
 * or explicit non-directional evidence.
 *
 * <p>This routing does not select a target, return, horizon, or observation. It
 * does not construct a calculator input, invoke a calculator, or publish a
 * metric result.</p>
 */
public final class CalculatorSideRouting {

    private CalculatorSideRouting() {
    }

    /** Routes one complete polarity resolution without reinterpreting it. */
    public static Result route(CallDirectionPolarityResolution resolution) {
        Objects.requireNonNull(resolution, "resolution must not be null");

        return switch (resolution) {
            case Directional directional -> new DirectionalRoute(
                    directional,
                    CalculatorSideAdapter.toTargetHitSide(directional.side()),
                    CalculatorSideAdapter.toDirectionalWinSide(directional.side()));
            case NonDirectional nonDirectional ->
                    new NonDirectionalRoute(nonDirectional);
        };
    }

    /** The two exact evidence branches produced by routing. */
    public sealed interface Result permits DirectionalRoute, NonDirectionalRoute {
    }

    /**
     * Preserves the directional source together with both mechanically derived
     * calculator-side enum values.
     */
    public record DirectionalRoute(
            Directional source,
            TargetHitSide targetHitSide,
            DirectionalWinSide directionalWinSide) implements Result {

        public DirectionalRoute {
            Objects.requireNonNull(source, "source must not be null");
            Objects.requireNonNull(targetHitSide, "targetHitSide must not be null");
            Objects.requireNonNull(
                    directionalWinSide, "directionalWinSide must not be null");

            TargetHitSide expectedTargetHitSide =
                    CalculatorSideAdapter.toTargetHitSide(source.side());
            DirectionalWinSide expectedDirectionalWinSide =
                    CalculatorSideAdapter.toDirectionalWinSide(source.side());
            if (targetHitSide != expectedTargetHitSide) {
                throw new IllegalArgumentException(
                        "targetHitSide must match the directional source");
            }
            if (directionalWinSide != expectedDirectionalWinSide) {
                throw new IllegalArgumentException(
                        "directionalWinSide must match the directional source");
            }
        }
    }

    /** Preserves non-directional policy evidence without adding a side. */
    public record NonDirectionalRoute(
            NonDirectional source) implements Result {

        public NonDirectionalRoute {
            Objects.requireNonNull(source, "source must not be null");
        }
    }
}
