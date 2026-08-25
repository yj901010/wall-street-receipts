package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class SecRequestRateLimiterTest {

    private static final long DEFAULT_INTERVAL_NANOS = 125_000_000L;

    @Test
    void defaultsToEightRequestsPerSecondWithoutAccumulatingAnIdleBurst() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = limiter(time, sleeps);

        limiter.acquirePermit();
        limiter.acquirePermit();

        assertThat(sleeps).containsExactly(DEFAULT_INTERVAL_NANOS);
        assertThat(time.readNanos()).isEqualTo(DEFAULT_INTERVAL_NANOS);

        time.advance(TimeUnit.SECONDS.toNanos(10));
        limiter.acquirePermit();
        limiter.acquirePermit();

        assertThat(sleeps).containsExactly(
                DEFAULT_INTERVAL_NANOS,
                DEFAULT_INTERVAL_NANOS);
    }

    @Test
    void serializesConcurrentCallersIntoOneAggregateMinimumInterval() throws Exception {
        FakeMonotonicTime time = new FakeMonotonicTime();
        List<Long> sleeps = Collections.synchronizedList(new ArrayList<>());
        SecRequestRateLimiter limiter = limiter(time, sleeps);
        int callerCount = 24;
        ExecutorService executor = Executors.newFixedThreadPool(callerCount);
        CountDownLatch ready = new CountDownLatch(callerCount);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int caller = 0; caller < callerCount; caller++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    limiter.acquirePermit();
                    return null;
                }));
            }

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(sleeps).hasSizeGreaterThanOrEqualTo(callerCount - 1)
                .allMatch(duration -> duration > 0 && duration <= DEFAULT_INTERVAL_NANOS);
        assertThat(time.readNanos())
                .isGreaterThanOrEqualTo(DEFAULT_INTERVAL_NANOS * (callerCount - 1));
    }

    @Test
    void roundsMinimumIntervalUpForLowerConfiguredRates() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = new SecRequestRateLimiter(
                3,
                time,
                duration -> {
                    sleeps.add(duration);
                    time.advance(duration);
                });

        limiter.acquirePermit();
        limiter.acquirePermit();

        assertThat(sleeps).containsExactly(333_333_334L);
    }

    @Test
    void failsClosedAndRestoresInterruptWhenWaitingIsInterrupted() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        SecRequestRateLimiter limiter = new SecRequestRateLimiter(
                SecRequestRateLimiter.DEFAULT_MAX_REQUESTS_PER_SECOND,
                time,
                duration -> {
                    throw new InterruptedException("test interruption");
                });
        limiter.acquirePermit();

        try {
            assertThatThrownBy(limiter::acquirePermit)
                    .isInstanceOf(
                            SecRequestRateLimiter.SecRequestRateLimitException.class)
                    .hasMessageContaining("was not started")
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        time.advance(DEFAULT_INTERVAL_NANOS);
        limiter.acquirePermit();
    }

    @Test
    void failsClosedWithoutIssuingFirstPermitToAlreadyInterruptedCaller() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = limiter(time, sleeps);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(limiter::acquirePermit)
                    .isInstanceOf(
                            SecRequestRateLimiter.SecRequestRateLimitException.class)
                    .hasMessageContaining("interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }

        limiter.acquirePermit();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void failsClosedIfMonotonicTimeRegressesOrDoesNotAdvanceDuringWait() {
        FakeMonotonicTime regressingTime = new FakeMonotonicTime();
        SecRequestRateLimiter regressingLimiter = limiter(
                regressingTime, new ArrayList<>());
        regressingLimiter.acquirePermit();
        regressingTime.advance(-1);

        assertThatThrownBy(regressingLimiter::acquirePermit)
                .isInstanceOf(SecRequestRateLimiter.SecRequestRateLimitException.class)
                .hasMessageContaining("monotonic time regressed");

        FakeMonotonicTime stalledTime = new FakeMonotonicTime();
        SecRequestRateLimiter stalledLimiter = new SecRequestRateLimiter(
                SecRequestRateLimiter.DEFAULT_MAX_REQUESTS_PER_SECOND,
                stalledTime,
                duration -> { });
        stalledLimiter.acquirePermit();

        assertThatThrownBy(stalledLimiter::acquirePermit)
                .isInstanceOf(SecRequestRateLimiter.SecRequestRateLimitException.class)
                .hasMessageContaining("monotonic time did not advance");
    }

    @Test
    void acceptsNanoTimeWraparoundWithoutAllowingAnEarlyPermit() {
        FakeMonotonicTime time = new FakeMonotonicTime(Long.MAX_VALUE - 1_000L);
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = limiter(time, sleeps);

        limiter.acquirePermit();
        limiter.acquirePermit();

        assertThat(sleeps).containsExactly(DEFAULT_INTERVAL_NANOS);
        assertThat(time.readNanos()).isNegative();
    }

    @Test
    void rejectsRatesAboveConservativeCeilingAndNonPositiveRates() {
        assertThatThrownBy(() -> new SecRequestRateLimiter(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 8");
        assertThatThrownBy(() -> new SecRequestRateLimiter(9))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 1 and 8");
    }

    @Test
    void blocksImmediatelyDuringCooldownWithoutSleepingOrIssuingAPermit() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = limiter(time, sleeps);
        limiter.applyCooldown(Duration.ofSeconds(30));

        assertThatThrownBy(limiter::acquirePermit)
                .isInstanceOf(SecRequestRateLimiter.SecRequestRateLimitException.class)
                .hasMessageContaining("was not started")
                .hasMessageContaining("cooldown is active");

        assertThat(sleeps).isEmpty();
        assertThat(time.readNanos()).isZero();
    }

    @Test
    void remainsBlockedImmediatelyBeforeExpiryAndPermitsAtExpiry() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = limiter(time, sleeps);
        long cooldownNanos = TimeUnit.SECONDS.toNanos(5);
        limiter.applyCooldown(Duration.ofNanos(cooldownNanos));

        time.advance(cooldownNanos - 1);
        assertThatThrownBy(limiter::acquirePermit)
                .isInstanceOf(SecRequestRateLimiter.SecRequestRateLimitException.class)
                .hasMessageContaining("cooldown is active");
        assertThat(sleeps).isEmpty();

        time.advance(1);
        limiter.acquirePermit();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void shorterCooldownDoesNotReduceExistingLongerRemainingCooldown() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        SecRequestRateLimiter limiter = limiter(time, new ArrayList<>());
        limiter.applyCooldown(Duration.ofSeconds(10));

        time.advance(TimeUnit.SECONDS.toNanos(2));
        limiter.applyCooldown(Duration.ofSeconds(3));
        time.advance(TimeUnit.SECONDS.toNanos(3));

        assertThatThrownBy(limiter::acquirePermit)
                .isInstanceOf(SecRequestRateLimiter.SecRequestRateLimitException.class)
                .hasMessageContaining("cooldown is active");

        time.advance(TimeUnit.SECONDS.toNanos(5));
        limiter.acquirePermit();
    }

    @Test
    void rejectsInvalidOrOverflowingCooldownWithoutChangingLimiterState() {
        FakeMonotonicTime time = new FakeMonotonicTime();
        SecRequestRateLimiter limiter = limiter(time, new ArrayList<>());

        for (Duration invalid : List.of(Duration.ZERO, Duration.ofNanos(-1))) {
            assertThatThrownBy(() -> limiter.applyCooldown(invalid))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("SEC cooldown duration must be positive");
        }
        assertThatThrownBy(() -> limiter.applyCooldown(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SEC cooldown duration must be positive");
        assertThatThrownBy(() -> limiter.applyCooldown(Duration.ofSeconds(Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceeds monotonic nanosecond range");

        limiter.acquirePermit();
    }

    @Test
    void cooldownExpiryIsSafeAcrossNanoTimeWraparound() {
        FakeMonotonicTime time = new FakeMonotonicTime(Long.MAX_VALUE - 10L);
        List<Long> sleeps = new ArrayList<>();
        SecRequestRateLimiter limiter = limiter(time, sleeps);
        limiter.applyCooldown(Duration.ofNanos(20));

        time.advance(19);
        assertThatThrownBy(limiter::acquirePermit)
                .isInstanceOf(SecRequestRateLimiter.SecRequestRateLimitException.class)
                .hasMessageContaining("cooldown is active");

        time.advance(1);
        limiter.acquirePermit();
        assertThat(time.readNanos()).isNegative();
        assertThat(sleeps).isEmpty();
    }

    @Test
    void cooldownPreemptsACallerAlreadyWaitingForRequestSpacing() throws Exception {
        FakeMonotonicTime time = new FakeMonotonicTime();
        CountDownLatch spacingWaitStarted = new CountDownLatch(1);
        CountDownLatch releaseSpacingWait = new CountDownLatch(1);
        AtomicBoolean permitGranted = new AtomicBoolean();
        SecRequestRateLimiter limiter = new SecRequestRateLimiter(
                SecRequestRateLimiter.DEFAULT_MAX_REQUESTS_PER_SECOND,
                time,
                duration -> {
                    spacingWaitStarted.countDown();
                    if (!releaseSpacingWait.await(5, TimeUnit.SECONDS)) {
                        throw new InterruptedException("test spacing wait timed out");
                    }
                    time.advance(duration);
                });
        limiter.acquirePermit();
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<?> waitingRequest = executor.submit(() -> {
                limiter.acquirePermit();
                permitGranted.set(true);
            });
            assertThat(spacingWaitStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> cooldown = executor.submit(
                    () -> limiter.applyCooldown(Duration.ofMinutes(10)));
            cooldown.get(1, TimeUnit.SECONDS);

            releaseSpacingWait.countDown();
            assertThatThrownBy(() -> waitingRequest.get(5, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(
                            SecRequestRateLimiter.SecRequestRateLimitException.class)
                    .hasRootCauseMessage(
                            "SEC request was not started because a provider cooldown is active");
            assertThat(permitGranted).isFalse();
        } finally {
            releaseSpacingWait.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static SecRequestRateLimiter limiter(
            FakeMonotonicTime time,
            List<Long> sleeps) {
        return new SecRequestRateLimiter(
                SecRequestRateLimiter.DEFAULT_MAX_REQUESTS_PER_SECOND,
                time,
                duration -> {
                    sleeps.add(duration);
                    time.advance(duration);
                });
    }

    private static final class FakeMonotonicTime
            implements SecRequestRateLimiter.MonotonicTimeSource {

        private final AtomicLong nowNanos;

        private FakeMonotonicTime() {
            this(0L);
        }

        private FakeMonotonicTime(long initialNanos) {
            nowNanos = new AtomicLong(initialNanos);
        }

        @Override
        public long readNanos() {
            return nowNanos.get();
        }

        private void advance(long durationNanos) {
            nowNanos.addAndGet(durationNanos);
        }
    }
}
