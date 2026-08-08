-- Durable, permission-scoped module XLSX export tasks and results.

CREATE TABLE un_module_export_task (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    schema_checksum CHAR(64) NOT NULL,
    query_json JSON NOT NULL,
    query_hash CHAR(64) NOT NULL,
    field_codes_json JSON NOT NULL,
    permission_snapshot_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
    total_rows INT NULL,
    processed_rows INT NOT NULL DEFAULT 0,
    result_filename VARCHAR(180) NULL,
    result_content LONGBLOB NULL,
    result_size BIGINT NULL,
    failure_code VARCHAR(64) NULL,
    failure_message VARCHAR(500) NULL,
    job_id BIGINT NOT NULL,
    requested_by_account_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    started_at DATETIME(3) NULL,
    finished_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_export_job (job_id),
    KEY idx_module_export_owner (
        system_id, tenant_id, logical_module_id, requested_by_member_id, created_at),
    KEY idx_module_export_status (status, updated_at),
    CONSTRAINT ck_module_export_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_module_export_counts CHECK (
        (total_rows IS NULL OR total_rows BETWEEN 0 AND 5000)
        AND processed_rows BETWEEN 0 AND 5000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_module_permission (
    id, system_id, module_id, resource_type, resource_id, permission_code, permission_name,
    permission_type, registered_permission_id, desired_status, created_revision, updated_revision,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY m.system_id, m.id) AS SIGNED),
    m.system_id, m.id, 'MODULE', m.id,
    CONCAT('module.', m.module_code, '.export'), CONCAT(m.module_name, ' export'), 'ACTION',
    NULL, 'ENABLED', m.updated_revision, m.updated_revision,
    UTC_TIMESTAMP(3), m.updated_by, UTC_TIMESTAMP(3), m.updated_by, 0
FROM un_module_definition m
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_module_permission) base
WHERE m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_module_permission p
      WHERE p.system_id=m.system_id
        AND p.permission_code=CONCAT('module.', m.module_code, '.export'));

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY p.system_id, p.permission_code) AS SIGNED),
    'SYSTEM', p.system_id, p.system_id, p.permission_code, p.permission_name, 'ACTION', 'ACTIVE',
    UTC_TIMESTAMP(3), p.updated_by, UTC_TIMESTAMP(3), p.updated_by, 0
FROM un_module_permission p
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_permission) base
WHERE p.deleted_at IS NULL AND p.desired_status='ENABLED'
  AND p.permission_code LIKE 'module.%.export'
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_permission target
      WHERE target.scope_type='SYSTEM' AND target.scope_key=p.system_id
        AND target.permission_code=p.permission_code);

UPDATE un_module_permission source
JOIN un_plat_permission target
  ON target.scope_type='SYSTEM' AND target.scope_key=source.system_id
 AND target.permission_code=source.permission_code
SET source.registered_permission_id=target.id,
    source.updated_at=UTC_TIMESTAMP(3), source.version=source.version+1
WHERE source.registered_permission_id IS NULL
  AND source.permission_code LIKE 'module.%.export';

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY r.scope_key, r.id, p.id) AS SIGNED),
    'SYSTEM', r.scope_key, r.id, p.id, 'ALLOW', UTC_TIMESTAMP(3), r.updated_by
FROM un_plat_role r
JOIN un_plat_permission p
  ON p.scope_type='SYSTEM' AND p.scope_key=r.scope_key AND p.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_role_permission) base
WHERE r.scope_type='SYSTEM' AND r.role_type='ROOT' AND r.status='ACTIVE'
  AND r.deleted_at IS NULL AND p.permission_code LIKE 'module.%.export'
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
      WHERE existing.role_id=r.id AND existing.permission_id=p.id);

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='SYSTEM';

UPDATE un_plat_system s
JOIN un_plat_authz_epoch e ON e.scope_type='SYSTEM' AND e.scope_key=s.id
SET s.permission_version=e.epoch, s.updated_at=UTC_TIMESTAMP(3), s.version=s.version+1;
