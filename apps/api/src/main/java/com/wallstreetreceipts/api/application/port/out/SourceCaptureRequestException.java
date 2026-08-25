package com.wallstreetreceipts.api.application.port.out;

import java.util.Objects;

/**
 * Provider-neutral, sanitized failure from one bounded source-capture invocation.
 *
 * <p>The exception deliberately carries only a closed failure kind and, for a known non-200
 * response, its status code. It never carries response bodies, request headers, credentials, or
 * provider exception text.
 */
public class SourceCaptureRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final FailureKind failureKind;
    private final Integer httpStatus;

    protected SourceCaptureRequestException(
            FailureKind failureKind,
            Integer httpStatus,
            String safeMessage) {
        super(Objects.requireNonNull(safeMessage, "safeMessage must not be null"));
        this.failureKind = Objects.requireNonNull(
                failureKind, "failureKind must not be null");
        if (failureKind == FailureKind.HTTP_STATUS) {
            if (httpStatus == null
                    || httpStatus < 100
                    || httpStatus > 599
                    || httpStatus == 200) {
                throw new IllegalArgumentException(
                        "HTTP_STATUS requires a known non-200 HTTP status");
            }
        } else if (httpStatus != null) {
            throw new IllegalArgumentException(
                    "httpStatus is valid only for HTTP_STATUS");
        }
        this.httpStatus = httpStatus;
    }

    public static SourceCaptureRequestException providerGateClosed() {
        return new SourceCaptureRequestException(
                FailureKind.PROVIDER_GATE_CLOSED,
                null,
                "source capture request was not started because the provider gate is closed");
    }

    public static SourceCaptureRequestException requestFailed() {
        return new SourceCaptureRequestException(
                FailureKind.REQUEST_FAILED,
                null,
                "source capture request failed before response status was known");
    }

    public static SourceCaptureRequestException httpStatus(int status) {
        return new SourceCaptureRequestException(
                FailureKind.HTTP_STATUS,
                status,
                "source capture request failed with HTTP " + status);
    }

    public static SourceCaptureRequestException responseUnreadable() {
        return new SourceCaptureRequestException(
                FailureKind.RESPONSE_UNREADABLE,
                null,
                "source capture response could not be read");
    }

    public static SourceCaptureRequestException responseTooLarge() {
        return new SourceCaptureRequestException(
                FailureKind.RESPONSE_TOO_LARGE,
                null,
                "source capture response exceeded the size limit");
    }

    public static SourceCaptureRequestException responseInvalid() {
        return new SourceCaptureRequestException(
                FailureKind.RESPONSE_INVALID,
                null,
                "source capture response was invalid");
    }

    public FailureKind failureKind() {
        return failureKind;
    }

    public Integer httpStatus() {
        return httpStatus;
    }

    public enum FailureKind {
        PROVIDER_GATE_CLOSED,
        REQUEST_FAILED,
        HTTP_STATUS,
        RESPONSE_UNREADABLE,
        RESPONSE_TOO_LARGE,
        RESPONSE_INVALID
    }
}
