-- Requester withdrawal extends the existing optimistic Flow state machine.

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_status;

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_completion;

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_status CHECK (
        status IN ('PENDING', 'APPROVED', 'REJECTED', 'WITHDRAWN')
    );

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED', 'WITHDRAWN')
            AND state_version BETWEEN 1 AND 10
            AND completed_at IS NOT NULL
            AND completed_at >= started_at)
    );

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING')
        OR (event_sequence BETWEEN 2 AND 11 AND event_type = 'APPROVED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'APPROVED'))
        OR (event_sequence BETWEEN 2 AND 11 AND event_type = 'REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED')
        OR (event_sequence BETWEEN 2 AND 11 AND event_type = 'WITHDRAWN'
            AND from_status = 'PENDING' AND to_status = 'WITHDRAWN'
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
    );

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id
    ) AS SIGNED),
    'SYSTEM',
    system_row.id,
    system_row.id,
    'flow.instance.withdraw',
    'Withdraw requested flow instances',
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
      AND existing.permission_code = 'flow.instance.withdraw'
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
 AND permission_row.permission_code = 'flow.instance.withdraw'
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
