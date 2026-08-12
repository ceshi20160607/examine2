-- Frozen ordered approval-stage definitions and restart-safe instance state.
-- NULL definitions and NULL instance state preserve the legacy one-stage path.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN approval_stages JSON NULL
        AFTER decision_comment_policies,
    ADD CONSTRAINT ck_flow_draft_approval_stages CHECK (
        approval_stages IS NULL
        OR (
            JSON_TYPE(approval_stages) = 'ARRAY'
            AND JSON_LENGTH(approval_stages) BETWEEN 2 AND 10
            AND OCTET_LENGTH(approval_stages) <= 262144
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN approval_stages JSON NULL
        AFTER decision_comment_policies,
    ADD CONSTRAINT ck_flow_version_approval_stages CHECK (
        approval_stages IS NULL
        OR (
            JSON_TYPE(approval_stages) = 'ARRAY'
            AND JSON_LENGTH(approval_stages) BETWEEN 2 AND 10
            AND OCTET_LENGTH(approval_stages) <= 262144
        )
    );

ALTER TABLE un_flow_instance
    ADD COLUMN approval_stage_state JSON NULL
        AFTER decision_comment_policy,
    ADD COLUMN current_stage_index INT UNSIGNED NOT NULL DEFAULT 0
        AFTER current_step_index,
    ADD CONSTRAINT ck_flow_instance_current_stage_index CHECK (
        current_stage_index <= 9
    ),
    ADD CONSTRAINT ck_flow_instance_approval_stage_state CHECK (
        (
            approval_stage_state IS NULL
            AND current_stage_index = 0
        )
        OR (
            JSON_TYPE(approval_stage_state) = 'ARRAY'
            AND JSON_LENGTH(approval_stage_state) BETWEEN 1 AND 10
            AND current_stage_index < JSON_LENGTH(approval_stage_state)
            AND OCTET_LENGTH(approval_stage_state) <= 262144
        )
    );
