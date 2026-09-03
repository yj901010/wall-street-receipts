package com.wallstreetreceipts.api.infrastructure.provider.sec;

import com.wallstreetreceipts.api.application.port.out.SourceCaptureRequestException;

public final class SecProviderException extends SourceCaptureRequestException {

    private static final long serialVersionUID = 1L;

    private SecProviderException(
            FailureKind failureKind,
            Integer httpStatus,
            String safeMessage) {
        super(failureKind, httpStatus, safeMessage);
    }

    public static SecProviderException httpStatus(int statusCode) {
        return new SecProviderException(
                FailureKind.HTTP_STATUS,
                statusCode,
                "SEC submissions request failed with HTTP " + statusCode);
    }

    public static SecProviderException unreadableResponse() {
        return new SecProviderException(
                FailureKind.RESPONSE_UNREADABLE,
                null,
                "SEC submissions response could not be read");
    }

    public static SecProviderException invalidResponse() {
        return new SecProviderException(
                FailureKind.RESPONSE_INVALID,
                null,
                "SEC submissions response was invalid");
    }

    public static SecProviderException responseTooLarge() {
        return new SecProviderException(
                FailureKind.RESPONSE_TOO_LARGE,
                null,
                "SEC submissions response exceeded the size limit");
    }

    public static SecProviderException requestNotStarted() {
        return new SecProviderException(
                FailureKind.PROVIDER_GATE_CLOSED,
                null,
                "SEC submissions request was not started because the provider gate is closed");
    }
}
