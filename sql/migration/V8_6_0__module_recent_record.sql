-- Batch 12 persists the authenticated member's newest runtime record accesses.
-- Rows are telemetry projections: they are pruned rather than soft-deleted.

CREATE TABLE un_module_recent (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    record_schema_version_id BIGINT NOT NULL,
    record_module_snapshot_id BIGINT NOT NULL,
    access_count BIGINT NOT NULL DEFAULT 1,
    last_accessed_at DATETIME(3) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_recent_record (
        system_id, tenant_id, member_id, logical_module_id, record_id
    ),
    KEY idx_recent_member (
        system_id, tenant_id, member_id, last_accessed_at DESC, id DESC
    ),
    CONSTRAINT fk_recent_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_recent_member FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_recent_module FOREIGN KEY (
        system_id, module_schema_version_id, module_snapshot_id
    ) REFERENCES un_module_runtime_schema_module (
        system_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_recent_record FOREIGN KEY (
        system_id, tenant_id, record_id,
        record_schema_version_id, record_module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id,
        schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_recent_access_count CHECK (access_count > 0),
    CONSTRAINT ck_recent_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
