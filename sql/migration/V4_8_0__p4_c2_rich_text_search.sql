ALTER TABLE un_module_record_search
    DROP CHECK ck_rs_field_type,
    ADD CONSTRAINT ck_rs_field_type CHECK (
        field_type IN ('TEXT', 'TEXTAREA', 'RICH_TEXT')
    );
