package com.wallstreetreceipts.api.application.cpi;

import static org.assertj.core.api.Assertions.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.Trigger;

class CpiAttemptQueryConcurrencyTest {
    private static final Instant NOW = Instant.parse("2026-09-08T15:02:00Z");
    private static final UUID ID = new UUID(0, 1);
    enum Shape { RECENT, SELECTED, MIXED }
    enum Outcome { SUCCESS, EMPTY, NULL, DATABASE_FAILURE, INVALID_EVIDENCE, FATAL_ERROR }

    @ParameterizedTest @EnumSource(Shape.class)
    void bothRoutesShareFourSlotsWithoutQueueingOrOverReleasingOnRejection(Shape shape) throws Exception {
        var reader = new GatedReader();
        var service = new CpiAttemptQueryService(reader, Clock.fixed(NOW, ZoneOffset.UTC));
        wave(service, reader, shape, Outcome.SUCCESS);
        wave(service, reader, Shape.MIXED, Outcome.SUCCESS);
    }

    @ParameterizedTest @EnumSource(Outcome.class)
    void allFourSlotsRecoverAfterEveryReaderOutcomeAndPostReadValidation(Outcome outcome) throws Exception {
        var reader = new GatedReader();
        var service = new CpiAttemptQueryService(reader, Clock.fixed(NOW, ZoneOffset.UTC));
        wave(service, reader, Shape.MIXED, outcome);
        wave(service, reader, Shape.MIXED, Outcome.SUCCESS);
    }

    private void wave(CpiAttemptQueryService service, GatedReader reader, Shape shape, Outcome outcome) throws Exception {
        reader.entered = new CountDownLatch(4);
        reader.release = new CountDownLatch(1);
        reader.outcome = outcome;
        int priorCalls = reader.calls.get();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var held = new ArrayList<Future<Throwable>>();
            try {
                for (int i = 0; i < 4; i++) {
                    boolean selected = selected(shape, i);
                    held.add(executor.submit(() -> {
                        try {
                            if (selected) service.find(ID.toString()); else service.recent();
                            return null;
                        } catch (RuntimeException | AssertionError failure) { return failure; }
                    }));
                }
                assertThat(reader.entered.await(3, TimeUnit.SECONDS)).as("all four reads admitted").isTrue();
                // The same busy service must reject repeatedly without consulting the reader or waiting.
                var rejected = executor.submit(() -> {
                    for (int i = 0; i < 10; i++) {
                        assertThatThrownBy(service::recent).isExactlyInstanceOf(CpiAttemptQueryService.Unavailable.class)
                                .hasMessage(null).hasNoCause();
                        assertThatThrownBy(() -> service.find(ID.toString())).isExactlyInstanceOf(CpiAttemptQueryService.Unavailable.class)
                                .hasMessage(null).hasNoCause();
                    }
                    assertThatThrownBy(() -> service.find("invalid")).isExactlyInstanceOf(CpiAttemptQueryService.InvalidQuery.class);
                });
                rejected.get(2, TimeUnit.SECONDS);
                assertThat(reader.calls.get()).isEqualTo(priorCalls + 4);
                assertThat(reader.active.get()).isEqualTo(4);
                assertThat(reader.peak.get()).isEqualTo(4);
            } finally { reader.release.countDown(); }
            for (int i = 0; i < held.size(); i++) {
                Throwable failure = held.get(i).get(3, TimeUnit.SECONDS);
                if (outcome == Outcome.SUCCESS || outcome == Outcome.EMPTY && !selected(shape, i)) {
                    assertThat(failure).isNull();
                } else if (outcome == Outcome.EMPTY) {
                    assertThat(failure).isExactlyInstanceOf(CpiAttemptQueryService.NotFound.class);
                } else if (outcome == Outcome.FATAL_ERROR) {
                    assertThat(failure).isExactlyInstanceOf(AssertionError.class).hasMessage("synthetic fatal error");
                } else {
                    assertThat(failure).isExactlyInstanceOf(CpiAttemptQueryService.Unavailable.class).hasMessage(null).hasNoCause();
                }
            }
        }
        assertThat(reader.active.get()).isZero();
        assertThat(reader.calls.get()).isEqualTo(priorCalls + 4); // No delayed overflow execution after release.
    }

    private static boolean selected(Shape shape, int index) {
        return shape == Shape.SELECTED || shape == Shape.MIXED && index % 2 == 1;
    }

    private static final class GatedReader implements CpiAttemptReader {
        private CountDownLatch entered;
        private CountDownLatch release;
        private Outcome outcome;
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger active = new AtomicInteger();
        private final AtomicInteger peak = new AtomicInteger();
        @Override public List<CpiCollectionAttempt> recentAttempts() {
            var row = read();
            return outcome == Outcome.NULL ? null : outcome == Outcome.EMPTY ? List.of() : List.of(row);
        }
        @Override public Optional<CpiCollectionAttempt> findAttempt(UUID id) {
            var row = read();
            return outcome == Outcome.NULL ? null : outcome == Outcome.EMPTY ? Optional.empty() : Optional.of(row);
        }
        private CpiCollectionAttempt read() {
            calls.incrementAndGet();
            peak.accumulateAndGet(active.incrementAndGet(), Math::max);
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new AssertionError("test gate timed out");
                if (outcome == Outcome.DATABASE_FAILURE) throw new IllegalStateException("synthetic private SQL", new RuntimeException("synthetic secret"));
                if (outcome == Outcome.FATAL_ERROR) throw new AssertionError("synthetic fatal error");
                return new CpiCollectionAttempt(ID, Trigger.MANUAL,
                        outcome == Outcome.INVALID_EVIDENCE ? NOW.plusSeconds(1) : NOW, true, null);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new AssertionError("test gate interrupted", exception);
            } finally { active.decrementAndGet(); }
        }
    }
}
