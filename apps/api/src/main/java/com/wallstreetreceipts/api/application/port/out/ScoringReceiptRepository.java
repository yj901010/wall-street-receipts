package com.wallstreetreceipts.api.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Insert/select only; caller holds the original call lock for idempotent appends. No update/delete API. */
public interface ScoringReceiptRepository {
    boolean lockCall(String callId);
    void insert(Entry entry);
    Optional<Entry> findByIdentity(String inputFingerprint, String ledgerFingerprint);
    Optional<Entry> find(String callId, UUID receiptId);
    List<Entry> recent(String callId); // At most 21 rows; public limit is 20 with explicit hasMore.

    record Entry(UUID receiptId, String callId, String snapshotId, String basisRevisionId, Integer basisRevisionSequence,
            String methodologyId, String methodologyVersion, String methodologyDefinitionHash, String methodologyDefinition,
            String inputFingerprint, String ledgerFingerprint, Instant evaluationAsOf, Instant recordedAt, byte[] inputBytes) {
        public Entry { inputBytes = inputBytes.clone(); }
        @Override public byte[] inputBytes() { return inputBytes.clone(); }
    }
}
