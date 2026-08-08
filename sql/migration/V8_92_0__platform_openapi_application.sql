-- Platform OpenAPI is deliberately isolated from tenant/system OpenAPI.
-- Only SecretRef locators are persisted; secret material never enters MySQL.
CREATE TABLE un_platform_openapi_application (
    id BIGINT NOT NULL,
    service_account_id BIGINT NOT NULL,
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
    UNIQUE KEY uk_platform_openapi_application_app_key (app_key),
    KEY idx_platform_openapi_application_account (service_account_id,status,updated_at,id),
    CONSTRAINT fk_platform_openapi_application_account FOREIGN KEY (service_account_id)
        REFERENCES un_plat_account(id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_openapi_application_ids CHECK
        (id > 0 AND service_account_id > 0 AND created_by > 0 AND updated_by > 0),
    CONSTRAINT ck_platform_openapi_application_key CHECK
        (CHAR_LENGTH(app_key) BETWEEN 16 AND 96 AND app_key REGEXP '^[A-Za-z0-9_-]+$'),
    CONSTRAINT ck_platform_openapi_application_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_platform_openapi_application_status CHECK (status IN ('ACTIVE','DISABLED')),
    CONSTRAINT ck_platform_openapi_application_scopes CHECK
        (JSON_TYPE(scopes_json)='ARRAY' AND JSON_LENGTH(scopes_json) BETWEEN 1 AND 64),
    CONSTRAINT ck_platform_openapi_application_ips CHECK
        (JSON_TYPE(ip_allowlist_json)='ARRAY' AND JSON_LENGTH(ip_allowlist_json) <= 128),
    CONSTRAINT ck_platform_openapi_application_rate CHECK (rate_limit_per_minute BETWEEN 1 AND 60000),
    CONSTRAINT ck_platform_openapi_application_credential CHECK
        (current_credential_version BETWEEN 1 AND 2147483647)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_openapi_credential (
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
    UNIQUE KEY uk_platform_openapi_credential_version (application_id,credential_version),
    CONSTRAINT fk_platform_openapi_credential_application FOREIGN KEY (application_id)
        REFERENCES un_platform_openapi_application(id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_openapi_credential_ref CHECK
        (CHAR_LENGTH(secret_ref) BETWEEN 3 AND 512 AND secret_ref=TRIM(secret_ref)
         AND secret_ref REGEXP '^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'),
    CONSTRAINT ck_platform_openapi_credential_status CHECK (status IN ('ACTIVE','REVOKED')),
    CONSTRAINT ck_platform_openapi_credential_lifecycle CHECK
        ((status='ACTIVE' AND revoked_at IS NULL) OR
         (status='REVOKED' AND revoked_at IS NOT NULL AND revoked_at >= activated_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_openapi_nonce (
    application_id BIGINT NOT NULL,
    credential_version INT UNSIGNED NOT NULL,
    nonce VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (application_id,credential_version,nonce),
    KEY idx_platform_openapi_nonce_expiry (expires_at,application_id),
    CONSTRAINT fk_platform_openapi_nonce_credential FOREIGN KEY (application_id,credential_version)
        REFERENCES un_platform_openapi_credential(application_id,credential_version) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_openapi_nonce CHECK
        (CHAR_LENGTH(nonce) BETWEEN 16 AND 128 AND nonce REGEXP '^[A-Za-z0-9._~-]+$'
         AND expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_openapi_rate_bucket (
    application_id BIGINT NOT NULL,
    window_start DATETIME(3) NOT NULL,
    request_count INT UNSIGNED NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (application_id,window_start),
    KEY idx_platform_openapi_rate_window (window_start,application_id),
    CONSTRAINT fk_platform_openapi_rate_application FOREIGN KEY (application_id)
        REFERENCES un_platform_openapi_application(id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_openapi_rate_window CHECK
        (SECOND(window_start)=0 AND MICROSECOND(window_start)=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_platform_openapi_call_log (
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
    KEY idx_platform_openapi_call_application (application_id,created_at,id),
    KEY idx_platform_openapi_call_key (app_key_hash,created_at,id),
    KEY idx_platform_openapi_call_result (result_category,created_at,id),
    KEY idx_platform_openapi_call_request (request_id),
    KEY idx_platform_openapi_call_trace (trace_id,created_at),
    CONSTRAINT fk_platform_openapi_call_application FOREIGN KEY (application_id)
        REFERENCES un_platform_openapi_application(id) ON DELETE RESTRICT,
    CONSTRAINT ck_platform_openapi_call CHECK
        (id > 0 AND CHAR_LENGTH(app_key_hash)=64 AND app_key_hash REGEXP '^[0-9a-f]{64}$'
         AND request_method IN ('GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS')
         AND http_status BETWEEN 100 AND 599 AND latency_ms <= 86400000
         AND OCTET_LENGTH(observed_ip) IN (4,16))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_plat_permission
    (id,scope_type,scope_key,system_id,permission_code,name,resource_type,status,
     created_at,created_by,updated_at,updated_by,version)
SELECT CAST(base.min_id AS SIGNED)-1,'PLATFORM',0,NULL,
       'platform.openapi.application.manage','Manage platform OpenAPI applications',
       'ACTION','ACTIVE',UTC_TIMESTAMP(3),seed.actor_id,UTC_TIMESTAMP(3),seed.actor_id,0
FROM (SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_permission) base
CROSS JOIN (SELECT MIN(id) actor_id FROM un_plat_account) seed
WHERE seed.actor_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM un_plat_permission p WHERE p.scope_type='PLATFORM' AND p.scope_key=0
      AND p.permission_code='platform.openapi.application.manage');

INSERT INTO un_plat_role_permission
    (id,scope_type,scope_key,role_id,permission_id,effect,created_at,created_by)
SELECT CAST(base.min_id AS SIGNED)-CAST(ROW_NUMBER() OVER (ORDER BY r.id) AS SIGNED),
       'PLATFORM',0,r.id,p.id,'ALLOW',UTC_TIMESTAMP(3),r.updated_by
FROM un_plat_role r JOIN un_plat_permission p
  ON p.scope_type='PLATFORM' AND p.scope_key=0
 AND p.permission_code='platform.openapi.application.manage' AND p.status='ACTIVE'
CROSS JOIN (SELECT LEAST(COALESCE(MIN(id),0),0) min_id FROM un_plat_role_permission) base
WHERE r.scope_type='PLATFORM' AND r.scope_key=0 AND r.role_type='ROOT'
  AND r.status='ACTIVE' AND r.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM un_plat_role_permission rp
                  WHERE rp.role_id=r.id AND rp.permission_id=p.id);

UPDATE un_plat_authz_epoch SET epoch=epoch+1,updated_at=UTC_TIMESTAMP(3),version=version+1
WHERE scope_type='PLATFORM' AND scope_key=0;
