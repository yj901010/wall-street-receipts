package com.wallstreetreceipts.api.infrastructure.provider.sec;

public final class SecProviderException extends RuntimeException {

    private SecProviderException(String safeMessage) {
        super(safeMessage);
    }

    static SecProviderException httpStatus(int statusCode) {
        return new SecProviderException("SEC submissions request failed with HTTP " + statusCode);
    }

    static SecProviderException unreadableResponse() {
        return new SecProviderException("SEC submissions response could not be read");
    }

    static SecProviderException invalidResponse() {
        return new SecProviderException("SEC submissions response was invalid");
    }

    static SecProviderException responseTooLarge() {
        return new SecProviderException("SEC submissions response exceeded the size limit");
    }

    static SecProviderException requestNotStarted() {
        return new SecProviderException(
                "SEC submissions request was not started because the provider gate is closed");
    }
}
