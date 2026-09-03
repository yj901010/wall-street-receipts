package com.wallstreetreceipts.api.web.security;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.web.RequestIdFilter;

public final class OperatorApiSecurityProblemWriter
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final String NO_STORE = "no-store";

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public OperatorApiSecurityProblemWriter(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException {
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer realm=\"operator-api\"");
        writeProblem(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "operator-authentication-required",
                "Authentication required",
                "A valid operator credential is required.",
                "OPERATOR_AUTHENTICATION_REQUIRED");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception) throws IOException {
        writeProblem(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "operator-access-denied",
                "Operator access denied",
                "The authenticated identity cannot access this operator resource.",
                "OPERATOR_ACCESS_DENIED");
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String type,
            String title,
            String detail,
            String code) throws IOException {
        String requestId = requestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://wall-street-receipts.invalid/problems/" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
        problem.setProperty("requestId", requestId);
        problem.setProperty("violations", List.of());

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader(HttpHeaders.CACHE_CONTROL, NO_STORE);
        response.setHeader(RequestIdFilter.HEADER, requestId);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank() ? value : "req-unavailable";
    }
}
