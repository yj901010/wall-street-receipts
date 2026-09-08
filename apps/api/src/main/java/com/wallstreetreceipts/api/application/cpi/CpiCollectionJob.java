package com.wallstreetreceipts.api.application.cpi;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiClient;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;

/** One bounded attempt shared by the manual command and the separate daily worker. */
public final class CpiCollectionJob {
    @FunctionalInterface
    public interface Fetcher {
        byte[] fetch(String key, int year, Instant started);
    }

    private final CpiRepository repository;
    private final BlsCpiParser parser;
    private final Clock clock;
    private final String key;
    private final Fetcher fetcher;

    public CpiCollectionJob(CpiRepository repository, BlsCpiParser parser, Clock clock,
                            String key, Fetcher fetcher) {
        if (key == null || !key.matches("[A-Za-z0-9]{16,128}")) {
            throw new IllegalArgumentException("BLS_REGISTRATION_KEY needs attention");
        }
        this.repository = repository;
        this.parser = parser;
        this.clock = clock;
        this.key = key;
        this.fetcher = fetcher;
    }

    /** Empty means the durable gate denied the attempt, not a successful retrieval. */
    public Optional<CpiSnapshot> collect() {
        return collect(Trigger.MANUAL);
    }

    public Optional<CpiSnapshot> collect(Trigger trigger) {
        java.util.Objects.requireNonNull(trigger);
        var started = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int year = started.atZone(ZoneOffset.UTC).getYear();
        var attemptId = UUID.randomUUID();
        if (!repository.beginAttempt(attemptId, trigger, started)) {
            repository.finishAttempt(attemptId, new Result(now(), Status.SKIPPED, null, null, null, null));
            return Optional.empty();
        }
        byte[] raw;
        try {
            raw = fetcher.fetch(key, year, started);
        } catch (BlsCpiClient.RateLimited exception) {
            repository.finishAttempt(attemptId, new Result(now(), Status.RATE_LIMITED, null, null, null,
                    exception.retryAt().truncatedTo(ChronoUnit.MICROS)));
            throw exception;
        } catch (RuntimeException exception) {
            repository.finishAttempt(attemptId, new Result(now(), Status.FAILED, null, null, Failure.FETCH, null));
            throw exception;
        }
        CpiSnapshot snapshot;
        try {
            snapshot = parser.parse(raw, UUID.randomUUID(), now(), year - 3, year);
        } catch (RuntimeException exception) {
            repository.finishAttempt(attemptId, new Result(now(), Status.FAILED, null, null, Failure.PARSE, null));
            throw exception;
        }
        // A persistence exception may have an unknown commit outcome. Do not
        // append FAILED, retry, or report SAVED when this operation throws.
        repository.saveAttempt(attemptId, snapshot, raw, year - 3, year, now());
        return Optional.of(snapshot);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
