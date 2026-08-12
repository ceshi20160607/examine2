-- Route-level sequential, any-one and all-member approval execution snapshots.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN approval_mode VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SEQUENTIAL'
        AFTER approver_id,
    ADD CONSTRAINT ck_flow_draft_approval_mode CHECK (
        approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL')
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN approval_mode VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SEQUENTIAL'
        AFTER approver_id,
    ADD CONSTRAINT ck_flow_version_approval_mode CHECK (
        approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL')
    );

ALTER TABLE un_flow_instance
    ADD COLUMN approval_mode VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'SEQUENTIAL'
        AFTER approver_ids_json,
    ADD COLUMN approved_approver_ids_json JSON NOT NULL DEFAULT (JSON_ARRAY())
        AFTER approval_mode,
    ADD COLUMN rejected_approver_ids_json JSON NOT NULL DEFAULT (JSON_ARRAY())
        AFTER approved_approver_ids_json,
    ADD CONSTRAINT ck_flow_instance_approval_mode CHECK (
        approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL')
    ),
    ADD CONSTRAINT ck_flow_instance_approval_decisions CHECK (
        JSON_TYPE(approved_approver_ids_json) = 'ARRAY'
        AND JSON_TYPE(rejected_approver_ids_json) = 'ARRAY'
        AND JSON_LENGTH(approved_approver_ids_json) BETWEEN 0 AND 10
        AND JSON_LENGTH(rejected_approver_ids_json) BETWEEN 0 AND 10
        AND JSON_LENGTH(approved_approver_ids_json)
            + JSON_LENGTH(rejected_approver_ids_json) <= 10
        AND (
            approval_mode <> 'SEQUENTIAL'
            OR (
                JSON_LENGTH(approved_approver_ids_json) = 0
                AND JSON_LENGTH(rejected_approver_ids_json) = 0
            )
        )
    );

ALTER TABLE un_flow_history_event
    DROP CHECK ck_flow_history_event;

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
    );
