CREATE TABLE scoring_methodologies (
    methodology_id VARCHAR(128) NOT NULL,
    methodology_version VARCHAR(64) NOT NULL,
    schema_version VARCHAR(32) NOT NULL,
    definition_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (methodology_id, methodology_version),
    CONSTRAINT uq_scoring_methodology_identity
        UNIQUE (methodology_id, methodology_version, definition_hash),
    CONSTRAINT ck_scoring_methodology_schema CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_scoring_methodology_version CHECK (
        REGEXP_LIKE(methodology_version, '^[A-Za-z0-9][A-Za-z0-9._+-]{0,63}$')
    ),
    CONSTRAINT ck_scoring_methodology_hash CHECK (
        REGEXP_LIKE(definition_hash, '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_scoring_methodology_status
        CHECK (status IN ('MODEL_ONLY', 'ACTIVE', 'RETIRED')),
    CONSTRAINT ck_scoring_methodology_time CHECK (captured_at >= effective_at),
    CONSTRAINT ck_scoring_methodology_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

ALTER TABLE analyst_calls
    ALTER COLUMN call_id SET DATA TYPE VARCHAR(128);

ALTER TABLE market_snapshots
    ALTER COLUMN snapshot_id SET DATA TYPE VARCHAR(128);

ALTER TABLE market_snapshots
    ALTER COLUMN call_id SET DATA TYPE VARCHAR(128);

ALTER TABLE analyst_call_revisions
    ALTER COLUMN call_id SET DATA TYPE VARCHAR(128);

ALTER TABLE market_snapshots
    ADD CONSTRAINT uq_market_snapshots_call_snapshot UNIQUE (call_id, snapshot_id);

ALTER TABLE analyst_calls
    ADD CONSTRAINT ck_analyst_calls_capture_time CHECK (captured_at >= processing_time);

ALTER TABLE market_snapshots
    ADD CONSTRAINT ck_market_snapshots_capture_time CHECK (captured_at >= processing_time);

CREATE TABLE call_outcomes (
    outcome_id VARCHAR(128) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    call_id VARCHAR(128) NOT NULL REFERENCES analyst_calls(call_id) ON DELETE RESTRICT,
    horizon VARCHAR(8) NOT NULL,
    basis_revision_id VARCHAR(128),
    basis_key VARCHAR(264) NOT NULL,
    basis_revision_sequence_number INTEGER,
    basis_revision_type VARCHAR(32),
    cancellation_revision_id VARCHAR(128),
    cancellation_revision_sequence_number INTEGER,
    cancellation_revision_type VARCHAR(32),
    snapshot_id VARCHAR(128),
    methodology_id VARCHAR(128) NOT NULL,
    methodology_version VARCHAR(64) NOT NULL,
    methodology_definition_hash CHAR(64) NOT NULL,
    input_fingerprint CHAR(64) NOT NULL,
    sequence_number INTEGER NOT NULL,
    supersedes_outcome_id VARCHAR(128),
    supersedes_sequence_number INTEGER,
    evaluation_status VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64),
    event_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    asset_return NUMERIC(38, 12),
    benchmark_return NUMERIC(38, 12),
    sector_return NUMERIC(38, 12),
    alpha NUMERIC(38, 12),
    sector_alpha NUMERIC(38, 12),
    mfe NUMERIC(38, 12),
    mae NUMERIC(38, 12),
    target_hit BOOLEAN,
    directional_win BOOLEAN,
    target_error NUMERIC(38, 12),
    data_complete BOOLEAN NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_call_outcomes_snapshot
        FOREIGN KEY (call_id, snapshot_id)
        REFERENCES market_snapshots(call_id, snapshot_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_call_outcomes_methodology
        FOREIGN KEY (methodology_id, methodology_version, methodology_definition_hash)
        REFERENCES scoring_methodologies(methodology_id, methodology_version, definition_hash)
        ON DELETE RESTRICT,
    CONSTRAINT fk_call_outcomes_basis_revision
        FOREIGN KEY (
            call_id,
            basis_revision_id,
            basis_revision_sequence_number,
            basis_revision_type
        )
        REFERENCES analyst_call_revisions(call_id, revision_id, sequence_number, revision_type)
        ON DELETE RESTRICT,
    CONSTRAINT fk_call_outcomes_cancellation_revision
        FOREIGN KEY (
            call_id,
            cancellation_revision_id,
            cancellation_revision_sequence_number,
            cancellation_revision_type
        )
        REFERENCES analyst_call_revisions(call_id, revision_id, sequence_number, revision_type)
        ON DELETE RESTRICT,
    CONSTRAINT uq_call_outcomes_natural_identity UNIQUE (
        call_id,
        basis_key,
        horizon,
        methodology_id,
        methodology_version,
        methodology_definition_hash,
        input_fingerprint
    ),
    CONSTRAINT uq_call_outcomes_lineage_sequence UNIQUE (
        call_id,
        basis_key,
        horizon,
        methodology_id,
        methodology_version,
        methodology_definition_hash,
        sequence_number
    ),
    CONSTRAINT uq_call_outcomes_parent_identity UNIQUE (
        call_id,
        basis_key,
        horizon,
        methodology_id,
        methodology_version,
        methodology_definition_hash,
        outcome_id,
        sequence_number
    ),
    CONSTRAINT fk_call_outcomes_supersedes
        FOREIGN KEY (
            call_id,
            basis_key,
            horizon,
            methodology_id,
            methodology_version,
            methodology_definition_hash,
            supersedes_outcome_id,
            supersedes_sequence_number
        )
        REFERENCES call_outcomes(
            call_id,
            basis_key,
            horizon,
            methodology_id,
            methodology_version,
            methodology_definition_hash,
            outcome_id,
            sequence_number
        )
        ON DELETE RESTRICT,
    CONSTRAINT uq_call_outcomes_supersedes UNIQUE (supersedes_outcome_id),
    CONSTRAINT ck_call_outcomes_schema CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_call_outcomes_horizon CHECK (horizon IN ('D1', 'W1', 'M1', 'M3', 'M6', 'Y1')),
    CONSTRAINT ck_call_outcomes_basis CHECK (
        (
            basis_revision_id IS NULL
            AND basis_revision_sequence_number IS NULL
            AND basis_revision_type IS NULL
            AND basis_key = 'ORIGINAL:' || call_id
        )
        OR (
            basis_revision_id IS NOT NULL
            AND basis_revision_sequence_number IS NOT NULL
            AND basis_revision_type = 'CORRECTION'
            AND basis_key = 'REVISION:' || basis_revision_id
        )
    ),
    CONSTRAINT ck_call_outcomes_hashes CHECK (
        REGEXP_LIKE(methodology_definition_hash, '^[0-9a-f]{64}$')
        AND REGEXP_LIKE(input_fingerprint, '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_call_outcomes_sequence CHECK (
        (
            sequence_number = 1
            AND supersedes_outcome_id IS NULL
            AND supersedes_sequence_number IS NULL
        )
        OR (
            sequence_number > 1
            AND supersedes_outcome_id IS NOT NULL
            AND supersedes_sequence_number = sequence_number - 1
        )
    ),
    CONSTRAINT ck_call_outcomes_evaluation CHECK (
        (
            evaluation_status = 'CALCULATED'
            AND reason_code IS NULL
            AND data_complete = TRUE
        )
        OR (
            evaluation_status = 'PENDING'
            AND reason_code = 'HORIZON_NOT_REACHED'
            AND data_complete = FALSE
        )
        OR (
            evaluation_status = 'INCOMPLETE'
            AND reason_code = 'HORIZON_DATA_MISSING'
            AND data_complete = FALSE
        )
        OR (
            evaluation_status = 'EXCLUDED'
            AND reason_code = 'CALL_CANCELLED'
            AND data_complete = FALSE
        )
    ),
    CONSTRAINT ck_call_outcomes_cancellation_evidence CHECK (
        (
            evaluation_status = 'EXCLUDED'
            AND cancellation_revision_id IS NOT NULL
            AND cancellation_revision_sequence_number IS NOT NULL
            AND cancellation_revision_type = 'CANCELLATION'
        )
        OR (
            evaluation_status <> 'EXCLUDED'
            AND cancellation_revision_id IS NULL
            AND cancellation_revision_sequence_number IS NULL
            AND cancellation_revision_type IS NULL
        )
    ),
    CONSTRAINT ck_call_outcomes_excluded_metrics CHECK (
        evaluation_status <> 'EXCLUDED'
        OR (
            asset_return IS NULL
            AND benchmark_return IS NULL
            AND sector_return IS NULL
            AND alpha IS NULL
            AND sector_alpha IS NULL
            AND mfe IS NULL
            AND mae IS NULL
            AND target_hit IS NULL
            AND directional_win IS NULL
            AND target_error IS NULL
        )
    ),
    CONSTRAINT ck_call_outcomes_target_error CHECK (target_error IS NULL OR target_error >= 0),
    CONSTRAINT ck_call_outcomes_time CHECK (processing_time >= event_time),
    CONSTRAINT ck_call_outcomes_capture_time CHECK (captured_at >= processing_time),
    CONSTRAINT ck_call_outcomes_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE INDEX idx_call_outcomes_call_order ON call_outcomes(
    call_id,
    methodology_id,
    methodology_version,
    sequence_number
);
CREATE INDEX idx_call_outcomes_event_time ON call_outcomes(event_time);
