-- Cycle116: canonical file-field references and structured print composition remain
-- in the existing file-reference and versioned definition/snapshot models. These
-- indexes keep field rebinding and immutable print-history reads scope-bounded.
ALTER TABLE un_module_record_value
    DROP CHECK ck_rv_supported_type,
    DROP CHECK ck_rv_typed_payload;

ALTER TABLE un_module_record_value
    ADD CONSTRAINT ck_rv_supported_type CHECK (
        field_type IN (
            'TEXT', 'TEXTAREA', 'NUMBER', 'DATE', 'DATETIME', 'RADIO', 'MEMBER', 'DEPARTMENT',
            'PERCENT', 'MONEY', 'DATE_RANGE', 'TIME', 'TIME_RANGE', 'MULTI_SELECT', 'CASCADE',
            'SWITCH', 'RATING', 'PROGRESS', 'TAG', 'PHONE', 'EMAIL', 'URL', 'IDENTITY',
            'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON', 'SECRET', 'STATUS', 'REFERENCE',
            'ATTACHMENT', 'IMAGE', 'FILE_GROUP', 'SIGNATURE',
            'FORMULA', 'SUMMARY', 'CALCULATED', 'LOOKUP', 'AGGREGATE', 'AI_FILL',
            'TENANT', 'AUTO_NUMBER', 'CREATED_BY', 'CREATED_AT', 'UPDATED_BY', 'UPDATED_AT'
        )
    ),
    ADD CONSTRAINT ck_rv_typed_payload CHECK (
        (field_type IN ('TEXT', 'TAG', 'PHONE', 'EMAIL', 'URL', 'AUTO_NUMBER')
            AND string_value IS NOT NULL
            AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL AND datetime_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TEXTAREA', 'ADDRESS', 'GEO', 'BARCODE', 'RICH_TEXT', 'JSON')
            AND text_value IS NOT NULL
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
        OR (field_type IN ('DATETIME', 'CREATED_AT', 'UPDATED_AT') AND datetime_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN ('TIME', 'TIME_RANGE') AND time_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type = 'SWITCH' AND boolean_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL)
        OR (field_type IN (
                'RADIO', 'MEMBER', 'DEPARTMENT', 'MULTI_SELECT', 'CASCADE', 'STATUS',
                'ATTACHMENT', 'IMAGE', 'FILE_GROUP', 'SIGNATURE',
                'TENANT', 'CREATED_BY', 'UPDATED_BY'
            ) AND reference_value IS NOT NULL
            AND string_value IS NULL AND text_value IS NULL AND decimal_value IS NULL AND date_value IS NULL
            AND datetime_value IS NULL AND time_value IS NULL AND boolean_value IS NULL AND currency_code IS NULL)
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
        OR (field_type = 'AI_FILL'
            AND text_value IS NULL AND time_value IS NULL AND currency_code IS NULL AND reference_value IS NULL
            AND ((result_schema = 'STRING' AND string_value IS NOT NULL
                    AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL)
                OR (result_schema IN ('DECIMAL', 'INTEGER') AND string_value IS NULL
                    AND decimal_value IS NOT NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL
                    AND (result_schema <> 'INTEGER' OR decimal_value = TRUNCATE(decimal_value, 0)))
                OR (result_schema = 'DATE' AND string_value IS NULL
                    AND decimal_value IS NULL AND date_value IS NOT NULL
                    AND datetime_value IS NULL AND boolean_value IS NULL)
                OR (result_schema = 'DATETIME' AND string_value IS NULL
                    AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NOT NULL AND boolean_value IS NULL)
                OR (result_schema = 'BOOLEAN' AND string_value IS NULL
                    AND decimal_value IS NULL AND date_value IS NULL
                    AND datetime_value IS NULL AND boolean_value IS NOT NULL)))
    );

CREATE INDEX idx_file_reference_field_target
    ON un_file_reference (system_id, tenant_id, target_type, target_id, created_at, file_id);

CREATE INDEX idx_module_print_task_record_history
    ON un_module_print_task (system_id, tenant_id, module_code, record_id,
                             requested_by_member_id, created_at, id);
