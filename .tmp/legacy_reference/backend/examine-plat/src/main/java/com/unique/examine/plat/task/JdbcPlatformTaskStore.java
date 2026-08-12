package com.unique.examine.plat.task;

import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.ai.PlatformOperationsQueryFacade;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Repository
public class JdbcPlatformTaskStore
        implements PlatformTaskStore, PlatformTaskQueryStore,
        PlatformTaskLifecycleStore {
    static final String UPSERT_SQL = """
            INSERT INTO un_platform_task(
              id,account_id,title,description,due_at,priority,status,source,
              authorization_epoch,payload_hash,idempotency_key,request_id,
              trace_id,created_at,created_by,updated_at,completed_at,
              cancelled_at,version)
            VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE id=id
            """;

    static final String FIND_SQL = """
            SELECT id,account_id,title,description,due_at,priority,status,source,
              authorization_epoch,payload_hash,idempotency_key,request_id,
              trace_id,created_at,created_by,updated_at,completed_at,
              cancelled_at,version
            FROM un_platform_task
            WHERE account_id=? AND idempotency_key=?
            """;

    static final String FIND_OWN_TASKS_SQL = """
            SELECT id,title,due_at,priority,status,source,created_at
            FROM un_platform_task
            WHERE account_id=?
            ORDER BY created_at DESC,id DESC
            LIMIT ?
            """;

    static final String COUNT_OWN_PAGE_SQL = """
            SELECT COUNT(*)
            FROM un_platform_task
            WHERE account_id=? AND (?='ALL' OR status=?)
            """;

    static final String FIND_OWN_PAGE_SQL = """
            SELECT id,title,description,due_at,priority,status,source,
              created_at,updated_at,completed_at,cancelled_at,version
            FROM un_platform_task
            WHERE account_id=? AND (?='ALL' OR status=?)
            ORDER BY updated_at DESC,id DESC
            LIMIT ? OFFSET ?
            """;

    static final String FIND_OWN_BY_ID_SQL = """
            SELECT id,title,description,due_at,priority,status,source,
              created_at,updated_at,completed_at,cancelled_at,version
            FROM un_platform_task
            WHERE account_id=? AND id=?
            """;

    static final String TRANSITION_SQL = """
            UPDATE un_platform_task
            SET status=?,updated_at=?,completed_at=?,cancelled_at=?,
              version=version+1
            WHERE account_id=? AND id=? AND version=? AND status IN (?,?)
            """;

    private final JdbcTemplate jdbc;

    public JdbcPlatformTaskStore(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    @Transactional
    public PlatformTask createOrReplay(PlatformTask value) {
        jdbc.update(UPSERT_SQL,
                value.id(), value.accountId(), value.title(), value.description(),
                timestamp(value.dueAt()), value.priority().name(),
                value.status().name(), value.source().name(),
                value.authorizationEpoch(), value.payloadHash(),
                value.idempotencyKey(), value.requestId(), value.traceId(),
                timestamp(value.createdAt()), value.createdBy(),
                timestamp(value.updatedAt()), timestamp(value.completedAt()),
                timestamp(value.cancelledAt()), value.version());
        var stored = jdbc.query(FIND_SQL, this::map,
                value.accountId(), value.idempotencyKey());
        if (stored.size() != 1) {
            throw new IllegalStateException(
                    "Platform task idempotent write did not resolve one row");
        }
        return stored.getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.List<PlatformOperationsQueryFacade.PersonalTask> findOwnTasks(
            long accountId, int limit) {
        if (accountId <= 0 || limit < 1
                || limit > PlatformOperationsQueryFacade.MAX_LIMIT) {
            throw new IllegalArgumentException("Personal task query is invalid");
        }
        return jdbc.query(FIND_OWN_TASKS_SQL, (value, row) ->
                        new PlatformOperationsQueryFacade.PersonalTask(
                                Long.toString(value.getLong("id")),
                                value.getString("title"),
                                instant(value, "due_at"),
                                PlatformTaskFacade.Priority.valueOf(
                                        value.getString("priority")),
                                PlatformTaskFacade.Status.valueOf(
                                        value.getString("status")),
                                PlatformTaskFacade.Source.valueOf(
                                        value.getString("source")),
                                instant(value, "created_at")),
                accountId, limit);
    }

    @Override
    @Transactional(readOnly = true)
    public long countOwn(long accountId, PlatformTaskStatusFilter status) {
        requirePageArguments(accountId, status);
        var total = jdbc.queryForObject(
                COUNT_OWN_PAGE_SQL, Long.class,
                accountId, status.name(), status.name());
        if (total == null || total < 0) {
            throw new IllegalStateException("Platform task count is invalid");
        }
        return total;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PlatformTaskApi.TaskView> findOwnPage(
            long accountId,
            PlatformTaskStatusFilter status,
            int offset,
            int size) {
        requirePageArguments(accountId, status);
        if (offset < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("Platform task page is invalid");
        }
        return jdbc.query(FIND_OWN_PAGE_SQL, this::mapSafe,
                accountId, status.name(), status.name(), size, offset);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<PlatformTaskApi.TaskView> findOwnById(
            long accountId, long taskId) {
        if (accountId <= 0 || taskId <= 0) {
            throw new IllegalArgumentException("Platform task identity is invalid");
        }
        var rows = jdbc.query(
                FIND_OWN_BY_ID_SQL, this::mapSafe, accountId, taskId);
        if (rows.size() > 1) {
            throw new IllegalStateException("Platform task identity is ambiguous");
        }
        return rows.stream().findFirst();
    }

    @Override
    @Transactional
    public int transition(
            long accountId,
            long taskId,
            long expectedVersion,
            Set<PlatformTaskFacade.Status> expectedStatuses,
            PlatformTaskFacade.Status targetStatus,
            Instant transitionAt) {
        if (accountId <= 0 || taskId <= 0 || expectedVersion < 0
                || expectedStatuses == null || expectedStatuses.isEmpty()
                || targetStatus == null || transitionAt == null) {
            throw new IllegalArgumentException("Platform task transition is invalid");
        }
        var sourceOne = expectedStatuses.contains(PlatformTaskFacade.Status.OPEN)
                ? PlatformTaskFacade.Status.OPEN
                : PlatformTaskFacade.Status.COMPLETED;
        var sourceTwo = expectedStatuses.contains(PlatformTaskFacade.Status.CANCELLED)
                ? PlatformTaskFacade.Status.CANCELLED
                : sourceOne;
        var completedAt = targetStatus == PlatformTaskFacade.Status.COMPLETED
                ? timestamp(transitionAt) : null;
        var cancelledAt = targetStatus == PlatformTaskFacade.Status.CANCELLED
                ? timestamp(transitionAt) : null;
        return jdbc.update(TRANSITION_SQL,
                targetStatus.name(), timestamp(transitionAt), completedAt,
                cancelledAt, accountId, taskId, expectedVersion,
                sourceOne.name(), sourceTwo.name());
    }

    private PlatformTask map(ResultSet value, int row) throws SQLException {
        return new PlatformTask(
                value.getLong("id"), value.getLong("account_id"),
                value.getString("title"), value.getString("description"),
                instant(value, "due_at"),
                PlatformTaskFacade.Priority.valueOf(value.getString("priority")),
                PlatformTaskFacade.Status.valueOf(value.getString("status")),
                PlatformTaskFacade.Source.valueOf(value.getString("source")),
                value.getLong("authorization_epoch"),
                value.getString("payload_hash"),
                value.getString("idempotency_key"), value.getString("request_id"),
                value.getString("trace_id"), instant(value, "created_at"),
                value.getLong("created_by"), instant(value, "updated_at"),
                instant(value, "completed_at"), instant(value, "cancelled_at"),
                value.getLong("version"));
    }

    private PlatformTaskApi.TaskView mapSafe(ResultSet value, int row)
            throws SQLException {
        return new PlatformTaskApi.TaskView(
                Long.toString(value.getLong("id")), value.getString("title"),
                value.getString("description"), instant(value, "due_at"),
                PlatformTaskFacade.Priority.valueOf(value.getString("priority")),
                PlatformTaskFacade.Status.valueOf(value.getString("status")),
                PlatformTaskFacade.Source.valueOf(value.getString("source")),
                instant(value, "created_at"), instant(value, "updated_at"),
                instant(value, "completed_at"), instant(value, "cancelled_at"),
                value.getLong("version"));
    }

    private static void requirePageArguments(
            long accountId, PlatformTaskStatusFilter status) {
        if (accountId <= 0 || status == null) {
            throw new IllegalArgumentException("Platform task query is invalid");
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(ResultSet value, String column)
            throws SQLException {
        var result = value.getTimestamp(column);
        return result == null ? null : result.toInstant();
    }
}
