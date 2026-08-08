-- Confirmation-gated generation of one unpublished Flow definition, report
-- definition or disabled print-template draft. AI rows contain redacted
-- projections and encrypted owner commands; domain owners keep exact replay.

CREATE TABLE un_ai_generated_draft_proposal (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    operation VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    preview_json JSON NULL,
    confidence DECIMAL(6,5) NOT NULL,
    clarification_summary VARCHAR(500) NULL,
    command_ciphertext MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NULL,
    command_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    command_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(6) NOT NULL,
    acted_by BIGINT NULL,
    result_json JSON NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_generated_draft_proposal_id (id),
    UNIQUE KEY uk_ai_generated_draft_proposal_turn (
        system_id, tenant_id, turn_id),
    KEY idx_ai_generated_draft_proposal_owner (
        system_id, tenant_id, member_id, session_id, id),
    KEY idx_ai_generated_draft_proposal_state (
        system_id, tenant_id, state, expires_at, id),
    CONSTRAINT fk_ai_generated_draft_proposal_session FOREIGN KEY (
        system_id, tenant_id, session_id)
        REFERENCES un_ai_agent_session (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_generated_draft_proposal_turn FOREIGN KEY (
        system_id, tenant_id, turn_id)
        REFERENCES un_ai_agent_turn (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_generated_draft_proposal_policy FOREIGN KEY (
        system_id, tenant_id, policy_version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_generated_draft_proposal_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_generated_draft_proposal_identity CHECK (
        id > 0 AND account_id > 0 AND member_id > 0
        AND session_id > 0 AND turn_id > 0 AND policy_version_id > 0
        AND provider_id > 0 AND provider_version >= 0
        AND authorization_epoch > 0 AND revision >= 0),
    CONSTRAINT ck_ai_generated_draft_proposal_operation CHECK (
        operation IN ('FLOW_DEFINITION_DRAFT','CONFIG_REPORT_DRAFT',
                      'CONFIG_PRINT_TEMPLATE_DRAFT')),
    CONSTRAINT ck_ai_generated_draft_proposal_hash CHECK (
        plan_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_generated_draft_proposal_json CHECK (
        (preview_json IS NULL OR JSON_TYPE(preview_json) = 'OBJECT')
        AND (result_json IS NULL OR JSON_TYPE(result_json) = 'OBJECT')),
    CONSTRAINT ck_ai_generated_draft_proposal_confidence CHECK (
        confidence BETWEEN 0.00000 AND 1.00000),
    CONSTRAINT ck_ai_generated_draft_proposal_payload CHECK (
        (state = 'CLARIFICATION_REQUIRED'
            AND preview_json IS NULL AND clarification_summary IS NOT NULL
            AND command_ciphertext IS NULL AND command_key_version IS NULL
            AND command_hash IS NULL)
        OR (state <> 'CLARIFICATION_REQUIRED'
            AND preview_json IS NOT NULL AND clarification_summary IS NULL
            AND CHAR_LENGTH(command_ciphertext) BETWEEN 1 AND 262144
            AND CHAR_LENGTH(command_key_version) BETWEEN 1 AND 64
            AND command_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_generated_draft_proposal_state CHECK (
        state IN ('CLARIFICATION_REQUIRED','PENDING','EXECUTING',
                  'SUCCEEDED','FAILED','REJECTED','EXPIRED','STALE',
                  'PERMISSION_DENIED')),
    CONSTRAINT ck_ai_generated_draft_proposal_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND ((state = 'CLARIFICATION_REQUIRED'
              AND acted_by IS NULL AND result_json IS NULL
              AND finished_at IS NULL)
          OR (state = 'PENDING' AND acted_by IS NULL
              AND result_json IS NULL AND finished_at IS NULL)
          OR (state = 'EXECUTING' AND acted_by > 0
              AND result_json IS NULL AND finished_at IS NULL)
          OR (state = 'SUCCEEDED' AND acted_by > 0
              AND result_json IS NOT NULL AND finished_at IS NOT NULL)
          OR (state IN ('FAILED','REJECTED','STALE','PERMISSION_DENIED')
              AND acted_by > 0 AND result_json IS NULL
              AND finished_at IS NOT NULL)
          OR (state = 'EXPIRED' AND (acted_by IS NULL OR acted_by > 0)
              AND result_json IS NULL AND finished_at IS NOT NULL))),
    CONSTRAINT ck_ai_generated_draft_proposal_time CHECK (
        expires_at > created_at AND updated_at >= created_at
        AND (finished_at IS NULL OR finished_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_generated_draft_attempt (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    proposal_id BIGINT NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_generated_draft_attempt_id (id),
    UNIQUE KEY uk_ai_generated_draft_attempt_key (
        system_id, tenant_id, proposal_id, action, request_key),
    KEY idx_ai_generated_draft_attempt_proposal (
        system_id, tenant_id, proposal_id, id),
    CONSTRAINT fk_ai_generated_draft_attempt_root FOREIGN KEY (
        system_id, tenant_id, proposal_id)
        REFERENCES un_ai_generated_draft_proposal (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_generated_draft_attempt_identity CHECK (
        id > 0 AND account_id > 0 AND member_id > 0 AND session_id > 0
        AND turn_id > 0 AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND proposal_id > 0),
    CONSTRAINT ck_ai_generated_draft_attempt_action CHECK (
        action = 'CONFIRM'),
    CONSTRAINT ck_ai_generated_draft_attempt_key CHECK (
        request_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_ai_generated_draft_attempt_hash CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
        AND (result_hash IS NULL OR result_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_generated_draft_attempt_state CHECK (
        (status = 'EXECUTING' AND finished_at IS NULL
            AND result_hash IS NULL)
        OR (status IN ('SUCCEEDED','FAILED','EXPIRED','STALE',
                       'PERMISSION_DENIED')
            AND finished_at IS NOT NULL AND result_hash IS NOT NULL)),
    CONSTRAINT ck_ai_generated_draft_attempt_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_generated_draft_event (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    proposal_id BIGINT NOT NULL,
    attempt_id BIGINT NULL,
    event_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    from_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    actor_member_id BIGINT NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_generated_draft_event_id (id),
    KEY idx_ai_generated_draft_event_proposal (
        system_id, tenant_id, proposal_id, id),
    CONSTRAINT fk_ai_generated_draft_event_root FOREIGN KEY (
        system_id, tenant_id, proposal_id)
        REFERENCES un_ai_generated_draft_proposal (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_generated_draft_event_attempt FOREIGN KEY (
        system_id, tenant_id, attempt_id)
        REFERENCES un_ai_generated_draft_attempt (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_generated_draft_event_identity CHECK (
        id > 0 AND account_id > 0 AND member_id > 0 AND session_id > 0
        AND turn_id > 0 AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND proposal_id > 0
        AND (attempt_id IS NULL OR attempt_id > 0) AND actor_member_id > 0
        AND revision >= 0),
    CONSTRAINT ck_ai_generated_draft_event_type CHECK (
        event_type IN ('CLARIFICATION_REQUIRED','PROPOSED','CONFIRMING',
                       'SUCCEEDED','FAILED','REJECTED','EXPIRED','STALE',
                       'PERMISSION_DENIED')),
    CONSTRAINT ck_ai_generated_draft_event_state CHECK (
        to_state IN ('CLARIFICATION_REQUIRED','PENDING','EXECUTING',
                     'SUCCEEDED','FAILED','REJECTED','EXPIRED','STALE',
                     'PERMISSION_DENIED')
        AND (from_state IS NULL OR from_state IN (
             'CLARIFICATION_REQUIRED','PENDING','EXECUTING',
             'SUCCEEDED','FAILED','REJECTED','EXPIRED','STALE',
             'PERMISSION_DENIED'))),
    CONSTRAINT ck_ai_generated_draft_event_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND event_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_ai_definition_draft_execution (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    proposal_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    turn_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    prepare_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prepare_trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execute_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execute_trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_json MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_flow_ai_definition_execution_id (id),
    UNIQUE KEY uk_flow_ai_definition_execution_proposal (
        system_id, tenant_id, member_id, proposal_id),
    UNIQUE KEY uk_flow_ai_definition_execution_key (
        system_id, tenant_id, member_id, idempotency_key),
    CONSTRAINT fk_flow_ai_definition_execution_member FOREIGN KEY (
        system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_flow_ai_definition_execution_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND member_id > 0
        AND account_id > 0 AND authorization_epoch > 0
        AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0),
    CONSTRAINT ck_flow_ai_definition_execution_operation CHECK (
        operation = 'FLOW_DEFINITION_DRAFT'),
    CONSTRAINT ck_flow_ai_definition_execution_tokens CHECK (
        proposal_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND session_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND turn_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND idempotency_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_flow_ai_definition_execution_hash CHECK (
        payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_flow_ai_definition_execution_result CHECK (
        ((result_json IS NULL AND completed_at IS NULL)
          OR (result_json IS NOT NULL AND completed_at IS NOT NULL))
        AND (result_json IS NULL OR JSON_VALID(result_json))),
    CONSTRAINT ck_flow_ai_definition_execution_time CHECK (
        expires_at > created_at
        AND (completed_at IS NULL OR completed_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_ai_generated_draft_execution (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    proposal_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    turn_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    prepare_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prepare_trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execute_request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    execute_trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_json MEDIUMTEXT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_ai_generated_execution_id (id),
    UNIQUE KEY uk_module_ai_generated_execution_proposal (
        system_id, tenant_id, member_id, proposal_id),
    UNIQUE KEY uk_module_ai_generated_execution_key (
        system_id, tenant_id, member_id, idempotency_key),
    CONSTRAINT fk_module_ai_generated_execution_member FOREIGN KEY (
        system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_module_ai_generated_execution_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND member_id > 0
        AND account_id > 0 AND authorization_epoch > 0
        AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0),
    CONSTRAINT ck_module_ai_generated_execution_operation CHECK (
        operation IN ('CONFIG_REPORT_DRAFT','CONFIG_PRINT_TEMPLATE_DRAFT')),
    CONSTRAINT ck_module_ai_generated_execution_tokens CHECK (
        proposal_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND session_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND turn_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND idempotency_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_module_ai_generated_execution_hash CHECK (
        payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_module_ai_generated_execution_result CHECK (
        ((result_json IS NULL AND completed_at IS NULL)
          OR (result_json IS NOT NULL AND completed_at IS NOT NULL))
        AND (result_json IS NULL OR JSON_VALID(result_json))),
    CONSTRAINT ck_module_ai_generated_execution_time CHECK (
        expires_at > created_at
        AND (completed_at IS NULL OR completed_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
