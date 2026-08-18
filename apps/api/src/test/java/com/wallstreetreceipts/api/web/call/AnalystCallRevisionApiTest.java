package com.wallstreetreceipts.api.web.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalystCallRevisionApiTest {

    private static final Set<String> REVISION_FIELDS = Set.of(
            "revisionId", "schemaVersion", "callId", "supersedesRevisionId", "sequenceNumber",
            "provider", "providerEventId", "revisionType", "eventTime", "processingTime",
            "correctedTerms", "reason", "sourceReferenceId", "dataMode", "capturedAt", "provenanceId");
    private static final Set<String> CORRECTED_TERMS_FIELDS = Set.of(
            "direction", "originalRating", "previousTarget", "target", "currency", "targetDate");
    private static final Set<String> PROBLEM_FIELDS = Set.of(
            "type", "title", "status", "detail", "instance", "code", "timestamp", "requestId", "violations");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @Test
    void returnsCanonicalRevisionLineageInSequenceOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/demo-call-002/revisions")
                        .header("X-Request-Id", "req-revision-list"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Request-Id", "req-revision-list"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].revisionId").value("demo-call-revision-001"))
                .andExpect(jsonPath("$[0].schemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$[0].callId").value("demo-call-002"))
                .andExpect(jsonPath("$[0].supersedesRevisionId")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].sequenceNumber").value(1))
                .andExpect(jsonPath("$[0].provider").value("fixture"))
                .andExpect(jsonPath("$[0].providerEventId").value("fixture-call-revision-001"))
                .andExpect(jsonPath("$[0].revisionType").value("CORRECTION"))
                .andExpect(jsonPath("$[0].eventTime").value("2026-08-11T14:40:00Z"))
                .andExpect(jsonPath("$[0].processingTime").value("2026-08-11T14:42:00Z"))
                .andExpect(jsonPath("$[0].correctedTerms.direction").value("BULLISH"))
                .andExpect(jsonPath("$[0].correctedTerms.target").value(232.0))
                .andExpect(jsonPath("$[0].correctedTerms.currency").value("USD"))
                .andExpect(jsonPath("$[0].sourceReferenceId").value("source-ref-demo-002"))
                .andExpect(jsonPath("$[0].dataMode").value("DEMO"))
                .andExpect(jsonPath("$[1].revisionId").value("demo-call-revision-002"))
                .andExpect(jsonPath("$[1].supersedesRevisionId").value("demo-call-revision-001"))
                .andExpect(jsonPath("$[1].sequenceNumber").value(2))
                .andExpect(jsonPath("$[1].revisionType").value("CANCELLATION"))
                .andExpect(jsonPath("$[1].correctedTerms").value(org.hamcrest.Matchers.nullValue()))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(fieldNames(root.get(0))).isEqualTo(REVISION_FIELDS);
        assertThat(fieldNames(root.get(0).get("correctedTerms"))).isEqualTo(CORRECTED_TERMS_FIELDS);
        assertThat(fieldNames(root.get(1))).isEqualTo(REVISION_FIELDS);
    }

    @Test
    void knownCallWithoutRevisionsReturnsAnEmptyArray() throws Exception {
        mockMvc.perform(get("/v1/calls/demo-call-001/revisions"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownCallUsesTheExistingClosedNotFoundProblem() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/missing-call/revisions")
                        .header("X-Request-Id", "req-missing-revisions"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-missing-revisions"))
                .andExpect(jsonPath("$.code").value("CALL_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("req-missing-revisions"))
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void revisionSubresourceExposesNoMutationMethod() throws Exception {
        var revisionMappings = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.getPatternValues().stream()
                        .anyMatch(pattern -> pattern.startsWith("/v1/calls/{id}/revisions")))
                .toList();
        assertThat(revisionMappings).hasSize(1);
        assertThat(revisionMappings.getFirst().getPatternValues())
                .containsExactly("/v1/calls/{id}/revisions");
        assertThat(revisionMappings.getFirst().getMethodsCondition().getMethods())
                .containsExactly(RequestMethod.GET);

        String path = "/v1/calls/demo-call-002/revisions";
        mockMvc.perform(post(path)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put(path)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch(path)).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete(path)).andExpect(status().isMethodNotAllowed());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        return fields;
    }
}
