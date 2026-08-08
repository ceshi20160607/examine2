package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDefinitionRecordStatusMappingMigrationContractTest {
    private static final List<String> MAPPING_COLUMNS = List.of(
            "status_field_code",
            "status_approved_value",
            "status_rejected_value",
            "status_withdrawn_value",
            "status_terminated_value"
    );

    @Test
    void migrationAddsMatchingAllNullOrAllPresentDraftAndVersionSnapshots()
            throws IOException {
        var migration = normalize(Files.readString(findMigration()));

        assertThat(migration).contains(
                "alter table un_flow_definition_draft",
                "alter table un_flow_definition_version",
                "constraint ck_flow_draft_record_status_mapping check",
                "constraint ck_flow_version_record_status_mapping check",
                "status_field_code regexp '^[a-za-z][a-za-z0-9_]{0,63}$'",
                "status_approved_value regexp '^[1-9][0-9]{0,18}$'",
                "status_rejected_value regexp '^[1-9][0-9]{0,18}$'",
                "status_withdrawn_value regexp '^[1-9][0-9]{0,18}$'",
                "status_terminated_value regexp '^[1-9][0-9]{0,18}$'"
        );
        assertThat(count(migration, "status_field_code is null")).isEqualTo(2);
        assertThat(count(migration, "status_field_code is not null")).isEqualTo(2);
        for (var column : MAPPING_COLUMNS) {
            assertThat(migration).contains("add column " + column);
        }
    }

    @Test
    void everyDefinitionDraftAndVersionWriteAndReadCarriesTheMappingColumns() {
        assertThat(List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.SELECT_DRAFTS,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION,
                JdbcApprovalSql.SELECT_LATEST_VERSION,
                JdbcApprovalSql.SELECT_TRIGGER_CANDIDATES
        )).allSatisfy(sql -> assertThat(normalize(sql))
                .contains(MAPPING_COLUMNS.toArray(String[]::new)));
    }

    private static Path findMigration() {
        var current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (current != null) {
            var candidate = current.resolve(
                    "sql/migration/V8_19_0__flow_definition_record_status_mapping.sql"
            );
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Cannot locate the Flow definition status mapping migration");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private static int count(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
