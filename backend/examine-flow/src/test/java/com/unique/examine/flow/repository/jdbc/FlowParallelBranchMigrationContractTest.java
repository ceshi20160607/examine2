package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalTaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowParallelBranchMigrationContractTest {
    @Test
    void migrationAndRepositoryPersistGraphBranchesAndBranchAwareTasks()
            throws Exception {
        var migration = Files.readString(Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_31_0__flow_parallel_branches.sql"
        ));

        assertThat(migration).contains(
                "parallel_branches JSON NULL",
                "JSON_LENGTH(parallel_branches) BETWEEN 2 AND 5",
                "CREATE TABLE un_flow_parallel_branch_execution",
                "'CANCELLED', 'WITHDRAWN', 'TERMINATED'",
                "fk_flow_parallel_branch_instance"
        );
        for (var sql : List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION,
                JdbcApprovalSql.SELECT_LATEST_VERSION,
                JdbcApprovalSql.SELECT_STARTABLE_DEFINITIONS,
                JdbcApprovalSql.SELECT_TRIGGER_CANDIDATES
        )) {
            assertThat(sql).contains("parallel_branches");
        }
        assertThat(JdbcApprovalSql.INSERT_PARALLEL_BRANCH).contains(
                "approver_ids_json",
                "approval_mode",
                "approved_approver_ids_json",
                "rejected_approver_ids_json"
        );
        assertThat(JdbcApprovalSql.UPDATE_PARALLEL_BRANCH).contains(
                "approver_ids_json=?",
                "approval_mode=?",
                "required_approvals=?",
                "WHERE system_id=? AND tenant_id=? AND instance_id=? AND branch_code=?"
        );
        assertThat(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.PENDING)).contains(
                "EXISTS (",
                "un_flow_parallel_branch_execution",
                "parallel_branch.status='PENDING'",
                "parallel_branch.approver_id=?"
        );
    }
}
