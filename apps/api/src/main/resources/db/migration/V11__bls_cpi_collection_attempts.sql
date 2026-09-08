-- Append-only through the repository. No invented history for pre-V11 captures.
CREATE TABLE bls_cpi_collection_attempts (
    attempt_id VARCHAR(36) PRIMARY KEY,
    trigger_kind VARCHAR(16) NOT NULL CHECK (trigger_kind IN ('MANUAL', 'SCHEDULED')),
    started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    permitted BOOLEAN NOT NULL,
    CONSTRAINT ck_bls_cpi_attempt_id CHECK (
        REGEXP_LIKE(attempt_id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
    ),
    CONSTRAINT uq_bls_cpi_attempt_start UNIQUE (attempt_id, started_at, permitted)
);
CREATE INDEX idx_bls_cpi_attempt_start ON bls_cpi_collection_attempts (started_at DESC, attempt_id DESC);

ALTER TABLE bls_cpi_captures ADD CONSTRAINT uq_bls_cpi_capture_time UNIQUE (capture_id, captured_at);

CREATE TABLE bls_cpi_collection_results (
    attempt_id VARCHAR(36) PRIMARY KEY,
    started_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    permitted BOOLEAN NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    status VARCHAR(16) NOT NULL,
    capture_id VARCHAR(36) UNIQUE,
    captured_at TIMESTAMP(6) WITH TIME ZONE,
    failure_code VARCHAR(16),
    retry_not_before TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT fk_bls_cpi_result_start FOREIGN KEY (attempt_id, started_at, permitted)
        REFERENCES bls_cpi_collection_attempts (attempt_id, started_at, permitted) ON DELETE RESTRICT,
    CONSTRAINT fk_bls_cpi_result_capture FOREIGN KEY (capture_id, captured_at)
        REFERENCES bls_cpi_captures (capture_id, captured_at) ON DELETE RESTRICT,
    CONSTRAINT ck_bls_cpi_result_time CHECK (completed_at >= started_at),
    CONSTRAINT ck_bls_cpi_result_shape CHECK (
        (status = 'SAVED' AND permitted = TRUE AND capture_id IS NOT NULL AND captured_at IS NOT NULL
            AND captured_at >= started_at AND captured_at <= completed_at
            AND failure_code IS NULL AND retry_not_before IS NULL)
        OR (status = 'SKIPPED' AND permitted = FALSE AND capture_id IS NULL AND captured_at IS NULL
            AND failure_code IS NULL AND retry_not_before IS NULL)
        OR (status = 'FAILED' AND permitted = TRUE AND capture_id IS NULL AND captured_at IS NULL
            AND failure_code IS NOT NULL AND failure_code IN ('FETCH', 'PARSE') AND retry_not_before IS NULL)
        OR (status = 'RATE_LIMITED' AND permitted = TRUE AND capture_id IS NULL AND captured_at IS NULL
            AND failure_code IS NULL AND retry_not_before IS NOT NULL AND retry_not_before >= started_at)
    )
);
