-- Tenant-scoped published KPI definitions, aligned subject targets and
-- append-only explainable calculations over exact data-source versions.

CREATE TABLE un_module_kpi (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    kpi_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kpi_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    draft_json JSON NOT NULL,
    draft_version BIGINT NOT NULL,
    active_version_id BIGINT NULL,
    active_version_no INT UNSIGNED NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_kpi_code (system_id, tenant_id, kpi_code),
    UNIQUE KEY uk_module_kpi_active_identity (
        system_id, tenant_id, id, active_version_id, active_version_no),
    KEY idx_module_kpi_list (
        system_id, tenant_id, updated_at DESC, id DESC),
    KEY idx_module_kpi_active (
        system_id, tenant_id, active_version_id),
    CONSTRAINT fk_module_kpi_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_kpi_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0),
    CONSTRAINT ck_module_kpi_code CHECK (
        kpi_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_kpi_name CHECK (
        CHAR_LENGTH(TRIM(kpi_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_kpi_description CHECK (
        description IS NULL
        OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000),
    CONSTRAINT ck_module_kpi_draft CHECK (
        draft_version > 0 AND JSON_TYPE(draft_json) = 'OBJECT'
        AND OCTET_LENGTH(draft_json) BETWEEN 2 AND 262144),
    CONSTRAINT ck_module_kpi_active CHECK (
        (active_version_id IS NULL AND active_version_no IS NULL)
        OR (active_version_id > 0 AND active_version_no > 0)),
    CONSTRAINT ck_module_kpi_state CHECK (
        updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_kpi_version (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    kpi_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    source_draft_version BIGINT NOT NULL,
    kpi_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kpi_name VARCHAR(200) NOT NULL,
    description VARCHAR(2000) NULL,
    subject_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    period_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attainment_direction VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    warning_threshold VARCHAR(1000)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    data_source_id BIGINT NOT NULL,
    data_source_version_id BIGINT NOT NULL,
    data_source_version_no INT UNSIGNED NOT NULL,
    data_source_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    module_id BIGINT NOT NULL,
    module_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schema_version_id VARCHAR(200)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregation VARCHAR(8)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    measure_field_id BIGINT NULL,
    measure_field_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    measure_field_name VARCHAR(200) NULL,
    measure_field_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    measure_query_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    time_field_id BIGINT NOT NULL,
    time_field_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    time_field_name VARCHAR(200) NOT NULL,
    time_field_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    time_query_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    calculator_version VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_json JSON NOT NULL,
    snapshot_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_by_member_id BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, kpi_id, id),
    UNIQUE KEY uk_module_kpi_version_id (
        system_id, tenant_id, kpi_id, id, version_no),
    UNIQUE KEY uk_module_kpi_version_no (
        system_id, tenant_id, kpi_id, version_no),
    KEY idx_module_kpi_version_list (
        system_id, tenant_id, kpi_id, version_no DESC),
    KEY idx_module_kpi_version_fingerprint (
        system_id, tenant_id, kpi_id,
        snapshot_fingerprint, version_no DESC),
    KEY idx_module_kpi_version_source (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no),
    CONSTRAINT fk_module_kpi_version_root FOREIGN KEY (
        system_id, tenant_id, kpi_id)
        REFERENCES un_module_kpi (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_kpi_version_source FOREIGN KEY (
        system_id, tenant_id, data_source_id,
        data_source_version_id, data_source_version_no)
        REFERENCES un_module_data_source_version (
            system_id, tenant_id, data_source_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_kpi_version_publisher FOREIGN KEY (
        system_id, published_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_kpi_version_identity CHECK (
        id > 0 AND kpi_id > 0 AND version_no > 0
        AND source_draft_version > 0 AND published_by_member_id > 0
        AND data_source_id > 0 AND data_source_version_id > 0
        AND data_source_version_no > 0 AND module_id > 0),
    CONSTRAINT ck_module_kpi_version_code CHECK (
        kpi_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,99}$'),
    CONSTRAINT ck_module_kpi_version_name CHECK (
        CHAR_LENGTH(TRIM(kpi_name)) BETWEEN 1 AND 200
        AND (description IS NULL
          OR CHAR_LENGTH(TRIM(description)) BETWEEN 1 AND 2000)),
    CONSTRAINT ck_module_kpi_version_shape CHECK (
        subject_type IN ('MEMBER','DEPARTMENT','ROLE')
        AND period_type IN ('MONTH','QUARTER','YEAR')
        AND attainment_direction IN ('AT_LEAST','AT_MOST')
        AND warning_threshold REGEXP '^(1|0[.][0-9]*[1-9])$'
        AND aggregation IN ('COUNT','SUM','AVG','MIN','MAX')
        AND ((aggregation = 'COUNT'
            AND measure_field_id IS NULL
            AND measure_field_code IS NULL
            AND measure_field_name IS NULL
            AND measure_field_type IS NULL
            AND measure_query_type IS NULL)
          OR (aggregation <> 'COUNT'
            AND measure_field_id > 0
            AND measure_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND CHAR_LENGTH(TRIM(measure_field_name)) BETWEEN 1 AND 200
            AND measure_field_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
            AND measure_query_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'))),
    CONSTRAINT ck_module_kpi_version_time CHECK (
        time_field_id > 0
        AND time_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND CHAR_LENGTH(TRIM(time_field_name)) BETWEEN 1 AND 200
        AND time_field_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
        AND time_query_type IN ('DATE','DATETIME')),
    CONSTRAINT ck_module_kpi_version_snapshot CHECK (
        CHAR_LENGTH(TRIM(schema_version_id)) BETWEEN 1 AND 200
        AND calculator_version REGEXP '^[A-Za-z][A-Za-z0-9_.-]{0,31}$'
        AND JSON_TYPE(snapshot_json) = 'OBJECT'
        AND OCTET_LENGTH(snapshot_json) BETWEEN 2 AND 262144
        AND snapshot_fingerprint REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_module_kpi
    ADD CONSTRAINT fk_module_kpi_active_version FOREIGN KEY (
        system_id, tenant_id, id, active_version_id, active_version_no)
        REFERENCES un_module_kpi_version (
            system_id, tenant_id, kpi_id, id, version_no)
        ON DELETE RESTRICT;

CREATE TABLE un_module_kpi_target (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    kpi_id BIGINT NOT NULL,
    kpi_version_id BIGINT NOT NULL,
    kpi_version_no INT UNSIGNED NOT NULL,
    subject_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    period_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    target_value VARCHAR(1000)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_key_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    latest_calculation_id BIGINT NULL,
    latest_calculation_no INT UNSIGNED NULL,
    created_by_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_by_member_id BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_kpi_target_version_identity (
        system_id, tenant_id, kpi_id,
        kpi_version_id, kpi_version_no, id),
    UNIQUE KEY uk_module_kpi_target_period (
        system_id, tenant_id, kpi_id, kpi_version_id,
        subject_type, subject_id, period_start),
    UNIQUE KEY uk_module_kpi_target_request (
        system_id, tenant_id, kpi_id, request_key_hash),
    UNIQUE KEY uk_module_kpi_target_latest_identity (
        system_id, tenant_id, id,
        latest_calculation_id, latest_calculation_no),
    KEY idx_module_kpi_target_list (
        system_id, tenant_id, kpi_id, period_start DESC, id DESC),
    KEY idx_module_kpi_target_subject (
        system_id, tenant_id, subject_type, subject_id,
        period_start DESC, id DESC),
    KEY idx_module_kpi_target_latest (
        system_id, tenant_id, latest_calculation_id),
    CONSTRAINT fk_module_kpi_target_version FOREIGN KEY (
        system_id, tenant_id, kpi_id,
        kpi_version_id, kpi_version_no)
        REFERENCES un_module_kpi_version (
            system_id, tenant_id, kpi_id, id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_kpi_target_creator FOREIGN KEY (
        system_id, created_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_kpi_target_updater FOREIGN KEY (
        system_id, updated_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_kpi_target_identity CHECK (
        id > 0 AND kpi_id > 0 AND kpi_version_id > 0
        AND kpi_version_no > 0 AND subject_id > 0
        AND created_by_member_id > 0 AND updated_by_member_id > 0),
    CONSTRAINT ck_module_kpi_target_subject CHECK (
        subject_type IN ('MEMBER','DEPARTMENT','ROLE')
        AND CHAR_LENGTH(TRIM(subject_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_kpi_target_period CHECK (
        (period_type = 'MONTH'
          AND DAYOFMONTH(period_start) = 1
          AND period_end = DATE_ADD(period_start, INTERVAL 1 MONTH))
        OR (period_type = 'QUARTER'
          AND DAYOFMONTH(period_start) = 1
          AND MONTH(period_start) IN (1,4,7,10)
          AND period_end = DATE_ADD(period_start, INTERVAL 3 MONTH))
        OR (period_type = 'YEAR'
          AND MONTH(period_start) = 1 AND DAYOFMONTH(period_start) = 1
          AND period_end = DATE_ADD(period_start, INTERVAL 1 YEAR))),
    CONSTRAINT ck_module_kpi_target_value CHECK (
        target_value REGEXP '^(0|[1-9][0-9]*)([.][0-9]*[1-9])?$'),
    CONSTRAINT ck_module_kpi_target_request CHECK (
        request_key_hash REGEXP '^[0-9a-f]{64}$'
        AND request_fingerprint REGEXP '^[0-9a-f]{64}$'),
    CONSTRAINT ck_module_kpi_target_state CHECK (
        status IN ('ACTIVE','CANCELLED')
        AND ((latest_calculation_id IS NULL
              AND latest_calculation_no IS NULL)
          OR (latest_calculation_id > 0
              AND latest_calculation_no > 0))
        AND updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_kpi_calculation (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    kpi_id BIGINT NOT NULL,
    kpi_version_id BIGINT NOT NULL,
    kpi_version_no INT UNSIGNED NOT NULL,
    target_id BIGINT NOT NULL,
    calculation_no INT UNSIGNED NOT NULL,
    request_key_hash CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    running_target_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN status = 'RUNNING' THEN target_id ELSE NULL END) STORED,
    calculator_version VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    authz_epoch BIGINT NULL,
    subject_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_id BIGINT NOT NULL,
    subject_name VARCHAR(200) NOT NULL,
    subject_member_count INT UNSIGNED NOT NULL,
    subject_members_fingerprint CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    subject_snapshot_json JSON NOT NULL,
    period_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    period_start DATE NOT NULL,
    period_end DATE NOT NULL,
    aggregation VARCHAR(8)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    target_value_snapshot VARCHAR(1000)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actual_value VARCHAR(1000)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    attainment_rate VARCHAR(1000)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    warning_status VARCHAR(24)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    statistics_query_id CHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    matched_record_count BIGINT UNSIGNED NULL,
    trend_json JSON NULL,
    explanation_json JSON NULL,
    error_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_message VARCHAR(500) NULL,
    requested_by_member_id BIGINT NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_kpi_calculation_identity (
        system_id, tenant_id, target_id, id, calculation_no),
    UNIQUE KEY uk_module_kpi_calculation_member_identity (
        system_id, tenant_id, kpi_id, target_id, id),
    UNIQUE KEY uk_module_kpi_calculation_no (
        system_id, tenant_id, target_id, calculation_no),
    UNIQUE KEY uk_module_kpi_calculation_request (
        system_id, tenant_id, request_key_hash),
    UNIQUE KEY uk_module_kpi_calculation_running (
        system_id, tenant_id, running_target_id),
    KEY idx_module_kpi_calculation_history (
        system_id, tenant_id, target_id, calculation_no DESC),
    KEY idx_module_kpi_calculation_status (
        system_id, tenant_id, status, started_at, id),
    CONSTRAINT fk_module_kpi_calculation_target FOREIGN KEY (
        system_id, tenant_id, kpi_id, kpi_version_id,
        kpi_version_no, target_id)
        REFERENCES un_module_kpi_target (
            system_id, tenant_id, kpi_id, kpi_version_id,
            kpi_version_no, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_kpi_calculation_requester FOREIGN KEY (
        system_id, requested_by_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_kpi_calculation_identity CHECK (
        id > 0 AND kpi_id > 0 AND kpi_version_id > 0
        AND kpi_version_no > 0 AND target_id > 0 AND calculation_no > 0
        AND requested_by_member_id > 0),
    CONSTRAINT ck_module_kpi_calculation_request CHECK (
        request_key_hash REGEXP '^[0-9a-f]{64}$'
        AND request_fingerprint REGEXP '^[0-9a-f]{64}$'
        AND calculator_version REGEXP '^[A-Za-z][A-Za-z0-9_.-]{0,31}$'),
    CONSTRAINT ck_module_kpi_calculation_subject CHECK (
        subject_type IN ('MEMBER','DEPARTMENT','ROLE')
        AND subject_id > 0
        AND CHAR_LENGTH(TRIM(subject_name)) BETWEEN 1 AND 200
        AND subject_member_count <= 1000
        AND JSON_TYPE(subject_snapshot_json) = 'OBJECT'
        AND OCTET_LENGTH(subject_snapshot_json) BETWEEN 2 AND 131072
        AND ((subject_type = 'ROLE'
              AND subject_members_fingerprint REGEXP '^[0-9a-f]{64}$')
          OR (subject_type <> 'ROLE'
              AND subject_members_fingerprint IS NULL))),
    CONSTRAINT ck_module_kpi_calculation_period CHECK (
        period_type IN ('MONTH','QUARTER','YEAR')
        AND period_end > period_start),
    CONSTRAINT ck_module_kpi_calculation_values CHECK (
        aggregation IN ('COUNT','SUM','AVG','MIN','MAX')
        AND target_value_snapshot
          REGEXP '^(0|[1-9][0-9]*)([.][0-9]*[1-9])?$'
        AND (actual_value IS NULL OR actual_value
          REGEXP '^(0|-?[1-9][0-9]*)([.][0-9]*[1-9])?$')
        AND (attainment_rate IS NULL OR attainment_rate
          REGEXP '^(0|[1-9][0-9]*)([.][0-9]*[1-9])?$')),
    CONSTRAINT ck_module_kpi_calculation_result CHECK (
        (status = 'RUNNING'
          AND running_target_id = target_id
          AND authz_epoch > 0
          AND completed_at IS NULL AND actual_value IS NULL
          AND attainment_rate IS NULL AND warning_status IS NULL
          AND statistics_query_id IS NULL
          AND matched_record_count IS NULL AND trend_json IS NULL
          AND explanation_json IS NULL
          AND error_code IS NULL AND error_message IS NULL)
        OR (status = 'SUCCEEDED'
          AND running_target_id IS NULL AND completed_at >= started_at
          AND authz_epoch > 0
          AND warning_status IN ('ACHIEVED','AT_RISK','MISSED')
          AND ((actual_value IS NULL AND attainment_rate IS NULL)
            OR (actual_value IS NOT NULL AND attainment_rate IS NOT NULL))
          AND statistics_query_id REGEXP '^[0-9a-f]{64}$'
          AND matched_record_count >= 0
          AND JSON_TYPE(trend_json) = 'ARRAY'
          AND JSON_TYPE(explanation_json) = 'OBJECT'
          AND error_code IS NULL AND error_message IS NULL)
        OR (status = 'FAILED'
          AND running_target_id IS NULL AND completed_at >= started_at
          AND (authz_epoch IS NULL OR authz_epoch > 0)
          AND actual_value IS NULL AND attainment_rate IS NULL
          AND warning_status = 'CALCULATION_FAILED'
          AND statistics_query_id IS NULL
          AND matched_record_count IS NULL AND trend_json IS NULL
          AND error_code REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
          AND CHAR_LENGTH(TRIM(error_message)) BETWEEN 1 AND 500)),
    CONSTRAINT ck_module_kpi_calculation_json CHECK (
        (trend_json IS NULL OR OCTET_LENGTH(trend_json) <= 131072)
        AND (explanation_json IS NULL
          OR OCTET_LENGTH(explanation_json) BETWEEN 2 AND 262144))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_module_kpi_target
    ADD CONSTRAINT fk_module_kpi_target_latest FOREIGN KEY (
        system_id, tenant_id, id,
        latest_calculation_id, latest_calculation_no)
        REFERENCES un_module_kpi_calculation (
            system_id, tenant_id, target_id, id, calculation_no)
        ON DELETE RESTRICT;

CREATE TABLE un_module_kpi_calculation_member (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    kpi_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    calculation_id BIGINT NOT NULL,
    member_ordinal SMALLINT UNSIGNED NOT NULL,
    member_id BIGINT NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, kpi_id,
        target_id, calculation_id, member_ordinal),
    UNIQUE KEY uk_module_kpi_calculation_member (
        system_id, tenant_id, kpi_id,
        target_id, calculation_id, member_id),
    KEY idx_module_kpi_calculation_member_lookup (
        system_id, tenant_id, member_id, calculation_id),
    CONSTRAINT fk_module_kpi_calculation_member_calc FOREIGN KEY (
        system_id, tenant_id, kpi_id, target_id, calculation_id)
        REFERENCES un_module_kpi_calculation (
            system_id, tenant_id, kpi_id, target_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_kpi_calculation_member_member FOREIGN KEY (
        system_id, member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_kpi_calculation_member_identity CHECK (
        kpi_id > 0 AND target_id > 0 AND calculation_id > 0
        AND member_ordinal < 1000 AND member_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
