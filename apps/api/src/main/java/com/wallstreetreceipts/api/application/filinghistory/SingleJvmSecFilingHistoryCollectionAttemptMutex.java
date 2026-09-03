package com.wallstreetreceipts.api.application.filinghistory;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Nonblocking, process-local ownership gate for operator-triggered SEC collection attempts.
 *
 * <p>This gate coordinates only one JVM. It makes no aggregate multi-replica fair-access claim.
 */
public final class SingleJvmSecFilingHistoryCollectionAttemptMutex {

    private final AtomicBoolean owned = new AtomicBoolean();

    public Optional<Lease> tryAcquire() {
        if (!owned.compareAndSet(false, true)) {
            return Optional.empty();
        }
        return Optional.of(new Lease(owned));
    }

    public static final class Lease implements AutoCloseable {

        private final AtomicBoolean owner;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Lease(AtomicBoolean owner) {
            this.owner = owner;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                owner.set(false);
            }
        }
    }
}
