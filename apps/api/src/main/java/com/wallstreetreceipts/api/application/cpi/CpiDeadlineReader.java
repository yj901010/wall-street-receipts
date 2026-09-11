package com.wallstreetreceipts.api.application.cpi;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

/** Bounds caller waiting, not driver termination. A cancelled but running read still owns its worker. */
public final class CpiDeadlineReader implements CpiAttemptReader, AutoCloseable {
    private final CpiAttemptReader delegate;
    private final long budgetNanos;
    private final LongSupplier ticks;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(4, 4, 0, TimeUnit.SECONDS,
            new SynchronousQueue<>(), Thread.ofPlatform().daemon().inheritInheritableThreadLocals(false)
                    .name("cpi-query-read-", 0).factory(), new ThreadPoolExecutor.AbortPolicy());

    public CpiDeadlineReader(CpiAttemptReader delegate) {
        this(delegate, Duration.ofSeconds(4), System::nanoTime);
    }
    // Injectable monotonic elapsed time; business/event timestamps still use the service's injected Clock.
    CpiDeadlineReader(CpiAttemptReader delegate, Duration budget, LongSupplier ticks) {
        this.delegate = Objects.requireNonNull(delegate);
        this.ticks = Objects.requireNonNull(ticks);
        if (budget == null || budget.isNegative() || budget.isZero() || budget.compareTo(Duration.ofSeconds(4)) > 0) {
            throw new IllegalArgumentException("CPI read wait budget is invalid");
        }
        this.budgetNanos = budget.toNanos();
    }

    @Override public List<CpiCollectionAttempt> recentAttempts() { return read(delegate::recentAttempts); }
    @Override public Optional<CpiCollectionAttempt> findAttempt(UUID id) { return read(() -> delegate.findAttempt(id)); }

    private <T> T read(Supplier<T> action) {
        if (Thread.currentThread().isInterrupted()) throw new CpiAttemptQueryService.Unavailable();
        long started = ticks.getAsLong();
        Future<T> pending = null;
        try {
            // No executor queue and never CallerRuns: an occupied worker cannot be reused by cancellation.
            pending = workers.submit(action::get);
            long remaining = budgetNanos - (ticks.getAsLong() - started);
            if (remaining <= 0) throw new TimeoutException();
            T result = pending.get(remaining, TimeUnit.NANOSECONDS);
            // A completed Future must not publish a result observed after the overall read budget.
            if (ticks.getAsLong() - started >= budgetNanos || workers.isShutdown()) throw new TimeoutException();
            return result;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new CpiAttemptQueryService.Unavailable();
        } catch (ExecutionException | TimeoutException | RejectedExecutionException failure) {
            throw new CpiAttemptQueryService.Unavailable(); // Never carry reader/driver text or causes into HTTP.
        } finally {
            if (pending != null) pending.cancel(true); // Best effort only; not proof that JDBC has stopped.
        }
    }

    @Override public void close() { workers.shutdownNow(); } // Never wait indefinitely for an uncooperative driver.
}
