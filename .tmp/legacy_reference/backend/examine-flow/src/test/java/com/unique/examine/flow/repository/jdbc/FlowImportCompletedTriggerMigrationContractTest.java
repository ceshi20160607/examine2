package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowImportCompletedTriggerMigrationContractTest {
    @Test
    void migrationAddsImportCompletedToBothImmutableTriggerSnapshots() throws Exception {
        var sql = Files.readString(Path.of("..", "..", "sql", "migration",
                "V8_24_0__flow_import_completed_trigger.sql"))
                .toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        assertThat(sql).contains("drop check ck_flow_draft_trigger_binding")
                .contains("drop check ck_flow_version_trigger_binding")
                .contains("'import_completed'")
                .doesNotContain("drop table");
        assertThat(count(sql, "'import_completed'")).isEqualTo(2);
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
