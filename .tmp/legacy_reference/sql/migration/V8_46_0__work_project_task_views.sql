-- Tenant-scoped Work projects and shared task metadata. Existing tasks remain
-- standalone because all new task columns are nullable.

CREATE TABLE un_work_project (
    id BIGINT UNSIGNED NOT NULL,
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    creator_member_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_work_project_id (id),
    KEY idx_work_project_list (
        system_id, tenant_id, status, updated_at, id),
    KEY idx_work_project_creator (
        system_id, tenant_id, creator_member_id, updated_at, id),
    CONSTRAINT ck_work_project_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0
        AND creator_member_id > 0),
    CONSTRAINT ck_work_project_title CHECK (
        CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 200),
    CONSTRAINT ck_work_project_description CHECK (
        description IS NULL OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_work_project_status CHECK (
        status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_work_project_state CHECK (
        updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_work_project_member (
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    project_id BIGINT UNSIGNED NOT NULL,
    member_id BIGINT UNSIGNED NOT NULL,
    role VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    joined_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, project_id, member_id),
    KEY idx_work_project_member_access (
        system_id, tenant_id, member_id, status, project_id),
    KEY idx_work_project_member_owner (
        system_id, tenant_id, project_id, status, role, member_id),
    CONSTRAINT fk_work_project_member_project FOREIGN KEY (
        system_id, tenant_id, project_id)
        REFERENCES un_work_project (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_work_project_member_identity CHECK (
        system_id > 0 AND tenant_id > 0 AND project_id > 0 AND member_id > 0),
    CONSTRAINT ck_work_project_member_role CHECK (role IN ('OWNER', 'MEMBER')),
    CONSTRAINT ck_work_project_member_status CHECK (
        status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_work_project_member_state CHECK (
        updated_at >= joined_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_work_task
    ADD COLUMN project_id BIGINT UNSIGNED NULL AFTER assignee_member_id,
    ADD COLUMN description VARCHAR(2000) NULL AFTER title,
    ADD COLUMN due_at DATETIME(6) NULL AFTER status,
    ADD KEY idx_work_task_project_state (
        system_id, tenant_id, project_id, status, updated_at, id),
    ADD KEY idx_work_task_project_due (
        system_id, tenant_id, project_id, due_at, id),
    ADD KEY idx_work_task_due_window (
        system_id, tenant_id, due_at, status, updated_at, id),
    ADD CONSTRAINT fk_work_task_project FOREIGN KEY (
        system_id, tenant_id, project_id)
        REFERENCES un_work_project (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_work_task_description CHECK (
        description IS NULL
        OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000);

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name,
    resource_type, status, created_at, created_by, updated_at, updated_by,
    version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id, definition.permission_code) AS SIGNED),
    'SYSTEM', system_row.id, system_row.id,
    definition.permission_code, definition.permission_name,
    definition.resource_type, 'ACTIVE', UTC_TIMESTAMP(3),
    system_row.created_by, UTC_TIMESTAMP(3), system_row.created_by, 0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT 'work.project.access' AS permission_code,
           'Access work projects' AS permission_name,
           'MENU' AS resource_type
    UNION ALL SELECT 'work.project.create', 'Create work projects', 'ACTION'
    UNION ALL SELECT 'work.project.manage', 'Manage all work projects', 'DATA'
) definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1 FROM un_plat_permission existing
    WHERE existing.scope_type='SYSTEM'
      AND existing.scope_key=system_row.id
      AND existing.permission_code=definition.permission_code
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id,
    effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key,role_row.id,permission_row.id) AS SIGNED),
    'SYSTEM', role_row.scope_key, role_row.id, permission_row.id,
    'ALLOW', UTC_TIMESTAMP(3), role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='SYSTEM'
 AND permission_row.scope_key=role_row.scope_key
 AND permission_row.status='ACTIVE'
 AND permission_row.permission_code IN (
     'work.project.access','work.project.create','work.project.manage')
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_role_permission
) base
WHERE role_row.scope_type='SYSTEM'
  AND role_row.role_type='ROOT'
  AND role_row.status='ACTIVE'
  AND role_row.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM un_plat_role_permission existing
    WHERE existing.role_id=role_row.id
      AND existing.permission_id=permission_row.id
  );

UPDATE un_plat_authz_epoch
SET epoch=epoch+1,updated_at=UTC_TIMESTAMP(3),version=version+1
WHERE scope_type='SYSTEM';

UPDATE un_plat_system system_row
JOIN un_plat_authz_epoch epoch_row
  ON epoch_row.scope_type='SYSTEM'
 AND epoch_row.scope_key=system_row.id
SET system_row.permission_version=epoch_row.epoch,
    system_row.updated_at=UTC_TIMESTAMP(3),
    system_row.version=system_row.version+1;
