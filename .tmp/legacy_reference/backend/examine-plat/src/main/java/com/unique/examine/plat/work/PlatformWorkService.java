package com.unique.examine.plat.work;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.task.JdbcPlatformTaskStore;
import com.unique.examine.plat.task.PlatformTask;
import org.springframework.http.HttpStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class PlatformWorkService {
    public static final String READ = "platform.work.read";
    public static final String MANAGE = "platform.work.manage";

    private final JdbcTemplate jdbc;
    private final JdbcPlatformTaskStore taskOwner;
    private final IdService ids;
    private final ObjectMapper json;
    private final Clock clock;

    @Autowired
    public PlatformWorkService(JdbcTemplate jdbc, JdbcPlatformTaskStore taskOwner,
                               IdService ids, ObjectMapper json) {
        this(jdbc, taskOwner, ids, json, Clock.systemUTC());
    }

    PlatformWorkService(JdbcTemplate jdbc, JdbcPlatformTaskStore taskOwner,
                        IdService ids, ObjectMapper json, Clock clock) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.taskOwner = Objects.requireNonNull(taskOwner);
        this.ids = Objects.requireNonNull(ids);
        this.json = Objects.requireNonNull(json);
        this.clock = Objects.requireNonNull(clock);
    }

    @Transactional(readOnly = true)
    public PlatformWorkApi.Overview overview(AuthenticatedSession session) {
        var caller = SessionGuard.requirePlatform(session, READ);
        var today = LocalDate.now(clock);
        return new PlatformWorkApi.Overview(
                count("SELECT COUNT(*) FROM un_platform_work_project WHERE owner_account_id=? AND status='ACTIVE'", caller.accountId()),
                count("SELECT COUNT(*) FROM un_platform_task WHERE account_id=? AND source='WORK' AND status='OPEN'", caller.accountId()),
                count("SELECT COUNT(*) FROM un_platform_task WHERE account_id=? AND source='WORK' AND status='OPEN' AND due_at<?", caller.accountId(), Timestamp.valueOf(today.atStartOfDay())),
                count("SELECT COUNT(*) FROM un_platform_task WHERE account_id=? AND source='WORK' AND status='OPEN' AND due_at>=? AND due_at<?", caller.accountId(), Timestamp.valueOf(today.atStartOfDay()), Timestamp.valueOf(today.plusDays(1).atStartOfDay())),
                count("SELECT COUNT(*) FROM un_platform_work_daily_report WHERE account_id=? AND report_date=? AND status='SUBMITTED'", caller.accountId(), today) > 0);
    }

    @Transactional(readOnly = true)
    public List<PlatformWorkApi.ProjectView> projects(AuthenticatedSession session) {
        var caller = SessionGuard.requirePlatform(session, READ);
        return jdbc.query("""
                SELECT p.id,p.code,p.name,p.status,p.start_date,p.due_date,p.updated_at,p.version,
                       COUNT(t.id) task_count,COALESCE(SUM(t.status='COMPLETED'),0) completed_count
                  FROM un_platform_work_project p
             LEFT JOIN un_platform_task t ON t.project_id=p.id AND t.account_id=p.owner_account_id
                 WHERE p.owner_account_id=?
              GROUP BY p.id,p.code,p.name,p.status,p.start_date,p.due_date,p.updated_at,p.version
              ORDER BY p.updated_at DESC,p.id DESC
                """, this::project, caller.accountId());
    }

    @Transactional
    public PlatformWorkApi.ProjectView createProject(AuthenticatedSession session,
                                                     PlatformWorkApi.ProjectInput input) {
        var caller = SessionGuard.requirePlatform(session, MANAGE);
        if (input == null) throw invalid("project input is required");
        var code = token(input.code(), "code", 64).toUpperCase(Locale.ROOT);
        if (!code.matches("^[A-Z][A-Z0-9_]{1,63}$")) throw invalid("project code is invalid");
        var name = text(input.name(), "name", 200, false);
        if (input.startDate() != null && input.dueDate() != null
                && input.dueDate().isBefore(input.startDate())) throw invalid("dueDate is before startDate");
        var id = ids.nextId();
        var now = clock.instant();
        try {
            jdbc.update("""
                    INSERT INTO un_platform_work_project(
                      id,owner_account_id,code,name,status,start_date,due_date,
                      created_at,created_by,updated_at,updated_by,version)
                    VALUES(?,?,?,?,'ACTIVE',?,?,?,?,?,?,0)
                    """, id, caller.accountId(), code, name, input.startDate(), input.dueDate(),
                    timestamp(now), caller.accountId(), timestamp(now), caller.accountId());
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException("PLATFORM_WORK_PROJECT_CONFLICT",
                    "Project code already exists", HttpStatus.CONFLICT);
        }
        return projects(session).stream().filter(value -> value.id().equals(Long.toString(id)))
                .findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public PlatformWorkApi.TaskPage tasks(AuthenticatedSession session,
                                          String kindValue, String statusValue,
                                          int page, int size) {
        var caller = SessionGuard.requirePlatform(session, READ);
        if (page < 1 || page > 10_000 || size < 1 || size > 100) throw invalid("invalid task page");
        var kind = enumValue(PlatformWorkApi.TaskKind.class, kindValue, null);
        var status = enumValue(PlatformWorkApi.TaskStatus.class, statusValue, PlatformWorkApi.TaskStatus.ALL);
        var where = " WHERE t.account_id=? AND t.source='WORK'"
                + (kind == null ? "" : " AND t.task_kind='" + kind.name() + "'")
                + (status == PlatformWorkApi.TaskStatus.ALL ? "" : " AND t.status='" + status.name() + "'");
        var total = count("SELECT COUNT(*) FROM un_platform_task t" + where, caller.accountId());
        var rows = jdbc.query("""
                SELECT t.id,t.task_kind,t.project_id,p.name project_name,t.title,t.description,
                       t.due_at,t.priority,t.status,t.labels_json,t.created_at,t.updated_at,t.version
                  FROM un_platform_task t
             LEFT JOIN un_platform_work_project p ON p.id=t.project_id AND p.owner_account_id=t.account_id
                """ + where + " ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?",
                this::task, caller.accountId(), size, Math.multiplyExact(page - 1, size));
        return new PlatformWorkApi.TaskPage(rows, page, size, total);
    }

    @Transactional
    public PlatformWorkApi.TaskView createTask(AuthenticatedSession session,
                                               PlatformWorkApi.TaskInput input,
                                               String idempotencyKey,
                                               String requestId, String traceId) {
        var caller = SessionGuard.requirePlatform(session, MANAGE);
        SessionGuard.requirePermission(caller, "platform.task.manage");
        if (input == null || input.kind() == null || input.priority() == null) throw invalid("task input is incomplete");
        var projectId = input.kind() == PlatformWorkApi.TaskKind.PROJECT
                ? positive(input.projectId(), "projectId") : null;
        if (projectId != null && count("SELECT COUNT(*) FROM un_platform_work_project WHERE id=? AND owner_account_id=?", projectId, caller.accountId()) != 1) {
            throw new BusinessException("PLATFORM_WORK_PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND);
        }
        var title = text(input.title(), "title", 200, false);
        var description = text(input.description(), "description", 2000, true);
        var now = clock.instant();
        if (input.dueAt() != null && !input.dueAt().isAfter(now)) throw invalid("dueAt must be in the future");
        var labels = input.labels().stream().map(value -> text(value, "label", 40, false)).distinct().limit(20).toList();
        var key = token(idempotencyKey, "Idempotency-Key", 128);
        var payload = input.kind() + "|" + projectId + "|" + title + "|" + description + "|" + input.dueAt() + "|" + input.priority() + "|" + labels;
        var candidate = new PlatformTask(ids.nextId(), caller.accountId(), title, description,
                input.dueAt(), input.priority(), PlatformTaskFacade.Status.OPEN,
                PlatformTaskFacade.Source.WORK, Math.max(1, caller.permissionVersion()), sha256(payload),
                key, correlation(requestId), correlation(traceId), now, caller.accountId());
        var stored = taskOwner.createOrReplay(candidate);
        if (!stored.payloadHash().equals(candidate.payloadHash())) {
            throw new BusinessException("PLATFORM_WORK_IDEMPOTENCY_CONFLICT",
                    "Idempotency key belongs to another task", HttpStatus.CONFLICT);
        }
        var labelsJson = write(labels);
        jdbc.update("""
                UPDATE un_platform_task SET task_kind=?,project_id=?,labels_json=CAST(? AS JSON)
                 WHERE id=? AND account_id=? AND source='WORK'
                   AND task_kind='PERSONAL' AND project_id IS NULL
                """, input.kind().name(), projectId, labelsJson, stored.id(), caller.accountId());
        return findTask(caller.accountId(), stored.id());
    }

    @Transactional(readOnly = true)
    public List<PlatformWorkApi.ReportView> reports(AuthenticatedSession session) {
        var caller = SessionGuard.requirePlatform(session, READ);
        return jdbc.query("""
                SELECT r.id,r.report_date,r.status,r.completed_text,r.plan_text,r.risk_text,
                       r.project_id,p.name project_name,r.updated_at,r.version
                  FROM un_platform_work_daily_report r
             LEFT JOIN un_platform_work_project p ON p.id=r.project_id AND p.owner_account_id=r.account_id
                 WHERE r.account_id=? ORDER BY r.report_date DESC,r.id DESC LIMIT 366
                """, this::report, caller.accountId());
    }

    @Transactional
    public PlatformWorkApi.ReportView saveReport(AuthenticatedSession session,
                                                 PlatformWorkApi.ReportInput input) {
        var caller = SessionGuard.requirePlatform(session, MANAGE);
        if (input == null || input.reportDate() == null || input.reportDate().isAfter(LocalDate.now(clock))) throw invalid("reportDate is invalid");
        var completed = text(input.completed(), "completed", 4000, false);
        var plan = text(input.plan(), "plan", 4000, false);
        var risks = text(input.risks(), "risks", 4000, true);
        var projectId = input.projectId() == null || input.projectId().isBlank() ? null : positive(input.projectId(), "projectId");
        if (projectId != null && count("SELECT COUNT(*) FROM un_platform_work_project WHERE id=? AND owner_account_id=?", projectId, caller.accountId()) != 1) throw new BusinessException("PLATFORM_WORK_PROJECT_NOT_FOUND", "Project was not found", HttpStatus.NOT_FOUND);
        var existing = jdbc.query("SELECT id,version,status FROM un_platform_work_daily_report WHERE account_id=? AND report_date=?",
                (row, ignored) -> new Object[]{row.getLong("id"), row.getLong("version"), row.getString("status")}, caller.accountId(), input.reportDate()).stream().findFirst().orElse(null);
        var now = clock.instant();
        long id;
        if (existing == null) {
            if (input.expectedVersion() != null) throw conflict();
            id = ids.nextId();
            jdbc.update("""
                    INSERT INTO un_platform_work_daily_report(
                      id,account_id,report_date,status,completed_text,plan_text,risk_text,project_id,
                      created_at,created_by,updated_at,updated_by,submitted_at,version)
                    VALUES(?,?,?,'DRAFT',?,?,?,?,?,?,?,?,NULL,0)
                    """, id, caller.accountId(), input.reportDate(), completed, plan, risks, projectId,
                    timestamp(now), caller.accountId(), timestamp(now), caller.accountId());
        } else {
            id = (long) existing[0];
            if (!"DRAFT".equals(existing[2]) || input.expectedVersion() == null || (long) existing[1] != input.expectedVersion()) throw conflict();
            var changed = jdbc.update("""
                    UPDATE un_platform_work_daily_report
                       SET completed_text=?,plan_text=?,risk_text=?,project_id=?,updated_at=?,updated_by=?,version=version+1
                     WHERE id=? AND account_id=? AND status='DRAFT' AND version=?
                    """, completed, plan, risks, projectId, timestamp(now), caller.accountId(), id, caller.accountId(), input.expectedVersion());
            if (changed != 1) throw conflict();
        }
        return findReport(caller.accountId(), id);
    }

    @Transactional
    public PlatformWorkApi.ReportView submitReport(AuthenticatedSession session,
                                                   String reportId, long version) {
        var caller = SessionGuard.requirePlatform(session, MANAGE);
        var id = positive(reportId, "reportId");
        var now = clock.instant();
        var changed = jdbc.update("""
                UPDATE un_platform_work_daily_report
                   SET status='SUBMITTED',submitted_at=?,updated_at=?,updated_by=?,version=version+1
                 WHERE id=? AND account_id=? AND status='DRAFT' AND version=?
                """, timestamp(now), timestamp(now), caller.accountId(), id, caller.accountId(), version);
        if (changed != 1) throw conflict();
        return findReport(caller.accountId(), id);
    }

    private PlatformWorkApi.TaskView findTask(long accountId, long taskId) {
        return jdbc.query("""
                SELECT t.id,t.task_kind,t.project_id,p.name project_name,t.title,t.description,
                       t.due_at,t.priority,t.status,t.labels_json,t.created_at,t.updated_at,t.version
                  FROM un_platform_task t
             LEFT JOIN un_platform_work_project p ON p.id=t.project_id AND p.owner_account_id=t.account_id
                 WHERE t.account_id=? AND t.id=? AND t.source='WORK'
                """, this::task, accountId, taskId).stream().findFirst()
                .orElseThrow(() -> new BusinessException("PLATFORM_WORK_TASK_NOT_FOUND", "Task was not found", HttpStatus.NOT_FOUND));
    }

    private PlatformWorkApi.ReportView findReport(long accountId, long id) {
        return jdbc.query("""
                SELECT r.id,r.report_date,r.status,r.completed_text,r.plan_text,r.risk_text,
                       r.project_id,p.name project_name,r.updated_at,r.version
                  FROM un_platform_work_daily_report r
             LEFT JOIN un_platform_work_project p ON p.id=r.project_id AND p.owner_account_id=r.account_id
                 WHERE r.account_id=? AND r.id=?
                """, this::report, accountId, id).stream().findFirst()
                .orElseThrow(() -> new BusinessException("PLATFORM_WORK_REPORT_NOT_FOUND", "Report was not found", HttpStatus.NOT_FOUND));
    }

    private PlatformWorkApi.ProjectView project(ResultSet row, int ignored) throws SQLException {
        return new PlatformWorkApi.ProjectView(Long.toString(row.getLong("id")), row.getString("code"),
                row.getString("name"), row.getString("status"), row.getObject("start_date", LocalDate.class),
                row.getObject("due_date", LocalDate.class), row.getLong("task_count"), row.getLong("completed_count"),
                row.getTimestamp("updated_at").toInstant(), row.getLong("version"));
    }

    private PlatformWorkApi.TaskView task(ResultSet row, int ignored) throws SQLException {
        var due = row.getTimestamp("due_at");
        var project = row.getObject("project_id");
        return new PlatformWorkApi.TaskView(Long.toString(row.getLong("id")),
                PlatformWorkApi.TaskKind.valueOf(row.getString("task_kind")),
                project == null ? null : Long.toString(row.getLong("project_id")), row.getString("project_name"),
                row.getString("title"), row.getString("description"), due == null ? null : due.toInstant(),
                PlatformTaskFacade.Priority.valueOf(row.getString("priority")),
                PlatformTaskFacade.Status.valueOf(row.getString("status")), readLabels(row.getString("labels_json")),
                row.getTimestamp("created_at").toInstant(), row.getTimestamp("updated_at").toInstant(), row.getLong("version"));
    }

    private PlatformWorkApi.ReportView report(ResultSet row, int ignored) throws SQLException {
        var project = row.getObject("project_id");
        return new PlatformWorkApi.ReportView(Long.toString(row.getLong("id")), row.getObject("report_date", LocalDate.class),
                PlatformWorkApi.ReportStatus.valueOf(row.getString("status")), row.getString("completed_text"),
                row.getString("plan_text"), row.getString("risk_text"), project == null ? null : Long.toString(row.getLong("project_id")),
                row.getString("project_name"), row.getTimestamp("updated_at").toInstant(), row.getLong("version"));
    }

    private List<String> readLabels(String value) {
        if (value == null) return List.of();
        try { return json.readValue(value, new TypeReference<>() {}); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Stored labels are invalid", failure); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalArgumentException("Labels are invalid", failure); }
    }

    private long count(String sql, Object... args) {
        var value = jdbc.queryForObject(sql, Long.class, args);
        return value == null ? 0 : value;
    }

    private static Timestamp timestamp(Instant value) { return Timestamp.from(value); }
    private static Long positive(String value, String name) {
        try { var parsed = Long.parseLong(value); if (parsed <= 0) throw new NumberFormatException(); return parsed; }
        catch (RuntimeException failure) { throw invalid(name + " is invalid"); }
    }
    private static String token(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum || !value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0," + (maximum - 1) + "}$")) throw invalid(name + " is invalid");
        return value;
    }
    private static String correlation(String value) { return value == null || value.isBlank() ? "platform-work" : token(value, "correlation", 128); }
    private static String text(String value, String name, int maximum, boolean nullable) {
        if (nullable && (value == null || value.isBlank())) return null;
        if (value == null || value.isBlank()) throw invalid(name + " is required");
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) throw invalid(name + " is too long");
        return value;
    }
    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Enum.valueOf(type, value); }
        catch (RuntimeException failure) { throw invalid("filter is invalid"); }
    }
    private static String sha256(String value) {
        try {
            var bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var result = new StringBuilder(64);
            for (byte item : bytes) result.append(String.format("%02x", item));
            return result.toString();
        } catch (NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private static BusinessException invalid(String message) { return new BusinessException("PLATFORM_WORK_REQUEST_INVALID", message, HttpStatus.BAD_REQUEST); }
    private static BusinessException conflict() { return new BusinessException("PLATFORM_WORK_VERSION_CONFLICT", "Work item version is stale", HttpStatus.CONFLICT); }
}
