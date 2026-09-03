package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

class SecRetryAfterPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-25T03:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private final SecRetryAfterPolicy policy = new SecRetryAfterPolicy(CLOCK);

    @Test
    void usesMinimumCooldownWhenHeaderIsMissing() {
        assertThat(policy.cooldownFor(new HttpHeaders()))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void usesMinimumCooldownForInvalidValue() {
        HttpHeaders headers = retryAfter("not-a-retry-time");

        assertThat(policy.cooldownFor(headers))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void usesMinimumCooldownForNegativeDeltaSeconds() {
        HttpHeaders headers = retryAfter("-30");

        assertThat(policy.cooldownFor(headers))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void raisesSubMinimumDeltaSecondsToMinimumCooldown() {
        HttpHeaders headers = retryAfter("599");

        assertThat(policy.cooldownFor(headers))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void honorsLongerDeltaSeconds() {
        HttpHeaders headers = retryAfter("901");

        assertThat(policy.cooldownFor(headers))
                .isEqualTo(Duration.ofSeconds(901));
    }

    @Test
    void honorsFutureRfc1123DateBeyondMinimum() {
        Duration expected = Duration.ofMinutes(47);
        HttpHeaders headers = retryAfter(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(NOW.plus(expected), ZoneOffset.UTC)));

        assertThat(policy.cooldownFor(headers)).isEqualTo(expected);
    }

    @Test
    void usesMinimumCooldownForPastRfc1123Date() {
        HttpHeaders headers = retryAfter(DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC)));

        assertThat(policy.cooldownFor(headers))
                .isEqualTo(Duration.ofMinutes(10));
    }

    @Test
    void failsClosedForDeltaSecondsThatOverflowSafeNanosecondRange() {
        HttpHeaders headers = retryAfter("999999999999999999999999999999999999999999");

        assertThat(policy.cooldownFor(headers))
                .isEqualTo(SecRetryAfterPolicy.FAIL_CLOSED_MAXIMUM_COOLDOWN)
                .isGreaterThan(Duration.ofDays(365 * 100L));
    }

    private static HttpHeaders retryAfter(String value) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, value);
        return headers;
    }
}
