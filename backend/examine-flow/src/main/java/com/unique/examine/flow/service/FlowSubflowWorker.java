package com.unique.examine.flow.service;

import com.unique.examine.flow.repository.ApprovalRepository;
import com.unique.examine.flow.repository.jdbc.JdbcApprovalRepositoryFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.function.BiFunction;

/** Restart-safe launcher, terminal observer and result reconciliation poller. */
@Component
public final class FlowSubflowWorker {
    private static final Logger LOGGER =
            LoggerFactory.getLogger(FlowSubflowWorker.class);
    private static final String SELECT_DUE_SCOPES = """
            SELECT work.system_id,work.tenant_id
            FROM (
                SELECT system_id,tenant_id,available_at AS due_at
                FROM un_flow_completion_execution
                WHERE execution_type='SUBFLOW'
                  AND status IN ('AVAILABLE','RETRYING')
                  AND available_at<=?
                UNION ALL
                SELECT system_id,tenant_id,launched_at AS due_at
                FROM un_flow_subflow_run
                WHERE child_status='RUNNING'
                   OR terminal_at IS NOT NULL
                      AND result_applied_at IS NULL
            ) work
            GROUP BY work.system_id,work.tenant_id
            ORDER BY MIN(work.due_at),work.system_id,work.tenant_id
            LIMIT ?
            """;
    public static final int DEFAULT_BATCH_SIZE = 20;

    private final JdbcTemplate jdbc;
    private final BiFunction<Long, Long, ApprovalRepository> repositories;
    private final FlowSubflowCoordinator runtime;
    private final Clock clock;

    @Autowired
    public FlowSubflowWorker(
            JdbcTemplate jdbc,
            JdbcApprovalRepositoryFactory repositories,
            FlowSubflowCoordinator runtime
    ) {
        this(
                jdbc,
                (systemId, tenantId) -> repositories.forTenant(
                        systemId, tenantId),
                runtime,
                Clock.systemUTC());
    }

    FlowSubflowWorker(
            JdbcTemplate jdbc,
            BiFunction<Long, Long, ApprovalRepository> repositories,
            FlowSubflowCoordinator runtime,
            Clock clock
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.repositories = Objects.requireNonNull(
                repositories, "repositories");
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Scheduled(
            initialDelayString =
                    "${examine.flow.subflow.initial-delay-ms:5000}",
            fixedDelayString =
                    "${examine.flow.subflow.poll-delay-ms:1000}"
    )
    public void poll() {
        pollOnce(DEFAULT_BATCH_SIZE);
    }

    public int pollOnce(int limit) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Subflow poll limit must be 1..100");
        }
        var now = clock.instant();
        var scopes = jdbc.query(
                SELECT_DUE_SCOPES,
                (result, row) -> new Scope(
                        result.getLong("system_id"),
                        result.getLong("tenant_id")),
                Timestamp.from(now), limit);
        var processed = 0;
        for (var scope : scopes) {
            try {
                var repository = repositories.apply(
                        scope.systemId(), scope.tenantId());
                var due = repository.findDueSubflowExecutions(
                        now, limit - processed);
                for (var execution : due) {
                    if (runtime.launch(
                            scope.systemId(), scope.tenantId(),
                            execution.id())) {
                        processed++;
                    }
                    if (processed >= limit) {
                        return processed;
                    }
                }

                var running = repository.findRunningSubflowRuns(
                        limit - processed);
                for (var run : running) {
                    if (runtime.reconcile(
                            scope.systemId(), scope.tenantId(),
                            run.executionId(), run.attemptNumber())) {
                        processed++;
                    }
                    if (processed >= limit) {
                        return processed;
                    }
                }

                var pending = repository.findPendingSubflowResults(
                        limit - processed);
                for (var run : pending) {
                    if (runtime.reconcile(
                            scope.systemId(), scope.tenantId(),
                            run.executionId(), run.attemptNumber())) {
                        processed++;
                    }
                    if (processed >= limit) {
                        return processed;
                    }
                }
            } catch (RuntimeException failure) {
                LOGGER.warn(
                        "Subflow processing failed for system={}, tenant={}",
                        scope.systemId(), scope.tenantId(), failure);
            }
        }
        return processed;
    }

    private record Scope(long systemId, long tenantId) {
    }
}
