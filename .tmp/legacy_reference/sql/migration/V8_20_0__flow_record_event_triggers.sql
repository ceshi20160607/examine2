-- Extend deterministic definition triggers to the frozen runtime-record event set.

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
            AND trigger_event IN (
                'RECORD_ACTIVATED',
                'RECORD_CREATED',
                'RECORD_UPDATED',
                'RECORD_DELETED',
                'RECORD_STATUS_CHANGED'
            )
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive IN (FALSE, TRUE)
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) <= 10
        )
    ),
    ADD CONSTRAINT ck_flow_draft_trigger_status_mapping CHECK (
        status_field_code IS NULL
        OR trigger_event IS NULL
        OR trigger_event = 'RECORD_ACTIVATED'
    );

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
            AND trigger_event IN (
                'RECORD_ACTIVATED',
                'RECORD_CREATED',
                'RECORD_UPDATED',
                'RECORD_DELETED',
                'RECORD_STATUS_CHANGED'
            )
            AND trigger_priority IS NOT NULL
            AND trigger_priority BETWEEN -1000 AND 1000
            AND trigger_exclusive IS NOT NULL
            AND trigger_exclusive IN (FALSE, TRUE)
            AND trigger_conditions IS NOT NULL
            AND JSON_TYPE(trigger_conditions) = 'ARRAY'
            AND JSON_LENGTH(trigger_conditions) <= 10
        )
    ),
    ADD CONSTRAINT ck_flow_version_trigger_status_mapping CHECK (
        status_field_code IS NULL
        OR trigger_event IS NULL
        OR trigger_event = 'RECORD_ACTIVATED'
    );
