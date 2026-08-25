package com.wallstreetreceipts.api.domain.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class FilingCatalogTest {

    private static final Instant ACCEPTED_AT =
            Instant.parse("2026-08-20T13:14:15.123456Z");
    private static final Instant PROCESSING_TIME =
            Instant.parse("2026-08-25T01:02:03.123456Z");
    private static final Instant CAPTURED_AT =
            Instant.parse("2026-08-25T01:02:03.654321Z");

    @Test
    void rejectsDuplicateProviderEventIdentity() {
        FilingRecord filing = filing("0000320193-26-000002");

        assertThatThrownBy(() -> catalog(List.of(filing, filing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("providerEventId must be unique");
    }

    @Test
    void preservesAccessionWhenSubmittingEntityPrefixDiffersFromCatalogCik() {
        FilingRecord filing = filing("0001193125-26-000002");

        assertThat(catalog(List.of(filing)).filings().getFirst().accessionNumber())
                .isEqualTo("0001193125-26-000002");
    }

    @Test
    void acceptsCanonicalNestedPrimaryDocumentPath() {
        FilingRecord filing = new FilingRecord(
                "0001193125-26-000002", "0001193125-26-000002", "4",
                LocalDate.parse("2026-08-20"), null, ACCEPTED_AT,
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000119312526000002/xslF345X06/form4.xml"));

        assertThat(catalog(List.of(filing)).filings().getFirst().primaryDocumentUri())
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
    void rejectsSubMicrosecondAndImpossiblePointInTimeRelationships() {
        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar", " ", "0000320193",
                URI.create("https://data.sec.gov/submissions/CIK0000320193.json"),
                PROCESSING_TIME, CAPTURED_AT, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("product must be nonblank and trimmed");

        assertThatThrownBy(() -> new FilingCatalog(
                "sec-edgar", "edgar-submissions-api", "0000320193",
                URI.create("https://data.sec.gov/submissions/CIK0000320193.json"),
                PROCESSING_TIME.plusNanos(1), CAPTURED_AT, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("microsecond precision");

        FilingRecord future = new FilingRecord(
                "0000320193-26-000002", "0000320193-26-000002", "10-Q",
                LocalDate.parse("2026-08-20"), null, PROCESSING_TIME.plusSeconds(1),
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000032019326000002/aapl.htm"));
        assertThatThrownBy(() -> catalog(List.of(future)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("processingTime must not precede filing acceptedAt");
    }

    private static FilingCatalog catalog(List<FilingRecord> filings) {
        return new FilingCatalog(
                "sec-edgar", "edgar-submissions-api", "0000320193",
                URI.create("https://data.sec.gov/submissions/CIK0000320193.json"),
                PROCESSING_TIME, CAPTURED_AT, filings);
    }

    private static FilingRecord filing(String accessionNumber) {
        String accessionPath = accessionNumber.replace("-", "");
        return new FilingRecord(
                accessionNumber, accessionNumber, "10-Q",
                LocalDate.parse("2026-08-20"), LocalDate.parse("2026-06-27"),
                ACCEPTED_AT,
                URI.create("https://www.sec.gov/Archives/edgar/data/320193/"
                        + accessionPath + "/aapl.htm"));
    }
}
