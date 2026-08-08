package com.unique.examine.module.dashboard.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DashboardMigrationContractTest {
    @Test
    void migrationFreezesScopedCasImmutableVersionsAndPinnedWidgets()
            throws Exception {
        var sql = normalize(Files.readString(migration()));

        assertThat(sql)
                .contains("create table un_module_dashboard (")
                .contains("create table un_module_dashboard_version (")
                .contains("create table un_module_dashboard_version_widget (")
                .contains("primary key (system_id, tenant_id, id)")
                .contains("unique key uk_module_dashboard_code ( system_id, tenant_id, dashboard_code)")
                .contains("unique key uk_module_dashboard_placement ( system_id, tenant_id, placement")
                .contains("placement = 'system_home'")
                .contains("draft_json json not null")
                .contains("draft_version bigint not null")
                .contains("version bigint not null")
                .contains("source_draft_version bigint not null")
                .contains("unique key uk_module_dashboard_draft_publish")
                .contains("snapshot_json json not null")
                .contains("snapshot_fingerprint char(64)")
                .contains("widget_count between 1 and 20")
                .contains("widget_ordinal < 20")
                .contains("unique key uk_module_dashboard_widget_ordinal")
                .contains("unique key uk_module_dashboard_widget_code")
                .contains("widget_type = 'stat_count' and row_limit is null")
                .contains("widget_type = 'data_list' and row_limit between 1 and 20")
                .contains("grid_x < 12")
                .contains("grid_x + grid_width <= 12")
                .contains("grid_y + grid_height <= 100")
                .contains("module_code varchar(100)")
                .contains("schema_version_id varchar(200)")
                .contains("constraint fk_module_dashboard_tenant foreign key")
                .contains("references un_plat_tenant (system_id, id)")
                .contains("constraint fk_module_dashboard_version_root foreign key")
                .contains("constraint fk_module_dashboard_active_version foreign key")
                .contains("active_version_id, active_version_no")
                .contains("constraint fk_module_dashboard_widget_version foreign key")
                .contains("dashboard_version_id, dashboard_version_no")
                .contains("constraint fk_module_dashboard_widget_source_version foreign key")
                .contains("data_source_version_id, data_source_version_no")
                .contains("references un_module_data_source_version ( system_id, tenant_id, data_source_id, id, version_no)")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade", "un_module_record", "drop table");
    }

    @Test
    void statisticsMigrationExtendsWidgetKindsWithoutMutatingPublications()
            throws Exception {
        var sql = normalize(Files.readString(migration(
                "V8_52_0__data_source_statistics_dashboard.sql")));

        assertThat(sql)
                .contains("alter table un_module_dashboard_version_widget")
                .contains("alter table un_module_data_source_version drop index uk_module_data_source_draft_publish")
                .contains("alter table un_module_dashboard_version drop index uk_module_dashboard_draft_publish")
                .contains("add column stat_aggregation varchar(8)")
                .contains("add column stat_measure_field_code varchar(64)")
                .contains("add column stat_measure_field_id bigint")
                .contains("add column stat_measure_field_name varchar(200)")
                .contains("add column stat_measure_field_type varchar(100)")
                .contains("add column stat_measure_query_type varchar(100)")
                .contains("add column stat_group_field_code varchar(64)")
                .contains("add column stat_group_field_id bigint")
                .contains("add column stat_group_limit tinyint unsigned")
                .contains("add column stat_time_field_code varchar(64)")
                .contains("add column stat_time_field_id bigint")
                .contains("add column stat_time_grain varchar(8)")
                .contains("add column stat_time_start date")
                .contains("add column stat_time_end date")
                .contains("drop check ck_module_dashboard_widget_type")
                .contains("widget_type = 'stat_value'")
                .contains("widget_type in ('bar_chart','pie_chart')")
                .contains("widget_type = 'line_trend'")
                .contains("datediff(stat_time_end, stat_time_start) between 1 and 100")
                .contains("weekday(stat_time_start) = 0")
                .contains("between 7 and 700")
                .contains("dayofmonth(stat_time_start) = 1")
                .contains("timestampdiff( month, stat_time_start, stat_time_end) between 1 and 100")
                .contains("constraint ck_module_dashboard_widget_stat_metadata")
                .contains("stat_measure_field_id > 0")
                .contains("stat_group_field_id > 0")
                .contains("stat_time_field_id > 0")
                .doesNotContain("drop table", "delete from", "update ");
    }

    @Test
    void kpiWidgetMigrationEnforcesExclusiveImmutablePins() throws Exception {
        var sql = normalize(Files.readString(migration(
                "V8_54_0__module_dashboard_kpi_widget.sql")));

        assertThat(sql)
                .contains("alter table un_module_dashboard_version_widget")
                .contains("modify column data_source_id bigint null")
                .contains("modify column data_source_version_id bigint null")
                .contains("add column kpi_id bigint null")
                .contains("add column kpi_version_id bigint null")
                .contains("add column kpi_version_no int unsigned null")
                .contains("add column kpi_code varchar(64)")
                .contains("add column kpi_name varchar(200)")
                .contains("add column kpi_subject_type varchar(16)")
                .contains("add column kpi_period_type varchar(16)")
                .contains("constraint fk_module_dashboard_widget_kpi_version foreign key")
                .contains("references un_module_kpi_version ( system_id, tenant_id, kpi_id, id, version_no)")
                .contains("on delete restrict")
                .contains("key idx_module_dashboard_widget_kpi")
                .contains("constraint ck_module_dashboard_widget_source_kind")
                .contains("widget_type = 'kpi_value'")
                .contains("data_source_id is null")
                .contains("kpi_id is not null and kpi_id > 0")
                .contains("kpi_version_id is not null and kpi_version_id > 0")
                .contains("kpi_version_no is not null and kpi_version_no > 0")
                .contains("data_source_id is not null and data_source_id > 0")
                .contains("widget_type <> 'kpi_value'")
                .contains("kpi_id is null and kpi_version_id is null")
                .doesNotContain("drop table", "delete from", "update ");
    }

    @Test
    void scopeAndWidgetBehaviorMigrationKeepsOneEngineAndOwnerIsolation()
            throws Exception {
        var sql = normalize(Files.readString(migration(
                "V8_88_0__dashboard_scopes_external_consumption.sql")));

        assertThat(sql)
                .contains("constraint ck_module_data_source_join_plan")
                .contains("constraint ck_module_data_source_version_join_plan")
                .contains("'$.multimodulejoin.inputs'")
                .contains("between 2 and 8")
                .contains("'fail_fast','allow_partial_left'")
                .contains("drop index uk_module_dashboard_placement")
                .contains("generated always as")
                .contains("unique key uk_module_dashboard_system_home")
                .contains("create table un_module_dashboard_scope_binding")
                .contains("unique key uk_module_dashboard_scope_context")
                .contains("scope_type='personal_home' and owner_member_id>0")
                .contains("references un_module_dashboard (system_id,tenant_id,id)")
                .contains("add column refresh_seconds smallint unsigned")
                .contains("add column click_through varchar(300)")
                .contains("add column style_variant varchar(16)")
                .contains("widget_type in ('data_list','todo_list','quick_entry')")
                .contains("widget_type in ('stat_value','progress')")
                .contains("widget_type in ('bar_chart','pie_chart','ranking')")
                .doesNotContain("drop table", "delete from", "update ");
    }

    private static Path migration() throws IOException {
        return migration("V8_51_0__module_dashboard_publish.sql");
    }

    private static Path migration(String fileName) throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration/" + fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate dashboard migration " + fileName);
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
