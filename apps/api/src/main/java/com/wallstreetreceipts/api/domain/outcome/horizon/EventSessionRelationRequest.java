package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.time.Instant;
import java.util.Objects;

import com.wallstreetreceipts.api.domain.PersistentInstant;

/** Explicit inputs for classifying one event against one supplied session catalog. */
public record EventSessionRelationRequest(
        EventSessionRelationPolicyVersion policyVersion,
        Instant eventTime,
        TradingSessionCatalog catalog) {

    public EventSessionRelationRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        PersistentInstant.requireMicrosecondPrecision(eventTime, "eventTime");
        Objects.requireNonNull(catalog, "catalog must not be null");
    }
}
