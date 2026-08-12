package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowDecisionCommentMigrationContractTest {
    @Test
    void storesDefinitionAndRuntimeDecisionCommentSnapshots() throws Exception {
        var sql = Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_36_0__flow_decision_comment_rules.sql"
        ));

        assertThat(sql).contains(
                "ADD COLUMN decision_comment_policies JSON NULL",
                "ADD COLUMN decision_comment_policy JSON NULL",
                "ck_flow_draft_decision_comment_policies",
                "ck_flow_version_decision_comment_policies",
                "ck_flow_instance_decision_comment_policy",
                "ck_flow_branch_decision_comment_policy"
        );
        for (var statement : List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION
        )) {
            assertThat(statement).contains("decision_comment_policies");
        }
        for (var statement : List.of(
                JdbcApprovalSql.INSERT_INSTANCE,
                JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.INSERT_PARALLEL_BRANCH,
                JdbcApprovalSql.SELECT_PARALLEL_BRANCHES
        )) {
            assertThat(statement).contains("decision_comment_policy");
        }
    }
}
