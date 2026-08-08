package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTaskReminder;
import com.unique.examine.work.port.WorkTaskReminderRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class JdbcWorkTaskReminderRepository
        implements WorkTaskReminderRepository {
    private static final String COLUMNS = """
            system_id,tenant_id,task_id,generation,scheduled_at,status,
            attempt_count,lease_owner,lease_token_hash,lease_expires_at,
            created_at,updated_at,sent_at,failed_at,cancelled_at,
            failure_code,failure_message,version
            """;
    static final String INSERT = """
            INSERT INTO un_work_task_reminder (
              system_id,tenant_id,task_id,generation,scheduled_at,status,
              attempt_count,lease_owner,lease_token_hash,lease_expires_at,
              created_at,updated_at,sent_at,failed_at,cancelled_at,
              failure_code,failure_message,version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String FIND = """
            SELECT %s FROM un_work_task_reminder
            WHERE system_id=? AND tenant_id=? AND task_id=? AND generation=?
            """.formatted(COLUMNS);
    static final String FIND_LATEST = """
            SELECT %s FROM un_work_task_reminder
            WHERE system_id=? AND tenant_id=? AND task_id=?
            ORDER BY generation DESC LIMIT 1
            """.formatted(COLUMNS);
    static final String FIND_LATEST_FOR_UPDATE = FIND_LATEST + " FOR UPDATE";
    static final String LOCK_TASK = """
            SELECT id FROM un_work_task
            WHERE system_id=? AND tenant_id=? AND id=? FOR UPDATE
            """;
    static final String FIND_DUE_FOR_UPDATE = """
            SELECT %s FROM un_work_task_reminder
            WHERE (status='PENDING' AND scheduled_at<=?)
               OR (status='PROCESSING' AND lease_expires_at<=?)
            ORDER BY scheduled_at ASC,task_id ASC,generation ASC,
                     system_id ASC,tenant_id ASC
            LIMIT ? FOR UPDATE SKIP LOCKED
            """.formatted(COLUMNS);
    static final String UPDATE = """
            UPDATE un_work_task_reminder
            SET status=?,attempt_count=?,lease_owner=?,lease_token_hash=?,
                lease_expires_at=?,updated_at=?,sent_at=?,failed_at=?,
                cancelled_at=?,failure_code=?,failure_message=?,version=?
            WHERE system_id=? AND tenant_id=? AND task_id=? AND generation=?
              AND version=?
              AND status NOT IN ('SENT','CANCELLED')
            """;

    static final RowMapper<WorkTaskReminder> ROW_MAPPER = (result, row) -> {
        var leaseExpiry = instant(result.getTimestamp("lease_expires_at"));
        var lease = leaseExpiry == null ? null : new WorkTaskReminder.Lease(
                result.getString("lease_owner"),
                result.getString("lease_token_hash"),
                leaseExpiry);
        return new WorkTaskReminder(
                result.getLong("system_id"),
                result.getLong("tenant_id"),
                result.getLong("task_id"),
                result.getInt("generation"),
                result.getTimestamp("scheduled_at").toInstant(),
                WorkTaskReminder.Status.valueOf(result.getString("status")),
                result.getInt("attempt_count"), lease,
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                instant(result.getTimestamp("sent_at")),
                instant(result.getTimestamp("failed_at")),
                instant(result.getTimestamp("cancelled_at")),
                result.getString("failure_code"),
                result.getString("failure_message"),
                result.getLong("version"));
    };

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public JdbcWorkTaskReminderRepository(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager
    ) {
        if (jdbc == null || transactionManager == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate and transaction manager are required");
        }
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Override
    public WorkTaskReminder schedule(
            long systemId,
            long tenantId,
            long taskId,
            Instant scheduledAt,
            Instant now
    ) {
        try {
            return transactions.execute(status -> {
                lockTask(systemId, tenantId, taskId);
                var latest = latestForUpdate(systemId, tenantId, taskId).orElse(null);
                if (latest != null && latest.isLive()) {
                    update(latest.cancelLive(now));
                }
                var generation = latest == null
                        ? 1 : Math.addExact(latest.generation(), 1);
                var reminder = WorkTaskReminder.pending(
                        systemId, tenantId, taskId, generation, scheduledAt, now);
                insert(reminder);
                return reminder;
            });
        } catch (DuplicateKeyException exception) {
            throw conflict(exception);
        }
    }

    @Override
    public Optional<WorkTaskReminder> find(
            long systemId,
            long tenantId,
            long taskId,
            int generation
    ) {
        return jdbc.query(FIND, ROW_MAPPER,
                systemId, tenantId, taskId, generation).stream().findFirst();
    }

    @Override
    public Optional<WorkTaskReminder> findLatest(
            long systemId,
            long tenantId,
            long taskId
    ) {
        return jdbc.query(FIND_LATEST, ROW_MAPPER,
                systemId, tenantId, taskId).stream().findFirst();
    }

    @Override
    public Optional<WorkTaskReminder> cancelLatestLive(
            long systemId,
            long tenantId,
            long taskId,
            Instant now
    ) {
        return transactions.execute(status -> {
            lockTask(systemId, tenantId, taskId);
            var latest = latestForUpdate(systemId, tenantId, taskId).orElse(null);
            if (latest == null || !latest.isLive()) {
                return Optional.ofNullable(latest);
            }
            var cancelled = latest.cancelLive(now);
            update(cancelled);
            return Optional.of(cancelled);
        });
    }

    @Override
    public WorkTaskReminder save(WorkTaskReminder reminder) {
        if (reminder.version() == 1) {
            try {
                insert(reminder);
            } catch (DuplicateKeyException exception) {
                throw conflict(exception);
            }
        } else {
            update(reminder);
        }
        return reminder;
    }

    @Override
    public List<WorkTaskReminder> claimDue(
            String owner,
            String tokenHash,
            Instant now,
            Instant leaseUntil,
            int limit
    ) {
        if (limit < 1 || limit > 100) {
            throw new IllegalArgumentException(
                    "Reminder claim limit must be within 1..100");
        }
        return transactions.execute(status -> jdbc.query(
                        FIND_DUE_FOR_UPDATE, ROW_MAPPER,
                        Timestamp.from(now), Timestamp.from(now), limit)
                .stream()
                .map(reminder -> {
                    var pending = reminder.status() == WorkTaskReminder.Status.PROCESSING
                            ? reminder.recoverExpiredLease(now) : reminder;
                    if (pending != reminder) {
                        update(pending);
                    }
                    var claimed = pending.claim(owner, tokenHash, leaseUntil, now);
                    update(claimed);
                    return claimed;
                }).toList());
    }

    private void lockTask(long systemId, long tenantId, long taskId) {
        var locked = jdbc.queryForList(LOCK_TASK, Long.class,
                systemId, tenantId, taskId);
        if (locked.isEmpty()) {
            throw new WorkDomainException("WORK_TASK_NOT_FOUND", "Task not found");
        }
    }

    private Optional<WorkTaskReminder> latestForUpdate(
            long systemId,
            long tenantId,
            long taskId
    ) {
        return jdbc.query(FIND_LATEST_FOR_UPDATE, ROW_MAPPER,
                systemId, tenantId, taskId).stream().findFirst();
    }

    private void insert(WorkTaskReminder reminder) {
        var lease = reminder.lease();
        jdbc.update(INSERT,
                reminder.systemId(), reminder.tenantId(), reminder.taskId(),
                reminder.generation(), timestamp(reminder.scheduledAt()),
                reminder.status().name(), reminder.attemptCount(),
                lease == null ? null : lease.owner(),
                lease == null ? null : lease.tokenHash(),
                lease == null ? null : timestamp(lease.expiresAt()),
                timestamp(reminder.createdAt()), timestamp(reminder.updatedAt()),
                timestamp(reminder.sentAt()), timestamp(reminder.failedAt()),
                timestamp(reminder.cancelledAt()), reminder.failureCode(),
                reminder.failureMessage(), reminder.version());
    }

    private void update(WorkTaskReminder reminder) {
        var lease = reminder.lease();
        var updated = jdbc.update(UPDATE,
                reminder.status().name(), reminder.attemptCount(),
                lease == null ? null : lease.owner(),
                lease == null ? null : lease.tokenHash(),
                lease == null ? null : timestamp(lease.expiresAt()),
                timestamp(reminder.updatedAt()), timestamp(reminder.sentAt()),
                timestamp(reminder.failedAt()), timestamp(reminder.cancelledAt()),
                reminder.failureCode(), reminder.failureMessage(), reminder.version(),
                reminder.systemId(), reminder.tenantId(), reminder.taskId(),
                reminder.generation(), reminder.version() - 1);
        if (updated != 1) {
            throw conflict(null);
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static WorkDomainException conflict(Throwable cause) {
        var error = new WorkDomainException(
                "WORK_TASK_REMINDER_VERSION_CONFLICT", "Reminder version is stale");
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }
}
