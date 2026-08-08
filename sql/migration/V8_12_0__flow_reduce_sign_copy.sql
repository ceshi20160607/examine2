-- Future-step reduce-sign facts and immutable Flow copy recipients.

ALTER TABLE un_flow_history_event
    ADD COLUMN target_step_index INT UNSIGNED NULL AFTER assignment_position;

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'APPROVED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED'
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
            AND target_member_id > 0 AND assignment_position IN ('BEFORE', 'AFTER')
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
    );

CREATE TABLE un_flow_copy_recipient (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    copy_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, copy_id),
    UNIQUE KEY uk_flow_copy_instance_recipient (
        system_id, tenant_id, instance_id, recipient_id
    ),
    KEY idx_flow_copy_page (
        system_id, tenant_id, instance_id, created_at, copy_id
    ),
    CONSTRAINT fk_flow_copy_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_copy_identity CHECK (
        copy_id > 0 AND actor_id > 0 AND recipient_id > 0
        AND actor_id <> recipient_id
    ),
    CONSTRAINT ck_flow_copy_message CHECK (
        CHAR_LENGTH(message) <= 500
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id, permission_definition.permission_code
    ) AS SIGNED),
    'SYSTEM',
    system_row.id,
    system_row.id,
    permission_definition.permission_code,
    permission_definition.name,
    'ACTION',
    'ACTIVE',
    UTC_TIMESTAMP(3),
    system_row.created_by,
    UTC_TIMESTAMP(3),
    system_row.created_by,
    0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT 'flow.instance.reduce-sign' AS permission_code,
           'Remove a future Flow approval step' AS name
    UNION ALL
    SELECT 'flow.instance.copy', 'Copy an active member on a Flow instance'
) permission_definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_permission existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
      AND existing.permission_code = permission_definition.permission_code
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
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
 AND permission_row.permission_code IN (
     'flow.instance.reduce-sign',
     'flow.instance.copy'
 )
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
