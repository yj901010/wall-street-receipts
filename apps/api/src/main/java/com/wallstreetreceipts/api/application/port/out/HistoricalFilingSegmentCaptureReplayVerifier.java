package com.wallstreetreceipts.api.application.port.out;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;

/** Deterministically replays one historical segment's exact retained bytes. */
public interface HistoricalFilingSegmentCaptureReplayVerifier {

    HistoricalFilingSegmentCapture verify(
            HistoricalFilingSegmentCapture capture,
            FilingCatalogCapture rootCapture);
}
