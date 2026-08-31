package com.wallstreetreceipts.api.web.filinghistory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditPage;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditResult;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;
import com.wallstreetreceipts.api.support.FilingHistoryCollectionTestFixture;
import com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture;

/** Locks the web DEMO artifact to the real domain assembly and ADR-052 mapper. */
class SecAuditDemoFixtureParityTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:00:00.123456Z");
    private static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:10:00.123456Z");
    private static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:20:00.123456Z");
    private static final Instant ASSEMBLED_AT =
            Instant.parse("2026-08-25T03:30:00.123456Z");
    private static final int PAGE_SIZE = 25;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void committedWebFixtureExactlyMatchesJavaAssemblyAndAuditResponses()
            throws IOException {
        FilingCatalogCapture root = SecFilingCatalogCaptureTestFixture
                .capture(ROOT_CAPTURED_AT)
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        HistoricalFilingSegmentCapture first = FilingHistoryCollectionTestFixture
                .segmentCapture(
                        root,
                        0,
                        FIRST_SEGMENT_CAPTURED_AT,
                        List.of(agreement(), conflictWithoutDocument()))
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        HistoricalFilingSegmentCapture second = FilingHistoryCollectionTestFixture
                .segmentCapture(
                        root,
                        1,
                        SECOND_SEGMENT_CAPTURED_AT,
                        List.of(agreement(), conflictWithDocument()))
                .withBodyRetention(BodyRetention.DURABLE_DECODED_BODY_RETAINED);
        FilingHistoryCollectionManifest manifest = FilingHistoryCollectionManifest
                .assemble(root, List.of(first, second), ASSEMBLED_AT);
        AuditResult audit = new AuditResult(manifest, ASSEMBLED_AT);

        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("fixtureSchemaVersion", "1.0.0");
        generated.put("generatedBy", "java-domain-and-adr-052-response-mapper");
        generated.put("manifestId", manifest.manifestId());
        generated.put("evaluationAsOf", ASSEMBLED_AT);
        generated.put("summary", SecFilingHistoryManifestAuditResponses.summary(audit));
        generated.put("descriptors", SecFilingHistoryManifestAuditResponses.descriptors(
                page(audit, manifest.descriptors(), "descriptorOrdinal")));
        generated.put("accessions", SecFilingHistoryManifestAuditResponses.accessions(
                page(audit, manifest.accessionGroups(), "groupOrdinal")));
        generated.put("occurrences", SecFilingHistoryManifestAuditResponses.occurrences(
                page(audit, manifest.occurrences(), "occurrenceOrdinal")));

        Path committedFixture = Path.of(System.getProperty("user.dir"))
                .resolve("../web/src/lib/providers/fixtures/sec-manifest-audit-demo.json")
                .normalize();
        assertThat(committedFixture).isRegularFile();
        JsonNode expected = OBJECT_MAPPER.readTree(Files.readAllBytes(committedFixture));
        JsonNode actual = OBJECT_MAPPER.readTree(
                OBJECT_MAPPER.writeValueAsBytes(generated));

        assertThat(actual).isEqualTo(expected);
    }

    private static HistoricalFilingRecord agreement() {
        return historical(
                "0000320193-14-000101",
                "10-K",
                "2014-12-31",
                "2014-09-27",
                "2014-12-31T20:00:00.123456Z",
                null);
    }

    private static HistoricalFilingRecord conflictWithoutDocument() {
        return historical(
                "0000320193-14-000102",
                "8-K",
                "2014-12-30",
                null,
                "2014-12-30T15:30:00.123456Z",
                null);
    }

    private static HistoricalFilingRecord conflictWithDocument() {
        return historical(
                "0000320193-14-000102",
                "8-K",
                "2014-12-30",
                null,
                "2014-12-30T15:30:00.123456Z",
                "https://www.sec.gov/Archives/edgar/data/320193/"
                        + "000032019314000102/conflict8k.htm");
    }

    private static HistoricalFilingRecord historical(
            String accession,
            String form,
            String filingDate,
            String reportDate,
            String acceptedAt,
            String primaryDocumentUri) {
        return new HistoricalFilingRecord(
                accession,
                accession,
                form,
                LocalDate.parse(filingDate),
                reportDate == null ? null : LocalDate.parse(reportDate),
                Instant.parse(acceptedAt),
                primaryDocumentUri == null ? null : URI.create(primaryDocumentUri));
    }

    private static <T> AuditPage<T> page(
            AuditResult audit,
            List<T> items,
            String orderField) {
        int totalPages = items.isEmpty() ? 0 : 1;
        return new AuditPage<>(
                audit,
                items,
                0,
                PAGE_SIZE,
                items.size(),
                totalPages,
                true,
                true,
                orderField);
    }
}
