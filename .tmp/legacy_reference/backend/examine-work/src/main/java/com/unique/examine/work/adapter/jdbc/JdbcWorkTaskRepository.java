package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.domain.WorkTask;
import com.unique.examine.work.domain.WorkTaskPage;
import com.unique.examine.work.domain.WorkTaskQuery;
import com.unique.examine.work.domain.WorkTaskMetricFacts;
import com.unique.examine.work.port.WorkTaskRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcWorkTaskRepository implements WorkTaskRepository {
    static final String INSERT_SQL = """
            INSERT INTO un_work_task (
                id, system_id, tenant_id, creator_member_id, assignee_member_id,
                title, status, created_at, updated_at, version,
                project_id, description, due_at, reminder_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    static final String UPDATE_SQL = """
            UPDATE un_work_task
               SET assignee_member_id = ?, title = ?, status = ?, updated_at = ?, version = ?,
                   project_id = ?, description = ?, due_at = ?, reminder_at = ?
             WHERE id = ? AND system_id = ? AND tenant_id = ? AND version = ?
            """;
    static final String FIND_SQL = """
            SELECT id, system_id, tenant_id, creator_member_id, assignee_member_id,
                   title, status, created_at, updated_at, version,
                   project_id, description, due_at, reminder_at
              FROM un_work_task
             WHERE system_id = ? AND tenant_id = ? AND id = ?
            """;
    static final String FIND_ALL_SQL = """
            SELECT id, system_id, tenant_id, creator_member_id, assignee_member_id,
                   title, status, created_at, updated_at, version,
                   project_id, description, due_at, reminder_at
              FROM un_work_task
             WHERE system_id = ? AND tenant_id = ?
             ORDER BY updated_at DESC, id DESC
            """;
    static final String FIND_PARTICIPATING_SQL = """
            SELECT id, system_id, tenant_id, creator_member_id, assignee_member_id,
                   title, status, created_at, updated_at, version,
                   project_id, description, due_at, reminder_at
              FROM un_work_task
             WHERE system_id = ? AND tenant_id = ?
               AND (creator_member_id = ? OR assignee_member_id = ?)
            ORDER BY updated_at DESC, id DESC
            """;
    static final String PAGE_SELECT = """
            SELECT id, system_id, tenant_id, creator_member_id, assignee_member_id,
                   title, status, created_at, updated_at, version,
                   project_id, description, due_at, reminder_at
              FROM un_work_task
            """;
    static final String PAGE_COUNT = """
            SELECT COUNT(*)
              FROM un_work_task
            """;
    static final String PAGE_ORDER = """
             ORDER BY CASE WHEN status = 'OPEN' THEN 0 ELSE 1 END ASC,
                      updated_at DESC, id DESC
             LIMIT ? OFFSET ?
            """;
    static final RowMapper<WorkTask> ROW_MAPPER = (resultSet, rowNum) -> new WorkTask(
            resultSet.getLong("id"),
            resultSet.getLong("system_id"),
            resultSet.getLong("tenant_id"),
            resultSet.getLong("creator_member_id"),
            resultSet.getLong("assignee_member_id"),
            resultSet.getString("title"),
            WorkTask.Status.valueOf(resultSet.getString("status")),
            resultSet.getTimestamp("created_at").toInstant(),
            resultSet.getTimestamp("updated_at").toInstant(),
            resultSet.getLong("version"),
            nullableLong(resultSet.getObject("project_id")),
            resultSet.getString("description"),
            resultSet.getTimestamp("due_at") == null
                    ? null : resultSet.getTimestamp("due_at").toInstant(),
            resultSet.getTimestamp("reminder_at") == null
                    ? null : resultSet.getTimestamp("reminder_at").toInstant());

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcWorkTaskRepository(JdbcTemplate jdbc, IdService ids) {
        if (jdbc == null || ids == null) {
            throw new IllegalArgumentException("JdbcTemplate and IdService are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public long nextId() {
        return ids.nextId();
    }

    @Override
    public Optional<WorkTask> findById(long systemId, long tenantId, long id) {
        return jdbc.query(FIND_SQL, ROW_MAPPER, systemId, tenantId, id).stream().findFirst();
    }

    @Override
    public List<WorkTask> findAll(long systemId, long tenantId) {
        return jdbc.query(FIND_ALL_SQL, ROW_MAPPER, systemId, tenantId);
    }

    @Override
    public List<WorkTask> findParticipating(long systemId, long tenantId, long memberId) {
        return jdbc.query(FIND_PARTICIPATING_SQL, ROW_MAPPER,
                systemId, tenantId, memberId, memberId);
    }

    @Override
    public WorkTaskPage findPage(
            long systemId,
            long tenantId,
            long memberId,
            WorkTaskQuery query
    ) {
        var statements = pageStatements(systemId, tenantId, memberId, query);
        var total = jdbc.queryForObject(
                statements.countSql(),
                Long.class,
                statements.countArguments().toArray());
        var items = jdbc.query(
                statements.pageSql(),
                ROW_MAPPER,
                statements.pageArguments().toArray());
        return new WorkTaskPage(
                items,
                query.page(),
                query.size(),
                total == null ? 0 : total);
    }

    @Override
    public WorkTaskMetricFacts metrics(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        return metrics(
                systemId, tenantId, memberId, tenantWide, null,
                fromInclusive, toExclusive, now);
    }

    @Override
    public WorkTaskMetricFacts metrics(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Long projectId,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        var statements = metricsStatements(
                systemId, tenantId, memberId, tenantWide, projectId,
                fromInclusive, toExclusive, now);
        var summary = jdbc.queryForObject(
                statements.summarySql(),
                (resultSet, rowNum) -> new MetricSummary(
                        resultSet.getLong("total_count"),
                        resultSet.getLong("completed_count"),
                        resultSet.getLong("open_count"),
                        resultSet.getLong("overdue_open_count"),
                        resultSet.getLong("due_range_open_count"),
                        resultSet.getLong("completed_range_count")),
                statements.summaryArguments().toArray());
        var created = utcDayCounts(jdbc.query(
                statements.createdDailySql(),
                (resultSet, rowNum) -> resultSet.getTimestamp("metric_at").toInstant(),
                statements.dailyArguments().toArray()));
        var completed = utcDayCounts(jdbc.query(
                statements.completedDailySql(),
                (resultSet, rowNum) -> resultSet.getTimestamp("metric_at").toInstant(),
                statements.dailyArguments().toArray()));
        var assignees = jdbc.query(
                statements.topAssigneesSql(),
                (resultSet, rowNum) ->
                        new WorkTaskMetricFacts.AssigneeOpen(
                                resultSet.getLong("assignee_member_id"),
                                resultSet.getLong("open_count")),
                statements.scopeArguments().toArray());
        summary = summary == null
                ? new MetricSummary(0, 0, 0, 0, 0, 0)
                : summary;
        return new WorkTaskMetricFacts(
                summary.total(), summary.completed(), summary.open(),
                summary.overdueOpen(),
                summary.dueInRangeOpen(), summary.completedInRange(),
                created, completed, assignees);
    }

    @Override
    public WorkTask save(WorkTask task) {
        if (task.version() == 1) {
            insert(task);
        } else {
            update(task);
        }
        return task;
    }

    private void insert(WorkTask task) {
        try {
            jdbc.update(INSERT_SQL,
                    task.id(), task.systemId(), task.tenantId(), task.creatorMemberId(),
                    task.assigneeMemberId(), task.title(), task.status().name(),
                    Timestamp.from(task.createdAt()), Timestamp.from(task.updatedAt()),
                    task.version(), task.projectId(), task.description(),
                    task.dueAt() == null ? null : Timestamp.from(task.dueAt()),
                    task.reminderAt() == null ? null : Timestamp.from(task.reminderAt()));
        } catch (DuplicateKeyException duplicate) {
            throw conflict(duplicate);
        }
    }

    private void update(WorkTask task) {
        int updated = jdbc.update(UPDATE_SQL,
                task.assigneeMemberId(), task.title(), task.status().name(),
                Timestamp.from(task.updatedAt()), task.version(),
                task.projectId(), task.description(),
                task.dueAt() == null ? null : Timestamp.from(task.dueAt()),
                task.reminderAt() == null ? null : Timestamp.from(task.reminderAt()),
                task.id(), task.systemId(), task.tenantId(), task.version() - 1);
        if (updated != 1) {
            throw conflict(null);
        }
    }

    private static WorkDomainException conflict(Throwable cause) {
        var error = new WorkDomainException("WORK_TASK_VERSION_CONFLICT", "Task version is stale");
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    static PageStatements pageStatements(
            long systemId,
            long tenantId,
            long memberId,
            WorkTaskQuery query
    ) {
        var where = new StringBuilder("""
                 WHERE system_id = ? AND tenant_id = ?
                """);
        var arguments = new ArrayList<Object>();
        arguments.add(systemId);
        arguments.add(tenantId);

        if (query.status() != WorkTaskQuery.StatusFilter.ALL) {
            where.append(" AND status = ?\n");
            arguments.add(query.status().name());
        }
        switch (query.role()) {
            case PARTICIPATING -> {
                where.append("""
                         AND (creator_member_id = ? OR assignee_member_id = ?
                           OR (project_id IS NOT NULL AND EXISTS (
                             SELECT 1 FROM un_work_project_member project_member
                             WHERE project_member.system_id=un_work_task.system_id
                               AND project_member.tenant_id=un_work_task.tenant_id
                               AND project_member.project_id=un_work_task.project_id
                               AND project_member.member_id=?
                               AND project_member.status='ACTIVE')))
                        """);
                arguments.add(memberId);
                arguments.add(memberId);
                arguments.add(memberId);
            }
            case CREATED_BY_ME -> {
                where.append(" AND creator_member_id = ?\n");
                arguments.add(memberId);
            }
            case ASSIGNED_TO_ME -> {
                where.append(" AND assignee_member_id = ?\n");
                arguments.add(memberId);
            }
            case ALL -> {
                // Service authorization limits this role to work.task.manage.
            }
        }
        if (!query.keyword().isEmpty()) {
            where.append(" AND title LIKE ? ESCAPE '!'\n");
            arguments.add(likePattern(query.keyword()));
        }
        if (query.projectId() != null) {
            where.append(" AND project_id = ?\n");
            arguments.add(query.projectId());
        }
        if (query.dueFrom() != null) {
            where.append(" AND due_at >= ?\n");
            arguments.add(Timestamp.from(query.dueFrom()));
        }
        if (query.dueTo() != null) {
            where.append(" AND due_at <= ?\n");
            arguments.add(Timestamp.from(query.dueTo()));
        }
        if (query.dueBefore() != null) {
            where.append(" AND due_at < ?\n");
            arguments.add(Timestamp.from(query.dueBefore()));
        }
        if (query.createdFrom() != null) {
            where.append(" AND created_at >= ?\n");
            arguments.add(Timestamp.from(query.createdFrom()));
        }
        if (query.createdBefore() != null) {
            where.append(" AND created_at < ?\n");
            arguments.add(Timestamp.from(query.createdBefore()));
        }
        if (query.updatedFrom() != null) {
            where.append(" AND updated_at >= ?\n");
            arguments.add(Timestamp.from(query.updatedFrom()));
        }
        if (query.updatedBefore() != null) {
            where.append(" AND updated_at < ?\n");
            arguments.add(Timestamp.from(query.updatedBefore()));
        }
        if (query.assigneeMemberId() != null) {
            where.append(" AND assignee_member_id = ?\n");
            arguments.add(query.assigneeMemberId());
        }
        if (query.reminderFrom() != null) {
            where.append(" AND reminder_at >= ?\n");
            arguments.add(Timestamp.from(query.reminderFrom()));
        }
        if (query.reminderTo() != null) {
            where.append(" AND reminder_at <= ?\n");
            arguments.add(Timestamp.from(query.reminderTo()));
        }

        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size());
        pageArguments.add(query.offset());
        return new PageStatements(
                PAGE_SELECT + where + PAGE_ORDER,
                pageArguments,
                PAGE_COUNT + where,
                arguments);
    }

    static String likePattern(String keyword) {
        return "%" + keyword
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_") + "%";
    }

    static MetricsStatements metricsStatements(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        return metricsStatements(
                systemId, tenantId, memberId, tenantWide, null,
                fromInclusive, toExclusive, now);
    }

    static MetricsStatements metricsStatements(
            long systemId,
            long tenantId,
            long memberId,
            boolean tenantWide,
            Long projectId,
            Instant fromInclusive,
            Instant toExclusive,
            Instant now
    ) {
        if (fromInclusive == null || toExclusive == null || now == null
                || !toExclusive.isAfter(fromInclusive)
                || projectId != null && projectId <= 0) {
            throw new IllegalArgumentException(
                    "Work metric timestamps are invalid");
        }
        var where = new StringBuilder("""
                 WHERE task.system_id = ? AND task.tenant_id = ?
                """);
        var scopeArguments = new ArrayList<Object>();
        scopeArguments.add(systemId);
        scopeArguments.add(tenantId);
        if (!tenantWide) {
            where.append("""
                     AND (task.creator_member_id = ? OR task.assignee_member_id = ?
                       OR (task.project_id IS NOT NULL AND EXISTS (
                         SELECT 1 FROM un_work_project_member project_member
                         WHERE project_member.system_id=task.system_id
                           AND project_member.tenant_id=task.tenant_id
                           AND project_member.project_id=task.project_id
                           AND project_member.member_id=?
                           AND project_member.status='ACTIVE')))
                    """);
            scopeArguments.add(memberId);
            scopeArguments.add(memberId);
            scopeArguments.add(memberId);
        }
        if (projectId != null) {
            where.append(" AND task.project_id = ?\n");
            scopeArguments.add(projectId);
        }
        var summaryArguments = new ArrayList<Object>();
        summaryArguments.add(Timestamp.from(now));
        summaryArguments.add(Timestamp.from(fromInclusive));
        summaryArguments.add(Timestamp.from(toExclusive));
        summaryArguments.add(Timestamp.from(fromInclusive));
        summaryArguments.add(Timestamp.from(toExclusive));
        summaryArguments.addAll(scopeArguments);

        var dailyArguments = new ArrayList<>(scopeArguments);
        dailyArguments.add(Timestamp.from(fromInclusive));
        dailyArguments.add(Timestamp.from(toExclusive));
        var summarySql = """
                SELECT COUNT(*) total_count,
                       COALESCE(SUM(CASE WHEN task.status='COMPLETED' THEN 1 ELSE 0 END), 0) completed_count,
                       COALESCE(SUM(CASE WHEN task.status='OPEN' THEN 1 ELSE 0 END), 0) open_count,
                       COALESCE(SUM(CASE WHEN task.status='OPEN' AND task.due_at < ? THEN 1 ELSE 0 END), 0) overdue_open_count,
                       COALESCE(SUM(CASE WHEN task.status='OPEN' AND task.due_at >= ? AND task.due_at < ? THEN 1 ELSE 0 END), 0) due_range_open_count,
                       COALESCE(SUM(CASE WHEN task.status='COMPLETED' AND task.updated_at >= ? AND task.updated_at < ? THEN 1 ELSE 0 END), 0) completed_range_count
                  FROM un_work_task task
                """ + where;
        var createdDailySql = """
                SELECT task.created_at metric_at
                  FROM un_work_task task
                """ + where + """
                   AND task.created_at >= ? AND task.created_at < ?
                 ORDER BY task.created_at ASC
                """;
        var completedDailySql = """
                SELECT task.updated_at metric_at
                  FROM un_work_task task
                """ + where + """
                   AND task.status='COMPLETED'
                   AND task.updated_at >= ? AND task.updated_at < ?
                 ORDER BY task.updated_at ASC
                """;
        var topAssigneesSql = """
                SELECT task.assignee_member_id, COUNT(*) open_count
                  FROM un_work_task task
                """ + where + """
                   AND task.status='OPEN'
                 GROUP BY task.assignee_member_id
                 ORDER BY open_count DESC, task.assignee_member_id ASC
                 LIMIT 20
                """;
        return new MetricsStatements(
                summarySql, summaryArguments,
                createdDailySql, completedDailySql, dailyArguments,
                topAssigneesSql, scopeArguments);
    }

    static java.util.List<WorkTaskMetricFacts.DayCount> utcDayCounts(
            java.util.List<Instant> values
    ) {
        var counts = new java.util.TreeMap<java.time.LocalDate, Long>();
        for (var value : values) {
            var date = value.atZone(java.time.ZoneOffset.UTC).toLocalDate();
            counts.merge(date, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .map(entry -> new WorkTaskMetricFacts.DayCount(
                        entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Long nullableLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    record PageStatements(
            String pageSql,
            List<Object> pageArguments,
            String countSql,
            List<Object> countArguments
    ) {
        PageStatements {
            pageArguments = List.copyOf(pageArguments);
            countArguments = List.copyOf(countArguments);
        }
    }

    record MetricsStatements(
            String summarySql,
            List<Object> summaryArguments,
            String createdDailySql,
            String completedDailySql,
            List<Object> dailyArguments,
            String topAssigneesSql,
            List<Object> scopeArguments
    ) {
        MetricsStatements {
            summaryArguments = List.copyOf(summaryArguments);
            dailyArguments = List.copyOf(dailyArguments);
            scopeArguments = List.copyOf(scopeArguments);
        }
    }

    private record MetricSummary(
            long total,
            long completed,
            long open,
            long overdueOpen,
            long dueInRangeOpen,
            long completedInRange
    ) {
    }
}
