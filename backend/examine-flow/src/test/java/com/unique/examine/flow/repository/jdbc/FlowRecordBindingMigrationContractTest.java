package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRecordBindingMigrationContractTest {
    @Test
    void migrationAddsAnAllOrNoneScopedRecordBinding() throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "alter table un_flow_instance",
                "add column module_code varchar(64)",
                "add column record_id bigint null",
                "add key idx_flow_instance_record ( system_id, tenant_id, module_code, record_id, started_at, instance_id )",
                "constraint ck_flow_instance_record_binding check",
                "module_code is null and record_id is null",
                "module_code is not null",
                "record_id is not null",
                "record_id > 0",
                "module_code regexp '^[a-za-z][a-za-z0-9_]{0,63}$'"
        );
        assertThat(migration).doesNotContain("v8_13_0");
    }

    @Test
    void everyInstanceWriteAndReadCarriesTheBindingColumns() {
        assertThat(normalize(JdbcApprovalSql.INSERT_INSTANCE))
                .contains("module_code", "record_id");
        assertThat(List.of(
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES,
                JdbcApprovalSql.SELECT_APPROVAL_TASKS,
                JdbcApprovalSql.SELECT_CLAIMABLE_TASKS
        )).allSatisfy(sql -> assertThat(normalize(sql))
                .contains("module_code", "record_id"));
        assertThat(normalize(JdbcApprovalSql.UPDATE_INSTANCE_DECISION))
                .doesNotContain("module_code=?", "record_id=?");
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration/V8_14_0__flow_record_binding.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the Flow record-binding migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
