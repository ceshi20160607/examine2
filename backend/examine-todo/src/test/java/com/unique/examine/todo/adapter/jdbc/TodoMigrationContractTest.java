package com.unique.examine.todo.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TodoMigrationContractTest {
    @Test
    void migrationFreezesDedupeRecipientIndexesSourceChecksAndNoCascades()
            throws Exception {
        var sql = Files.readString(findMigration(
                "V8_49_0__unified_todo_action_center.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_todo_item")
                .contains("CREATE TABLE un_todo_action_log")
                .contains("uk_todo_item_identity")
                .contains("uk_todo_action_idempotency")
                .contains("idx_todo_item_recipient_status")
                .contains("idx_todo_item_recipient_category")
                .contains("idx_todo_item_source")
                .contains("fk_todo_action_item")
                .contains("ck_todo_item_source")
                .contains("ck_todo_action_source")
                .contains("ON DELETE RESTRICT")
                .doesNotContain("ON DELETE CASCADE");
    }

    @Test
    void eventMessageMigrationOnlyExtendsExistingChecks() throws Exception {
        var sql = Files.readString(findMigration(
                "V8_78_0__todo_event_message_reminder_cc.sql"));

        assertThat(sql)
                .contains("DROP CHECK ck_todo_item_source")
                .contains("ADD CONSTRAINT ck_todo_item_source CHECK")
                .contains("source_type='WORK_TASK'")
                .contains("source_type='FLOW_APPROVAL'")
                .contains("source_type='EVENT_MESSAGE'")
                .contains("category IN ('REMINDER','CC')")
                .contains("available_actions='MARK_READ'")
                .contains("action_scope='MARK_READ'")
                .contains("DROP CHECK ck_todo_action_source")
                .contains("requested_action='MARK_READ'")
                .doesNotContain("CREATE TABLE", "ADD COLUMN", "DROP TABLE", "DROP COLUMN");
    }

    private static Path findMigration(String fileName) {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration")
                    .resolve(fileName);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate Todo migration " + fileName);
    }
}
