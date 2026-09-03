ALTER TABLE sec_filing_catalog_historical_segments
    ADD CONSTRAINT uq_sec_catalog_segment_exact
    UNIQUE (
        capture_id,
        ordinal,
        catalog_cik,
        catalog_captured_at,
        file_name,
        advertised_filing_count,
        advertised_filing_from,
        advertised_filing_to
    );

CREATE TABLE sec_historical_filing_segment_captures (
    segment_capture_id CHAR(64) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    product VARCHAR(64) NOT NULL,
    root_capture_id CHAR(64) NOT NULL,
    descriptor_ordinal INTEGER NOT NULL,
    cik CHAR(10) NOT NULL,
    root_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    file_name VARCHAR(128) NOT NULL,
    advertised_filing_count BIGINT NOT NULL,
    advertised_filing_from DATE NOT NULL,
    advertised_filing_to DATE NOT NULL,
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
    observed_filing_count BIGINT NOT NULL,
    observed_filing_from DATE,
    observed_filing_to DATE,
    advertised_comparison VARCHAR(64) NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sec_historical_segment_natural
        UNIQUE (
            root_capture_id,
            descriptor_ordinal,
            source_uri,
            captured_at
        ),
    CONSTRAINT uq_sec_historical_segment_children
        UNIQUE (
            segment_capture_id,
            cik,
            processing_time,
            captured_at
        ),
    CONSTRAINT fk_sec_historical_segment_descriptor
        FOREIGN KEY (
            root_capture_id,
            descriptor_ordinal,
            cik,
            root_captured_at,
            file_name,
            advertised_filing_count,
            advertised_filing_from,
            advertised_filing_to
        ) REFERENCES sec_filing_catalog_historical_segments (
            capture_id,
            ordinal,
            catalog_cik,
            catalog_captured_at,
            file_name,
            advertised_filing_count,
            advertised_filing_from,
            advertised_filing_to
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_historical_segment_body
        FOREIGN KEY (decoded_body_sha256, decoded_body_length)
        REFERENCES sec_decoded_response_bodies (
            decoded_body_sha256,
            decoded_body_length
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_historical_segment_id CHECK (
        REGEXP_LIKE(segment_capture_id, '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_sec_historical_segment_schema CHECK (
        schema_version = '1.0.0'
    ),
    CONSTRAINT ck_sec_historical_segment_binding CHECK (
        provider = 'sec-edgar'
        AND product = 'edgar-submissions-historical-segment-api'
        AND descriptor_ordinal >= 0
        AND REGEXP_LIKE(cik, '^[0-9]{10}$')
        AND cik <> '0000000000'
        AND REGEXP_LIKE(
            file_name,
            '^CIK[0-9]{10}-submissions-[0-9]{3}\.json$'
        )
        AND SUBSTRING(file_name FROM 4 FOR 10) = cik
        AND SUBSTRING(file_name FROM 27 FOR 3) <> '000'
        AND advertised_filing_count > 0
        AND advertised_filing_from <= advertised_filing_to
        AND source_uri = 'https://data.sec.gov/submissions/' || file_name
    ),
    CONSTRAINT ck_sec_historical_segment_time CHECK (
        root_captured_at <= processing_time
        AND processing_time <= captured_at
    ),
    CONSTRAINT ck_sec_historical_segment_receipt CHECK (
        http_status = 200
        AND media_type = TRIM(media_type)
        AND media_type <> ''
        AND transport_content_encoding IN ('IDENTITY', 'GZIP', 'DEFLATE')
        AND (etag IS NULL OR (
            etag = TRIM(etag)
            AND LENGTH(etag) > 0
            AND LENGTH(etag) <= 1024
        ))
        AND parser_version = 'SEC_SUBMISSIONS_HISTORICAL_SEGMENT_V1'
        AND REGEXP_LIKE(decoded_body_sha256, '^[0-9a-f]{64}$')
        AND decoded_body_length > 0
        AND decoded_body_length <= 8388608
        AND body_representation = 'DECODED_HTTP_ENTITY_BODY'
        AND body_retention = 'DURABLE_DECODED_BODY_RETAINED'
    ),
    CONSTRAINT ck_sec_historical_segment_observed CHECK (
        observed_filing_count >= 0
        AND (
            (observed_filing_count = 0
                AND observed_filing_from IS NULL
                AND observed_filing_to IS NULL
                AND advertised_comparison = 'COUNT_MISMATCH')
            OR
            (observed_filing_count > 0
                AND observed_filing_from IS NOT NULL
                AND observed_filing_to IS NOT NULL
                AND observed_filing_from <= observed_filing_to
                AND (
                    (observed_filing_count = advertised_filing_count
                        AND observed_filing_from >= advertised_filing_from
                        AND observed_filing_to <= advertised_filing_to
                        AND advertised_comparison = 'MATCHES_ADVERTISED')
                    OR
                    (observed_filing_count <> advertised_filing_count
                        AND observed_filing_from >= advertised_filing_from
                        AND observed_filing_to <= advertised_filing_to
                        AND advertised_comparison = 'COUNT_MISMATCH')
                    OR
                    (observed_filing_count = advertised_filing_count
                        AND (
                            observed_filing_from < advertised_filing_from
                            OR observed_filing_to > advertised_filing_to
                        )
                        AND advertised_comparison = 'RANGE_MISMATCH')
                    OR
                    (observed_filing_count <> advertised_filing_count
                        AND (
                            observed_filing_from < advertised_filing_from
                            OR observed_filing_to > advertised_filing_to
                        )
                        AND advertised_comparison = 'COUNT_AND_RANGE_MISMATCH')
                )
            )
        )
    ),
    CONSTRAINT ck_sec_historical_segment_immutable CHECK (immutable = TRUE)
);

CREATE TABLE sec_historical_filing_segment_filings (
    segment_capture_id CHAR(64) NOT NULL,
    ordinal INTEGER NOT NULL,
    segment_cik CHAR(10) NOT NULL,
    segment_processing_time TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    segment_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    provider_event_id VARCHAR(32) NOT NULL,
    accession_number VARCHAR(32) NOT NULL,
    form VARCHAR(128) NOT NULL,
    filing_date DATE NOT NULL,
    report_date DATE,
    accepted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    primary_document_uri VARCHAR(2048),
    PRIMARY KEY (segment_capture_id, ordinal),
    CONSTRAINT uq_sec_historical_segment_event
        UNIQUE (segment_capture_id, provider_event_id),
    CONSTRAINT fk_sec_historical_segment_capture
        FOREIGN KEY (
            segment_capture_id,
            segment_cik,
            segment_processing_time,
            segment_captured_at
        ) REFERENCES sec_historical_filing_segment_captures (
            segment_capture_id,
            cik,
            processing_time,
            captured_at
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_historical_segment_filing_ordinal CHECK (ordinal >= 0),
    CONSTRAINT ck_sec_historical_segment_filing_accession CHECK (
        provider_event_id = accession_number
        AND REGEXP_LIKE(accession_number, '^[0-9]{10}-[0-9]{2}-[0-9]{6}$')
    ),
    CONSTRAINT ck_sec_historical_segment_filing_form CHECK (
        form = TRIM(form) AND form <> ''
    ),
    CONSTRAINT ck_sec_historical_segment_filing_pit CHECK (
        accepted_at <= segment_processing_time
        AND segment_processing_time <= segment_captured_at
    )
);

CREATE INDEX idx_sec_historical_segment_point_in_time
    ON sec_historical_filing_segment_captures (
        root_capture_id,
        descriptor_ordinal,
        captured_at DESC,
        segment_capture_id DESC
    );
