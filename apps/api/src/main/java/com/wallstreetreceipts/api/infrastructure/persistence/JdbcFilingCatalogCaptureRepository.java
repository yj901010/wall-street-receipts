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
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureReplayVerifier;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

@Repository
public class JdbcFilingCatalogCaptureRepository
        implements FilingCatalogCaptureRepository {

    private static final String SELECT_ROOT = """
            SELECT
                c.capture_id,
                c.provider,
                c.product,
                c.cik,
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
                c.recent_filing_count,
                c.historical_segment_count,
                c.historical_segment_status,
                b.decoded_body
            FROM sec_filing_catalog_captures c
            JOIN sec_decoded_response_bodies b
              ON b.decoded_body_sha256 = c.decoded_body_sha256
             AND b.decoded_body_length = c.decoded_body_length
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final FilingCatalogCaptureReplayVerifier replayVerifier;
    private volatile Boolean postgreSql;

    public JdbcFilingCatalogCaptureRepository(
            NamedParameterJdbcTemplate jdbc,
            FilingCatalogCaptureReplayVerifier replayVerifier) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.replayVerifier = Objects.requireNonNull(replayVerifier, "replayVerifier");
    }

    @Override
    @Transactional
    public FilingCatalogCaptureAppendResult append(FilingCatalogCapture capture) {
        Objects.requireNonNull(capture, "capture must not be null");
        if (capture.catalog().sourceReceipt().bodyRetention()
                != BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE) {
            throw new IllegalArgumentException(
                    "append requires a decoded body pending durable persistence");
        }
        replayVerifier.verify(capture);
        FilingCatalogCapture durable = capture.withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        replayVerifier.verify(durable);

        Optional<FilingCatalogCapture> naturalExisting = findByNaturalIdentity(durable);
        if (naturalExisting.isPresent()) {
            return replayOrConflict(
                    naturalExisting.orElseThrow(), durable, "natural capture identity");
        }
        Optional<FilingCatalogCapture> idExisting = findByCaptureId(durable.captureId());
        if (idExisting.isPresent()) {
            return replayOrConflict(
                    idExisting.orElseThrow(), durable, "captureId");
        }

        ensureDecodedBody(durable);
        int inserted = insertRoot(durable);
        if (inserted == 0) {
            Optional<FilingCatalogCapture> racedNatural = findByNaturalIdentity(durable);
            if (racedNatural.isPresent()) {
                return replayOrConflict(
                        racedNatural.orElseThrow(), durable, "natural capture identity");
            }
            Optional<FilingCatalogCapture> racedId = findByCaptureId(durable.captureId());
            if (racedId.isPresent()) {
                return replayOrConflict(racedId.orElseThrow(), durable, "captureId");
            }
            throw new IllegalArgumentException(
                    "filing catalog capture insert conflicted without a replayable row");
        }

        insertRecentFilings(durable);
        insertHistoricalSegments(durable);
        FilingCatalogCapture persisted = findByCaptureId(durable.captureId())
                .orElseThrow(() -> new IllegalStateException(
                        "inserted filing catalog capture could not be reconstructed"));
        if (!persisted.equals(durable)) {
            throw new IllegalArgumentException(
                    "inserted filing catalog capture did not round-trip exactly");
        }
        return FilingCatalogCaptureAppendResult.INSERTED;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FilingCatalogCapture> findByCaptureId(String captureId) {
        requireCanonicalText(captureId, "captureId");
        return findOne(
                SELECT_ROOT + " WHERE c.capture_id = :captureId",
                new MapSqlParameterSource("captureId", captureId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FilingCatalogCapture> findLatestAtOrBefore(
            String provider,
            String product,
            String cik,
            Instant evaluationAsOf,
            String parserVersion) {
        requireCanonicalText(provider, "provider");
        requireCanonicalText(product, "product");
        FilingCatalog.requireCik(cik);
        PersistentInstant.requireMicrosecondPrecision(
                evaluationAsOf, "evaluationAsOf");
        requireCanonicalText(parserVersion, "parserVersion");
        return findOne(
                SELECT_ROOT + """
                        WHERE c.provider = :provider
                          AND c.product = :product
                          AND c.cik = :cik
                          AND c.captured_at <= :evaluationAsOf
                          AND c.parser_version = :parserVersion
                        ORDER BY c.captured_at DESC, c.capture_id DESC
                        LIMIT 1
                        """,
                new MapSqlParameterSource()
                        .addValue("provider", provider)
                        .addValue("product", product)
                        .addValue("cik", cik)
                        .addValue("evaluationAsOf", utc(evaluationAsOf))
                        .addValue("parserVersion", parserVersion));
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_filing_catalog_captures", Long.class);
        return count == null ? 0 : count;
    }

    private FilingCatalogCaptureAppendResult replayOrConflict(
            FilingCatalogCapture existing,
            FilingCatalogCapture proposed,
            String identity) {
        if (existing.equals(proposed)) {
            return FilingCatalogCaptureAppendResult.IDENTICAL_REPLAY;
        }
        throw new IllegalArgumentException(
                "conflicting filing catalog capture for " + identity);
    }

    private Optional<FilingCatalogCapture> findByNaturalIdentity(
            FilingCatalogCapture capture) {
        FilingCatalog catalog = capture.catalog();
        return findOne(
                SELECT_ROOT + """
                        WHERE c.provider = :provider
                          AND c.product = :product
                          AND c.source_uri = :sourceUri
                          AND c.captured_at = :capturedAt
                        """,
                new MapSqlParameterSource()
                        .addValue("provider", catalog.provider())
                        .addValue("product", catalog.product())
                        .addValue("sourceUri", catalog.sourceUri().toASCIIString())
                        .addValue("capturedAt", utc(catalog.capturedAt())));
    }

    private Optional<FilingCatalogCapture> findOne(
            String sql,
            MapSqlParameterSource parameters) {
        List<RootRow> rows = jdbc.query(sql, parameters, this::mapRoot);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "filing catalog capture query returned an ambiguous root");
        }
        RootRow root = rows.getFirst();
        List<OrderedFiling> recent = jdbc.query(
                """
                        SELECT ordinal, provider_event_id, accession_number, form,
                               filing_date, report_date, accepted_at, primary_document_uri
                        FROM sec_filing_catalog_recent_filings
                        WHERE capture_id = :captureId
                        ORDER BY ordinal ASC
                        """,
                new MapSqlParameterSource("captureId", root.captureId()),
                this::mapRecentFiling);
        List<OrderedSegment> historical = jdbc.query(
                """
                        SELECT ordinal, file_name, advertised_filing_count,
                               advertised_filing_from, advertised_filing_to
                        FROM sec_filing_catalog_historical_segments
                        WHERE capture_id = :captureId
                        ORDER BY ordinal ASC
                        """,
                new MapSqlParameterSource("captureId", root.captureId()),
                this::mapHistoricalSegment);
        requireContiguousOrdinals(recent.stream().map(OrderedFiling::ordinal).toList());
        requireContiguousOrdinals(historical.stream().map(OrderedSegment::ordinal).toList());
        if (recent.size() != root.recentFilingCount()
                || historical.size() != root.historicalSegmentCount()) {
            throw new IllegalStateException(
                    "filing catalog capture child count does not match its root receipt");
        }

        FilingCatalog catalog = new FilingCatalog(
                root.receipt().provider(),
                root.receipt().product(),
                root.cik(),
                root.receipt().sourceUri(),
                root.processingTime(),
                root.receipt().capturedAt(),
                root.receipt(),
                recent.stream().map(OrderedFiling::filing).toList(),
                historical.stream().map(OrderedSegment::segment).toList());
        if (catalog.historicalSegmentStatus() != root.historicalSegmentStatus()) {
            throw new IllegalStateException(
                    "filing catalog capture status does not match its children");
        }
        FilingCatalogCapture capture = new FilingCatalogCapture(
                catalog, root.decodedBody());
        if (!capture.captureId().equals(root.captureId())) {
            throw new IllegalStateException(
                    "filing catalog capture identity could not be reproduced");
        }
        return Optional.of(replayVerifier.verify(capture));
    }

    private void ensureDecodedBody(FilingCatalogCapture capture) {
        SourceResponseReceipt receipt = capture.catalog().sourceReceipt();
        MapSqlParameterSource identity = new MapSqlParameterSource()
                .addValue("digest", receipt.decodedBodySha256());
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
            FilingCatalogCapture capture) {
        if (stored.length() != capture.catalog().sourceReceipt().decodedBodyLength()
                || !Arrays.equals(stored.body(), capture.decodedBody())) {
            throw new IllegalArgumentException(
                    "decoded body digest identity conflicts with stored bytes");
        }
    }

    private int insertRoot(FilingCatalogCapture capture) {
        FilingCatalog catalog = capture.catalog();
        SourceResponseReceipt receipt = catalog.sourceReceipt();
        String sql = """
                INSERT INTO sec_filing_catalog_captures (
                    capture_id, schema_version, provider, product, cik, source_uri,
                    processing_time, captured_at, http_status, media_type,
                    transport_content_encoding, etag, last_modified, parser_version,
                    decoded_body_sha256, decoded_body_length, body_representation,
                    body_retention, recent_filing_count, historical_segment_count,
                    historical_segment_status, immutable
                ) VALUES (
                    :captureId, :schemaVersion, :provider, :product, :cik, :sourceUri,
                    :processingTime, :capturedAt, :httpStatus, :mediaType,
                    :contentEncoding, :etag, :lastModified, :parserVersion,
                    :bodyDigest, :bodyLength, :bodyRepresentation,
                    :bodyRetention, :recentCount, :historicalCount,
                    :historicalStatus, TRUE
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT DO NOTHING";
        }
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("captureId", capture.captureId())
                .addValue("schemaVersion", FilingCatalogCapture.SCHEMA_VERSION)
                .addValue("provider", catalog.provider())
                .addValue("product", catalog.product())
                .addValue("cik", catalog.cik())
                .addValue("sourceUri", catalog.sourceUri().toASCIIString())
                .addValue("processingTime", utc(catalog.processingTime()))
                .addValue("capturedAt", utc(catalog.capturedAt()))
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
                .addValue("recentCount", catalog.recentFilings().size())
                .addValue("historicalCount", catalog.historicalSegments().size())
                .addValue("historicalStatus", catalog.historicalSegmentStatus().name()));
    }

    private void insertRecentFilings(FilingCatalogCapture capture) {
        FilingCatalog catalog = capture.catalog();
        for (int ordinal = 0; ordinal < catalog.recentFilings().size(); ordinal++) {
            FilingRecord filing = catalog.recentFilings().get(ordinal);
            jdbc.update(
                    """
                            INSERT INTO sec_filing_catalog_recent_filings (
                                capture_id, ordinal, catalog_cik,
                                catalog_processing_time, catalog_captured_at,
                                provider_event_id, accession_number, form,
                                filing_date, report_date, accepted_at, primary_document_uri
                            ) VALUES (
                                :captureId, :ordinal, :cik,
                                :processingTime, :capturedAt,
                                :providerEventId, :accessionNumber, :form,
                                :filingDate, :reportDate, :acceptedAt, :primaryDocumentUri
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("captureId", capture.captureId())
                            .addValue("ordinal", ordinal)
                            .addValue("cik", catalog.cik())
                            .addValue("processingTime", utc(catalog.processingTime()))
                            .addValue("capturedAt", utc(catalog.capturedAt()))
                            .addValue("providerEventId", filing.providerEventId())
                            .addValue("accessionNumber", filing.accessionNumber())
                            .addValue("form", filing.form())
                            .addValue("filingDate", filing.filingDate())
                            .addValue("reportDate", filing.reportDate())
                            .addValue("acceptedAt", utc(filing.acceptedAt()))
                            .addValue("primaryDocumentUri",
                                    filing.primaryDocumentUri().toASCIIString()));
        }
    }

    private void insertHistoricalSegments(FilingCatalogCapture capture) {
        FilingCatalog catalog = capture.catalog();
        for (int ordinal = 0; ordinal < catalog.historicalSegments().size(); ordinal++) {
            HistoricalFilingSegmentDescriptor segment =
                    catalog.historicalSegments().get(ordinal);
            jdbc.update(
                    """
                            INSERT INTO sec_filing_catalog_historical_segments (
                                capture_id, ordinal, catalog_cik,
                                catalog_processing_time, catalog_captured_at,
                                file_name, advertised_filing_count,
                                advertised_filing_from, advertised_filing_to
                            ) VALUES (
                                :captureId, :ordinal, :cik,
                                :processingTime, :capturedAt,
                                :fileName, :filingCount, :filingFrom, :filingTo
                            )
                            """,
                    new MapSqlParameterSource()
                            .addValue("captureId", capture.captureId())
                            .addValue("ordinal", ordinal)
                            .addValue("cik", catalog.cik())
                            .addValue("processingTime", utc(catalog.processingTime()))
                            .addValue("capturedAt", utc(catalog.capturedAt()))
                            .addValue("fileName", segment.fileName())
                            .addValue("filingCount", segment.advertisedFilingCount())
                            .addValue("filingFrom", segment.advertisedFilingFrom())
                            .addValue("filingTo", segment.advertisedFilingTo()));
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
                result.getString("capture_id"),
                result.getString("cik"),
                instant(result, "processing_time"),
                receipt,
                result.getBytes("decoded_body"),
                result.getInt("recent_filing_count"),
                result.getInt("historical_segment_count"),
                FilingCatalog.HistoricalSegmentStatus.valueOf(
                        result.getString("historical_segment_status")));
    }

    private OrderedFiling mapRecentFiling(ResultSet result, int rowNumber)
            throws SQLException {
        return new OrderedFiling(
                result.getInt("ordinal"),
                new FilingRecord(
                        result.getString("provider_event_id"),
                        result.getString("accession_number"),
                        result.getString("form"),
                        result.getObject("filing_date", LocalDate.class),
                        result.getObject("report_date", LocalDate.class),
                        instant(result, "accepted_at"),
                        URI.create(result.getString("primary_document_uri"))));
    }

    private OrderedSegment mapHistoricalSegment(ResultSet result, int rowNumber)
            throws SQLException {
        return new OrderedSegment(
                result.getInt("ordinal"),
                new HistoricalFilingSegmentDescriptor(
                        result.getString("file_name"),
                        result.getLong("advertised_filing_count"),
                        result.getObject("advertised_filing_from", LocalDate.class),
                        result.getObject("advertised_filing_to", LocalDate.class)));
    }

    private static void requireContiguousOrdinals(List<Integer> ordinals) {
        for (int expected = 0; expected < ordinals.size(); expected++) {
            if (ordinals.get(expected) != expected) {
                throw new IllegalStateException(
                        "filing catalog capture child ordinals are not contiguous");
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
            String cik,
            Instant processingTime,
            SourceResponseReceipt receipt,
            byte[] decodedBody,
            int recentFilingCount,
            int historicalSegmentCount,
            FilingCatalog.HistoricalSegmentStatus historicalSegmentStatus) {
    }

    private record OrderedFiling(int ordinal, FilingRecord filing) {
    }

    private record OrderedSegment(
            int ordinal,
            HistoricalFilingSegmentDescriptor segment) {
    }

    private record StoredBody(long length, byte[] body) {
    }
}
