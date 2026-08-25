ALTER TABLE sec_historical_filing_segment_captures
    ADD CONSTRAINT uq_sec_segment_attempt_source
    UNIQUE (
        segment_capture_id,
        root_capture_id,
        descriptor_ordinal
    );

CREATE TABLE sec_filing_collection_attempts (
    attempt_id CHAR(64) PRIMARY KEY,
    schema_version VARCHAR(32) NOT NULL,
    provider VARCHAR(64) NOT NULL,
    product VARCHAR(96) NOT NULL,
    policy_version VARCHAR(96) NOT NULL,
    command_sha256 CHAR(64) NOT NULL,
    operator_request_id VARCHAR(36) NOT NULL,
    command_kind VARCHAR(32) NOT NULL,
    cik CHAR(10),
    root_capture_id CHAR(64),
    requested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    max_provider_invocations SMALLINT NOT NULL,
    descriptor_action_count INTEGER NOT NULL,
    immutable BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_sec_collection_attempt_operator_request
        UNIQUE (operator_request_id),
    CONSTRAINT uq_sec_collection_attempt_command
        UNIQUE (command_sha256, operator_request_id),
    CONSTRAINT uq_sec_collection_attempt_kind
        UNIQUE (attempt_id, command_kind),
    CONSTRAINT uq_sec_collection_attempt_root
        UNIQUE (attempt_id, root_capture_id),
    CONSTRAINT uq_sec_collection_attempt_requested_at
        UNIQUE (attempt_id, requested_at),
    CONSTRAINT fk_sec_collection_attempt_exact_root
        FOREIGN KEY (root_capture_id)
        REFERENCES sec_filing_catalog_captures (capture_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_sec_collection_attempt_identity CHECK (
        REGEXP_LIKE(attempt_id, '^[0-9a-f]{64}$')
        AND REGEXP_LIKE(command_sha256, '^[0-9a-f]{64}$')
        AND operator_request_id = LOWER(operator_request_id)
        AND REGEXP_LIKE(
            operator_request_id,
            '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
        )
        AND operator_request_id <> '00000000-0000-0000-0000-000000000000'
    ),
    CONSTRAINT ck_sec_collection_attempt_contract CHECK (
        schema_version = '1.0.0'
        AND provider = 'sec-edgar'
        AND product = 'edgar-submissions-operator-collection-attempt'
        AND policy_version = 'SEC_OPERATOR_CONTROLLED_COLLECTION_ATTEMPT_V1'
        AND max_provider_invocations = 1
        AND descriptor_action_count >= 0
        AND immutable = TRUE
    ),
    CONSTRAINT ck_sec_collection_attempt_command_xor CHECK (
        (
            command_kind = 'CAPTURE_ROOT'
            AND cik IS NOT NULL
            AND REGEXP_LIKE(cik, '^[0-9]{10}$')
            AND cik <> '0000000000'
            AND root_capture_id IS NULL
            AND descriptor_action_count = 0
        )
        OR
        (
            command_kind = 'COLLECT_EXACT_ROOT'
            AND cik IS NULL
            AND root_capture_id IS NOT NULL
            AND REGEXP_LIKE(root_capture_id, '^[0-9a-f]{64}$')
        )
    )
);

CREATE TABLE sec_filing_collection_attempt_descriptor_actions (
    attempt_id CHAR(64) NOT NULL,
    command_kind VARCHAR(32) NOT NULL,
    root_capture_id CHAR(64) NOT NULL,
    descriptor_ordinal INTEGER NOT NULL,
    action_kind VARCHAR(32) NOT NULL,
    selected_segment_capture_id CHAR(64),
    capture_now_slot SMALLINT,
    PRIMARY KEY (attempt_id, descriptor_ordinal),
    CONSTRAINT uq_sec_collection_action_kind
        UNIQUE (
            attempt_id,
            root_capture_id,
            descriptor_ordinal,
            action_kind
        ),
    CONSTRAINT uq_sec_collection_action_selected_segment
        UNIQUE (attempt_id, selected_segment_capture_id),
    CONSTRAINT uq_sec_collection_action_capture_now
        UNIQUE (attempt_id, capture_now_slot),
    CONSTRAINT fk_sec_collection_action_attempt
        FOREIGN KEY (attempt_id, command_kind)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            command_kind
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_action_attempt_root
        FOREIGN KEY (attempt_id, root_capture_id)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            root_capture_id
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_action_root_descriptor
        FOREIGN KEY (root_capture_id, descriptor_ordinal)
        REFERENCES sec_filing_catalog_historical_segments (
            capture_id,
            ordinal
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_action_exact_segment
        FOREIGN KEY (
            selected_segment_capture_id,
            root_capture_id,
            descriptor_ordinal
        ) REFERENCES sec_historical_filing_segment_captures (
            segment_capture_id,
            root_capture_id,
            descriptor_ordinal
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_collection_action_identity CHECK (
        command_kind = 'COLLECT_EXACT_ROOT'
        AND descriptor_ordinal >= 0
    ),
    CONSTRAINT ck_sec_collection_action_xor CHECK (
        (
            action_kind = 'SELECT_EXACT'
            AND selected_segment_capture_id IS NOT NULL
            AND REGEXP_LIKE(
                selected_segment_capture_id,
                '^[0-9a-f]{64}$'
            )
            AND capture_now_slot IS NULL
        )
        OR
        (
            action_kind = 'CAPTURE_NOW'
            AND selected_segment_capture_id IS NULL
            AND capture_now_slot = 1
        )
    )
);

CREATE TABLE sec_filing_collection_attempt_provider_dispatches (
    attempt_id CHAR(64) PRIMARY KEY,
    command_kind VARCHAR(32) NOT NULL,
    root_capture_id CHAR(64),
    descriptor_ordinal INTEGER,
    action_kind VARCHAR(32),
    provider_operation VARCHAR(64) NOT NULL,
    attempt_requested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    dispatched_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_sec_collection_dispatch_action
        UNIQUE (
            attempt_id,
            root_capture_id,
            descriptor_ordinal
        ),
    CONSTRAINT uq_sec_collection_dispatch_time
        UNIQUE (attempt_id, dispatched_at),
    CONSTRAINT fk_sec_collection_dispatch_attempt
        FOREIGN KEY (attempt_id, command_kind)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            command_kind
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_dispatch_attempt_time
        FOREIGN KEY (attempt_id, attempt_requested_at)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            requested_at
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_dispatch_attempt_root
        FOREIGN KEY (attempt_id, root_capture_id)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            root_capture_id
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_dispatch_capture_action
        FOREIGN KEY (
            attempt_id,
            root_capture_id,
            descriptor_ordinal,
            action_kind
        ) REFERENCES sec_filing_collection_attempt_descriptor_actions (
            attempt_id,
            root_capture_id,
            descriptor_ordinal,
            action_kind
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_collection_dispatch_time CHECK (
        attempt_requested_at <= dispatched_at
    ),
    CONSTRAINT ck_sec_collection_dispatch_operation_xor CHECK (
        (
            command_kind = 'CAPTURE_ROOT'
            AND provider_operation = 'CAPTURE_ROOT'
            AND root_capture_id IS NULL
            AND descriptor_ordinal IS NULL
            AND action_kind IS NULL
        )
        OR
        (
            command_kind = 'COLLECT_EXACT_ROOT'
            AND provider_operation = 'CAPTURE_HISTORICAL_SEGMENT'
            AND root_capture_id IS NOT NULL
            AND descriptor_ordinal IS NOT NULL
            AND descriptor_ordinal >= 0
            AND action_kind = 'CAPTURE_NOW'
        )
    )
);

CREATE TABLE sec_filing_collection_attempt_outcomes (
    attempt_id CHAR(64) PRIMARY KEY,
    command_kind VARCHAR(32) NOT NULL,
    root_capture_id CHAR(64),
    provider_dispatch_attempt_id CHAR(64),
    provider_dispatched_at TIMESTAMP(6) WITH TIME ZONE,
    terminal_status VARCHAR(32) NOT NULL,
    terminal_stage VARCHAR(64) NOT NULL,
    request_disposition VARCHAR(64) NOT NULL,
    failure_code VARCHAR(64),
    http_status SMALLINT,
    root_capture_artifact_id CHAR(64),
    root_append_status VARCHAR(32) NOT NULL,
    segment_capture_artifact_id CHAR(64),
    segment_descriptor_ordinal INTEGER,
    segment_append_status VARCHAR(32) NOT NULL,
    manifest_artifact_id CHAR(64),
    manifest_append_status VARCHAR(32) NOT NULL,
    attempt_requested_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    completed_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_sec_collection_outcome_attempt
        FOREIGN KEY (attempt_id, command_kind)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            command_kind
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_attempt_time
        FOREIGN KEY (attempt_id, attempt_requested_at)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            requested_at
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_attempt_root
        FOREIGN KEY (attempt_id, root_capture_id)
        REFERENCES sec_filing_collection_attempts (
            attempt_id,
            root_capture_id
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_dispatch
        FOREIGN KEY (
            provider_dispatch_attempt_id,
            provider_dispatched_at
        )
        REFERENCES sec_filing_collection_attempt_provider_dispatches (
            attempt_id,
            dispatched_at
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_segment_dispatch
        FOREIGN KEY (
            provider_dispatch_attempt_id,
            root_capture_id,
            segment_descriptor_ordinal
        ) REFERENCES sec_filing_collection_attempt_provider_dispatches (
            attempt_id,
            root_capture_id,
            descriptor_ordinal
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_root_artifact
        FOREIGN KEY (root_capture_artifact_id)
        REFERENCES sec_filing_catalog_captures (capture_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_segment_artifact
        FOREIGN KEY (
            segment_capture_artifact_id,
            root_capture_id,
            segment_descriptor_ordinal
        ) REFERENCES sec_historical_filing_segment_captures (
            segment_capture_id,
            root_capture_id,
            descriptor_ordinal
        ) ON DELETE RESTRICT,
    CONSTRAINT fk_sec_collection_outcome_manifest_artifact
        FOREIGN KEY (manifest_artifact_id, root_capture_id)
        REFERENCES sec_filing_history_collection_manifests (
            manifest_id,
            root_capture_id
        ) ON DELETE RESTRICT,
    CONSTRAINT ck_sec_collection_outcome_time CHECK (
        attempt_requested_at <= completed_at
        AND (
            provider_dispatched_at IS NULL
            OR provider_dispatched_at <= completed_at
        )
    ),
    CONSTRAINT ck_sec_collection_outcome_request_disposition CHECK (
        (
            request_disposition = 'NO_PROVIDER_INVOCATION'
            AND provider_dispatch_attempt_id IS NULL
            AND provider_dispatched_at IS NULL
        )
        OR
        (
            request_disposition = 'PROVIDER_INVOCATION_NOT_STARTED'
            AND (
                (
                    provider_dispatch_attempt_id IS NULL
                    AND provider_dispatched_at IS NULL
                )
                OR
                (
                    provider_dispatch_attempt_id = attempt_id
                    AND provider_dispatched_at IS NOT NULL
                )
            )
        )
        OR
        (
            request_disposition IN (
                'PROVIDER_RESPONSE_RECEIVED',
                'PROVIDER_START_OR_RESPONSE_UNKNOWN'
            )
            AND provider_dispatch_attempt_id = attempt_id
            AND provider_dispatched_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_sec_collection_outcome_http_status CHECK (
        (
            failure_code = 'PROVIDER_HTTP_STATUS'
            AND terminal_status = 'FAILED_KNOWN'
            AND request_disposition = 'PROVIDER_RESPONSE_RECEIVED'
            AND http_status BETWEEN 100 AND 599
            AND http_status <> 200
        )
        OR
        (
            failure_code IS DISTINCT FROM 'PROVIDER_HTTP_STATUS'
            AND http_status IS NULL
        )
    ),
    CONSTRAINT ck_sec_collection_outcome_terminal_xor CHECK (
        (
            terminal_status = 'SUCCEEDED'
            AND failure_code IS NULL
            AND http_status IS NULL
            AND (
                (
                    command_kind = 'CAPTURE_ROOT'
                    AND terminal_stage = 'ROOT_CAPTURE'
                    AND root_capture_id IS NULL
                    AND request_disposition = 'PROVIDER_RESPONSE_RECEIVED'
                    AND root_capture_artifact_id IS NOT NULL
                    AND root_append_status IN ('INSERTED', 'IDENTICAL_REPLAY')
                    AND segment_capture_artifact_id IS NULL
                    AND segment_descriptor_ordinal IS NULL
                    AND segment_append_status = 'NOT_APPLICABLE'
                    AND manifest_artifact_id IS NULL
                    AND manifest_append_status = 'NOT_APPLICABLE'
                )
                OR
                (
                    command_kind = 'COLLECT_EXACT_ROOT'
                    AND terminal_stage = 'MANIFEST_ASSEMBLY'
                    AND root_capture_id IS NOT NULL
                    AND root_capture_artifact_id IS NULL
                    AND root_append_status = 'NOT_APPLICABLE'
                    AND manifest_artifact_id IS NOT NULL
                    AND manifest_append_status IN (
                        'INSERTED',
                        'IDENTICAL_REPLAY'
                    )
                    AND (
                        (
                            request_disposition = 'NO_PROVIDER_INVOCATION'
                            AND segment_capture_artifact_id IS NULL
                            AND segment_descriptor_ordinal IS NULL
                            AND segment_append_status = 'NOT_APPLICABLE'
                        )
                        OR
                        (
                            request_disposition = 'PROVIDER_RESPONSE_RECEIVED'
                            AND segment_capture_artifact_id IS NOT NULL
                            AND segment_descriptor_ordinal IS NOT NULL
                            AND segment_descriptor_ordinal >= 0
                            AND segment_append_status IN (
                                'INSERTED',
                                'IDENTICAL_REPLAY'
                            )
                        )
                    )
                )
            )
        )
        OR
        (
            terminal_status = 'FAILED_KNOWN'
            AND failure_code IN (
                'EXACT_EVIDENCE_VALIDATION_FAILED',
                'PROVIDER_GATE_CLOSED',
                'PROVIDER_REQUEST_FAILED',
                'PROVIDER_HTTP_STATUS',
                'PROVIDER_RESPONSE_UNREADABLE',
                'PROVIDER_RESPONSE_TOO_LARGE',
                'PROVIDER_RESPONSE_INVALID',
                'SOURCE_CAPTURE_PERSISTENCE_FAILED',
                'MANIFEST_ASSEMBLY_FAILED',
                'LOCAL_PERSISTENCE_FAILED'
            )
            AND root_capture_artifact_id IS NULL
            AND root_append_status = 'NOT_APPLICABLE'
            AND segment_capture_artifact_id IS NULL
            AND segment_descriptor_ordinal IS NULL
            AND segment_append_status = 'NOT_APPLICABLE'
            AND manifest_artifact_id IS NULL
            AND manifest_append_status = 'NOT_APPLICABLE'
        )
    ),
    CONSTRAINT ck_sec_collection_outcome_failure_stage CHECK (
        terminal_status = 'SUCCEEDED'
        OR
        (
            failure_code = 'EXACT_EVIDENCE_VALIDATION_FAILED'
            AND command_kind = 'COLLECT_EXACT_ROOT'
            AND terminal_stage = 'EXACT_EVIDENCE_VALIDATION'
            AND request_disposition = 'NO_PROVIDER_INVOCATION'
        )
        OR
        (
            failure_code = 'PROVIDER_GATE_CLOSED'
            AND terminal_stage = 'PROVIDER_GATE'
            AND request_disposition = 'PROVIDER_INVOCATION_NOT_STARTED'
        )
        OR
        (
            failure_code = 'PROVIDER_REQUEST_FAILED'
            AND (
                (
                    command_kind = 'CAPTURE_ROOT'
                    AND terminal_stage = 'ROOT_CAPTURE'
                )
                OR
                (
                    command_kind = 'COLLECT_EXACT_ROOT'
                    AND terminal_stage = 'SEGMENT_CAPTURE'
                )
            )
            AND request_disposition = 'PROVIDER_START_OR_RESPONSE_UNKNOWN'
        )
        OR
        (
            failure_code = 'PROVIDER_RESPONSE_UNREADABLE'
            AND (
                (
                    command_kind = 'CAPTURE_ROOT'
                    AND terminal_stage = 'ROOT_CAPTURE'
                )
                OR
                (
                    command_kind = 'COLLECT_EXACT_ROOT'
                    AND terminal_stage = 'SEGMENT_CAPTURE'
                )
            )
            AND request_disposition = 'PROVIDER_START_OR_RESPONSE_UNKNOWN'
        )
        OR
        (
            failure_code IN (
                'PROVIDER_HTTP_STATUS',
                'PROVIDER_RESPONSE_TOO_LARGE',
                'PROVIDER_RESPONSE_INVALID'
            )
            AND (
                (
                    command_kind = 'CAPTURE_ROOT'
                    AND terminal_stage = 'ROOT_CAPTURE'
                )
                OR
                (
                    command_kind = 'COLLECT_EXACT_ROOT'
                    AND terminal_stage = 'SEGMENT_CAPTURE'
                )
            )
            AND request_disposition = 'PROVIDER_RESPONSE_RECEIVED'
        )
        OR
        (
            failure_code = 'SOURCE_CAPTURE_PERSISTENCE_FAILED'
            AND (
                (
                    command_kind = 'CAPTURE_ROOT'
                    AND terminal_stage IN ('ROOT_CAPTURE', 'LOCAL_COMMIT')
                )
                OR
                (
                    command_kind = 'COLLECT_EXACT_ROOT'
                    AND terminal_stage IN ('SEGMENT_CAPTURE', 'LOCAL_COMMIT')
                )
            )
            AND request_disposition = 'PROVIDER_RESPONSE_RECEIVED'
        )
        OR
        (
            failure_code = 'MANIFEST_ASSEMBLY_FAILED'
            AND command_kind = 'COLLECT_EXACT_ROOT'
            AND terminal_stage = 'MANIFEST_ASSEMBLY'
            AND request_disposition IN (
                'NO_PROVIDER_INVOCATION',
                'PROVIDER_RESPONSE_RECEIVED'
            )
        )
        OR
        (
            failure_code = 'LOCAL_PERSISTENCE_FAILED'
            AND terminal_stage = 'LOCAL_COMMIT'
            AND (
                (
                    command_kind = 'COLLECT_EXACT_ROOT'
                    AND request_disposition = 'NO_PROVIDER_INVOCATION'
                )
                OR request_disposition = 'PROVIDER_RESPONSE_RECEIVED'
            )
        )
    )
);

CREATE INDEX idx_sec_collection_attempt_point_in_time
    ON sec_filing_collection_attempts (
        requested_at DESC,
        attempt_id DESC
    );

CREATE INDEX idx_sec_collection_attempt_root
    ON sec_filing_collection_attempts (
        root_capture_id,
        requested_at DESC,
        attempt_id DESC
    );

CREATE INDEX idx_sec_collection_action_selected_segment
    ON sec_filing_collection_attempt_descriptor_actions (
        selected_segment_capture_id,
        attempt_id,
        descriptor_ordinal
    );

CREATE INDEX idx_sec_collection_outcome_completed
    ON sec_filing_collection_attempt_outcomes (
        completed_at DESC,
        attempt_id DESC
    );
