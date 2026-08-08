package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowQuorumApprovalMigrationContractTest {
    @Test
    void storesDefinitionRulesAndRuntimeRequiredCountSnapshots() throws Exception {
        var sql = Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_34_0__flow_quorum_approval.sql"
        ));

        assertThat(sql).contains(
                "approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL', 'QUORUM')",
                "ADD COLUMN quorum_rules JSON NULL",
                "ADD COLUMN required_approvals TINYINT UNSIGNED NOT NULL",
                "UPDATE un_flow_instance",
                "UPDATE un_flow_parallel_branch_execution"
        );
        for (var statement : List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION
        )) {
            assertThat(statement).contains("quorum_rules");
        }
        for (var statement : List.of(
                JdbcApprovalSql.INSERT_INSTANCE,
                JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.INSERT_PARALLEL_BRANCH,
                JdbcApprovalSql.SELECT_PARALLEL_BRANCHES
        )) {
            assertThat(statement).contains("required_approvals");
        }
        assertThat(JdbcApprovalSql.SELECT_APPROVAL_TASKS)
                .contains("approval_mode IN ('ANY','ALL','QUORUM')");
    }
}
