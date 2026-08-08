package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalCompletionStep;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowSubflowMigrationContractTest {
    @Test
    void createsRestrictiveScopedRunsAndRunningExecutionState()
            throws Exception {
        var sql = migration();

        assertThat(sql)
                .contains("'subflow'")
                .contains("'running'")
                .contains("drop check ck_flow_instance_start_context")
                .contains("'$.rootinstanceid'")
                .contains("'$.subflowdepth'")
                .contains("json_length(start_context) = 6")
                .contains("between 1 and 8")
                .contains("json_extract(config_json, '$.definitionid') is not null")
                .contains("json_extract(config_json, '$.version') is not null")
                .contains("json_type(json_extract( config_json, '$.definitionid')) in ('integer', 'unsigned integer')")
                .contains("json_type(json_extract( config_json, '$.version')) = 'integer'")
                .contains("create table un_flow_subflow_run")
                .contains("unique key uk_flow_subflow_execution_attempt")
                .contains("unique key uk_flow_subflow_launch_key")
                .contains("unique key uk_flow_subflow_child")
                .contains("subflow_depth between 1 and 8")
                .contains("key idx_flow_subflow_running")
                .contains("key idx_flow_subflow_result_due")
                .contains("'activated', 'started', 'claimed'")
                .contains("on delete restrict")
                .doesNotContain("on delete cascade");
    }

    @Test
    void jdbcQueriesSeparateUnlockedCandidatesFromOrderedLocks() {
        assertThat(JdbcApprovalCompletionSql.SELECT_DUE_SUBFLOW)
                .contains("system_id=? AND tenant_id=?")
                .contains("execution_type='SUBFLOW'")
                .doesNotContain("FOR UPDATE");
        assertThat(JdbcApprovalCompletionSql.SELECT_PENDING_SUBFLOW_RESULTS)
                .contains("result_applied_at IS NULL")
                .doesNotContain("FOR UPDATE");
        assertThat(JdbcApprovalCompletionSql.SELECT_PENDING_SUBFLOW_RESULTS_FOR_UPDATE)
                .contains("FOR UPDATE SKIP LOCKED");
        assertThat(JdbcApprovalCompletionSql.UPDATE_SUBFLOW_RUN)
                .contains("state_version=?");

        var serialized = JdbcApprovalRepository.completionSteps(List.of(
                ApprovalCompletionStep.subflow(
                        "child", "Child",
                        new ApprovalCompletionStep.Subflow(102L, 7))));
        assertThat(serialized)
                .contains("\"type\":\"SUBFLOW\"")
                .contains("\"definitionId\":102")
                .contains("\"version\":7");
    }

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                        "..", "..", "sql", "migration",
                        "V8_43_0__flow_subflow_completion.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
