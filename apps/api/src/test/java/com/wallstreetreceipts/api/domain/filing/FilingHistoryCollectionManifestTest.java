package com.wallstreetreceipts.api.domain.filing;

import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionComparison.MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionComparison.MULTIPLE_OCCURRENCES_EXACT_AGREEMENT;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionComparison.SINGLE_SOURCE_OCCURRENCE;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorSelectionState.NOT_SELECTED;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorSelectionState.SELECTED_EXACT_CAPTURE;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.OccurrenceSourceKind.HISTORICAL_SEGMENT;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.OccurrenceSourceKind.ROOT_RECENT;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.SelectionCoverage.ALL_ADVERTISED_DESCRIPTORS_SELECTED;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.SelectionCoverage.NO_ADVERTISED_DESCRIPTORS;
import static com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.SelectionCoverage.PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionGroup;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.FilingOccurrence;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;
import com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

class FilingHistoryCollectionManifestTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:12:03.123456Z");
    private static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:22:03.123456Z");
    private static final Instant ASSEMBLED_AT =
            Instant.parse("2026-08-25T01:32:03.123456Z");

    @Test
    void assemblesAllSelectedCapturesInRootDescriptorAndProviderRowOrder() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture first = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT));
        HistoricalFilingSegmentCapture second = segmentCapture(
                root,
                1,
                root.catalog().historicalSegments().get(1),
                SECOND_SEGMENT_CAPTURED_AT,
                List.of(uniqueHistoricalFiling("0000320193-10-000003")),
                "second-segment");

        FilingHistoryCollectionManifest manifest =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(second, first), ASSEMBLED_AT);

        assertThat(manifest.schemaVersion()).isEqualTo("1.0.0");
        assertThat(manifest.provider()).isEqualTo("sec-edgar");
        assertThat(manifest.product())
                .isEqualTo("edgar-submissions-root-relative-collection-manifest");
        assertThat(manifest.policyVersion())
                .isEqualTo("SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1");
        assertThat(manifest.selectionCoverage())
                .isEqualTo(ALL_ADVERTISED_DESCRIPTORS_SELECTED);
        assertThat(manifest.advertisedDescriptorCount()).isEqualTo(2);
        assertThat(manifest.selectedDescriptorCount()).isEqualTo(2);
        assertThat(manifest.descriptors())
                .extracting(member -> member.descriptorOrdinal())
                .containsExactly(0, 1);
        assertThat(manifest.descriptors())
                .extracting(member -> member.selectedSegmentCaptureId())
                .containsExactly(first.captureId(), second.captureId());
        assertThat(manifest.descriptors())
                .extracting(member -> member.selectionState())
                .containsOnly(SELECTED_EXACT_CAPTURE);

        assertThat(manifest.occurrences())
                .extracting(occurrence -> occurrence.projection().accessionNumber())
                .containsExactly(
                        "0000320193-26-000001",
                        "0000320193-26-000002",
                        "0000320193-20-000001",
                        "0000320193-15-000002",
                        "0000320193-10-000003");
        assertThat(manifest.occurrences())
                .extracting(FilingOccurrence::occurrenceOrdinal)
                .containsExactly(0, 1, 2, 3, 4);
        assertThat(manifest.occurrences())
                .extracting(FilingOccurrence::sourceKind)
                .containsExactly(
                        ROOT_RECENT,
                        ROOT_RECENT,
                        HISTORICAL_SEGMENT,
                        HISTORICAL_SEGMENT,
                        HISTORICAL_SEGMENT);
        assertThat(manifest.occurrences())
                .extracting(FilingOccurrence::descriptorOrdinal)
                .containsExactly(null, null, 0, 0, 1);
        assertThat(manifest.evidenceAvailableAt())
                .isEqualTo(SECOND_SEGMENT_CAPTURED_AT);
        assertThat(manifest.assembledAt()).isEqualTo(ASSEMBLED_AT);
        assertThat(manifest.sourceOccurrenceCount()).isEqualTo(5);
        assertThat(manifest.distinctAccessionCount()).isEqualTo(5);
        assertThat(manifest.singleAccessionCount()).isEqualTo(5);
    }

    @Test
    void exposesPartialAndNoAdvertisedDescriptorCoverageWithoutRewritingRootState() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture selected = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT));

        FilingHistoryCollectionManifest noneSelected =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(), ASSEMBLED_AT);
        FilingHistoryCollectionManifest partial =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(selected), ASSEMBLED_AT);
        FilingHistoryCollectionManifest noAdvertised =
                FilingHistoryCollectionManifest.assemble(
                        durableRootWithHistoricalDescriptors(List.of()),
                        List.of(),
                        ASSEMBLED_AT);

        assertThat(noneSelected.selectionCoverage())
                .isEqualTo(PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED);
        assertThat(noneSelected.selectedDescriptorCount()).isZero();
        assertThat(noneSelected.descriptors())
                .extracting(member -> member.selectionState())
                .containsOnly(NOT_SELECTED);
        assertThat(partial.selectionCoverage())
                .isEqualTo(PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED);
        assertThat(partial.selectedDescriptorCount()).isEqualTo(1);
        assertThat(partial.descriptors())
                .extracting(member -> member.selectionState())
                .containsExactly(SELECTED_EXACT_CAPTURE, NOT_SELECTED);
        assertThat(noAdvertised.selectionCoverage())
                .isEqualTo(NO_ADVERTISED_DESCRIPTORS);
        assertThat(noAdvertised.descriptors()).isEmpty();
        assertThat(noAdvertised.evidenceAvailableAt()).isEqualTo(ROOT_CAPTURED_AT);
    }

    @Test
    void retainsEveryOccurrenceAndClassifiesExactAgreementAndNullableUriConflict() {
        FilingCatalogCapture root = durableRoot();
        FilingRecord firstRootFiling = root.catalog().recentFilings().get(0);
        FilingRecord secondRootFiling = root.catalog().recentFilings().get(1);
        HistoricalFilingSegmentCapture selected = segmentCapture(
                root,
                0,
                root.catalog().historicalSegments().get(0),
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(
                        historicalCopy(firstRootFiling, firstRootFiling.primaryDocumentUri()),
                        historicalCopy(secondRootFiling, null),
                        uniqueHistoricalFiling("0000320193-20-000003")),
                "reconciliation-segment");

        FilingHistoryCollectionManifest manifest =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(selected), ASSEMBLED_AT);

        assertThat(manifest.sourceOccurrenceCount()).isEqualTo(5);
        assertThat(manifest.distinctAccessionCount()).isEqualTo(3);
        assertThat(manifest.agreeingAccessionCount()).isEqualTo(1);
        assertThat(manifest.conflictingAccessionCount()).isEqualTo(1);
        assertThat(manifest.singleAccessionCount()).isEqualTo(1);
        assertThat(manifest.accessionGroups())
                .extracting(AccessionGroup::comparison)
                .containsExactly(
                        MULTIPLE_OCCURRENCES_EXACT_AGREEMENT,
                        MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT,
                        SINGLE_SOURCE_OCCURRENCE);
        assertThat(manifest.accessionGroups().get(0).occurrenceCount()).isEqualTo(2);
        assertThat(manifest.accessionGroups().get(0).distinctProjectionCount())
                .isEqualTo(1);
        assertThat(manifest.accessionGroups().get(1).occurrenceCount()).isEqualTo(2);
        assertThat(manifest.accessionGroups().get(1).distinctProjectionCount())
                .isEqualTo(2);
        List<URI> historicalDocumentUris = manifest.occurrences().stream()
                .filter(occurrence -> occurrence.sourceKind() == HISTORICAL_SEGMENT)
                .map(occurrence -> occurrence.projection().primaryDocumentUri())
                .toList();
        assertThat(historicalDocumentUris)
                .contains(firstRootFiling.primaryDocumentUri())
                .containsNull();
    }

    @Test
    void identityIsStableAcrossInputAndAssemblyOrderButChangesWithSelection() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture first = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT));
        HistoricalFilingSegmentCapture second = segmentCapture(
                root,
                1,
                root.catalog().historicalSegments().get(1),
                SECOND_SEGMENT_CAPTURED_AT,
                List.of(uniqueHistoricalFiling("0000320193-10-000003")),
                "second-segment");
        FilingHistoryCollectionManifest initial =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(second, first), ASSEMBLED_AT);
        FilingHistoryCollectionManifest laterAssembly =
                FilingHistoryCollectionManifest.assemble(
                        root,
                        List.of(first, second),
                        ASSEMBLED_AT.plusSeconds(60));
        FilingHistoryCollectionManifest partial =
                FilingHistoryCollectionManifest.assemble(
                        root, List.of(first), ASSEMBLED_AT);

        assertThat(initial.manifestId()).matches("[0-9a-f]{64}");
        assertThat(initial.selectionSha256()).matches("[0-9a-f]{64}");
        assertThat(laterAssembly.manifestId()).isEqualTo(initial.manifestId());
        assertThat(laterAssembly.selectionSha256())
                .isEqualTo(initial.selectionSha256());
        assertThat(initial.sameContentAs(laterAssembly)).isTrue();
        assertThat(initial).isNotEqualTo(laterAssembly);
        assertThat(partial.manifestId()).isNotEqualTo(initial.manifestId());
        assertThat(partial.selectionSha256())
                .isNotEqualTo(initial.selectionSha256());
    }

    @Test
    void rejectsPendingAndUnsupportedRootContracts() {
        FilingCatalogCapture pending = SecFilingCatalogCaptureTestFixture.capture(
                ROOT_CAPTURED_AT);
        FilingCatalogCapture wrongParser = rootWithContract(
                durableRoot(),
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecFilingCatalogCaptureTestFixture.PRODUCT,
                "OTHER_ROOT_PARSER",
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);

        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                pending, List.of(), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durableRoot must use the exact durable SEC catalog contract");
        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                wrongParser, List.of(), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("durableRoot must use the exact durable SEC catalog contract");
    }

    @Test
    void rejectsPendingAndUnsupportedSegmentContracts() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture pending =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT);
        HistoricalFilingSegmentCapture wrongParser = segmentCaptureWithContract(
                root,
                0,
                root.catalog().historicalSegments().get(0),
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(uniqueHistoricalFiling("0000320193-20-000003")),
                "wrong-parser-segment",
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecHistoricalFilingSegmentCaptureTestFixture.PRODUCT,
                "OTHER_SEGMENT_PARSER",
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);

        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root, List.of(pending), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selected capture must use the exact durable SEC segment contract");
        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root, List.of(wrongParser), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selected capture must use the exact durable SEC segment contract");
    }

    @Test
    void rejectsAnotherRootAndMismatchedRootDescriptorBindings() {
        FilingCatalogCapture root = durableRoot();
        FilingCatalogCapture anotherRoot = durable(
                SecFilingCatalogCaptureTestFixture.capture(
                        ROOT_CAPTURED_AT.plusSeconds(60)));
        HistoricalFilingSegmentCapture anotherRootSegment = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        anotherRoot, FIRST_SEGMENT_CAPTURED_AT));
        HistoricalFilingSegmentCapture mismatchedDescriptor = segmentCapture(
                root,
                1,
                root.catalog().historicalSegments().get(0),
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(uniqueHistoricalFiling("0000320193-20-000003")),
                "mismatched-descriptor");

        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root, List.of(anotherRootSegment), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selected capture must identify the exact durable root");
        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root, List.of(mismatchedDescriptor), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selected capture must match the exact root descriptor");
    }

    @Test
    void rejectsDuplicateSelectedCaptureAndDuplicateDescriptorOrdinal() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture first = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT));
        HistoricalFilingSegmentCapture laterSameDescriptor = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT.plusSeconds(60)));

        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root, List.of(first, first), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selected segment captureId must be unique");
        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root, List.of(first, laterSameDescriptor), ASSEMBLED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("selected descriptorOrdinal must be unique");
    }

    @Test
    void rejectsAssemblyTimeBeforeEvidenceOrBeyondMicrosecondPrecision() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture selected = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT));

        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root,
                List.of(selected),
                FIRST_SEGMENT_CAPTURED_AT.minusNanos(1_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assembledAt must not precede evidenceAvailableAt");
        assertThatThrownBy(() -> FilingHistoryCollectionManifest.assemble(
                root,
                List.of(selected),
                ASSEMBLED_AT.plusNanos(1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("assembledAt must not exceed microsecond precision");
    }

    @Test
    void defensivelyOwnsSelectedInputAndPublishedLists() {
        FilingCatalogCapture root = durableRoot();
        HistoricalFilingSegmentCapture selected = durable(
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        root, FIRST_SEGMENT_CAPTURED_AT));
        List<HistoricalFilingSegmentCapture> mutable = new ArrayList<>();
        mutable.add(selected);

        FilingHistoryCollectionManifest manifest =
                FilingHistoryCollectionManifest.assemble(
                        root, mutable, ASSEMBLED_AT);
        mutable.clear();

        assertThat(manifest.selectedDescriptorCount()).isEqualTo(1);
        assertThatThrownBy(() -> manifest.descriptors().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> manifest.occurrences().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> manifest.accessionGroups().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    private static FilingCatalogCapture durableRoot() {
        return durable(SecFilingCatalogCaptureTestFixture.capture(ROOT_CAPTURED_AT));
    }

    private static FilingCatalogCapture durableRootWithHistoricalDescriptors(
            List<HistoricalFilingSegmentDescriptor> descriptors) {
        FilingCatalogCapture base = durableRoot();
        byte[] body = ("root-descriptors-" + descriptors.size())
                .getBytes(StandardCharsets.UTF_8);
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecFilingCatalogCaptureTestFixture.PRODUCT,
                SecFilingCatalogCaptureTestFixture.SOURCE_URI,
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                SecFilingCatalogCaptureTestFixture.PARSER_VERSION,
                sha256(body),
                body.length,
                ROOT_CAPTURED_AT,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        FilingCatalog catalog = new FilingCatalog(
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecFilingCatalogCaptureTestFixture.PRODUCT,
                SecFilingCatalogCaptureTestFixture.CIK,
                SecFilingCatalogCaptureTestFixture.SOURCE_URI,
                ROOT_CAPTURED_AT,
                ROOT_CAPTURED_AT,
                receipt,
                base.catalog().recentFilings(),
                descriptors);
        return new FilingCatalogCapture(catalog, body);
    }

    private static FilingCatalogCapture rootWithContract(
            FilingCatalogCapture base,
            String provider,
            String product,
            String parserVersion,
            BodyRetention retention) {
        SourceResponseReceipt baseReceipt = base.catalog().sourceReceipt();
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                provider,
                product,
                baseReceipt.sourceUri(),
                baseReceipt.httpStatus(),
                baseReceipt.mediaType(),
                baseReceipt.transportContentEncoding(),
                baseReceipt.etag(),
                baseReceipt.lastModified(),
                parserVersion,
                baseReceipt.decodedBodySha256(),
                baseReceipt.decodedBodyLength(),
                baseReceipt.capturedAt(),
                baseReceipt.bodyRepresentation(),
                retention);
        FilingCatalog baseCatalog = base.catalog();
        FilingCatalog catalog = new FilingCatalog(
                provider,
                product,
                baseCatalog.cik(),
                baseCatalog.sourceUri(),
                baseCatalog.processingTime(),
                baseCatalog.capturedAt(),
                receipt,
                baseCatalog.recentFilings(),
                baseCatalog.historicalSegments());
        return new FilingCatalogCapture(catalog, base.decodedBody());
    }

    private static HistoricalFilingSegmentCapture segmentCapture(
            FilingCatalogCapture root,
            int descriptorOrdinal,
            HistoricalFilingSegmentDescriptor descriptor,
            Instant capturedAt,
            List<HistoricalFilingRecord> filings,
            String bodyText) {
        return segmentCaptureWithContract(
                root,
                descriptorOrdinal,
                descriptor,
                capturedAt,
                filings,
                bodyText,
                SecFilingCatalogCaptureTestFixture.PROVIDER,
                SecHistoricalFilingSegmentCaptureTestFixture.PRODUCT,
                SecHistoricalFilingSegmentCaptureTestFixture.PARSER_VERSION,
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    private static HistoricalFilingSegmentCapture segmentCaptureWithContract(
            FilingCatalogCapture root,
            int descriptorOrdinal,
            HistoricalFilingSegmentDescriptor descriptor,
            Instant capturedAt,
            List<HistoricalFilingRecord> filings,
            String bodyText,
            String provider,
            String product,
            String parserVersion,
            BodyRetention retention) {
        byte[] body = bodyText.getBytes(StandardCharsets.UTF_8);
        URI sourceUri = URI.create(
                "https://data.sec.gov/submissions/" + descriptor.fileName());
        SourceResponseReceipt receipt = new SourceResponseReceipt(
                provider,
                product,
                sourceUri,
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                parserVersion,
                sha256(body),
                body.length,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                retention);
        HistoricalFilingSegment segment = new HistoricalFilingSegment(
                provider,
                product,
                root.captureId(),
                root.catalog().capturedAt(),
                descriptorOrdinal,
                root.catalog().cik(),
                descriptor,
                sourceUri,
                capturedAt,
                capturedAt,
                receipt,
                filings);
        return new HistoricalFilingSegmentCapture(segment, body);
    }

    private static HistoricalFilingRecord historicalCopy(
            FilingRecord filing,
            URI primaryDocumentUri) {
        return new HistoricalFilingRecord(
                filing.providerEventId(),
                filing.accessionNumber(),
                filing.form(),
                filing.filingDate(),
                filing.reportDate(),
                filing.acceptedAt(),
                primaryDocumentUri);
    }

    private static HistoricalFilingRecord uniqueHistoricalFiling(
            String accessionNumber) {
        return new HistoricalFilingRecord(
                accessionNumber,
                accessionNumber,
                "8-K",
                LocalDate.parse("2010-01-04"),
                null,
                Instant.parse("2010-01-04T12:00:00.123456Z"),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + accessionNumber.replace("-", "")
                        + "/filing.htm"));
    }

    private static FilingCatalogCapture durable(FilingCatalogCapture capture) {
        return capture.withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    private static HistoricalFilingSegmentCapture durable(
            HistoricalFilingSegmentCapture capture) {
        return capture.withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    private static String sha256(byte[] body) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(body));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
