-- Synchronous exact-version child approvals for completion pipelines.
-- Every relationship is tenant scoped and restrictive; no historical run may
-- be erased through a parent, child, execution or version deletion.

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_start_context,
    ADD CONSTRAINT ck_flow_instance_start_context CHECK (
        start_context IS NULL
        OR (
            JSON_TYPE(start_context) = 'OBJECT'
            AND JSON_LENGTH(start_context) = 6
            AND JSON_CONTAINS_PATH(
                start_context,
                'all',
                '$.requesterMemberId',
                '$.moduleCode',
                '$.recordId',
                '$.valuesJson',
                '$.rootInstanceId',
                '$.subflowDepth'
            ) = 1
            AND JSON_TYPE(JSON_EXTRACT(
                start_context, '$.valuesJson')) = 'OBJECT'
            AND JSON_LENGTH(JSON_EXTRACT(
                start_context, '$.valuesJson')) <= 200
            AND JSON_TYPE(JSON_EXTRACT(
                start_context, '$.subflowDepth')) = 'INTEGER'
            AND (
                (
                    JSON_TYPE(JSON_EXTRACT(
                        start_context, '$.rootInstanceId')) = 'NULL'
                    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                        start_context, '$.subflowDepth')) AS UNSIGNED) = 0
                )
                OR (
                    JSON_TYPE(JSON_EXTRACT(
                        start_context, '$.rootInstanceId'))
                        IN ('INTEGER', 'UNSIGNED INTEGER')
                    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                        start_context, '$.rootInstanceId')) AS UNSIGNED) > 0
                    AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                        start_context, '$.subflowDepth')) AS UNSIGNED)
                        BETWEEN 1 AND 8
                )
            )
            AND OCTET_LENGTH(start_context) <= 262144
        )
    );

ALTER TABLE un_flow_completion_execution
    DROP CHECK ck_flow_completion_step,
    DROP CHECK ck_flow_completion_status,
    DROP CHECK ck_flow_completion_times,
    MODIFY COLUMN active_slot TINYINT UNSIGNED GENERATED ALWAYS AS (
        CASE
            WHEN status IN (
                'AVAILABLE', 'LEASED', 'RETRYING', 'RUNNING', 'FAILED'
            ) THEN 1
            ELSE NULL
        END
    ) STORED,
    ADD CONSTRAINT ck_flow_completion_step CHECK (
        step_code REGEXP '^[a-z][a-z0-9_]{0,63}$'
        AND CHAR_LENGTH(TRIM(step_name)) BETWEEN 1 AND 80
        AND execution_type IN ('EXTERNAL_TASK', 'WEBHOOK', 'SUBFLOW')
        AND JSON_TYPE(config_json) = 'OBJECT'
        AND OCTET_LENGTH(config_json) <= 4096
        AND JSON_TYPE(payload_json) = 'OBJECT'
        AND OCTET_LENGTH(payload_json) <= 65535
        AND (
            execution_type <> 'SUBFLOW'
            OR (
                JSON_EXTRACT(config_json, '$.definitionId') IS NOT NULL
                AND JSON_EXTRACT(config_json, '$.version') IS NOT NULL
                AND JSON_TYPE(JSON_EXTRACT(
                    config_json, '$.definitionId'))
                    IN ('INTEGER', 'UNSIGNED INTEGER')
                AND JSON_TYPE(JSON_EXTRACT(
                    config_json, '$.version')) = 'INTEGER'
                AND
                CAST(JSON_UNQUOTE(JSON_EXTRACT(
                    config_json, '$.definitionId')) AS UNSIGNED) > 0
                AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                    config_json, '$.version')) AS UNSIGNED) > 0
            )
        )
    ),
    ADD CONSTRAINT ck_flow_completion_status CHECK (
        status IN (
            'WAITING', 'AVAILABLE', 'LEASED', 'RETRYING', 'RUNNING',
            'SUCCEEDED', 'FAILED', 'CANCELLED'
        )
    ),
    ADD CONSTRAINT ck_flow_completion_times CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (terminal_at IS NULL OR terminal_at >= created_at)
        AND (
            (status = 'WAITING' AND available_at IS NULL
                AND started_at IS NULL AND terminal_at IS NULL)
            OR (status IN ('AVAILABLE', 'RETRYING')
                AND available_at IS NOT NULL AND terminal_at IS NULL)
            OR (status = 'LEASED'
                AND available_at IS NOT NULL AND terminal_at IS NULL)
            OR (status = 'RUNNING'
                AND execution_type = 'SUBFLOW'
                AND available_at IS NOT NULL
                AND started_at IS NOT NULL
                AND terminal_at IS NULL)
            OR (status IN ('SUCCEEDED', 'FAILED', 'CANCELLED')
                AND terminal_at IS NOT NULL)
        )
    );

ALTER TABLE un_flow_completion_attempt
    DROP CHECK ck_flow_completion_attempt_event,
    ADD CONSTRAINT ck_flow_completion_attempt_event CHECK (
        event_type IN (
            'ACTIVATED', 'STARTED', 'CLAIMED', 'LEASE_EXPIRED', 'RETRIED',
            'SUCCEEDED', 'FAILED', 'CANCELLED'
        )
    );

CREATE TABLE un_flow_subflow_run (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    subflow_run_id BIGINT NOT NULL,
    execution_id BIGINT NOT NULL,
    attempt_number INT UNSIGNED NOT NULL,
    launch_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    child_instance_id BIGINT NOT NULL,
    target_definition_id BIGINT NOT NULL,
    target_definition_version INT UNSIGNED NOT NULL,
    root_instance_id BIGINT NOT NULL,
    subflow_depth TINYINT UNSIGNED NOT NULL,
    child_status VARCHAR(24)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    launched_at DATETIME(6) NOT NULL,
    terminal_at DATETIME(6) NULL,
    result_code VARCHAR(24)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_applied_at DATETIME(6) NULL,
    state_version INT UNSIGNED NOT NULL DEFAULT 0,
    PRIMARY KEY (system_id, tenant_id, subflow_run_id),
    UNIQUE KEY uk_flow_subflow_execution_attempt (
        system_id, tenant_id, execution_id, attempt_number
    ),
    UNIQUE KEY uk_flow_subflow_launch_key (
        system_id, tenant_id, launch_key
    ),
    UNIQUE KEY uk_flow_subflow_child (
        system_id, tenant_id, child_instance_id
    ),
    KEY idx_flow_subflow_execution (
        system_id, tenant_id, execution_id, attempt_number
    ),
    KEY idx_flow_subflow_running (
        system_id, tenant_id, child_status, launched_at, subflow_run_id
    ),
    KEY idx_flow_subflow_result_due (
        system_id, tenant_id, result_applied_at, terminal_at, subflow_run_id
    ),
    CONSTRAINT fk_flow_subflow_execution FOREIGN KEY (
        system_id, tenant_id, execution_id
    ) REFERENCES un_flow_completion_execution (
        system_id, tenant_id, execution_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_subflow_child FOREIGN KEY (
        system_id, tenant_id, child_instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_subflow_root FOREIGN KEY (
        system_id, tenant_id, root_instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_subflow_target FOREIGN KEY (
        system_id, tenant_id,
        target_definition_id, target_definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_subflow_identity CHECK (
        subflow_run_id > 0
        AND execution_id > 0
        AND attempt_number > 0
        AND launch_key REGEXP '^[0-9a-f]{64}$'
        AND child_instance_id > 0
        AND target_definition_id > 0
        AND target_definition_version > 0
        AND root_instance_id > 0
        AND subflow_depth BETWEEN 1 AND 8
    ),
    CONSTRAINT ck_flow_subflow_result CHECK (
        child_status IN (
            'RUNNING', 'APPROVED_COMPLETED', 'REJECTED',
            'WITHDRAWN', 'TERMINATED'
        )
        AND (
            (
                child_status = 'RUNNING'
                AND terminal_at IS NULL
                AND result_code IS NULL
                AND result_applied_at IS NULL
            )
            OR (
                child_status <> 'RUNNING'
                AND terminal_at IS NOT NULL
                AND terminal_at >= launched_at
                AND result_code = child_status
                AND (
                    result_applied_at IS NULL
                    OR result_applied_at >= terminal_at
                )
            )
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
