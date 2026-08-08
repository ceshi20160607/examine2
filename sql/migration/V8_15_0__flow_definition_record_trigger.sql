-- Definition-owned immutable activation trigger snapshots and durable scoped dispatch results.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN trigger_module_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER approver_id,
    ADD COLUMN trigger_event VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER trigger_module_code,
    ADD COLUMN trigger_priority INT NULL
        AFTER trigger_event,
    ADD COLUMN trigger_exclusive BOOLEAN NULL
        AFTER trigger_priority,
    ADD KEY idx_flow_draft_trigger (
        system_id, tenant_id, trigger_module_code, trigger_event,
        trigger_priority DESC, definition_id
    ),
    ADD CONSTRAINT ck_flow_draft_trigger_binding CHECK (
        (
            trigger_module_code IS NULL
            AND trigger_event IS NULL
            AND trigger_priority IS NULL
            AND trigger_exclusive IS NULL
        )
        OR (
            trigger_module_code IS NOT NULL
            AND trigger_module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND trigger_event IS NOT NULL
            AND trigger_event = 'RECORD_ACTIVATED'
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive = TRUE
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN trigger_module_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER approver_id,
    ADD COLUMN trigger_event VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER trigger_module_code,
    ADD COLUMN trigger_priority INT NULL
        AFTER trigger_event,
    ADD COLUMN trigger_exclusive BOOLEAN NULL
        AFTER trigger_priority,
    ADD KEY idx_flow_version_trigger (
        system_id, tenant_id, trigger_module_code, trigger_event,
        trigger_priority DESC, definition_id, version_no
    ),
    ADD CONSTRAINT ck_flow_version_trigger_binding CHECK (
        (
            trigger_module_code IS NULL
            AND trigger_event IS NULL
            AND trigger_priority IS NULL
            AND trigger_exclusive IS NULL
        )
        OR (
            trigger_module_code IS NOT NULL
            AND trigger_module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND trigger_event IS NOT NULL
            AND trigger_event = 'RECORD_ACTIVATED'
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive = TRUE
        )
    );

CREATE TABLE un_flow_trigger_dispatch (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_key VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    definition_id BIGINT NULL,
    definition_version INT UNSIGNED NULL,
    instance_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (system_id, tenant_id, event_key),
    UNIQUE KEY uk_flow_trigger_dispatch_instance (
        system_id, tenant_id, instance_id
    ),
    KEY idx_flow_trigger_dispatch_definition (
        system_id, tenant_id, definition_id, definition_version, created_at
    ),
    CONSTRAINT fk_flow_trigger_dispatch_version FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_trigger_dispatch_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_trigger_dispatch_event_key CHECK (
        CHAR_LENGTH(TRIM(event_key)) BETWEEN 1 AND 200
    ),
    CONSTRAINT ck_flow_trigger_dispatch_result CHECK (
        (
            definition_id IS NULL
            AND definition_version IS NULL
            AND instance_id IS NULL
        )
        OR (
            definition_id IS NOT NULL
            AND definition_id > 0
            AND definition_version IS NOT NULL
            AND definition_version > 0
            AND instance_id IS NOT NULL
            AND instance_id > 0
        )
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
