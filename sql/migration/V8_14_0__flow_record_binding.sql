-- Flow-owned immutable snapshot of an optional runtime-record binding.

ALTER TABLE un_flow_instance
    ADD COLUMN module_code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER business_key,
    ADD COLUMN record_id BIGINT NULL
        AFTER module_code,
    ADD KEY idx_flow_instance_record (
        system_id, tenant_id, module_code, record_id, started_at, instance_id
    ),
    ADD CONSTRAINT ck_flow_instance_record_binding CHECK (
        (module_code IS NULL AND record_id IS NULL)
        OR (
            module_code IS NOT NULL
            AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
            AND record_id IS NOT NULL
            AND record_id > 0
        )
    );
