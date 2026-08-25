package com.wallstreetreceipts.api.persistence;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.CIK;
import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptClaimOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptCommitter;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorSelectionState;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppendStatus;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.LifecycleState;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcSecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.support.FilingHistoryCollectionTestFixture;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

@SpringBootTest
@ActiveProfiles("test")
class SecFilingHistoryCollectionAttemptPersistenceTest {

    private static final Instant BASE = Instant.parse("2026-08-25T06:00:00.123456Z");

    @Autowired
    private SecFilingHistoryCollectionAttemptRepository attemptRepository;

    @Autowired
    private SecFilingHistoryCollectionAttemptCommitter committer;

    @Autowired
    private FilingCatalogCaptureRepository rootRepository;

    @Autowired
    private HistoricalFilingSegmentCaptureRepository segmentRepository;

    @Autowired
    private FilingHistoryCollectionManifestRepository manifestRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void claimsOriginalCommandOnceAndReconstructsDispatchAndFailurePointInTime() {
        long before = attemptRepository.count();
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        "10000000-0000-4000-8000-000000000001",
                        CIK,
                        BASE);

        var first = attemptRepository.claim(planned);
        var replay = attemptRepository.claim(
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        planned.operatorRequestId(), CIK, BASE.plusSeconds(60)));

        assertThat(first.status()).isEqualTo(Status.CLAIMED);
        assertThat(first.attempt()).isEqualTo(planned);
        assertThat(replay.status()).isEqualTo(Status.IDENTICAL_REPLAY);
        assertThat(replay.attempt()).isEqualTo(planned);
        assertThat(attemptRepository.count()).isEqualTo(before + 1);
        assertThatThrownBy(() -> attemptRepository.claim(
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        planned.operatorRequestId(), "1", BASE.plusSeconds(120))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("operatorRequestId is already bound to another command");

        Instant dispatchedAt = BASE.plusSeconds(1);
        SecFilingHistoryCollectionAttempt dispatched =
                attemptRepository.appendProviderDispatch(
                        planned.attemptId(), ProviderDispatch.captureRoot(dispatchedAt));
        TerminalOutcome failure = TerminalOutcome.failed(
                TerminalStage.PROVIDER_GATE,
                RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED,
                FailureCode.PROVIDER_GATE_CLOSED,
                null,
                BASE.plusSeconds(2));
        SecFilingHistoryCollectionAttempt terminal =
                attemptRepository.appendTerminalOutcome(planned.attemptId(), failure);

        assertThat(dispatched.lifecycleState())
                .isEqualTo(LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE);
        assertThat(terminal.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_FAILED_KNOWN);
        assertThat(attemptRepository.findByAttemptIdAtOrBefore(
                planned.attemptId(), BASE.minusNanos(1_000))).isEmpty();
        assertThat(attemptRepository.findByAttemptIdAtOrBefore(
                planned.attemptId(), BASE))
                .get().extracting(SecFilingHistoryCollectionAttempt::lifecycleState)
                .isEqualTo(LifecycleState.PLANNED);
        assertThat(attemptRepository.findByAttemptIdAtOrBefore(
                planned.attemptId(), dispatchedAt))
                .get().extracting(SecFilingHistoryCollectionAttempt::lifecycleState)
                .isEqualTo(LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE);
        assertThat(attemptRepository.findByAttemptIdAtOrBefore(
                planned.attemptId(), failure.completedAt()))
                .contains(terminal);

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_provider_dispatches "
                        + "WHERE attempt_id = ?",
                Integer.class,
                planned.attemptId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_outcomes "
                        + "WHERE attempt_id = ?",
                Integer.class,
                planned.attemptId())).isEqualTo(1);
    }

    @Test
    void concurrentClaimConvergesOnOneHeaderAndOneCanonicalActionSet() throws Exception {
        FilingCatalogCapture root = persistRoot(BASE.plusSeconds(10));
        HistoricalFilingSegmentCapture selected = persistSegment(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, BASE.plusSeconds(20)));
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "20000000-0000-4000-8000-000000000002",
                        root.captureId(),
                        List.of(
                                DescriptorAction.captureNow(1),
                                DescriptorAction.selectExact(0, selected.captureId())),
                        BASE.plusSeconds(30));
        long before = attemptRepository.count();
        Callable<Status> claim = () -> attemptRepository.claim(planned).status();

        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Status> statuses = executor.invokeAll(List.of(claim, claim)).stream()
                    .map(future -> {
                        try {
                            return future.get();
                        } catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .toList();
            assertThat(statuses).containsExactlyInAnyOrder(Status.CLAIMED, Status.IDENTICAL_REPLAY);
        }

        SecFilingHistoryCollectionAttempt persisted = attemptRepository.findByAttemptId(
                planned.attemptId()).orElseThrow();
        assertThat(attemptRepository.count()).isEqualTo(before + 1);
        assertThat(persisted.descriptorActions())
                .extracting(DescriptorAction::descriptorOrdinal)
                .containsExactly(0, 1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_descriptor_actions "
                        + "WHERE attempt_id = ?",
                Integer.class,
                planned.attemptId())).isEqualTo(2);
    }

    @Test
    void rejectsMissingRootEvidenceWithoutRetainingAnAttemptOrLeakingSqlDetails() {
        long attemptsBefore = attemptRepository.count();
        long actionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_descriptor_actions",
                Long.class);
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "21000000-0000-4000-8000-000000000002",
                        "f".repeat(64),
                        List.of(),
                        BASE.plusSeconds(40));

        assertThatThrownBy(() -> attemptRepository.claim(planned))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("collection attempt exact evidence was not accepted");

        assertThat(attemptRepository.count()).isEqualTo(attemptsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_descriptor_actions",
                Long.class)).isEqualTo(actionsBefore);
        assertThat(attemptRepository.findByAttemptId(planned.attemptId())).isEmpty();
    }

    @Test
    void rejectsMissingSelectedSegmentAndRollsBackItsAlreadyInsertedHeader() {
        FilingCatalogCapture root = persistRoot(BASE.plusSeconds(50));
        long attemptsBefore = attemptRepository.count();
        long actionsBefore = jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_descriptor_actions",
                Long.class);
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "22000000-0000-4000-8000-000000000002",
                        root.captureId(),
                        List.of(DescriptorAction.selectExact(0, "e".repeat(64))),
                        BASE.plusSeconds(60));

        assertThatThrownBy(() -> attemptRepository.claim(planned))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("collection attempt exact evidence was not accepted");

        assertThat(attemptRepository.count()).isEqualTo(attemptsBefore);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_descriptor_actions",
                Long.class)).isEqualTo(actionsBefore);
        assertThat(attemptRepository.findByAttemptId(planned.attemptId())).isEmpty();
    }

    @Test
    void atomicallyCommitsInsertedAndIdenticalReplayRootArtifacts() {
        FilingCatalogCapture pending = capture(BASE.plusSeconds(100));
        SecFilingHistoryCollectionAttempt first = claimAndDispatchRoot(
                "30000000-0000-4000-8000-000000000003",
                BASE.plusSeconds(90));

        SecFilingHistoryCollectionAttempt inserted = committer.commitRootCaptureSuccess(
                first.attemptId(), pending, BASE.plusSeconds(95));

        assertThat(inserted.terminalOutcome().rootArtifact().status())
                .isEqualTo(ArtifactAppendStatus.INSERTED);
        assertThat(inserted.terminalOutcome().rootArtifact().artifactId())
                .isEqualTo(pending.captureId());
        assertThat(inserted.terminalOutcome().completedAt())
                .isEqualTo(pending.catalog().capturedAt());
        assertThat(inserted.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);

        SecFilingHistoryCollectionAttempt second = claimAndDispatchRoot(
                "30000000-0000-4000-8000-000000000004",
                BASE.plusSeconds(120));
        SecFilingHistoryCollectionAttempt replay = committer.commitRootCaptureSuccess(
                second.attemptId(), pending, BASE.plusSeconds(130));

        assertThat(replay.terminalOutcome().rootArtifact().status())
                .isEqualTo(ArtifactAppendStatus.IDENTICAL_REPLAY);
        assertThat(rootRepository.findByCaptureId(pending.captureId())).isPresent();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempt_outcomes "
                        + "WHERE attempt_id IN (?, ?)",
                Integer.class,
                first.attemptId(),
                second.attemptId())).isEqualTo(2);
    }

    @Test
    void atomicallyAssemblesSelectionOnlyManifestFromExactStoredCapture() {
        FilingCatalogCapture root = persistRoot(BASE.plusSeconds(200));
        HistoricalFilingSegmentCapture selected = persistSegment(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, BASE.plusSeconds(210)));
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "40000000-0000-4000-8000-000000000005",
                        root.captureId(),
                        List.of(DescriptorAction.selectExact(0, selected.captureId())),
                        BASE.plusSeconds(220));
        attemptRepository.claim(planned);

        SecFilingHistoryCollectionAttempt succeeded =
                committer.commitSelectionOnlyCollectionSuccess(
                        planned.attemptId(), BASE.plusSeconds(230));

        assertThat(succeeded.providerDispatch()).isNull();
        assertThat(succeeded.terminalOutcome().requestDisposition())
                .isEqualTo(RequestDisposition.NO_PROVIDER_INVOCATION);
        assertThat(succeeded.terminalOutcome().manifestArtifact().status())
                .isEqualTo(ArtifactAppendStatus.INSERTED);
        assertThat(succeeded.terminalOutcome().segmentArtifact().status())
                .isEqualTo(ArtifactAppendStatus.NOT_APPLICABLE);
        var manifest = manifestRepository.findByManifestId(
                succeeded.terminalOutcome().manifestArtifact().artifactId()).orElseThrow();
        assertThat(manifest.rootCaptureId()).isEqualTo(root.captureId());
        assertThat(succeeded.terminalOutcome().completedAt())
                .isAfterOrEqualTo(manifest.assembledAt());
        assertThat(manifest.descriptors())
                .extracting(member -> member.selectionState())
                .containsExactly(
                        DescriptorSelectionState.SELECTED_EXACT_CAPTURE,
                        DescriptorSelectionState.NOT_SELECTED);

        SecFilingHistoryCollectionAttempt rootOnly =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "40000000-0000-4000-8000-000000000009",
                        root.captureId(),
                        List.of(),
                        BASE.plusSeconds(240));
        attemptRepository.claim(rootOnly);
        SecFilingHistoryCollectionAttempt otherSelection =
                committer.commitSelectionOnlyCollectionSuccess(
                        rootOnly.attemptId(), BASE.plusSeconds(250));
        jdbc.update(
                "UPDATE sec_filing_collection_attempt_outcomes "
                        + "SET manifest_artifact_id = ? WHERE attempt_id = ?",
                otherSelection.terminalOutcome().manifestArtifact().artifactId(),
                planned.attemptId());

        assertThat(attemptRepository.findByAttemptIdAtOrBefore(
                planned.attemptId(), BASE.plusSeconds(229)))
                .get().extracting(SecFilingHistoryCollectionAttempt::lifecycleState)
                .isEqualTo(LifecycleState.PLANNED);
        assertThatThrownBy(() -> attemptRepository.findByAttemptId(planned.attemptId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("attempt manifest artifact does not match its exact selections");
    }

    @Test
    void atomicallyCommitsCapturedSegmentManifestAndCanonicalActionOrder() {
        FilingCatalogCapture root = persistRoot(BASE.plusSeconds(300));
        HistoricalFilingSegmentCapture selected = persistSegment(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, BASE.plusSeconds(310)));
        HistoricalFilingSegmentCapture pending =
                FilingHistoryCollectionTestFixture.segmentCapture(
                        root, 1, BASE.plusSeconds(320), List.of());
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "50000000-0000-4000-8000-000000000006",
                        root.captureId(),
                        List.of(
                                DescriptorAction.captureNow(1),
                                DescriptorAction.selectExact(0, selected.captureId())),
                        BASE.plusSeconds(330));
        attemptRepository.claim(planned);
        attemptRepository.appendProviderDispatch(
                planned.attemptId(),
                ProviderDispatch.captureHistoricalSegment(1, BASE.plusSeconds(331)));

        SecFilingHistoryCollectionAttempt succeeded =
                committer.commitCapturedSegmentCollectionSuccess(
                        planned.attemptId(), pending, BASE.plusSeconds(340));

        assertThat(succeeded.descriptorActions())
                .extracting(DescriptorAction::descriptorOrdinal)
                .containsExactly(0, 1);
        assertThat(succeeded.terminalOutcome().segmentArtifact().artifactId())
                .isEqualTo(pending.captureId());
        assertThat(succeeded.terminalOutcome().segmentArtifact().status())
                .isEqualTo(ArtifactAppendStatus.INSERTED);
        assertThat(succeeded.terminalOutcome().manifestArtifact().status())
                .isEqualTo(ArtifactAppendStatus.INSERTED);
        var manifest = manifestRepository.findByManifestId(
                succeeded.terminalOutcome().manifestArtifact().artifactId()).orElseThrow();
        assertThat(manifest.descriptors())
                .extracting(member -> member.selectedSegmentCaptureId())
                .containsExactly(selected.captureId(), pending.captureId());
        assertThat(attemptRepository.findByAttemptId(planned.attemptId()))
                .contains(succeeded);
    }

    @Test
    void rollsBackPendingSegmentWhenManifestCannotBeAssembled() {
        Instant futureRootAt = Instant.parse("2099-01-01T00:00:00.123456Z");
        FilingCatalogCapture root = persistRoot(futureRootAt);
        HistoricalFilingSegmentCapture pending =
                FilingHistoryCollectionTestFixture.segmentCapture(
                        root, 0, futureRootAt.plusSeconds(10), List.of());
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "60000000-0000-4000-8000-000000000007",
                        root.captureId(),
                        List.of(DescriptorAction.captureNow(0)),
                        futureRootAt.plusSeconds(20));
        attemptRepository.claim(planned);
        attemptRepository.appendProviderDispatch(
                planned.attemptId(),
                ProviderDispatch.captureHistoricalSegment(
                        0, futureRootAt.plusSeconds(21)));
        long segmentsBefore = segmentRepository.count();
        long manifestsBefore = manifestRepository.count();

        assertThatThrownBy(() -> committer.commitCapturedSegmentCollectionSuccess(
                planned.attemptId(), pending, futureRootAt.plusSeconds(30)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assembledAt must not precede evidenceAvailableAt");

        assertThat(segmentRepository.count()).isEqualTo(segmentsBefore);
        assertThat(segmentRepository.findByCaptureId(pending.captureId())).isEmpty();
        assertThat(manifestRepository.count()).isEqualTo(manifestsBefore);
        SecFilingHistoryCollectionAttempt durable = attemptRepository.findByAttemptId(
                planned.attemptId()).orElseThrow();
        assertThat(durable.providerDispatch()).isNotNull();
        assertThat(durable.terminalOutcome()).isNull();
        assertThat(durable.lifecycleState())
                .isEqualTo(LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE);
    }

    @Test
    void failsClosedOnStoredActionCountTamperingAndExposesNoMutationSurface() {
        FilingCatalogCapture root = persistRoot(BASE.plusSeconds(400));
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        "70000000-0000-4000-8000-000000000008",
                        root.captureId(),
                        List.of(DescriptorAction.captureNow(0)),
                        BASE.plusSeconds(410));
        attemptRepository.claim(planned);
        jdbc.update(
                "UPDATE sec_filing_collection_attempts "
                        + "SET descriptor_action_count = 2 WHERE attempt_id = ?",
                planned.attemptId());

        assertThatThrownBy(() -> attemptRepository.findByAttemptId(planned.attemptId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("collection attempt action count does not match its header");
        assertThat(Arrays.stream(
                        SecFilingHistoryCollectionAttemptRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("purge")))
                .isTrue();
        assertThat(Arrays.stream(
                        JdbcSecFilingHistoryCollectionAttemptRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("purge")))
                .isTrue();
    }

    private SecFilingHistoryCollectionAttempt claimAndDispatchRoot(
            String operatorRequestId,
            Instant requestedAt) {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        operatorRequestId, CIK, requestedAt);
        attemptRepository.claim(planned);
        return attemptRepository.appendProviderDispatch(
                planned.attemptId(),
                ProviderDispatch.captureRoot(requestedAt.plusSeconds(1)));
    }

    private FilingCatalogCapture persistRoot(Instant capturedAt) {
        FilingCatalogCapture pending = capture(capturedAt);
        assertThat(rootRepository.append(pending))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        return rootRepository.findByCaptureId(pending.captureId()).orElseThrow();
    }

    private HistoricalFilingSegmentCapture persistSegment(
            HistoricalFilingSegmentCapture pending) {
        segmentRepository.append(pending);
        return segmentRepository.findByCaptureId(pending.captureId()).orElseThrow();
    }
}
