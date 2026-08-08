-- Cycle115: extend the existing immutable dashboard engine to application,
-- module and owner-isolated personal scopes. External source consumption uses
-- existing data-source publications and requires no parallel storage.

-- MULTI_MODULE_JOIN is an immutable data-source publication stored in the
-- existing draft/snapshot JSON. Database checks enforce the top-level bounded
-- plan; Java publication checks pin exact 2..8 source versions, field
-- capabilities, left-deep edges, cardinality, namespace and tenant identity.
ALTER TABLE un_module_data_source
    ADD CONSTRAINT ck_module_data_source_join_plan CHECK (
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
            draft_json,'$.sourceKind')),'NATIVE_MODULE')
            <> 'MULTI_MODULE_JOIN'
        OR (JSON_TYPE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin'))='OBJECT'
            AND JSON_TYPE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.inputs'))='ARRAY'
            AND JSON_LENGTH(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.inputs')) BETWEEN 2 AND 8
            AND JSON_TYPE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.edges'))='ARRAY'
            AND JSON_LENGTH(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.edges')) =
                JSON_LENGTH(JSON_EXTRACT(
                    draft_json,'$.multiModuleJoin.inputs')) - 1
            AND JSON_TYPE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.projections'))='ARRAY'
            AND JSON_LENGTH(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.projections')) BETWEEN 1 AND 50
            AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.timeoutSeconds')) AS UNSIGNED)
                BETWEEN 1 AND 10
            AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.rowLimit')) AS UNSIGNED)
                BETWEEN 1 AND 100
            AND JSON_UNQUOTE(JSON_EXTRACT(
                draft_json,'$.multiModuleJoin.failureMode')) IN (
                    'FAIL_FAST','ALLOW_PARTIAL_LEFT')));

ALTER TABLE un_module_data_source_version
    ADD CONSTRAINT ck_module_data_source_version_join_plan CHECK (
        COALESCE(JSON_UNQUOTE(JSON_EXTRACT(
            snapshot_json,'$.sourceKind')),'NATIVE_MODULE')
            <> 'MULTI_MODULE_JOIN'
        OR (JSON_TYPE(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin'))='OBJECT'
            AND JSON_LENGTH(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin.inputs')) BETWEEN 2 AND 8
            AND JSON_LENGTH(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin.edges')) =
                JSON_LENGTH(JSON_EXTRACT(
                    snapshot_json,'$.multiModuleJoin.inputs')) - 1
            AND JSON_LENGTH(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin.projections')) BETWEEN 1 AND 50
            AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin.timeoutSeconds')) AS UNSIGNED)
                BETWEEN 1 AND 10
            AND CAST(JSON_UNQUOTE(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin.rowLimit')) AS UNSIGNED)
                BETWEEN 1 AND 100
            AND JSON_UNQUOTE(JSON_EXTRACT(
                snapshot_json,'$.multiModuleJoin.failureMode')) IN (
                    'FAIL_FAST','ALLOW_PARTIAL_LEFT')));

ALTER TABLE un_module_dashboard
    DROP INDEX uk_module_dashboard_placement,
    DROP CHECK ck_module_dashboard_placement,
    ADD COLUMN system_home_singleton TINYINT
        GENERATED ALWAYS AS (
            CASE WHEN placement='SYSTEM_HOME' THEN 1 ELSE NULL END) STORED
        AFTER placement,
    ADD UNIQUE KEY uk_module_dashboard_system_home (
        system_id,tenant_id,system_home_singleton),
    ADD CONSTRAINT ck_module_dashboard_placement CHECK (
        placement IN ('SYSTEM_HOME','APPLICATION_HOME','MODULE_HOME',
                      'PERSONAL_HOME'));

ALTER TABLE un_module_dashboard_version
    DROP CHECK ck_module_dashboard_version_placement,
    ADD CONSTRAINT ck_module_dashboard_version_placement CHECK (
        placement IN ('SYSTEM_HOME','APPLICATION_HOME','MODULE_HOME',
                      'PERSONAL_HOME'));

CREATE TABLE un_module_dashboard_scope_binding (
    dashboard_id BIGINT NOT NULL,
    system_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    scope_type VARCHAR(32)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    scope_key VARCHAR(128)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    owner_member_id BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (system_id,tenant_id,dashboard_id),
    UNIQUE KEY uk_module_dashboard_scope_context (
        system_id,tenant_id,scope_type,scope_key,owner_member_id),
    KEY idx_module_dashboard_personal (
        system_id,tenant_id,owner_member_id,scope_key,dashboard_id),
    CONSTRAINT fk_module_dashboard_scope_root FOREIGN KEY (
        system_id,tenant_id,dashboard_id)
        REFERENCES un_module_dashboard (system_id,tenant_id,id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_module_dashboard_scope_identity CHECK (
        dashboard_id > 0 AND system_id > 0 AND tenant_id > 0),
    CONSTRAINT ck_module_dashboard_scope_key CHECK (
        scope_key REGEXP '^[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}$'),
    CONSTRAINT ck_module_dashboard_scope_owner CHECK (
        (scope_type IN ('APPLICATION_HOME','MODULE_HOME')
          AND owner_member_id=0)
        OR (scope_type='PERSONAL_HOME' AND owner_member_id>0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE un_module_dashboard_version_widget
    ADD COLUMN refresh_seconds SMALLINT UNSIGNED NOT NULL DEFAULT 0
        AFTER kpi_period_type,
    ADD COLUMN click_through VARCHAR(300) NULL AFTER refresh_seconds,
    ADD COLUMN style_variant VARCHAR(16)
        CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STANDARD'
        AFTER click_through,
    DROP CHECK ck_module_dashboard_widget_type,
    ADD CONSTRAINT ck_module_dashboard_widget_behavior CHECK (
        (refresh_seconds=0 OR refresh_seconds BETWEEN 15 AND 3600)
        AND style_variant IN ('STANDARD','COMPACT','EMPHASIS')
        AND (click_through IS NULL OR (
            CHAR_LENGTH(click_through) BETWEEN 1 AND 300
            AND click_through LIKE '/%'
            AND click_through NOT LIKE '//%'
            AND click_through NOT LIKE '%://%'))),
    ADD CONSTRAINT ck_module_dashboard_widget_type CHECK (
        (widget_type = 'KPI_VALUE' AND row_limit IS NULL
          AND stat_aggregation IS NULL)
        OR (widget_type = 'STAT_COUNT' AND row_limit IS NULL
          AND stat_aggregation IS NULL)
        OR (widget_type IN ('DATA_LIST','TODO_LIST','QUICK_ENTRY')
          AND row_limit BETWEEN 1 AND 20 AND stat_aggregation IS NULL)
        OR (widget_type IN ('STAT_VALUE','PROGRESS') AND row_limit IS NULL
          AND stat_aggregation IS NOT NULL
          AND stat_group_field_code IS NULL
          AND stat_time_field_code IS NULL)
        OR (widget_type IN ('BAR_CHART','PIE_CHART','RANKING')
          AND row_limit IS NULL AND stat_aggregation IS NOT NULL
          AND stat_group_field_code IS NOT NULL
          AND stat_time_field_code IS NULL)
        OR (widget_type = 'LINE_TREND' AND row_limit IS NULL
          AND stat_aggregation IS NOT NULL
          AND stat_group_field_code IS NULL
          AND stat_time_field_code IS NOT NULL));
