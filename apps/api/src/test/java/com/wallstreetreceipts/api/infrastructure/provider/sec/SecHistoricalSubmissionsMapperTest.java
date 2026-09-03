package com.wallstreetreceipts.api.infrastructure.provider.sec;

import static com.wallstreetreceipts.api.support.SecFilingCatalogCaptureTestFixture.capture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegment.AdvertisedComparison;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

class SecHistoricalSubmissionsMapperTest {

    private static final Instant ROOT_CAPTURED_AT =
            Instant.parse("2026-08-25T01:00:00.123456Z");
    private static final Instant RECEIVED_AT =
            Instant.parse("2026-08-25T02:00:00.987654321Z");
    private static final Instant CANONICAL_RECEIVED_AT =
            RECEIVED_AT.truncatedTo(ChronoUnit.MICROS);
    private static final URI OFFICIAL_SEGMENT_URI = URI.create(
            "https://data.sec.gov/submissions/CIK0000320193-submissions-002.json");
    private static final URI TEST_BASE_URL = URI.create("https://127.0.0.1:9443");
    private static final String EXPECTED_TEST_ENDPOINT =
            "https://127.0.0.1:9443/submissions/"
                    + "CIK0000320193-submissions-002.json";

    private FilingCatalogCapture durableRoot;

    @BeforeEach
    void setUp() {
        durableRoot = capture(ROOT_CAPTURED_AT).withBodyRetention(
                BodyRetention.DURABLE_DECODED_BODY_RETAINED);
    }

    @Test
    void mapsExactBodyAndPreservesProviderOrderWithExplicitCountMismatch()
            throws Exception {
        byte[] original = validSegmentJson().getBytes(StandardCharsets.UTF_8);
        SecHistoricalRawResponseCapture raw = raw(OFFICIAL_SEGMENT_URI, original);
        original[0] = 'X';

        HistoricalFilingSegmentCapture result = raw.toCapture(
                durableRoot, 0, CANONICAL_RECEIVED_AT);

        assertThat(result.segment()).satisfies(segment -> {
            assertThat(segment.rootCaptureId()).isEqualTo(durableRoot.captureId());
            assertThat(segment.rootCapturedAt()).isEqualTo(ROOT_CAPTURED_AT);
            assertThat(segment.descriptorOrdinal()).isZero();
            assertThat(segment.descriptor().advertisedFilingCount()).isEqualTo(2_000);
            assertThat(segment.filings())
                    .extracting(filing -> filing.accessionNumber())
                    .containsExactly(
                            "0000320193-20-000002",
                            "0000320193-15-000001");
            assertThat(segment.observedFilingCount()).isEqualTo(2);
            assertThat(segment.observedFilingFrom()).hasToString("2015-01-01");
            assertThat(segment.observedFilingTo()).hasToString("2020-12-31");
            assertThat(segment.advertisedComparison())
                    .isEqualTo(AdvertisedComparison.COUNT_MISMATCH);
            assertThat(segment.sourceReceipt().bodyRetention())
                    .isEqualTo(BodyRetention.DECODED_BODY_ATTACHED_PENDING_PERSISTENCE);
        });
        assertThat(new String(result.decodedBody(), StandardCharsets.UTF_8))
                .isEqualTo(validSegmentJson());
    }

    @Test
    void preservesObservedMissingPrimaryDocumentsAsNullWithoutInventingAUri()
            throws Exception {
        String missingDocuments = validSegmentJson().replace(
                "\"primaryDocument\": [\"form2020.htm\", \"form2015.htm\"]",
                "\"primaryDocument\": [\"\", null]");

        HistoricalFilingSegmentCapture result = raw(missingDocuments).toCapture(
                durableRoot, 0, CANONICAL_RECEIVED_AT);

        assertThat(result.segment().filings())
                .extracting(filing -> filing.primaryDocumentUri())
                .containsExactly(null, null);
        assertThatThrownBy(() -> raw(missingDocuments.replace(
                "\"primaryDocument\": [\"\", null]",
                "\"primaryDocument\": [\"   \", null]")).toCapture(
                        durableRoot, 0, CANONICAL_RECEIVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("primaryDocument[0] must be empty or nonblank and trimmed");
    }

    @Test
    void rejectsMissingUnequalAndNullRequiredParallelArraysWithoutPartialSalvage() {
        String missing = validSegmentJson().replace(
                "  \"act\": [\"34\", \"34\"],\n", "");
        String unequal = validSegmentJson().replace(
                "\"filingDate\": [\"2020-12-31\", \"2015-01-01\"]",
                "\"filingDate\": [\"2020-12-31\"]");
        String requiredNull = validSegmentJson().replace(
                "\"form\": [\"10-K\", \"10-K\"]",
                "\"form\": [null, \"10-K\"]");

        assertThatThrownBy(() -> raw(missing).toCapture(
                durableRoot, 0, CANONICAL_RECEIVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("act must be present");
        assertThatThrownBy(() -> raw(unequal).toCapture(
                durableRoot, 0, CANONICAL_RECEIVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("arrays must have identical lengths")
                .hasMessageContaining("filingDate");
        assertThatThrownBy(() -> raw(requiredNull).toCapture(
                durableRoot, 0, CANONICAL_RECEIVED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("form[0] must not be null");
    }

    @Test
    void strictReaderRejectsDuplicateKeysScalarCoercionFloatIntegersAndTrailingTokens() {
        String duplicate = validSegmentJson().replace(
                "\"form\": [\"10-K\", \"10-K\"]",
                "\"form\": [\"10-K\", \"10-K\"], \"form\": [\"8-K\", \"8-K\"]");
        String scalarCoercion = validSegmentJson().replace(
                "\"form\": [\"10-K\", \"10-K\"]",
                "\"form\": [10, 10]");
        String floatInteger = validSegmentJson().replace(
                "\"isXBRL\": [1, 1]",
                "\"isXBRL\": [1.5, 1]");
        String trailing = validSegmentJson() + "{}";

        for (String invalid : new String[] {
                duplicate, scalarCoercion, floatInteger, trailing
        }) {
            assertThatThrownBy(() -> raw(invalid).toCapture(
                    durableRoot, 0, CANONICAL_RECEIVED_AT))
                    .isInstanceOf(IOException.class);
        }
    }

    @Test
    void rawCaptureRejectsMalformedUtf8BeforeReceiptOrParserCreation() {
        byte[] malformedUtf8 = {(byte) 0xc3, (byte) 0x28};

        assertThatThrownBy(() -> raw(OFFICIAL_SEGMENT_URI, malformedUtf8))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions response could not be read")
                .hasNoCause();
    }

    @Test
    void providerPerformsExactlyOneGetForTheCapturedDescriptorPath() {
        SecRequestRateLimiter limiter = new SecRequestRateLimiter();
        RestClient.Builder builder = restClientBuilder(limiter);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecEdgarHistoricalFilingSegmentProvider provider = provider(
                builder.build(), limiter);
        server.expect(once(), requestTo(EXPECTED_TEST_ENDPOINT))
                .andRespond(withSuccess(validSegmentJson(), MediaType.APPLICATION_JSON));

        HistoricalFilingSegmentCapture result = provider.loadHistoricalSegmentCapture(
                durableRoot, 0);

        assertThat(result.segment().sourceUri()).hasToString(EXPECTED_TEST_ENDPOINT);
        assertThat(result.segment().descriptorOrdinal()).isZero();
        assertThat(result.segment().advertisedComparison())
                .isEqualTo(AdvertisedComparison.COUNT_MISMATCH);
        server.verify();
    }

    @Test
    void providerApplies429CooldownAndDoesNotRetryOrStartASecondRequest() {
        SecRequestRateLimiter limiter = new SecRequestRateLimiter();
        RestClient.Builder builder = restClientBuilder(limiter);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecEdgarHistoricalFilingSegmentProvider provider = provider(
                builder.build(), limiter);
        server.expect(once(), requestTo(EXPECTED_TEST_ENDPOINT))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .header(HttpHeaders.RETRY_AFTER, "1200")
                        .contentType(MediaType.TEXT_PLAIN)
                        .body("secret historical response body"));

        assertThatThrownBy(() -> provider.loadHistoricalSegmentCapture(durableRoot, 0))
                .isInstanceOf(SecProviderException.class)
                .hasMessage("SEC submissions request failed with HTTP 429")
                .hasNoCause();
        assertThatThrownBy(() -> provider.loadHistoricalSegmentCapture(durableRoot, 0))
                .isInstanceOf(SecProviderException.class)
                .hasMessage(
                        "SEC submissions request was not started because the provider gate is closed")
                .hasNoCause();
        server.verify();
    }

    @Test
    void providerRejectsUnknownDescriptorBeforeStartingNetworkIo() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SecRequestRateLimiter limiter = new SecRequestRateLimiter();
        SecEdgarHistoricalFilingSegmentProvider provider = provider(
                builder.build(), limiter);

        assertThatThrownBy(() -> provider.loadHistoricalSegmentCapture(durableRoot, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("descriptorOrdinal must identify a captured historical descriptor");
        server.verify();
    }

    private static SecHistoricalRawResponseCapture raw(String body) {
        return raw(OFFICIAL_SEGMENT_URI, body.getBytes(StandardCharsets.UTF_8));
    }

    private static SecHistoricalRawResponseCapture raw(
            URI sourceUri,
            byte[] body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return SecHistoricalRawResponseCapture.capture(
                sourceUri, 200, headers, body, CANONICAL_RECEIVED_AT);
    }

    private static RestClient.Builder restClientBuilder(SecRequestRateLimiter limiter) {
        return RestClient.builder()
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .requestInterceptor(new SecRequestRateLimitInterceptor(limiter))
                .requestInterceptor(new SecResponseSizeLimitInterceptor())
                .requestInterceptor(new SecResponseDecompressionInterceptor());
    }

    private static SecEdgarHistoricalFilingSegmentProvider provider(
            RestClient restClient,
            SecRequestRateLimiter limiter) {
        Clock clock = Clock.fixed(RECEIVED_AT, ZoneOffset.UTC);
        return new SecEdgarHistoricalFilingSegmentProvider(
                restClient,
                TEST_BASE_URL,
                clock,
                limiter,
                new SecRetryAfterPolicy(clock));
    }

    private static String validSegmentJson() {
        return """
                {
                  "accessionNumber": ["0000320193-20-000002", "0000320193-15-000001"],
                  "filingDate": ["2020-12-31", "2015-01-01"],
                  "reportDate": ["2020-09-26", ""],
                  "acceptanceDateTime": ["2020-12-31T20:00:00.123456Z", "2015-01-01T12:00:00Z"],
                  "act": ["34", "34"],
                  "form": ["10-K", "10-K"],
                  "fileNumber": ["001-36743", "001-36743"],
                  "filmNumber": ["201234567", "151234568"],
                  "items": ["", ""],
                  "size": [123456, 234567],
                  "isXBRL": [1, 1],
                  "isInlineXBRL": [1, 0],
                  "primaryDocument": ["form2020.htm", "form2015.htm"],
                  "primaryDocDescription": ["Annual report", "Annual report"]
                }
                """;
    }
}
