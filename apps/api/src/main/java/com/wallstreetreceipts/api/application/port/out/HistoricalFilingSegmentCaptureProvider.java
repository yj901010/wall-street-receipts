package com.wallstreetreceipts.api.application.port.out;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;

/** Loads one exact historical segment referenced by an already durable root capture. */
public interface HistoricalFilingSegmentCaptureProvider {

    HistoricalFilingSegmentCapture loadHistoricalSegmentCapture(
            FilingCatalogCapture durableRoot,
            int descriptorOrdinal);

    String providerName();
}
