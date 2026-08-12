-- P4-C3 freezes published module/field snapshots before relation and subtable rows can reference them.

CREATE TABLE un_module_runtime_schema_module (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    source_module_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    module_code VARCHAR(64) NOT NULL,
    module_name VARCHAR(128) NOT NULL,
    snapshot_json JSON NOT NULL,
    checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rsm_identity (system_id, schema_version_id, module_snapshot_id),
    UNIQUE KEY uk_rsm_source (system_id, schema_version_id, source_module_id),
    KEY idx_rsm_logical (system_id, logical_module_id, schema_version_id),
    CONSTRAINT fk_rsm_config_version FOREIGN KEY (system_id, schema_version_id)
        REFERENCES un_module_config_version (system_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_rsm_checksum CHECK (checksum REGEXP '^[a-f0-9]{64}$'),
    CONSTRAINT ck_rsm_snapshot_size CHECK (OCTET_LENGTH(snapshot_json) <= 262144)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_runtime_schema_field (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    field_snapshot_id BIGINT NOT NULL,
    source_field_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    parent_field_snapshot_id BIGINT NULL,
    dictionary_id BIGINT NULL,
    target_module_id BIGINT NULL,
    field_code VARCHAR(64) NOT NULL,
    field_name VARCHAR(128) NOT NULL,
    field_type VARCHAR(32) NOT NULL,
    field_scope VARCHAR(24) NOT NULL,
    is_required BOOLEAN NOT NULL,
    is_readonly BOOLEAN NOT NULL,
    property_json JSON NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_rsf_identity (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ),
    UNIQUE KEY uk_rsf_discriminator (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id, field_type, field_scope
    ),
    UNIQUE KEY uk_rsf_logical (
        system_id, schema_version_id, module_snapshot_id, logical_field_id
    ),
    KEY idx_rsf_logical (system_id, logical_module_id, logical_field_id, schema_version_id),
    CONSTRAINT fk_rsf_module FOREIGN KEY (system_id, schema_version_id, module_snapshot_id)
        REFERENCES un_module_runtime_schema_module (system_id, schema_version_id, module_snapshot_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_rsf_parent FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id, parent_field_snapshot_id
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_rsf_scope CHECK (field_scope IN ('RECORD', 'SUBTABLE_COLUMN')),
    CONSTRAINT ck_rsf_column_type CHECK (field_scope <> 'SUBTABLE_COLUMN' OR field_type <> 'SUBTABLE'),
    CONSTRAINT ck_rsf_parent_scope CHECK (
        (field_scope = 'RECORD' AND parent_field_snapshot_id IS NULL)
        OR (field_scope = 'SUBTABLE_COLUMN' AND parent_field_snapshot_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Published history predating P4-C3 is projected from the immutable version JSON.
INSERT INTO un_module_runtime_schema_module (
    id, system_id, schema_version_id, module_snapshot_id, source_module_id, logical_module_id,
    module_code, module_name, snapshot_json, checksum, created_at
)
SELECT -CAST(CONV(SUBSTRING(SHA2(CONCAT('rsm:', v.system_id, ':', v.id, ':', m.module_id), 256), 1, 15), 16, 10) AS SIGNED),
       v.system_id, v.id, m.module_id, m.module_id, m.module_id,
       m.module_code, m.module_name,
       JSON_OBJECT('id', CAST(m.module_id AS CHAR), 'moduleCode', m.module_code, 'moduleName', m.module_name),
       SHA2(CONCAT(v.snapshot_checksum, ':module:', m.module_id), 256), v.published_at
FROM un_module_config_version v
CROSS JOIN JSON_TABLE(v.snapshot_json, '$.modules[*]' COLUMNS (
    module_id BIGINT PATH '$.id',
    module_code VARCHAR(64) PATH '$.module_code',
    module_name VARCHAR(128) PATH '$.module_name'
)) m;

INSERT INTO un_module_runtime_schema_field (
    id, system_id, schema_version_id, module_snapshot_id, field_snapshot_id, source_field_id,
    logical_module_id, logical_field_id, parent_field_snapshot_id, dictionary_id, target_module_id,
    field_code, field_name, field_type, field_scope, is_required, is_readonly, property_json, created_at
)
SELECT -CAST(CONV(SUBSTRING(SHA2(CONCAT('rsf:', v.system_id, ':', v.id, ':', f.field_id), 256), 1, 15), 16, 10) AS SIGNED),
       v.system_id, v.id, f.module_id, f.field_id, f.field_id, f.module_id, f.field_id, NULL,
       f.dictionary_id, f.target_module_id, f.field_code, f.field_name, f.field_type, 'RECORD',
       f.is_required, f.is_readonly, JSON_OBJECT(), v.published_at
FROM un_module_config_version v
CROSS JOIN JSON_TABLE(v.snapshot_json, '$.fields[*]' COLUMNS (
    field_id BIGINT PATH '$.id',
    module_id BIGINT PATH '$.module_id',
    dictionary_id BIGINT PATH '$.dictionary_id' NULL ON EMPTY,
    target_module_id BIGINT PATH '$.target_module_id' NULL ON EMPTY,
    field_code VARCHAR(64) PATH '$.field_code',
    field_name VARCHAR(128) PATH '$.field_name',
    field_type VARCHAR(32) PATH '$.field_type',
    is_required BOOLEAN PATH '$.is_required',
    is_readonly BOOLEAN PATH '$.is_readonly'
)) f;

ALTER TABLE un_module_record
    ADD CONSTRAINT fk_record_runtime_module FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_runtime_schema_module (
        system_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT fk_rv_runtime_field FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) ON DELETE RESTRICT;

CREATE TABLE un_module_record_relation (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_record_id BIGINT NOT NULL,
    source_schema_version_id BIGINT NOT NULL,
    source_module_snapshot_id BIGINT NOT NULL,
    source_logical_module_id BIGINT NOT NULL,
    source_field_snapshot_id BIGINT NOT NULL,
    source_logical_field_id BIGINT NOT NULL,
    source_field_type VARCHAR(32) NOT NULL DEFAULT 'RELATION',
    source_field_scope VARCHAR(24) NOT NULL DEFAULT 'RECORD',
    target_record_id BIGINT NOT NULL,
    target_schema_version_id BIGINT NOT NULL,
    target_module_snapshot_id BIGINT NOT NULL,
    target_logical_module_id BIGINT NOT NULL,
    ordinal INT NOT NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_relation_target (
        system_id, tenant_id, source_record_id, source_schema_version_id,
        source_module_snapshot_id, source_field_snapshot_id, target_record_id
    ),
    UNIQUE KEY uk_relation_ordinal (
        system_id, tenant_id, source_record_id, source_field_snapshot_id, ordinal
    ),
    KEY idx_relation_target (system_id, tenant_id, target_record_id),
    CONSTRAINT fk_relation_source_record FOREIGN KEY (
        system_id, tenant_id, source_record_id, source_schema_version_id, source_module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_relation_source_field FOREIGN KEY (
        system_id, source_schema_version_id, source_module_snapshot_id,
        source_field_snapshot_id, source_field_type, source_field_scope
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id, field_type, field_scope
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_relation_target_record FOREIGN KEY (
        system_id, tenant_id, target_record_id, target_schema_version_id, target_module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_relation_source CHECK (source_field_type = 'RELATION' AND source_field_scope = 'RECORD'),
    CONSTRAINT ck_relation_ordinal CHECK (ordinal BETWEEN 0 AND 499),
    CONSTRAINT ck_relation_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_sub_record (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    parent_field_snapshot_id BIGINT NOT NULL,
    parent_logical_field_id BIGINT NOT NULL,
    parent_field_type VARCHAR(32) NOT NULL DEFAULT 'SUBTABLE',
    parent_field_scope VARCHAR(24) NOT NULL DEFAULT 'RECORD',
    row_id BIGINT NOT NULL,
    client_row_key VARCHAR(64) NULL,
    ordinal INT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_record_identity (
        system_id, tenant_id, parent_record_id, schema_version_id,
        module_snapshot_id, parent_field_snapshot_id, row_id
    ),
    UNIQUE KEY uk_sub_record_ordinal (
        system_id, tenant_id, parent_record_id, parent_field_snapshot_id, ordinal
    ),
    UNIQUE KEY uk_sub_record_client (
        system_id, tenant_id, parent_record_id, parent_field_snapshot_id, client_row_key
    ),
    KEY idx_sub_parent (
        system_id, tenant_id, parent_record_id, parent_field_snapshot_id, status
    ),
    CONSTRAINT fk_sub_parent_record FOREIGN KEY (
        system_id, tenant_id, parent_record_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_sub_parent_field FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id,
        parent_field_snapshot_id, parent_field_type, parent_field_scope
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id, field_type, field_scope
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_sub_record_identity CHECK (id = row_id),
    CONSTRAINT ck_sub_parent CHECK (parent_field_type = 'SUBTABLE' AND parent_field_scope = 'RECORD'),
    CONSTRAINT ck_sub_ordinal CHECK (ordinal BETWEEN 0 AND 199),
    CONSTRAINT ck_sub_status CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT ck_sub_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_sub_value (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    parent_record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    parent_field_snapshot_id BIGINT NOT NULL,
    row_id BIGINT NOT NULL,
    column_field_snapshot_id BIGINT NOT NULL,
    source_field_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    column_field_type VARCHAR(32) NOT NULL,
    column_field_scope VARCHAR(24) NOT NULL DEFAULT 'SUBTABLE_COLUMN',
    ordinal INT NOT NULL DEFAULT 0,
    string_value VARCHAR(4000) NULL,
    text_value TEXT NULL,
    decimal_value DECIMAL(38, 10) NULL,
    date_value DATE NULL,
    datetime_value DATETIME(3) NULL,
    time_value TIME NULL,
    boolean_value BOOLEAN NULL,
    currency_code CHAR(3) NULL,
    reference_value BIGINT NULL,
    encrypted_value BLOB NULL,
    encryption_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    value_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    hash_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    display_value VARCHAR(1000) NULL,
    created_at DATETIME(3) NOT NULL,
    created_by BIGINT NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    updated_by BIGINT NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sub_value_ordinal (
        system_id, tenant_id, parent_record_id, parent_field_snapshot_id,
        row_id, column_field_snapshot_id, ordinal
    ),
    KEY idx_sv_logical (system_id, tenant_id, parent_record_id, logical_field_id),
    CONSTRAINT fk_sv_sub_record FOREIGN KEY (
        system_id, tenant_id, parent_record_id, schema_version_id,
        module_snapshot_id, parent_field_snapshot_id, row_id
    ) REFERENCES un_module_sub_record (
        system_id, tenant_id, parent_record_id, schema_version_id,
        module_snapshot_id, parent_field_snapshot_id, row_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_sv_column_field FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id,
        column_field_snapshot_id, column_field_type, column_field_scope
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id, field_type, field_scope
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_sv_scope CHECK (column_field_scope = 'SUBTABLE_COLUMN'),
    CONSTRAINT ck_sv_ordinal CHECK (ordinal BETWEEN 0 AND 99),
    CONSTRAINT ck_sv_currency CHECK (currency_code IS NULL OR currency_code REGEXP '^[A-Z]{3}$'),
    CONSTRAINT ck_sv_sensitive CHECK (
        (column_field_type IN ('IDENTITY', 'SECRET')
            AND encrypted_value IS NOT NULL AND value_hash IS NOT NULL
            AND encryption_key_version IS NOT NULL AND hash_key_version IS NOT NULL)
        OR (column_field_type NOT IN ('IDENTITY', 'SECRET')
            AND encrypted_value IS NULL AND value_hash IS NULL
            AND encryption_key_version IS NULL AND hash_key_version IS NULL)
    ),
    CONSTRAINT ck_sv_typed_payload CHECK (
        ((column_field_type IN ('TEXT', 'TAG', 'PHONE', 'EMAIL', 'URL') AND string_value IS NOT NULL)
            OR (column_field_type IN ('TEXTAREA', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON') AND text_value IS NOT NULL)
            OR (column_field_type IN ('NUMBER', 'PERCENT', 'RATING', 'PROGRESS') AND decimal_value IS NOT NULL)
            OR (column_field_type = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL)
            OR (column_field_type IN ('DATE', 'DATE_RANGE') AND date_value IS NOT NULL)
            OR (column_field_type = 'DATETIME' AND datetime_value IS NOT NULL)
            OR (column_field_type IN ('TIME', 'TIME_RANGE') AND time_value IS NOT NULL)
            OR (column_field_type = 'SWITCH' AND boolean_value IS NOT NULL)
            OR (column_field_type IN ('RADIO', 'MEMBER', 'DEPARTMENT', 'MULTI_SELECT', 'CASCADE', 'STATUS') AND reference_value IS NOT NULL)
            OR column_field_type IN ('IDENTITY', 'SECRET'))
        AND ((string_value IS NOT NULL) + (text_value IS NOT NULL) + (decimal_value IS NOT NULL)
            + (date_value IS NOT NULL) + (datetime_value IS NOT NULL) + (time_value IS NOT NULL)
            + (boolean_value IS NOT NULL) + (reference_value IS NOT NULL)
            + (encrypted_value IS NOT NULL) = 1)
    ),
    CONSTRAINT ck_sv_version CHECK (version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_reference_state (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    field_snapshot_id BIGINT NOT NULL,
    source_record_id BIGINT NULL,
    source_record_version BIGINT NULL,
    recalculation_state VARCHAR(16) NOT NULL,
    failure_correlation_id VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reference_state_field (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ),
    KEY idx_reference_state_source (system_id, tenant_id, source_record_id, recalculation_state),
    CONSTRAINT fk_reference_state_record FOREIGN KEY (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT fk_reference_state_field FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_reference_state CHECK (recalculation_state IN ('READY', 'PENDING', 'FAILED')),
    CONSTRAINT ck_reference_failure CHECK (
        (recalculation_state = 'FAILED' AND failure_correlation_id IS NOT NULL)
        OR (recalculation_state <> 'FAILED' AND failure_correlation_id IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE un_module_reference_recalc_task (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    source_record_id BIGINT NOT NULL,
    source_record_version BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    available_at DATETIME(3) NOT NULL,
    correlation_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reference_recalc_source (
        system_id, tenant_id, source_record_id, source_record_version
    ),
    KEY idx_reference_recalc_ready (status, available_at, id),
    CONSTRAINT ck_reference_recalc_status CHECK (status IN ('PENDING', 'RUNNING', 'PASSED', 'FAILED')),
    CONSTRAINT ck_reference_recalc_attempt CHECK (attempt_count BETWEEN 0 AND 20)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE un_module_record_value
    DROP CHECK ck_rv_typed_payload,
    DROP CHECK ck_rv_supported_type;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT',
            'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE',
            'SWITCH', 'RATING', 'PROGRESS', 'TAG', 'PHONE', 'EMAIL', 'URL', 'IDENTITY',
            'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS', 'REFERENCE'
        )
    ),
    ADD CONSTRAINT ck_rv_typed_payload CHECK (
        (field_type IN ('TEXT', 'TAG', 'PHONE', 'EMAIL', 'URL') AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TEXTAREA', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON') AND text_value IS NOT NULL
            AND string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('NUMBER', 'PERCENT', 'RATING', 'PROGRESS') AND decimal_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND reference_value IS NULL)
        OR (field_type IN ('DATE', 'DATE_RANGE') AND date_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'DATETIME' AND datetime_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TIME', 'TIME_RANGE') AND time_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'SWITCH' AND boolean_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('RADIO', 'MEMBER', 'DEPARTMENT', 'MULTI_SELECT', 'CASCADE', 'STATUS')
            AND reference_value IS NOT NULL AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL)
        OR (field_type IN ('IDENTITY', 'SECRET') AND string_value IS NULL AND text_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'REFERENCE'
            AND ((string_value IS NOT NULL) + (decimal_value IS NOT NULL) + (date_value IS NOT NULL)
                + (datetime_value IS NOT NULL) + (boolean_value IS NOT NULL) = 1)
            AND text_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
    );
