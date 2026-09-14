package com.wallstreetreceipts.api.web.scoring;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.wallstreetreceipts.api.application.scoring.TargetHitScoringReceiptService;

@RestController
@RequestMapping("/v1/calls/{callId}/target-hit-scoring-receipts")
public class TargetHitScoringReceiptController {
    private final TargetHitScoringReceiptService service;
    public TargetHitScoringReceiptController(TargetHitScoringReceiptService service) { this.service = service; }
    public record Page(String dataMode, String source, int limit, boolean hasMore, List<TargetHitScoringReceiptResponse> items) {}
    public record Error(String code, String dataMode) {}
    private static class InvalidQuery extends RuntimeException {}
    @GetMapping
    public ResponseEntity<Page> recent(@PathVariable String callId, @RequestParam Map<String, String> query) {
        validate(callId, query);
        var page = service.recent(callId);
        return response(HttpStatus.OK, new Page("DEMO", "PERSISTED_DEMO_INPUT_REPLAY", 20, page.hasMore(), page.items().stream().map(TargetHitScoringReceiptResponse::from).toList()));
    }
    @GetMapping("/{receiptId}")
    public ResponseEntity<TargetHitScoringReceiptResponse> find(@PathVariable String callId, @PathVariable String receiptId, @RequestParam Map<String, String> query) {
        validate(callId, query);
        if (!receiptId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new InvalidQuery();
        return response(HttpStatus.OK, TargetHitScoringReceiptResponse.from(service.find(callId, UUID.fromString(receiptId))));
    }
    private static void validate(String call, Map<String, String> query) {
        if (!call.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") || !query.isEmpty()) throw new InvalidQuery();
    }
    @ExceptionHandler(InvalidQuery.class)
    ResponseEntity<Error> invalid() { return response(HttpStatus.BAD_REQUEST, new Error("INVALID_TARGET_HIT_SCORING_QUERY", "DEMO")); }
    @ExceptionHandler(TargetHitScoringReceiptService.Missing.class)
    ResponseEntity<Error> missing() { return response(HttpStatus.NOT_FOUND, new Error("TARGET_HIT_SCORING_RECEIPT_NOT_FOUND", "DEMO")); }
    // Includes transaction acquisition/commit failures outside the service method's try block.
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Error> unavailable() { return response(HttpStatus.SERVICE_UNAVAILABLE, new Error("TARGET_HIT_SCORING_RECEIPT_UNAVAILABLE", "DEMO")); }
    private static <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
