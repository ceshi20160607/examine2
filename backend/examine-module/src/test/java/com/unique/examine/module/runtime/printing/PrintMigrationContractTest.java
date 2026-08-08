package com.unique.examine.module.runtime.printing;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PrintMigrationContractTest {
    @Test
    void migrationOwnsVersionedTemplatesDurablePdfHistoryAndPermission() throws Exception {
        var sql = Files.readString(Path.of("..", "..", "sql", "migration",
                "V8_25_0__module_print_template_pdf.sql"));

        assertThat(sql).contains("CREATE TABLE un_module_print_template (")
                .contains("CREATE TABLE un_module_print_template_version (")
                .contains("CREATE TABLE un_module_print_task (")
                .contains("definition_checksum CHAR(64) NOT NULL")
                .contains("snapshot_json JSON NOT NULL")
                .contains("result_content LONGBLOB NULL")
                .contains("CONCAT('module.', m.module_code, '.print')")
                .contains("fk_print_version_schema")
                .doesNotContain("DROP TABLE");
    }

    @Test
    void cycle116MigrationIndexesCanonicalFieldReferencesAndPrintHistoryWithoutParallelTables() throws Exception {
        var sql = Files.readString(Path.of("..", "..", "sql", "migration",
                "V8_90_0__file_field_and_print_composition.sql"));

        assertThat(sql).contains("idx_file_reference_field_target")
                .contains("ON un_file_reference")
                .contains("idx_module_print_task_record_history")
                .contains("ON un_module_print_task")
                .contains("DROP CHECK ck_rv_supported_type")
                .contains("'ATTACHMENT', 'IMAGE', 'FILE_GROUP', 'SIGNATURE'")
                .contains("AND reference_value IS NOT NULL")
                .doesNotContain("CREATE TABLE")
                .doesNotContain("DROP TABLE");
    }
}
