-- P5 single-node approval persistence. Every key and foreign key carries system/tenant scope.

CREATE TABLE un_flow_definition_draft (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    name VARCHAR(160) NOT NULL,
    approver_id BIGINT NOT NULL,
    revision INT UNSIGNED NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, definition_id),
    KEY idx_flow_draft_approver (system_id, tenant_id, approver_id, updated_at),
    CONSTRAINT ck_flow_draft_scope CHECK (system_id > 0 AND tenant_id > 0 AND definition_id > 0),
    CONSTRAINT ck_flow_draft_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_flow_draft_approver CHECK (approver_id > 0),
    CONSTRAINT ck_flow_draft_revision CHECK (revision > 0),
    CONSTRAINT ck_flow_draft_time CHECK (updated_at >= created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_definition_version (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    name VARCHAR(160) NOT NULL,
    approver_id BIGINT NOT NULL,
    source_revision INT UNSIGNED NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, definition_id, version_no),
    UNIQUE KEY uk_flow_version_source_revision (
        system_id, tenant_id, definition_id, source_revision
    ),
    KEY idx_flow_version_approver (
        system_id, tenant_id, approver_id, published_at, definition_id
    ),
    CONSTRAINT fk_flow_version_draft FOREIGN KEY (
        system_id, tenant_id, definition_id
    ) REFERENCES un_flow_definition_draft (
        system_id, tenant_id, definition_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_version_number CHECK (version_no > 0 AND source_revision > 0),
    CONSTRAINT ck_flow_version_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 160),
    CONSTRAINT ck_flow_version_approver CHECK (approver_id > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_instance (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    business_key VARCHAR(160) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    requester_id BIGINT NOT NULL,
    approver_id BIGINT NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    state_version INT UNSIGNED NOT NULL DEFAULT 0,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    PRIMARY KEY (system_id, tenant_id, instance_id),
    UNIQUE KEY uk_flow_instance_business (
        system_id, tenant_id, definition_id, business_key
    ),
    KEY idx_flow_instance_inbox (
        system_id, tenant_id, approver_id, status, started_at, instance_id
    ),
    KEY idx_flow_instance_requester (
        system_id, tenant_id, requester_id, started_at, instance_id
    ),
    CONSTRAINT fk_flow_instance_version FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_instance_identity CHECK (
        instance_id > 0 AND requester_id > 0 AND approver_id > 0
    ),
    CONSTRAINT ck_flow_instance_business CHECK (
        CHAR_LENGTH(TRIM(business_key)) BETWEEN 1 AND 160
    ),
    CONSTRAINT ck_flow_instance_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_flow_instance_state_version CHECK (state_version BETWEEN 0 AND 1),
    CONSTRAINT ck_flow_instance_completion CHECK (
        (status = 'PENDING' AND state_version = 0 AND completed_at IS NULL)
        OR (status IN ('APPROVED', 'REJECTED') AND state_version = 1
            AND completed_at IS NOT NULL AND completed_at >= started_at)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_history_event (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    event_sequence INT UNSIGNED NOT NULL,
    event_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    actor_id BIGINT NOT NULL,
    from_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    comment VARCHAR(1000) NOT NULL DEFAULT '',
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, instance_id, event_sequence),
    KEY idx_flow_history_actor (
        system_id, tenant_id, actor_id, occurred_at, instance_id
    ),
    CONSTRAINT fk_flow_history_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_history_sequence CHECK (event_sequence BETWEEN 1 AND 2),
    CONSTRAINT ck_flow_history_actor CHECK (actor_id > 0),
    CONSTRAINT ck_flow_history_event CHECK (
        (event_sequence = 1 AND event_type = 'STARTED'
            AND from_status IS NULL AND to_status = 'PENDING')
        OR (event_sequence = 2 AND event_type = 'APPROVED'
            AND from_status = 'PENDING' AND to_status = 'APPROVED')
        OR (event_sequence = 2 AND event_type = 'REJECTED'
            AND from_status = 'PENDING' AND to_status = 'REJECTED')
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
