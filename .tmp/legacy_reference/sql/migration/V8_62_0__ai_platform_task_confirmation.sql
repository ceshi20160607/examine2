-- Confirmation-gated platform Agent task creation. Platform task ownership and
-- AI proposal state remain account scoped and structurally exclude every
-- system-business identity or payload column.

CREATE TABLE un_platform_task (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    due_at DATETIME(6) NULL,
    priority VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_task_account_request (account_id, idempotency_key),
    KEY idx_platform_task_account_list (account_id, created_at DESC, id DESC),
    KEY idx_platform_task_trace (trace_id, created_at, id),
    CONSTRAINT fk_platform_task_account FOREIGN KEY (account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_task_creator FOREIGN KEY (created_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_task_identity CHECK (
        id > 0 AND account_id > 0 AND created_by > 0
        AND created_by = account_id AND authorization_epoch > 0),
    CONSTRAINT ck_platform_task_title CHECK (
        CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_platform_task_description CHECK (
        description IS NULL
        OR (CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000)),
    CONSTRAINT ck_platform_task_due CHECK (
        due_at IS NULL OR due_at > created_at),
    CONSTRAINT ck_platform_task_priority CHECK (
        priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    CONSTRAINT ck_platform_task_status CHECK (status = 'OPEN'),
    CONSTRAINT ck_platform_task_source CHECK (source = 'AGENT'),
    CONSTRAINT ck_platform_task_hash CHECK (
        payload_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_task_correlation CHECK (
        idempotency_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:/+=-]{0,127}$'
        AND request_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:/+=-]{0,127}$'
        AND trace_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:/+=-]{0,127}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_task_proposal (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    title_summary VARCHAR(200) NULL,
    description_summary VARCHAR(200) NULL,
    due_at DATETIME(6) NULL,
    priority VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    confidence DECIMAL(5,4) NOT NULL,
    clarification_summary VARCHAR(200) NULL,
    sealed_ciphertext MEDIUMTEXT CHARACTER SET ascii COLLATE ascii_bin NULL,
    sealed_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    sealed_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(6) NOT NULL,
    confirmed_by BIGINT NULL,
    task_id BIGINT NULL,
    task_title_summary VARCHAR(200) NULL,
    task_description_summary VARCHAR(200) NULL,
    task_due_at DATETIME(6) NULL,
    task_priority VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    task_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    task_source VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    task_created_at DATETIME(6) NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    owner_trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_task_proposal_account (account_id, id),
    UNIQUE KEY uk_platform_ai_task_proposal_turn (account_id, turn_id),
    KEY idx_platform_ai_task_proposal_session (
        account_id, session_id, created_at, id),
    KEY idx_platform_ai_task_proposal_state (
        account_id, state, expires_at, id),
    KEY idx_platform_ai_task_proposal_policy (policy_version_id),
    KEY idx_platform_ai_task_proposal_provider (provider_id),
    KEY idx_platform_ai_task_proposal_task (task_id),
    CONSTRAINT fk_platform_ai_task_proposal_session FOREIGN KEY (
        account_id, session_id) REFERENCES un_platform_ai_session (
        account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_proposal_turn FOREIGN KEY (
        account_id, session_id, turn_id) REFERENCES un_platform_ai_turn (
        account_id, session_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_proposal_policy FOREIGN KEY (
        policy_version_id) REFERENCES un_platform_ai_policy_version (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_proposal_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_proposal_confirmer FOREIGN KEY (confirmed_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_proposal_result FOREIGN KEY (task_id)
        REFERENCES un_platform_task (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_task_proposal_identity CHECK (
        id > 0 AND account_id > 0 AND session_id > 0 AND turn_id > 0
        AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND authorization_epoch > 0
        AND revision >= 0),
    CONSTRAINT ck_platform_ai_task_proposal_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_task_proposal_prompt CHECK (
        prompt_version REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$'),
    CONSTRAINT ck_platform_ai_task_proposal_plan CHECK (
        plan_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ai_task_proposal_state CHECK (
        state IN ('CLARIFICATION_REQUIRED', 'PENDING', 'EXECUTING',
                  'SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_platform_ai_task_proposal_preview CHECK (
        confidence BETWEEN 0.0000 AND 1.0000
        AND ((state = 'CLARIFICATION_REQUIRED'
              AND title_summary IS NULL AND description_summary IS NULL
              AND due_at IS NULL AND priority IS NULL
              AND CHAR_LENGTH(TRIM(clarification_summary)) BETWEEN 1 AND 200)
          OR (state <> 'CLARIFICATION_REQUIRED'
              AND CHAR_LENGTH(TRIM(title_summary)) BETWEEN 1 AND 200
              AND (description_summary IS NULL
                   OR CHAR_LENGTH(TRIM(description_summary)) BETWEEN 1 AND 200)
              AND priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
              AND clarification_summary IS NULL))),
    CONSTRAINT ck_platform_ai_task_proposal_sealed CHECK (
        (state = 'CLARIFICATION_REQUIRED'
         AND sealed_ciphertext IS NULL
         AND sealed_key_version IS NULL AND sealed_hash IS NULL)
        OR (state <> 'CLARIFICATION_REQUIRED'
            AND CHAR_LENGTH(sealed_ciphertext) BETWEEN 1 AND 131072
            AND CHAR_LENGTH(sealed_key_version) BETWEEN 1 AND 64
            AND sealed_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_platform_ai_task_proposal_result CHECK (
        (state = 'SUCCEEDED'
         AND confirmed_by = account_id AND task_id > 0
         AND CHAR_LENGTH(TRIM(task_title_summary)) BETWEEN 1 AND 200
         AND task_priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')
         AND task_status = 'OPEN' AND task_source = 'AGENT'
         AND task_created_at IS NOT NULL)
        OR (state <> 'SUCCEEDED'
            AND task_id IS NULL AND task_title_summary IS NULL
            AND task_description_summary IS NULL AND task_due_at IS NULL
            AND task_priority IS NULL AND task_status IS NULL
            AND task_source IS NULL AND task_created_at IS NULL)),
    CONSTRAINT ck_platform_ai_task_proposal_result_code CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_platform_ai_task_proposal_owner_correlation CHECK (
        (owner_request_id IS NULL AND owner_trace_id IS NULL)
        OR (owner_request_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
            AND owner_trace_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$')),
    CONSTRAINT ck_platform_ai_task_proposal_times CHECK (
        expires_at > created_at AND updated_at >= created_at
        AND (finished_at IS NULL OR finished_at >= created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_task_proposal_attempt (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    proposal_id BIGINT NOT NULL,
    action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_task_attempt_account (account_id, id),
    UNIQUE KEY uk_platform_ai_task_attempt_request (
        account_id, proposal_id, action, request_key),
    KEY idx_platform_ai_task_attempt_proposal (
        account_id, proposal_id, created_at, id),
    CONSTRAINT fk_platform_ai_task_attempt_session FOREIGN KEY (
        account_id, session_id) REFERENCES un_platform_ai_session (
        account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_attempt_turn FOREIGN KEY (
        account_id, session_id, turn_id) REFERENCES un_platform_ai_turn (
        account_id, session_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_attempt_policy FOREIGN KEY (
        policy_version_id) REFERENCES un_platform_ai_policy_version (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_attempt_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_attempt_proposal FOREIGN KEY (
        account_id, proposal_id) REFERENCES un_platform_ai_task_proposal (
        account_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_task_attempt_identity CHECK (
        id > 0 AND account_id > 0 AND session_id > 0 AND turn_id > 0
        AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND proposal_id > 0),
    CONSTRAINT ck_platform_ai_task_attempt_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_task_attempt_action CHECK (
        action IN ('CONFIRM', 'REJECT')),
    CONSTRAINT ck_platform_ai_task_attempt_key CHECK (
        request_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_platform_ai_task_attempt_hash CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
        AND (result_hash IS NULL OR result_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_platform_ai_task_attempt_state CHECK (
        (status = 'EXECUTING' AND finished_at IS NULL AND result_hash IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND finished_at IS NOT NULL)),
    CONSTRAINT ck_platform_ai_task_attempt_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_task_proposal_event (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
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
    actor_account_id BIGINT NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_platform_ai_task_event_proposal (
        account_id, proposal_id, revision, id),
    KEY idx_platform_ai_task_event_attempt (account_id, attempt_id),
    CONSTRAINT fk_platform_ai_task_event_session FOREIGN KEY (
        account_id, session_id) REFERENCES un_platform_ai_session (
        account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_event_turn FOREIGN KEY (
        account_id, session_id, turn_id) REFERENCES un_platform_ai_turn (
        account_id, session_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_event_policy FOREIGN KEY (
        policy_version_id) REFERENCES un_platform_ai_policy_version (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_event_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_event_proposal FOREIGN KEY (
        account_id, proposal_id) REFERENCES un_platform_ai_task_proposal (
        account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_event_attempt FOREIGN KEY (
        account_id, attempt_id) REFERENCES un_platform_ai_task_proposal_attempt (
        account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_task_event_actor FOREIGN KEY (actor_account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_task_event_identity CHECK (
        id > 0 AND account_id > 0 AND session_id > 0 AND turn_id > 0
        AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND proposal_id > 0
        AND (attempt_id IS NULL OR attempt_id > 0)
        AND actor_account_id = account_id AND revision >= 0),
    CONSTRAINT ck_platform_ai_task_event_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_task_event_type CHECK (
        event_type IN ('PROPOSED', 'CLARIFICATION_REQUIRED', 'CONFIRMING',
                       'SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_platform_ai_task_event_state CHECK (
        to_state IN ('CLARIFICATION_REQUIRED', 'PENDING', 'EXECUTING',
                     'SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED')
        AND (from_state IS NULL OR from_state IN (
             'CLARIFICATION_REQUIRED', 'PENDING', 'EXECUTING',
             'SUCCEEDED', 'FAILED', 'REJECTED', 'EXPIRED'))),
    CONSTRAINT ck_platform_ai_task_event_correlation CHECK (
        request_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND trace_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_platform_ai_task_event_result CHECK (
        result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND event_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_platform_ai_turn
    DROP CHECK ck_platform_ai_turn_operation,
    ADD CONSTRAINT ck_platform_ai_turn_operation CHECK (
        operation IN (
            'UNRESOLVED', 'AUTHORIZED_SYSTEMS_QUERY',
            'SYSTEM_SWITCH_GUIDANCE', 'PLATFORM_TASK_DRAFT'));

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type,
    status, created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY definition.permission_code) AS SIGNED),
    'PLATFORM', 0, NULL, definition.permission_code, definition.permission_name,
    definition.resource_type, 'ACTIVE', UTC_TIMESTAMP(3), seed.actor_id,
    UTC_TIMESTAMP(3), seed.actor_id, 0
FROM (
    SELECT 'platform.task.read' AS permission_code,
           'Read own platform tasks' AS permission_name,
           'MENU' AS resource_type
    UNION ALL
    SELECT 'platform.task.create',
           'Create own platform tasks', 'ACTION'
) definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
CROSS JOIN (
    SELECT MIN(id) AS actor_id FROM un_plat_account
) seed
WHERE seed.actor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_permission existing
      WHERE existing.scope_type = 'PLATFORM'
        AND existing.scope_key = 0
        AND existing.permission_code = definition.permission_code
  );

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect,
    created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.id, permission_row.id) AS SIGNED),
    'PLATFORM', 0, role_row.id, permission_row.id, 'ALLOW',
    UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type = 'PLATFORM'
 AND permission_row.scope_key = 0
 AND permission_row.permission_code IN (
     'platform.task.read', 'platform.task.create')
 AND permission_row.status = 'ACTIVE'
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_role_permission
) base
WHERE role_row.scope_type = 'PLATFORM'
  AND role_row.scope_key = 0
  AND role_row.role_type = 'ROOT'
  AND role_row.status = 'ACTIVE'
  AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
      WHERE existing.role_id = role_row.id
        AND existing.permission_id = permission_row.id
  );

UPDATE un_plat_authz_epoch
SET epoch = epoch + 1,
    updated_at = UTC_TIMESTAMP(3),
    version = version + 1
WHERE scope_type = 'PLATFORM' AND scope_key = 0;
