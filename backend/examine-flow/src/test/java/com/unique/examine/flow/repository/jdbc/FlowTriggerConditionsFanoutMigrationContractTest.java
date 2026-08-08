package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTriggerConditionsFanoutMigrationContractTest {
    @Test
    void migrationAddsConditionSnapshotsAndRelaxesExclusiveOnlyChecks() throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "alter table un_flow_definition_draft add column trigger_conditions json null",
                "alter table un_flow_definition_version add column trigger_conditions json null",
                "set trigger_conditions = json_array() where trigger_module_code is not null",
                "drop check ck_flow_draft_trigger_binding",
                "drop check ck_flow_version_trigger_binding",
                "trigger_exclusive in (false, true)",
                "json_type(trigger_conditions) = 'array'",
                "json_length(trigger_conditions) <= 10"
        );
        assertThat(migration).doesNotContain("trigger_exclusive = true");
    }

    @Test
    void migrationCreatesAndBackfillsScopedOrderedDispatchChildren() throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "create table un_flow_trigger_dispatch_instance",
                "primary key (system_id, tenant_id, event_key, ordinal)",
                "unique key uk_flow_trigger_dispatch_item_instance ( system_id, tenant_id, instance_id )",
                "references un_flow_trigger_dispatch ( system_id, tenant_id, event_key ) on delete restrict",
                "references un_flow_definition_version ( system_id, tenant_id, definition_id, version_no ) on delete restrict",
                "references un_flow_instance ( system_id, tenant_id, instance_id ) on delete restrict",
                "insert into un_flow_trigger_dispatch_instance",
                "select system_id, tenant_id, event_key, 0, definition_id, definition_version, instance_id, created_at",
                "from un_flow_trigger_dispatch where instance_id is not null"
        );
    }

    @Test
    void jdbcPersistsAndReplaysChildrenByOrdinal() {
        assertThat(normalize(JdbcApprovalSql.INSERT_TRIGGER_DISPATCH_INSTANCE)).contains(
                "system_id",
                "tenant_id",
                "event_key",
                "ordinal",
                "definition_id",
                "definition_version",
                "instance_id"
        );
        assertThat(normalize(JdbcApprovalSql.SELECT_TRIGGER_DISPATCH_INSTANCES)).contains(
                "system_id=?",
                "tenant_id=?",
                "event_key=?",
                "order by ordinal"
        );
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_17_0__flow_trigger_conditions_fanout.sql"
            );
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the Flow trigger fan-out migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
