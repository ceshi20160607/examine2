package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowRecordEventTriggerMigrationContractTest {
    @Test
    void migrationExpandsBothSnapshotsAndRestrictsAutomaticStatusMappings()
            throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "alter table un_flow_definition_draft",
                "alter table un_flow_definition_version",
                "drop check ck_flow_draft_trigger_binding",
                "drop check ck_flow_version_trigger_binding",
                "'record_activated'",
                "'record_created'",
                "'record_updated'",
                "'record_deleted'",
                "'record_status_changed'",
                "constraint ck_flow_draft_trigger_status_mapping check",
                "constraint ck_flow_version_trigger_status_mapping check",
                "status_field_code is null or trigger_event is null "
                        + "or trigger_event = 'record_activated'"
        );
        assertThat(count(migration, "trigger_event in (")).isEqualTo(2);
        assertThat(count(migration, "status_field_code is null")).isEqualTo(2);
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_20_0__flow_record_event_triggers.sql"
            );
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the Flow record-event trigger migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
