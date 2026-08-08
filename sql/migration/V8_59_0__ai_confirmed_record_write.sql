-- Explicitly confirmed AI record writes. Owner command material is persisted
-- only as authenticated ciphertext; previews and readbacks are permission-
-- projected display values supplied by the module-runtime owner.

CREATE TABLE un_ai_agent_confirmation (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version_id BIGINT NOT NULL,
    record_id BIGINT NULL,
    expected_record_version BIGINT UNSIGNED NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    preview_json JSON NOT NULL,
    confidence_json JSON NOT NULL,
    clarifications_json JSON NOT NULL,
    sealed_ciphertext MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sealed_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sealed_command_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed_by BIGINT NULL,
    result_json JSON NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    owner_trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_confirmation_id (id),
    UNIQUE KEY uk_ai_confirmation_turn (system_id, tenant_id, turn_id),
    KEY idx_ai_confirmation_member (
        system_id, tenant_id, member_id, state, expires_at, id),
    KEY idx_ai_confirmation_record (
        system_id, tenant_id, module_code, record_id, created_at),
    CONSTRAINT fk_ai_confirmation_session FOREIGN KEY (
        system_id, tenant_id, session_id)
        REFERENCES un_ai_agent_session (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_confirmation_turn FOREIGN KEY (
        system_id, tenant_id, turn_id)
        REFERENCES un_ai_agent_turn (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_confirmation_policy FOREIGN KEY (
        system_id, tenant_id, policy_version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_confirmation_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_confirmation_identity CHECK (
        id > 0 AND member_id > 0 AND session_id > 0 AND turn_id > 0
        AND policy_version_id > 0 AND provider_id > 0 AND provider_version >= 0
        AND authorization_epoch > 0 AND schema_version_id > 0 AND revision >= 0),
    CONSTRAINT ck_ai_confirmation_operation CHECK (
        operation IN ('RECORD_CREATE', 'RECORD_UPDATE')),
    CONSTRAINT ck_ai_confirmation_record CHECK (
        (operation = 'RECORD_CREATE' AND record_id IS NULL
            AND expected_record_version IS NULL)
        OR (operation = 'RECORD_UPDATE' AND record_id > 0
            AND expected_record_version IS NOT NULL)),
    CONSTRAINT ck_ai_confirmation_module CHECK (
        module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_ai_confirmation_json CHECK (
        JSON_TYPE(preview_json) = 'OBJECT'
        AND JSON_TYPE(confidence_json) = 'ARRAY'
        AND JSON_LENGTH(confidence_json) <= 128
        AND JSON_TYPE(clarifications_json) = 'ARRAY'
        AND JSON_LENGTH(clarifications_json) <= 20
        AND (result_json IS NULL OR JSON_TYPE(result_json) = 'OBJECT')),
    CONSTRAINT ck_ai_confirmation_sealed CHECK (
        CHAR_LENGTH(sealed_ciphertext) BETWEEN 1 AND 131072
        AND CHAR_LENGTH(sealed_key_version) BETWEEN 1 AND 64
        AND sealed_command_hash REGEXP '^[0-9a-f]{64}$'
        AND plan_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_confirmation_state CHECK (
        state IN ('PENDING', 'EXECUTING', 'SUCCEEDED', 'FAILED',
                  'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_ai_confirmation_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND ((state = 'PENDING' AND confirmed_by IS NULL
              AND result_json IS NULL AND finished_at IS NULL)
          OR (state = 'EXECUTING' AND confirmed_by > 0
              AND result_json IS NULL AND finished_at IS NULL)
          OR (state = 'SUCCEEDED' AND confirmed_by > 0
              AND result_json IS NOT NULL AND finished_at IS NOT NULL)
          OR (state IN ('FAILED', 'REJECTED', 'EXPIRED')
              AND result_json IS NULL AND finished_at IS NOT NULL))),
    CONSTRAINT ck_ai_confirmation_times CHECK (
        expires_at > created_at AND updated_at >= created_at
        AND (finished_at IS NULL OR finished_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_confirmation_attempt (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    confirmation_id BIGINT NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_confirmation_attempt_id (id),
    UNIQUE KEY uk_ai_confirmation_attempt_key (
        system_id, tenant_id, confirmation_id, request_key),
    CONSTRAINT fk_ai_confirmation_attempt_root FOREIGN KEY (
        system_id, tenant_id, confirmation_id)
        REFERENCES un_ai_agent_confirmation (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_confirmation_attempt_identity CHECK (
        id > 0 AND confirmation_id > 0),
    CONSTRAINT ck_ai_confirmation_attempt_key CHECK (
        request_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_ai_confirmation_attempt_hash CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
        AND (result_hash IS NULL OR result_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_confirmation_attempt_state CHECK (
        (status = 'EXECUTING' AND finished_at IS NULL AND result_hash IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND finished_at IS NOT NULL)),
    CONSTRAINT ck_ai_confirmation_attempt_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_confirmation_event (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    confirmation_id BIGINT NOT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_state VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    actor_member_id BIGINT NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_confirmation_event_id (id),
    KEY idx_ai_confirmation_event_root (
        system_id, tenant_id, confirmation_id, revision, id),
    CONSTRAINT fk_ai_confirmation_event_root FOREIGN KEY (
        system_id, tenant_id, confirmation_id)
        REFERENCES un_ai_agent_confirmation (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_confirmation_event_identity CHECK (
        id > 0 AND confirmation_id > 0 AND actor_member_id > 0),
    CONSTRAINT ck_ai_confirmation_event_type CHECK (
        event_type IN ('PROPOSED', 'CONFIRMING', 'SUCCEEDED', 'FAILED',
                       'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_ai_confirmation_event_state CHECK (
        to_state IN ('PENDING', 'EXECUTING', 'SUCCEEDED', 'FAILED',
                     'REJECTED', 'EXPIRED')
        AND (from_state IS NULL OR from_state IN (
             'PENDING', 'EXECUTING', 'SUCCEEDED', 'FAILED',
             'REJECTED', 'EXPIRED'))),
    CONSTRAINT ck_ai_confirmation_event_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND event_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
