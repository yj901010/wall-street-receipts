package com.wallstreetreceipts.api.domain.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.wallstreetreceipts.api.domain.filing.FilingCatalog.HistoricalSegmentStatus;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;

class FilingCatalogTest {

    private static final Instant ACCEPTED_AT =
            Instant.parse("2026-08-20T13:14:15.123456Z");
    private static final Instant PROCESSING_TIME =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.654321Z");

    @Test
    void rejectsDuplicateProviderEventIdentity() {
        FilingRecord filing = filing("0000320193-26-000002", "2026-08-20");

        assertThatThrownBy(() -> catalog(List.of(filing, filing), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerEventId must be unique");
    }

    @Test
    void preservesProviderOrderAndDefensivelyCopiesBothCatalogSections() {
        FilingRecord first = filing("0000320193-26-000002", "2026-08-20");
        FilingRecord second = filing("0000320193-26-000001", "2026-08-19");
        HistoricalFilingSegmentDescriptor segmentTwo = segment(
                "002", "2018-01-01", "2019-12-31");
        HistoricalFilingSegmentDescriptor segmentOne = segment(
                "001", "2015-01-01", "2017-12-31");
        List<FilingRecord> recentInput = new ArrayList<>(List.of(first, second));
        List<HistoricalFilingSegmentDescriptor> historicalInput =
                new ArrayList<>(List.of(segmentTwo, segmentOne));

        FilingCatalog result = catalog(recentInput, historicalInput);
        recentInput.clear();
        historicalInput.clear();

        assertThat(result.recentFilings()).containsExactly(first, second);
        assertThat(result.historicalSegments()).containsExactly(segmentTwo, segmentOne);
        assertThatThrownBy(() -> result.recentFilings().add(first))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.historicalSegments().add(segmentOne))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void preservesAccessionWhenSubmittingEntityPrefixDiffersFromCatalogCik() {
        FilingRecord filing = filing("0001193125-26-000002", "2026-08-20");

        assertThat(catalog(List.of(filing), List.of())
                .recentFilings().getFirst().accessionNumber())
                .isEqualTo("0001193125-26-000002");
    }

    @Test
    void acceptsCanonicalNestedPrimaryDocumentPath() {
        FilingRecord filing = new FilingRecord(
                "0001193125-26-000002", "0001193125-26-000002", "4",
                LocalDate.parse("2026-08-20"), null, ACCEPTED_AT,
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000119312526000002/xslF345X06/form4.xml"));

        assertThat(catalog(List.of(filing), List.of())
                .recentFilings().getFirst().primaryDocumentUri())
                .hasToString("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000119312526000002/xslF345X06/form4.xml");
    }

    @Test
    void rejectsNonCanonicalPrimaryDocumentUri() {
        assertThatThrownBy(() -> new FilingRecord(
                "different-provider-event", "0000320193-26-000002", "10-Q",
                LocalDate.parse("2026-08-20"), null, ACCEPTED_AT,
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000032019326000002/aapl.htm")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerEventId must equal");

        assertThatThrownBy(() -> new FilingRecord(
                "0000320193-26-000002", "0000320193-26-000002", "10-Q",
                LocalDate.parse("2026-08-20"), null, ACCEPTED_AT,
                URI.create("https://example.test/Archives/edgar/data/320193/"
                        + "000032019326000002/aapl.htm")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical SEC Archives URI");
    }

    @Test
    void reportsAdvertisedHistoricalStateAndPreservesInclusiveOverlaps() {
        FilingCatalog recentOnly = catalog(
                List.of(filing("0000320193-26-000002", "2026-08-20")),
                List.of());
        assertThat(recentOnly.historicalSegmentStatus())
                .isEqualTo(HistoricalSegmentStatus.RECENT_ONLY_NO_SEGMENTS_ADVERTISED);
        assertThat(recentOnly.hasAdvertisedHistoricalDateRangeOverlap()).isFalse();
        assertThat(recentOnly.hasAdvertisedRecentHistoricalDateOverlap()).isFalse();

        HistoricalFilingSegmentDescriptor first = segment(
                "002", "2020-01-01", "2026-08-20");
        HistoricalFilingSegmentDescriptor second = segment(
                "001", "2026-08-20", "2026-12-31");
        FilingCatalog overlapping = catalog(
                List.of(filing("0000320193-26-000002", "2026-08-20")),
                List.of(first, second));

        assertThat(overlapping.historicalSegments()).containsExactly(first, second);
        assertThat(overlapping.historicalSegmentStatus())
                .isEqualTo(
                        HistoricalSegmentStatus
                                .RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED);
        assertThat(overlapping.hasAdvertisedHistoricalDateRangeOverlap()).isTrue();
        assertThat(overlapping.hasAdvertisedRecentHistoricalDateOverlap()).isTrue();

        FilingCatalog disjoint = catalog(
                List.of(filing("0000320193-26-000002", "2026-08-20")),
                List.of(
                        segment("001", "2015-01-01", "2017-12-31"),
                        segment("002", "2018-01-01", "2019-12-31")));
        assertThat(disjoint.hasAdvertisedHistoricalDateRangeOverlap()).isFalse();
        assertThat(disjoint.hasAdvertisedRecentHistoricalDateOverlap()).isFalse();
    }

    @Test
    void rejectsMissingNullAndDuplicateCatalogSections() {
        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar", "edgar-submissions-api", "0000320193",
                sourceUri(), PROCESSING_TIME, CAPTURED_AT, receipt(), null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("recentFilings must not be null");
        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar", "edgar-submissions-api", "0000320193",
                sourceUri(), PROCESSING_TIME, CAPTURED_AT, receipt(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("historicalSegments must not be null");

        List<FilingRecord> recentWithNull = new ArrayList<>();
        recentWithNull.add(null);
        assertThatThrownBy(() -> catalog(recentWithNull, List.of()))
                .isInstanceOf(NullPointerException.class);

        List<HistoricalFilingSegmentDescriptor> historicalWithNull = new ArrayList<>();
        historicalWithNull.add(null);
        assertThatThrownBy(() -> catalog(List.of(), historicalWithNull))
                .isInstanceOf(NullPointerException.class);

        HistoricalFilingSegmentDescriptor duplicate = segment(
                "001", "2015-01-01", "2017-12-31");
        assertThatThrownBy(() -> catalog(List.of(), List.of(duplicate, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("historical fileName must be unique within the catalog");
    }

    @Test
    void rejectsSubMicrosecondAndImpossiblePointInTimeRelationships() {
        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar", " ", "0000320193", sourceUri(),
                PROCESSING_TIME, CAPTURED_AT, receipt(), List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product must be nonblank and trimmed");

        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar", "edgar-submissions-api", "0000320193", sourceUri(),
                PROCESSING_TIME.plusNanos(1), CAPTURED_AT, receipt(),
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");

        FilingRecord future = new FilingRecord(
                "0000320193-26-000002", "0000320193-26-000002", "10-Q",
                LocalDate.parse("2026-08-20"), null, PROCESSING_TIME.plusSeconds(1),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000032019326000002/aapl.htm"));
        assertThatThrownBy(() -> catalog(List.of(future), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTime must not precede filing acceptedAt");
    }

    @Test
    void rejectsAReceiptForADifferentCatalogCapture() {
        SourceResponseReceipt wrongCapture = new SourceResponseReceipt(
                "sec-edgar",
                "edgar-submissions-api",
                URI.create("https://data.sec.gov/submissions/CIK0000789019.json"),
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                "SEC_SUBMISSIONS_CATALOG_V2",
                "0".repeat(64),
                1,
                CAPTURED_AT,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED);

        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar",
                "edgar-submissions-api",
                "0000320193",
                sourceUri(),
                PROCESSING_TIME,
                CAPTURED_AT,
                wrongCapture,
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceReceipt must identify this exact catalog capture");
    }

    private static FilingCatalog catalog(
            List<FilingRecord> recentFilings,
            List<HistoricalFilingSegmentDescriptor> historicalSegments) {
        return new FilingCatalog(
                "sec-edgar", "edgar-submissions-api", "0000320193",
                sourceUri(), PROCESSING_TIME, CAPTURED_AT, receipt(),
                recentFilings, historicalSegments);
    }

    private static HistoricalFilingSegmentDescriptor segment(
            String ordinal,
            String filingFrom,
            String filingTo) {
        return new HistoricalFilingSegmentDescriptor(
                "CIK0000320193-submissions-" + ordinal + ".json",
                2_000,
                LocalDate.parse(filingFrom),
                LocalDate.parse(filingTo));
    }

    private static SourceResponseReceipt receipt() {
        return new SourceResponseReceipt(
                "sec-edgar",
                "edgar-submissions-api",
                sourceUri(),
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                "SEC_SUBMISSIONS_CATALOG_V2",
                "0".repeat(64),
                1,
                CAPTURED_AT,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED);
    }

    private static URI sourceUri() {
        return URI.create("https://data.sec.gov/submissions/CIK0000320193.json");
    }

    private static FilingRecord filing(String accessionNumber, String filingDate) {
        String accessionPath = accessionNumber.replace("-", "");
        return new FilingRecord(
                accessionNumber, accessionNumber, "10-Q",
                LocalDate.parse(filingDate), LocalDate.parse("2026-06-27"),
                ACCEPTED_AT,
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + accessionPath + "/aapl.htm"));
    }
}
