-- VS2 organization and permission expand migration.
-- This migration intentionally contains no account, credential, or bootstrap secret data.

-- Strengthen the V1 ownership graph before the VS2 tables reference it.
ALTER TABLE un_plat_system
    DROP CHECK ck_plat_system_status,
    ADD UNIQUE KEY uk_plat_system_id_owner (id, owner_account_id),
    ADD CONSTRAINT ck_plat_system_status CHECK (status IN ('INITIALIZING', 'ACTIVE', 'DISABLED', 'ARCHIVED', 'INIT_FAILED')),
    ADD CONSTRAINT ck_plat_system_permission_version CHECK (permission_version > 0);

ALTER TABLE un_plat_tenant
    ADD COLUMN active_default_system_id BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN is_default = 1 AND deleted_at IS NULL THEN system_id ELSE NULL END
        ) STORED,
    ADD UNIQUE KEY uk_plat_tenant_system_id (system_id, id),
    ADD UNIQUE KEY uk_plat_tenant_one_active_default (active_default_system_id),
    ADD CONSTRAINT ck_plat_tenant_default_state CHECK (
        is_default = 0 OR (status = 'ACTIVE' AND deleted_at IS NULL)
    );

ALTER TABLE un_plat_member
    DROP FOREIGN KEY fk_plat_member_tenant,
    ADD UNIQUE KEY uk_plat_member_system_id (system_id, id),
    ADD UNIQUE KEY uk_plat_member_system_id_account (system_id, id, account_id),
    ADD CONSTRAINT fk_plat_member_system_tenant
        FOREIGN KEY (system_id, default_tenant_id)
        REFERENCES un_plat_tenant (system_id, id);

ALTER TABLE un_plat_role
    ADD COLUMN data_scope_id BIGINT NULL AFTER permission_version,
    ADD COLUMN is_builtin BOOLEAN NOT NULL DEFAULT FALSE AFTER data_scope_id,
    ADD COLUMN published_version BIGINT NOT NULL DEFAULT 0 AFTER is_builtin,
    ADD COLUMN tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED AFTER tenant_id,
    DROP FOREIGN KEY fk_plat_role_tenant,
    DROP CHECK ck_plat_role_scope,
    ADD UNIQUE KEY uk_plat_role_scope_id (scope_type, scope_key, id),
    ADD UNIQUE KEY uk_plat_role_scope_tenant_id (scope_type, scope_key, tenant_key, id),
    ADD UNIQUE KEY uk_plat_role_system_id (system_id, id),
    ADD CONSTRAINT fk_plat_role_system_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    ADD CONSTRAINT ck_plat_role_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    ),
    ADD CONSTRAINT ck_plat_role_builtin CHECK (is_builtin IN (0, 1)),
    ADD CONSTRAINT ck_plat_role_versions CHECK (permission_version > 0 AND published_version >= 0);

ALTER TABLE un_plat_permission
    ADD UNIQUE KEY uk_plat_permission_scope_id (scope_type, scope_key, id);

-- A role-permission edge carries its scope and is accepted only when both ends
-- expose the same scope candidate key.
ALTER TABLE un_plat_role_permission
    ADD COLUMN scope_type VARCHAR(16) NULL AFTER id,
    ADD COLUMN scope_key BIGINT NULL AFTER scope_type;

UPDATE un_plat_role_permission rp
JOIN un_plat_role r ON r.id = rp.role_id
SET rp.scope_type = r.scope_type,
    rp.scope_key = r.scope_key;

ALTER TABLE un_plat_role_permission
    DROP FOREIGN KEY fk_plat_role_permission_role,
    DROP FOREIGN KEY fk_plat_role_permission_permission,
    MODIFY COLUMN scope_type VARCHAR(16) NOT NULL,
    MODIFY COLUMN scope_key BIGINT NOT NULL,
    ADD CONSTRAINT fk_plat_role_permission_scoped_role
        FOREIGN KEY (scope_type, scope_key, role_id)
        REFERENCES un_plat_role (scope_type, scope_key, id),
    ADD CONSTRAINT fk_plat_role_permission_scoped_permission
        FOREIGN KEY (scope_type, scope_key, permission_id)
        REFERENCES un_plat_permission (scope_type, scope_key, id),
    ADD CONSTRAINT ck_plat_role_permission_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0)
        OR (scope_type = 'SYSTEM' AND scope_key > 0)
    );

-- tenant_key maps a system-wide binding to 0. Unlike a nullable tenant_id in a
-- unique key, it admits only one system-wide member/role edge.
ALTER TABLE un_plat_member_role
    ADD COLUMN system_id BIGINT NULL AFTER id,
    ADD COLUMN tenant_key BIGINT
        GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED AFTER tenant_id;

UPDATE un_plat_member_role mr
JOIN un_plat_member m ON m.id = mr.member_id
SET mr.system_id = m.system_id;

ALTER TABLE un_plat_member_role
    DROP FOREIGN KEY fk_plat_member_role_member,
    DROP FOREIGN KEY fk_plat_member_role_role,
    DROP FOREIGN KEY fk_plat_member_role_tenant,
    DROP INDEX uk_plat_member_role,
    MODIFY COLUMN system_id BIGINT NOT NULL,
    ADD UNIQUE KEY uk_plat_member_role_scope (system_id, member_id, role_id, tenant_key),
    ADD CONSTRAINT fk_plat_member_role_scoped_member
        FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id),
    ADD CONSTRAINT fk_plat_member_role_scoped_role
        FOREIGN KEY (system_id, role_id)
        REFERENCES un_plat_role (system_id, id),
    ADD CONSTRAINT fk_plat_member_role_scoped_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    ADD CONSTRAINT ck_plat_member_role_tenant CHECK (tenant_id IS NULL OR tenant_id > 0),
    ADD CONSTRAINT ck_plat_member_role_validity CHECK (valid_until IS NULL OR valid_until > valid_from);

CREATE TABLE un_sys_feature_flag (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    flag_key VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    rollout_percentage INT NOT NULL DEFAULT 100,
    rules_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sys_feature_flag_scope (scope_type, scope_key, tenant_key, flag_key),
    KEY idx_sys_feature_flag_system (system_id, tenant_id, enabled),
    CONSTRAINT fk_sys_feature_flag_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_sys_feature_flag_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT ck_sys_feature_flag_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    ),
    CONSTRAINT ck_sys_feature_flag_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_sys_feature_flag_rollout CHECK (rollout_percentage BETWEEN 0 AND 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_department (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    parent_id BIGINT NULL,
    department_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_department_code (scope_type, scope_key, department_code),
    UNIQUE KEY uk_plat_department_scope_id (scope_type, scope_key, id),
    UNIQUE KEY uk_plat_department_scope_tenant_id (scope_type, scope_key, tenant_key, id),
    KEY idx_plat_department_tree (scope_type, scope_key, parent_id, status, sort_order),
    KEY idx_plat_department_system (system_id, tenant_id, status),
    CONSTRAINT fk_plat_department_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_plat_department_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT fk_plat_department_parent
        FOREIGN KEY (scope_type, scope_key, tenant_key, parent_id)
        REFERENCES un_plat_department (scope_type, scope_key, tenant_key, id),
    CONSTRAINT ck_plat_department_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    ),
    CONSTRAINT ck_plat_department_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_plat_department_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_department_closure (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    ancestor_id BIGINT NOT NULL,
    descendant_id BIGINT NOT NULL,
    node_low BIGINT GENERATED ALWAYS AS (LEAST(ancestor_id, descendant_id)) STORED,
    node_high BIGINT GENERATED ALWAYS AS (GREATEST(ancestor_id, descendant_id)) STORED,
    depth INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_department_closure_path
        (scope_type, scope_key, tenant_key, ancestor_id, descendant_id),
    UNIQUE KEY uk_plat_department_closure_acyclic
        (scope_type, scope_key, tenant_key, node_low, node_high),
    KEY idx_plat_department_closure_desc
        (scope_type, scope_key, tenant_key, descendant_id, depth),
    CONSTRAINT fk_plat_department_closure_ancestor
        FOREIGN KEY (scope_type, scope_key, tenant_key, ancestor_id)
        REFERENCES un_plat_department (scope_type, scope_key, tenant_key, id),
    CONSTRAINT fk_plat_department_closure_descendant
        FOREIGN KEY (scope_type, scope_key, tenant_key, descendant_id)
        REFERENCES un_plat_department (scope_type, scope_key, tenant_key, id),
    CONSTRAINT ck_plat_department_closure_depth CHECK (
        (ancestor_id = descendant_id AND depth = 0)
        OR (ancestor_id <> descendant_id AND depth > 0)
    ),
    CONSTRAINT ck_plat_department_closure_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND tenant_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_member_department (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    account_id BIGINT NULL,
    member_id BIGINT NULL,
    principal_id BIGINT GENERATED ALWAYS AS (COALESCE(account_id, member_id)) STORED,
    department_id BIGINT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END
    ) STORED,
    primary_principal_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN is_primary = 1 AND deleted_at IS NULL THEN COALESCE(account_id, member_id) ELSE NULL END
    ) STORED,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_member_department_active
        (scope_type, scope_key, principal_id, department_id, active_marker),
    UNIQUE KEY uk_plat_member_department_primary
        (scope_type, scope_key, primary_principal_id),
    KEY idx_plat_member_department_dept (scope_type, scope_key, department_id, is_primary),
    CONSTRAINT fk_plat_member_department_account FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_plat_member_department_member
        FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id),
    CONSTRAINT fk_plat_member_department_department
        FOREIGN KEY (scope_type, scope_key, tenant_key, department_id)
        REFERENCES un_plat_department (scope_type, scope_key, tenant_key, id),
    CONSTRAINT ck_plat_member_department_principal CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL
            AND account_id IS NOT NULL AND member_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL
            AND account_id IS NULL AND member_id IS NOT NULL)
    ),
    CONSTRAINT ck_plat_member_department_primary CHECK (is_primary IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_member_tenant (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    granted_at DATETIME(3) NOT NULL,
    granted_by BIGINT NOT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_member_tenant (system_id, member_id, tenant_id),
    UNIQUE KEY uk_plat_member_tenant_owner (system_id, member_id, tenant_id, id),
    KEY idx_plat_member_tenant_access (system_id, tenant_id, status, member_id),
    CONSTRAINT fk_plat_member_tenant_member
        FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id),
    CONSTRAINT fk_plat_member_tenant_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT ck_plat_member_tenant_status CHECK (status IN ('ACTIVE', 'DISABLED', 'EXPIRED')),
    CONSTRAINT ck_plat_member_tenant_expiry CHECK (expires_at IS NULL OR expires_at > granted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE un_plat_member_department
    ADD CONSTRAINT fk_plat_member_department_tenant_access
        FOREIGN KEY (system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id);

-- Every V1 system owner receives explicit access to every undeleted tenant in
-- that system. Tenant IDs are snowflake IDs and are collision-free in this new table.
INSERT INTO un_plat_member_tenant (
    id, system_id, member_id, tenant_id, status,
    granted_at, granted_by, expires_at,
    created_at, created_by, updated_at, updated_by,
    deleted_at, deleted_by, version
)
SELECT
    t.id,
    s.id,
    m.id,
    t.id,
    CASE WHEN t.status = 'ACTIVE' THEN 'ACTIVE' ELSE 'DISABLED' END,
    GREATEST(m.joined_at, t.created_at),
    s.owner_account_id,
    NULL,
    GREATEST(m.created_at, t.created_at),
    s.owner_account_id,
    GREATEST(m.updated_at, t.updated_at),
    s.owner_account_id,
    NULL,
    NULL,
    0
FROM un_plat_system s
JOIN un_plat_member m
    ON m.system_id = s.id
   AND m.account_id = s.owner_account_id
   AND m.deleted_at IS NULL
JOIN un_plat_tenant t
    ON t.system_id = s.id
   AND t.deleted_at IS NULL;

CREATE TABLE un_plat_data_scope (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    scope_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    scope_kind VARCHAR(32) NOT NULL,
    field_rule_json JSON NULL,
    is_builtin BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_data_scope_code (scope_type, scope_key, scope_code),
    UNIQUE KEY uk_plat_data_scope_scope_id (scope_type, scope_key, id),
    UNIQUE KEY uk_plat_data_scope_scope_tenant_id (scope_type, scope_key, tenant_key, id),
    KEY idx_plat_data_scope_system (system_id, tenant_id, status, scope_kind),
    CONSTRAINT fk_plat_data_scope_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_plat_data_scope_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT ck_plat_data_scope_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    ),
    CONSTRAINT ck_plat_data_scope_kind CHECK (scope_kind IN (
        'ALL', 'SELF', 'PRIMARY_DEPARTMENT', 'DEPARTMENT_TREE',
        'SELECTED_DEPARTMENTS', 'SELECTED_MEMBERS', 'FIELD_RULE'
    )),
    CONSTRAINT ck_plat_data_scope_field_rule CHECK (
        (scope_kind = 'FIELD_RULE' AND field_rule_json IS NOT NULL)
        OR (scope_kind <> 'FIELD_RULE' AND field_rule_json IS NULL)
    ),
    CONSTRAINT ck_plat_data_scope_builtin CHECK (is_builtin IN (0, 1)),
    CONSTRAINT ck_plat_data_scope_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_data_scope_target (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    data_scope_id BIGINT NOT NULL,
    target_type VARCHAR(24) NOT NULL,
    department_id BIGINT NULL,
    account_id BIGINT NULL,
    member_id BIGINT NULL,
    target_id BIGINT GENERATED ALWAYS AS (COALESCE(department_id, account_id, member_id)) STORED,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END
    ) STORED,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_data_scope_target
        (scope_type, scope_key, data_scope_id, target_type, target_id, active_marker),
    KEY idx_plat_data_scope_target_lookup (scope_type, scope_key, target_type, target_id),
    CONSTRAINT fk_plat_data_scope_target_scope
        FOREIGN KEY (scope_type, scope_key, tenant_key, data_scope_id)
        REFERENCES un_plat_data_scope (scope_type, scope_key, tenant_key, id),
    CONSTRAINT fk_plat_data_scope_target_department
        FOREIGN KEY (scope_type, scope_key, tenant_key, department_id)
        REFERENCES un_plat_department (scope_type, scope_key, tenant_key, id),
    CONSTRAINT fk_plat_data_scope_target_account FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_plat_data_scope_target_member
        FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id),
    CONSTRAINT fk_plat_data_scope_target_tenant_access
        FOREIGN KEY (system_id, member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id),
    CONSTRAINT ck_plat_data_scope_target_shape CHECK (
        (target_type = 'DEPARTMENT' AND department_id IS NOT NULL
            AND account_id IS NULL AND member_id IS NULL
            AND ((scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL)
                OR (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)))
        OR
        (target_type = 'ACCOUNT' AND department_id IS NULL
            AND account_id IS NOT NULL AND member_id IS NULL
            AND scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND tenant_id IS NULL)
        OR
        (target_type = 'MEMBER' AND department_id IS NULL
            AND account_id IS NULL AND member_id IS NOT NULL
            AND scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Preserve the V1 owner/runtime behavior: every existing role starts with an
-- explicit ALL data scope. New roles may remain without a scope and then deny data.
INSERT INTO un_plat_data_scope (
    id, scope_type, scope_key, system_id, tenant_id,
    scope_code, name, scope_kind, field_rule_json,
    is_builtin, status,
    created_at, created_by, updated_at, updated_by,
    deleted_at, deleted_by, version
)
SELECT
    r.id,
    r.scope_type,
    r.scope_key,
    r.system_id,
    r.tenant_id,
    CONCAT('legacy_all_', LEFT(SHA2(CAST(r.id AS CHAR), 256), 16)),
    LEFT(CONCAT(r.name, ' (legacy all)'), 160),
    'ALL',
    NULL,
    CASE WHEN r.role_type = 'CUSTOM' THEN 0 ELSE 1 END,
    r.status,
    r.created_at,
    r.created_by,
    r.updated_at,
    r.updated_by,
    r.deleted_at,
    NULL,
    0
FROM un_plat_role r;

UPDATE un_plat_role
SET data_scope_id = id,
    is_builtin = CASE WHEN role_type = 'CUSTOM' THEN 0 ELSE 1 END,
    published_version = GREATEST(permission_version, 1);

ALTER TABLE un_plat_role
    ADD CONSTRAINT fk_plat_role_active_data_scope
        FOREIGN KEY (scope_type, scope_key, tenant_key, data_scope_id)
        REFERENCES un_plat_data_scope (scope_type, scope_key, tenant_key, id);

CREATE TABLE un_plat_account_role (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL DEFAULT 'PLATFORM',
    scope_key BIGINT NOT NULL DEFAULT 0,
    account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_account_role (account_id, role_id),
    KEY idx_plat_account_role_valid (account_id, valid_from, valid_until),
    CONSTRAINT fk_plat_account_role_account FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_plat_account_role_scoped_role
        FOREIGN KEY (scope_type, scope_key, role_id)
        REFERENCES un_plat_role (scope_type, scope_key, id),
    CONSTRAINT ck_plat_account_role_scope CHECK (scope_type = 'PLATFORM' AND scope_key = 0),
    CONSTRAINT ck_plat_account_role_validity CHECK (valid_until IS NULL OR valid_until > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_authz_epoch (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    epoch BIGINT NOT NULL DEFAULT 1,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_authz_epoch_scope (scope_type, scope_key),
    CONSTRAINT fk_plat_authz_epoch_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT ck_plat_authz_epoch_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL AND id > 0)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL AND id = system_id)
    ),
    CONSTRAINT ck_plat_authz_epoch_value CHECK (epoch > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_plat_authz_epoch (
    id, scope_type, scope_key, system_id, epoch,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    MIN(account_id),
    'PLATFORM',
    0,
    NULL,
    GREATEST(1, COALESCE(MAX(permission_version), 1)),
    UTC_TIMESTAMP(3),
    NULL,
    UTC_TIMESTAMP(3),
    NULL,
    0
FROM un_plat_context_session
WHERE context_type = 'PLATFORM'
HAVING COUNT(*) > 0;

INSERT INTO un_plat_authz_epoch (
    id, scope_type, scope_key, system_id, epoch,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    s.id,
    'SYSTEM',
    s.id,
    s.id,
    GREATEST(s.permission_version, 1),
    s.created_at,
    s.created_by,
    s.updated_at,
    s.updated_by,
    0
FROM un_plat_system s;

CREATE TABLE un_plat_authz_version (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    role_id BIGINT NOT NULL,
    version_no BIGINT NOT NULL,
    snapshot_json JSON NOT NULL,
    checksum CHAR(64) NULL,
    published_at DATETIME(3) NOT NULL,
    published_by BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_authz_version_role (role_id, version_no),
    UNIQUE KEY uk_plat_authz_version_scope_id (scope_type, scope_key, id),
    KEY idx_plat_authz_version_scope (scope_type, scope_key, published_at),
    CONSTRAINT fk_plat_authz_version_role
        FOREIGN KEY (scope_type, scope_key, role_id)
        REFERENCES un_plat_role (scope_type, scope_key, id),
    CONSTRAINT ck_plat_authz_version_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    ),
    CONSTRAINT ck_plat_authz_version_number CHECK (version_no > 0),
    CONSTRAINT ck_plat_authz_version_snapshot CHECK (JSON_TYPE(snapshot_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_plat_authz_version (
    id, scope_type, scope_key, system_id, role_id, version_no,
    snapshot_json, checksum, published_at, published_by, created_at
)
SELECT
    r.id,
    r.scope_type,
    r.scope_key,
    r.system_id,
    r.id,
    r.published_version,
    JSON_OBJECT(
        'roleId', CAST(r.id AS CHAR),
        'roleCode', r.role_code,
        'scopeType', r.scope_type,
        'scopeKey', CAST(r.scope_key AS CHAR),
        'dataScope', JSON_OBJECT(
            'id', CAST(ds.id AS CHAR),
            'kind', ds.scope_kind
        ),
        'permissions', COALESCE(
            (
                SELECT JSON_ARRAYAGG(JSON_OBJECT(
                    'permissionId', CAST(p.id AS CHAR),
                    'code', p.permission_code,
                    'effect', rp.effect
                ))
                FROM un_plat_role_permission rp
                JOIN un_plat_permission p ON p.id = rp.permission_id
                WHERE rp.role_id = r.id
            ),
            JSON_ARRAY()
        )
    ),
    NULL,
    r.updated_at,
    r.updated_by,
    r.updated_at
FROM un_plat_role r
JOIN un_plat_data_scope ds ON ds.id = r.data_scope_id;

UPDATE un_plat_authz_version
SET checksum = SHA2(CAST(snapshot_json AS CHAR), 256);

ALTER TABLE un_plat_authz_version
    MODIFY COLUMN checksum CHAR(64) NOT NULL;

CREATE TABLE un_plat_role_draft (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    role_id BIGINT NOT NULL,
    draft_version BIGINT NOT NULL,
    base_published_version BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    draft_json JSON NOT NULL,
    check_result_json JSON NULL,
    checksum CHAR(64) NULL,
    checked_at DATETIME(3) NULL,
    checked_by BIGINT NULL,
    published_at DATETIME(3) NULL,
    published_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_role_draft_role (role_id),
    UNIQUE KEY uk_plat_role_draft_scope_role (scope_type, scope_key, role_id),
    KEY idx_plat_role_draft_state (scope_type, scope_key, status, updated_at),
    CONSTRAINT fk_plat_role_draft_role
        FOREIGN KEY (scope_type, scope_key, role_id)
        REFERENCES un_plat_role (scope_type, scope_key, id),
    CONSTRAINT ck_plat_role_draft_scope CHECK (
        (scope_type = 'PLATFORM' AND scope_key = 0 AND system_id IS NULL)
        OR
        (scope_type = 'SYSTEM' AND scope_key = system_id AND system_id IS NOT NULL)
    ),
    CONSTRAINT ck_plat_role_draft_versions CHECK (
        draft_version > 0 AND base_published_version >= 0
    ),
    CONSTRAINT ck_plat_role_draft_status CHECK (status IN ('DRAFT', 'CHECKED', 'PUBLISHED')),
    CONSTRAINT ck_plat_role_draft_state CHECK (
        (status = 'DRAFT' AND checked_at IS NULL AND checked_by IS NULL
            AND published_at IS NULL AND published_by IS NULL)
        OR
        (status = 'CHECKED' AND checked_at IS NOT NULL AND checked_by IS NOT NULL
            AND checksum IS NOT NULL AND published_at IS NULL AND published_by IS NULL)
        OR
        (status = 'PUBLISHED' AND checked_at IS NOT NULL AND checked_by IS NOT NULL
            AND checksum IS NOT NULL AND published_at IS NOT NULL AND published_by IS NOT NULL)
    ),
    CONSTRAINT ck_plat_role_draft_document CHECK (JSON_TYPE(draft_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_plat_role_draft (
    id, scope_type, scope_key, system_id, role_id,
    draft_version, base_published_version, status,
    draft_json, check_result_json, checksum,
    checked_at, checked_by, published_at, published_by,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    av.id,
    av.scope_type,
    av.scope_key,
    av.system_id,
    av.role_id,
    av.version_no,
    av.version_no,
    'PUBLISHED',
    av.snapshot_json,
    JSON_OBJECT('valid', TRUE, 'source', 'V1_BACKFILL'),
    av.checksum,
    av.published_at,
    av.published_by,
    av.published_at,
    av.published_by,
    av.created_at,
    av.published_by,
    av.published_at,
    av.published_by,
    0
FROM un_plat_authz_version av;

CREATE TABLE un_plat_access_request (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    target_tenant_id BIGINT NULL,
    target_tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(target_tenant_id, 0)) STORED,
    requested_role_id BIGINT NULL,
    request_reason VARCHAR(1000) NOT NULL,
    status VARCHAR(24) NOT NULL,
    active_request_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN status = 'SUBMITTED' THEN 1 ELSE NULL END
    ) STORED,
    reviewed_at DATETIME(3) NULL,
    reviewed_by BIGINT NULL,
    decision_reason VARCHAR(1000) NULL,
    resolved_member_id BIGINT NULL,
    expires_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_access_request_active
        (account_id, system_id, target_tenant_key, active_request_marker),
    KEY idx_plat_access_request_review (system_id, status, created_at),
    KEY idx_plat_access_request_account (account_id, status, updated_at),
    CONSTRAINT fk_plat_access_request_account FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_plat_access_request_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_plat_access_request_tenant
        FOREIGN KEY (system_id, target_tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT fk_plat_access_request_role
        FOREIGN KEY (system_id, requested_role_id)
        REFERENCES un_plat_role (system_id, id),
    CONSTRAINT fk_plat_access_request_member
        FOREIGN KEY (system_id, resolved_member_id)
        REFERENCES un_plat_member (system_id, id),
    CONSTRAINT ck_plat_access_request_status CHECK (
        status IN ('SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_plat_access_request_review CHECK (
        (status = 'SUBMITTED' AND reviewed_at IS NULL AND reviewed_by IS NULL)
        OR
        (status IN ('APPROVED', 'REJECTED') AND reviewed_at IS NOT NULL AND reviewed_by IS NOT NULL)
        OR status IN ('CANCELLED', 'EXPIRED')
    ),
    CONSTRAINT ck_plat_access_request_resolution CHECK (
        (status = 'APPROVED' AND resolved_member_id IS NOT NULL)
        OR (status <> 'APPROVED' AND resolved_member_id IS NULL)
    ),
    CONSTRAINT ck_plat_access_request_expiry CHECK (expires_at IS NULL OR expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_quota (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    tenant_key BIGINT GENERATED ALWAYS AS (COALESCE(tenant_id, 0)) STORED,
    quota_key VARCHAR(96) NOT NULL,
    soft_limit BIGINT NULL,
    hard_limit BIGINT NOT NULL,
    used_value BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_quota_scope (system_id, tenant_key, quota_key),
    KEY idx_plat_quota_status (system_id, tenant_id, status),
    CONSTRAINT fk_plat_quota_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_plat_quota_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT ck_plat_quota_scope CHECK (
        (scope_type = 'SYSTEM' AND tenant_id IS NULL)
        OR (scope_type = 'TENANT' AND tenant_id IS NOT NULL)
    ),
    CONSTRAINT ck_plat_quota_values CHECK (
        hard_limit >= 0 AND used_value >= 0
        AND (soft_limit IS NULL OR (soft_limit >= 0 AND soft_limit <= hard_limit))
    ),
    CONSTRAINT ck_plat_quota_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_tenant_domain (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    domain_name VARCHAR(253) NOT NULL,
    domain_normalized VARCHAR(253)
        GENERATED ALWAYS AS (LOWER(TRIM(domain_name))) STORED,
    status VARCHAR(24) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    active_primary_tenant_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN is_primary = 1 AND status = 'VERIFIED' AND deleted_at IS NULL THEN tenant_id ELSE NULL END
    ) STORED,
    verification_token_hash CHAR(64) NULL,
    verified_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_tenant_domain_name (domain_normalized),
    UNIQUE KEY uk_plat_tenant_domain_primary (active_primary_tenant_id),
    KEY idx_plat_tenant_domain_tenant (system_id, tenant_id, status),
    CONSTRAINT fk_plat_tenant_domain_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT ck_plat_tenant_domain_status CHECK (status IN ('PENDING', 'VERIFIED', 'DISABLED')),
    CONSTRAINT ck_plat_tenant_domain_primary CHECK (is_primary IN (0, 1)),
    CONSTRAINT ck_plat_tenant_domain_verified CHECK (
        (status = 'VERIFIED' AND verified_at IS NOT NULL)
        OR (status <> 'VERIFIED' AND verified_at IS NULL AND is_primary = 0)
    ),
    CONSTRAINT ck_plat_tenant_domain_name CHECK (
        CHAR_LENGTH(TRIM(domain_name)) BETWEEN 1 AND 253
        AND TRIM(domain_name) NOT LIKE '% %'
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_system_setting (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    setting_key VARCHAR(128) NOT NULL,
    value_kind VARCHAR(24) NOT NULL,
    value_json JSON NULL,
    secret_ref VARCHAR(255) NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_system_setting_key (system_id, setting_key),
    KEY idx_plat_system_setting_status (system_id, status, updated_at),
    CONSTRAINT fk_plat_system_setting_system FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT ck_plat_system_setting_value CHECK (
        (value_kind = 'JSON' AND value_json IS NOT NULL AND secret_ref IS NULL)
        OR (value_kind = 'SECRET_REF' AND value_json IS NULL AND secret_ref IS NOT NULL)
    ),
    CONSTRAINT ck_plat_system_setting_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Snapshot the epoch and current relational grants for every surviving V1
-- context. permission_version remains as a synchronized compatibility column.
ALTER TABLE un_plat_context_session
    ADD COLUMN authz_epoch BIGINT NULL AFTER permission_version,
    ADD COLUMN role_snapshot_json JSON NULL AFTER permissions_json,
    ADD COLUMN data_scope_snapshot_json JSON NULL AFTER role_snapshot_json,
    ADD COLUMN authz_scope_key BIGINT
        GENERATED ALWAYS AS (
            CASE WHEN context_type = 'PLATFORM' THEN 0 ELSE system_id END
        ) STORED AFTER data_scope_snapshot_json;

UPDATE un_plat_context_session cs
SET cs.authz_epoch = cs.permission_version,
    cs.role_snapshot_json = CASE
        WHEN cs.context_type = 'PLATFORM' THEN JSON_ARRAY()
        ELSE COALESCE(
            (
                SELECT JSON_ARRAYAGG(JSON_OBJECT(
                    'roleId', CAST(r.id AS CHAR),
                    'publishedVersion', r.published_version
                ))
                FROM un_plat_member_role mr
                JOIN un_plat_role r ON r.id = mr.role_id
                WHERE mr.member_id = cs.member_id
                  AND mr.system_id = cs.system_id
                  AND mr.valid_from <= cs.issued_at
                  AND (mr.valid_until IS NULL OR mr.valid_until > cs.issued_at)
                  AND (mr.tenant_id IS NULL OR mr.tenant_id = cs.tenant_id)
            ),
            JSON_ARRAY()
        )
    END,
    cs.data_scope_snapshot_json = CASE
        WHEN cs.context_type = 'PLATFORM' THEN JSON_ARRAY()
        ELSE COALESCE(
            (
                SELECT JSON_ARRAYAGG(JSON_OBJECT(
                    'roleId', CAST(r.id AS CHAR),
                    'dataScopeId', CAST(ds.id AS CHAR),
                    'kind', ds.scope_kind
                ))
                FROM un_plat_member_role mr
                JOIN un_plat_role r ON r.id = mr.role_id
                JOIN un_plat_data_scope ds ON ds.id = r.data_scope_id
                WHERE mr.member_id = cs.member_id
                  AND mr.system_id = cs.system_id
                  AND mr.valid_from <= cs.issued_at
                  AND (mr.valid_until IS NULL OR mr.valid_until > cs.issued_at)
                  AND (mr.tenant_id IS NULL OR mr.tenant_id = cs.tenant_id)
            ),
            JSON_ARRAY()
        )
    END;

ALTER TABLE un_plat_context_session
    DROP FOREIGN KEY fk_plat_context_tenant,
    DROP FOREIGN KEY fk_plat_context_member,
    MODIFY COLUMN authz_epoch BIGINT NOT NULL DEFAULT 1,
    MODIFY COLUMN role_snapshot_json JSON NOT NULL DEFAULT (JSON_ARRAY()),
    MODIFY COLUMN data_scope_snapshot_json JSON NOT NULL DEFAULT (JSON_ARRAY()),
    ADD CONSTRAINT fk_plat_context_scoped_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    ADD CONSTRAINT fk_plat_context_scoped_member
        FOREIGN KEY (system_id, member_id, account_id)
        REFERENCES un_plat_member (system_id, id, account_id),
    ADD CONSTRAINT fk_plat_context_authz_scope
        FOREIGN KEY (context_type, authz_scope_key)
        REFERENCES un_plat_authz_epoch (scope_type, scope_key),
    ADD CONSTRAINT ck_plat_context_authz_epoch CHECK (
        authz_epoch > 0 AND permission_version = authz_epoch
    ),
    ADD CONSTRAINT ck_plat_context_role_snapshot CHECK (JSON_TYPE(role_snapshot_json) = 'ARRAY'),
    ADD CONSTRAINT ck_plat_context_data_scope_snapshot CHECK (JSON_TYPE(data_scope_snapshot_json) = 'ARRAY');
