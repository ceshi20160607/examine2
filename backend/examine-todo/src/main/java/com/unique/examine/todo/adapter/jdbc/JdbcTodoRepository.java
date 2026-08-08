package com.unique.examine.todo.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import com.unique.examine.todo.domain.*;
import com.unique.examine.todo.port.TodoRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class JdbcTodoRepository implements TodoRepository {
    private static final String ITEM_COLUMNS = """
            id,system_id,tenant_id,recipient_member_id,source_type,source_id,
            source_version,action_scope,category,priority,title,due_at,route_hint,
            available_actions,represented_member_id,status,close_reason,
            created_at,updated_at,closed_at,version
            """;
    private static final String ACTION_COLUMNS = """
            id,system_id,tenant_id,todo_item_id,recipient_member_id,actor_member_id,
            caller_idempotency_key,source_type,source_id,source_version,
            requested_action,status,result_code,result_message,request_id,trace_id,
            created_at,completed_at,version
            """;
    static final String INSERT_ITEM = """
            INSERT INTO un_todo_item (%s)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.formatted(ITEM_COLUMNS);
    static final String UPDATE_ITEM = """
            UPDATE un_todo_item
            SET source_version=?,category=?,priority=?,title=?,due_at=?,route_hint=?,
                available_actions=?,represented_member_id=?,status=?,close_reason=?,
                updated_at=?,closed_at=?,version=?
            WHERE system_id=? AND tenant_id=? AND id=? AND recipient_member_id=?
              AND version=? AND status='OPEN'
            """;
    static final String FIND_IDENTITY = """
            SELECT %s FROM un_todo_item
            WHERE system_id=? AND tenant_id=? AND recipient_member_id=?
              AND source_type=? AND source_id=? AND action_scope=?
            """.formatted(ITEM_COLUMNS);
    static final String FIND_ITEM = """
            SELECT %s FROM un_todo_item
            WHERE system_id=? AND tenant_id=? AND recipient_member_id=? AND id=?
            """.formatted(ITEM_COLUMNS);
    static final String FIND_OPEN = """
            SELECT %s FROM un_todo_item
            WHERE system_id=? AND tenant_id=? AND recipient_member_id=?
              AND source_type=? AND status='OPEN'
            ORDER BY priority ASC,due_at ASC,created_at ASC,id ASC
            """.formatted(ITEM_COLUMNS);
    private static final String PAGE_SELECT = "SELECT " + ITEM_COLUMNS + " FROM un_todo_item\n";
    private static final String PAGE_COUNT = "SELECT COUNT(*) FROM un_todo_item\n";
    private static final String PAGE_ORDER = """
            ORDER BY priority ASC,due_at IS NULL ASC,due_at ASC,created_at ASC,id ASC
            LIMIT ? OFFSET ?
            """;
    static final String COUNTS = """
            SELECT COUNT(*) AS open_count,
                   COALESCE(SUM(category='TASK'),0) AS task_count,
                   COALESCE(SUM(category='APPROVAL'),0) AS approval_count,
                   COALESCE(SUM(due_at>=? AND due_at<?),0) AS today_count,
                   COALESCE(SUM(due_at<?),0) AS overdue_count
            FROM un_todo_item
            WHERE system_id=? AND tenant_id=? AND recipient_member_id=?
              AND status='OPEN'
            """;
    static final String INSERT_ACTION = """
            INSERT INTO un_todo_action_log (%s)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """.formatted(ACTION_COLUMNS);
    static final String FIND_ACTION = """
            SELECT %s FROM un_todo_action_log
            WHERE system_id=? AND tenant_id=? AND actor_member_id=?
              AND caller_idempotency_key=?
            """.formatted(ACTION_COLUMNS);
    static final String COMPLETE_ACTION = """
            UPDATE un_todo_action_log
            SET status=?,result_code=?,result_message=?,completed_at=?,version=?
            WHERE system_id=? AND tenant_id=? AND id=? AND actor_member_id=?
              AND caller_idempotency_key=? AND version=? AND status='PROCESSING'
            """;

    static final RowMapper<TodoItem> ITEM_MAPPER = (result, row) -> {
        var identity = new TodoIdentity(
                result.getLong("system_id"), result.getLong("tenant_id"),
                result.getLong("recipient_member_id"),
                TodoItem.SourceType.valueOf(result.getString("source_type")),
                result.getString("source_id"), result.getString("action_scope"));
        var closeReason = result.getString("close_reason");
        return new TodoItem(result.getLong("id"), identity,
                result.getLong("source_version"),
                TodoItem.Category.valueOf(result.getString("category")),
                result.getInt("priority"), result.getString("title"),
                instant(result.getTimestamp("due_at")), result.getString("route_hint"),
                parseActions(result.getString("available_actions")),
                nullableLong(result.getObject("represented_member_id")),
                TodoItem.State.valueOf(result.getString("status")),
                closeReason == null ? null : TodoItem.CloseReason.valueOf(closeReason),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant(),
                instant(result.getTimestamp("closed_at")), result.getLong("version"));
    };
    static final RowMapper<TodoActionLog> ACTION_MAPPER = (result, row) -> {
        var resultCode = result.getString("result_code");
        return new TodoActionLog(result.getLong("id"), result.getLong("system_id"),
                result.getLong("tenant_id"), result.getLong("todo_item_id"),
                result.getLong("recipient_member_id"), result.getLong("actor_member_id"),
                result.getString("caller_idempotency_key"),
                TodoItem.SourceType.valueOf(result.getString("source_type")),
                result.getString("source_id"), result.getLong("source_version"),
                TodoItem.ActionCode.valueOf(result.getString("requested_action")),
                TodoActionLog.Status.valueOf(result.getString("status")),
                resultCode == null ? null : TodoActionLog.ResultCode.valueOf(resultCode),
                result.getString("result_message"), result.getString("request_id"),
                result.getString("trace_id"),
                result.getTimestamp("created_at").toInstant(),
                instant(result.getTimestamp("completed_at")), result.getLong("version"));
    };

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcTodoRepository(JdbcTemplate jdbc, IdService ids) {
        if (jdbc == null || ids == null) {
            throw new IllegalArgumentException("JdbcTemplate and IdService are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override public long nextItemId() { return ids.nextId(); }
    @Override public long nextActionLogId() { return ids.nextId(); }

    @Override
    public Optional<TodoItem> findByIdentity(TodoIdentity identity) {
        return jdbc.query(FIND_IDENTITY, ITEM_MAPPER,
                identity.systemId(), identity.tenantId(), identity.recipientMemberId(),
                identity.sourceType().name(), identity.sourceId(), identity.actionScope())
                .stream().findFirst();
    }

    @Override
    public Optional<TodoItem> findById(long systemId, long tenantId,
                                       long recipientMemberId, long itemId) {
        return jdbc.query(FIND_ITEM, ITEM_MAPPER,
                systemId, tenantId, recipientMemberId, itemId).stream().findFirst();
    }

    @Override
    public List<TodoItem> findOpen(long systemId, long tenantId,
                                   long recipientMemberId, TodoItem.SourceType sourceType) {
        return jdbc.query(FIND_OPEN, ITEM_MAPPER,
                systemId, tenantId, recipientMemberId, sourceType.name());
    }

    @Override
    public TodoPage findPage(long systemId, long tenantId,
                             long recipientMemberId, TodoQuery query) {
        var statements = pageStatements(systemId, tenantId, recipientMemberId, query);
        var total = jdbc.queryForObject(statements.countSql(), Long.class,
                statements.countArguments().toArray());
        var items = jdbc.query(statements.pageSql(), ITEM_MAPPER,
                statements.pageArguments().toArray());
        return new TodoPage(items, query.page(), query.size(), total == null ? 0 : total);
    }

    @Override
    public TodoCounts counts(long systemId, long tenantId, long recipientMemberId,
                             Instant todayStart, Instant tomorrowStart) {
        return jdbc.queryForObject(COUNTS, (result, row) -> new TodoCounts(
                        result.getLong("open_count"), result.getLong("task_count"),
                        result.getLong("approval_count"), result.getLong("today_count"),
                        result.getLong("overdue_count")),
                timestamp(todayStart), timestamp(tomorrowStart), timestamp(todayStart),
                systemId, tenantId, recipientMemberId);
    }

    @Override
    public TodoItem save(TodoItem item) {
        if (item.version() == 1) insertItem(item); else updateItem(item);
        return item;
    }

    @Override
    public TodoActionReservation reserveAction(TodoActionLog proposed) {
        try {
            insertAction(proposed);
            return new TodoActionReservation(proposed, true);
        } catch (DuplicateKeyException duplicate) {
            var existing = findAction(proposed.systemId(), proposed.tenantId(),
                    proposed.actorMemberId(), proposed.callerIdempotencyKey())
                    .orElseThrow(() -> conflict("TODO_ACTION_VERSION_CONFLICT",
                            "Todo action reservation raced"));
            return new TodoActionReservation(existing, false);
        }
    }

    @Override
    public TodoActionLog completeAction(TodoActionLog completed) {
        var updated = jdbc.update(COMPLETE_ACTION, completed.status().name(),
                completed.resultCode().name(), completed.resultMessage(),
                timestamp(completed.completedAt()), completed.version(),
                completed.systemId(), completed.tenantId(), completed.id(),
                completed.actorMemberId(), completed.callerIdempotencyKey(),
                completed.version() - 1);
        if (updated != 1) throw conflict("TODO_ACTION_VERSION_CONFLICT",
                "Todo action log version is stale");
        return completed;
    }

    @Override
    public Optional<TodoActionLog> findAction(long systemId, long tenantId,
                                              long actorMemberId,
                                              String callerIdempotencyKey) {
        return jdbc.query(FIND_ACTION, ACTION_MAPPER,
                systemId, tenantId, actorMemberId, callerIdempotencyKey)
                .stream().findFirst();
    }

    static PageStatements pageStatements(long systemId, long tenantId,
                                          long recipientMemberId, TodoQuery query) {
        var where = new StringBuilder("""
                WHERE system_id=? AND tenant_id=? AND recipient_member_id=?
                """);
        var arguments = new ArrayList<Object>();
        arguments.add(systemId); arguments.add(tenantId); arguments.add(recipientMemberId);
        if (query.state() != TodoQuery.StateFilter.ALL) {
            where.append(" AND status=?\n"); arguments.add(query.state().name());
        }
        if (query.category() != TodoQuery.CategoryFilter.ALL) {
            where.append(" AND category=?\n"); arguments.add(query.category().name());
        }
        switch (query.time()) {
            case TODAY -> {
                where.append(" AND due_at>=? AND due_at<?\n");
                arguments.add(timestamp(query.todayStart()));
                arguments.add(timestamp(query.tomorrowStart()));
            }
            case OVERDUE -> {
                where.append(" AND due_at<?\n");
                arguments.add(timestamp(query.todayStart()));
            }
            case ALL -> { }
        }
        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size()); pageArguments.add(query.offset());
        return new PageStatements(PAGE_SELECT + where + PAGE_ORDER, pageArguments,
                PAGE_COUNT + where, arguments);
    }

    private void insertItem(TodoItem item) {
        try {
            var identity = item.identity();
            jdbc.update(INSERT_ITEM, item.id(), identity.systemId(), identity.tenantId(),
                    identity.recipientMemberId(), identity.sourceType().name(),
                    identity.sourceId(), item.sourceVersion(), identity.actionScope(),
                    item.category().name(), item.priority(), item.title(), timestamp(item.dueAt()),
                    item.routeHint(), actions(item), item.representedMemberId(),
                    item.state().name(), item.closeReason() == null ? null : item.closeReason().name(),
                    timestamp(item.createdAt()), timestamp(item.updatedAt()),
                    timestamp(item.closedAt()), item.version());
        } catch (DuplicateKeyException duplicate) {
            throw conflict("TODO_VERSION_CONFLICT", "Todo item already exists");
        }
    }

    private void updateItem(TodoItem item) {
        var identity = item.identity();
        var updated = jdbc.update(UPDATE_ITEM, item.sourceVersion(), item.category().name(),
                item.priority(), item.title(), timestamp(item.dueAt()), item.routeHint(),
                actions(item), item.representedMemberId(), item.state().name(),
                item.closeReason() == null ? null : item.closeReason().name(),
                timestamp(item.updatedAt()), timestamp(item.closedAt()), item.version(),
                identity.systemId(), identity.tenantId(), item.id(),
                identity.recipientMemberId(), item.version() - 1);
        if (updated != 1) throw conflict("TODO_VERSION_CONFLICT", "Todo item version is stale");
    }

    private void insertAction(TodoActionLog log) {
        jdbc.update(INSERT_ACTION, log.id(), log.systemId(), log.tenantId(),
                log.todoItemId(), log.recipientMemberId(), log.actorMemberId(),
                log.callerIdempotencyKey(), log.sourceType().name(), log.sourceId(),
                log.sourceVersion(), log.requestedAction().name(), log.status().name(),
                null, null, log.requestId(), log.traceId(), timestamp(log.createdAt()),
                null, log.version());
    }

    private static String actions(TodoItem item) {
        return item.availableActions().stream().map(Enum::name).sorted()
                .collect(Collectors.joining(","));
    }

    private static EnumSet<TodoItem.ActionCode> parseActions(String value) {
        var result = EnumSet.noneOf(TodoItem.ActionCode.class);
        Arrays.stream(value.split(",")).map(TodoItem.ActionCode::valueOf).forEach(result::add);
        return result;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }
    private static Instant instant(Timestamp value) { return value == null ? null : value.toInstant(); }
    private static Long nullableLong(Object value) { return value == null ? null : ((Number) value).longValue(); }
    private static TodoException conflict(String code, String message) { return new TodoException(code, message); }

    record PageStatements(String pageSql, List<Object> pageArguments,
                          String countSql, List<Object> countArguments) {
        PageStatements {
            pageArguments = List.copyOf(pageArguments);
            countArguments = List.copyOf(countArguments);
        }
    }
}
