-- Immutable human approval/rejection opinion requirements.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN decision_comment_policies JSON NULL
        AFTER deadline_policies,
    ADD CONSTRAINT ck_flow_draft_decision_comment_policies CHECK (
        decision_comment_policies IS NULL
        OR JSON_TYPE(decision_comment_policies) = 'OBJECT'
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN decision_comment_policies JSON NULL
        AFTER deadline_policies,
    ADD CONSTRAINT ck_flow_version_decision_comment_policies CHECK (
        decision_comment_policies IS NULL
        OR JSON_TYPE(decision_comment_policies) = 'OBJECT'
    );

ALTER TABLE un_flow_instance
    ADD COLUMN decision_comment_policy JSON NULL
        AFTER deadline_processed_at,
    ADD CONSTRAINT ck_flow_instance_decision_comment_policy CHECK (
        decision_comment_policy IS NULL
        OR JSON_TYPE(decision_comment_policy) = 'OBJECT'
    );

ALTER TABLE un_flow_parallel_branch_execution
    ADD COLUMN decision_comment_policy JSON NULL
        AFTER deadline_processed_at,
    ADD CONSTRAINT ck_flow_branch_decision_comment_policy CHECK (
        decision_comment_policy IS NULL
        OR JSON_TYPE(decision_comment_policy) = 'OBJECT'
    );
