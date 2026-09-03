package com.wallstreetreceipts.api.application.port.out;

import java.util.Objects;

import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;

public record FilingHistoryCollectionManifestAppendOutcome(
        Status status,
        FilingHistoryCollectionManifest manifest) {

    public FilingHistoryCollectionManifestAppendOutcome {
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(manifest, "manifest must not be null");
    }

    public enum Status {
        INSERTED,
        IDENTICAL_REPLAY
    }
}
