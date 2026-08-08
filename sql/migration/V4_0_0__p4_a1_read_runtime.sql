-- P4-A1 establishes the typed read model consumed by the first record list/detail slice.
-- Runtime schema projection tables are introduced by a later forward migration; until then
-- schema_version_id is anchored to the immutable VS3 published configuration snapshot.

CREATE TABLE un_module_record (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    record_no VARCHAR(128) NOT NULL,
    title VARCHAR(500) NULL,
    status VARCHAR(24) NOT NULL,
    prior_status VARCHAR(24) NULL,
    owner_member_id BIGINT NOT NULL,
    owner_department_id BIGINT NULL,
    draft_expires_at DATETIME(3) NULL,
    deleted_at DATETIME(3) NULL,
    deleted_by BIGINT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_identity (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ),
    UNIQUE KEY uk_record_logical (
        system_id, tenant_id, logical_module_id, record_id
    ),
    KEY idx_record_list (
        system_id, tenant_id, logical_module_id, status, record_id
    ),
    KEY idx_record_owner (
        system_id, tenant_id, logical_module_id, owner_member_id, status, record_id
    ),
    KEY idx_record_department (
        system_id, tenant_id, logical_module_id, owner_department_id, status, record_id
    ),
    CONSTRAINT fk_record_schema_version FOREIGN KEY (system_id, schema_version_id)
        REFERENCES un_module_config_version (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_tenant FOREIGN KEY (system_id, tenant_id)
        REFERENCES un_plat_tenant (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_record_owner FOREIGN KEY (system_id, owner_member_id)
        REFERENCES un_plat_member (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_record_identity CHECK (id = record_id),
    CONSTRAINT ck_record_version CHECK (version >= 0),
    CONSTRAINT ck_record_status CHECK (
        status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'TRASHED', 'EXPIRED')
    ),
    CONSTRAINT ck_record_prior_status CHECK (
        (status = 'TRASHED' AND prior_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED') AND deleted_at IS NOT NULL)
        OR (status <> 'TRASHED' AND prior_status IS NULL AND deleted_at IS NULL)
    ),
    CONSTRAINT ck_record_draft_expiry CHECK (
        (status = 'DRAFT' AND draft_expires_at IS NOT NULL)
        OR (status <> 'DRAFT' AND draft_expires_at IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_record_value (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    field_snapshot_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    field_version BIGINT NOT NULL DEFAULT 1,
    field_type VARCHAR(32) NOT NULL,
    ordinal INT NOT NULL DEFAULT 0,
    string_value VARCHAR(4000) NULL,
    text_value TEXT NULL,
    decimal_value DECIMAL(38, 10) NULL,
    date_value DATE NULL,
    datetime_value DATETIME(3) NULL,
    reference_value BIGINT NULL,
    encrypted_value VARBINARY(4096) NULL,
    value_hash CHAR(64) NULL,
    display_value VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_value_ordinal (
        system_id, tenant_id, record_id, schema_version_id,
        module_snapshot_id, field_snapshot_id, ordinal
    ),
    KEY idx_rv_logical (
        system_id, tenant_id, logical_module_id, logical_field_id, record_id
    ),
    CONSTRAINT fk_rv_record FOREIGN KEY (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_rv_field_version CHECK (field_version > 0),
    CONSTRAINT ck_rv_ordinal CHECK (ordinal BETWEEN 0 AND 99),
    CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN ('TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT')
    ),
    CONSTRAINT ck_rv_typed_payload CHECK (
        (field_type = 'TEXT' AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND reference_value IS NULL)
        OR (field_type = 'TEXTAREA' AND text_value IS NOT NULL
            AND string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND reference_value IS NULL)
        OR (field_type = 'NUMBER' AND decimal_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND reference_value IS NULL)
        OR (field_type = 'DATE' AND date_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND datetime_value IS NULL AND reference_value IS NULL)
        OR (field_type = 'DATETIME' AND datetime_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND reference_value IS NULL)
        OR (field_type IN ('RADIO', 'MEMBER', 'DEPARTMENT') AND reference_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL)
    ),
    CONSTRAINT ck_rv_p4_plain_value CHECK (encrypted_value IS NULL AND value_hash IS NULL),
    CONSTRAINT ck_rv_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_record_index (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    index_generation_id BIGINT NOT NULL DEFAULT 1,
    normalization_generation_id BIGINT NOT NULL DEFAULT 1,
    path_snapshot_id BIGINT NOT NULL DEFAULT 0,
    ordinal INT NOT NULL DEFAULT 0,
    record_status VARCHAR(24) NOT NULL,
    value_kind VARCHAR(24) NOT NULL,
    string_value VARCHAR(512) NULL,
    decimal_value DECIMAL(38, 10) NULL,
    date_value DATE NULL,
    datetime_value DATETIME(3) NULL,
    boolean_value BOOLEAN NULL,
    reference_value BIGINT NULL,
    hash_value CHAR(64) NULL,
    currency_code CHAR(3) NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_index_ordinal (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_id, ordinal, path_snapshot_id
    ),
    KEY idx_ri_typed_routes (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, value_kind, record_id
    ),
    KEY idx_ri_string (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, string_value, record_id
    ),
    KEY idx_ri_decimal (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, decimal_value, record_id
    ),
    KEY idx_ri_datetime (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, datetime_value, record_id
    ),
    KEY idx_ri_reference (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, reference_value, record_id
    ),
    CONSTRAINT fk_ri_record FOREIGN KEY (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_ri_generations CHECK (
        index_generation_id > 0 AND normalization_generation_id > 0
    ),
    CONSTRAINT ck_ri_ordinal CHECK (ordinal BETWEEN 0 AND 99),
    CONSTRAINT ck_ri_record_status CHECK (
        record_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'TRASHED', 'EXPIRED')
    ),
    CONSTRAINT ck_ri_value_kind CHECK (
        value_kind IN ('STRING', 'DECIMAL', 'DATE', 'DATETIME', 'BOOLEAN', 'REFERENCE', 'HASH', 'MONEY')
    ),
    CONSTRAINT ck_ri_typed_value CHECK (
        (value_kind = 'STRING' AND string_value IS NOT NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'DECIMAL' AND decimal_value IS NOT NULL AND string_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'DATE' AND date_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND datetime_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'DATETIME' AND datetime_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'BOOLEAN' AND boolean_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'REFERENCE' AND reference_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND boolean_value IS NULL AND hash_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'HASH' AND hash_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND currency_code IS NULL)
        OR (value_kind = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL
            AND string_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_record_search (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    index_generation_id BIGINT NOT NULL DEFAULT 1,
    record_status VARCHAR(24) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    token_ordinal INT NOT NULL,
    token VARCHAR(255) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_search_token (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_id, token_ordinal, token_hash
    ),
    KEY idx_rs_query (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, token_hash, record_id
    ),
    CONSTRAINT fk_rs_record FOREIGN KEY (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_rs_generation CHECK (index_generation_id > 0),
    CONSTRAINT ck_rs_status CHECK (
        record_status IN ('DRAFT', 'ACTIVE', 'ARCHIVED', 'TRASHED', 'EXPIRED')
    ),
    CONSTRAINT ck_rs_field_type CHECK (field_type IN ('TEXT', 'TEXTAREA')),
    CONSTRAINT ck_rs_ordinal CHECK (token_ordinal BETWEEN 0 AND 255),
    CONSTRAINT ck_rs_token CHECK (CHAR_LENGTH(token) BETWEEN 1 AND 255),
    CONSTRAINT ck_rs_hash CHECK (token_hash REGEXP '^[a-f0-9]{64}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
