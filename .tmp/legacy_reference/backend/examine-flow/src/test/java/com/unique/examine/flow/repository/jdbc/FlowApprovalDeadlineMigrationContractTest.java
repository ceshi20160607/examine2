package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowApprovalDeadlineMigrationContractTest {
    @Test
    void storesImmutablePoliciesRuntimeSnapshotsAndBoundedWorkerIndexes()
            throws Exception {
        var sql = Files.readString(Path.of(
                "..", "..", "sql", "migration",
                "V8_35_0__flow_approval_deadlines.sql"
        ));

        assertThat(sql).contains(
                "ADD COLUMN deadline_policies JSON NULL",
                "ADD COLUMN deadline_policy JSON NULL",
                "ADD COLUMN deadline_remind_at DATETIME(6) NULL",
                "ADD COLUMN deadline_due_at DATETIME(6) NULL",
                "ADD COLUMN deadline_reminded_at DATETIME(6) NULL",
                "ADD COLUMN deadline_processed_at DATETIME(6) NULL",
                "ix_flow_instance_deadline_poll",
                "ix_flow_branch_deadline_poll",
                "MODIFY COLUMN event_type VARCHAR(32)",
                "'DEADLINE_REMINDER_SENT'",
                "'DEADLINE_OVERDUE'",
                "'DEADLINE_AUTO_APPROVED'",
                "'DEADLINE_AUTO_REJECTED'"
        );
        for (var statement : List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION
        )) {
            assertThat(statement).contains("deadline_policies");
        }
        for (var statement : List.of(
                JdbcApprovalSql.INSERT_INSTANCE,
                JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.INSERT_PARALLEL_BRANCH,
                JdbcApprovalSql.SELECT_PARALLEL_BRANCHES
        )) {
            assertThat(statement).contains(
                    "deadline_policy",
                    "deadline_remind_at",
                    "deadline_due_at",
                    "deadline_reminded_at",
                    "deadline_processed_at"
            );
        }
        assertThat(JdbcFlowDeadlineSql.SELECT_DUE).contains(
                "instance_row.deadline_processed_at IS NULL",
                "branch_row.deadline_processed_at IS NULL",
                "LIMIT ?"
        );
        assertThat(JdbcFlowDeadlineSql.LOCK_INSTANCE).contains("FOR UPDATE");
    }
}
