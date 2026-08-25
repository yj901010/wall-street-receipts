package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;
import com.wallstreetreceipts.api.application.filinghistory.ExecuteSecFilingHistoryCollectionAttemptService;
import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptNotFoundException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptQueryService;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.DescriptorAction;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.FailureCode;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.ProviderDispatch;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.RequestDisposition;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalOutcome;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt.TerminalStage;
import com.wallstreetreceipts.api.web.RequestIdFilter;

class OperatorSecCollectionAttemptApiTest {

    private static final String REQUEST_ID = "11111111-1111-4111-8111-111111111111";
    private static final String ROOT_CAPTURE_ID = "a".repeat(64);
    private static final String SEGMENT_CAPTURE_ID = "b".repeat(64);
    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00.123456Z");
    private static final String ROOT_PATH =
            OperatorSecCollectionAttemptController.PATH + "/root";
    private static final String EXACT_ROOT_PATH =
            OperatorSecCollectionAttemptController.PATH + "/exact-root";
    private static final Set<String> ATTEMPT_FIELDS = Set.of(
            "schemaVersion",
            "provider",
            "product",
            "policyVersion",
            "attemptId",
            "commandSha256",
            "operatorRequestId",
            "commandKind",
            "cik",
            "rootCaptureId",
            "descriptorActions",
            "requestedAt",
            "maxProviderInvocations",
            "lifecycleState",
            "attemptIndeterminate",
            "providerStartOrResponseUnknown",
            "automaticRetryAllowed",
            "providerDispatch",
            "terminalOutcome");

    private ExecuteSecFilingHistoryCollectionAttemptService executionService;
    private SecFilingHistoryCollectionAttemptQueryService queryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        executionService = mock(ExecuteSecFilingHistoryCollectionAttemptService.class);
        queryService = mock(SecFilingHistoryCollectionAttemptQueryService.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new OperatorSecCollectionAttemptController(
                                executionService, queryService))
                .setControllerAdvice(new OperatorSecCollectionAttemptExceptionHandler(clock))
                .addFilters(new RequestIdFilter())
                .build();
    }

    @Test
    void captureRootReturnsAllowlistedNoStoreStatusAndLocation() throws Exception {
        SecFilingHistoryCollectionAttempt attempt =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(REQUEST_ID, "320193", NOW);
        when(executionService.captureRoot(REQUEST_ID, "320193")).thenReturn(attempt);

        MvcResult result = mockMvc.perform(post(ROOT_PATH)
                        .header(RequestIdFilter.HEADER, "req-root")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorRequestId":"%s","cik":"320193"}
                                """.formatted(REQUEST_ID)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(RequestIdFilter.HEADER, "req-root"))
                .andExpect(header().string(
                        HttpHeaders.LOCATION,
                        OperatorSecCollectionAttemptController.PATH
                                + "/" + attempt.attemptId()))
                .andExpect(jsonPath("$.schemaVersion").value("1.0.0"))
                .andExpect(jsonPath("$.provider").value("sec-edgar"))
                .andExpect(jsonPath("$.attemptId").value(attempt.attemptId()))
                .andExpect(jsonPath("$.operatorRequestId").value(REQUEST_ID))
                .andExpect(jsonPath("$.commandKind").value("CAPTURE_ROOT"))
                .andExpect(jsonPath("$.cik").value("0000320193"))
                .andExpect(jsonPath("$.lifecycleState").value("PLANNED"))
                .andExpect(jsonPath("$.attemptIndeterminate").value(false))
                .andExpect(jsonPath("$.providerStartOrResponseUnknown").value(false))
                .andExpect(jsonPath("$.automaticRetryAllowed").value(false))
                .andExpect(jsonPath("$.contactEmail").doesNotExist())
                .andExpect(jsonPath("$.rootCaptureId").value(nullValue()))
                .andExpect(jsonPath("$.providerDispatch").value(nullValue()))
                .andExpect(jsonPath("$.terminalOutcome").value(nullValue()))
                .andReturn();

        JsonNode response = new ObjectMapper()
                .readTree(result.getResponse().getContentAsByteArray());
        assertThat(fieldNames(response)).containsExactlyInAnyOrderElementsOf(ATTEMPT_FIELDS);

        verify(executionService).captureRoot(REQUEST_ID, "320193");
        verifyNoInteractions(queryService);
    }

    @Test
    void collectExactRootMapsMutuallyExclusiveActions() throws Exception {
        List<DescriptorAction> actions = List.of(
                DescriptorAction.selectExact(0, SEGMENT_CAPTURE_ID),
                DescriptorAction.captureNow(1));
        SecFilingHistoryCollectionAttempt attempt =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        REQUEST_ID, ROOT_CAPTURE_ID, actions, NOW);
        when(executionService.collectExactRoot(REQUEST_ID, ROOT_CAPTURE_ID, actions))
                .thenReturn(attempt);

        mockMvc.perform(post(EXACT_ROOT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "operatorRequestId":"%s",
                                  "rootCaptureId":"%s",
                                  "descriptorActions":[
                                    {"descriptorOrdinal":0,"actionKind":"SELECT_EXACT",
                                     "selectedSegmentCaptureId":"%s"},
                                    {"descriptorOrdinal":1,"actionKind":"CAPTURE_NOW"}
                                  ]
                                }
                                """.formatted(
                                        REQUEST_ID,
                                        ROOT_CAPTURE_ID,
                                        SEGMENT_CAPTURE_ID)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.cik").value(nullValue()))
                .andExpect(jsonPath("$.descriptorActions.length()").value(2))
                .andExpect(jsonPath("$.descriptorActions[0].actionKind")
                        .value("SELECT_EXACT"))
                .andExpect(jsonPath("$.descriptorActions[1].actionKind")
                        .value("CAPTURE_NOW"));

        verify(executionService).collectExactRoot(REQUEST_ID, ROOT_CAPTURE_ID, actions);
    }

    @Test
    void exactEvidenceFailureAfterAdmissionRemainsA200TerminalRepresentation()
            throws Exception {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCollectExactRoot(
                        REQUEST_ID, ROOT_CAPTURE_ID, List.of(), NOW);
        SecFilingHistoryCollectionAttempt failed = planned.withTerminalOutcome(
                TerminalOutcome.failed(
                        TerminalStage.EXACT_EVIDENCE_VALIDATION,
                        RequestDisposition.NO_PROVIDER_INVOCATION,
                        FailureCode.EXACT_EVIDENCE_VALIDATION_FAILED,
                        null,
                        NOW));
        when(executionService.collectExactRoot(REQUEST_ID, ROOT_CAPTURE_ID, List.of()))
                .thenReturn(failed);

        mockMvc.perform(post(EXACT_ROOT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorRequestId":"%s","rootCaptureId":"%s",
                                 "descriptorActions":[]}
                                """.formatted(REQUEST_ID, ROOT_CAPTURE_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lifecycleState").value("TERMINAL_FAILED_KNOWN"))
                .andExpect(jsonPath("$.terminalOutcome.failureCode")
                        .value("EXACT_EVIDENCE_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.terminalOutcome.requestDisposition")
                        .value("NO_PROVIDER_INVOCATION"))
                .andExpect(jsonPath("$.terminalOutcome.providerHttpStatus")
                        .value(nullValue()))
                .andExpect(jsonPath("$.terminalOutcome.rootArtifact.artifactId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.terminalOutcome.segmentArtifact.artifactId")
                        .value(nullValue()))
                .andExpect(jsonPath("$.terminalOutcome.manifestArtifact.artifactId")
                        .value(nullValue()));
    }

    @Test
    void statusPreservesIndeterminateSafetyFlagsWithoutSuggestingRetry() throws Exception {
        SecFilingHistoryCollectionAttempt planned =
                SecFilingHistoryCollectionAttempt.planCaptureRoot(REQUEST_ID, "320193", NOW);
        SecFilingHistoryCollectionAttempt indeterminate = planned.withProviderDispatch(
                ProviderDispatch.captureRoot(NOW));
        when(queryService.findByAttemptId(indeterminate.attemptId()))
                .thenReturn(indeterminate);

        mockMvc.perform(get(OperatorSecCollectionAttemptController.PATH
                        + "/" + indeterminate.attemptId()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().doesNotExist(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.lifecycleState")
                        .value("PROVIDER_DISPATCHED_INDETERMINATE"))
                .andExpect(jsonPath("$.attemptIndeterminate").value(true))
                .andExpect(jsonPath("$.providerStartOrResponseUnknown").value(true))
                .andExpect(jsonPath("$.automaticRetryAllowed").value(false))
                .andExpect(jsonPath("$.providerDispatch.operation").value("CAPTURE_ROOT"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"cik\":\"320193\",\"commandKind\":\"CAPTURE_ROOT\"}",
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"operatorRequestId\":\"22222222-2222-4222-8222-222222222222\",\"cik\":\"320193\"}",
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"cik\":320193}",
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"cik\":\"320193\"} {\"cik\":\"1\"}"
    })
    void rootCommandRejectsUnknownDuplicateWrongTypeAndTrailingContent(String body)
            throws Exception {
        assertInvalidCommand(ROOT_PATH, body);
        verifyNoInteractions(executionService, queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "text/plain"})
    void missingOrWrongContentTypeIsA400OperatorCommandProblem(String contentType)
            throws Exception {
        var request = post(ROOT_PATH)
                .content("""
                        {"operatorRequestId":"%s","cik":"320193"}
                        """.formatted(REQUEST_ID));
        if (!contentType.isEmpty()) {
            request.contentType(contentType);
        }

        mockMvc.perform(request)
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_OPERATOR_COMMAND"));
        verifyNoInteractions(executionService, queryService);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"rootCaptureId\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"descriptorActions\":[{\"descriptorOrdinal\":0,\"actionKind\":\"SELECT_EXACT\"}]}",
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"rootCaptureId\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"descriptorActions\":[{\"descriptorOrdinal\":0,\"actionKind\":\"CAPTURE_NOW\",\"selectedSegmentCaptureId\":\"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb\"}]}",
        "{\"operatorRequestId\":\"11111111-1111-4111-8111-111111111111\",\"rootCaptureId\":\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\",\"descriptorActions\":[{\"descriptorOrdinal\":0,\"actionKind\":\"CAPTURE_NOW\",\"url\":\"https://example.invalid\"}]}"
    })
    void exactRootRejectsNonExclusiveOrUnknownActionShapes(String body) throws Exception {
        assertInvalidCommand(EXACT_ROOT_PATH, body);
        verifyNoInteractions(executionService, queryService);
    }

    @Test
    void operatorRequestConflictIs409() throws Exception {
        when(executionService.captureRoot(anyString(), anyString()))
                .thenThrow(new OperatorRequestConflictException());

        performValidRoot()
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("OPERATOR_REQUEST_CONFLICT"));
    }

    @Test
    void exactEvidenceAdmissionRejectionIs422AndCreatesNoStatusRepresentation()
            throws Exception {
        when(executionService.collectExactRoot(anyString(), anyString(), anyList()))
                .thenThrow(new ExactEvidenceNotAdmittedException());

        mockMvc.perform(post(EXACT_ROOT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"operatorRequestId":"%s","rootCaptureId":"%s",
                                 "descriptorActions":[]}
                                """.formatted(REQUEST_ID, ROOT_CAPTURE_ID)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EXACT_EVIDENCE_NOT_ADMITTED"));
    }

    @Test
    void exactUnknownAttemptIs404WithoutEchoingTheIdentifier() throws Exception {
        String attemptId = "f".repeat(64);
        when(queryService.findByAttemptId(attemptId))
                .thenThrow(new SecFilingHistoryCollectionAttemptNotFoundException(attemptId));

        mockMvc.perform(get(OperatorSecCollectionAttemptController.PATH + "/" + attemptId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SEC_COLLECTION_ATTEMPT_NOT_FOUND"))
                .andExpect(jsonPath("$.detail")
                        .value("The requested SEC collection attempt was not found."));
    }

    private void assertInvalidCommand(String path, String body) throws Exception {
        mockMvc.perform(post(path)
                        .header(RequestIdFilter.HEADER, "req-invalid")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(
                        MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(RequestIdFilter.HEADER, "req-invalid"))
                .andExpect(jsonPath("$.code").value("INVALID_OPERATOR_COMMAND"));
    }

    private org.springframework.test.web.servlet.ResultActions performValidRoot()
            throws Exception {
        return mockMvc.perform(post(ROOT_PATH)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"operatorRequestId":"%s","cik":"320193"}
                        """.formatted(REQUEST_ID)));
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        node.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
