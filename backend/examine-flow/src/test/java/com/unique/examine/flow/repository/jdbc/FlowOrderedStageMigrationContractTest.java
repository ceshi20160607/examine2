package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowOrderedStageMigrationContractTest {
    @Test
    void migrationAddsNullableLegacySnapshotsAndBoundedStageCursor() throws Exception {
        var migration = normalize(Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_39_0__flow_ordered_stages.sql"
        )));

        assertThat(migration).contains(
                "alter table un_flow_definition_draft",
                "add column approval_stages json null",
                "alter table un_flow_definition_version",
                "json_length(approval_stages) between 2 and 10",
                "alter table un_flow_instance",
                "add column approval_stage_state json null",
                "add column current_stage_index int unsigned not null default 0",
                "approval_stage_state is null and current_stage_index = 0",
                "json_length(approval_stage_state) between 1 and 10",
                "current_stage_index < json_length(approval_stage_state)"
        );
        assertThat(count(migration, "octet_length(")).isEqualTo(3);
    }

    @Test
    void everyDefinitionAndInstanceSnapshotSqlCarriesOrderedStageState() {
        for (var sql : List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.SELECT_DRAFTS,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION,
                JdbcApprovalSql.SELECT_LATEST_VERSION,
                JdbcApprovalSql.SELECT_VERSIONS,
                JdbcApprovalSql.SELECT_STARTABLE_DEFINITIONS,
                JdbcApprovalSql.SELECT_TRIGGER_CANDIDATES
        )) {
            assertThat(normalize(sql)).contains("approval_stages");
        }
        for (var sql : List.of(
                JdbcApprovalSql.INSERT_INSTANCE,
                JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES,
                JdbcApprovalSql.SELECT_APPROVAL_TASKS,
                JdbcApprovalSql.SELECT_CLAIMABLE_TASKS
        )) {
            assertThat(normalize(sql)).contains(
                    "approval_stage_state",
                    "current_stage_index"
            );
        }
        assertThat(normalize(JdbcApprovalSql.SELECT_INSTANCE_FOR_UPDATE))
                .endsWith("for update");
        assertThat(normalize(JdbcApprovalSql.SELECT_PARALLEL_BRANCHES))
                .contains("approval_stage_state", "current_stage_index");
    }

    private static int count(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }

    private static String normalize(String value) {
        return value.toLowerCase().replaceAll("\\s+", " ").trim();
    }
}
