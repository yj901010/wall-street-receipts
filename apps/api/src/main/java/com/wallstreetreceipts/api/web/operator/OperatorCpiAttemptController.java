package com.wallstreetreceipts.api.web.operator;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.wallstreetreceipts.api.application.cpi.CpiAttemptQueryService;
import com.wallstreetreceipts.api.application.cpi.CpiAttemptReader;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;

@RestController
@RequestMapping(OperatorCpiAttemptController.PATH)
@ConditionalOnProperty(prefix = "app.operator-api", name = "enabled", havingValue = "true")
public class OperatorCpiAttemptController {
    public static final String PATH = "/internal/v1/cpi/collection-attempts";
    private static final List<String> LIMITATIONS = List.of("NOT_A_HEARTBEAT", "NOT_CPI_FRESHNESS",
            "NOT_POINT_IN_TIME_HISTORY", "RECEIPTS_NOT_REPLAYED", "NO_PROVIDER_REQUEST_PROOF",
            "NO_PRE_V11_OR_MISSING_START_HISTORY");
    private final CpiAttemptQueryService query;
    public OperatorCpiAttemptController(CpiAttemptQueryService query) { this.query = query; }

    @GetMapping
    public ResponseEntity<Recent> recent(@RequestParam MultiValueMap<String, String> parameters) {
        rejectParameters(parameters);
        var result = query.recent();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Recent(metadata(result.observedAt()),
                CpiAttemptReader.RECENT_LIMIT, result.hasMore(), "STARTED_AT_DESC_ATTEMPT_ID_DESC",
                result.attempts().stream().map(OperatorCpiAttemptController::row).toList()));
    }
    @GetMapping("/{attemptId}")
    public ResponseEntity<Selected> selected(@PathVariable String attemptId, @RequestParam MultiValueMap<String, String> parameters) {
        rejectParameters(parameters);
        var result = query.find(attemptId);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Selected(metadata(result.observedAt()), row(result.attempts().getFirst())));
    }
    private static void rejectParameters(MultiValueMap<String, String> parameters) {
        if (!parameters.isEmpty()) throw new CpiAttemptQueryService.InvalidQuery();
    }
    private static Metadata metadata(Instant observed) {
        return new Metadata("1.0.0", "UNVERIFIED", "PERSISTED_ATTEMPT_RECORDS", "Asia/Seoul", kst(observed), LIMITATIONS);
    }
    private static Attempt row(CpiCollectionAttempt value) {
        var terminal = value.result();
        return new Attempt(value.attemptId().toString(), value.trigger().name(), kst(value.startedAt()), value.permitted(),
                terminal == null ? "UNKNOWN" : terminal.status().name(), terminal == null ? null : new Terminal(
                        kst(terminal.completedAt()), terminal.captureId() == null ? null : terminal.captureId().toString(),
                        kst(terminal.capturedAt()), terminal.failure() == null ? null : terminal.failure().name(), kst(terminal.retryNotBefore())));
    }
    static String kst(Instant at) {
        return at == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(at.atZone(ZoneId.of("Asia/Seoul")));
    }
    public record Metadata(String schemaVersion, String dataMode, String evidenceMode, String timezone,
                           String observedAtKst, List<String> limitations) {}
    public record Recent(Metadata metadata, int limit, boolean hasMore, String order, List<Attempt> attempts) {}
    public record Selected(Metadata metadata, Attempt attempt) {}
    public record Attempt(String attemptId, String trigger, String startedAtKst, boolean gatePermitted,
                          String status, Terminal terminal) {}
    public record Terminal(String completedAtKst, String captureId, String capturedAtKst, String failureCode, String retryNotBeforeKst) {}
}
