package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAssignmentMigrationContractTest {
    @Test
    void migrationBackfillsRuntimeSnapshotAssignmentFactsPermissionsAndExpandedCasProgress()
            throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "add column approver_ids_json json null after approver_id",
                "group_concat(step_row.approver_id order by step_row.step_no separator ',')",
                "modify column approver_ids_json json not null",
                "json_length(approver_ids_json) between 1 and 10",
                "json_contains(approver_ids_json, cast(approver_id as json), '$') = 1",
                "drop check ck_flow_instance_state_version",
                "state_version between 0 and 2147483647",
                "status in ('approved', 'rejected', 'withdrawn', 'terminated')",
                "add column target_member_id bigint null",
                "add column assignment_position varchar(8)",
                "event_type = 'transferred'",
                "event_type = 'add_signed'",
                "assignment_position in ('before', 'after')",
                "'flow.instance.transfer'",
                "'flow.instance.add-sign'",
                "role_row.role_type = 'root'",
                "update un_plat_authz_epoch",
                "update un_plat_system system_row");
        assertThat(migration).doesNotContain(
                "update un_flow_definition_version_step",
                "delete from un_flow",
                "drop table");
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_10_0__flow_transfer_add_sign.sql"
            );
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate V8_10_0 Flow assignment migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
