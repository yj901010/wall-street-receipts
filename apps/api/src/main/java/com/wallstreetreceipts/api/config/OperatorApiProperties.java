package com.wallstreetreceipts.api.config;

import java.util.regex.Pattern;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.operator-api")
public record OperatorApiProperties(
        boolean enabled,
        String tokenSha256) {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final String ALL_ZERO_SHA_256 = "0".repeat(64);

    public OperatorApiProperties {
        if (enabled && (tokenSha256 == null
                || !SHA_256.matcher(tokenSha256).matches()
                || ALL_ZERO_SHA_256.equals(tokenSha256))) {
            throw new IllegalArgumentException(
                    "OPERATOR_API_TOKEN_SHA256 must be a non-zero SHA-256 digest encoded as exactly "
                            + "64 lowercase hexadecimal characters when OPERATOR_API_ENABLED is true");
        }
    }

    @Override
    public String toString() {
        return "OperatorApiProperties[enabled=" + enabled + ", tokenSha256=<redacted>]";
    }
}
