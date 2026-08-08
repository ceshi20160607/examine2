ALTER TABLE un_module_record_value
    DROP CHECK ck_rv_typed_payload,
    DROP CHECK ck_rv_supported_type,
    DROP CHECK ck_rv_p4_plain_value;

ALTER TABLE un_module_record_value
    MODIFY COLUMN encrypted_value BLOB NULL,
    ADD COLUMN encryption_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER encrypted_value,
    ADD COLUMN hash_key_version VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER value_hash;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT',
            'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE',
            'SWITCH', 'RATING', 'PROGRESS', 'TAG',
            'PHONE', 'EMAIL', 'URL', 'IDENTITY', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT',
            'JSON', 'SECRET', 'STATUS'
        )
    ),
    ADD CONSTRAINT ck_rv_sensitive_versions CHECK (
        (field_type IN ('IDENTITY', 'SECRET')
            AND encrypted_value IS NOT NULL AND value_hash IS NOT NULL
            AND encryption_key_version IS NOT NULL AND hash_key_version IS NOT NULL
            AND encryption_key_version REGEXP '^[A-Za-z0-9._-]{1,64}$'
            AND hash_key_version REGEXP '^[A-Za-z0-9._-]{1,64}$'
            AND OCTET_LENGTH(encrypted_value) BETWEEN 30 AND 20000)
        OR (field_type NOT IN ('IDENTITY', 'SECRET')
            AND encrypted_value IS NULL AND value_hash IS NULL
            AND encryption_key_version IS NULL AND hash_key_version IS NULL)
    ),
    ADD CONSTRAINT ck_rv_typed_payload CHECK (
        (field_type IN ('TEXT', 'TAG', 'PHONE', 'EMAIL', 'URL') AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TEXTAREA', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON')
            AND text_value IS NOT NULL AND string_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('NUMBER', 'PERCENT', 'RATING', 'PROGRESS') AND decimal_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND reference_value IS NULL)
        OR (field_type IN ('DATE', 'DATE_RANGE') AND date_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'DATETIME' AND datetime_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TIME', 'TIME_RANGE') AND time_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'SWITCH' AND boolean_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('RADIO', 'MEMBER', 'DEPARTMENT', 'MULTI_SELECT', 'CASCADE', 'STATUS')
            AND reference_value IS NOT NULL AND string_value IS NULL AND text_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL)
        OR (field_type IN ('IDENTITY', 'SECRET')
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
    );
