package com.wallstreetreceipts.api.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.application.filinghistory.ExecuteSecFilingHistoryCollectionAttemptService;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptQueryService;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

/** A synthetic token must never authorize SEC reads or collection in CPI-only mode. */
@SpringBootTest(properties = {"app.operator-api.enabled=true", "OPERATOR_API_ACCESS=CPI_READ_ONLY",
        "app.operator-api.token-sha256=905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CpiReadOnlyOperatorSecurityTest {
    private static final String CPI = "/internal/v1/cpi/collection-attempts";
    private static final String SEC = "/internal/v1/sec/collection-attempts";
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final UUID ID = new UUID(0, 1);
    @Autowired MockMvc mvc;
    @Autowired AuthenticationProvider authenticationProvider;
    @MockitoBean CpiRepository repository;
    @MockitoBean ExecuteSecFilingHistoryCollectionAttemptService secExecution;
    @MockitoBean SecFilingHistoryCollectionAttemptQueryService secQuery;

    @Test
    void authenticatedPrincipalHasOnlyCpiReadAuthorityAndErasesCredentials() {
        var request = OperatorBearerAuthenticationToken.unauthenticated(TOKEN);
        var result = authenticationProvider.authenticate(request);
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority).containsExactly("CPI_READ");
        assertThat(request.getCredentials()).isNull();
        assertThat(result.getCredentials()).isNull();
    }

    @Test
    void recentExactAndHeadReadsKeepTheirEvidenceContract() throws Exception {
        var attempt = new CpiCollectionAttempt(ID, CpiCollectionAttempt.Trigger.MANUAL,
                Instant.parse("2020-01-01T14:59:59.123456Z"), true, null);
        when(repository.recentAttempts()).thenReturn(List.of(attempt));
        when(repository.findAttempt(ID)).thenReturn(Optional.of(attempt));
        mvc.perform(auth(get(CPI))).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.metadata.dataMode").value("UNVERIFIED"))
                .andExpect(jsonPath("$.attempts[0].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.attempts[0].startedAtKst").value("2020-01-01T23:59:59.123456+09:00"));
        mvc.perform(auth(get(CPI + "/" + ID))).andExpect(status().isOk())
                .andExpect(jsonPath("$.attempt.attemptId").value(ID.toString()));
        mvc.perform(auth(head(CPI))).andExpect(status().isOk());
        mvc.perform(auth(head(CPI + "/" + ID))).andExpect(status().isOk());
        verifyNoInteractions(secExecution, secQuery);
    }

    @Test
    void validBearerCannotReachSecCommandsQueriesOrOtherProtectedPaths() throws Exception {
        var rootBody = "{\"operatorRequestId\":\"" + ID + "\",\"cik\":\"0000320193\"}";
        for (var request : List.of(post(SEC + "/root").contentType(MediaType.APPLICATION_JSON).content(rootBody),
                post(SEC + "/exact-root").contentType(MediaType.APPLICATION_JSON).content("{}"),
                get(SEC + "/" + ID), head(SEC + "/" + ID), put(SEC + "/root"), delete(SEC + "/" + ID),
                get("/internal/v1/sec/unknown"), get("/internal/v1/cpi/raw"),
                get(CPI + "/" + ID + "/raw"))) {
            var response = mvc.perform(auth(request)).andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                    .andReturn().getResponse();
            assertThat(response.getContentAsString()).doesNotContain(TOKEN);
        }
        verifyNoInteractions(repository, secExecution, secQuery);
    }

    @Test
    void mutationAndOptionsAreDeniedBeforeTheCpiRepository() throws Exception {
        for (var request : List.of(post(CPI), put(CPI), patch(CPI), delete(CPI), options(CPI),
                post(CPI + "/" + ID), post(CPI + "/retry"))) {
            mvc.perform(auth(request)).andExpect(status().isForbidden())
                    .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        verifyNoInteractions(repository, secExecution, secQuery);
    }

    @Test
    void missingWrongDuplicateAndForwardedCredentialsRemainUnauthorized() throws Exception {
        for (var path : List.of(CPI, CPI + "/" + ID, SEC + "/root")) {
            mvc.perform(get(path).header("X-Forwarded-User", "operator").queryParam("access_token", TOKEN))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION,
                            "Bearer AQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN, "Bearer " + TOKEN))
                    .andExpect(status().isUnauthorized());
        }
        verifyNoInteractions(repository, secExecution, secQuery);
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder request) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN);
    }
}
