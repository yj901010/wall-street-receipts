package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.wallstreetreceipts.api.application.port.out.FilingCatalogProvider;
import com.wallstreetreceipts.api.domain.filing.FilingCatalog;

public final class SecEdgarFilingCatalogProvider implements FilingCatalogProvider {

    private static final String PROVIDER_NAME = "sec-edgar";
    private static final String SUBMISSIONS_PATH_TEMPLATE = "/submissions/CIK%s.json";

    private final RestClient restClient;
    private final URI baseUrl;
    private final Clock clock;
    private final SecRequestRateLimiter rateLimiter;
    private final SecRetryAfterPolicy retryAfterPolicy;

    public SecEdgarFilingCatalogProvider(
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
    public FilingCatalog loadRecentFilings(String cik) {
        String paddedCik = normalizeCik(cik);
        URI endpoint = baseUrl.resolve(SUBMISSIONS_PATH_TEMPLATE.formatted(paddedCik));
        SecRawResponseCapture capture = retrieve(endpoint);
        Instant receivedAt = capture.receipt().capturedAt();

        try {
            return capture.toCanonical(receivedAt);
        } catch (java.io.IOException exception) {
            throw SecProviderException.unreadableResponse();
        } catch (RuntimeException exception) {
            throw SecProviderException.invalidResponse();
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private SecRawResponseCapture retrieve(URI endpoint) {
        try {
            ResponseEntity<byte[]> response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(
                            status -> status.value() != 200,
                            (request, providerResponse) -> {
                                int statusCode = providerResponse.getStatusCode().value();
                                if (statusCode == 429) {
                                    rateLimiter.applyCooldown(
                                            retryAfterPolicy.cooldownFor(
                                                    providerResponse.getHeaders()));
                                }
                                throw SecProviderException.httpStatus(statusCode);
                            })
                    .toEntity(byte[].class);

            byte[] decodedBody = response.getBody();
            if (decodedBody == null || decodedBody.length == 0) {
                throw SecProviderException.unreadableResponse();
            }
            Instant capturedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            return SecRawResponseCapture.capture(
                    endpoint,
                    response.getStatusCode().value(),
                    response.getHeaders(),
                    decodedBody,
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

    private static String normalizeCik(String cik) {
        if (cik == null
                || !cik.matches("\\d{1,10}")
                || cik.chars().allMatch(character -> character == '0')) {
            throw new IllegalArgumentException("CIK must contain between 1 and 10 digits");
        }
        return "0".repeat(10 - cik.length()) + cik;
    }
}
