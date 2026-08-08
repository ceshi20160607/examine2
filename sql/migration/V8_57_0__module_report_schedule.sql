-- Durable tenant report schedules. Schedule recipients are the current
-- configuration; occurrence recipients and delivery rows are immutable facts.

CREATE TABLE un_module_report_schedule (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    report_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schedule_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schedule_name VARCHAR(200) NOT NULL,
    time_zone VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cadence_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    local_time TIME NOT NULL,
    days_of_week_json JSON NOT NULL,
    recipient_count TINYINT UNSIGNED NOT NULL,
    enabled BOOLEAN NOT NULL,
    owner_account_id BIGINT NOT NULL,
    owner_member_id BIGINT NOT NULL,
    next_fire_at DATETIME(6) NULL,
    last_scheduled_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_report_schedule_id (id),
    UNIQUE KEY uk_module_report_schedule_code (
        system_id, tenant_id, report_id, schedule_code),
    UNIQUE KEY uk_module_report_schedule_report_identity (
        system_id, tenant_id, id, report_id),
    KEY idx_module_report_schedule_list (
        system_id, tenant_id, report_id, updated_at DESC, id DESC),
    KEY idx_module_report_schedule_due (
        enabled, next_fire_at, system_id, tenant_id, id),
    CONSTRAINT fk_module_report_schedule_report FOREIGN KEY (
        system_id, tenant_id, report_id)
        REFERENCES un_module_report (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_schedule_owner FOREIGN KEY (
        system_id, owner_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (
            system_id, member_id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_schedule_identity CHECK (
        id > 0 AND system_id > 0 AND tenant_id > 0 AND report_id > 0
        AND owner_account_id > 0 AND owner_member_id > 0),
    CONSTRAINT ck_module_report_schedule_codes CHECK (
        schedule_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
        AND report_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_module_report_schedule_name CHECK (
        CHAR_LENGTH(TRIM(schedule_name)) BETWEEN 1 AND 200),
    CONSTRAINT ck_module_report_schedule_zone CHECK (
        time_zone = 'UTC'
        OR time_zone REGEXP
          '^[A-Za-z][A-Za-z0-9._+-]*/[A-Za-z0-9._+-]+(/[A-Za-z0-9._+-]+)*$'),
    CONSTRAINT ck_module_report_schedule_cadence CHECK (
        JSON_SCHEMA_VALID(
          '{"type":"array","uniqueItems":true,"maxItems":7,"items":{"type":"integer","minimum":1,"maximum":7}}',
          days_of_week_json)
        AND ((cadence_type = 'DAILY'
              AND JSON_LENGTH(days_of_week_json) = 0)
          OR (cadence_type = 'WEEKLY'
              AND JSON_LENGTH(days_of_week_json) BETWEEN 1 AND 7))),
    CONSTRAINT ck_module_report_schedule_recipients CHECK (
        recipient_count BETWEEN 1 AND 50),
    CONSTRAINT ck_module_report_schedule_state CHECK (
        enabled IN (0,1) AND enabled = (next_fire_at IS NOT NULL)
        AND (last_scheduled_at IS NULL
             OR last_scheduled_at <= updated_at)
        AND updated_at >= created_at AND version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_report_schedule_recipient (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    recipient_ordinal TINYINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, schedule_id, recipient_ordinal),
    UNIQUE KEY uk_module_report_schedule_recipient_member (
        system_id, tenant_id, schedule_id, recipient_member_id),
    KEY idx_module_report_schedule_recipient_lookup (
        system_id, tenant_id, recipient_member_id, schedule_id),
    CONSTRAINT fk_module_report_schedule_recipient_root FOREIGN KEY (
        system_id, tenant_id, schedule_id)
        REFERENCES un_module_report_schedule (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_schedule_recipient_member FOREIGN KEY (
        system_id, recipient_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (
            system_id, member_id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_schedule_recipient CHECK (
        schedule_id > 0 AND recipient_ordinal < 50
        AND recipient_member_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_report_schedule_occurrence (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    schedule_id BIGINT NOT NULL,
    schedule_version BIGINT NOT NULL,
    schedule_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    schedule_name VARCHAR(200) NOT NULL,
    report_id BIGINT NOT NULL,
    report_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_account_id BIGINT NOT NULL,
    owner_member_id BIGINT NOT NULL,
    configured_recipient_count TINYINT UNSIGNED NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    occurrence_key VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count TINYINT UNSIGNED NOT NULL,
    max_attempts TINYINT UNSIGNED NOT NULL,
    export_id BIGINT NULL,
    export_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_filename VARCHAR(180) NULL,
    result_size BIGINT NULL,
    total_rows BIGINT NULL,
    processed_rows INT UNSIGNED NOT NULL,
    truncated BOOLEAN NOT NULL,
    delivered_recipient_count TINYINT UNSIGNED NOT NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_message VARCHAR(500) NULL,
    available_at DATETIME(6) NULL,
    lease_until DATETIME(6) NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(6) NULL,
    finished_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_report_schedule_occurrence_id (id),
    UNIQUE KEY uk_module_report_schedule_occurrence_fire (
        system_id, tenant_id, schedule_id, scheduled_at),
    UNIQUE KEY uk_module_report_schedule_occurrence_key (
        system_id, tenant_id, occurrence_key),
    UNIQUE KEY uk_module_report_schedule_occurrence_export (
        system_id, tenant_id, export_id),
    UNIQUE KEY uk_module_report_schedule_occurrence_identity (
        system_id, tenant_id, id, report_id),
    KEY idx_module_report_schedule_occurrence_claim (
        status, available_at, lease_until, scheduled_at,
        system_id, tenant_id, id),
    KEY idx_module_report_schedule_occurrence_history (
        system_id, tenant_id, report_code, scheduled_at DESC, id DESC),
    CONSTRAINT fk_module_report_schedule_occurrence_schedule FOREIGN KEY (
        system_id, tenant_id, schedule_id, report_id)
        REFERENCES un_module_report_schedule (
            system_id, tenant_id, id, report_id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_schedule_occurrence_owner FOREIGN KEY (
        system_id, owner_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (
            system_id, member_id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_schedule_occurrence_export FOREIGN KEY (
        system_id, tenant_id, export_id)
        REFERENCES un_module_report_export_run (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_schedule_occurrence_identity CHECK (
        id > 0 AND schedule_id > 0 AND schedule_version > 0
        AND report_id > 0 AND owner_account_id > 0 AND owner_member_id > 0),
    CONSTRAINT ck_module_report_schedule_occurrence_attempt CHECK (
        max_attempts BETWEEN 1 AND 3
        AND attempt_count BETWEEN 0 AND max_attempts),
    CONSTRAINT ck_module_report_schedule_occurrence_counts CHECK (
        configured_recipient_count BETWEEN 1 AND 50
        AND delivered_recipient_count <= configured_recipient_count
        AND processed_rows <= 5000
        AND (total_rows IS NULL OR total_rows >= processed_rows)),
    CONSTRAINT ck_module_report_schedule_occurrence_state CHECK (
        (status = 'PENDING' AND attempt_count < max_attempts
          AND export_id IS NULL AND export_status IS NULL
          AND result_filename IS NULL AND result_size IS NULL
          AND total_rows IS NULL AND processed_rows = 0 AND truncated = 0
          AND delivered_recipient_count = 0 AND available_at IS NOT NULL
          AND lease_until IS NULL AND finished_at IS NULL)
        OR (status = 'RUNNING' AND attempt_count BETWEEN 1 AND max_attempts
          AND result_filename IS NULL AND result_size IS NULL
          AND total_rows IS NULL AND processed_rows = 0 AND truncated = 0
          AND delivered_recipient_count = 0 AND available_at IS NOT NULL
          AND finished_at IS NULL)
        OR (status = 'SUCCEEDED' AND attempt_count BETWEEN 1 AND max_attempts
          AND export_id > 0 AND export_status = 'SUCCEEDED'
          AND result_filename LIKE '%.xlsx' AND result_size > 0
          AND total_rows >= processed_rows
          AND truncated = (total_rows > processed_rows)
          AND failure_code IS NULL AND failure_message IS NULL
          AND available_at IS NULL AND lease_until IS NULL
          AND finished_at IS NOT NULL)
        OR (status = 'FAILED' AND attempt_count BETWEEN 1 AND max_attempts
          AND result_filename IS NULL AND result_size IS NULL
          AND total_rows IS NULL AND processed_rows = 0 AND truncated = 0
          AND failure_code REGEXP '^[A-Z][A-Z0-9_]{1,63}$'
          AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500
          AND available_at IS NULL AND lease_until IS NULL
          AND finished_at IS NOT NULL)),
    CONSTRAINT ck_module_report_schedule_occurrence_time CHECK (
        (lease_until IS NULL OR lease_until > updated_at)
        AND (started_at IS NULL OR started_at >= created_at)
        AND (finished_at IS NULL OR finished_at >= created_at)
        AND updated_at >= created_at AND version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_report_schedule_occurrence_recipient (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    occurrence_id BIGINT NOT NULL,
    recipient_ordinal TINYINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, occurrence_id, recipient_ordinal),
    UNIQUE KEY uk_module_report_schedule_occurrence_recipient_member (
        system_id, tenant_id, occurrence_id, recipient_member_id),
    CONSTRAINT fk_module_report_schedule_occurrence_recipient_root FOREIGN KEY (
        system_id, tenant_id, occurrence_id)
        REFERENCES un_module_report_schedule_occurrence (
            system_id, tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_schedule_occurrence_recipient_member FOREIGN KEY (
        system_id, recipient_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (
            system_id, member_id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_schedule_occurrence_recipient CHECK (
        occurrence_id > 0 AND recipient_ordinal < 50
        AND recipient_member_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_module_report_schedule_delivery (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    occurrence_id BIGINT NOT NULL,
    recipient_member_id BIGINT NOT NULL,
    message_id BIGINT NOT NULL,
    delivery_key VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    delivered_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_module_report_schedule_delivery_id (id),
    UNIQUE KEY uk_module_report_schedule_delivery_recipient (
        system_id, tenant_id, occurrence_id, recipient_member_id),
    UNIQUE KEY uk_module_report_schedule_delivery_key (
        system_id, tenant_id, delivery_key),
    UNIQUE KEY uk_module_report_schedule_delivery_message (
        system_id, tenant_id, message_id),
    KEY idx_module_report_schedule_delivery_history (
        system_id, tenant_id, recipient_member_id,
        delivered_at DESC, occurrence_id DESC),
    CONSTRAINT fk_module_report_schedule_delivery_occurrence_recipient
        FOREIGN KEY (
            system_id, tenant_id, occurrence_id, recipient_member_id)
        REFERENCES un_module_report_schedule_occurrence_recipient (
            system_id, tenant_id, occurrence_id, recipient_member_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_module_report_schedule_delivery_member FOREIGN KEY (
        system_id, recipient_member_id, tenant_id)
        REFERENCES un_plat_member_tenant (
            system_id, member_id, tenant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_module_report_schedule_delivery_identity CHECK (
        id > 0 AND occurrence_id > 0 AND recipient_member_id > 0
        AND message_id > 0 AND CHAR_LENGTH(TRIM(delivery_key)) BETWEEN 1 AND 256)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
