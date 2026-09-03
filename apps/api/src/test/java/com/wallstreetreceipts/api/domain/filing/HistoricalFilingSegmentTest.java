package com.wallstreetreceipts.api.domain.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment.AdvertisedComparison;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

class HistoricalFilingSegmentTest {

    private static final String PROVIDER = "sec-edgar";
    private static final String PRODUCT = "edgar-submissions-historical-segment-api";
    private static final String ROOT_CAPTURE_ID = "a".repeat(64);
    private static final String CIK = "0000320193";
    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant PROCESSING_TIME =
            Instant.parse("2026-08-25T01:03:03.123456Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:03:03.654321Z");
    private static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193-submissions-001.json");

    @Test
    void preservesProviderOrderAndCalculatesObservedMetadataDeterministically() {
        HistoricalFilingRecord first = filing("0000320193-20-000002", "2020-12-31");
        HistoricalFilingRecord second = filing("0000320193-15-000001", "2015-01-01");
        List<HistoricalFilingRecord> input = new ArrayList<>(List.of(first, second));

        HistoricalFilingSegment result = segment(descriptor(
                2, "2015-01-01", "2020-12-31"), input);
        input.clear();

        assertThat(result.filings()).containsExactly(first, second);
        assertThatThrownBy(() -> result.filings().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(result.observedFilingCount()).isEqualTo(2);
        assertThat(result.observedFilingFrom())
                .isEqualTo(LocalDate.parse("2015-01-01"));
        assertThat(result.observedFilingTo())
                .isEqualTo(LocalDate.parse("2020-12-31"));
        assertThat(result.advertisedComparison())
                .isEqualTo(AdvertisedComparison.MATCHES_ADVERTISED);
    }

    @Test
    void comparesCountAndInclusiveAdvertisedRangeContainmentIndependently() {
        List<HistoricalFilingRecord> filings = List.of(
                filing("0000320193-20-000002", "2020-12-31"),
                filing("0000320193-15-000001", "2015-01-01"));

        assertThat(segment(descriptor(2, "2014-01-01", "2021-12-31"), filings)
                .advertisedComparison())
                .isEqualTo(AdvertisedComparison.MATCHES_ADVERTISED);
        assertThat(segment(descriptor(3, "2014-01-01", "2021-12-31"), filings)
                .advertisedComparison())
                .isEqualTo(AdvertisedComparison.COUNT_MISMATCH);
        assertThat(segment(descriptor(2, "2016-01-01", "2020-12-31"), filings)
                .advertisedComparison())
                .isEqualTo(AdvertisedComparison.RANGE_MISMATCH);
        assertThat(segment(descriptor(2, "2015-01-01", "2019-12-31"), filings)
                .advertisedComparison())
                .isEqualTo(AdvertisedComparison.RANGE_MISMATCH);
        assertThat(segment(descriptor(3, "2016-01-01", "2019-12-31"), filings)
                .advertisedComparison())
                .isEqualTo(AdvertisedComparison.COUNT_AND_RANGE_MISMATCH);

        HistoricalFilingSegment empty = segment(
                descriptor(1, "2015-01-01", "2015-01-01"), List.of());
        assertThat(empty.observedFilingCount()).isZero();
        assertThat(empty.observedFilingFrom()).isNull();
        assertThat(empty.observedFilingTo()).isNull();
        assertThat(empty.advertisedComparison())
                .isEqualTo(AdvertisedComparison.COUNT_MISMATCH);
    }

    @Test
    void rejectsDuplicateNullWrongCatalogAndFutureFilingRows() {
        HistoricalFilingRecord filing = filing(
                "0000320193-20-000002", "2020-12-31");
        assertThatThrownBy(() -> segment(
                descriptor(2, "2020-12-31", "2020-12-31"),
                List.of(filing, filing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerEventId must be unique within the historical segment");

        List<HistoricalFilingRecord> withNull = new ArrayList<>();
        withNull.add(null);
        assertThatThrownBy(() -> segment(
                descriptor(1, "2020-12-31", "2020-12-31"), withNull))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("filings must not contain null");

        HistoricalFilingRecord wrongCatalog = new HistoricalFilingRecord(
                "0000789019-20-000002", "0000789019-20-000002", "10-K",
                LocalDate.parse("2020-12-31"), null,
                Instant.parse("2020-12-31T12:00:00Z"),
                URI.create("https://www.sec.gov/Archives/edgar/data/789019/"
                        + "000078901920000002/form10k.htm"));
        assertThatThrownBy(() -> segment(
                descriptor(1, "2020-12-31", "2020-12-31"),
                List.of(wrongCatalog)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("primaryDocumentUri must use the catalog cik");

        HistoricalFilingRecord future = new HistoricalFilingRecord(
                "0000320193-26-000002", "0000320193-26-000002", "10-Q",
                LocalDate.parse("2026-08-26"), null,
                PROCESSING_TIME.plusSeconds(1),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000032019326000002/form10q.htm"));
        assertThatThrownBy(() -> segment(
                descriptor(1, "2026-08-26", "2026-08-26"), List.of(future)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingTime must not precede filing acceptedAt");
    }

    @Test
    void rejectsInvalidRootDescriptorSourceAndReceiptBindings() {
        HistoricalFilingSegmentDescriptor valid = descriptor(
                1, "2020-12-31", "2020-12-31");
        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, "A".repeat(64), ROOT_CAPTURED_AT, 0, CIK,
                valid, SOURCE_URI, PROCESSING_TIME, CAPTURED_AT, receipt(SOURCE_URI),
                List.of(filing("0000320193-20-000002", "2020-12-31"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("rootCaptureId must be lowercase SHA-256 hex");

        HistoricalFilingSegmentDescriptor wrongCik = new HistoricalFilingSegmentDescriptor(
                "CIK0000789019-submissions-001.json", 1,
                LocalDate.parse("2020-12-31"), LocalDate.parse("2020-12-31"));
        assertThatThrownBy(() -> segment(wrongCik, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("descriptor fileName must be the CIK-bound SEC submissions fileName");

        HistoricalFilingSegmentDescriptor zeroFile = new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-000.json", 1,
                LocalDate.parse("2020-12-31"), LocalDate.parse("2020-12-31"));
        assertThatThrownBy(() -> segment(zeroFile, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("descriptor fileName must be the CIK-bound SEC submissions fileName");

        URI wrongPath = URI.create(
                "https://data.sec.gov/submissions/CIK0000320193-submissions-002.json");
        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, ROOT_CAPTURE_ID, ROOT_CAPTURED_AT, 0, CIK,
                valid, wrongPath, PROCESSING_TIME, CAPTURED_AT, receipt(wrongPath),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceUri must use the exact historical segment submissions path");

        SourceResponseReceipt wrongReceipt = new SourceResponseReceipt(
                PROVIDER, "other-product", SOURCE_URI, 200, "application/json",
                TransportContentEncoding.IDENTITY, null, null,
                "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1", "0".repeat(64), 1,
                CAPTURED_AT, BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, ROOT_CAPTURE_ID, ROOT_CAPTURED_AT, 0, CIK,
                valid, SOURCE_URI, PROCESSING_TIME, CAPTURED_AT, wrongReceipt,
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceReceipt must identify this exact historical segment capture");
    }

    @Test
    void rejectsImpossibleOrImpreciseKnowledgeTimesAndNegativeOrdinal() {
        HistoricalFilingSegmentDescriptor descriptor = descriptor(
                1, "2020-12-31", "2020-12-31");
        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, ROOT_CAPTURE_ID, ROOT_CAPTURED_AT, -1, CIK,
                descriptor, SOURCE_URI, PROCESSING_TIME, CAPTURED_AT,
                receipt(SOURCE_URI), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("descriptorOrdinal must be nonnegative");

        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, ROOT_CAPTURE_ID, ROOT_CAPTURED_AT, 0, CIK,
                descriptor, SOURCE_URI, ROOT_CAPTURED_AT.minusSeconds(1), CAPTURED_AT,
                receipt(SOURCE_URI), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("processingTime must not precede rootCapturedAt");

        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, ROOT_CAPTURE_ID, ROOT_CAPTURED_AT, 0, CIK,
                descriptor, SOURCE_URI, PROCESSING_TIME, PROCESSING_TIME.minusSeconds(1),
                receipt(SOURCE_URI), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("capturedAt must not precede processingTime");

        assertThatThrownBy(() -> new HistoricalFilingSegment(
                PROVIDER, PRODUCT, ROOT_CAPTURE_ID, ROOT_CAPTURED_AT.plusNanos(1), 0, CIK,
                descriptor, SOURCE_URI, PROCESSING_TIME, CAPTURED_AT,
                receipt(SOURCE_URI), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rootCapturedAt must not exceed microsecond precision");
    }

    private static HistoricalFilingSegment segment(
            HistoricalFilingSegmentDescriptor descriptor,
            List<HistoricalFilingRecord> filings) {
        return new HistoricalFilingSegment(
                PROVIDER,
                PRODUCT,
                ROOT_CAPTURE_ID,
                ROOT_CAPTURED_AT,
                0,
                CIK,
                descriptor,
                SOURCE_URI,
                PROCESSING_TIME,
                CAPTURED_AT,
                receipt(SOURCE_URI),
                filings);
    }

    private static HistoricalFilingSegmentDescriptor descriptor(
            long count,
            String filingFrom,
            String filingTo) {
        return new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-001.json",
                count,
                LocalDate.parse(filingFrom),
                LocalDate.parse(filingTo));
    }

    private static HistoricalFilingRecord filing(
            String accessionNumber,
            String filingDate) {
        String accessionPath = accessionNumber.replace("-", "");
        return new HistoricalFilingRecord(
                accessionNumber,
                accessionNumber,
                "10-K",
                LocalDate.parse(filingDate),
                null,
                LocalDate.parse(filingDate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant(),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + accessionPath + "/form10k.htm"));
    }

    private static SourceResponseReceipt receipt(URI sourceUri) {
        return new SourceResponseReceipt(
                PROVIDER,
                PRODUCT,
                sourceUri,
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                "SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1",
                "0".repeat(64),
                1,
                CAPTURED_AT,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
    }
}
