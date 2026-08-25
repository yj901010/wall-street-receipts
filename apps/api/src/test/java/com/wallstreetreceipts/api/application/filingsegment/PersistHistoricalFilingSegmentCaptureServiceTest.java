package com.wallstreetreceipts.api.application.filingsegment;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

class PersistHistoricalFilingSegmentCaptureServiceTest {

    @Test
    void resolvesOneExactDurableRootThenPerformsOneProviderLoadAndOneAppend() {
        FilingCatalogCaptureRepository rootRepository = mock(
                FilingCatalogCaptureRepository.class);
        HistoricalFilingSegmentCaptureProvider provider = mock(
                HistoricalFilingSegmentCaptureProvider.class);
        HistoricalFilingSegmentCaptureRepository segmentRepository = mock(
                HistoricalFilingSegmentCaptureRepository.class);
        HistoricalFilingSegmentCapture segmentCapture = mock(
                HistoricalFilingSegmentCapture.class);
        FilingCatalogCapture durableRoot = capture(
                Instant.parse("2026-08-25T01:00:00.123456Z"))
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        when(rootRepository.findByCaptureId(durableRoot.captureId()))
                .thenReturn(Optional.of(durableRoot));
        when(provider.loadHistoricalSegmentCapture(durableRoot, 0))
                .thenReturn(segmentCapture);
        when(segmentRepository.append(segmentCapture))
                .thenReturn(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
        PersistHistoricalFilingSegmentCaptureService service =
                new PersistHistoricalFilingSegmentCaptureService(
                        rootRepository, provider, segmentRepository);

        assertThat(service.capture(durableRoot.captureId(), 0))
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);

        InOrder order = inOrder(rootRepository, provider, segmentRepository);
        order.verify(rootRepository).findByCaptureId(durableRoot.captureId());
        order.verify(provider).loadHistoricalSegmentCapture(durableRoot, 0);
        order.verify(segmentRepository).append(segmentCapture);
        verifyNoMoreInteractions(rootRepository, provider, segmentRepository);
    }

    @Test
    void missingRootFailsBeforeProviderOrSegmentRepositoryInteraction() {
        FilingCatalogCaptureRepository rootRepository = mock(
                FilingCatalogCaptureRepository.class);
        HistoricalFilingSegmentCaptureProvider provider = mock(
                HistoricalFilingSegmentCaptureProvider.class);
        HistoricalFilingSegmentCaptureRepository segmentRepository = mock(
                HistoricalFilingSegmentCaptureRepository.class);
        String missingId = "b".repeat(64);
        when(rootRepository.findByCaptureId(missingId)).thenReturn(Optional.empty());
        PersistHistoricalFilingSegmentCaptureService service =
                new PersistHistoricalFilingSegmentCaptureService(
                        rootRepository, provider, segmentRepository);

        assertThatThrownBy(() -> service.capture(missingId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durable filing catalog root capture was not found");
        verify(rootRepository).findByCaptureId(missingId);
        verifyNoInteractions(provider, segmentRepository);
        verifyNoMoreInteractions(rootRepository);
    }
}
