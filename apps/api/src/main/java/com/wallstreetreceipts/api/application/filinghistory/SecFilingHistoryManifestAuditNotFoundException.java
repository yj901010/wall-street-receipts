package com.wallstreetreceipts.api.application.filinghistory;

/** Exact point-in-time lookup failure without an unrestricted or latest fallback. */
public final class SecFilingHistoryManifestAuditNotFoundException
        extends RuntimeException {

    public SecFilingHistoryManifestAuditNotFoundException() {
        super("SEC filing-history manifest was not found at the evaluation cutoff");
    }
}
