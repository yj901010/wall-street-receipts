package com.wallstreetreceipts.api.web.cpi;

import java.time.Clock;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;

@RestController
@ConditionalOnProperty(prefix = "app.cpi", name = "enabled", havingValue = "true")
public class CpiController {
    private final CpiRepository repository;
    private final Clock clock;
    public CpiController(CpiRepository repository, Clock clock) { this.repository = repository; this.clock = clock; }
    @GetMapping("/v1/macro/cpi")
    public ResponseEntity<Snapshot> latest(@RequestParam MultiValueMap<String, String> query) {
        if (!query.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CPI latest retrieval accepts no query parameters");
        var now = clock.instant();
        var snapshot = repository.latest(now).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No CPI retrieval stored"));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Snapshot("1.0.0", "OBSERVED_MONTHLY",
                CpiSnapshot.POLICY, snapshot.captureId().toString(), snapshot.capturedAt().toString(), now.toString(),
                snapshot.responseSha256(), "BLS", "https://api.bls.gov/publicAPI/v2/timeseries/data/",
                "NOT_PROVIDED", "RETRIEVAL_VINTAGE_ONLY", "NSA", "1982-84=100", "WSR_CPI_YOY_HALF_UP_1DP_V1",
                snapshot.series().stream().map(series -> new Series(series.id(), series.observations().stream()
                        .map(row -> new Observation(row.month().toString(), row.index() == null ? null : row.index().toPlainString(),
                                CpiSnapshot.yearOverYear(series, row), row.footnotes())).toList())).toList()));
    }
    public record Snapshot(String schemaVersion, String dataMode, String policyVersion, String captureId,
                           String capturedAt, String servedAt, String responseSha256, String source, String sourceUrl,
                           String releaseTimeStatus, String vintageStatus, String seasonality, String unit,
                           String calculationVersion, List<Series> series) {}
    public record Series(String id, List<Observation> observations) {}
    public record Observation(String month, String index, String yearOverYearPct, List<String> footnotes) {}
}
