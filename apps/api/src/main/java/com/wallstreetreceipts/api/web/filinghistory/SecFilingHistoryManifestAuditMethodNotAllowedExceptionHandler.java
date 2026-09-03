package com.wallstreetreceipts.api.web.filinghistory;

import java.net.URI;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wallstreetreceipts.api.web.RequestIdFilter;

/**
 * Handles method mismatch before Spring has selected a controller. Legacy routes keep their
 * existing 405 representation; exact manifest audit routes additionally receive no-store.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class SecFilingHistoryManifestAuditMethodNotAllowedExceptionHandler {

    private static final String AUDIT_PATH_PREFIX =
            "/v1/sec/filing-history/manifests/";

    private final Clock clock;

    public SecFilingHistoryManifestAuditMethodNotAllowedExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ProblemDetail> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.METHOD_NOT_ALLOWED,
                "This resource is read-only.");
        problem.setType(URI.create(
                "https://wall-street-receipts.invalid/problems/method-not-allowed"));
        problem.setTitle("Method not allowed");
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "METHOD_NOT_ALLOWED");
        problem.setProperty("timestamp", clock.instant());
        problem.setProperty("requestId", requestId);
        problem.setProperty("violations", List.of());

        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .headers(headers -> headers.setAllow(
                        exception.getSupportedHttpMethods()))
                .header(RequestIdFilter.HEADER, requestId);
        if (isExactManifestAuditPath(request)) {
            response.cacheControl(CacheControl.noStore());
        }
        return response.body(problem);
    }

    private static boolean isExactManifestAuditPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        String applicationPath = contextPath == null || contextPath.isEmpty()
                ? requestUri
                : requestUri.substring(contextPath.length());
        return applicationPath.startsWith(AUDIT_PATH_PREFIX);
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank()
                ? value
                : "req-unavailable";
    }
}
