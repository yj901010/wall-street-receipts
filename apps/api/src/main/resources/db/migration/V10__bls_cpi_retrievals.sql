CREATE TABLE bls_cpi_captures (
    capture_id VARCHAR(36) PRIMARY KEY,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    response_sha256 CHAR(64) NOT NULL,
    start_year INTEGER NOT NULL,
    end_year INTEGER NOT NULL,
    response_json TEXT NOT NULL,
    CONSTRAINT ck_bls_cpi_receipt CHECK (
        REGEXP_LIKE(capture_id, '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$')
        AND REGEXP_LIKE(response_sha256, '^[0-9a-f]{64}$')
        AND start_year >= 1900 AND end_year - start_year = 3
        AND OCTET_LENGTH(response_json) BETWEEN 1 AND 1048576
    )
);
CREATE INDEX idx_bls_cpi_latest ON bls_cpi_captures (captured_at DESC, capture_id DESC);

-- Shared across collector restarts and processes using this database.
CREATE TABLE bls_cpi_collection_gate (
    singleton_id INTEGER PRIMARY KEY CHECK (singleton_id = 1),
    next_allowed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL
);
INSERT INTO bls_cpi_collection_gate (singleton_id, next_allowed_at)
VALUES (1, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00');
