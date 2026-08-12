-- Tenant-scoped single-module data-source drafts and immutable publications.
-- Runtime consumers resolve only the active version snapshot; draft JSON remains
-- management-only and is guarded by draft_version plus the root CAS version.

CREATE TABLE un_module_data_source (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    data_source_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module_id BIGINT NOT NULL,
    data_source_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    draft_json JSON NOT NULL,
    draft_version BIGINT NOT NULL,
    active_version_id BIGINT NULL,
    active_version_no INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_data_source_code (
        system_id, tenant_id, data_source_code),
    UNIQUE KEY uk_module_data_source_identity (
        system_id, tenant_id, id, active_version_id, active_version_no),
    KEY idx_module_data_source_list (
        system_id, tenant_id, updated_at DESC, id DESC),
    KEY idx_module_data_source_module (
        system_id, tenant_id, module_id, updated_at DESC, id DESC),
    KEY idx_module_data_source_active (
        system_id, tenant_id, active_version_id),
    CONSTRAINT fk_module_data_source_tenant FOREIGN KEY (
        system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_data_source_module FOREIGN KEY (
        system_id, module_id)
        REFERENCES un_module_definition (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_data_source_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND module_id > 0),
    CONSTRAINT ck_module_data_source_code CHECK (
        data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_data_source_name CHECK (
        CHAR_LENGTH(TRIM(data_source_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_data_source_description CHECK (
        description IS NULL
        OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_module_data_source_draft CHECK (
        draft_version > 0
        AND JSON_TYPE(draft_json) = 'OBJECT'
        AND OCTET_LENGTH(draft_json) BETWEEN 2 AND 262144),
    CONSTRAINT ck_module_data_source_active CHECK (
        (active_version_id IS NULL AND active_version_no IS NULL)
        OR (active_version_id > 0 AND active_version_no > 0)),
    CONSTRAINT ck_module_data_source_state CHECK (
        updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_data_source_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    data_source_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    source_draft_version BIGINT NOT NULL,
    data_source_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module_id BIGINT NOT NULL,
    module_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version_id VARCHAR(200)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    data_source_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    snapshot_json JSON NOT NULL,
    snapshot_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_by_member_id BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, data_source_id, id),
    UNIQUE KEY uk_module_data_source_version_id (
        system_id, tenant_id, data_source_id, id, version_no),
    UNIQUE KEY uk_module_data_source_version_no (
        system_id, tenant_id, data_source_id, version_no),
    UNIQUE KEY uk_module_data_source_draft_publish (
        system_id, tenant_id, data_source_id, source_draft_version),
    KEY idx_module_data_source_version_list (
        system_id, tenant_id, data_source_id, version_no DESC),
    KEY idx_module_data_source_version_fingerprint (
        system_id, tenant_id, data_source_id,
        snapshot_fingerprint, version_no DESC),
    CONSTRAINT fk_module_data_source_version_root FOREIGN KEY (
        system_id, tenant_id, data_source_id)
        REFERENCES un_module_data_source (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_data_source_version_module FOREIGN KEY (
        system_id, module_id)
        REFERENCES un_module_definition (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_data_source_version_identity CHECK (
        id > 0 AND data_source_id > 0 AND version_no > 0
        AND source_draft_version > 0 AND module_id > 0
        AND published_by_member_id > 0),
    CONSTRAINT ck_module_data_source_version_code CHECK (
        data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,99}$'),
    CONSTRAINT ck_module_data_source_version_schema CHECK (
        CHAR_LENGTH(TRIM(schema_version_id)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_data_source_version_name CHECK (
        CHAR_LENGTH(TRIM(data_source_name)) BETWEEN 1 AND 200
        AND (description IS NULL
          OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000)),
    CONSTRAINT ck_module_data_source_version_snapshot CHECK (
        JSON_TYPE(snapshot_json) = 'OBJECT'
        AND OCTET_LENGTH(snapshot_json) BETWEEN 2 AND 262144
        AND snapshot_fingerprint REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_module_data_source
    ADD CONSTRAINT fk_module_data_source_active_version FOREIGN KEY (
        system_id, tenant_id, id, active_version_id, active_version_no)
        REFERENCES un_module_data_source_version (
            system_id, tenant_id, data_source_id, id, version_no)
        ON DELETE RESTRICT;

-- Existing systems need the same management action that PermissionCatalog
-- provisions for systems created after this migration. Active ROOT roles keep
-- their bootstrap administration contract, and cached grants are invalidated.
INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name,
    resource_type, status, created_at, created_by, updated_at, updated_by,
    version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id) AS SIGNED),
    'SYSTEM', system_row.id, system_row.id, 'module.config.manage',
    'Manage module configuration', 'ACTION', 'ACTIVE', UTC_TIMESTAMP(3),
    system_row.created_by, UTC_TIMESTAMP(3), system_row.created_by, 0
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
      AND existing.permission_code = 'module.config.manage'
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect,
    created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key, role_row.id, permission_row.id) AS SIGNED),
    'SYSTEM', role_row.scope_key, role_row.id, permission_row.id, 'ALLOW',
    UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type = 'SYSTEM'
 AND permission_row.scope_key = role_row.scope_key
 AND permission_row.permission_code = 'module.config.manage'
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
