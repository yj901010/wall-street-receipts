package com.wallstreetreceipts.api.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;

public interface FilingCatalogCaptureRepository {

    FilingCatalogCaptureAppendResult append(FilingCatalogCapture capture);

    Optional<FilingCatalogCapture> findByCaptureId(String captureId);

    Optional<FilingCatalogCapture> findLatestAtOrBefore(
            String provider,
            String product,
            String cik,
            Instant evaluationAsOf,
            String parserVersion);

    long count();
}
