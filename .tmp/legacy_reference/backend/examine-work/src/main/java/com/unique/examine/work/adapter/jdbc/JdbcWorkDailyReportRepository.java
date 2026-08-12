package com.unique.examine.work.adapter.jdbc;

import com.unique.examine.core.id.IdService;
import com.unique.examine.work.domain.WorkDailyReport;
import com.unique.examine.work.domain.WorkDailyReportPage;
import com.unique.examine.work.domain.WorkDailyReportQuery;
import com.unique.examine.work.domain.WorkDomainException;
import com.unique.examine.work.port.WorkDailyReportRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class JdbcWorkDailyReportRepository
        implements WorkDailyReportRepository {
    private static final String COLUMNS = """
            report_row.id,report_row.system_id,report_row.tenant_id,
            report_row.author_member_id,report_row.work_date,
            report_row.completed_work,report_row.planned_work,
            report_row.blockers,report_row.status,report_row.created_at,
            report_row.updated_at,report_row.submitted_at,report_row.version
            """;
    private static final String ACTIVE_AUTHOR_JOIN = """
            JOIN un_plat_member author_row
              ON author_row.system_id=report_row.system_id
             AND author_row.id=report_row.author_member_id
             AND author_row.status='ACTIVE' AND author_row.deleted_at IS NULL
            JOIN un_plat_member_tenant membership_row
              ON membership_row.system_id=report_row.system_id
             AND membership_row.tenant_id=report_row.tenant_id
             AND membership_row.member_id=report_row.author_member_id
             AND membership_row.status='ACTIVE'
             AND membership_row.deleted_at IS NULL
             AND (membership_row.expires_at IS NULL
               OR membership_row.expires_at>CURRENT_TIMESTAMP(3))
            """;

    static final String INSERT = """
            INSERT INTO un_work_daily_report
              (id,system_id,tenant_id,author_member_id,work_date,
               completed_work,planned_work,blockers,status,created_at,
               updated_at,submitted_at,version)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;
    static final String UPDATE = """
            UPDATE un_work_daily_report
            SET completed_work=?,planned_work=?,blockers=?,status=?,
                updated_at=?,submitted_at=?,version=?
            WHERE system_id=? AND tenant_id=? AND id=? AND version=?
            """;
    static final String FIND_BY_ID = """
            SELECT %s FROM un_work_daily_report report_row
            %s
            WHERE report_row.system_id=? AND report_row.tenant_id=?
              AND report_row.id=?
            """.formatted(COLUMNS, ACTIVE_AUTHOR_JOIN);
    static final String FIND_BY_AUTHOR_DATE = """
            SELECT %s FROM un_work_daily_report report_row
            %s
            WHERE report_row.system_id=? AND report_row.tenant_id=?
              AND report_row.author_member_id=? AND report_row.work_date=?
            """.formatted(COLUMNS, ACTIVE_AUTHOR_JOIN);
    private static final String PAGE_SELECT = """
            SELECT %s FROM un_work_daily_report report_row
            %s
            """.formatted(COLUMNS, ACTIVE_AUTHOR_JOIN);
    private static final String PAGE_COUNT = """
            SELECT COUNT(*) FROM un_work_daily_report report_row
            %s
            """.formatted(ACTIVE_AUTHOR_JOIN);
    private static final String PAGE_ORDER = """
            ORDER BY report_row.work_date DESC,
                     report_row.updated_at DESC,report_row.id DESC
            LIMIT ? OFFSET ?
            """;

    static final RowMapper<WorkDailyReport> ROW_MAPPER = (result, row) ->
            new WorkDailyReport(
                    result.getLong("id"), result.getLong("system_id"),
                    result.getLong("tenant_id"),
                    result.getLong("author_member_id"),
                    result.getDate("work_date").toLocalDate(),
                    result.getString("completed_work"),
                    result.getString("planned_work"),
                    result.getString("blockers"),
                    WorkDailyReport.Status.valueOf(result.getString("status")),
                    result.getTimestamp("created_at").toInstant(),
                    result.getTimestamp("updated_at").toInstant(),
                    result.getTimestamp("submitted_at") == null
                            ? null
                            : result.getTimestamp("submitted_at").toInstant(),
                    result.getLong("version"));

    private final JdbcTemplate jdbc;
    private final IdService ids;

    public JdbcWorkDailyReportRepository(JdbcTemplate jdbc, IdService ids) {
        if (jdbc == null || ids == null) {
            throw new IllegalArgumentException(
                    "JdbcTemplate and IdService are required");
        }
        this.jdbc = jdbc;
        this.ids = ids;
    }

    @Override
    public long nextId() {
        return ids.nextId();
    }

    @Override
    public Optional<WorkDailyReport> findById(
            long systemId, long tenantId, long reportId
    ) {
        return jdbc.query(FIND_BY_ID, ROW_MAPPER,
                systemId, tenantId, reportId).stream().findFirst();
    }

    @Override
    public Optional<WorkDailyReport> findByAuthorAndDate(
            long systemId,
            long tenantId,
            long authorMemberId,
            LocalDate workDate
    ) {
        return jdbc.query(FIND_BY_AUTHOR_DATE, ROW_MAPPER,
                systemId, tenantId, authorMemberId, Date.valueOf(workDate))
                .stream().findFirst();
    }

    @Override
    public WorkDailyReportPage findPage(
            long systemId,
            long tenantId,
            long currentMemberId,
            boolean manager,
            WorkDailyReportQuery query
    ) {
        var statements = pageStatements(
                systemId, tenantId, currentMemberId, manager, query);
        var total = jdbc.queryForObject(
                statements.countSql(), Long.class,
                statements.countArguments().toArray());
        var items = jdbc.query(
                statements.pageSql(), ROW_MAPPER,
                statements.pageArguments().toArray());
        return new WorkDailyReportPage(
                items, query.page(), query.size(), total == null ? 0 : total);
    }

    @Override
    public WorkDailyReport save(WorkDailyReport report) {
        if (report.version() == 1) {
            try {
                insert(report);
                return report;
            } catch (DuplicateKeyException exception) {
                var existing = findById(
                        report.systemId(), report.tenantId(), report.id());
                if (existing.filter(report::equals).isPresent()) {
                    return existing.get();
                }
                throw conflict("Daily report already exists", exception);
            }
        }
        var updated = jdbc.update(
                UPDATE, report.completedWork(), report.plannedWork(),
                report.blockers(), report.status().name(),
                Timestamp.from(report.updatedAt()),
                report.submittedAt() == null
                        ? null : Timestamp.from(report.submittedAt()),
                report.version(), report.systemId(), report.tenantId(),
                report.id(), report.version() - 1);
        if (updated == 1) {
            return report;
        }
        var existing = findById(
                report.systemId(), report.tenantId(), report.id());
        if (existing.filter(report::equals).isPresent()) {
            return existing.get();
        }
        throw conflict("Daily report version is stale", null);
    }

    static PageStatements pageStatements(
            long systemId,
            long tenantId,
            long currentMemberId,
            boolean manager,
            WorkDailyReportQuery query
    ) {
        var where = new StringBuilder("""
                WHERE report_row.system_id=? AND report_row.tenant_id=?
                """);
        var arguments = new ArrayList<Object>();
        arguments.add(systemId);
        arguments.add(tenantId);
        if (!manager || query.scope() == WorkDailyReportQuery.Scope.SELF) {
            where.append(" AND report_row.author_member_id=?\n");
            arguments.add(currentMemberId);
        } else if (query.authorMemberId() != null) {
            where.append(" AND report_row.author_member_id=?\n");
            arguments.add(query.authorMemberId());
        }
        where.append(" AND report_row.work_date BETWEEN ? AND ?\n");
        arguments.add(Date.valueOf(query.dateFrom()));
        arguments.add(Date.valueOf(query.dateTo()));
        if (query.status() != WorkDailyReportQuery.StatusFilter.ALL) {
            where.append(" AND report_row.status=?\n");
            arguments.add(query.status().name());
        }
        var pageArguments = new ArrayList<>(arguments);
        pageArguments.add(query.size());
        pageArguments.add(query.offset());
        return new PageStatements(
                PAGE_SELECT + where + PAGE_ORDER, pageArguments,
                PAGE_COUNT + where, arguments);
    }

    private void insert(WorkDailyReport report) {
        jdbc.update(
                INSERT, report.id(), report.systemId(), report.tenantId(),
                report.authorMemberId(), Date.valueOf(report.workDate()),
                report.completedWork(), report.plannedWork(), report.blockers(),
                report.status().name(), Timestamp.from(report.createdAt()),
                Timestamp.from(report.updatedAt()),
                report.submittedAt() == null
                        ? null : Timestamp.from(report.submittedAt()),
                report.version());
    }

    private static WorkDomainException conflict(
            String message, Throwable cause
    ) {
        var error = new WorkDomainException(
                "WORK_REPORT_VERSION_CONFLICT", message);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
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
}
