package com.unique.examine.module.runtime.exporting;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ExportMigrationContractTest {
    @Test
    void migrationOwnsDurableResultAndIndependentPermission() throws Exception {
        var sql = Files.readString(Path.of("..", "..", "sql", "migration",
                "V8_23_0__module_xlsx_export_job.sql"));

        assertThat(sql).contains("CREATE TABLE un_module_export_task")
                .contains("query_json JSON NOT NULL")
                .contains("permission_snapshot_json JSON NOT NULL")
                .contains("result_content LONGBLOB NULL")
                .contains("CONCAT('module.', m.module_code, '.export')")
                .contains("processed_rows BETWEEN 0 AND 5000")
                .doesNotContain("DROP TABLE");
    }
}
