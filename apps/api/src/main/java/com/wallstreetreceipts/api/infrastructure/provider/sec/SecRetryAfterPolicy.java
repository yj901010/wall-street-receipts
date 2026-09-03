package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.math.BigInteger;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import org.springframework.http.HttpHeaders;

/**
 * Computes the process-local cooldown to apply after an SEC rate-limit response.
 *
 * <p>The SEC public API does not guarantee that {@code Retry-After} is present. This policy never
 * authorizes an automatic retry; callers use its result only to open a fail-closed cooldown.
 */
public final class SecRetryAfterPolicy {

    static final Duration MINIMUM_COOLDOWN = Duration.ofMinutes(10);

    /**
     * Largest cooldown that can be represented as nanoseconds without arithmetic overflow.
     *
     * <p>This is roughly 292 years. Returning it for an unrepresentably large server value fails
     * closed while remaining safe for monotonic nanosecond-based cooldown state.
     */
    static final Duration FAIL_CLOSED_MAXIMUM_COOLDOWN = Duration.ofNanos(Long.MAX_VALUE);

    private static final BigInteger MAXIMUM_WHOLE_SECONDS =
            BigInteger.valueOf(FAIL_CLOSED_MAXIMUM_COOLDOWN.getSeconds());

    private final Clock clock;

    public SecRetryAfterPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Returns a conservative cooldown for a rate-limit response.
     *
     * <p>Missing, malformed, expired, or sub-minimum values produce the ten-minute minimum. Valid
     * delta-seconds and RFC 1123 dates longer than the minimum are honored up to the fail-closed
     * representable maximum. Header contents are never included in exceptions or messages.
     */
    public Duration cooldownFor(HttpHeaders responseHeaders) {
        Objects.requireNonNull(responseHeaders, "responseHeaders");

        String retryAfter = responseHeaders.getFirst(HttpHeaders.RETRY_AFTER);
        if (retryAfter == null) {
            return MINIMUM_COOLDOWN;
        }

        String value = retryAfter.trim();
        if (value.isEmpty()) {
            return MINIMUM_COOLDOWN;
        }

        Duration parsedCooldown = isUnsignedDecimal(value)
                ? parseDeltaSeconds(value)
                : parseRfc1123Date(value);
        return enforceBounds(parsedCooldown);
    }

    private Duration parseDeltaSeconds(String value) {
        try {
            BigInteger seconds = new BigInteger(value);
            if (seconds.compareTo(MAXIMUM_WHOLE_SECONDS) > 0) {
                return FAIL_CLOSED_MAXIMUM_COOLDOWN;
            }
            return Duration.ofSeconds(seconds.longValueExact());
        } catch (ArithmeticException | NumberFormatException exception) {
            return FAIL_CLOSED_MAXIMUM_COOLDOWN;
        }
    }

    private Duration parseRfc1123Date(String value) {
        try {
            Instant retryAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME)
                    .toInstant();
            return Duration.between(clock.instant(), retryAt);
        } catch (DateTimeException | ArithmeticException exception) {
            return MINIMUM_COOLDOWN;
        }
    }

    private static Duration enforceBounds(Duration candidate) {
        if (candidate.compareTo(MINIMUM_COOLDOWN) < 0) {
            return MINIMUM_COOLDOWN;
        }
        if (candidate.compareTo(FAIL_CLOSED_MAXIMUM_COOLDOWN) > 0) {
            return FAIL_CLOSED_MAXIMUM_COOLDOWN;
        }
        return candidate;
    }

    private static boolean isUnsignedDecimal(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }
}
