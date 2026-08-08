CREATE TABLE un_module_record_unique (
    id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    record_id BIGINT NOT NULL,
    schema_version_id BIGINT NOT NULL,
    module_snapshot_id BIGINT NOT NULL,
    logical_module_id BIGINT NOT NULL,
    logical_field_id BIGINT NOT NULL,
    normalization_generation_id BIGINT NOT NULL DEFAULT 1,
    field_type VARCHAR(32) NOT NULL,
    record_status VARCHAR(24) NOT NULL,
    currency_code CHAR(3) NULL,
    currency_key CHAR(3) GENERATED ALWAYS AS (COALESCE(currency_code, '---')) STORED,
    normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_record_unique_value (
        system_id, tenant_id, logical_module_id, logical_field_id,
        normalization_generation_id, currency_key, normalized_hash
    ),
    KEY idx_ru_record (system_id, tenant_id, record_id),
    CONSTRAINT fk_ru_record FOREIGN KEY (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) REFERENCES un_module_record (
        system_id, tenant_id, record_id, schema_version_id, module_snapshot_id
    ) ON DELETE RESTRICT,
    CONSTRAINT ck_ru_generation CHECK (normalization_generation_id > 0),
    CONSTRAINT ck_ru_supported_type CHECK (
        field_type IN ('PERCENT', 'MONEY', 'TIME', 'SWITCH', 'RATING', 'PROGRESS')
    ),
    CONSTRAINT ck_ru_record_status CHECK (record_status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_ru_currency CHECK (
        (field_type = 'MONEY' AND currency_code REGEXP '^[A-Z]{3}$')
        OR (field_type <> 'MONEY' AND currency_code IS NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO un_module_record_unique (
    id, system_id, tenant_id, record_id, schema_version_id, module_snapshot_id,
    logical_module_id, logical_field_id, normalization_generation_id, field_type,
    record_status, currency_code, normalized_hash, created_at, updated_at
)
SELECT
    -CAST(ROW_NUMBER() OVER (
        ORDER BY i.system_id, i.tenant_id, i.logical_module_id, i.logical_field_id, i.record_id
    ) AS SIGNED),
    i.system_id,
    i.tenant_id,
    i.record_id,
    i.schema_version_id,
    i.module_snapshot_id,
    i.logical_module_id,
    i.logical_field_id,
    i.normalization_generation_id,
    f.field_type,
    r.status,
    i.currency_code,
    SHA2(CONCAT(
        f.field_type,
        '|',
        CASE f.field_type
            WHEN 'TIME' THEN TIME_FORMAT(i.time_value, '%H:%i:%s')
            WHEN 'SWITCH' THEN IF(i.boolean_value, 'true', 'false')
            ELSE CASE
                WHEN i.decimal_value = 0 THEN '0'
                ELSE TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM CAST(i.decimal_value AS CHAR)))
            END
        END
    ), 256),
    i.created_at,
    i.updated_at
FROM un_module_record_index i
JOIN un_module_record r
  ON r.system_id = i.system_id
 AND r.tenant_id = i.tenant_id
 AND r.record_id = i.record_id
 AND r.schema_version_id = i.schema_version_id
 AND r.module_snapshot_id = i.module_snapshot_id
JOIN un_module_field f
  ON f.system_id = i.system_id
 AND f.id = i.logical_field_id
WHERE i.ordinal = 0
  AND i.index_generation_id = 1
  AND r.status IN ('ACTIVE', 'ARCHIVED')
  AND f.index_mode = 'UNIQUE'
  AND f.field_type IN ('PERCENT', 'MONEY', 'TIME', 'SWITCH', 'RATING', 'PROGRESS');
