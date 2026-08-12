-- Add the import-completed record event to the existing deterministic Flow trigger owner.

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
        )
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
        )
    );
