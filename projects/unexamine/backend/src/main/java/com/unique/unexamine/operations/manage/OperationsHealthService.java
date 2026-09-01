package com.unique.unexamine.operations.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.operations.base.entity.OpsHealthItem;
import com.unique.unexamine.operations.base.entity.OpsHealthRun;
import com.unique.unexamine.operations.base.service.OpsHealthItemBaseService;
import com.unique.unexamine.operations.base.service.OpsHealthRunBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class OperationsHealthService {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final StartupPreflightChecks preflight;
    private final OpsHealthRunBaseService runs;
    private final OpsHealthItemBaseService items;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuditRecorder auditRecorder;
    private final String releaseVersion;

    public OperationsHealthService(
            StartupPreflightChecks preflight,
            OpsHealthRunBaseService runs,
            OpsHealthItemBaseService items,
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuditRecorder auditRecorder,
            @Value("${app.release-version:unknown}") String releaseVersion) {
        this.preflight = preflight;
        this.runs = runs;
        this.items = items;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.auditRecorder = auditRecorder;
        this.releaseVersion = releaseVersion;
    }

    @Transactional(readOnly = true)
    public OperationsHealthModels.HealthView latest(AuthenticatedContext context) {
        requireSystem(context);
        OpsHealthRun run = runs.selectList(new LambdaQueryWrapper<OpsHealthRun>()
                        .eq(OpsHealthRun::getSystemId, context.systemId())
                        .orderByDesc(OpsHealthRun::getId).last("limit 20"))
                .stream().filter(candidate -> Objects.equals(number(summary(candidate).get("tenantId")), context.tenantId()))
                .findFirst().orElse(null);
        return run == null ? null : view(context, run, healthItems(run.getId()));
    }

    @Transactional
    public OperationsHealthModels.HealthView recheck(AuthenticatedContext context, String requestId) {
        requireSystem(context);
        LocalDateTime started = LocalDateTime.now();
        OpsHealthRun run = new OpsHealthRun();
        run.setContextType("SYSTEM");
        run.setPlatformId(context.platformId());
        run.setSystemId(context.systemId());
        run.setRunType("MANUAL_RECHECK");
        run.setReleaseVersion(releaseVersion);
        run.setStatus("RUNNING");
        run.setSummaryJson(json(Map.of(
                "requestId", requestId,
                "traceId", requestId,
                "tenantId", context.tenantId(),
                "accountId", context.accountId(),
                "ready", false,
                "structuredContext", Map.of("systemId", context.systemId(),
                        "tenantId", context.tenantId(), "userId", context.accountId()))));
        run.setStartedByAccountId(context.accountId());
        run.setStartedAt(started);
        runs.insert(run);

        List<StartupPreflightChecks.Check> results = preflight.inspect();
        for (StartupPreflightChecks.Check result : results) {
            OpsHealthItem item = new OpsHealthItem();
            item.setHealthRunId(run.getId());
            item.setCheckCode(result.code());
            item.setCheckName(result.name());
            item.setCategory(result.category());
            item.setStatus(result.status());
            item.setMessage(result.message());
            item.setMetricJson(json(result.metric()));
            item.setCheckedAt(LocalDateTime.now());
            items.insert(item);
        }

        boolean ready = results.stream().filter(StartupPreflightChecks.Check::critical)
                .allMatch(StartupPreflightChecks.Check::passed);
        String migrationVersion = results.stream().filter(item -> "DATABASE_MIGRATION".equals(item.code()))
                .map(item -> String.valueOf(item.metric().getOrDefault("version", "unknown"))).findFirst().orElse("unknown");
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("requestId", requestId);
        summary.put("traceId", requestId);
        summary.put("tenantId", context.tenantId());
        summary.put("accountId", context.accountId());
        summary.put("ready", ready);
        summary.put("passed", results.stream().filter(StartupPreflightChecks.Check::passed).count());
        summary.put("failed", results.stream().filter(item -> !item.passed()).count());
        summary.put("migrationVersion", migrationVersion);
        summary.put("releaseVersion", releaseVersion);
        summary.put("structuredContext", Map.of("systemId", context.systemId(),
                "tenantId", context.tenantId(), "userId", context.accountId()));
        run.setStatus(ready ? "READY" : "NOT_READY");
        run.setSummaryJson(json(summary));
        run.setFinishedAt(LocalDateTime.now());
        runs.updateById(run);

        auditRecorder.recordWithPermissionSnapshot(requestId, context.accountId(), context.systemId(), context.tenantId(),
                context.memberId(), "OPERATIONS_HEALTH_RECHECKED", "OPS_HEALTH_RUN", String.valueOf(run.getId()),
                run.getStatus(), Map.of("roleIds", context.roleIds(), "permissions", context.permissions()),
                Map.of("ready", ready, "passed", summary.get("passed"), "failed", summary.get("failed"),
                        "releaseVersion", releaseVersion, "migrationVersion", migrationVersion));
        return view(context, run, healthItems(run.getId()));
    }

    @Transactional(readOnly = true)
    public OperationsHealthModels.ObservationView observe(AuthenticatedContext context, String rawRequestId) {
        requireSystem(context);
        String requestId = rawRequestId == null ? "" : rawRequestId.strip();
        if (!requestId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new DomainException("REQUEST_ID_INVALID", "requestId 必须是 8 到 64 位字母、数字、下划线或短横线",
                    HttpStatus.BAD_REQUEST);
        }

        List<Map<String, Object>> requestLogs = rows(
                "select request_id,trace_id,system_id,tenant_id,account_id,client_source,http_method,request_path,"
                        + "status_code,result_code,duration_millis,occurred_at from ops_request_log "
                        + "where request_id=? and system_id=? and tenant_id=? order by id",
                requestId, context.systemId(), context.tenantId());
        List<Map<String, Object>> audits = rows(
                "select id,trace_id,request_id,actor_account_id,event_category,event_code,object_type,object_id,"
                        + "result_code,occurred_at from audit_event where system_id=? and tenant_id=? "
                        + "and (request_id=? or trace_id=?) order by id",
                context.systemId(), context.tenantId(), requestId, requestId);
        List<Map<String, Object>> applicationCalls = rows(
                "select id,request_id,trace_id,application_id,resource_type,resource_id,action_code,status,response_code,"
                        + "duration_millis,called_at,finished_at from app_call where target_system_id=? and target_tenant_id=? "
                        + "and (request_id=? or trace_id=?) order by id",
                context.systemId(), context.tenantId(), requestId, requestId);
        List<Map<String, Object>> flowEvents = audits.stream()
                .filter(row -> text(row.get("event_code")).startsWith("FLOW_")
                        || text(row.get("object_type")).startsWith("FLOW_"))
                .toList();
        Set<Long> jobIds = new LinkedHashSet<>();
        audits.stream().filter(row -> "BACKGROUND_JOB".equals(text(row.get("object_type"))))
                .map(row -> longOrNull(row.get("object_id"))).filter(Objects::nonNull).forEach(jobIds::add);
        List<Map<String, Object>> jobs = jobIds.isEmpty() ? List.of() : rows(
                "select id,job_type,source_type,source_id,status,progress_current,progress_total,attempt_count,max_attempts,"
                        + "error_code,created_at,started_at,finished_at from job_background where system_id=? and tenant_id=? "
                        + "and id in (" + placeholders(jobIds.size()) + ") order by id",
                concat(List.of(context.systemId(), context.tenantId()), new ArrayList<>(jobIds)).toArray());

        List<OpsHealthRun> matchingHealth = runs.selectList(new LambdaQueryWrapper<OpsHealthRun>()
                        .eq(OpsHealthRun::getSystemId, context.systemId()).orderByDesc(OpsHealthRun::getId).last("limit 100"))
                .stream().filter(run -> requestId.equals(text(summary(run).get("requestId")))
                        && Objects.equals(number(summary(run).get("tenantId")), context.tenantId())).toList();

        List<OperationsHealthModels.ObservationEntry> logs = new ArrayList<>();
        for (Map<String, Object> row : requestLogs) {
            logs.add(new OperationsHealthModels.ObservationEntry("HTTP", "STRUCTURED_REQUEST", text(row.get("result_code")),
                    text(row.get("http_method")) + " " + text(row.get("request_path")), date(row.get("occurred_at")),
                    Map.of("clientSource", text(row.get("client_source")), "statusCode", value(row.get("status_code")),
                            "durationMillis", value(row.get("duration_millis")))));
        }
        for (Map<String, Object> row : audits) {
            logs.add(new OperationsHealthModels.ObservationEntry("AUDIT", text(row.get("event_category")),
                    text(row.get("result_code")), text(row.get("event_code")), date(row.get("occurred_at")),
                    Map.of("objectType", text(row.get("object_type")), "objectId", text(row.get("object_id")))));
        }
        for (OpsHealthRun run : matchingHealth) {
            logs.add(new OperationsHealthModels.ObservationEntry("HEALTH", "DEPENDENCY_CHECK", run.getStatus(),
                    "系统体检 #" + run.getId(), format(run.getFinishedAt()), Map.of("healthRunId", run.getId())));
        }
        logs.sort(Comparator.comparing(OperationsHealthModels.ObservationEntry::occurredAt,
                Comparator.nullsLast(String::compareTo)));

        long duration = requestLogs.stream().mapToLong(row -> longValue(row.get("duration_millis"))).sum();
        int healthChecks = matchingHealth.stream().mapToInt(run -> healthItems(run.getId()).size()).sum();
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("requestCount", requestLogs.size());
        metrics.put("serverDurationMillis", duration);
        metrics.put("auditCount", audits.size());
        metrics.put("jobCount", jobs.size());
        metrics.put("applicationCallCount", applicationCalls.size());
        metrics.put("flowEventCount", flowEvents.size());
        metrics.put("healthCheckCount", healthChecks);

        boolean web = requestLogs.stream().anyMatch(row -> "WEB".equals(text(row.get("client_source"))));
        boolean database = !audits.isEmpty() || !matchingHealth.isEmpty() || !applicationCalls.isEmpty() || !jobs.isEmpty();
        List<OperationsHealthModels.ObservationStage> stages = List.of(
                stage("FRONTEND", "前端请求", web, web ? "X-Client-Source=WEB" : "没有匹配的 Web 请求记录"),
                stage("BACKEND", "后端请求", !requestLogs.isEmpty(), requestLogs.size() + " 条结构化请求日志"),
                stage("DATABASE", "数据库持久化", database, database ? "已读回持久化业务或审计记录" : "没有匹配的持久化记录"),
                stage("APPLICATION", "应用调用", !applicationCalls.isEmpty(), applicationCalls.size() + " 条应用调用"),
                stage("FLOW", "Flow", !flowEvents.isEmpty(), flowEvents.size() + " 条 Flow 事件"),
                stage("BACKGROUND_JOB", "后台作业", !jobs.isEmpty(), jobs.size() + " 个关联作业"),
                stage("AUDIT", "运维审计", !audits.isEmpty(), audits.size() + " 条不可删除审计"));
        boolean observable = stages.stream().anyMatch(OperationsHealthModels.ObservationStage::observed);
        Long accountId = requestLogs.stream().map(row -> number(row.get("account_id"))).filter(Objects::nonNull)
                .findFirst().orElseGet(() -> audits.stream().map(row -> number(row.get("actor_account_id")))
                        .filter(Objects::nonNull).findFirst().orElse(null));
        return new OperationsHealthModels.ObservationView(requestId, requestId, context.systemId(), context.tenantId(),
                accountId, observable, stages, logs, metrics, jobs, audits, applicationCalls, flowEvents);
    }

    private OperationsHealthModels.ObservationStage stage(String code, String name, boolean observed, String evidence) {
        return new OperationsHealthModels.ObservationStage(code, name, observed, evidence);
    }

    private OperationsHealthModels.HealthView view(
            AuthenticatedContext context, OpsHealthRun run, List<OpsHealthItem> healthItems) {
        Map<String, Object> summary = summary(run);
        List<OperationsHealthModels.HealthItem> itemViews = healthItems.stream().map(item ->
                new OperationsHealthModels.HealthItem(item.getId(), item.getCheckCode(), item.getCheckName(),
                        item.getCategory(), item.getStatus(), item.getMessage(), parse(item.getMetricJson()),
                        format(item.getCheckedAt()))).toList();
        return new OperationsHealthModels.HealthView(run.getId(), run.getStatus(),
                Boolean.TRUE.equals(summary.get("ready")), run.getReleaseVersion(),
                text(summary.getOrDefault("migrationVersion", "unknown")), text(summary.get("requestId")),
                format(run.getStartedAt()), format(run.getFinishedAt()), itemViews, jobs(context), errors(context));
    }

    private List<OpsHealthItem> healthItems(Long runId) {
        return items.selectList(new LambdaQueryWrapper<OpsHealthItem>()
                .eq(OpsHealthItem::getHealthRunId, runId).orderByAsc(OpsHealthItem::getId));
    }

    private OperationsHealthModels.JobOverview jobs(AuthenticatedContext context) {
        Map<String, Long> counts = new LinkedHashMap<>();
        rows("select status,count(*) as total from job_background where system_id=? and tenant_id=? group by status",
                context.systemId(), context.tenantId()).forEach(row -> counts.put(text(row.get("status")), longValue(row.get("total"))));
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        return new OperationsHealthModels.JobOverview(counts.getOrDefault("QUEUED", 0L),
                counts.getOrDefault("RUNNING", 0L), counts.getOrDefault("RETRY_WAIT", 0L),
                counts.getOrDefault("FAILED", 0L) + counts.getOrDefault("PERMANENT_FAILED", 0L)
                        + counts.getOrDefault("TIMED_OUT", 0L),
                counts.getOrDefault("SUCCEEDED", 0L), total);
    }

    private List<OperationsHealthModels.ErrorSummary> errors(AuthenticatedContext context) {
        List<OperationsHealthModels.ErrorSummary> result = new ArrayList<>();
        rows("select request_id,result_code,status_code,request_path,occurred_at from ops_request_log "
                        + "where system_id=? and tenant_id=? and status_code>=400 order by id desc limit 8",
                context.systemId(), context.tenantId()).forEach(row -> result.add(new OperationsHealthModels.ErrorSummary(
                text(row.get("request_id")), "HTTP", text(row.get("result_code")), text(row.get("request_path")),
                String.valueOf(value(row.get("status_code"))), date(row.get("occurred_at")), "REQUEST", text(row.get("request_path")))));
        rows("select id,status,error_code,error_message,updated_at from job_background where system_id=? and tenant_id=? "
                        + "and error_code is not null order by id desc limit 8",
                context.systemId(), context.tenantId()).forEach(row -> result.add(new OperationsHealthModels.ErrorSummary(
                requestIdForJob(row.get("id")), "BACKGROUND_JOB", text(row.get("error_code")),
                safeError(row.get("error_message")), text(row.get("status")), date(row.get("updated_at")),
                "BACKGROUND_JOB", text(row.get("id")))));
        return result.stream().sorted(Comparator.comparing(OperationsHealthModels.ErrorSummary::occurredAt,
                        Comparator.nullsLast(Comparator.reverseOrder()))).limit(8).toList();
    }

    private String requestIdForJob(Object jobId) {
        List<Map<String, Object>> rows = rows("select request_id from audit_event where object_type='BACKGROUND_JOB' "
                + "and object_id=? order by id desc limit 1", text(jobId));
        return rows.isEmpty() ? "" : text(rows.getFirst().get("request_id"));
    }

    private String safeError(Object value) {
        String text = text(value);
        if (text.isBlank()) return "后台作业失败，请按 requestId 查看日志";
        String lowered = text.toLowerCase(Locale.ROOT);
        if (lowered.contains("password") || lowered.contains("secret") || lowered.contains("token")) {
            return "错误摘要包含敏感字段，已脱敏；请按 requestId 查看受控日志";
        }
        return text.length() > 240 ? text.substring(0, 240) : text;
    }

    private Map<String, Object> summary(OpsHealthRun run) {
        return parse(run.getSummaryJson());
    }

    private Map<String, Object> parse(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, MAP_TYPE); }
        catch (JsonProcessingException exception) { return Map.of("unreadable", true); }
    }

    private String json(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Operations evidence cannot be serialized", exception); }
    }

    private List<Map<String, Object>> rows(String sql, Object... parameters) {
        return jdbc.queryForList(sql, parameters).stream().map(this::normalized).toList();
    }

    private Map<String, Object> normalized(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key.toLowerCase(Locale.ROOT),
                value instanceof Timestamp timestamp ? timestamp.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : value));
        return result;
    }

    private List<Object> concat(List<?> left, List<?> right) {
        List<Object> result = new ArrayList<>(left);
        result.addAll(right);
        return result;
    }

    private String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
    private Object value(Object value) { return value == null ? 0 : value; }
    private long longValue(Object value) { return value instanceof Number number ? number.longValue() : 0; }
    private Long longOrNull(Object value) {
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException exception) { return null; }
    }
    private Long number(Object value) { return value instanceof Number number ? number.longValue() : longOrNull(value); }
    private String date(Object value) { return value == null ? null : String.valueOf(value); }
    private String format(LocalDateTime value) { return value == null ? null : value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); }

    private void requireSystem(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.accountId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "请先进入系统租户上下文", HttpStatus.CONFLICT);
        }
    }
}
