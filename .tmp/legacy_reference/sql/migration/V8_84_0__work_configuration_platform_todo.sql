-- Cycle114 versioned Work configuration and platform Todo closure.

CREATE TABLE un_work_configuration (
    id BIGINT UNSIGNED NOT NULL,
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    rollback_from_revision BIGINT UNSIGNED NULL,
    created_by BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_by BIGINT UNSIGNED NULL,
    published_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status='PUBLISHED' THEN 1 ELSE NULL END
    ) STORED,
    PRIMARY KEY (system_id,tenant_id,id),
    UNIQUE KEY uk_work_configuration_id (id),
    UNIQUE KEY uk_work_configuration_revision (
        system_id,tenant_id,revision),
    UNIQUE KEY uk_work_configuration_active (
        system_id,tenant_id,active_marker),
    KEY idx_work_configuration_history (
        system_id,tenant_id,revision,status),
    CONSTRAINT ck_work_configuration_identity CHECK (
        id>0 AND system_id>0 AND tenant_id>0 AND revision>0
        AND created_by>0 AND version>0),
    CONSTRAINT ck_work_configuration_status CHECK (
        status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT ck_work_configuration_publish CHECK (
        (status='DRAFT' AND published_by IS NULL AND published_at IS NULL)
        OR (status IN ('PUBLISHED','RETIRED')
            AND published_by IS NOT NULL AND published_at IS NOT NULL
            AND published_at>=created_at)),
    CONSTRAINT ck_work_configuration_rollback CHECK (
        rollback_from_revision IS NULL OR rollback_from_revision>0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_work_runtime_field_value (
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    object_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    object_id BIGINT UNSIGNED NOT NULL,
    configuration_revision BIGINT UNSIGNED NOT NULL,
    value_json JSON NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    updated_by BIGINT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id,tenant_id,object_type,object_id),
    KEY idx_work_runtime_configuration (
        system_id,tenant_id,configuration_revision,object_type),
    CONSTRAINT fk_work_runtime_configuration FOREIGN KEY (
        system_id,tenant_id,configuration_revision)
        REFERENCES un_work_configuration (system_id,tenant_id,revision)
        ON DELETE RESTRICT,
    CONSTRAINT ck_work_runtime_identity CHECK (
        system_id>0 AND tenant_id>0 AND object_id>0
        AND configuration_revision>0 AND updated_by>0 AND version>0),
    CONSTRAINT ck_work_runtime_type CHECK (
        object_type IN ('PROJECT_TASK','ORDINARY_TASK','DAILY_REPORT'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_todo_action (
    account_id BIGINT UNSIGNED NOT NULL,
    idempotency_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    requested_action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_version BIGINT UNSIGNED NOT NULL,
    result_json JSON NOT NULL,
    completed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (account_id,idempotency_key),
    KEY idx_platform_todo_action_task (
        account_id,task_id,completed_at),
    CONSTRAINT ck_platform_todo_action_identity CHECK (
        account_id>0 AND task_id>0),
    CONSTRAINT ck_platform_todo_action_code CHECK (
        requested_action IN ('COMPLETE','REOPEN','CANCEL'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id,scope_type,scope_key,system_id,permission_code,name,
    resource_type,status,created_at,created_by,updated_at,updated_by,version)
SELECT
    CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id) AS SIGNED),
    'SYSTEM',system_row.id,system_row.id,'work.config.manage',
    'Manage versioned Work configuration','ACTION','ACTIVE',
    UTC_TIMESTAMP(3),system_row.created_by,UTC_TIMESTAMP(3),
    system_row.created_by,0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1 FROM un_plat_permission existing
     WHERE existing.scope_type='SYSTEM'
       AND existing.scope_key=system_row.id
       AND existing.permission_code='work.config.manage'
);

INSERT INTO un_plat_role_permission (
    id,scope_type,scope_key,role_id,permission_id,effect,created_at,created_by)
SELECT
    CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key,role_row.id) AS SIGNED),
    'SYSTEM',role_row.scope_key,role_row.id,permission_row.id,
    'ALLOW',UTC_TIMESTAMP(3),role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='SYSTEM'
 AND permission_row.scope_key=role_row.scope_key
 AND permission_row.permission_code='work.config.manage'
 AND permission_row.status='ACTIVE'
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id),0),0) min_id
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
