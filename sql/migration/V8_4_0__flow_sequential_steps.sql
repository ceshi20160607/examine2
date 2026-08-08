-- P5 sequential approval. Preserve legacy approver columns while adding ordered draft/version snapshots.

CREATE TABLE un_flow_definition_draft_step (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    step_no INT UNSIGNED NOT NULL,
    approver_id BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, definition_id, step_no),
    KEY idx_flow_draft_step_approver (
        system_id, tenant_id, approver_id, definition_id, step_no
    ),
    CONSTRAINT fk_flow_draft_step_draft FOREIGN KEY (
        system_id, tenant_id, definition_id
    ) REFERENCES un_flow_definition_draft (
        system_id, tenant_id, definition_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_draft_step_number CHECK (step_no BETWEEN 1 AND 10),
    CONSTRAINT ck_flow_draft_step_approver CHECK (approver_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_definition_version_step (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    step_no INT UNSIGNED NOT NULL,
    approver_id BIGINT NOT NULL,
    PRIMARY KEY (system_id, tenant_id, definition_id, version_no, step_no),
    KEY idx_flow_version_step_approver (
        system_id, tenant_id, approver_id, definition_id, version_no, step_no
    ),
    CONSTRAINT fk_flow_version_step_version FOREIGN KEY (
        system_id, tenant_id, definition_id, version_no
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_version_step_number CHECK (step_no BETWEEN 1 AND 10),
    CONSTRAINT ck_flow_version_step_approver CHECK (approver_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_flow_definition_draft_step (
    system_id, tenant_id, definition_id, step_no, approver_id
)
SELECT system_id, tenant_id, definition_id, 1, approver_id
FROM un_flow_definition_draft;

INSERT INTO un_flow_definition_version_step (
    system_id, tenant_id, definition_id, version_no, step_no, approver_id
)
SELECT system_id, tenant_id, definition_id, version_no, 1, approver_id
FROM un_flow_definition_version;

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_state_version;

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_completion;

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_state_version CHECK (state_version BETWEEN 0 AND 10);

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_completion CHECK (
        (status = 'PENDING' AND completed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED')
            AND state_version BETWEEN 1 AND 10
            AND completed_at IS NOT NULL
            AND completed_at >= started_at)
    );

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_sequence;

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_sequence CHECK (event_sequence BETWEEN 1 AND 11);

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING')
        OR (event_sequence BETWEEN 2 AND 11 AND event_type = 'APPROVED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'APPROVED'))
        OR (event_sequence BETWEEN 2 AND 11 AND event_type = 'REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED')
    );
