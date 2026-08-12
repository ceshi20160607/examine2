-- P4-B3 persists owner-scoped query definitions. Deleted names may be reused,
-- while an active member/module/name tuple remains unique.
CREATE TABLE un_module_saved_view (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    query_json JSON NOT NULL,
    columns_json JSON NOT NULL,
    active_marker TINYINT GENERATED ALWAYS AS (
        CASE WHEN deleted_at IS NULL THEN 1 ELSE NULL END
    ) STORED,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_saved_view_name (
        system_id, tenant_id, member_id, logical_module_id, name, active_marker
    ),
    KEY idx_saved_view_member (
        system_id, tenant_id, member_id, logical_module_id, updated_at, id
    ),
    CONSTRAINT fk_saved_view_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_saved_view_member FOREIGN KEY (system_id, member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_saved_view_schema FOREIGN KEY (system_id, schema_version_id)
        REFERENCES un_module_config_version (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_saved_view_module FOREIGN KEY (system_id, module_snapshot_id)
        REFERENCES un_module_definition (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_saved_view_module_identity CHECK (logical_module_id = module_snapshot_id),
    CONSTRAINT ck_saved_view_name CHECK (CHAR_LENGTH(TRIM(name)) BETWEEN 1 AND 100),
    CONSTRAINT ck_saved_view_version CHECK (version >= 0),
    CONSTRAINT ck_saved_view_delete CHECK (
        (deleted_at IS NULL AND deleted_by IS NULL)
        OR (deleted_at IS NOT NULL AND deleted_by IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
