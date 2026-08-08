-- Immutable, tenant-scoped task reminder generations with renewable worker leases.

ALTER TABLE un_work_task
    ADD COLUMN reminder_at DATETIME(6) NULL AFTER due_at,
    ADD KEY idx_work_task_reminder_window (
        system_id, tenant_id, reminder_at, status, id),
    ADD CONSTRAINT ck_work_task_reminder_schedule CHECK (
        reminder_at IS NULL
        OR (status='OPEN' AND (due_at IS NULL OR reminder_at<=due_at)));

CREATE TABLE un_work_task_reminder (
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    task_id BIGINT UNSIGNED NOT NULL,
    generation INT UNSIGNED NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL,
    lease_owner VARCHAR(160) NULL,
    lease_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    sent_at DATETIME(6) NULL,
    failed_at DATETIME(6) NULL,
    cancelled_at DATETIME(6) NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_message VARCHAR(500) NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, task_id, generation),
    UNIQUE KEY uk_work_task_reminder_generation (
        system_id, tenant_id, task_id, generation),
    KEY idx_work_task_reminder_due (
        status, scheduled_at, task_id, generation, system_id, tenant_id),
    KEY idx_work_task_reminder_lease (
        status, lease_expires_at, task_id, generation, system_id, tenant_id),
    CONSTRAINT fk_work_task_reminder_task FOREIGN KEY (
        system_id, tenant_id, task_id)
        REFERENCES un_work_task (system_id, tenant_id, id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_work_task_reminder_identity CHECK (
        system_id>0 AND tenant_id>0 AND task_id>0 AND generation>0),
    CONSTRAINT ck_work_task_reminder_status CHECK (
        status IN ('PENDING','PROCESSING','SENT','CANCELLED','FAILED')),
    CONSTRAINT ck_work_task_reminder_time CHECK (
        scheduled_at>=created_at AND updated_at>=created_at AND version>0),
    CONSTRAINT ck_work_task_reminder_lease CHECK (
        (status='PROCESSING'
          AND attempt_count>0
          AND lease_owner IS NOT NULL
          AND CHAR_LENGTH(TRIM(lease_owner)) BETWEEN 1 AND 160
          AND lease_token_hash REGEXP '^[0-9a-f]{64}$'
          AND lease_expires_at IS NOT NULL
          AND lease_expires_at>updated_at)
        OR (status<>'PROCESSING'
          AND lease_owner IS NULL
          AND lease_token_hash IS NULL
          AND lease_expires_at IS NULL)),
    CONSTRAINT ck_work_task_reminder_state CHECK (
        (status IN ('PENDING','PROCESSING')
          AND sent_at IS NULL AND failed_at IS NULL AND cancelled_at IS NULL
          AND failure_code IS NULL AND failure_message IS NULL)
        OR (status='SENT' AND attempt_count>0
          AND sent_at BETWEEN created_at AND updated_at
          AND failed_at IS NULL AND cancelled_at IS NULL
          AND failure_code IS NULL AND failure_message IS NULL)
        OR (status='CANCELLED'
          AND cancelled_at BETWEEN created_at AND updated_at
          AND sent_at IS NULL AND failed_at IS NULL
          AND failure_code IS NULL AND failure_message IS NULL)
        OR (status='FAILED' AND attempt_count>0
          AND failed_at BETWEEN created_at AND updated_at
          AND sent_at IS NULL AND cancelled_at IS NULL
          AND failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
          AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
