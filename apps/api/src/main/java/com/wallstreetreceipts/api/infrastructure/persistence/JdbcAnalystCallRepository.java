package com.wallstreetreceipts.api.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.call.AnalystCallDetail;
import com.wallstreetreceipts.api.application.call.AnalystCallFilter;
import com.wallstreetreceipts.api.application.call.AnalystCallPage;
import com.wallstreetreceipts.api.application.call.CallSortField;
import com.wallstreetreceipts.api.application.port.out.AnalystCallDataSet;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCall;
import com.wallstreetreceipts.api.domain.call.CallDirection;
import com.wallstreetreceipts.api.domain.call.CallStatus;
import com.wallstreetreceipts.api.domain.market.DataMode;
import com.wallstreetreceipts.api.domain.market.MarketSnapshot;
import com.wallstreetreceipts.api.domain.master.Analyst;
import com.wallstreetreceipts.api.domain.master.Asset;
import com.wallstreetreceipts.api.domain.master.AssetType;
import com.wallstreetreceipts.api.domain.master.Institution;
import com.wallstreetreceipts.api.domain.source.SourceDocument;
import com.wallstreetreceipts.api.domain.source.SourceReference;
import com.wallstreetreceipts.api.domain.source.SourceType;

@Repository
public class JdbcAnalystCallRepository implements AnalystCallRepository {

    private static final String PROVIDER_EVENT_KIND = "ANALYST_CALL";

    private static final String SELECT_DETAIL = """
            SELECT
                c.call_id AS c_call_id,
                c.provider AS c_provider,
                c.provider_event_id AS c_provider_event_id,
                c.event_time AS c_event_time,
                c.processing_time AS c_processing_time,
                c.direction AS c_direction,
                c.original_rating AS c_original_rating,
                c.previous_target AS c_previous_target,
                c.target AS c_target,
                c.currency AS c_currency,
                c.target_date AS c_target_date,
                c.status AS c_status,
                c.data_mode AS c_data_mode,
                c.captured_at AS c_captured_at,
                c.provenance_id AS c_provenance_id,
                i.institution_id AS i_id,
                i.canonical_name AS i_name,
                i.slug AS i_slug,
                i.country AS i_country,
                i.active AS i_active,
                i.data_mode AS i_data_mode,
                i.effective_at AS i_effective_at,
                i.captured_at AS i_captured_at,
                i.provenance_id AS i_provenance_id,
                an.analyst_id AS an_id,
                an.canonical_name AS an_name,
                an.active AS an_active,
                an.data_mode AS an_data_mode,
                an.effective_at AS an_effective_at,
                an.captured_at AS an_captured_at,
                an.provenance_id AS an_provenance_id,
                a.asset_id AS a_id,
                a.asset_type AS a_type,
                a.canonical_name AS a_name,
                a.ticker AS a_ticker,
                a.primary_currency AS a_currency,
                a.active AS a_active,
                a.data_mode AS a_data_mode,
                a.effective_at AS a_effective_at,
                a.captured_at AS a_captured_at,
                a.provenance_id AS a_provenance_id,
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
                sd.provenance_id AS sd_provenance_id,
                ms.snapshot_id AS ms_id,
                ms.call_id AS ms_call_id,
                ms.asset_id AS ms_asset_id,
                ms.event_time AS ms_event_time,
                ms.processing_time AS ms_processing_time,
                ms.asset_price AS ms_asset_price,
                ms.spx AS ms_spx,
                ms.ndx AS ms_ndx,
                ms.vix AS ms_vix,
                ms.treasury_2y AS ms_treasury_2y,
                ms.treasury_10y AS ms_treasury_10y,
                ms.real_yield AS ms_real_yield,
                ms.dxy AS ms_dxy,
                ms.wti AS ms_wti,
                ms.gold AS ms_gold,
                ms.volatility AS ms_volatility,
                ms.distance_from_52w_high AS ms_distance_from_52w_high,
                ms.distance_from_ath AS ms_distance_from_ath,
                ms.data_mode AS ms_data_mode,
                ms.captured_at AS ms_captured_at,
                ms.provenance_id AS ms_provenance_id
            FROM analyst_calls c
            JOIN institutions i ON i.institution_id = c.institution_id
            LEFT JOIN analysts an ON an.analyst_id = c.analyst_id
            JOIN assets a ON a.asset_id = c.asset_id
            JOIN source_references sr ON sr.source_reference_id = c.source_reference_id
            JOIN source_documents sd ON sd.source_document_id = sr.source_document_id
            LEFT JOIN market_snapshots ms ON ms.call_id = c.call_id
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private volatile Boolean postgreSql;

    public JdbcAnalystCallRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public int importDataSet(AnalystCallDataSet dataSet) {
        dataSet.institutions().forEach(this::insertInstitutionIfAbsent);
        dataSet.analysts().forEach(this::insertAnalystIfAbsent);
        dataSet.assets().forEach(this::insertAssetIfAbsent);

        Map<String, MarketSnapshot> snapshotsByCall = new HashMap<>();
        dataSet.snapshots().forEach(snapshot -> {
            MarketSnapshot duplicate = snapshotsByCall.put(snapshot.callId(), snapshot);
            if (duplicate != null) {
                throw new IllegalArgumentException("Multiple fixture snapshots for call " + snapshot.callId());
            }
        });

        int imported = 0;
        for (AnalystCall call : dataSet.calls()) {
            if (saveIfAbsentInternal(call, snapshotsByCall.get(call.id()))) {
                imported++;
            }
        }
        return imported;
    }

    @Override
    @Transactional
    public boolean saveIfAbsent(AnalystCall call, MarketSnapshot snapshot) {
        return saveIfAbsentInternal(call, snapshot);
    }

    private boolean saveIfAbsentInternal(AnalystCall call, MarketSnapshot snapshot) {
        if (snapshot != null
                && (!snapshot.callId().equals(call.id())
                || !snapshot.assetId().equals(call.asset().id())
                || !snapshot.eventTime().equals(call.eventTime()))) {
            throw new IllegalArgumentException("Snapshot identity and eventTime must match its analyst call");
        }
        if (!claimProviderEventIdentity(
                call.provider(), call.providerEventId(), PROVIDER_EVENT_KIND, call.id())) {
            return false;
        }

        insertInstitutionIfAbsent(call.institution());
        if (call.analyst() != null) {
            insertAnalystIfAbsent(call.analyst());
        }
        insertAssetIfAbsent(call.asset());
        insertSourceDocumentIfAbsent(call.sourceReference().document());
        insertSourceReferenceIfAbsent(call.sourceReference());
        insertCall(call);
        if (snapshot != null) {
            insertSnapshot(snapshot);
        }
        return true;
    }

    @Override
    public AnalystCallPage findAll(AnalystCallFilter filter) {
        QueryParts parts = filters(filter);
        MapSqlParameterSource parameters = parts.parameters()
                .addValue("limit", filter.size())
                .addValue("offset", Math.multiplyExact((long) filter.page(), filter.size()));

        String orderBy = sortExpression(filter.sort()) + " " + filter.order().name();
        String query = SELECT_DETAIL + parts.where()
                + " ORDER BY " + orderBy + ", c.call_id ASC LIMIT :limit OFFSET :offset";
        List<AnalystCallDetail> items = jdbc.query(query, parameters, this::mapDetail);

        String countQuery = """
                SELECT COUNT(*)
                FROM analyst_calls c
                JOIN institutions i ON i.institution_id = c.institution_id
                LEFT JOIN analysts an ON an.analyst_id = c.analyst_id
                JOIN assets a ON a.asset_id = c.asset_id
                """ + parts.where();
        Long total = jdbc.queryForObject(countQuery, parts.parameters(), Long.class);
        long totalElements = total == null ? 0 : total;
        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + filter.size() - 1) / filter.size());
        boolean first = filter.page() == 0;
        boolean last = totalPages == 0 || filter.page() >= totalPages - 1;

        return new AnalystCallPage(
                items, filter.page(), filter.size(), totalElements, totalPages, first, last,
                filter.sort(), filter.order());
    }

    @Override
    public Optional<AnalystCallDetail> findById(String callId) {
        List<AnalystCallDetail> result = jdbc.query(
                SELECT_DETAIL + " WHERE c.call_id = :callId",
                new MapSqlParameterSource("callId", callId),
                this::mapDetail);
        return result.stream().findFirst();
    }

    @Override
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject("SELECT COUNT(*) FROM analyst_calls", Long.class);
        return count == null ? 0 : count;
    }

    private QueryParts filters(AnalystCallFilter filter) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        MapSqlParameterSource parameters = new MapSqlParameterSource();

        addFilter(where, parameters, "assetId", filter.assetId(), " AND c.asset_id = :assetId");
        addFilter(where, parameters, "ticker", filter.ticker(), " AND LOWER(a.ticker) = LOWER(:ticker)");
        addFilter(where, parameters, "institutionId", filter.institutionId(),
                " AND c.institution_id = :institutionId");
        addFilter(where, parameters, "analystId", filter.analystId(), " AND c.analyst_id = :analystId");
        addEnumFilter(where, parameters, "direction", filter.direction(), " AND c.direction = :direction");
        addEnumFilter(where, parameters, "status", filter.status(), " AND c.status = :status");
        addEnumFilter(where, parameters, "dataMode", filter.dataMode(), " AND c.data_mode = :dataMode");
        if (filter.from() != null) {
            where.append(" AND c.event_time >= :from");
            parameters.addValue("from", utc(filter.from()));
        }
        if (filter.to() != null) {
            where.append(" AND c.event_time < :to");
            parameters.addValue("to", utc(filter.to()));
        }
        return new QueryParts(where.toString(), parameters);
    }

    private static void addFilter(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String name,
            String value,
            String clause) {
        if (value != null && !value.isBlank()) {
            where.append(clause);
            parameters.addValue(name, value);
        }
    }

    private static void addEnumFilter(
            StringBuilder where,
            MapSqlParameterSource parameters,
            String name,
            Enum<?> value,
            String clause) {
        if (value != null) {
            where.append(clause);
            parameters.addValue(name, value.name());
        }
    }

    private static String sortExpression(CallSortField field) {
        return switch (field) {
            case EVENT_TIME -> "c.event_time";
            case PROCESSING_TIME -> "c.processing_time";
            case CAPTURED_AT -> "c.captured_at";
        };
    }

    private void insertInstitutionIfAbsent(Institution institution) {
        insertIfMissing(
                "institutions", "institution_id", institution.id(),
                """
                        INSERT INTO institutions (
                            institution_id, canonical_name, slug, country, active, data_mode,
                            effective_at, captured_at, provenance_id
                        ) VALUES (
                            :id, :name, :slug, :country, :active, :dataMode,
                            :effectiveAt, :capturedAt, :provenanceId
                        )
                        """,
                parameters -> parameters
                        .addValue("id", institution.id())
                        .addValue("name", institution.canonicalName())
                        .addValue("slug", institution.slug())
                        .addValue("country", institution.country())
                        .addValue("active", institution.active())
                        .addValue("dataMode", institution.dataMode().name())
                        .addValue("effectiveAt", utc(institution.effectiveAt()))
                        .addValue("capturedAt", utc(institution.capturedAt()))
                        .addValue("provenanceId", institution.provenanceId()));
    }

    private void insertAnalystIfAbsent(Analyst analyst) {
        insertIfMissing(
                "analysts", "analyst_id", analyst.id(),
                """
                        INSERT INTO analysts (
                            analyst_id, canonical_name, active, data_mode, effective_at, captured_at, provenance_id
                        ) VALUES (
                            :id, :name, :active, :dataMode, :effectiveAt, :capturedAt, :provenanceId
                        )
                        """,
                parameters -> parameters
                        .addValue("id", analyst.id())
                        .addValue("name", analyst.canonicalName())
                        .addValue("active", analyst.active())
                        .addValue("dataMode", analyst.dataMode().name())
                        .addValue("effectiveAt", utc(analyst.effectiveAt()))
                        .addValue("capturedAt", utc(analyst.capturedAt()))
                        .addValue("provenanceId", analyst.provenanceId()));
    }

    private void insertAssetIfAbsent(Asset asset) {
        insertIfMissing(
                "assets", "asset_id", asset.id(),
                """
                        INSERT INTO assets (
                            asset_id, asset_type, canonical_name, ticker, primary_currency, active,
                            data_mode, effective_at, captured_at, provenance_id
                        ) VALUES (
                            :id, :type, :name, :ticker, :currency, :active,
                            :dataMode, :effectiveAt, :capturedAt, :provenanceId
                        )
                        """,
                parameters -> parameters
                        .addValue("id", asset.id())
                        .addValue("type", asset.type().name())
                        .addValue("name", asset.canonicalName())
                        .addValue("ticker", asset.ticker())
                        .addValue("currency", asset.primaryCurrency().getCurrencyCode())
                        .addValue("active", asset.active())
                        .addValue("dataMode", asset.dataMode().name())
                        .addValue("effectiveAt", utc(asset.effectiveAt()))
                        .addValue("capturedAt", utc(asset.capturedAt()))
                        .addValue("provenanceId", asset.provenanceId()));
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

    private void insertCall(AnalystCall call) {
        String insertSql = """
                INSERT INTO analyst_calls (
                    call_id, provider, provider_event_id, institution_id, analyst_id, asset_id,
                    event_time, processing_time, direction, original_rating, previous_target, target,
                    currency, target_date, source_reference_id, status, data_mode, captured_at, provenance_id
                ) VALUES (
                    :id, :provider, :providerEventId, :institutionId, :analystId, :assetId,
                    :eventTime, :processingTime, :direction, :originalRating, :previousTarget, :target,
                    :currency, :targetDate, :sourceReferenceId, :status, :dataMode, :capturedAt, :provenanceId
                )
                """;
        if (isPostgreSql()) {
            insertSql += " ON CONFLICT (provider, provider_event_id) DO NOTHING";
        }
        int inserted = jdbc.update(
                insertSql,
                new MapSqlParameterSource()
                        .addValue("id", call.id())
                        .addValue("provider", call.provider())
                        .addValue("providerEventId", call.providerEventId())
                        .addValue("institutionId", call.institution().id())
                        .addValue("analystId", call.analyst() == null ? null : call.analyst().id())
                        .addValue("assetId", call.asset().id())
                        .addValue("eventTime", utc(call.eventTime()))
                        .addValue("processingTime", utc(call.processingTime()))
                        .addValue("direction", call.direction().name())
                        .addValue("originalRating", call.originalRating())
                        .addValue("previousTarget", call.previousTarget())
                        .addValue("target", call.target())
                        .addValue("currency", call.currency() == null ? null : call.currency().getCurrencyCode())
                        .addValue("targetDate", call.targetDate())
                        .addValue("sourceReferenceId", call.sourceReference().id())
                        .addValue("status", call.status().name())
                        .addValue("dataMode", call.dataMode().name())
                        .addValue("capturedAt", utc(call.capturedAt()))
                        .addValue("provenanceId", call.provenanceId()));
        if (inserted != 1) {
            throw new IllegalStateException("claimed provider event did not create an analyst call");
        }
    }

    private void insertSnapshot(MarketSnapshot snapshot) {
        jdbc.update(
                """
                        INSERT INTO market_snapshots (
                            snapshot_id, call_id, asset_id, event_time, processing_time, asset_price,
                            spx, ndx, vix, treasury_2y, treasury_10y, real_yield, dxy, wti, gold,
                            volatility, distance_from_52w_high, distance_from_ath, immutable,
                            data_mode, captured_at, provenance_id
                        ) VALUES (
                            :id, :callId, :assetId, :eventTime, :processingTime, :assetPrice,
                            :spx, :ndx, :vix, :treasury2y, :treasury10y, :realYield, :dxy, :wti, :gold,
                            :volatility, :distanceFrom52WeekHigh, :distanceFromAth, TRUE,
                            :dataMode, :capturedAt, :provenanceId
                        )
                        """,
                new MapSqlParameterSource()
                        .addValue("id", snapshot.id())
                        .addValue("callId", snapshot.callId())
                        .addValue("assetId", snapshot.assetId())
                        .addValue("eventTime", utc(snapshot.eventTime()))
                        .addValue("processingTime", utc(snapshot.processingTime()))
                        .addValue("assetPrice", snapshot.assetPrice())
                        .addValue("spx", snapshot.spx())
                        .addValue("ndx", snapshot.ndx())
                        .addValue("vix", snapshot.vix())
                        .addValue("treasury2y", snapshot.treasury2y())
                        .addValue("treasury10y", snapshot.treasury10y())
                        .addValue("realYield", snapshot.realYield())
                        .addValue("dxy", snapshot.dxy())
                        .addValue("wti", snapshot.wti())
                        .addValue("gold", snapshot.gold())
                        .addValue("volatility", snapshot.volatility())
                        .addValue("distanceFrom52WeekHigh", snapshot.distanceFrom52WeekHigh())
                        .addValue("distanceFromAth", snapshot.distanceFromAth())
                        .addValue("dataMode", snapshot.dataMode().name())
                        .addValue("capturedAt", utc(snapshot.capturedAt()))
                        .addValue("provenanceId", snapshot.provenanceId()));
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

        String existsSql = "SELECT COUNT(*) FROM " + table + " WHERE " + idColumn + " = :id";
        Long count = jdbc.queryForObject(existsSql, parameters, Long.class);
        if (count == null || count == 0) {
            jdbc.update(insertSql, parameters);
        }
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

    private AnalystCallDetail mapDetail(ResultSet result, int rowNumber) throws SQLException {
        Institution institution = new Institution(
                result.getString("i_id"), result.getString("i_name"), result.getString("i_slug"),
                result.getString("i_country"), result.getBoolean("i_active"),
                DataMode.valueOf(result.getString("i_data_mode")), instant(result, "i_effective_at"),
                instant(result, "i_captured_at"), result.getString("i_provenance_id"));

        Analyst analyst = result.getString("an_id") == null ? null : new Analyst(
                result.getString("an_id"), result.getString("an_name"), result.getBoolean("an_active"),
                DataMode.valueOf(result.getString("an_data_mode")), instant(result, "an_effective_at"),
                instant(result, "an_captured_at"), result.getString("an_provenance_id"));

        Asset asset = new Asset(
                result.getString("a_id"), AssetType.valueOf(result.getString("a_type")), result.getString("a_name"),
                result.getString("a_ticker"), Currency.getInstance(result.getString("a_currency")),
                result.getBoolean("a_active"), DataMode.valueOf(result.getString("a_data_mode")),
                instant(result, "a_effective_at"), instant(result, "a_captured_at"),
                result.getString("a_provenance_id"));

        SourceDocument document = new SourceDocument(
                result.getString("sd_id"), SourceType.valueOf(result.getString("sd_type")),
                result.getString("sd_publisher"), result.getString("sd_title"), nullableUri(result.getString("sd_url")),
                nullableInstant(result, "sd_published_at"), result.getString("sd_provider"),
                result.getString("sd_external_id"), result.getString("sd_content_hash"),
                result.getString("sd_license_class"), DataMode.valueOf(result.getString("sd_data_mode")),
                instant(result, "sd_captured_at"), result.getString("sd_provenance_id"));

        SourceReference sourceReference = new SourceReference(
                result.getString("sr_id"), document, nullableInteger(result, "sr_page"),
                nullableLong(result, "sr_start_ms"), nullableLong(result, "sr_end_ms"),
                result.getString("sr_fragment"), result.getBigDecimal("sr_confidence"),
                result.getBoolean("sr_verified"), DataMode.valueOf(result.getString("sr_data_mode")),
                instant(result, "sr_captured_at"), result.getString("sr_provenance_id"));

        String currencyCode = result.getString("c_currency");
        AnalystCall call = new AnalystCall(
                result.getString("c_call_id"), result.getString("c_provider"), result.getString("c_provider_event_id"),
                institution, analyst, asset, instant(result, "c_event_time"), instant(result, "c_processing_time"),
                CallDirection.valueOf(result.getString("c_direction")), result.getString("c_original_rating"),
                result.getBigDecimal("c_previous_target"), result.getBigDecimal("c_target"),
                currencyCode == null ? null : Currency.getInstance(currencyCode), localDate(result, "c_target_date"),
                sourceReference, CallStatus.valueOf(result.getString("c_status")),
                DataMode.valueOf(result.getString("c_data_mode")), instant(result, "c_captured_at"),
                result.getString("c_provenance_id"));

        MarketSnapshot snapshot = result.getString("ms_id") == null ? null : new MarketSnapshot(
                result.getString("ms_id"), result.getString("ms_call_id"), result.getString("ms_asset_id"),
                instant(result, "ms_event_time"), instant(result, "ms_processing_time"),
                result.getBigDecimal("ms_asset_price"), result.getBigDecimal("ms_spx"), result.getBigDecimal("ms_ndx"),
                result.getBigDecimal("ms_vix"), result.getBigDecimal("ms_treasury_2y"),
                result.getBigDecimal("ms_treasury_10y"), result.getBigDecimal("ms_real_yield"),
                result.getBigDecimal("ms_dxy"), result.getBigDecimal("ms_wti"), result.getBigDecimal("ms_gold"),
                result.getBigDecimal("ms_volatility"), result.getBigDecimal("ms_distance_from_52w_high"),
                result.getBigDecimal("ms_distance_from_ath"), DataMode.valueOf(result.getString("ms_data_mode")),
                instant(result, "ms_captured_at"), result.getString("ms_provenance_id"));

        return new AnalystCallDetail(call, snapshot);
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

    private record QueryParts(String where, MapSqlParameterSource parameters) {
    }
}
