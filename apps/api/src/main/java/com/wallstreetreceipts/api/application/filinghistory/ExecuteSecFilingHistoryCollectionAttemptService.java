package com.wallstreetreceipts.api.application.filinghistory;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptClaimOutcome;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptCommitter;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.application.port.out.SourceCaptureRequestException;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStatus;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/**
 * Executes one claimed operator command with zero retries and at most one provider invocation.
 *
 * <p>Provider I/O occurs only after the immutable claim, exact-evidence preflight when needed,
 * process-local nonblocking ownership, and durable provider-port dispatch. A dispatch without a
 * terminal is intentionally left indeterminate and is never resumed here.
 */
public final class ExecuteSecFilingHistoryCollectionAttemptService {

    private static final String SEC_PROVIDER = "sec-edgar";
    private static final String ROOT_PRODUCT = "edgar-submissions-api";
    private static final String ROOT_PARSER = "SEC_SUBMISSIONS_CATALOG_V2";
    private static final String SEGMENT_PRODUCT =
            "edgar-submissions-historical-segment-api";
    private static final String SEGMENT_PARSER =
            "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1";

    private final SecFilingHistoryCollectionAttemptRepository attemptRepository;
    private final SecFilingHistoryCollectionAttemptCommitter committer;
    private final FilingCatalogCaptureRepository rootRepository;
    private final HistoricalFilingSegmentCaptureRepository segmentRepository;
    private final Optional<FilingCatalogCaptureProvider> rootProvider;
    private final Optional<HistoricalFilingSegmentCaptureProvider> segmentProvider;
    private final SingleJvmSecFilingHistoryCollectionAttemptMutex mutex;
    private final Clock clock;

    public ExecuteSecFilingHistoryCollectionAttemptService(
            SecFilingHistoryCollectionAttemptRepository attemptRepository,
            SecFilingHistoryCollectionAttemptCommitter committer,
            FilingCatalogCaptureRepository rootRepository,
            HistoricalFilingSegmentCaptureRepository segmentRepository,
            Optional<FilingCatalogCaptureProvider> rootProvider,
            Optional<HistoricalFilingSegmentCaptureProvider> segmentProvider,
            SingleJvmSecFilingHistoryCollectionAttemptMutex mutex,
            Clock clock) {
        this.attemptRepository = Objects.requireNonNull(
                attemptRepository, "attemptRepository");
        this.committer = Objects.requireNonNull(committer, "committer");
        this.rootRepository = Objects.requireNonNull(rootRepository, "rootRepository");
        this.segmentRepository = Objects.requireNonNull(
                segmentRepository, "segmentRepository");
        this.rootProvider = Objects.requireNonNull(rootProvider, "rootProvider");
        this.segmentProvider = Objects.requireNonNull(segmentProvider, "segmentProvider");
        this.mutex = Objects.requireNonNull(mutex, "mutex");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SecFilingHistoryCollectionAttempt captureRoot(
            String operatorRequestId,
            String cik) {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(
                        operatorRequestId, cik, now());
        Claim claim = claim(planned);
        if (claim.replay()) {
            return claim.attempt();
        }
        FilingCatalogCaptureProvider provider = rootProvider.orElse(null);
        if (!isExactSecProvider(provider)) {
            return providerGateClosed(claim.attempt());
        }
        Optional<SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease> lease =
                mutex.tryAcquire();
        if (lease.isEmpty()) {
            return providerGateClosed(claim.attempt());
        }
        try (SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease ignored = lease.orElseThrow()) {
            SecFilingHistoryCollectionAttempt dispatched = appendDispatch(
                    claim.attempt(), ProviderDispatch.captureRoot(now()));
            FilingCatalogCapture pendingCapture;
            try {
                pendingCapture = provider.loadCatalogCapture(dispatched.cik());
                validatePendingRootCapture(dispatched, pendingCapture);
            } catch (SourceCaptureRequestException exception) {
                return providerFailure(dispatched, TerminalStage.ROOT_CAPTURE, exception);
            } catch (RuntimeException exception) {
                return unknownProviderFailure(dispatched, TerminalStage.ROOT_CAPTURE);
            }
            SecFilingHistoryCollectionAttempt committed =
                    committer.commitRootCaptureSuccess(
                            dispatched.attemptId(), pendingCapture, now());
            return requireCommittedSuccess(
                    dispatched,
                    committed,
                    pendingCapture.captureId(),
                    null,
                    false);
        }
    }

    public SecFilingHistoryCollectionAttempt collectExactRoot(
            String operatorRequestId,
            String rootCaptureId,
            List<DescriptorAction> descriptorActions) {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        operatorRequestId, rootCaptureId, descriptorActions, now());
        Claim claim = claim(planned);
        if (claim.replay()) {
            return claim.attempt();
        }

        FilingCatalogCapture exactRoot;
        try {
            exactRoot = validateExactEvidence(claim.attempt());
        } catch (ExactEvidenceValidationException exception) {
            return appendFailure(
                    claim.attempt(),
                    TerminalStage.EXACT_EVIDENCE_VALIDATION,
                    RequestDisposition.NO_PROVIDER_INVOCATION,
                    FailureCode.EXACT_EVIDENCE_VALIDATION_FAILED,
                    null);
        }

        Optional<DescriptorAction> captureNow = claim.attempt().captureNowAction();
        if (captureNow.isEmpty()) {
            SecFilingHistoryCollectionAttempt committed =
                    committer.commitSelectionOnlyCollectionSuccess(
                            claim.attempt().attemptId(), now());
            return requireCommittedSuccess(
                    claim.attempt(), committed, null, null, true);
        }

        HistoricalFilingSegmentCaptureProvider provider = segmentProvider.orElse(null);
        if (!isExactSecProvider(provider)) {
            return providerGateClosed(claim.attempt());
        }
        Optional<SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease> lease =
                mutex.tryAcquire();
        if (lease.isEmpty()) {
            return providerGateClosed(claim.attempt());
        }
        int descriptorOrdinal = captureNow.orElseThrow().descriptorOrdinal();
        try (SingleJvmSecFilingHistoryCollectionAttemptMutex.Lease ignored = lease.orElseThrow()) {
            SecFilingHistoryCollectionAttempt dispatched = appendDispatch(
                    claim.attempt(),
                    ProviderDispatch.captureHistoricalSegment(descriptorOrdinal, now()));
            HistoricalFilingSegmentCapture pendingCapture;
            try {
                pendingCapture = provider.loadHistoricalSegmentCapture(
                        exactRoot, descriptorOrdinal);
                validatePendingSegmentCapture(exactRoot, descriptorOrdinal, pendingCapture);
            } catch (SourceCaptureRequestException exception) {
                return providerFailure(dispatched, TerminalStage.SEGMENT_CAPTURE, exception);
            } catch (RuntimeException exception) {
                return unknownProviderFailure(dispatched, TerminalStage.SEGMENT_CAPTURE);
            }
            SecFilingHistoryCollectionAttempt committed =
                    committer.commitCapturedSegmentCollectionSuccess(
                            dispatched.attemptId(), pendingCapture, now());
            return requireCommittedSuccess(
                    dispatched,
                    committed,
                    null,
                    pendingCapture.captureId(),
                    true);
        }
    }

    private Claim claim(SecFilingHistoryCollectionAttempt planned) {
        SecFilingHistoryCollectionAttemptClaimOutcome outcome =
                attemptRepository.claim(planned);
        SecFilingHistoryCollectionAttempt durable = outcome.attempt();
        if (!planned.attemptId().equals(durable.attemptId())
                || !planned.operatorRequestId().equals(durable.operatorRequestId())
                || !planned.commandSha256().equals(durable.commandSha256())) {
            throw new IllegalStateException(
                    "attempt claim returned another immutable command identity");
        }
        return new Claim(
                durable,
                outcome.status()
                        == SecFilingHistoryCollectionAttemptClaimOutcome.Status.IDENTICAL_REPLAY);
    }

    private FilingCatalogCapture validateExactEvidence(
            SecFilingHistoryCollectionAttempt attempt) {
        FilingCatalogCapture root = rootRepository
                .findByCaptureId(attempt.rootCaptureId())
                .orElseThrow(() -> new ExactEvidenceValidationException(
                        "exact durable root capture was not found"));
        validateDurableRoot(attempt.rootCaptureId(), root);

        for (DescriptorAction action : attempt.descriptorActions()) {
            if (action.descriptorOrdinal() >= root.catalog().historicalSegments().size()) {
                throw new ExactEvidenceValidationException(
                        "descriptor action does not identify the exact durable root");
            }
            if (action.actionKind()
                    == SecFilingHistoryCollectionAttempt.DescriptorActionKind.SELECT_EXACT) {
                HistoricalFilingSegmentCapture segment = segmentRepository
                        .findByCaptureId(action.selectedSegmentCaptureId())
                        .orElseThrow(() -> new ExactEvidenceValidationException(
                                "exact durable segment capture was not found"));
                validateDurableSegment(root, action, segment);
            }
        }
        return root;
    }

    private static void validateDurableRoot(
            String expectedCaptureId,
            FilingCatalogCapture root) {
        if (root == null
                || !expectedCaptureId.equals(root.captureId())
                || !SEC_PROVIDER.equals(root.catalog().provider())
                || !ROOT_PRODUCT.equals(root.catalog().product())
                || !ROOT_PARSER.equals(root.catalog().sourceReceipt().parserVersion())
                || root.catalog().sourceReceipt().bodyRetention()
                        != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new ExactEvidenceValidationException(
                    "root repository returned incompatible exact evidence");
        }
    }

    private static void validateDurableSegment(
            FilingCatalogCapture root,
            DescriptorAction action,
            HistoricalFilingSegmentCapture capture) {
        HistoricalFilingSegment segment = capture == null ? null : capture.segment();
        if (segment == null
                || !action.selectedSegmentCaptureId().equals(capture.captureId())
                || !root.captureId().equals(segment.rootCaptureId())
                || segment.descriptorOrdinal() != action.descriptorOrdinal()
                || !root.catalog().cik().equals(segment.cik())
                || !root.catalog().capturedAt().equals(segment.rootCapturedAt())
                || !root.catalog().historicalSegments()
                        .get(action.descriptorOrdinal())
                        .equals(segment.descriptor())
                || !SEC_PROVIDER.equals(segment.provider())
                || !SEGMENT_PRODUCT.equals(segment.product())
                || !SEGMENT_PARSER.equals(segment.sourceReceipt().parserVersion())
                || segment.sourceReceipt().bodyRetention()
                        != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new ExactEvidenceValidationException(
                    "segment repository returned incompatible exact evidence");
        }
    }

    private static void validatePendingRootCapture(
            SecFilingHistoryCollectionAttempt attempt,
            FilingCatalogCapture capture) {
        FilingCatalog catalog = capture == null ? null : capture.catalog();
        URI expectedUri = URI.create(
                "https://data.sec.gov/submissions/CIK" + attempt.cik() + ".json");
        if (catalog == null
                || !attempt.cik().equals(catalog.cik())
                || !SEC_PROVIDER.equals(catalog.provider())
                || !ROOT_PRODUCT.equals(catalog.product())
                || !expectedUri.equals(catalog.sourceUri())
                || !ROOT_PARSER.equals(catalog.sourceReceipt().parserVersion())
                || catalog.sourceReceipt().bodyRetention()
                        != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE) {
            throw SourceCaptureRequestException.responseInvalid();
        }
    }

    private static void validatePendingSegmentCapture(
            FilingCatalogCapture root,
            int descriptorOrdinal,
            HistoricalFilingSegmentCapture capture) {
        HistoricalFilingSegment segment = capture == null ? null : capture.segment();
        if (segment == null
                || !root.captureId().equals(segment.rootCaptureId())
                || segment.descriptorOrdinal() != descriptorOrdinal
                || !root.catalog().cik().equals(segment.cik())
                || !root.catalog().capturedAt().equals(segment.rootCapturedAt())
                || !root.catalog().historicalSegments()
                        .get(descriptorOrdinal)
                        .equals(segment.descriptor())
                || !SEC_PROVIDER.equals(segment.provider())
                || !SEGMENT_PRODUCT.equals(segment.product())
                || !SEGMENT_PARSER.equals(segment.sourceReceipt().parserVersion())
                || segment.sourceReceipt().bodyRetention()
                        != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE) {
            throw SourceCaptureRequestException.responseInvalid();
        }
    }

    private SecFilingHistoryCollectionAttempt appendDispatch(
            SecFilingHistoryCollectionAttempt attempt,
            ProviderDispatch dispatch) {
        SecFilingHistoryCollectionAttempt durable = attemptRepository
                .appendProviderDispatch(attempt.attemptId(), dispatch);
        if (!attempt.attemptId().equals(durable.attemptId())
                || durable.providerDispatch() == null
                || !dispatch.equals(durable.providerDispatch())
                || durable.terminalOutcome() != null) {
            throw new IllegalStateException(
                    "provider dispatch append returned inconsistent attempt state");
        }
        return durable;
    }

    private static SecFilingHistoryCollectionAttempt requireCommittedSuccess(
            SecFilingHistoryCollectionAttempt expected,
            SecFilingHistoryCollectionAttempt committed,
            String expectedRootArtifactId,
            String expectedSegmentArtifactId,
            boolean manifestRequired) {
        if (committed == null
                || !expected.attemptId().equals(committed.attemptId())
                || !expected.commandSha256().equals(committed.commandSha256())
                || !expected.operatorRequestId().equals(committed.operatorRequestId())
                || expected.commandKind() != committed.commandKind()
                || !Objects.equals(expected.cik(), committed.cik())
                || !Objects.equals(expected.rootCaptureId(), committed.rootCaptureId())
                || !expected.descriptorActions().equals(committed.descriptorActions())
                || !expected.requestedAt().equals(committed.requestedAt())
                || !Objects.equals(expected.providerDispatch(), committed.providerDispatch())
                || committed.terminalOutcome() == null
                || committed.terminalOutcome().status() != TerminalStatus.SUCCEEDED
                || !Objects.equals(
                        expectedRootArtifactId,
                        committed.terminalOutcome().rootArtifact().artifactId())
                || !Objects.equals(
                        expectedSegmentArtifactId,
                        committed.terminalOutcome().segmentArtifact().artifactId())
                || committed.terminalOutcome().manifestArtifact().isProduced()
                        != manifestRequired) {
            throw new IllegalStateException(
                    "attempt committer returned inconsistent successful state");
        }
        return committed;
    }

    private SecFilingHistoryCollectionAttempt providerGateClosed(
            SecFilingHistoryCollectionAttempt attempt) {
        return appendFailure(
                attempt,
                TerminalStage.PROVIDER_GATE,
                RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED,
                FailureCode.PROVIDER_GATE_CLOSED,
                null);
    }

    private SecFilingHistoryCollectionAttempt providerFailure(
            SecFilingHistoryCollectionAttempt attempt,
            TerminalStage stage,
            SourceCaptureRequestException exception) {
        ProviderFailure mapped = switch (exception.failureKind()) {
            case PROVIDER_GATE_CLOSED -> new ProviderFailure(
                    FailureCode.PROVIDER_GATE_CLOSED,
                    RequestDisposition.PROVIDER_INVOCATION_NOT_STARTED,
                    null,
                    TerminalStage.PROVIDER_GATE);
            case REQUEST_FAILED -> new ProviderFailure(
                    FailureCode.PROVIDER_REQUEST_FAILED,
                    RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN,
                    null,
                    stage);
            case HTTP_STATUS -> new ProviderFailure(
                    FailureCode.PROVIDER_HTTP_STATUS,
                    RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                    exception.httpStatus(),
                    stage);
            case RESPONSE_UNREADABLE -> new ProviderFailure(
                    FailureCode.PROVIDER_RESPONSE_UNREADABLE,
                    RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN,
                    null,
                    stage);
            case RESPONSE_TOO_LARGE -> new ProviderFailure(
                    FailureCode.PROVIDER_RESPONSE_TOO_LARGE,
                    RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                    null,
                    stage);
            case RESPONSE_INVALID -> new ProviderFailure(
                    FailureCode.PROVIDER_RESPONSE_INVALID,
                    RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                    null,
                    stage);
        };
        return appendFailure(
                attempt,
                mapped.stage(),
                mapped.disposition(),
                mapped.failureCode(),
                mapped.httpStatus());
    }

    private SecFilingHistoryCollectionAttempt unknownProviderFailure(
            SecFilingHistoryCollectionAttempt attempt,
            TerminalStage stage) {
        return appendFailure(
                attempt,
                stage,
                RequestDisposition.PROVIDER_START_OR_RESPONSE_UNKNOWN,
                FailureCode.PROVIDER_REQUEST_FAILED,
                null);
    }

    private SecFilingHistoryCollectionAttempt appendFailure(
            SecFilingHistoryCollectionAttempt attempt,
            TerminalStage stage,
            RequestDisposition disposition,
            FailureCode failureCode,
            Integer httpStatus) {
        TerminalOutcome failure = TerminalOutcome.failed(
                stage, disposition, failureCode, httpStatus, now());
        SecFilingHistoryCollectionAttempt durable = attemptRepository
                .appendTerminalOutcome(attempt.attemptId(), failure);
        if (!attempt.attemptId().equals(durable.attemptId())
                || durable.terminalOutcome() == null
                || !failure.equals(durable.terminalOutcome())) {
            throw new IllegalStateException(
                    "terminal outcome append returned inconsistent attempt state");
        }
        return durable;
    }

    private static boolean isExactSecProvider(FilingCatalogCaptureProvider provider) {
        return provider != null && SEC_PROVIDER.equals(provider.providerName());
    }

    private static boolean isExactSecProvider(
            HistoricalFilingSegmentCaptureProvider provider) {
        return provider != null && SEC_PROVIDER.equals(provider.providerName());
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    private record Claim(
            SecFilingHistoryCollectionAttempt attempt,
            boolean replay) {
    }

    private record ProviderFailure(
            FailureCode failureCode,
            RequestDisposition disposition,
            Integer httpStatus,
            TerminalStage stage) {
    }

    private static final class ExactEvidenceValidationException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private ExactEvidenceValidationException(String message) {
            super(message);
        }
    }
}
