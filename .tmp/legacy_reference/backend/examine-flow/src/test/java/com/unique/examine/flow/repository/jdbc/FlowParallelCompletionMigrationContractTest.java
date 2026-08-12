package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalCompletionStep;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowParallelCompletionMigrationContractTest {
    @Test
    void freezesGroupIdentityAndRemovesTheSingleActiveAssumption()
            throws Exception {
        var sql = migration();

        assertThat(sql)
                .contains("add column parallel_group varchar(64)")
                .contains("drop index uk_flow_completion_one_active")
                .contains("idx_flow_completion_active_stage")
                .contains("idx_flow_completion_stage_join")
                .contains("parallel_group regexp '^[a-z][a-z0-9_]{0,63}$'")
                .contains("'stage_joined'")
                .doesNotContain("on delete cascade");
    }

    @Test
    void jdbcLocksAStageInStableOrderAndSerializesTheGroup() {
        assertThat(JdbcApprovalCompletionSql.SELECT_STAGE_FOR_UPDATE)
                .contains("system_id=? AND execution_row.tenant_id=?")
                .contains("ORDER BY execution_row.ordinal,execution_row.execution_id")
                .contains("FOR UPDATE");
        assertThat(JdbcApprovalCompletionSql.INSERT_EXECUTION)
                .contains("parallel_group");
        var serialized = JdbcApprovalRepository.completionSteps(List.of(
                step("first").withParallelGroup("publish"),
                step("second").withParallelGroup("publish")));
        assertThat(serialized)
                .contains("\"parallelGroup\":\"publish\"");
    }

    private static ApprovalCompletionStep step(String code) {
        return ApprovalCompletionStep.externalTask(
                code, code,
                new ApprovalCompletionStep.ExternalTask(
                        "topic." + code, 60, 3, 1024));
    }

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                        "..", "..", "sql", "migration",
                        "V8_44_0__flow_parallel_completion_join.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
