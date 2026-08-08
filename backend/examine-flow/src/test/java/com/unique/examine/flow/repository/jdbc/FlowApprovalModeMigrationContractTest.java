package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalTaskStatus;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowApprovalModeMigrationContractTest {
    @Test
    void migrationAndRepositoryPersistModesDecisionsAndEligibleTaskQueries()
            throws Exception {
        var migration = Files.readString(Path.of(
                "..",
                "..",
                "sql",
                "migration",
                "V8_30_0__flow_approval_modes.sql"
        ));

        assertThat(migration).contains(
                "approval_mode IN ('SEQUENTIAL', 'ANY', 'ALL')",
                "approved_approver_ids_json JSON NOT NULL",
                "rejected_approver_ids_json JSON NOT NULL",
                "to_status IN ('PENDING', 'REJECTED')"
        );
        for (var sql : List.of(
                JdbcApprovalSql.INSERT_DRAFT,
                JdbcApprovalSql.UPDATE_DRAFT,
                JdbcApprovalSql.SELECT_DRAFT,
                JdbcApprovalSql.INSERT_VERSION,
                JdbcApprovalSql.SELECT_VERSION,
                JdbcApprovalSql.SELECT_LATEST_VERSION,
                JdbcApprovalSql.SELECT_STARTABLE_DEFINITIONS,
                JdbcApprovalSql.SELECT_TRIGGER_CANDIDATES,
                JdbcApprovalSql.INSERT_INSTANCE,
                JdbcApprovalSql.UPDATE_INSTANCE_DECISION,
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES
        )) {
            assertThat(sql).contains("approval_mode");
        }
        assertThat(JdbcApprovalSql.INSERT_INSTANCE).contains(
                "approved_approver_ids_json",
                "rejected_approver_ids_json"
        );
        assertThat(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.PENDING))
                .contains(
                        "JSON_CONTAINS(approver_ids_json",
                        "JSON_CONTAINS(approved_approver_ids_json",
                        "JSON_CONTAINS(rejected_approver_ids_json"
                );
    }
}
