package com.wallstreetreceipts.api.persistence;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture.PARSER_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment.AdvertisedComparison;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.support.SecHistoricalFilingSegmentCaptureTestFixture;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class HistoricalFilingSegmentCapturePersistenceTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:12:03.123456Z");

    @Autowired
    private FilingCatalogCaptureRepository rootRepository;

    @Autowired
    private HistoricalFilingSegmentCaptureRepository segmentRepository;

    @Autowired
    private JdbcTemplate jdbc;

    private FilingCatalogCapture durableRoot;

    @BeforeEach
    void persistRoot() {
        FilingCatalogCapture pending = capture(ROOT_CAPTURED_AT);
        rootRepository.append(pending);
        durableRoot = rootRepository.findByCaptureId(pending.captureId()).orElseThrow();
    }

    @Test
    void appendsAndReconstructsExactBodyReceiptOrderAndMismatchEvidence() {
        HistoricalFilingSegmentCapture pending =
                SecHistoricalFilingSegmentCaptureTestFixture
                        .captureWithMissingPrimaryDocument(
                        durableRoot, SEGMENT_CAPTURED_AT);

        assertThat(segmentRepository.append(pending))
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);

        HistoricalFilingSegmentCapture stored = segmentRepository
                .findByCaptureId(pending.captureId())
                .orElseThrow();
        assertThat(stored).isEqualTo(pending.withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED));
        assertThat(stored.decodedBody()).containsExactly(pending.decodedBody());
        assertThat(stored.segment().filings())
                .extracting(HistoricalFilingRecord::accessionNumber)
                .containsExactly(
                        "0000320193-20-000001",
                        "0000320193-15-000002");
        assertThat(stored.segment().filings().getFirst().primaryDocumentUri()).isNull();
        assertThat(stored.segment().observedFilingCount()).isEqualTo(2);
        assertThat(stored.segment().advertisedComparison())
                .isEqualTo(AdvertisedComparison.COUNT_MISMATCH);
    }

    @Test
    void exactReplayIsNoOpAndLaterObservationSharesOnlyTheBodyRow() {
        HistoricalFilingSegmentCapture first =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT);
        HistoricalFilingSegmentCapture later =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT.plusSeconds(60));

        assertThat(segmentRepository.append(first))
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);
        assertThat(segmentRepository.append(first))
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.IDENTICAL_REPLAY);
        assertThat(segmentRepository.append(later))
                .isEqualTo(HistoricalFilingSegmentCaptureAppendResult.INSERTED);

        assertThat(segmentRepository.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM sec_decoded_response_bodies", Long.class))
                .isEqualTo(2);
    }

    @Test
    void naturalIdentityConflictFailsClosed() {
        HistoricalFilingSegmentCapture first =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT, "10-K");
        HistoricalFilingSegmentCapture conflicting =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT, "20-F");
        segmentRepository.append(first);

        assertThatThrownBy(() -> segmentRepository.append(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("natural capture identity");
        assertThat(segmentRepository.count()).isEqualTo(1);
    }

    @Test
    void pointInTimeReadUsesSegmentKnowledgeTimeAndExactParser() {
        HistoricalFilingSegmentCapture first =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT);
        HistoricalFilingSegmentCapture later =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT.plusSeconds(60));
        segmentRepository.append(first);
        segmentRepository.append(later);

        assertThat(segmentRepository.findLatestAtOrBefore(
                durableRoot.captureId(),
                0,
                SEGMENT_CAPTURED_AT.minusNanos(1_000),
                PARSER_VERSION)).isEmpty();
        assertThat(segmentRepository.findLatestAtOrBefore(
                durableRoot.captureId(),
                0,
                SEGMENT_CAPTURED_AT.plusSeconds(30),
                PARSER_VERSION)).contains(first.withBodyRetention(
                        BodyRetention.DURABLE_DECODED_BODY_RETAINED));
        assertThat(segmentRepository.findLatestAtOrBefore(
                durableRoot.captureId(),
                0,
                SEGMENT_CAPTURED_AT.plusSeconds(120),
                "OTHER_PARSER")).isEmpty();
    }

    @Test
    void missingDurableRootRejectsAppendBeforeAnySegmentWrite() {
        FilingCatalogCapture inMemoryRoot = capture(ROOT_CAPTURED_AT.plusSeconds(60))
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        HistoricalFilingSegmentCapture segment =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        inMemoryRoot, SEGMENT_CAPTURED_AT.plusSeconds(120));

        assertThatThrownBy(() -> segmentRepository.append(segment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("historical segment root capture could not be reconstructed");
        assertThat(segmentRepository.count()).isZero();
    }

    @Test
    void readFailsClosedWhenStoredProjectionIsTampered() {
        HistoricalFilingSegmentCapture capture =
                SecHistoricalFilingSegmentCaptureTestFixture.capture(
                        durableRoot, SEGMENT_CAPTURED_AT);
        segmentRepository.append(capture);
        jdbc.update(
                """
                        UPDATE sec_historical_filing_segment_captures
                        SET observed_filing_count = advertised_filing_count,
                            advertised_comparison = 'MATCHES_ADVERTISED'
                        WHERE segment_capture_id = ?
                        """,
                capture.captureId());

        assertThatThrownBy(() -> segmentRepository.findByCaptureId(capture.captureId()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("historical segment child count does not match its capture receipt");
    }

    @Test
    void repositoryAndCaptureExposeNoMutationSurface() {
        assertThat(Arrays.stream(HistoricalFilingSegmentCaptureRepository.class.getMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("update") || name.startsWith("delete")))
                .isTrue();
        assertThat(Arrays.stream(HistoricalFilingSegmentCapture.class.getDeclaredFields())
                .filter(field -> !field.isSynthetic())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .allMatch(field -> Modifier.isPrivate(field.getModifiers())
                        && Modifier.isFinal(field.getModifiers())))
                .isTrue();
        assertThat(Arrays.stream(HistoricalFilingSegment.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .noneMatch(name -> name.startsWith("set")))
                .isTrue();
    }
}
