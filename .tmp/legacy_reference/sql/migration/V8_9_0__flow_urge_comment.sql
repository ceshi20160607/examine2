-- Append-only Flow urges and comments, plus their system permissions.

CREATE TABLE un_flow_urge (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    urge_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    actor_id BIGINT NOT NULL,
    recipient_id BIGINT NOT NULL,
    message VARCHAR(500) NOT NULL DEFAULT '',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, urge_id),
    KEY idx_flow_urge_instance_page (
        system_id, tenant_id, instance_id, created_at, urge_id
    ),
    CONSTRAINT fk_flow_urge_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_urge_identity CHECK (
        urge_id > 0 AND actor_id > 0 AND recipient_id > 0
    ),
    CONSTRAINT ck_flow_urge_message CHECK (
        message = TRIM(message) AND CHAR_LENGTH(message) BETWEEN 0 AND 500
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_comment (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    comment_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    body VARCHAR(2000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, comment_id),
    KEY idx_flow_comment_instance_page (
        system_id, tenant_id, instance_id, created_at, comment_id
    ),
    CONSTRAINT fk_flow_comment_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_comment_identity CHECK (
        comment_id > 0 AND author_id > 0
    ),
    CONSTRAINT ck_flow_comment_body CHECK (
        body = TRIM(body) AND CHAR_LENGTH(body) BETWEEN 1 AND 2000
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id, permission_definition.permission_code
    ) AS SIGNED),
    'SYSTEM',
    system_row.id,
    system_row.id,
    permission_definition.permission_code,
    permission_definition.name,
    'ACTION',
    'ACTIVE',
    UTC_TIMESTAMP(3),
    system_row.created_by,
    UTC_TIMESTAMP(3),
    system_row.created_by,
    0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT 'flow.instance.urge' AS permission_code, 'Urge pending flow instances' AS name
    UNION ALL
    SELECT 'flow.instance.comment', 'Comment on flow instances'
) permission_definition
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_permission existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
      AND existing.permission_code = permission_definition.permission_code
);

INSERT INTO un_plat_role_permission (
    id, scope_type, scope_key, role_id, permission_id, effect, created_at, created_by
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY role_row.scope_key, role_row.id, permission_row.id
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
 AND permission_row.permission_code IN ('flow.instance.urge', 'flow.instance.comment')
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
