package com.unique.examine.module.runtime.flow;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class RecordFlowFanoutMigrationContractTest {

    @Test
    void migrationFreezesAdditionalIdentityOrderingEventAndRecordOwnership() throws Exception {
        var sql = normalize(Files.readString(migrationPath()));

        assertThat(sql)
                .contains("create table un_module_record_flow_state_item")
                .contains("primary key (system_id, tenant_id, record_id, instance_id)")
                .contains("unique key uk_record_flow_item_instance ( system_id, tenant_id, instance_id )")
                .contains("key idx_record_flow_item_page ( system_id, tenant_id, record_id, created_at, instance_id )")
                .contains("key idx_record_flow_item_pending ( system_id, tenant_id, record_id, status, instance_id )")
                .contains("key idx_record_flow_item_event ( system_id, tenant_id, event_key, instance_id )")
                .contains("foreign key ( system_id, tenant_id, logical_module_id, record_id )")
                .contains("references un_module_record ( system_id, tenant_id, logical_module_id, record_id )")
                .contains("char_length(trim(event_key)) between 1 and 200")
                .contains("status in ('pending', 'approved', 'rejected', 'withdrawn', 'terminated')")
                .contains("version >= 0");
    }

    private static Path migrationPath() throws IOException {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_16_0__module_record_flow_fanout.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IOException("Cannot locate V8_16_0__module_record_flow_fanout.sql");
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ");
    }
}
