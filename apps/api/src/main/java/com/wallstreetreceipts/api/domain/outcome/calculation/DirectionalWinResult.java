package com.wallstreetreceipts.api.domain.outcome.calculation;

import java.util.Objects;

/** A directional-win value or an explicit missing-evidence result. */
public sealed interface DirectionalWinResult
        permits DirectionalWinResult.Available, DirectionalWinResult.Unavailable {

    /** A directional-win value backed by a provided asset return. */
    record Available(boolean directionalWin) implements DirectionalWinResult {
    }

    /** Evidence whose absence makes directional win unavailable. */
    enum UnavailableReason {
        ASSET_RETURN_MISSING
    }

    /** No directional-win value was calculated because the return was missing. */
    record Unavailable(UnavailableReason reason) implements DirectionalWinResult {

        public Unavailable {
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }
}
