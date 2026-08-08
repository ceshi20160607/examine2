INSERT INTO un_plat_permission
    (id, scope_type, scope_key, system_id, permission_code, name, resource_type,
     status, created_at, created_by, updated_at, updated_by, version)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY s.id) AS SIGNED),
       'SYSTEM', s.id, s.id, 'system.audit.view', 'View system audit logs', 'ACTION',
       'ACTIVE', UTC_TIMESTAMP(3), seed.actor_id, UTC_TIMESTAMP(3), seed.actor_id, 0
FROM un_plat_system s
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_permission) base
CROSS JOIN (SELECT MIN(id) actor_id FROM un_plat_account) seed
WHERE s.status <> 'DELETED' AND seed.actor_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM un_plat_permission p
    WHERE p.scope_type='SYSTEM' AND p.scope_key=s.id
      AND p.permission_code='system.audit.view'
  );

INSERT INTO un_plat_role_permission
    (id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by)
SELECT CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY r.id) AS SIGNED),
       'SYSTEM', r.scope_key, r.id, p.id, 'ALLOW', UTC_TIMESTAMP(3), r.updated_by
FROM un_plat_role r
JOIN un_plat_permission p
  ON p.scope_type='SYSTEM' AND p.scope_key=r.scope_key
 AND p.permission_code='system.audit.view' AND p.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) min_id FROM un_plat_role_permission) base
WHERE r.scope_type='SYSTEM' AND r.role_type='ROOT'
  AND r.status='ACTIVE' AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM un_plat_role_permission rp
    WHERE rp.role_id=r.id AND rp.permission_id=p.id
  );

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='SYSTEM';

UPDATE un_plat_system
SET permission_version=permission_version+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE status <> 'DELETED';
