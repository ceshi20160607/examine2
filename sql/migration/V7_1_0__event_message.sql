CREATE TABLE un_event_message (
    id BIGINT UNSIGNED NOT NULL,
    system_id BIGINT UNSIGNED NOT NULL,
    tenant_id BIGINT UNSIGNED NOT NULL,
    sender_member_id BIGINT UNSIGNED NOT NULL,
    recipient_member_id BIGINT UNSIGNED NOT NULL,
    template_code VARCHAR(100) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body VARCHAR(4000) NOT NULL,
    target_type VARCHAR(64) NULL,
    target_id VARCHAR(64) NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    read_at DATETIME(6) NULL,
    archived_at DATETIME(6) NULL,
    version BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_message_scope_id (system_id, tenant_id, id),
    KEY idx_event_message_inbox_state (
        system_id, tenant_id, recipient_member_id, status, created_at
    ),
    KEY idx_event_message_template_created (
        system_id, tenant_id, template_code, created_at
    ),
    CONSTRAINT chk_event_message_scope CHECK (system_id > 0 AND tenant_id > 0),
    CONSTRAINT chk_event_message_members CHECK (sender_member_id > 0 AND recipient_member_id > 0),
    CONSTRAINT chk_event_message_target CHECK (
        (target_type IS NULL AND target_id IS NULL)
        OR (target_type IS NOT NULL AND target_id IS NOT NULL)
    ),
    CONSTRAINT chk_event_message_status CHECK (status IN ('UNREAD', 'READ', 'ARCHIVED')),
    CONSTRAINT chk_event_message_state CHECK (
        (status = 'UNREAD' AND read_at IS NULL AND archived_at IS NULL)
        OR (status = 'READ' AND read_at IS NOT NULL AND archived_at IS NULL)
        OR (status = 'ARCHIVED' AND read_at IS NOT NULL AND archived_at IS NOT NULL)
    ),
    CONSTRAINT chk_event_message_version CHECK (version > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
