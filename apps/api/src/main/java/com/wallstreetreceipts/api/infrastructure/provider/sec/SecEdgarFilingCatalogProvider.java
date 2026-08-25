package com.wallstreetreceipts.api.infrastructure.provider.sec;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

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
        SecSubmissionsResponse response = retrieve(endpoint);
        Instant receivedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

        try {
            return SecSubmissionsMapper.toCanonical(response, endpoint, receivedAt, receivedAt);
        } catch (RuntimeException exception) {
            throw SecProviderException.invalidResponse();
        }
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    private SecSubmissionsResponse retrieve(URI endpoint) {
        try {
            SecSubmissionsResponse response = restClient.get()
                    .uri(endpoint)
                    .retrieve()
                    .onStatus(
                            status -> !status.is2xxSuccessful(),
                            (request, providerResponse) -> {
                                int statusCode = providerResponse.getStatusCode().value();
                                if (statusCode == 429) {
                                    rateLimiter.applyCooldown(
                                            retryAfterPolicy.cooldownFor(
                                                    providerResponse.getHeaders()));
                                }
                                throw SecProviderException.httpStatus(statusCode);
                            })
                    .body(SecSubmissionsResponse.class);

            if (response == null) {
                throw SecProviderException.unreadableResponse();
            }
            return response;
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
