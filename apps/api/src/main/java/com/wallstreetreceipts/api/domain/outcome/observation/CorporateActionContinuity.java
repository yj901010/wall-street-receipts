package com.wallstreetreceipts.api.domain.outcome.observation;

/** Whether the asset remains price-comparable through the endpoint session. */
public enum CorporateActionContinuity {
    SPLIT_REVERSE_SPLIT_CONTINUOUS,
    MERGER,
    SPIN_OFF,
    DELISTING,
    SPECIAL_DISTRIBUTION,
    UNKNOWN
}
