package com.wallstreetreceipts.api.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import com.wallstreetreceipts.api.application.port.out.ScoringReceiptRepository;

@Repository
public class JdbcScoringReceiptRepository implements ScoringReceiptRepository {
    private final JdbcTemplate jdbc;
    public JdbcScoringReceiptRepository(javax.sql.DataSource source) {
        jdbc = new JdbcTemplate(source); jdbc.setQueryTimeout(3);
    }
    @Override public boolean lockCall(String callId) {
        return !jdbc.query("SELECT call_id FROM analyst_calls WHERE call_id = ? FOR UPDATE", (r, n) -> r.getString(1), callId).isEmpty();
    }
    @Override public void insert(Entry e) {
        jdbc.update("""
                INSERT INTO demo_scoring_receipts(receipt_id,call_id,snapshot_id,basis_revision_id,basis_revision_sequence,basis_revision_type,
                    methodology_id,methodology_version,methodology_definition_hash,methodology_definition,input_fingerprint,ledger_fingerprint,
                    evaluation_as_of,recorded_at,input_bytes)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, e.receiptId().toString(), e.callId(), e.snapshotId(), e.basisRevisionId(), e.basisRevisionSequence(),
                e.basisRevisionId() == null ? null : "CORRECTION", e.methodologyId(), e.methodologyVersion(), e.methodologyDefinitionHash(),
                e.methodologyDefinition(), e.inputFingerprint(), e.ledgerFingerprint(), Timestamp.from(e.evaluationAsOf()), Timestamp.from(e.recordedAt()), e.inputBytes());
    }
    @Override public Optional<Entry> findByIdentity(String input, String ledger) {
        return jdbc.query("SELECT * FROM demo_scoring_receipts WHERE input_fingerprint=? AND ledger_fingerprint=?", this::row, input, ledger).stream().findFirst();
    }
    @Override public Optional<Entry> find(String call, UUID id) {
        return jdbc.query("SELECT * FROM demo_scoring_receipts WHERE call_id=? AND receipt_id=?", this::row, call, id.toString()).stream().findFirst();
    }
    @Override public List<Entry> recent(String call) {
        return jdbc.query("SELECT * FROM demo_scoring_receipts WHERE call_id=? ORDER BY recorded_at DESC, receipt_id DESC LIMIT 21", this::row, call);
    }
    private Entry row(ResultSet r, int n) throws SQLException {
        if (!"DEMO".equals(r.getString("data_mode")) || !"PARTIAL_ENDPOINT".equals(r.getString("receipt_scope")) || r.getBoolean("data_complete")) {
            throw new SQLException("Invalid partial scoring scope");
        }
        return new Entry(UUID.fromString(r.getString("receipt_id")), r.getString("call_id"), r.getString("snapshot_id"),
                r.getString("basis_revision_id"), r.getObject("basis_revision_sequence", Integer.class),
                r.getString("methodology_id"), r.getString("methodology_version"), r.getString("methodology_definition_hash"),
                r.getString("methodology_definition"), r.getString("input_fingerprint"), r.getString("ledger_fingerprint"),
                r.getTimestamp("evaluation_as_of").toInstant(), r.getTimestamp("recorded_at").toInstant(), r.getBytes("input_bytes"));
    }
}
