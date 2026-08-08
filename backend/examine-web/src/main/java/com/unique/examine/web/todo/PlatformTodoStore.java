package com.unique.examine.web.todo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class PlatformTodoStore {
    private static final String SELECT = """
            SELECT id,title,description,due_at,priority,status,
                   created_at,updated_at,version
              FROM un_platform_task
            """;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public PlatformTodoStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PlatformTodoApiModels.Page page(
            long accountId, PlatformTodoApiModels.StateFilter state,
            int page, int size) {
        return page(accountId, state, PlatformTodoApiModels.TypeFilter.ALL,
                page, size);
    }

    public PlatformTodoApiModels.Page page(
            long accountId, PlatformTodoApiModels.StateFilter state,
            PlatformTodoApiModels.TypeFilter type, int page, int size) {
        requirePage(page, size);
        var predicate = predicate(state) + typePredicate(type);
        var total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_platform_task WHERE account_id=?" + predicate,
                Long.class, accountId);
        var items = jdbc.query(SELECT + " WHERE account_id=?" + predicate
                        + " ORDER BY CASE WHEN status='OPEN' THEN 0 ELSE 1 END,"
                        + " updated_at DESC,id DESC LIMIT ? OFFSET ?",
                (row, ignored) -> view(
                        row.getLong("id"), row.getString("title"),
                        row.getString("description"),
                        row.getTimestamp("due_at") == null ? null
                                : row.getTimestamp("due_at").toInstant(),
                        row.getString("priority"), row.getString("status"),
                        row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("updated_at").toInstant(),
                        row.getLong("version")),
                accountId, size, Math.multiplyExact(page - 1, size));
        return new PlatformTodoApiModels.Page(
                items, page, size, total == null ? 0 : total);
    }

    public PlatformTodoApiModels.Counts counts(long accountId) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(status='OPEN'),0) open_count,
                       COALESCE(SUM(status<>'OPEN'),0) closed_count,
                       COUNT(*) total_count,
                       COALESCE(SUM(status='OPEN' AND due_at>=UTC_DATE()
                         AND due_at<DATE_ADD(UTC_DATE(),INTERVAL 1 DAY)),0) today_count,
                       COALESCE(SUM(status='OPEN' AND due_at>=UTC_TIMESTAMP(6)
                         AND due_at<DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY)),0) reminder_count
                  FROM un_platform_task WHERE account_id=?
                """, (row, ignored) -> new PlatformTodoApiModels.Counts(
                        row.getLong("open_count"), row.getLong("closed_count"),
                        row.getLong("total_count"), row.getLong("today_count"),
                        row.getLong("reminder_count"), 0, 0), accountId);
    }

    public Optional<PlatformTodoApiModels.TodoView> find(
            long accountId, long taskId) {
        return jdbc.query(SELECT + " WHERE account_id=? AND id=?",
                (row, ignored) -> view(
                        row.getLong("id"), row.getString("title"),
                        row.getString("description"),
                        row.getTimestamp("due_at") == null ? null
                                : row.getTimestamp("due_at").toInstant(),
                        row.getString("priority"), row.getString("status"),
                        row.getTimestamp("created_at").toInstant(),
                        row.getTimestamp("updated_at").toInstant(),
                        row.getLong("version")), accountId, taskId)
                .stream().findFirst();
    }

    public Optional<Replay> replay(long accountId, String idempotencyKey) {
        return jdbc.query("""
                SELECT task_id,requested_action,request_version,result_json
                  FROM un_platform_todo_action
                 WHERE account_id=? AND idempotency_key=?
                """, (row, ignored) -> new Replay(
                        row.getLong("task_id"),
                        PlatformTodoApiModels.Action.valueOf(
                                row.getString("requested_action")),
                        row.getLong("request_version"),
                        read(row.getString("result_json"))),
                accountId, idempotencyKey).stream().findFirst();
    }

    public void saveReplay(
            long accountId, String idempotencyKey, long taskId,
            PlatformTodoApiModels.Action action, long requestVersion,
            PlatformTodoApiModels.TodoView result, Instant completedAt) {
        try {
            jdbc.update("""
                    INSERT INTO un_platform_todo_action(
                      account_id,idempotency_key,task_id,requested_action,
                      request_version,result_json,completed_at)
                    VALUES (?,?,?,?,?,CAST(? AS JSON),?)
                    """, accountId, idempotencyKey, taskId, action.name(),
                    requestVersion, write(result), Timestamp.from(completedAt));
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(
                    "PLATFORM_TODO_IDEMPOTENCY_CONFLICT",
                    "Platform Todo idempotency key is already in use",
                    HttpStatus.CONFLICT);
        }
    }

    private PlatformTodoApiModels.TodoView read(String value) {
        try {
            return json.readValue(value, PlatformTodoApiModels.TodoView.class);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Stored platform Todo result is invalid", failure);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("Platform Todo result is invalid", failure);
        }
    }

    private static PlatformTodoApiModels.TodoView view(
            long id, String title, String description, Instant dueAt,
            String priority, String status, Instant createdAt,
            Instant updatedAt, long version) {
        var actions = switch (status) {
            case "OPEN" -> List.of(
                    PlatformTodoApiModels.Action.COMPLETE,
                    PlatformTodoApiModels.Action.CANCEL);
            case "COMPLETED", "CANCELLED" ->
                    List.of(PlatformTodoApiModels.Action.REOPEN);
            default -> throw new IllegalStateException(
                    "Stored platform task status is invalid");
        };
        var now = Instant.now();
        var category = status.equals("OPEN") && dueAt != null
                && !dueAt.isBefore(now) && dueAt.isBefore(now.plusSeconds(86_400))
                ? "REMINDER" : "TASK";
        return new PlatformTodoApiModels.TodoView(
                Long.toString(id), "PLATFORM", "PLATFORM_TASK", category,
                title, description, priority,
                status.equals("OPEN") ? "OPEN" : "CLOSED", status,
                dueAt == null ? null : dueAt.toString(),
                "/platform/work?task=" + id, actions,
                createdAt.toString(), updatedAt.toString(), version);
    }

    private static String predicate(PlatformTodoApiModels.StateFilter state) {
        if (state == null) throw invalid("state is required");
        return switch (state) {
            case ALL -> "";
            case OPEN -> " AND status='OPEN'";
            case CLOSED -> " AND status IN ('COMPLETED','CANCELLED')";
        };
    }

    private static String typePredicate(PlatformTodoApiModels.TypeFilter type) {
        if (type == null) throw invalid("type is required");
        return switch (type) {
            case ALL, TASK -> "";
            case TODAY -> " AND status='OPEN' AND due_at>=UTC_DATE() AND due_at<DATE_ADD(UTC_DATE(),INTERVAL 1 DAY)";
            case REMINDER -> " AND status='OPEN' AND due_at>=UTC_TIMESTAMP(6) AND due_at<DATE_ADD(UTC_TIMESTAMP(6),INTERVAL 1 DAY)";
            // Platform Flow is a separate delivery gap.  Do not leak system
            // approvals into platform context while no platform approval owner exists.
            case APPROVAL, FAILED -> " AND 1=0";
        };
    }

    private static void requirePage(int page, int size) {
        if (page < 1 || page > 10_000 || size < 1 || size > 100) {
            throw invalid("page must be 1..10000 and size must be 1..100");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                "PLATFORM_TODO_REQUEST_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    public record Replay(
            long taskId, PlatformTodoApiModels.Action action,
            long requestVersion, PlatformTodoApiModels.TodoView result) {
    }
}
