package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.wallstreetreceipts.api.application.port.out.HistoricalFilingSegmentCaptureProvider;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;
import com.wallstreetreceipts.api.domain.filing.FilingCatalogCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentCapture;
import com.wallstreetreceipts.api.domain.filing.HistoricalFilingSegmentDescriptor;
import com.wallstreetreceipts.api.domain.source.SourceResponseReceipt.BodyRetention;

/** Performs one explicit GET for one descriptor from one durable root capture. */
public final class SecEdgarHistoricalFilingSegmentProvider
        implements HistoricalFilingSegmentCaptureProvider {

    private static final String SUBMISSIONS_PATH_PREFIX = "/submissions/";

    private final RestClient restClient;
    private final URI baseUrl;
    private final Clock clock;
    private final SecRequestRateLimiter rateLimiter;
    private final SecRetryAfterPolicy retryAfterPolicy;

    public SecEdgarHistoricalFilingSegmentProvider(
            RestClient restClient,
            URI baseUrl,
            Clock clock,
            SecRequestRateLimiter rateLimiter,
            SecRetryAfterPolicy retryAfterPolicy) {
        this.restClient = Objects.requireNonNull(restClient, "restClient");
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.rateLimiter = Objects.requireNonNull(rateLimiter, "rateLimiter");
        this.retryAfterPolicy = Objects.requireNonNull(retryAfterPolicy, "retryAfterPolicy");
    }

    @Override
    public HistoricalFilingSegmentCapture loadHistoricalSegmentCapture(
            FilingCatalogCapture durableRoot,
            int descriptorOrdinal) {
        HistoricalFilingSegmentDescriptor descriptor = exactDescriptor(
                durableRoot, descriptorOrdinal);
        URI endpoint = baseUrl.resolve(SUBMISSIONS_PATH_PREFIX + descriptor.fileName());
        SecHistoricalRawResponseCapture response = retrieve(endpoint);
        Instant receivedAt = response.receipt().capturedAt();
        try {
            return response.toCapture(durableRoot, descriptorOrdinal, receivedAt);
        } catch (java.io.IOException exception) {
            throw SecProviderException.unreadableResponse();
        } catch (RuntimeException exception) {
            throw SecProviderException.invalidResponse();
        }
    }

    @Override
    public String providerName() {
        return SecHistoricalSubmissionsMapper.PROVIDER_NAME;
    }

    private SecHistoricalRawResponseCapture retrieve(URI endpoint) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(
                            status -> status.value() != 200,
                            (request, providerResponse) -> {
                                int status = providerResponse.getStatusCode().value();
                                if (status == 429) {
                                    rateLimiter.applyCooldown(
                                            retryAfterPolicy.cooldownFor(
                                                    providerResponse.getHeaders()));
                                }
                                throw SecProviderException.httpStatus(status);
                            })
                    .toEntity(byte[].class);
            byte[] body = response.getBody();
            if (body == null || body.length == 0) {
                throw SecProviderException.unreadableResponse();
            }
            Instant capturedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            return SecHistoricalRawResponseCapture.capture(
                    endpoint,
                    response.getStatusCode().value(),
                    response.getHeaders(),
                    body,
                    capturedAt);
        } catch (SecProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            if (SecResponseSizeLimitInterceptor.causedByLimitExceeded(exception)) {
                throw SecProviderException.responseTooLarge();
            }
            throw SecProviderException.unreadableResponse();
        }
    }

    private static HistoricalFilingSegmentDescriptor exactDescriptor(
            FilingCatalogCapture rootCapture,
            int descriptorOrdinal) {
        Objects.requireNonNull(rootCapture, "durableRoot must not be null");
        FilingCatalog root = rootCapture.catalog();
        if (!SecSubmissionsMapper.PROVIDER_NAME.equals(root.provider())
                || !SecSubmissionsMapper.PRODUCT_NAME.equals(root.product())
                || !SecSubmissionsMapper.PARSER_VERSION.equals(
                        root.sourceReceipt().parserVersion())
                || root.sourceReceipt().bodyRetention()
                        != BodyRetention.DURABLE_DECODED_BODY_RETAINED) {
            throw new IllegalArgumentException(
                    "durableRoot must be a durable SEC submissions catalog capture");
        }
        if (descriptorOrdinal < 0
                || descriptorOrdinal >= root.historicalSegments().size()) {
            throw new IllegalArgumentException(
                    "descriptorOrdinal must identify a captured historical descriptor");
        }
        HistoricalFilingSegmentDescriptor descriptor =
                root.historicalSegments().get(descriptorOrdinal);
        String expectedPrefix = "CIK" + root.cik() + "-submissions-";
        if (!descriptor.fileName().startsWith(expectedPrefix)
                || !descriptor.fileName().matches(
                        "CIK[0-9]{10}-submissions-(?!000)[0-9]{3}\\.json")) {
            throw new IllegalArgumentException(
                    "captured descriptor filename is not bound to the catalog CIK");
        }
        return descriptor;
    }
}
