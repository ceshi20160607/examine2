package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDefinitionTriggerMigrationContractTest {
    @Test
    void migrationAddsAllOrNoneDraftVersionSnapshotsAndImmutableDispatchResults()
            throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "alter table un_flow_definition_draft",
                "alter table un_flow_definition_version",
                "trigger_module_code varchar(64)",
                "trigger_event varchar(32)",
                "trigger_priority int null",
                "trigger_exclusive boolean null",
                "constraint ck_flow_draft_trigger_binding check",
                "constraint ck_flow_version_trigger_binding check",
                "trigger_event = 'record_activated'",
                "trigger_priority between -1000 and 1000",
                "trigger_exclusive = true",
                "create table un_flow_trigger_dispatch",
                "primary key (system_id, tenant_id, event_key)",
                "constraint ck_flow_trigger_dispatch_result check",
                "definition_id is null",
                "definition_version is null",
                "instance_id is null",
                "definition_id is not null",
                "definition_version is not null",
                "instance_id is not null",
                "on delete restrict"
        );
        assertThat(count(migration, "on delete restrict")).isEqualTo(2);
    }

    @Test
    void jdbcMatcherUsesOnlyLatestPublishedVersionsAndStablePriorityOrdering() {
        var matcher = normalize(JdbcApprovalSql.SELECT_TRIGGER_CANDIDATES);

        assertThat(matcher).contains(
                "select definition_id,max(version_no) as version_no",
                "group by definition_id",
                "latest.version_no=version_row.version_no",
                "version_row.system_id=?",
                "version_row.tenant_id=?",
                "version_row.trigger_module_code=?",
                "version_row.trigger_event=?",
                "order by version_row.trigger_priority desc,version_row.definition_id asc"
        );
        assertThat(normalize(JdbcApprovalSql.SELECT_TRIGGER_DISPATCH_FOR_UPDATE))
                .contains(
                        "system_id=?",
                        "tenant_id=?",
                        "event_key=?",
                        "for update"
                );
        assertThat(normalize(JdbcApprovalSql.INSERT_TRIGGER_DISPATCH))
                .contains(
                        "event_key",
                        "definition_id",
                        "definition_version",
                        "instance_id"
                );
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_15_0__flow_definition_record_trigger.sql"
            );
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the Flow definition trigger migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
