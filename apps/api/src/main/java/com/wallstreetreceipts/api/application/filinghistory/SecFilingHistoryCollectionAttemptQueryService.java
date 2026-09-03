package com.wallstreetreceipts.api.application.filinghistory;

import java.util.Objects;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;

/** Exact operational lookup over the immutable ADR-042 attempt identity. */
public final class SecFilingHistoryCollectionAttemptQueryService {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    private final SecFilingHistoryCollectionAttemptRepository repository;

    public SecFilingHistoryCollectionAttemptQueryService(
            SecFilingHistoryCollectionAttemptRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public SecFilingHistoryCollectionAttempt findByAttemptId(String attemptId) {
        if (attemptId == null || !SHA_256.matcher(attemptId).matches()) {
            throw new IllegalArgumentException("attemptId must be lowercase SHA-256 hex");
        }
        return repository.findByAttemptId(attemptId)
                .orElseThrow(() ->
                        new SecFilingHistoryCollectionAttemptNotFoundException(attemptId));
    }
}
