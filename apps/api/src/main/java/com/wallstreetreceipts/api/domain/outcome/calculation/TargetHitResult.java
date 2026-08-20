package com.wallstreetreceipts.api.domain.outcome.calculation;

import java.util.Objects;

/**
 * A target-hit value or an explicit account of the evidence that was missing.
 */
public sealed interface TargetHitResult
        permits TargetHitResult.Available, TargetHitResult.Unavailable {

    /** A calculated target-hit value backed by all required inputs. */
    record Available(boolean targetHit) implements TargetHitResult {
    }

    /** Required inputs that can make a target-hit value unavailable. */
    enum UnavailableReason {
        TARGET_MISSING,
        FAVORABLE_EXTREME_MISSING,
        TARGET_AND_FAVORABLE_EXTREME_MISSING
    }

    /**
     * No target-hit value was calculated because one or more required inputs
     * were missing.
     */
    record Unavailable(UnavailableReason reason) implements TargetHitResult {

        public Unavailable {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
