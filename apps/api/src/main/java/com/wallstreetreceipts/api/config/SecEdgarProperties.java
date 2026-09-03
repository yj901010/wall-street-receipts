package com.wallstreetreceipts.api.config;

import java.net.URI;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.public-data.sec")
public record SecEdgarProperties(
        boolean enabled,
        URI baseUrl,
        String contactEmail) {

    private static final URI DEFAULT_BASE_URL = URI.create("https://data.sec.gov");

    public SecEdgarProperties {
        baseUrl = baseUrl == null ? DEFAULT_BASE_URL : baseUrl;
    }

    @Override
    public String toString() {
        return "SecEdgarProperties[enabled=" + enabled
                + ", baseUrl=<redacted>, contactEmail=<redacted>]";
    }
}
