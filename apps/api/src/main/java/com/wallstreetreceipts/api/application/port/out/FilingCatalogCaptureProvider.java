package com.wallstreetreceipts.api.application.port.out;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;

/** Provider-neutral boundary that preserves the exact decoded root response for persistence. */
public interface FilingCatalogCaptureProvider {

    FilingCatalogCapture loadCatalogCapture(String cik);

    String providerName();
}
