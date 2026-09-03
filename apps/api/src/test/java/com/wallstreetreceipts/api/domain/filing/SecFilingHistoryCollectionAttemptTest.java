package com.wallstreetreceipts.api.domain.filing;

import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppend;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.CommandKind;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.LifecycleState;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;
import static com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SecFilingHistoryCollectionAttemptTest {

    private static final String OPERATOR_REQUEST_ID =
            "6f9619ff-8b86-d011-b42d-00cf4fc964ff";
    private static final String ANOTHER_OPERATOR_REQUEST_ID =
            "7f9619ff-8b86-d011-b42d-00cf4fc964ff";
    private static final String ROOT_CAPTURE_ID = "a".repeat(64);
    private static final String FIRST_SEGMENT_CAPTURE_ID = "b".repeat(64);
    private static final String SECOND_SEGMENT_CAPTURE_ID = "c".repeat(64);
    private static final String MANIFEST_ID = "d".repeat(64);
    private static final Instant REQUESTED_AT =
            Instant.parse("2026-08-25T01:00:00.123456Z");
    private static final Instant DISPATCHED_AT =
            Instant.parse("2026-08-25T01:00:01.123456Z");
    private static final Instant COMPLETED_AT =
            Instant.parse("2026-08-25T01:00:02.123456Z");

    @Test
    void plansCanonicalRootCommandWithStableContentIdentities() {
        SecFilingHistoryCollectionAttempt first =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        OPERATOR_REQUEST_ID, "320193", REQUESTED_AT);
        SecFilingHistoryCollectionAttempt laterSameCommand =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        OPERATOR_REQUEST_ID,
                        "0000320193",
                        REQUESTED_AT.plusSeconds(30));
        SecFilingHistoryCollectionAttempt anotherOperator =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        ANOTHER_OPERATOR_REQUEST_ID, "320193", REQUESTED_AT);

        assertThat(first.commandKind()).isEqualTo(CommandKind.CAPTURE_ROOT);
        assertThat(first.cik()).isEqualTo("0000320193");
        assertThat(first.rootCaptureId()).isNull();
        assertThat(first.descriptorActions()).isEmpty();
        assertThat(first.commandSha256()).hasSize(64);
        assertThat(first.attemptId()).hasSize(64);
        assertThat(first.maxProviderInvocations()).isOne();
        assertThat(first.lifecycleState()).isEqualTo(LifecycleState.PLANNED);
        assertThat(laterSameCommand.commandSha256()).isEqualTo(first.commandSha256());
        assertThat(laterSameCommand.attemptId()).isEqualTo(first.attemptId());
        assertThat(anotherOperator.commandSha256()).isEqualTo(first.commandSha256());
        assertThat(anotherOperator.attemptId()).isNotEqualTo(first.attemptId());
    }

    @Test
    void canonicalizesCollectionActionsByExactDescriptorOrdinal() {
        List<DescriptorAction> mutable = new ArrayList<>(List.of(
                DescriptorAction.captureNow(9),
                DescriptorAction.selectExact(2, FIRST_SEGMENT_CAPTURE_ID)));
        SecFilingHistoryCollectionAttempt attempt =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        OPERATOR_REQUEST_ID,
                        ROOT_CAPTURE_ID,
                        mutable,
                        REQUESTED_AT);
        mutable.clear();

        assertThat(attempt.commandKind()).isEqualTo(CommandKind.COLLECT_EXACT_ROOT);
        assertThat(attempt.cik()).isNull();
        assertThat(attempt.rootCaptureId()).isEqualTo(ROOT_CAPTURE_ID);
        assertThat(attempt.descriptorActions())
                .extracting(DescriptorAction::descriptorOrdinal)
                .containsExactly(2, 9);
        assertThat(attempt.captureNowAction())
                .contains(DescriptorAction.captureNow(9));
        assertThat(attempt.descriptorActions()).isUnmodifiable();

        SecFilingHistoryCollectionAttempt reordered =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        OPERATOR_REQUEST_ID,
                        ROOT_CAPTURE_ID,
                        List.of(
                                DescriptorAction.selectExact(
                                        2, FIRST_SEGMENT_CAPTURE_ID),
                                DescriptorAction.captureNow(9)),
                        REQUESTED_AT.plusSeconds(1));
        assertThat(reordered.commandSha256()).isEqualTo(attempt.commandSha256());
        assertThat(reordered.attemptId()).isEqualTo(attempt.attemptId());
    }

    @Test
    void rejectsNoncanonicalOperatorRequestAndInvalidCommandShapes() {
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCaptureRoot(
                OPERATOR_REQUEST_ID.toUpperCase(), "320193", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical nonzero UUID");
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCaptureRoot(
                "00000000-0000-0000-0000-000000000000", "320193", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical nonzero UUID");
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCaptureRoot(
                OPERATOR_REQUEST_ID, "0000000000", REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CIK");
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                OPERATOR_REQUEST_ID,
                "A".repeat(64),
                List.of(),
                REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rootCaptureId");
    }

    @Test
    void rejectsDuplicateActionsAndMoreThanOneProviderInvocation() {
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                OPERATOR_REQUEST_ID,
                ROOT_CAPTURE_ID,
                List.of(
                        DescriptorAction.selectExact(1, FIRST_SEGMENT_CAPTURE_ID),
                        DescriptorAction.captureNow(1)),
                REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("descriptorOrdinal must be unique");
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                OPERATOR_REQUEST_ID,
                ROOT_CAPTURE_ID,
                List.of(
                        DescriptorAction.selectExact(1, FIRST_SEGMENT_CAPTURE_ID),
                        DescriptorAction.selectExact(2, FIRST_SEGMENT_CAPTURE_ID)),
                REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("selectedSegmentCaptureId must be unique");
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                OPERATOR_REQUEST_ID,
                ROOT_CAPTURE_ID,
                List.of(
                        DescriptorAction.captureNow(1),
                        DescriptorAction.captureNow(2)),
                REQUESTED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most one CAPTURE_NOW");
    }

    @Test
    void dispatchIsAppendOnlyAndAloneMeansIndeterminate() {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        OPERATOR_REQUEST_ID,
                        ROOT_CAPTURE_ID,
                        List.of(DescriptorAction.captureNow(4)),
                        REQUESTED_AT);
        SecFilingHistoryCollectionAttempt dispatched = planned.withProviderDispatch(
                ProviderDispatch.captureHistoricalSegment(4, DISPATCHED_AT));

        assertThat(dispatched.lifecycleState())
                .isEqualTo(LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE);
        assertThat(dispatched.terminalOutcome()).isNull();
        assertThatThrownBy(() -> dispatched.withProviderDispatch(
                ProviderDispatch.captureHistoricalSegment(4, DISPATCHED_AT)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> planned.withProviderDispatch(
                ProviderDispatch.captureHistoricalSegment(3, DISPATCHED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact CAPTURE_NOW");
    }

    @Test
    void successfulRootCaptureProducesOnlyExactRootArtifact() {
        SecFilingHistoryCollectionAttempt terminal =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                                OPERATOR_REQUEST_ID, "320193", REQUESTED_AT)
                        .withProviderDispatch(ProviderDispatch.captureRoot(DISPATCHED_AT))
                        .withTerminalOutcome(TerminalOutcome.succeeded(
                                TerminalStage.ROOT_CAPTURE,
                                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                                ArtifactAppend.inserted(ROOT_CAPTURE_ID),
                                ArtifactAppend.notApplicable(),
                                ArtifactAppend.notApplicable(),
                                COMPLETED_AT));

        assertThat(terminal.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);
        assertThat(terminal.terminalOutcome().rootArtifact().artifactId())
                .isEqualTo(ROOT_CAPTURE_ID);
        assertThatThrownBy(() -> terminal.withTerminalOutcome(terminal.terminalOutcome()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("append-only");
    }

    @Test
    void successfulSelectionOnlyCollectionUsesNoProviderAndProducesManifest() {
        SecFilingHistoryCollectionAttempt terminal =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                                OPERATOR_REQUEST_ID,
                                ROOT_CAPTURE_ID,
                                List.of(DescriptorAction.selectExact(
                                        0, FIRST_SEGMENT_CAPTURE_ID)),
                                REQUESTED_AT)
                        .withTerminalOutcome(TerminalOutcome.succeeded(
                                TerminalStage.MANIFEST_ASSEMBLY,
                                RequestDisposition.NO_PROVIDER_INVOCATION,
                                ArtifactAppend.notApplicable(),
                                ArtifactAppend.notApplicable(),
                                ArtifactAppend.identicalReplay(MANIFEST_ID),
                                COMPLETED_AT));

        assertThat(terminal.providerDispatch()).isNull();
        assertThat(terminal.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);
        assertThat(terminal.terminalOutcome().manifestArtifact().artifactId())
                .isEqualTo(MANIFEST_ID);
    }

    @Test
    void successfulCapturedSegmentCollectionProducesSegmentAndManifest() {
        SecFilingHistoryCollectionAttempt terminal =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                                OPERATOR_REQUEST_ID,
                                ROOT_CAPTURE_ID,
                                List.of(
                                        DescriptorAction.selectExact(
                                                0, FIRST_SEGMENT_CAPTURE_ID),
                                        DescriptorAction.captureNow(4)),
                                REQUESTED_AT)
                        .withProviderDispatch(
                                ProviderDispatch.captureHistoricalSegment(4, DISPATCHED_AT))
                        .withTerminalOutcome(TerminalOutcome.succeeded(
                                TerminalStage.MANIFEST_ASSEMBLY,
                                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                                ArtifactAppend.notApplicable(),
                                ArtifactAppend.identicalReplay(SECOND_SEGMENT_CAPTURE_ID),
                                ArtifactAppend.inserted(MANIFEST_ID),
                                COMPLETED_AT));

        assertThat(terminal.lifecycleState()).isEqualTo(LifecycleState.TERMINAL_SUCCEEDED);
        assertThat(terminal.terminalOutcome().segmentArtifact().artifactId())
                .isEqualTo(SECOND_SEGMENT_CAPTURE_ID);
        assertThat(terminal.terminalOutcome().manifestArtifact().artifactId())
                .isEqualTo(MANIFEST_ID);
    }

    @Test
    void knownFailureHasClosedShapeAndCannotClaimArtifacts() {
        SecFilingHistoryCollectionAttempt dispatched =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                                OPERATOR_REQUEST_ID, "320193", REQUESTED_AT)
                        .withProviderDispatch(ProviderDispatch.captureRoot(DISPATCHED_AT));
        SecFilingHistoryCollectionAttempt failed = dispatched.withTerminalOutcome(
                TerminalOutcome.failed(
                        TerminalStage.ROOT_CAPTURE,
                        RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                        FailureCode.PROVIDER_HTTP_STATUS,
                        429,
                        COMPLETED_AT));

        assertThat(failed.lifecycleState())
                .isEqualTo(LifecycleState.TERMINAL_FAILED_KNOWN);
        assertThat(failed.terminalOutcome().httpStatus()).isEqualTo(429);
        assertThat(failed.terminalOutcome().rootArtifact().isProduced()).isFalse();

        assertThatThrownBy(() -> dispatched.withTerminalOutcome(new TerminalOutcome(
                SecFilingHistoryCollectionAttempt.TerminalStatus.FAILED_KNOWN,
                TerminalStage.ROOT_CAPTURE,
                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                FailureCode.PROVIDER_HTTP_STATUS,
                429,
                ArtifactAppend.inserted(ROOT_CAPTURE_ID),
                ArtifactAppend.notApplicable(),
                ArtifactAppend.notApplicable(),
                COMPLETED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot claim atomically committed artifacts");

        assertThatThrownBy(() -> TerminalOutcome.failed(
                TerminalStage.ROOT_CAPTURE,
                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                FailureCode.PROVIDER_HTTP_STATUS,
                200,
                COMPLETED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("valid only for a provider HTTP-status failure");
    }

    @Test
    void providerGateCanRejectBeforeAProviderDispatchIsRecorded() {
        SecFilingHistoryCollectionAttempt failed =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                                OPERATOR_REQUEST_ID, "320193", REQUESTED_AT)
                        .withTerminalOutcome(TerminalOutcome.failed(
                                TerminalStage.PROVIDER_GATE,
                                RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED,
                                FailureCode.PROVIDER_GATE_CLOSED,
                                null,
                                COMPLETED_AT));

        assertThat(failed.providerDispatch()).isNull();
        assertThat(failed.lifecycleState())
                .isEqualTo(LifecycleState.TERMINAL_FAILED_KNOWN);
    }

    @Test
    void failureCodesRejectIncompatibleCommandStageAndDispositionShapes() {
        SecFilingHistoryCollectionAttempt dispatchedRoot =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                                OPERATOR_REQUEST_ID, "320193", REQUESTED_AT)
                        .withProviderDispatch(ProviderDispatch.captureRoot(DISPATCHED_AT));
        assertThatThrownBy(() -> dispatchedRoot.withTerminalOutcome(
                TerminalOutcome.failed(
                        TerminalStage.ROOT_CAPTURE,
                        RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN,
                        FailureCode.PROVIDER_HTTP_STATUS,
                        429,
                        COMPLETED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("terminal failure shape is inconsistent");

        SecFilingHistoryCollectionAttempt selectionOnly =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        ANOTHER_OPERATOR_REQUEST_ID,
                        ROOT_CAPTURE_ID,
                        List.of(DescriptorAction.selectExact(0, FIRST_SEGMENT_CAPTURE_ID)),
                        REQUESTED_AT);
        assertThatThrownBy(() -> selectionOnly.withTerminalOutcome(
                TerminalOutcome.failed(
                        TerminalStage.PROVIDER_GATE,
                        RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED,
                        FailureCode.PROVIDER_GATE_CLOSED,
                        null,
                        COMPLETED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider-bound command");

        assertThatThrownBy(() -> dispatchedRoot.withTerminalOutcome(
                TerminalOutcome.failed(
                        TerminalStage.SEGMENT_CAPTURE,
                        RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                        FailureCode.SOURCE_CAPTURE_PERSISTENCE_FAILED,
                        null,
                        COMPLETED_AT)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid terminal shape");
    }

    @Test
    void pointInTimeViewNeverBackdatesHeaderDispatchOrTerminal() {
        SecFilingHistoryCollectionAttempt terminal =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                                OPERATOR_REQUEST_ID, "320193", REQUESTED_AT)
                        .withProviderDispatch(ProviderDispatch.captureRoot(DISPATCHED_AT))
                        .withTerminalOutcome(TerminalOutcome.succeeded(
                                TerminalStage.ROOT_CAPTURE,
                                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                                ArtifactAppend.inserted(ROOT_CAPTURE_ID),
                                ArtifactAppend.notApplicable(),
                                ArtifactAppend.notApplicable(),
                                COMPLETED_AT));

        assertThat(terminal.knownAt(REQUESTED_AT.minusNanos(1_000))).isEmpty();
        assertThat(terminal.knownAt(REQUESTED_AT).orElseThrow().lifecycleState())
                .isEqualTo(LifecycleState.PLANNED);
        assertThat(terminal.knownAt(DISPATCHED_AT).orElseThrow().lifecycleState())
                .isEqualTo(LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE);
        assertThat(terminal.knownAt(COMPLETED_AT).orElseThrow()).isEqualTo(terminal);
    }

    @Test
    void rejectsTamperedIdentityAndNonMicrosecondLedgerTimes() {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        OPERATOR_REQUEST_ID, "320193", REQUESTED_AT);

        assertThatThrownBy(() -> new SecFilingHistoryCollectionAttempt(
                "e".repeat(64),
                planned.commandSha256(),
                planned.operatorRequestId(),
                planned.commandKind(),
                planned.cik(),
                planned.rootCaptureId(),
                planned.descriptorActions(),
                planned.requestedAt(),
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attemptId must reproduce");
        assertThatThrownBy(() -> SecFilingHistoryCollectionAttempt.planCaptureRoot(
                OPERATOR_REQUEST_ID,
                "320193",
                REQUESTED_AT.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");
    }
}
