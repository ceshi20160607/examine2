package com.unique.examine.module.report.export.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportExportMigrationContractTest {
    @Test
    void migrationFreezesReplayScopedPinsBoundedResultsAndStateShapes()
            throws Exception {
        var sql = normalize(Files.readString(migration()));

        assertThat(sql)
                .contains("create table un_module_report_export_run (")
                .contains("primary key (system_id, tenant_id, id)")
                .contains("unique key uk_module_report_export_id (id)")
                .contains("unique key uk_module_report_export_request ( system_id, tenant_id, requested_by_member_id, report_id, request_key_hash)")
                .contains("unique key uk_module_report_export_job (job_id)")
                .contains("report_version_id bigint not null")
                .contains("report_version_no int unsigned not null")
                .contains("data_source_version_id bigint not null")
                .contains("data_source_version_no int unsigned not null")
                .contains("fields_json json not null")
                .contains("json_type(fields_json) = 'array'")
                .contains("json_length(fields_json) = field_count")
                .contains("field_count between 1 and 100")
                .contains("request_key_hash regexp '^[0-9a-f]{64}$'")
                .contains("requested_by_account_id bigint not null")
                .contains("request_id varchar(128)")
                .contains("trace_id varchar(128)")
                .contains("total_rows is null or total_rows >= 0")
                .contains("processed_rows between 0 and 5000")
                .contains("truncated = (total_rows > processed_rows)")
                .contains("status = 'queued'")
                .contains("status = 'running'")
                .contains("status = 'succeeded'")
                .contains("status = 'failed'")
                .contains("result_size = octet_length(result_content)")
                .contains("lower(result_filename) like '%.xlsx'")
                .contains("result_size <= 134217728")
                .contains("error_code regexp '^[a-z][a-z0-9_]{1,99}$'")
                .contains("constraint fk_module_report_export_root foreign key")
                .contains("references un_module_report ( system_id, tenant_id, id)")
                .contains("constraint fk_module_report_export_version foreign key")
                .contains("references un_module_report_version ( system_id, tenant_id, report_id, id, version_no)")
                .contains("constraint fk_module_report_export_source foreign key")
                .contains("references un_module_data_source_version ( system_id, tenant_id, data_source_id, id, version_no)")
                .contains("on delete restrict")
                .contains("idx_module_report_export_owner")
                .contains("requested_by_member_id, created_at desc, id desc")
                .doesNotContain("on delete cascade", "drop table", "delete from", "update ");
    }

    private static Path migration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_56_0__module_report_xlsx_export.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate report export migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
