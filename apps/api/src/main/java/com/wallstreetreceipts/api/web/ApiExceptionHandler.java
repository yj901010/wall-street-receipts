package com.wallstreetreceipts.api.web;

import java.net.URI;
import java.time.Clock;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.wallstreetreceipts.api.application.call.AnalystCallNotFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    private final Clock clock;

    public ApiExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(AnalystCallNotFoundException.class)
    ResponseEntity<ProblemDetail> handleNotFound(
            AnalystCallNotFoundException exception,
            HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setType(URI.create("https://wall-street-receipts.invalid/problems/call-not-found"));
        problem.setTitle("Analyst call not found");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem(problem, HttpStatus.NOT_FOUND, "CALL_NOT_FOUND", request);
    }

    @ExceptionHandler({
            IllegalArgumentException.class,
            MethodArgumentTypeMismatchException.class,
            MethodArgumentNotValidException.class
    })
    ResponseEntity<ProblemDetail> handleBadRequest(Exception exception, HttpServletRequest request) {
        String detail = exception.getMessage() == null ? "The request is invalid." : exception.getMessage();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setType(URI.create("https://wall-street-receipts.invalid/problems/invalid-query"));
        problem.setTitle("Invalid query parameters");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem(problem, HttpStatus.BAD_REQUEST, "INVALID_QUERY", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleInternal(Exception exception, HttpServletRequest request) {
        String requestId = requestId(request);
        LOGGER.error("Unhandled API error requestId={}", requestId, exception);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected server error occurred.");
        problem.setType(URI.create("https://wall-street-receipts.invalid/problems/internal-error"));
        problem.setTitle("Internal server error");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem(problem, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
    }

    private ResponseEntity<ProblemDetail> problem(
            ProblemDetail detail,
            HttpStatus status,
            String code,
            HttpServletRequest request) {
        String requestId = requestId(request);
        detail.setProperty("code", code);
        detail.setProperty("timestamp", clock.instant());
        detail.setProperty("requestId", requestId);
        detail.setProperty("violations", List.of());
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .header(RequestIdFilter.HEADER, requestId)
                .body(detail);
    }

    private static String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE);
        return requestId instanceof String value && !value.isBlank() ? value : "req-unavailable";
    }
}
