-- Additive partial DEMO audit storage; does not activate scoring_methodologies or write call_outcomes.
ALTER TABLE market_snapshots ADD CONSTRAINT uq_scoring_snapshot_call UNIQUE (call_id, snapshot_id);

CREATE TABLE demo_scoring_receipts (
    receipt_id VARCHAR(36) PRIMARY KEY,
    call_id VARCHAR(100) NOT NULL REFERENCES analyst_calls(call_id) ON DELETE RESTRICT,
    snapshot_id VARCHAR(100) NOT NULL,
    basis_revision_id VARCHAR(128),
    basis_revision_sequence INTEGER,
    basis_revision_type VARCHAR(32),
    methodology_id VARCHAR(128) NOT NULL,
    methodology_version VARCHAR(64) NOT NULL,
    methodology_definition_hash CHAR(64) NOT NULL,
    methodology_definition TEXT NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    ledger_fingerprint CHAR(64) NOT NULL,
    evaluation_as_of TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    recorded_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    input_bytes BYTEA NOT NULL,
    data_mode VARCHAR(16) NOT NULL DEFAULT 'DEMO' CHECK (data_mode = 'DEMO'),
    receipt_scope VARCHAR(32) NOT NULL DEFAULT 'PARTIAL_ENDPOINT' CHECK (receipt_scope = 'PARTIAL_ENDPOINT'),
    data_complete BOOLEAN NOT NULL DEFAULT FALSE CHECK (data_complete = FALSE),
    CONSTRAINT fk_scoring_snapshot FOREIGN KEY (call_id, snapshot_id)
        REFERENCES market_snapshots(call_id, snapshot_id) ON DELETE RESTRICT,
    CONSTRAINT fk_scoring_correction FOREIGN KEY (call_id, basis_revision_id, basis_revision_sequence, basis_revision_type)
        REFERENCES analyst_call_revisions(call_id, revision_id, sequence_number, revision_type) ON DELETE RESTRICT,
    CONSTRAINT ck_scoring_basis CHECK (
        (basis_revision_id IS NULL AND basis_revision_sequence IS NULL AND basis_revision_type IS NULL)
        OR (basis_revision_id IS NOT NULL AND basis_revision_sequence IS NOT NULL AND basis_revision_sequence > 0
            AND basis_revision_type IS NOT NULL AND basis_revision_type = 'CORRECTION')),
    CONSTRAINT ck_scoring_time CHECK (recorded_at >= evaluation_as_of),
    CONSTRAINT ck_scoring_bytes CHECK (OCTET_LENGTH(input_bytes) BETWEEN 1 AND 1048576),
    CONSTRAINT uq_scoring_input_ledger UNIQUE (input_fingerprint, ledger_fingerprint)
);
CREATE INDEX idx_scoring_call_recorded ON demo_scoring_receipts(call_id, recorded_at DESC, receipt_id DESC);
