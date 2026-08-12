-- Count/percentage quorum rules with immutable runtime required-count snapshots.

ALTER TABLE un_flow_definition_draft
    DROP CHECK ck_flow_draft_approval_mode,
    ADD COLUMN quorum_rules JSON NULL
        AFTER approver_sources,
    ADD CONSTRAINT ck_flow_draft_approval_mode CHECK (
        approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL', 'QUORUM')
    ),
    ADD CONSTRAINT ck_flow_draft_quorum_rules CHECK (
        quorum_rules IS NULL OR JSON_TYPE(quorum_rules) = 'OBJECT'
    );

ALTER TABLE un_flow_definition_version
    DROP CHECK ck_flow_version_approval_mode,
    ADD COLUMN quorum_rules JSON NULL
        AFTER approver_sources,
    ADD CONSTRAINT ck_flow_version_approval_mode CHECK (
        approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL', 'QUORUM')
    ),
    ADD CONSTRAINT ck_flow_version_quorum_rules CHECK (
        quorum_rules IS NULL OR JSON_TYPE(quorum_rules) = 'OBJECT'
    );

ALTER TABLE un_flow_instance
    DROP CHECK ck_flow_instance_approval_mode,
    ADD COLUMN required_approvals TINYINT UNSIGNED NOT NULL DEFAULT 1
        AFTER approval_mode,
    ADD CONSTRAINT ck_flow_instance_approval_mode CHECK (
        approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL', 'QUORUM')
    );

UPDATE un_flow_instance
SET required_approvals = JSON_LENGTH(approver_ids_json)
WHERE approval_mode IN ('SEQUENTIAL', 'ALL');

ALTER TABLE un_flow_instance
    ADD CONSTRAINT ck_flow_instance_required_approvals CHECK (
        required_approvals BETWEEN 1 AND JSON_LENGTH(approver_ids_json)
        AND (
            (approval_mode = 'ANY' AND required_approvals = 1)
            OR (
                approval_mode IN ('SEQUENTIAL', 'ALL')
                AND required_approvals = JSON_LENGTH(approver_ids_json)
            )
            OR approval_mode = 'QUORUM'
        )
    );

ALTER TABLE un_flow_parallel_branch_execution
    DROP CHECK ck_flow_parallel_branch_route,
    ADD COLUMN required_approvals TINYINT UNSIGNED NOT NULL DEFAULT 1
        AFTER approval_mode;

UPDATE un_flow_parallel_branch_execution
SET required_approvals = JSON_LENGTH(approver_ids_json)
WHERE approval_mode IN ('SEQUENTIAL', 'ALL');

ALTER TABLE un_flow_parallel_branch_execution
    ADD CONSTRAINT ck_flow_parallel_branch_route CHECK (
        JSON_TYPE(approver_ids_json) = 'ARRAY'
        AND JSON_LENGTH(approver_ids_json) BETWEEN 1 AND 10
        AND current_step_index < JSON_LENGTH(approver_ids_json)
        AND approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL', 'QUORUM')
        AND required_approvals BETWEEN 1 AND JSON_LENGTH(approver_ids_json)
        AND (
            (approval_mode = 'ANY' AND required_approvals = 1)
            OR (
                approval_mode IN ('SEQUENTIAL', 'ALL')
                AND required_approvals = JSON_LENGTH(approver_ids_json)
            )
            OR approval_mode = 'QUORUM'
        )
    );
