package com.unique.examine.module.runtime.flow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFlowMigrationContractTest {

    @Test
    void migrationOwnsOneProjectionPerRecordAndOneRecordPerInstance() throws Exception {
        var sql = normalize(Files.readString(migrationPath()));

        assertThat(sql)
                .contains("create table un_module_record_flow_state")
                .contains("primary key (system_id, tenant_id, record_id)")
                .contains("unique key uk_record_flow_instance ( system_id, tenant_id, instance_id )")
                .contains("foreign key ( system_id, tenant_id, logical_module_id, record_id )")
                .contains("references un_module_record ( system_id, tenant_id, logical_module_id, record_id )")
                .contains("status in ('pending', 'approved', 'rejected', 'withdrawn', 'terminated')")
                .contains("version >= 0")
                .contains("key idx_record_flow_status");
    }

    private static Path migrationPath() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_13_0__module_record_flow_state.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate V8_13_0__module_record_flow_state.sql");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ");
    }
}
