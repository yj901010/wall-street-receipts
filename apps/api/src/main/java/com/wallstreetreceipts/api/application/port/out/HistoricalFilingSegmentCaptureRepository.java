package com.wallstreetreceipts.api.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;

public interface HistoricalFilingSegmentCaptureRepository {

    HistoricalFilingSegmentCaptureAppendResult append(
            HistoricalFilingSegmentCapture capture);

    Optional<HistoricalFilingSegmentCapture> findByCaptureId(String captureId);

    Optional<HistoricalFilingSegmentCapture> findLatestAtOrBefore(
            String rootCaptureId,
            int descriptorOrdinal,
            Instant evaluationAsOf,
            String parserVersion);

    long count();
}
