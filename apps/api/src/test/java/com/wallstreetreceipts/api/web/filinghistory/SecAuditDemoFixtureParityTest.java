package com.wallstreetreceipts.api.web.filinghistory;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditPage;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditQueryService.AuditResult;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.support.SecManifestAuditDemoFixture;

/** Locks the web DEMO artifact to the real domain assembly and ADR-052 mapper. */
class SecAuditDemoFixtureParityTest {

    private static final int PAGE_SIZE = 25;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Test
    void committedWebFixtureExactlyMatchesJavaAssemblyAndAuditResponses()
            throws IOException {
        FilingHistoryCollectionManifest manifest =
                SecManifestAuditDemoFixture.assembledManifest();
        AuditResult audit = new AuditResult(
                manifest, SecManifestAuditDemoFixture.ASSEMBLED_AT);

        Map<String, Object> generated = new LinkedHashMap<>();
        generated.put("fixtureSchemaVersion", "1.0.0");
        generated.put("generatedBy", "java-domain-and-adr-052-response-mapper");
        generated.put("manifestId", manifest.manifestId());
        generated.put("evaluationAsOf", SecManifestAuditDemoFixture.ASSEMBLED_AT);
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
