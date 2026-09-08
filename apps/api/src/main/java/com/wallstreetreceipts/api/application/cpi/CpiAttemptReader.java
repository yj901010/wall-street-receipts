package com.wallstreetreceipts.api.application.cpi;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

/** Read-only operational evidence port; no collection, gate mutation or raw receipt access. */
public interface CpiAttemptReader {
    int RECENT_LIMIT = 20;
    Optional<CpiCollectionAttempt> findAttempt(UUID id);
    /** At most 21 rows, ordered by started_at DESC, canonical attempt_id DESC. */
    List<CpiCollectionAttempt> recentAttempts();
}
