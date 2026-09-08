package com.wallstreetreceipts.api.web.operator;

import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;

/** Explicit DEMO operational records; no collector, actual provider or secret. */
@SpringBootTest(properties = {"app.operator-api.enabled=true",
        "app.operator-api.token-sha256=905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OperatorCpiAttemptApiTest.FixedClock.class)
class OperatorCpiAttemptApiTest {
    static final String PATH = OperatorCpiAttemptController.PATH;
    static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    static final Instant NOW = Instant.parse("2026-09-08T15:02:00.123456Z");
    static final Instant START = Instant.parse("2026-09-08T14:00:00.123456Z");
    @Autowired MockMvc mvc;
    @MockitoBean CpiRepository repository;

    @Test void authenticatedRecentAndSelectedExposeOnlyClosedKstOperationalEvidence() throws Exception {
        var id = new UUID(0, 1); var capture = new UUID(0, 2);
        var saved = new CpiCollectionAttempt(id, Trigger.SCHEDULED, START, true,
                new Result(START.plusSeconds(1), Status.SAVED, capture, START, null, null));
        when(repository.recentAttempts()).thenReturn(List.of(saved));
        when(repository.findAttempt(id)).thenReturn(Optional.of(saved));
        var response = mvc.perform(auth(get(PATH))).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.metadata.dataMode").value("UNVERIFIED"))
                .andExpect(jsonPath("$.metadata.evidenceMode").value("PERSISTED_ATTEMPT_RECORDS"))
                .andExpect(jsonPath("$.metadata.observedAtKst").value("2026-09-09T00:02:00.123456+09:00"))
                .andExpect(jsonPath("$.metadata.timezone").value("Asia/Seoul"))
                .andExpect(jsonPath("$.metadata.limitations").isArray())
                .andExpect(jsonPath("$.limit").value(20)).andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.attempts[0].startedAtKst").value("2026-09-08T23:00:00.123456+09:00"))
                .andExpect(jsonPath("$.attempts[0].status").value("SAVED"))
                .andExpect(jsonPath("$.attempts[0].terminal.captureId").value(capture.toString()))
                .andExpect(jsonPath("$.attempts[0].terminal.completedAtKst").value("2026-09-08T23:00:01.123456+09:00"))
                .andReturn().getResponse();
        assertThat(response.getContentAsByteArray()).hasSizeLessThan(32768);
        assertThat(response.getContentAsString()).doesNotContain(TOKEN, "response_json", "responseSha256", "registrationkey", "raw");
        mvc.perform(auth(get(PATH + "/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.attemptId").value(id.toString()))
                .andExpect(jsonPath("$.attempt.terminal.capturedAtKst").value("2026-09-08T23:00:00.123456+09:00"));
        verify(repository).recentAttempts(); verify(repository).findAttempt(id); verifyNoMoreInteractions(repository);
    }

    @Test void emptyMissingAndUnknownNeverBecomeSavedOrRunning() throws Exception {
        when(repository.recentAttempts()).thenReturn(List.of());
        mvc.perform(auth(get(PATH))).andExpect(status().isOk()).andExpect(jsonPath("$.attempts").isEmpty());
        var id = new UUID(0, 1);
        when(repository.findAttempt(id)).thenReturn(Optional.empty());
        mvc.perform(auth(get(PATH + "/" + id))).andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("CPI_ATTEMPT_NOT_FOUND"));
        when(repository.findAttempt(id)).thenReturn(Optional.of(new CpiCollectionAttempt(id, Trigger.MANUAL, START, true, null)));
        mvc.perform(auth(get(PATH + "/" + id))).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.status").value("UNKNOWN"))
                .andExpect(jsonPath("$.attempt.terminal").value((Object) null))
                .andExpect(content().string(not(containsString("RUNNING"))));
    }

    @Test void failedSkippedAndRateLimitedStayDistinctAndBackoffIsNotNextRun() throws Exception {
        for (var state : new Status[]{Status.FAILED, Status.SKIPPED, Status.RATE_LIMITED}) {
            var id = new UUID(0, state.ordinal() + 1);
            var terminal = new Result(START, state, null, null, state == Status.FAILED ? Failure.PARSE : null,
                    state == Status.RATE_LIMITED ? START.plusSeconds(172800) : null);
            when(repository.findAttempt(id)).thenReturn(Optional.of(new CpiCollectionAttempt(id, Trigger.MANUAL, START, state != Status.SKIPPED, terminal)));
            mvc.perform(auth(get(PATH + "/" + id))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.attempt.status").value(state.name()))
                    .andExpect(jsonPath("$.attempt.terminal.captureId").value((Object) null))
                    .andExpect(content().string(not(containsString("nextRun"))));
        }
    }

    @Test void noBearerBadDuplicateOrForwardedIdentityCannotReadEvenUnknownPaths() throws Exception {
        for (var path : List.of(PATH, PATH + "/00000000-0000-0000-0000-000000000001", "/internal/v1/cpi/SECRET_PATH")) {
            mvc.perform(get(path).queryParam("access_token", TOKEN).header("X-Forwarded-User", "operator"))
                    .andExpect(status().isUnauthorized()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.instance").value(PATH))
                    .andExpect(jsonPath("$.timestamp").value("2026-09-09T00:02:00.123456+09:00"))
                    .andExpect(content().string(not(containsString(TOKEN))))
                    .andExpect(content().string(not(containsString("SECRET_PATH"))));
        }
        for (var token : List.of("short", "A".repeat(44), "AQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=")) {
            mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get(PATH).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN, "Bearer " + TOKEN)).andExpect(status().isUnauthorized());
        verifyNoInteractions(repository);
    }

    @Test void mutationVerbsDeniedEvenWithValidTokenAndHeadIsReadOnly() throws Exception {
        for (var request : List.of(post(PATH), put(PATH), patch(PATH), delete(PATH), options(PATH), post(PATH + "/retry"))) {
            mvc.perform(auth(request)).andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        verifyNoInteractions(repository);
        when(repository.recentAttempts()).thenReturn(List.of());
        // MockMvc retains rendered bytes for HEAD; the real Tomcat test asserts an empty wire body.
        mvc.perform(auth(head(PATH))).andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        verify(repository).recentAttempts(); verifyNoMoreInteractions(repository);
    }

    @Test void invalidSelectionAndAnyQueryAreRejectedWithoutEchoOrDbAccess() throws Exception {
        for (var request : List.of(get(PATH + "/SECRET_PATH"), get(PATH + "/1-1-1-1-1"),
                get(PATH).queryParam("limit", "1000"), get(PATH).queryParam("token", TOKEN),
                get(PATH + "/00000000-0000-0000-0000-000000000001").queryParam("asOf", "SECRET_VALUE"))) {
            mvc.perform(auth(request)).andExpect(status().isBadRequest())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andExpect(jsonPath("$.code").value("INVALID_CPI_ATTEMPT_QUERY"))
                    .andExpect(jsonPath("$.instance").value(PATH))
                    .andExpect(content().string(not(containsString("SECRET"))))
                    .andExpect(content().string(not(containsString(TOKEN))));
        }
        verifyNoInteractions(repository);
    }

    @Test void databaseFailureReturnsSanitized503AndDoesNotReuseLastSuccess() throws Exception {
        when(repository.recentAttempts()).thenReturn(List.of()).thenThrow(new IllegalStateException("SECRET_SQL_OR_KEY"));
        mvc.perform(auth(get(PATH))).andExpect(status().isOk());
        mvc.perform(auth(get(PATH))).andExpect(status().isServiceUnavailable())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("CPI_ATTEMPT_QUERY_UNAVAILABLE"))
                .andExpect(jsonPath("$.timestampKst").value("2026-09-09T00:02:00.123456+09:00"))
                .andExpect(jsonPath("$.attempts").doesNotExist())
                .andExpect(content().string(not(containsString("SECRET_SQL_OR_KEY"))));
    }

    @Test void firewallRejectionHasNoStoreKstAndNoReflectedSecrets() throws Exception {
        mvc.perform(auth(get("/internal/v1/cpi/rejected%25path").queryParam("token", TOKEN)))
                .andExpect(status().isBadRequest()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.instance").value("/invalid-request"))
                .andExpect(jsonPath("$.timestamp").value("2026-09-09T00:02:00.123456+09:00"))
                .andExpect(content().string(not(containsString(TOKEN))));
        verifyNoInteractions(repository);
    }
    static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) { return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN); }
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClock {
        @Bean @Primary Clock cpiQueryTestClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
}
