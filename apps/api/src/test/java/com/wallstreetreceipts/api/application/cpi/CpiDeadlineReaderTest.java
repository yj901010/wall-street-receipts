package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

class CpiDeadlineReaderTest {
    enum Failure { RUNTIME, ERROR }

    @Test @Timeout(10)
    void cancelledUninterruptibleReadsKeepAllFourWorkersAndOverflowNeverQueues() throws Exception {
        var entered = new CountDownLatch(4);
        var release = new CountDownLatch(1);
        var calls = new AtomicInteger();
        var active = new AtomicInteger();
        var interruptions = new AtomicInteger();
        var interrupted = new CountDownLatch(4);
        var delegate = reader(() -> {
            calls.incrementAndGet(); active.incrementAndGet(); entered.countDown();
            try {
                while (true) {
                    try { if (release.await(4, TimeUnit.SECONDS)) return List.of(); else throw new AssertionError("test release missing"); }
                    catch (InterruptedException ignored) { interruptions.incrementAndGet(); interrupted.countDown(); }
                }
            } finally { active.decrementAndGet(); }
        });
        try (var bounded = new CpiDeadlineReader(delegate, Duration.ofMillis(500), System::nanoTime);
             var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = new ArrayList<Future<Throwable>>();
            try {
                for (int i = 0; i < 4; i++) {
                    boolean selected = i % 2 == 1;
                    pending.add(callers.submit(() -> failure(() -> selected ? bounded.findAttempt(new UUID(0, 1)) : bounded.recentAttempts())));
                }
                assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
                for (var call : pending) assertUnavailable(call.get(2, TimeUnit.SECONDS));
                assertThat(active.get()).isEqualTo(4); // Cancellation has NOT released the worker slots.
                var overflow = callers.submit(() -> {
                    for (int i = 0; i < 20; i++) {
                        assertUnavailable(failure(bounded::recentAttempts));
                        assertUnavailable(failure(() -> bounded.findAttempt(new UUID(0, 1))));
                    }
                });
                overflow.get(1, TimeUnit.SECONDS);
                assertThat(calls.get()).isEqualTo(4);
                assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
                assertThat(interruptions.get()).isEqualTo(4);
            } finally { release.countDown(); }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (active.get() != 0 && System.nanoTime() < deadline) Thread.sleep(5);
            assertThat(active.get()).isZero();
            // Allow the workers to finish their FutureTask bookkeeping after the delegate's finally.
            callers.submit(() -> {
                while (true) {
                    try { assertThat(bounded.recentAttempts()).isEmpty(); return null; }
                    catch (CpiAttemptQueryService.Unavailable notYetIdle) {
                        if (System.nanoTime() >= deadline) throw notYetIdle;
                        Thread.sleep(5);
                    }
                }
            }).get(2, TimeUnit.SECONDS);
            assertThat(calls.get()).isEqualTo(5); // Rejected work was never executed later.
        }
    }

    @ParameterizedTest @EnumSource(Failure.class)
    void workerFailuresAreSanitizedAndTheReaderRemainsUsable(Failure failure) {
        var calls = new AtomicInteger();
        try (var bounded = new CpiDeadlineReader(reader(() -> {
            if (calls.incrementAndGet() == 1) {
                if (failure == Failure.ERROR) throw new AssertionError("synthetic private failure");
                throw new IllegalStateException("synthetic SQL", new RuntimeException("synthetic secret"));
            }
            return List.of();
        }))) {
            assertUnavailable(failure(bounded::recentAttempts));
            assertThat(bounded.recentAttempts()).isEmpty();
            assertThat(bounded.findAttempt(new UUID(0, 1))).isEmpty();
        }
    }

    @Test void alreadyCompletedResultPastMonotonicDeadlineIsNotPublished() {
        var ticks = new AtomicLong(Long.MAX_VALUE - 1000);
        try (var bounded = new CpiDeadlineReader(reader(() -> {
            ticks.addAndGet(TimeUnit.SECONDS.toNanos(4)); // Monotonic subtraction must tolerate nanoTime wraparound.
            return List.of();
        }), Duration.ofSeconds(4), ticks::get)) {
            assertUnavailable(failure(bounded::recentAttempts));
        }
    }

    @Test @Timeout(5)
    void callerInterruptionIsPreservedAndNeverRunsWorkOnTheCaller() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var outcome = new AtomicReference<Throwable>();
        var preserved = new AtomicReference<Boolean>();
        try (var bounded = new CpiDeadlineReader(reader(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            return List.of();
        }))) {
            var caller = Thread.ofPlatform().start(() -> {
                outcome.set(failure(bounded::recentAttempts)); preserved.set(Thread.currentThread().isInterrupted());
            });
            try {
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                caller.interrupt(); caller.join(1000);
                assertThat(caller.isAlive()).isFalse();
                assertUnavailable(outcome.get()); assertThat(preserved.get()).isTrue();
            } finally { release.countDown(); caller.join(1000); }
        }
    }

    @Test void preInterruptedCallerDoesNotSubmitAndRetainsItsFlag() {
        var calls = new AtomicInteger();
        try (var bounded = new CpiDeadlineReader(reader(() -> { calls.incrementAndGet(); return List.of(); }))) {
            Thread.currentThread().interrupt();
            try { assertUnavailable(failure(bounded::recentAttempts)); assertThat(Thread.currentThread().isInterrupted()).isTrue(); }
            finally { Thread.interrupted(); }
            assertThat(calls.get()).isZero();
        }
    }

    @Test void closeIsIdempotentRejectsNewWorkAndDoesNotInheritRequestThreadLocals() {
        var local = new InheritableThreadLocal<String>();
        local.set("synthetic request credential");
        var bounded = new CpiDeadlineReader(reader(() -> { assertThat(local.get()).isNull(); return List.of(); }));
        try { assertThat(bounded.recentAttempts()).isEmpty(); }
        finally { local.remove(); bounded.close(); bounded.close(); }
        assertUnavailable(failure(bounded::recentAttempts));
    }

    @Test void invalidBudgetsDoNotStartWorkers() {
        for (var duration : List.of(Duration.ZERO, Duration.ofNanos(-1), Duration.ofSeconds(5))) {
            assertThatThrownBy(() -> new CpiDeadlineReader(reader(List::of), duration, System::nanoTime))
                    .isInstanceOf(IllegalArgumentException.class).hasMessage("CPI read wait budget is invalid").hasNoCause();
        }
    }

    @Test @Timeout(5)
    void closeDoesNotWaitForAnUncooperativeReadOrPermitReplacementWork() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var exited = new CountDownLatch(1);
        var bounded = new CpiDeadlineReader(reader(() -> {
            entered.countDown();
            try {
                while (true) {
                    try { release.await(); return List.of(); }
                    catch (InterruptedException ignored) { /* Deliberately retain the occupied worker. */ }
                }
            } finally { exited.countDown(); }
        }), Duration.ofMillis(200), System::nanoTime);
        try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
            try {
                var pending = callers.submit(() -> failure(bounded::recentAttempts));
                assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
                assertUnavailable(pending.get(1, TimeUnit.SECONDS));
                callers.submit(bounded::close).get(1, TimeUnit.SECONDS);
                assertThat(exited.getCount()).isEqualTo(1);
                assertUnavailable(failure(bounded::recentAttempts));
            } finally { release.countDown(); bounded.close(); }
            assertThat(exited.await(1, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertUnavailable(Throwable failure) {
        assertThat(failure).isExactlyInstanceOf(CpiAttemptQueryService.Unavailable.class).hasMessage(null).hasNoCause();
    }
    private static Throwable failure(Supplier<?> action) {
        try { action.get(); return null; } catch (Throwable failure) { return failure; }
    }
    private static CpiAttemptReader reader(Supplier<List<CpiCollectionAttempt>> action) {
        return new CpiAttemptReader() {
            public List<CpiCollectionAttempt> recentAttempts() { return action.get(); }
            public Optional<CpiCollectionAttempt> findAttempt(UUID id) { return action.get().stream().findFirst(); }
        };
    }
}
