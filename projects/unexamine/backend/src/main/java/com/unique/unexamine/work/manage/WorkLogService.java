package com.unique.unexamine.work.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.base.entity.WorkLog;
import com.unique.unexamine.work.base.entity.WorkLogRevision;
import com.unique.unexamine.work.base.entity.WorkProject;
import com.unique.unexamine.work.base.entity.WorkProjectMember;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.entity.WorkTaskMember;
import com.unique.unexamine.work.base.service.WorkLogBaseService;
import com.unique.unexamine.work.base.service.WorkLogRevisionBaseService;
import com.unique.unexamine.work.base.service.WorkProjectBaseService;
import com.unique.unexamine.work.base.service.WorkProjectMemberBaseService;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.base.service.WorkTaskMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class WorkLogService {
    private static final Set<String> STATUSES = Set.of("DRAFT", "SUBMITTED", "WITHDRAWN");
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("DRAFT", "SUBMITTED"),
            "SUBMITTED", Set.of("SUBMITTED", "WITHDRAWN"),
            "WITHDRAWN", Set.of("WITHDRAWN", "DRAFT"));

    private final WorkLogBaseService logService;
    private final WorkLogRevisionBaseService revisionService;
    private final WorkProjectBaseService projectService;
    private final WorkProjectMemberBaseService projectMemberService;
    private final WorkTaskBaseService taskService;
    private final WorkTaskMemberBaseService taskMemberService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public WorkLogService(
            WorkLogBaseService logService,
            WorkLogRevisionBaseService revisionService,
            WorkProjectBaseService projectService,
            WorkProjectMemberBaseService projectMemberService,
            WorkTaskBaseService taskService,
            WorkTaskMemberBaseService taskMemberService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.logService = logService;
        this.revisionService = revisionService;
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.taskService = taskService;
        this.taskMemberService = taskMemberService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<WorkLogModels.LogView> list(
            AuthenticatedContext context, LocalDate workDate, Long authorAccountId) {
        requireAction(context, "VIEW");
        Long author = authorAccountId == null ? context.accountId() : authorAccountId;
        requireCanViewAuthor(context, author);
        return scopedLogs(context).stream()
                .filter(log -> Objects.equals(log.getAuthorAccountId(), author))
                .filter(log -> workDate == null || Objects.equals(log.getWorkDate(), workDate))
                .sorted(Comparator.comparing(WorkLog::getWorkDate).reversed()
                        .thenComparing(WorkLog::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(log -> view(log, false)).toList();
    }

    @Transactional
    public WorkLogModels.LogView create(
            AuthenticatedContext context, WorkLogModels.CreateLogRequest input, String traceId) {
        requireAction(context, "CREATE_LOG");
        validateDate(input.workDate());
        validateReferences(context, input.customValues());
        WorkLog log = new WorkLog();
        bindContext(log, context);
        log.setAuthorAccountId(context.accountId());
        log.setWorkDate(input.workDate());
        log.setTitle(input.title().strip());
        log.setContentText(input.content().strip());
        log.setDurationMinutes(input.durationMinutes());
        log.setStatus("DRAFT");
        log.setCustomValuesJson(toJson(safeMap(input.customValues())));
        log.setVersion(0);
        logService.insert(log);
        insertRevision(log, 1, "用户主动创建日志", context.accountId());
        audit(context, traceId, "WORK_LOG_CREATED", "WORK_LOG", log.getId(),
                Map.of("workDate", input.workDate().toString(), "status", "DRAFT", "manual", true));
        return view(log, true);
    }

    @Transactional(readOnly = true)
    public WorkLogModels.LogView detail(AuthenticatedContext context, Long logId) {
        requireAction(context, "VIEW");
        WorkLog log = requireScoped(context, logId);
        requireCanViewAuthor(context, log.getAuthorAccountId());
        return view(log, true);
    }

    @Transactional
    public WorkLogModels.LogView update(
            AuthenticatedContext context, Long logId, WorkLogModels.UpdateLogRequest input, String traceId) {
        requireAction(context, "UPDATE_LOG");
        WorkLog log = requireScoped(context, logId);
        if (!Objects.equals(log.getAuthorAccountId(), context.accountId())) {
            throw new DomainException("WORK_LOG_AUTHOR_REQUIRED", "只能维护本人主动创建的工作日志", HttpStatus.FORBIDDEN);
        }
        if (!Objects.equals(log.getVersion(), input.expectedVersion())) {
            throw conflict("WORK_LOG_VERSION_CONFLICT", "工作日志已被其他人修订，请刷新后重试");
        }
        validateDate(input.workDate());
        validateReferences(context, input.customValues());
        String targetStatus = input.status().strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(targetStatus)
                || !TRANSITIONS.getOrDefault(log.getStatus(), Set.of()).contains(targetStatus)) {
            throw invalid("WORK_LOG_STATUS_TRANSITION_INVALID", "工作日志状态不能从 "
                    + log.getStatus() + " 变为 " + targetStatus);
        }
        boolean contentChanged = !Objects.equals(log.getWorkDate(), input.workDate())
                || !Objects.equals(log.getTitle(), input.title().strip())
                || !Objects.equals(log.getContentText(), input.content().strip())
                || !Objects.equals(log.getDurationMinutes(), input.durationMinutes())
                || !Objects.equals(readMap(log.getCustomValuesJson()), safeMap(input.customValues()));
        if ("SUBMITTED".equals(log.getStatus()) && "SUBMITTED".equals(targetStatus) && contentChanged) {
            throw invalid("WORK_LOG_SUBMITTED_IMMUTABLE", "已提交日志需先撤回再修订内容");
        }
        String previousStatus = log.getStatus();
        log.setWorkDate(input.workDate());
        log.setTitle(input.title().strip());
        log.setContentText(input.content().strip());
        log.setDurationMinutes(input.durationMinutes());
        log.setStatus(targetStatus);
        log.setCustomValuesJson(toJson(safeMap(input.customValues())));
        log.setUpdatedAt(LocalDateTime.now());
        if (logService.updateById(log) != 1) {
            throw conflict("WORK_LOG_VERSION_CONFLICT", "工作日志已被其他人修订，请刷新后重试");
        }
        int revisionNumber = revisions(logId).stream().map(WorkLogRevision::getRevisionNumber)
                .max(Integer::compareTo).orElse(0) + 1;
        insertRevision(log, revisionNumber, input.revisionReason().strip(), context.accountId());
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("revisionNumber", revisionNumber);
        detail.put("previousStatus", previousStatus);
        detail.put("status", targetStatus);
        detail.put("contentChanged", contentChanged);
        audit(context, traceId, "WORK_LOG_REVISED", "WORK_LOG", log.getId(), detail);
        return view(logService.selectById(logId), true);
    }

    private WorkLogModels.LogView view(WorkLog log, boolean includeRevisions) {
        List<WorkLogModels.RevisionView> revisionViews = includeRevisions
                ? revisions(log.getId()).stream().sorted(Comparator.comparing(WorkLogRevision::getRevisionNumber).reversed())
                .map(revision -> new WorkLogModels.RevisionView(
                        revision.getId(), revision.getRevisionNumber(), readMap(revision.getSnapshotJson()),
                        revision.getRevisionReason(), revision.getRevisedByAccountId(), revision.getRevisedAt())).toList()
                : List.of();
        return new WorkLogModels.LogView(
                log.getId(), log.getContextType(), log.getPlatformId(), log.getSystemId(), log.getTenantId(),
                log.getAuthorAccountId(), log.getWorkDate(), log.getTitle(), log.getContentText(),
                log.getDurationMinutes(), log.getStatus(), readMap(log.getCustomValuesJson()), log.getVersion(),
                log.getCreatedAt(), log.getUpdatedAt(), revisionViews);
    }

    private void insertRevision(WorkLog log, int number, String reason, Long actorAccountId) {
        WorkLogRevision revision = new WorkLogRevision();
        revision.setWorkLogId(log.getId());
        revision.setRevisionNumber(number);
        revision.setSnapshotJson(toJson(snapshot(log)));
        revision.setRevisionReason(reason);
        revision.setRevisedByAccountId(actorAccountId);
        revisionService.insert(revision);
    }

    private Map<String, Object> snapshot(WorkLog log) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", log.getId());
        snapshot.put("contextType", log.getContextType());
        snapshot.put("platformId", log.getPlatformId());
        snapshot.put("systemId", log.getSystemId());
        snapshot.put("tenantId", log.getTenantId());
        snapshot.put("authorAccountId", log.getAuthorAccountId());
        snapshot.put("workDate", log.getWorkDate().toString());
        snapshot.put("title", log.getTitle());
        snapshot.put("content", log.getContentText());
        snapshot.put("durationMinutes", log.getDurationMinutes());
        snapshot.put("status", log.getStatus());
        snapshot.put("customValues", readMap(log.getCustomValuesJson()));
        snapshot.put("version", log.getVersion());
        return snapshot;
    }

    private List<WorkLog> scopedLogs(AuthenticatedContext context) {
        if (context.systemId() == null) {
            return logService.selectList(Wrappers.<WorkLog>lambdaQuery()
                    .eq(WorkLog::getContextType, "PLATFORM")
                    .eq(WorkLog::getPlatformId, context.platformId())
                    .isNull(WorkLog::getSystemId).isNull(WorkLog::getTenantId));
        }
        return logService.selectList(Wrappers.<WorkLog>lambdaQuery()
                .eq(WorkLog::getContextType, "SYSTEM")
                .eq(WorkLog::getPlatformId, context.platformId())
                .eq(WorkLog::getSystemId, context.systemId())
                .eq(WorkLog::getTenantId, context.tenantId()));
    }

    private WorkLog requireScoped(AuthenticatedContext context, Long logId) {
        WorkLog log = logService.selectById(logId);
        if (log == null || !scoped(context, log)) {
            throw new DomainException("WORK_LOG_NOT_FOUND", "工作日志不存在或不在当前范围", HttpStatus.NOT_FOUND);
        }
        return log;
    }

    private boolean scoped(AuthenticatedContext context, WorkLog log) {
        if (!Objects.equals(log.getPlatformId(), context.platformId())) {
            return false;
        }
        if (context.systemId() == null) {
            return "PLATFORM".equals(log.getContextType()) && log.getSystemId() == null && log.getTenantId() == null;
        }
        return "SYSTEM".equals(log.getContextType())
                && Objects.equals(log.getSystemId(), context.systemId())
                && Objects.equals(log.getTenantId(), context.tenantId());
    }

    private void bindContext(WorkLog log, AuthenticatedContext context) {
        log.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        log.setPlatformId(context.platformId());
        log.setSystemId(context.systemId());
        log.setTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private void requireCanViewAuthor(AuthenticatedContext context, Long authorAccountId) {
        if (!Objects.equals(authorAccountId, context.accountId())
                && !allowed(context, "VIEW_OTHERS")) {
            throw new DomainException("WORK_LOG_VIEW_OTHERS_DENIED", "查看他人工作日志需要明确权限", HttpStatus.FORBIDDEN);
        }
    }

    private void validateDate(LocalDate workDate) {
        if (workDate.isAfter(LocalDate.now())) {
            throw invalid("WORK_LOG_FUTURE_DATE_INVALID", "工作日志只能填写当天或补写过去日期");
        }
    }

    private void validateReferences(AuthenticatedContext context, Map<String, Object> customValues) {
        Map<String, Object> values = safeMap(customValues);
        Long projectId = asLong(values.get("projectId"));
        if (projectId != null) {
            WorkProject project = projectService.selectById(projectId);
            if (project == null || !sameContext(context, project)
                    || !canReferenceProject(context.accountId(), project)) {
                throw invalid("WORK_LOG_PROJECT_REFERENCE_INVALID", "关联项目不存在、越权或不在当前上下文");
            }
        }
        Object taskIdsValue = values.get("taskIds");
        if (taskIdsValue instanceof List<?> taskIds) {
            for (Object raw : taskIds) {
                Long taskId = asLong(raw);
                WorkTask task = taskId == null ? null : taskService.selectById(taskId);
                if (task == null || !sameContext(context, task) || !canReferenceTask(context.accountId(), task)) {
                    throw invalid("WORK_LOG_TASK_REFERENCE_INVALID", "关联任务不存在、越权或不在当前上下文");
                }
            }
        } else if (taskIdsValue != null) {
            throw invalid("WORK_LOG_TASK_REFERENCE_INVALID", "关联任务必须是任务 ID 列表");
        }
    }

    private boolean sameContext(AuthenticatedContext context, WorkProject project) {
        return Objects.equals(project.getPlatformId(), context.platformId())
                && Objects.equals(project.getSystemId(), context.systemId())
                && Objects.equals(project.getTenantId(), context.systemId() == null ? null : context.tenantId());
    }

    private boolean sameContext(AuthenticatedContext context, WorkTask task) {
        return Objects.equals(task.getPlatformId(), context.platformId())
                && Objects.equals(task.getSystemId(), context.systemId())
                && Objects.equals(task.getTenantId(), context.systemId() == null ? null : context.tenantId());
    }

    private boolean canReferenceProject(Long accountId, WorkProject project) {
        return Objects.equals(project.getOwnerAccountId(), accountId)
                || projectMemberService.selectList(Wrappers.<WorkProjectMember>lambdaQuery()
                .eq(WorkProjectMember::getProjectId, project.getId())
                .eq(WorkProjectMember::getAccountId, accountId)
                .eq(WorkProjectMember::getStatus, "ACTIVE")).size() == 1;
    }

    private boolean canReferenceTask(Long accountId, WorkTask task) {
        if (Objects.equals(task.getOwnerAccountId(), accountId)) {
            return true;
        }
        boolean taskMember = !taskMemberService.selectList(Wrappers.<WorkTaskMember>lambdaQuery()
                .eq(WorkTaskMember::getTaskId, task.getId())
                .eq(WorkTaskMember::getAccountId, accountId)).isEmpty();
        if (taskMember) {
            return true;
        }
        WorkProject project = task.getProjectId() == null ? null : projectService.selectById(task.getProjectId());
        return project != null && canReferenceProject(accountId, project);
    }

    private List<WorkLogRevision> revisions(Long logId) {
        return revisionService.selectList(Wrappers.<WorkLogRevision>lambdaQuery()
                .eq(WorkLogRevision::getWorkLogId, logId));
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (!allowed(context, action)) {
            throw new DomainException("PERMISSION_DENIED", "没有工作日志 " + action + " 权限", HttpStatus.FORBIDDEN);
        }
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

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    private Map<String, Object> readMap(String json) {
        try {
            return json == null || json.isBlank() ? Map.of()
                    : objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot read work log JSON", exception);
        }
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize work log JSON", exception);
        }
    }

    private Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
