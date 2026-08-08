-- Immutable approval deadline policies, runtime timestamps and worker claim indexes.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN deadline_policies JSON NULL
        AFTER quorum_rules,
    ADD CONSTRAINT ck_flow_draft_deadline_policies CHECK (
        deadline_policies IS NULL OR JSON_TYPE(deadline_policies) = 'OBJECT'
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN deadline_policies JSON NULL
        AFTER quorum_rules,
    ADD CONSTRAINT ck_flow_version_deadline_policies CHECK (
        deadline_policies IS NULL OR JSON_TYPE(deadline_policies) = 'OBJECT'
    );

ALTER TABLE un_flow_instance
    ADD COLUMN deadline_policy JSON NULL
        AFTER required_approvals,
    ADD COLUMN deadline_remind_at DATETIME(6) NULL
        AFTER deadline_policy,
    ADD COLUMN deadline_due_at DATETIME(6) NULL
        AFTER deadline_remind_at,
    ADD COLUMN deadline_reminded_at DATETIME(6) NULL
        AFTER deadline_due_at,
    ADD COLUMN deadline_processed_at DATETIME(6) NULL
        AFTER deadline_reminded_at,
    ADD CONSTRAINT ck_flow_instance_deadline CHECK (
        (
            deadline_policy IS NULL
            AND deadline_remind_at IS NULL
            AND deadline_due_at IS NULL
            AND deadline_reminded_at IS NULL
            AND deadline_processed_at IS NULL
        )
        OR (
            JSON_TYPE(deadline_policy) = 'OBJECT'
            AND deadline_due_at IS NOT NULL
            AND (deadline_remind_at IS NULL OR deadline_remind_at < deadline_due_at)
            AND (deadline_reminded_at IS NULL OR deadline_remind_at IS NOT NULL)
            AND (deadline_processed_at IS NULL OR deadline_processed_at >= deadline_due_at)
        )
    ),
    ADD INDEX ix_flow_instance_deadline_poll
        (system_id, tenant_id, status, deadline_processed_at, deadline_remind_at, deadline_due_at);

ALTER TABLE un_flow_parallel_branch_execution
    ADD COLUMN deadline_policy JSON NULL
        AFTER required_approvals,
    ADD COLUMN deadline_remind_at DATETIME(6) NULL
        AFTER deadline_policy,
    ADD COLUMN deadline_due_at DATETIME(6) NULL
        AFTER deadline_remind_at,
    ADD COLUMN deadline_reminded_at DATETIME(6) NULL
        AFTER deadline_due_at,
    ADD COLUMN deadline_processed_at DATETIME(6) NULL
        AFTER deadline_reminded_at,
    ADD CONSTRAINT ck_flow_parallel_branch_deadline CHECK (
        (
            deadline_policy IS NULL
            AND deadline_remind_at IS NULL
            AND deadline_due_at IS NULL
            AND deadline_reminded_at IS NULL
            AND deadline_processed_at IS NULL
        )
        OR (
            JSON_TYPE(deadline_policy) = 'OBJECT'
            AND deadline_due_at IS NOT NULL
            AND (deadline_remind_at IS NULL OR deadline_remind_at < deadline_due_at)
            AND (deadline_reminded_at IS NULL OR deadline_remind_at IS NOT NULL)
            AND (deadline_processed_at IS NULL OR deadline_processed_at >= deadline_due_at)
        )
    ),
    ADD INDEX ix_flow_branch_deadline_poll
        (system_id, tenant_id, status, deadline_processed_at, deadline_remind_at, deadline_due_at);

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

ALTER TABLE un_flow_history_event
    MODIFY COLUMN event_type VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL;

ALTER TABLE un_flow_history_event
    ADD CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'APPROVED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL)
        OR (event_sequence >= 2 AND event_type = 'REJECTED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'REJECTED')
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
            AND target_member_id > 0 AND assignment_position IN ('BEFORE', 'AFTER')
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
        OR (event_sequence >= 2 AND event_type = 'DEADLINE_AUTO_APPROVED'
            AND from_status = 'PENDING' AND to_status IN ('PENDING', 'APPROVED')
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
        OR (event_sequence >= 2 AND event_type = 'DEADLINE_AUTO_REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED'
            AND target_member_id IS NULL AND assignment_position IS NULL
            AND target_step_index IS NULL
            AND CHAR_LENGTH(TRIM(comment)) BETWEEN 1 AND 500)
    );
