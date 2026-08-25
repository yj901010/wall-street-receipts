package com.wallstreetreceipts.api.config;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.wallstreetreceipts.api.infrastructure.provider.sec.SecEdgarFilingCatalogProvider;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecProviderConfigurationException;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRequestRateLimitInterceptor;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRequestRateLimiter;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecResponseDecompressionInterceptor;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecResponseSizeLimitInterceptor;
import com.wallstreetreceipts.api.infrastructure.provider.sec.SecRetryAfterPolicy;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SecEdgarProperties.class)
@ConditionalOnProperty(
        prefix = "app.public-data.sec",
        name = "enabled",
        havingValue = "true")
public class SecEdgarConfiguration {

    private static final String USER_AGENT_PRODUCT = "WallStreetReceipts/0.1";
    private static final URI OFFICIAL_BASE_URL = URI.create("https://data.sec.gov");
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern CONTACT_EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+"
                    + "(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
                    + "[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");

    private final boolean loopbackTestBaseUrlAllowed;

    public SecEdgarConfiguration() {
        this(false);
    }

    SecEdgarConfiguration(boolean loopbackTestBaseUrlAllowed) {
        this.loopbackTestBaseUrlAllowed = loopbackTestBaseUrlAllowed;
    }

    @Bean
    SecRequestRateLimiter secRequestRateLimiter() {
        return new SecRequestRateLimiter();
    }

    @Bean
    SecRetryAfterPolicy secRetryAfterPolicy(Clock clock) {
        return new SecRetryAfterPolicy(clock);
    }

    @Bean
    RestClient secEdgarRestClient(
            RestClient.Builder restClientBuilder,
            SecEdgarProperties properties,
            SecRequestRateLimiter rateLimiter) {
        return configureRestClient(restClientBuilder.clone(), properties, rateLimiter).build();
    }

    RestClient.Builder configureRestClient(
            RestClient.Builder restClientBuilder,
            SecEdgarProperties properties,
            SecRequestRateLimiter rateLimiter) {
        URI baseUrl = requireValidBaseUrl(properties.baseUrl());
        String contactEmail = requireContactEmail(properties.contactEmail());
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return restClientBuilder
                .baseUrl(baseUrl.toString())
                .defaultHeader(HttpHeaders.USER_AGENT, USER_AGENT_PRODUCT + " (" + contactEmail + ")")
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip, deflate")
                .requestInterceptor(new SecRequestRateLimitInterceptor(rateLimiter))
                .requestInterceptor(new SecResponseSizeLimitInterceptor())
                .requestInterceptor(new SecResponseDecompressionInterceptor())
                .requestFactory(requestFactory);
    }

    @Bean
    SecEdgarFilingCatalogProvider secEdgarFilingCatalogProvider(
            @Qualifier("secEdgarRestClient") RestClient restClient,
            SecEdgarProperties properties,
            Clock clock,
            SecRequestRateLimiter rateLimiter,
            SecRetryAfterPolicy retryAfterPolicy) {
        return new SecEdgarFilingCatalogProvider(
                restClient,
                properties.baseUrl(),
                clock,
                rateLimiter,
                retryAfterPolicy);
    }

    private static String requireContactEmail(String configuredEmail) {
        if (configuredEmail == null || configuredEmail.isBlank()) {
            throw new SecProviderConfigurationException(
                    "SEC provider is enabled but SEC_CONTACT_EMAIL is not configured");
        }

        String contactEmail = configuredEmail.strip();
        if (!CONTACT_EMAIL.matcher(contactEmail).matches()) {
            throw new SecProviderConfigurationException("SEC_CONTACT_EMAIL is invalid");
        }
        return contactEmail;
    }

    private URI requireValidBaseUrl(URI configuredBaseUrl) {
        if (OFFICIAL_BASE_URL.equals(configuredBaseUrl)) {
            return configuredBaseUrl;
        }

        boolean supportedScheme = "https".equalsIgnoreCase(configuredBaseUrl.getScheme())
                || "http".equalsIgnoreCase(configuredBaseUrl.getScheme());
        boolean rootPath = configuredBaseUrl.getPath() == null
                || configuredBaseUrl.getPath().isEmpty()
                || "/".equals(configuredBaseUrl.getPath());
        if (!configuredBaseUrl.isAbsolute()
                || !loopbackTestBaseUrlAllowed
                || !supportedScheme
                || !isLoopbackHost(configuredBaseUrl.getHost())
                || !rootPath
                || configuredBaseUrl.getUserInfo() != null
                || configuredBaseUrl.getQuery() != null
                || configuredBaseUrl.getFragment() != null) {
            throw new SecProviderConfigurationException("SEC provider base URL is invalid");
        }
        return configuredBaseUrl;
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) {
            return false;
        }
        if ("localhost".equalsIgnoreCase(host)
                || "::1".equals(host)
                || "[::1]".equals(host)) {
            return true;
        }

        String[] octets = host.split("\\.", -1);
        if (octets.length != 4 || !"127".equals(octets[0])) {
            return false;
        }
        for (String octet : octets) {
            try {
                int value = Integer.parseInt(octet);
                if (value < 0 || value > 255 || !Integer.toString(value).equals(octet)) {
                    return false;
                }
            } catch (NumberFormatException exception) {
                return false;
            }
        }
        return true;
    }
}
