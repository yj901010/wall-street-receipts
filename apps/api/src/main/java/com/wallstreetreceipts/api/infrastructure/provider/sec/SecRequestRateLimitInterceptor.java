package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.io.IOException;
import java.util.Objects;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

/** Applies the shared process-local SEC request gate before any network I/O starts. */
public final class SecRequestRateLimitInterceptor implements ClientHttpRequestInterceptor {

    private final SecRequestRateLimiter rateLimiter;

    public SecRequestRateLimitInterceptor(SecRequestRateLimiter rateLimiter) {
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        try {
            rateLimiter.acquirePermit();
        } catch (SecRequestRateLimiter.SecRequestRateLimitException exception) {
            throw SecProviderException.requestNotStarted();
        }
        return execution.execute(request, body);
    }
}
