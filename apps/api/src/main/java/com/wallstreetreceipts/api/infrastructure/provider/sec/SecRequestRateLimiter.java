package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Process-local, aggregate request-spacing and cooldown guard for SEC HTTP traffic.
 *
 * <p>The limiter deliberately does not accumulate unused capacity. A shared instance therefore
 * spaces every granted permit, including after idle periods, and cannot emit a token-bucket burst.
 * Cooldowns are shared only by callers of that instance inside one JVM; multi-process deployments
 * require a separate distributed coordination mechanism.
 */
public final class SecRequestRateLimiter {

    public static final int DEFAULT_MAX_REQUESTS_PER_SECOND = 8;

    private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);

    private final long minimumIntervalNanos;
    private final MonotonicTimeSource monotonicTimeSource;
    private final NanoSleeper sleeper;
    private final Object monitor = new Object();

    private boolean permitIssued;
    private long lastPermitNanos;
    private boolean cooldownActive;
    private long cooldownStartedNanos;
    private long cooldownDurationNanos;

    public SecRequestRateLimiter() {
        this(DEFAULT_MAX_REQUESTS_PER_SECOND);
    }

    public SecRequestRateLimiter(int maximumRequestsPerSecond) {
        this(maximumRequestsPerSecond, System::nanoTime, SecRequestRateLimiter::sleepNanos);
    }

    SecRequestRateLimiter(
            int maximumRequestsPerSecond,
            MonotonicTimeSource monotonicTimeSource,
            NanoSleeper sleeper) {
        if (maximumRequestsPerSecond < 1
                || maximumRequestsPerSecond > DEFAULT_MAX_REQUESTS_PER_SECOND) {
            throw new IllegalArgumentException(
                    "SEC request rate must be between 1 and "
                            + DEFAULT_MAX_REQUESTS_PER_SECOND + " requests per second");
        }
        this.minimumIntervalNanos =
                ceilDivide(NANOS_PER_SECOND, maximumRequestsPerSecond);
        this.monotonicTimeSource = Objects.requireNonNull(
                monotonicTimeSource, "monotonicTimeSource");
        this.sleeper = Objects.requireNonNull(sleeper, "sleeper");
    }

    /**
     * Waits until the next request may start or throws without granting a permit.
     *
     * <p>Callers must share this limiter across every SEC HTTP adapter in the process.
     */
    public void acquirePermit() {
        boolean spacingWaitCompleted = false;
        long observedBeforeSpacingWait = 0;

        while (true) {
            rejectInterruptedCaller();

            long remainingNanos;
            synchronized (monitor) {
                long observedNanos = monotonicTimeSource.readNanos();
                if (spacingWaitCompleted) {
                    requireMonotonicProgress(observedBeforeSpacingWait, observedNanos);
                }
                rejectActiveCooldown(observedNanos);
                if (!permitIssued) {
                    issuePermit(observedNanos);
                    return;
                }

                long elapsedNanos = elapsedSinceLastPermit(observedNanos);
                if (elapsedNanos >= minimumIntervalNanos) {
                    issuePermit(observedNanos);
                    return;
                }

                remainingNanos = minimumIntervalNanos - elapsedNanos;
                observedBeforeSpacingWait = observedNanos;
            }

            // Never hold the state monitor while waiting. A concurrent HTTP 429 must be able to
            // publish its cooldown before this caller can re-check and issue a permit.
            sleepOrFailClosed(remainingNanos);
            spacingWaitCompleted = true;
        }
    }

    private static void requireMonotonicProgress(long previousNanos, long observedNanos) {
        long progressNanos = observedNanos - previousNanos;
        if (progressNanos < 0) {
            throw SecRequestRateLimitException.monotonicTimeRegressed();
        }
        if (progressNanos == 0) {
            throw SecRequestRateLimitException.monotonicTimeDidNotAdvance();
        }
    }

    /**
     * Applies a process-local cooldown calculated by the SEC HTTP response handler.
     *
     * <p>A shorter cooldown never replaces a longer remaining cooldown. This method records only
     * monotonic elapsed time and never uses a wall clock.
     *
     * @param duration positive cooldown duration, commonly derived from a validated Retry-After
     *        response
     */
    public void applyCooldown(Duration duration) {
        long requestedDurationNanos = requireValidCooldown(duration);

        synchronized (monitor) {
            long observedNanos = monotonicTimeSource.readNanos();
            if (cooldownActive) {
                long remainingNanos = remainingCooldownNanos(observedNanos);
                if (remainingNanos > 0 && requestedDurationNanos <= remainingNanos) {
                    return;
                }
            }

            cooldownStartedNanos = observedNanos;
            cooldownDurationNanos = requestedDurationNanos;
            cooldownActive = true;
        }
    }

    private void rejectActiveCooldown(long observedNanos) {
        if (cooldownActive && remainingCooldownNanos(observedNanos) > 0) {
            throw SecRequestRateLimitException.cooldownActive();
        }
    }

    private long remainingCooldownNanos(long observedNanos) {
        long elapsedNanos = observedNanos - cooldownStartedNanos;
        if (elapsedNanos < 0) {
            throw SecRequestRateLimitException.monotonicTimeRegressed();
        }
        if (elapsedNanos >= cooldownDurationNanos) {
            cooldownActive = false;
            cooldownDurationNanos = 0;
            return 0;
        }
        return cooldownDurationNanos - elapsedNanos;
    }

    private long elapsedSinceLastPermit(long observedNanos) {
        long elapsedNanos = observedNanos - lastPermitNanos;
        if (elapsedNanos < 0) {
            throw SecRequestRateLimitException.monotonicTimeRegressed();
        }
        return elapsedNanos;
    }

    private void issuePermit(long observedNanos) {
        lastPermitNanos = observedNanos;
        permitIssued = true;
    }

    private void sleepOrFailClosed(long durationNanos) {
        try {
            sleeper.sleep(durationNanos);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw SecRequestRateLimitException.interrupted();
        }
    }

    private static void rejectInterruptedCaller() {
        if (Thread.currentThread().isInterrupted()) {
            throw SecRequestRateLimitException.interrupted();
        }
    }

    private static void sleepNanos(long durationNanos) throws InterruptedException {
        TimeUnit.NANOSECONDS.sleep(durationNanos);
    }

    private static long ceilDivide(long dividend, long divisor) {
        return dividend / divisor + (dividend % divisor == 0 ? 0 : 1);
    }

    private static long requireValidCooldown(Duration duration) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException("SEC cooldown duration must be positive");
        }
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException(
                    "SEC cooldown duration exceeds monotonic nanosecond range");
        }
    }

    @FunctionalInterface
    interface MonotonicTimeSource {
        long readNanos();
    }

    @FunctionalInterface
    interface NanoSleeper {
        void sleep(long durationNanos) throws InterruptedException;
    }

    public static final class SecRequestRateLimitException extends RuntimeException {

        private SecRequestRateLimitException(String safeMessage) {
            super(safeMessage);
        }

        private static SecRequestRateLimitException interrupted() {
            return new SecRequestRateLimitException(
                    "SEC request was not started because rate-limit waiting was interrupted");
        }

        private static SecRequestRateLimitException cooldownActive() {
            return new SecRequestRateLimitException(
                    "SEC request was not started because a provider cooldown is active");
        }

        private static SecRequestRateLimitException monotonicTimeRegressed() {
            return new SecRequestRateLimitException(
                    "SEC request was not started because monotonic time regressed");
        }

        private static SecRequestRateLimitException monotonicTimeDidNotAdvance() {
            return new SecRequestRateLimitException(
                    "SEC request was not started because monotonic time did not advance");
        }
    }
}
