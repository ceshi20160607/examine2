-- Recipient-scoped normalized Todo projections and immutable idempotent actions.

CREATE TABLE un_todo_item (
    id BIGINT UNSIGNED NOT NULL,
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT UNSIGNED NOT NULL,
    source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id VARCHAR(200) NOT NULL,
    source_version BIGINT UNSIGNED NOT NULL,
    action_scope VARCHAR(200) NOT NULL,
    category VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    priority SMALLINT UNSIGNED NOT NULL,
    title VARCHAR(500) NOT NULL,
    due_at DATETIME(6) NULL,
    route_hint VARCHAR(500) NOT NULL,
    available_actions VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    represented_member_id BIGINT UNSIGNED NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    close_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    closed_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_todo_item_id (id),
    UNIQUE KEY uk_todo_item_identity (
        system_id, tenant_id, recipient_member_id,
        source_type, source_id, action_scope),
    UNIQUE KEY uk_todo_item_scope_recipient (
        system_id, tenant_id, id, recipient_member_id),
    KEY idx_todo_item_recipient_status (
        system_id, tenant_id, recipient_member_id, status,
        priority, due_at, created_at, id),
    KEY idx_todo_item_recipient_category (
        system_id, tenant_id, recipient_member_id, category, status,
        priority, due_at, created_at, id),
    KEY idx_todo_item_source (
        system_id, tenant_id, source_type, source_id, source_version),
    CONSTRAINT ck_todo_item_identity CHECK (
        id>0 AND system_id>0 AND tenant_id>0 AND recipient_member_id>0
        AND source_version>0),
    CONSTRAINT ck_todo_item_source CHECK (
        (source_type='WORK_TASK' AND category='TASK'
          AND available_actions='COMPLETE' AND represented_member_id IS NULL)
        OR (source_type='FLOW_APPROVAL' AND category='APPROVAL'
          AND available_actions='APPROVE,REJECT'
          AND represented_member_id IS NOT NULL AND represented_member_id>0)),
    CONSTRAINT ck_todo_item_text CHECK (
        CHAR_LENGTH(TRIM(source_id)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(action_scope)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(title)) BETWEEN 1 AND 500
        AND CHAR_LENGTH(TRIM(route_hint)) BETWEEN 1 AND 500),
    CONSTRAINT ck_todo_item_priority CHECK (priority<=999),
    CONSTRAINT ck_todo_item_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_todo_item_state CHECK (
        updated_at>=created_at AND version>0
        AND ((status='OPEN' AND close_reason IS NULL AND closed_at IS NULL)
          OR (status='CLOSED'
            AND close_reason IS NOT NULL AND closed_at IS NOT NULL
            AND close_reason IN ('SOURCE_STALE','SOURCE_COMPLETED','SOURCE_MISSING',
              'RECIPIENT_INELIGIBLE','ACTION_COMPLETED')
            AND closed_at BETWEEN created_at AND updated_at)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_todo_action_log (
    id BIGINT UNSIGNED NOT NULL,
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    todo_item_id BIGINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT UNSIGNED NOT NULL,
    actor_member_id BIGINT UNSIGNED NOT NULL,
    caller_idempotency_key VARCHAR(200) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_id VARCHAR(200) NOT NULL,
    source_version BIGINT UNSIGNED NOT NULL,
    requested_action VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_code VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    result_message VARCHAR(500) NULL,
    request_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (system_id, tenant_id, id),
    UNIQUE KEY uk_todo_action_log_id (id),
    UNIQUE KEY uk_todo_action_idempotency (
        system_id, tenant_id, actor_member_id, caller_idempotency_key),
    KEY idx_todo_action_item (
        system_id, tenant_id, todo_item_id, created_at, id),
    CONSTRAINT fk_todo_action_item FOREIGN KEY (
        system_id, tenant_id, todo_item_id, recipient_member_id)
        REFERENCES un_todo_item (
            system_id, tenant_id, id, recipient_member_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_todo_action_identity CHECK (
        id>0 AND system_id>0 AND tenant_id>0 AND todo_item_id>0
        AND recipient_member_id>0 AND actor_member_id>0
        AND actor_member_id=recipient_member_id
        AND source_version>0 AND version>0),
    CONSTRAINT ck_todo_action_source CHECK (
        (source_type='WORK_TASK' AND requested_action='COMPLETE')
        OR (source_type='FLOW_APPROVAL'
          AND requested_action IN ('APPROVE','REJECT'))),
    CONSTRAINT ck_todo_action_text CHECK (
        CHAR_LENGTH(TRIM(caller_idempotency_key)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(source_id)) BETWEEN 1 AND 200
        AND CHAR_LENGTH(TRIM(request_id)) BETWEEN 1 AND 128
        AND CHAR_LENGTH(TRIM(trace_id)) BETWEEN 1 AND 128
        AND (result_message IS NULL
          OR CHAR_LENGTH(TRIM(result_message)) BETWEEN 1 AND 500)),
    CONSTRAINT ck_todo_action_status CHECK (
        status IN ('PROCESSING','COMPLETED')),
    CONSTRAINT ck_todo_action_state CHECK (
        (status='PROCESSING' AND result_code IS NULL
          AND result_message IS NULL AND completed_at IS NULL)
        OR (status='COMPLETED'
          AND result_code IS NOT NULL AND completed_at IS NOT NULL
          AND result_code IN ('SUCCESS','STALE','DENIED','CONFLICT','FAILED')
          AND completed_at>=created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
