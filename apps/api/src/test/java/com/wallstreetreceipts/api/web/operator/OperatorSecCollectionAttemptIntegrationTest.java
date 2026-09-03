package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.port.out.FilingCatalogCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.application.port.out.SecFilingHistoryCollectionAttemptRepository;

@SpringBootTest(properties = {
    "app.operator-api.enabled=true",
    "app.operator-api.token-sha256="
            + "905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6",
    "app.public-data.sec.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class OperatorSecCollectionAttemptIntegrationTest {

    private static final String TOKEN =
            "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String REQUEST_ID = "43000000-0000-4000-8000-000000000043";
    private static final String UNAUTHENTICATED_REQUEST_ID =
            "43100000-0000-4000-8000-000000000043";
    private static final String MISSING_EVIDENCE_REQUEST_ID =
            "43200000-0000-4000-8000-000000000043";
    private static final String ROOT_PATH =
            OperatorSecCollectionAttemptController.PATH + "/root";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecFilingHistoryCollectionAttemptRepository repository;

    @Autowired(required = false)
    private List<FilingCatalogCaptureProvider> rootProviders = List.of();

    @Autowired(required = false)
    private List<HistoricalFilingSegmentCaptureProvider> segmentProviders = List.of();

    @Test
    void authenticatedOfflineCommandPersistsReplaysAndReconstructsWithoutAnyProvider()
            throws Exception {
        assertThat(rootProviders).isEmpty();
        assertThat(segmentProviders).isEmpty();
        long countBefore = repository.count();
        String body = rootCommand(REQUEST_ID, "320193");

        MvcResult firstResult = mockMvc.perform(authenticated(post(ROOT_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.lifecycleState").value("TERMINAL_FAILED_KNOWN"))
                .andExpect(jsonPath("$.terminalOutcome.failureCode")
                        .value("PROVIDER_GATE_CLOSED"))
                .andExpect(jsonPath("$.terminalOutcome.requestDisposition")
                        .value("PROVIDER_INVOCATION_NOT_STARTED"))
                .andExpect(jsonPath("$.providerDispatch").value((Object) null))
                .andExpect(jsonPath("$.attemptIndeterminate").value(false))
                .andExpect(jsonPath("$.providerStartOrResponseUnknown").value(false))
                .andExpect(jsonPath("$.automaticRetryAllowed").value(false))
                .andReturn();
        JsonNode first = response(firstResult);
        String attemptId = first.path("attemptId").asText();
        String requestedAt = first.path("requestedAt").asText();

        assertThat(repository.count()).isEqualTo(countBefore + 1);
        assertThat(repository.findByAttemptId(attemptId)).isPresent();
        assertThat(firstResult.getResponse().getHeader(HttpHeaders.LOCATION))
                .isEqualTo(OperatorSecCollectionAttemptController.PATH + "/" + attemptId);

        MvcResult replayResult = mockMvc.perform(authenticated(post(ROOT_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.requestedAt").value(requestedAt))
                .andReturn();
        assertThat(repository.count()).isEqualTo(countBefore + 1);
        assertThat(response(replayResult)).isEqualTo(first);

        mockMvc.perform(authenticated(get(
                                OperatorSecCollectionAttemptController.PATH + "/" + attemptId)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.attemptId").value(attemptId))
                .andExpect(jsonPath("$.terminalOutcome.failureCode")
                        .value("PROVIDER_GATE_CLOSED"));

        mockMvc.perform(authenticated(post(ROOT_PATH))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootCommand(REQUEST_ID, "1")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_REQUEST_CONFLICT"));
        assertThat(repository.count()).isEqualTo(countBefore + 1);

        mockMvc.perform(post(ROOT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rootCommand(UNAUTHENTICATED_REQUEST_ID, "320193")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code")
                        .value("OPERATOR_AUTHENTICATION_REQUIRED"));
        assertThat(repository.count()).isEqualTo(countBefore + 1);

        mockMvc.perform(authenticated(post(
                                OperatorSecCollectionAttemptController.PATH + "/exact-root"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorRequestId":"%s","rootCaptureId":"%s",
                                 "descriptorActions":[]}
                                """.formatted(
                                        MISSING_EVIDENCE_REQUEST_ID,
                                        "f".repeat(64))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXACT_EVIDENCE_NOT_ADMITTED"));
        assertThat(repository.count()).isEqualTo(countBefore + 1);
    }

    private static MockHttpServletRequestBuilder authenticated(
            MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
    }

    private static String rootCommand(String operatorRequestId, String cik) {
        return """
                {"operatorRequestId":"%s","cik":"%s"}
                """.formatted(operatorRequestId, cik);
    }

    private JsonNode response(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }
}
