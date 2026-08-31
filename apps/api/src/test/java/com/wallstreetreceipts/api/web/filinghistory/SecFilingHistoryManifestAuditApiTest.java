package com.wallstreetreceipts.api.web.filinghistory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureRepository;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestAppendOutcome.Status;
import com.wallstreetreceipts.api.application.port.out.FilingHistoryCollectionManifestRepository;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureAppendResult;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureRepository;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.FilingHistoryCollectionManifest;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingRecord;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.support.FilingHistoryCollectionTestFixture;
import com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture;

@SpringBootTest(properties = {
        "app.operator-api.enabled=true",
        "app.operator-api.token-sha256="
                + "905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecFilingHistoryManifestAuditApiTest {

    private static final String BASE_PATH = "/v1/sec/filing-history/manifests";
    private static final String REQUEST_ID = "req-sec-manifest-audit";
    private static final String AUDIT_SCHEMA_VERSION = "1.0.0";
    private static final String AUDIT_POLICY_VERSION = "SEC_EXACT_MANIFEST_AUDIT_V1";
    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:00:00.123456Z");
    private static final Instant FIRST_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:10:00.123456Z");
    private static final Instant SECOND_SEGMENT_CAPTURED_AT =
            Instant.parse("2026-08-25T03:20:00.123456Z");
    private static final Instant ALL_ASSEMBLED_AT =
            Instant.parse("2026-08-25T03:30:00.123456Z");
    private static final Instant PARTIAL_ASSEMBLED_AT =
            Instant.parse("2026-08-25T03:31:00.123456Z");
    private static final Set<String> PROBLEM_FIELDS = Set.of(
            "type", "title", "status", "detail", "instance", "code",
            "timestamp", "requestId", "violations");
    private static final Set<String> SUMMARY_FIELDS = Set.of(
            "auditSchemaVersion", "auditPolicyVersion", "evaluationAsOf",
            "manifestId", "manifestSchemaVersion", "provider", "product",
            "policyVersion", "selectionSha256", "rootCaptureId", "rootCapturedAt",
            "cik", "evidenceAvailableAt", "assembledAt", "selectionCoverage",
            "advertisedDescriptorCount", "selectedDescriptorCount",
            "omittedDescriptorCount", "sourceOccurrenceCount",
            "distinctAccessionCount", "singleSourceAccessionCount",
            "exactAgreementAccessionCount", "canonicalConflictAccessionCount",
            "immutable", "disclosure");
    private static final Set<String> DISCLOSURE_FIELDS = Set.of(
            "coverageScope", "atomicSecSnapshotClaim", "currentHistoryStatus",
            "correctionRemovalStatus", "amendmentLinkageStatus", "legalAuthorityStatus");
    private static final Set<String> PAGE_ENVELOPE_FIELDS = Set.of(
            "auditSchemaVersion", "auditPolicyVersion", "manifestId",
            "evaluationAsOf", "items", "page");
    private static final Set<String> PAGE_FIELDS = Set.of(
            "number", "size", "totalElements", "totalPages", "first", "last", "order");
    private static final Set<String> ORDER_FIELDS = Set.of("field", "direction");
    private static final Set<String> DESCRIPTOR_FIELDS = Set.of(
            "descriptorOrdinal", "fileName", "advertisedFilingCount",
            "advertisedFilingFrom", "advertisedFilingTo", "selectionState",
            "selectedSegmentCaptureId");
    private static final Set<String> ACCESSION_FIELDS = Set.of(
            "groupOrdinal", "accessionNumber", "occurrenceCount",
            "distinctProjectionCount", "comparison");
    private static final Set<String> OCCURRENCE_FIELDS = Set.of(
            "occurrenceOrdinal", "groupOrdinal", "sourceKind", "sourceCaptureId",
            "descriptorOrdinal", "sourceRowOrdinal", "projectionSha256",
            "providerEventId", "accessionNumber", "form", "filingDate",
            "reportDate", "acceptedAt", "primaryDocumentUri");
    private static final Set<String> FORBIDDEN_PUBLIC_FIELDS = Set.of(
            "datamode", "decodedbody", "rawbody", "requestheaders", "responseheaders",
            "headers", "contactemail", "useragent", "operator", "attemptid",
            "authorization", "token");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FilingCatalogCaptureRepository rootRepository;

    @Autowired
    private HistoricalFilingSegmentCaptureRepository segmentRepository;

    @Autowired
    private FilingHistoryCollectionManifestRepository manifestRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Autowired
    private ApplicationContext applicationContext;

    private FilingCatalogCapture durableRoot;
    private HistoricalFilingSegmentCapture firstSegment;
    private HistoricalFilingSegmentCapture secondSegment;
    private FilingHistoryCollectionManifest allSelected;
    private FilingHistoryCollectionManifest partialSelection;

    @BeforeEach
    void persistExactManifestEvidence() {
        FilingCatalogCapture pendingRoot =
                SecFilingCatalogCaptureTestFixture.capture(ROOT_CAPTURED_AT);
        assertThat(rootRepository.append(pendingRoot))
                .isEqualTo(FilingCatalogCaptureAppendResult.INSERTED);
        durableRoot = rootRepository.findByCaptureId(pendingRoot.captureId()).orElseThrow();

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

        firstSegment = persistSegment(
                0,
                FIRST_SEGMENT_CAPTURED_AT,
                List.of(agreeingNullDocument, conflictingNullDocument));
        secondSegment = persistSegment(
                1,
                SECOND_SEGMENT_CAPTURED_AT,
                List.of(agreeingNullDocument, conflictingDocument));

        allSelected = FilingHistoryCollectionManifest.assemble(
                durableRoot, List.of(firstSegment, secondSegment), ALL_ASSEMBLED_AT);
        partialSelection = FilingHistoryCollectionManifest.assemble(
                durableRoot, List.of(secondSegment), PARTIAL_ASSEMBLED_AT);
        assertThat(manifestRepository.append(allSelected).status()).isEqualTo(Status.INSERTED);
        assertThat(manifestRepository.append(partialSelection).status()).isEqualTo(Status.INSERTED);
    }

    @Test
    void summaryIsClosedImmutablePointInTimeEvidenceWithExplicitDisclosure() throws Exception {
        MvcResult result = mockMvc.perform(get(summaryPath(allSelected))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.auditSchemaVersion").value(AUDIT_SCHEMA_VERSION))
                .andExpect(jsonPath("$.auditPolicyVersion").value(AUDIT_POLICY_VERSION))
                .andExpect(jsonPath("$.evaluationAsOf").value(ALL_ASSEMBLED_AT.toString()))
                .andExpect(jsonPath("$.manifestId").value(allSelected.manifestId()))
                .andExpect(jsonPath("$.manifestSchemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.provider").value("sec-edgar"))
                .andExpect(jsonPath("$.product")
                        .value("edgar-submissions-root-relative-collection-manifest"))
                .andExpect(jsonPath("$.policyVersion")
                        .value("SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1"))
                .andExpect(jsonPath("$.selectionSha256").value(allSelected.selectionSha256()))
                .andExpect(jsonPath("$.rootCaptureId").value(durableRoot.captureId()))
                .andExpect(jsonPath("$.rootCapturedAt").value(ROOT_CAPTURED_AT.toString()))
                .andExpect(jsonPath("$.cik").value("0000320193"))
                .andExpect(jsonPath("$.evidenceAvailableAt")
                        .value(SECOND_SEGMENT_CAPTURED_AT.toString()))
                .andExpect(jsonPath("$.assembledAt").value(ALL_ASSEMBLED_AT.toString()))
                .andExpect(jsonPath("$.selectionCoverage")
                        .value("ALL_ADVERTISED_DESCRIPTORS_SELECTED"))
                .andExpect(jsonPath("$.advertisedDescriptorCount").value(2))
                .andExpect(jsonPath("$.selectedDescriptorCount").value(2))
                .andExpect(jsonPath("$.omittedDescriptorCount").value(0))
                .andExpect(jsonPath("$.sourceOccurrenceCount").value(6))
                .andExpect(jsonPath("$.distinctAccessionCount").value(4))
                .andExpect(jsonPath("$.singleSourceAccessionCount").value(2))
                .andExpect(jsonPath("$.exactAgreementAccessionCount").value(1))
                .andExpect(jsonPath("$.canonicalConflictAccessionCount").value(1))
                .andExpect(jsonPath("$.immutable").value(true))
                .andExpect(jsonPath("$.disclosure.coverageScope")
                        .value("ROOT_RELATIVE_SELECTED_REFERENCES_ONLY"))
                .andExpect(jsonPath("$.disclosure.atomicSecSnapshotClaim").value("NOT_MADE"))
                .andExpect(jsonPath("$.disclosure.currentHistoryStatus").value("NOT_RESOLVED"))
                .andExpect(jsonPath("$.disclosure.correctionRemovalStatus").value("NOT_RESOLVED"))
                .andExpect(jsonPath("$.disclosure.amendmentLinkageStatus").value("NOT_RESOLVED"))
                .andExpect(jsonPath("$.disclosure.legalAuthorityStatus").value("NOT_CLAIMED"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(fieldNames(root)).isEqualTo(SUMMARY_FIELDS);
        assertThat(fieldNames(root.path("disclosure"))).isEqualTo(DISCLOSURE_FIELDS);
        assertNoForbiddenPublicFields(root);
    }

    @Test
    void pointInTimeBoundaryIsClosedBeforeAndVisibleAtAndAfterAssembly() throws Exception {
        assertProblem(
                get(summaryPath(allSelected)).param(
                        "evaluationAsOf", ALL_ASSEMBLED_AT.minusNanos(1_000).toString()),
                404,
                "SEC_FILING_HISTORY_MANIFEST_NOT_FOUND");

        mockMvc.perform(get(summaryPath(allSelected))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assembledAt").value(ALL_ASSEMBLED_AT.toString()));
        mockMvc.perform(get(summaryPath(allSelected))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.plusSeconds(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestId").value(allSelected.manifestId()));
    }

    @Test
    void absentAndFutureInvisibleManifestUseTheSameSanitizedNotFoundProblem() throws Exception {
        String missingManifestId = "f".repeat(64);
        JsonNode absent = assertProblem(
                get(BASE_PATH + "/" + missingManifestId)
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString()),
                404,
                "SEC_FILING_HISTORY_MANIFEST_NOT_FOUND");
        JsonNode futureInvisible = assertProblem(
                get(summaryPath(allSelected)).param(
                        "evaluationAsOf", ALL_ASSEMBLED_AT.minusNanos(1_000).toString()),
                404,
                "SEC_FILING_HISTORY_MANIFEST_NOT_FOUND");

        assertThat(futureInvisible.path("type").asText()).isEqualTo(absent.path("type").asText());
        assertThat(futureInvisible.path("title").asText()).isEqualTo(absent.path("title").asText());
        assertThat(futureInvisible.path("detail").asText()).isEqualTo(absent.path("detail").asText());
        assertThat(absent.path("detail").asText())
                .doesNotContain(missingManifestId, allSelected.manifestId(), ALL_ASSEMBLED_AT.toString());
    }

    @Test
    void summaryRejectsMissingNonCanonicalDuplicateAndUnknownParameters() throws Exception {
        String path = summaryPath(allSelected);
        assertInvalid(get(path));
        assertInvalid(get(path).param("evaluationAsOf", ""));
        assertInvalid(get(path).param("evaluationAsOf", "not-an-instant"));
        assertInvalid(get(path).param(
                "evaluationAsOf", "2026-08-25T12:30:00.123456+09:00"));
        assertInvalid(get(path).param(
                "evaluationAsOf", "2026-08-25T03:30:00.1234567Z"));
        assertInvalid(get(path).param(
                "evaluationAsOf", "2026-08-25T24:00:00Z"));
        assertInvalid(get(path).param(
                "evaluationAsOf", ALL_ASSEMBLED_AT.toString(), ALL_ASSEMBLED_AT.toString()));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("unexpected", "value"));
    }

    @Test
    void invalidManifestIdentifiersAreRejectedBeforeLookup() throws Exception {
        assertInvalid(get(BASE_PATH + "/" + "A".repeat(64))
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString()));
        assertInvalid(get(BASE_PATH + "/abc")
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString()));
    }

    @Test
    void descriptorPagePreservesRootOrderAndExplicitPartialSelectionNull() throws Exception {
        JsonNode all = getSuccess(
                childPath(allSelected, "descriptors"), ALL_ASSEMBLED_AT, null, null);
        assertPage(all, allSelected, ALL_ASSEMBLED_AT, 0, 25, 2, 1,
                true, true, "descriptorOrdinal");
        assertThat(all.path("items")).hasSize(2);
        assertThat(all.path("items").get(0).path("descriptorOrdinal").asInt()).isZero();
        assertThat(all.path("items").get(1).path("descriptorOrdinal").asInt()).isEqualTo(1);
        assertThat(all.path("items").get(0).path("fileName").asText())
                .isEqualTo("CIK0000320193-submissions-002.json");
        assertThat(all.path("items").get(1).path("fileName").asText())
                .isEqualTo("CIK0000320193-submissions-001.json");
        assertThat(all.path("items")).allSatisfy(item ->
                assertThat(fieldNames(item)).isEqualTo(DESCRIPTOR_FIELDS));

        JsonNode partialSummary = getSuccess(
                summaryPath(partialSelection), PARTIAL_ASSEMBLED_AT, null, null);
        assertThat(partialSummary.path("selectionCoverage").asText())
                .isEqualTo("PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED");
        assertThat(partialSummary.path("selectedDescriptorCount").asInt()).isEqualTo(1);
        assertThat(partialSummary.path("omittedDescriptorCount").asInt()).isEqualTo(1);

        JsonNode partial = getSuccess(
                childPath(partialSelection, "descriptors"), PARTIAL_ASSEMBLED_AT, null, null);
        assertThat(partial.path("items").get(0).path("selectionState").asText())
                .isEqualTo("NOT_SELECTED");
        assertThat(partial.path("items").get(0).has("selectedSegmentCaptureId")).isTrue();
        assertThat(partial.path("items").get(0).path("selectedSegmentCaptureId").isNull()).isTrue();
        assertThat(partial.path("items").get(1).path("selectionState").asText())
                .isEqualTo("SELECTED_EXACT_CAPTURE");
        assertThat(partial.path("items").get(1).path("selectedSegmentCaptureId").asText())
                .isEqualTo(secondSegment.captureId());
        assertNoForbiddenPublicFields(partial);
    }

    @Test
    void accessionPagePreservesSingleAgreementAndConflictGroupsInFixedOrder() throws Exception {
        JsonNode root = getSuccess(
                childPath(allSelected, "accessions"), ALL_ASSEMBLED_AT, null, null);
        assertPage(root, allSelected, ALL_ASSEMBLED_AT, 0, 25, 4, 1,
                true, true, "groupOrdinal");
        assertThat(integerValues(root.path("items"), "groupOrdinal"))
                .containsExactly(0, 1, 2, 3);
        assertThat(textValues(root.path("items"), "comparison"))
                .containsExactly(
                        "SINGLE_SOURCE_OCCURRENCE",
                        "SINGLE_SOURCE_OCCURRENCE",
                        "MULTIPLE_OCCURRENCES_EXACT_AGREEMENT",
                        "MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT");
        assertThat(root.path("items").get(2).path("occurrenceCount").asLong()).isEqualTo(2);
        assertThat(root.path("items").get(2).path("distinctProjectionCount").asLong()).isEqualTo(1);
        assertThat(root.path("items").get(3).path("occurrenceCount").asLong()).isEqualTo(2);
        assertThat(root.path("items").get(3).path("distinctProjectionCount").asLong()).isEqualTo(2);
        assertThat(root.path("items")).allSatisfy(item ->
                assertThat(fieldNames(item)).isEqualTo(ACCESSION_FIELDS));
        assertNoForbiddenPublicFields(root);
    }

    @Test
    void occurrencePagePreservesSourceIdentityOrderAndExplicitNullableEvidence() throws Exception {
        JsonNode root = getSuccess(
                childPath(allSelected, "occurrences"), ALL_ASSEMBLED_AT, null, "100");
        assertPage(root, allSelected, ALL_ASSEMBLED_AT, 0, 100, 6, 1,
                true, true, "occurrenceOrdinal");
        assertThat(integerValues(root.path("items"), "occurrenceOrdinal"))
                .containsExactly(0, 1, 2, 3, 4, 5);
        assertThat(textValues(root.path("items"), "sourceKind"))
                .containsExactly(
                        "ROOT_RECENT", "ROOT_RECENT",
                        "HISTORICAL_SEGMENT", "HISTORICAL_SEGMENT",
                        "HISTORICAL_SEGMENT", "HISTORICAL_SEGMENT");
        assertThat(root.path("items").get(0).path("groupOrdinal").asInt()).isZero();
        assertThat(root.path("items").get(0).path("sourceCaptureId").asText())
                .isEqualTo(durableRoot.captureId());
        assertThat(root.path("items").get(0).path("descriptorOrdinal").isNull()).isTrue();
        assertThat(root.path("items").get(2).path("groupOrdinal").asInt()).isEqualTo(2);
        assertThat(root.path("items").get(2).path("sourceCaptureId").asText())
                .isEqualTo(firstSegment.captureId());
        assertThat(root.path("items").get(2).path("descriptorOrdinal").asInt()).isZero();
        assertThat(root.path("items").get(2).has("primaryDocumentUri")).isTrue();
        assertThat(root.path("items").get(2).path("primaryDocumentUri").isNull()).isTrue();
        assertThat(root.path("items").get(3).has("reportDate")).isTrue();
        assertThat(root.path("items").get(3).path("reportDate").isNull()).isTrue();
        assertThat(root.path("items").get(5).path("primaryDocumentUri").asText())
                .endsWith("/conflict8k.htm");
        assertThat(root.path("items")).allSatisfy(item ->
                assertThat(fieldNames(item)).isEqualTo(OCCURRENCE_FIELDS));
        assertNoForbiddenPublicFields(root);
    }

    @Test
    void childPaginationAcceptsDefaultsAndMaximumAndReturnsTruthfulEmptyFarPage()
            throws Exception {
        JsonNode defaults = getSuccess(
                childPath(allSelected, "descriptors"), ALL_ASSEMBLED_AT, null, null);
        assertPage(defaults, allSelected, ALL_ASSEMBLED_AT, 0, 25, 2, 1,
                true, true, "descriptorOrdinal");

        JsonNode maximum = getSuccess(
                childPath(allSelected, "accessions"), ALL_ASSEMBLED_AT, "0", "100");
        assertPage(maximum, allSelected, ALL_ASSEMBLED_AT, 0, 100, 4, 1,
                true, true, "groupOrdinal");

        JsonNode farPage = getSuccess(
                childPath(allSelected, "occurrences"),
                ALL_ASSEMBLED_AT,
                Integer.toString(Integer.MAX_VALUE),
                "100");
        assertThat(farPage.path("items").isArray()).isTrue();
        assertThat(farPage.path("items")).isEmpty();
        assertPage(farPage, allSelected, ALL_ASSEMBLED_AT,
                Integer.MAX_VALUE, 100, 6, 1, false, true, "occurrenceOrdinal");
    }

    @Test
    void childPagesRejectInvalidDuplicateAndUnknownPaginationParameters() throws Exception {
        String path = childPath(allSelected, "occurrences");
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("page", "-1"));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("page", ""));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("page", "2147483648"));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("size", "0"));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("size", "101"));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("page", "0", "1"));
        assertInvalid(get(path)
                .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                .param("sort", "acceptedAt"));
    }

    @Test
    void fourAuditRoutesAreAnonymousGetOnlyAndEveryMutationReturnsNoStore405()
            throws Exception {
        var auditMappings = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.getPatternValues().stream()
                        .anyMatch(pattern -> pattern.startsWith(BASE_PATH + "/{manifestId}")))
                .toList();
        assertThat(auditMappings).hasSize(4);
        assertThat(auditMappings.stream()
                .flatMap(mapping -> mapping.getPatternValues().stream())
                .collect(java.util.stream.Collectors.toSet()))
                .containsExactlyInAnyOrder(
                        BASE_PATH + "/{manifestId}",
                        BASE_PATH + "/{manifestId}/descriptors",
                        BASE_PATH + "/{manifestId}/accessions",
                        BASE_PATH + "/{manifestId}/occurrences");
        assertThat(auditMappings).allSatisfy(mapping ->
                assertThat(mapping.getMethodsCondition().getMethods())
                        .containsExactly(RequestMethod.GET));

        assertMethodNotAllowed(post(summaryPath(allSelected)));
        assertMethodNotAllowed(put(childPath(allSelected, "descriptors")));
        assertMethodNotAllowed(patch(childPath(allSelected, "accessions")));
        assertMethodNotAllowed(delete(childPath(allSelected, "occurrences")));
    }

    @Test
    void implicitHeadPreservesPointInTimeStatusAndAuditHeaders()
            throws Exception {
        mockMvc.perform(head(summaryPath(allSelected))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(head(summaryPath(allSelected))
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));

        mockMvc.perform(head(BASE_PATH + "/" + "f".repeat(64))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
    }

    @Test
    void firewallRejectedAuditRequestRetainsRequestIdAndNoStoreProblem()
            throws Exception {
        MvcResult result = mockMvc.perform(get(BASE_PATH + "/rejected%25path")
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.instance").value("/invalid-request"))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void generatedRequestIdIsReturnedAndProviderDisabledReadsNeedNoSecContactOrNetwork()
            throws Exception {
        assertThat(applicationContext.getBeansOfType(FilingCatalogCaptureProvider.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(HistoricalFilingSegmentCaptureProvider.class))
                .isEmpty();

        MvcResult result = mockMvc.perform(get(summaryPath(allSelected))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();

        assertThat(result.getResponse().getHeader("X-Request-Id"))
                .matches("req-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    void corruptManifestEvidenceFailsClosedBeforePagingWithSanitizedNoStore500()
            throws Exception {
        jdbc.update(
                """
                        UPDATE sec_filing_history_collection_manifests
                        SET single_source_group_count = 1,
                            exact_agreement_group_count = 2
                        WHERE manifest_id = ?
                        """,
                allSelected.manifestId());

        JsonNode problem = assertProblem(
                get(childPath(allSelected, "descriptors"))
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString()),
                500,
                "INTERNAL_ERROR");
        assertThat(problem.path("detail").asText())
                .isEqualTo("An unexpected server error occurred.")
                .doesNotContain("summary", "source", "database", allSelected.manifestId());
    }

    private JsonNode getSuccess(
            String path,
            Instant evaluationAsOf,
            String page,
            String size) throws Exception {
        MockHttpServletRequestBuilder request = get(path)
                .param("evaluationAsOf", evaluationAsOf.toString())
                .header("X-Request-Id", REQUEST_ID);
        if (page != null) {
            request.param("page", page);
        }
        if (size != null) {
            request.param("size", size);
        }
        MvcResult result = mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertNoForbiddenPublicFields(root);
        return root;
    }

    private void assertPage(
            JsonNode root,
            FilingHistoryCollectionManifest manifest,
            Instant evaluationAsOf,
            int number,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            String orderField) {
        assertThat(fieldNames(root)).isEqualTo(PAGE_ENVELOPE_FIELDS);
        assertThat(root.path("auditSchemaVersion").asText()).isEqualTo(AUDIT_SCHEMA_VERSION);
        assertThat(root.path("auditPolicyVersion").asText()).isEqualTo(AUDIT_POLICY_VERSION);
        assertThat(root.path("manifestId").asText()).isEqualTo(manifest.manifestId());
        assertThat(root.path("evaluationAsOf").asText()).isEqualTo(evaluationAsOf.toString());
        JsonNode page = root.path("page");
        assertThat(fieldNames(page)).isEqualTo(PAGE_FIELDS);
        assertThat(page.path("number").asInt()).isEqualTo(number);
        assertThat(page.path("size").asInt()).isEqualTo(size);
        assertThat(page.path("totalElements").asLong()).isEqualTo(totalElements);
        assertThat(page.path("totalPages").asInt()).isEqualTo(totalPages);
        assertThat(page.path("first").asBoolean()).isEqualTo(first);
        assertThat(page.path("last").asBoolean()).isEqualTo(last);
        assertThat(fieldNames(page.path("order"))).isEqualTo(ORDER_FIELDS);
        assertThat(page.path("order").path("field").asText()).isEqualTo(orderField);
        assertThat(page.path("order").path("direction").asText()).isEqualTo("ASC");
    }

    private JsonNode assertInvalid(MockHttpServletRequestBuilder request) throws Exception {
        return assertProblem(
                request,
                400,
                "INVALID_SEC_FILING_HISTORY_MANIFEST_AUDIT_QUERY");
    }

    private JsonNode assertProblem(
            MockHttpServletRequestBuilder request,
            int expectedStatus,
            String expectedCode) throws Exception {
        MvcResult result = mockMvc.perform(request.header("X-Request-Id", REQUEST_ID))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(jsonPath("$.status").value(expectedStatus))
                .andExpect(jsonPath("$.code").value(expectedCode))
                .andExpect(jsonPath("$.requestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.violations").isArray())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(fieldNames(root)).isEqualTo(PROBLEM_FIELDS);
        assertNoForbiddenPublicFields(root);
        return root;
    }

    private void assertMethodNotAllowed(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request
                        .param("evaluationAsOf", ALL_ASSEMBLED_AT.toString())
                        .header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.ALLOW, containsString("GET")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andReturn();
        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
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

    private static String summaryPath(FilingHistoryCollectionManifest manifest) {
        return BASE_PATH + "/" + manifest.manifestId();
    }

    private static String childPath(
            FilingHistoryCollectionManifest manifest,
            String child) {
        return summaryPath(manifest) + "/" + child;
    }

    private static List<Integer> integerValues(JsonNode array, String field) {
        java.util.ArrayList<Integer> values = new java.util.ArrayList<>();
        array.forEach(item -> values.add(item.path(field).asInt()));
        return List.copyOf(values);
    }

    private static List<String> textValues(JsonNode array, String field) {
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        array.forEach(item -> values.add(item.path(field).asText()));
        return List.copyOf(values);
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        return fields;
    }

    private static void assertNoForbiddenPublicFields(JsonNode node) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                assertThat(name.toLowerCase(java.util.Locale.ROOT))
                        .isNotIn(FORBIDDEN_PUBLIC_FIELDS);
                assertNoForbiddenPublicFields(node.get(name));
            }
        } else if (node.isArray()) {
            node.forEach(SecFilingHistoryManifestAuditApiTest::assertNoForbiddenPublicFields);
        }
    }
}
