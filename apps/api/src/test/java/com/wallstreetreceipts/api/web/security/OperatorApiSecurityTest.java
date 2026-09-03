package com.wallstreetreceipts.api.web.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.web.RequestIdFilter;

@SpringBootTest(properties = {
        "app.operator-api.enabled=true",
        "app.operator-api.token-sha256="
                + "905f28def18eaac05ae6f12b2c3452744afaf626da1343d57b395b544e0519b6"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(OperatorApiSecurityTest.ProbeConfiguration.class)
class OperatorApiSecurityTest {

    private static final String PATH = "/internal/v1/sec/security-probe";
    private static final String TOKEN = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String WRONG_TOKEN = "AQECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final Set<String> PROBLEM_FIELDS = Set.of(
            "type", "title", "status", "detail", "instance", "code", "timestamp", "requestId", "violations");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OperatorApiSecurityProblemWriter problemWriter;

    @Test
    void publicApiRemainsAnonymous() throws Exception {
        mockMvc.perform(get("/v1/calls"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.CACHE_CONTROL))
                .andExpect(header().doesNotExist("X-Content-Type-Options"));
    }

    @Test
    void publicReadOnlyPostStillReachesMvcMethodNotAllowedHandling() throws Exception {
        mockMvc.perform(post("/v1/calls"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void validOpaqueBearerTokenAuthenticatesOperatorPostWithoutCsrf() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("X-Request-Id", "req-operator-valid"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", "req-operator-valid"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void missingBearerTokenReturnsSanitizedNoStoreProblem() throws Exception {
        MvcResult result = mockMvc.perform(post(PATH)
                        .header("X-Request-Id", "req-operator-missing"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-operator-missing"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(
                        HttpHeaders.WWW_AUTHENTICATE,
                        "Bearer realm=\"operator-api\""))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("OPERATOR_AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.requestId").value("req-operator-missing"))
                .andExpect(jsonPath("$.violations").isArray())
                .andReturn();

        assertThat(fieldNames(objectMapper.readTree(result.getResponse().getContentAsByteArray())))
                .isEqualTo(PROBLEM_FIELDS);
    }

    @Test
    void wrongBearerTokenUsesSameSanitizedAuthenticationFailure() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + WRONG_TOKEN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_AUTHENTICATION_REQUIRED"))
                .andExpect(content().string(not(containsString(WRONG_TOKEN))));
    }

    @Test
    void forwardedIdentityHeadersAreNeverTrustedWithoutBearerCredential() throws Exception {
        mockMvc.perform(post(PATH)
                        .header("X-User", "operator@example.invalid")
                        .header("X-Forwarded-User", "operator@example.invalid")
                        .header("Cf-Access-Authenticated-User-Email", "operator@example.invalid"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("OPERATOR_AUTHENTICATION_REQUIRED"))
                .andExpect(content().string(not(containsString("operator@example.invalid"))));
    }

    @Test
    void malformedAndOversizedBearerTokensAreRejectedBeforeAuthentication() throws Exception {
        for (String malformed : Set.of(
                "short",
                "A".repeat(44),
                TOKEN + "extra",
                TOKEN.substring(0, 20) + " " + TOKEN.substring(21))) {
            mockMvc.perform(post(PATH)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + malformed))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("OPERATOR_AUTHENTICATION_REQUIRED"))
                    .andExpect(content().string(not(containsString(malformed))));
        }
    }

    @Test
    void multipleAuthorizationHeadersAreRejectedAsAmbiguous() throws Exception {
        mockMvc.perform(post(PATH)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN, "Bearer " + TOKEN))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void firewallRejectedOperatorRequestIsClosedAndDoesNotReflectRequestSecrets()
            throws Exception {
        mockMvc.perform(post("/internal/v1/sec/rejected%25path")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + TOKEN)
                        .header("X-Request-Id", "req-firewall-rejected")
                        .queryParam("credential", TOKEN))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("X-Request-Id", "req-firewall-rejected"))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.code").value("INVALID_QUERY"))
                .andExpect(jsonPath("$.requestId").value("req-firewall-rejected"))
                .andExpect(jsonPath("$.instance").value("/invalid-request"))
                .andExpect(content().string(not(containsString(TOKEN))))
                .andExpect(content().string(not(containsString("credential"))));
    }

    @Test
    void authenticatedIdentityWithoutOperatorAuthorityGetsSanitizedForbiddenProblem()
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", PATH);
        request.setAttribute(RequestIdFilter.ATTRIBUTE, "req-operator-forbidden");
        MockHttpServletResponse response = new MockHttpServletResponse();

        problemWriter.handle(request, response, new AccessDeniedException("sensitive detail"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        assertThat(response.getHeader(RequestIdFilter.HEADER)).isEqualTo("req-operator-forbidden");
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isNull();
        assertThat(response.getContentAsString())
                .doesNotContain("sensitive detail")
                .doesNotContain("authenticated-viewer");
        JsonNode problem = objectMapper.readTree(response.getContentAsByteArray());
        assertThat(problem.path("status").asInt()).isEqualTo(403);
        assertThat(problem.path("code").asText()).isEqualTo("OPERATOR_ACCESS_DENIED");
        assertThat(problem.path("requestId").asText()).isEqualTo("req-operator-forbidden");
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> fields = new HashSet<>();
        Iterator<String> names = node.fieldNames();
        names.forEachRemaining(fields::add);
        return fields;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {

        @Bean
        SecurityProbeController securityProbeController() {
            return new SecurityProbeController();
        }
    }

    @RestController
    static class SecurityProbeController {

        @PostMapping(PATH)
        Map<String, String> probe() {
            return Map.of("status", "ok");
        }
    }
}
