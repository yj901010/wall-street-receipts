package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog.HistoricalSegmentStatus;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRepresentation;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.TransportContentEncoding;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecSubmissionsResponse.SecFilings;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecSubmissionsResponse.SecHistoricalFilingFile;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecSubmissionsResponse.SecRecentFilings;

class SecSubmissionsMapperTest {

    private static final String CIK = "0000320193";
    private static final URI SOURCE_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193.json");
    private static final Instant PROCESSING_TIME =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.654321Z");

    @Test
    void deserializesVendorShapeAndPreservesRecentAndHistoricalProviderOrder()
            throws Exception {
        String json = """
                {
                  "cik": "0000320193",
                  "name": "Apple Inc.",
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0001193125-26-000002", "0000320193-26-000001"],
                      "filingDate": ["2026-08-20", "2026-08-19"],
                      "reportDate": ["2026-06-27", ""],
                      "acceptanceDateTime": ["2026-08-20T13:14:15.123456Z", "2026-08-19T10:11:12Z"],
                      "act": ["34", "34"],
                      "form": ["10-Q", "8-K"],
                      "fileNumber": ["001-36743", "001-36743"],
                      "filmNumber": ["261234567", "261234568"],
                      "items": ["", "2.02"],
                      "size": [12345, 6789],
                      "isXBRL": [1, 1],
                      "isInlineXBRL": [1, 1],
                      "primaryDocument": ["xslF345X06/form4.xml", "aapl-20260819.htm"],
                      "primaryDocDescription": ["10-Q", "8-K"]
                    },
                    "files": [
                      {
                        "name": "CIK0000320193-submissions-002.json",
                        "filingCount": 1500,
                        "filingFrom": "2018-01-01",
                        "filingTo": "2020-12-31"
                      },
                      {
                        "name": "CIK0000320193-submissions-001.json",
                        "filingCount": 2000,
                        "filingFrom": "2015-01-01",
                        "filingTo": "2017-12-31"
                      }
                    ]
                  }
                }
                """;

        SecSubmissionsResponse response =
                new ObjectMapper().readValue(json, SecSubmissionsResponse.class);
        FilingCatalog result = SecSubmissionsMapper.toCanonical(
                response, receipt(CAPTURED_AT), PROCESSING_TIME);

        assertThat(result.provider()).isEqualTo("sec-edgar");
        assertThat(result.product()).isEqualTo("edgar-submissions-api");
        assertThat(result.cik()).isEqualTo(CIK);
        assertThat(result.sourceUri()).isEqualTo(SOURCE_URI);
        assertThat(result.processingTime()).isEqualTo(PROCESSING_TIME);
        assertThat(result.capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(result.sourceReceipt().parserVersion())
                .isEqualTo(SecSubmissionsMapper.PARSER_VERSION);
        assertThat(result.recentFilings()).hasSize(2);
        assertThat(result.recentFilings().get(0)).satisfies(filing -> {
            assertThat(filing.providerEventId()).isEqualTo("0001193125-26-000002");
            assertThat(filing.accessionNumber()).isEqualTo("0001193125-26-000002");
            assertThat(filing.form()).isEqualTo("10-Q");
            assertThat(filing.filingDate()).isEqualTo(LocalDate.parse("2026-08-20"));
            assertThat(filing.reportDate()).isEqualTo(LocalDate.parse("2026-06-27"));
            assertThat(filing.acceptedAt())
                    .isEqualTo(Instant.parse("2026-08-20T13:14:15.123456Z"));
            assertThat(filing.primaryDocumentUri()).isEqualTo(URI.create(
                    "https://www.sec.gov/Archives/edgar/data/320193/"
                            + "000119312526000002/xslF345X06/form4.xml"));
        });
        assertThat(result.recentFilings().get(1).providerEventId())
                .isEqualTo("0000320193-26-000001");
        assertThat(result.recentFilings().get(1).reportDate()).isNull();
        assertThat(result.historicalSegments())
                .extracting(segment -> segment.fileName())
                .containsExactly(
                        "CIK0000320193-submissions-002.json",
                        "CIK0000320193-submissions-001.json");
        assertThat(result.historicalSegments().get(0)).satisfies(segment -> {
            assertThat(segment.advertisedFilingCount()).isEqualTo(1_500);
            assertThat(segment.advertisedFilingFrom())
                    .isEqualTo(LocalDate.parse("2018-01-01"));
            assertThat(segment.advertisedFilingTo())
                    .isEqualTo(LocalDate.parse("2020-12-31"));
        });
        assertThat(result.historicalSegmentStatus())
                .isEqualTo(
                        HistoricalSegmentStatus
                                .RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED);
    }

    @Test
    void rejectsNumberToStringCoercionForOfficialStringCikField() {
        assertThatThrownBy(() -> new ObjectMapper().readValue(
                "{\"cik\":320193}", SecSubmissionsResponse.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("cik must be a JSON string");
    }

    @Test
    void rejectsMissingTopLevelSectionsAndNullHistoricalEntries() {
        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                null, receipt(CAPTURED_AT), PROCESSING_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("source must not be null");
        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                response(validRecent()), null, PROCESSING_TIME))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("sourceReceipt must not be null");
        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                new SecSubmissionsResponse(CIK, null),
                receipt(CAPTURED_AT), PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filings must be present");
        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                new SecSubmissionsResponse(CIK, new SecFilings(null, List.of())),
                receipt(CAPTURED_AT), PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filings.recent must be present");
        assertThatThrownBy(() -> map(validRecent(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filings.files must be present");

        List<SecHistoricalFilingFile> filesWithNull = new ArrayList<>();
        filesWithNull.add(null);
        assertThatThrownBy(() -> map(validRecent(), filesWithNull))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("filings.files[0] must not be null");
    }

    @Test
    void rejectsMissingBlankAndUntrimmedHistoricalFileNames() {
        assertHistoricalRejected(
                new SecHistoricalFilingFile(null, 1L, "2020-01-01", "2020-12-31"),
                "name",
                "must be nonblank and trimmed");
        for (String fileName : List.of(
                "",
                " ",
                " CIK0000320193-submissions-001.json",
                "CIK0000320193-submissions-001.json ")) {
            assertHistoricalRejected(
                    new SecHistoricalFilingFile(
                            fileName, 1L, "2020-01-01", "2020-12-31"),
                    "name",
                    "must be nonblank and trimmed");
        }
    }

    @Test
    void rejectsHistoricalFileNamesWithWrongCikOrdinalOrPathSyntax() {
        for (String fileName : List.of(
                "CIK0000789019-submissions-001.json",
                "CIK0000320193-submissions-000.json",
                "CIK0000320193-submissions-01.json",
                "CIK0000320193-submissions-0001.json",
                "cik0000320193-submissions-001.json",
                "CIK0000320193-submissions-001.JSON",
                "../CIK0000320193-submissions-001.json",
                "dir/CIK0000320193-submissions-001.json",
                "dir\\CIK0000320193-submissions-001.json",
                "%2e%2e-CIK0000320193-submissions-001.json",
                "CIK0000320193-submissions-001.json?download=1",
                "CIK0000320193-submissions-001.json#fragment",
                "CIK0000320193-submissions-001.json.bak")) {
            assertHistoricalRejected(
                    new SecHistoricalFilingFile(
                            fileName, 1L, "2020-01-01", "2020-12-31"),
                    "name",
                    "must be the catalog CIK-bound SEC submissions fileName");
        }
    }

    @Test
    void rejectsMissingAndNonPositiveHistoricalCounts() {
        for (Long count : new Long[] {null, 0L, -1L, Long.MIN_VALUE}) {
            assertHistoricalRejected(
                    new SecHistoricalFilingFile(
                            "CIK0000320193-submissions-001.json",
                            count,
                            "2020-01-01",
                            "2020-12-31"),
                    "filingCount",
                    "must be positive");
        }
    }

    @Test
    void rejectsMissingNonCanonicalImpossibleAndReversedHistoricalDates() {
        assertHistoricalRejected(
                new SecHistoricalFilingFile(
                        "CIK0000320193-submissions-001.json",
                        1L,
                        null,
                        "2020-12-31"),
                "filingFrom",
                "must be nonblank and trimmed");
        assertHistoricalRejected(
                new SecHistoricalFilingFile(
                        "CIK0000320193-submissions-001.json",
                        1L,
                        "2020-01-01",
                        null),
                "filingTo",
                "must be nonblank and trimmed");

        for (String filingFrom : List.of(
                "",
                " ",
                " 2020-01-01",
                "2020-01-01 ",
                "2020-1-01",
                "2020-02-30",
                "2020-01-01T00:00:00Z")) {
            assertHistoricalRejected(
                    new SecHistoricalFilingFile(
                            "CIK0000320193-submissions-001.json",
                            1L,
                            filingFrom,
                            "2020-12-31"),
                    "filingFrom",
                    filingFrom.isBlank() || !filingFrom.equals(filingFrom.strip())
                            ? "must be nonblank and trimmed"
                            : "must be an ISO-8601 calendar date");
        }

        assertHistoricalRejected(
                new SecHistoricalFilingFile(
                        "CIK0000320193-submissions-001.json",
                        1L,
                        "2020-12-31",
                        "2020-01-01"),
                "filingTo",
                "must not precede filingFrom");

        FilingCatalog singleDay = map(
                validRecent(),
                List.of(new SecHistoricalFilingFile(
                        "CIK0000320193-submissions-001.json",
                        1L,
                        "2020-01-01",
                        "2020-01-01")));
        assertThat(singleDay.historicalSegments().getFirst().advertisedFilingFrom())
                .isEqualTo(singleDay.historicalSegments().getFirst().advertisedFilingTo());
    }

    @Test
    void rejectsDuplicateHistoricalFileNameButPreservesOverlapAndProviderOrder() {
        SecHistoricalFilingFile duplicate = historicalFile(
                "001", 2_000, "2015-01-01", "2019-12-31");
        assertThatThrownBy(() -> map(validRecent(), List.of(duplicate, duplicate)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filings.files[1].name")
                .hasMessageContaining("unique within filings.files");

        SecHistoricalFilingFile secondProviderFile = historicalFile(
                "002", 500, "2026-08-20", "2026-12-31");
        SecHistoricalFilingFile firstProviderFile = historicalFile(
                "001", 2_000, "2015-01-01", "2026-08-20");
        FilingCatalog overlapping = map(
                validRecent(), List.of(secondProviderFile, firstProviderFile));

        assertThat(overlapping.historicalSegments())
                .extracting(segment -> segment.fileName())
                .containsExactly(
                        secondProviderFile.name(),
                        firstProviderFile.name());
        assertThat(overlapping.hasAdvertisedHistoricalDateRangeOverlap()).isTrue();
        assertThat(overlapping.hasAdvertisedRecentHistoricalDateOverlap()).isTrue();
    }

    @Test
    void rejectsEveryParallelArrayLengthMismatchBeforeMappingAnyRow() {
        SecRecentFilings recent = validRecent();
        SecRecentFilings mismatch = copy(
                recent,
                List.of("0000320193-26-000002"),
                recent.acceptanceDateTime(),
                recent.primaryDocument());

        assertThatThrownBy(() -> map(mismatch))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identical lengths")
                .hasMessageContaining("filingDate");
    }

    @Test
    void rejectsMissingArrayAndPreservesNullReportDate() {
        SecRecentFilings recent = validRecent();
        SecRecentFilings missing = new SecRecentFilings(
                recent.accessionNumber(), recent.filingDate(), recent.reportDate(),
                recent.acceptanceDateTime(), recent.act(), recent.form(), recent.fileNumber(),
                recent.filmNumber(), recent.items(), null, recent.isXBRL(),
                recent.isInlineXBRL(), recent.primaryDocument(), recent.primaryDocDescription());

        assertThatThrownBy(() -> map(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size must be present");

        List<String> reportDates = new ArrayList<>(recent.reportDate());
        reportDates.set(1, null);
        SecRecentFilings nullValue = new SecRecentFilings(
                recent.accessionNumber(), recent.filingDate(), reportDates,
                recent.acceptanceDateTime(), recent.act(), recent.form(), recent.fileNumber(),
                recent.filmNumber(), recent.items(), recent.size(), recent.isXBRL(),
                recent.isInlineXBRL(), recent.primaryDocument(), recent.primaryDocDescription());

        FilingCatalog result = SecSubmissionsMapper.toCanonical(
                response(nullValue), receipt(CAPTURED_AT), PROCESSING_TIME);
        assertThat(result.recentFilings().get(1).reportDate()).isNull();
    }

    @Test
    void rejectsInvalidDatesExcessTimestampPrecisionAndUnsafeDocumentPath() {
        SecRecentFilings recent = validRecent();
        SecRecentFilings invalidDate = copy(
                recent,
                recent.filingDate(),
                List.of("2026-08-20T13:14:15.123456789Z", "2026-08-19T10:11:12Z"),
                recent.primaryDocument());
        assertThatThrownBy(() -> map(invalidDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");

        for (String path : List.of(
                "../aapl.htm", "%2e%2e/aapl.htm", "/aapl.htm",
                "xslF345X06\\form4.xml", "xslF345X06//form4.xml")) {
            SecRecentFilings unsafePath = copy(
                    recent,
                    recent.filingDate(),
                    recent.acceptanceDateTime(),
                    List.of(path, "aapl-20260819.htm"));
            assertThatThrownBy(() -> map(unsafePath))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("primaryDocument[0]");
        }

        SecRecentFilings impossibleDate = copy(
                recent,
                List.of("2026-02-30", "2026-08-19"),
                recent.acceptanceDateTime(),
                recent.primaryDocument());
        assertThatThrownBy(() -> map(impossibleDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filingDate[0]");
    }

    @Test
    void rejectsNonCanonicalCikSourceIdentityAndCaptureTimeline() {
        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                new SecSubmissionsResponse(
                        "12345678901", new SecFilings(validRecent(), List.of())),
                receipt(CAPTURED_AT), PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit JSON string");

        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                response(validRecent()),
                receipt(
                        URI.create("https://data.sec.gov/submissions/CIK0000789019.json"),
                        CAPTURED_AT),
                PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requested SEC submissions path");

        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                response(validRecent()), receipt(PROCESSING_TIME), CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt must not precede processingTime");
    }

    @Test
    void rejectsV1ReceiptAndAcceptsV2ReceiptWithoutHistoricalFiles() {
        SourceResponseReceipt v1Receipt = receipt(
                SOURCE_URI, CAPTURED_AT, "SEC_SUBMISSIONS_RECENT_V1");

        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                response(validRecent()), v1Receipt, PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("sourceReceipt must use the SEC submissions parser identity");

        FilingCatalog v2Catalog = SecSubmissionsMapper.toCanonical(
                response(validRecent()), receipt(CAPTURED_AT), PROCESSING_TIME);
        assertThat(v2Catalog.sourceReceipt().parserVersion())
                .isEqualTo("SEC_SUBMISSIONS_CATALOG_V2");
        assertThat(v2Catalog.historicalSegments()).isEmpty();
        assertThat(v2Catalog.historicalSegmentStatus())
                .isEqualTo(HistoricalSegmentStatus.RECENT_ONLY_NO_SEGMENTS_ADVERTISED);
    }

    private static void assertHistoricalRejected(
            SecHistoricalFilingFile file,
            String field,
            String detail) {
        assertThatThrownBy(() -> map(validRecent(), List.of(file)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filings.files[0]." + field)
                .hasMessageContaining(detail);
    }

    private static FilingCatalog map(SecRecentFilings recent) {
        return map(recent, List.of());
    }

    private static FilingCatalog map(
            SecRecentFilings recent,
            List<SecHistoricalFilingFile> files) {
        return SecSubmissionsMapper.toCanonical(
                response(recent, files), receipt(CAPTURED_AT), PROCESSING_TIME);
    }

    private static SecSubmissionsResponse response(SecRecentFilings recent) {
        return response(recent, List.of());
    }

    private static SecSubmissionsResponse response(
            SecRecentFilings recent,
            List<SecHistoricalFilingFile> files) {
        return new SecSubmissionsResponse(CIK, new SecFilings(recent, files));
    }

    private static SecHistoricalFilingFile historicalFile(
            String ordinal,
            long count,
            String filingFrom,
            String filingTo) {
        return new SecHistoricalFilingFile(
                "CIK" + CIK + "-submissions-" + ordinal + ".json",
                count,
                filingFrom,
                filingTo);
    }

    private static SourceResponseReceipt receipt(Instant capturedAt) {
        return receipt(SOURCE_URI, capturedAt);
    }

    private static SourceResponseReceipt receipt(URI sourceUri, Instant capturedAt) {
        return receipt(sourceUri, capturedAt, SecSubmissionsMapper.PARSER_VERSION);
    }

    private static SourceResponseReceipt receipt(
            URI sourceUri,
            Instant capturedAt,
            String parserVersion) {
        return new SourceResponseReceipt(
                SecSubmissionsMapper.PROVIDER_NAME,
                SecSubmissionsMapper.PRODUCT_NAME,
                sourceUri,
                200,
                "application/json",
                TransportContentEncoding.IDENTITY,
                null,
                null,
                parserVersion,
                "0".repeat(64),
                1,
                capturedAt,
                BodyRepresentation.DECODED_HTTP_ENTITY_BODY,
                BodyRetention.RECEIPT_ONLY_BODY_NOT_RETAINED);
    }

    private static SecRecentFilings validRecent() {
        return new SecRecentFilings(
                List.of("0000320193-26-000002", "0000320193-26-000001"),
                List.of("2026-08-20", "2026-08-19"),
                List.of("2026-06-27", ""),
                List.of("2026-08-20T13:14:15.123456Z", "2026-08-19T10:11:12Z"),
                List.of("34", "34"),
                List.of("10-Q", "8-K"),
                List.of("001-36743", "001-36743"),
                List.of("261234567", "261234568"),
                List.of("", "2.02"),
                List.of(12345L, 6789L),
                List.of(1, 1),
                List.of(1, 1),
                List.of("aapl-20260627.htm", "aapl-20260819.htm"),
                List.of("10-Q", "8-K"));
    }

    private static SecRecentFilings copy(
            SecRecentFilings source,
            List<String> filingDates,
            List<String> acceptanceDateTimes,
            List<String> primaryDocuments) {
        return new SecRecentFilings(
                source.accessionNumber(), filingDates, source.reportDate(), acceptanceDateTimes,
                source.act(), source.form(), source.fileNumber(), source.filmNumber(), source.items(),
                source.size(), source.isXBRL(), source.isInlineXBRL(), primaryDocuments,
                source.primaryDocDescription());
    }
}
