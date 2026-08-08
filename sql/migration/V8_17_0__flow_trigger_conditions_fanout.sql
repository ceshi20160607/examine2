-- Conditional definition triggers and durable ordered Flow fan-out dispatch results.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN trigger_conditions JSON NULL
        AFTER trigger_exclusive;

UPDATE un_flow_definition_draft
SET trigger_conditions = JSON_ARRAY()
WHERE trigger_module_code IS NOT NULL;

ALTER TABLE un_flow_definition_draft
    DROP CHECK ck_flow_draft_trigger_binding,
    ADD CONSTRAINT ck_flow_draft_trigger_binding CHECK (
        (
            trigger_module_code IS NULL
            AND trigger_event IS NULL
            AND trigger_priority IS NULL
            AND trigger_exclusive IS NULL
            AND trigger_conditions IS NULL
        )
        OR (
            trigger_module_code IS NOT NULL
            AND trigger_module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND trigger_event IS NOT NULL
            AND trigger_event = 'RECORD_ACTIVATED'
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive IN (FALSE, TRUE)
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) <= 10
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN trigger_conditions JSON NULL
        AFTER trigger_exclusive;

UPDATE un_flow_definition_version
SET trigger_conditions = JSON_ARRAY()
WHERE trigger_module_code IS NOT NULL;

ALTER TABLE un_flow_definition_version
    DROP CHECK ck_flow_version_trigger_binding,
    ADD CONSTRAINT ck_flow_version_trigger_binding CHECK (
        (
            trigger_module_code IS NULL
            AND trigger_event IS NULL
            AND trigger_priority IS NULL
            AND trigger_exclusive IS NULL
            AND trigger_conditions IS NULL
        )
        OR (
            trigger_module_code IS NOT NULL
            AND trigger_module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND trigger_event IS NOT NULL
            AND trigger_event = 'RECORD_ACTIVATED'
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive IN (FALSE, TRUE)
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) <= 10
        )
    );

CREATE TABLE un_flow_trigger_dispatch_instance (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    event_key VARCHAR(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    ordinal INT UNSIGNED NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    instance_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (system_id, tenant_id, event_key, ordinal),
    UNIQUE KEY uk_flow_trigger_dispatch_item_instance (
        system_id, tenant_id, instance_id
    ),
    KEY idx_flow_trigger_dispatch_item_version (
        system_id, tenant_id, definition_id, definition_version
    ),
    CONSTRAINT fk_flow_trigger_dispatch_item_parent FOREIGN KEY (
        system_id, tenant_id, event_key
    ) REFERENCES un_flow_trigger_dispatch (
        system_id, tenant_id, event_key
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_trigger_dispatch_item_version FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_trigger_dispatch_item_instance FOREIGN KEY (
        system_id, tenant_id, instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_trigger_dispatch_item_identity CHECK (
        system_id > 0
        AND tenant_id > 0
        AND definition_id > 0
        AND definition_version > 0
        AND instance_id > 0
    ),
    CONSTRAINT ck_flow_trigger_dispatch_item_event_key CHECK (
        CHAR_LENGTH(TRIM(event_key)) BETWEEN 1 AND 200
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO un_flow_trigger_dispatch_instance (
    system_id,
    tenant_id,
    event_key,
    ordinal,
    definition_id,
    definition_version,
    instance_id,
    created_at
)
SELECT
    system_id,
    tenant_id,
    event_key,
    0,
    definition_id,
    definition_version,
    instance_id,
    created_at
FROM un_flow_trigger_dispatch
WHERE instance_id IS NOT NULL;
