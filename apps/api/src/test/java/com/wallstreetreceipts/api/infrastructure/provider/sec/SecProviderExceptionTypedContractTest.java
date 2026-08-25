package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.application.port.out.SourceCaptureRequestException;
import com.wallstreetreceipts.api.application.port.out.SourceCaptureRequestException.FailureKind;

class SecProviderExceptionTypedContractTest {

    @Test
    void secFailuresExposeOnlyTheClosedProviderNeutralClassification() {
        assertTyped(SecProviderException.requestNotStarted(),
                FailureKind.PROVIDER_GATE_CLOSED, null);
        assertTyped(SecProviderException.unreadableResponse(),
                FailureKind.RESPONSE_UNREADABLE, null);
        assertTyped(SecProviderException.responseTooLarge(),
                FailureKind.RESPONSE_TOO_LARGE, null);
        assertTyped(SecProviderException.invalidResponse(),
                FailureKind.RESPONSE_INVALID, null);
        assertTyped(SecProviderException.httpStatus(429), FailureKind.HTTP_STATUS, 429);
    }

    private static void assertTyped(
            SecProviderException exception,
            FailureKind expectedKind,
            Integer expectedStatus) {
        assertThat(exception).isInstanceOf(SourceCaptureRequestException.class);
        assertThat(exception.failureKind()).isEqualTo(expectedKind);
        assertThat(exception.httpStatus()).isEqualTo(expectedStatus);
        assertThat(exception.getCause()).isNull();
    }
}
