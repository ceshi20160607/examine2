package com.unique.examine.work.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkMigrationContractTest {
    @Test
    void migrationFreezesScopeStateVersionUniquenessAndIndexes() throws Exception {
        var sql = Files.readString(findMigration("V7_0_0__work_task.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_work_task")
                .contains("UNIQUE KEY uk_work_task_scope_id (system_id, tenant_id, id)")
                .contains("chk_work_task_status")
                .contains("status IN ('OPEN', 'COMPLETED')")
                .contains("chk_work_task_version CHECK (version > 0)")
                .contains("idx_work_task_assignee_state")
                .contains("idx_work_task_creator_state");
    }

    @Test
    void projectMigrationAddsRestrictiveProjectsSharedMetadataAndPermissions()
            throws Exception {
        var sql = Files.readString(
                findMigration("V8_46_0__work_project_task_views.sql"));
        assertThat(sql)
                .contains("CREATE TABLE un_work_project")
                .contains("CREATE TABLE un_work_project_member")
                .contains("status IN ('ACTIVE', 'ARCHIVED')")
                .contains("role IN ('OWNER', 'MEMBER')")
                .contains("status IN ('ACTIVE', 'REMOVED')")
                .contains("ADD COLUMN project_id BIGINT UNSIGNED NULL")
                .contains("ADD COLUMN description VARCHAR(2000) NULL")
                .contains("ADD COLUMN due_at DATETIME(6) NULL")
                .contains("idx_work_task_project_state")
                .contains("idx_work_task_project_due")
                .contains("idx_work_task_due_window")
                .contains("ON DELETE RESTRICT")
                .contains("'work.project.access'")
                .contains("'work.project.create'")
                .contains("'work.project.manage'")
                .contains("permission_version=epoch_row.epoch")
                .doesNotContain("ON DELETE CASCADE");
        assertThat(count(sql, "'work.project.access'"))
                .isGreaterThanOrEqualTo(2);
        assertThat(count(sql, "'work.project.create'"))
                .isGreaterThanOrEqualTo(2);
        assertThat(count(sql, "'work.project.manage'"))
                .isGreaterThanOrEqualTo(2);
    }

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length())
                / needle.length();
    }

    private static Path findMigration(String name) {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql").resolve("migration").resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate migration " + name);
    }
}
