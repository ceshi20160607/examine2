package com.unique.examine.flow.repository;

import com.unique.examine.flow.repository.jdbc.JdbcApprovalSql;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowAssignmentRepositoryContractTest {
    @Test
    void assignmentUsesTheExistingPendingStateVersionCasAndWritesRuntimeSequence() {
        var update = normalize(JdbcApprovalSql.UPDATE_INSTANCE_DECISION);

        assertThat(update)
                .contains(
                        "set approver_id=?,approver_ids_json=?,current_step_index=?,claim_state=?",
                        "status=?,completion_phase=?,active_completion_ordinal=?",
                        "state_version=?,completed_at=?",
                        "where system_id=? and tenant_id=? and instance_id=?",
                        "status='pending' and state_version=?");
        assertThat(normalize(JdbcApprovalSql.INSERT_INSTANCE))
                .contains(
                        "approver_id,approver_ids_json,current_step_index,claim_state",
                        "status,completion_phase,completion_failure_policy,active_completion_ordinal",
                        "state_version,started_at,completed_at"
                );
    }

    @Test
    void everyInstanceReadUsesRuntimeSnapshotAndHistoryPersistsAssignmentFacts() {
        assertThat(List.of(
                JdbcApprovalSql.SELECT_INSTANCE,
                JdbcApprovalSql.SELECT_INSTANCES,
                JdbcApprovalSql.SELECT_APPROVAL_TASKS
        )).allSatisfy(sql -> assertThat(normalize(sql))
                .contains("approver_ids_json as approver_ids")
                .doesNotContain("un_flow_definition_version_step"));
        assertThat(normalize(JdbcApprovalSql.INSERT_HISTORY))
                .contains("target_member_id", "assignment_position", "target_step_index");
        assertThat(normalize(JdbcApprovalSql.SELECT_HISTORY))
                .contains("target_member_id", "assignment_position", "target_step_index");
    }

    private static String normalize(String value) {
        return value.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }
}
