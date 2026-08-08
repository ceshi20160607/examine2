package com.unique.examine.module.runtime.service;

import com.unique.examine.core.api.RuntimeAuthorizationFacade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class ReferenceRecalculationWorker {
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceRecalculationWorker.class);

    private final JdbcTemplate jdbc;
    private final ReferenceMaterializationService references;
    private final DerivedMaterializationService derived;
    private final RecordMutationSupport mutations;
    private final RuntimeAuthorizationFacade authorization;
    private final TransactionTemplate transaction;
    private final int batchSize;

    public ReferenceRecalculationWorker(
            JdbcTemplate jdbc,
            ReferenceMaterializationService references,
            DerivedMaterializationService derived,
            RecordMutationSupport mutations,
            RuntimeAuthorizationFacade authorization,
            PlatformTransactionManager transactionManager,
            @Value("${examine.runtime.reference-recalculation.batch-size:50}") int batchSize
    ) {
        this.jdbc = jdbc;
        this.references = references;
        this.derived = derived;
        this.mutations = mutations;
        this.authorization = authorization;
        this.transaction = new TransactionTemplate(transactionManager);
        this.batchSize = Math.max(1, Math.min(batchSize, 200));
    }

    @Scheduled(
            initialDelayString = "${examine.runtime.reference-recalculation.initial-delay-ms:1000}",
            fixedDelayString = "${examine.runtime.reference-recalculation.fixed-delay-ms:1000}"
    )
    public int process() {
        var completed = 0;
        for (var index = 0; index < batchSize; index++) {
            try {
                var found = transaction.execute(status -> processOne());
                if (!Boolean.TRUE.equals(found)) {
                    break;
                }
                completed++;
            } catch (TaskFailure failure) {
                LOG.warn("Reference/derived recalculation failed for taskId={}, sourceRecordId={}, attempt={}",
                        failure.task().id(), failure.task().sourceRecordId(),
                        failure.task().attemptCount() + 1, failure.getCause());
                transaction.executeWithoutResult(status -> fail(failure.task(), LocalDateTime.now()));
                completed++;
            }
        }
        return completed;
    }

    private boolean processOne() {
        var now = LocalDateTime.now();
        var tasks = jdbc.query("SELECT id,system_id,tenant_id,source_record_id,source_record_version,attempt_count,"
                        + "correlation_id FROM un_module_reference_recalc_task WHERE status='PENDING' "
                        + "AND available_at<=? ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED",
                (row, number) -> new Task(row.getLong("id"), row.getLong("system_id"), row.getLong("tenant_id"),
                        row.getLong("source_record_id"), row.getLong("source_record_version"),
                        row.getInt("attempt_count"), row.getString("correlation_id")), now);
        if (tasks.isEmpty()) {
            return false;
        }
        var task = tasks.getFirst();
        try {
            jdbc.update("UPDATE un_module_reference_recalc_task SET status='RUNNING',updated_at=?,version=version+1 "
                    + "WHERE id=? AND status='PENDING'", now, task.id());
            processTask(task, now);
            pass(task.id(), now);
            return true;
        } catch (RuntimeException exception) {
            throw new TaskFailure(task, exception);
        }
    }

    private void processTask(Task task, LocalDateTime now) {
        var currentTargets = jdbc.query("SELECT record_id,schema_version_id,module_snapshot_id,version "
                        + "FROM un_module_record WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=?",
                (row, number) -> new ReferenceMaterializationService.DependentRecord(
                        task.systemId(), task.tenantId(), row.getLong("record_id"),
                        row.getLong("schema_version_id"), row.getLong("module_snapshot_id"),
                        row.getLong("version")), task.systemId(), task.tenantId(), task.sourceRecordId());
        if (currentTargets.size() != 1 || currentTargets.getFirst().version() != task.sourceRecordVersion()) {
            return;
        }
        var activeSchemaVersion = lockActiveSchemaVersion(task.systemId());
        if (activeSchemaVersion == null
                || currentTargets.getFirst().schemaVersionId() != activeSchemaVersion) {
            return;
        }
        var permissionEpoch = authorization.currentSystemEpoch(task.systemId());
        var affected = new LinkedHashMap<Long, ReferenceMaterializationService.DependentRecord>();
        affected.put(task.sourceRecordId(), currentTargets.getFirst());
        references.dependents(task.systemId(), task.tenantId(), task.sourceRecordId())
                .forEach(record -> affected.put(record.recordId(), record));
        for (var dependent : affected.values()) {
            if (dependent.schemaVersionId() != activeSchemaVersion) {
                continue;
            }
            requirePermissionEpoch(task.systemId(), permissionEpoch);
            var changed = references.recomputeParent(dependent.systemId(), dependent.tenantId(),
                    dependent.schemaVersionId(), dependent.moduleSnapshotId(), dependent.recordId(), now);
            changed |= derived.recomputeCross(dependent.systemId(), dependent.tenantId(),
                    dependent.schemaVersionId(), dependent.moduleSnapshotId(), dependent.recordId(), now);
            changed |= derived.recomputeLocal(dependent.systemId(), dependent.tenantId(),
                    dependent.schemaVersionId(), dependent.moduleSnapshotId(), dependent.recordId(), now);
            if (!changed) {
                continue;
            }
            requirePermissionEpoch(task.systemId(), permissionEpoch);
            var updated = jdbc.update("UPDATE un_module_record SET version=version+1,updated_at=? "
                            + "WHERE system_id=? AND tenant_id=? AND record_id=? AND schema_version_id=? "
                            + "AND module_snapshot_id=? AND version=?",
                    now, dependent.systemId(), dependent.tenantId(), dependent.recordId(),
                    dependent.schemaVersionId(), dependent.moduleSnapshotId(), dependent.version());
            if (updated != 1) {
                throw new IllegalStateException("Dependent record changed during reference recalculation");
            }
            mutations.systemChanged(dependent.systemId(), dependent.tenantId(), dependent.recordId(),
                    dependent.version() + 1, "REFERENCE_RECALCULATED",
                    Map.of("state", "PENDING", "version", dependent.version(),
                            "permissionEpoch", permissionEpoch),
                    Map.of("state", "READY", "version", dependent.version() + 1,
                            "permissionEpoch", permissionEpoch),
                    "reference-worker-" + task.id(), task.correlationId());
        }
    }

    private Long lockActiveSchemaVersion(long systemId) {
        var versions = jdbc.query("SELECT active_version_id FROM un_module_config_root "
                        + "WHERE system_id=? FOR SHARE",
                (row, number) -> row.getObject("active_version_id", Long.class), systemId);
        return versions.size() == 1 ? versions.getFirst() : null;
    }

    private void requirePermissionEpoch(long systemId, long expectedEpoch) {
        if (authorization.currentSystemEpoch(systemId) != expectedEpoch) {
            throw new IllegalStateException("Permission epoch changed during derived recalculation");
        }
    }

    private void pass(long taskId, LocalDateTime now) {
        jdbc.update("UPDATE un_module_reference_recalc_task SET status='PASSED',updated_at=?,version=version+1 "
                + "WHERE id=? AND status='RUNNING'", now, taskId);
    }

    private void fail(Task task, LocalDateTime now) {
        var nextAttempt = Math.min(task.attemptCount() + 1, 20);
        var terminal = nextAttempt >= 3;
        jdbc.update("UPDATE un_module_reference_recalc_task SET status=?,attempt_count=?,available_at=?,updated_at=?,"
                        + "version=version+1 WHERE id=? AND status='PENDING'",
                terminal ? "FAILED" : "PENDING", nextAttempt, terminal ? now : now.plusSeconds(nextAttempt), now,
                task.id());
        if (terminal) {
            jdbc.update("UPDATE un_module_reference_state SET recalculation_state='FAILED',"
                            + "failure_correlation_id=?,updated_at=?,version=version+1 WHERE system_id=? "
                            + "AND tenant_id=? AND source_record_id=? AND recalculation_state='PENDING'",
                    task.correlationId(), now, task.systemId(), task.tenantId(), task.sourceRecordId());
            derived.failForSource(task.systemId(), task.tenantId(), task.sourceRecordId(),
                    task.correlationId(), now);
        }
    }

    private record Task(long id, long systemId, long tenantId, long sourceRecordId, long sourceRecordVersion,
                        int attemptCount,
                        String correlationId) { }

    private static final class TaskFailure extends RuntimeException {
        private final Task task;

        private TaskFailure(Task task, RuntimeException cause) {
            super(cause);
            this.task = task;
        }

        private Task task() {
            return task;
        }
    }
}
