ALTER TABLE un_file_object
    ADD KEY idx_file_object_scope_status_created (
        system_id, tenant_id, status, created_at, id
    );
