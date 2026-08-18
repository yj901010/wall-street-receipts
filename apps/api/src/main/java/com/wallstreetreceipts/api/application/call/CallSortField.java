package com.wallstreetreceipts.api.application.call;

import java.util.Arrays;

public enum CallSortField {
    EVENT_TIME("eventTime"),
    PROCESSING_TIME("processingTime"),
    CAPTURED_AT("capturedAt");

    private final String apiName;

    CallSortField(String apiName) {
        this.apiName = apiName;
    }

    public String apiName() {
        return apiName;
    }

    public static CallSortField fromApiName(String value) {
        return Arrays.stream(values())
                .filter(candidate -> candidate.apiName.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported sort field: " + value));
    }
}
