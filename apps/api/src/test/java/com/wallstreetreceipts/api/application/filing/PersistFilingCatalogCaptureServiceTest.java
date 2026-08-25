package com.wallstreetreceipts.api.application.filing;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;

class PersistFilingCatalogCaptureServiceTest {

    @Test
    void performsExactlyOneProviderLoadAndOneAtomicRepositoryAppend() {
        FilingCatalogCaptureProvider provider = mock(FilingCatalogCaptureProvider.class);
        FilingCatalogCaptureRepository repository = mock(FilingCatalogCaptureRepository.class);
        FilingCatalogCapture capture = capture(
                Instant.parse("2026-08-25T01:02:03.123456Z"));
        when(provider.loadCatalogCapture("320193")).thenReturn(capture);
        when(repository.append(capture))
                .thenReturn(FilingCatalogCaptureAppendResult.INSERTED);
        PersistFilingCatalogCaptureService service =
                new PersistFilingCatalogCaptureService(provider, repository);

        assertThat(service.capture("320193"))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);

        verify(provider).loadCatalogCapture("320193");
        verify(repository).append(capture);
        verifyNoMoreInteractions(provider, repository);
    }
}
