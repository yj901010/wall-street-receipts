package com.wallstreetreceipts.api.domain.outcome.horizon;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;

/** Explicit inputs for resolving one named horizon for one forecast basis. */
public record SessionCloseHorizonRequest(
        SessionCloseHorizonPolicyVersion policyVersion,
        OutcomeBasis basis,
        OutcomeHorizon horizon,
        TradingSessionCatalog catalog) {

    public SessionCloseHorizonRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(basis, "basis must not be null");
        Objects.requireNonNull(horizon, "horizon must not be null");
        Objects.requireNonNull(catalog, "catalog must not be null");
    }
}
