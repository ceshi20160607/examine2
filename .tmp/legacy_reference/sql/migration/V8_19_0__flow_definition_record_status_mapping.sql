-- Immutable definition-owned terminal record STATUS mappings.

ALTER TABLE un_flow_definition_draft
    ADD COLUMN status_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER trigger_conditions,
    ADD COLUMN status_approved_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_field_code,
    ADD COLUMN status_rejected_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_approved_value,
    ADD COLUMN status_withdrawn_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_rejected_value,
    ADD COLUMN status_terminated_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_withdrawn_value,
    ADD CONSTRAINT ck_flow_draft_record_status_mapping CHECK (
        (
            status_field_code IS NULL
            AND status_approved_value IS NULL
            AND status_rejected_value IS NULL
            AND status_withdrawn_value IS NULL
            AND status_terminated_value IS NULL
        )
        OR (
            status_field_code IS NOT NULL
            AND status_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND status_approved_value IS NOT NULL
            AND status_approved_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_rejected_value IS NOT NULL
            AND status_rejected_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_withdrawn_value IS NOT NULL
            AND status_withdrawn_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_terminated_value IS NOT NULL
            AND status_terminated_value REGEXP '^[1-9][0-9]{0,18}$'
        )
    );

ALTER TABLE un_flow_definition_version
    ADD COLUMN status_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER trigger_conditions,
    ADD COLUMN status_approved_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_field_code,
    ADD COLUMN status_rejected_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_approved_value,
    ADD COLUMN status_withdrawn_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_rejected_value,
    ADD COLUMN status_terminated_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_withdrawn_value,
    ADD CONSTRAINT ck_flow_version_record_status_mapping CHECK (
        (
            status_field_code IS NULL
            AND status_approved_value IS NULL
            AND status_rejected_value IS NULL
            AND status_withdrawn_value IS NULL
            AND status_terminated_value IS NULL
        )
        OR (
            status_field_code IS NOT NULL
            AND status_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND status_approved_value IS NOT NULL
            AND status_approved_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_rejected_value IS NOT NULL
            AND status_rejected_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_withdrawn_value IS NOT NULL
            AND status_withdrawn_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_terminated_value IS NOT NULL
            AND status_terminated_value REGEXP '^[1-9][0-9]{0,18}$'
        )
    );
