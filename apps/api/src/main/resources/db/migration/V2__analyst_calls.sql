CREATE TABLE institutions (
    institution_id VARCHAR(100) PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL,
    slug VARCHAR(255) NOT NULL UNIQUE,
    country CHAR(2) NOT NULL,
    active BOOLEAN NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT ck_institutions_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE analysts (
    analyst_id VARCHAR(100) PRIMARY KEY,
    canonical_name VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT ck_analysts_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE assets (
    asset_id VARCHAR(100) PRIMARY KEY,
    asset_type VARCHAR(32) NOT NULL,
    canonical_name VARCHAR(255) NOT NULL,
    ticker VARCHAR(32),
    primary_currency CHAR(3) NOT NULL,
    active BOOLEAN NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    effective_at TIMESTAMP WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_assets_ticker UNIQUE (ticker),
    CONSTRAINT ck_assets_type
        CHECK (asset_type IN ('INDEX', 'EQUITY', 'ETF', 'BOND', 'COMMODITY', 'FX')),
    CONSTRAINT ck_assets_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE source_documents (
    source_document_id VARCHAR(100) PRIMARY KEY,
    source_type VARCHAR(32) NOT NULL,
    publisher VARCHAR(255),
    title VARCHAR(500) NOT NULL,
    canonical_url VARCHAR(2048),
    published_at TIMESTAMP WITH TIME ZONE,
    provider VARCHAR(100) NOT NULL,
    external_id VARCHAR(255),
    content_hash VARCHAR(255),
    license_class VARCHAR(64) NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_source_document_provider UNIQUE (provider, external_id),
    CONSTRAINT ck_source_documents_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE source_references (
    source_reference_id VARCHAR(100) PRIMARY KEY,
    source_document_id VARCHAR(100) NOT NULL REFERENCES source_documents(source_document_id),
    page_number INTEGER,
    start_ms BIGINT,
    end_ms BIGINT,
    extracted_fragment VARCHAR(4000),
    extraction_confidence NUMERIC(6, 5),
    verified BOOLEAN NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT ck_source_reference_page CHECK (page_number IS NULL OR page_number > 0),
    CONSTRAINT ck_source_reference_timecode CHECK (
        (start_ms IS NULL AND end_ms IS NULL)
        OR (start_ms >= 0 AND end_ms > start_ms)
    ),
    CONSTRAINT ck_source_reference_confidence CHECK (
        extraction_confidence IS NULL
        OR (extraction_confidence >= 0 AND extraction_confidence <= 1)
    ),
    CONSTRAINT ck_source_references_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE analyst_calls (
    call_id VARCHAR(100) PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    provider_event_id VARCHAR(255) NOT NULL,
    institution_id VARCHAR(100) NOT NULL REFERENCES institutions(institution_id),
    analyst_id VARCHAR(100) REFERENCES analysts(analyst_id),
    asset_id VARCHAR(100) NOT NULL REFERENCES assets(asset_id),
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    processing_time TIMESTAMP WITH TIME ZONE NOT NULL,
    direction VARCHAR(32) NOT NULL,
    original_rating VARCHAR(255),
    previous_target NUMERIC(38, 12),
    target NUMERIC(38, 12),
    currency CHAR(3),
    target_date DATE,
    source_reference_id VARCHAR(100) NOT NULL REFERENCES source_references(source_reference_id),
    status VARCHAR(32) NOT NULL,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT uq_analyst_calls_provider_event UNIQUE (provider, provider_event_id),
    CONSTRAINT ck_analyst_calls_time CHECK (processing_time >= event_time),
    CONSTRAINT ck_analyst_calls_direction CHECK (
        direction IN ('STRONG_BULLISH', 'BULLISH', 'NEUTRAL', 'BEARISH', 'STRONG_BEARISH')
    ),
    CONSTRAINT ck_analyst_calls_status CHECK (status IN ('ACTIVE', 'CORRECTED', 'CANCELLED')),
    CONSTRAINT ck_analyst_calls_previous_target CHECK (previous_target IS NULL OR previous_target > 0),
    CONSTRAINT ck_analyst_calls_target CHECK (target IS NULL OR target > 0),
    CONSTRAINT ck_analyst_calls_currency CHECK (
        (previous_target IS NULL AND target IS NULL)
        OR currency IS NOT NULL
    ),
    CONSTRAINT ck_analyst_calls_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE TABLE market_snapshots (
    snapshot_id VARCHAR(100) PRIMARY KEY,
    call_id VARCHAR(100) NOT NULL UNIQUE REFERENCES analyst_calls(call_id) ON DELETE RESTRICT,
    asset_id VARCHAR(100) NOT NULL REFERENCES assets(asset_id),
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    processing_time TIMESTAMP WITH TIME ZONE NOT NULL,
    asset_price NUMERIC(38, 12),
    spx NUMERIC(38, 12),
    ndx NUMERIC(38, 12),
    vix NUMERIC(38, 12),
    treasury_2y NUMERIC(38, 12),
    treasury_10y NUMERIC(38, 12),
    real_yield NUMERIC(38, 12),
    dxy NUMERIC(38, 12),
    wti NUMERIC(38, 12),
    gold NUMERIC(38, 12),
    volatility NUMERIC(38, 12),
    distance_from_52w_high NUMERIC(38, 12),
    distance_from_ath NUMERIC(38, 12),
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    data_mode VARCHAR(16) NOT NULL,
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    provenance_id VARCHAR(255) NOT NULL,
    CONSTRAINT ck_market_snapshots_time CHECK (processing_time >= event_time),
    CONSTRAINT ck_market_snapshots_price CHECK (asset_price IS NULL OR asset_price > 0),
    CONSTRAINT ck_market_snapshots_immutable CHECK (immutable = TRUE),
    CONSTRAINT ck_market_snapshots_data_mode
        CHECK (data_mode IN ('REALTIME', 'DELAYED', 'EOD', 'DEMO'))
);

CREATE INDEX idx_analyst_calls_event_time ON analyst_calls(event_time);
CREATE INDEX idx_analyst_calls_asset_event_time ON analyst_calls(asset_id, event_time);
CREATE INDEX idx_analyst_calls_institution_event_time ON analyst_calls(institution_id, event_time);
CREATE INDEX idx_analyst_calls_analyst_event_time ON analyst_calls(analyst_id, event_time);
