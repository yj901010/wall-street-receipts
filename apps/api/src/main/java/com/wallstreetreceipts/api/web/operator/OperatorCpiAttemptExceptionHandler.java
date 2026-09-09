package com.wallstreetreceipts.api.web.operator;

import java.net.URI;
import java.time.Clock;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.wallstreetreceipts.api.application.cpi.CpiAttemptQueryService;

@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = OperatorCpiAttemptController.class)
public class OperatorCpiAttemptExceptionHandler {
    private final Clock clock;
    public OperatorCpiAttemptExceptionHandler(Clock clock) { this.clock = clock; }
    @ExceptionHandler(CpiAttemptQueryService.InvalidQuery.class)
    ResponseEntity<ProblemDetail> invalid() { return problem(HttpStatus.BAD_REQUEST, "INVALID_CPI_ATTEMPT_QUERY"); }
    @ExceptionHandler(CpiAttemptQueryService.NotFound.class)
    ResponseEntity<ProblemDetail> absent() { return problem(HttpStatus.NOT_FOUND, "CPI_ATTEMPT_NOT_FOUND"); }
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ProblemDetail> unavailable() { return problem(HttpStatus.SERVICE_UNAVAILABLE, "CPI_ATTEMPT_QUERY_UNAVAILABLE"); }
    private ResponseEntity<ProblemDetail> problem(HttpStatus status, String code) {
        var problem = ProblemDetail.forStatusAndDetail(status, "CPI attempt query did not return evidence.");
        problem.setInstance(URI.create(OperatorCpiAttemptController.PATH));
        problem.setProperty("code", code);
        problem.setProperty("timestampKst", OperatorCpiAttemptController.kst(clock.instant()));
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).cacheControl(CacheControl.noStore()).body(problem);
    }
}
