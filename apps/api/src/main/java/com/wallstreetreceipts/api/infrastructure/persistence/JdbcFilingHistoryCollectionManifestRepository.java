package com.wallstreetreceipts.api.infrastructure.persistence;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionComparison;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionGroup;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorMember;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorSelectionState;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.FilingOccurrence;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.OccurrenceProjection;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.OccurrenceSourceKind;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.SelectionCoverage;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;

@Repository
public class JdbcFilingHistoryCollectionManifestRepository
        implements FilingHistoryCollectionManifestRepository {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final String SELECT_MANIFEST = """
            SELECT manifest_id, schema_version, provider, product, policy_version,
                   root_capture_id, cik, root_captured_at, selection_sha256,
                   evidence_available_at, assembled_at,
                   advertised_descriptor_count, selected_descriptor_count,
                   selection_coverage, source_occurrence_count,
                   distinct_accession_count, single_source_group_count,
                   exact_agreement_group_count, canonical_conflict_group_count
            FROM sec_filing_history_collection_manifests
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final FilingCatalogCaptureRepository rootRepository;
    private final HistoricalFilingSegmentCaptureRepository segmentRepository;
    private volatile Boolean postgreSql;

    public JdbcFilingHistoryCollectionManifestRepository(
            NamedParameterJdbcTemplate jdbc,
            FilingCatalogCaptureRepository rootRepository,
            HistoricalFilingSegmentCaptureRepository segmentRepository) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.rootRepository = Objects.requireNonNull(rootRepository, "rootRepository");
        this.segmentRepository = Objects.requireNonNull(segmentRepository, "segmentRepository");
    }

    @Override
    @Transactional
    public FilingHistoryCollectionManifestAppendOutcome append(
            FilingHistoryCollectionManifest manifest) {
        Objects.requireNonNull(manifest, "manifest must not be null");
        FilingHistoryCollectionManifest verified = reassemble(
                manifest.rootCaptureId(), manifest.descriptors(), manifest.assembledAt());
        if (!verified.equals(manifest)) {
            throw new IllegalArgumentException(
                    "filing history collection manifest does not match durable source captures");
        }

        Optional<FilingHistoryCollectionManifest> natural = findByNaturalIdentity(manifest);
        if (natural.isPresent()) {
            return replayOrConflict(natural.orElseThrow(), manifest, "natural selection identity");
        }
        Optional<FilingHistoryCollectionManifest> byId = findByManifestId(manifest.manifestId());
        if (byId.isPresent()) {
            return replayOrConflict(byId.orElseThrow(), manifest, "manifestId");
        }

        int inserted = insertManifest(manifest);
        if (inserted == 0) {
            Optional<FilingHistoryCollectionManifest> racedNatural =
                    findByNaturalIdentity(manifest);
            if (racedNatural.isPresent()) {
                return replayOrConflict(
                        racedNatural.orElseThrow(), manifest, "natural selection identity");
            }
            Optional<FilingHistoryCollectionManifest> racedId =
                    findByManifestId(manifest.manifestId());
            if (racedId.isPresent()) {
                return replayOrConflict(racedId.orElseThrow(), manifest, "manifestId");
            }
            throw new IllegalArgumentException(
                    "filing history collection insert conflicted without a replayable row");
        }

        Map<String, HistoricalFilingSegmentCapture> selected = selectedCaptures(manifest);
        insertDescriptors(manifest, selected);
        insertAccessionGroups(manifest);
        insertOccurrences(manifest);
        FilingHistoryCollectionManifest persisted = findByManifestId(manifest.manifestId())
                .orElseThrow(() -> new IllegalStateException(
                        "inserted filing history collection could not be reconstructed"));
        if (!persisted.equals(manifest)) {
            throw new IllegalArgumentException(
                    "inserted filing history collection did not round-trip exactly");
        }
        return new FilingHistoryCollectionManifestAppendOutcome(Status.INSERTED, persisted);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FilingHistoryCollectionManifest> findByManifestId(String manifestId) {
        requireSha256(manifestId, "manifestId");
        return findOne(
                SELECT_MANIFEST + " WHERE manifest_id = :manifestId",
                new MapSqlParameterSource("manifestId", manifestId));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FilingHistoryCollectionManifest> findByManifestIdAtOrBefore(
            String manifestId,
            Instant evaluationAsOf) {
        requireSha256(manifestId, "manifestId");
        PersistentInstant.requireMicrosecondPrecision(evaluationAsOf, "evaluationAsOf");
        return findOne(
                SELECT_MANIFEST + """
                         WHERE manifest_id = :manifestId
                           AND assembled_at <= :evaluationAsOf
                        """,
                new MapSqlParameterSource()
                        .addValue("manifestId", manifestId)
                        .addValue("evaluationAsOf", utc(evaluationAsOf)));
    }

    @Override
    @Transactional(readOnly = true)
    public long count() {
        Long count = jdbc.getJdbcOperations().queryForObject(
                "SELECT COUNT(*) FROM sec_filing_history_collection_manifests",
                Long.class);
        return count == null ? 0 : count;
    }

    private Optional<FilingHistoryCollectionManifest> findByNaturalIdentity(
            FilingHistoryCollectionManifest manifest) {
        return findOne(
                SELECT_MANIFEST + """
                         WHERE root_capture_id = :rootCaptureId
                           AND policy_version = :policyVersion
                           AND selection_sha256 = :selectionSha256
                        """,
                new MapSqlParameterSource()
                        .addValue("rootCaptureId", manifest.rootCaptureId())
                        .addValue("policyVersion", FilingHistoryCollectionManifest.POLICY_VERSION)
                        .addValue("selectionSha256", manifest.selectionSha256()));
    }

    private Optional<FilingHistoryCollectionManifest> findOne(
            String sql,
            MapSqlParameterSource parameters) {
        List<ManifestRow> rows = jdbc.query(sql, parameters, this::mapManifest);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        if (rows.size() != 1) {
            throw new IllegalStateException(
                    "filing history collection query returned an ambiguous manifest");
        }
        ManifestRow row = rows.getFirst();
        List<StoredDescriptor> descriptors = readDescriptors(row.manifestId());
        requireContiguousOrdinals(
                descriptors.stream().map(StoredDescriptor::descriptorOrdinal).toList(),
                "filing history collection descriptor ordinals");
        if (descriptors.size() != row.advertisedDescriptorCount()) {
            throw new IllegalStateException(
                    "filing history collection descriptor count does not match its manifest");
        }

        FilingCatalogCapture root = rootRepository.findByCaptureId(row.rootCaptureId())
                .orElseThrow(() -> new IllegalStateException(
                        "filing history collection root capture could not be reconstructed"));
        List<HistoricalFilingSegmentCapture> selected = descriptors.stream()
                .filter(descriptor -> descriptor.selectionState()
                        == DescriptorSelectionState.SELECTED_EXACT_CAPTURE)
                .map(descriptor -> segmentRepository
                        .findByCaptureId(descriptor.selectedSegmentCaptureId())
                        .orElseThrow(() -> new IllegalStateException(
                                "filing history collection segment capture could not be reconstructed")))
                .toList();
        FilingHistoryCollectionManifest reconstructed =
                FilingHistoryCollectionManifest.assemble(root, selected, row.assembledAt());
        verifyManifestRow(row, reconstructed);
        verifyDescriptors(descriptors, reconstructed, selected);
        verifyAccessionGroups(readAccessionGroups(row.manifestId()), reconstructed);
        verifyOccurrences(readOccurrences(row.manifestId()), reconstructed);
        return Optional.of(reconstructed);
    }

    private FilingHistoryCollectionManifest reassemble(
            String rootCaptureId,
            List<DescriptorMember> descriptors,
            Instant assembledAt) {
        FilingCatalogCapture root = rootRepository.findByCaptureId(rootCaptureId)
                .orElseThrow(() -> new IllegalStateException(
                        "filing history collection root capture could not be reconstructed"));
        List<HistoricalFilingSegmentCapture> selected = descriptors.stream()
                .filter(member -> member.selectionState()
                        == DescriptorSelectionState.SELECTED_EXACT_CAPTURE)
                .map(member -> segmentRepository.findByCaptureId(
                        member.selectedSegmentCaptureId()).orElseThrow(() ->
                                new IllegalStateException(
                                        "filing history collection segment capture could not be reconstructed")))
                .toList();
        return FilingHistoryCollectionManifest.assemble(root, selected, assembledAt);
    }

    private Map<String, HistoricalFilingSegmentCapture> selectedCaptures(
            FilingHistoryCollectionManifest manifest) {
        Map<String, HistoricalFilingSegmentCapture> result = new HashMap<>();
        for (DescriptorMember member : manifest.descriptors()) {
            if (member.selectionState() == DescriptorSelectionState.SELECTED_EXACT_CAPTURE) {
                HistoricalFilingSegmentCapture capture = segmentRepository
                        .findByCaptureId(member.selectedSegmentCaptureId())
                        .orElseThrow(() -> new IllegalStateException(
                                "filing history collection segment capture could not be reconstructed"));
                result.put(member.selectedSegmentCaptureId(), capture);
            }
        }
        return Map.copyOf(result);
    }

    private int insertManifest(FilingHistoryCollectionManifest manifest) {
        String sql = """
                INSERT INTO sec_filing_history_collection_manifests (
                    manifest_id, schema_version, provider, product, policy_version,
                    root_capture_id, cik, root_captured_at, selection_sha256,
                    evidence_available_at, assembled_at,
                    advertised_descriptor_count, selected_descriptor_count,
                    selection_coverage, source_occurrence_count,
                    distinct_accession_count, single_source_group_count,
                    exact_agreement_group_count, canonical_conflict_group_count,
                    immutable
                ) VALUES (
                    :manifestId, :schemaVersion, :provider, :product, :policyVersion,
                    :rootCaptureId, :cik, :rootCapturedAt, :selectionSha256,
                    :evidenceAvailableAt, :assembledAt,
                    :advertisedDescriptorCount, :selectedDescriptorCount,
                    :selectionCoverage, :sourceOccurrenceCount,
                    :distinctAccessionCount, :singleSourceGroupCount,
                    :exactAgreementGroupCount, :canonicalConflictGroupCount,
                    TRUE
                )
                """;
        if (isPostgreSql()) {
            sql += " ON CONFLICT DO NOTHING";
        }
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("manifestId", manifest.manifestId())
                .addValue("schemaVersion", FilingHistoryCollectionManifest.SCHEMA_VERSION)
                .addValue("provider", FilingHistoryCollectionManifest.PROVIDER)
                .addValue("product", FilingHistoryCollectionManifest.PRODUCT)
                .addValue("policyVersion", FilingHistoryCollectionManifest.POLICY_VERSION)
                .addValue("rootCaptureId", manifest.rootCaptureId())
                .addValue("cik", manifest.cik())
                .addValue("rootCapturedAt", utc(manifest.rootCapturedAt()))
                .addValue("selectionSha256", manifest.selectionSha256())
                .addValue("evidenceAvailableAt", utc(manifest.evidenceAvailableAt()))
                .addValue("assembledAt", utc(manifest.assembledAt()))
                .addValue("advertisedDescriptorCount", manifest.advertisedDescriptorCount())
                .addValue("selectedDescriptorCount", manifest.selectedDescriptorCount())
                .addValue("selectionCoverage", manifest.selectionCoverage().name())
                .addValue("sourceOccurrenceCount", manifest.sourceOccurrenceCount())
                .addValue("distinctAccessionCount", manifest.distinctAccessionCount())
                .addValue("singleSourceGroupCount", manifest.singleAccessionCount())
                .addValue("exactAgreementGroupCount", manifest.agreeingAccessionCount())
                .addValue("canonicalConflictGroupCount", manifest.conflictingAccessionCount()));
    }

    private void insertDescriptors(
            FilingHistoryCollectionManifest manifest,
            Map<String, HistoricalFilingSegmentCapture> selectedCaptures) {
        SqlParameterSource[] batch = new SqlParameterSource[manifest.descriptors().size()];
        for (int ordinal = 0; ordinal < manifest.descriptors().size(); ordinal++) {
            DescriptorMember member = manifest.descriptors().get(ordinal);
            HistoricalFilingSegmentDescriptor descriptor = member.descriptor();
            HistoricalFilingSegmentCapture selected = member.selectedSegmentCaptureId() == null
                    ? null : selectedCaptures.get(member.selectedSegmentCaptureId());
            batch[ordinal] = new MapSqlParameterSource()
                    .addValue("manifestId", manifest.manifestId())
                    .addValue("descriptorOrdinal", member.descriptorOrdinal())
                    .addValue("rootCaptureId", manifest.rootCaptureId())
                    .addValue("cik", manifest.cik())
                    .addValue("rootCapturedAt", utc(manifest.rootCapturedAt()))
                    .addValue("fileName", descriptor.fileName())
                    .addValue("advertisedFilingCount", descriptor.advertisedFilingCount())
                    .addValue("advertisedFilingFrom", descriptor.advertisedFilingFrom())
                    .addValue("advertisedFilingTo", descriptor.advertisedFilingTo())
                    .addValue("selectionState", member.selectionState().name())
                    .addValue("selectedSegmentCaptureId", member.selectedSegmentCaptureId())
                    .addValue("selectedSegmentCapturedAt", selected == null
                            ? null : utc(selected.segment().capturedAt()));
        }
        if (batch.length > 0) {
            jdbc.batchUpdate("""
                    INSERT INTO sec_filing_history_collection_descriptors (
                        manifest_id, descriptor_ordinal, root_capture_id, cik,
                        root_captured_at, file_name, advertised_filing_count,
                        advertised_filing_from, advertised_filing_to,
                        selection_state, selected_segment_capture_id,
                        selected_segment_captured_at
                    ) VALUES (
                        :manifestId, :descriptorOrdinal, :rootCaptureId, :cik,
                        :rootCapturedAt, :fileName, :advertisedFilingCount,
                        :advertisedFilingFrom, :advertisedFilingTo,
                        :selectionState, :selectedSegmentCaptureId,
                        :selectedSegmentCapturedAt
                    )
                    """, batch);
        }
    }

    private void insertAccessionGroups(FilingHistoryCollectionManifest manifest) {
        SqlParameterSource[] batch = new SqlParameterSource[manifest.accessionGroups().size()];
        for (int ordinal = 0; ordinal < manifest.accessionGroups().size(); ordinal++) {
            AccessionGroup group = manifest.accessionGroups().get(ordinal);
            batch[ordinal] = new MapSqlParameterSource()
                    .addValue("manifestId", manifest.manifestId())
                    .addValue("groupOrdinal", group.groupOrdinal())
                    .addValue("accessionNumber", group.accessionNumber())
                    .addValue("occurrenceCount", group.occurrenceCount())
                    .addValue("distinctProjectionCount", group.distinctProjectionCount())
                    .addValue("comparison", group.comparison().name());
        }
        if (batch.length > 0) {
            jdbc.batchUpdate("""
                    INSERT INTO sec_filing_history_collection_accession_groups (
                        manifest_id, group_ordinal, accession_number,
                        occurrence_count, distinct_projection_count, comparison
                    ) VALUES (
                        :manifestId, :groupOrdinal, :accessionNumber,
                        :occurrenceCount, :distinctProjectionCount, :comparison
                    )
                    """, batch);
        }
    }

    private void insertOccurrences(FilingHistoryCollectionManifest manifest) {
        SqlParameterSource[] batch = new SqlParameterSource[manifest.occurrences().size()];
        for (int ordinal = 0; ordinal < manifest.occurrences().size(); ordinal++) {
            FilingOccurrence occurrence = manifest.occurrences().get(ordinal);
            OccurrenceProjection projection = occurrence.projection();
            boolean rootSource = occurrence.sourceKind() == OccurrenceSourceKind.ROOT_RECENT;
            batch[ordinal] = new MapSqlParameterSource()
                    .addValue("manifestId", manifest.manifestId())
                    .addValue("occurrenceOrdinal", occurrence.occurrenceOrdinal())
                    .addValue("sourceKind", occurrence.sourceKind().name())
                    .addValue("rootSourceCaptureId", rootSource
                            ? manifest.rootCaptureId() : null)
                    .addValue("descriptorOrdinal", occurrence.descriptorOrdinal())
                    .addValue("segmentSourceCaptureId", occurrence.segmentCaptureId())
                    .addValue("sourceRowOrdinal", occurrence.sourceOrdinal())
                    .addValue("providerEventId", projection.providerEventId())
                    .addValue("accessionNumber", projection.accessionNumber())
                    .addValue("form", projection.form())
                    .addValue("filingDate", projection.filingDate())
                    .addValue("reportDate", projection.reportDate())
                    .addValue("acceptedAt", utc(projection.acceptedAt()))
                    .addValue("primaryDocumentUri", projection.primaryDocumentUri() == null
                            ? null : projection.primaryDocumentUri().toASCIIString())
                    .addValue("projectionSha256", occurrence.projectionSha256());
        }
        if (batch.length > 0) {
            jdbc.batchUpdate("""
                    INSERT INTO sec_filing_history_collection_occurrences (
                        manifest_id, occurrence_ordinal, source_kind,
                        root_source_capture_id, descriptor_ordinal,
                        segment_source_capture_id, source_row_ordinal,
                        provider_event_id, accession_number, form, filing_date,
                        report_date, accepted_at, primary_document_uri,
                        projection_sha256
                    ) VALUES (
                        :manifestId, :occurrenceOrdinal, :sourceKind,
                        :rootSourceCaptureId, :descriptorOrdinal,
                        :segmentSourceCaptureId, :sourceRowOrdinal,
                        :providerEventId, :accessionNumber, :form, :filingDate,
                        :reportDate, :acceptedAt, :primaryDocumentUri,
                        :projectionSha256
                    )
                    """, batch);
        }
    }

    private List<StoredDescriptor> readDescriptors(String manifestId) {
        return jdbc.query("""
                SELECT descriptor_ordinal, root_capture_id, cik, root_captured_at,
                       file_name, advertised_filing_count, advertised_filing_from,
                       advertised_filing_to, selection_state,
                       selected_segment_capture_id, selected_segment_captured_at
                FROM sec_filing_history_collection_descriptors
                WHERE manifest_id = :manifestId
                ORDER BY descriptor_ordinal ASC
                """, new MapSqlParameterSource("manifestId", manifestId),
                (result, rowNumber) -> new StoredDescriptor(
                        result.getInt("descriptor_ordinal"),
                        result.getString("root_capture_id"),
                        result.getString("cik"),
                        instant(result, "root_captured_at"),
                        new HistoricalFilingSegmentDescriptor(
                                result.getString("file_name"),
                                result.getLong("advertised_filing_count"),
                                result.getObject("advertised_filing_from", LocalDate.class),
                                result.getObject("advertised_filing_to", LocalDate.class)),
                        DescriptorSelectionState.valueOf(
                                result.getString("selection_state")),
                        result.getString("selected_segment_capture_id"),
                        nullableInstant(result, "selected_segment_captured_at")));
    }

    private List<StoredAccessionGroup> readAccessionGroups(String manifestId) {
        return jdbc.query("""
                SELECT group_ordinal, accession_number, occurrence_count,
                       distinct_projection_count, comparison
                FROM sec_filing_history_collection_accession_groups
                WHERE manifest_id = :manifestId
                ORDER BY group_ordinal ASC
                """, new MapSqlParameterSource("manifestId", manifestId),
                (result, rowNumber) -> new StoredAccessionGroup(
                        result.getInt("group_ordinal"),
                        result.getString("accession_number"),
                        result.getLong("occurrence_count"),
                        result.getLong("distinct_projection_count"),
                        AccessionComparison.valueOf(result.getString("comparison"))));
    }

    private List<StoredOccurrence> readOccurrences(String manifestId) {
        return jdbc.query("""
                SELECT occurrence_ordinal, source_kind, root_source_capture_id,
                       descriptor_ordinal, segment_source_capture_id,
                       source_row_ordinal, provider_event_id, accession_number,
                       form, filing_date, report_date, accepted_at,
                       primary_document_uri, projection_sha256
                FROM sec_filing_history_collection_occurrences
                WHERE manifest_id = :manifestId
                ORDER BY occurrence_ordinal ASC
                """, new MapSqlParameterSource("manifestId", manifestId),
                (result, rowNumber) -> new StoredOccurrence(
                        result.getInt("occurrence_ordinal"),
                        OccurrenceSourceKind.valueOf(result.getString("source_kind")),
                        result.getString("root_source_capture_id"),
                        nullableInteger(result, "descriptor_ordinal"),
                        result.getString("segment_source_capture_id"),
                        result.getInt("source_row_ordinal"),
                        new OccurrenceProjection(
                                result.getString("provider_event_id"),
                                result.getString("accession_number"),
                                result.getString("form"),
                                result.getObject("filing_date", LocalDate.class),
                                result.getObject("report_date", LocalDate.class),
                                instant(result, "accepted_at"),
                                nullableUri(result, "primary_document_uri")),
                        result.getString("projection_sha256")));
    }

    private ManifestRow mapManifest(ResultSet result, int rowNumber) throws SQLException {
        return new ManifestRow(
                result.getString("manifest_id"),
                result.getString("schema_version"),
                result.getString("provider"),
                result.getString("product"),
                result.getString("policy_version"),
                result.getString("root_capture_id"),
                result.getString("cik"),
                instant(result, "root_captured_at"),
                result.getString("selection_sha256"),
                instant(result, "evidence_available_at"),
                instant(result, "assembled_at"),
                result.getLong("advertised_descriptor_count"),
                result.getLong("selected_descriptor_count"),
                SelectionCoverage.valueOf(result.getString("selection_coverage")),
                result.getLong("source_occurrence_count"),
                result.getLong("distinct_accession_count"),
                result.getLong("single_source_group_count"),
                result.getLong("exact_agreement_group_count"),
                result.getLong("canonical_conflict_group_count"));
    }

    private static void verifyManifestRow(
            ManifestRow row,
            FilingHistoryCollectionManifest manifest) {
        if (!row.manifestId().equals(manifest.manifestId())
                || !row.schemaVersion().equals(FilingHistoryCollectionManifest.SCHEMA_VERSION)
                || !row.provider().equals(FilingHistoryCollectionManifest.PROVIDER)
                || !row.product().equals(FilingHistoryCollectionManifest.PRODUCT)
                || !row.policyVersion().equals(FilingHistoryCollectionManifest.POLICY_VERSION)
                || !row.rootCaptureId().equals(manifest.rootCaptureId())
                || !row.cik().equals(manifest.cik())
                || !row.rootCapturedAt().equals(manifest.rootCapturedAt())
                || !row.selectionSha256().equals(manifest.selectionSha256())
                || !row.evidenceAvailableAt().equals(manifest.evidenceAvailableAt())
                || !row.assembledAt().equals(manifest.assembledAt())
                || row.advertisedDescriptorCount() != manifest.advertisedDescriptorCount()
                || row.selectedDescriptorCount() != manifest.selectedDescriptorCount()
                || row.selectionCoverage() != manifest.selectionCoverage()
                || row.sourceOccurrenceCount() != manifest.sourceOccurrenceCount()
                || row.distinctAccessionCount() != manifest.distinctAccessionCount()
                || row.singleSourceGroupCount() != manifest.singleAccessionCount()
                || row.exactAgreementGroupCount() != manifest.agreeingAccessionCount()
                || row.canonicalConflictGroupCount() != manifest.conflictingAccessionCount()) {
            throw new IllegalStateException(
                    "filing history collection manifest summary does not match its sources");
        }
    }

    private static void verifyDescriptors(
            List<StoredDescriptor> stored,
            FilingHistoryCollectionManifest manifest,
            List<HistoricalFilingSegmentCapture> selectedCaptures) {
        Map<String, Instant> selectedTimes = new HashMap<>();
        for (HistoricalFilingSegmentCapture capture : selectedCaptures) {
            selectedTimes.put(capture.captureId(), capture.segment().capturedAt());
        }
        List<DescriptorMember> expected = manifest.descriptors();
        for (int index = 0; index < expected.size(); index++) {
            StoredDescriptor row = stored.get(index);
            DescriptorMember member = expected.get(index);
            Instant expectedSelectedAt = member.selectedSegmentCaptureId() == null
                    ? null : selectedTimes.get(member.selectedSegmentCaptureId());
            if (row.descriptorOrdinal() != member.descriptorOrdinal()
                    || !row.rootCaptureId().equals(manifest.rootCaptureId())
                    || !row.cik().equals(manifest.cik())
                    || !row.rootCapturedAt().equals(manifest.rootCapturedAt())
                    || !row.descriptor().equals(member.descriptor())
                    || row.selectionState() != member.selectionState()
                    || !Objects.equals(
                            row.selectedSegmentCaptureId(),
                            member.selectedSegmentCaptureId())
                    || !Objects.equals(row.selectedSegmentCapturedAt(), expectedSelectedAt)) {
                throw new IllegalStateException(
                        "filing history collection descriptor does not match its source");
            }
        }
    }

    private static void verifyAccessionGroups(
            List<StoredAccessionGroup> stored,
            FilingHistoryCollectionManifest manifest) {
        requireContiguousOrdinals(
                stored.stream().map(StoredAccessionGroup::groupOrdinal).toList(),
                "filing history collection accession group ordinals");
        if (stored.size() != manifest.accessionGroups().size()) {
            throw new IllegalStateException(
                    "filing history collection accession group count does not match");
        }
        for (int index = 0; index < stored.size(); index++) {
            StoredAccessionGroup row = stored.get(index);
            AccessionGroup group = manifest.accessionGroups().get(index);
            if (row.groupOrdinal() != group.groupOrdinal()
                    || !row.accessionNumber().equals(group.accessionNumber())
                    || row.occurrenceCount() != group.occurrenceCount()
                    || row.distinctProjectionCount() != group.distinctProjectionCount()
                    || row.comparison() != group.comparison()) {
                throw new IllegalStateException(
                        "filing history collection accession group does not match occurrences");
            }
        }
    }

    private static void verifyOccurrences(
            List<StoredOccurrence> stored,
            FilingHistoryCollectionManifest manifest) {
        requireContiguousOrdinals(
                stored.stream().map(StoredOccurrence::occurrenceOrdinal).toList(),
                "filing history collection occurrence ordinals");
        if (stored.size() != manifest.occurrences().size()) {
            throw new IllegalStateException(
                    "filing history collection occurrence count does not match");
        }
        for (int index = 0; index < stored.size(); index++) {
            StoredOccurrence row = stored.get(index);
            FilingOccurrence occurrence = manifest.occurrences().get(index);
            String expectedRootSource = occurrence.sourceKind() == OccurrenceSourceKind.ROOT_RECENT
                    ? manifest.rootCaptureId() : null;
            if (row.occurrenceOrdinal() != occurrence.occurrenceOrdinal()
                    || row.sourceKind() != occurrence.sourceKind()
                    || !Objects.equals(row.rootSourceCaptureId(), expectedRootSource)
                    || !Objects.equals(row.descriptorOrdinal(), occurrence.descriptorOrdinal())
                    || !Objects.equals(
                            row.segmentSourceCaptureId(), occurrence.segmentCaptureId())
                    || row.sourceRowOrdinal() != occurrence.sourceOrdinal()
                    || !row.projection().equals(occurrence.projection())
                    || !row.projectionSha256().equals(occurrence.projectionSha256())) {
                throw new IllegalStateException(
                        "filing history collection occurrence does not match its source");
            }
        }
    }

    private FilingHistoryCollectionManifestAppendOutcome replayOrConflict(
            FilingHistoryCollectionManifest existing,
            FilingHistoryCollectionManifest proposed,
            String identity) {
        if (sameContent(existing, proposed)) {
            return new FilingHistoryCollectionManifestAppendOutcome(
                    Status.IDENTICAL_REPLAY, existing);
        }
        throw new IllegalArgumentException(
                "conflicting filing history collection manifest for " + identity);
    }

    private static boolean sameContent(
            FilingHistoryCollectionManifest left,
            FilingHistoryCollectionManifest right) {
        return left.manifestId().equals(right.manifestId())
                && left.selectionSha256().equals(right.selectionSha256())
                && left.rootCaptureId().equals(right.rootCaptureId())
                && left.rootCapturedAt().equals(right.rootCapturedAt())
                && left.cik().equals(right.cik())
                && left.evidenceAvailableAt().equals(right.evidenceAvailableAt())
                && left.selectionCoverage() == right.selectionCoverage()
                && left.descriptors().equals(right.descriptors())
                && left.occurrences().equals(right.occurrences())
                && left.accessionGroups().equals(right.accessionGroups());
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

    private static void requireSha256(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
    }

    private static void requireContiguousOrdinals(
            List<Integer> ordinals,
            String field) {
        for (int expected = 0; expected < ordinals.size(); expected++) {
            if (ordinals.get(expected) != expected) {
                throw new IllegalStateException(field + " are not contiguous");
            }
        }
    }

    private static Integer nullableInteger(ResultSet result, String column)
            throws SQLException {
        int value = result.getInt(column);
        return result.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet result, String column)
            throws SQLException {
        Instant value = nullableInstant(result, column);
        if (value == null) {
            throw new SQLException(column + " must not be null");
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

    private static URI nullableUri(ResultSet result, String column)
            throws SQLException {
        String value = result.getString(column);
        return value == null ? null : URI.create(value);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    private record ManifestRow(
            String manifestId,
            String schemaVersion,
            String provider,
            String product,
            String policyVersion,
            String rootCaptureId,
            String cik,
            Instant rootCapturedAt,
            String selectionSha256,
            Instant evidenceAvailableAt,
            Instant assembledAt,
            long advertisedDescriptorCount,
            long selectedDescriptorCount,
            SelectionCoverage selectionCoverage,
            long sourceOccurrenceCount,
            long distinctAccessionCount,
            long singleSourceGroupCount,
            long exactAgreementGroupCount,
            long canonicalConflictGroupCount) {
    }

    private record StoredDescriptor(
            int descriptorOrdinal,
            String rootCaptureId,
            String cik,
            Instant rootCapturedAt,
            HistoricalFilingSegmentDescriptor descriptor,
            DescriptorSelectionState selectionState,
            String selectedSegmentCaptureId,
            Instant selectedSegmentCapturedAt) {
    }

    private record StoredAccessionGroup(
            int groupOrdinal,
            String accessionNumber,
            long occurrenceCount,
            long distinctProjectionCount,
            AccessionComparison comparison) {
    }

    private record StoredOccurrence(
            int occurrenceOrdinal,
            OccurrenceSourceKind sourceKind,
            String rootSourceCaptureId,
            Integer descriptorOrdinal,
            String segmentSourceCaptureId,
            int sourceRowOrdinal,
            OccurrenceProjection projection,
            String projectionSha256) {
    }
}
