ALTER TABLE analyst_calls
    ADD CONSTRAINT uq_analyst_calls_context_identity
        UNIQUE (call_id, event_time, data_mode);

ALTER TABLE source_documents
    ALTER COLUMN source_document_id SET DATA TYPE VARCHAR(128);

ALTER TABLE source_references
    ALTER COLUMN source_document_id SET DATA TYPE VARCHAR(128);

ALTER TABLE source_references
    ALTER COLUMN source_reference_id SET DATA TYPE VARCHAR(128);

ALTER TABLE analyst_calls
    ALTER COLUMN source_reference_id SET DATA TYPE VARCHAR(128);

ALTER TABLE analyst_call_revisions
    ALTER COLUMN source_reference_id SET DATA TYPE VARCHAR(128);

ALTER TABLE source_references
    ADD CONSTRAINT uq_source_references_context_identity
        UNIQUE (source_reference_id, data_mode);

CREATE TABLE macro_observations (
    macro_observation_id VARCHAR(128) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    series VARCHAR(32) NOT NULL,
    observation_value NUMERIC(38, 12),
    unit VARCHAR(32) NOT NULL,
    observation_date DATE NOT NULL,
    released_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    vintage_start DATE,
    vintage_end DATE,
    vintage_start_key DATE NOT NULL,
    vintage_end_key DATE NOT NULL,
    source_reference_id VARCHAR(128) NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_macro_observations_source
        FOREIGN KEY (source_reference_id, data_mode)
        REFERENCES source_references(source_reference_id, data_mode)
        ON DELETE RESTRICT,
    CONSTRAINT uq_macro_observations_snapshot_identity UNIQUE (
        macro_observation_id, series, released_at, processing_time, captured_at,
        vintage_start_key, vintage_end_key, data_mode
    ),
    CONSTRAINT ck_macro_observations_schema CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_macro_observations_series CHECK (
        series IN (
            'FED_FUNDS_LOWER', 'FED_FUNDS_UPPER', 'CPI_YOY',
            'CORE_CPI_YOY', 'PPI_YOY', 'UNEMPLOYMENT_RATE'
        )
    ),
    CONSTRAINT ck_macro_observations_unit
        CHECK (unit IN ('PERCENT', 'PERCENTAGE_POINTS', 'INDEX')),
    CONSTRAINT ck_macro_observations_value CHECK (
        observation_value IS NULL OR (
            observation_value > -100000000000000000000000000
            AND observation_value < 100000000000000000000000000
        )
    ),
    CONSTRAINT ck_macro_observations_time CHECK (
        processing_time >= released_at AND captured_at >= processing_time
    ),
    CONSTRAINT ck_macro_observations_vintage CHECK (
        vintage_start_key = COALESCE(vintage_start, DATE '0001-01-01')
        AND vintage_end_key = COALESCE(vintage_end, DATE '9999-12-31')
        AND vintage_start_key <= vintage_end_key
    ),
    CONSTRAINT ck_macro_observations_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE macro_snapshots (
    macro_snapshot_id VARCHAR(128) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    call_id VARCHAR(128) NOT NULL UNIQUE,
    event_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    event_date DATE NOT NULL,
    processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_macro_snapshots_call
        FOREIGN KEY (call_id, event_time, data_mode)
        REFERENCES analyst_calls(call_id, event_time, data_mode)
        ON DELETE RESTRICT,
    CONSTRAINT uq_macro_snapshots_observation_identity UNIQUE (
        macro_snapshot_id, event_time, event_date, processing_time, captured_at, data_mode
    ),
    CONSTRAINT ck_macro_snapshots_schema CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_macro_snapshots_event_date CHECK (
        event_date = CAST(event_time AT TIME ZONE 'UTC' AS DATE)
    ),
    CONSTRAINT ck_macro_snapshots_time CHECK (
        processing_time >= event_time AND captured_at >= processing_time
    ),
    CONSTRAINT ck_macro_snapshots_immutable CHECK (immutable = TRUE),
    CONSTRAINT ck_macro_snapshots_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE macro_snapshot_observations (
    macro_snapshot_id VARCHAR(128) NOT NULL,
    ordinal INTEGER NOT NULL,
    macro_observation_id VARCHAR(128) NOT NULL,
    series VARCHAR(32) NOT NULL,
    snapshot_event_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    snapshot_event_date DATE NOT NULL,
    snapshot_processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    snapshot_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    observation_released_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    observation_processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    observation_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    vintage_start_key DATE NOT NULL,
    vintage_end_key DATE NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    PRIMARY KEY (macro_snapshot_id, ordinal),
    CONSTRAINT uq_macro_snapshot_observation_once
        UNIQUE (macro_snapshot_id, macro_observation_id),
    CONSTRAINT fk_macro_snapshot_observations_snapshot
        FOREIGN KEY (
            macro_snapshot_id, snapshot_event_time, snapshot_event_date,
            snapshot_processing_time, snapshot_captured_at, data_mode
        ) REFERENCES macro_snapshots(
            macro_snapshot_id, event_time, event_date, processing_time, captured_at, data_mode
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_macro_snapshot_observations_observation
        FOREIGN KEY (
            macro_observation_id, series, observation_released_at,
            observation_processing_time, observation_captured_at,
            vintage_start_key, vintage_end_key, data_mode
        ) REFERENCES macro_observations(
            macro_observation_id, series, released_at, processing_time, captured_at,
            vintage_start_key, vintage_end_key, data_mode
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_macro_snapshot_observations_ordinal CHECK (
        (ordinal = 0 AND series = 'FED_FUNDS_LOWER')
        OR (ordinal = 1 AND series = 'FED_FUNDS_UPPER')
        OR (ordinal = 2 AND series = 'CPI_YOY')
        OR (ordinal = 3 AND series = 'CORE_CPI_YOY')
        OR (ordinal = 4 AND series = 'PPI_YOY')
        OR (ordinal = 5 AND series = 'UNEMPLOYMENT_RATE')
    ),
    CONSTRAINT ck_macro_snapshot_observations_point_in_time CHECK (
        observation_released_at <= snapshot_event_time
        AND observation_processing_time <= snapshot_processing_time
        AND observation_captured_at <= snapshot_captured_at
        AND snapshot_event_date >= vintage_start_key
        AND snapshot_event_date <= vintage_end_key
    )
);

CREATE TABLE event_contexts (
    event_context_id VARCHAR(128) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    call_id VARCHAR(128) NOT NULL UNIQUE,
    event_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    earnings_at TIMESTAMP(6) WITH TIME ZONE,
    next_cpi_at TIMESTAMP(6) WITH TIME ZONE,
    next_fomc_at TIMESTAMP(6) WITH TIME ZONE,
    next_nfp_at TIMESTAMP(6) WITH TIME ZONE,
    options_expiration_at TIMESTAMP(6) WITH TIME ZONE,
    source_reference_id VARCHAR(128) NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(128) NOT NULL,
    CONSTRAINT fk_event_contexts_call
        FOREIGN KEY (call_id, event_time, data_mode)
        REFERENCES analyst_calls(call_id, event_time, data_mode)
        ON DELETE RESTRICT,
    CONSTRAINT fk_event_contexts_source
        FOREIGN KEY (source_reference_id, data_mode)
        REFERENCES source_references(source_reference_id, data_mode)
        ON DELETE RESTRICT,
    CONSTRAINT ck_event_contexts_schema CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_event_contexts_time CHECK (
        processing_time >= event_time AND captured_at >= processing_time
    ),
    CONSTRAINT ck_event_contexts_future_events CHECK (
        (next_cpi_at IS NULL OR next_cpi_at >= event_time)
        AND (next_fomc_at IS NULL OR next_fomc_at >= event_time)
        AND (next_nfp_at IS NULL OR next_nfp_at >= event_time)
        AND (options_expiration_at IS NULL OR options_expiration_at >= event_time)
    ),
    CONSTRAINT ck_event_contexts_immutable CHECK (immutable = TRUE),
    CONSTRAINT ck_event_contexts_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE INDEX idx_macro_observations_series_release
    ON macro_observations(series, released_at);
