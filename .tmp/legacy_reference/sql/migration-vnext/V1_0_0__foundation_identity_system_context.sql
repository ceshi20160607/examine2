-- P1 foundation only: registration/login/session plus first-system bootstrap.
-- The VNext profile loads this location independently from the legacy migration chain.

CREATE TABLE un_sys_idempotency (
    id BIGINT NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_key VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    status VARCHAR(24) NOT NULL,
    response_http_status INT NULL,
    response_code VARCHAR(64) NULL,
    response_body MEDIUMTEXT NULL,
    locked_until DATETIME(3) NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_idempotency_scope_key (scope_type, scope_key, idempotency_key),
    KEY idx_vnext_idempotency_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_audit_security (
    id BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    account_id BIGINT NULL,
    account_hint VARCHAR(160) NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    source_type VARCHAR(24) NOT NULL,
    remote_address VARCHAR(64) NULL,
    user_agent VARCHAR(500) NULL,
    request_id VARCHAR(64) NOT NULL,
    trace_id VARCHAR(64) NOT NULL,
    result VARCHAR(24) NOT NULL,
    failure_code VARCHAR(64) NULL,
    detail_json JSON NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_vnext_audit_security_account (account_id, created_at),
    KEY idx_vnext_audit_security_request (request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_account (
    id BIGINT NOT NULL,
    account_code VARCHAR(64) NOT NULL,
    username VARCHAR(80) NOT NULL,
    username_normalized VARCHAR(80) NOT NULL,
    email VARCHAR(254) NULL,
    email_normalized VARCHAR(254) NULL,
    phone VARCHAR(32) NULL,
    display_name VARCHAR(120) NOT NULL,
    locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN',
    time_zone VARCHAR(64) NOT NULL DEFAULT 'Asia/Shanghai',
    status VARCHAR(24) NOT NULL,
    last_login_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_account_code (account_code),
    UNIQUE KEY uk_vnext_account_username (username_normalized),
    UNIQUE KEY uk_vnext_account_email (email_normalized),
    KEY idx_vnext_account_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_credential (
    id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    credential_type VARCHAR(24) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(32) NOT NULL,
    password_parameters VARCHAR(255) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until DATETIME(3) NULL,
    password_changed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_credential_account_type (account_id, credential_type),
    CONSTRAINT fk_vnext_credential_account
        FOREIGN KEY (account_id) REFERENCES un_plat_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_system (
    id BIGINT NOT NULL,
    system_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description VARCHAR(1000) NULL,
    status VARCHAR(24) NOT NULL,
    tenant_mode VARCHAR(16) NOT NULL,
    owner_account_id BIGINT NOT NULL,
    permission_version BIGINT NOT NULL DEFAULT 1,
    initialized_at DATETIME(3) NULL,
    init_failure_code VARCHAR(64) NULL,
    init_failed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    archived_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    tombstone_reason VARCHAR(1000) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_system_code (system_code),
    KEY idx_vnext_system_owner (owner_account_id, status),
    CONSTRAINT fk_vnext_system_owner
        FOREIGN KEY (owner_account_id) REFERENCES un_plat_account (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_tenant (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_code VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(24) NOT NULL,
    active_default_system_id BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN is_default = 1 AND status = 'ACTIVE' AND deleted_at IS NULL THEN system_id
                ELSE NULL
            END
        ) STORED,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_tenant_code (system_id, tenant_code),
    UNIQUE KEY uk_vnext_tenant_system_id (system_id, id),
    UNIQUE KEY uk_vnext_tenant_one_active_default (active_default_system_id),
    KEY idx_vnext_tenant_default (system_id, is_default, status),
    CONSTRAINT ck_vnext_tenant_default_state CHECK (
        is_default = 0 OR (status = 'ACTIVE' AND deleted_at IS NULL)
    ),
    CONSTRAINT fk_vnext_tenant_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_member (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    member_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    default_tenant_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL,
    joined_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_member_account (system_id, account_id),
    UNIQUE KEY uk_vnext_member_code (system_id, member_code),
    UNIQUE KEY uk_vnext_member_system_id (system_id, id),
    UNIQUE KEY uk_vnext_member_system_account_id (system_id, id, account_id),
    CONSTRAINT fk_vnext_member_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_vnext_member_account
        FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_vnext_member_tenant
        FOREIGN KEY (system_id, default_tenant_id) REFERENCES un_plat_tenant (system_id, id)
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
    UNIQUE KEY uk_vnext_member_tenant (system_id, member_id, tenant_id),
    KEY idx_vnext_member_tenant_access (system_id, tenant_id, status, member_id),
    CONSTRAINT fk_vnext_member_tenant_member
        FOREIGN KEY (system_id, member_id) REFERENCES un_plat_member (system_id, id),
    CONSTRAINT fk_vnext_member_tenant_tenant
        FOREIGN KEY (system_id, tenant_id) REFERENCES un_plat_tenant (system_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_data_scope (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
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
    UNIQUE KEY uk_vnext_data_scope_code (scope_type, scope_key, scope_code),
    CONSTRAINT fk_vnext_data_scope_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_role (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    role_code VARCHAR(64) NOT NULL,
    name VARCHAR(120) NOT NULL,
    role_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    permission_version BIGINT NOT NULL DEFAULT 1,
    data_scope_id BIGINT NULL,
    is_builtin BOOLEAN NOT NULL DEFAULT FALSE,
    published_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    deleted_at DATETIME(3) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_role_code (scope_type, scope_key, role_code),
    UNIQUE KEY uk_vnext_role_scope_id (scope_type, scope_key, id),
    UNIQUE KEY uk_vnext_role_system_id (system_id, id),
    CONSTRAINT fk_vnext_role_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_vnext_role_data_scope
        FOREIGN KEY (data_scope_id) REFERENCES un_plat_data_scope (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_permission (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    system_id BIGINT NULL,
    permission_code VARCHAR(128) NOT NULL,
    name VARCHAR(160) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_permission_code (scope_type, scope_key, permission_code),
    UNIQUE KEY uk_vnext_permission_scope_id (scope_type, scope_key, id),
    CONSTRAINT fk_vnext_permission_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_role_permission (
    id BIGINT NOT NULL,
    scope_type VARCHAR(16) NOT NULL,
    scope_key BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    effect VARCHAR(8) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_role_permission (role_id, permission_id),
    CONSTRAINT fk_vnext_role_permission_role
        FOREIGN KEY (scope_type, scope_key, role_id)
        REFERENCES un_plat_role (scope_type, scope_key, id),
    CONSTRAINT fk_vnext_role_permission_permission
        FOREIGN KEY (scope_type, scope_key, permission_id)
        REFERENCES un_plat_permission (scope_type, scope_key, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_member_role (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    tenant_id BIGINT NULL,
    valid_from DATETIME(3) NOT NULL,
    valid_until DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_member_role (system_id, member_id, role_id, tenant_id),
    CONSTRAINT fk_vnext_member_role_member
        FOREIGN KEY (system_id, member_id) REFERENCES un_plat_member (system_id, id),
    CONSTRAINT fk_vnext_member_role_role
        FOREIGN KEY (system_id, role_id) REFERENCES un_plat_role (system_id, id),
    CONSTRAINT fk_vnext_member_role_tenant
        FOREIGN KEY (system_id, tenant_id) REFERENCES un_plat_tenant (system_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    UNIQUE KEY uk_vnext_account_role (account_id, role_id),
    CONSTRAINT fk_vnext_account_role_account
        FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_vnext_account_role_role
        FOREIGN KEY (scope_type, scope_key, role_id)
        REFERENCES un_plat_role (scope_type, scope_key, id)
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
    UNIQUE KEY uk_vnext_authz_epoch_scope (scope_type, scope_key),
    CONSTRAINT fk_vnext_authz_epoch_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_context_session (
    id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    context_type VARCHAR(16) NOT NULL,
    account_id BIGINT NOT NULL,
    system_id BIGINT NULL,
    tenant_id BIGINT NULL,
    member_id BIGINT NULL,
    permission_version BIGINT NOT NULL,
    authz_epoch BIGINT NOT NULL,
    permissions_json JSON NOT NULL,
    role_snapshot_json JSON NOT NULL,
    data_scope_snapshot_json JSON NOT NULL,
    status VARCHAR(24) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    last_seen_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_context_token (token_hash),
    KEY idx_vnext_context_account (account_id, status, expires_at),
    CONSTRAINT fk_vnext_context_account
        FOREIGN KEY (account_id) REFERENCES un_plat_account (id),
    CONSTRAINT fk_vnext_context_system
        FOREIGN KEY (system_id) REFERENCES un_plat_system (id),
    CONSTRAINT fk_vnext_context_tenant
        FOREIGN KEY (system_id, tenant_id) REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT fk_vnext_context_member
        FOREIGN KEY (system_id, member_id, account_id)
        REFERENCES un_plat_member (system_id, id, account_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_refresh_token (
    id BIGINT NOT NULL,
    context_session_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    token_family CHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    issued_at DATETIME(3) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    revoked_at DATETIME(3) NULL,
    rotated_to_id BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_vnext_refresh_token_hash (token_hash),
    KEY idx_vnext_refresh_family (token_family, status, expires_at),
    CONSTRAINT fk_vnext_refresh_session
        FOREIGN KEY (context_session_id) REFERENCES un_plat_context_session (id),
    CONSTRAINT fk_vnext_refresh_rotated
        FOREIGN KEY (rotated_to_id) REFERENCES un_plat_refresh_token (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
