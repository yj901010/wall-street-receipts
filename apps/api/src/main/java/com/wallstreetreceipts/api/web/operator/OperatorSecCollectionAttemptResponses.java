package com.wallstreetreceipts.api.web.operator;

import java.time.Instant;
import java.util.List;

import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppendStatus;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.CommandKind;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorActionKind;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.LifecycleState;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderOperation;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStatus;

/** Allowlisted operator status representation; it contains no provider configuration or secrets. */
public final class OperatorSecCollectionAttemptResponses {

    private OperatorSecCollectionAttemptResponses() {
    }

    public record Attempt(
            String schemaVersion,
            String provider,
            String product,
            String policyVersion,
            String attemptId,
            String commandSha256,
            String operatorRequestId,
            CommandKind commandKind,
            String cik,
            String rootCaptureId,
            List<DescriptorAction> descriptorActions,
            Instant requestedAt,
            int maxProviderInvocations,
            LifecycleState lifecycleState,
            boolean attemptIndeterminate,
            boolean providerStartOrResponseUnknown,
            boolean automaticRetryAllowed,
            ProviderDispatch providerDispatch,
            TerminalOutcome terminalOutcome) {

        public Attempt {
            descriptorActions = List.copyOf(descriptorActions);
        }
    }

    public record DescriptorAction(
            int descriptorOrdinal,
            DescriptorActionKind actionKind,
            String selectedSegmentCaptureId) {
    }

    public record ProviderDispatch(
            ProviderOperation operation,
            Integer descriptorOrdinal,
            Instant dispatchedAt) {
    }

    public record ArtifactAppend(
            String artifactId,
            ArtifactAppendStatus status) {
    }

    public record TerminalOutcome(
            TerminalStatus status,
            TerminalStage stage,
            RequestDisposition requestDisposition,
            FailureCode failureCode,
            Integer providerHttpStatus,
            ArtifactAppend rootArtifact,
            ArtifactAppend segmentArtifact,
            ArtifactAppend manifestArtifact,
            Instant completedAt) {
    }
}
