CREATE TABLE provider_event_identities (
    provider VARCHAR(100) NOT NULL,
    provider_event_id VARCHAR(256) NOT NULL,
    event_kind VARCHAR(32) NOT NULL,
    canonical_event_id VARCHAR(128) NOT NULL,
    PRIMARY KEY (provider, provider_event_id),
    CONSTRAINT uq_provider_event_identity_target
        UNIQUE (provider, provider_event_id, event_kind, canonical_event_id),
    CONSTRAINT ck_provider_event_identity_kind
        CHECK (event_kind IN ('ANALYST_CALL', 'ANALYST_CALL_REVISION'))
);

INSERT INTO provider_event_identities (
    provider, provider_event_id, event_kind, canonical_event_id
)
SELECT provider, provider_event_id, 'ANALYST_CALL', call_id
FROM analyst_calls;

ALTER TABLE analyst_calls
    ADD COLUMN provider_event_kind VARCHAR(32) NOT NULL DEFAULT 'ANALYST_CALL';

ALTER TABLE analyst_calls
    ADD CONSTRAINT ck_analyst_calls_provider_event_kind
        CHECK (provider_event_kind = 'ANALYST_CALL');

ALTER TABLE analyst_calls
    ADD CONSTRAINT fk_analyst_calls_provider_event_identity
        FOREIGN KEY (provider, provider_event_id, provider_event_kind, call_id)
        REFERENCES provider_event_identities (
            provider, provider_event_id, event_kind, canonical_event_id
        )
        ON DELETE RESTRICT;

CREATE TABLE analyst_call_revisions (
    revision_id VARCHAR(128) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    call_id VARCHAR(100) NOT NULL REFERENCES analyst_calls(call_id) ON DELETE RESTRICT,
    supersedes_revision_id VARCHAR(128),
    supersedes_sequence_number INTEGER,
    supersedes_revision_type VARCHAR(32),
    sequence_number INTEGER NOT NULL,
    provider VARCHAR(100) NOT NULL,
    provider_event_id VARCHAR(256) NOT NULL,
    provider_event_kind VARCHAR(32) NOT NULL DEFAULT 'ANALYST_CALL_REVISION',
    revision_type VARCHAR(32) NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    processing_time TIMESTAMP WITH TIME ZONE NOT NULL,
    corrected_direction VARCHAR(32),
    corrected_original_rating VARCHAR(200),
    corrected_previous_target NUMERIC(38, 12),
    corrected_target NUMERIC(38, 12),
    corrected_currency CHAR(3),
    corrected_target_date DATE,
    reason VARCHAR(2000) NOT NULL,
    source_reference_id VARCHAR(100) NOT NULL REFERENCES source_references(source_reference_id),
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(128) NOT NULL,
    CONSTRAINT uq_call_revisions_parent_identity
        UNIQUE (call_id, revision_id, sequence_number, revision_type),
    CONSTRAINT fk_call_revisions_supersedes
        FOREIGN KEY (
            call_id,
            supersedes_revision_id,
            supersedes_sequence_number,
            supersedes_revision_type
        )
        REFERENCES analyst_call_revisions(call_id, revision_id, sequence_number, revision_type)
        ON DELETE RESTRICT,
    CONSTRAINT uq_call_revisions_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT uq_call_revisions_sequence UNIQUE (call_id, sequence_number),
    CONSTRAINT uq_call_revisions_supersedes UNIQUE (supersedes_revision_id),
    CONSTRAINT ck_call_revisions_provider_event_kind
        CHECK (provider_event_kind = 'ANALYST_CALL_REVISION'),
    CONSTRAINT fk_call_revisions_provider_event_identity
        FOREIGN KEY (provider, provider_event_id, provider_event_kind, revision_id)
        REFERENCES provider_event_identities (
            provider, provider_event_id, event_kind, canonical_event_id
        )
        ON DELETE RESTRICT,
    CONSTRAINT ck_call_revisions_schema_version CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_call_revisions_sequence CHECK (sequence_number > 0),
    CONSTRAINT ck_call_revisions_lineage_root CHECK (
        (
            sequence_number = 1
            AND supersedes_revision_id IS NULL
            AND supersedes_sequence_number IS NULL
            AND supersedes_revision_type IS NULL
        )
        OR (
            sequence_number > 1
            AND supersedes_revision_id IS NOT NULL
            AND supersedes_sequence_number IS NOT NULL
            AND supersedes_revision_type IS NOT NULL
            AND supersedes_sequence_number = sequence_number - 1
            AND supersedes_revision_type = 'CORRECTION'
        )
    ),
    CONSTRAINT ck_call_revisions_time CHECK (processing_time >= event_time),
    CONSTRAINT ck_call_revisions_capture_time CHECK (captured_at >= processing_time),
    CONSTRAINT ck_call_revisions_type CHECK (revision_type IN ('CORRECTION', 'CANCELLATION')),
    CONSTRAINT ck_call_revisions_direction CHECK (
        corrected_direction IS NULL
        OR corrected_direction IN ('STRONG_BULLISH', 'BULLISH', 'NEUTRAL', 'BEARISH', 'STRONG_BEARISH')
    ),
    CONSTRAINT ck_call_revisions_previous_target CHECK (
        corrected_previous_target IS NULL OR corrected_previous_target > 0
    ),
    CONSTRAINT ck_call_revisions_target CHECK (corrected_target IS NULL OR corrected_target > 0),
    CONSTRAINT ck_call_revisions_currency CHECK (
        (
            corrected_previous_target IS NULL
            AND corrected_target IS NULL
            AND (
                corrected_currency IS NULL
                OR REGEXP_LIKE(corrected_currency, '^[A-Z]{3}$')
            )
        )
        OR (
            corrected_currency IS NOT NULL
            AND REGEXP_LIKE(corrected_currency, '^[A-Z]{3}$')
        )
    ),
    CONSTRAINT ck_call_revisions_payload CHECK (
        (revision_type = 'CORRECTION' AND corrected_direction IS NOT NULL)
        OR (
            revision_type = 'CANCELLATION'
            AND corrected_direction IS NULL
            AND corrected_original_rating IS NULL
            AND corrected_previous_target IS NULL
            AND corrected_target IS NULL
            AND corrected_currency IS NULL
            AND corrected_target_date IS NULL
        )
    ),
    CONSTRAINT ck_call_revisions_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE INDEX idx_call_revisions_call_sequence
    ON analyst_call_revisions(call_id, sequence_number);
CREATE INDEX idx_call_revisions_event_time
    ON analyst_call_revisions(event_time);
