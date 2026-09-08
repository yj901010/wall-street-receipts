package com.wallstreetreceipts.api.application.cpi;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;
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
        var started = clock.instant().truncatedTo(ChronoUnit.MICROS);
        int year = started.atZone(ZoneOffset.UTC).getYear();
        if (!repository.claimCollection(started)) return Optional.empty();
        try {
            byte[] raw = fetcher.fetch(key, year, started);
            var snapshot = parser.parse(raw, UUID.randomUUID(),
                    clock.instant().truncatedTo(ChronoUnit.MICROS), year - 3, year);
            repository.append(snapshot, raw, year - 3, year);
            return Optional.of(snapshot);
        } catch (BlsCpiClient.RateLimited exception) {
            repository.postponeCollection(exception.retryAt());
            throw exception;
        }
    }
}
