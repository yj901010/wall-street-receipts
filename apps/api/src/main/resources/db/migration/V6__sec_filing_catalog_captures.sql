CREATE TABLE sec_decoded_response_bodies (
    decoded_body_sha256 CHAR(64) PRIMARY KEY,
    decoded_body_length BIGINT NOT NULL,
    decoded_body BYTEA NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sec_decoded_response_body_identity
        UNIQUE (decoded_body_sha256, decoded_body_length),
    CONSTRAINT ck_sec_decoded_response_body_digest CHECK (
        REGEXP_LIKE(decoded_body_sha256, '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_sec_decoded_response_body_length CHECK (
        decoded_body_length > 0
        AND decoded_body_length <= 8388608
        AND OCTET_LENGTH(decoded_body) = decoded_body_length
    ),
    CONSTRAINT ck_sec_decoded_response_body_immutable CHECK (immutable = TRUE)
);

CREATE TABLE sec_filing_catalog_captures (
    capture_id CHAR(64) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    product VARCHAR(64) NOT NULL,
    cik CHAR(10) NOT NULL,
    source_uri VARCHAR(1024) NOT NULL,
    processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    http_status SMALLINT NOT NULL,
    media_type VARCHAR(128) NOT NULL,
    transport_content_encoding VARCHAR(16) NOT NULL,
    etag VARCHAR(1024),
    last_modified TIMESTAMP(6) WITH TIME ZONE,
    parser_version VARCHAR(64) NOT NULL,
    decoded_body_sha256 CHAR(64) NOT NULL,
    decoded_body_length BIGINT NOT NULL,
    body_representation VARCHAR(64) NOT NULL,
    body_retention VARCHAR(64) NOT NULL,
    recent_filing_count INTEGER NOT NULL,
    historical_segment_count INTEGER NOT NULL,
    historical_segment_status VARCHAR(64) NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sec_filing_catalog_capture_natural
        UNIQUE (provider, product, source_uri, captured_at),
    CONSTRAINT uq_sec_filing_catalog_capture_children
        UNIQUE (capture_id, cik, processing_time, captured_at),
    CONSTRAINT fk_sec_filing_catalog_capture_body
        FOREIGN KEY (decoded_body_sha256, decoded_body_length)
        REFERENCES sec_decoded_response_bodies(
            decoded_body_sha256, decoded_body_length
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_filing_catalog_capture_id CHECK (
        REGEXP_LIKE(capture_id, '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_sec_filing_catalog_capture_schema
        CHECK (schema_version = '1.0.0'),
    CONSTRAINT ck_sec_filing_catalog_capture_identity CHECK (
        provider = TRIM(provider)
        AND product = TRIM(product)
        AND provider <> ''
        AND product <> ''
        AND REGEXP_LIKE(cik, '^[0-9]{10}$')
        AND cik <> '0000000000'
        AND source_uri = TRIM(source_uri)
        AND source_uri LIKE '%/submissions/CIK' || cik || '.json'
    ),
    CONSTRAINT ck_sec_filing_catalog_capture_time
        CHECK (captured_at >= processing_time),
    CONSTRAINT ck_sec_filing_catalog_capture_receipt CHECK (
        http_status = 200
        AND media_type = TRIM(media_type)
        AND media_type <> ''
        AND transport_content_encoding IN ('IDENTITY', 'GZIP', 'DEFLATE')
        AND (etag IS NULL OR (
            etag = TRIM(etag)
            AND LENGTH(etag) > 0
            AND LENGTH(etag) <= 1024
        ))
        AND parser_version = TRIM(parser_version)
        AND parser_version <> ''
        AND REGEXP_LIKE(decoded_body_sha256, '^[0-9a-f]{64}$')
        AND decoded_body_length > 0
        AND decoded_body_length <= 8388608
        AND body_representation = 'DECODED_HTTP_ENTITY_BODY'
        AND body_retention = 'DURABLE_DECODED_BODY_RETAINED'
    ),
    CONSTRAINT ck_sec_filing_catalog_capture_counts CHECK (
        recent_filing_count >= 0
        AND historical_segment_count >= 0
        AND (
            (historical_segment_count = 0
                AND historical_segment_status =
                    'RECENT_ONLY_NO_SEGMENTS_ADVERTISED')
            OR (historical_segment_count > 0
                AND historical_segment_status =
                    'RECENT_ONLY_SEGMENTS_ADVERTISED_NOT_FETCHED')
        )
    ),
    CONSTRAINT ck_sec_filing_catalog_capture_immutable CHECK (immutable = TRUE)
);

CREATE TABLE sec_filing_catalog_recent_filings (
    capture_id CHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL,
    catalog_cik CHAR(10) NOT NULL,
    catalog_processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    catalog_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provider_event_id VARCHAR(32) NOT NULL,
    accession_number VARCHAR(32) NOT NULL,
    form VARCHAR(128) NOT NULL,
    filing_date DATE NOT NULL,
    report_date DATE,
    accepted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    primary_document_uri VARCHAR(2048) NOT NULL,
    PRIMARY KEY (capture_id, ordinal),
    CONSTRAINT uq_sec_filing_catalog_recent_event
        UNIQUE (capture_id, provider_event_id),
    CONSTRAINT fk_sec_filing_catalog_recent_capture
        FOREIGN KEY (
            capture_id, catalog_cik, catalog_processing_time, catalog_captured_at
        ) REFERENCES sec_filing_catalog_captures(
            capture_id, cik, processing_time, captured_at
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_filing_catalog_recent_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_sec_filing_catalog_recent_accession CHECK (
        provider_event_id = accession_number
        AND REGEXP_LIKE(accession_number, '^[0-9]{10}-[0-9]{2}-[0-9]{6}$')
    ),
    CONSTRAINT ck_sec_filing_catalog_recent_form CHECK (
        form = TRIM(form) AND form <> ''
    ),
    CONSTRAINT ck_sec_filing_catalog_recent_point_in_time CHECK (
        accepted_at <= catalog_processing_time
        AND catalog_captured_at >= catalog_processing_time
    )
);

CREATE TABLE sec_filing_catalog_historical_segments (
    capture_id CHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL,
    catalog_cik CHAR(10) NOT NULL,
    catalog_processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    catalog_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    file_name VARCHAR(128) NOT NULL,
    advertised_filing_count BIGINT NOT NULL,
    advertised_filing_from DATE NOT NULL,
    advertised_filing_to DATE NOT NULL,
    PRIMARY KEY (capture_id, ordinal),
    CONSTRAINT uq_sec_filing_catalog_historical_file
        UNIQUE (capture_id, file_name),
    CONSTRAINT fk_sec_filing_catalog_historical_capture
        FOREIGN KEY (
            capture_id, catalog_cik, catalog_processing_time, catalog_captured_at
        ) REFERENCES sec_filing_catalog_captures(
            capture_id, cik, processing_time, captured_at
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_filing_catalog_historical_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_sec_filing_catalog_historical_file CHECK (
        REGEXP_LIKE(
            file_name,
            '^CIK[0-9]{10}-submissions-[0-9]{3}\.json$'
        )
        AND SUBSTRING(file_name FROM 4 FOR 10) = catalog_cik
        AND SUBSTRING(file_name FROM 27 FOR 3) <> '000'
    ),
    CONSTRAINT ck_sec_filing_catalog_historical_manifest CHECK (
        advertised_filing_count > 0
        AND advertised_filing_from <= advertised_filing_to
    )
);

CREATE INDEX idx_sec_filing_catalog_capture_point_in_time
    ON sec_filing_catalog_captures(
        provider, product, cik, captured_at DESC, capture_id DESC
    );
