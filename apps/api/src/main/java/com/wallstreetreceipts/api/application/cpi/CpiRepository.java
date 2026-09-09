package com.wallstreetreceipts.api.application.cpi;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;

public interface CpiRepository extends CpiAttemptReader {
    /** Atomically persist a new start and the durable gate decision before HTTP. */
    boolean beginAttempt(UUID id, CpiCollectionAttempt.Trigger trigger, Instant startedAt);
    /** Non-SAVED result; RATE_LIMITED also extends the gate in the same transaction. */
    void finishAttempt(UUID id, CpiCollectionAttempt.Result result);
    /** Receipt and SAVED result must commit together. */
    void saveAttempt(UUID id, CpiSnapshot snapshot, byte[] raw, int startYear, int endYear, Instant completedAt);
    boolean claimCollection(Instant now);
    void postponeCollection(Instant until);
    void append(CpiSnapshot snapshot, byte[] raw, int startYear, int endYear);
    Optional<CpiSnapshot> latest(Instant noLaterThan);
}
