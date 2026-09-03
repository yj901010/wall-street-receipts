package com.wallstreetreceipts.api.web.filinghistory;

import java.net.URI;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wallstreetreceipts.api.application.filinghistory.InvalidSecFilingHistoryManifestAuditQueryException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryManifestAuditNotFoundException;
import com.wallstreetreceipts.api.web.RequestIdFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = SecFilingHistoryManifestAuditController.class)
public class SecFilingHistoryManifestAuditExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SecFilingHistoryManifestAuditExceptionHandler.class);

    private final Clock clock;

    public SecFilingHistoryManifestAuditExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(InvalidSecFilingHistoryManifestAuditQueryException.class)
    ResponseEntity<ProblemDetail> handleInvalidQuery(
            InvalidSecFilingHistoryManifestAuditQueryException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-sec-filing-history-manifest-audit-query",
                "Invalid SEC filing-history manifest audit query",
                "The SEC filing-history manifest audit query is invalid.",
                "INVALID_SEC_FILING_HISTORY_MANIFEST_AUDIT_QUERY",
                request);
    }

    @ExceptionHandler(SecFilingHistoryManifestAuditNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            SecFilingHistoryManifestAuditNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "sec-filing-history-manifest-not-found",
                "SEC filing-history manifest not found",
                "The requested SEC filing-history manifest was not found.",
                "SEC_FILING_HISTORY_MANIFEST_NOT_FOUND",
                request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleInternal(
            Exception exception,
            HttpServletRequest request) {
        String requestId = requestId(request);
        LOGGER.error("Exact SEC manifest audit failed requestId={}", requestId, exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal server error",
                "An unexpected server error occurred.",
                "INTERNAL_ERROR",
                request);
    }

    private ResponseEntity<ProblemDetail> problem(
            HttpStatus status,
            String type,
            String title,
            String detail,
            String code,
            HttpServletRequest request) {
        String requestId = requestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://wall-street-receipts.invalid/problems/" + type));
        problem.setTitle(title);
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        problem.setProperty("timestamp", clock.instant());
        problem.setProperty("requestId", requestId);
        problem.setProperty("violations", List.of());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .header(RequestIdFilter.HEADER, requestId)
                .body(problem);
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank()
                ? value
                : "req-unavailable";
    }
}
