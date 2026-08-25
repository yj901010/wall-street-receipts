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
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wallstreetreceipts.api.web.RequestIdFilter;

public final class ApiRequestRejectedHandler implements RequestRejectedHandler {

    private static final URI SAFE_INSTANCE = URI.create("/invalid-request");
    private static final String OPERATOR_API_PREFIX = "/internal/v1/sec/";

    private final Clock clock;
    private final ObjectMapper objectMapper;

    public ApiRequestRejectedHandler(Clock clock, ObjectMapper objectMapper) {
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            RequestRejectedException exception) throws IOException {
        String requestId = requestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "The request is invalid.");
        problem.setType(URI.create("https://wall-street-receipts.invalid/problems/invalid-query"));
        problem.setTitle("Invalid query parameters");
        problem.setInstance(SAFE_INSTANCE);
        problem.setProperty("code", "INVALID_QUERY");
        problem.setProperty("timestamp", clock.instant());
        problem.setProperty("requestId", requestId);
        problem.setProperty("violations", List.of());

        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        response.setHeader(RequestIdFilter.HEADER, requestId);
        if (isOperatorApiRequest(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        objectMapper.writeValue(response.getOutputStream(), problem);
    }

    private static boolean isOperatorApiRequest(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        return requestUri != null && requestUri.startsWith(OPERATOR_API_PREFIX);
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank()
                ? value
                : "req-unavailable";
    }
}
