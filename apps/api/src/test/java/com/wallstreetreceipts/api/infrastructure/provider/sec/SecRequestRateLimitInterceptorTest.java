package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;

class SecRequestRateLimitInterceptorTest {

    @Test
    void doesNotExecuteNetworkCallWhileSharedCooldownIsActive() {
        SecRequestRateLimiter rateLimiter = new SecRequestRateLimiter();
        rateLimiter.applyCooldown(Duration.ofMinutes(10));
        SecRequestRateLimitInterceptor interceptor =
                new SecRequestRateLimitInterceptor(rateLimiter);
        AtomicBoolean executed = new AtomicBoolean();

        assertThatThrownBy(() -> interceptor.intercept(
                        mock(HttpRequest.class),
                        new byte[0],
                        (request, body) -> {
                            executed.set(true);
                            throw new AssertionError("network execution must remain closed");
                        }))
                .isInstanceOf(SecProviderException.class)
                .hasMessage(
                        "SEC submissions request was not started because the provider gate is closed")
                .hasNoCause();

        assertThat(executed).isFalse();
    }
}
