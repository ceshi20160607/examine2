package com.unique.examine.work.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTaskReminderMigrationContractTest {
    @Test
    void migrationFreezesRestrictiveGenerationLeaseAndScanShape() throws Exception {
        var sql = Files.readString(findMigration());

        assertThat(sql)
                .contains("ADD COLUMN reminder_at DATETIME(6) NULL")
                .contains("CREATE TABLE un_work_task_reminder")
                .contains("uk_work_task_reminder_generation")
                .contains("idx_work_task_reminder_due")
                .contains("idx_work_task_reminder_lease")
                .contains("fk_work_task_reminder_task")
                .contains("ck_work_task_reminder_schedule")
                .contains("ck_work_task_reminder_lease")
                .contains("ck_work_task_reminder_state")
                .contains("ON DELETE RESTRICT")
                .doesNotContain("ON DELETE CASCADE");
    }

    private static Path findMigration() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration")
                    .resolve("V8_48_0__work_task_reminder.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate V8.48 migration");
    }
}
