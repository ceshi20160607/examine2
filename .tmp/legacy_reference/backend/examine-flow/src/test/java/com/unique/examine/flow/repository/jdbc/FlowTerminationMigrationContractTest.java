package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTerminationMigrationContractTest {
    @Test
    void migrationExpandsStateHistoryPermissionRootGrantAndAuthzEpoch() throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "drop check ck_flow_instance_status",
                "drop check ck_flow_instance_completion",
                "status in ('pending', 'approved', 'rejected', 'withdrawn', 'terminated')",
                "status in ('approved', 'rejected', 'withdrawn', 'terminated')",
                "drop check ck_flow_history_event",
                "event_type = 'terminated'",
                "from_status = 'pending' and to_status = 'terminated'",
                "char_length(trim(comment)) between 1 and 500",
                "'flow.instance.terminate'",
                "role_row.role_type = 'root'",
                "insert into un_plat_role_permission",
                "update un_plat_authz_epoch",
                "update un_plat_system system_row");
        assertThat(migration).doesNotContain(
                "create table un_flow_instance",
                "create table un_flow_history_event",
                "delete from un_flow");
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_8_0__flow_instance_termination.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate V8_8_0 flow termination migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
