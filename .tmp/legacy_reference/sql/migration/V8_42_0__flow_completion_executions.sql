-- Immutable, ordered post-approval completion executions for external tasks
-- and governed webhooks. Legacy definitions keep a NULL completion plan.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN completion_steps JSON NULL AFTER decision_evidence_policies,
    ADD CONSTRAINT ck_flow_draft_completion_steps CHECK (
        completion_steps IS NULL
        OR (
            JSON_TYPE(completion_steps) = 'ARRAY'
            AND JSON_LENGTH(completion_steps) BETWEEN 1 AND 8
            AND OCTET_LENGTH(completion_steps) <= 16384
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN completion_steps JSON NULL AFTER decision_evidence_policies,
    ADD CONSTRAINT ck_flow_version_completion_steps CHECK (
        completion_steps IS NULL
        OR (
            JSON_TYPE(completion_steps) = 'ARRAY'
            AND JSON_LENGTH(completion_steps) BETWEEN 1 AND 8
            AND OCTET_LENGTH(completion_steps) <= 16384
        )
    );

ALTER TABLE un_flow_instance
    ADD COLUMN completion_phase VARCHAR(20)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'HUMAN_APPROVAL'
        AFTER status,
    ADD COLUMN active_completion_ordinal TINYINT UNSIGNED NULL
        AFTER completion_phase;

UPDATE un_flow_instance
SET completion_phase = 'COMPLETED'
WHERE status <> 'PENDING';

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_completion_phase CHECK (
        (
            status = 'PENDING'
            AND completion_phase IN ('HUMAN_APPROVAL', 'EXTERNAL_EXECUTION')
        )
        OR (
            status <> 'PENDING'
            AND completion_phase = 'COMPLETED'
        )
    ),
    ADD CONSTRAINT ck_flow_instance_completion_ordinal CHECK (
        (
            completion_phase = 'EXTERNAL_EXECUTION'
            AND active_completion_ordinal BETWEEN 0 AND 7
        )
        OR (
            completion_phase <> 'EXTERNAL_EXECUTION'
            AND active_completion_ordinal IS NULL
        )
    );

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'APPROVED'
            AND from_status = 'PENDING'
            AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'REJECTED'
            AND from_status = 'PENDING'
            AND to_status IN ('PENDING', 'REJECTED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'WITHDRAWN'
            AND from_status = 'PENDING' AND to_status = 'WITHDRAWN'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'TERMINATED'
            AND from_status = 'PENDING' AND to_status = 'TERMINATED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'TRANSFERRED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'ADD_SIGNED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0
            AND assignment_position IN ('BEFORE', 'AFTER')
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'RETURNED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'CLAIM_CANCELLED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'CLAIMED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(comment) <= 500)
        OR (event_sequence >= 2 AND event_type = 'SIGN_REMOVED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NOT NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type IN (
                'DEADLINE_REMINDER_SENT', 'DEADLINE_OVERDUE'
            )
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2
            AND event_type = 'DEADLINE_AUTO_APPROVED'
            AND from_status = 'PENDING'
            AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2
            AND event_type = 'DEADLINE_AUTO_REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2
            AND event_type = 'COMPLETION_COMPLETED'
            AND from_status = 'PENDING' AND to_status = 'APPROVED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND comment = '')
    );

CREATE TABLE un_flow_completion_execution (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    ordinal TINYINT UNSIGNED NOT NULL,
    step_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    step_name VARCHAR(80) NOT NULL,
    execution_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_json JSON NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    state_version INT UNSIGNED NOT NULL DEFAULT 0,
    available_at DATETIME(6) NULL,
    lease_owner VARCHAR(160) NULL,
    lease_token_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    terminal_at DATETIME(6) NULL,
    result_json JSON NULL,
    failure_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_message VARCHAR(500) NULL,
    failure_retryable BOOLEAN NULL,
    active_slot TINYINT UNSIGNED GENERATED ALWAYS AS (
        CASE
            WHEN status IN ('AVAILABLE', 'LEASED', 'RETRYING', 'FAILED')
                THEN 1
            ELSE NULL
        END
    ) STORED,
    PRIMARY KEY (system_id, tenant_id, execution_id),
    UNIQUE KEY uk_flow_completion_instance_ordinal (
        system_id, tenant_id, instance_id, ordinal
    ),
    UNIQUE KEY uk_flow_completion_one_active (
        system_id, tenant_id, instance_id, active_slot
    ),
    KEY idx_flow_completion_due (
        system_id, tenant_id, execution_type, status,
        available_at, execution_id
    ),
    KEY idx_flow_completion_lease_due (
        system_id, tenant_id, status, lease_expires_at,
        execution_type, execution_id
    ),
    CONSTRAINT fk_flow_completion_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_completion_version FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_completion_identity CHECK (
        execution_id > 0
        AND instance_id > 0
        AND definition_id > 0
        AND definition_version > 0
        AND ordinal BETWEEN 0 AND 7
    ),
    CONSTRAINT ck_flow_completion_step CHECK (
        step_code REGEXP '^[a-z][a-z0-9_]{0,63}$'
        AND CHAR_LENGTH(TRIM(step_name)) BETWEEN 1 AND 80
        AND execution_type IN ('EXTERNAL_TASK', 'WEBHOOK')
        AND JSON_TYPE(config_json) = 'OBJECT'
        AND OCTET_LENGTH(config_json) <= 4096
        AND JSON_TYPE(payload_json) = 'OBJECT'
        AND OCTET_LENGTH(payload_json) <= 65535
    ),
    CONSTRAINT ck_flow_completion_status CHECK (
        status IN (
            'WAITING', 'AVAILABLE', 'LEASED', 'RETRYING',
            'SUCCEEDED', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_flow_completion_lease CHECK (
        (
            status = 'LEASED'
            AND lease_owner IS NOT NULL
            AND CHAR_LENGTH(TRIM(lease_owner)) BETWEEN 1 AND 160
            AND lease_token_hash REGEXP '^[0-9a-f]{64}$'
            AND lease_expires_at IS NOT NULL
            AND started_at IS NOT NULL
            AND attempt_count > 0
        )
        OR (
            status <> 'LEASED'
            AND lease_owner IS NULL
            AND lease_token_hash IS NULL
            AND lease_expires_at IS NULL
        )
    ),
    CONSTRAINT ck_flow_completion_times CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (terminal_at IS NULL OR terminal_at >= created_at)
        AND (
            (status = 'WAITING' AND available_at IS NULL
                AND started_at IS NULL AND terminal_at IS NULL)
            OR (status IN ('AVAILABLE', 'RETRYING')
                AND available_at IS NOT NULL AND terminal_at IS NULL)
            OR (status = 'LEASED'
                AND available_at IS NOT NULL AND terminal_at IS NULL)
            OR (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
                AND terminal_at IS NOT NULL)
        )
    ),
    CONSTRAINT ck_flow_completion_result_failure CHECK (
        (
            status = 'SUCCEEDED'
            AND result_json IS NOT NULL
            AND JSON_TYPE(result_json) = 'OBJECT'
            AND OCTET_LENGTH(result_json) <= 8192
            AND failure_code IS NULL
            AND failure_message IS NULL
            AND failure_retryable IS NULL
        )
        OR (
            status = 'FAILED'
            AND result_json IS NULL
            AND failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
            AND failure_message IS NOT NULL
            AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500
            AND failure_retryable IS NOT NULL
        )
        OR (
            status NOT IN ('SUCCEEDED', 'FAILED')
            AND result_json IS NULL
            AND (
                (
                    failure_code IS NULL
                    AND failure_message IS NULL
                    AND failure_retryable IS NULL
                )
                OR (
                    status IN ('RETRYING', 'CANCELLED')
                    AND failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
                    AND failure_message IS NOT NULL
                    AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500
                    AND failure_retryable IS NOT NULL
                )
            )
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_completion_attempt (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    attempt_number INT UNSIGNED NOT NULL,
    event_sequence INT UNSIGNED NOT NULL,
    event_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_member_id BIGINT NULL,
    lease_owner VARCHAR(160) NULL,
    idempotency_key_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_json JSON NULL,
    failure_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_message VARCHAR(500) NULL,
    http_status SMALLINT UNSIGNED NULL,
    duration_ms BIGINT UNSIGNED NULL,
    response_sha256 CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, attempt_id),
    UNIQUE KEY uk_flow_completion_attempt_sequence (
        system_id, tenant_id, execution_id, event_sequence
    ),
    UNIQUE KEY uk_flow_completion_attempt_idempotency (
        system_id, tenant_id, execution_id, idempotency_key_hash
    ),
    KEY idx_flow_completion_attempt_execution (
        system_id, tenant_id, execution_id, occurred_at, attempt_id
    ),
    CONSTRAINT fk_flow_completion_attempt_execution FOREIGN KEY (
        system_id, tenant_id, execution_id
    ) REFERENCES un_flow_completion_execution (
        system_id, tenant_id, execution_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_completion_attempt_identity CHECK (
        attempt_id > 0
        AND execution_id > 0
        AND event_sequence > 0
        AND (actor_member_id IS NULL OR actor_member_id > 0)
    ),
    CONSTRAINT ck_flow_completion_attempt_event CHECK (
        event_type IN (
            'ACTIVATED', 'CLAIMED', 'LEASE_EXPIRED', 'RETRIED',
            'SUCCEEDED', 'FAILED', 'CANCELLED'
        )
    ),
    CONSTRAINT ck_flow_completion_attempt_values CHECK (
        (lease_owner IS NULL
            OR CHAR_LENGTH(TRIM(lease_owner)) BETWEEN 1 AND 160)
        AND (idempotency_key_hash IS NULL
            OR idempotency_key_hash REGEXP '^[0-9a-f]{64}$')
        AND (result_json IS NULL
            OR (
                JSON_TYPE(result_json) = 'OBJECT'
                AND OCTET_LENGTH(result_json) <= 8192
            ))
        AND (
            (
                failure_code IS NULL
                AND failure_message IS NULL
            )
            OR (
                failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
                AND failure_message IS NOT NULL
                AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500
            )
        )
        AND (http_status IS NULL OR http_status BETWEEN 100 AND 599)
        AND (duration_ms IS NULL OR duration_ms <= 86400000)
        AND (response_sha256 IS NULL
            OR response_sha256 REGEXP '^[0-9a-f]{64}$')
        AND (
            (
                http_status IS NULL
                AND duration_ms IS NULL
                AND response_sha256 IS NULL
                AND started_at IS NULL
                AND completed_at IS NULL
            )
            OR (
                event_type IN ('SUCCEEDED', 'FAILED', 'RETRIED')
                AND duration_ms IS NOT NULL
                AND started_at IS NOT NULL
                AND completed_at IS NOT NULL
                AND completed_at >= started_at
            )
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name,
    resource_type, status, created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id
    ) AS SIGNED),
    'SYSTEM',
    system_row.id,
    system_row.id,
    'flow.external-task.work',
    'Claim and complete flow external tasks',
    'ACTION',
    'ACTIVE',
    UTC_TIMESTAMP(3),
    system_row.created_by,
    UTC_TIMESTAMP(3),
    system_row.created_by,
    0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_permission existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
      AND existing.permission_code = 'flow.external-task.work'
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id,
    effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key, role_row.id, permission_row.id
    ) AS SIGNED),
    'SYSTEM',
    role_row.scope_key,
    role_row.id,
    permission_row.id,
    'ALLOW',
    UTC_TIMESTAMP(3),
    role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type = 'SYSTEM'
 AND permission_row.scope_key = role_row.scope_key
 AND permission_row.permission_code = 'flow.external-task.work'
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
      SELECT 1
      FROM un_plat_role_permission existing
      WHERE existing.role_id = role_row.id
        AND existing.permission_id = permission_row.id
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
