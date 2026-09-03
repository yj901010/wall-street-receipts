package com.wallstreetreceipts.api.persistence;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.AccessionComparison;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.DescriptorSelectionState;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.OccurrenceSourceKind;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest.SelectionCoverage;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcFilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.support.FilingHistoryCollectionTestFixture;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class FilingHistoryCollectionManifestPersistenceTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:00:00.123456Z");
    private static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:10:00.123456Z");
    private static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:20:00.123456Z");
    private static final Instant FIRST_ASSEMBLED_AT =
            Instant.parse("2026-08-25T03:30:00.123456Z");

    @Autowired
    private FilingCatalogCaptureRepository rootRepository;

    @Autowired
    private HistoricalFilingSegmentCaptureRepository segmentRepository;

    @Autowired
    private FilingHistoryCollectionManifestRepository manifestRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private FilingCatalogCapture durableRoot;

    @BeforeEach
    void persistDurableRoot() {
        FilingCatalogCapture pendingRoot = capture(ROOT_CAPTURED_AT);
        assertThat(rootRepository.append(pendingRoot))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        durableRoot = rootRepository.findByCaptureId(pendingRoot.captureId())
                .orElseThrow();
    }

    @Test
    void appendsAndReconstructsExactOrderedEvidenceWithNullableAgreementAndConflict() {
        List<HistoricalFilingSegmentCapture> selected = persistBothSegments();
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest.assemble(
                durableRoot, selected, FIRST_ASSEMBLED_AT);

        var append = manifestRepository.append(manifest);

        assertThat(append.status()).isEqualTo(Status.INSERTED);
        assertThat(append.manifest()).isEqualTo(manifest);
        assertThat(manifestRepository.findByManifestId(manifest.manifestId()))
                .contains(manifest);
        assertThat(manifest.selectionCoverage())
                .isEqualTo(SelectionCoverage.ALL_ADVERTISED_DESCRIPTORS_SELECTED);
        assertThat(manifest.descriptors())
                .extracting(member -> member.descriptorOrdinal())
                .containsExactly(0, 1);
        assertThat(manifest.descriptors())
                .extracting(member -> member.selectionState())
                .containsExactly(
                        DescriptorSelectionState.SELECTED_EXACT_CAPTURE,
                        DescriptorSelectionState.SELECTED_EXACT_CAPTURE);
        assertThat(manifest.occurrences())
                .extracting(occurrence -> occurrence.sourceKind())
                .containsExactly(
                        OccurrenceSourceKind.ROOT_RECENT,
                        OccurrenceSourceKind.ROOT_RECENT,
                        OccurrenceSourceKind.HISTORICAL_SEGMENT,
                        OccurrenceSourceKind.HISTORICAL_SEGMENT,
                        OccurrenceSourceKind.HISTORICAL_SEGMENT,
                        OccurrenceSourceKind.HISTORICAL_SEGMENT);
        assertThat(manifest.accessionGroups())
                .extracting(group -> group.comparison())
                .containsExactly(
                        AccessionComparison.SINGLE_SOURCE_OCCURRENCE,
                        AccessionComparison.SINGLE_SOURCE_OCCURRENCE,
                        AccessionComparison.MULTIPLE_OCCURRENCES_EXACT_AGREEMENT,
                        AccessionComparison.MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT);
        assertThat(manifest.singleAccessionCount()).isEqualTo(2);
        assertThat(manifest.agreeingAccessionCount()).isEqualTo(1);
        assertThat(manifest.conflictingAccessionCount()).isEqualTo(1);
        assertThat(manifest.occurrences().get(2).projection().primaryDocumentUri())
                .isNull();
        assertThat(manifest.occurrences().get(4).projection().primaryDocumentUri())
                .isNull();
        assertThat(manifest.occurrences().get(3).projection().primaryDocumentUri())
                .isNull();
        assertThat(manifest.occurrences().get(5).projection().primaryDocumentUri())
                .isNotNull();
    }

    @Test
    void keepsEveryDescriptorInRootOrderAndPersistsAnotherExactSelectionSeparately() {
        List<HistoricalFilingSegmentCapture> selected = persistBothSegments();
        FilingHistoryCollectionManifest allSelected = FilingHistoryCollectionManifest.assemble(
                durableRoot, selected, FIRST_ASSEMBLED_AT);
        FilingHistoryCollectionManifest onlySecond = FilingHistoryCollectionManifest.assemble(
                durableRoot,
                List.of(selected.get(1)),
                FIRST_ASSEMBLED_AT.plusSeconds(60));

        assertThat(manifestRepository.append(allSelected).status())
                .isEqualTo(Status.INSERTED);
        assertThat(manifestRepository.append(onlySecond).status())
                .isEqualTo(Status.INSERTED);

        assertThat(manifestRepository.count()).isEqualTo(2);
        assertThat(onlySecond.manifestId()).isNotEqualTo(allSelected.manifestId());
        assertThat(onlySecond.selectionCoverage())
                .isEqualTo(SelectionCoverage.PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED);
        assertThat(onlySecond.descriptors())
                .extracting(member -> member.descriptorOrdinal())
                .containsExactly(0, 1);
        assertThat(onlySecond.descriptors())
                .extracting(member -> member.selectionState())
                .containsExactly(
                        DescriptorSelectionState.NOT_SELECTED,
                        DescriptorSelectionState.SELECTED_EXACT_CAPTURE);
        assertThat(manifestRepository.findByManifestId(onlySecond.manifestId()))
                .contains(onlySecond);
    }

    @Test
    void identicalSelectionReplayReturnsTheFirstAssembledObservation() {
        List<HistoricalFilingSegmentCapture> selected = persistBothSegments();
        FilingHistoryCollectionManifest first = FilingHistoryCollectionManifest.assemble(
                durableRoot, selected, FIRST_ASSEMBLED_AT);
        FilingHistoryCollectionManifest laterReplay = FilingHistoryCollectionManifest.assemble(
                durableRoot, selected, FIRST_ASSEMBLED_AT.plusSeconds(300));

        assertThat(manifestRepository.append(first).status()).isEqualTo(Status.INSERTED);
        var replay = manifestRepository.append(laterReplay);

        assertThat(replay.status()).isEqualTo(Status.IDENTICAL_REPLAY);
        assertThat(replay.manifest()).isEqualTo(first);
        assertThat(replay.manifest().assembledAt()).isEqualTo(FIRST_ASSEMBLED_AT);
        assertThat(manifestRepository.count()).isEqualTo(1);
    }

    @Test
    void exactManifestIdPointInTimeReadClosesBeforeAtAndAfterAssembly() {
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest.assemble(
                durableRoot, persistBothSegments(), FIRST_ASSEMBLED_AT);
        manifestRepository.append(manifest);

        assertThat(manifestRepository.findByManifestIdAtOrBefore(
                manifest.manifestId(), FIRST_ASSEMBLED_AT.minusNanos(1_000)))
                .isEmpty();
        assertThat(manifestRepository.findByManifestIdAtOrBefore(
                manifest.manifestId(), FIRST_ASSEMBLED_AT))
                .contains(manifest);
        assertThat(manifestRepository.findByManifestIdAtOrBefore(
                manifest.manifestId(), FIRST_ASSEMBLED_AT.plusSeconds(1)))
                .contains(manifest);
    }

    @Test
    void validLookingSummaryAndSelectionHashTamperingFailClosedOnRead() {
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest.assemble(
                durableRoot, persistBothSegments(), FIRST_ASSEMBLED_AT);
        manifestRepository.append(manifest);

        jdbc.update(
                """
                        UPDATE sec_filing_history_collection_manifests
                        SET single_source_group_count = 1,
                            exact_agreement_group_count = 2
                        WHERE manifest_id = ?
                        """,
                manifest.manifestId());
        assertThatThrownBy(() -> manifestRepository.findByManifestId(
                manifest.manifestId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("filing history collection manifest summary does not match its sources");

        jdbc.update(
                """
                        UPDATE sec_filing_history_collection_manifests
                        SET single_source_group_count = 2,
                            exact_agreement_group_count = 1,
                            selection_sha256 = ?
                        WHERE manifest_id = ?
                        """,
                "f".repeat(64),
                manifest.manifestId());
        assertThatThrownBy(() -> manifestRepository.findByManifestId(
                manifest.manifestId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("filing history collection manifest summary does not match its sources");
    }

    @Test
    void repositoryAndManifestExposeNoUpdateOrDeleteSurface() {
        assertThat(Arrays.stream(FilingHistoryCollectionManifestRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("purge")))
                .isTrue();
        assertThat(Arrays.stream(
                JdbcFilingHistoryCollectionManifestRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update")
                        || name.startsWith("delete")
                        || name.startsWith("remove")
                        || name.startsWith("purge")))
                .isTrue();
        assertThat(Arrays.stream(FilingHistoryCollectionManifest.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())))
                .isTrue();
    }

    private List<HistoricalFilingSegmentCapture> persistBothSegments() {
        HistoricalFilingRecord agreeingNullDocument = record(
                "0000320193-14-000101",
                "10-K",
                LocalDate.parse("2014-12-31"),
                LocalDate.parse("2014-09-27"),
                Instant.parse("2014-12-31T20:00:00.123456Z"),
                null);
        HistoricalFilingRecord conflictingNullDocument = record(
                "0000320193-14-000102",
                "8-K",
                LocalDate.parse("2014-12-30"),
                null,
                Instant.parse("2014-12-30T15:30:00.123456Z"),
                null);
        HistoricalFilingRecord conflictingDocument = record(
                "0000320193-14-000102",
                "8-K",
                LocalDate.parse("2014-12-30"),
                null,
                Instant.parse("2014-12-30T15:30:00.123456Z"),
                URI.create("https://www.sec.gov/Archives/edgar/data/"
                        + "320193/000032019314000102/conflict8k.htm"));

        HistoricalFilingSegmentCapture first = persistSegment(
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(agreeingNullDocument, conflictingNullDocument));
        HistoricalFilingSegmentCapture second = persistSegment(
                1,
                SECOND_SEGMENT_CAPTURED_AT,
                List.of(agreeingNullDocument, conflictingDocument));
        return List.of(first, second);
    }

    private HistoricalFilingSegmentCapture persistSegment(
            int descriptorOrdinal,
            Instant capturedAt,
            List<HistoricalFilingRecord> filings) {
        HistoricalFilingSegmentCapture pending =
                FilingHistoryCollectionTestFixture.segmentCapture(
                        durableRoot, descriptorOrdinal, capturedAt, filings);
        assertThat(segmentRepository.append(pending))
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
        return segmentRepository.findByCaptureId(pending.captureId()).orElseThrow();
    }

    private static HistoricalFilingRecord record(
            String accessionNumber,
            String form,
            LocalDate filingDate,
            LocalDate reportDate,
            Instant acceptedAt,
            URI primaryDocumentUri) {
        return new HistoricalFilingRecord(
                accessionNumber,
                accessionNumber,
                form,
                filingDate,
                reportDate,
                acceptedAt,
                primaryDocumentUri);
    }
}
