-- Tenant-scoped, read-only AI Agent foundation. Provider credentials are
-- external SecretRefs only; no plaintext secret or raw tool value is stored.

CREATE TABLE un_ai_provider (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    provider_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_name VARCHAR(160) NOT NULL,
    base_url VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    secret_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    timeout_seconds TINYINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_provider_id (id),
    UNIQUE KEY uk_ai_provider_code (system_id, tenant_id, provider_code),
    KEY idx_ai_provider_list (system_id, tenant_id, updated_at DESC, id DESC),
    CONSTRAINT fk_ai_provider_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_provider_identity CHECK (
        id > 0 AND created_by > 0 AND updated_by > 0 AND version >= 0),
    CONSTRAINT ck_ai_provider_code CHECK (
        provider_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_ai_provider_name CHECK (
        CHAR_LENGTH(TRIM(provider_name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_ai_provider_url CHECK (
        CHAR_LENGTH(base_url) BETWEEN 8 AND 1024
        AND base_url NOT LIKE '%@%'
        AND base_url NOT LIKE '%?%'
        AND base_url NOT LIKE '%#%'),
    CONSTRAINT ck_ai_provider_model CHECK (
        CHAR_LENGTH(TRIM(model_code)) BETWEEN 1 AND 128),
    CONSTRAINT ck_ai_provider_secret_ref CHECK (
        CHAR_LENGTH(secret_ref) BETWEEN 3 AND 512
        AND secret_ref = TRIM(secret_ref)
        AND secret_ref REGEXP '^[A-Za-z][A-Za-z0-9+.-]{1,31}://[^[:space:]]+$'),
    CONSTRAINT ck_ai_provider_limits CHECK (
        timeout_seconds BETWEEN 1 AND 30 AND enabled IN (0, 1)),
    CONSTRAINT ck_ai_provider_times CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_policy (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    draft_revision BIGINT UNSIGNED NOT NULL,
    draft_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    draft_json JSON NOT NULL,
    max_rows TINYINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL,
    redaction_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    draft_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_version_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_policy_id (id),
    UNIQUE KEY uk_ai_agent_policy_tenant (system_id, tenant_id),
    KEY idx_ai_agent_policy_provider (system_id, tenant_id, provider_id),
    CONSTRAINT fk_ai_agent_policy_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_policy_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_policy_identity CHECK (
        id > 0 AND draft_revision > 0 AND provider_id > 0
        AND updated_by > 0 AND provider_version >= 0),
    CONSTRAINT ck_ai_agent_policy_status CHECK (
        draft_status IN ('DRAFT', 'CHECKED', 'PUBLISHED')),
    CONSTRAINT ck_ai_agent_policy_json CHECK (
        JSON_TYPE(draft_json) = 'OBJECT'),
    CONSTRAINT ck_ai_agent_policy_limits CHECK (
        max_rows BETWEEN 1 AND 50 AND enabled IN (0, 1)),
    CONSTRAINT ck_ai_agent_policy_redaction CHECK (redaction_mode = 'STRICT'),
    CONSTRAINT ck_ai_agent_policy_prompt CHECK (
        prompt_version REGEXP '^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$'),
    CONSTRAINT ck_ai_agent_policy_hash CHECK (
        draft_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_agent_policy_active CHECK (
        active_version_id IS NULL OR active_version_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_policy_check (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    draft_revision BIGINT UNSIGNED NOT NULL,
    draft_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issues_json JSON NOT NULL,
    blocker_count SMALLINT UNSIGNED NOT NULL,
    checked_at DATETIME(6) NOT NULL,
    checked_by BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_policy_check_id (id),
    UNIQUE KEY uk_ai_agent_policy_check_revision (
        system_id, tenant_id, policy_id, draft_revision),
    CONSTRAINT fk_ai_agent_policy_check_root FOREIGN KEY (
        system_id, tenant_id, policy_id)
        REFERENCES un_ai_agent_policy (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_policy_check_identity CHECK (
        id > 0 AND policy_id > 0 AND draft_revision > 0 AND checked_by > 0),
    CONSTRAINT ck_ai_agent_policy_check_status CHECK (
        (status = 'PASSED' AND blocker_count = 0)
        OR (status = 'FAILED' AND blocker_count > 0)),
    CONSTRAINT ck_ai_agent_policy_check_issues CHECK (
        JSON_TYPE(issues_json) = 'ARRAY'
        AND JSON_LENGTH(issues_json) <= 256),
    CONSTRAINT ck_ai_agent_policy_check_hash CHECK (
        draft_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_policy_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    max_rows TINYINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL,
    redaction_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_at DATETIME(6) NOT NULL,
    published_by BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_policy_version_id (id),
    UNIQUE KEY uk_ai_agent_policy_version_no (
        system_id, tenant_id, policy_id, version_no),
    KEY idx_ai_agent_policy_version_provider (
        system_id, tenant_id, provider_id, provider_version),
    CONSTRAINT fk_ai_agent_policy_version_root FOREIGN KEY (
        system_id, tenant_id, policy_id)
        REFERENCES un_ai_agent_policy (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_policy_version_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_policy_version_identity CHECK (
        id > 0 AND policy_id > 0 AND version_no > 0 AND provider_id > 0
        AND provider_version >= 0 AND published_by > 0),
    CONSTRAINT ck_ai_agent_policy_version_snapshot CHECK (
        JSON_TYPE(snapshot_json) = 'OBJECT'),
    CONSTRAINT ck_ai_agent_policy_version_limits CHECK (
        max_rows BETWEEN 1 AND 50 AND enabled IN (0, 1)),
    CONSTRAINT ck_ai_agent_policy_version_redaction CHECK (
        redaction_mode = 'STRICT'),
    CONSTRAINT ck_ai_agent_policy_version_hash CHECK (
        snapshot_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_ai_agent_policy
    ADD CONSTRAINT fk_ai_agent_policy_active_version FOREIGN KEY (
        system_id, tenant_id, active_version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT;

CREATE TABLE un_ai_agent_policy_publish (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_policy_publish_id (id),
    UNIQUE KEY uk_ai_agent_policy_publish_request (
        system_id, tenant_id, policy_id, request_key),
    CONSTRAINT fk_ai_agent_policy_publish_root FOREIGN KEY (
        system_id, tenant_id, policy_id)
        REFERENCES un_ai_agent_policy (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_policy_publish_version FOREIGN KEY (
        system_id, tenant_id, version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_policy_publish_identity CHECK (
        id > 0 AND policy_id > 0 AND version_id > 0),
    CONSTRAINT ck_ai_agent_policy_publish_key CHECK (
        request_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_ai_agent_policy_publish_hash CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_session (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title_summary VARCHAR(200) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_session_id (id),
    KEY idx_ai_agent_session_member (
        system_id, tenant_id, member_id, updated_at DESC, id DESC),
    CONSTRAINT fk_ai_agent_session_member FOREIGN KEY (
        system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_session_policy FOREIGN KEY (
        system_id, tenant_id, policy_version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_session_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_session_identity CHECK (
        id > 0 AND member_id > 0 AND policy_version_id > 0
        AND provider_id > 0 AND provider_version >= 0
        AND authorization_epoch > 0),
    CONSTRAINT ck_ai_agent_session_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_ai_agent_session_title CHECK (
        CHAR_LENGTH(TRIM(title_summary)) BETWEEN 1 AND 200),
    CONSTRAINT ck_ai_agent_session_times CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_turn (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_summary VARCHAR(500) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    response_summary VARCHAR(500) NULL,
    response_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    returned_rows SMALLINT UNSIGNED NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    retryable BOOLEAN NOT NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_turn_id (id),
    KEY idx_ai_agent_turn_session (
        system_id, tenant_id, session_id, created_at, id),
    KEY idx_ai_agent_turn_trace (trace_id, created_at),
    CONSTRAINT fk_ai_agent_turn_session FOREIGN KEY (
        system_id, tenant_id, session_id)
        REFERENCES un_ai_agent_session (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_turn_policy FOREIGN KEY (
        system_id, tenant_id, policy_version_id)
        REFERENCES un_ai_agent_policy_version (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_turn_provider FOREIGN KEY (
        system_id, tenant_id, provider_id)
        REFERENCES un_ai_provider (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_turn_identity CHECK (
        id > 0 AND session_id > 0 AND policy_version_id > 0
        AND provider_id > 0 AND provider_version >= 0
        AND authorization_epoch > 0),
    CONSTRAINT ck_ai_agent_turn_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYABLE')),
    CONSTRAINT ck_ai_agent_turn_hashes CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
        AND (plan_hash IS NULL OR plan_hash REGEXP '^[0-9a-f]{64}$')
        AND (response_hash IS NULL OR response_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_agent_turn_result CHECK (
        returned_rows <= 50 AND retryable IN (0, 1)
        AND result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_ai_agent_turn_state CHECK (
        (status = 'RUNNING' AND finished_at IS NULL)
        OR (status <> 'RUNNING' AND finished_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_message (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    redacted_summary VARCHAR(500) NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    character_count INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_message_id (id),
    KEY idx_ai_agent_message_session (
        system_id, tenant_id, session_id, created_at, id),
    CONSTRAINT fk_ai_agent_message_session FOREIGN KEY (
        system_id, tenant_id, session_id)
        REFERENCES un_ai_agent_session (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_ai_agent_message_turn FOREIGN KEY (
        system_id, tenant_id, turn_id)
        REFERENCES un_ai_agent_turn (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_message_identity CHECK (
        id > 0 AND session_id > 0 AND (turn_id IS NULL OR turn_id > 0)),
    CONSTRAINT ck_ai_agent_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_ai_agent_message_hash CHECK (
        content_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_ai_agent_message_size CHECK (character_count <= 128000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_tool_call (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    tool_name VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    response_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_count SMALLINT UNSIGNED NOT NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_tool_call_id (id),
    KEY idx_ai_agent_tool_call_turn (system_id, tenant_id, turn_id, id),
    CONSTRAINT fk_ai_agent_tool_call_turn FOREIGN KEY (
        system_id, tenant_id, turn_id)
        REFERENCES un_ai_agent_turn (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_tool_call_identity CHECK (id > 0 AND turn_id > 0),
    CONSTRAINT ck_ai_agent_tool_call_name CHECK (tool_name = 'RECORD_QUERY'),
    CONSTRAINT ck_ai_agent_tool_call_status CHECK (
        status IN ('SUCCEEDED', 'FAILED', 'RETRYABLE')),
    CONSTRAINT ck_ai_agent_tool_call_hash CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
        AND (response_hash IS NULL OR response_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_ai_agent_tool_call_result CHECK (
        result_count <= 50
        AND result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_ai_agent_usage (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    provider_calls TINYINT UNSIGNED NOT NULL,
    prompt_tokens INT UNSIGNED NOT NULL,
    completion_tokens INT UNSIGNED NOT NULL,
    total_tokens INT UNSIGNED NOT NULL,
    provider_latency_ms BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_ai_agent_usage_id (id),
    UNIQUE KEY uk_ai_agent_usage_turn (system_id, tenant_id, turn_id),
    CONSTRAINT fk_ai_agent_usage_turn FOREIGN KEY (
        system_id, tenant_id, turn_id)
        REFERENCES un_ai_agent_turn (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_ai_agent_usage_identity CHECK (id > 0 AND turn_id > 0),
    CONSTRAINT ck_ai_agent_usage_counts CHECK (
        provider_calls BETWEEN 0 AND 8
        AND total_tokens = prompt_tokens + completion_tokens)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type,
    status, created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id, definition.permission_code) AS SIGNED),
    'SYSTEM', system_row.id, system_row.id,
    definition.permission_code, definition.permission_name,
    definition.resource_type, 'ACTIVE', UTC_TIMESTAMP(3),
    system_row.created_by, UTC_TIMESTAMP(3), system_row.created_by, 0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT 'ai.policy.manage' AS permission_code,
           'Manage AI Agent policy' AS permission_name,
           'ACTION' AS resource_type
    UNION ALL SELECT 'ai.agent.use', 'Use AI Agent', 'MENU'
) definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1 FROM un_plat_permission existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
      AND existing.permission_code = definition.permission_code
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect,
    created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key, role_row.id, permission_row.id) AS SIGNED),
    'SYSTEM', role_row.scope_key, role_row.id, permission_row.id,
    'ALLOW', UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type = 'SYSTEM'
 AND permission_row.scope_key = role_row.scope_key
 AND permission_row.permission_code IN ('ai.policy.manage', 'ai.agent.use')
 AND permission_row.status = 'ACTIVE'
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_role_permission
) base
WHERE role_row.scope_type = 'SYSTEM'
  AND role_row.role_type = 'ROOT'
  AND role_row.status = 'ACTIVE'
  AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
      WHERE existing.role_id = role_row.id
        AND existing.permission_id = permission_row.id
  );

INSERT INTO un_plat_authz_epoch (
    id, scope_type, scope_key, system_id, epoch,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    system_row.id, 'SYSTEM', system_row.id, system_row.id,
    GREATEST(system_row.permission_version, 1), UTC_TIMESTAMP(3),
    system_row.created_by, UTC_TIMESTAMP(3), system_row.updated_by, 0
FROM un_plat_system system_row
WHERE NOT EXISTS (
    SELECT 1 FROM un_plat_authz_epoch existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
);

UPDATE un_plat_authz_epoch
SET epoch = epoch + 1,
    updated_at = UTC_TIMESTAMP(3),
    version = version + 1
WHERE scope_type = 'SYSTEM';

UPDATE un_plat_system system_row
JOIN un_plat_authz_epoch epoch_row
  ON epoch_row.scope_type = 'SYSTEM'
 AND epoch_row.scope_key = system_row.id
SET system_row.permission_version = epoch_row.epoch,
    system_row.updated_at = UTC_TIMESTAMP(3),
    system_row.version = system_row.version + 1;
