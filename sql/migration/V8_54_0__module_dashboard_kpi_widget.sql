-- Let an immutable dashboard version pin either a published data-source
-- version (legacy widgets) or a published KPI version (KPI_VALUE), never both.

ALTER TABLE un_module_dashboard_version_widget
    DROP CHECK ck_module_dashboard_widget_identity,
    DROP CHECK ck_module_dashboard_widget_code,
    DROP CHECK ck_module_dashboard_widget_schema,
    DROP CHECK ck_module_dashboard_widget_type,
    MODIFY COLUMN data_source_id BIGINT NULL,
    MODIFY COLUMN data_source_version_id BIGINT NULL,
    MODIFY COLUMN data_source_version_no INT UNSIGNED NULL,
    MODIFY COLUMN data_source_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    MODIFY COLUMN module_code VARCHAR(100)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    MODIFY COLUMN schema_version_id VARCHAR(200)
        CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD COLUMN kpi_id BIGINT NULL AFTER stat_time_end,
    ADD COLUMN kpi_version_id BIGINT NULL AFTER kpi_id,
    ADD COLUMN kpi_version_no INT UNSIGNED NULL AFTER kpi_version_id,
    ADD COLUMN kpi_code VARCHAR(64)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER kpi_version_no,
    ADD COLUMN kpi_name VARCHAR(200) NULL AFTER kpi_code,
    ADD COLUMN kpi_subject_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER kpi_name,
    ADD COLUMN kpi_period_type VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NULL AFTER kpi_subject_type,
    ADD KEY idx_module_dashboard_widget_kpi (
        system_id, tenant_id, kpi_id, kpi_version_id, kpi_version_no),
    ADD CONSTRAINT fk_module_dashboard_widget_kpi_version FOREIGN KEY (
        system_id, tenant_id, kpi_id, kpi_version_id, kpi_version_no)
        REFERENCES un_module_kpi_version (
            system_id, tenant_id, kpi_id, id, version_no)
        ON DELETE RESTRICT,
    ADD CONSTRAINT ck_module_dashboard_widget_identity CHECK (
        id > 0 AND dashboard_id > 0 AND dashboard_version_id > 0
        AND dashboard_version_no > 0 AND widget_ordinal < 20),
    ADD CONSTRAINT ck_module_dashboard_widget_source_kind CHECK (
        (widget_type = 'KPI_VALUE'
          AND data_source_id IS NULL
          AND data_source_version_id IS NULL
          AND data_source_version_no IS NULL
          AND data_source_code IS NULL
          AND module_code IS NULL
          AND schema_version_id IS NULL
          AND row_limit IS NULL
          AND stat_aggregation IS NULL
          AND stat_measure_field_code IS NULL
          AND stat_measure_field_id IS NULL
          AND stat_measure_field_name IS NULL
          AND stat_measure_field_type IS NULL
          AND stat_measure_query_type IS NULL
          AND stat_group_field_code IS NULL
          AND stat_group_field_id IS NULL
          AND stat_group_field_name IS NULL
          AND stat_group_field_type IS NULL
          AND stat_group_query_type IS NULL
          AND stat_group_limit IS NULL
          AND stat_time_field_code IS NULL
          AND stat_time_field_id IS NULL
          AND stat_time_field_name IS NULL
          AND stat_time_field_type IS NULL
          AND stat_time_query_type IS NULL
          AND stat_time_grain IS NULL
          AND stat_time_start IS NULL
          AND stat_time_end IS NULL
          AND kpi_id IS NOT NULL AND kpi_id > 0
          AND kpi_version_id IS NOT NULL AND kpi_version_id > 0
          AND kpi_version_no IS NOT NULL AND kpi_version_no > 0
          AND kpi_code IS NOT NULL AND kpi_name IS NOT NULL
          AND kpi_subject_type IS NOT NULL AND kpi_period_type IS NOT NULL
          AND kpi_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
          AND CHAR_LENGTH(TRIM(kpi_name)) BETWEEN 1 AND 200
          AND kpi_subject_type IN ('MEMBER','DEPARTMENT','ROLE')
          AND kpi_period_type IN ('MONTH','QUARTER','YEAR'))
        OR
        (widget_type <> 'KPI_VALUE'
          AND data_source_id IS NOT NULL AND data_source_id > 0
          AND data_source_version_id IS NOT NULL
          AND data_source_version_id > 0
          AND data_source_version_no IS NOT NULL
          AND data_source_version_no > 0
          AND data_source_code IS NOT NULL AND module_code IS NOT NULL
          AND schema_version_id IS NOT NULL
          AND data_source_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'
          AND module_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,99}$'
          AND CHAR_LENGTH(TRIM(schema_version_id)) BETWEEN 1 AND 200
          AND kpi_id IS NULL AND kpi_version_id IS NULL
          AND kpi_version_no IS NULL AND kpi_code IS NULL
          AND kpi_name IS NULL AND kpi_subject_type IS NULL
          AND kpi_period_type IS NULL)),
    ADD CONSTRAINT ck_module_dashboard_widget_code CHECK (
        widget_code REGEXP '^[A-Za-z][A-Za-z0-9_]{0,63}$'),
    ADD CONSTRAINT ck_module_dashboard_widget_type CHECK (
        (widget_type = 'KPI_VALUE' AND row_limit IS NULL
          AND stat_aggregation IS NULL)
        OR (widget_type = 'STAT_COUNT' AND row_limit IS NULL
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
