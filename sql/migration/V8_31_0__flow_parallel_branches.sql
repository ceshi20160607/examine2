-- One bounded parallel split/join with durable branch execution snapshots.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN parallel_branches JSON NULL
        AFTER gateway_branches,
    ADD CONSTRAINT ck_flow_draft_parallel_gateway CHECK (
        (gateway_branches IS NULL OR parallel_branches IS NULL)
        AND (
            parallel_branches IS NULL
            OR (
                JSON_TYPE(parallel_branches) = 'ARRAY'
                AND JSON_LENGTH(parallel_branches) BETWEEN 2 AND 5
            )
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN parallel_branches JSON NULL
        AFTER gateway_branches,
    ADD CONSTRAINT ck_flow_version_parallel_gateway CHECK (
        (gateway_branches IS NULL OR parallel_branches IS NULL)
        AND (
            parallel_branches IS NULL
            OR (
                JSON_TYPE(parallel_branches) = 'ARRAY'
                AND JSON_LENGTH(parallel_branches) BETWEEN 2 AND 5
            )
        )
    );

CREATE TABLE un_flow_parallel_branch_execution (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    branch_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    branch_name VARCHAR(80) NOT NULL,
    branch_order TINYINT UNSIGNED NOT NULL,
    approver_id BIGINT NOT NULL,
    approver_ids_json JSON NOT NULL,
    current_step_index TINYINT UNSIGNED NOT NULL DEFAULT 0,
    approval_mode VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    approved_approver_ids_json JSON NOT NULL DEFAULT (JSON_ARRAY()),
    rejected_approver_ids_json JSON NOT NULL DEFAULT (JSON_ARRAY()),
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, instance_id, branch_code),
    UNIQUE KEY uk_flow_parallel_branch_order (
        system_id, tenant_id, instance_id, branch_order
    ),
    UNIQUE KEY uk_flow_parallel_branch_name (
        system_id, tenant_id, instance_id, branch_name
    ),
    KEY idx_flow_parallel_branch_task (
        system_id, tenant_id, status, approver_id, started_at, instance_id
    ),
    CONSTRAINT fk_flow_parallel_branch_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_parallel_branch_identity CHECK (
        branch_code REGEXP '^[a-z][a-z0-9_]{0,63}$'
        AND CHAR_LENGTH(TRIM(branch_name)) BETWEEN 1 AND 80
        AND branch_order BETWEEN 0 AND 4
        AND approver_id > 0
    ),
    CONSTRAINT ck_flow_parallel_branch_route CHECK (
        JSON_TYPE(approver_ids_json) = 'ARRAY'
        AND JSON_LENGTH(approver_ids_json) BETWEEN 1 AND 10
        AND current_step_index < JSON_LENGTH(approver_ids_json)
        AND approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL')
    ),
    CONSTRAINT ck_flow_parallel_branch_decisions CHECK (
        JSON_TYPE(approved_approver_ids_json) = 'ARRAY'
        AND JSON_TYPE(rejected_approver_ids_json) = 'ARRAY'
        AND JSON_LENGTH(approved_approver_ids_json)
            + JSON_LENGTH(rejected_approver_ids_json) <= 10
        AND (
            approval_mode <> 'SEQUENTIAL'
            OR (
                JSON_LENGTH(approved_approver_ids_json) = 0
                AND JSON_LENGTH(rejected_approver_ids_json) = 0
            )
        )
    ),
    CONSTRAINT ck_flow_parallel_branch_status CHECK (
        status IN (
            'PENDING', 'APPROVED', 'REJECTED',
            'CANCELLED', 'WITHDRAWN', 'TERMINATED'
        )
    ),
    CONSTRAINT ck_flow_parallel_branch_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status <> 'PENDING' AND completed_at IS NOT NULL
            AND completed_at >= started_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
