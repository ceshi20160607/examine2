-- Durable branch-local ordered-stage cursors and immutable start context.
-- NULL branch state/start context retain the exact legacy execution path.
-- Branch plans are embedded in the already-versioned gateway branch JSON.

ALTER TABLE un_flow_instance
    ADD COLUMN start_context JSON NULL
        AFTER approval_stage_state,
    ADD CONSTRAINT ck_flow_instance_start_context CHECK (
        start_context IS NULL
        OR (
            JSON_TYPE(start_context) = 'OBJECT'
            AND JSON_LENGTH(start_context) = 4
            AND JSON_CONTAINS_PATH(
                start_context,
                'all',
                '$.requesterMemberId',
                '$.moduleCode',
                '$.recordId',
                '$.valuesJson'
            ) = 1
            AND JSON_TYPE(JSON_EXTRACT(
                start_context, '$.valuesJson')) = 'OBJECT'
            AND JSON_LENGTH(JSON_EXTRACT(
                start_context, '$.valuesJson')) <= 200
            AND OCTET_LENGTH(start_context) <= 262144
        )
    );

ALTER TABLE un_flow_parallel_branch_execution
    ADD COLUMN approval_stage_state JSON NULL
        AFTER decision_comment_policy,
    ADD COLUMN current_stage_index INT UNSIGNED NOT NULL DEFAULT 0
        AFTER current_step_index,
    ADD CONSTRAINT ck_flow_parallel_branch_current_stage_index CHECK (
        current_stage_index <= 9
    ),
    ADD CONSTRAINT ck_flow_parallel_branch_approval_stage_state CHECK (
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
