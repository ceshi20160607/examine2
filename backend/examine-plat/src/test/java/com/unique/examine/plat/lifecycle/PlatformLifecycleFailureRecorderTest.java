package com.unique.examine.plat.lifecycle;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.OperationAudit;
import com.unique.examine.core.api.OperationAuditFacade;
import com.unique.examine.core.context.ContextType;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;
import com.unique.examine.plat.manage.service.SystemAdminMutationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlatformLifecycleFailureRecorderTest {
    @Test
    void failedExecutionLedgerHasOneBoundArgumentPerSqlPlaceholder() {
        var jdbc = new CapturingJdbc();
        var ids = new IdService() { @Override public long nextId() { return 99; } };
        var recorder = new PlatformLifecycleFailureRecorder(jdbc, new ObjectMapper(), ids,
                new SystemAdminMutationSupport(null, noOpAudits(), null, new ObjectMapper()));
        var session = new AuthenticatedSession(1, 2, ContextType.SYSTEM, 10L, 20L, 3L, 1, Set.of());

        recorder.record(session, 10, 20, "21", "30", "MIGRATION", "TENANT_DATA_IMPACT_CHANGED",
                new ClientRequest("request-1", "trace-1", "127.0.0.1", "test"));

        assertThat(jdbc.insertSql).contains("operation_type,status", "'FAILED'");
        assertThat(jdbc.insertSql.chars().filter(value -> value == '?').count()).isEqualTo(19);
        assertThat(jdbc.insertArguments).hasSize(19);
        assertThat(jdbc.insertArguments[3]).isEqualTo(21L);
        assertThat(jdbc.insertArguments[4]).isEqualTo(30L);
    }

    private static OperationAuditFacade noOpAudits() {
        return new OperationAuditFacade() {
            @Override public void recordSuccess(OperationAudit audit) { }
            @Override public void recordDenied(OperationAudit audit) { }
            @Override public void recordFailed(OperationAudit audit) { }
        };
    }

    private static final class CapturingJdbc extends JdbcTemplate {
        private String insertSql;
        private Object[] insertArguments;

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... args) {
            return List.of(Map.of(
                    "target_tenant_id", 21L,
                    "plan_fingerprint", "a".repeat(64),
                    "database_migration_version", "8.86.0",
                    "payload_row_count", 4L,
                    "payload_size_bytes", 512L));
        }

        @Override
        public int update(String sql, Object... args) {
            insertSql = sql;
            insertArguments = args;
            return 1;
        }
    }
}
