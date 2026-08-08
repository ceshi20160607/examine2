-- Optional immutable STATUS-field mapping captured by each Module-owned Flow projection.

ALTER TABLE un_module_record_flow_state
    ADD COLUMN status_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER updated_by,
    ADD COLUMN status_approved_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_field_code,
    ADD COLUMN status_rejected_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_approved_value,
    ADD COLUMN status_withdrawn_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_rejected_value,
    ADD COLUMN status_terminated_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_withdrawn_value,
    ADD CONSTRAINT ck_record_flow_status_mapping_presence CHECK (
        (status_field_code IS NULL
            AND status_approved_value IS NULL
            AND status_rejected_value IS NULL
            AND status_withdrawn_value IS NULL
            AND status_terminated_value IS NULL)
        OR
        (status_field_code IS NOT NULL
            AND status_approved_value IS NOT NULL
            AND status_rejected_value IS NOT NULL
            AND status_withdrawn_value IS NOT NULL
            AND status_terminated_value IS NOT NULL)
    ),
    ADD CONSTRAINT ck_record_flow_status_mapping_syntax CHECK (
        status_field_code IS NULL
        OR (
            status_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND status_approved_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_rejected_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_withdrawn_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_terminated_value REGEXP '^[1-9][0-9]{0,18}$'
        )
    );

ALTER TABLE un_module_record_flow_state_item
    ADD COLUMN status_field_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER updated_by,
    ADD COLUMN status_approved_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_field_code,
    ADD COLUMN status_rejected_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_approved_value,
    ADD COLUMN status_withdrawn_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_rejected_value,
    ADD COLUMN status_terminated_value VARCHAR(19) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER status_withdrawn_value,
    ADD CONSTRAINT ck_record_flow_item_mapping_presence CHECK (
        (status_field_code IS NULL
            AND status_approved_value IS NULL
            AND status_rejected_value IS NULL
            AND status_withdrawn_value IS NULL
            AND status_terminated_value IS NULL)
        OR
        (status_field_code IS NOT NULL
            AND status_approved_value IS NOT NULL
            AND status_rejected_value IS NOT NULL
            AND status_withdrawn_value IS NOT NULL
            AND status_terminated_value IS NOT NULL)
    ),
    ADD CONSTRAINT ck_record_flow_item_mapping_syntax CHECK (
        status_field_code IS NULL
        OR (
            status_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND status_approved_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_rejected_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_withdrawn_value REGEXP '^[1-9][0-9]{0,18}$'
            AND status_terminated_value REGEXP '^[1-9][0-9]{0,18}$'
        )
    );
