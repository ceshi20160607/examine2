-- Complete the existing Agent-created personal platform-task aggregate with
-- self-scoped native lifecycle facts and one ROOT-only management permission.

ALTER TABLE un_platform_task
    ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at,
    ADD COLUMN completed_at DATETIME(6) NULL AFTER updated_at,
    ADD COLUMN cancelled_at DATETIME(6) NULL AFTER completed_at,
    ADD COLUMN version BIGINT UNSIGNED NULL AFTER cancelled_at;

UPDATE un_platform_task
SET updated_at = created_at,
    version = 0
WHERE updated_at IS NULL OR version IS NULL;

ALTER TABLE un_platform_task
    MODIFY COLUMN updated_at DATETIME(6) NOT NULL
        DEFAULT CURRENT_TIMESTAMP(6),
    MODIFY COLUMN version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    DROP CHECK ck_platform_task_status,
    ADD CONSTRAINT ck_platform_task_status CHECK (
        status IN ('OPEN', 'COMPLETED', 'CANCELLED')),
    ADD CONSTRAINT ck_platform_task_lifecycle CHECK (
        updated_at >= created_at
        AND ((status = 'OPEN'
              AND completed_at IS NULL AND cancelled_at IS NULL)
          OR (status = 'COMPLETED'
              AND completed_at IS NOT NULL AND cancelled_at IS NULL
              AND completed_at BETWEEN created_at AND updated_at)
          OR (status = 'CANCELLED'
              AND completed_at IS NULL AND cancelled_at IS NOT NULL
              AND cancelled_at BETWEEN created_at AND updated_at)));

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type,
    status, created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - 1,
    'PLATFORM', 0, NULL, 'platform.task.manage',
    'Manage own platform tasks', 'ACTION', 'ACTIVE',
    UTC_TIMESTAMP(3), seed.actor_id, UTC_TIMESTAMP(3), seed.actor_id, 0
FROM (
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
        AND existing.permission_code = 'platform.task.manage'
  );

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect,
    created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.id) AS SIGNED),
    'PLATFORM', 0, role_row.id, permission_row.id, 'ALLOW',
    UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type = 'PLATFORM'
 AND permission_row.scope_key = 0
 AND permission_row.permission_code = 'platform.task.manage'
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
