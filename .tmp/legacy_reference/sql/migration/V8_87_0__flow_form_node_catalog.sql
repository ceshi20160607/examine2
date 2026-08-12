-- Cycle115 P5 closure: immutable Flow node graphs, field-form snapshots,
-- dependency impact facts and durable node execution/history.

CREATE TABLE un_flow_definition_extension_draft (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    source_revision INT UNSIGNED NOT NULL,
    graph_json JSON NOT NULL,
    graph_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, definition_id),
    CONSTRAINT fk_flow_extension_draft_definition FOREIGN KEY (
        system_id, tenant_id, definition_id
    ) REFERENCES un_flow_definition_draft (
        system_id, tenant_id, definition_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_extension_draft_identity CHECK (
        source_revision > 0 AND updated_by > 0),
    CONSTRAINT ck_flow_extension_draft_graph CHECK (
        JSON_TYPE(graph_json) = 'OBJECT'
        AND JSON_LENGTH(JSON_EXTRACT(graph_json, '$.nodes')) BETWEEN 2 AND 200),
    CONSTRAINT ck_flow_extension_draft_checksum CHECK (
        graph_checksum REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_definition_extension_version (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    source_revision INT UNSIGNED NOT NULL,
    graph_json JSON NOT NULL,
    graph_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    published_by BIGINT NOT NULL,
    published_at DATETIME(6) NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, definition_id, definition_version),
    UNIQUE KEY uk_flow_extension_version_revision (
        system_id, tenant_id, definition_id, source_revision),
    KEY idx_flow_extension_version_checksum (
        system_id, graph_checksum, published_at),
    CONSTRAINT fk_flow_extension_version_definition FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_extension_version_identity CHECK (
        definition_version > 0 AND source_revision > 0 AND published_by > 0),
    CONSTRAINT ck_flow_extension_version_graph CHECK (
        JSON_TYPE(graph_json) = 'OBJECT'
        AND JSON_LENGTH(JSON_EXTRACT(graph_json, '$.nodes')) BETWEEN 2 AND 200),
    CONSTRAINT ck_flow_extension_version_checksum CHECK (
        graph_checksum REGEXP '^[0-9a-f]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_instance_form_snapshot (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    module_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    policy_json JSON NOT NULL,
    initial_form_json JSON NOT NULL,
    snapshot_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    materialized_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, instance_id, node_code),
    CONSTRAINT fk_flow_form_snapshot_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_form_snapshot_extension FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_extension_version (
        system_id, tenant_id, definition_id, definition_version
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_form_snapshot_codes CHECK (
        node_code REGEXP '^[a-z][a-z0-9_.-]{0,63}$'
        AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    CONSTRAINT ck_flow_form_snapshot_json CHECK (
        JSON_TYPE(policy_json) = 'ARRAY'
        AND JSON_TYPE(initial_form_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_form_write_history (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    history_sequence INT UNSIGNED NOT NULL,
    actor_member_id BIGINT NOT NULL,
    record_version_before BIGINT UNSIGNED NOT NULL,
    record_version_after BIGINT UNSIGNED NOT NULL,
    changes_json JSON NOT NULL,
    before_json JSON NOT NULL,
    after_json JSON NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, instance_id, node_code, history_sequence),
    KEY idx_flow_form_history_actor (
        system_id, tenant_id, actor_member_id, occurred_at, instance_id),
    CONSTRAINT fk_flow_form_history_snapshot FOREIGN KEY (
        system_id, tenant_id, instance_id, node_code
    ) REFERENCES un_flow_instance_form_snapshot (
        system_id, tenant_id, instance_id, node_code
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_form_history_identity CHECK (
        history_sequence > 0 AND actor_member_id > 0
        AND record_version_after > record_version_before),
    CONSTRAINT ck_flow_form_history_json CHECK (
        JSON_TYPE(changes_json) = 'OBJECT'
        AND JSON_TYPE(before_json) = 'OBJECT'
        AND JSON_TYPE(after_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_node_execution (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    node_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    execution_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    result_json JSON NOT NULL,
    execution_version BIGINT UNSIGNED NOT NULL DEFAULT 0,
    updated_by BIGINT NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, instance_id, node_code),
    CONSTRAINT fk_flow_node_execution_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_node_execution_extension FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_extension_version (
        system_id, tenant_id, definition_id, definition_version
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_node_execution_identity CHECK (
        updated_by > 0
        AND node_code REGEXP '^[a-z][a-z0-9_.-]{0,63}$'),
    CONSTRAINT ck_flow_node_execution_status CHECK (
        execution_status IN (
            'CONTINUED','WAITING_HUMAN','WAITING_EVENT','WAITING_TIMER',
            'WAITING_EXTERNAL','WAITING_CONFIRMATION','COMPLETED')),
    CONSTRAINT ck_flow_node_execution_result CHECK (
        JSON_TYPE(result_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE un_flow_node_execution_event (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    instance_id BIGINT NOT NULL,
    node_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_sequence INT UNSIGNED NOT NULL,
    from_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    to_status VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    input_json JSON NOT NULL,
    result_json JSON NOT NULL,
    actor_member_id BIGINT NOT NULL,
    occurred_at DATETIME(6) NOT NULL,
    PRIMARY KEY (
        system_id, tenant_id, instance_id, node_code, event_sequence),
    CONSTRAINT fk_flow_node_event_execution FOREIGN KEY (
        system_id, tenant_id, instance_id, node_code
    ) REFERENCES un_flow_node_execution (
        system_id, tenant_id, instance_id, node_code
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_node_event_identity CHECK (
        event_sequence > 0 AND actor_member_id > 0),
    CONSTRAINT ck_flow_node_event_json CHECK (
        JSON_TYPE(input_json) = 'OBJECT'
        AND JSON_TYPE(result_json) = 'OBJECT')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
