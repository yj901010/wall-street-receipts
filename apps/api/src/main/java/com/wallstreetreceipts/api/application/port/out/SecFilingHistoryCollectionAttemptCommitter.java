package com.wallstreetreceipts.api.application.port.out;

import java.time.Instant;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;

/**
 * Local atomic success boundary for exact source artifacts, the optional ADR-041 manifest, and
 * the terminal attempt fact. Provider I/O must finish before entering this boundary.
 */
public interface SecFilingHistoryCollectionAttemptCommitter {

    SecFilingHistoryCollectionAttempt commitRootCaptureSuccess(
            String attemptId,
            FilingCatalogCapture pendingRootCapture,
            Instant completedAt);

    SecFilingHistoryCollectionAttempt commitSelectionOnlyCollectionSuccess(
            String attemptId,
            Instant completedAt);

    SecFilingHistoryCollectionAttempt commitCapturedSegmentCollectionSuccess(
            String attemptId,
            HistoricalFilingSegmentCapture pendingSegmentCapture,
            Instant completedAt);
}
