-- V8.94 originally created the platform Work/Message aggregates. Some rolling
-- installations applied an earlier package before its permission seed was
-- present, leaving implemented routes unreachable. Repair the catalog and the
-- platform ROOT grants idempotently without rewriting migration history.

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type,
    status, created_at, created_by, updated_at, updated_by, version
)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
           ORDER BY definition.permission_code) AS SIGNED),
       'PLATFORM', 0, NULL, definition.permission_code, definition.permission_name,
       definition.resource_type, 'ACTIVE', UTC_TIMESTAMP(3), seed.actor_id,
       UTC_TIMESTAMP(3), seed.actor_id, 0
FROM (
    SELECT 'platform.work.read' permission_code, 'Read own platform work' permission_name, 'MENU' resource_type
    UNION ALL SELECT 'platform.work.manage', 'Manage own platform work', 'ACTION'
    UNION ALL SELECT 'platform.message.read', 'Read own platform messages', 'MENU'
    UNION ALL SELECT 'platform.message.manage', 'Manage own platform messages', 'ACTION'
) definition
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_permission) base
CROSS JOIN (SELECT MIN(id) actor_id FROM un_plat_account) seed
WHERE seed.actor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_permission existing
       WHERE existing.scope_type='PLATFORM' AND existing.scope_key=0
         AND existing.permission_code=definition.permission_code);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
           ORDER BY role_row.id, permission_row.id) AS SIGNED),
       'PLATFORM', 0, role_row.id, permission_row.id, 'ALLOW',
       UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='PLATFORM' AND permission_row.scope_key=0
 AND permission_row.permission_code IN (
     'platform.work.read','platform.work.manage',
     'platform.message.read','platform.message.manage')
 AND permission_row.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_role_permission) base
WHERE role_row.scope_type='PLATFORM' AND role_row.scope_key=0
  AND role_row.role_type='ROOT' AND role_row.status='ACTIVE'
  AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
       WHERE existing.role_id=role_row.id AND existing.permission_id=permission_row.id);

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='PLATFORM' AND scope_key=0;
