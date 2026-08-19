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
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AnalystCallContextApiTest {

    private static final Set<String> CONTEXT_FIELDS = Set.of("macroSnapshot", "eventContext");
    private static final Set<String> SNAPSHOT_FIELDS = Set.of(
            "schemaVersion", "macroSnapshotId", "callId", "eventTime", "processingTime",
            "observations", "immutable", "dataMode", "capturedAt", "provenanceId");
    private static final Set<String> OBSERVATION_FIELDS = Set.of(
            "schemaVersion", "macroObservationId", "series", "value", "unit", "observationDate",
            "releasedAt", "processingTime", "vintageStart", "vintageEnd", "sourceReferenceId",
            "dataMode", "capturedAt", "provenanceId");
    private static final Set<String> EVENT_FIELDS = Set.of(
            "schemaVersion", "eventContextId", "callId", "eventTime", "processingTime", "earningsAt",
            "nextCpiAt", "nextFomcAt", "nextNfpAt", "optionsExpirationAt", "sourceReferenceId",
            "immutable", "dataMode", "capturedAt", "provenanceId");
    private static final Set<String> PROBLEM_FIELDS = Set.of(
            "type", "title", "status", "detail", "instance", "code", "timestamp", "requestId", "violations");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void returnsClosedPointInTimeContextWithEmbeddedOrderedObservations() throws Exception {
        MvcResult result = mockMvc.perform(get("/v1/calls/demo-call-001/context"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.macroSnapshot.schemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.macroSnapshot.macroSnapshotId").value("macro-snapshot-demo-001"))
                .andExpect(jsonPath("$.macroSnapshot.callId").value("demo-call-001"))
                .andExpect(jsonPath("$.macroSnapshot.eventTime").value("2026-08-10T12:00:00Z"))
                .andExpect(jsonPath("$.macroSnapshot.processingTime").value("2026-08-10T12:03:00Z"))
                .andExpect(jsonPath("$.macroSnapshot.immutable").value(true))
                .andExpect(jsonPath("$.macroSnapshot.dataMode").value("DEMO"))
                .andExpect(jsonPath("$.macroSnapshot.observations.length()").value(6))
                .andExpect(jsonPath("$.macroSnapshot.observations[0].series").value("FED_FUNDS_LOWER"))
                .andExpect(jsonPath("$.macroSnapshot.observations[1].series").value("FED_FUNDS_UPPER"))
                .andExpect(jsonPath("$.macroSnapshot.observations[2].macroObservationId")
                        .value("macro-observation-demo-cpi-original-001"))
                .andExpect(jsonPath("$.macroSnapshot.observations[2].value").value(3.1))
                .andExpect(jsonPath("$.macroSnapshot.observations[2].vintageEnd").value("2026-08-14"))
                .andExpect(jsonPath("$.macroSnapshot.observations[4].series").value("PPI_YOY"))
                .andExpect(jsonPath("$.macroSnapshot.observations[4].value")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.macroSnapshot.observations[5].series").value("UNEMPLOYMENT_RATE"))
                .andExpect(jsonPath("$.eventContext.eventContextId").value("event-context-demo-001"))
                .andExpect(jsonPath("$.eventContext.earningsAt")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.eventContext.nextCpiAt").value("2026-08-12T12:30:00Z"))
                .andExpect(jsonPath("$.eventContext.nextFomcAt").value("2026-09-16T18:00:00Z"))
                .andExpect(jsonPath("$.eventContext.nextNfpAt").value("2026-09-04T12:30:00Z"))
                .andExpect(jsonPath("$.eventContext.optionsExpirationAt").value("2026-08-21T20:00:00Z"))
                .andExpect(jsonPath("$.eventContext.sourceReferenceId")
                        .value("source-ref-demo-event-calendar-001"))
                .andExpect(jsonPath("$.eventContext.immutable").value(true))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertThat(fieldNames(root)).isEqualTo(CONTEXT_FIELDS);
        assertThat(fieldNames(root.get("macroSnapshot"))).isEqualTo(SNAPSHOT_FIELDS);
        root.get("macroSnapshot").get("observations")
                .forEach(observation -> assertThat(fieldNames(observation)).isEqualTo(OBSERVATION_FIELDS));
        assertThat(fieldNames(root.get("eventContext"))).isEqualTo(EVENT_FIELDS);
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("macro-observation-demo-cpi-revision-001")
                .doesNotContain("proximity", "score", "derived");
    }

    @Test
    void everyKnownEmptyCallReturnsBothRequiredNullableKeys() throws Exception {
        for (String callId : Set.of("demo-call-002", "demo-call-003")) {
            MvcResult result = mockMvc.perform(get("/v1/calls/{id}/context", callId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.macroSnapshot").value(org.hamcrest.Matchers.nullValue()))
                    .andExpect(jsonPath("$.eventContext").value(org.hamcrest.Matchers.nullValue()))
                    .andReturn();
            assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                    .isEqualTo(CONTEXT_FIELDS);
        }
    }

    @Test
    void unknownAndInvalidCallsUseClosedProblemResponses() throws Exception {
        MvcResult missing = mockMvc.perform(get("/v1/calls/missing-call/context")
                        .header("X-Request-Id", "req-context-missing"))
                .andExpect(status().isNotFound())
                .andExpect(header().string("X-Request-Id", "req-context-missing"))
                .andExpect(jsonPath("$.code").value("CALL_NOT_FOUND"))
                .andExpect(jsonPath("$.requestId").value("req-context-missing"))
                .andReturn();
        MvcResult invalid = mockMvc.perform(get("/v1/calls/invalid%20id/context")
                        .header("X-Request-Id", "req-context-invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "req-context-invalid"))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.requestId").value("req-context-invalid"))
                .andReturn();
        assertThat(fieldNames(objectMapper.readTree(missing.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
        assertThat(fieldNames(objectMapper.readTree(invalid.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void contextIsReadOnlyAndExistingDetailShapeRemainsUnchanged() throws Exception {
        mockMvc.perform(post("/v1/calls/demo-call-001/context")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/v1/calls/demo-call-001/context")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(patch("/v1/calls/demo-call-001/context")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/v1/calls/demo-call-001/context")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/v1/calls/demo-call-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.macroSnapshot").doesNotExist())
                .andExpect(jsonPath("$.eventContext").doesNotExist());
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        Iterator<String> iterator = node.fieldNames();
        iterator.forEachRemaining(names::add);
        return names;
    }
}
