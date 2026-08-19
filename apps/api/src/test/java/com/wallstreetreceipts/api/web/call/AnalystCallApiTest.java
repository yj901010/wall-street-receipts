package com.wallstreetreceipts.api.web.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.port.out.AnalystCallProvider;
import com.wallstreetreceipts.api.application.port.out.AnalystCallRepository;
import com.wallstreetreceipts.api.domain.call.AnalystCall;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalystCallApiTest {

    private static final String REQUEST_ID = "req-api-test";
    private static final Set<String> PROBLEM_FIELDS = Set.of(
            "type", "title", "status", "detail", "instance", "code", "timestamp", "requestId", "violations");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AnalystCallProvider provider;

    @Autowired
    private AnalystCallRepository repository;

    @Test
    void listUsesCanonicalContractAndStableDefaultPage() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls").header("X-Request-Id", REQUEST_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Request-Id", REQUEST_ID))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].call.schemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.items[0].call.callId").value("demo-call-002"))
                .andExpect(jsonPath("$.items[0].call.provider").value("fixture"))
                .andExpect(jsonPath("$.items[0].call.sourceReferenceId").value("source-ref-demo-002"))
                .andExpect(jsonPath("$.items[0].institution.institutionId").value("inst-gs"))
                .andExpect(jsonPath("$.items[0].asset.assetId").value("asset-nvda"))
                .andExpect(jsonPath("$.items[0].source.document.sourceDocumentId")
                        .value("source-demo-video-002"))
                .andExpect(jsonPath("$.items[0].source.reference.sourceReferenceId")
                        .value("source-ref-demo-002"))
                .andExpect(jsonPath("$.items[0].snapshot").doesNotExist())
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.size").value(25))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(1))
                .andExpect(jsonPath("$.page.first").value(true))
                .andExpect(jsonPath("$.page.last").value(true))
                .andExpect(jsonPath("$.page.sort.field").value("eventTime"))
                .andExpect(jsonPath("$.page.sort.order").value("desc"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(fieldNames(root)).containsExactlyInAnyOrder("items", "page");
    }

    @Test
    void paginationAndSortAreDeterministic() throws Exception {
        mockMvc.perform(get("/v1/calls")
                        .param("page", "1")
                        .param("size", "1")
                        .param("sort", "processingTime")
                        .param("order", "desc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].call.callId").value("demo-call-001"))
                .andExpect(jsonPath("$.page.number").value(1))
                .andExpect(jsonPath("$.page.size").value(1))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(3))
                .andExpect(jsonPath("$.page.first").value(false))
                .andExpect(jsonPath("$.page.last").value(false))
                .andExpect(jsonPath("$.page.sort.field").value("processingTime"))
                .andExpect(jsonPath("$.page.sort.order").value("desc"));
    }

    @Test
    void maximumIntegerPageDoesNotOverflowTheDatabaseOffset() throws Exception {
        mockMvc.perform(get("/v1/calls")
                        .param("page", Integer.toString(Integer.MAX_VALUE))
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.number").value(Integer.MAX_VALUE))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.last").value(true));
    }

    @Test
    void multipleFiltersAreCombinedWithAnd() throws Exception {
        mockMvc.perform(get("/v1/calls")
                        .param("ticker", "NVDA")
                        .param("institutionId", "inst-jpm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @ParameterizedTest
    @MethodSource("filters")
    void supportsEveryDocumentedScalarFilter(String field, String value, int total, String firstCallId)
            throws Exception {
        MockHttpServletRequestBuilder request = get("/v1/calls").param(field, value);
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(total))
                .andExpect(total == 0
                        ? jsonPath("$.items").isEmpty()
                        : jsonPath("$.items[0].call.callId").value(firstCallId));
    }

    static java.util.stream.Stream<Arguments> filters() {
        return java.util.stream.Stream.of(
                Arguments.of("assetId", "asset-spx", 1, "demo-call-001"),
                Arguments.of("ticker", "nvda", 1, "demo-call-002"),
                Arguments.of("institutionId", "inst-jpm", 1, "demo-call-001"),
                Arguments.of("analystId", "analyst-demo-b", 1, "demo-call-002"),
                Arguments.of("direction", "BULLISH", 2, "demo-call-002"),
                Arguments.of("status", "ACTIVE", 3, "demo-call-002"),
                Arguments.of("dataMode", "DEMO", 3, "demo-call-002"),
                Arguments.of("from", "2026-08-11T00:00:00Z", 1, "demo-call-002"),
                Arguments.of("to", "2026-08-11T00:00:00Z", 2, "demo-call-001"));
    }

    @Test
    void detailIncludesTraceableSourceAndImmutablePointInTimeSnapshot() throws Exception {
        mockMvc.perform(get("/v1/calls/demo-call-002"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.call.callId").value("demo-call-002"))
                .andExpect(jsonPath("$.call.eventTime").value("2026-08-11T14:20:00Z"))
                .andExpect(jsonPath("$.source.document.canonicalUrl")
                        .value("https://example.invalid/demo-call-002"))
                .andExpect(jsonPath("$.source.document.provenanceId").value("fixture-analyst-calls-v1"))
                .andExpect(jsonPath("$.source.reference.sourceDocumentId").value("source-demo-video-002"))
                .andExpect(jsonPath("$.snapshot.schemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.snapshot.snapshotId").value("market-snapshot-demo-002"))
                .andExpect(jsonPath("$.snapshot.eventTime").value("2026-08-11T14:20:00Z"))
                .andExpect(jsonPath("$.snapshot.assetPrice").value(183.42))
                .andExpect(jsonPath("$.snapshot.immutable").value(true))
                .andExpect(jsonPath("$.snapshot.dataMode").value("DEMO"));
    }

    @Test
    void detailReturnsExplicitNullSourceMetadataAndRetainsRequiredEvidence() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/demo-call-003"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.call.callId").value("demo-call-003"))
                .andExpect(jsonPath("$.source.document.sourceDocumentId")
                        .value("source-demo-article-003"))
                .andExpect(jsonPath("$.source.document.sourceType").value("ARTICLE"))
                .andExpect(jsonPath("$.source.document.publisher")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.source.document.title")
                        .value("DEMO unattributed neutral outlook"))
                .andExpect(jsonPath("$.source.document.canonicalUrl")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.source.document.publishedAt")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.source.document.provider").value("fixture"))
                .andExpect(jsonPath("$.source.document.externalId")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.source.document.contentHash")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.source.document.licenseClass").value("INTERNAL_DEMO"))
                .andExpect(jsonPath("$.source.document.dataMode").value("DEMO"))
                .andExpect(jsonPath("$.source.document.capturedAt").value("2026-08-10T10:02:00Z"))
                .andExpect(jsonPath("$.source.document.provenanceId")
                        .value("fixture-analyst-calls-v1"))
                .andExpect(jsonPath("$.source.reference.sourceReferenceId")
                        .value("source-ref-demo-003"))
                .andExpect(jsonPath("$.source.reference.sourceDocumentId")
                        .value("source-demo-article-003"))
                .andExpect(jsonPath("$.source.reference.verified").value(false))
                .andExpect(jsonPath("$.source.reference.dataMode").value("DEMO"))
                .andExpect(jsonPath("$.source.reference.capturedAt").value("2026-08-10T10:02:00Z"))
                .andExpect(jsonPath("$.source.reference.provenanceId")
                        .value("fixture-analyst-calls-v1"))
                .andReturn();

        JsonNode document = objectMapper.readTree(result.getResponse().getContentAsByteArray())
                .path("source")
                .path("document");
        assertThat(fieldNames(document)).containsExactlyInAnyOrder(
                "schemaVersion", "sourceDocumentId", "sourceType", "publisher", "title",
                "canonicalUrl", "publishedAt", "provider", "externalId", "contentHash",
                "licenseClass", "dataMode", "capturedAt", "provenanceId");
    }

    @Test
    @Transactional
    void detailPreservesNullableAnalystAndMissingSnapshot() throws Exception {
        AnalystCall source = provider.load().calls().getFirst();
        AnalystCall withoutOptionalRelations = new AnalystCall(
                "test-call-nullables", source.provider(), "test-event-nullables",
                source.institution(), null, source.asset(), source.eventTime(), source.processingTime(),
                source.direction(), source.originalRating(), source.previousTarget(), source.target(),
                source.currency(), source.targetDate(), source.sourceReference(), source.status(),
                source.dataMode(), source.capturedAt(), source.provenanceId());

        assertThat(repository.saveIfAbsent(withoutOptionalRelations, null)).isTrue();

        mockMvc.perform(get("/v1/calls/test-call-nullables"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.call.callId").value("test-call-nullables"))
                .andExpect(jsonPath("$.analyst").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.snapshot").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void missingDetailUsesClosedProblemShapeAndPropagatesRequestId() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/missing-call")
                        .header("X-Request-Id", "req-not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-not-found"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("CALL_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("req-not-found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.violations").isArray())
                .andExpect(jsonPath("$.callId").doesNotExist())
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @ParameterizedTest
    @MethodSource("invalidPathIdentifiers")
    void invalidPathIdentifiersAreRejectedBeforeLookup(String id) throws Exception {
        mockMvc.perform(get("/v1/calls/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }

    static java.util.stream.Stream<String> invalidPathIdentifiers() {
        return java.util.stream.Stream.of("invalid$id", "a".repeat(129));
    }

    @ParameterizedTest
    @MethodSource("invalidQueries")
    void invalidQueriesUseClosedProblemShape(String field, String value) throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls")
                        .param(field, value)
                        .header("X-Request-Id", "req-bad-query"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-bad-query"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.requestId").value("req-bad-query"))
                .andExpect(jsonPath("$.violations").isArray())
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    static java.util.stream.Stream<Arguments> invalidQueries() {
        return java.util.stream.Stream.of(
                Arguments.of("assetId", "invalid/id"),
                Arguments.of("ticker", "ABCDEFGHIJKLMNOPQRSTUVWXYZ"),
                Arguments.of("page", "-1"),
                Arguments.of("page", ""),
                Arguments.of("size", "0"),
                Arguments.of("size", ""),
                Arguments.of("sort", "target"),
                Arguments.of("sort", "EVENTTIME"),
                Arguments.of("sort", ""),
                Arguments.of("order", "sideways"),
                Arguments.of("order", "ASC"),
                Arguments.of("order", ""),
                Arguments.of("direction", "bullish"),
                Arguments.of("status", "active"),
                Arguments.of("dataMode", "demo"),
                Arguments.of("from", "not-an-instant"),
                Arguments.of("size", "101"),
                Arguments.of("from", "2026-08-11T00:00:00Z&to=2026-08-11T00:00:00Z"));
    }

    @Test
    void equalTimeRangeIsRejectedBecauseToIsExclusive() throws Exception {
        mockMvc.perform(get("/v1/calls")
                        .param("from", "2026-08-11T00:00:00Z")
                        .param("to", "2026-08-11T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        return fields;
    }
}
