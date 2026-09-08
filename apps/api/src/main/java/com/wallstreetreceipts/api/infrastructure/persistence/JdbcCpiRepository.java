package com.wallstreetreceipts.api.infrastructure.persistence;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.wallstreetreceipts.api.application.cpi.CpiRepository;
import com.wallstreetreceipts.api.domain.cpi.CpiSnapshot;
import com.wallstreetreceipts.api.infrastructure.provider.bls.BlsCpiParser;

@Repository
public class JdbcCpiRepository implements CpiRepository {
    private final JdbcTemplate jdbc;
    private final BlsCpiParser parser;
    public JdbcCpiRepository(JdbcTemplate jdbc, BlsCpiParser parser) { this.jdbc = jdbc; this.parser = parser; }
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
