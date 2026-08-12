package com.unique.examine.module.report.scheduling.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportScheduleMigrationContractTest {
    @Test
    void migrationFreezesScopedSchedulesRecipientsAndRestartSafeOccurrences()
            throws Exception {
        var sql = normalize(Files.readString(migration()));

        assertThat(sql)
                .contains("create table un_module_report_schedule (")
                .contains("create table un_module_report_schedule_recipient (")
                .contains("create table un_module_report_schedule_occurrence (")
                .contains("create table un_module_report_schedule_occurrence_recipient (")
                .contains("create table un_module_report_schedule_delivery (")
                .contains("primary key (system_id, tenant_id, id)")
                .contains("unique key uk_module_report_schedule_code ( system_id, tenant_id, report_id, schedule_code)")
                .contains("unique key uk_module_report_schedule_occurrence_fire ( system_id, tenant_id, schedule_id, scheduled_at)")
                .contains("unique key uk_module_report_schedule_occurrence_export")
                .contains("days_of_week_json json not null")
                .contains("json_schema_valid(")
                .contains("\"uniqueitems\":true")
                .contains("\"minimum\":1,\"maximum\":7")
                .contains("cadence_type = 'daily'")
                .contains("cadence_type = 'weekly'")
                .contains("json_length(days_of_week_json) between 1 and 7")
                .contains("recipient_count between 1 and 50")
                .contains("recipient_ordinal < 50")
                .contains("enabled = (next_fire_at is not null)")
                .contains("last_scheduled_at datetime(6) null")
                .contains("idx_module_report_schedule_due")
                .contains("enabled, next_fire_at, system_id, tenant_id, id")
                .contains("idx_module_report_schedule_occurrence_claim")
                .contains("status, available_at, lease_until, scheduled_at")
                .contains("max_attempts between 1 and 3")
                .contains("attempt_count between 0 and max_attempts")
                .contains("status = 'pending'")
                .contains("status = 'running'")
                .contains("status = 'succeeded'")
                .contains("status = 'failed'")
                .contains("constraint fk_module_report_schedule_report foreign key")
                .contains("references un_module_report ( system_id, tenant_id, id)")
                .contains("constraint fk_module_report_schedule_owner foreign key")
                .contains("references un_plat_member_tenant ( system_id, member_id, tenant_id)")
                .contains("constraint fk_module_report_schedule_recipient_member foreign key")
                .contains("constraint fk_module_report_schedule_occurrence_schedule foreign key")
                .contains("constraint fk_module_report_schedule_occurrence_export foreign key")
                .contains("constraint fk_module_report_schedule_delivery_occurrence_recipient foreign key")
                .contains("unique key uk_module_report_schedule_delivery_message ( system_id, tenant_id, message_id)")
                .doesNotContain("fk_module_report_schedule_delivery_message")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade", "drop table", "delete from", "update ");
    }

    private static Path migration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_57_0__module_report_schedule.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate report schedule migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
