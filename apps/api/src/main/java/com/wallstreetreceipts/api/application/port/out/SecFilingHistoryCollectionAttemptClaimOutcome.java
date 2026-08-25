package com.wallstreetreceipts.api.application.port.out;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;

/** Durable claim result for an operator idempotency key and its exact command. */
public record SecFilingHistoryCollectionAttemptClaimOutcome(
        Status status,
        SecFilingHistoryCollectionAttempt attempt) {

    public SecFilingHistoryCollectionAttemptClaimOutcome {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(attempt, "attempt must not be null");
    }

    public enum Status {
        CLAIMED,
        IDENTICAL_REPLAY
    }
}
