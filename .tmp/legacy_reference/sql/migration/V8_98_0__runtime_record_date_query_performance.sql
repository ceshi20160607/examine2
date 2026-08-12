CREATE INDEX idx_ri_date
    ON un_module_record_index (
        system_id,
        tenant_id,
        logical_module_id,
        logical_field_id,
        index_generation_id,
        record_status,
        date_value,
        record_id
    );
