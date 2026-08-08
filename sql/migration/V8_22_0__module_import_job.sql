-- Durable, permission-scoped module import preview/commit/rollback batches.

CREATE TABLE un_module_import_batch (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    schema_checksum CHAR(64) NOT NULL,
    import_mode VARCHAR(16) NOT NULL,
    match_field_code VARCHAR(64) NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INT NOT NULL,
    new_rows INT NOT NULL DEFAULT 0,
    update_rows INT NOT NULL DEFAULT 0,
    failed_rows INT NOT NULL DEFAULT 0,
    preview_job_id BIGINT NOT NULL,
    commit_job_id BIGINT NULL,
    rollback_job_id BIGINT NULL,
    requested_by_account_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    committed_at DATETIME(3) NULL,
    rolled_back_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_import_preview_job (preview_job_id),
    UNIQUE KEY uk_module_import_commit_job (commit_job_id),
    UNIQUE KEY uk_module_import_rollback_job (rollback_job_id),
    KEY idx_module_import_owner (system_id, tenant_id, logical_module_id, requested_by_member_id, created_at),
    KEY idx_module_import_status (status, updated_at),
    CONSTRAINT ck_module_import_mode CHECK (import_mode IN ('NEW', 'UPSERT')),
    CONSTRAINT ck_module_import_status CHECK (status IN (
        'PREVIEW_QUEUED','PREVIEWING','READY','INVALID','COMMIT_QUEUED','COMMITTING',
        'COMMITTED','ROLLBACK_QUEUED','ROLLING_BACK','ROLLED_BACK','FAILED')),
    CONSTRAINT ck_module_import_counts CHECK (
        total_rows BETWEEN 1 AND 200 AND new_rows >= 0 AND update_rows >= 0 AND failed_rows >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_import_row (
    id BIGINT NOT NULL,
    batch_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_row_number INT NOT NULL,
    planned_action VARCHAR(16) NULL,
    row_status VARCHAR(24) NOT NULL,
    input_json JSON NOT NULL,
    unique_fingerprint_json JSON NULL,
    error_code VARCHAR(64) NULL,
    error_message VARCHAR(500) NULL,
    target_record_id BIGINT NULL,
    target_before_json JSON NULL,
    target_after_version BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_module_import_row (batch_id, source_row_number),
    KEY idx_module_import_row_status (batch_id, row_status, source_row_number),
    KEY idx_module_import_row_target (system_id, tenant_id, target_record_id),
    CONSTRAINT fk_module_import_row_batch FOREIGN KEY (batch_id) REFERENCES un_module_import_batch(id),
    CONSTRAINT ck_module_import_row_action CHECK (planned_action IS NULL OR planned_action IN ('NEW', 'UPDATE')),
    CONSTRAINT ck_module_import_row_status CHECK (
        row_status IN ('PREVIEW_PENDING','VALID','INVALID','COMMITTED','ROLLED_BACK'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_module_permission (
    id, system_id, module_id, resource_type, resource_id, permission_code, permission_name,
    permission_type, registered_permission_id, desired_status, created_revision, updated_revision,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY m.system_id, m.id) AS SIGNED),
    m.system_id,
    m.id,
    'MODULE',
    m.id,
    CONCAT('module.', m.module_code, '.import'),
    CONCAT(m.module_name, ' import'),
    'ACTION',
    NULL,
    'ENABLED',
    m.updated_revision,
    m.updated_revision,
    UTC_TIMESTAMP(3),
    m.updated_by,
    UTC_TIMESTAMP(3),
    m.updated_by,
    0
FROM un_module_definition m
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_module_permission
) base
WHERE m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_module_permission p
      WHERE p.system_id=m.system_id
        AND p.permission_code=CONCAT('module.', m.module_code, '.import')
  );

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY p.system_id, p.permission_code) AS SIGNED),
    'SYSTEM', p.system_id, p.system_id, p.permission_code, p.permission_name, 'ACTION', 'ACTIVE',
    UTC_TIMESTAMP(3), p.updated_by, UTC_TIMESTAMP(3), p.updated_by, 0
FROM un_module_permission p
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_permission
) base
WHERE p.deleted_at IS NULL
  AND p.desired_status='ENABLED'
  AND p.permission_code LIKE 'module.%.import'
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_permission target
      WHERE target.scope_type='SYSTEM'
        AND target.scope_key=p.system_id
        AND target.permission_code=p.permission_code
  );

UPDATE un_module_permission source
JOIN un_plat_permission target
  ON target.scope_type='SYSTEM'
 AND target.scope_key=source.system_id
 AND target.permission_code=source.permission_code
SET source.registered_permission_id=target.id,
    source.updated_at=UTC_TIMESTAMP(3),
    source.version=source.version+1
WHERE source.registered_permission_id IS NULL
  AND source.permission_code LIKE 'module.%.import';

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY r.scope_key, r.id, p.id) AS SIGNED),
    'SYSTEM', r.scope_key, r.id, p.id, 'ALLOW', UTC_TIMESTAMP(3), r.updated_by
FROM un_plat_role r
JOIN un_plat_permission p
  ON p.scope_type='SYSTEM'
 AND p.scope_key=r.scope_key
 AND p.status='ACTIVE'
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_role_permission
) base
WHERE r.scope_type='SYSTEM'
  AND r.role_type='ROOT'
  AND r.status='ACTIVE'
  AND r.deleted_at IS NULL
  AND p.permission_code LIKE 'module.%.import'
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
      WHERE existing.role_id=r.id AND existing.permission_id=p.id
  );

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='SYSTEM';

UPDATE un_plat_system s
JOIN un_plat_authz_epoch e ON e.scope_type='SYSTEM' AND e.scope_key=s.id
SET s.permission_version=e.epoch,
    s.updated_at=UTC_TIMESTAMP(3),
    s.version=s.version+1;
