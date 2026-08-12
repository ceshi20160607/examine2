-- RH-01 persists an append-only, tenant-scoped mutation timeline for runtime records.
-- Values are already reduced to field-level diffs and sensitive entries are masked
-- before they reach this table.

CREATE TABLE un_module_record_history (
    history_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    record_version BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_member_id BIGINT NULL,
    occurred_at DATETIME(3) NOT NULL,
    diff_json JSON NOT NULL,
    PRIMARY KEY (history_id),
    UNIQUE KEY uk_module_record_history_event (
        system_id, tenant_id, record_id, record_version, action
    ),
    KEY idx_module_record_history_page (
        system_id, tenant_id, record_id, occurred_at, history_id
    ),
    CONSTRAINT fk_module_record_history_record
        FOREIGN KEY (system_id, tenant_id, record_id)
        REFERENCES un_module_record (system_id, tenant_id, record_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_record_history_actor
        FOREIGN KEY (system_id, actor_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_module_record_history_version CHECK (record_version >= 0),
    CONSTRAINT ck_module_record_history_action CHECK (
        action REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Existing modules receive the new permission without requiring a republish.
INSERT INTO un_module_permission (
    id, system_id, module_id, resource_type, resource_id, permission_code, permission_name,
    permission_type, registered_permission_id, desired_status, created_revision, updated_revision,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED)
        - CAST(ROW_NUMBER() OVER (ORDER BY m.system_id, m.id) AS SIGNED),
    m.system_id,
    m.id,
    'MODULE',
    m.id,
    CONCAT('module.', m.module_code, '.history.read'),
    CONCAT(m.module_name, ' history.read'),
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
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_module_permission
) base
WHERE m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM un_module_permission p
      WHERE p.system_id = m.system_id
        AND p.permission_code = CONCAT('module.', m.module_code, '.history.read')
  );

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED)
        - CAST(ROW_NUMBER() OVER (ORDER BY p.system_id, p.permission_code) AS SIGNED),
    'SYSTEM',
    p.system_id,
    p.system_id,
    p.permission_code,
    p.permission_name,
    p.permission_type,
    'ACTIVE',
    UTC_TIMESTAMP(3),
    p.updated_by,
    UTC_TIMESTAMP(3),
    p.updated_by,
    0
FROM un_module_permission p
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE p.deleted_at IS NULL
  AND p.desired_status = 'ENABLED'
  AND p.permission_code LIKE 'module.%.history.read'
  AND NOT EXISTS (
      SELECT 1
      FROM un_plat_permission target
      WHERE target.scope_type = 'SYSTEM'
        AND target.scope_key = p.system_id
        AND target.permission_code = p.permission_code
  );

UPDATE un_module_permission source
JOIN un_plat_permission target
  ON target.scope_type = 'SYSTEM'
 AND target.scope_key = source.system_id
 AND target.permission_code = source.permission_code
SET source.registered_permission_id = target.id,
    source.updated_at = UTC_TIMESTAMP(3),
    source.version = source.version + 1
WHERE source.registered_permission_id IS NULL
  AND source.permission_code LIKE 'module.%.history.read';

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED)
        - CAST(ROW_NUMBER() OVER (ORDER BY r.scope_key, r.id, p.id) AS SIGNED),
    'SYSTEM',
    r.scope_key,
    r.id,
    p.id,
    'ALLOW',
    UTC_TIMESTAMP(3),
    r.updated_by
FROM un_plat_role r
JOIN un_plat_permission p
  ON p.scope_type = 'SYSTEM'
 AND p.scope_key = r.scope_key
 AND p.status = 'ACTIVE'
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_role_permission
) base
WHERE r.scope_type = 'SYSTEM'
  AND r.role_type = 'ROOT'
  AND r.status = 'ACTIVE'
  AND r.deleted_at IS NULL
  AND p.permission_code LIKE 'module.%.history.read'
  AND NOT EXISTS (
      SELECT 1
      FROM un_plat_role_permission existing
      WHERE existing.role_id = r.id
        AND existing.permission_id = p.id
  );

UPDATE un_plat_authz_epoch
SET epoch = epoch + 1,
    updated_at = UTC_TIMESTAMP(3),
    version = version + 1
WHERE scope_type = 'SYSTEM';

UPDATE un_plat_system s
JOIN un_plat_authz_epoch e
  ON e.scope_type = 'SYSTEM'
 AND e.scope_key = s.id
SET s.permission_version = e.epoch,
    s.updated_at = UTC_TIMESTAMP(3),
    s.version = s.version + 1;
