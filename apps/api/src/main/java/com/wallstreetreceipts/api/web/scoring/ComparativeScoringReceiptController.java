package com.wallstreetreceipts.api.web.scoring;

import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import com.wallstreetreceipts.api.application.scoring.ComparativeScoringReceiptService;

@RestController
@RequestMapping("/v1/calls/{callId}/comparative-scoring-receipts")
public class ComparativeScoringReceiptController {
    private final ComparativeScoringReceiptService service;
    public ComparativeScoringReceiptController(ComparativeScoringReceiptService service) { this.service = service; }
    public record Page(String dataMode, String source, int limit, boolean hasMore, List<ComparativeScoringReceiptResponse> items) {}
    public record Error(String code, String dataMode) {}
    private static class InvalidQuery extends RuntimeException {}
    @GetMapping
    public ResponseEntity<Page> recent(@PathVariable String callId, @RequestParam Map<String, String> query) {
        validate(callId, query);
        var page = service.recent(callId);
        return response(HttpStatus.OK, new Page("DEMO", "PERSISTED_DEMO_INPUT_REPLAY", 20, page.hasMore(), page.items().stream().map(ComparativeScoringReceiptResponse::from).toList()));
    }
    @GetMapping("/{receiptId}")
    public ResponseEntity<ComparativeScoringReceiptResponse> find(@PathVariable String callId, @PathVariable String receiptId, @RequestParam Map<String, String> query) {
        validate(callId, query);
        if (!receiptId.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw new InvalidQuery();
        return response(HttpStatus.OK, ComparativeScoringReceiptResponse.from(service.find(callId, UUID.fromString(receiptId))));
    }
    private static void validate(String call, Map<String, String> query) {
        if (!call.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") || !query.isEmpty()) throw new InvalidQuery();
    }
    @ExceptionHandler(InvalidQuery.class)
    ResponseEntity<Error> invalid() { return response(HttpStatus.BAD_REQUEST, new Error("INVALID_COMPARATIVE_SCORING_QUERY", "DEMO")); }
    @ExceptionHandler(ComparativeScoringReceiptService.Missing.class)
    ResponseEntity<Error> missing() { return response(HttpStatus.NOT_FOUND, new Error("COMPARATIVE_SCORING_RECEIPT_NOT_FOUND", "DEMO")); }
    // Includes transaction acquisition/commit failures outside the service method's try block.
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<Error> unavailable() { return response(HttpStatus.SERVICE_UNAVAILABLE, new Error("COMPARATIVE_SCORING_RECEIPT_UNAVAILABLE", "DEMO")); }
    private static <T> ResponseEntity<T> response(HttpStatus status, T body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
