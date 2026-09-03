ALTER TABLE sec_filing_catalog_captures
    ADD CONSTRAINT uq_sec_catalog_manifest_root
    UNIQUE (capture_id, cik, captured_at);

ALTER TABLE sec_filing_catalog_recent_filings
    ADD CONSTRAINT uq_sec_catalog_recent_manifest_source
    UNIQUE (
        capture_id,
        ordinal,
        provider_event_id,
        accession_number
    );

ALTER TABLE sec_historical_filing_segment_captures
    ADD CONSTRAINT uq_sec_history_segment_manifest_member
    UNIQUE (
        segment_capture_id,
        root_capture_id,
        descriptor_ordinal,
        cik,
        root_captured_at,
        file_name,
        advertised_filing_count,
        advertised_filing_from,
        advertised_filing_to,
        captured_at
    );

ALTER TABLE sec_historical_filing_segment_filings
    ADD CONSTRAINT uq_sec_history_filing_manifest_source
    UNIQUE (
        segment_capture_id,
        ordinal,
        provider_event_id,
        accession_number
    );

CREATE TABLE sec_filing_history_collection_manifests (
    manifest_id CHAR(64) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    product VARCHAR(64) NOT NULL,
    policy_version VARCHAR(64) NOT NULL,
    root_capture_id CHAR(64) NOT NULL,
    cik CHAR(10) NOT NULL,
    root_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    selection_sha256 CHAR(64) NOT NULL,
    evidence_available_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    assembled_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    advertised_descriptor_count INTEGER NOT NULL,
    selected_descriptor_count INTEGER NOT NULL,
    selection_coverage VARCHAR(64) NOT NULL,
    source_occurrence_count BIGINT NOT NULL,
    distinct_accession_count BIGINT NOT NULL,
    single_source_group_count BIGINT NOT NULL,
    exact_agreement_group_count BIGINT NOT NULL,
    canonical_conflict_group_count BIGINT NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sec_history_manifest_natural
        UNIQUE (root_capture_id, policy_version, selection_sha256),
    CONSTRAINT uq_sec_history_manifest_root_member
        UNIQUE (manifest_id, root_capture_id),
    CONSTRAINT uq_sec_history_manifest_children
        UNIQUE (manifest_id, root_capture_id, cik, root_captured_at),
    CONSTRAINT fk_sec_history_manifest_root
        FOREIGN KEY (root_capture_id, cik, root_captured_at)
        REFERENCES sec_filing_catalog_captures (
            capture_id,
            cik,
            captured_at
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_history_manifest_id CHECK (
        REGEXP_LIKE(manifest_id, '^[0-9a-f]{64}$')
    ),
    CONSTRAINT ck_sec_history_manifest_contract CHECK (
        schema_version = '1.0.0'
        AND provider = 'sec-edgar'
        AND product =
            'edgar-submissions-root-relative-collection-manifest'
        AND policy_version =
            'SEC_ROOT_RELATIVE_ACCESSION_RECONCILIATION_V1'
        AND REGEXP_LIKE(selection_sha256, '^[0-9a-f]{64}$')
        AND REGEXP_LIKE(cik, '^[0-9]{10}$')
        AND cik <> '0000000000'
    ),
    CONSTRAINT ck_sec_history_manifest_time CHECK (
        root_captured_at <= evidence_available_at
        AND evidence_available_at <= assembled_at
    ),
    CONSTRAINT ck_sec_history_manifest_descriptor_counts CHECK (
        advertised_descriptor_count >= 0
        AND selected_descriptor_count >= 0
        AND selected_descriptor_count <= advertised_descriptor_count
        AND (
            (selection_coverage = 'NO_ADVERTISED_DESCRIPTORS'
                AND advertised_descriptor_count = 0
                AND selected_descriptor_count = 0)
            OR
            (selection_coverage =
                    'PARTIAL_ADVERTISED_DESCRIPTORS_SELECTED'
                AND advertised_descriptor_count > 0
                AND selected_descriptor_count < advertised_descriptor_count)
            OR
            (selection_coverage = 'ALL_ADVERTISED_DESCRIPTORS_SELECTED'
                AND advertised_descriptor_count > 0
                AND selected_descriptor_count = advertised_descriptor_count)
        )
    ),
    CONSTRAINT ck_sec_history_manifest_occurrence_counts CHECK (
        source_occurrence_count >= 0
        AND distinct_accession_count >= 0
        AND distinct_accession_count <= source_occurrence_count
        AND single_source_group_count >= 0
        AND exact_agreement_group_count >= 0
        AND canonical_conflict_group_count >= 0
        AND distinct_accession_count =
            single_source_group_count
            + exact_agreement_group_count
            + canonical_conflict_group_count
        AND (
            (source_occurrence_count = 0
                AND distinct_accession_count = 0)
            OR
            (source_occurrence_count > 0
                AND distinct_accession_count > 0)
        )
    ),
    CONSTRAINT ck_sec_history_manifest_immutable CHECK (immutable = TRUE)
);

CREATE TABLE sec_filing_history_collection_descriptors (
    manifest_id CHAR(64) NOT NULL,
    descriptor_ordinal INTEGER NOT NULL,
    root_capture_id CHAR(64) NOT NULL,
    cik CHAR(10) NOT NULL,
    root_captured_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    file_name VARCHAR(128) NOT NULL,
    advertised_filing_count BIGINT NOT NULL,
    advertised_filing_from DATE NOT NULL,
    advertised_filing_to DATE NOT NULL,
    selection_state VARCHAR(64) NOT NULL,
    selected_segment_capture_id CHAR(64),
    selected_segment_captured_at TIMESTAMP(6) WITH TIME ZONE,
    PRIMARY KEY (manifest_id, descriptor_ordinal),
    CONSTRAINT uq_sec_history_descriptor_selected_member
        UNIQUE (
            manifest_id,
            descriptor_ordinal,
            selected_segment_capture_id
        ),
    CONSTRAINT fk_sec_history_descriptor_manifest
        FOREIGN KEY (
            manifest_id,
            root_capture_id,
            cik,
            root_captured_at
        ) REFERENCES sec_filing_history_collection_manifests (
            manifest_id,
            root_capture_id,
            cik,
            root_captured_at
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_history_descriptor_root_source
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
    CONSTRAINT fk_sec_history_descriptor_segment_source
        FOREIGN KEY (
            selected_segment_capture_id,
            root_capture_id,
            descriptor_ordinal,
            cik,
            root_captured_at,
            file_name,
            advertised_filing_count,
            advertised_filing_from,
            advertised_filing_to,
            selected_segment_captured_at
        ) REFERENCES sec_historical_filing_segment_captures (
            segment_capture_id,
            root_capture_id,
            descriptor_ordinal,
            cik,
            root_captured_at,
            file_name,
            advertised_filing_count,
            advertised_filing_from,
            advertised_filing_to,
            captured_at
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_history_descriptor_identity CHECK (
        descriptor_ordinal >= 0
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
    ),
    CONSTRAINT ck_sec_history_descriptor_selection CHECK (
        (
            selection_state = 'NOT_SELECTED'
            AND selected_segment_capture_id IS NULL
            AND selected_segment_captured_at IS NULL
        )
        OR
        (
            selection_state = 'SELECTED_EXACT_CAPTURE'
            AND selected_segment_capture_id IS NOT NULL
            AND REGEXP_LIKE(
                selected_segment_capture_id,
                '^[0-9a-f]{64}$'
            )
            AND selected_segment_captured_at IS NOT NULL
            AND selected_segment_captured_at >= root_captured_at
        )
    )
);

CREATE TABLE sec_filing_history_collection_accession_groups (
    manifest_id CHAR(64) NOT NULL,
    group_ordinal INTEGER NOT NULL,
    accession_number VARCHAR(32) NOT NULL,
    occurrence_count BIGINT NOT NULL,
    distinct_projection_count BIGINT NOT NULL,
    comparison VARCHAR(64) NOT NULL,
    PRIMARY KEY (manifest_id, group_ordinal),
    CONSTRAINT uq_sec_history_group_accession
        UNIQUE (manifest_id, accession_number),
    CONSTRAINT fk_sec_history_group_manifest
        FOREIGN KEY (manifest_id)
        REFERENCES sec_filing_history_collection_manifests (manifest_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_sec_history_group_identity CHECK (
        group_ordinal >= 0
        AND REGEXP_LIKE(
            accession_number,
            '^[0-9]{10}-[0-9]{2}-[0-9]{6}$'
        )
    ),
    CONSTRAINT ck_sec_history_group_comparison CHECK (
        occurrence_count > 0
        AND distinct_projection_count > 0
        AND distinct_projection_count <= occurrence_count
        AND (
            (comparison = 'SINGLE_SOURCE_OCCURRENCE'
                AND occurrence_count = 1
                AND distinct_projection_count = 1)
            OR
            (comparison = 'MULTIPLE_OCCURRENCES_EXACT_AGREEMENT'
                AND occurrence_count > 1
                AND distinct_projection_count = 1)
            OR
            (comparison = 'MULTIPLE_OCCURRENCES_CANONICAL_CONFLICT'
                AND occurrence_count > 1
                AND distinct_projection_count > 1)
        )
    )
);

CREATE TABLE sec_filing_history_collection_occurrences (
    manifest_id CHAR(64) NOT NULL,
    occurrence_ordinal BIGINT NOT NULL,
    source_kind VARCHAR(64) NOT NULL,
    root_source_capture_id CHAR(64),
    descriptor_ordinal INTEGER,
    segment_source_capture_id CHAR(64),
    source_row_ordinal INTEGER NOT NULL,
    provider_event_id VARCHAR(32) NOT NULL,
    accession_number VARCHAR(32) NOT NULL,
    form VARCHAR(128) NOT NULL,
    filing_date DATE NOT NULL,
    report_date DATE,
    accepted_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    primary_document_uri VARCHAR(2048),
    projection_sha256 CHAR(64) NOT NULL,
    PRIMARY KEY (manifest_id, occurrence_ordinal),
    CONSTRAINT uq_sec_history_occurrence_root_source
        UNIQUE (
            manifest_id,
            root_source_capture_id,
            source_row_ordinal
        ),
    CONSTRAINT uq_sec_history_occurrence_segment_source
        UNIQUE (
            manifest_id,
            segment_source_capture_id,
            source_row_ordinal
        ),
    CONSTRAINT fk_sec_history_occurrence_manifest
        FOREIGN KEY (manifest_id)
        REFERENCES sec_filing_history_collection_manifests (manifest_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_sec_history_occurrence_group
        FOREIGN KEY (manifest_id, accession_number)
        REFERENCES sec_filing_history_collection_accession_groups (
            manifest_id,
            accession_number
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_history_occurrence_root_member
        FOREIGN KEY (manifest_id, root_source_capture_id)
        REFERENCES sec_filing_history_collection_manifests (
            manifest_id,
            root_capture_id
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_history_occurrence_selected_member
        FOREIGN KEY (
            manifest_id,
            descriptor_ordinal,
            segment_source_capture_id
        ) REFERENCES sec_filing_history_collection_descriptors (
            manifest_id,
            descriptor_ordinal,
            selected_segment_capture_id
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_history_occurrence_root_source
        FOREIGN KEY (
            root_source_capture_id,
            source_row_ordinal,
            provider_event_id,
            accession_number
        ) REFERENCES sec_filing_catalog_recent_filings (
            capture_id,
            ordinal,
            provider_event_id,
            accession_number
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_history_occurrence_segment_source
        FOREIGN KEY (
            segment_source_capture_id,
            source_row_ordinal,
            provider_event_id,
            accession_number
        ) REFERENCES sec_historical_filing_segment_filings (
            segment_capture_id,
            ordinal,
            provider_event_id,
            accession_number
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_history_occurrence_order CHECK (
        occurrence_ordinal >= 0
        AND source_row_ordinal >= 0
    ),
    CONSTRAINT ck_sec_history_occurrence_source CHECK (
        (
            source_kind = 'ROOT_RECENT'
            AND root_source_capture_id IS NOT NULL
            AND REGEXP_LIKE(root_source_capture_id, '^[0-9a-f]{64}$')
            AND descriptor_ordinal IS NULL
            AND segment_source_capture_id IS NULL
        )
        OR
        (
            source_kind = 'HISTORICAL_SEGMENT'
            AND root_source_capture_id IS NULL
            AND descriptor_ordinal IS NOT NULL
            AND descriptor_ordinal >= 0
            AND segment_source_capture_id IS NOT NULL
            AND REGEXP_LIKE(
                segment_source_capture_id,
                '^[0-9a-f]{64}$'
            )
        )
    ),
    CONSTRAINT ck_sec_history_occurrence_projection CHECK (
        provider_event_id = accession_number
        AND REGEXP_LIKE(
            accession_number,
            '^[0-9]{10}-[0-9]{2}-[0-9]{6}$'
        )
        AND form = TRIM(form)
        AND form <> ''
        AND (
            primary_document_uri IS NULL
            OR (
                primary_document_uri = TRIM(primary_document_uri)
                AND primary_document_uri <> ''
            )
        )
        AND REGEXP_LIKE(projection_sha256, '^[0-9a-f]{64}$')
    )
);

CREATE INDEX idx_sec_history_manifest_point_in_time
    ON sec_filing_history_collection_manifests (
        root_capture_id,
        policy_version,
        assembled_at DESC,
        manifest_id DESC
    );

CREATE INDEX idx_sec_history_descriptor_selected_capture
    ON sec_filing_history_collection_descriptors (
        selected_segment_capture_id,
        manifest_id,
        descriptor_ordinal
    );

CREATE INDEX idx_sec_history_occurrence_accession
    ON sec_filing_history_collection_occurrences (
        manifest_id,
        accession_number,
        occurrence_ordinal
    );
