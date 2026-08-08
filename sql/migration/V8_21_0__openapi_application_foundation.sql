-- Internal management foundation for authenticated OpenAPI applications.
-- Credentials store only an external SecretRef. No credential secret material
-- is persisted in this schema.

CREATE TABLE un_openapi_application (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    service_member_id BIGINT NOT NULL,
    app_key VARCHAR(96) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scopes_json JSON NOT NULL,
    ip_allowlist_json JSON NOT NULL,
    rate_limit_per_minute INT UNSIGNED NOT NULL,
    current_credential_version INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_application_app_key (app_key),
    UNIQUE KEY uk_openapi_application_scope_id (system_id, tenant_id, id),
    KEY idx_openapi_application_tenant_status (
        system_id, tenant_id, status, updated_at, id
    ),
    KEY idx_openapi_application_service_member (
        system_id, service_member_id, tenant_id, status
    ),
    CONSTRAINT fk_openapi_application_system
        FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_openapi_application_tenant
        FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_openapi_application_member
        FOREIGN KEY (system_id, service_member_id)
        REFERENCES un_plat_member (system_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_openapi_application_member_tenant
        FOREIGN KEY (system_id, service_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (system_id, member_id, tenant_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_application_identity CHECK (
        id > 0
        AND system_id > 0
        AND tenant_id > 0
        AND service_member_id > 0
        AND created_by > 0
        AND updated_by > 0
    ),
    CONSTRAINT ck_openapi_application_app_key CHECK (
        CHAR_LENGTH(app_key) BETWEEN 16 AND 96
        AND app_key REGEXP '^[A-Za-z0-9_-]+$'
    ),
    CONSTRAINT ck_openapi_application_name CHECK (
        CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160
    ),
    CONSTRAINT ck_openapi_application_status CHECK (
        status IN ('ACTIVE', 'DISABLED')
    ),
    CONSTRAINT ck_openapi_application_scopes CHECK (
        JSON_TYPE(scopes_json) = 'ARRAY'
        AND JSON_LENGTH(scopes_json) BETWEEN 1 AND 64
    ),
    CONSTRAINT ck_openapi_application_ip_allowlist CHECK (
        JSON_TYPE(ip_allowlist_json) = 'ARRAY'
        AND JSON_LENGTH(ip_allowlist_json) <= 128
    ),
    CONSTRAINT ck_openapi_application_rate_limit CHECK (
        rate_limit_per_minute BETWEEN 1 AND 60000
    ),
    CONSTRAINT ck_openapi_application_credential_version CHECK (
        current_credential_version BETWEEN 1 AND 2147483647
    ),
    CONSTRAINT ck_openapi_application_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_credential (
    id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    credential_version INT UNSIGNED NOT NULL,
    secret_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activated_at DATETIME(3) NOT NULL,
    revoked_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_credential_application_version (
        application_id, credential_version
    ),
    KEY idx_openapi_credential_application_status (
        application_id, status, credential_version
    ),
    CONSTRAINT fk_openapi_credential_application
        FOREIGN KEY (application_id)
        REFERENCES un_openapi_application (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_credential_identity CHECK (
        id > 0 AND application_id > 0 AND created_by > 0
    ),
    CONSTRAINT ck_openapi_credential_version CHECK (
        credential_version BETWEEN 1 AND 2147483647
    ),
    CONSTRAINT ck_openapi_credential_secret_ref CHECK (
        CHAR_LENGTH(secret_ref) BETWEEN 3 AND 512
        AND secret_ref = TRIM(secret_ref)
        AND secret_ref REGEXP '^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'
    ),
    CONSTRAINT ck_openapi_credential_status CHECK (
        status IN ('ACTIVE', 'REVOKED')
    ),
    CONSTRAINT ck_openapi_credential_lifecycle CHECK (
        (status = 'ACTIVE' AND revoked_at IS NULL)
        OR
        (status = 'REVOKED' AND revoked_at IS NOT NULL
            AND revoked_at >= activated_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_nonce (
    application_id BIGINT NOT NULL,
    credential_version INT UNSIGNED NOT NULL,
    nonce VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (application_id, credential_version, nonce),
    KEY idx_openapi_nonce_expiry (expires_at, application_id),
    CONSTRAINT fk_openapi_nonce_credential
        FOREIGN KEY (application_id, credential_version)
        REFERENCES un_openapi_credential (application_id, credential_version)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_nonce_value CHECK (
        CHAR_LENGTH(nonce) BETWEEN 16 AND 128
        AND nonce REGEXP '^[A-Za-z0-9._~-]+$'
    ),
    CONSTRAINT ck_openapi_nonce_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_rate_bucket (
    application_id BIGINT NOT NULL,
    window_start DATETIME(3) NOT NULL,
    request_count INT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (application_id, window_start),
    KEY idx_openapi_rate_bucket_window (window_start, application_id),
    CONSTRAINT fk_openapi_rate_bucket_application
        FOREIGN KEY (application_id)
        REFERENCES un_openapi_application (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_rate_bucket_window CHECK (
        SECOND(window_start) = 0 AND MICROSECOND(window_start) = 0
    ),
    CONSTRAINT ck_openapi_rate_bucket_count CHECK (
        request_count BETWEEN 0 AND 4294967295
    ),
    CONSTRAINT ck_openapi_rate_bucket_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_call_log (
    id BIGINT NOT NULL,
    application_id BIGINT NULL,
    app_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    credential_version INT UNSIGNED NULL,
    route_template VARCHAR(255) NOT NULL,
    request_method VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_category VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    http_status SMALLINT UNSIGNED NOT NULL,
    latency_ms BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    observed_ip VARBINARY(16) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_openapi_call_application (
        application_id, created_at, id
    ),
    KEY idx_openapi_call_app_key (
        app_key_hash, created_at, id
    ),
    KEY idx_openapi_call_result (
        result_category, created_at, id
    ),
    KEY idx_openapi_call_request (request_id),
    KEY idx_openapi_call_trace (trace_id, created_at),
    CONSTRAINT fk_openapi_call_application
        FOREIGN KEY (application_id)
        REFERENCES un_openapi_application (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_call_identity CHECK (
        id > 0
        AND CHAR_LENGTH(app_key_hash) = 64
        AND app_key_hash REGEXP '^[0-9a-f]{64}$'
        AND (credential_version IS NULL
            OR credential_version BETWEEN 1 AND 2147483647)
    ),
    CONSTRAINT ck_openapi_call_route CHECK (
        CHAR_LENGTH(route_template) BETWEEN 1 AND 255
        AND LEFT(route_template, 1) = '/'
        AND LOCATE('?', route_template) = 0
    ),
    CONSTRAINT ck_openapi_call_method CHECK (
        request_method IN (
            'GET', 'HEAD', 'POST', 'PUT', 'PATCH', 'DELETE', 'OPTIONS'
        )
    ),
    CONSTRAINT ck_openapi_call_result CHECK (
        CHAR_LENGTH(result_category) BETWEEN 2 AND 32
        AND result_category REGEXP '^[A-Z][A-Z0-9_]+$'
    ),
    CONSTRAINT ck_openapi_call_metrics CHECK (
        http_status BETWEEN 100 AND 599
        AND latency_ms <= 86400000
    ),
    CONSTRAINT ck_openapi_call_correlation CHECK (
        CHAR_LENGTH(request_id) BETWEEN 1 AND 64
        AND CHAR_LENGTH(trace_id) BETWEEN 1 AND 64
    ),
    CONSTRAINT ck_openapi_call_ip CHECK (
        OCTET_LENGTH(observed_ip) IN (4, 16)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission (
    id, scope_type, scope_key, system_id, permission_code, name, resource_type, status,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    CAST(base.min_id AS SIGNED) - CAST(ROW_NUMBER() OVER (
        ORDER BY system_row.id
    ) AS SIGNED),
    'SYSTEM',
    system_row.id,
    system_row.id,
    'openapi.application.manage',
    'Manage OpenAPI applications',
    'ACTION',
    'ACTIVE',
    UTC_TIMESTAMP(3),
    system_row.created_by,
    UTC_TIMESTAMP(3),
    system_row.created_by,
    0
FROM un_plat_system system_row
CROSS JOIN (
    SELECT LEAST(COALESCE(MIN(id), 0), 0) AS min_id
    FROM un_plat_permission
) base
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_permission existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
      AND existing.permission_code = 'openapi.application.manage'
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
 AND permission_row.permission_code = 'openapi.application.manage'
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

-- Fill any legacy system missing its epoch row before invalidating cached
-- authorization snapshots.
INSERT INTO un_plat_authz_epoch (
    id, scope_type, scope_key, system_id, epoch,
    created_at, created_by, updated_at, updated_by, version
)
SELECT
    system_row.id,
    'SYSTEM',
    system_row.id,
    system_row.id,
    GREATEST(system_row.permission_version, 1),
    UTC_TIMESTAMP(3),
    system_row.created_by,
    UTC_TIMESTAMP(3),
    system_row.updated_by,
    0
FROM un_plat_system system_row
WHERE NOT EXISTS (
    SELECT 1
    FROM un_plat_authz_epoch existing
    WHERE existing.scope_type = 'SYSTEM'
      AND existing.scope_key = system_row.id
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
