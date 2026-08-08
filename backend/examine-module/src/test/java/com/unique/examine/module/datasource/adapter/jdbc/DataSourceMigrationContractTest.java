package com.unique.examine.module.datasource.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceMigrationContractTest {
    @Test
    void migrationFreezesScopedDraftCasAndImmutableActiveSnapshots()
            throws Exception {
        var sql = normalize(Files.readString(migration()));

        assertThat(sql)
                .contains("create table un_module_data_source (")
                .contains("create table un_module_data_source_version (")
                .contains("primary key (system_id, tenant_id, id)")
                .contains("unique key uk_module_data_source_code ( system_id, tenant_id, data_source_code)")
                .contains("draft_json json not null")
                .contains("draft_version bigint not null")
                .contains("version bigint not null")
                .contains("source_draft_version bigint not null")
                .contains("unique key uk_module_data_source_draft_publish")
                .contains("snapshot_json json not null")
                .contains("snapshot_fingerprint char(64)")
                .contains("unique key uk_module_data_source_version_no")
                .contains("constraint fk_module_data_source_tenant foreign key")
                .contains("references un_plat_tenant (system_id, id)")
                .contains("constraint fk_module_data_source_module foreign key")
                .contains("references un_module_definition (system_id, id)")
                .contains("constraint fk_module_data_source_version_root foreign key")
                .contains("constraint fk_module_data_source_active_version foreign key")
                .contains("active_version_id, active_version_no")
                .contains("on delete restrict")
                .contains("'module.config.manage'")
                .contains("role_row.role_type = 'root'")
                .contains("update un_plat_authz_epoch")
                .contains("set system_row.permission_version = epoch_row.epoch")
                .doesNotContain("un_module_record", "drop table");
    }

    private static Path migration() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_50_0__module_data_source_publish.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate V8.50 data-source migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
