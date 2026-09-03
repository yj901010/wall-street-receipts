package com.wallstreetreceipts.api.application.filinghistory;

/** Closed validation failure for the exact manifest audit query grammar. */
public final class InvalidSecFilingHistoryManifestAuditQueryException
        extends RuntimeException {

    public InvalidSecFilingHistoryManifestAuditQueryException(String message) {
        super(message);
    }
}
