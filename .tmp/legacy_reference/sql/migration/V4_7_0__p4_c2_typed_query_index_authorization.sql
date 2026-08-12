ALTER TABLE un_module_record_index
    DROP INDEX uk_record_index_ordinal,
    DROP CHECK ck_ri_value_kind,
    DROP CHECK ck_ri_typed_value,
    MODIFY COLUMN string_value VARCHAR(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL,
    MODIFY COLUMN hash_value CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN hash_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER hash_value,
    ADD COLUMN geohash VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER hash_key_version,
    ADD COLUMN geo_lat DOUBLE NULL AFTER geohash,
    ADD COLUMN geo_lng DOUBLE NULL AFTER geo_lat,
    ADD COLUMN hash_version_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (COALESCE(hash_key_version, '-')) STORED AFTER geo_lng;

ALTER TABLE un_module_record_index
    ADD UNIQUE KEY uk_record_index_ordinal (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_id, ordinal, path_snapshot_id, hash_version_key
    ),
    ADD KEY idx_ri_hash (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, hash_key_version, hash_value, record_id
    ),
    ADD KEY idx_ri_json_path (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, path_snapshot_id, value_kind, record_id
    ),
    ADD KEY idx_ri_geo (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, geohash, geo_lat, geo_lng, record_id
    ),
    ADD KEY idx_ri_geo_bounds (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, geo_lat, geo_lng, record_id
    ),
    ADD CONSTRAINT ck_ri_value_kind CHECK (
        value_kind IN (
            'STRING', 'DECIMAL', 'DATE', 'DATETIME', 'TIME', 'BOOLEAN', 'REFERENCE',
            'HASH', 'MONEY', 'GEO', 'NULL'
        )
    ),
    ADD CONSTRAINT ck_ri_typed_value CHECK (
        (value_kind = 'STRING' AND string_value IS NOT NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'DECIMAL' AND decimal_value IS NOT NULL AND string_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'DATE' AND date_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'DATETIME' AND datetime_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'TIME' AND time_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND hash_key_version IS NULL AND geohash IS NULL AND geo_lat IS NULL
            AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'BOOLEAN' AND boolean_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'REFERENCE' AND reference_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'HASH' AND hash_value IS NOT NULL AND hash_key_version IS NOT NULL
            AND hash_value REGEXP '^[0-9a-f]{64}$'
            AND hash_key_version REGEXP '^[A-Za-z0-9._-]{1,64}$' AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND geohash IS NULL
            AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
        OR (value_kind = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL
            AND string_value IS NULL AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND hash_key_version IS NULL AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL)
        OR (value_kind = 'GEO' AND geohash IS NOT NULL AND geo_lat IS NOT NULL AND geo_lng IS NOT NULL
            AND geohash REGEXP '^[0-9bcdefghjkmnpqrstuvwxyz]{12}$'
            AND geo_lat BETWEEN -90 AND 90 AND geo_lng BETWEEN -180 AND 180
            AND string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL AND reference_value IS NULL
            AND hash_value IS NULL AND hash_key_version IS NULL AND currency_code IS NULL)
        OR (value_kind = 'NULL' AND string_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL AND hash_value IS NULL AND hash_key_version IS NULL
            AND geohash IS NULL AND geo_lat IS NULL AND geo_lng IS NULL AND currency_code IS NULL)
    );

ALTER TABLE un_module_record_unique
    DROP INDEX uk_record_unique_value,
    DROP CHECK ck_ru_supported_type,
    DROP CHECK ck_ru_currency,
    MODIFY COLUMN normalized_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    ADD COLUMN hash_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER normalized_hash,
    ADD COLUMN hash_version_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin
        GENERATED ALWAYS AS (COALESCE(hash_key_version, '-')) STORED AFTER hash_key_version;

ALTER TABLE un_module_record_unique
    ADD UNIQUE KEY uk_record_unique_value (
        system_id, tenant_id, logical_module_id, logical_field_id,
        normalization_generation_id, currency_key, hash_version_key, normalized_hash
    ),
    ADD CONSTRAINT ck_ru_supported_type CHECK (
        field_type IN (
            'PERCENT', 'MONEY', 'TIME', 'SWITCH', 'RATING', 'PROGRESS',
            'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'BARCODE', 'SECRET'
        )
    ),
    ADD CONSTRAINT ck_ru_currency CHECK (
        (field_type = 'MONEY' AND currency_code REGEXP '^[A-Z]{3}$')
        OR (field_type <> 'MONEY' AND currency_code IS NULL)
    ),
    ADD CONSTRAINT ck_ru_hash_version CHECK (
        (field_type IN ('IDENTITY', 'SECRET') AND hash_key_version IS NOT NULL
            AND hash_key_version REGEXP '^[A-Za-z0-9._-]{1,64}$')
        OR (field_type NOT IN ('IDENTITY', 'SECRET') AND hash_key_version IS NULL)
    );
