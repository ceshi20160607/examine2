CREATE TABLE un_file_object (
    id BIGINT UNSIGNED NOT NULL,
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    uploader_member_id BIGINT UNSIGNED NOT NULL,
    object_key VARCHAR(300) NOT NULL,
    original_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    size_bytes BIGINT UNSIGNED NOT NULL,
    sha256 CHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_file_object_scope_id (system_id, tenant_id, id),
    UNIQUE KEY uk_file_object_key (object_key),
    KEY idx_file_object_uploader_created (
        system_id, tenant_id, uploader_member_id, created_at
    ),
    KEY idx_file_object_sha256 (system_id, tenant_id, sha256),
    CONSTRAINT chk_file_object_scope CHECK (system_id > 0 AND tenant_id > 0),
    CONSTRAINT chk_file_object_uploader CHECK (uploader_member_id > 0),
    CONSTRAINT chk_file_object_status CHECK (status = 'ACTIVE'),
    CONSTRAINT chk_file_object_size CHECK (size_bytes >= 0),
    CONSTRAINT chk_file_object_sha256 CHECK (CHAR_LENGTH(sha256) = 64),
    CONSTRAINT chk_file_object_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_file_reference (
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    file_id BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(64) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    created_by_member_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, file_id, target_type, target_id),
    KEY idx_file_reference_target (
        system_id, tenant_id, target_type, target_id, file_id
    ),
    CONSTRAINT fk_file_reference_object
        FOREIGN KEY (system_id, tenant_id, file_id)
        REFERENCES un_file_object (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_file_reference_scope CHECK (system_id > 0 AND tenant_id > 0),
    CONSTRAINT chk_file_reference_creator CHECK (created_by_member_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
