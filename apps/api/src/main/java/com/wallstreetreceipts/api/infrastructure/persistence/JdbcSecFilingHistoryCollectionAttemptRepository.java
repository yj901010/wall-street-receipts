package com.wallstreetreceipts.api.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;
import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;
import com.wallstreetreceipts.api.application.filinghistory.PersistFilingHistoryCollectionManifestService;
import com.wallstreetreceipts.api.application.filinghistory.PersistFilingHistoryCollectionManifestService.DescriptorCaptureSelection;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptClaimOutcome;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptCommitter;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;
import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppend;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ArtifactAppendStatus;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.CommandKind;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorActionKind;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderOperation;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStatus;

/**
 * JDBC append-only ledger and local success committer for one bounded SEC collection attempt.
 * Provider I/O is deliberately absent from this adapter.
 */
@Repository
public class JdbcSecFilingHistoryCollectionAttemptRepository
        implements SecFilingHistoryCollectionAttemptRepository,
                SecFilingHistoryCollectionAttemptCommitter {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final String SELECT_HEADER = """
            SELECT attempt_id, schema_version, provider, product, policy_version,
                   command_sha256, operator_request_id, command_kind, cik,
                   root_capture_id, requested_at, max_provider_invocations,
                   descriptor_action_count, immutable
            FROM sec_filing_collection_attempts
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final FilingCatalogCaptureRepository rootRepository;
    private final HistoricalFilingSegmentCaptureRepository segmentRepository;
    private final PersistFilingHistoryCollectionManifestService manifestService;
    private volatile Boolean postgreSql;

    public JdbcSecFilingHistoryCollectionAttemptRepository(
            NamedParameterJdbcTemplate jdbc,
            FilingCatalogCaptureRepository rootRepository,
            HistoricalFilingSegmentCaptureRepository segmentRepository,
            PersistFilingHistoryCollectionManifestService manifestService) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.rootRepository = Objects.requireNonNull(rootRepository, "rootRepository");
        this.segmentRepository = Objects.requireNonNull(
                segmentRepository, "segmentRepository");
        this.manifestService = Objects.requireNonNull(manifestService, "manifestService");
    }

    @Override
    @Transactional
    public SecFilingHistoryCollectionAttemptClaimOutcome claim(
            SecFilingHistoryCollectionAttempt plannedAttempt) {
        Objects.requireNonNull(plannedAttempt, "plannedAttempt must not be null");
        if (plannedAttempt.providerDispatch() != null
                || plannedAttempt.terminalOutcome() != null) {
            throw new IllegalArgumentException("claim requires an unstarted planned attempt");
        }

        Optional<SecFilingHistoryCollectionAttempt> existing =
                findByOperatorRequestId(plannedAttempt.operatorRequestId());
        if (existing.isPresent()) {
            return claimReplayOrConflict(existing.orElseThrow(), plannedAttempt);
        }

        int inserted;
        try {
            inserted = insertHeader(plannedAttempt);
        } catch (DataIntegrityViolationException exception) {
            // PostgreSQL's ON CONFLICT path never throws for an idempotency race. H2 has no
            // equivalent clause, so its duplicate-key path needs the defensive reread.
            if (!isPostgreSql()) {
                Optional<SecFilingHistoryCollectionAttempt> raced =
                        findByOperatorRequestId(plannedAttempt.operatorRequestId());
                if (raced.isPresent()) {
                    return claimReplayOrConflict(raced.orElseThrow(), plannedAttempt);
                }
            }
            throw rejectedClaim(plannedAttempt);
        }
        if (inserted == 0) {
            SecFilingHistoryCollectionAttempt raced = findByOperatorRequestId(
                    plannedAttempt.operatorRequestId()).orElseThrow(() ->
                            new IllegalArgumentException(
                                    "attempt claim conflicted without an operator-request row"));
            return claimReplayOrConflict(raced, plannedAttempt);
        }

        try {
            insertDescriptorActions(plannedAttempt);
        } catch (DataIntegrityViolationException exception) {
            // The transaction rolls the already-inserted header back. Do not expose vendor SQL,
            // constraint names, or a partial attempt for caller-supplied exact evidence.
            throw rejectedClaim(plannedAttempt);
        }
        SecFilingHistoryCollectionAttempt persisted = findByAttemptId(
                plannedAttempt.attemptId()).orElseThrow(() -> new IllegalStateException(
                        "inserted collection attempt could not be reconstructed"));
        if (!persisted.equals(plannedAttempt)) {
            throw new IllegalArgumentException(
                    "inserted collection attempt did not round-trip exactly");
        }
        return new SecFilingHistoryCollectionAttemptClaimOutcome(
                SecFilingHistoryCollectionAttemptClaimOutcome.Status.CLAIMED,
                persisted);
    }

    @Override
    @Transactional
    public SecFilingHistoryCollectionAttempt appendProviderDispatch(
            String attemptId,
            ProviderDispatch dispatch) {
        Objects.requireNonNull(dispatch, "dispatch must not be null");
        SecFilingHistoryCollectionAttempt existing = requireAttempt(attemptId);
        if (existing.providerDispatch() != null) {
            if (existing.providerDispatch().equals(dispatch)) {
                return existing;
            }
            throw new IllegalArgumentException(
                    "attempt already contains another provider dispatch");
        }
        SecFilingHistoryCollectionAttempt proposed = existing.withProviderDispatch(dispatch);

        int inserted;
        try {
            inserted = insertDispatch(proposed);
        } catch (DataIntegrityViolationException exception) {
            Optional<SecFilingHistoryCollectionAttempt> raced = findByAttemptId(attemptId);
            if (raced.isPresent() && dispatch.equals(raced.orElseThrow().providerDispatch())) {
                return raced.orElseThrow();
            }
            throw exception;
        }
        if (inserted == 0) {
            SecFilingHistoryCollectionAttempt raced = requireAttempt(attemptId);
            if (dispatch.equals(raced.providerDispatch())) {
                return raced;
            }
            throw new IllegalArgumentException(
                    "provider dispatch insert conflicted with another durable fact");
        }
        return requireExactRoundTrip(proposed, "provider dispatch");
    }

    @Override
    @Transactional
    public SecFilingHistoryCollectionAttempt appendTerminalOutcome(
            String attemptId,
            TerminalOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome must not be null");
        if (outcome.status() == TerminalStatus.SUCCEEDED) {
            throw new IllegalArgumentException(
                    "successful outcomes must use the atomic success committer");
        }
        SecFilingHistoryCollectionAttempt existing = requireAttempt(attemptId);
        if (existing.terminalOutcome() != null) {
            if (existing.terminalOutcome().equals(outcome)) {
                return existing;
            }
            throw new IllegalArgumentException("attempt already contains another terminal outcome");
        }
        SecFilingHistoryCollectionAttempt proposed = existing.withTerminalOutcome(outcome);
        return appendTerminal(proposed);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SecFilingHistoryCollectionAttempt> findByAttemptId(String attemptId) {
        requireSha256(attemptId, "attemptId");
        return findOne(
                SELECT_HEADER + " WHERE attempt_id = :attemptId",
                new MapSqlParameterSource("attemptId", attemptId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SecFilingHistoryCollectionAttempt> findByAttemptIdAtOrBefore(
            String attemptId,
            Instant evaluationAsOf) {
        requireSha256(attemptId, "attemptId");
        PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        return findOne(
                SELECT_HEADER + """
                         WHERE attempt_id = :attemptId
                           AND requested_at <= :evaluationAsOf
                        """,
                new MapSqlParameterSource()
                        .addValue("attemptId", attemptId)
                        .addValue("evaluationAsOf", utc(evaluationAsOf)),
                evaluationAsOf);
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_filing_collection_attempts", Long.class);
        return count == null ? 0 : count;
    }

    @Override
    @Transactional
    public SecFilingHistoryCollectionAttempt commitRootCaptureSuccess(
            String attemptId,
            FilingCatalogCapture pendingRootCapture,
            Instant completedAt) {
        Objects.requireNonNull(pendingRootCapture, "pendingRootCapture must not be null");
        SecFilingHistoryCollectionAttempt attempt = requireOpenAttempt(attemptId);
        if (attempt.commandKind() != CommandKind.CAPTURE_ROOT
                || attempt.providerDispatch() == null
                || attempt.providerDispatch().operation() != ProviderOperation.CAPTURE_ROOT) {
            throw new IllegalArgumentException(
                    "root success requires an exact dispatched CAPTURE_ROOT attempt");
        }
        if (!attempt.cik().equals(pendingRootCapture.catalog().cik())) {
            throw new IllegalArgumentException(
                    "pending root capture must match the planned canonical CIK");
        }

        FilingCatalogCaptureAppendResult appendResult = rootRepository.append(
                pendingRootCapture);
        FilingCatalogCapture durableRoot = rootRepository.findByCaptureId(
                pendingRootCapture.captureId()).orElseThrow(() -> new IllegalStateException(
                        "appended root capture could not be reconstructed"));
        if (!attempt.cik().equals(durableRoot.catalog().cik())) {
            throw new IllegalStateException(
                    "durable root capture does not match the planned canonical CIK");
        }

        TerminalOutcome outcome = TerminalOutcome.succeeded(
                TerminalStage.ROOT_CAPTURE,
                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                artifact(durableRoot.captureId(), appendResult),
                ArtifactAppend.notApplicable(),
                ArtifactAppend.notApplicable(),
                notBefore(completedAt, durableRoot.catalog().capturedAt()));
        return appendTerminal(attempt.withTerminalOutcome(outcome));
    }

    @Override
    @Transactional
    public SecFilingHistoryCollectionAttempt commitSelectionOnlyCollectionSuccess(
            String attemptId,
            Instant completedAt) {
        SecFilingHistoryCollectionAttempt attempt = requireOpenAttempt(attemptId);
        if (attempt.commandKind() != CommandKind.COLLECT_EXACT_ROOT
                || attempt.captureNowAction().isPresent()
                || attempt.providerDispatch() != null) {
            throw new IllegalArgumentException(
                    "selection-only success requires an undispatched exact-root collection");
        }

        FilingHistoryCollectionManifestAppendOutcome manifest = manifestService.persist(
                attempt.rootCaptureId(), selections(attempt, null));
        TerminalOutcome outcome = TerminalOutcome.succeeded(
                TerminalStage.MANIFEST_ASSEMBLY,
                RequestDisposition.NO_PROVIDER_INVOCATION,
                ArtifactAppend.notApplicable(),
                ArtifactAppend.notApplicable(),
                artifact(manifest.manifest().manifestId(), manifest.status()),
                notBefore(completedAt, manifest.manifest().assembledAt()));
        SecFilingHistoryCollectionAttempt proposed = attempt.withTerminalOutcome(outcome);
        verifyManifestArtifact(proposed, outcome.manifestArtifact().artifactId());
        return appendTerminal(proposed);
    }

    @Override
    @Transactional
    public SecFilingHistoryCollectionAttempt commitCapturedSegmentCollectionSuccess(
            String attemptId,
            HistoricalFilingSegmentCapture pendingSegmentCapture,
            Instant completedAt) {
        Objects.requireNonNull(pendingSegmentCapture, "pendingSegmentCapture must not be null");
        SecFilingHistoryCollectionAttempt attempt = requireOpenAttempt(attemptId);
        DescriptorAction captureNow = attempt.captureNowAction().orElseThrow(() ->
                new IllegalArgumentException(
                        "captured-segment success requires one CAPTURE_NOW action"));
        if (attempt.commandKind() != CommandKind.COLLECT_EXACT_ROOT
                || attempt.providerDispatch() == null
                || attempt.providerDispatch().operation()
                        != ProviderOperation.CAPTURE_HISTORICAL_SEGMENT
                || !Objects.equals(
                        attempt.providerDispatch().descriptorOrdinal(),
                        captureNow.descriptorOrdinal())) {
            throw new IllegalArgumentException(
                    "captured-segment success requires the exact dispatched descriptor");
        }
        if (!attempt.rootCaptureId().equals(
                        pendingSegmentCapture.segment().rootCaptureId())
                || captureNow.descriptorOrdinal()
                        != pendingSegmentCapture.segment().descriptorOrdinal()) {
            throw new IllegalArgumentException(
                    "pending segment capture must match the exact planned root descriptor");
        }

        HistoricalFilingSegmentCaptureAppendResult segmentAppend =
                segmentRepository.append(pendingSegmentCapture);
        HistoricalFilingSegmentCapture durableSegment = segmentRepository.findByCaptureId(
                pendingSegmentCapture.captureId()).orElseThrow(() ->
                        new IllegalStateException(
                                "appended historical segment could not be reconstructed"));
        if (!attempt.rootCaptureId().equals(durableSegment.segment().rootCaptureId())
                || captureNow.descriptorOrdinal()
                        != durableSegment.segment().descriptorOrdinal()) {
            throw new IllegalStateException(
                    "durable segment capture does not match the planned root descriptor");
        }

        FilingHistoryCollectionManifestAppendOutcome manifest = manifestService.persist(
                attempt.rootCaptureId(), selections(attempt, durableSegment.captureId()));
        TerminalOutcome outcome = TerminalOutcome.succeeded(
                TerminalStage.MANIFEST_ASSEMBLY,
                RequestDisposition.PROVIDER_RESPONSE_RECEIVED,
                ArtifactAppend.notApplicable(),
                artifact(durableSegment.captureId(), segmentAppend),
                artifact(manifest.manifest().manifestId(), manifest.status()),
                notBefore(completedAt, manifest.manifest().assembledAt()));
        SecFilingHistoryCollectionAttempt proposed = attempt.withTerminalOutcome(outcome);
        verifyManifestArtifact(proposed, outcome.manifestArtifact().artifactId());
        return appendTerminal(proposed);
    }

    private SecFilingHistoryCollectionAttemptClaimOutcome claimReplayOrConflict(
            SecFilingHistoryCollectionAttempt existing,
            SecFilingHistoryCollectionAttempt proposed) {
        if (!existing.sameCommandAs(proposed)) {
            throw new OperatorRequestConflictException();
        }
        return new SecFilingHistoryCollectionAttemptClaimOutcome(
                SecFilingHistoryCollectionAttemptClaimOutcome.Status.IDENTICAL_REPLAY,
                existing);
    }

    private static RuntimeException rejectedClaim(
            SecFilingHistoryCollectionAttempt plannedAttempt) {
        if (plannedAttempt.commandKind() == CommandKind.COLLECT_EXACT_ROOT) {
            return new ExactEvidenceNotAdmittedException();
        }
        return new IllegalStateException("collection attempt claim could not be persisted");
    }

    private int insertHeader(SecFilingHistoryCollectionAttempt attempt) {
        String sql = """
                INSERT INTO sec_filing_collection_attempts (
                    attempt_id, schema_version, provider, product, policy_version,
                    command_sha256, operator_request_id, command_kind, cik,
                    root_capture_id, requested_at, max_provider_invocations,
                    descriptor_action_count, immutable
                ) VALUES (
                    :attemptId, :schemaVersion, :provider, :product, :policyVersion,
                    :commandSha256, :operatorRequestId, :commandKind, :cik,
                    :rootCaptureId, :requestedAt, :maxProviderInvocations,
                    :descriptorActionCount, TRUE
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT DO NOTHING";
        }
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("attemptId", attempt.attemptId())
                .addValue("schemaVersion", SecFilingHistoryCollectionAttempt.SCHEMA_VERSION)
                .addValue("provider", SecFilingHistoryCollectionAttempt.PROVIDER)
                .addValue("product", SecFilingHistoryCollectionAttempt.PRODUCT)
                .addValue("policyVersion", SecFilingHistoryCollectionAttempt.POLICY_VERSION)
                .addValue("commandSha256", attempt.commandSha256())
                .addValue("operatorRequestId", attempt.operatorRequestId())
                .addValue("commandKind", attempt.commandKind().name())
                .addValue("cik", attempt.cik())
                .addValue("rootCaptureId", attempt.rootCaptureId())
                .addValue("requestedAt", utc(attempt.requestedAt()))
                .addValue("maxProviderInvocations", attempt.maxProviderInvocations())
                .addValue("descriptorActionCount", attempt.descriptorActions().size()));
    }

    private void insertDescriptorActions(SecFilingHistoryCollectionAttempt attempt) {
        for (DescriptorAction action : attempt.descriptorActions()) {
            jdbc.update(
                    """
                            INSERT INTO sec_filing_collection_attempt_descriptor_actions (
                                attempt_id, command_kind, root_capture_id,
                                descriptor_ordinal, action_kind,
                                selected_segment_capture_id, capture_now_slot
                            ) VALUES (
                                :attemptId, :commandKind, :rootCaptureId,
                                :descriptorOrdinal, :actionKind,
                                :selectedSegmentCaptureId, :captureNowSlot
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("attemptId", attempt.attemptId())
                            .addValue("commandKind", attempt.commandKind().name())
                            .addValue("rootCaptureId", attempt.rootCaptureId())
                            .addValue("descriptorOrdinal", action.descriptorOrdinal())
                            .addValue("actionKind", action.actionKind().name())
                            .addValue(
                                    "selectedSegmentCaptureId",
                                    action.selectedSegmentCaptureId())
                            .addValue(
                                    "captureNowSlot",
                                    action.actionKind() == DescriptorActionKind.CAPTURE_NOW
                                            ? 1
                                            : null));
        }
    }

    private int insertDispatch(SecFilingHistoryCollectionAttempt attempt) {
        ProviderDispatch dispatch = Objects.requireNonNull(attempt.providerDispatch());
        boolean segment = dispatch.operation()
                == ProviderOperation.CAPTURE_HISTORICAL_SEGMENT;
        String sql = """
                INSERT INTO sec_filing_collection_attempt_provider_dispatches (
                    attempt_id, command_kind, root_capture_id, descriptor_ordinal,
                    action_kind, provider_operation, attempt_requested_at, dispatched_at
                ) VALUES (
                    :attemptId, :commandKind, :rootCaptureId, :descriptorOrdinal,
                    :actionKind, :providerOperation, :requestedAt, :dispatchedAt
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT DO NOTHING";
        }
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("attemptId", attempt.attemptId())
                .addValue("commandKind", attempt.commandKind().name())
                .addValue("rootCaptureId", segment ? attempt.rootCaptureId() : null)
                .addValue("descriptorOrdinal", dispatch.descriptorOrdinal())
                .addValue("actionKind", segment ? DescriptorActionKind.CAPTURE_NOW.name() : null)
                .addValue("providerOperation", dispatch.operation().name())
                .addValue("requestedAt", utc(attempt.requestedAt()))
                .addValue("dispatchedAt", utc(dispatch.dispatchedAt())));
    }

    private SecFilingHistoryCollectionAttempt appendTerminal(
            SecFilingHistoryCollectionAttempt proposed) {
        int inserted;
        try {
            inserted = insertTerminal(proposed);
        } catch (DataIntegrityViolationException exception) {
            Optional<SecFilingHistoryCollectionAttempt> raced =
                    findByAttemptId(proposed.attemptId());
            if (raced.isPresent()
                    && proposed.terminalOutcome().equals(
                            raced.orElseThrow().terminalOutcome())) {
                return raced.orElseThrow();
            }
            throw exception;
        }
        if (inserted == 0) {
            SecFilingHistoryCollectionAttempt raced = requireAttempt(proposed.attemptId());
            if (proposed.terminalOutcome().equals(raced.terminalOutcome())) {
                return raced;
            }
            throw new IllegalArgumentException(
                    "terminal outcome insert conflicted with another durable fact");
        }
        return requireExactRoundTrip(proposed, "terminal outcome");
    }

    private int insertTerminal(SecFilingHistoryCollectionAttempt attempt) {
        TerminalOutcome outcome = Objects.requireNonNull(attempt.terminalOutcome());
        ProviderDispatch dispatch = attempt.providerDispatch();
        Integer segmentOrdinal = outcome.segmentArtifact().isProduced()
                ? attempt.captureNowAction().map(DescriptorAction::descriptorOrdinal)
                        .orElseThrow(() -> new IllegalStateException(
                                "produced segment artifact has no CAPTURE_NOW action"))
                : null;
        String sql = """
                INSERT INTO sec_filing_collection_attempt_outcomes (
                    attempt_id, command_kind, root_capture_id,
                    provider_dispatch_attempt_id, provider_dispatched_at,
                    terminal_status, terminal_stage, request_disposition,
                    failure_code, http_status,
                    root_capture_artifact_id, root_append_status,
                    segment_capture_artifact_id, segment_descriptor_ordinal,
                    segment_append_status, manifest_artifact_id,
                    manifest_append_status, attempt_requested_at, completed_at
                ) VALUES (
                    :attemptId, :commandKind, :rootCaptureId,
                    :providerDispatchAttemptId, :providerDispatchedAt,
                    :terminalStatus, :terminalStage, :requestDisposition,
                    :failureCode, :httpStatus,
                    :rootCaptureArtifactId, :rootAppendStatus,
                    :segmentCaptureArtifactId, :segmentDescriptorOrdinal,
                    :segmentAppendStatus, :manifestArtifactId,
                    :manifestAppendStatus, :requestedAt, :completedAt
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT DO NOTHING";
        }
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("attemptId", attempt.attemptId())
                .addValue("commandKind", attempt.commandKind().name())
                .addValue("rootCaptureId", attempt.rootCaptureId())
                .addValue(
                        "providerDispatchAttemptId",
                        dispatch == null ? null : attempt.attemptId())
                .addValue(
                        "providerDispatchedAt",
                        dispatch == null ? null : utc(dispatch.dispatchedAt()))
                .addValue("terminalStatus", outcome.status().name())
                .addValue("terminalStage", outcome.stage().name())
                .addValue("requestDisposition", outcome.requestDisposition().name())
                .addValue(
                        "failureCode",
                        outcome.failureCode() == null ? null : outcome.failureCode().name())
                .addValue("httpStatus", outcome.httpStatus())
                .addValue("rootCaptureArtifactId", outcome.rootArtifact().artifactId())
                .addValue("rootAppendStatus", outcome.rootArtifact().status().name())
                .addValue("segmentCaptureArtifactId", outcome.segmentArtifact().artifactId())
                .addValue("segmentDescriptorOrdinal", segmentOrdinal)
                .addValue("segmentAppendStatus", outcome.segmentArtifact().status().name())
                .addValue("manifestArtifactId", outcome.manifestArtifact().artifactId())
                .addValue("manifestAppendStatus", outcome.manifestArtifact().status().name())
                .addValue("requestedAt", utc(attempt.requestedAt()))
                .addValue("completedAt", utc(outcome.completedAt())));
    }

    private Optional<SecFilingHistoryCollectionAttempt> findByOperatorRequestId(
            String operatorRequestId) {
        return findOne(
                SELECT_HEADER + " WHERE operator_request_id = :operatorRequestId",
                new MapSqlParameterSource("operatorRequestId", operatorRequestId));
    }

    private Optional<SecFilingHistoryCollectionAttempt> findOne(
            String sql,
            MapSqlParameterSource parameters) {
        return findOne(sql, parameters, null);
    }

    private Optional<SecFilingHistoryCollectionAttempt> findOne(
            String sql,
            MapSqlParameterSource parameters,
            Instant evaluationAsOf) {
        List<HeaderRow> rows = jdbc.query(sql, parameters, this::mapHeader);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException("collection attempt query returned an ambiguous row");
        }
        HeaderRow row = rows.getFirst();
        verifyHeaderSummary(row);
        List<DescriptorAction> actions = readActions(row.attemptId());
        if (actions.size() != row.descriptorActionCount()) {
            throw new IllegalStateException(
                    "collection attempt action count does not match its header");
        }

        SecFilingHistoryCollectionAttempt attempt = new SecFilingHistoryCollectionAttempt(
                row.attemptId(),
                row.commandSha256(),
                row.operatorRequestId(),
                row.commandKind(),
                row.cik(),
                row.rootCaptureId(),
                actions,
                row.requestedAt(),
                null,
                null);
        // Read the append-later fact first. Under READ COMMITTED this avoids observing a newly
        // committed terminal after an earlier no-dispatch read. If no outcome is visible yet, a
        // later visible dispatch is still a valid earlier indeterminate view.
        Optional<OutcomeRow> outcomeRow = readOutcome(
                row.attemptId(), evaluationAsOf);
        Optional<DispatchRow> dispatchRow = readDispatch(
                row.attemptId(), evaluationAsOf);
        if (dispatchRow.isPresent()) {
            verifyDispatchSummary(row, dispatchRow.orElseThrow());
            attempt = attempt.withProviderDispatch(dispatchRow.orElseThrow().dispatch());
        }
        if (outcomeRow.isPresent()) {
            verifyOutcomeSummary(attempt, outcomeRow.orElseThrow());
            attempt = attempt.withTerminalOutcome(outcomeRow.orElseThrow().outcome());
            verifyTerminalArtifacts(attempt, outcomeRow.orElseThrow());
        }
        return Optional.of(attempt);
    }

    private List<DescriptorAction> readActions(String attemptId) {
        return jdbc.query(
                """
                        SELECT descriptor_ordinal, action_kind,
                               selected_segment_capture_id, capture_now_slot
                        FROM sec_filing_collection_attempt_descriptor_actions
                        WHERE attempt_id = :attemptId
                        ORDER BY descriptor_ordinal ASC
                        """,
                new MapSqlParameterSource("attemptId", attemptId),
                (result, rowNumber) -> {
                    DescriptorActionKind kind = DescriptorActionKind.valueOf(
                            result.getString("action_kind"));
                    Integer captureNowSlot = nullableInteger(result, "capture_now_slot");
                    if ((kind == DescriptorActionKind.CAPTURE_NOW && !Objects.equals(1, captureNowSlot))
                            || (kind == DescriptorActionKind.SELECT_EXACT
                                    && captureNowSlot != null)) {
                        throw new IllegalStateException(
                                "collection attempt action summary is inconsistent");
                    }
                    return new DescriptorAction(
                            result.getInt("descriptor_ordinal"),
                            kind,
                            result.getString("selected_segment_capture_id"));
                });
    }

    private Optional<DispatchRow> readDispatch(
            String attemptId,
            Instant evaluationAsOf) {
        String sql = """
                SELECT command_kind, root_capture_id, descriptor_ordinal,
                       action_kind, provider_operation,
                       attempt_requested_at, dispatched_at
                FROM sec_filing_collection_attempt_provider_dispatches
                WHERE attempt_id = :attemptId
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                "attemptId", attemptId);
        if (evaluationAsOf != null) {
            sql += " AND dispatched_at <= :evaluationAsOf";
            parameters.addValue("evaluationAsOf", utc(evaluationAsOf));
        }
        List<DispatchRow> rows = jdbc.query(
                sql,
                parameters,
                (result, rowNumber) -> new DispatchRow(
                        CommandKind.valueOf(result.getString("command_kind")),
                        result.getString("root_capture_id"),
                        nullableInteger(result, "descriptor_ordinal"),
                        result.getString("action_kind"),
                        new ProviderDispatch(
                                ProviderOperation.valueOf(
                                        result.getString("provider_operation")),
                                nullableInteger(result, "descriptor_ordinal"),
                                instant(result, "dispatched_at")),
                        instant(result, "attempt_requested_at")));
        return exactlyZeroOrOne(rows, "provider dispatch");
    }

    private Optional<OutcomeRow> readOutcome(
            String attemptId,
            Instant evaluationAsOf) {
        String sql = """
                SELECT command_kind, root_capture_id,
                       provider_dispatch_attempt_id, provider_dispatched_at,
                       terminal_status, terminal_stage, request_disposition,
                       failure_code, http_status,
                       root_capture_artifact_id, root_append_status,
                       segment_capture_artifact_id, segment_descriptor_ordinal,
                       segment_append_status, manifest_artifact_id,
                       manifest_append_status, attempt_requested_at, completed_at
                FROM sec_filing_collection_attempt_outcomes
                WHERE attempt_id = :attemptId
                """;
        MapSqlParameterSource parameters = new MapSqlParameterSource(
                "attemptId", attemptId);
        if (evaluationAsOf != null) {
            sql += " AND completed_at <= :evaluationAsOf";
            parameters.addValue("evaluationAsOf", utc(evaluationAsOf));
        }
        List<OutcomeRow> rows = jdbc.query(
                sql,
                parameters,
                (result, rowNumber) -> new OutcomeRow(
                        CommandKind.valueOf(result.getString("command_kind")),
                        result.getString("root_capture_id"),
                        result.getString("provider_dispatch_attempt_id"),
                        nullableInstant(result, "provider_dispatched_at"),
                        new TerminalOutcome(
                                TerminalStatus.valueOf(result.getString("terminal_status")),
                                TerminalStage.valueOf(result.getString("terminal_stage")),
                                RequestDisposition.valueOf(
                                        result.getString("request_disposition")),
                                nullableFailureCode(result),
                                nullableInteger(result, "http_status"),
                                artifact(result, "root_capture_artifact_id", "root_append_status"),
                                artifact(
                                        result,
                                        "segment_capture_artifact_id",
                                        "segment_append_status"),
                                artifact(
                                        result,
                                        "manifest_artifact_id",
                                        "manifest_append_status"),
                                instant(result, "completed_at")),
                        nullableInteger(result, "segment_descriptor_ordinal"),
                        instant(result, "attempt_requested_at")));
        return exactlyZeroOrOne(rows, "terminal outcome");
    }

    private void verifyHeaderSummary(HeaderRow row) {
        if (!SecFilingHistoryCollectionAttempt.SCHEMA_VERSION.equals(row.schemaVersion())
                || !SecFilingHistoryCollectionAttempt.PROVIDER.equals(row.provider())
                || !SecFilingHistoryCollectionAttempt.PRODUCT.equals(row.product())
                || !SecFilingHistoryCollectionAttempt.POLICY_VERSION.equals(row.policyVersion())
                || row.maxProviderInvocations()
                        != SecFilingHistoryCollectionAttempt.MAX_PROVIDER_INVOCATIONS
                || row.descriptorActionCount() < 0
                || !row.immutable()) {
            throw new IllegalStateException(
                    "collection attempt header summary is inconsistent");
        }
    }

    private static void verifyDispatchSummary(HeaderRow header, DispatchRow row) {
        ProviderDispatch dispatch = row.dispatch();
        boolean root = dispatch.operation() == ProviderOperation.CAPTURE_ROOT;
        String expectedAction = root ? null : DescriptorActionKind.CAPTURE_NOW.name();
        String expectedRoot = root ? null : header.rootCaptureId();
        if (row.commandKind() != header.commandKind()
                || !Objects.equals(row.rootCaptureId(), expectedRoot)
                || !Objects.equals(row.actionKind(), expectedAction)
                || !row.attemptRequestedAt().equals(header.requestedAt())) {
            throw new IllegalStateException(
                    "collection attempt dispatch summary is inconsistent");
        }
    }

    private static void verifyOutcomeSummary(
            SecFilingHistoryCollectionAttempt attempt,
            OutcomeRow row) {
        ProviderDispatch dispatch = attempt.providerDispatch();
        String expectedDispatchAttemptId = dispatch == null ? null : attempt.attemptId();
        Instant expectedDispatchedAt = dispatch == null ? null : dispatch.dispatchedAt();
        if (row.commandKind() != attempt.commandKind()
                || !Objects.equals(row.rootCaptureId(), attempt.rootCaptureId())
                || !Objects.equals(
                        row.providerDispatchAttemptId(), expectedDispatchAttemptId)
                || !Objects.equals(row.providerDispatchedAt(), expectedDispatchedAt)
                || !row.attemptRequestedAt().equals(attempt.requestedAt())) {
            throw new IllegalStateException(
                    "collection attempt terminal summary is inconsistent");
        }
        Integer expectedSegmentOrdinal = row.outcome().segmentArtifact().isProduced()
                ? attempt.captureNowAction().map(DescriptorAction::descriptorOrdinal)
                        .orElseThrow(() -> new IllegalStateException(
                                "terminal segment artifact has no CAPTURE_NOW action"))
                : null;
        if (!Objects.equals(row.segmentDescriptorOrdinal(), expectedSegmentOrdinal)) {
            throw new IllegalStateException(
                    "collection attempt terminal segment summary is inconsistent");
        }
    }

    private void verifyTerminalArtifacts(
            SecFilingHistoryCollectionAttempt attempt,
            OutcomeRow row) {
        TerminalOutcome outcome = attempt.terminalOutcome();
        if (outcome.rootArtifact().isProduced()) {
            FilingCatalogCapture root = rootRepository.findByCaptureId(
                    outcome.rootArtifact().artifactId()).orElseThrow(() ->
                            new IllegalStateException(
                                    "attempt root artifact could not be reconstructed"));
            if (!attempt.cik().equals(root.catalog().cik())) {
                throw new IllegalStateException(
                        "attempt root artifact does not match its planned CIK");
            }
        }
        if (outcome.segmentArtifact().isProduced()) {
            HistoricalFilingSegmentCapture segment = segmentRepository.findByCaptureId(
                    outcome.segmentArtifact().artifactId()).orElseThrow(() ->
                            new IllegalStateException(
                                    "attempt segment artifact could not be reconstructed"));
            if (!attempt.rootCaptureId().equals(segment.segment().rootCaptureId())
                    || !Objects.equals(
                            row.segmentDescriptorOrdinal(),
                            segment.segment().descriptorOrdinal())) {
                throw new IllegalStateException(
                        "attempt segment artifact does not match its planned source");
            }
        }
        if (outcome.manifestArtifact().isProduced()) {
            verifyManifestArtifact(attempt, outcome.manifestArtifact().artifactId());
        }
    }

    private void verifyManifestArtifact(
            SecFilingHistoryCollectionAttempt attempt,
            String manifestId) {
        List<String> roots = jdbc.query(
                """
                        SELECT root_capture_id
                        FROM sec_filing_history_collection_manifests
                        WHERE manifest_id = :manifestId
                        """,
                new MapSqlParameterSource("manifestId", manifestId),
                (result, rowNumber) -> result.getString("root_capture_id"));
        if (roots.size() != 1 || !attempt.rootCaptureId().equals(roots.getFirst())) {
            throw new IllegalStateException(
                    "attempt manifest artifact does not match its exact root");
        }

        Map<Integer, String> expected = new LinkedHashMap<>();
        for (DescriptorAction action : attempt.descriptorActions()) {
            String captureId = action.actionKind() == DescriptorActionKind.SELECT_EXACT
                    ? action.selectedSegmentCaptureId()
                    : attempt.terminalOutcome().segmentArtifact().artifactId();
            expected.put(action.descriptorOrdinal(), captureId);
        }
        List<ManifestSelection> stored = jdbc.query(
                """
                        SELECT descriptor_ordinal, selected_segment_capture_id
                        FROM sec_filing_history_collection_descriptors
                        WHERE manifest_id = :manifestId
                          AND selection_state = 'SELECTED_EXACT_CAPTURE'
                        ORDER BY descriptor_ordinal ASC
                        """,
                new MapSqlParameterSource("manifestId", manifestId),
                (result, rowNumber) -> new ManifestSelection(
                        result.getInt("descriptor_ordinal"),
                        result.getString("selected_segment_capture_id")));
        List<ManifestSelection> expectedSelections = expected.entrySet().stream()
                .map(entry -> new ManifestSelection(entry.getKey(), entry.getValue()))
                .toList();
        if (!stored.equals(expectedSelections)) {
            throw new IllegalStateException(
                    "attempt manifest artifact does not match its exact selections");
        }
    }

    private SecFilingHistoryCollectionAttempt requireAttempt(String attemptId) {
        return findByAttemptId(attemptId).orElseThrow(() ->
                new IllegalArgumentException("collection attempt was not found"));
    }

    private SecFilingHistoryCollectionAttempt requireOpenAttempt(String attemptId) {
        SecFilingHistoryCollectionAttempt attempt = requireAttempt(attemptId);
        if (attempt.terminalOutcome() != null) {
            throw new IllegalArgumentException("collection attempt is already terminal");
        }
        return attempt;
    }

    private SecFilingHistoryCollectionAttempt requireExactRoundTrip(
            SecFilingHistoryCollectionAttempt expected,
            String fact) {
        SecFilingHistoryCollectionAttempt persisted = requireAttempt(expected.attemptId());
        if (!persisted.equals(expected)) {
            throw new IllegalArgumentException(
                    "inserted collection attempt " + fact + " did not round-trip exactly");
        }
        return persisted;
    }

    private static List<DescriptorCaptureSelection> selections(
            SecFilingHistoryCollectionAttempt attempt,
            String capturedSegmentId) {
        return attempt.descriptorActions().stream()
                .map(action -> new DescriptorCaptureSelection(
                        action.descriptorOrdinal(),
                        action.actionKind() == DescriptorActionKind.SELECT_EXACT
                                ? action.selectedSegmentCaptureId()
                                : Objects.requireNonNull(
                                        capturedSegmentId,
                                        "capturedSegmentId must satisfy CAPTURE_NOW")))
                .toList();
    }

    private static ArtifactAppend artifact(
            String artifactId,
            FilingCatalogCaptureAppendResult status) {
        return status == FilingCatalogCaptureAppendResult.INSERTED
                ? ArtifactAppend.inserted(artifactId)
                : ArtifactAppend.identicalReplay(artifactId);
    }

    private static ArtifactAppend artifact(
            String artifactId,
            HistoricalFilingSegmentCaptureAppendResult status) {
        return status == HistoricalFilingSegmentCaptureAppendResult.INSERTED
                ? ArtifactAppend.inserted(artifactId)
                : ArtifactAppend.identicalReplay(artifactId);
    }

    private static ArtifactAppend artifact(
            String artifactId,
            FilingHistoryCollectionManifestAppendOutcome.Status status) {
        return status == FilingHistoryCollectionManifestAppendOutcome.Status.INSERTED
                ? ArtifactAppend.inserted(artifactId)
                : ArtifactAppend.identicalReplay(artifactId);
    }

    private static ArtifactAppend artifact(
            ResultSet result,
            String idColumn,
            String statusColumn) throws SQLException {
        String id = result.getString(idColumn);
        ArtifactAppendStatus status = ArtifactAppendStatus.valueOf(
                result.getString(statusColumn));
        return new ArtifactAppend(id, status);
    }

    private static FailureCode nullableFailureCode(ResultSet result) throws SQLException {
        String value = result.getString("failure_code");
        return value == null ? null : FailureCode.valueOf(value);
    }

    private HeaderRow mapHeader(ResultSet result, int rowNumber) throws SQLException {
        return new HeaderRow(
                result.getString("attempt_id"),
                result.getString("schema_version"),
                result.getString("provider"),
                result.getString("product"),
                result.getString("policy_version"),
                result.getString("command_sha256"),
                result.getString("operator_request_id"),
                CommandKind.valueOf(result.getString("command_kind")),
                result.getString("cik"),
                result.getString("root_capture_id"),
                instant(result, "requested_at"),
                result.getInt("max_provider_invocations"),
                result.getInt("descriptor_action_count"),
                result.getBoolean("immutable"));
    }

    private boolean isPostgreSql() {
        Boolean cached = postgreSql;
        if (cached != null) {
            return cached;
        }
        Boolean detected = jdbc.getJdbcOperations().execute(
                (ConnectionCallback<Boolean>) connection -> connection.getMetaData()
                        .getDatabaseProductName()
                        .toLowerCase(Locale.ROOT)
                        .contains("postgresql"));
        postgreSql = Boolean.TRUE.equals(detected);
        return postgreSql;
    }

    private static <T> Optional<T> exactlyZeroOrOne(List<T> rows, String fact) {
        if (rows.size() > 1) {
            throw new IllegalStateException(
                    "collection attempt contains multiple " + fact + " rows");
        }
        return rows.stream().findFirst();
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant notBefore(Instant requestedCompletedAt, Instant artifactTime) {
        PersistentInstant.requireMicrosecondPrecision(
                requestedCompletedAt, "completedAt");
        PersistentInstant.requireMicrosecondPrecision(artifactTime, "artifactTime");
        return requestedCompletedAt.isBefore(artifactTime)
                ? artifactTime
                : requestedCompletedAt;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Instant value = nullableInstant(result, column);
        if (value == null) {
            throw new SQLException("missing timestamp representation for " + column);
        }
        return value;
    }

    private static Instant nullableInstant(ResultSet result, String column)
            throws SQLException {
        Object value = result.getObject(column);
        if (value == null) {
            return null;
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new SQLException("unsupported timestamp representation for " + column);
    }

    private static Integer nullableInteger(ResultSet result, String column)
            throws SQLException {
        Object value = result.getObject(column);
        return value == null ? null : ((Number) value).intValue();
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
    }

    private record HeaderRow(
            String attemptId,
            String schemaVersion,
            String provider,
            String product,
            String policyVersion,
            String commandSha256,
            String operatorRequestId,
            CommandKind commandKind,
            String cik,
            String rootCaptureId,
            Instant requestedAt,
            int maxProviderInvocations,
            int descriptorActionCount,
            boolean immutable) {
    }

    private record DispatchRow(
            CommandKind commandKind,
            String rootCaptureId,
            Integer descriptorOrdinal,
            String actionKind,
            ProviderDispatch dispatch,
            Instant attemptRequestedAt) {
    }

    private record OutcomeRow(
            CommandKind commandKind,
            String rootCaptureId,
            String providerDispatchAttemptId,
            Instant providerDispatchedAt,
            TerminalOutcome outcome,
            Integer segmentDescriptorOrdinal,
            Instant attemptRequestedAt) {
    }

    private record ManifestSelection(int descriptorOrdinal, String segmentCaptureId) {
    }
}
