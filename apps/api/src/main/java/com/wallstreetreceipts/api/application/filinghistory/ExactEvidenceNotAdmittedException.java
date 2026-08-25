package com.wallstreetreceipts.api.application.filinghistory;

/** Raised when exact evidence fails the transactional foreign-key admission boundary. */
public final class ExactEvidenceNotAdmittedException extends IllegalArgumentException {

    public ExactEvidenceNotAdmittedException() {
        super("collection attempt exact evidence was not accepted");
    }
}
