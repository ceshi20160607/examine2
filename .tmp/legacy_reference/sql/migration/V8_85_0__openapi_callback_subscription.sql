-- Cycle114 application-owned OpenAPI callback delivery closure.
-- OpenAPI application-owned callbacks. Secret material is never persisted;
-- only versioned SecretRefs are stored.

CREATE TABLE un_openapi_callback_subscription (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_config_version INT UNSIGNED NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_callback_scope_id (system_id, tenant_id, application_id, id),
    KEY idx_openapi_callback_application (system_id, tenant_id, application_id, status, updated_at, id),
    CONSTRAINT fk_openapi_callback_application
        FOREIGN KEY (system_id, tenant_id, application_id)
        REFERENCES un_openapi_application (system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_callback_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND application_id > 0
        AND created_by > 0 AND updated_by > 0
    ),
    CONSTRAINT ck_openapi_callback_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_openapi_callback_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_openapi_callback_config_version CHECK (
        current_config_version BETWEEN 1 AND 2147483647
    ),
    CONSTRAINT ck_openapi_callback_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_callback_version (
    id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    config_version INT UNSIGNED NOT NULL,
    endpoint_url VARCHAR(1024) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_types_json JSON NOT NULL,
    secret_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    signing_secret_version INT UNSIGNED NOT NULL,
    max_attempts INT UNSIGNED NOT NULL,
    base_backoff_seconds INT UNSIGNED NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    activated_at DATETIME(3) NOT NULL,
    retired_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_callback_version (subscription_id, config_version),
    KEY idx_openapi_callback_version_status (subscription_id, status, config_version),
    CONSTRAINT fk_openapi_callback_version_subscription
        FOREIGN KEY (subscription_id) REFERENCES un_openapi_callback_subscription (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_callback_version_identity CHECK (
        id > 0 AND subscription_id > 0 AND created_by > 0
        AND config_version BETWEEN 1 AND 2147483647
        AND signing_secret_version BETWEEN 1 AND 2147483647
    ),
    CONSTRAINT ck_openapi_callback_endpoint CHECK (
        CHAR_LENGTH(endpoint_url) BETWEEN 9 AND 1024
        AND endpoint_url REGEXP '^https://'
        AND LOCATE('@', endpoint_url) = 0
        AND LOCATE('?', endpoint_url) = 0
        AND LOCATE('#', endpoint_url) = 0
    ),
    CONSTRAINT ck_openapi_callback_events CHECK (
        JSON_TYPE(event_types_json) = 'ARRAY'
        AND JSON_LENGTH(event_types_json) BETWEEN 1 AND 32
    ),
    CONSTRAINT ck_openapi_callback_secret_ref CHECK (
        CHAR_LENGTH(secret_ref) BETWEEN 3 AND 512
        AND secret_ref = TRIM(secret_ref)
        AND secret_ref REGEXP '^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'
    ),
    CONSTRAINT ck_openapi_callback_retry CHECK (
        max_attempts BETWEEN 1 AND 10 AND base_backoff_seconds BETWEEN 1 AND 3600
    ),
    CONSTRAINT ck_openapi_callback_version_status CHECK (status IN ('ACTIVE', 'RETIRED')),
    CONSTRAINT ck_openapi_callback_version_lifecycle CHECK (
        (status = 'ACTIVE' AND retired_at IS NULL)
        OR (status = 'RETIRED' AND retired_at IS NOT NULL AND retired_at >= activated_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_callback_delivery (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    application_id BIGINT NOT NULL,
    subscription_id BIGINT NOT NULL,
    callback_version_id BIGINT NOT NULL,
    event_id VARCHAR(160) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_json JSON NOT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL,
    last_http_status SMALLINT UNSIGNED NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NULL,
    version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_callback_delivery_dedupe (subscription_id, event_id, event_type),
    KEY idx_openapi_callback_delivery_scope (
        system_id, tenant_id, application_id, subscription_id, created_at, id
    ),
    KEY idx_openapi_callback_delivery_status (status, updated_at, id),
    KEY idx_openapi_callback_delivery_request (request_id),
    KEY idx_openapi_callback_delivery_trace (trace_id, created_at),
    CONSTRAINT fk_openapi_callback_delivery_subscription
        FOREIGN KEY (system_id, tenant_id, application_id, subscription_id)
        REFERENCES un_openapi_callback_subscription (system_id, tenant_id, application_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_openapi_callback_delivery_version
        FOREIGN KEY (callback_version_id) REFERENCES un_openapi_callback_version (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_callback_delivery_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND application_id > 0
        AND subscription_id > 0 AND callback_version_id > 0
    ),
    CONSTRAINT ck_openapi_callback_delivery_event CHECK (
        CHAR_LENGTH(event_id) BETWEEN 1 AND 160
        AND event_type REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
    ),
    CONSTRAINT ck_openapi_callback_delivery_payload CHECK (
        JSON_TYPE(payload_json) = 'OBJECT'
        AND payload_hash REGEXP '^[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_openapi_callback_delivery_status CHECK (
        status IN ('PENDING', 'RETRYING', 'SUCCEEDED', 'FAILED')
    ),
    CONSTRAINT ck_openapi_callback_delivery_attempt CHECK (attempt_count BETWEEN 0 AND 10),
    CONSTRAINT ck_openapi_callback_delivery_http CHECK (
        last_http_status IS NULL OR last_http_status BETWEEN 100 AND 599
    ),
    CONSTRAINT ck_openapi_callback_delivery_failure CHECK (
        failure_code IS NULL OR failure_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
    ),
    CONSTRAINT ck_openapi_callback_delivery_completion CHECK (
        (status IN ('PENDING', 'RETRYING') AND completed_at IS NULL)
        OR (status IN ('SUCCEEDED', 'FAILED') AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_openapi_callback_delivery_version_value CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_openapi_callback_attempt (
    id BIGINT NOT NULL,
    delivery_id BIGINT NOT NULL,
    attempt_no INT UNSIGNED NOT NULL,
    outcome VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    http_status SMALLINT UNSIGNED NULL,
    duration_ms BIGINT UNSIGNED NOT NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    started_at DATETIME(3) NOT NULL,
    completed_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_openapi_callback_attempt (delivery_id, attempt_no),
    KEY idx_openapi_callback_attempt_delivery (delivery_id, completed_at, id),
    CONSTRAINT fk_openapi_callback_attempt_delivery
        FOREIGN KEY (delivery_id) REFERENCES un_openapi_callback_delivery (id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_openapi_callback_attempt_identity CHECK (
        id > 0 AND delivery_id > 0 AND attempt_no BETWEEN 1 AND 10
    ),
    CONSTRAINT ck_openapi_callback_attempt_outcome CHECK (
        outcome IN ('SUCCEEDED', 'RETRYABLE_FAILURE', 'TERMINAL_FAILURE')
    ),
    CONSTRAINT ck_openapi_callback_attempt_http CHECK (
        http_status IS NULL OR http_status BETWEEN 100 AND 599
    ),
    CONSTRAINT ck_openapi_callback_attempt_duration CHECK (duration_ms <= 86400000),
    CONSTRAINT ck_openapi_callback_attempt_failure CHECK (
        failure_code IS NULL OR failure_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
    ),
    CONSTRAINT ck_openapi_callback_attempt_time CHECK (completed_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
