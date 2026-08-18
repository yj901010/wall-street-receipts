package com.wallstreetreceipts.api.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.Currency;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.AnalystCallRevisionRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevision;
import com.wallstreetreceipts.api.domain.call.AnalystCallRevisionType;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.call.CorrectedCallTerms;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.source.SourceType;

@Repository
public class JdbcAnalystCallRevisionRepository implements AnalystCallRevisionRepository {

    private static final String PROVIDER_EVENT_KIND = "ANALYST_CALL_REVISION";

    private static final String SELECT_REVISIONS = """
            SELECT
                r.revision_id AS r_id,
                r.schema_version AS r_schema_version,
                r.call_id AS r_call_id,
                r.supersedes_revision_id AS r_supersedes_id,
                r.sequence_number AS r_sequence,
                r.provider AS r_provider,
                r.provider_event_id AS r_provider_event_id,
                r.revision_type AS r_type,
                r.event_time AS r_event_time,
                r.processing_time AS r_processing_time,
                r.corrected_direction AS r_direction,
                r.corrected_original_rating AS r_original_rating,
                r.corrected_previous_target AS r_previous_target,
                r.corrected_target AS r_target,
                r.corrected_currency AS r_currency,
                r.corrected_target_date AS r_target_date,
                r.reason AS r_reason,
                r.data_mode AS r_data_mode,
                r.captured_at AS r_captured_at,
                r.provenance_id AS r_provenance_id,
                sr.source_reference_id AS sr_id,
                sr.page_number AS sr_page,
                sr.start_ms AS sr_start_ms,
                sr.end_ms AS sr_end_ms,
                sr.extracted_fragment AS sr_fragment,
                sr.extraction_confidence AS sr_confidence,
                sr.verified AS sr_verified,
                sr.data_mode AS sr_data_mode,
                sr.captured_at AS sr_captured_at,
                sr.provenance_id AS sr_provenance_id,
                sd.source_document_id AS sd_id,
                sd.source_type AS sd_type,
                sd.publisher AS sd_publisher,
                sd.title AS sd_title,
                sd.canonical_url AS sd_url,
                sd.published_at AS sd_published_at,
                sd.provider AS sd_provider,
                sd.external_id AS sd_external_id,
                sd.content_hash AS sd_content_hash,
                sd.license_class AS sd_license_class,
                sd.data_mode AS sd_data_mode,
                sd.captured_at AS sd_captured_at,
                sd.provenance_id AS sd_provenance_id
            FROM analyst_call_revisions r
            JOIN source_references sr ON sr.source_reference_id = r.source_reference_id
            JOIN source_documents sd ON sd.source_document_id = sr.source_document_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public JdbcAnalystCallRevisionRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int importAll(List<AnalystCallRevision> revisions) {
        int imported = 0;
        List<AnalystCallRevision> ordered = revisions.stream()
                .sorted(Comparator.comparing(AnalystCallRevision::callId)
                        .thenComparingInt(AnalystCallRevision::sequenceNumber))
                .toList();
        for (AnalystCallRevision revision : ordered) {
            if (saveIfAbsentInternal(revision)) {
                imported++;
            }
        }
        return imported;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(AnalystCallRevision revision) {
        return saveIfAbsentInternal(revision);
    }

    private boolean saveIfAbsentInternal(AnalystCallRevision revision) {
        if (!claimProviderEventIdentity(
                revision.provider(), revision.providerEventId(), PROVIDER_EVENT_KIND, revision.id())) {
            return false;
        }

        Instant callEventTime = callEventTime(revision.callId());
        if (revision.eventTime().isBefore(callEventTime)) {
            throw new IllegalArgumentException("revision eventTime must not precede the original call eventTime");
        }

        LatestRevision latest = latestRevision(revision.callId());
        validateLineage(revision, latest);

        insertSourceDocumentIfAbsent(revision.sourceReference().document());
        insertSourceReferenceIfAbsent(revision.sourceReference());
        return insertRevision(revision);
    }

    @Override
    public List<AnalystCallRevision> findByCallId(String callId) {
        return jdbc.query(
                SELECT_REVISIONS
                        + " WHERE r.call_id = :callId ORDER BY r.sequence_number ASC",
                new MapSqlParameterSource("callId", callId),
                this::mapRevision);
    }

    @Override
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM analyst_call_revisions", Long.class);
        return count == null ? 0 : count;
    }

    private void validateLineage(AnalystCallRevision revision, LatestRevision latest) {
        if (latest == null) {
            if (revision.sequenceNumber() != 1 || revision.supersedesRevisionId() != null) {
                throw new IllegalArgumentException("the first revision must start at sequence 1");
            }
            return;
        }

        if (latest.type() == AnalystCallRevisionType.CANCELLATION) {
            throw new IllegalArgumentException("a cancelled analyst call cannot receive another revision");
        }
        if (revision.sequenceNumber() != latest.sequenceNumber() + 1
                || !latest.id().equals(revision.supersedesRevisionId())) {
            throw new IllegalArgumentException("revision must supersede the latest lineage event");
        }
        if (revision.eventTime().isBefore(latest.eventTime())) {
            throw new IllegalArgumentException("revision eventTime must not move backwards in the lineage");
        }
    }

    private Instant callEventTime(String callId) {
        List<Instant> values = jdbc.query(
                "SELECT event_time FROM analyst_calls WHERE call_id = :callId",
                new MapSqlParameterSource("callId", callId),
                (result, rowNumber) -> instant(result, "event_time"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("unknown original analyst call: " + callId);
        }
        return values.getFirst();
    }

    private LatestRevision latestRevision(String callId) {
        List<LatestRevision> values = jdbc.query(
                """
                        SELECT revision_id, sequence_number, revision_type, event_time
                        FROM analyst_call_revisions
                        WHERE call_id = :callId
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource("callId", callId),
                (result, rowNumber) -> new LatestRevision(
                        result.getString("revision_id"), result.getInt("sequence_number"),
                        AnalystCallRevisionType.valueOf(result.getString("revision_type")),
                        instant(result, "event_time")));
        return values.isEmpty() ? null : values.getFirst();
    }

    private boolean insertRevision(AnalystCallRevision revision) {
        CorrectedCallTerms terms = revision.correctedTerms();
        String sql = """
                INSERT INTO analyst_call_revisions (
                    revision_id, schema_version, call_id, supersedes_revision_id,
                    supersedes_sequence_number, supersedes_revision_type, sequence_number,
                    provider, provider_event_id, revision_type, event_time, processing_time,
                    corrected_direction, corrected_original_rating, corrected_previous_target,
                    corrected_target, corrected_currency, corrected_target_date, reason,
                    source_reference_id, data_mode, captured_at, provenance_id
                ) VALUES (
                    :id, :schemaVersion, :callId, :supersedesRevisionId,
                    :supersedesSequenceNumber, :supersedesRevisionType, :sequenceNumber,
                    :provider, :providerEventId, :type, :eventTime, :processingTime,
                    :direction, :originalRating, :previousTarget,
                    :target, :currency, :targetDate, :reason,
                    :sourceReferenceId, :dataMode, :capturedAt, :provenanceId
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT (provider, provider_event_id) DO NOTHING";
        }
        int inserted = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("id", revision.id())
                .addValue("schemaVersion", revision.schemaVersion())
                .addValue("callId", revision.callId())
                .addValue("supersedesRevisionId", revision.supersedesRevisionId())
                .addValue("supersedesSequenceNumber",
                        revision.supersedesRevisionId() == null ? null : revision.sequenceNumber() - 1)
                .addValue("supersedesRevisionType",
                        revision.supersedesRevisionId() == null ? null : AnalystCallRevisionType.CORRECTION.name())
                .addValue("sequenceNumber", revision.sequenceNumber())
                .addValue("provider", revision.provider())
                .addValue("providerEventId", revision.providerEventId())
                .addValue("type", revision.type().name())
                .addValue("eventTime", utc(revision.eventTime()))
                .addValue("processingTime", utc(revision.processingTime()))
                .addValue("direction", terms == null ? null : terms.direction().name())
                .addValue("originalRating", terms == null ? null : terms.originalRating())
                .addValue("previousTarget", terms == null ? null : terms.previousTarget())
                .addValue("target", terms == null ? null : terms.target())
                .addValue("currency", terms == null || terms.currency() == null
                        ? null : terms.currency().getCurrencyCode())
                .addValue("targetDate", terms == null ? null : terms.targetDate())
                .addValue("reason", revision.reason())
                .addValue("sourceReferenceId", revision.sourceReference().id())
                .addValue("dataMode", revision.dataMode().name())
                .addValue("capturedAt", utc(revision.capturedAt()))
                .addValue("provenanceId", revision.provenanceId()));
        return inserted == 1;
    }

    private boolean claimProviderEventIdentity(
            String provider,
            String providerEventId,
            String eventKind,
            String canonicalEventId) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("provider", provider)
                .addValue("providerEventId", providerEventId)
                .addValue("eventKind", eventKind)
                .addValue("canonicalEventId", canonicalEventId);
        String insert = """
                INSERT INTO provider_event_identities (
                    provider, provider_event_id, event_kind, canonical_event_id
                ) VALUES (
                    :provider, :providerEventId, :eventKind, :canonicalEventId
                )
                """;
        int claimed;
        if (isPostgreSql()) {
            claimed = jdbc.update(insert + " ON CONFLICT (provider, provider_event_id) DO NOTHING", parameters);
        } else {
            Long existing = jdbc.queryForObject(
                    """
                            SELECT COUNT(*) FROM provider_event_identities
                            WHERE provider = :provider AND provider_event_id = :providerEventId
                            """,
                    parameters,
                    Long.class);
            claimed = existing == null || existing == 0 ? jdbc.update(insert, parameters) : 0;
        }
        if (claimed == 1) {
            return true;
        }

        String claimedKind = jdbc.queryForObject(
                """
                        SELECT event_kind FROM provider_event_identities
                        WHERE provider = :provider AND provider_event_id = :providerEventId
                        """,
                parameters,
                String.class);
        if (eventKind.equals(claimedKind)) {
            return false;
        }
        throw new IllegalArgumentException(
                "provider event identity is already claimed by " + claimedKind);
    }

    private void insertSourceDocumentIfAbsent(SourceDocument document) {
        insertIfMissing(
                "source_documents", "source_document_id", document.id(),
                """
                        INSERT INTO source_documents (
                            source_document_id, source_type, publisher, title, canonical_url, published_at,
                            provider, external_id, content_hash, license_class, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :type, :publisher, :title, :url, :publishedAt,
                            :provider, :externalId, :contentHash, :licenseClass, :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                parameters -> parameters
                        .addValue("id", document.id())
                        .addValue("type", document.type().name())
                        .addValue("publisher", document.publisher())
                        .addValue("title", document.title())
                        .addValue("url", document.canonicalUrl() == null ? null : document.canonicalUrl().toString())
                        .addValue("publishedAt", utcNullable(document.publishedAt()))
                        .addValue("provider", document.provider())
                        .addValue("externalId", document.externalId())
                        .addValue("contentHash", document.contentHash())
                        .addValue("licenseClass", document.licenseClass())
                        .addValue("dataMode", document.dataMode().name())
                        .addValue("capturedAt", utc(document.capturedAt()))
                        .addValue("provenanceId", document.provenanceId()));
    }

    private void insertSourceReferenceIfAbsent(SourceReference reference) {
        insertIfMissing(
                "source_references", "source_reference_id", reference.id(),
                """
                        INSERT INTO source_references (
                            source_reference_id, source_document_id, page_number, start_ms, end_ms,
                            extracted_fragment, extraction_confidence, verified, data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :documentId, :page, :startMs, :endMs,
                            :fragment, :confidence, :verified, :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                parameters -> parameters
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
    }

    private void insertIfMissing(
            String table,
            String idColumn,
            String id,
            String insertSql,
            Consumer<MapSqlParameterSource> parameterWriter) {
        MapSqlParameterSource parameters = new MapSqlParameterSource("id", id);
        parameterWriter.accept(parameters);
        if (isPostgreSql()) {
            jdbc.update(insertSql + " ON CONFLICT (" + idColumn + ") DO NOTHING", parameters);
            return;
        }

        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = :id",
                parameters,
                Long.class);
        if (count == null || count == 0) {
            jdbc.update(insertSql, parameters);
        }
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

    private AnalystCallRevision mapRevision(ResultSet result, int rowNumber) throws SQLException {
        SourceDocument document = new SourceDocument(
                result.getString("sd_id"), SourceType.valueOf(result.getString("sd_type")),
                result.getString("sd_publisher"), result.getString("sd_title"),
                nullableUri(result.getString("sd_url")), nullableInstant(result, "sd_published_at"),
                result.getString("sd_provider"), result.getString("sd_external_id"),
                result.getString("sd_content_hash"), result.getString("sd_license_class"),
                DataMode.valueOf(result.getString("sd_data_mode")), instant(result, "sd_captured_at"),
                result.getString("sd_provenance_id"));

        SourceReference sourceReference = new SourceReference(
                result.getString("sr_id"), document, nullableInteger(result, "sr_page"),
                nullableLong(result, "sr_start_ms"), nullableLong(result, "sr_end_ms"),
                result.getString("sr_fragment"), result.getBigDecimal("sr_confidence"),
                result.getBoolean("sr_verified"), DataMode.valueOf(result.getString("sr_data_mode")),
                instant(result, "sr_captured_at"), result.getString("sr_provenance_id"));

        AnalystCallRevisionType type = AnalystCallRevisionType.valueOf(result.getString("r_type"));
        String currencyCode = result.getString("r_currency");
        CorrectedCallTerms terms = type == AnalystCallRevisionType.CORRECTION
                ? new CorrectedCallTerms(
                        CallDirection.valueOf(result.getString("r_direction")),
                        result.getString("r_original_rating"), result.getBigDecimal("r_previous_target"),
                        result.getBigDecimal("r_target"),
                        currencyCode == null ? null : Currency.getInstance(currencyCode),
                        localDate(result, "r_target_date"))
                : null;

        return new AnalystCallRevision(
                result.getString("r_id"), result.getString("r_schema_version"),
                result.getString("r_call_id"), result.getString("r_supersedes_id"),
                result.getInt("r_sequence"), result.getString("r_provider"),
                result.getString("r_provider_event_id"), type, instant(result, "r_event_time"),
                instant(result, "r_processing_time"), terms, result.getString("r_reason"), sourceReference,
                DataMode.valueOf(result.getString("r_data_mode")), instant(result, "r_captured_at"),
                result.getString("r_provenance_id"));
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
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate date) {
            return date;
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        throw new SQLException("Unsupported date value for " + column + ": " + value);
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

    private static URI nullableUri(String value) {
        return value == null ? null : URI.create(value);
    }

    private record LatestRevision(
            String id,
            int sequenceNumber,
            AnalystCallRevisionType type,
            Instant eventTime) {
    }
}
