package com.wallstreetreceipts.api.infrastructure.persistence;

import java.math.BigDecimal;
import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.CallContextDataSet;
import com.wallstreetreceipts.api.application.port.out.CallContextRepository;
import com.wallstreetreceipts.api.domain.context.EventContext;
import com.wallstreetreceipts.api.domain.context.MacroObservation;
import com.wallstreetreceipts.api.domain.context.MacroSeries;
import com.wallstreetreceipts.api.domain.context.MacroSnapshot;
import com.wallstreetreceipts.api.domain.context.MacroUnit;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.source.SourceType;

@Repository
public class JdbcCallContextRepository implements CallContextRepository {

    private static final LocalDate OPEN_START = LocalDate.of(1, 1, 1);
    private static final LocalDate OPEN_END = LocalDate.of(9999, 12, 31);

    private static final String SELECT_OBSERVATION = """
            SELECT macro_observation_id, schema_version, series, observation_value, unit, observation_date,
                   released_at, processing_time, vintage_start, vintage_end, source_reference_id,
                   data_mode, captured_at, provenance_id
            FROM macro_observations
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public JdbcCallContextRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int importDataSet(CallContextDataSet dataSet) {
        for (SourceDocument document : dataSet.sourceDocuments()) {
            saveSourceDocumentIfAbsent(document);
        }
        for (SourceReference reference : dataSet.sourceReferences()) {
            saveSourceReferenceIfAbsent(reference);
        }

        int imported = 0;
        for (MacroObservation observation : dataSet.macroObservations()) {
            if (saveObservationIfAbsent(observation)) {
                imported++;
            }
        }
        for (MacroSnapshot snapshot : dataSet.macroSnapshots()) {
            if (saveMacroSnapshotIfAbsent(snapshot)) {
                imported++;
            }
        }
        for (EventContext context : dataSet.eventContexts()) {
            if (saveEventContextIfAbsent(context)) {
                imported++;
            }
        }
        return imported;
    }

    @Override
    public Optional<MacroSnapshot> findMacroSnapshotByCallId(String callId) {
        List<SnapshotRow> rows = jdbc.query(
                """
                        SELECT macro_snapshot_id, schema_version, call_id, event_time,
                               processing_time, data_mode, captured_at, provenance_id
                        FROM macro_snapshots WHERE call_id = :callId
                        """,
                new MapSqlParameterSource("callId", callId),
                (result, rowNumber) -> new SnapshotRow(
                        result.getString("macro_snapshot_id"), result.getString("schema_version"),
                        result.getString("call_id"), instant(result, "event_time"),
                        instant(result, "processing_time"), DataMode.valueOf(result.getString("data_mode")),
                        instant(result, "captured_at"), result.getString("provenance_id")));
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        SnapshotRow row = rows.getFirst();
        List<MacroObservation> observations = jdbc.query(
                SELECT_OBSERVATION + """
                        WHERE macro_observation_id IN (
                            SELECT macro_observation_id FROM macro_snapshot_observations
                            WHERE macro_snapshot_id = :macroSnapshotId
                        )
                        ORDER BY CASE series
                            WHEN 'FED_FUNDS_LOWER' THEN 0
                            WHEN 'FED_FUNDS_UPPER' THEN 1
                            WHEN 'CPI_YOY' THEN 2
                            WHEN 'CORE_CPI_YOY' THEN 3
                            WHEN 'PPI_YOY' THEN 4
                            WHEN 'UNEMPLOYMENT_RATE' THEN 5
                            ELSE 6
                        END
                        """,
                new MapSqlParameterSource("macroSnapshotId", row.id()),
                this::mapObservation);
        return Optional.of(new MacroSnapshot(
                row.schemaVersion(), row.id(), row.callId(), row.eventTime(), row.processingTime(),
                observations, row.dataMode(), row.capturedAt(), row.provenanceId()));
    }

    @Override
    public Optional<EventContext> findEventContextByCallId(String callId) {
        List<EventContext> values = jdbc.query(
                """
                        SELECT event_context_id, schema_version, call_id, event_time, processing_time,
                               earnings_at, next_cpi_at, next_fomc_at, next_nfp_at,
                               options_expiration_at, source_reference_id, data_mode, captured_at, provenance_id
                        FROM event_contexts WHERE call_id = :callId
                        """,
                new MapSqlParameterSource("callId", callId),
                this::mapEventContext);
        return values.stream().findFirst();
    }

    @Override
    public Optional<MacroObservation> findObservationById(String macroObservationId) {
        List<MacroObservation> values = jdbc.query(
                SELECT_OBSERVATION + " WHERE macro_observation_id = :id",
                new MapSqlParameterSource("id", macroObservationId),
                this::mapObservation);
        return values.stream().findFirst();
    }

    @Override
    public long observationCount() {
        return count("macro_observations");
    }

    @Override
    public long macroSnapshotCount() {
        return count("macro_snapshots");
    }

    @Override
    public long eventContextCount() {
        return count("event_contexts");
    }

    private boolean saveObservationIfAbsent(MacroObservation observation) {
        Optional<MacroObservation> existing = findObservationById(observation.macroObservationId());
        if (existing.isPresent()) {
            return replayOrConflict(existing.orElseThrow(), observation, "macroObservationId");
        }
        validateSourceEvidence(
                observation.sourceReferenceId(), observation.dataMode(), observation.processingTime(),
                observation.capturedAt(), observation.macroObservationId());
        int inserted = insert(
                """
                        INSERT INTO macro_observations (
                            macro_observation_id, schema_version, series, observation_value, unit, observation_date,
                            released_at, processing_time, vintage_start, vintage_end,
                            vintage_start_key, vintage_end_key, source_reference_id,
                            data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :schemaVersion, :series, :value, :unit, :observationDate,
                            :releasedAt, :processingTime, :vintageStart, :vintageEnd,
                            :vintageStartKey, :vintageEndKey, :sourceReferenceId,
                            :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                "macro_observation_id",
                new MapSqlParameterSource()
                        .addValue("id", observation.macroObservationId())
                        .addValue("schemaVersion", observation.schemaVersion())
                        .addValue("series", observation.series().name())
                        .addValue("value", observation.value())
                        .addValue("unit", observation.unit().name())
                        .addValue("observationDate", observation.observationDate())
                        .addValue("releasedAt", utc(observation.releasedAt()))
                        .addValue("processingTime", utc(observation.processingTime()))
                        .addValue("vintageStart", observation.vintageStart())
                        .addValue("vintageEnd", observation.vintageEnd())
                        .addValue("vintageStartKey", startKey(observation.vintageStart()))
                        .addValue("vintageEndKey", endKey(observation.vintageEnd()))
                        .addValue("sourceReferenceId", observation.sourceReferenceId())
                        .addValue("dataMode", observation.dataMode().name())
                        .addValue("capturedAt", utc(observation.capturedAt()))
                        .addValue("provenanceId", observation.provenanceId()));
        if (inserted == 1) {
            return true;
        }
        return replayOrConflict(
                findObservationById(observation.macroObservationId()).orElseThrow(
                        () -> new IllegalArgumentException("conflicting macro observation identity")),
                observation,
                "macroObservationId");
    }

    private boolean saveMacroSnapshotIfAbsent(MacroSnapshot snapshot) {
        Optional<MacroSnapshot> byCall = findMacroSnapshotByCallId(snapshot.callId());
        if (byCall.isPresent()) {
            return replayOrConflict(byCall.orElseThrow(), snapshot, "callId");
        }
        validateCallBinding(
                snapshot.callId(), snapshot.eventTime(), snapshot.processingTime(), snapshot.capturedAt(),
                snapshot.dataMode(), snapshot.macroSnapshotId());
        for (MacroObservation observation : snapshot.observations()) {
            MacroObservation archived = findObservationById(observation.macroObservationId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "unknown macro observation: " + observation.macroObservationId()));
            if (!archived.equals(observation)) {
                throw new IllegalArgumentException(
                        "macro snapshot observation differs from archived evidence: "
                                + observation.macroObservationId());
            }
        }
        int inserted = insert(
                """
                        INSERT INTO macro_snapshots (
                            macro_snapshot_id, schema_version, call_id, event_time, event_date,
                            processing_time, immutable, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :schemaVersion, :callId, :eventTime, :eventDate,
                            :processingTime, TRUE, :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                null,
                new MapSqlParameterSource()
                        .addValue("id", snapshot.macroSnapshotId())
                        .addValue("schemaVersion", snapshot.schemaVersion())
                        .addValue("callId", snapshot.callId())
                        .addValue("eventTime", utc(snapshot.eventTime()))
                        .addValue("eventDate", utcDate(snapshot.eventTime()))
                        .addValue("processingTime", utc(snapshot.processingTime()))
                        .addValue("dataMode", snapshot.dataMode().name())
                        .addValue("capturedAt", utc(snapshot.capturedAt()))
                        .addValue("provenanceId", snapshot.provenanceId()));
        if (inserted != 1) {
            Optional<MacroSnapshot> raced = findMacroSnapshotByCallId(snapshot.callId());
            if (raced.isPresent()) {
                return replayOrConflict(raced.orElseThrow(), snapshot, "callId");
            }
            throw new IllegalArgumentException("conflicting macroSnapshotId: " + snapshot.macroSnapshotId());
        }
        for (int ordinal = 0; ordinal < snapshot.observations().size(); ordinal++) {
            insertSnapshotObservation(snapshot, snapshot.observations().get(ordinal), ordinal);
        }
        return true;
    }

    private boolean saveEventContextIfAbsent(EventContext context) {
        Optional<EventContext> byCall = findEventContextByCallId(context.callId());
        if (byCall.isPresent()) {
            return replayOrConflict(byCall.orElseThrow(), context, "callId");
        }
        validateCallBinding(
                context.callId(), context.eventTime(), context.processingTime(), context.capturedAt(),
                context.dataMode(), context.eventContextId());
        validateSourceEvidence(
                context.sourceReferenceId(), context.dataMode(), context.eventTime(), context.capturedAt(),
                context.eventContextId());
        int inserted = insert(
                """
                        INSERT INTO event_contexts (
                            event_context_id, schema_version, call_id, event_time, processing_time,
                            earnings_at, next_cpi_at, next_fomc_at, next_nfp_at, options_expiration_at,
                            source_reference_id, immutable, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :schemaVersion, :callId, :eventTime, :processingTime,
                            :earningsAt, :nextCpiAt, :nextFomcAt, :nextNfpAt, :optionsExpirationAt,
                            :sourceReferenceId, TRUE, :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                null,
                new MapSqlParameterSource()
                        .addValue("id", context.eventContextId())
                        .addValue("schemaVersion", context.schemaVersion())
                        .addValue("callId", context.callId())
                        .addValue("eventTime", utc(context.eventTime()))
                        .addValue("processingTime", utc(context.processingTime()))
                        .addValue("earningsAt", utcNullable(context.earningsAt()))
                        .addValue("nextCpiAt", utcNullable(context.nextCpiAt()))
                        .addValue("nextFomcAt", utcNullable(context.nextFomcAt()))
                        .addValue("nextNfpAt", utcNullable(context.nextNfpAt()))
                        .addValue("optionsExpirationAt", utcNullable(context.optionsExpirationAt()))
                        .addValue("sourceReferenceId", context.sourceReferenceId())
                        .addValue("dataMode", context.dataMode().name())
                        .addValue("capturedAt", utc(context.capturedAt()))
                        .addValue("provenanceId", context.provenanceId()));
        if (inserted == 1) {
            return true;
        }
        Optional<EventContext> raced = findEventContextByCallId(context.callId());
        if (raced.isPresent()) {
            return replayOrConflict(raced.orElseThrow(), context, "callId");
        }
        throw new IllegalArgumentException("conflicting eventContextId: " + context.eventContextId());
    }

    private void insertSnapshotObservation(MacroSnapshot snapshot, MacroObservation observation, int ordinal) {
        jdbc.update(
                """
                        INSERT INTO macro_snapshot_observations (
                            macro_snapshot_id, ordinal, macro_observation_id, series,
                            snapshot_event_time, snapshot_event_date, snapshot_processing_time,
                            snapshot_captured_at, observation_released_at, observation_processing_time,
                            observation_captured_at, vintage_start_key, vintage_end_key, data_mode
                        ) VALUES (
                            :macroSnapshotId, :ordinal, :macroObservationId, :series,
                            :snapshotEventTime, :snapshotEventDate, :snapshotProcessingTime,
                            :snapshotCapturedAt, :observationReleasedAt, :observationProcessingTime,
                            :observationCapturedAt, :vintageStartKey, :vintageEndKey, :dataMode
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("macroSnapshotId", snapshot.macroSnapshotId())
                        .addValue("ordinal", ordinal)
                        .addValue("macroObservationId", observation.macroObservationId())
                        .addValue("series", observation.series().name())
                        .addValue("snapshotEventTime", utc(snapshot.eventTime()))
                        .addValue("snapshotEventDate", utcDate(snapshot.eventTime()))
                        .addValue("snapshotProcessingTime", utc(snapshot.processingTime()))
                        .addValue("snapshotCapturedAt", utc(snapshot.capturedAt()))
                        .addValue("observationReleasedAt", utc(observation.releasedAt()))
                        .addValue("observationProcessingTime", utc(observation.processingTime()))
                        .addValue("observationCapturedAt", utc(observation.capturedAt()))
                        .addValue("vintageStartKey", startKey(observation.vintageStart()))
                        .addValue("vintageEndKey", endKey(observation.vintageEnd()))
                        .addValue("dataMode", snapshot.dataMode().name()));
    }

    private void validateCallBinding(
            String callId,
            Instant eventTime,
            Instant processingTime,
            Instant capturedAt,
            DataMode dataMode,
            String contextId) {
        List<CallBinding> calls = jdbc.query(
                """
                        SELECT event_time, processing_time, captured_at, data_mode
                        FROM analyst_calls WHERE call_id = :callId
                        """,
                new MapSqlParameterSource("callId", callId),
                (result, rowNumber) -> new CallBinding(
                        instant(result, "event_time"), instant(result, "processing_time"),
                        instant(result, "captured_at"), DataMode.valueOf(result.getString("data_mode"))));
        if (calls.isEmpty()) {
            throw new IllegalArgumentException("unknown context analyst call: " + callId);
        }
        CallBinding call = calls.getFirst();
        if (!call.eventTime().equals(eventTime)) {
            throw new IllegalArgumentException("context eventTime must exactly match analyst call: " + contextId);
        }
        if (call.processingTime().isAfter(processingTime) || call.capturedAt().isAfter(capturedAt)) {
            throw new IllegalArgumentException("context cannot predate analyst call evidence: " + contextId);
        }
        if (call.dataMode() != dataMode) {
            throw new IllegalArgumentException("context and analyst call dataMode must match: " + contextId);
        }
    }

    private void validateSourceEvidence(
            String sourceReferenceId,
            DataMode dataMode,
            Instant availabilityCutoff,
            Instant capturedAt,
            String ownerId) {
        List<EvidenceBinding> values = jdbc.query(
                """
                        SELECT sr.data_mode, sr.captured_at AS reference_captured_at,
                               sd.data_mode AS document_data_mode, sd.captured_at AS document_captured_at
                        FROM source_references sr
                        JOIN source_documents sd ON sd.source_document_id = sr.source_document_id
                        WHERE sr.source_reference_id = :sourceReferenceId
                        """,
                new MapSqlParameterSource("sourceReferenceId", sourceReferenceId),
                (result, rowNumber) -> new EvidenceBinding(
                        DataMode.valueOf(result.getString("data_mode")),
                        instant(result, "reference_captured_at"),
                        DataMode.valueOf(result.getString("document_data_mode")),
                        instant(result, "document_captured_at")));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("unknown context source reference: " + sourceReferenceId);
        }
        EvidenceBinding evidence = values.getFirst();
        if (evidence.referenceMode() != dataMode || evidence.documentMode() != dataMode) {
            throw new IllegalArgumentException("context and source evidence dataMode must match: " + ownerId);
        }
        if (evidence.documentCapturedAt().isAfter(evidence.referenceCapturedAt())
                || evidence.referenceCapturedAt().isAfter(availabilityCutoff)
                || evidence.referenceCapturedAt().isAfter(capturedAt)) {
            throw new IllegalArgumentException("context source evidence was not available in time: " + ownerId);
        }
    }

    private void saveSourceDocumentIfAbsent(SourceDocument document) {
        Optional<SourceDocument> existing = findSourceDocument(document.id());
        if (existing.isPresent()) {
            replayOrConflict(existing.orElseThrow(), document, "sourceDocumentId");
            return;
        }
        int inserted = insert(
                """
                        INSERT INTO source_documents (
                            source_document_id, source_type, publisher, title, canonical_url, published_at,
                            provider, external_id, content_hash, license_class, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :type, :publisher, :title, :canonicalUrl, :publishedAt,
                            :provider, :externalId, :contentHash, :licenseClass, :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                "source_document_id",
                new MapSqlParameterSource()
                        .addValue("id", document.id())
                        .addValue("type", document.type().name())
                        .addValue("publisher", document.publisher())
                        .addValue("title", document.title())
                        .addValue("canonicalUrl", document.canonicalUrl() == null ? null : document.canonicalUrl().toString())
                        .addValue("publishedAt", utcNullable(document.publishedAt()))
                        .addValue("provider", document.provider())
                        .addValue("externalId", document.externalId())
                        .addValue("contentHash", document.contentHash())
                        .addValue("licenseClass", document.licenseClass())
                        .addValue("dataMode", document.dataMode().name())
                        .addValue("capturedAt", utc(document.capturedAt()))
                        .addValue("provenanceId", document.provenanceId()));
        if (inserted != 1) {
            replayOrConflict(
                    findSourceDocument(document.id()).orElseThrow(
                            () -> new IllegalArgumentException("conflicting source document provider identity")),
                    document,
                    "sourceDocumentId");
        }
    }

    private void saveSourceReferenceIfAbsent(SourceReference reference) {
        Optional<SourceReference> existing = findSourceReference(reference.id());
        if (existing.isPresent()) {
            if (!sameSourceReference(existing.orElseThrow(), reference)) {
                throw new IllegalArgumentException("conflicting sourceReferenceId: " + reference.id());
            }
            return;
        }
        SourceDocument document = findSourceDocument(reference.document().id())
                .orElseThrow(() -> new IllegalArgumentException(
                        "unknown context source document: " + reference.document().id()));
        if (!document.equals(reference.document())) {
            throw new IllegalArgumentException("source reference document differs from archived evidence");
        }
        if (document.dataMode() != reference.dataMode()
                || document.capturedAt().isAfter(reference.capturedAt())) {
            throw new IllegalArgumentException("source reference evidence boundary is invalid: " + reference.id());
        }
        int inserted = insert(
                """
                        INSERT INTO source_references (
                            source_reference_id, source_document_id, page_number, start_ms, end_ms,
                            extracted_fragment, extraction_confidence, verified, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :documentId, :page, :startMs, :endMs,
                            :fragment, :confidence, :verified, :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                "source_reference_id",
                new MapSqlParameterSource()
                        .addValue("id", reference.id())
                        .addValue("documentId", reference.document().id())
                        .addValue("page", reference.page())
                        .addValue("startMs", reference.startMs())
                        .addValue("endMs", reference.endMs())
                        .addValue("fragment", reference.extractedFragment())
                        .addValue("confidence", reference.extractionConfidence())
                        .addValue("verified", reference.verified())
                        .addValue("dataMode", reference.dataMode().name())
                        .addValue("capturedAt", utc(reference.capturedAt()))
                        .addValue("provenanceId", reference.provenanceId()));
        if (inserted != 1) {
            SourceReference raced = findSourceReference(reference.id()).orElseThrow(
                    () -> new IllegalArgumentException("conflicting sourceReferenceId: " + reference.id()));
            if (!sameSourceReference(raced, reference)) {
                throw new IllegalArgumentException("conflicting sourceReferenceId: " + reference.id());
            }
        }
    }

    private Optional<SourceDocument> findSourceDocument(String id) {
        List<SourceDocument> values = jdbc.query(
                """
                        SELECT source_document_id, source_type, publisher, title, canonical_url, published_at,
                               provider, external_id, content_hash, license_class, data_mode, captured_at, provenance_id
                        FROM source_documents WHERE source_document_id = :id
                        """,
                new MapSqlParameterSource("id", id),
                (result, rowNumber) -> new SourceDocument(
                        result.getString("source_document_id"),
                        SourceType.valueOf(result.getString("source_type")), result.getString("publisher"),
                        result.getString("title"), nullableUri(result.getString("canonical_url")),
                        nullableInstant(result, "published_at"), result.getString("provider"),
                        result.getString("external_id"), result.getString("content_hash"),
                        result.getString("license_class"), DataMode.valueOf(result.getString("data_mode")),
                        instant(result, "captured_at"), result.getString("provenance_id")));
        return values.stream().findFirst();
    }

    private Optional<SourceReference> findSourceReference(String id) {
        List<SourceReference> values = jdbc.query(
                """
                        SELECT sr.source_reference_id, sr.page_number, sr.start_ms, sr.end_ms,
                               sr.extracted_fragment, sr.extraction_confidence, sr.verified,
                               sr.data_mode, sr.captured_at, sr.provenance_id,
                               sd.source_document_id, sd.source_type, sd.publisher, sd.title,
                               sd.canonical_url, sd.published_at, sd.provider, sd.external_id,
                               sd.content_hash, sd.license_class, sd.data_mode AS document_data_mode,
                               sd.captured_at AS document_captured_at, sd.provenance_id AS document_provenance_id
                        FROM source_references sr
                        JOIN source_documents sd ON sd.source_document_id = sr.source_document_id
                        WHERE sr.source_reference_id = :id
                        """,
                new MapSqlParameterSource("id", id),
                (result, rowNumber) -> {
                    SourceDocument document = new SourceDocument(
                            result.getString("source_document_id"),
                            SourceType.valueOf(result.getString("source_type")), result.getString("publisher"),
                            result.getString("title"), nullableUri(result.getString("canonical_url")),
                            nullableInstant(result, "published_at"), result.getString("provider"),
                            result.getString("external_id"), result.getString("content_hash"),
                            result.getString("license_class"),
                            DataMode.valueOf(result.getString("document_data_mode")),
                            instant(result, "document_captured_at"), result.getString("document_provenance_id"));
                    return new SourceReference(
                            result.getString("source_reference_id"), document,
                            nullableInteger(result, "page_number"), nullableLong(result, "start_ms"),
                            nullableLong(result, "end_ms"), result.getString("extracted_fragment"),
                            result.getBigDecimal("extraction_confidence"), result.getBoolean("verified"),
                            DataMode.valueOf(result.getString("data_mode")), instant(result, "captured_at"),
                            result.getString("provenance_id"));
                });
        return values.stream().findFirst();
    }

    private int insert(String sql, String conflictColumn, MapSqlParameterSource parameters) {
        if (isPostgreSql()) {
            String conflict = conflictColumn == null
                    ? " ON CONFLICT DO NOTHING"
                    : " ON CONFLICT (" + conflictColumn + ") DO NOTHING";
            return jdbc.update(sql + conflict, parameters);
        }
        return jdbc.update(sql, parameters);
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

    private MacroObservation mapObservation(ResultSet result, int rowNumber) throws SQLException {
        return new MacroObservation(
                result.getString("schema_version"), result.getString("macro_observation_id"),
                MacroSeries.valueOf(result.getString("series")), result.getBigDecimal("observation_value"),
                MacroUnit.valueOf(result.getString("unit")), localDate(result, "observation_date"),
                instant(result, "released_at"), instant(result, "processing_time"),
                nullableLocalDate(result, "vintage_start"), nullableLocalDate(result, "vintage_end"),
                result.getString("source_reference_id"), DataMode.valueOf(result.getString("data_mode")),
                instant(result, "captured_at"), result.getString("provenance_id"));
    }

    private EventContext mapEventContext(ResultSet result, int rowNumber) throws SQLException {
        return new EventContext(
                result.getString("schema_version"), result.getString("event_context_id"),
                result.getString("call_id"), instant(result, "event_time"),
                instant(result, "processing_time"), nullableInstant(result, "earnings_at"),
                nullableInstant(result, "next_cpi_at"), nullableInstant(result, "next_fomc_at"),
                nullableInstant(result, "next_nfp_at"), nullableInstant(result, "options_expiration_at"),
                result.getString("source_reference_id"), DataMode.valueOf(result.getString("data_mode")),
                instant(result, "captured_at"), result.getString("provenance_id"));
    }

    private long count(String table) {
        Long value = jdbc.getJdbcOperations().queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private static <T> boolean replayOrConflict(T existing, T incoming, String identity) {
        if (existing.equals(incoming)) {
            return false;
        }
        throw new IllegalArgumentException("conflicting " + identity + ": " + identityValue(incoming));
    }

    private static Object identityValue(Object value) {
        if (value instanceof MacroObservation observation) {
            return observation.macroObservationId();
        }
        if (value instanceof MacroSnapshot snapshot) {
            return snapshot.macroSnapshotId();
        }
        if (value instanceof EventContext context) {
            return context.eventContextId();
        }
        if (value instanceof SourceDocument document) {
            return document.id();
        }
        return value;
    }

    private static boolean sameSourceReference(SourceReference left, SourceReference right) {
        return left.id().equals(right.id())
                && left.document().equals(right.document())
                && Objects.equals(left.page(), right.page())
                && Objects.equals(left.startMs(), right.startMs())
                && Objects.equals(left.endMs(), right.endMs())
                && Objects.equals(left.extractedFragment(), right.extractedFragment())
                && decimalsEqual(left.extractionConfidence(), right.extractionConfidence())
                && left.verified() == right.verified()
                && left.dataMode() == right.dataMode()
                && left.capturedAt().equals(right.capturedAt())
                && left.provenanceId().equals(right.provenanceId());
    }

    private static boolean decimalsEqual(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == right;
        }
        return left.compareTo(right) == 0;
    }

    private static Instant instant(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
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

    private static Instant nullableInstant(ResultSet result, String column) throws SQLException {
        return result.getObject(column) == null ? null : instant(result, column);
    }

    private static LocalDate localDate(ResultSet result, String column) throws SQLException {
        Object value = result.getObject(column);
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new SQLException("Unsupported date value for " + column + ": " + value);
    }

    private static LocalDate nullableLocalDate(ResultSet result, String column) throws SQLException {
        return result.getObject(column) == null ? null : localDate(result, column);
    }

    private static Integer nullableInteger(ResultSet result, String column) throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Long nullableLong(ResultSet result, String column) throws SQLException {
        long value = result.getLong(column);
        return result.wasNull() ? null : value;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static OffsetDateTime utcNullable(Instant instant) {
        return instant == null ? null : utc(instant);
    }

    private static LocalDate utcDate(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC).toLocalDate();
    }

    private static LocalDate startKey(LocalDate value) {
        return value == null ? OPEN_START : value;
    }

    private static LocalDate endKey(LocalDate value) {
        return value == null ? OPEN_END : value;
    }

    private static URI nullableUri(String value) {
        return value == null ? null : URI.create(value);
    }

    private record SnapshotRow(
            String id,
            String schemaVersion,
            String callId,
            Instant eventTime,
            Instant processingTime,
            DataMode dataMode,
            Instant capturedAt,
            String provenanceId) {
    }

    private record CallBinding(
            Instant eventTime,
            Instant processingTime,
            Instant capturedAt,
            DataMode dataMode) {
    }

    private record EvidenceBinding(
            DataMode referenceMode,
            Instant referenceCapturedAt,
            DataMode documentMode,
            Instant documentCapturedAt) {
    }
}
