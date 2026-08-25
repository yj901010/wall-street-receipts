package com.wallstreetreceipts.api.application.filingsegment;

import java.util.Objects;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;

/** One explicit durable root descriptor, one segment GET, and one atomic append. */
public final class PersistHistoricalFilingSegmentCaptureService {

    private final FilingCatalogCaptureRepository rootRepository;
    private final HistoricalFilingSegmentCaptureProvider provider;
    private final HistoricalFilingSegmentCaptureRepository segmentRepository;

    public PersistHistoricalFilingSegmentCaptureService(
            FilingCatalogCaptureRepository rootRepository,
            HistoricalFilingSegmentCaptureProvider provider,
            HistoricalFilingSegmentCaptureRepository segmentRepository) {
        this.rootRepository = Objects.requireNonNull(rootRepository, "rootRepository");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.segmentRepository = Objects.requireNonNull(
                segmentRepository, "segmentRepository");
    }

    public HistoricalFilingSegmentCaptureAppendResult capture(
            String rootCaptureId,
            int descriptorOrdinal) {
        FilingCatalogCapture durableRoot = rootRepository
                .findByCaptureId(rootCaptureId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "durable filing catalog root capture was not found"));
        return segmentRepository.append(provider.loadHistoricalSegmentCapture(
                durableRoot, descriptorOrdinal));
    }
}
