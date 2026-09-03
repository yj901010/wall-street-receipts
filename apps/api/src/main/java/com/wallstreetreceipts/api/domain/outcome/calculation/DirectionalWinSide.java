package com.wallstreetreceipts.api.domain.outcome.calculation;

/**
 * The already-resolved forecast side used by the directional-win comparison.
 *
 * <p>Mapping richer analyst-call directions to this deliberately smaller set is
 * an upstream methodology decision and is not performed by this calculation
 * core.</p>
 */
public enum DirectionalWinSide {
    BULLISH,
    BEARISH
}
