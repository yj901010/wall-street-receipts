package com.wallstreetreceipts.api.application.filinghistory;

/** Raised when an operator request UUID is already bound to another immutable command. */
public final class OperatorRequestConflictException extends IllegalArgumentException {

    public OperatorRequestConflictException() {
        super("operatorRequestId is already bound to another command");
    }
}
