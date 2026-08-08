package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowInclusiveGatewayMigrationContractTest {
    @Test
    void migrationAddsBoundedMutuallyExclusiveInclusiveSnapshots() throws Exception {
        var migration = Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_32_0__flow_inclusive_gateway.sql"
        );
        var sql = Files.readString(migration);

        assertThat(sql).contains(
                "ADD COLUMN inclusive_branches JSON NULL",
                "JSON_LENGTH(inclusive_branches) BETWEEN 2 AND 5",
                "gateway_branches IS NULL AND parallel_branches IS NULL"
        );
        assertThat(sql.split("ADD COLUMN inclusive_branches JSON NULL", -1)).hasSize(3);
        assertThat(JdbcApprovalSql.INSERT_DRAFT).contains("inclusive_branches");
        assertThat(JdbcApprovalSql.INSERT_VERSION).contains("inclusive_branches");
        assertThat(JdbcApprovalSql.SELECT_DRAFT).contains("inclusive_branches");
        assertThat(JdbcApprovalSql.SELECT_VERSION).contains("inclusive_branches");
        assertThat(JdbcApprovalSql.INSERT_PARALLEL_BRANCH)
                .contains("un_flow_parallel_branch_execution");
    }
}
