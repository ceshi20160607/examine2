package com.unique.examine.work.adapter.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WorkDailyReportMigrationContractTest {
    @Test
    void migrationFreezesIdentityBoundsStateIndexesAndPermissions()
            throws Exception {
        var sql = Files.readString(findMigration());

        assertThat(sql)
                .contains("CREATE TABLE un_work_daily_report")
                .contains("UNIQUE KEY uk_work_daily_report_author_date")
                .contains("idx_work_daily_report_list")
                .contains("idx_work_daily_report_member_list")
                .contains("idx_work_daily_report_status_list")
                .contains("BETWEEN 1 AND 4000")
                .contains("status IN ('DRAFT', 'SUBMITTED')")
                .contains("fk_work_daily_report_author")
                .contains("ON DELETE RESTRICT")
                .contains("'work.report.access'")
                .contains("'work.report.create'")
                .contains("'work.report.manage'")
                .contains("SET epoch=epoch+1")
                .doesNotContain("ON DELETE CASCADE");
        assertThat(count(sql, "UPDATE un_plat_authz_epoch")).isEqualTo(1);
    }

    private static Path findMigration() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql/migration")
                    .resolve("V8_47_0__work_daily_report.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate V8.47 migration");
    }

    private static int count(String value, String needle) {
        return (value.length() - value.replace(needle, "").length())
                / needle.length();
    }
}
