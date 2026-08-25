package com.wallstreetreceipts.api.infrastructure.provider.sec;

public final class SecProviderConfigurationException extends RuntimeException {

    public SecProviderConfigurationException(String safeMessage) {
        super(safeMessage);
    }
}
