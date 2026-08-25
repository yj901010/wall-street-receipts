package com.wallstreetreceipts.api.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureReplayVerifier;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment.AdvertisedComparison;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

@Repository
public class JdbcHistoricalFilingSegmentCaptureRepository
        implements HistoricalFilingSegmentCaptureRepository {

    private static final String SELECT_ROOT = """
            SELECT
                c.segment_capture_id,
                c.provider,
                c.product,
                c.root_capture_id,
                c.descriptor_ordinal,
                c.cik,
                c.root_captured_at,
                c.file_name,
                c.advertised_filing_count,
                c.advertised_filing_from,
                c.advertised_filing_to,
                c.source_uri,
                c.processing_time,
                c.captured_at,
                c.http_status,
                c.media_type,
                c.transport_content_encoding,
                c.etag,
                c.last_modified,
                c.parser_version,
                c.decoded_body_sha256,
                c.decoded_body_length,
                c.body_representation,
                c.body_retention,
                c.observed_filing_count,
                c.observed_filing_from,
                c.observed_filing_to,
                c.advertised_comparison,
                b.decoded_body
            FROM sec_historical_filing_segment_captures c
            JOIN sec_decoded_response_bodies b
              ON b.decoded_body_sha256 = c.decoded_body_sha256
             AND b.decoded_body_length = c.decoded_body_length
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final HistoricalFilingSegmentCaptureReplayVerifier replayVerifier;
    private final FilingCatalogCaptureRepository rootRepository;
    private volatile Boolean postgreSql;

    public JdbcHistoricalFilingSegmentCaptureRepository(
            NamedParameterJdbcTemplate jdbc,
            HistoricalFilingSegmentCaptureReplayVerifier replayVerifier,
            FilingCatalogCaptureRepository rootRepository) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.replayVerifier = Objects.requireNonNull(replayVerifier, "replayVerifier");
        this.rootRepository = Objects.requireNonNull(rootRepository, "rootRepository");
    }

    @Override
    @Transactional
    public HistoricalFilingSegmentCaptureAppendResult append(
            HistoricalFilingSegmentCapture capture) {
        Objects.requireNonNull(capture, "capture must not be null");
        if (capture.segment().sourceReceipt().bodyRetention()
                != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE) {
            throw new IllegalArgumentException(
                    "append requires a decoded body pending durable persistence");
        }
        FilingCatalogCapture root = exactRoot(capture.segment().rootCaptureId());
        replayVerifier.verify(capture, root);
        HistoricalFilingSegmentCapture durable = capture.withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        replayVerifier.verify(durable, root);

        Optional<HistoricalFilingSegmentCapture> natural = findByNaturalIdentity(durable);
        if (natural.isPresent()) {
            return replayOrConflict(natural.orElseThrow(), durable, "natural capture identity");
        }
        Optional<HistoricalFilingSegmentCapture> byId = findByCaptureId(durable.captureId());
        if (byId.isPresent()) {
            return replayOrConflict(byId.orElseThrow(), durable, "captureId");
        }

        ensureDecodedBody(durable);
        int inserted = insertRoot(durable);
        if (inserted == 0) {
            Optional<HistoricalFilingSegmentCapture> racedNatural =
                    findByNaturalIdentity(durable);
            if (racedNatural.isPresent()) {
                return replayOrConflict(
                        racedNatural.orElseThrow(), durable, "natural capture identity");
            }
            Optional<HistoricalFilingSegmentCapture> racedId =
                    findByCaptureId(durable.captureId());
            if (racedId.isPresent()) {
                return replayOrConflict(racedId.orElseThrow(), durable, "captureId");
            }
            throw new IllegalArgumentException(
                    "historical segment capture insert conflicted without a replayable row");
        }

        insertFilings(durable);
        HistoricalFilingSegmentCapture persisted = findByCaptureId(durable.captureId())
                .orElseThrow(() -> new IllegalStateException(
                        "inserted historical segment capture could not be reconstructed"));
        if (!persisted.equals(durable)) {
            throw new IllegalArgumentException(
                    "inserted historical segment capture did not round-trip exactly");
        }
        return HistoricalFilingSegmentCaptureAppendResult.INSERTED;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HistoricalFilingSegmentCapture> findByCaptureId(String captureId) {
        requireCanonicalText(captureId, "captureId");
        return findOne(
                SELECT_ROOT + " WHERE c.segment_capture_id = :captureId",
                new MapSqlParameterSource("captureId", captureId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HistoricalFilingSegmentCapture> findLatestAtOrBefore(
            String rootCaptureId,
            int descriptorOrdinal,
            Instant evaluationAsOf,
            String parserVersion) {
        requireCanonicalText(rootCaptureId, "rootCaptureId");
        if (descriptorOrdinal < 0) {
            throw new IllegalArgumentException("descriptorOrdinal must be nonnegative");
        }
        PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        requireCanonicalText(parserVersion, "parserVersion");
        return findOne(
                SELECT_ROOT + """
                        WHERE c.root_capture_id = :rootCaptureId
                          AND c.descriptor_ordinal = :descriptorOrdinal
                          AND c.captured_at <= :evaluationAsOf
                          AND c.parser_version = :parserVersion
                        ORDER BY c.captured_at DESC, c.segment_capture_id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("rootCaptureId", rootCaptureId)
                        .addValue("descriptorOrdinal", descriptorOrdinal)
                        .addValue("evaluationAsOf", utc(evaluationAsOf))
                        .addValue("parserVersion", parserVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_historical_filing_segment_captures",
                Long.class);
        return count == null ? 0 : count;
    }

    private Optional<HistoricalFilingSegmentCapture> findByNaturalIdentity(
            HistoricalFilingSegmentCapture capture) {
        HistoricalFilingSegment segment = capture.segment();
        return findOne(
                SELECT_ROOT + """
                        WHERE c.root_capture_id = :rootCaptureId
                          AND c.descriptor_ordinal = :descriptorOrdinal
                          AND c.source_uri = :sourceUri
                          AND c.captured_at = :capturedAt
                        """,
                new MapSqlParameterSource()
                        .addValue("rootCaptureId", segment.rootCaptureId())
                        .addValue("descriptorOrdinal", segment.descriptorOrdinal())
                        .addValue("sourceUri", segment.sourceUri().toASCIIString())
                        .addValue("capturedAt", utc(segment.capturedAt())));
    }

    private Optional<HistoricalFilingSegmentCapture> findOne(
            String sql,
            MapSqlParameterSource parameters) {
        List<RootRow> rows = jdbc.query(sql, parameters, this::mapRoot);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "historical segment capture query returned an ambiguous root");
        }
        RootRow row = rows.getFirst();
        List<OrderedFiling> orderedFilings = jdbc.query(
                """
                        SELECT ordinal, provider_event_id, accession_number, form,
                               filing_date, report_date, accepted_at, primary_document_uri
                        FROM sec_historical_filing_segment_filings
                        WHERE segment_capture_id = :captureId
                        ORDER BY ordinal ASC
                        """,
                new MapSqlParameterSource("captureId", row.captureId()),
                this::mapFiling);
        requireContiguousOrdinals(orderedFilings.stream()
                .map(OrderedFiling::ordinal)
                .toList());
        if (orderedFilings.size() != row.observedFilingCount()) {
            throw new IllegalStateException(
                    "historical segment child count does not match its capture receipt");
        }

        HistoricalFilingSegment segment = new HistoricalFilingSegment(
                row.receipt().provider(),
                row.receipt().product(),
                row.rootCaptureId(),
                row.rootCapturedAt(),
                row.descriptorOrdinal(),
                row.cik(),
                row.descriptor(),
                row.receipt().sourceUri(),
                row.processingTime(),
                row.receipt().capturedAt(),
                row.receipt(),
                orderedFilings.stream().map(OrderedFiling::filing).toList());
        if (!Objects.equals(segment.observedFilingFrom(), row.observedFilingFrom())
                || !Objects.equals(segment.observedFilingTo(), row.observedFilingTo())
                || segment.advertisedComparison() != row.advertisedComparison()) {
            throw new IllegalStateException(
                    "historical segment observed comparison does not match its rows");
        }
        HistoricalFilingSegmentCapture capture = new HistoricalFilingSegmentCapture(
                segment, row.decodedBody());
        if (!capture.captureId().equals(row.captureId())) {
            throw new IllegalStateException(
                    "historical segment capture identity could not be reproduced");
        }
        FilingCatalogCapture root = exactRoot(row.rootCaptureId());
        return Optional.of(replayVerifier.verify(capture, root));
    }

    private FilingCatalogCapture exactRoot(String rootCaptureId) {
        return rootRepository.findByCaptureId(rootCaptureId)
                .orElseThrow(() -> new IllegalStateException(
                        "historical segment root capture could not be reconstructed"));
    }

    private HistoricalFilingSegmentCaptureAppendResult replayOrConflict(
            HistoricalFilingSegmentCapture existing,
            HistoricalFilingSegmentCapture proposed,
            String identity) {
        if (existing.equals(proposed)) {
            return HistoricalFilingSegmentCaptureAppendResult.IDENTICAL_REPLAY;
        }
        throw new IllegalArgumentException(
                "conflicting historical segment capture for " + identity);
    }

    private void ensureDecodedBody(HistoricalFilingSegmentCapture capture) {
        SourceResponseReceipt receipt = capture.segment().sourceReceipt();
        MapSqlParameterSource identity = new MapSqlParameterSource(
                "digest", receipt.decodedBodySha256());
        List<StoredBody> existing = jdbc.query(
                """
                        SELECT decoded_body_length, decoded_body
                        FROM sec_decoded_response_bodies
                        WHERE decoded_body_sha256 = :digest
                        """,
                identity,
                (result, rowNumber) -> new StoredBody(
                        result.getLong("decoded_body_length"),
                        result.getBytes("decoded_body")));
        if (!existing.isEmpty()) {
            requireExactBody(existing.getFirst(), capture);
            return;
        }

        String sql = """
                INSERT INTO sec_decoded_response_bodies (
                    decoded_body_sha256, decoded_body_length, decoded_body, immutable
                ) VALUES (
                    :digest, :length, :body, TRUE
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT (decoded_body_sha256) DO NOTHING";
        }
        int inserted = jdbc.update(sql, new MapSqlParameterSource()
                .addValue("digest", receipt.decodedBodySha256())
                .addValue("length", receipt.decodedBodyLength())
                .addValue("body", capture.decodedBody()));
        if (inserted == 0) {
            StoredBody raced = jdbc.query(
                    """
                            SELECT decoded_body_length, decoded_body
                            FROM sec_decoded_response_bodies
                            WHERE decoded_body_sha256 = :digest
                            """,
                    identity,
                    (result, rowNumber) -> new StoredBody(
                            result.getLong("decoded_body_length"),
                            result.getBytes("decoded_body")))
                    .stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "decoded body insert conflicted without a stored body"));
            requireExactBody(raced, capture);
        }
    }

    private static void requireExactBody(
            StoredBody stored,
            HistoricalFilingSegmentCapture capture) {
        if (stored.length() != capture.segment().sourceReceipt().decodedBodyLength()
                || !Arrays.equals(stored.body(), capture.decodedBody())) {
            throw new IllegalArgumentException(
                    "decoded body digest identity conflicts with stored bytes");
        }
    }

    private int insertRoot(HistoricalFilingSegmentCapture capture) {
        HistoricalFilingSegment segment = capture.segment();
        SourceResponseReceipt receipt = segment.sourceReceipt();
        HistoricalFilingSegmentDescriptor descriptor = segment.descriptor();
        String sql = """
                INSERT INTO sec_historical_filing_segment_captures (
                    segment_capture_id, schema_version, provider, product,
                    root_capture_id, descriptor_ordinal, cik, root_captured_at,
                    file_name, advertised_filing_count, advertised_filing_from,
                    advertised_filing_to, source_uri, processing_time, captured_at,
                    http_status, media_type, transport_content_encoding, etag,
                    last_modified, parser_version, decoded_body_sha256,
                    decoded_body_length, body_representation, body_retention,
                    observed_filing_count, observed_filing_from, observed_filing_to,
                    advertised_comparison, immutable
                ) VALUES (
                    :captureId, :schemaVersion, :provider, :product,
                    :rootCaptureId, :descriptorOrdinal, :cik, :rootCapturedAt,
                    :fileName, :advertisedCount, :advertisedFrom,
                    :advertisedTo, :sourceUri, :processingTime, :capturedAt,
                    :httpStatus, :mediaType, :contentEncoding, :etag,
                    :lastModified, :parserVersion, :bodyDigest,
                    :bodyLength, :bodyRepresentation, :bodyRetention,
                    :observedCount, :observedFrom, :observedTo,
                    :advertisedComparison, TRUE
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT DO NOTHING";
        }
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("captureId", capture.captureId())
                .addValue("schemaVersion", HistoricalFilingSegmentCapture.SCHEMA_VERSION)
                .addValue("provider", segment.provider())
                .addValue("product", segment.product())
                .addValue("rootCaptureId", segment.rootCaptureId())
                .addValue("descriptorOrdinal", segment.descriptorOrdinal())
                .addValue("cik", segment.cik())
                .addValue("rootCapturedAt", utc(segment.rootCapturedAt()))
                .addValue("fileName", descriptor.fileName())
                .addValue("advertisedCount", descriptor.advertisedFilingCount())
                .addValue("advertisedFrom", descriptor.advertisedFilingFrom())
                .addValue("advertisedTo", descriptor.advertisedFilingTo())
                .addValue("sourceUri", segment.sourceUri().toASCIIString())
                .addValue("processingTime", utc(segment.processingTime()))
                .addValue("capturedAt", utc(segment.capturedAt()))
                .addValue("httpStatus", receipt.httpStatus())
                .addValue("mediaType", receipt.mediaType())
                .addValue("contentEncoding", receipt.transportContentEncoding().name())
                .addValue("etag", receipt.etag())
                .addValue("lastModified", utc(receipt.lastModified()))
                .addValue("parserVersion", receipt.parserVersion())
                .addValue("bodyDigest", receipt.decodedBodySha256())
                .addValue("bodyLength", receipt.decodedBodyLength())
                .addValue("bodyRepresentation", receipt.bodyRepresentation().name())
                .addValue("bodyRetention", receipt.bodyRetention().name())
                .addValue("observedCount", segment.observedFilingCount())
                .addValue("observedFrom", segment.observedFilingFrom())
                .addValue("observedTo", segment.observedFilingTo())
                .addValue("advertisedComparison", segment.advertisedComparison().name()));
    }

    private void insertFilings(HistoricalFilingSegmentCapture capture) {
        HistoricalFilingSegment segment = capture.segment();
        SqlParameterSource[] batch = new SqlParameterSource[segment.filings().size()];
        for (int ordinal = 0; ordinal < segment.filings().size(); ordinal++) {
            HistoricalFilingRecord filing = segment.filings().get(ordinal);
            batch[ordinal] = new MapSqlParameterSource()
                    .addValue("captureId", capture.captureId())
                    .addValue("ordinal", ordinal)
                    .addValue("cik", segment.cik())
                    .addValue("processingTime", utc(segment.processingTime()))
                    .addValue("capturedAt", utc(segment.capturedAt()))
                    .addValue("providerEventId", filing.providerEventId())
                    .addValue("accessionNumber", filing.accessionNumber())
                    .addValue("form", filing.form())
                    .addValue("filingDate", filing.filingDate())
                    .addValue("reportDate", filing.reportDate())
                    .addValue("acceptedAt", utc(filing.acceptedAt()))
                    .addValue(
                            "primaryDocumentUri",
                            filing.primaryDocumentUri() == null
                                    ? null
                                    : filing.primaryDocumentUri().toASCIIString());
        }
        if (batch.length > 0) {
            jdbc.batchUpdate(
                    """
                            INSERT INTO sec_historical_filing_segment_filings (
                                segment_capture_id, ordinal, segment_cik,
                                segment_processing_time, segment_captured_at,
                                provider_event_id, accession_number, form,
                                filing_date, report_date, accepted_at,
                                primary_document_uri
                            ) VALUES (
                                :captureId, :ordinal, :cik,
                                :processingTime, :capturedAt,
                                :providerEventId, :accessionNumber, :form,
                                :filingDate, :reportDate, :acceptedAt,
                                :primaryDocumentUri
                            )
                            """,
                    batch);
        }
    }

    private RootRow mapRoot(ResultSet result, int rowNumber) throws SQLException {
        Instant capturedAt = instant(result, "captured_at");
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                result.getString("provider"),
                result.getString("product"),
                URI.create(result.getString("source_uri")),
                result.getInt("http_status"),
                result.getString("media_type"),
                TransportContentEncoding.valueOf(
                        result.getString("transport_content_encoding")),
                result.getString("etag"),
                nullableInstant(result, "last_modified"),
                result.getString("parser_version"),
                result.getString("decoded_body_sha256"),
                result.getLong("decoded_body_length"),
                capturedAt,
                BodyRepresentation.valueOf(result.getString("body_representation")),
                BodyRetention.valueOf(result.getString("body_retention")));
        return new RootRow(
                result.getString("segment_capture_id"),
                result.getString("root_capture_id"),
                result.getInt("descriptor_ordinal"),
                result.getString("cik"),
                instant(result, "root_captured_at"),
                new HistoricalFilingSegmentDescriptor(
                        result.getString("file_name"),
                        result.getLong("advertised_filing_count"),
                        result.getObject("advertised_filing_from", LocalDate.class),
                        result.getObject("advertised_filing_to", LocalDate.class)),
                instant(result, "processing_time"),
                receipt,
                result.getBytes("decoded_body"),
                result.getLong("observed_filing_count"),
                result.getObject("observed_filing_from", LocalDate.class),
                result.getObject("observed_filing_to", LocalDate.class),
                AdvertisedComparison.valueOf(result.getString("advertised_comparison")));
    }

    private OrderedFiling mapFiling(ResultSet result, int rowNumber)
            throws SQLException {
        return new OrderedFiling(
                result.getInt("ordinal"),
                new HistoricalFilingRecord(
                        result.getString("provider_event_id"),
                        result.getString("accession_number"),
                        result.getString("form"),
                        result.getObject("filing_date", LocalDate.class),
                        result.getObject("report_date", LocalDate.class),
                        instant(result, "accepted_at"),
                        nullableUri(result, "primary_document_uri")));
    }

    private static void requireContiguousOrdinals(List<Integer> ordinals) {
        for (int expected = 0; expected < ordinals.size(); expected++) {
            if (ordinals.get(expected) != expected) {
                throw new IllegalStateException(
                        "historical segment child ordinals are not contiguous");
            }
        }
    }

    private boolean isPostgreSql() {
        Boolean cached = postgreSql;
        if (cached != null) {
            return cached;
        }
        Boolean detected = jdbc.getJdbcOperations().execute(
                (ConnectionCallback<Boolean>) connection -> connection.getMetaData()
                        .getDatabaseProductName()
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("postgresql"));
        postgreSql = Boolean.TRUE.equals(detected);
        return postgreSql;
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        Object value = result.getObject(column);
        if (value instanceof OffsetDateTime offsetDateTime) {
            return offsetDateTime.toInstant();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toInstant();
        }
        throw new SQLException("unsupported timestamp representation for " + column);
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

    private static URI nullableUri(ResultSet result, String column)
            throws SQLException {
        String value = result.getString(column);
        return value == null ? null : URI.create(value);
    }

    private static void requireCanonicalText(String value, String field) {
        if (value == null) {
            throw new NullPointerException(field + " must not be null");
        }
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    field + " must be nonblank and trimmed");
        }
    }

    private record RootRow(
            String captureId,
            String rootCaptureId,
            int descriptorOrdinal,
            String cik,
            Instant rootCapturedAt,
            HistoricalFilingSegmentDescriptor descriptor,
            Instant processingTime,
            SourceResponseReceipt receipt,
            byte[] decodedBody,
            long observedFilingCount,
            LocalDate observedFilingFrom,
            LocalDate observedFilingTo,
            AdvertisedComparison advertisedComparison) {
    }

    private record OrderedFiling(int ordinal, HistoricalFilingRecord filing) {
    }

    private record StoredBody(long length, byte[] body) {
    }
}
