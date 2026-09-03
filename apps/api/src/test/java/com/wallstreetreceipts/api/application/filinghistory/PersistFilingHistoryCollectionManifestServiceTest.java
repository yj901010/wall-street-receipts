package com.wallstreetreceipts.api.application.filinghistory;

import static com.wallstreetreceipts.api.application.filinghistory.PersistFilingHistoryCollectionManifestService.DescriptorCaptureSelection;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

class PersistFilingHistoryCollectionManifestServiceTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:12:03.123456Z");
    private static final Instant ASSEMBLED_AT =
            Instant.parse("2026-08-25T01:22:03.123456Z");

    private final FilingCatalogCaptureRepository rootRepository =
            mock(FilingCatalogCaptureRepository.class);
    private final HistoricalFilingSegmentCaptureRepository segmentRepository =
            mock(HistoricalFilingSegmentCaptureRepository.class);
    private final FilingHistoryCollectionManifestRepository manifestRepository =
            mock(FilingHistoryCollectionManifestRepository.class);

    private FilingCatalogCapture root;
    private HistoricalFilingSegmentCapture segment;

    @BeforeEach
    void setUpDurableEvidence() {
        root = capture(ROOT_CAPTURED_AT).withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        segment = SecHistoricalFilingSegmentCaptureTestFixture.capture(
                root, SEGMENT_CAPTURED_AT).withBodyRetention(
                        BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    @Test
    void readsOnlyExplicitCaptureIdsAndAppendsOneCanonicalManifest() {
        when(rootRepository.findByCaptureId(root.captureId()))
                .thenReturn(Optional.of(root));
        when(segmentRepository.findByCaptureId(segment.captureId()))
                .thenReturn(Optional.of(segment));
        when(manifestRepository.append(any())).thenAnswer(invocation -> {
            FilingHistoryCollectionManifest manifest = invocation.getArgument(0);
            return new FilingHistoryCollectionManifestAppendOutcome(
                    Status.INSERTED, manifest);
        });
        PersistFilingHistoryCollectionManifestService service = service(ASSEMBLED_AT);

        FilingHistoryCollectionManifestAppendOutcome outcome = service.persist(
                root.captureId(),
                List.of(new DescriptorCaptureSelection(0, segment.captureId())));

        assertThat(outcome.status()).isEqualTo(Status.INSERTED);
        assertThat(outcome.manifest().rootCaptureId()).isEqualTo(root.captureId());
        assertThat(outcome.manifest().assembledAt()).isEqualTo(ASSEMBLED_AT);
        ArgumentCaptor<FilingHistoryCollectionManifest> captor =
                ArgumentCaptor.forClass(FilingHistoryCollectionManifest.class);
        verify(manifestRepository).append(captor.capture());
        assertThat(captor.getValue().descriptors().getFirst()
                .selectedSegmentCaptureId()).isEqualTo(segment.captureId());
        verify(rootRepository).findByCaptureId(root.captureId());
        verify(segmentRepository).findByCaptureId(segment.captureId());
        verify(rootRepository, never()).findLatestAtOrBefore(
                any(), any(), any(), any(), any());
        verify(segmentRepository, never()).findLatestAtOrBefore(
                any(), any(Integer.class), any(), any());
    }

    @Test
    void missingRootStopsBeforeSegmentLookupOrAppend() {
        when(rootRepository.findByCaptureId(root.captureId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(ASSEMBLED_AT).persist(
                root.captureId(),
                List.of(new DescriptorCaptureSelection(0, segment.captureId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durable filing catalog root capture was not found");

        verifyNoInteractions(segmentRepository, manifestRepository);
    }

    @Test
    void missingExactSegmentStopsBeforeAppendWithoutLatestFallback() {
        when(rootRepository.findByCaptureId(root.captureId()))
                .thenReturn(Optional.of(root));
        when(segmentRepository.findByCaptureId(segment.captureId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service(ASSEMBLED_AT).persist(
                root.captureId(),
                List.of(new DescriptorCaptureSelection(0, segment.captureId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durable historical segment capture was not found");

        verify(segmentRepository, never()).findLatestAtOrBefore(
                any(), any(Integer.class), any(), any());
        verifyNoInteractions(manifestRepository);
    }

    @Test
    void duplicateSelectionsFailBeforeRepositoryReads() {
        DescriptorCaptureSelection selection =
                new DescriptorCaptureSelection(0, segment.captureId());

        assertThatThrownBy(() -> service(ASSEMBLED_AT).persist(
                root.captureId(), List.of(selection, selection)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("descriptorOrdinal must be unique within selections");

        verifyNoInteractions(rootRepository, segmentRepository, manifestRepository);
    }

    @Test
    void explicitOrdinalMismatchAndClockBeforeEvidenceFailClosed() {
        when(rootRepository.findByCaptureId(root.captureId()))
                .thenReturn(Optional.of(root));
        when(segmentRepository.findByCaptureId(segment.captureId()))
                .thenReturn(Optional.of(segment));

        assertThatThrownBy(() -> service(ASSEMBLED_AT).persist(
                root.captureId(),
                List.of(new DescriptorCaptureSelection(1, segment.captureId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("explicitly selected descriptorOrdinal");
        assertThatThrownBy(() -> service(SEGMENT_CAPTURED_AT.minusSeconds(1)).persist(
                root.captureId(),
                List.of(new DescriptorCaptureSelection(0, segment.captureId()))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assembledAt must not precede evidenceAvailableAt");

        verifyNoInteractions(manifestRepository);
    }

    @Test
    void repositoryIdentityMismatchFailsClosedBeforeAppend() {
        FilingCatalogCapture anotherRoot = capture(ROOT_CAPTURED_AT.plusSeconds(1))
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        when(rootRepository.findByCaptureId(root.captureId()))
                .thenReturn(Optional.of(anotherRoot));

        assertThatThrownBy(() -> service(ASSEMBLED_AT).persist(
                root.captureId(), List.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another root capture identity");

        when(rootRepository.findByCaptureId(root.captureId()))
                .thenReturn(Optional.of(root));
        HistoricalFilingSegmentCapture anotherSegment =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, SEGMENT_CAPTURED_AT.plusSeconds(1))
                        .withBodyRetention(
                                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        when(segmentRepository.findByCaptureId(segment.captureId()))
                .thenReturn(Optional.of(anotherSegment));

        assertThatThrownBy(() -> service(ASSEMBLED_AT).persist(
                root.captureId(),
                List.of(new DescriptorCaptureSelection(0, segment.captureId()))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another capture identity");

        verifyNoInteractions(manifestRepository);
    }

    private PersistFilingHistoryCollectionManifestService service(Instant now) {
        return new PersistFilingHistoryCollectionManifestService(
                rootRepository,
                segmentRepository,
                manifestRepository,
                Clock.fixed(now, ZoneOffset.UTC));
    }
}
