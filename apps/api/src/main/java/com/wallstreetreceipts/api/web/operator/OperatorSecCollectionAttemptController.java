package com.wallstreetreceipts.api.web.operator;

import java.net.URI;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.wallstreetreceipts.api.application.filinghistory.ExecuteSecFilingHistoryCollectionAttemptService;
import com.wallstreetreceipts.api.application.filinghistory.SecFilingHistoryCollectionAttemptQueryService;
import com.wallstreetreceipts.api.domain.filing.SecFilingHistoryCollectionAttempt;

@RestController
@RequestMapping(OperatorSecCollectionAttemptController.PATH)
@ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
public class OperatorSecCollectionAttemptController {

    static final String PATH = "/internal/v1/sec/collection-attempts";

    private final ExecuteSecFilingHistoryCollectionAttemptService executionService;
    private final SecFilingHistoryCollectionAttemptQueryService queryService;

    public OperatorSecCollectionAttemptController(
            ExecuteSecFilingHistoryCollectionAttemptService executionService,
            SecFilingHistoryCollectionAttemptQueryService queryService) {
        this.executionService = executionService;
        this.queryService = queryService;
    }

    @PostMapping("/root")
    public ResponseEntity<OperatorSecCollectionAttemptResponses.Attempt> captureRoot(
            @RequestBody OperatorSecCollectionAttemptRequests.CaptureRoot command) {
        SecFilingHistoryCollectionAttempt attempt = executionService.captureRoot(
                command.operatorRequestId(), command.cik());
        return response(attempt);
    }

    @PostMapping("/exact-root")
    public ResponseEntity<OperatorSecCollectionAttemptResponses.Attempt> collectExactRoot(
            @RequestBody OperatorSecCollectionAttemptRequests.CollectExactRoot command) {
        SecFilingHistoryCollectionAttempt attempt = executionService.collectExactRoot(
                command.operatorRequestId(),
                command.rootCaptureId(),
                command.toDomainActions());
        return response(attempt);
    }

    @GetMapping("/{attemptId}")
    public ResponseEntity<OperatorSecCollectionAttemptResponses.Attempt> status(
            @PathVariable String attemptId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(OperatorSecCollectionAttemptResponseMapper.toResponse(
                        queryService.findByAttemptId(attemptId)));
    }

    private static ResponseEntity<OperatorSecCollectionAttemptResponses.Attempt> response(
            SecFilingHistoryCollectionAttempt attempt) {
        return ResponseEntity.ok()
                .location(URI.create(PATH + "/" + attempt.attemptId()))
                .cacheControl(CacheControl.noStore())
                .body(OperatorSecCollectionAttemptResponseMapper.toResponse(attempt));
    }
}
