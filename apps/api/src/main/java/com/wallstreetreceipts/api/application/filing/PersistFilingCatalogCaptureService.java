package com.wallstreetreceipts.api.application.filing;

import java.util.Objects;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;

/** One-shot orchestration only; scheduling and retries remain outside this boundary. */
public final class PersistFilingCatalogCaptureService {

    private final FilingCatalogCaptureProvider provider;
    private final FilingCatalogCaptureRepository repository;

    public PersistFilingCatalogCaptureService(
            FilingCatalogCaptureProvider provider,
            FilingCatalogCaptureRepository repository) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public FilingCatalogCaptureAppendResult capture(String cik) {
        return repository.append(provider.loadCatalogCapture(cik));
    }
}
