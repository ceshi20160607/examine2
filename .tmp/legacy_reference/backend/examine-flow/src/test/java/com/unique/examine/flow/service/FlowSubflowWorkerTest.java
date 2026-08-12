package com.unique.examine.flow.service;

import com.unique.examine.flow.domain.ApprovalCompletionExecution;
import com.unique.examine.flow.domain.ApprovalCompletionStep;
import com.unique.examine.flow.repository.memory.InMemoryApprovalRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowSubflowWorkerTest {
    private static final Instant NOW =
            Instant.parse("2026-07-31T00:00:00Z");

    @Test
    void pollOnceDispatchesUnlockedCandidatesAndReplayDoesNoWork() {
        var dataSource = new JdbcDataSource();
        dataSource.setURL(
                "jdbc:h2:mem:subflow-worker;MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE un_flow_completion_execution (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    execution_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    available_at TIMESTAMP NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbc.execute("""
                CREATE TABLE un_flow_subflow_run (
                    system_id BIGINT NOT NULL,
                    tenant_id BIGINT NOT NULL,
                    child_status VARCHAR(32) NOT NULL,
                    launched_at TIMESTAMP NOT NULL,
                    terminal_at TIMESTAMP NULL,
                    result_applied_at TIMESTAMP NULL
                )
                """);
        jdbc.update("""
                INSERT INTO un_flow_completion_execution(
                    system_id,tenant_id,execution_type,status,
                    available_at,created_at
                ) VALUES (1,2,'SUBFLOW','AVAILABLE',?,?)
                """, java.sql.Timestamp.from(NOW),
                java.sql.Timestamp.from(NOW));

        var repository = new InMemoryApprovalRepository();
        var step = ApprovalCompletionStep.subflow(
                "child", "Child",
                new ApprovalCompletionStep.Subflow(9, 1));
        repository.materializeCompletionExecutions(List.of(
                ApprovalCompletionExecution.materialize(
                        100, 200, 300, 1, 0, step, "{}", NOW)));
        var launches = new AtomicInteger();
        FlowSubflowCoordinator coordinator = new FlowSubflowCoordinator() {
            @Override
            public boolean launch(
                    long systemId, long tenantId, long executionId
            ) {
                var current = repository.findCompletionExecution(executionId)
                        .orElseThrow();
                repository.saveCompletionExecution(
                        current.startSubflow(NOW));
                launches.incrementAndGet();
                return true;
            }

            @Override
            public boolean reconcile(
                    long systemId,
                    long tenantId,
                    long executionId,
                    int attempt
            ) {
                return false;
            }
        };
        var worker = new FlowSubflowWorker(
                jdbc,
                (systemId, tenantId) -> repository,
                coordinator,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThat(worker.pollOnce(10)).isEqualTo(1);
        assertThat(worker.pollOnce(10)).isZero();
        assertThat(launches).hasValue(1);
        assertThatThrownBy(() -> worker.pollOnce(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
