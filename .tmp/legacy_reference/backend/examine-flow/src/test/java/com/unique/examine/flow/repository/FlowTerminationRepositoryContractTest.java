package com.unique.examine.flow.repository;

import com.unique.examine.flow.domain.ApprovalDefinitionVersion;
import com.unique.examine.flow.domain.ApprovalInstance;
import com.unique.examine.flow.domain.ApprovalTaskStatus;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalSql;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTerminationRepositoryContractTest {
    @Test
    void optimisticSaveSqlKeepsPendingAndStateVersionCasForTermination() {
        var sql = JdbcApprovalSql.UPDATE_INSTANCE_DECISION
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", " ");

        assertThat(sql)
                .contains("set approver_id=?,approver_ids_json=?,current_step_index=?,claim_state=?")
                .contains("status=?,completion_phase=?,active_completion_ordinal=?")
                .contains("state_version=?,completed_at=?")
                .contains("system_id=? and tenant_id=? and instance_id=?")
                .contains("status='pending' and state_version=?");
        assertThat(JdbcApprovalSql.INSERT_HISTORY)
                .contains("event_type", "actor_id", "from_status", "to_status", "comment");
    }

    @Test
    void terminatedInstanceLeavesPendingProjectionAndMayAppearCompleted() {
        var repository = new InMemoryApprovalRepository();
        var now = Instant.parse("2026-07-27T09:00:00Z");
        var definition = new ApprovalDefinitionVersion(1, 1, "Approval", 20, 1, now);
        var pending = repository.saveInstance(
                ApprovalInstance.start(2, definition, "expense-001", 10, now));
        repository.saveInstance(pending.terminate(99, "duplicate", now.plusSeconds(60)));

        assertThat(repository.findApprovalTasks(20, ApprovalTaskStatus.PENDING, 0, 20)).isEmpty();
        assertThat(repository.countApprovalTasks(20, ApprovalTaskStatus.PENDING)).isZero();
        assertThat(repository.findApprovalTasks(20, ApprovalTaskStatus.COMPLETED, 0, 20))
                .extracting(ApprovalInstance::status)
                .containsExactly(ApprovalInstance.Status.TERMINATED);
    }
}
