package com.unique.unexamine.work.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.base.entity.WorkFieldConfig;
import com.unique.unexamine.work.base.entity.WorkLog;
import com.unique.unexamine.work.base.entity.WorkProject;
import com.unique.unexamine.work.base.entity.WorkProjectMember;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.entity.WorkTaskMember;
import com.unique.unexamine.work.base.service.WorkFieldConfigBaseService;
import com.unique.unexamine.work.base.service.WorkLogBaseService;
import com.unique.unexamine.work.base.service.WorkProjectBaseService;
import com.unique.unexamine.work.base.service.WorkProjectMemberBaseService;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.base.service.WorkTaskMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class WorkConfigurationService {
    private static final Set<String> TARGET_TYPES = Set.of("PROJECT", "TASK", "LOG", "STATISTICS");
    private static final Set<String> FIELD_TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "DATETIME", "BOOLEAN", "DICTIONARY", "REFERENCE", "QUERY");
    private static final Pattern CODE = Pattern.compile("^[a-z][a-z0-9_-]{1,99}$");

    private final WorkFieldConfigBaseService configService;
    private final WorkTaskBaseService taskService;
    private final WorkTaskMemberBaseService taskMemberService;
    private final WorkProjectBaseService projectService;
    private final WorkProjectMemberBaseService projectMemberService;
    private final WorkLogBaseService logService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public WorkConfigurationService(
            WorkFieldConfigBaseService configService,
            WorkTaskBaseService taskService,
            WorkTaskMemberBaseService taskMemberService,
            WorkProjectBaseService projectService,
            WorkProjectMemberBaseService projectMemberService,
            WorkLogBaseService logService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.configService = configService;
        this.taskService = taskService;
        this.taskMemberService = taskMemberService;
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.logService = logService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public WorkConfigurationModels.ConfigurationView configuration(AuthenticatedContext context) {
        requireAction(context, "VIEW_CONFIG");
        List<WorkFieldConfig> own = ownConfigurations(context);
        List<WorkFieldConfig> platformDefaults = context.systemId() == null ? List.of()
                : platformConfigurations(context.platformId());
        List<WorkConfigurationModels.FieldView> inherited = platformDefaults.stream()
                .filter(this::hasPublication).map(row -> publishedView(row, "PLATFORM_DEFAULT")).toList();
        LinkedHashMap<String, WorkConfigurationModels.FieldView> effective = new LinkedHashMap<>();
        for (WorkFieldConfig row : platformDefaults) {
            if (hasPublication(row)) effective.put(key(row), publishedView(row, "PLATFORM_DEFAULT"));
        }
        for (WorkFieldConfig row : own) {
            if (hasPublication(row)) effective.put(key(row), publishedView(row,
                    context.systemId() == null ? "PLATFORM_DEFAULT" : "SYSTEM_OVERRIDE"));
        }
        return new WorkConfigurationModels.ConfigurationView(
                own.stream().map(row -> draftView(row, "OWN_DRAFT")).toList(), inherited,
                effective.values().stream().sorted(Comparator
                        .comparing(WorkConfigurationModels.FieldView::targetType)
                        .thenComparingInt(WorkConfigurationModels.FieldView::sortOrder)
                        .thenComparing(WorkConfigurationModels.FieldView::fieldCode)).toList());
    }

    @Transactional
    public WorkConfigurationModels.FieldView saveField(
            AuthenticatedContext context, String targetType, String fieldCode,
            WorkConfigurationModels.SaveFieldRequest input, String traceId) {
        requireAction(context, "CONFIGURE");
        String target = normalizeTarget(targetType);
        String code = normalizeCode(fieldCode);
        String fieldType = input.fieldType().strip().toUpperCase(Locale.ROOT);
        if (!FIELD_TYPES.contains(fieldType)) {
            throw invalid("WORK_CONFIG_FIELD_TYPE_INVALID", "工作字段类型无效");
        }
        if ("STATISTICS".equals(target) && !"QUERY".equals(fieldType)) {
            throw invalid("WORK_STATISTICS_QUERY_TYPE_REQUIRED", "统计查询定义必须使用 QUERY 类型");
        }
        validateSettings(target, input.settings());
        WorkFieldConfig row = ownConfiguration(context, target, code, false);
        if (row == null) {
            if (input.expectedVersion() != null) {
                throw conflict("WORK_CONFIG_VERSION_CONFLICT", "配置尚不存在，请刷新后重试");
            }
            row = new WorkFieldConfig();
            applyContext(row, context);
            row.setTargetType(target);
            row.setFieldCode(code);
            row.setVersion(0);
            row.setConfigJson(toJson(Map.of("settings", safeMap(input.settings()))));
            applyDraft(row, input.fieldName(), fieldType, input.required(), input.sortOrder(), input.settings());
            row.setStatus("DRAFT");
            configService.insert(row);
        } else {
            if (input.expectedVersion() == null || !Objects.equals(row.getVersion(), input.expectedVersion())) {
                throw conflict("WORK_CONFIG_VERSION_CONFLICT", "工作配置已被其他人修订，请刷新后重试");
            }
            LinkedHashMap<String, Object> root = new LinkedHashMap<>(readMap(row.getConfigJson()));
            root.put("settings", safeMap(input.settings()));
            row.setConfigJson(toJson(root));
            applyDraft(row, input.fieldName(), fieldType, input.required(), input.sortOrder(), input.settings());
            row.setStatus("DRAFT");
            row.setUpdatedAt(LocalDateTime.now());
            if (configService.updateById(row) != 1) {
                throw conflict("WORK_CONFIG_VERSION_CONFLICT", "工作配置已被其他人修订，请刷新后重试");
            }
        }
        audit(context, traceId, "WORK_CONFIG_DRAFT_SAVED", "WORK_FIELD_CONFIG", row.getId(),
                Map.of("targetType", target, "fieldCode", code));
        return draftView(configService.selectById(row.getId()), "OWN_DRAFT");
    }

    @Transactional
    public WorkConfigurationModels.FieldView publishField(
            AuthenticatedContext context, String targetType, String fieldCode,
            WorkConfigurationModels.PublishFieldRequest input, String traceId) {
        requireAction(context, "PUBLISH_CONFIG");
        WorkFieldConfig row = requireOwnForUpdate(context, normalizeTarget(targetType), normalizeCode(fieldCode));
        requireRowVersion(row, input.expectedVersion());
        LinkedHashMap<String, Object> root = new LinkedHashMap<>(readMap(row.getConfigJson()));
        LinkedHashMap<String, Object> publication = new LinkedHashMap<>(map(root.get("publication")));
        List<Map<String, Object>> versions = new ArrayList<>(maps(publication.get("versions")));
        int next = versions.stream().mapToInt(item -> integer(item.get("version"), 0)).max().orElse(0) + 1;
        versions.add(publicationVersion(next, null, input.changeSummary().strip(), context.accountId(), snapshot(row, root)));
        publication.put("currentVersion", next);
        publication.put("versions", versions);
        root.put("publication", publication);
        row.setConfigJson(toJson(root));
        row.setStatus("PUBLISHED");
        row.setUpdatedAt(LocalDateTime.now());
        if (configService.updateById(row) != 1) {
            throw conflict("WORK_CONFIG_VERSION_CONFLICT", "工作配置发布指针已被其他人更新");
        }
        audit(context, traceId, "WORK_CONFIG_PUBLISHED", "WORK_FIELD_CONFIG", row.getId(),
                Map.of("publicationVersion", next, "targetType", row.getTargetType(), "fieldCode", row.getFieldCode()));
        return draftView(configService.selectById(row.getId()), "OWN_DRAFT");
    }

    @Transactional
    public WorkConfigurationModels.FieldView rollbackField(
            AuthenticatedContext context, String targetType, String fieldCode,
            WorkConfigurationModels.RollbackFieldRequest input, String traceId) {
        requireAction(context, "PUBLISH_CONFIG");
        WorkFieldConfig row = requireOwnForUpdate(context, normalizeTarget(targetType), normalizeCode(fieldCode));
        requireRowVersion(row, input.expectedVersion());
        LinkedHashMap<String, Object> root = new LinkedHashMap<>(readMap(row.getConfigJson()));
        LinkedHashMap<String, Object> publication = new LinkedHashMap<>(map(root.get("publication")));
        List<Map<String, Object>> versions = new ArrayList<>(maps(publication.get("versions")));
        Map<String, Object> target = versions.stream()
                .filter(item -> integer(item.get("version"), 0) == input.targetPublicationVersion())
                .findFirst().orElseThrow(() -> invalid("WORK_CONFIG_ROLLBACK_VERSION_NOT_FOUND", "回滚目标版本不存在"));
        Map<String, Object> snapshot = map(target.get("snapshot"));
        restoreSnapshot(row, snapshot);
        root.put("settings", map(snapshot.get("settings")));
        int next = versions.stream().mapToInt(item -> integer(item.get("version"), 0)).max().orElse(0) + 1;
        versions.add(publicationVersion(next, input.targetPublicationVersion(), input.reason().strip(),
                context.accountId(), snapshot));
        publication.put("currentVersion", next);
        publication.put("versions", versions);
        root.put("publication", publication);
        row.setConfigJson(toJson(root));
        row.setStatus("PUBLISHED");
        row.setUpdatedAt(LocalDateTime.now());
        if (configService.updateById(row) != 1) {
            throw conflict("WORK_CONFIG_VERSION_CONFLICT", "工作配置回滚时已被其他人更新");
        }
        audit(context, traceId, "WORK_CONFIG_ROLLED_BACK", "WORK_FIELD_CONFIG", row.getId(),
                Map.of("targetPublicationVersion", input.targetPublicationVersion(), "newPublicationVersion", next));
        return draftView(configService.selectById(row.getId()), "OWN_DRAFT");
    }

    @Transactional(readOnly = true)
    public WorkConfigurationModels.CalendarView calendar(
            AuthenticatedContext context, LocalDate from, LocalDate to, Long projectId, Long accountId) {
        requireAction(context, "VIEW_CALENDAR");
        if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 366) {
            throw invalid("WORK_CALENDAR_RANGE_INVALID", "日历范围必须有效且不能超过 366 天");
        }
        if (accountId != null && !Objects.equals(accountId, context.accountId()) && !allowed(context, "VIEW_OTHERS")) {
            throw forbidden("WORK_CALENDAR_VIEW_OTHERS_DENIED", "按其他人员统计需要明确权限");
        }
        WorkProject selectedProject = projectId == null ? null : requireVisibleProject(context, projectId);
        boolean detailAvailable = allowed(context, "VIEW_DETAIL");
        LinkedHashMap<LocalDate, DayAccumulator> days = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) days.put(date, new DayAccumulator(date));

        List<WorkTask> tasks = scopedTasks(context).stream()
                .filter(task -> canViewTask(context, task))
                .filter(task -> selectedProject == null || Objects.equals(task.getProjectId(), selectedProject.getId()))
                .filter(task -> accountId == null || Objects.equals(task.getOwnerAccountId(), accountId)).toList();
        LocalDate today = LocalDate.now();
        for (WorkTask task : tasks) {
            LocalDate dueDate = task.getDueAt() == null ? null : task.getDueAt().toLocalDate();
            LocalDate completedDate = task.getCompletedAt() == null ? null : task.getCompletedAt().toLocalDate();
            if (dueDate != null && days.containsKey(dueDate)) {
                DayAccumulator day = days.get(dueDate);
                day.tasksDue++;
                boolean overdue = dueDate.isBefore(today) && !"COMPLETED".equals(task.getStatus());
                if (overdue) day.overdueTasks++;
                if (overdue || "BLOCKED".equals(task.getStatus())) day.riskTasks++;
                day.addTask(task);
            }
            if (completedDate != null && days.containsKey(completedDate)) {
                days.get(completedDate).tasksCompleted++;
                days.get(completedDate).addTask(task);
            }
        }

        List<WorkLog> logs = scopedLogs(context).stream()
                .filter(log -> Objects.equals(log.getAuthorAccountId(), context.accountId()) || allowed(context, "VIEW_OTHERS"))
                .filter(log -> accountId == null || Objects.equals(log.getAuthorAccountId(), accountId))
                .filter(log -> projectId == null || Objects.equals(asLong(readMap(log.getCustomValuesJson()).get("projectId")), projectId))
                .toList();
        for (WorkLog log : logs) {
            if (!days.containsKey(log.getWorkDate())) continue;
            DayAccumulator day = days.get(log.getWorkDate());
            day.logCount++;
            day.logMinutes += log.getDurationMinutes() == null ? 0 : log.getDurationMinutes();
            day.logs.add(new WorkConfigurationModels.LogCalendarItem(log.getId(), log.getTitle(), log.getStatus(),
                    log.getAuthorAccountId(), log.getDurationMinutes() == null ? 0 : log.getDurationMinutes(),
                    log.getWorkDate()));
        }

        List<WorkProject> projects = scopedProjects(context).stream().filter(project -> canViewProject(context, project))
                .filter(project -> selectedProject == null || Objects.equals(project.getId(), selectedProject.getId())).toList();
        for (WorkProject project : projects) {
            if (project.getDueDate() == null || !days.containsKey(project.getDueDate())) continue;
            DayAccumulator day = days.get(project.getDueDate());
            day.projectMilestones++;
            day.milestones.add(new WorkConfigurationModels.ProjectMilestoneItem(
                    project.getId(), project.getName(), project.getStatus(), project.getDueDate()));
        }

        List<WorkConfigurationModels.CalendarDay> views = days.values().stream()
                .map(day -> day.view(detailAvailable)).toList();
        WorkConfigurationModels.CalendarSummary summary = new WorkConfigurationModels.CalendarSummary(
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::tasksDue).sum(),
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::tasksCompleted).sum(),
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::overdueTasks).sum(),
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::riskTasks).sum(),
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::logCount).sum(),
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::logMinutes).sum(),
                views.stream().mapToInt(WorkConfigurationModels.CalendarDay::projectMilestones).sum());
        return new WorkConfigurationModels.CalendarView(context.systemId() == null ? "PLATFORM" : "SYSTEM",
                context.systemId(), context.tenantId(), from, to, projectId, accountId,
                detailAvailable, summary, views);
    }

    private void applyDraft(WorkFieldConfig row, String name, String fieldType, Boolean required,
                            Integer sortOrder, Map<String, Object> settings) {
        row.setFieldName(name.strip());
        row.setFieldType(fieldType);
        row.setRequired(Boolean.TRUE.equals(required));
        row.setSortOrder(sortOrder == null ? 0 : sortOrder);
    }

    private Map<String, Object> snapshot(WorkFieldConfig row, Map<String, Object> root) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("fieldName", row.getFieldName());
        snapshot.put("fieldType", row.getFieldType());
        snapshot.put("required", Boolean.TRUE.equals(row.getRequired()));
        snapshot.put("sortOrder", row.getSortOrder());
        snapshot.put("settings", map(root.get("settings")));
        return snapshot;
    }

    private Map<String, Object> publicationVersion(
            int version, Integer basedOn, String summary, Long actor, Map<String, Object> snapshot) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("version", version);
        result.put("basedOnVersion", basedOn);
        result.put("changeSummary", summary);
        result.put("publishedAt", LocalDateTime.now().toString());
        result.put("publishedByAccountId", actor);
        result.put("snapshot", new LinkedHashMap<>(snapshot));
        return result;
    }

    private void restoreSnapshot(WorkFieldConfig row, Map<String, Object> snapshot) {
        row.setFieldName(string(snapshot.get("fieldName")));
        row.setFieldType(string(snapshot.get("fieldType")));
        row.setRequired(Boolean.TRUE.equals(snapshot.get("required")));
        row.setSortOrder(integer(snapshot.get("sortOrder"), 0));
    }

    private WorkConfigurationModels.FieldView draftView(WorkFieldConfig row, String source) {
        Map<String, Object> root = readMap(row.getConfigJson());
        return fieldView(row, row.getFieldName(), row.getFieldType(), Boolean.TRUE.equals(row.getRequired()),
                row.getSortOrder() == null ? 0 : row.getSortOrder(), map(root.get("settings")), row.getStatus(), source);
    }

    private WorkConfigurationModels.FieldView publishedView(WorkFieldConfig row, String source) {
        Map<String, Object> root = readMap(row.getConfigJson());
        Map<String, Object> publication = map(root.get("publication"));
        int current = integer(publication.get("currentVersion"), 0);
        Map<String, Object> snapshot = maps(publication.get("versions")).stream()
                .filter(item -> integer(item.get("version"), 0) == current)
                .map(item -> map(item.get("snapshot"))).findFirst().orElse(Map.of());
        return fieldView(row, string(snapshot.get("fieldName")), string(snapshot.get("fieldType")),
                Boolean.TRUE.equals(snapshot.get("required")), integer(snapshot.get("sortOrder"), 0),
                map(snapshot.get("settings")), "PUBLISHED", source);
    }

    private WorkConfigurationModels.FieldView fieldView(
            WorkFieldConfig row, String name, String type, boolean required, int sortOrder,
            Map<String, Object> settings, String status, String source) {
        Map<String, Object> publication = map(readMap(row.getConfigJson()).get("publication"));
        Integer current = integer(publication.get("currentVersion"), 0);
        if (current == 0) current = null;
        List<WorkConfigurationModels.PublicationVersionView> versions = maps(publication.get("versions")).stream()
                .map(item -> new WorkConfigurationModels.PublicationVersionView(
                        integer(item.get("version"), 0), nullableInteger(item.get("basedOnVersion")),
                        string(item.get("changeSummary")), dateTime(item.get("publishedAt")),
                        asLong(item.get("publishedByAccountId")), map(item.get("snapshot")))).toList();
        return new WorkConfigurationModels.FieldView(row.getId(), row.getContextType(), row.getPlatformId(),
                row.getSystemId(), row.getTenantId(), row.getTargetType(), row.getFieldCode(), name, type,
                required, sortOrder, settings, status, row.getVersion(), current, versions, source);
    }

    private void validateSettings(String target, Map<String, Object> settings) {
        if (!"STATISTICS".equals(target)) return;
        Map<String, Object> values = safeMap(settings);
        Object groupBy = values.get("groupBy");
        Object metrics = values.get("metrics");
        if (!(groupBy instanceof List<?> groups) || groups.isEmpty()
                || !(metrics instanceof List<?> metricList) || metricList.isEmpty()) {
            throw invalid("WORK_STATISTICS_QUERY_INVALID", "统计查询必须定义非空 groupBy 和 metrics");
        }
        Set<String> allowedGroups = Set.of("DATE", "ACCOUNT", "PROJECT", "STATUS", "PRIORITY");
        Set<String> allowedMetrics = Set.of("TASK_COUNT", "COMPLETED_COUNT", "OVERDUE_COUNT", "RISK_COUNT",
                "LOG_COUNT", "LOG_MINUTES");
        if (groups.stream().map(String::valueOf).map(String::toUpperCase).anyMatch(item -> !allowedGroups.contains(item))
                || metricList.stream().map(String::valueOf).map(String::toUpperCase)
                .anyMatch(item -> !allowedMetrics.contains(item))) {
            throw invalid("WORK_STATISTICS_QUERY_INVALID", "统计查询包含不支持的分组或指标");
        }
    }

    private WorkFieldConfig requireOwnForUpdate(AuthenticatedContext context, String target, String code) {
        WorkFieldConfig row = ownConfiguration(context, target, code, true);
        if (row == null) throw notFound("WORK_CONFIG_NOT_FOUND", "工作配置不存在或不在当前上下文");
        return row;
    }

    private WorkFieldConfig ownConfiguration(
            AuthenticatedContext context, String target, String code, boolean forUpdate) {
        var query = Wrappers.<WorkFieldConfig>lambdaQuery()
                .eq(WorkFieldConfig::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkFieldConfig::getPlatformId, context.platformId())
                .isNull(context.systemId() == null, WorkFieldConfig::getSystemId)
                .eq(context.systemId() != null, WorkFieldConfig::getSystemId, context.systemId())
                .isNull(context.tenantId() == null, WorkFieldConfig::getTenantId)
                .eq(context.tenantId() != null, WorkFieldConfig::getTenantId, context.tenantId())
                .eq(WorkFieldConfig::getTargetType, target).eq(WorkFieldConfig::getFieldCode, code);
        if (forUpdate) query.last("FOR UPDATE");
        return configService.selectList(query).stream().findFirst().orElse(null);
    }

    private List<WorkFieldConfig> ownConfigurations(AuthenticatedContext context) {
        return configService.selectList(Wrappers.<WorkFieldConfig>lambdaQuery()
                .eq(WorkFieldConfig::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkFieldConfig::getPlatformId, context.platformId())
                .isNull(context.systemId() == null, WorkFieldConfig::getSystemId)
                .eq(context.systemId() != null, WorkFieldConfig::getSystemId, context.systemId())
                .isNull(context.tenantId() == null, WorkFieldConfig::getTenantId)
                .eq(context.tenantId() != null, WorkFieldConfig::getTenantId, context.tenantId())
                .orderByAsc(WorkFieldConfig::getTargetType).orderByAsc(WorkFieldConfig::getSortOrder));
    }

    private List<WorkFieldConfig> platformConfigurations(Long platformId) {
        return configService.selectList(Wrappers.<WorkFieldConfig>lambdaQuery()
                .eq(WorkFieldConfig::getContextType, "PLATFORM")
                .eq(WorkFieldConfig::getPlatformId, platformId)
                .isNull(WorkFieldConfig::getSystemId).isNull(WorkFieldConfig::getTenantId)
                .orderByAsc(WorkFieldConfig::getTargetType).orderByAsc(WorkFieldConfig::getSortOrder));
    }

    private boolean hasPublication(WorkFieldConfig row) {
        return integer(map(readMap(row.getConfigJson()).get("publication")).get("currentVersion"), 0) > 0;
    }

    private void requireRowVersion(WorkFieldConfig row, Integer expected) {
        if (!Objects.equals(row.getVersion(), expected)) {
            throw conflict("WORK_CONFIG_VERSION_CONFLICT", "工作配置已被其他人修订，请刷新后重试");
        }
    }

    private WorkProject requireVisibleProject(AuthenticatedContext context, Long projectId) {
        WorkProject project = projectService.selectById(projectId);
        if (project == null || !inContext(context, project.getContextType(), project.getPlatformId(),
                project.getSystemId(), project.getTenantId()) || !canViewProject(context, project)) {
            throw notFound("WORK_CALENDAR_PROJECT_NOT_FOUND", "项目不存在或不在授权范围");
        }
        return project;
    }

    private boolean canViewTask(AuthenticatedContext context, WorkTask task) {
        if (allowed(context, "VIEW_ALL") || Objects.equals(task.getOwnerAccountId(), context.accountId())) return true;
        boolean member = !taskMemberService.selectList(Wrappers.<WorkTaskMember>lambdaQuery()
                .eq(WorkTaskMember::getTaskId, task.getId())
                .eq(WorkTaskMember::getAccountId, context.accountId())).isEmpty();
        if (member) return true;
        WorkProject project = task.getProjectId() == null ? null : projectService.selectById(task.getProjectId());
        return project != null && canViewProject(context, project);
    }

    private boolean canViewProject(AuthenticatedContext context, WorkProject project) {
        return allowed(context, "VIEW_ALL") || Objects.equals(project.getOwnerAccountId(), context.accountId())
                || !projectMemberService.selectList(Wrappers.<WorkProjectMember>lambdaQuery()
                .eq(WorkProjectMember::getProjectId, project.getId())
                .eq(WorkProjectMember::getAccountId, context.accountId())
                .eq(WorkProjectMember::getStatus, "ACTIVE")).isEmpty();
    }

    private List<WorkTask> scopedTasks(AuthenticatedContext context) {
        return taskService.selectList(Wrappers.<WorkTask>lambdaQuery()
                .eq(WorkTask::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkTask::getPlatformId, context.platformId())
                .isNull(context.systemId() == null, WorkTask::getSystemId)
                .eq(context.systemId() != null, WorkTask::getSystemId, context.systemId())
                .isNull(context.tenantId() == null, WorkTask::getTenantId)
                .eq(context.tenantId() != null, WorkTask::getTenantId, context.tenantId()));
    }

    private List<WorkLog> scopedLogs(AuthenticatedContext context) {
        return logService.selectList(Wrappers.<WorkLog>lambdaQuery()
                .eq(WorkLog::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkLog::getPlatformId, context.platformId())
                .isNull(context.systemId() == null, WorkLog::getSystemId)
                .eq(context.systemId() != null, WorkLog::getSystemId, context.systemId())
                .isNull(context.tenantId() == null, WorkLog::getTenantId)
                .eq(context.tenantId() != null, WorkLog::getTenantId, context.tenantId()));
    }

    private List<WorkProject> scopedProjects(AuthenticatedContext context) {
        return projectService.selectList(Wrappers.<WorkProject>lambdaQuery()
                .eq(WorkProject::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkProject::getPlatformId, context.platformId())
                .isNull(context.systemId() == null, WorkProject::getSystemId)
                .eq(context.systemId() != null, WorkProject::getSystemId, context.systemId())
                .isNull(context.tenantId() == null, WorkProject::getTenantId)
                .eq(context.tenantId() != null, WorkProject::getTenantId, context.tenantId()));
    }

    private boolean inContext(AuthenticatedContext context, String contextType, Long platformId,
                              Long systemId, Long tenantId) {
        return Objects.equals(platformId, context.platformId())
                && Objects.equals(contextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                && Objects.equals(systemId, context.systemId()) && Objects.equals(tenantId, context.tenantId());
    }

    private void applyContext(WorkFieldConfig row, AuthenticatedContext context) {
        row.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        row.setPlatformId(context.platformId());
        row.setSystemId(context.systemId());
        row.setTenantId(context.tenantId());
    }

    private String normalizeTarget(String targetType) {
        String target = targetType == null ? "" : targetType.strip().toUpperCase(Locale.ROOT);
        if (!TARGET_TYPES.contains(target)) throw invalid("WORK_CONFIG_TARGET_INVALID", "工作配置目标类型无效");
        return target;
    }

    private String normalizeCode(String fieldCode) {
        String code = fieldCode == null ? "" : fieldCode.strip().toLowerCase(Locale.ROOT);
        if (!CODE.matcher(code).matches()) throw invalid("WORK_CONFIG_CODE_INVALID", "工作配置编码无效");
        return code;
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (!allowed(context, action)) throw forbidden("PERMISSION_DENIED", "没有工作 " + action + " 权限");
    }

    private boolean allowed(AuthenticatedContext context, String action) {
        String resourceCode = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        return permissionChecker.allows(context, "WORK", resourceCode, action)
                || permissionChecker.allows(context, "WORK", "*", action);
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode,
                       String objectType, Object objectId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, objectType, String.valueOf(objectId), "SUCCESS", detail);
    }

    private String key(WorkFieldConfig row) {
        return row.getTargetType() + ":" + row.getFieldCode();
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(this::map).toList();
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, new TypeReference<>() { }); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("cannot read work configuration JSON", exception); }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("无法保存工作配置", exception); }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int integer(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private Integer nullableInteger(Object value) {
        return value == null ? null : integer(value, 0);
    }

    private Long asLong(Object value) {
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return null; }
    }

    private LocalDateTime dateTime(Object value) {
        try { return value == null ? null : LocalDateTime.parse(String.valueOf(value)); }
        catch (RuntimeException ignored) { return null; }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private DomainException forbidden(String code, String message) {
        return new DomainException(code, message, HttpStatus.FORBIDDEN);
    }

    private DomainException notFound(String code, String message) {
        return new DomainException(code, message, HttpStatus.NOT_FOUND);
    }

    private static final class DayAccumulator {
        private final LocalDate date;
        private int tasksDue;
        private int tasksCompleted;
        private int overdueTasks;
        private int riskTasks;
        private int logCount;
        private int logMinutes;
        private int projectMilestones;
        private final List<WorkConfigurationModels.TaskCalendarItem> tasks = new ArrayList<>();
        private final List<WorkConfigurationModels.LogCalendarItem> logs = new ArrayList<>();
        private final List<WorkConfigurationModels.ProjectMilestoneItem> milestones = new ArrayList<>();

        private DayAccumulator(LocalDate date) {
            this.date = date;
        }

        private void addTask(WorkTask task) {
            if (tasks.stream().anyMatch(item -> Objects.equals(item.id(), task.getId()))) return;
            tasks.add(new WorkConfigurationModels.TaskCalendarItem(task.getId(), task.getTitle(), task.getStatus(),
                    task.getPriority(), task.getProjectId(), task.getOwnerAccountId(), task.getDueAt(),
                    task.getCompletedAt()));
        }

        private WorkConfigurationModels.CalendarDay view(boolean detailAvailable) {
            return new WorkConfigurationModels.CalendarDay(date, tasksDue, tasksCompleted, overdueTasks, riskTasks,
                    logCount, logMinutes, projectMilestones, detailAvailable,
                    detailAvailable ? List.copyOf(tasks) : List.of(),
                    detailAvailable ? List.copyOf(logs) : List.of(),
                    detailAvailable ? List.copyOf(milestones) : List.of());
        }
    }
}
