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
class AnalystCallOutcomeApiTest {

    private static final Set<String> OUTCOME_FIELDS = Set.of(
            "outcomeId", "schemaVersion", "callId", "horizon", "basisRevisionId", "cancellationRevisionId",
            "snapshotId",
            "methodologyId", "methodologyVersion", "methodologyDefinitionHash", "inputFingerprint",
            "sequenceNumber", "supersedesOutcomeId", "evaluationStatus", "reasonCode", "eventTime",
            "processingTime", "assetReturn", "benchmarkReturn", "sectorReturn", "alpha", "sectorAlpha",
            "mfe", "mae", "targetHit", "directionalWin", "targetError", "dataComplete", "dataMode",
            "capturedAt", "provenanceId");
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
    void returnsClosedCanonicalOutcomeHistoryInDeterministicOrder() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/demo-call-001/outcomes")
                        .header("X-Request-Id", "req-outcome-list"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("X-Request-Id", "req-outcome-list"))
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].outcomeId").value("outcome-demo-call-001-d1-v1-001"))
                .andExpect(jsonPath("$[1].outcomeId").value("outcome-demo-call-001-d1-v1-002"))
                .andExpect(jsonPath("$[1].supersedesOutcomeId")
                        .value("outcome-demo-call-001-d1-v1-001"))
                .andExpect(jsonPath("$[2].outcomeId").value("outcome-demo-call-001-d1-v2-001"))
                .andExpect(jsonPath("$[3].outcomeId").value("outcome-demo-call-001-m1-v1-001"))
                .andExpect(jsonPath("$[0].basisRevisionId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].cancellationRevisionId")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].snapshotId").value("market-snapshot-demo-001"))
                .andExpect(jsonPath("$[0].evaluationStatus").value("INCOMPLETE"))
                .andExpect(jsonPath("$[0].reasonCode").value("HORIZON_DATA_MISSING"))
                .andExpect(jsonPath("$[0].assetReturn").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].targetHit").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].directionalWin").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$[0].dataComplete").value(false))
                .andExpect(jsonPath("$[0].dataMode").value("DEMO"))
                .andExpect(jsonPath("$[3].evaluationStatus").value("PENDING"))
                .andExpect(jsonPath("$[3].reasonCode").value("HORIZON_NOT_REACHED"))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(root).allSatisfy(outcome -> assertThat(fieldNames(outcome)).isEqualTo(OUTCOME_FIELDS));
    }

    @Test
    void knownCallWithoutOutcomesReturnsAnEmptyArray() throws Exception {
        mockMvc.perform(get("/v1/calls/demo-call-002/outcomes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void unknownCallUsesTheExistingClosedNotFoundProblem() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/missing-call/outcomes")
                        .header("X-Request-Id", "req-missing-outcomes"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-missing-outcomes"))
                .andExpect(jsonPath("$.code").value("CALL_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("req-missing-outcomes"))
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void invalidOpaqueCallIdUsesTheClosedBadRequestProblem() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/bad$id/outcomes")
                        .header("X-Request-Id", "req-invalid-outcome-id"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-invalid-outcome-id"))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.detail").value("id is not a valid opaque identifier"))
                .andExpect(jsonPath("$.requestId").value("req-invalid-outcome-id"))
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void outcomeSubresourceExposesNoMutationMethod() throws Exception {
        var outcomeMappings = handlerMapping.getHandlerMethods().keySet().stream()
                .filter(mapping -> mapping.getPatternValues().stream()
                        .anyMatch(pattern -> pattern.startsWith("/v1/calls/{id}/outcomes")))
                .toList();
        assertThat(outcomeMappings).hasSize(1);
        assertThat(outcomeMappings.getFirst().getPatternValues())
                .containsExactly("/v1/calls/{id}/outcomes");
        assertThat(outcomeMappings.getFirst().getMethodsCondition().getMethods())
                .containsExactly(RequestMethod.GET);

        String path = "/v1/calls/demo-call-001/outcomes";
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
