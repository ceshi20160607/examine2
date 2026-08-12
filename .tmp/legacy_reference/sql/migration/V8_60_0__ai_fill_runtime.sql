-- Governed AI_FILL owner materialization. Provider requests and proposal state
-- remain AI-module concerns; only typed current values and immutable owner
-- outcomes live here. Source values are represented solely by a version hash.

-- AI_FILL is a published, typed dependency contract just like the existing
-- derived fields. V4.10 predates that runtime and therefore classified it as
-- an ordinary field, which rejects the projected result/dependency metadata.
ALTER TABLE un_module_runtime_schema_field
    DROP CHECK ck_rsf_derived_contract;

ALTER TABLE un_module_runtime_schema_field
    ADD CONSTRAINT ck_rsf_derived_contract CHECK (
        (field_scope = 'RECORD'
            AND field_type IN (
                'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE',
                'AI_FILL')
            AND is_required = FALSE AND is_readonly = TRUE
            AND result_schema IN (
                'STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN')
            AND evaluator_version = 1
            AND expression_checksum REGEXP '^[a-f0-9]{64}$'
            AND topological_rank BETWEEN 0 AND 64
            AND dependency_json IS NOT NULL
            AND JSON_TYPE(dependency_json) = 'ARRAY'
            AND OCTET_LENGTH(dependency_json) <= 32768)
        OR (field_type NOT IN (
                'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE',
                'AI_FILL')
            AND result_schema IS NULL AND evaluator_version IS NULL
            AND expression_checksum IS NULL AND topological_rank IS NULL
            AND dependency_json IS NULL)
    );

-- The materialized value participates in normal record reads, but retains a
-- distinct one-hash dependency envelope because it is confirmation-driven and
-- must never be recomputed by the formula/summary workers.
ALTER TABLE un_module_record_value
    DROP CHECK ck_rv_supported_type,
    DROP CHECK ck_rv_derived_metadata,
    DROP CHECK ck_rv_typed_payload;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT',
            'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE',
            'SWITCH', 'RATING', 'PROGRESS', 'TAG', 'PHONE', 'EMAIL', 'URL', 'IDENTITY',
            'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS', 'REFERENCE',
            'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE', 'AI_FILL',
            'TENANT', 'AUTO_NUMBER', 'CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT'
        )
    ),
    ADD CONSTRAINT ck_rv_derived_metadata CHECK (
        (field_type IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND field_scope = 'RECORD'
            AND result_schema IN ('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN')
            AND dependency_version_json IS NOT NULL
            AND JSON_TYPE(dependency_version_json) = 'ARRAY'
            AND OCTET_LENGTH(dependency_version_json) <= 32768
            AND evaluator_version = 1
            AND recalculation_state IN ('READY', 'PENDING', 'FAILED')
            AND ((recalculation_state = 'FAILED' AND failure_correlation_id IS NOT NULL
                    AND failure_correlation_id REGEXP '^[A-Za-z0-9._:-]{1,64}$')
                OR (recalculation_state <> 'FAILED' AND failure_correlation_id IS NULL)))
        OR (field_type = 'AI_FILL'
            AND field_scope = 'RECORD'
            AND result_schema IN ('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN')
            AND dependency_version_json IS NOT NULL
            AND JSON_TYPE(dependency_version_json) = 'OBJECT'
            AND JSON_LENGTH(dependency_version_json) = 1
            AND JSON_UNQUOTE(JSON_EXTRACT(
                    dependency_version_json, '$.sourceVersionHash')) REGEXP '^[0-9a-f]{64}$'
            AND OCTET_LENGTH(dependency_version_json) <= 128
            AND evaluator_version = 1
            AND recalculation_state = 'READY'
            AND failure_correlation_id IS NULL)
        OR (field_type NOT IN (
                'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE', 'AI_FILL')
            AND field_scope = 'RECORD'
            AND result_schema IS NULL AND dependency_version_json IS NULL
            AND evaluator_version IS NULL AND recalculation_state IS NULL
            AND failure_correlation_id IS NULL)
    ),
    ADD CONSTRAINT ck_rv_typed_payload CHECK (
        (field_type IN ('TEXT', 'TAG', 'PHONE', 'EMAIL', 'URL', 'AUTO_NUMBER')
            AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TEXTAREA', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON')
            AND text_value IS NOT NULL
            AND string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('NUMBER', 'PERCENT', 'RATING', 'PROGRESS') AND decimal_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND reference_value IS NULL)
        OR (field_type IN ('DATE', 'DATE_RANGE') AND date_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('DATETIME', 'CREATED_AT', 'UPDATED_AT') AND datetime_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TIME', 'TIME_RANGE') AND time_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'SWITCH' AND boolean_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN (
                'RADIO', 'MEMBER', 'DEPARTMENT', 'MULTI_SELECT', 'CASCADE', 'STATUS',
                'TENANT', 'CREATED_BY', 'UPDATED_BY'
            ) AND reference_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL)
        OR (field_type IN ('IDENTITY', 'SECRET') AND string_value IS NULL AND text_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'REFERENCE'
            AND ((string_value IS NOT NULL) + (decimal_value IS NOT NULL) + (date_value IS NOT NULL)
                + (datetime_value IS NOT NULL) + (boolean_value IS NOT NULL) = 1)
            AND text_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND text_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL
            AND ((string_value IS NOT NULL) + (decimal_value IS NOT NULL) + (date_value IS NOT NULL)
                + (datetime_value IS NOT NULL) + (boolean_value IS NOT NULL) <= 1)
            AND ((string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL)
                OR (result_schema = 'STRING' AND string_value IS NOT NULL)
                OR (result_schema = 'DECIMAL' AND decimal_value IS NOT NULL)
                OR (result_schema = 'INTEGER' AND decimal_value IS NOT NULL
                    AND decimal_value = TRUNCATE(decimal_value, 0))
                OR (result_schema = 'DATE' AND date_value IS NOT NULL)
                OR (result_schema = 'DATETIME' AND datetime_value IS NOT NULL)
                OR (result_schema = 'BOOLEAN' AND boolean_value IS NOT NULL)))
        OR (field_type = 'AI_FILL'
            AND text_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL
            AND ((result_schema = 'STRING' AND string_value IS NOT NULL
                    AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL)
                OR (result_schema IN ('DECIMAL', 'INTEGER') AND string_value IS NULL
                    AND decimal_value IS NOT NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL
                    AND (result_schema <> 'INTEGER' OR decimal_value = TRUNCATE(decimal_value, 0)))
                OR (result_schema = 'DATE' AND string_value IS NULL
                    AND decimal_value IS NULL AND date_value IS NOT NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL)
                OR (result_schema = 'DATETIME' AND string_value IS NULL
                    AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NOT NULL AND boolean_value IS NULL)
                OR (result_schema = 'BOOLEAN' AND string_value IS NULL
                    AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NOT NULL)))
    );

CREATE TABLE un_module_ai_fill_materialization (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    field_snapshot_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_schema VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    string_value TEXT NULL,
    decimal_value DECIMAL(38,18) NULL,
    date_value DATE NULL,
    datetime_value DATETIME(6) NULL,
    boolean_value BOOLEAN NULL,
    display_value TEXT NOT NULL,
    value_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_version_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    materialized_by_member_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    model_snapshot VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version_id BIGINT NOT NULL,
    proposal_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, record_id, field_snapshot_id),
    UNIQUE KEY uk_module_ai_fill_materialization_id (id),
    KEY idx_module_ai_fill_field (
        system_id, tenant_id, field_snapshot_id, updated_at, record_id),
    CONSTRAINT ck_module_ai_fill_materialization_identity CHECK (
        id > 0 AND record_id > 0 AND schema_version_id > 0
        AND module_snapshot_id > 0 AND field_snapshot_id > 0
        AND logical_field_id > 0 AND materialized_by_member_id > 0
        AND provider_id > 0 AND policy_version_id > 0
        AND provider_version >= 0 AND version >= 0),
    CONSTRAINT ck_module_ai_fill_materialization_field CHECK (
        field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND result_schema IN (
            'STRING','DECIMAL','INTEGER','DATE','DATETIME','BOOLEAN')),
    CONSTRAINT ck_module_ai_fill_materialization_typed CHECK (
        (result_schema = 'STRING' AND string_value IS NOT NULL
            AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND boolean_value IS NULL)
        OR (result_schema IN ('DECIMAL','INTEGER') AND string_value IS NULL
            AND decimal_value IS NOT NULL AND date_value IS NULL
            AND datetime_value IS NULL AND boolean_value IS NULL)
        OR (result_schema = 'DATE' AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NOT NULL
            AND datetime_value IS NULL AND boolean_value IS NULL)
        OR (result_schema = 'DATETIME' AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NOT NULL AND boolean_value IS NULL)
        OR (result_schema = 'BOOLEAN' AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND boolean_value IS NOT NULL)),
    CONSTRAINT ck_module_ai_fill_materialization_hash CHECK (
        value_hash REGEXP '^[0-9a-f]{64}$'
        AND source_version_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_module_ai_fill_materialization_confidence CHECK (
        confidence BETWEEN 0.5000 AND 1.0000),
    CONSTRAINT ck_module_ai_fill_materialization_time CHECK (
        updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_ai_fill_history (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    record_version BIGINT UNSIGNED NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    field_snapshot_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_schema VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_version_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confidence DECIMAL(5,4) NOT NULL,
    actor_member_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    model_snapshot VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version_id BIGINT NOT NULL,
    proposal_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    previous_value_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_value_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    materialization_version BIGINT UNSIGNED NULL,
    outcome VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_ai_fill_history_id (id),
    UNIQUE KEY uk_module_ai_fill_history_proposal (
        system_id, tenant_id, proposal_id),
    KEY idx_module_ai_fill_history_record (
        system_id, tenant_id, record_id, field_snapshot_id, created_at, id),
    CONSTRAINT ck_module_ai_fill_history_identity CHECK (
        id > 0 AND record_id > 0 AND record_version >= 0
        AND schema_version_id > 0 AND module_snapshot_id > 0
        AND field_snapshot_id > 0 AND logical_field_id > 0
        AND actor_member_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND policy_version_id > 0),
    CONSTRAINT ck_module_ai_fill_history_field CHECK (
        field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND result_schema IN (
            'STRING','DECIMAL','INTEGER','DATE','DATETIME','BOOLEAN')),
    CONSTRAINT ck_module_ai_fill_history_hash CHECK (
        source_version_hash REGEXP '^[0-9a-f]{64}$'
        AND (previous_value_hash IS NULL
            OR previous_value_hash REGEXP '^[0-9a-f]{64}$')
        AND (result_value_hash IS NULL
            OR result_value_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_module_ai_fill_history_confidence CHECK (
        confidence BETWEEN 0.5000 AND 1.0000),
    CONSTRAINT ck_module_ai_fill_history_outcome CHECK (
        (outcome = 'CREATED' AND previous_value_hash IS NULL
            AND result_value_hash IS NOT NULL
            AND materialization_version = 0)
        OR (outcome = 'OVERWRITTEN' AND previous_value_hash IS NOT NULL
            AND result_value_hash IS NOT NULL
            AND materialization_version > 0)
        OR (outcome = 'REJECTED' AND result_value_hash IS NULL
            AND materialization_version IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Actor-bound AI fill proposals. Provider output is reduced to a safe preview
-- and an authenticated owner command; generic Agent conversation tables are
-- not involved and hidden source values are never copied here.
CREATE TABLE un_ai_fill_proposal (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    module_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    record_id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    field_name VARCHAR(160) NOT NULL,
    result_schema VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_record_version BIGINT UNSIGNED NOT NULL,
    schema_version_id BIGINT NOT NULL,
    source_version_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(160) NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    sources_json JSON NOT NULL,
    before_display_value TEXT NULL,
    after_display_value TEXT NULL,
    confidence DECIMAL(6,5) NOT NULL,
    clarification_summary VARCHAR(500) NULL,
    is_overwrite BOOLEAN NOT NULL,
    result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    sealed_ciphertext MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NULL,
    sealed_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    sealed_command_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    acted_by BIGINT NULL,
    result_json JSON NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    owner_trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    prompt_tokens INT UNSIGNED NOT NULL,
    completion_tokens INT UNSIGNED NOT NULL,
    provider_latency_ms BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_fill_proposal_id (id),
    KEY idx_ai_fill_proposal_member (
        system_id, tenant_id, member_id, module_code, record_id,
        field_code, created_at DESC, id DESC),
    KEY idx_ai_fill_proposal_target (
        system_id, tenant_id, module_code, record_id, field_code, state, id),
    CONSTRAINT fk_ai_fill_proposal_policy FOREIGN KEY (
        system_id, tenant_id, policy_version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_fill_proposal_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_fill_proposal_identity CHECK (
        id > 0 AND member_id > 0 AND record_id > 0 AND field_id > 0
        AND schema_version_id > 0 AND policy_version_id > 0
        AND provider_id > 0 AND provider_version >= 0
        AND authorization_epoch > 0 AND revision >= 0),
    CONSTRAINT ck_ai_fill_proposal_target CHECK (
        module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND result_schema IN (
            'STRING','DECIMAL','INTEGER','DATE','DATETIME','BOOLEAN')),
    CONSTRAINT ck_ai_fill_proposal_json CHECK (
        JSON_TYPE(sources_json) = 'ARRAY'
        AND JSON_LENGTH(sources_json) BETWEEN 1 AND 16
        AND (result_json IS NULL OR JSON_TYPE(result_json) = 'OBJECT')),
    CONSTRAINT ck_ai_fill_proposal_confidence CHECK (
        confidence BETWEEN 0.00000 AND 1.00000),
    CONSTRAINT ck_ai_fill_proposal_hash CHECK (
        source_version_hash REGEXP '^[0-9a-f]{64}$'
        AND (result_hash IS NULL OR result_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_fill_proposal_sealed CHECK (
        (state IN ('CLARIFICATION_REQUIRED','FAILED','EXPIRED')
            AND sealed_ciphertext IS NULL AND sealed_key_version IS NULL
            AND sealed_command_hash IS NULL)
        OR (CHAR_LENGTH(sealed_ciphertext) BETWEEN 1 AND 131072
            AND CHAR_LENGTH(sealed_key_version) BETWEEN 1 AND 64
            AND sealed_command_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_fill_proposal_state CHECK (
        state IN ('CLARIFICATION_REQUIRED','PENDING','EXECUTING','SUCCEEDED',
                  'FAILED','REJECTED','EXPIRED')),
    CONSTRAINT ck_ai_fill_proposal_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND ((state IN ('PENDING','CLARIFICATION_REQUIRED')
              AND acted_by IS NULL AND result_json IS NULL
              AND finished_at IS NULL)
          OR (state = 'EXECUTING' AND acted_by > 0
              AND result_json IS NULL AND finished_at IS NULL)
          OR (state = 'SUCCEEDED' AND acted_by > 0
              AND result_json IS NOT NULL AND finished_at IS NOT NULL)
          OR (state IN ('FAILED','REJECTED','EXPIRED')
              AND result_json IS NULL AND finished_at IS NOT NULL))),
    CONSTRAINT ck_ai_fill_proposal_time CHECK (
        expires_at > created_at AND updated_at >= created_at
        AND (finished_at IS NULL OR finished_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- PROPOSE reservations intentionally have no proposal foreign key because the
-- attempt is inserted before the provider call and proposal row.
CREATE TABLE un_ai_fill_attempt (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    proposal_id BIGINT NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_fill_attempt_id (id),
    UNIQUE KEY uk_ai_fill_attempt_key (
        system_id, tenant_id, member_id, action, request_key),
    KEY idx_ai_fill_attempt_proposal (
        system_id, tenant_id, proposal_id, action, created_at),
    CONSTRAINT ck_ai_fill_attempt_identity CHECK (
        id > 0 AND member_id > 0 AND proposal_id > 0),
    CONSTRAINT ck_ai_fill_attempt_action CHECK (
        action IN ('PROPOSE','CONFIRM','REJECT')),
    CONSTRAINT ck_ai_fill_attempt_key CHECK (
        request_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND request_hash REGEXP '^[0-9a-f]{64}$'
        AND result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_ai_fill_attempt_status CHECK (
        (status = 'EXECUTING' AND finished_at IS NULL)
        OR (status IN ('SUCCEEDED','FAILED','REJECTED')
            AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_fill_event (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    proposal_id BIGINT NOT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    actor_member_id BIGINT NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_fill_event_id (id),
    KEY idx_ai_fill_event_proposal (
        system_id, tenant_id, proposal_id, revision, id),
    CONSTRAINT fk_ai_fill_event_proposal FOREIGN KEY (
        system_id, tenant_id, proposal_id)
        REFERENCES un_ai_fill_proposal (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_fill_event_identity CHECK (
        id > 0 AND proposal_id > 0 AND actor_member_id > 0),
    CONSTRAINT ck_ai_fill_event_type CHECK (
        event_type IN ('PROPOSED','CONFIRMING','REJECTING','SUCCEEDED',
                       'FAILED','REJECTED','EXPIRED')),
    CONSTRAINT ck_ai_fill_event_state CHECK (
        to_state IN ('CLARIFICATION_REQUIRED','PENDING','EXECUTING',
                     'SUCCEEDED','FAILED','REJECTED','EXPIRED')
        AND (from_state IS NULL OR from_state IN (
             'CLARIFICATION_REQUIRED','PENDING','EXECUTING',
             'SUCCEEDED','FAILED','REJECTED','EXPIRED'))),
    CONSTRAINT ck_ai_fill_event_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND event_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
