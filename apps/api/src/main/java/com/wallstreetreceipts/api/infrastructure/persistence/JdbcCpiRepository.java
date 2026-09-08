package com.wallstreetreceipts.api.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.Objects;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt;
import com.wallstreetreceipts.api.domain.cpi.CpiCollectionAttempt.*;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;

@Repository
public class JdbcCpiRepository implements CpiRepository {
    private final JdbcTemplate jdbc;
    private final BlsCpiParser parser;
    private final TransactionTemplate attempts;
    public JdbcCpiRepository(JdbcTemplate jdbc, BlsCpiParser parser) {
        this.jdbc = jdbc;
        this.parser = parser;
        this.attempts = new TransactionTemplate(new DataSourceTransactionManager(Objects.requireNonNull(jdbc.getDataSource())));
        // Start/gate and terminal evidence survive any caller transaction rollback.
        // No transaction spans the HTTP request; each write is a separate bounded unit.
        attempts.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        attempts.setTimeout(10);
    }
    @Override public boolean beginAttempt(UUID id, Trigger trigger, Instant startedAt) {
        new CpiCollectionAttempt(id, trigger, startedAt, false, null);
        return Boolean.TRUE.equals(attempts.execute(transaction -> {
            lockGate();
            boolean permitted = claimCollection(startedAt);
            jdbc.update("INSERT INTO bls_cpi_collection_attempts (attempt_id, trigger_kind, started_at, permitted) VALUES (?, ?, ?, ?)",
                    id.toString(), trigger.name(), Timestamp.from(startedAt), permitted);
            return permitted;
        }));
    }
    @Override public void finishAttempt(UUID id, Result result) {
        Objects.requireNonNull(result);
        if (result.status() == Status.SAVED) throw new IllegalArgumentException("SAVED requires atomic receipt persistence");
        attempts.executeWithoutResult(transaction -> {
            if (result.status() == Status.RATE_LIMITED) lockGate();
            insertResult(id, result);
            if (result.status() == Status.RATE_LIMITED) postponeCollection(result.retryNotBefore());
        });
    }
    private void lockGate() {
        // Missing gate is broken persistence, not evidence of a cooldown skip.
        if (jdbc.queryForList("SELECT singleton_id FROM bls_cpi_collection_gate WHERE singleton_id = 1 FOR UPDATE").size() != 1) {
            throw new IllegalStateException("CPI collection gate unavailable");
        }
    }
    @Override public void saveAttempt(UUID id, CpiSnapshot snapshot, byte[] raw, int start, int end, Instant completedAt) {
        var result = new Result(completedAt, Status.SAVED, snapshot.captureId(), snapshot.capturedAt(), null, null);
        attempts.executeWithoutResult(transaction -> {
            var attempt = findAttempt(id).orElseThrow(() -> new IllegalArgumentException("CPI attempt not found"));
            int requestedYear = attempt.startedAt().atZone(java.time.ZoneOffset.UTC).getYear();
            if (start != requestedYear - 3 || end != requestedYear) throw new IllegalArgumentException("CPI attempt request years differ");
            append(snapshot, raw, start, end);
            insertResult(id, result);
        });
    }
    private void insertResult(UUID id, Result result) {
        var attempt = findAttempt(id).orElseThrow(() -> new IllegalArgumentException("CPI attempt not found"));
        if (attempt.result() != null) throw new IllegalStateException("CPI attempt already completed");
        new CpiCollectionAttempt(id, attempt.trigger(), attempt.startedAt(), attempt.permitted(), result);
        jdbc.update("INSERT INTO bls_cpi_collection_results (attempt_id, started_at, permitted, completed_at, status, capture_id, captured_at, failure_code, retry_not_before) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id.toString(), Timestamp.from(attempt.startedAt()), attempt.permitted(), Timestamp.from(result.completedAt()),
                result.status().name(), result.captureId() == null ? null : result.captureId().toString(),
                timestamp(result.capturedAt()), result.failure() == null ? null : result.failure().name(), timestamp(result.retryNotBefore()));
    }
    @Override public Optional<CpiCollectionAttempt> findAttempt(UUID id) {
        return jdbc.query(ATTEMPT_SELECT + " WHERE a.attempt_id = ?", ATTEMPT_ROW, id.toString()).stream().findFirst();
    }
    @Override public List<CpiCollectionAttempt> recentAttempts() {
        // One statement snapshot, fixed bound, no count(*) or raw receipt query.
        return jdbc.query(ATTEMPT_SELECT + " ORDER BY a.started_at DESC, a.attempt_id DESC LIMIT 21", ATTEMPT_ROW);
    }
    private static final String ATTEMPT_SELECT = "SELECT a.attempt_id, a.trigger_kind, a.started_at, a.permitted, r.completed_at, r.status, r.capture_id, r.captured_at, r.failure_code, r.retry_not_before FROM bls_cpi_collection_attempts a LEFT JOIN bls_cpi_collection_results r ON a.attempt_id = r.attempt_id";
    private static final RowMapper<CpiCollectionAttempt> ATTEMPT_ROW =
                (rs, row) -> new CpiCollectionAttempt(UUID.fromString(rs.getString("attempt_id")),
                        Trigger.valueOf(rs.getString("trigger_kind")), rs.getTimestamp("started_at").toInstant(), rs.getBoolean("permitted"),
                        rs.getString("status") == null ? null : new Result(rs.getTimestamp("completed_at").toInstant(),
                                Status.valueOf(rs.getString("status")), rs.getString("capture_id") == null ? null : UUID.fromString(rs.getString("capture_id")),
                                instant(rs.getTimestamp("captured_at")), rs.getString("failure_code") == null ? null : Failure.valueOf(rs.getString("failure_code")),
                                instant(rs.getTimestamp("retry_not_before"))));
    private static Timestamp timestamp(Instant value) { return value == null ? null : Timestamp.from(value); }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    @Override public boolean claimCollection(Instant now) {
        // Autocommit before HTTP: a failed collection cannot roll back its rate gate.
        return jdbc.update("UPDATE bls_cpi_collection_gate SET next_allowed_at = ? WHERE singleton_id = 1 AND next_allowed_at <= ?",
                Timestamp.from(now.plus(Duration.ofMinutes(15))), Timestamp.from(now)) == 1;
    }
    @Override public void postponeCollection(Instant until) {
        jdbc.update("UPDATE bls_cpi_collection_gate SET next_allowed_at = ? WHERE singleton_id = 1 AND next_allowed_at < ?",
                Timestamp.from(until), Timestamp.from(until));
    }
    @Override public void append(CpiSnapshot snapshot, byte[] raw, int start, int end) {
        CpiSnapshot replay = parser.parse(raw, snapshot.captureId(), snapshot.capturedAt(), start, end);
        if (!replay.equals(snapshot)) throw new IllegalArgumentException("CPI receipt replay mismatch");
        jdbc.update("INSERT INTO bls_cpi_captures (capture_id, captured_at, response_sha256, start_year, end_year, response_json) VALUES (?, ?, ?, ?, ?, ?)",
                snapshot.captureId().toString(), Timestamp.from(snapshot.capturedAt()), snapshot.responseSha256(), start, end,
                new String(raw, StandardCharsets.UTF_8));
    }
    @Override public Optional<CpiSnapshot> latest(Instant at) {
        return jdbc.query("SELECT capture_id, captured_at, response_sha256, start_year, end_year, response_json FROM bls_cpi_captures WHERE captured_at <= ? ORDER BY captured_at DESC, capture_id DESC LIMIT 1",
                (rs, row) -> {
                    var capture = parser.parse(rs.getString("response_json").getBytes(StandardCharsets.UTF_8),
                            UUID.fromString(rs.getString("capture_id")), rs.getTimestamp("captured_at").toInstant(),
                            rs.getInt("start_year"), rs.getInt("end_year"));
                    if (!capture.responseSha256().equals(rs.getString("response_sha256"))) {
                        throw new IllegalStateException("Stored CPI receipt failed integrity verification");
                    }
                    return capture;
                }, Timestamp.from(at)).stream().findFirst();
    }
}
