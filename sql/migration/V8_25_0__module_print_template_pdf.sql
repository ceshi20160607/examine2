-- Versioned module print templates, durable single-record PDF tasks and history.

CREATE TABLE un_module_print_template (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    desired_status VARCHAR(16) NOT NULL,
    paper_size VARCHAR(8) NOT NULL,
    orientation VARCHAR(16) NOT NULL,
    definition_json JSON NOT NULL,
    published_version_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_print_template_code (system_id, logical_module_id, template_code),
    UNIQUE KEY uk_print_template_system_id (system_id, id),
    KEY idx_print_template_module (system_id, logical_module_id, desired_status, updated_at),
    CONSTRAINT ck_print_template_code CHECK (template_code REGEXP '^[a-z][a-z0-9_]{1,63}$'),
    CONSTRAINT ck_print_template_status CHECK (desired_status IN ('ENABLED','DISABLED','ARCHIVED')),
    CONSTRAINT ck_print_template_paper CHECK (paper_size IN ('A4','A5')),
    CONSTRAINT ck_print_template_orientation CHECK (orientation IN ('PORTRAIT','LANDSCAPE')),
    CONSTRAINT ck_print_template_delete CHECK (deleted_at IS NULL OR deleted_by IS NOT NULL),
    CONSTRAINT fk_print_template_module FOREIGN KEY (system_id, logical_module_id)
        REFERENCES un_module_definition (system_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_print_template_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    template_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    version_no BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    paper_size VARCHAR(8) NOT NULL,
    orientation VARCHAR(16) NOT NULL,
    definition_json JSON NOT NULL,
    definition_checksum CHAR(64) NOT NULL,
    published_at DATETIME(3) NOT NULL,
    published_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_print_template_version_no (system_id, template_id, version_no),
    UNIQUE KEY uk_print_template_version_system_id (system_id, id),
    KEY idx_print_version_schema (system_id, schema_version_id, module_snapshot_id),
    CONSTRAINT ck_print_template_version_no CHECK (version_no > 0),
    CONSTRAINT ck_print_version_checksum CHECK (definition_checksum REGEXP '^[a-f0-9]{64}$'),
    CONSTRAINT fk_print_version_template FOREIGN KEY (system_id, template_id)
        REFERENCES un_module_print_template (system_id, id),
    CONSTRAINT fk_print_version_schema FOREIGN KEY (system_id, schema_version_id, module_snapshot_id)
        REFERENCES un_module_runtime_schema_module (system_id, schema_version_id, module_snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE un_module_print_template
    ADD CONSTRAINT fk_print_template_published FOREIGN KEY (system_id, published_version_id)
        REFERENCES un_module_print_template_version (system_id, id);

CREATE TABLE un_module_print_task (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    record_id BIGINT NOT NULL,
    record_version BIGINT NOT NULL,
    record_no VARCHAR(64) NOT NULL,
    template_id BIGINT NOT NULL,
    template_version_id BIGINT NOT NULL,
    template_version_no BIGINT NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    template_name VARCHAR(128) NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    snapshot_json JSON NOT NULL,
    status VARCHAR(16) NOT NULL,
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
    UNIQUE KEY uk_module_print_job (job_id),
    KEY idx_module_print_history (
        system_id, tenant_id, logical_module_id, record_id, requested_by_member_id, created_at),
    KEY idx_module_print_status (status, updated_at),
    CONSTRAINT ck_module_print_status CHECK (status IN ('QUEUED','RUNNING','SUCCEEDED','FAILED')),
    CONSTRAINT ck_module_print_record_version CHECK (record_version > 0 AND template_version_no > 0),
    CONSTRAINT ck_module_print_snapshot_size CHECK (OCTET_LENGTH(snapshot_json) <= 524288),
    CONSTRAINT fk_module_print_template_version FOREIGN KEY (system_id, template_version_id)
        REFERENCES un_module_print_template_version (system_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_module_permission (
    id, system_id, module_id, resource_type, resource_id, permission_code, permission_name,
    permission_type, registered_permission_id, desired_status, created_revision, updated_revision,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY m.system_id, m.id) AS SIGNED),
    m.system_id, m.id, 'MODULE', m.id,
    CONCAT('module.', m.module_code, '.print'), CONCAT(m.module_name, ' print'), 'ACTION',
    NULL, 'ENABLED', m.updated_revision, m.updated_revision,
    UTC_TIMESTAMP(3), m.updated_by, UTC_TIMESTAMP(3), m.updated_by, 0
FROM un_module_definition m
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_module_permission) base
WHERE m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM un_module_permission p
      WHERE p.system_id=m.system_id
        AND p.permission_code=CONCAT('module.', m.module_code, '.print'));

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
  AND p.permission_code LIKE 'module.%.print'
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
  AND source.permission_code LIKE 'module.%.print';

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
  AND r.deleted_at IS NULL AND p.permission_code LIKE 'module.%.print'
  AND NOT EXISTS (
      SELECT 1 FROM un_plat_role_permission existing
      WHERE existing.role_id=r.id AND existing.permission_id=p.id);

UPDATE un_plat_authz_epoch
SET epoch=epoch+1, updated_at=UTC_TIMESTAMP(3), version=version+1
WHERE scope_type='SYSTEM';

UPDATE un_plat_system s
JOIN un_plat_authz_epoch e ON e.scope_type='SYSTEM' AND e.scope_key=s.id
SET s.permission_version=e.epoch, s.updated_at=UTC_TIMESTAMP(3), s.version=s.version+1;
