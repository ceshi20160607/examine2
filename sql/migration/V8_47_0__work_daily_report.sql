-- One durable daily report per active tenant member and business date.

CREATE TABLE un_work_daily_report (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    author_member_id BIGINT NOT NULL,
    work_date DATE NOT NULL,
    completed_work VARCHAR(4000) NOT NULL,
    planned_work VARCHAR(4000) NOT NULL,
    blockers VARCHAR(4000) NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    submitted_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_work_daily_report_id (id),
    UNIQUE KEY uk_work_daily_report_author_date (
        system_id, tenant_id, author_member_id, work_date),
    KEY idx_work_daily_report_list (
        system_id, tenant_id, work_date, updated_at, id),
    KEY idx_work_daily_report_member_list (
        system_id, tenant_id, author_member_id,
        work_date, status, updated_at, id),
    KEY idx_work_daily_report_status_list (
        system_id, tenant_id, status, work_date, updated_at, id),
    CONSTRAINT fk_work_daily_report_author FOREIGN KEY (
        system_id, author_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (
            system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_work_daily_report_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0
        AND author_member_id > 0),
    CONSTRAINT ck_work_daily_report_completed CHECK (
        CHAR_LENGTH(TRIM(completed_work)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_work_daily_report_planned CHECK (
        CHAR_LENGTH(TRIM(planned_work)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_work_daily_report_blockers CHECK (
        blockers IS NULL
        OR CHAR_LENGTH(TRIM(blockers)) BETWEEN 1 AND 4000),
    CONSTRAINT ck_work_daily_report_status CHECK (
        status IN ('DRAFT', 'SUBMITTED')),
    CONSTRAINT ck_work_daily_report_state CHECK (
        updated_at >= created_at AND version > 0
        AND ((status = 'DRAFT' AND submitted_at IS NULL)
          OR (status = 'SUBMITTED' AND submitted_at IS NOT NULL
            AND submitted_at >= created_at
            AND submitted_at <= updated_at))
)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name,
    resource_type, status, created_at, created_by, updated_at, updated_by,
    version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id,definition.permission_code) AS SIGNED),
    'SYSTEM',system_row.id,system_row.id,
    definition.permission_code,definition.permission_name,
    definition.resource_type,'ACTIVE',UTC_TIMESTAMP(3),
    system_row.created_by,UTC_TIMESTAMP(3),system_row.created_by,0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT 'work.report.access' AS permission_code,
           'Access work daily reports' AS permission_name,
           'MENU' AS resource_type
    UNION ALL SELECT 'work.report.create',
                     'Create work daily reports','ACTION'
    UNION ALL SELECT 'work.report.manage',
                     'Manage all work daily reports','DATA'
) definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id),0),0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1 FROM un_plat_permission existing
    WHERE existing.scope_type='SYSTEM'
      AND existing.scope_key=system_row.id
      AND existing.permission_code=definition.permission_code
);

INSERT INTO un_plat_role_permission (
    id,scope_type,scope_key,role_id,permission_id,
    effect,created_at,created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key,role_row.id,permission_row.id) AS SIGNED),
    'SYSTEM',role_row.scope_key,role_row.id,permission_row.id,
    'ALLOW',UTC_TIMESTAMP(3),role_row.updated_by
FROM un_plat_role role_row
JOIN un_plat_permission permission_row
  ON permission_row.scope_type='SYSTEM'
 AND permission_row.scope_key=role_row.scope_key
 AND permission_row.status='ACTIVE'
 AND permission_row.permission_code IN (
     'work.report.access','work.report.create','work.report.manage')
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id),0),0) AS min_id
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
