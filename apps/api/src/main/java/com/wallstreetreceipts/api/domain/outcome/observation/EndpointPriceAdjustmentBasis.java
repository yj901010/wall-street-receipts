package com.wallstreetreceipts.api.domain.outcome.observation;

/** Corporate-action and dividend basis carried by a price or target. */
public enum EndpointPriceAdjustmentBasis {
    SPLIT_AND_REVERSE_SPLIT_ADJUSTED_TO_ENDPOINT_SHARE_BASIS_DIVIDEND_UNADJUSTED,
    UNADJUSTED_OR_OTHER,
    DIVIDEND_OR_TOTAL_RETURN_ADJUSTED
}
