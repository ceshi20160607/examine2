package com.unique.examine.flow.repository.jdbc;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class FlowCompletionMigrationContractTest {
    @Test
    void createsImmutableScopedCompletionExecutionsAndAttempts()
            throws Exception {
        var sql = migration();

        assertThat(sql)
                .contains("add column completion_steps json null")
                .contains("completion_phase varchar(20)")
                .contains("'human_approval', 'external_execution'")
                .contains("create table un_flow_completion_execution")
                .contains("unique key uk_flow_completion_instance_ordinal")
                .contains("unique key uk_flow_completion_one_active")
                .contains("key idx_flow_completion_due")
                .contains("key idx_flow_completion_lease_due")
                .contains("create table un_flow_completion_attempt")
                .contains("unique key uk_flow_completion_attempt_sequence")
                .contains("unique key uk_flow_completion_attempt_idempotency")
                .contains("on delete restrict")
                .doesNotContain("idx_flow_completion_webhook_due")
                .doesNotContain("on delete cascade");
    }

    @Test
    void boundsLeasePayloadDiagnosticsAndWorkerPermission()
            throws Exception {
        var sql = migration();

        assertThat(sql)
                .contains("lease_token_hash regexp '^[0-9a-f]{64}$'")
                .contains("octet_length(payload_json) <= 65535")
                .contains("octet_length(result_json) <= 8192")
                .contains("http_status between 100 and 599")
                .contains("duration_ms <= 86400000")
                .contains("response_sha256 regexp '^[0-9a-f]{64}$'")
                .contains("'waiting', 'available', 'leased', 'retrying'")
                .contains("'flow.external-task.work'")
                .contains("event_type = 'completion_completed'");
    }

    @Test
    void jdbcQueriesKeepTenantScopeCasSkipLockedAndOptionalTopic() {
        assertThat(JdbcApprovalCompletionSql.SELECT_AVAILABLE_EXTERNAL)
                .contains("system_id=? AND tenant_id=?")
                .contains("? IS NULL")
                .contains("JSON_UNQUOTE(JSON_EXTRACT(config_json, '$.topic'))=?");
        assertThat(JdbcApprovalCompletionSql.UPDATE_EXECUTION)
                .contains("state_version=?");
        assertThat(JdbcApprovalCompletionSql.SELECT_DUE_WEBHOOK_FOR_UPDATE)
                .contains("FOR UPDATE SKIP LOCKED");
        assertThat(JdbcApprovalSql.INSERT_DRAFT)
                .contains("completion_steps");
        assertThat(JdbcApprovalSql.INSERT_INSTANCE)
                .contains("completion_phase", "active_completion_ordinal");
        assertThat(JdbcApprovalSql.UPDATE_INSTANCE_COMPLETION_PROGRESS)
                .contains("completion_phase=?")
                .contains("active_completion_ordinal=?")
                .contains("completion_phase IN ('EXTERNAL_EXECUTION','COMPENSATING')")
                .contains("state_version=?");
    }

    private static String migration() throws Exception {
        return Files.readString(Path.of(
                        "..", "..", "sql", "migration",
                        "V8_42_0__flow_completion_executions.sql"))
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .trim();
    }
}
