package com.wallstreetreceipts.api.application.filinghistory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SingleJvmSecFilingHistoryCollectionAttemptMutexTest {

    @Test
    void ownershipIsNonblockingExclusiveReleasedAndIdempotentlyClosed() {
        SingleJvmSecFilingHistoryCollectionAttemptMutex mutex =
                new SingleJvmSecFilingHistoryCollectionAttemptMutex();

        SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease first =
                mutex.tryAcquire().orElseThrow();
        assertThat(mutex.tryAcquire()).isEmpty();

        first.close();
        first.close();

        SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease second =
                mutex.tryAcquire().orElseThrow();
        assertThat(mutex.tryAcquire()).isEmpty();
        second.close();
        assertThat(mutex.tryAcquire()).isPresent();
    }
}
