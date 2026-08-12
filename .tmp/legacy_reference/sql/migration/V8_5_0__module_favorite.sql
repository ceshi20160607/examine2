-- Batch 11 persists member-owned runtime favorites and registers their permission.
-- target_record_key closes MySQL's nullable-unique-key gap for MODULE favorites.

CREATE TABLE un_module_favorite (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    target_type VARCHAR(16) NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    record_id BIGINT NULL,
    record_schema_version_id BIGINT NULL,
    record_module_snapshot_id BIGINT NULL,
    target_record_key BIGINT GENERATED ALWAYS AS (COALESCE(record_id, 0)) STORED,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END
    ) STORED,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_target (
        system_id, tenant_id, member_id, target_type,
        logical_module_id, target_record_key, active_marker
    ),
    KEY idx_favorite_member (
        system_id, tenant_id, member_id, updated_at, id
    ),
    CONSTRAINT fk_favorite_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_favorite_member FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_favorite_module FOREIGN KEY (
        system_id, module_schema_version_id, module_snapshot_id
    ) REFERENCES un_module_runtime_schema_module (
        system_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_favorite_record FOREIGN KEY (
        system_id, tenant_id, record_id,
        record_schema_version_id, record_module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id,
        schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_favorite_target_type CHECK (target_type IN ('MODULE', 'RECORD')),
    CONSTRAINT ck_favorite_target CHECK (
        (target_type = 'MODULE'
            AND record_id IS NULL
            AND record_schema_version_id IS NULL
            AND record_module_snapshot_id IS NULL)
        OR
        (target_type = 'RECORD'
            AND record_id IS NOT NULL AND record_id > 0
            AND record_schema_version_id IS NOT NULL
            AND record_module_snapshot_id IS NOT NULL)
    ),
    CONSTRAINT ck_favorite_version CHECK (version >= 0),
    CONSTRAINT ck_favorite_delete CHECK (
        (deleted_at IS NULL AND deleted_by IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    'runtime.favorite.manage',
    'Manage personal favorites',
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
      AND existing.permission_code = 'runtime.favorite.manage'
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key, role_row.id
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
 AND permission_row.permission_code = 'runtime.favorite.manage'
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

-- Existing access tokens must refresh their cached permission snapshot.
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
