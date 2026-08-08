-- Reusable, exact-version statistics configuration for published dashboards.
-- Aggregate values are computed at runtime from the permission-scoped native
-- record query; these columns preserve the immutable request and exact logical
-- field identities/types used when the dashboard was published.

-- A draft can legitimately publish again when its referenced immutable module
-- or data-source version changes. Version-number uniqueness plus root CAS keeps
-- concurrent publication safe; draft-version-only uniqueness rejected this
-- valid republish sequence in V8.50/V8.51.
ALTER TABLE un_module_data_source_version
    DROP INDEX uk_module_data_source_draft_publish;

ALTER TABLE un_module_dashboard_version
    DROP INDEX uk_module_dashboard_draft_publish;

ALTER TABLE un_module_dashboard_version_widget
    ADD COLUMN stat_aggregation VARCHAR(8)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER row_limit,
    ADD COLUMN stat_measure_field_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_aggregation,
    ADD COLUMN stat_measure_field_id BIGINT NULL
        AFTER stat_measure_field_code,
    ADD COLUMN stat_measure_field_name VARCHAR(200) NULL
        AFTER stat_measure_field_id,
    ADD COLUMN stat_measure_field_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER stat_measure_field_name,
    ADD COLUMN stat_measure_query_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER stat_measure_field_type,
    ADD COLUMN stat_group_field_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL
        AFTER stat_measure_query_type,
    ADD COLUMN stat_group_field_id BIGINT NULL AFTER stat_group_field_code,
    ADD COLUMN stat_group_field_name VARCHAR(200) NULL
        AFTER stat_group_field_id,
    ADD COLUMN stat_group_field_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_group_field_name,
    ADD COLUMN stat_group_query_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_group_field_type,
    ADD COLUMN stat_group_limit TINYINT UNSIGNED NULL
        AFTER stat_group_query_type,
    ADD COLUMN stat_time_field_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_group_limit,
    ADD COLUMN stat_time_field_id BIGINT NULL AFTER stat_time_field_code,
    ADD COLUMN stat_time_field_name VARCHAR(200) NULL AFTER stat_time_field_id,
    ADD COLUMN stat_time_field_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_time_field_name,
    ADD COLUMN stat_time_query_type VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_time_field_type,
    ADD COLUMN stat_time_grain VARCHAR(8)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER stat_time_query_type,
    ADD COLUMN stat_time_start DATE NULL AFTER stat_time_grain,
    ADD COLUMN stat_time_end DATE NULL AFTER stat_time_start,
    DROP CHECK ck_module_dashboard_widget_type,
    ADD CONSTRAINT ck_module_dashboard_widget_stat_fields CHECK (
        (stat_measure_field_code IS NULL
          OR stat_measure_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$')
        AND (stat_group_field_code IS NULL
          OR stat_group_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$')
        AND (stat_time_field_code IS NULL
          OR stat_time_field_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$')),
    ADD CONSTRAINT ck_module_dashboard_widget_stat_metadata CHECK (
        ((stat_measure_field_code IS NULL
            AND stat_measure_field_id IS NULL
            AND stat_measure_field_name IS NULL
            AND stat_measure_field_type IS NULL
            AND stat_measure_query_type IS NULL)
          OR (stat_measure_field_code IS NOT NULL
            AND stat_measure_field_id > 0
            AND CHAR_LENGTH(TRIM(stat_measure_field_name)) BETWEEN 1 AND 200
            AND stat_measure_field_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
            AND stat_measure_query_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'))
        AND ((stat_group_field_code IS NULL
            AND stat_group_field_id IS NULL
            AND stat_group_field_name IS NULL
            AND stat_group_field_type IS NULL
            AND stat_group_query_type IS NULL)
          OR (stat_group_field_code IS NOT NULL
            AND stat_group_field_id > 0
            AND CHAR_LENGTH(TRIM(stat_group_field_name)) BETWEEN 1 AND 200
            AND stat_group_field_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
            AND stat_group_query_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'))
        AND ((stat_time_field_code IS NULL
            AND stat_time_field_id IS NULL
            AND stat_time_field_name IS NULL
            AND stat_time_field_type IS NULL
            AND stat_time_query_type IS NULL)
          OR (stat_time_field_code IS NOT NULL
            AND stat_time_field_id > 0
            AND CHAR_LENGTH(TRIM(stat_time_field_name)) BETWEEN 1 AND 200
            AND stat_time_field_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'
            AND stat_time_query_type REGEXP '^[A-Z][A-Z0-9_]{0,99}$'))),
    ADD CONSTRAINT ck_module_dashboard_widget_stat_shape CHECK (
        (stat_aggregation IS NULL
          AND stat_measure_field_code IS NULL
          AND stat_group_field_code IS NULL AND stat_group_limit IS NULL
          AND stat_time_field_code IS NULL AND stat_time_grain IS NULL
          AND stat_time_start IS NULL AND stat_time_end IS NULL)
        OR
        (stat_aggregation IN ('COUNT','SUM','AVG','MIN','MAX')
          AND ((stat_aggregation = 'COUNT'
                 AND stat_measure_field_code IS NULL)
            OR (stat_aggregation <> 'COUNT'
                 AND stat_measure_field_code IS NOT NULL))
          AND ((stat_group_field_code IS NULL AND stat_group_limit IS NULL)
            OR (stat_group_field_code IS NOT NULL
                 AND stat_group_limit BETWEEN 1 AND 20))
          AND ((stat_time_field_code IS NULL AND stat_time_grain IS NULL
                 AND stat_time_start IS NULL AND stat_time_end IS NULL)
            OR (stat_time_field_code IS NOT NULL
                 AND stat_time_grain IN ('DAY','WEEK','MONTH')
                 AND stat_time_start IS NOT NULL
                 AND stat_time_end > stat_time_start
                 AND (
                   (stat_time_grain = 'DAY'
                     AND DATEDIFF(stat_time_end, stat_time_start)
                         BETWEEN 1 AND 100)
                   OR (stat_time_grain = 'WEEK'
                     AND WEEKDAY(stat_time_start) = 0
                     AND WEEKDAY(stat_time_end) = 0
                     AND MOD(DATEDIFF(stat_time_end, stat_time_start), 7) = 0
                     AND DATEDIFF(stat_time_end, stat_time_start)
                         BETWEEN 7 AND 700)
                   OR (stat_time_grain = 'MONTH'
                     AND DAYOFMONTH(stat_time_start) = 1
                     AND DAYOFMONTH(stat_time_end) = 1
                     AND TIMESTAMPDIFF(
                         MONTH, stat_time_start, stat_time_end)
                         BETWEEN 1 AND 100))))
          AND NOT (stat_group_field_code IS NOT NULL
                   AND stat_time_field_code IS NOT NULL))),
    ADD CONSTRAINT ck_module_dashboard_widget_type CHECK (
        (widget_type = 'STAT_COUNT' AND row_limit IS NULL
          AND stat_aggregation IS NULL)
        OR (widget_type = 'DATA_LIST' AND row_limit BETWEEN 1 AND 20
          AND stat_aggregation IS NULL)
        OR (widget_type = 'STAT_VALUE' AND row_limit IS NULL
          AND stat_aggregation IS NOT NULL
          AND stat_group_field_code IS NULL
          AND stat_time_field_code IS NULL)
        OR (widget_type IN ('BAR_CHART','PIE_CHART') AND row_limit IS NULL
          AND stat_aggregation IS NOT NULL
          AND stat_group_field_code IS NOT NULL
          AND stat_time_field_code IS NULL)
        OR (widget_type = 'LINE_TREND' AND row_limit IS NULL
          AND stat_aggregation IS NOT NULL
          AND stat_group_field_code IS NULL
          AND stat_time_field_code IS NOT NULL));
