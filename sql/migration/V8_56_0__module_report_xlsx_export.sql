-- Durable tenant-scoped XLSX export runs over immutable report publications.
-- The worker reauthorizes at execution time; this table stores no SQL or
-- authorization predicate and retains no partial file for failed runs.

CREATE TABLE un_module_report_export_run (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    report_version_id BIGINT NOT NULL,
    report_version_no INT UNSIGNED NOT NULL,
    report_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    report_name VARCHAR(200) NOT NULL,
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
    fields_json JSON NOT NULL,
    fields_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    field_count TINYINT UNSIGNED NOT NULL,
    request_key_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    requested_by_account_id BIGINT NOT NULL,
    requested_by_member_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    status VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    total_rows BIGINT NULL,
    processed_rows SMALLINT UNSIGNED NOT NULL,
    truncated BOOLEAN NOT NULL,
    result_filename VARCHAR(180) NULL,
    result_content LONGBLOB NULL,
    result_size BIGINT UNSIGNED NULL,
    error_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_message VARCHAR(500) NULL,
    request_id VARCHAR(128) NOT NULL,
    trace_id VARCHAR(128) NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_report_export_id (id),
    UNIQUE KEY uk_module_report_export_request (
        system_id, tenant_id, requested_by_member_id,
        report_id, request_key_hash),
    UNIQUE KEY uk_module_report_export_job (job_id),
    KEY idx_module_report_export_owner (
        system_id, tenant_id, report_id, requested_by_member_id,
        created_at DESC, id DESC),
    KEY idx_module_report_export_report_version (
        system_id, tenant_id, report_id,
        report_version_id, report_version_no),
    KEY idx_module_report_export_source_version (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no),
    KEY idx_module_report_export_status (
        status, updated_at, id),
    CONSTRAINT fk_module_report_export_root FOREIGN KEY (
        system_id, tenant_id, report_id)
        REFERENCES un_module_report (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_export_version FOREIGN KEY (
        system_id, tenant_id, report_id,
        report_version_id, report_version_no)
        REFERENCES un_module_report_version (
            system_id, tenant_id, report_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_export_source FOREIGN KEY (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no)
        REFERENCES un_module_data_source_version (
            system_id, tenant_id, data_source_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_export_requester FOREIGN KEY (
        system_id, requested_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_export_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND report_id > 0
        AND report_version_id > 0 AND report_version_no > 0
        AND data_source_id > 0 AND data_source_version_id > 0
        AND data_source_version_no > 0 AND module_id > 0
        AND requested_by_account_id > 0
        AND requested_by_member_id > 0 AND job_id > 0),
    CONSTRAINT ck_module_report_export_code CHECK (
        report_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,99}$'),
    CONSTRAINT ck_module_report_export_names CHECK (
        CHAR_LENGTH(TRIM(report_name)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(data_source_name)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(schema_version_id)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_report_export_fields CHECK (
        field_count BETWEEN 1 AND 100
        AND JSON_TYPE(fields_json) = 'ARRAY'
        AND JSON_LENGTH(fields_json) = field_count
        AND OCTET_LENGTH(fields_json) BETWEEN 2 AND 262144
        AND fields_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_module_report_export_request CHECK (
        request_key_hash REGEXP '^[0-9a-f]{64}$'
        AND CHAR_LENGTH(TRIM(request_id)) BETWEEN 1 AND 128
        AND CHAR_LENGTH(TRIM(trace_id)) BETWEEN 1 AND 128),
    CONSTRAINT ck_module_report_export_result CHECK (
        (total_rows IS NULL OR total_rows >= 0)
        AND processed_rows BETWEEN 0 AND 5000),
    CONSTRAINT ck_module_report_export_state CHECK (
        (status = 'QUEUED'
          AND total_rows IS NULL AND processed_rows = 0
          AND truncated = 0
          AND result_filename IS NULL AND result_content IS NULL
          AND result_size IS NULL AND error_code IS NULL
          AND error_message IS NULL AND finished_at IS NULL
          AND (started_at IS NULL OR started_at >= created_at))
        OR (status = 'RUNNING'
          AND total_rows IS NULL AND processed_rows = 0
          AND truncated = 0
          AND result_filename IS NULL AND result_content IS NULL
          AND result_size IS NULL AND error_code IS NULL
          AND error_message IS NULL AND started_at >= created_at
          AND finished_at IS NULL)
        OR (status = 'SUCCEEDED'
          AND total_rows >= processed_rows
          AND truncated = (total_rows > processed_rows)
          AND CHAR_LENGTH(TRIM(result_filename)) BETWEEN 6 AND 180
          AND LOWER(result_filename) LIKE '%.xlsx'
          AND result_content IS NOT NULL AND result_size > 0
          AND result_size = OCTET_LENGTH(result_content)
          AND result_size <= 134217728
          AND error_code IS NULL AND error_message IS NULL
          AND started_at >= created_at AND finished_at >= started_at)
        OR (status = 'FAILED'
          AND total_rows IS NULL AND processed_rows = 0
          AND truncated = 0
          AND result_filename IS NULL AND result_content IS NULL
          AND result_size IS NULL
          AND error_code REGEXP '^[A-Z][A-Z0-9_]{1,99}$'
          AND CHAR_LENGTH(TRIM(error_message)) BETWEEN 1 AND 500
          AND finished_at >= created_at
          AND (started_at IS NULL OR finished_at >= started_at))),
    CONSTRAINT ck_module_report_export_time CHECK (
        updated_at >= created_at AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
