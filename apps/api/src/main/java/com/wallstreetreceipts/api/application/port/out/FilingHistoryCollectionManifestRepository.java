package com.wallstreetreceipts.api.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;

public interface FilingHistoryCollectionManifestRepository {

    FilingHistoryCollectionManifestAppendOutcome append(
            FilingHistoryCollectionManifest manifest);

    Optional<FilingHistoryCollectionManifest> findByManifestId(String manifestId);

    Optional<FilingHistoryCollectionManifest> findByManifestIdAtOrBefore(
            String manifestId,
            Instant evaluationAsOf);

    long count();
}
