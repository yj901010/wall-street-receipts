package com.wallstreetreceipts.api.web.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import com.wallstreetreceipts.api.config.OperatorApiProperties;

public final class OperatorBearerTokenAuthenticationProvider implements AuthenticationProvider {

    private static final byte[] DISABLED_DIGEST = new byte[32];

    private final boolean enabled;
    private final byte[] expectedDigest;
    private final String authority;

    public OperatorBearerTokenAuthenticationProvider(
            OperatorApiProperties properties,
            String authority) {
        this.enabled = properties.enabled();
        this.expectedDigest = properties.enabled()
                ? HexFormat.of().parseHex(properties.tokenSha256())
                : DISABLED_DIGEST.clone();
        this.authority = authority;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        Object credentials = authentication.getCredentials();
        if (!enabled || !(credentials instanceof String bearerToken) || bearerToken.isEmpty()) {
            throw invalidCredential();
        }

        byte[] actualDigest = sha256(bearerToken);
        boolean matches = MessageDigest.isEqual(expectedDigest, actualDigest);
        java.util.Arrays.fill(actualDigest, (byte) 0);
        if (authentication instanceof CredentialsContainer credentialsContainer) {
            credentialsContainer.eraseCredentials();
        }
        if (!matches) {
            throw invalidCredential();
        }

        return OperatorBearerAuthenticationToken.authenticated(
                List.of(new SimpleGrantedAuthority(authority)));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OperatorBearerAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static byte[] sha256(String bearerToken) {
        byte[] tokenBytes = bearerToken.getBytes(StandardCharsets.UTF_8);
        try {
            return MessageDigest.getInstance("SHA-256").digest(tokenBytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } finally {
            java.util.Arrays.fill(tokenBytes, (byte) 0);
        }
    }

    private static BadCredentialsException invalidCredential() {
        return new BadCredentialsException("Invalid operator credential");
    }
}
