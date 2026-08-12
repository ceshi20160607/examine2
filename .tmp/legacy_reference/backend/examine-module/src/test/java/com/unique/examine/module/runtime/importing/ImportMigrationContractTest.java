package com.unique.examine.module.runtime.importing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ImportMigrationContractTest {
    @Test
    void migrationOwnsDurableBatchRowsAndIndependentPermission() throws Exception {
        var sql = Files.readString(Path.of("..", "..", "sql", "migration", "V8_22_0__module_import_job.sql"));

        assertThat(sql).contains("CREATE TABLE un_module_import_batch")
                .contains("CREATE TABLE un_module_import_row")
                .contains("preview_job_id BIGINT NOT NULL")
                .contains("target_before_json JSON NULL")
                .contains("target_after_version BIGINT NULL")
                .contains("CONCAT('module.', m.module_code, '.import')")
                .contains("'PREVIEWING'")
                .doesNotContain("DROP TABLE");
    }
}
