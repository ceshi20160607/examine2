package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalTaskStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowClaimRepositoryContractTest {
    @Test
    void instanceCasPersistsCursorClaimStateAndEveryProjectionReadsThem() {
        var update = normalize(JdbcApprovalSql.UPDATE_INSTANCE_DECISION);
        assertThat(update)
                .contains(
                        "current_step_index=?",
                        "claim_state=?",
                        "status='pending' and state_version=?"
                );

        assertThat(List.of(
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES,
                JdbcApprovalSql.SELECT_APPROVAL_TASKS,
                JdbcApprovalSql.SELECT_CLAIMABLE_TASKS
        )).allSatisfy(sql -> assertThat(normalize(sql))
                .contains("current_step_index", "claim_state"));
    }

    @Test
    void openTasksHaveStableClaimPoolAndNeverRemainInOrdinaryOwnerProjection() {
        assertThat(normalize(JdbcApprovalSql.SELECT_CLAIMABLE_TASKS))
                .contains(
                        "status='pending'",
                        "completion_phase='human_approval'",
                        "claim_state='open'",
                        "order by started_at desc,instance_id desc",
                        "limit ? offset ?"
                );
        assertThat(normalize(JdbcApprovalSql.COUNT_CLAIMABLE_TASKS))
                .contains(
                        "status='pending'",
                        "completion_phase='human_approval'",
                        "claim_state='open'");
        assertThat(normalize(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.PENDING)))
                .contains("status='pending' and claim_state='claimed'");
        assertThat(normalize(JdbcApprovalSql.approvalTasks(ApprovalTaskStatus.ALL)))
                .contains("status<>'pending' or claim_state='claimed'");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
