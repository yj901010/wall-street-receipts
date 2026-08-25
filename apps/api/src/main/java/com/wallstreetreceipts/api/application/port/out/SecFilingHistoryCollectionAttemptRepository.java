package com.wallstreetreceipts.api.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;

/** Append-only operational ledger; it never selects source evidence by latest CIK or clock. */
public interface SecFilingHistoryCollectionAttemptRepository {

    SecFilingHistoryCollectionAttemptClaimOutcome claim(
            SecFilingHistoryCollectionAttempt plannedAttempt);

    SecFilingHistoryCollectionAttempt appendProviderDispatch(
            String attemptId,
            ProviderDispatch dispatch);

    SecFilingHistoryCollectionAttempt appendTerminalOutcome(
            String attemptId,
            TerminalOutcome outcome);

    Optional<SecFilingHistoryCollectionAttempt> findByAttemptId(String attemptId);

    Optional<SecFilingHistoryCollectionAttempt> findByAttemptIdAtOrBefore(
            String attemptId,
            Instant evaluationAsOf);

    long count();
}
