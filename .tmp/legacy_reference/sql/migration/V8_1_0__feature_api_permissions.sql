-- Registers the HTTP feature permissions for existing systems.
-- New systems receive the same definitions from PermissionCatalog.

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id, definition.permission_code
    ) AS SIGNED),
    'SYSTEM',
    system_row.id,
    system_row.id,
    definition.permission_code,
    definition.permission_name,
    definition.resource_type,
    'ACTIVE',
    UTC_TIMESTAMP(3),
    system_row.created_by,
    UTC_TIMESTAMP(3),
    system_row.created_by,
    0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT 'flow.definition.manage' AS permission_code, 'Manage flow definitions' AS permission_name, 'ACTION' AS resource_type
    UNION ALL SELECT 'flow.instance.start', 'Start flow instances', 'ACTION'
    UNION ALL SELECT 'flow.instance.decide', 'Decide flow tasks', 'ACTION'
    UNION ALL SELECT 'flow.instance.read', 'Read flow instances', 'DATA'
    UNION ALL SELECT 'work.task.access', 'Access work tasks', 'MENU'
    UNION ALL SELECT 'work.task.create', 'Create work tasks', 'ACTION'
    UNION ALL SELECT 'work.task.manage', 'Manage all work tasks', 'DATA'
    UNION ALL SELECT 'event.message.access', 'Access message inbox', 'MENU'
    UNION ALL SELECT 'file.create', 'Upload files', 'ACTION'
    UNION ALL SELECT 'file.read', 'Read files', 'DATA'
    UNION ALL SELECT 'file.reference', 'Reference files', 'ACTION'
    UNION ALL SELECT 'file.manage', 'Manage all files', 'DATA'
) definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_permission existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
      AND existing.permission_code = definition.permission_code
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
 AND permission_row.status = 'ACTIVE'
 AND (
       permission_row.permission_code LIKE 'flow.%'
    OR permission_row.permission_code LIKE 'work.task.%'
    OR permission_row.permission_code = 'event.message.access'
    OR permission_row.permission_code LIKE 'file.%'
 )
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

-- Existing access tokens are invalidated so their cached permission snapshots
-- cannot omit the newly registered grants.
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
