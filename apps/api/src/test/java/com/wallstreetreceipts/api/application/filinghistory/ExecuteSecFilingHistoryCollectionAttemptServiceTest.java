package com.wallstreetreceipts.api.application.filinghistory;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.CIK;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptClaimOutcome;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptClaimOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptCommitter;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.application.port.out.SourceCaptureRequestException;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppend;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.LifecycleState;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.support.FilingHistoryCollectionTestFixture;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

class ExecuteSecFilingHistoryCollectionAttemptServiceTest {

    private static final String REQUEST_ID = "11111111-1111-4111-8111-111111111111";
    private static final String OTHER_REQUEST_ID =
            "22222222-2222-4222-8222-222222222222";
    private static final String MANIFEST_ID = "a".repeat(64);
    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:00:00.123456Z");
    private static final Instant SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:10:00.123456Z");
    private static final Instant NOW = Instant.parse("2026-08-25T02:00:00.123456Z");

    private final SecFilingHistoryCollectionAttemptRepository attemptRepository =
            mock(SecFilingHistoryCollectionAttemptRepository.class);
    private final SecFilingHistoryCollectionAttemptCommitter committer =
            mock(SecFilingHistoryCollectionAttemptCommitter.class);
    private final FilingCatalogCaptureRepository rootRepository =
            mock(FilingCatalogCaptureRepository.class);
    private final HistoricalFilingSegmentCaptureRepository segmentRepository =
            mock(HistoricalFilingSegmentCaptureRepository.class);
    private final FilingCatalogCaptureProvider rootProvider =
            mock(FilingCatalogCaptureProvider.class);
    private final HistoricalFilingSegmentCaptureProvider segmentProvider =
            mock(HistoricalFilingSegmentCaptureProvider.class);
    private final SingleJvmSecFilingHistoryCollectionAttemptMutex mutex =
            new SingleJvmSecFilingHistoryCollectionAttemptMutex();
    private final AtomicReference<SecFilingHistoryCollectionAttempt> durableAttempt =
            new AtomicReference<>();

    private FilingCatalogCapture durableRoot;
    private HistoricalFilingSegmentCapture durableSegment;
    private ExecuteSecFilingHistoryCollectionAttemptService service;

    @BeforeEach
    void setUp() {
        durableRoot = capture(ROOT_CAPTURED_AT).withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        durableSegment = SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT)
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        when(rootProvider.providerName()).thenReturn("sec-edgar");
        when(segmentProvider.providerName()).thenReturn("sec-edgar");
        configureClaimedLedger();
        configureSuccessfulCommitter();
        service = service(Optional.of(rootProvider), Optional.of(segmentProvider));
    }

    @Test
    void captureRootClaimsDispatchesOnceAndUsesOnlyTheAtomicSuccessCommitter() {
        FilingCatalogCapture pendingRoot = capture(NOW);
        when(rootProvider.loadCatalogCapture(CIK)).thenReturn(pendingRoot);

        SecFilingHistoryCollectionAttempt result =
                service.captureRoot(REQUEST_ID, CIK);

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);
        assertThat(result.terminalOutcome().rootArtifact().artifactId())
                .isEqualTo(pendingRoot.captureId());
        verify(attemptRepository).claim(any());
        verify(attemptRepository).appendProviderDispatch(
                result.attemptId(), result.providerDispatch());
        verify(rootProvider).loadCatalogCapture(CIK);
        verify(committer).commitRootCaptureSuccess(result.attemptId(), pendingRoot, NOW);
        verify(attemptRepository, never()).appendTerminalOutcome(anyString(), any());
        verifyNoInteractions(rootRepository, segmentRepository, segmentProvider);
    }

    @Test
    void identicalReplayReturnsExistingStateWithoutEvidenceProviderMutexOrCommitterWork() {
        SecFilingHistoryCollectionAttempt existing =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(REQUEST_ID, CIK, NOW);
        doReturn(new SecFilingHistoryCollectionAttemptClaimOutcome(
                        Status.IDENTICAL_REPLAY, existing))
                .when(attemptRepository)
                .claim(any());
        ExecuteSecFilingHistoryCollectionAttemptService replayService = service(
                Optional.of(rootProvider), Optional.of(segmentProvider));

        SecFilingHistoryCollectionAttempt result =
                replayService.captureRoot(REQUEST_ID, CIK);

        assertThat(result).isSameAs(existing);
        verify(attemptRepository).claim(any());
        verify(attemptRepository, never()).appendProviderDispatch(anyString(), any());
        verify(attemptRepository, never()).appendTerminalOutcome(anyString(), any());
        verifyNoInteractions(
                committer,
                rootRepository,
                segmentRepository,
                rootProvider,
                segmentProvider);
    }

    @Test
    void busySingleJvmMutexTerminatesKnownNotStartedWithoutDispatchOrProviderCall() {
        SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease held =
                mutex.tryAcquire().orElseThrow();
        try {
            SecFilingHistoryCollectionAttempt result =
                    service.captureRoot(REQUEST_ID, CIK);

            assertThat(result.lifecycleState())
                    .isEqualTo(LifecycleState.TERMINAL_FAILED_KNOWN);
            assertThat(result.providerDispatch()).isNull();
            assertThat(result.terminalOutcome().failureCode())
                    .isEqualTo(FailureCode.PROVIDER_GATE_CLOSED);
            assertThat(result.terminalOutcome().requestDisposition())
                    .isEqualTo(RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED);
            verify(attemptRepository, never()).appendProviderDispatch(anyString(), any());
            verify(rootProvider, never()).loadCatalogCapture(anyString());
            verifyNoInteractions(committer);
        } finally {
            held.close();
        }
    }

    @Test
    void typedHttpFailurePersistsExactKnownStatusWithoutRetryOrCommitterCall() {
        when(rootProvider.loadCatalogCapture(CIK))
                .thenThrow(SourceCaptureRequestException.httpStatus(429));

        SecFilingHistoryCollectionAttempt result =
                service.captureRoot(REQUEST_ID, CIK);

        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.PROVIDER_HTTP_STATUS);
        assertThat(result.terminalOutcome().httpStatus()).isEqualTo(429);
        assertThat(result.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.PROVIDER_RESPONSE_RECEIVED);
        verify(rootProvider).loadCatalogCapture(CIK);
        verifyNoInteractions(committer);
    }

    @Test
    void unreadableProviderFailureKeepsStartOrResponseUnknown() {
        when(rootProvider.loadCatalogCapture(CIK))
                .thenThrow(SourceCaptureRequestException.responseUnreadable());

        SecFilingHistoryCollectionAttempt result =
                service.captureRoot(REQUEST_ID, CIK);

        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.PROVIDER_RESPONSE_UNREADABLE);
        assertThat(result.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN);
        verify(rootProvider).loadCatalogCapture(CIK);
        verifyNoInteractions(committer);
    }

    @Test
    void providerPortGateClosedAfterDispatchRetainsDispatchAndMarksNotStarted() {
        when(rootProvider.loadCatalogCapture(CIK))
                .thenThrow(SourceCaptureRequestException.providerGateClosed());

        SecFilingHistoryCollectionAttempt result =
                service.captureRoot(REQUEST_ID, CIK);

        assertThat(result.providerDispatch()).isNotNull();
        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.PROVIDER_GATE_CLOSED);
        assertThat(result.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED);
        verify(attemptRepository).appendProviderDispatch(
                result.attemptId(), result.providerDispatch());
        verify(rootProvider).loadCatalogCapture(CIK);
        verifyNoInteractions(committer);
    }

    @Test
    void unexpectedProviderFailureIsUnknownAndNeverRetried() {
        when(rootProvider.loadCatalogCapture(CIK))
                .thenThrow(new IllegalStateException("provider implementation failed"));

        SecFilingHistoryCollectionAttempt result =
                service.captureRoot(REQUEST_ID, CIK);

        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.PROVIDER_REQUEST_FAILED);
        assertThat(result.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN);
        verify(rootProvider).loadCatalogCapture(CIK);
        verifyNoInteractions(committer);
    }

    @Test
    void incompatibleProviderCaptureIsAClosedInvalidResponse() {
        when(rootProvider.loadCatalogCapture(CIK)).thenReturn(null);

        SecFilingHistoryCollectionAttempt result =
                service.captureRoot(REQUEST_ID, CIK);

        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.PROVIDER_RESPONSE_INVALID);
        assertThat(result.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.PROVIDER_RESPONSE_RECEIVED);
        verifyNoInteractions(committer);
    }

    @Test
    void selectionOnlyValidatesEveryExactCaptureThenCommitsWithZeroProviderCalls() {
        when(rootRepository.findByCaptureId(durableRoot.captureId()))
                .thenReturn(Optional.of(durableRoot));
        when(segmentRepository.findByCaptureId(durableSegment.captureId()))
                .thenReturn(Optional.of(durableSegment));

        SecFilingHistoryCollectionAttempt result = service.collectExactRoot(
                REQUEST_ID,
                durableRoot.captureId(),
                List.of(DescriptorAction.selectExact(0, durableSegment.captureId())));

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);
        assertThat(result.providerDispatch()).isNull();
        assertThat(result.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.NO_PROVIDER_INVOCATION);
        verify(rootRepository).findByCaptureId(durableRoot.captureId());
        verify(segmentRepository).findByCaptureId(durableSegment.captureId());
        verify(committer).commitSelectionOnlyCollectionSuccess(result.attemptId(), NOW);
        verifyNoInteractions(rootProvider, segmentProvider);
        verify(attemptRepository, never()).appendProviderDispatch(anyString(), any());
    }

    @Test
    void unreconstructableClaimedExactSelectionTerminatesBeforeDispatchOrManifestCommit() {
        when(rootRepository.findByCaptureId(durableRoot.captureId()))
                .thenReturn(Optional.of(durableRoot));
        when(segmentRepository.findByCaptureId(durableSegment.captureId()))
                .thenReturn(Optional.empty());

        SecFilingHistoryCollectionAttempt result = service.collectExactRoot(
                REQUEST_ID,
                durableRoot.captureId(),
                List.of(
                        DescriptorAction.selectExact(0, durableSegment.captureId()),
                        DescriptorAction.captureNow(1)));

        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.EXACT_EVIDENCE_VALIDATION_FAILED);
        assertThat(result.providerDispatch()).isNull();
        verifyNoInteractions(rootProvider, segmentProvider, committer);
        verify(attemptRepository, never()).appendProviderDispatch(anyString(), any());
    }

    @Test
    void captureNowValidatesAllSelectionsThenDispatchesOnlyOneExactSegment() {
        HistoricalFilingSegmentCapture selectedOrdinalOne =
                FilingHistoryCollectionTestFixture.segmentCapture(
                                durableRoot, 1, SEGMENT_CAPTURED_AT, List.of())
                        .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        HistoricalFilingSegmentCapture pendingOrdinalZero =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(durableRoot, NOW);
        when(rootRepository.findByCaptureId(durableRoot.captureId()))
                .thenReturn(Optional.of(durableRoot));
        when(segmentRepository.findByCaptureId(selectedOrdinalOne.captureId()))
                .thenReturn(Optional.of(selectedOrdinalOne));
        when(segmentProvider.loadHistoricalSegmentCapture(durableRoot, 0))
                .thenReturn(pendingOrdinalZero);

        SecFilingHistoryCollectionAttempt result = service.collectExactRoot(
                REQUEST_ID,
                durableRoot.captureId(),
                List.of(
                        DescriptorAction.selectExact(1, selectedOrdinalOne.captureId()),
                        DescriptorAction.captureNow(0)));

        assertThat(result.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);
        assertThat(result.providerDispatch().descriptorOrdinal()).isZero();
        verify(segmentRepository).findByCaptureId(selectedOrdinalOne.captureId());
        verify(segmentProvider).loadHistoricalSegmentCapture(durableRoot, 0);
        verify(committer).commitCapturedSegmentCollectionSuccess(
                result.attemptId(), pendingOrdinalZero, NOW);
        verifyNoInteractions(rootProvider);
    }

    @Test
    void missingProviderBeanTerminatesAtGateWithoutMutexDispatchOrNetwork() {
        ExecuteSecFilingHistoryCollectionAttemptService noProvider = service(
                Optional.empty(), Optional.empty());

        SecFilingHistoryCollectionAttempt result =
                noProvider.captureRoot(OTHER_REQUEST_ID, CIK);

        assertThat(result.terminalOutcome().failureCode())
                .isEqualTo(FailureCode.PROVIDER_GATE_CLOSED);
        assertThat(result.providerDispatch()).isNull();
        verify(attemptRepository, never()).appendProviderDispatch(anyString(), any());
        verifyNoInteractions(committer, rootProvider, segmentProvider);
    }

    @Test
    void committerFailureDoesNotInventTerminalOrRepeatTheProviderInvocation() {
        FilingCatalogCapture pendingRoot = capture(NOW);
        when(rootProvider.loadCatalogCapture(CIK)).thenReturn(pendingRoot);
        doThrow(new IllegalStateException("local commit acknowledgement unknown"))
                .when(committer)
                .commitRootCaptureSuccess(anyString(), any(), any());

        assertThatThrownBy(() -> service.captureRoot(REQUEST_ID, CIK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("local commit acknowledgement unknown");

        assertThat(durableAttempt.get().providerDispatch()).isNotNull();
        assertThat(durableAttempt.get().terminalOutcome()).isNull();
        verify(rootProvider).loadCatalogCapture(CIK);
        verify(committer).commitRootCaptureSuccess(
                durableAttempt.get().attemptId(), pendingRoot, NOW);
        verify(attemptRepository, never()).appendTerminalOutcome(anyString(), any());
    }

    @Test
    void mismatchedCommitterArtifactIsRejectedInsteadOfBeingTrusted() {
        FilingCatalogCapture pendingRoot = capture(NOW);
        when(rootProvider.loadCatalogCapture(CIK)).thenReturn(pendingRoot);
        doAnswer(invocation -> durableAttempt.get().withTerminalOutcome(
                        TerminalOutcome.succeeded(
                                TerminalStage.ROOT_CAPTURE,
                                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                                ArtifactAppend.inserted("b".repeat(64)),
                                ArtifactAppend.notApplicable(),
                                ArtifactAppend.notApplicable(),
                                NOW)))
                .when(committer)
                .commitRootCaptureSuccess(anyString(), any(), any());

        assertThatThrownBy(() -> service.captureRoot(REQUEST_ID, CIK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt committer returned inconsistent successful state");

        verify(rootProvider).loadCatalogCapture(CIK);
        verify(committer).commitRootCaptureSuccess(
                durableAttempt.get().attemptId(), pendingRoot, NOW);
        verify(attemptRepository, never()).appendTerminalOutcome(anyString(), any());
    }

    private ExecuteSecFilingHistoryCollectionAttemptService service(
            Optional<FilingCatalogCaptureProvider> optionalRootProvider,
            Optional<HistoricalFilingSegmentCaptureProvider> optionalSegmentProvider) {
        return new ExecuteSecFilingHistoryCollectionAttemptService(
                attemptRepository,
                committer,
                rootRepository,
                segmentRepository,
                optionalRootProvider,
                optionalSegmentProvider,
                mutex,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private void configureClaimedLedger() {
        when(attemptRepository.claim(any())).thenAnswer(invocation -> {
            SecFilingHistoryCollectionAttempt planned = invocation.getArgument(0);
            durableAttempt.set(planned);
            return new SecFilingHistoryCollectionAttemptClaimOutcome(Status.CLAIMED, planned);
        });
        when(attemptRepository.appendProviderDispatch(anyString(), any()))
                .thenAnswer(invocation -> {
                    SecFilingHistoryCollectionAttempt next = durableAttempt.get()
                            .withProviderDispatch(invocation.getArgument(1));
                    durableAttempt.set(next);
                    return next;
                });
        when(attemptRepository.appendTerminalOutcome(anyString(), any()))
                .thenAnswer(invocation -> {
                    SecFilingHistoryCollectionAttempt next = durableAttempt.get()
                            .withTerminalOutcome(invocation.getArgument(1));
                    durableAttempt.set(next);
                    return next;
                });
    }

    private void configureSuccessfulCommitter() {
        when(committer.commitRootCaptureSuccess(anyString(), any(), any()))
                .thenAnswer(invocation -> terminalSuccess(TerminalOutcome.succeeded(
                        TerminalStage.ROOT_CAPTURE,
                        RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                        ArtifactAppend.inserted(
                                ((FilingCatalogCapture) invocation.getArgument(1)).captureId()),
                        ArtifactAppend.notApplicable(),
                        ArtifactAppend.notApplicable(),
                        invocation.getArgument(2))));
        when(committer.commitSelectionOnlyCollectionSuccess(anyString(), any()))
                .thenAnswer(invocation -> terminalSuccess(TerminalOutcome.succeeded(
                        TerminalStage.MANIFEST_ASSEMBLY,
                        RequestDisposition.NO_PROVIDER_INVOCATION,
                        ArtifactAppend.notApplicable(),
                        ArtifactAppend.notApplicable(),
                        ArtifactAppend.inserted(MANIFEST_ID),
                        invocation.getArgument(1))));
        when(committer.commitCapturedSegmentCollectionSuccess(anyString(), any(), any()))
                .thenAnswer(invocation -> terminalSuccess(TerminalOutcome.succeeded(
                        TerminalStage.MANIFEST_ASSEMBLY,
                        RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                        ArtifactAppend.notApplicable(),
                        ArtifactAppend.inserted(
                                ((HistoricalFilingSegmentCapture) invocation.getArgument(1))
                                        .captureId()),
                        ArtifactAppend.inserted(MANIFEST_ID),
                        invocation.getArgument(2))));
    }

    private SecFilingHistoryCollectionAttempt terminalSuccess(TerminalOutcome outcome) {
        SecFilingHistoryCollectionAttempt next = durableAttempt.get().withTerminalOutcome(outcome);
        durableAttempt.set(next);
        return next;
    }
}
