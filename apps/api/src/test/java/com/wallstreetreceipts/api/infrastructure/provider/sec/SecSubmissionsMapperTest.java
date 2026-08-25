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
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecSubmissionsResponse.SecFilings;
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
    void deserializesVendorShapeAndMapsProviderOrderWithoutInventingNullableReportDate()
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
                    "files": []
                  }
                }
                """;

        SecSubmissionsResponse response =
                new ObjectMapper().readValue(json, SecSubmissionsResponse.class);
        var result = SecSubmissionsMapper.toCanonical(
                response, SOURCE_URI, PROCESSING_TIME, CAPTURED_AT);

        assertThat(result.provider()).isEqualTo("sec-edgar");
        assertThat(result.product()).isEqualTo("edgar-submissions-api");
        assertThat(result.cik()).isEqualTo(CIK);
        assertThat(result.sourceUri()).isEqualTo(SOURCE_URI);
        assertThat(result.processingTime()).isEqualTo(PROCESSING_TIME);
        assertThat(result.capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(result.filings()).hasSize(2);
        assertThat(result.filings().get(0)).satisfies(filing -> {
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
        assertThat(result.filings().get(1).providerEventId())
                .isEqualTo("0000320193-26-000001");
        assertThat(result.filings().get(1).reportDate()).isNull();
    }

    @Test
    void rejectsNumberToStringCoercionForOfficialStringCikField() {
        assertThatThrownBy(() -> new ObjectMapper().readValue(
                "{\"cik\":320193}", SecSubmissionsResponse.class))
                .isInstanceOf(JsonProcessingException.class)
                .hasMessageContaining("cik must be a JSON string");
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
    void rejectsMissingArrayAndPreservesNullReportDate()
    {
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

        var result = SecSubmissionsMapper.toCanonical(
                response(nullValue), SOURCE_URI, PROCESSING_TIME, CAPTURED_AT);
        assertThat(result.filings().get(1).reportDate()).isNull();
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

        List<String> filingDates = List.of("2026-02-30", "2026-08-19");
        SecRecentFilings impossibleDate = copy(
                recent,
                filingDates,
                recent.acceptanceDateTime(),
                recent.primaryDocument());
        assertThatThrownBy(() -> map(impossibleDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("filingDate[0]");
    }

    @Test
    void rejectsNonCanonicalCikSourceIdentityAndCaptureTimeline() {
        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                new SecSubmissionsResponse("12345678901", new SecFilings(validRecent())),
                SOURCE_URI, PROCESSING_TIME, CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10-digit JSON string");

        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                response(validRecent()),
                URI.create("https://data.sec.gov/submissions/CIK0000789019.json"),
                PROCESSING_TIME,
                CAPTURED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requested SEC submissions path");

        assertThatThrownBy(() -> SecSubmissionsMapper.toCanonical(
                response(validRecent()), SOURCE_URI, CAPTURED_AT, PROCESSING_TIME))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("capturedAt must not precede processingTime");
    }

    private static Object map(SecRecentFilings recent) {
        return SecSubmissionsMapper.toCanonical(
                response(recent), SOURCE_URI, PROCESSING_TIME, CAPTURED_AT);
    }

    private static SecSubmissionsResponse response(SecRecentFilings recent) {
        return new SecSubmissionsResponse(CIK, new SecFilings(recent));
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
