package com.wallstreetreceipts.api.application.call;

public final class AnalystCallNotFoundException extends RuntimeException {

    private final String callId;

    public AnalystCallNotFoundException(String callId) {
        super("No analyst call exists for id '" + callId + "'");
        this.callId = callId;
    }

    public String callId() {
        return callId;
    }
}
