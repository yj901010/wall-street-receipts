package com.wallstreetreceipts.api.application.filinghistory;

/** Exact attempt lookup failure without a latest or alternate-identity fallback. */
public final class SecFilingHistoryCollectionAttemptNotFoundException extends RuntimeException {

    public SecFilingHistoryCollectionAttemptNotFoundException(String attemptId) {
        super("SEC filing-history collection attempt was not found: " + attemptId);
    }
}
