package com.wallstreetreceipts.api.web.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

public final class OperatorBearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer";

    private final RequestMatcher protectedPath;
    private final AuthenticationProvider authenticationProvider;

    public OperatorBearerTokenAuthenticationFilter(
            RequestMatcher protectedPath,
            AuthenticationProvider authenticationProvider) {
        this.protectedPath = protectedPath;
        this.authenticationProvider = authenticationProvider;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !protectedPath.matches(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        SecurityContextHolder.clearContext();
        String bearerToken = bearerToken(request);
        if (bearerToken != null) {
            try {
                Authentication authentication = authenticationProvider.authenticate(
                        OperatorBearerAuthenticationToken.unauthenticated(bearerToken));
                SecurityContext context = SecurityContextHolder.createEmptyContext();
                context.setAuthentication(authentication);
                SecurityContextHolder.setContext(context);
            } catch (AuthenticationException exception) {
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        List<String> authorizationHeaders =
                Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (authorizationHeaders.size() != 1) {
            return null;
        }

        String header = authorizationHeaders.getFirst();
        int separator = header.indexOf(' ');
        if (separator != BEARER.length()
                || !header.regionMatches(true, 0, BEARER, 0, BEARER.length())) {
            return null;
        }

        String token = header.substring(separator + 1);
        if (token.length() != 44 || token.charAt(43) != '=') {
            return null;
        }

        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(token);
        } catch (IllegalArgumentException exception) {
            return null;
        }
        try {
            if (decoded.length != 32
                    || !Base64.getEncoder().encodeToString(decoded).equals(token)) {
                return null;
            }
            return token;
        } finally {
            Arrays.fill(decoded, (byte) 0);
        }
    }
}
