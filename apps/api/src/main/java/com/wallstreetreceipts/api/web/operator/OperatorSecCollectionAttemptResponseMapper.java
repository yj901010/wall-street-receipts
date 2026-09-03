package com.wallstreetreceipts.api.web.operator;

import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppend;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.LifecycleState;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;

final class OperatorSecCollectionAttemptResponseMapper {

    private OperatorSecCollectionAttemptResponseMapper() {
    }

    static OperatorSecCollectionAttemptResponses.Attempt toResponse(
            SecFilingHistoryCollectionAttempt attempt) {
        LifecycleState lifecycle = attempt.lifecycleState();
        TerminalOutcome terminal = attempt.terminalOutcome();
        boolean attemptIndeterminate =
                lifecycle == LifecycleState.PROVIDER_DISPATCHED_INDETERMINATE;
        boolean providerStartOrResponseUnknown = attemptIndeterminate
                || terminal != null
                        && terminal.requestDisposition()
                                == RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN;
        return new OperatorSecCollectionAttemptResponses.Attempt(
                SecFilingHistoryCollectionAttempt.SCHEMA_VERSION,
                SecFilingHistoryCollectionAttempt.PROVIDER,
                SecFilingHistoryCollectionAttempt.PRODUCT,
                SecFilingHistoryCollectionAttempt.POLICY_VERSION,
                attempt.attemptId(),
                attempt.commandSha256(),
                attempt.operatorRequestId(),
                attempt.commandKind(),
                attempt.cik(),
                attempt.rootCaptureId(),
                attempt.descriptorActions().stream()
                        .map(action -> new OperatorSecCollectionAttemptResponses.DescriptorAction(
                                action.descriptorOrdinal(),
                                action.actionKind(),
                                action.selectedSegmentCaptureId()))
                        .toList(),
                attempt.requestedAt(),
                attempt.maxProviderInvocations(),
                lifecycle,
                attemptIndeterminate,
                providerStartOrResponseUnknown,
                false,
                providerDispatch(attempt.providerDispatch()),
                terminalOutcome(terminal));
    }

    private static OperatorSecCollectionAttemptResponses.ProviderDispatch providerDispatch(
            ProviderDispatch dispatch) {
        return dispatch == null
                ? null
                : new OperatorSecCollectionAttemptResponses.ProviderDispatch(
                        dispatch.operation(),
                        dispatch.descriptorOrdinal(),
                        dispatch.dispatchedAt());
    }

    private static OperatorSecCollectionAttemptResponses.TerminalOutcome terminalOutcome(
            TerminalOutcome outcome) {
        return outcome == null
                ? null
                : new OperatorSecCollectionAttemptResponses.TerminalOutcome(
                        outcome.status(),
                        outcome.stage(),
                        outcome.requestDisposition(),
                        outcome.failureCode(),
                        outcome.httpStatus(),
                        artifact(outcome.rootArtifact()),
                        artifact(outcome.segmentArtifact()),
                        artifact(outcome.manifestArtifact()),
                        outcome.completedAt());
    }

    private static OperatorSecCollectionAttemptResponses.ArtifactAppend artifact(
            ArtifactAppend artifact) {
        return new OperatorSecCollectionAttemptResponses.ArtifactAppend(
                artifact.artifactId(), artifact.status());
    }
}
