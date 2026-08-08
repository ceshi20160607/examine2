-- Tenant-scoped report drafts and immutable publications over exact native
-- data-source versions. Runtime rows are ephemeral and are not persisted here.

CREATE TABLE un_module_report (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    report_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    draft_json JSON NOT NULL,
    draft_version BIGINT NOT NULL,
    active_version_id BIGINT NULL,
    active_version_no INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_report_code (
        system_id, tenant_id, report_code),
    UNIQUE KEY uk_module_report_active_identity (
        system_id, tenant_id, id, active_version_id, active_version_no),
    KEY idx_module_report_list (
        system_id, tenant_id, updated_at DESC, id DESC),
    KEY idx_module_report_active (
        system_id, tenant_id, active_version_id),
    CONSTRAINT fk_module_report_tenant FOREIGN KEY (
        system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0),
    CONSTRAINT ck_module_report_code CHECK (
        report_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_report_name CHECK (
        CHAR_LENGTH(TRIM(report_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_report_description CHECK (
        description IS NULL
        OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_module_report_draft CHECK (
        draft_version > 0
        AND JSON_TYPE(draft_json) = 'OBJECT'
        AND OCTET_LENGTH(draft_json) BETWEEN 2 AND 262144),
    CONSTRAINT ck_module_report_active CHECK (
        (active_version_id IS NULL AND active_version_no IS NULL)
        OR (active_version_id > 0 AND active_version_no > 0)),
    CONSTRAINT ck_module_report_state CHECK (
        updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_report_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    source_draft_version BIGINT NOT NULL,
    report_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    data_source_id BIGINT NOT NULL,
    data_source_version_id BIGINT NOT NULL,
    data_source_version_no INT UNSIGNED NOT NULL,
    data_source_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    data_source_name VARCHAR(200) NOT NULL,
    module_id BIGINT NOT NULL,
    module_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version_id VARCHAR(200)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    field_count TINYINT UNSIGNED NOT NULL,
    snapshot_json JSON NOT NULL,
    snapshot_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_by_member_id BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, report_id, id),
    UNIQUE KEY uk_module_report_version_id (
        system_id, tenant_id, report_id, id, version_no),
    UNIQUE KEY uk_module_report_version_no (
        system_id, tenant_id, report_id, version_no),
    KEY idx_module_report_version_list (
        system_id, tenant_id, report_id, version_no DESC),
    KEY idx_module_report_version_fingerprint (
        system_id, tenant_id, report_id,
        snapshot_fingerprint, version_no DESC),
    KEY idx_module_report_version_source (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no),
    CONSTRAINT fk_module_report_version_root FOREIGN KEY (
        system_id, tenant_id, report_id)
        REFERENCES un_module_report (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_version_source FOREIGN KEY (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no)
        REFERENCES un_module_data_source_version (
            system_id, tenant_id, data_source_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_version_publisher FOREIGN KEY (
        system_id, published_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_version_identity CHECK (
        id > 0 AND report_id > 0 AND version_no > 0
        AND source_draft_version > 0 AND data_source_id > 0
        AND data_source_version_id > 0 AND data_source_version_no > 0
        AND module_id > 0 AND published_by_member_id > 0),
    CONSTRAINT ck_module_report_version_code CHECK (
        report_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,99}$'),
    CONSTRAINT ck_module_report_version_name CHECK (
        CHAR_LENGTH(TRIM(report_name)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(data_source_name)) BETWEEN 1 AND 200
        AND (description IS NULL
          OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000)),
    CONSTRAINT ck_module_report_version_schema CHECK (
        CHAR_LENGTH(TRIM(schema_version_id)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_report_version_fields CHECK (
        field_count BETWEEN 1 AND 100),
    CONSTRAINT ck_module_report_version_snapshot CHECK (
        JSON_TYPE(snapshot_json) = 'OBJECT'
        AND OCTET_LENGTH(snapshot_json) BETWEEN 2 AND 262144
        AND snapshot_fingerprint REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_report_version_field (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    report_version_id BIGINT NOT NULL,
    report_version_no INT UNSIGNED NOT NULL,
    field_ordinal TINYINT UNSIGNED NOT NULL,
    field_id BIGINT NOT NULL,
    field_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    field_name VARCHAR(200) NOT NULL,
    field_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    query_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, report_id, report_version_id, id),
    UNIQUE KEY uk_module_report_field_ordinal (
        system_id, tenant_id, report_id,
        report_version_id, field_ordinal),
    UNIQUE KEY uk_module_report_field_code (
        system_id, tenant_id, report_id,
        report_version_id, field_code),
    UNIQUE KEY uk_module_report_field_id (
        system_id, tenant_id, report_id,
        report_version_id, field_id),
    KEY idx_module_report_field_version (
        system_id, tenant_id, report_id,
        report_version_id, report_version_no),
    CONSTRAINT fk_module_report_field_version FOREIGN KEY (
        system_id, tenant_id, report_id,
        report_version_id, report_version_no)
        REFERENCES un_module_report_version (
            system_id, tenant_id, report_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_field_identity CHECK (
        id > 0 AND report_id > 0 AND report_version_id > 0
        AND report_version_no > 0 AND field_ordinal < 100
        AND field_id > 0),
    CONSTRAINT ck_module_report_field_code CHECK (
        field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_report_field_name CHECK (
        CHAR_LENGTH(TRIM(field_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_report_field_type CHECK (
        field_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
        AND query_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_module_report
    ADD CONSTRAINT fk_module_report_active_version FOREIGN KEY (
        system_id, tenant_id, id, active_version_id, active_version_no)
        REFERENCES un_module_report_version (
            system_id, tenant_id, report_id, id, version_no)
        ON DELETE RESTRICT;
