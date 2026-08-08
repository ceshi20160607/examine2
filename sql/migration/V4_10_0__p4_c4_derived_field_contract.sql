-- P4-C4 stores immutable derived declarations and canonical typed materializations
-- in the existing runtime schema/value tables. The frozen runtime table count remains unchanged.

ALTER TABLE un_module_runtime_schema_field
    ADD COLUMN result_schema VARCHAR(16) NULL AFTER property_json,
    ADD COLUMN evaluator_version SMALLINT UNSIGNED NULL AFTER result_schema,
    ADD COLUMN expression_checksum CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER evaluator_version,
    ADD COLUMN topological_rank SMALLINT UNSIGNED NULL AFTER expression_checksum,
    ADD COLUMN dependency_json JSON NULL AFTER topological_rank;

-- V4.9 projected historical fields before their complete properties were needed at runtime.
-- Restore those immutable properties from the published version payload before deriving metadata.
UPDATE un_module_runtime_schema_field f
JOIN un_module_config_version v
  ON v.system_id = f.system_id AND v.id = f.schema_version_id
CROSS JOIN JSON_TABLE(v.snapshot_json, '$.fields[*]' COLUMNS (
    field_ordinal FOR ORDINALITY,
    field_id BIGINT PATH '$.id'
)) published_field
SET f.property_json = COALESCE(
        JSON_EXTRACT(v.snapshot_json,
            CONCAT('$.fields[', published_field.field_ordinal - 1, '].property_json')),
        JSON_OBJECT()
    )
WHERE f.field_scope = 'RECORD'
  AND f.field_snapshot_id = published_field.field_id;

-- P4-C4 did not exist before this migration. Keep any pre-existing experimental snapshots
-- readable and explainable; all newly published snapshots receive exact Java-analyzed edges/ranks.
UPDATE un_module_runtime_schema_field
SET is_required = FALSE,
    is_readonly = TRUE,
    result_schema = CASE
        WHEN JSON_UNQUOTE(JSON_EXTRACT(property_json, '$.resultSchema')) IN
             ('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN')
            THEN JSON_UNQUOTE(JSON_EXTRACT(property_json, '$.resultSchema'))
        ELSE 'STRING'
    END,
    evaluator_version = 1,
    expression_checksum = SHA2(CAST(property_json AS CHAR), 256),
    topological_rank = 0,
    dependency_json = JSON_ARRAY()
WHERE field_scope = 'RECORD'
  AND field_type IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE');

ALTER TABLE un_module_runtime_schema_field
    ADD UNIQUE KEY uk_rsf_result (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id,
        field_type, field_scope, result_schema
    ),
    ADD KEY idx_rsf_derived_rank (
        system_id, schema_version_id, module_snapshot_id, topological_rank, field_snapshot_id
    ),
    ADD CONSTRAINT ck_rsf_derived_contract CHECK (
        (field_scope = 'RECORD'
            AND field_type IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND is_required = FALSE AND is_readonly = TRUE
            AND result_schema IN ('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN')
            AND evaluator_version = 1
            AND expression_checksum REGEXP '^[a-f0-9]{64}$'
            AND topological_rank BETWEEN 0 AND 64
            AND dependency_json IS NOT NULL
            AND JSON_TYPE(dependency_json) = 'ARRAY'
            AND OCTET_LENGTH(dependency_json) <= 32768)
        OR (field_type NOT IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND result_schema IS NULL AND evaluator_version IS NULL
            AND expression_checksum IS NULL AND topological_rank IS NULL
            AND dependency_json IS NULL)
    );

ALTER TABLE un_module_record_value
    DROP FOREIGN KEY fk_rv_runtime_field,
    DROP CHECK ck_rv_typed_payload,
    DROP CHECK ck_rv_supported_type;

ALTER TABLE un_module_record_value
    ADD COLUMN field_scope VARCHAR(24) NOT NULL DEFAULT 'RECORD' AFTER field_type,
    ADD COLUMN result_schema VARCHAR(16) NULL AFTER field_scope,
    ADD COLUMN dependency_version_json JSON NULL AFTER result_schema,
    ADD COLUMN evaluator_version SMALLINT UNSIGNED NULL AFTER dependency_version_json,
    ADD COLUMN recalculation_state VARCHAR(16) NULL AFTER evaluator_version,
    ADD COLUMN failure_correlation_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER recalculation_state,
    ADD KEY idx_rv_derived_state (
        system_id, tenant_id, recalculation_state, record_id, field_snapshot_id
    ),
    ADD CONSTRAINT fk_rv_runtime_field FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id
    ) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_rv_runtime_result FOREIGN KEY (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id,
        field_type, field_scope, result_schema
    ) REFERENCES un_module_runtime_schema_field (
        system_id, schema_version_id, module_snapshot_id, field_snapshot_id,
        field_type, field_scope, result_schema
    ) ON DELETE RESTRICT;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT',
            'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE',
            'SWITCH', 'RATING', 'PROGRESS', 'TAG', 'PHONE', 'EMAIL', 'URL', 'IDENTITY',
            'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS', 'REFERENCE',
            'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE'
        )
    ),
    ADD CONSTRAINT ck_rv_derived_metadata CHECK (
        (field_type IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND field_scope = 'RECORD'
            AND result_schema IN ('STRING', 'DECIMAL', 'INTEGER', 'DATE', 'DATETIME', 'BOOLEAN')
            AND dependency_version_json IS NOT NULL
            AND JSON_TYPE(dependency_version_json) = 'ARRAY'
            AND OCTET_LENGTH(dependency_version_json) <= 32768
            AND evaluator_version = 1
            AND recalculation_state IN ('READY', 'PENDING', 'FAILED')
            AND ((recalculation_state = 'FAILED' AND failure_correlation_id IS NOT NULL
                    AND failure_correlation_id REGEXP '^[A-Za-z0-9._:-]{1,64}$')
                OR (recalculation_state <> 'FAILED' AND failure_correlation_id IS NULL)))
        OR (field_type NOT IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND field_scope = 'RECORD'
            AND result_schema IS NULL AND dependency_version_json IS NULL
            AND evaluator_version IS NULL AND recalculation_state IS NULL
            AND failure_correlation_id IS NULL)
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
        OR (field_type IN ('FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE')
            AND text_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL
            AND ((string_value IS NOT NULL) + (decimal_value IS NOT NULL) + (date_value IS NOT NULL)
                + (datetime_value IS NOT NULL) + (boolean_value IS NOT NULL) <= 1)
            AND ((string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL)
                OR (result_schema = 'STRING' AND string_value IS NOT NULL)
                OR (result_schema = 'DECIMAL' AND decimal_value IS NOT NULL)
                OR (result_schema = 'INTEGER' AND decimal_value IS NOT NULL
                    AND decimal_value = TRUNCATE(decimal_value, 0))
                OR (result_schema = 'DATE' AND date_value IS NOT NULL)
                OR (result_schema = 'DATETIME' AND datetime_value IS NOT NULL)
                OR (result_schema = 'BOOLEAN' AND boolean_value IS NOT NULL)))
    );
