package com.unique.examine.module.kpi.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class KpiMigrationContractTest {
    @Test
    void migrationPinsDefinitionsTargetsAndAppendOnlyCalculations()
            throws Exception {
        var sql = normalize(Files.readString(migration()));

        assertThat(sql)
                .contains("create table un_module_kpi (")
                .contains("create table un_module_kpi_version (")
                .contains("create table un_module_kpi_target (")
                .contains("create table un_module_kpi_calculation (")
                .contains("create table un_module_kpi_calculation_member (")
                .contains("primary key (system_id, tenant_id, id)")
                .contains("unique key uk_module_kpi_code")
                .contains("draft_json json not null")
                .contains("draft_version bigint not null")
                .contains("constraint fk_module_kpi_active_version")
                .contains("constraint fk_module_kpi_version_source")
                .contains("references un_module_data_source_version")
                .contains("data_source_version_id, data_source_version_no")
                .contains("subject_type in ('member','department','role')")
                .contains("period_type in ('month','quarter','year')")
                .contains("attainment_direction in ('at_least','at_most')")
                .contains("warning_threshold varchar(1000)")
                .contains("time_query_type in ('date','datetime')")
                .contains("unique key uk_module_kpi_target_period")
                .contains("unique key uk_module_kpi_target_request")
                .contains("period_end = date_add(period_start, interval 3 month)")
                .contains("target_value varchar(1000)")
                .contains("actual_value varchar(1000)")
                .contains("attainment_rate varchar(1000)")
                .contains("request_key_hash char(64)")
                .contains("unique key uk_module_kpi_calculation_request")
                .contains("unique key uk_module_kpi_calculation_running")
                .contains("case when status = 'running' then target_id else null end")
                .contains("authz_epoch bigint null")
                .contains("statistics_query_id char(64)")
                .contains("trend_json json")
                .contains("explanation_json json")
                .contains("warning_status in ('achieved','at_risk','missed')")
                .contains("warning_status = 'calculation_failed'")
                .contains("constraint fk_module_kpi_calculation_member_calc")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade", "drop table", "delete from");
    }

    private static Path migration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_53_0__module_kpi_target_calculation.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate V8.53 KPI migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
