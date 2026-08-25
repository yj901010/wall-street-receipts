package com.wallstreetreceipts.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPOutputStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarFilingCatalogProvider;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecProviderConfigurationException;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecProviderException;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRequestRateLimiter;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecResponseSizeLimitInterceptor;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRetryAfterPolicy;

class SecEdgarConfigurationTest {

    private static final URI TEST_BASE_URL = URI.create("https://127.0.0.1:9443");
    private static final String CONTACT_EMAIL = "operations@example.test";
    private static final Instant RECEIVED_AT = Instant.parse("2026-08-21T00:00:00.123456789Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(RECEIVED_AT, ZoneOffset.UTC);
    private static final String EXPECTED_ENDPOINT =
            "https://127.0.0.1:9443/submissions/CIK0000320193.json";

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;
    private SecEdgarFilingCatalogProvider provider;
    private SecRequestRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        SecEdgarProperties properties =
                new SecEdgarProperties(true, TEST_BASE_URL, CONTACT_EMAIL);
        rateLimiter = new SecRequestRateLimiter();
        restClientBuilder = new SecEdgarConfiguration(true)
                .configureRestClient(RestClient.builder(), properties, rateLimiter);
        server = MockRestServiceServer.bindTo(restClientBuilder).build();

        RestClient restClient = restClientBuilder.build();
        provider = new SecEdgarFilingCatalogProvider(
                restClient,
                TEST_BASE_URL,
                FIXED_CLOCK,
                rateLimiter,
                new SecRetryAfterPolicy(FIXED_CLOCK));
    }

    @Test
    void requestsPaddedCikWithDeclaredUserAgentAndMapsSuccessfulResponse() {
        server.expect(once(), requestTo(EXPECTED_ENDPOINT))
                .andExpect(header(
                        HttpHeaders.USER_AGENT,
                        "WallStreetReceipts/0.1 (" + CONTACT_EMAIL + ")"))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(header(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate"))
                .andRespond(withSuccess(validSubmissionsJson(), MediaType.APPLICATION_JSON));

        FilingCatalog catalog = provider.loadRecentFilings("320193");

        assertThat(catalog.cik()).isEqualTo("0000320193");
        assertThat(catalog.sourceUri()).hasToString(EXPECTED_ENDPOINT);
        assertThat(catalog.processingTime()).isEqualTo(RECEIVED_AT.truncatedTo(ChronoUnit.MICROS));
        assertThat(catalog.capturedAt()).isEqualTo(RECEIVED_AT.truncatedTo(ChronoUnit.MICROS));
        assertThat(catalog.filings()).hasSize(1);
        assertThat(catalog.filings().getFirst().accessionNumber())
                .isEqualTo("0000320193-26-000001");
        server.verify();
    }

    @Test
    void decodesAdvertisedGzipResponses() throws IOException {
        server.expect(once(), requestTo(EXPECTED_ENDPOINT))
                .andRespond(withSuccess(gzip(validSubmissionsJson()), MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.CONTENT_ENCODING, "gzip"));

        FilingCatalog catalog = provider.loadRecentFilings("320193");

        assertThat(catalog.filings()).hasSize(1);
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {302, 404, 503})
    void turnsEveryNonSuccessStatusIntoASanitizedExceptionWithoutRetry(int statusCode) {
        server.expect(once(), requestTo(EXPECTED_ENDPOINT))
                .andRespond(withStatus(HttpStatusCode.valueOf(statusCode))
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("secret-body api-key=do-not-expose " + CONTACT_EMAIL));

        assertThatThrownBy(() -> provider.loadRecentFilings("320193"))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions request failed with HTTP " + statusCode)
                .hasNoCause()
                .message()
                .doesNotContain("320193", "127.0.0.1", CONTACT_EMAIL, "do-not-expose");
        server.verify();
    }

    @Test
    void opensFailClosedCooldownAfter429BeforeInspectingAnOversizedErrorBody() {
        server.expect(once(), requestTo(EXPECTED_ENDPOINT))
                .andRespond(withStatus(HttpStatusCode.valueOf(429))
                        .header(HttpHeaders.RETRY_AFTER, "1200")
                        .header(
                                HttpHeaders.CONTENT_LENGTH,
                                Long.toString(
                                        SecResponseSizeLimitInterceptor
                                                        .MAX_DECOMPRESSED_RESPONSE_BYTES
                                                + 1))
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("secret-body api-key=do-not-expose " + CONTACT_EMAIL));

        assertThatThrownBy(() -> provider.loadRecentFilings("320193"))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions request failed with HTTP 429")
                .hasNoCause()
                .message()
                .doesNotContain("1200", "320193", CONTACT_EMAIL, "do-not-expose");

        assertThatThrownBy(() -> provider.loadRecentFilings("320193"))
                .isInstanceOf(SecProviderException.class)
                .hasMessage(
                        "SEC submissions request was not started because the provider gate is closed")
                .hasNoCause()
                .message()
                .doesNotContain("1200", "320193", CONTACT_EMAIL, "do-not-expose");
        server.verify();
    }

    @Test
    void rejectsDeclaredIdentityResponseOverDecodedLimitBeforeParsing() {
        server.expect(once(), requestTo(EXPECTED_ENDPOINT))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON)
                        .header(
                                HttpHeaders.CONTENT_LENGTH,
                                Long.toString(
                                        SecResponseSizeLimitInterceptor
                                                        .MAX_DECOMPRESSED_RESPONSE_BYTES
                                                + 1)));

        assertThatThrownBy(() -> provider.loadRecentFilings("320193"))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response exceeded the size limit")
                .hasNoCause();
        server.verify();
    }

    @Test
    void rejectsMalformedPayloadWithoutExposingBodyOrEndpoint() {
        server.expect(once(), requestTo(EXPECTED_ENDPOINT))
                .andRespond(withSuccess(
                        "{\"cik\":\"secret-body-do-not-expose\",",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> provider.loadRecentFilings("320193"))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response could not be read")
                .hasNoCause()
                .message()
                .doesNotContain("320193", "127.0.0.1", CONTACT_EMAIL, "do-not-expose");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "0", "0000000000", "12345678901", "12A3", "-1"})
    void rejectsInvalidCikBeforeMakingARequest(String invalidCik) {
        assertThatThrownBy(() -> provider.loadRecentFilings(invalidCik))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CIK must contain between 1 and 10 digits")
                .hasNoCause();
        server.verify();
    }

    @Test
    void rejectsNullCikBeforeMakingARequest() {
        assertThatThrownBy(() -> provider.loadRecentFilings(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("CIK must contain between 1 and 10 digits")
                .hasNoCause();
        server.verify();
    }

    @Test
    void remainsDisabledByDefault() {
        contextRunner().run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(SecEdgarFilingCatalogProvider.class);
        });
    }

    @Test
    void usesTheFixedOfficialBaseUrlWhenNoOverrideIsConfigured() {
        SecEdgarProperties properties = new SecEdgarProperties(false, null, null);

        assertThat(properties.baseUrl()).isEqualTo(URI.create("https://data.sec.gov"));
    }

    @Test
    void failsStartupWhenEnabledWithoutContactEmailAndDoesNotExposeConfiguration() {
        contextRunner()
                .withPropertyValues(
                        "app.public-data.sec.enabled=true",
                        "app.public-data.sec.base-url=https://data.sec.gov",
                        "app.public-data.sec.contact-email=")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(SecProviderConfigurationException.class)
                            .rootCause()
                            .hasMessage("SEC provider is enabled but SEC_CONTACT_EMAIL is not configured")
                            .message()
                            .doesNotContain(CONTACT_EMAIL);
                });
    }

    @Test
    void rejectsArbitraryBaseUrlsWithoutExposingThem() {
        SecEdgarProperties properties = new SecEdgarProperties(
                true,
                URI.create("https://api-key@untrusted.example/secret?key=do-not-expose"),
                CONTACT_EMAIL);

        assertThatThrownBy(() -> new SecEdgarConfiguration()
                .configureRestClient(
                        RestClient.builder(), properties, new SecRequestRateLimiter()))
                .isInstanceOf(SecProviderConfigurationException.class)
                .hasMessage("SEC provider base URL is invalid")
                .hasNoCause()
                .message()
                .doesNotContain("untrusted", "api-key", "do-not-expose", CONTACT_EMAIL);
    }

    @Test
    void productionConfigurationRejectsLoopbackTestOverride() {
        SecEdgarProperties properties = new SecEdgarProperties(
                true,
                TEST_BASE_URL,
                CONTACT_EMAIL);

        assertThatThrownBy(() -> new SecEdgarConfiguration()
                .configureRestClient(
                        RestClient.builder(), properties, new SecRequestRateLimiter()))
                .isInstanceOf(SecProviderConfigurationException.class)
                .hasMessage("SEC provider base URL is invalid")
                .hasNoCause();
    }

    @Test
    void rejectsInvalidContactEmailWithoutExposingIt() {
        String invalidEmail = "do-not-expose";
        SecEdgarProperties properties =
                new SecEdgarProperties(true, TEST_BASE_URL, invalidEmail);

        assertThatThrownBy(() -> new SecEdgarConfiguration(true)
                .configureRestClient(
                        RestClient.builder(), properties, new SecRequestRateLimiter()))
                .isInstanceOf(SecProviderConfigurationException.class)
                .hasMessage("SEC_CONTACT_EMAIL is invalid")
                .hasNoCause()
                .message()
                .doesNotContain(invalidEmail);
    }

    @Test
    void redactsConfigurationPropertiesStringRepresentation() {
        SecEdgarProperties properties = new SecEdgarProperties(
                true,
                URI.create("https://data.sec.gov"),
                CONTACT_EMAIL);

        assertThat(properties.toString())
                .contains("enabled=true", "baseUrl=<redacted>", "contactEmail=<redacted>")
                .doesNotContain("data.sec.gov", CONTACT_EMAIL);
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(SecEdgarConfiguration.class, TestDependencies.class);
    }

    private static String validSubmissionsJson() {
        return """
                {
                  "cik": "0000320193",
                  "filings": {
                    "recent": {
                      "accessionNumber": ["0000320193-26-000001"],
                      "filingDate": ["2026-08-20"],
                      "reportDate": ["2026-06-27"],
                      "acceptanceDateTime": ["2026-08-20T20:00:00.123456Z"],
                      "act": ["34"],
                      "form": ["10-Q"],
                      "fileNumber": ["001-36743"],
                      "filmNumber": ["261234567"],
                      "items": [""],
                      "size": [123456],
                      "isXBRL": [1],
                      "isInlineXBRL": [1],
                      "primaryDocument": ["xslF345X06/form4.xml"],
                      "primaryDocDescription": ["10-Q"]
                    }
                  }
                }
                """;
    }

    private static byte[] gzip(String value) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(value.getBytes(StandardCharsets.UTF_8));
        }
        return compressed.toByteArray();
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        Clock clock() {
            return FIXED_CLOCK;
        }
    }
}
