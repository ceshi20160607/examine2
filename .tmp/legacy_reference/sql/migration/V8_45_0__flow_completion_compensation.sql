-- Durable reverse compensation for failed completion plans. Existing data is
-- explicitly MANUAL_RETRY; compensation snapshots never depend on live drafts.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN completion_failure_policy VARCHAR(20)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL_RETRY'
        AFTER decision_evidence_policies,
    ADD CONSTRAINT ck_flow_draft_completion_failure_policy CHECK (
        completion_failure_policy IN ('MANUAL_RETRY', 'COMPENSATE')
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN completion_failure_policy VARCHAR(20)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL_RETRY'
        AFTER decision_evidence_policies,
    ADD CONSTRAINT ck_flow_version_completion_failure_policy CHECK (
        completion_failure_policy IN ('MANUAL_RETRY', 'COMPENSATE')
    );

ALTER TABLE un_flow_instance
    ADD COLUMN completion_failure_policy VARCHAR(20)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'MANUAL_RETRY'
        AFTER completion_phase,
    DROP CHECK ck_flow_instance_completion_phase,
    ADD CONSTRAINT ck_flow_instance_completion_failure_policy CHECK (
        completion_failure_policy IN ('MANUAL_RETRY', 'COMPENSATE')
    ),
    ADD CONSTRAINT ck_flow_instance_completion_phase CHECK (
        (status = 'PENDING' AND completion_phase IN (
            'HUMAN_APPROVAL', 'EXTERNAL_EXECUTION', 'COMPENSATING'))
        OR (status <> 'PENDING' AND completion_phase = 'COMPLETED')
    ),
    ADD CONSTRAINT ck_flow_instance_compensating_policy CHECK (
        completion_phase <> 'COMPENSATING'
        OR completion_failure_policy = 'COMPENSATE'
    );

ALTER TABLE un_flow_history_event DROP CHECK ck_flow_history_event;

-- Preserve the complete V8.42 history contract verbatim and add exactly one
-- terminal compensation transition. Keeping this explicit avoids depending
-- on vendor-specific serialization of information_schema CHECK expressions.
ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'APPROVED'
            AND from_status = 'PENDING'
            AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'REJECTED'
            AND from_status = 'PENDING'
            AND to_status IN ('PENDING', 'REJECTED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'WITHDRAWN'
            AND from_status = 'PENDING' AND to_status = 'WITHDRAWN'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'TERMINATED'
            AND from_status = 'PENDING' AND to_status = 'TERMINATED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'TRANSFERRED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'ADD_SIGNED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0
            AND assignment_position IN ('BEFORE', 'AFTER')
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'RETURNED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'CLAIM_CANCELLED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'CLAIMED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(comment) <= 500)
        OR (event_sequence >= 2 AND event_type = 'SIGN_REMOVED'
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id > 0 AND assignment_position IS NULL
            AND target_step_index IS NOT NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type IN (
                'DEADLINE_REMINDER_SENT', 'DEADLINE_OVERDUE'
            )
            AND from_status = 'PENDING' AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2
            AND event_type = 'DEADLINE_AUTO_APPROVED'
            AND from_status = 'PENDING'
            AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2
            AND event_type = 'DEADLINE_AUTO_REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2
            AND event_type = 'COMPLETION_COMPLETED'
            AND from_status = 'PENDING' AND to_status = 'APPROVED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL AND comment = '')
        OR (event_sequence >= 2
            AND event_type = 'COMPLETION_COMPENSATED'
            AND from_status = 'PENDING' AND to_status = 'TERMINATED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL AND comment = '')
    );

CREATE TABLE un_flow_completion_compensation (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    compensation_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    original_execution_id BIGINT NOT NULL,
    original_ordinal TINYINT UNSIGNED NOT NULL,
    reverse_ordinal TINYINT UNSIGNED NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    step_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    step_name VARCHAR(80) NOT NULL,
    execution_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_json JSON NOT NULL,
    payload_json JSON NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count INT UNSIGNED NOT NULL DEFAULT 0,
    state_version INT UNSIGNED NOT NULL DEFAULT 0,
    available_at DATETIME(6) NULL,
    lease_owner VARCHAR(160) NULL,
    lease_token_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_expires_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    started_at DATETIME(6) NULL,
    terminal_at DATETIME(6) NULL,
    result_json JSON NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_message VARCHAR(500) NULL,
    failure_retryable BOOLEAN NULL,
    PRIMARY KEY (system_id, tenant_id, compensation_id),
    UNIQUE KEY uk_flow_compensation_original (
        system_id, tenant_id, instance_id, original_execution_id),
    UNIQUE KEY uk_flow_compensation_reverse (
        system_id, tenant_id, instance_id, reverse_ordinal),
    KEY idx_flow_compensation_lock_order (
        system_id, tenant_id, instance_id, original_ordinal, compensation_id),
    KEY idx_flow_compensation_due (
        system_id, tenant_id, execution_type, status, available_at,
        compensation_id),
    KEY idx_flow_compensation_lease_due (
        system_id, tenant_id, status, lease_expires_at, execution_type,
        compensation_id),
    CONSTRAINT fk_flow_compensation_instance FOREIGN KEY (
        system_id, tenant_id, instance_id)
        REFERENCES un_flow_instance (system_id, tenant_id, instance_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_flow_compensation_original FOREIGN KEY (
        system_id, tenant_id, original_execution_id)
        REFERENCES un_flow_completion_execution (
            system_id, tenant_id, execution_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_flow_compensation_version FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version)
        REFERENCES un_flow_definition_version (
            system_id, tenant_id, definition_id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT ck_flow_compensation_identity CHECK (
        compensation_id > 0 AND instance_id > 0
        AND original_execution_id > 0 AND original_ordinal BETWEEN 0 AND 7
        AND reverse_ordinal BETWEEN 0 AND 7 AND definition_id > 0
        AND definition_version > 0),
    CONSTRAINT ck_flow_compensation_step CHECK (
        step_code REGEXP '^[a-z][a-z0-9_]{0,63}$'
        AND CHAR_LENGTH(TRIM(step_name)) BETWEEN 1 AND 80
        AND execution_type IN ('EXTERNAL_TASK', 'WEBHOOK', 'SUBFLOW')
        AND JSON_TYPE(config_json) = 'OBJECT'
        AND OCTET_LENGTH(config_json) <= 4096
        AND JSON_TYPE(payload_json) = 'OBJECT'
        AND OCTET_LENGTH(payload_json) <= 65535),
    CONSTRAINT ck_flow_compensation_status CHECK (
        status IN ('WAITING', 'AVAILABLE', 'LEASED', 'RETRYING', 'RUNNING',
                   'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_flow_compensation_lease CHECK (
        (status = 'LEASED' AND lease_owner IS NOT NULL
            AND CHAR_LENGTH(TRIM(lease_owner)) BETWEEN 1 AND 160
            AND lease_token_hash REGEXP '^[0-9a-f]{64}$'
            AND lease_expires_at IS NOT NULL AND started_at IS NOT NULL
            AND attempt_count > 0)
        OR (status <> 'LEASED' AND lease_owner IS NULL
            AND lease_token_hash IS NULL AND lease_expires_at IS NULL)),
    CONSTRAINT ck_flow_compensation_times CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (terminal_at IS NULL OR terminal_at >= created_at)
        AND ((status = 'WAITING' AND available_at IS NULL
                AND started_at IS NULL AND terminal_at IS NULL)
          OR (status IN ('AVAILABLE', 'RETRYING', 'LEASED')
                AND available_at IS NOT NULL AND terminal_at IS NULL)
          OR (status = 'RUNNING' AND execution_type = 'SUBFLOW'
                AND available_at IS NOT NULL AND started_at IS NOT NULL
                AND terminal_at IS NULL)
          OR (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
                AND terminal_at IS NOT NULL))),
    CONSTRAINT ck_flow_compensation_result CHECK (
        (status = 'SUCCEEDED' AND result_json IS NOT NULL
            AND JSON_TYPE(result_json) = 'OBJECT'
            AND OCTET_LENGTH(result_json) <= 8192
            AND failure_code IS NULL AND failure_message IS NULL
            AND failure_retryable IS NULL)
        OR (status = 'FAILED' AND result_json IS NULL
            AND failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
            AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500
            AND failure_retryable IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'FAILED') AND result_json IS NULL
            AND ((failure_code IS NULL AND failure_message IS NULL
                    AND failure_retryable IS NULL)
              OR (status IN ('RETRYING', 'CANCELLED')
                    AND failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
                    AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500
                    AND failure_retryable IS NOT NULL))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_compensation_attempt (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    attempt_id BIGINT NOT NULL,
    compensation_id BIGINT NOT NULL,
    attempt_number INT UNSIGNED NOT NULL,
    event_sequence INT UNSIGNED NOT NULL,
    event_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_member_id BIGINT NULL,
    lease_owner VARCHAR(160) NULL,
    idempotency_key_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_json JSON NULL,
    failure_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    failure_message VARCHAR(500) NULL,
    http_status SMALLINT UNSIGNED NULL,
    duration_ms BIGINT UNSIGNED NULL,
    response_sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    started_at DATETIME(6) NULL,
    completed_at DATETIME(6) NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, attempt_id),
    UNIQUE KEY uk_flow_compensation_attempt_sequence (
        system_id, tenant_id, compensation_id, event_sequence),
    UNIQUE KEY uk_flow_compensation_attempt_idempotency (
        system_id, tenant_id, compensation_id, idempotency_key_hash),
    KEY idx_flow_compensation_attempt (
        system_id, tenant_id, compensation_id, occurred_at, attempt_id),
    CONSTRAINT fk_flow_compensation_attempt FOREIGN KEY (
        system_id, tenant_id, compensation_id)
        REFERENCES un_flow_completion_compensation (
            system_id, tenant_id, compensation_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_flow_compensation_attempt_identity CHECK (
        attempt_id > 0 AND compensation_id > 0 AND event_sequence > 0
        AND (actor_member_id IS NULL OR actor_member_id > 0)),
    CONSTRAINT ck_flow_compensation_attempt_event CHECK (
        event_type IN ('ACTIVATED', 'STARTED', 'STAGE_JOINED', 'CLAIMED',
            'LEASE_EXPIRED', 'RETRIED', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_flow_compensation_attempt_values CHECK (
        (lease_owner IS NULL
            OR CHAR_LENGTH(TRIM(lease_owner)) BETWEEN 1 AND 160)
        AND (idempotency_key_hash IS NULL
            OR idempotency_key_hash REGEXP '^[0-9a-f]{64}$')
        AND (result_json IS NULL OR (JSON_TYPE(result_json) = 'OBJECT'
            AND OCTET_LENGTH(result_json) <= 8192))
        AND ((failure_code IS NULL AND failure_message IS NULL)
          OR (failure_code REGEXP '^[A-Z][A-Z0-9_]{0,63}$'
            AND CHAR_LENGTH(TRIM(failure_message)) BETWEEN 1 AND 500))
        AND (http_status IS NULL OR http_status BETWEEN 100 AND 599)
        AND (duration_ms IS NULL OR duration_ms <= 86400000)
        AND (response_sha256 IS NULL
            OR response_sha256 REGEXP '^[0-9a-f]{64}$'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_compensation_subflow_run (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    compensation_subflow_run_id BIGINT NOT NULL,
    compensation_id BIGINT NOT NULL,
    attempt_number INT UNSIGNED NOT NULL,
    launch_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    child_instance_id BIGINT NOT NULL,
    target_definition_id BIGINT NOT NULL,
    target_definition_version INT UNSIGNED NOT NULL,
    root_instance_id BIGINT NOT NULL,
    subflow_depth TINYINT UNSIGNED NOT NULL,
    child_status VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    launched_at DATETIME(6) NOT NULL,
    terminal_at DATETIME(6) NULL,
    result_code VARCHAR(24) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_applied_at DATETIME(6) NULL,
    state_version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (system_id, tenant_id, compensation_subflow_run_id),
    UNIQUE KEY uk_flow_compensation_subflow_attempt (
        system_id, tenant_id, compensation_id, attempt_number),
    UNIQUE KEY uk_flow_compensation_subflow_launch (
        system_id, tenant_id, launch_key),
    UNIQUE KEY uk_flow_compensation_subflow_child (
        system_id, tenant_id, child_instance_id),
    KEY idx_flow_compensation_subflow_result (
        system_id, tenant_id, result_applied_at, terminal_at,
        compensation_subflow_run_id),
    CONSTRAINT fk_flow_compensation_subflow_compensation FOREIGN KEY (
        system_id, tenant_id, compensation_id)
        REFERENCES un_flow_completion_compensation (
            system_id, tenant_id, compensation_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_flow_compensation_subflow_child FOREIGN KEY (
        system_id, tenant_id, child_instance_id)
        REFERENCES un_flow_instance (system_id, tenant_id, instance_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_flow_compensation_subflow_root FOREIGN KEY (
        system_id, tenant_id, root_instance_id)
        REFERENCES un_flow_instance (system_id, tenant_id, instance_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_flow_compensation_subflow_target FOREIGN KEY (
        system_id, tenant_id, target_definition_id,
        target_definition_version)
        REFERENCES un_flow_definition_version (
            system_id, tenant_id, definition_id, version_no)
        ON DELETE RESTRICT,
    CONSTRAINT ck_flow_compensation_subflow_identity CHECK (
        compensation_subflow_run_id > 0 AND compensation_id > 0
        AND attempt_number > 0 AND launch_key REGEXP '^[0-9a-f]{64}$'
        AND child_instance_id > 0 AND target_definition_id > 0
        AND target_definition_version > 0 AND root_instance_id > 0
        AND subflow_depth BETWEEN 1 AND 8),
    CONSTRAINT ck_flow_compensation_subflow_result CHECK (
        child_status IN ('RUNNING', 'APPROVED_COMPLETED', 'REJECTED',
            'WITHDRAWN', 'TERMINATED')
        AND ((child_status = 'RUNNING' AND terminal_at IS NULL
                AND result_code IS NULL AND result_applied_at IS NULL)
          OR (child_status <> 'RUNNING' AND terminal_at IS NOT NULL
                AND terminal_at >= launched_at AND result_code = child_status
                AND (result_applied_at IS NULL
                    OR result_applied_at >= terminal_at))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
