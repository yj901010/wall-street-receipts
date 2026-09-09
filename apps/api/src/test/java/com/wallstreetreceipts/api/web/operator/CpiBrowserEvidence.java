package com.wallstreetreceipts.api.web.operator;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import com.wallstreetreceipts.api.infrastructure.persistence.JdbcCpiRepository;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;
import com.wallstreetreceipts.api.support.CpiTestFixture;

/** Test-only DEMO state, installed by the disposable database owner, never by the API reader. */
final class CpiBrowserEvidence {
    static void seed(JdbcTemplate owner) {
        var parser = new BlsCpiParser();
        var repository = new JdbcCpiRepository(owner, parser);
        for (int i = 0; i < 24; i++) {
            var id = new UUID(0, i + 1);
            var at = Instant.parse("2026-09-08T14:59:59.123456Z").minusSeconds(i / 2);
            owner.update("INSERT INTO bls_cpi_collection_attempts (attempt_id, trigger_kind, started_at, permitted) VALUES (?, ?, ?, ?)",
                    id.toString(), i % 2 == 0 ? "MANUAL" : "SCHEDULED", Timestamp.from(at), i != 4);
            if (i == 0) {
                var receipt = parser.parse(CpiTestFixture.bytes(), new UUID(0, 1000), at, 2023, 2026);
                repository.append(receipt, CpiTestFixture.bytes(), 2023, 2026);
                owner.update("INSERT INTO bls_cpi_collection_results (attempt_id, started_at, permitted, completed_at, status, capture_id, captured_at) VALUES (?, ?, TRUE, ?, 'SAVED', ?, ?)",
                        id.toString(), Timestamp.from(at), Timestamp.from(at), receipt.captureId().toString(), Timestamp.from(at));
            } else if (i >= 2 && i <= 5) {
                owner.update("INSERT INTO bls_cpi_collection_results (attempt_id, started_at, permitted, completed_at, status, failure_code, retry_not_before) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        id.toString(), Timestamp.from(at), i != 4, Timestamp.from(at),
                        i == 2 ? "RATE_LIMITED" : i == 4 ? "SKIPPED" : "FAILED", i == 3 ? "FETCH" : i == 5 ? "PARSE" : null,
                        i == 2 ? Timestamp.from(Instant.parse("2026-09-09T15:00:00Z")) : null);
            }
        }
    }
    static List<String> inventory(JdbcTemplate owner) {
        // Complete deterministic row snapshots, including raw receipt and mutable gate; not just counts.
        return List.of(
                owner.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY attempt_id), '[]'::jsonb)::text FROM bls_cpi_collection_attempts t", String.class),
                owner.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY attempt_id), '[]'::jsonb)::text FROM bls_cpi_collection_results t", String.class),
                owner.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY capture_id), '[]'::jsonb)::text FROM bls_cpi_captures t", String.class),
                owner.queryForObject("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY singleton_id), '[]'::jsonb)::text FROM bls_cpi_collection_gate t", String.class));
    }
    private CpiBrowserEvidence() {}
}
