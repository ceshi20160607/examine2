-- Immutable fixed-interval Flow triggers and durable exact-slot runtime state.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN trigger_start_at DATETIME(6) NULL
        AFTER trigger_conditions,
    ADD COLUMN trigger_interval_minutes INT UNSIGNED NULL
        AFTER trigger_start_at,
    ADD COLUMN trigger_requester_id BIGINT NULL
        AFTER trigger_interval_minutes,
    DROP CHECK ck_flow_draft_trigger_binding,
    ADD CONSTRAINT ck_flow_draft_trigger_binding CHECK (
        (
            trigger_module_code IS NULL
            AND trigger_event IS NULL
            AND trigger_priority IS NULL
            AND trigger_exclusive IS NULL
            AND trigger_conditions IS NULL
            AND trigger_start_at IS NULL
            AND trigger_interval_minutes IS NULL
            AND trigger_requester_id IS NULL
        )
        OR (
            trigger_module_code IS NOT NULL
            AND trigger_module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND trigger_event IS NOT NULL
            AND trigger_event IN (
                'RECORD_ACTIVATED',
                'RECORD_CREATED',
                'RECORD_UPDATED',
                'RECORD_DELETED',
                'RECORD_STATUS_CHANGED',
                'IMPORT_COMPLETED'
            )
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive IN (FALSE, TRUE)
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) <= 10
            AND trigger_start_at IS NULL
            AND trigger_interval_minutes IS NULL
            AND trigger_requester_id IS NULL
        )
        OR (
            trigger_module_code IS NULL
            AND trigger_event IS NOT NULL
            AND trigger_event = 'PERIODIC'
            AND trigger_priority IS NOT NULL
            AND trigger_priority = 0
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive = TRUE
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) = 0
            AND trigger_start_at IS NOT NULL
            AND trigger_interval_minutes IS NOT NULL
            AND trigger_interval_minutes BETWEEN 1 AND 525600
            AND trigger_requester_id IS NOT NULL
            AND trigger_requester_id > 0
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN trigger_start_at DATETIME(6) NULL
        AFTER trigger_conditions,
    ADD COLUMN trigger_interval_minutes INT UNSIGNED NULL
        AFTER trigger_start_at,
    ADD COLUMN trigger_requester_id BIGINT NULL
        AFTER trigger_interval_minutes,
    DROP CHECK ck_flow_version_trigger_binding,
    ADD CONSTRAINT ck_flow_version_trigger_binding CHECK (
        (
            trigger_module_code IS NULL
            AND trigger_event IS NULL
            AND trigger_priority IS NULL
            AND trigger_exclusive IS NULL
            AND trigger_conditions IS NULL
            AND trigger_start_at IS NULL
            AND trigger_interval_minutes IS NULL
            AND trigger_requester_id IS NULL
        )
        OR (
            trigger_module_code IS NOT NULL
            AND trigger_module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND trigger_event IS NOT NULL
            AND trigger_event IN (
                'RECORD_ACTIVATED',
                'RECORD_CREATED',
                'RECORD_UPDATED',
                'RECORD_DELETED',
                'RECORD_STATUS_CHANGED',
                'IMPORT_COMPLETED'
            )
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive IN (FALSE, TRUE)
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) <= 10
            AND trigger_start_at IS NULL
            AND trigger_interval_minutes IS NULL
            AND trigger_requester_id IS NULL
        )
        OR (
            trigger_module_code IS NULL
            AND trigger_event IS NOT NULL
            AND trigger_event = 'PERIODIC'
            AND trigger_priority IS NOT NULL
            AND trigger_priority = 0
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive = TRUE
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) = 0
            AND trigger_start_at IS NOT NULL
            AND trigger_interval_minutes IS NOT NULL
            AND trigger_interval_minutes BETWEEN 1 AND 525600
            AND trigger_requester_id IS NOT NULL
            AND trigger_requester_id > 0
        )
    );

CREATE TABLE un_flow_periodic_schedule (
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    requester_id BIGINT NOT NULL,
    interval_minutes INT UNSIGNED NOT NULL,
    start_at DATETIME(6) NOT NULL,
    next_fire_at DATETIME(6) NOT NULL,
    last_scheduled_at DATETIME(6) NULL,
    last_instance_id BIGINT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    pause_reason VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id, tenant_id, definition_id),
    KEY idx_flow_periodic_due (
        status, next_fire_at, system_id, tenant_id, definition_id
    ),
    KEY idx_flow_periodic_instance (
        system_id, tenant_id, last_instance_id
    ),
    CONSTRAINT fk_flow_periodic_version FOREIGN KEY (
        system_id, tenant_id, definition_id, definition_version
    ) REFERENCES un_flow_definition_version (
        system_id, tenant_id, definition_id, version_no
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_flow_periodic_instance FOREIGN KEY (
        system_id, tenant_id, last_instance_id
    ) REFERENCES un_flow_instance (
        system_id, tenant_id, instance_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_flow_periodic_scope CHECK (
        system_id > 0 AND tenant_id > 0 AND definition_id > 0
        AND definition_version > 0 AND requester_id > 0
    ),
    CONSTRAINT ck_flow_periodic_interval CHECK (
        interval_minutes BETWEEN 1 AND 525600
    ),
    CONSTRAINT ck_flow_periodic_status CHECK (
        (status = 'ACTIVE' AND pause_reason IS NULL)
        OR (status = 'PAUSED' AND pause_reason IS NOT NULL)
    ),
    CONSTRAINT ck_flow_periodic_last CHECK (
        (last_scheduled_at IS NULL AND last_instance_id IS NULL)
        OR (last_scheduled_at IS NOT NULL AND last_instance_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
