package com.wallstreetreceipts.api.domain.cpi;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Collector processing evidence, not a provider event, heartbeat or CPI freshness claim. */
public record CpiCollectionAttempt(UUID attemptId, Trigger trigger, Instant startedAt,
                                   boolean permitted, Result result) {
    public enum Trigger { MANUAL, SCHEDULED }
    public enum Status { SAVED, SKIPPED, FAILED, RATE_LIMITED }
    public enum Failure { FETCH, PARSE }

    public CpiCollectionAttempt {
        Objects.requireNonNull(attemptId);
        Objects.requireNonNull(trigger);
        requireTime(startedAt);
        if (result != null && (result.completedAt().isBefore(startedAt)
                || permitted == (result.status() == Status.SKIPPED)
                || result.capturedAt() != null && result.capturedAt().isBefore(startedAt)
                || result.retryNotBefore() != null && result.retryNotBefore().isBefore(startedAt))) {
            throw new IllegalArgumentException("Invalid CPI attempt chronology or gate result");
        }
    }

    /** Null result means no terminal record, NOT proof of a currently running attempt. */
    public record Result(Instant completedAt, Status status, UUID captureId, Instant capturedAt,
                         Failure failure, Instant retryNotBefore) {
        public Result {
            requireTime(completedAt);
            Objects.requireNonNull(status);
            boolean valid = switch (status) {
                case SAVED -> captureId != null && capturedAt != null && failure == null && retryNotBefore == null;
                case SKIPPED -> captureId == null && capturedAt == null && failure == null && retryNotBefore == null;
                case FAILED -> captureId == null && capturedAt == null && failure != null && retryNotBefore == null;
                case RATE_LIMITED -> captureId == null && capturedAt == null && failure == null && retryNotBefore != null;
            };
            if (!valid) throw new IllegalArgumentException("Invalid CPI attempt result shape");
            if (capturedAt != null) {
                requireTime(capturedAt);
                if (capturedAt.isAfter(completedAt)) throw new IllegalArgumentException("Invalid CPI capture chronology");
            }
            if (retryNotBefore != null) requireTime(retryNotBefore);
        }
    }

    private static void requireTime(Instant value) {
        Objects.requireNonNull(value);
        if (value.getNano() % 1_000 != 0) throw new IllegalArgumentException("CPI attempt time requires microseconds");
    }
}
