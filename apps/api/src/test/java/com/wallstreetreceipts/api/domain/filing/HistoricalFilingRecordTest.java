package com.wallstreetreceipts.api.domain.filing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class HistoricalFilingRecordTest {

    private static final String ACCESSION = "0001193125-15-000001";
    private static final LocalDate FILING_DATE = LocalDate.parse("2015-01-01");
    private static final Instant ACCEPTED_AT =
            Instant.parse("2015-01-01T12:34:56.123456Z");
    private static final URI DOCUMENT_URI = URI.create(
            "https://www.sec.gov/Archives/edgar/data/320193/"
                    + "000119312515000001/xslF345X06/form4.xml");

    @Test
    void preservesAnObservedMissingPrimaryDocumentWithoutInventingAUri() {
        HistoricalFilingRecord result = record(null);

        assertThat(result.providerEventId()).isEqualTo(ACCESSION);
        assertThat(result.accessionNumber()).isEqualTo(ACCESSION);
        assertThat(result.primaryDocumentUri()).isNull();
        result.requireCatalogArchiveIdentity("0000320193");
    }

    @Test
    void acceptsAndValidatesAnExactCikBoundSecArchiveDocument() {
        HistoricalFilingRecord result = record(DOCUMENT_URI);

        assertThat(result.primaryDocumentUri()).isEqualTo(DOCUMENT_URI);
        result.requireCatalogArchiveIdentity("0000320193");
        assertThatThrownBy(() -> result.requireCatalogArchiveIdentity("0000789019"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("primaryDocumentUri must use the catalog cik and accessionNumber");
    }

    @Test
    void rejectsInventedNonCanonicalDocumentsAndInvalidEventIdentity() {
        assertThatThrownBy(() -> record(URI.create(
                "https://example.test/Archives/edgar/data/320193/"
                        + "000119312515000001/form4.xml")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical SEC Archives URI");

        assertThatThrownBy(() -> new HistoricalFilingRecord(
                "different-event", ACCESSION, "4", FILING_DATE, null,
                ACCEPTED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("providerEventId must equal the exact SEC accessionNumber");

        assertThatThrownBy(() -> new HistoricalFilingRecord(
                "not-an-accession", "not-an-accession", "4", FILING_DATE, null,
                ACCEPTED_AT, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("accessionNumber must use the SEC 10-2-6 digit format");
    }

    @Test
    void rejectsMissingFieldsAndSubMicrosecondAcceptanceTimesEvenWithoutADocument() {
        assertThatThrownBy(() -> new HistoricalFilingRecord(
                ACCESSION, ACCESSION, "4", null, null, ACCEPTED_AT, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("filingDate must not be null");

        assertThatThrownBy(() -> new HistoricalFilingRecord(
                ACCESSION, ACCESSION, "4", FILING_DATE, null,
                ACCEPTED_AT.plusNanos(1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("acceptedAt must not exceed microsecond precision");
    }

    private static HistoricalFilingRecord record(URI primaryDocumentUri) {
        return new HistoricalFilingRecord(
                ACCESSION,
                ACCESSION,
                "4",
                FILING_DATE,
                null,
                ACCEPTED_AT,
                primaryDocumentUri);
    }
}
