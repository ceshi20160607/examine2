package com.unique.examine.flow.interaction.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowInteractionMigrationContractTest {
    @Test
    void migrationCreatesScopedAppendOnlyTablesAndBackfillsBothPermissions() throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "create table un_flow_urge",
                "create table un_flow_comment",
                "primary key (system_id, tenant_id, urge_id)",
                "primary key (system_id, tenant_id, comment_id)",
                "system_id, tenant_id, instance_id, created_at, urge_id",
                "system_id, tenant_id, instance_id, created_at, comment_id",
                "references un_flow_instance",
                "on delete restrict",
                "char_length(message) between 0 and 500",
                "char_length(body) between 1 and 2000",
                "'flow.instance.urge'",
                "'flow.instance.comment'",
                "role_row.role_type = 'root'",
                "update un_plat_authz_epoch",
                "update un_plat_system system_row");
        assertThat(migration).doesNotContain(
                "alter table un_flow_instance",
                "alter table un_flow_history_event",
                "delete from un_flow");
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration/V8_9_0__flow_urge_comment.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate V8_9_0 Flow interaction migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
