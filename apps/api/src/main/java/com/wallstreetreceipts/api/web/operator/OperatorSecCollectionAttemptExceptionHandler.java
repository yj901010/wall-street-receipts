package com.wallstreetreceipts.api.web.operator;

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
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.wallstreetreceipts.api.application.filinghistory.ExactEvidenceNotAdmittedException;
import com.wallstreetreceipts.api.application.filinghistory.OperatorRequestConflictException;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptNotFoundException;
import com.wallstreetreceipts.api.web.RequestIdFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OperatorSecCollectionAttemptController.class)
public class OperatorSecCollectionAttemptExceptionHandler {

    private final Clock clock;

    public OperatorSecCollectionAttemptExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(SecFilingHistoryCollectionAttemptNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            SecFilingHistoryCollectionAttemptNotFoundException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "sec-collection-attempt-not-found",
                "SEC collection attempt not found",
                "The requested SEC collection attempt was not found.",
                "SEC_COLLECTION_ATTEMPT_NOT_FOUND",
                request);
    }

    @ExceptionHandler(OperatorRequestConflictException.class)
    ResponseEntity<ProblemDetail> handleConflict(
            OperatorRequestConflictException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "operator-request-conflict",
                "Operator request conflict",
                exception.getMessage(),
                "OPERATOR_REQUEST_CONFLICT",
                request);
    }

    @ExceptionHandler(ExactEvidenceNotAdmittedException.class)
    ResponseEntity<ProblemDetail> handleExactEvidenceNotAdmitted(
            ExactEvidenceNotAdmittedException exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "exact-evidence-not-admitted",
                "Exact evidence not admitted",
                "The referenced exact evidence was not admitted; no attempt was created.",
                "EXACT_EVIDENCE_NOT_ADMITTED",
                request);
    }

    @ExceptionHandler({
        IllegalArgumentException.class,
        HttpMessageNotReadableException.class,
        HttpMediaTypeNotSupportedException.class
    })
    ResponseEntity<ProblemDetail> handleInvalidCommand(
            Exception exception,
            HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "invalid-operator-command",
                "Invalid operator command",
                "The operator command is invalid.",
                "INVALID_OPERATOR_COMMAND",
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
