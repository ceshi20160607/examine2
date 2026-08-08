CREATE INDEX idx_record_runtime_cover
    ON un_module_record (
        system_id,
        tenant_id,
        logical_module_id,
        schema_version_id,
        module_snapshot_id,
        status,
        record_id
    );

CREATE INDEX idx_record_runtime_record_no
    ON un_module_record (
        system_id,
        tenant_id,
        logical_module_id,
        schema_version_id,
        module_snapshot_id,
        status,
        record_no,
        record_id
    );
