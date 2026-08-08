package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowConditionalGatewayMigrationContractTest {
    @Test
    void migrationAndRepositoryPersistDraftAndImmutableVersionGatewaySnapshots()
            throws Exception {
        var migration = Files.readString(Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_29_0__flow_conditional_gateway.sql"
        ));

        assertThat(migration)
                .contains(
                        "ALTER TABLE un_flow_definition_draft",
                        "ALTER TABLE un_flow_definition_version",
                        "gateway_branches JSON NULL",
                        "JSON_TYPE(gateway_branches) = 'ARRAY'",
                        "JSON_LENGTH(gateway_branches) BETWEEN 2 AND 6"
                );
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
            assertThat(sql).contains("gateway_branches");
        }
    }
}
