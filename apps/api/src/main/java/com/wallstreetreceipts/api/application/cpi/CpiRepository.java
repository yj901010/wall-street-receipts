package com.wallstreetreceipts.api.application.cpi;

import java.time.Instant;
import java.util.Optional;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;

public interface CpiRepository {
    boolean claimCollection(Instant now);
    void postponeCollection(Instant until);
    void append(CpiSnapshot snapshot, byte[] raw, int startYear, int endYear);
    Optional<CpiSnapshot> latest(Instant noLaterThan);
}
