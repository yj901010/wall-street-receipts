package com.wallstreetreceipts.api.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.CallOutcomeRepository;
import com.wallstreetreceipts.api.application.port.out.ScoringMethodologyRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevisionType;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.outcome.CallOutcome;
import com.wallstreetreceipts.api.domain.outcome.OutcomeEvaluationStatus;
import com.wallstreetreceipts.api.domain.outcome.OutcomeHorizon;
import com.wallstreetreceipts.api.domain.outcome.OutcomeReasonCode;
import com.wallstreetreceipts.api.domain.outcome.ScoringMethodology;

@Repository
public class JdbcCallOutcomeRepository implements CallOutcomeRepository {

    static final Comparator<CallOutcome> IMPORT_ORDER = Comparator.comparing(CallOutcome::methodologyId)
            .thenComparing(CallOutcome::methodologyVersion)
            .thenComparing(CallOutcome::callId)
            .thenComparing(outcome -> basisKey(outcome.callId(), outcome.basisRevisionId()))
            .thenComparingInt(outcome -> outcome.horizon().ordinal())
            .thenComparingInt(CallOutcome::sequenceNumber);

    private static final String SELECT_OUTCOME = """
            SELECT outcome_id, schema_version, call_id, horizon, basis_revision_id, snapshot_id,
                   cancellation_revision_id,
                   methodology_id, methodology_version, methodology_definition_hash, input_fingerprint,
                   sequence_number, supersedes_outcome_id, evaluation_status, reason_code,
                   event_time, processing_time, asset_return, benchmark_return, sector_return,
                   alpha, sector_alpha, mfe, mae, target_hit, directional_win, target_error,
                   data_complete, data_mode, captured_at, provenance_id
            FROM call_outcomes
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final ScoringMethodologyRepository methodologyRepository;
    private volatile Boolean postgreSql;

    public JdbcCallOutcomeRepository(
            NamedParameterJdbcTemplate jdbc,
            ScoringMethodologyRepository methodologyRepository) {
        this.jdbc = jdbc;
        this.methodologyRepository = methodologyRepository;
    }

    @Override
    @Transactional
    public int importAll(List<CallOutcome> outcomes) {
        int imported = 0;
        List<CallOutcome> ordered = outcomes.stream()
                .sorted(IMPORT_ORDER)
                .toList();
        for (CallOutcome outcome : ordered) {
            if (saveIfAbsentInternal(outcome)) {
                imported++;
            }
        }
        return imported;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(CallOutcome outcome) {
        return saveIfAbsentInternal(outcome);
    }

    private boolean saveIfAbsentInternal(CallOutcome outcome) {
        ScoringMethodology methodology = lockMethodology(outcome);
        References references = validateReferences(outcome, methodology);
        Optional<CallOutcome> naturalExisting = findByNaturalIdentity(outcome, references.basis().key());
        if (naturalExisting.isPresent()) {
            return replayOrConflict(naturalExisting.orElseThrow(), outcome, "natural identity");
        }
        Optional<CallOutcome> idExisting = findByOutcomeId(outcome.outcomeId());
        if (idExisting.isPresent()) {
            return replayOrConflict(idExisting.orElseThrow(), outcome, "outcomeId");
        }

        LatestOutcome latest = latestOutcome(outcome, references.basis().key());
        validateLineage(outcome, latest);

        int inserted = insert(outcome, references);
        if (inserted == 1) {
            return true;
        }
        Optional<CallOutcome> raced = findByNaturalIdentity(outcome, references.basis().key());
        if (raced.isPresent()) {
            return replayOrConflict(raced.orElseThrow(), outcome, "natural identity");
        }
        Optional<CallOutcome> racedId = findByOutcomeId(outcome.outcomeId());
        if (racedId.isPresent()) {
            return replayOrConflict(racedId.orElseThrow(), outcome, "outcomeId");
        }
        throw new IllegalArgumentException(
                "conflicting call outcome lineage after concurrent insert: " + outcome.outcomeId());
    }

    @Override
    public List<CallOutcome> findByCallId(String callId) {
        return jdbc.query(
                SELECT_OUTCOME + """
                        WHERE call_id = :callId
                        ORDER BY CASE horizon
                            WHEN 'D1' THEN 1
                            WHEN 'W1' THEN 2
                            WHEN 'M1' THEN 3
                            WHEN 'M3' THEN 4
                            WHEN 'M6' THEN 5
                            WHEN 'Y1' THEN 6
                            ELSE 7
                        END ASC,
                        methodology_id ASC,
                        methodology_version ASC,
                        sequence_number ASC,
                        outcome_id ASC
                        """,
                new MapSqlParameterSource("callId", callId),
                this::mapOutcome);
    }

    @Override
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject("SELECT COUNT(*) FROM call_outcomes", Long.class);
        return count == null ? 0 : count;
    }

    private References validateReferences(CallOutcome outcome, ScoringMethodology methodology) {
        TemporalReference original = oneTemporalReference(
                "SELECT event_time, processing_time, captured_at FROM analyst_calls WHERE call_id = :callId",
                new MapSqlParameterSource("callId", outcome.callId()),
                "unknown original analyst call: " + outcome.callId());
        if (outcome.eventTime().isBefore(original.eventTime())) {
            throw new IllegalArgumentException("outcome eventTime must not precede the original call eventTime");
        }
        if (original.processingTime().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("original call must be processed by outcome processingTime");
        }
        if (original.capturedAt().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("original call must be captured by outcome processingTime");
        }

        if (!methodology.definitionHash().equals(outcome.methodologyDefinitionHash())) {
            throw new IllegalArgumentException("outcome methodology definition hash does not match");
        }
        if (methodology.effectiveAt().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome methodology is not effective at processingTime");
        }
        if (methodology.capturedAt().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome methodology must be captured by processingTime");
        }
        if (methodology.dataMode() != outcome.dataMode()) {
            throw new IllegalArgumentException("outcome and methodology dataMode must match");
        }

        if (outcome.snapshotId() != null) {
            List<TemporalReference> matchingSnapshots = jdbc.query(
                    """
                            SELECT event_time, processing_time, captured_at FROM market_snapshots
                            WHERE call_id = :callId AND snapshot_id = :snapshotId
                            """,
                    new MapSqlParameterSource()
                            .addValue("callId", outcome.callId())
                            .addValue("snapshotId", outcome.snapshotId()),
                    (result, rowNumber) -> new TemporalReference(
                            instant(result, "event_time"), instant(result, "processing_time"),
                            instant(result, "captured_at")));
            if (matchingSnapshots.isEmpty()) {
                throw new IllegalArgumentException("outcome snapshot must belong to the same call");
            }
            if (matchingSnapshots.getFirst().processingTime().isAfter(outcome.processingTime())) {
                throw new IllegalArgumentException("outcome snapshot must be processed by processingTime");
            }
            if (matchingSnapshots.getFirst().capturedAt().isAfter(outcome.processingTime())) {
                throw new IllegalArgumentException("outcome snapshot must be captured by processingTime");
            }
        }

        Basis basis = validateBasisRevision(outcome);
        CancellationEvidence cancellation = validateCancellationRevision(outcome);
        return new References(basis, cancellation);
    }

    private ScoringMethodology lockMethodology(CallOutcome outcome) {
        List<String> locked = jdbc.query(
                """
                        SELECT methodology_id
                        FROM scoring_methodologies
                        WHERE methodology_id = :methodologyId
                          AND methodology_version = :methodologyVersion
                        FOR UPDATE
                        """,
                new MapSqlParameterSource()
                        .addValue("methodologyId", outcome.methodologyId())
                        .addValue("methodologyVersion", outcome.methodologyVersion()),
                (result, rowNumber) -> result.getString("methodology_id"));
        if (locked.isEmpty()) {
            throw new IllegalArgumentException(
                    "unknown scoring methodology: "
                            + outcome.methodologyId() + ":" + outcome.methodologyVersion());
        }
        return methodologyRepository.findByIdAndVersion(
                        outcome.methodologyId(), outcome.methodologyVersion())
                .orElseThrow(() -> new IllegalStateException("locked scoring methodology disappeared"));
    }

    private Basis validateBasisRevision(CallOutcome outcome) {
        if (outcome.basisRevisionId() == null) {
            return new Basis(basisKey(outcome.callId(), null), null, null);
        }
        RevisionReference revision = revisionReference(
                outcome.callId(), outcome.basisRevisionId(), "outcome basis revision");
        if (revision.type() != AnalystCallRevisionType.CORRECTION) {
            throw new IllegalArgumentException("a cancellation cannot be used as an outcome basis");
        }
        if (revision.eventTime().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome basis revision must exist by processingTime");
        }
        if (revision.processingTime().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome basis revision must be processed by processingTime");
        }
        if (revision.capturedAt().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome basis revision must be captured by processingTime");
        }
        return new Basis(
                basisKey(outcome.callId(), outcome.basisRevisionId()), revision.sequenceNumber(), revision.type());
    }

    private CancellationEvidence validateCancellationRevision(CallOutcome outcome) {
        if (outcome.cancellationRevisionId() == null) {
            return new CancellationEvidence(null, null);
        }
        RevisionReference revision = revisionReference(
                outcome.callId(), outcome.cancellationRevisionId(), "outcome cancellation revision");
        if (revision.type() != AnalystCallRevisionType.CANCELLATION) {
            throw new IllegalArgumentException("outcome cancellation evidence must be a cancellation");
        }
        if (revision.processingTime().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome cancellation revision must be processed by processingTime");
        }
        if (revision.capturedAt().isAfter(outcome.processingTime())) {
            throw new IllegalArgumentException("outcome cancellation revision must be captured by processingTime");
        }
        return new CancellationEvidence(revision.sequenceNumber(), revision.type());
    }

    private RevisionReference revisionReference(String callId, String revisionId, String description) {
        List<RevisionReference> revisions = jdbc.query(
                """
                        SELECT sequence_number, revision_type, event_time, processing_time, captured_at
                        FROM analyst_call_revisions
                        WHERE call_id = :callId AND revision_id = :revisionId
                        """,
                new MapSqlParameterSource()
                        .addValue("callId", callId)
                        .addValue("revisionId", revisionId),
                (result, rowNumber) -> new RevisionReference(
                        result.getInt("sequence_number"),
                        AnalystCallRevisionType.valueOf(result.getString("revision_type")),
                        instant(result, "event_time"), instant(result, "processing_time"),
                        instant(result, "captured_at")));
        if (revisions.isEmpty()) {
            throw new IllegalArgumentException(description + " must belong to the same call");
        }
        return revisions.getFirst();
    }

    private Optional<CallOutcome> findByNaturalIdentity(CallOutcome outcome, String basisKey) {
        List<CallOutcome> values = jdbc.query(
                SELECT_OUTCOME + """
                        WHERE call_id = :callId
                          AND basis_key = :basisKey
                          AND horizon = :horizon
                          AND methodology_id = :methodologyId
                          AND methodology_version = :methodologyVersion
                          AND methodology_definition_hash = :methodologyDefinitionHash
                          AND input_fingerprint = :inputFingerprint
                        """,
                identityParameters(outcome, basisKey),
                this::mapOutcome);
        return values.stream().findFirst();
    }

    private Optional<CallOutcome> findByOutcomeId(String outcomeId) {
        List<CallOutcome> values = jdbc.query(
                SELECT_OUTCOME + " WHERE outcome_id = :outcomeId",
                new MapSqlParameterSource("outcomeId", outcomeId),
                this::mapOutcome);
        return values.stream().findFirst();
    }

    private LatestOutcome latestOutcome(CallOutcome outcome, String basisKey) {
        List<LatestOutcome> values = jdbc.query(
                """
                        SELECT outcome_id, sequence_number, event_time, processing_time, captured_at
                        FROM call_outcomes
                        WHERE call_id = :callId
                          AND basis_key = :basisKey
                          AND horizon = :horizon
                          AND methodology_id = :methodologyId
                          AND methodology_version = :methodologyVersion
                          AND methodology_definition_hash = :methodologyDefinitionHash
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                identityParameters(outcome, basisKey),
                (result, rowNumber) -> new LatestOutcome(
                        result.getString("outcome_id"), result.getInt("sequence_number"),
                        instant(result, "event_time"), instant(result, "processing_time"),
                        instant(result, "captured_at")));
        return values.isEmpty() ? null : values.getFirst();
    }

    private static void validateLineage(CallOutcome outcome, LatestOutcome latest) {
        if (latest == null) {
            if (outcome.sequenceNumber() != 1 || outcome.supersedesOutcomeId() != null) {
                throw new IllegalArgumentException("the first outcome in a lineage must start at sequence 1");
            }
            return;
        }
        if (outcome.sequenceNumber() != latest.sequenceNumber() + 1
                || !latest.outcomeId().equals(outcome.supersedesOutcomeId())) {
            throw new IllegalArgumentException("outcome must supersede the latest result in the same lineage");
        }
        if (outcome.eventTime().isBefore(latest.eventTime())
                || outcome.processingTime().isBefore(latest.processingTime())
                || outcome.capturedAt().isBefore(latest.capturedAt())) {
            throw new IllegalArgumentException("outcome lineage timestamps must not move backwards");
        }
    }

    private int insert(CallOutcome outcome, References references) {
        String sql = """
                        INSERT INTO call_outcomes (
                            outcome_id, schema_version, call_id, horizon,
                            basis_revision_id, basis_key, basis_revision_sequence_number, basis_revision_type,
                            cancellation_revision_id, cancellation_revision_sequence_number,
                            cancellation_revision_type,
                            snapshot_id, methodology_id, methodology_version, methodology_definition_hash,
                            input_fingerprint, sequence_number, supersedes_outcome_id,
                            supersedes_sequence_number, evaluation_status, reason_code,
                            event_time, processing_time, asset_return, benchmark_return, sector_return,
                            alpha, sector_alpha, mfe, mae, target_hit, directional_win, target_error,
                            data_complete, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :outcomeId, :schemaVersion, :callId, :horizon,
                            :basisRevisionId, :basisKey, :basisRevisionSequenceNumber, :basisRevisionType,
                            :cancellationRevisionId, :cancellationRevisionSequenceNumber,
                            :cancellationRevisionType,
                            :snapshotId, :methodologyId, :methodologyVersion, :methodologyDefinitionHash,
                            :inputFingerprint, :sequenceNumber, :supersedesOutcomeId,
                            :supersedesSequenceNumber, :evaluationStatus, :reasonCode,
                            :eventTime, :processingTime, :assetReturn, :benchmarkReturn, :sectorReturn,
                            :alpha, :sectorAlpha, :mfe, :mae, :targetHit, :directionalWin, :targetError,
                            :dataComplete, :dataMode, :capturedAt, :provenanceId
                        )
                        """;
        if (isPostgreSql()) {
            sql += """
                    ON CONFLICT DO NOTHING
                    """;
        }
        return jdbc.update(sql, parameters(outcome, references));
    }

    private static MapSqlParameterSource parameters(CallOutcome outcome, References references) {
        return identityParameters(outcome, references.basis().key())
                .addValue("outcomeId", outcome.outcomeId())
                .addValue("schemaVersion", outcome.schemaVersion())
                .addValue("basisRevisionId", outcome.basisRevisionId())
                .addValue("basisRevisionSequenceNumber", references.basis().revisionSequenceNumber())
                .addValue("basisRevisionType", references.basis().revisionType() == null
                        ? null : references.basis().revisionType().name())
                .addValue("cancellationRevisionId", outcome.cancellationRevisionId())
                .addValue("cancellationRevisionSequenceNumber",
                        references.cancellation().revisionSequenceNumber())
                .addValue("cancellationRevisionType", references.cancellation().revisionType() == null
                        ? null : references.cancellation().revisionType().name())
                .addValue("snapshotId", outcome.snapshotId())
                .addValue("sequenceNumber", outcome.sequenceNumber())
                .addValue("supersedesOutcomeId", outcome.supersedesOutcomeId())
                .addValue("supersedesSequenceNumber",
                        outcome.supersedesOutcomeId() == null ? null : outcome.sequenceNumber() - 1)
                .addValue("evaluationStatus", outcome.evaluationStatus().name())
                .addValue("reasonCode", outcome.reasonCode() == null ? null : outcome.reasonCode().name())
                .addValue("eventTime", utc(outcome.eventTime()))
                .addValue("processingTime", utc(outcome.processingTime()))
                .addValue("assetReturn", outcome.assetReturn())
                .addValue("benchmarkReturn", outcome.benchmarkReturn())
                .addValue("sectorReturn", outcome.sectorReturn())
                .addValue("alpha", outcome.alpha())
                .addValue("sectorAlpha", outcome.sectorAlpha())
                .addValue("mfe", outcome.mfe())
                .addValue("mae", outcome.mae())
                .addValue("targetHit", outcome.targetHit())
                .addValue("directionalWin", outcome.directionalWin())
                .addValue("targetError", outcome.targetError())
                .addValue("dataComplete", outcome.dataComplete())
                .addValue("dataMode", outcome.dataMode().name())
                .addValue("capturedAt", utc(outcome.capturedAt()))
                .addValue("provenanceId", outcome.provenanceId());
    }

    private static MapSqlParameterSource identityParameters(CallOutcome outcome, String basisKey) {
        return new MapSqlParameterSource()
                .addValue("callId", outcome.callId())
                .addValue("basisKey", basisKey)
                .addValue("horizon", outcome.horizon().name())
                .addValue("methodologyId", outcome.methodologyId())
                .addValue("methodologyVersion", outcome.methodologyVersion())
                .addValue("methodologyDefinitionHash", outcome.methodologyDefinitionHash())
                .addValue("inputFingerprint", outcome.inputFingerprint());
    }

    private static boolean replayOrConflict(CallOutcome existing, CallOutcome candidate, String identity) {
        if (semanticallyEqual(existing, candidate)) {
            return false;
        }
        throw new IllegalArgumentException("conflicting call outcome " + identity + ": " + candidate.outcomeId());
    }

    private static boolean semanticallyEqual(CallOutcome left, CallOutcome right) {
        return Objects.equals(left.outcomeId(), right.outcomeId())
                && Objects.equals(left.schemaVersion(), right.schemaVersion())
                && Objects.equals(left.callId(), right.callId())
                && left.horizon() == right.horizon()
                && Objects.equals(left.basisRevisionId(), right.basisRevisionId())
                && Objects.equals(left.cancellationRevisionId(), right.cancellationRevisionId())
                && Objects.equals(left.snapshotId(), right.snapshotId())
                && Objects.equals(left.methodologyId(), right.methodologyId())
                && Objects.equals(left.methodologyVersion(), right.methodologyVersion())
                && Objects.equals(left.methodologyDefinitionHash(), right.methodologyDefinitionHash())
                && Objects.equals(left.inputFingerprint(), right.inputFingerprint())
                && left.sequenceNumber() == right.sequenceNumber()
                && Objects.equals(left.supersedesOutcomeId(), right.supersedesOutcomeId())
                && left.evaluationStatus() == right.evaluationStatus()
                && left.reasonCode() == right.reasonCode()
                && Objects.equals(left.eventTime(), right.eventTime())
                && Objects.equals(left.processingTime(), right.processingTime())
                && decimalEquals(left.assetReturn(), right.assetReturn())
                && decimalEquals(left.benchmarkReturn(), right.benchmarkReturn())
                && decimalEquals(left.sectorReturn(), right.sectorReturn())
                && decimalEquals(left.alpha(), right.alpha())
                && decimalEquals(left.sectorAlpha(), right.sectorAlpha())
                && decimalEquals(left.mfe(), right.mfe())
                && decimalEquals(left.mae(), right.mae())
                && Objects.equals(left.targetHit(), right.targetHit())
                && Objects.equals(left.directionalWin(), right.directionalWin())
                && decimalEquals(left.targetError(), right.targetError())
                && left.dataComplete() == right.dataComplete()
                && left.dataMode() == right.dataMode()
                && Objects.equals(left.capturedAt(), right.capturedAt())
                && Objects.equals(left.provenanceId(), right.provenanceId());
    }

    private static boolean decimalEquals(BigDecimal left, BigDecimal right) {
        return left == null ? right == null : right != null && left.compareTo(right) == 0;
    }

    private CallOutcome mapOutcome(ResultSet result, int rowNumber) throws SQLException {
        String reason = result.getString("reason_code");
        return new CallOutcome(
                result.getString("outcome_id"), result.getString("schema_version"),
                result.getString("call_id"), OutcomeHorizon.valueOf(result.getString("horizon")),
                result.getString("basis_revision_id"), result.getString("cancellation_revision_id"),
                result.getString("snapshot_id"),
                result.getString("methodology_id"), result.getString("methodology_version"),
                result.getString("methodology_definition_hash"), result.getString("input_fingerprint"),
                result.getInt("sequence_number"), result.getString("supersedes_outcome_id"),
                OutcomeEvaluationStatus.valueOf(result.getString("evaluation_status")),
                reason == null ? null : OutcomeReasonCode.valueOf(reason),
                instant(result, "event_time"), instant(result, "processing_time"),
                result.getBigDecimal("asset_return"), result.getBigDecimal("benchmark_return"),
                result.getBigDecimal("sector_return"), result.getBigDecimal("alpha"),
                result.getBigDecimal("sector_alpha"), result.getBigDecimal("mfe"), result.getBigDecimal("mae"),
                nullableBoolean(result, "target_hit"), nullableBoolean(result, "directional_win"),
                result.getBigDecimal("target_error"), result.getBoolean("data_complete"),
                DataMode.valueOf(result.getString("data_mode")), instant(result, "captured_at"),
                result.getString("provenance_id"));
    }

    private TemporalReference oneTemporalReference(
            String sql,
            MapSqlParameterSource parameters,
            String missingMessage) {
        List<TemporalReference> values = jdbc.query(
                sql,
                parameters,
                (result, rowNumber) -> new TemporalReference(
                        instant(result, "event_time"), instant(result, "processing_time"),
                        instant(result, "captured_at")));
        if (values.isEmpty()) {
            throw new IllegalArgumentException(missingMessage);
        }
        return values.getFirst();
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        return toInstant(value, column);
    }

    private static Instant toInstant(Object value, String column) throws SQLException {
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new SQLException("Unsupported timestamp value for " + column + ": " + value);
    }

    private static Boolean nullableBoolean(ResultSet result, String column) throws SQLException {
        boolean value = result.getBoolean(column);
        return result.wasNull() ? null : value;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private boolean isPostgreSql() {
        Boolean current = postgreSql;
        if (current != null) {
            return current;
        }
        Boolean detected = jdbc.getJdbcOperations().execute((ConnectionCallback<Boolean>) connection ->
                "PostgreSQL".equals(connection.getMetaData().getDatabaseProductName()));
        postgreSql = Boolean.TRUE.equals(detected);
        return postgreSql;
    }

    private static String basisKey(String callId, String revisionId) {
        return revisionId == null ? "ORIGINAL:" + callId : "REVISION:" + revisionId;
    }

    private record Basis(
            String key,
            Integer revisionSequenceNumber,
            AnalystCallRevisionType revisionType) {
    }

    private record RevisionReference(
            int sequenceNumber,
            AnalystCallRevisionType type,
            Instant eventTime,
            Instant processingTime,
            Instant capturedAt) {
    }

    private record CancellationEvidence(
            Integer revisionSequenceNumber,
            AnalystCallRevisionType revisionType) {
    }

    private record References(Basis basis, CancellationEvidence cancellation) {
    }

    private record LatestOutcome(
            String outcomeId,
            int sequenceNumber,
            Instant eventTime,
            Instant processingTime,
            Instant capturedAt) {
    }

    private record TemporalReference(Instant eventTime, Instant processingTime, Instant capturedAt) {
    }
}
