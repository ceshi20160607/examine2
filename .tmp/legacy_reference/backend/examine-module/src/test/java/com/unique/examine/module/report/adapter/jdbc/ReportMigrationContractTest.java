package com.unique.examine.module.report.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ReportMigrationContractTest {
    @Test
    void migrationFreezesScopedCasImmutableVersionsAndOrderedFieldPins()
            throws Exception {
        var sql = normalize(Files.readString(migration()));

        assertThat(sql)
                .contains("create table un_module_report (")
                .contains("create table un_module_report_version (")
                .contains("create table un_module_report_version_field (")
                .contains("primary key (system_id, tenant_id, id)")
                .contains("unique key uk_module_report_code ( system_id, tenant_id, report_code)")
                .contains("draft_json json not null")
                .contains("draft_version bigint not null")
                .contains("version bigint not null")
                .contains("active_version_id bigint null")
                .contains("active_version_no int unsigned null")
                .contains("source_draft_version bigint not null")
                .contains("snapshot_json json not null")
                .contains("snapshot_fingerprint char(64)")
                .contains("field_count between 1 and 100")
                .contains("field_ordinal < 100")
                .contains("unique key uk_module_report_field_ordinal")
                .contains("unique key uk_module_report_field_code")
                .contains("unique key uk_module_report_field_id")
                .contains("field_id bigint not null")
                .contains("field_code varchar(64)")
                .contains("field_name varchar(200)")
                .contains("field_type varchar(100)")
                .contains("query_type varchar(100)")
                .contains("constraint fk_module_report_tenant foreign key")
                .contains("references un_plat_tenant (system_id, id)")
                .contains("constraint fk_module_report_version_root foreign key")
                .contains("constraint fk_module_report_active_version foreign key")
                .contains("active_version_id, active_version_no")
                .contains("constraint fk_module_report_field_version foreign key")
                .contains("report_version_id, report_version_no")
                .contains("constraint fk_module_report_version_source foreign key")
                .contains("data_source_version_id, data_source_version_no")
                .contains("references un_module_data_source_version ( system_id, tenant_id, data_source_id, id, version_no)")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade", "drop table", "delete from", "update ");
    }

    private static Path migration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_55_0__module_report_definition.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate report migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
