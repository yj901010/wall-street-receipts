package com.wallstreetreceipts.api.domain.filing;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.wallstreetreceipts.api.domain.PersistentInstant;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/**
 * Immutable root-relative manifest assembled from exact durable SEC captures.
 * Source occurrences remain distinct even when their accession projections agree.
 */
public final class FilingHistoryCollectionManifest {

    public static final String SCHEMA_VERSION = "1.0.0";
    public static final String PROVIDER = "sec-edgar";
    public static final String PRODUCT =
            "edgar-submissions-root-relative-collection-manifest";
    public static final String POLICY_VERSION =
            "SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1";

    private static final String ROOT_PRODUCT = "edgar-submissions-api";
    private static final String ROOT_PARSER_VERSION = "SEC_SUBMISSIONS_CATALOG_V2";
    private static final String SEGMENT_PRODUCT =
            "edgar-submissions-historical-segment-api";
    private static final String SEGMENT_PARSER_VERSION =
            "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1";
    private static final String MANIFEST_IDENTITY_VERSION =
            "SEC_FILING_HISTORY_COLLECTION_MANIFEST_ID_V1";
    private static final String SELECTION_IDENTITY_VERSION =
            "SEC_FILING_HISTORY_COLLECTION_SELECTION_V1";
    private static final String PROJECTION_IDENTITY_VERSION =
            "SEC_FILING_HISTORY_OCCURRENCE_PROJECTION_V1";
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern SEC_ACCESSION_NUMBER =
            Pattern.compile("[0-9]{10}-[0-9]{2}-[0-9]{6}");
    private static final HexFormat LOWERCASE_HEX = HexFormat.of();

    private final String manifestId;
    private final String selectionSha256;
    private final String rootCaptureId;
    private final Instant rootCapturedAt;
    private final String cik;
    private final Instant evidenceAvailableAt;
    private final Instant assembledAt;
    private final SelectionCoverage selectionCoverage;
    private final List<DescriptorMember> descriptors;
    private final List<FilingOccurrence> occurrences;
    private final List<AccessionGroup> accessionGroups;

    private FilingHistoryCollectionManifest(
            String manifestId,
            String selectionSha256,
            String rootCaptureId,
            Instant rootCapturedAt,
            String cik,
            Instant evidenceAvailableAt,
            Instant assembledAt,
            SelectionCoverage selectionCoverage,
            List<DescriptorMember> descriptors,
            List<FilingOccurrence> occurrences,
            List<AccessionGroup> accessionGroups) {
        requireSha256(manifestId, "manifestId");
        requireSha256(selectionSha256, "selectionSha256");
        requireSha256(rootCaptureId, "rootCaptureId");
        PersistentInstant.requireMicrosecondPrecision(
                rootCapturedAt, "rootCapturedAt");
        FilingCatalog.requireCik(cik);
        PersistentInstant.requireMicrosecondPrecision(
                evidenceAvailableAt, "evidenceAvailableAt");
        PersistentInstant.requireMicrosecondPrecision(assembledAt, "assembledAt");
        if (assembledAt.isBefore(evidenceAvailableAt)) {
            throw new IllegalArgumentException(
                    "assembledAt must not precede evidenceAvailableAt");
        }
        this.manifestId = manifestId;
        this.selectionSha256 = selectionSha256;
        this.rootCaptureId = rootCaptureId;
        this.rootCapturedAt = rootCapturedAt;
        this.cik = cik;
        this.evidenceAvailableAt = evidenceAvailableAt;
        this.assembledAt = assembledAt;
        this.selectionCoverage = Objects.requireNonNull(
                selectionCoverage, "selectionCoverage must not be null");
        this.descriptors = List.copyOf(descriptors);
        this.occurrences = List.copyOf(occurrences);
        this.accessionGroups = List.copyOf(accessionGroups);
    }

    public static FilingHistoryCollectionManifest assemble(
            FilingCatalogCapture durableRoot,
            List<HistoricalFilingSegmentCapture> selectedCaptures,
            Instant assembledAt) {
        Objects.requireNonNull(durableRoot, "durableRoot must not be null");
        Objects.requireNonNull(selectedCaptures, "selectedCaptures must not be null");
        PersistentInstant.requireMicrosecondPrecision(assembledAt, "assembledAt");
        requireExactDurableRoot(durableRoot);

        FilingCatalog root = durableRoot.catalog();
        Map<Integer, HistoricalFilingSegmentCapture> selectedByOrdinal =
                new TreeMap<>();
        Set<String> selectedCaptureIds = new HashSet<>();
        Instant evidenceAvailableAt = root.capturedAt();
        for (HistoricalFilingSegmentCapture selectedCapture : selectedCaptures) {
            Objects.requireNonNull(
                    selectedCapture, "selectedCaptures must not contain null");
            if (!selectedCaptureIds.add(selectedCapture.captureId())) {
                throw new IllegalArgumentException(
                        "selected segment captureId must be unique");
            }
            requireExactSelectedCapture(durableRoot, selectedCapture);
            int ordinal = selectedCapture.segment().descriptorOrdinal();
            if (selectedByOrdinal.putIfAbsent(ordinal, selectedCapture) != null) {
                throw new IllegalArgumentException(
                        "selected descriptorOrdinal must be unique");
            }
            if (selectedCapture.segment().capturedAt().isAfter(evidenceAvailableAt)) {
                evidenceAvailableAt = selectedCapture.segment().capturedAt();
            }
        }
        if (assembledAt.isBefore(evidenceAvailableAt)) {
            throw new IllegalArgumentException(
                    "assembledAt must not precede evidenceAvailableAt");
        }

        List<DescriptorMember> descriptors = descriptorMembers(
                root, selectedByOrdinal);
        SelectionCoverage selectionCoverage = selectionCoverage(
                descriptors.size(), selectedByOrdinal.size());
        List<FilingOccurrence> occurrences = occurrences(
                root, selectedByOrdinal);
        List<AccessionGroup> accessionGroups = accessionGroups(occurrences);
        String selectionSha256 = selectionSha256(
                durableRoot.captureId(), descriptors);
        String manifestId = manifestId(durableRoot.captureId(), selectionSha256);

        return new FilingHistoryCollectionManifest(
                manifestId,
                selectionSha256,
                durableRoot.captureId(),
                root.capturedAt(),
                root.cik(),
                evidenceAvailableAt,
                assembledAt,
                selectionCoverage,
                descriptors,
                occurrences,
                accessionGroups);
    }

    public String manifestId() {
        return manifestId;
    }

    public String schemaVersion() {
        return SCHEMA_VERSION;
    }

    public String provider() {
        return PROVIDER;
    }

    public String product() {
        return PRODUCT;
    }

    public String policyVersion() {
        return POLICY_VERSION;
    }

    public String selectionSha256() {
        return selectionSha256;
    }

    public String rootCaptureId() {
        return rootCaptureId;
    }

    public Instant rootCapturedAt() {
        return rootCapturedAt;
    }

    public String cik() {
        return cik;
    }

    public Instant evidenceAvailableAt() {
        return evidenceAvailableAt;
    }

    public Instant assembledAt() {
        return assembledAt;
    }

    public SelectionCoverage selectionCoverage() {
        return selectionCoverage;
    }

    public List<DescriptorMember> descriptors() {
        return descriptors;
    }

    public List<FilingOccurrence> occurrences() {
        return occurrences;
    }

    public List<AccessionGroup> accessionGroups() {
        return accessionGroups;
    }

    public int advertisedDescriptorCount() {
        return descriptors.size();
    }

    public int selectedDescriptorCount() {
        return (int) descriptors.stream()
                .filter(DescriptorMember::selected)
                .count();
    }

    public long sourceOccurrenceCount() {
        return occurrences.size();
    }

    public long distinctAccessionCount() {
        return accessionGroups.size();
    }

    public long singleAccessionCount() {
        return countGroups(AccessionComparison.SINGLE_SOURCE_OCCURRENCE);
    }

    public long agreeingAccessionCount() {
        return countGroups(
                AccessionComparison.MULTIPLE_OCCURRENCES_EXACT_AGREEMENT);
    }

    public long conflictingAccessionCount() {
        return countGroups(
                AccessionComparison.MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT);
    }

    /** Compares immutable selection-derived content while ignoring assembly time. */
    public boolean sameContentAs(FilingHistoryCollectionManifest other) {
        return other != null
                && manifestId.equals(other.manifestId)
                && selectionSha256.equals(other.selectionSha256)
                && rootCaptureId.equals(other.rootCaptureId)
                && rootCapturedAt.equals(other.rootCapturedAt)
                && cik.equals(other.cik)
                && evidenceAvailableAt.equals(other.evidenceAvailableAt)
                && selectionCoverage == other.selectionCoverage
                && descriptors.equals(other.descriptors)
                && occurrences.equals(other.occurrences)
                && accessionGroups.equals(other.accessionGroups);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof FilingHistoryCollectionManifest that)) {
            return false;
        }
        return sameContentAs(that) && assembledAt.equals(that.assembledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                manifestId,
                selectionSha256,
                rootCaptureId,
                rootCapturedAt,
                cik,
                evidenceAvailableAt,
                assembledAt,
                selectionCoverage,
                descriptors,
                occurrences,
                accessionGroups);
    }

    @Override
    public String toString() {
        return "FilingHistoryCollectionManifest[manifestId=" + manifestId
                + ", rootCaptureId=" + rootCaptureId
                + ", evidenceAvailableAt=" + evidenceAvailableAt
                + ", assembledAt=" + assembledAt
                + ", selectionCoverage=" + selectionCoverage
                + ", advertisedDescriptorCount=" + advertisedDescriptorCount()
                + ", selectedDescriptorCount=" + selectedDescriptorCount()
                + ", sourceOccurrenceCount=" + sourceOccurrenceCount()
                + ", distinctAccessionCount=" + distinctAccessionCount() + "]";
    }

    private long countGroups(AccessionComparison comparison) {
        return accessionGroups.stream()
                .filter(group -> group.comparison() == comparison)
                .count();
    }

    private static void requireExactDurableRoot(FilingCatalogCapture capture) {
        FilingCatalog root = capture.catalog();
        if (!PROVIDER.equals(root.provider())
                || !ROOT_PRODUCT.equals(root.product())
                || !ROOT_PARSER_VERSION.equals(root.sourceReceipt().parserVersion())
                || root.sourceReceipt().bodyRetention()
                        != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "durableRoot must use the exact durable SEC catalog contract");
        }
    }

    private static void requireExactSelectedCapture(
            FilingCatalogCapture durableRoot,
            HistoricalFilingSegmentCapture selectedCapture) {
        HistoricalFilingSegment segment = selectedCapture.segment();
        FilingCatalog root = durableRoot.catalog();
        if (!PROVIDER.equals(segment.provider())
                || !SEGMENT_PRODUCT.equals(segment.product())
                || !SEGMENT_PARSER_VERSION.equals(
                        segment.sourceReceipt().parserVersion())
                || segment.sourceReceipt().bodyRetention()
                        != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "selected capture must use the exact durable SEC segment contract");
        }
        if (!durableRoot.captureId().equals(segment.rootCaptureId())
                || !root.capturedAt().equals(segment.rootCapturedAt())
                || !root.cik().equals(segment.cik())) {
            throw new IllegalArgumentException(
                    "selected capture must identify the exact durable root");
        }
        int ordinal = segment.descriptorOrdinal();
        if (ordinal < 0 || ordinal >= root.historicalSegments().size()) {
            throw new IllegalArgumentException(
                    "selected descriptorOrdinal must exist in the durable root");
        }
        if (!root.historicalSegments().get(ordinal).equals(segment.descriptor())) {
            throw new IllegalArgumentException(
                    "selected capture must match the exact root descriptor");
        }
    }

    private static List<DescriptorMember> descriptorMembers(
            FilingCatalog root,
            Map<Integer, HistoricalFilingSegmentCapture> selectedByOrdinal) {
        List<DescriptorMember> members = new ArrayList<>(
                root.historicalSegments().size());
        for (int ordinal = 0; ordinal < root.historicalSegments().size(); ordinal++) {
            HistoricalFilingSegmentCapture selected = selectedByOrdinal.get(ordinal);
            members.add(new DescriptorMember(
                    ordinal,
                    root.historicalSegments().get(ordinal),
                    selected == null
                            ? DescriptorSelectionState.NOT_SELECTED
                            : DescriptorSelectionState.SELECTED_EXACT_CAPTURE,
                    selected == null ? null : selected.captureId()));
        }
        return List.copyOf(members);
    }

    private static SelectionCoverage selectionCoverage(
            int advertisedCount,
            int selectedCount) {
        if (advertisedCount == 0) {
            return SelectionCoverage.NO_ADVERTISED_DESCRIPTORS;
        }
        if (selectedCount == advertisedCount) {
            return SelectionCoverage.ALL_ADVERTISED_DESCRIPTORS_SELECTED;
        }
        return SelectionCoverage.PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED;
    }

    private static List<FilingOccurrence> occurrences(
            FilingCatalog root,
            Map<Integer, HistoricalFilingSegmentCapture> selectedByOrdinal) {
        List<FilingOccurrence> occurrences = new ArrayList<>();
        for (int sourceOrdinal = 0;
                sourceOrdinal < root.recentFilings().size();
                sourceOrdinal++) {
            addOccurrence(
                    occurrences,
                    OccurrenceSourceKind.ROOT_RECENT,
                    null,
                    null,
                    sourceOrdinal,
                    projection(root.recentFilings().get(sourceOrdinal)));
        }
        for (Map.Entry<Integer, HistoricalFilingSegmentCapture> selection
                : selectedByOrdinal.entrySet()) {
            List<HistoricalFilingRecord> filings = selection.getValue()
                    .segment()
                    .filings();
            for (int sourceOrdinal = 0;
                    sourceOrdinal < filings.size();
                    sourceOrdinal++) {
                addOccurrence(
                        occurrences,
                        OccurrenceSourceKind.HISTORICAL_SEGMENT,
                        selection.getKey(),
                        selection.getValue().captureId(),
                        sourceOrdinal,
                        projection(filings.get(sourceOrdinal)));
            }
        }
        return List.copyOf(occurrences);
    }

    private static void addOccurrence(
            List<FilingOccurrence> occurrences,
            OccurrenceSourceKind sourceKind,
            Integer descriptorOrdinal,
            String segmentCaptureId,
            int sourceOrdinal,
            OccurrenceProjection projection) {
        occurrences.add(new FilingOccurrence(
                occurrences.size(),
                sourceKind,
                descriptorOrdinal,
                segmentCaptureId,
                sourceOrdinal,
                projection,
                projectionSha256(projection)));
    }

    private static OccurrenceProjection projection(FilingRecord filing) {
        return new OccurrenceProjection(
                filing.providerEventId(),
                filing.accessionNumber(),
                filing.form(),
                filing.filingDate(),
                filing.reportDate(),
                filing.acceptedAt(),
                filing.primaryDocumentUri());
    }

    private static OccurrenceProjection projection(HistoricalFilingRecord filing) {
        return new OccurrenceProjection(
                filing.providerEventId(),
                filing.accessionNumber(),
                filing.form(),
                filing.filingDate(),
                filing.reportDate(),
                filing.acceptedAt(),
                filing.primaryDocumentUri());
    }

    private static List<AccessionGroup> accessionGroups(
            List<FilingOccurrence> occurrences) {
        Map<String, List<OccurrenceProjection>> byAccession = new LinkedHashMap<>();
        for (FilingOccurrence occurrence : occurrences) {
            byAccession.computeIfAbsent(
                    occurrence.projection().accessionNumber(),
                    ignored -> new ArrayList<>())
                    .add(occurrence.projection());
        }
        List<AccessionGroup> groups = new ArrayList<>(byAccession.size());
        for (Map.Entry<String, List<OccurrenceProjection>> entry
                : byAccession.entrySet()) {
            long occurrenceCount = entry.getValue().size();
            long distinctProjectionCount = new LinkedHashSet<>(entry.getValue()).size();
            AccessionComparison comparison;
            if (occurrenceCount == 1) {
                comparison = AccessionComparison.SINGLE_SOURCE_OCCURRENCE;
            } else if (distinctProjectionCount == 1) {
                comparison =
                        AccessionComparison.MULTIPLE_OCCURRENCES_EXACT_AGREEMENT;
            } else {
                comparison =
                        AccessionComparison.MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT;
            }
            groups.add(new AccessionGroup(
                    groups.size(),
                    entry.getKey(),
                    occurrenceCount,
                    distinctProjectionCount,
                    comparison));
        }
        return List.copyOf(groups);
    }

    private static String selectionSha256(
            String rootCaptureId,
            List<DescriptorMember> descriptors) {
        StringBuilder identity = new StringBuilder();
        appendLengthPrefixed(identity, SELECTION_IDENTITY_VERSION);
        appendLengthPrefixed(identity, rootCaptureId);
        appendLengthPrefixed(identity, Integer.toString(descriptors.size()));
        for (DescriptorMember member : descriptors) {
            HistoricalFilingSegmentDescriptor descriptor = member.descriptor();
            appendLengthPrefixed(identity, Integer.toString(member.descriptorOrdinal()));
            appendLengthPrefixed(identity, descriptor.fileName());
            appendLengthPrefixed(
                    identity, Long.toString(descriptor.advertisedFilingCount()));
            appendLengthPrefixed(identity, descriptor.advertisedFilingFrom().toString());
            appendLengthPrefixed(identity, descriptor.advertisedFilingTo().toString());
            appendLengthPrefixed(identity, member.selectionState().name());
            appendLengthPrefixed(identity, member.selectedSegmentCaptureId());
        }
        return sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String manifestId(
            String rootCaptureId,
            String selectionSha256) {
        StringBuilder identity = new StringBuilder();
        appendLengthPrefixed(identity, MANIFEST_IDENTITY_VERSION);
        appendLengthPrefixed(identity, SCHEMA_VERSION);
        appendLengthPrefixed(identity, PROVIDER);
        appendLengthPrefixed(identity, PRODUCT);
        appendLengthPrefixed(identity, POLICY_VERSION);
        appendLengthPrefixed(identity, rootCaptureId);
        appendLengthPrefixed(identity, selectionSha256);
        return sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String projectionSha256(OccurrenceProjection projection) {
        StringBuilder identity = new StringBuilder();
        appendLengthPrefixed(identity, PROJECTION_IDENTITY_VERSION);
        appendLengthPrefixed(identity, projection.providerEventId());
        appendLengthPrefixed(identity, projection.accessionNumber());
        appendLengthPrefixed(identity, projection.form());
        appendLengthPrefixed(identity, projection.filingDate().toString());
        appendLengthPrefixed(
                identity,
                projection.reportDate() == null
                        ? null
                        : projection.reportDate().toString());
        appendLengthPrefixed(identity, projection.acceptedAt().toString());
        appendLengthPrefixed(
                identity,
                projection.primaryDocumentUri() == null
                        ? null
                        : projection.primaryDocumentUri().toASCIIString());
        return sha256(identity.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void appendLengthPrefixed(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
            return;
        }
        target.append(value.getBytes(StandardCharsets.UTF_8).length)
                .append(':')
                .append(value);
    }

    private static String sha256(byte[] content) {
        try {
            return LOWERCASE_HEX.formatHex(
                    MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    field + " must be lowercase SHA-256 hex");
        }
    }

    public record DescriptorMember(
            int descriptorOrdinal,
            HistoricalFilingSegmentDescriptor descriptor,
            DescriptorSelectionState selectionState,
            String selectedSegmentCaptureId) {

        public DescriptorMember {
            if (descriptorOrdinal < 0) {
                throw new IllegalArgumentException(
                        "descriptorOrdinal must be nonnegative");
            }
            Objects.requireNonNull(descriptor, "descriptor must not be null");
            Objects.requireNonNull(
                    selectionState, "selectionState must not be null");
            if (selectionState == DescriptorSelectionState.SELECTED_EXACT_CAPTURE) {
                requireSha256(
                        selectedSegmentCaptureId, "selectedSegmentCaptureId");
            } else if (selectedSegmentCaptureId != null) {
                throw new IllegalArgumentException(
                        "an unselected descriptor must not identify a segment capture");
            }
        }

        public boolean selected() {
            return selectionState == DescriptorSelectionState.SELECTED_EXACT_CAPTURE;
        }
    }

    public record OccurrenceProjection(
            String providerEventId,
            String accessionNumber,
            String form,
            LocalDate filingDate,
            LocalDate reportDate,
            Instant acceptedAt,
            URI primaryDocumentUri) {

        public OccurrenceProjection {
            new HistoricalFilingRecord(
                    providerEventId,
                    accessionNumber,
                    form,
                    filingDate,
                    reportDate,
                    acceptedAt,
                    primaryDocumentUri);
        }
    }

    public record FilingOccurrence(
            int occurrenceOrdinal,
            OccurrenceSourceKind sourceKind,
            Integer descriptorOrdinal,
            String segmentCaptureId,
            int sourceOrdinal,
            OccurrenceProjection projection,
            String projectionSha256) {

        public FilingOccurrence {
            if (occurrenceOrdinal < 0) {
                throw new IllegalArgumentException(
                        "occurrenceOrdinal must be nonnegative");
            }
            Objects.requireNonNull(sourceKind, "sourceKind must not be null");
            if (sourceOrdinal < 0) {
                throw new IllegalArgumentException(
                        "sourceOrdinal must be nonnegative");
            }
            Objects.requireNonNull(projection, "projection must not be null");
            if (sourceKind == OccurrenceSourceKind.ROOT_RECENT) {
                if (descriptorOrdinal != null || segmentCaptureId != null) {
                    throw new IllegalArgumentException(
                            "root occurrence must not identify a historical segment");
                }
            } else {
                if (descriptorOrdinal == null || descriptorOrdinal < 0) {
                    throw new IllegalArgumentException(
                            "historical occurrence must identify a descriptorOrdinal");
                }
                requireSha256(segmentCaptureId, "segmentCaptureId");
            }
            requireSha256(projectionSha256, "projectionSha256");
            if (!FilingHistoryCollectionManifest.projectionSha256(projection)
                    .equals(projectionSha256)) {
                throw new IllegalArgumentException(
                        "projectionSha256 must identify the exact occurrence projection");
            }
        }
    }

    public record AccessionGroup(
            int groupOrdinal,
            String accessionNumber,
            long occurrenceCount,
            long distinctProjectionCount,
            AccessionComparison comparison) {

        public AccessionGroup {
            if (groupOrdinal < 0) {
                throw new IllegalArgumentException(
                        "groupOrdinal must be nonnegative");
            }
            if (accessionNumber == null
                    || !SEC_ACCESSION_NUMBER.matcher(accessionNumber).matches()) {
                throw new IllegalArgumentException(
                        "accessionNumber must use the SEC 10-2-6 digit format");
            }
            if (occurrenceCount <= 0
                    || distinctProjectionCount <= 0
                    || distinctProjectionCount > occurrenceCount) {
                throw new IllegalArgumentException(
                        "accession group counts must describe observed projections");
            }
            Objects.requireNonNull(comparison, "comparison must not be null");
            boolean coherent = switch (comparison) {
                case SINGLE_SOURCE_OCCURRENCE ->
                    occurrenceCount == 1 && distinctProjectionCount == 1;
                case MULTIPLE_OCCURRENCES_EXACT_AGREEMENT ->
                    occurrenceCount > 1 && distinctProjectionCount == 1;
                case MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT ->
                    occurrenceCount > 1 && distinctProjectionCount > 1;
            };
            if (!coherent) {
                throw new IllegalArgumentException(
                        "accession comparison must match its occurrence counts");
            }
        }
    }

    public enum DescriptorSelectionState {
        NOT_SELECTED,
        SELECTED_EXACT_CAPTURE
    }

    public enum SelectionCoverage {
        NO_ADVERTISED_DESCRIPTORS,
        PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED,
        ALL_ADVERTISED_DESCRIPTORS_SELECTED
    }

    public enum OccurrenceSourceKind {
        ROOT_RECENT,
        HISTORICAL_SEGMENT
    }

    public enum AccessionComparison {
        SINGLE_SOURCE_OCCURRENCE,
        MULTIPLE_OCCURRENCES_EXACT_AGREEMENT,
        MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT
    }
}
