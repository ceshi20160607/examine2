-- Cycle115 PL115-A: confirmed system deletion and operable tenant lifecycle control plane.
-- Confirmation secrets are persisted only as SHA-256 hashes. Tenant backup payloads contain
-- plan metadata (tenant/domain/quota/table impacts) is JSON. Business row payloads are
-- stored only inside an authenticated encrypted envelope whose key remains an external SecretRef.

CREATE TABLE un_plat_system_delete_request (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    system_version BIGINT NOT NULL,
    dependency_json JSON NOT NULL,
    blocker_json JSON NOT NULL,
    impact_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmation_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    consumed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    active_system_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'PREVIEWED' THEN system_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plat_system_delete_active (active_system_id),
    KEY idx_plat_system_delete_expiry (status, expires_at),
    CONSTRAINT fk_plat_system_delete_system FOREIGN KEY (system_id)
        REFERENCES un_plat_system (id),
    CONSTRAINT fk_plat_system_delete_actor FOREIGN KEY (created_by)
        REFERENCES un_plat_account (id),
    CONSTRAINT ck_plat_system_delete_status CHECK (
        status IN ('PREVIEWED', 'CONSUMED', 'BLOCKED', 'EXPIRED')),
    CONSTRAINT ck_plat_system_delete_hashes CHECK (
        impact_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND confirmation_token_hash REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_plat_system_delete_state CHECK (
        (status = 'CONSUMED' AND consumed_at IS NOT NULL)
        OR (status <> 'CONSUMED' AND consumed_at IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_plat_tenant_lifecycle_operation (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    source_tenant_id BIGINT NOT NULL,
    target_tenant_id BIGINT NULL,
    plan_operation_id BIGINT NULL,
    operation_type VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reason VARCHAR(1000) NOT NULL,
    snapshot_json JSON NOT NULL,
    result_json JSON NULL,
    snapshot_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    plan_fingerprint CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    confirmation_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    expires_at DATETIME(3) NULL,
    consumed_at DATETIME(3) NULL,
    payload_ciphertext LONGBLOB NULL,
    payload_ciphertext_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    payload_plaintext_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    encryption_key_ref VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    encryption_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    payload_schema_version INT NOT NULL DEFAULT 1,
    database_migration_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    payload_row_count BIGINT NOT NULL DEFAULT 0,
    payload_size_bytes BIGINT NOT NULL DEFAULT 0,
    requested_at DATETIME(3) NOT NULL,
    started_at DATETIME(3) NOT NULL,
    finished_at DATETIME(3) NOT NULL,
    requested_by BIGINT NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_plat_tenant_lifecycle_source (
        system_id, source_tenant_id, operation_type, requested_at),
    KEY idx_plat_tenant_lifecycle_target (
        system_id, target_tenant_id, operation_type, requested_at),
    KEY idx_plat_tenant_lifecycle_plan (plan_operation_id),
    KEY idx_plat_tenant_lifecycle_expiry (status, expires_at),
    CONSTRAINT fk_plat_tenant_lifecycle_source FOREIGN KEY (system_id, source_tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT fk_plat_tenant_lifecycle_target FOREIGN KEY (system_id, target_tenant_id)
        REFERENCES un_plat_tenant (system_id, id),
    CONSTRAINT fk_plat_tenant_lifecycle_plan FOREIGN KEY (plan_operation_id)
        REFERENCES un_plat_tenant_lifecycle_operation (id),
    CONSTRAINT fk_plat_tenant_lifecycle_actor FOREIGN KEY (requested_by)
        REFERENCES un_plat_account (id),
    CONSTRAINT ck_plat_tenant_lifecycle_type CHECK (
        operation_type IN (
            'BACKUP', 'MIGRATION_PREVIEW', 'MIGRATION',
            'RECOVERY_PREVIEW', 'RECOVERY')),
    CONSTRAINT ck_plat_tenant_lifecycle_status CHECK (
        status IN ('PREVIEWED', 'BLOCKED', 'CONSUMED', 'EXPIRED', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT ck_plat_tenant_lifecycle_target CHECK (
        (operation_type IN ('MIGRATION_PREVIEW', 'MIGRATION') AND target_tenant_id IS NOT NULL
            AND target_tenant_id <> source_tenant_id)
        OR (operation_type IN ('BACKUP', 'RECOVERY_PREVIEW', 'RECOVERY')
            AND target_tenant_id IS NULL)),
    CONSTRAINT ck_plat_tenant_lifecycle_checksum CHECK (
        snapshot_checksum REGEXP '^[0-9a-f]{64}$'
        AND plan_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_plat_tenant_lifecycle_preview CHECK (
        (operation_type IN ('MIGRATION_PREVIEW', 'RECOVERY_PREVIEW')
            AND status IN ('PREVIEWED', 'BLOCKED', 'CONSUMED', 'EXPIRED')
            AND expires_at IS NOT NULL
            AND ((status IN ('PREVIEWED', 'CONSUMED') AND confirmation_token_hash IS NOT NULL)
                OR (status IN ('BLOCKED', 'EXPIRED')))
            AND ((status = 'CONSUMED' AND consumed_at IS NOT NULL)
                OR (status <> 'CONSUMED' AND consumed_at IS NULL)))
        OR (operation_type NOT IN ('MIGRATION_PREVIEW', 'RECOVERY_PREVIEW')
            AND expires_at IS NULL AND consumed_at IS NULL
            AND confirmation_token_hash IS NULL)),
    CONSTRAINT ck_plat_tenant_lifecycle_execution CHECK (
        (operation_type = 'BACKUP' AND status = 'SUCCEEDED' AND plan_operation_id IS NULL)
        OR (operation_type IN ('MIGRATION_PREVIEW', 'RECOVERY_PREVIEW')
            AND plan_operation_id IS NULL)
        OR (operation_type IN ('MIGRATION', 'RECOVERY')
            AND status IN ('SUCCEEDED', 'FAILED') AND plan_operation_id IS NOT NULL)),
    CONSTRAINT ck_plat_tenant_lifecycle_payload CHECK (
        (operation_type = 'BACKUP'
            AND payload_ciphertext IS NOT NULL
            AND payload_ciphertext_sha256 REGEXP '^[0-9a-f]{64}$'
            AND payload_plaintext_sha256 REGEXP '^[0-9a-f]{64}$'
            AND encryption_key_ref REGEXP '^[A-Za-z][A-Za-z0-9+.-]{1,31}:[^[:space:]]+$'
            AND encryption_key_version REGEXP '^[0-9a-f]{16}$'
            AND payload_schema_version = 1
            AND payload_row_count >= 0 AND payload_size_bytes > 0)
        OR (operation_type <> 'BACKUP'
            AND payload_ciphertext IS NULL
            AND payload_ciphertext_sha256 IS NULL
            AND payload_plaintext_sha256 IS NULL
            AND encryption_key_ref IS NULL
            AND encryption_key_version IS NULL
            AND payload_schema_version = 1
            AND payload_row_count >= 0 AND payload_size_bytes >= 0)),
    CONSTRAINT ck_plat_tenant_lifecycle_time CHECK (
        started_at >= requested_at AND finished_at >= started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
