package com.unique.examine.module.manage.ai;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AiConfigurationArtifactSuggestionMigrationContractTest {
    private static final String MIGRATION =
            "V8_76_0__ai_configuration_artifact_suggestions.sql";

    @Test
    void migrationOnlyExpandsTheExistingArtifactKindCheck() throws Exception {
        var sql = Files.readString(findMigration()).toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("alter table un_ai_config_artifact_proposal")
                .contains("drop check ck_ai_config_artifact_kind")
                .contains("add constraint ck_ai_config_artifact_kind check")
                .contains("'selection_field'", "'page_layout'",
                        "'filter_scenario'", "'field_permission_stage'")
                .doesNotContain("create table", "add column", "publish", "grant");
    }

    private static Path findMigration() {
        var current = Path.of("").toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve("sql").resolve("migration")
                    .resolve(MIGRATION);
            if (Files.isRegularFile(candidate)) return candidate;
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate migration " + MIGRATION);
    }
}
