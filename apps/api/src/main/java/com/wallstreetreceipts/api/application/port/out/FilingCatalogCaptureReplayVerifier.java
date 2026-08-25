package com.wallstreetreceipts.api.application.port.out;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;

/** Deterministically replays exact retained bytes with their declared parser contract. */
public interface FilingCatalogCaptureReplayVerifier {

    FilingCatalogCapture verify(FilingCatalogCapture capture);
}
