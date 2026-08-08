ALTER TABLE un_module_record_value
    DROP CHECK ck_rv_typed_payload,
    DROP CHECK ck_rv_supported_type;

ALTER TABLE un_module_record_value
    ADD COLUMN time_value TIME NULL AFTER datetime_value,
    ADD COLUMN boolean_value BOOLEAN NULL AFTER time_value,
    ADD COLUMN currency_code CHAR(3) NULL AFTER boolean_value;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT',
            'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE',
            'SWITCH', 'RATING', 'PROGRESS', 'TAG'
        )
    ),
    ADD CONSTRAINT ck_rv_currency_code CHECK (
        currency_code IS NULL OR currency_code REGEXP '^[A-Z]{3}$'
    ),
    ADD CONSTRAINT ck_rv_typed_payload CHECK (
        (field_type = 'TEXT' AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'TEXTAREA' AND text_value IS NOT NULL
            AND string_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
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
        OR (field_type IN ('RADIO', 'MEMBER', 'DEPARTMENT', 'MULTI_SELECT', 'CASCADE')
            AND reference_value IS NOT NULL AND string_value IS NULL AND text_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL)
        OR (field_type = 'TAG' AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL
            AND currency_code IS NULL AND reference_value IS NULL)
    );

ALTER TABLE un_module_record_index
    DROP CHECK ck_ri_typed_value,
    DROP CHECK ck_ri_value_kind;

ALTER TABLE un_module_record_index
    ADD COLUMN time_value TIME NULL AFTER datetime_value,
    ADD KEY idx_ri_time (
        system_id, tenant_id, logical_module_id, logical_field_id,
        index_generation_id, record_status, time_value, record_id
    );

ALTER TABLE un_module_record_index
    ADD CONSTRAINT ck_ri_value_kind CHECK (
        value_kind IN ('STRING', 'DECIMAL', 'DATE', 'DATETIME', 'TIME', 'BOOLEAN', 'REFERENCE', 'HASH', 'MONEY')
    ),
    ADD CONSTRAINT ck_ri_typed_value CHECK (
        (value_kind = 'STRING' AND string_value IS NOT NULL AND decimal_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'DECIMAL' AND decimal_value IS NOT NULL AND string_value IS NULL
            AND date_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'DATE' AND date_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND datetime_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'DATETIME' AND datetime_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND time_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'TIME' AND time_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND boolean_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'BOOLEAN' AND boolean_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND reference_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'REFERENCE' AND reference_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND hash_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'HASH' AND hash_value IS NOT NULL AND string_value IS NULL
            AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND reference_value IS NULL
            AND currency_code IS NULL)
        OR (value_kind = 'MONEY' AND decimal_value IS NOT NULL AND currency_code IS NOT NULL
            AND string_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND reference_value IS NULL
            AND hash_value IS NULL)
    );
