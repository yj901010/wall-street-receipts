package com.wallstreetreceipts.api.domain.outcome.direction;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.call.CallDirection;

/** Explicit inputs for reducing one caller-selected canonical direction. */
public record CallDirectionPolarityRequest(
        CallDirectionPolarityPolicyVersion policyVersion,
        CallDirection direction) {

    public CallDirectionPolarityRequest {
        Objects.requireNonNull(policyVersion, "policyVersion must not be null");
        Objects.requireNonNull(direction, "direction must not be null");
    }
}
