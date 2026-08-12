-- Platform-context AI Agent foundation. This schema is deliberately isolated
-- from tenant/system AI tables: the only business projection it may persist is
-- the current account's redacted authorized-system directory.

CREATE TABLE un_platform_ai_provider (
    id BIGINT NOT NULL,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(160) NOT NULL,
    base_url VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    secret_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    timeout_seconds TINYINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_provider_code (code),
    KEY idx_platform_ai_provider_list (updated_at DESC, id DESC),
    CONSTRAINT fk_platform_ai_provider_creator FOREIGN KEY (created_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_provider_updater FOREIGN KEY (updated_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_provider_identity CHECK (
        id > 0 AND created_by > 0 AND updated_by > 0 AND revision >= 0),
    CONSTRAINT ck_platform_ai_provider_code CHECK (
        code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_platform_ai_provider_name CHECK (
        CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_platform_ai_provider_url CHECK (
        CHAR_LENGTH(base_url) BETWEEN 8 AND 1024
        AND base_url = TRIM(base_url)
        AND base_url NOT LIKE '%@%'
        AND base_url NOT LIKE '%?%'
        AND base_url NOT LIKE '%#%'),
    CONSTRAINT ck_platform_ai_provider_model CHECK (
        CHAR_LENGTH(TRIM(model_code)) BETWEEN 1 AND 128),
    CONSTRAINT ck_platform_ai_provider_secret_ref CHECK (
        CHAR_LENGTH(secret_ref) BETWEEN 3 AND 512
        AND secret_ref = TRIM(secret_ref)
        AND secret_ref REGEXP '^[A-Za-z][A-Za-z0-9+.-]{1,31}://[^[:space:]]+$'),
    CONSTRAINT ck_platform_ai_provider_limits CHECK (
        timeout_seconds BETWEEN 1 AND 30 AND enabled IN (0, 1)),
    CONSTRAINT ck_platform_ai_provider_times CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_policy (
    id BIGINT NOT NULL,
    singleton_key TINYINT AS (1) STORED,
    revision BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    draft_json JSON NOT NULL,
    draft_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_version_id BIGINT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_policy_singleton (singleton_key),
    KEY idx_platform_ai_policy_provider (provider_id),
    CONSTRAINT fk_platform_ai_policy_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_policy_updater FOREIGN KEY (updated_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_policy_identity CHECK (
        id > 0 AND revision > 0 AND provider_id > 0
        AND provider_version >= 0 AND updated_by > 0),
    CONSTRAINT ck_platform_ai_policy_status CHECK (
        status IN ('DRAFT', 'CHECKED', 'PUBLISHED')),
    CONSTRAINT ck_platform_ai_policy_json CHECK (
        JSON_TYPE(draft_json) = 'OBJECT'),
    CONSTRAINT ck_platform_ai_policy_hash CHECK (
        draft_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ai_policy_active CHECK (
        active_version_id IS NULL OR active_version_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_policy_check (
    id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    draft_revision BIGINT UNSIGNED NOT NULL,
    draft_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    issues_json JSON NOT NULL,
    checked_at DATETIME(6) NOT NULL,
    checked_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_policy_check_revision (policy_id, draft_revision),
    CONSTRAINT fk_platform_ai_policy_check_root FOREIGN KEY (policy_id)
        REFERENCES un_platform_ai_policy (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_policy_check_actor FOREIGN KEY (checked_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_policy_check_identity CHECK (
        id > 0 AND policy_id > 0 AND draft_revision > 0 AND checked_by > 0),
    CONSTRAINT ck_platform_ai_policy_check_hash CHECK (
        draft_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ai_policy_check_issues CHECK (
        JSON_TYPE(issues_json) = 'ARRAY' AND JSON_LENGTH(issues_json) <= 64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_policy_version (
    id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    model_code VARCHAR(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    max_systems SMALLINT UNSIGNED NOT NULL,
    daily_request_quota INT UNSIGNED NOT NULL,
    daily_token_quota BIGINT UNSIGNED NOT NULL,
    max_concurrency TINYINT UNSIGNED NOT NULL,
    strict_redaction BOOLEAN NOT NULL,
    data_residency VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    prompt_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    enabled BOOLEAN NOT NULL,
    published_at DATETIME(6) NOT NULL,
    published_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_policy_version_no (policy_id, version_no),
    UNIQUE KEY uk_platform_ai_policy_version_root (policy_id, id),
    KEY idx_platform_ai_policy_version_provider (provider_id, provider_version),
    CONSTRAINT fk_platform_ai_policy_version_root FOREIGN KEY (policy_id)
        REFERENCES un_platform_ai_policy (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_policy_version_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_policy_version_publisher FOREIGN KEY (published_by)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_policy_version_identity CHECK (
        id > 0 AND policy_id > 0 AND version_no > 0 AND provider_id > 0
        AND provider_version >= 0 AND published_by > 0),
    CONSTRAINT ck_platform_ai_policy_version_snapshot CHECK (
        JSON_TYPE(snapshot_json) = 'OBJECT'),
    CONSTRAINT ck_platform_ai_policy_version_limits CHECK (
        max_systems BETWEEN 1 AND 100
        AND daily_request_quota BETWEEN 1 AND 10000
        AND daily_token_quota BETWEEN 10000 AND 10000000
        AND max_concurrency BETWEEN 1 AND 16),
    CONSTRAINT ck_platform_ai_policy_version_redaction CHECK (
        strict_redaction = 1
        AND data_residency = 'PLATFORM_METADATA_ONLY'),
    CONSTRAINT ck_platform_ai_policy_version_prompt CHECK (
        prompt_version REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,63}$'),
    CONSTRAINT ck_platform_ai_policy_version_hash CHECK (
        snapshot_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ai_policy_version_enabled CHECK (enabled IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_platform_ai_policy
    ADD CONSTRAINT fk_platform_ai_policy_active_version FOREIGN KEY (
        id, active_version_id) REFERENCES un_platform_ai_policy_version (
        policy_id, id)
        ON DELETE RESTRICT;

CREATE TABLE un_platform_ai_policy_publish_replay (
    id BIGINT NOT NULL,
    policy_id BIGINT NOT NULL,
    request_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_policy_publish_request (policy_id, request_key),
    CONSTRAINT fk_platform_ai_policy_publish_root FOREIGN KEY (policy_id)
        REFERENCES un_platform_ai_policy (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_policy_publish_version FOREIGN KEY (
        policy_id, version_id) REFERENCES un_platform_ai_policy_version (
        policy_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_policy_publish_identity CHECK (
        id > 0 AND policy_id > 0 AND version_id > 0),
    CONSTRAINT ck_platform_ai_policy_publish_key CHECK (
        request_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_platform_ai_policy_publish_hash CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_session (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    title_summary VARCHAR(200) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_session_account (account_id, id),
    KEY idx_platform_ai_session_list (account_id, updated_at DESC, id DESC),
    CONSTRAINT fk_platform_ai_session_account FOREIGN KEY (account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_session_identity CHECK (id > 0 AND account_id > 0),
    CONSTRAINT ck_platform_ai_session_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_session_title CHECK (
        CHAR_LENGTH(TRIM(title_summary)) BETWEEN 1 AND 200),
    CONSTRAINT ck_platform_ai_session_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_platform_ai_session_times CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_turn (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    provider_version BIGINT UNSIGNED NOT NULL,
    authorization_epoch BIGINT UNSIGNED NOT NULL,
    operation VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_summary VARCHAR(200) NOT NULL,
    request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    response_summary VARCHAR(200) NULL,
    response_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    returned_systems SMALLINT UNSIGNED NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    retryable BOOLEAN NOT NULL,
    reserved_tokens INT UNSIGNED NOT NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_turn_account (account_id, id),
    UNIQUE KEY uk_platform_ai_turn_session_identity (
        account_id, session_id, id),
    KEY idx_platform_ai_turn_session (account_id, session_id, created_at, id),
    KEY idx_platform_ai_turn_trace (trace_id, created_at),
    CONSTRAINT fk_platform_ai_turn_session FOREIGN KEY (account_id, session_id)
        REFERENCES un_platform_ai_session (account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_turn_policy FOREIGN KEY (policy_version_id)
        REFERENCES un_platform_ai_policy_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_turn_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_turn_identity CHECK (
        id > 0 AND account_id > 0 AND session_id > 0
        AND policy_version_id > 0 AND provider_id > 0
        AND provider_version >= 0 AND authorization_epoch > 0),
    CONSTRAINT ck_platform_ai_turn_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_turn_operation CHECK (
        operation IN (
            'UNRESOLVED', 'AUTHORIZED_SYSTEMS_QUERY',
            'SYSTEM_SWITCH_GUIDANCE')),
    CONSTRAINT ck_platform_ai_turn_status CHECK (
        status IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'RETRYABLE')),
    CONSTRAINT ck_platform_ai_turn_hashes CHECK (
        request_hash REGEXP '^[0-9a-f]{64}$'
        AND (plan_hash IS NULL OR plan_hash REGEXP '^[0-9a-f]{64}$')
        AND (response_hash IS NULL OR response_hash REGEXP '^[0-9a-f]{64}$')),
    CONSTRAINT ck_platform_ai_turn_result CHECK (
        returned_systems <= 100 AND retryable IN (0, 1)
        AND result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_platform_ai_turn_correlation CHECK (
        request_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND trace_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_platform_ai_turn_state CHECK (
        (status = 'RUNNING' AND finished_at IS NULL AND retryable = 0)
        OR (status = 'RETRYABLE' AND finished_at IS NOT NULL AND retryable = 1)
        OR (status IN ('SUCCEEDED', 'FAILED')
            AND finished_at IS NOT NULL AND retryable = 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_message (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    redacted_summary VARCHAR(200) NOT NULL,
    content_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    content_length INT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_platform_ai_message_session (account_id, session_id, created_at, id),
    CONSTRAINT fk_platform_ai_message_session FOREIGN KEY (account_id, session_id)
        REFERENCES un_platform_ai_session (account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_message_turn FOREIGN KEY (
        account_id, session_id, turn_id) REFERENCES un_platform_ai_turn (
        account_id, session_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_message_identity CHECK (
        id > 0 AND account_id > 0 AND session_id > 0
        AND turn_id > 0),
    CONSTRAINT ck_platform_ai_message_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_message_role CHECK (role IN ('USER', 'ASSISTANT')),
    CONSTRAINT ck_platform_ai_message_hash CHECK (
        content_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ai_message_size CHECK (content_length <= 128000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_usage (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    provider_id BIGINT NOT NULL,
    call_count TINYINT UNSIGNED NOT NULL,
    prompt_tokens INT UNSIGNED NOT NULL,
    completion_tokens INT UNSIGNED NOT NULL,
    total_tokens INT UNSIGNED NOT NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_usage_turn (account_id, turn_id),
    CONSTRAINT fk_platform_ai_usage_turn FOREIGN KEY (account_id, turn_id)
        REFERENCES un_platform_ai_turn (account_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_usage_policy FOREIGN KEY (policy_version_id)
        REFERENCES un_platform_ai_policy_version (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_usage_provider FOREIGN KEY (provider_id)
        REFERENCES un_platform_ai_provider (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_usage_identity CHECK (
        id > 0 AND account_id > 0 AND turn_id > 0
        AND policy_version_id > 0 AND provider_id > 0),
    CONSTRAINT ck_platform_ai_usage_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_usage_counts CHECK (
        call_count BETWEEN 0 AND 2
        AND total_tokens = prompt_tokens + completion_tokens)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_evidence (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    turn_id BIGINT NOT NULL,
    evidence_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    projection_json JSON NOT NULL,
    projection_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    returned_systems SMALLINT UNSIGNED NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_platform_ai_evidence_turn (account_id, turn_id),
    CONSTRAINT fk_platform_ai_evidence_turn FOREIGN KEY (account_id, turn_id)
        REFERENCES un_platform_ai_turn (account_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_evidence_identity CHECK (
        id > 0 AND account_id > 0 AND turn_id > 0),
    CONSTRAINT ck_platform_ai_evidence_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_evidence_type CHECK (
        evidence_type IN ('AUTHORIZED_SYSTEMS', 'SWITCH_GUIDANCE')),
    CONSTRAINT ck_platform_ai_evidence_json CHECK (
        JSON_TYPE(projection_json) IN ('ARRAY', 'OBJECT')
        AND JSON_LENGTH(projection_json) <= 100),
    CONSTRAINT ck_platform_ai_evidence_hashes CHECK (
        plan_hash REGEXP '^[0-9a-f]{64}$'
        AND projection_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_platform_ai_evidence_result CHECK (
        returned_systems <= 100
        AND result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_audit_event (
    id BIGINT NOT NULL,
    scope VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    account_id BIGINT NOT NULL,
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_platform_ai_audit_account (account_id, created_at, id),
    KEY idx_platform_ai_audit_trace (trace_id, created_at),
    CONSTRAINT fk_platform_ai_audit_account FOREIGN KEY (account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_audit_identity CHECK (
        id > 0 AND account_id > 0 AND aggregate_id > 0),
    CONSTRAINT ck_platform_ai_audit_scope CHECK (scope = 'PLATFORM'),
    CONSTRAINT ck_platform_ai_audit_aggregate CHECK (
        aggregate_type REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,31}$'),
    CONSTRAINT ck_platform_ai_audit_event CHECK (
        event_type REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
        AND result_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'),
    CONSTRAINT ck_platform_ai_audit_correlation CHECK (
        request_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'
        AND trace_id REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_platform_ai_audit_hash CHECK (
        event_hash REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_ai_quota_bucket (
    account_id BIGINT NOT NULL,
    policy_version_id BIGINT NOT NULL,
    period_start DATETIME(6) NOT NULL,
    request_count INT UNSIGNED NOT NULL,
    used_tokens BIGINT UNSIGNED NOT NULL,
    reserved_tokens BIGINT UNSIGNED NOT NULL,
    running_count INT UNSIGNED NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id, policy_version_id, period_start),
    CONSTRAINT fk_platform_ai_quota_account FOREIGN KEY (account_id)
        REFERENCES un_plat_account (id) ON DELETE RESTRICT,
    CONSTRAINT fk_platform_ai_quota_policy FOREIGN KEY (policy_version_id)
        REFERENCES un_platform_ai_policy_version (id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_ai_quota_identity CHECK (
        account_id > 0 AND policy_version_id > 0),
    CONSTRAINT ck_platform_ai_quota_counts CHECK (
        request_count <= 10000
        AND used_tokens <= 10000000
        AND reserved_tokens <= 10000000
        AND running_count <= 16)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- Make the two platform capabilities first-class catalog permissions and grant
-- them to active built-in PLATFORM ROOT roles. Runtime also requires the
-- pre-existing platform.runtime.access permission.
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
    SELECT 'platform.ai.agent.use' AS permission_code,
           'Use platform AI Agent' AS permission_name,
           'MENU' AS resource_type
    UNION ALL
    SELECT 'platform.ai.policy.manage',
           'Manage platform AI Agent policy', 'ACTION'
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
     'platform.ai.agent.use', 'platform.ai.policy.manage')
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

INSERT INTO un_plat_authz_epoch (
    id, scope_type, scope_key, system_id, epoch,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    seed.actor_id, 'PLATFORM', 0, NULL, 1,
    UTC_TIMESTAMP(3), seed.actor_id, UTC_TIMESTAMP(3), seed.actor_id, 0
FROM (
    SELECT MIN(id) AS actor_id FROM un_plat_account
) seed
WHERE seed.actor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_authz_epoch existing
      WHERE existing.scope_type = 'PLATFORM' AND existing.scope_key = 0
  );

UPDATE un_plat_authz_epoch
SET epoch = epoch + 1,
    updated_at = UTC_TIMESTAMP(3),
    version = version + 1
WHERE scope_type = 'PLATFORM' AND scope_key = 0;
