-- Runtime assignment snapshot and append-only transfer/add-sign history facts.

ALTER TABLE un_flow_instance
    ADD COLUMN approver_ids_json JSON NULL AFTER approver_id;

UPDATE un_flow_instance instance_row
SET instance_row.approver_ids_json = (
    SELECT CAST(CONCAT(
        '[',
        GROUP_CONCAT(step_row.approver_id ORDER BY step_row.step_no SEPARATOR ','),
        ']'
    ) AS JSON)
    FROM un_flow_definition_version_step step_row
    WHERE step_row.system_id = instance_row.system_id
      AND step_row.tenant_id = instance_row.tenant_id
      AND step_row.definition_id = instance_row.definition_id
      AND step_row.version_no = instance_row.definition_version
);

ALTER TABLE un_flow_instance
    MODIFY COLUMN approver_ids_json JSON NOT NULL;

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_approver_snapshot CHECK (
        JSON_TYPE(approver_ids_json) = 'ARRAY'
        AND JSON_LENGTH(approver_ids_json) BETWEEN 1 AND 10
        AND JSON_CONTAINS(approver_ids_json, CAST(approver_id AS JSON), '$') = 1
    );

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_state_version;

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_completion;

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_state_version CHECK (
        state_version BETWEEN 0 AND 2147483647
    );

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED', 'WITHDRAWN', 'TERMINATED')
            AND state_version BETWEEN 1 AND 2147483647
            AND completed_at IS NOT NULL
            AND completed_at >= started_at)
    );

ALTER TABLE un_flow_history_event
    ADD COLUMN target_member_id BIGINT NULL AFTER occurred_at,
    ADD COLUMN assignment_position VARCHAR(8)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER target_member_id;

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_sequence;

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_sequence CHECK (
        event_sequence BETWEEN 1 AND 2147483647
    );

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL)
        OR (event_sequence >= 2 AND event_type = 'APPROVED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL)
        OR (event_sequence >= 2 AND event_type = 'REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED'
            AND target_member_id IS NULL AND assignment_position IS NULL)
        OR (event_sequence >= 2 AND event_type = 'WITHDRAWN'
            AND from_status = 'PENDING' AND to_status = 'WITHDRAWN'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'TERMINATED'
            AND from_status = 'PENDING' AND to_status = 'TERMINATED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'TRANSFERRED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'ADD_SIGNED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IN ('BEFORE', 'AFTER')
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
    );

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
    SELECT 'flow.instance.transfer' AS permission_code, 'Transfer current flow approval task' AS name
    UNION ALL
    SELECT 'flow.instance.add-sign', 'Add a reviewer around the current flow step'
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
     'flow.instance.transfer',
     'flow.instance.add-sign'
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
