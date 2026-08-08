-- P4-B3 registers query-scope permissions and backfills deterministic search tokens.

INSERT INTO un_module_permission (
    id, system_id, module_id, resource_type, resource_id, permission_code, permission_name,
    permission_type, registered_permission_id, desired_status, created_revision, updated_revision,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY m.system_id, m.id, v.verb) AS SIGNED),
    m.system_id,
    m.id,
    'MODULE',
    m.id,
    CONCAT('module.', m.module_code, '.', v.verb),
    CONCAT(m.module_name, ' ', v.verb),
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
    SELECT 'archive.view' AS verb
    UNION ALL
    SELECT 'trash.view' AS verb
) v
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_module_permission
) base
WHERE m.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1
      FROM un_module_permission p
      WHERE p.system_id = m.system_id
        AND p.permission_code = CONCAT('module.', m.module_code, '.', v.verb)
  );

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY p.system_id, p.permission_code) AS SIGNED),
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
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_permission
) base
WHERE p.deleted_at IS NULL
  AND p.desired_status = 'ENABLED'
  AND (p.permission_code LIKE 'module.%.archive.view'
       OR p.permission_code LIKE 'module.%.trash.view')
  AND NOT EXISTS (
      SELECT 1
      FROM un_plat_permission target
      WHERE target.scope_type = 'SYSTEM'
        AND target.scope_key = p.system_id
        AND target.permission_code = p.permission_code
  );

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY s.id) AS SIGNED),
    'SYSTEM',
    s.id,
    s.id,
    'runtime.saved_view.manage',
    'Manage personal saved views',
    'ACTION',
    'ACTIVE',
    UTC_TIMESTAMP(3),
    s.created_by,
    UTC_TIMESTAMP(3),
    s.created_by,
    0
FROM un_plat_system s
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_permission target
    WHERE target.scope_type = 'SYSTEM'
      AND target.scope_key = s.id
      AND target.permission_code = 'runtime.saved_view.manage'
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
  AND (source.permission_code LIKE 'module.%.archive.view'
       OR source.permission_code LIKE 'module.%.trash.view');

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (ORDER BY r.scope_key, r.id, p.id) AS SIGNED),
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
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_plat_role_permission
) base
WHERE r.scope_type = 'SYSTEM'
  AND r.role_type = 'ROOT'
  AND r.status = 'ACTIVE'
  AND r.deleted_at IS NULL
  AND (p.permission_code = 'runtime.saved_view.manage'
       OR p.permission_code LIKE 'module.%.archive.view'
       OR p.permission_code LIKE 'module.%.trash.view')
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

INSERT INTO un_module_record_search (
    id, system_id, tenant_id, record_id, schema_version_id, module_snapshot_id,
    logical_module_id, logical_field_id, index_generation_id, record_status, field_type,
    token_ordinal, token, token_hash, created_at
)
WITH RECURSIVE token_length(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM token_length WHERE n < 20
), searchable_value AS (
    SELECT
        rv.system_id,
        rv.tenant_id,
        rv.record_id,
        rv.schema_version_id,
        rv.module_snapshot_id,
        rv.logical_module_id,
        rv.logical_field_id,
        rv.field_type,
        r.status AS record_status,
        LOWER(TRIM(COALESCE(rv.string_value, rv.text_value))) AS normalized_value
    FROM un_module_record_value rv
    JOIN un_module_record r
      ON r.system_id = rv.system_id
     AND r.tenant_id = rv.tenant_id
     AND r.record_id = rv.record_id
     AND r.schema_version_id = rv.schema_version_id
     AND r.module_snapshot_id = rv.module_snapshot_id
    JOIN un_module_field f
      ON f.system_id = rv.system_id
     AND f.id = rv.logical_field_id
     AND f.deleted_at IS NULL
     AND f.desired_status = 'ENABLED'
     AND f.is_searchable = 1
     AND f.index_mode <> 'NONE'
    WHERE rv.field_type IN ('TEXT', 'TEXTAREA')
      AND COALESCE(rv.string_value, rv.text_value) IS NOT NULL
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY value.system_id, value.tenant_id, value.record_id, value.logical_field_id, token_length.n
    ) AS SIGNED),
    value.system_id,
    value.tenant_id,
    value.record_id,
    value.schema_version_id,
    value.module_snapshot_id,
    value.logical_module_id,
    value.logical_field_id,
    1,
    value.record_status,
    value.field_type,
    token_length.n,
    CASE
        WHEN token_length.n = 0 THEN LEFT(value.normalized_value, 255)
        ELSE LEFT(value.normalized_value, token_length.n)
    END,
    SHA2(CASE
        WHEN token_length.n = 0 THEN LEFT(value.normalized_value, 255)
        ELSE LEFT(value.normalized_value, token_length.n)
    END, 256),
    UTC_TIMESTAMP(3)
FROM searchable_value value
JOIN token_length
  ON token_length.n = 0
  OR (token_length.n BETWEEN 2 AND 20 AND CHAR_LENGTH(value.normalized_value) >= token_length.n)
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id FROM un_module_record_search
) base
WHERE CHAR_LENGTH(value.normalized_value) >= 2
  AND NOT EXISTS (
      SELECT 1
      FROM un_module_record_search existing
      WHERE existing.system_id = value.system_id
        AND existing.tenant_id = value.tenant_id
        AND existing.logical_module_id = value.logical_module_id
        AND existing.logical_field_id = value.logical_field_id
        AND existing.index_generation_id = 1
        AND existing.record_id = value.record_id
        AND existing.token_ordinal = token_length.n
        AND existing.token_hash = SHA2(CASE
            WHEN token_length.n = 0 THEN LEFT(value.normalized_value, 255)
            ELSE LEFT(value.normalized_value, token_length.n)
        END, 256)
  );
