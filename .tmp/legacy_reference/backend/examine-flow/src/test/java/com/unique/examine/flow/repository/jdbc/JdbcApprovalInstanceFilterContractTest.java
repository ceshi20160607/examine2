package com.unique.examine.flow.repository.jdbc;

import com.unique.examine.flow.domain.ApprovalInstance;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcApprovalInstanceFilterContractTest {
    @Test
    void bindsScopedStatusAndInclusiveExclusiveCompletionFiltersForPageAndCount() {
        var jdbc = new RecordingJdbcTemplate();
        var repository = new JdbcApprovalRepository(
                jdbc, transactions(), new FlowTenantScope(10, 20));
        var from = Instant.parse("2026-08-01T00:00:00Z");
        var to = Instant.parse("2026-08-02T00:00:00Z");

        repository.findInstances(
                ApprovalInstance.Status.REJECTED, from, to, 40, 20);

        assertThat(normalize(jdbc.sql))
                .contains("WHERE system_id=? AND tenant_id=?")
                .contains("(? IS NULL OR status=?)")
                .contains("(? IS NULL OR completed_at>=?)")
                .contains("(? IS NULL OR completed_at<?)")
                .contains("LIMIT ? OFFSET ?");
        assertThat(jdbc.arguments).containsExactly(
                10L, 20L,
                "REJECTED", "REJECTED",
                Timestamp.from(from), Timestamp.from(from),
                Timestamp.from(to), Timestamp.from(to),
                20, 40);

        assertThat(repository.countInstances(
                ApprovalInstance.Status.REJECTED, from, to)).isEqualTo(1L);
        assertThat(normalize(jdbc.sql))
                .startsWith("SELECT COUNT(*) FROM un_flow_instance")
                .contains("WHERE system_id=? AND tenant_id=?")
                .doesNotContain("LIMIT", "OFFSET");
        assertThat(jdbc.arguments).containsExactly(
                10L, 20L,
                "REJECTED", "REJECTED",
                Timestamp.from(from), Timestamp.from(from),
                Timestamp.from(to), Timestamp.from(to));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static PlatformTransactionManager transactions() {
        return new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return new SimpleTransactionStatus();
            }

            @Override
            public void commit(TransactionStatus status) {
            }

            @Override
            public void rollback(TransactionStatus status) {
            }
        };
    }

    private static final class RecordingJdbcTemplate extends JdbcTemplate {
        private String sql;
        private Object[] arguments;

        @Override
        public <T> List<T> query(
                String sql,
                RowMapper<T> rowMapper,
                Object... arguments
        ) {
            this.sql = sql;
            this.arguments = arguments;
            return List.of();
        }

        @Override
        public <T> T queryForObject(
                String sql,
                Class<T> requiredType,
                Object... arguments
        ) {
            this.sql = sql;
            this.arguments = arguments;
            return requiredType.cast(1L);
        }
    }
}
