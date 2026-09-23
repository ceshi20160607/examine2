package com.unique.unexamine.work.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.base.entity.AuditEvent;
import com.unique.unexamine.audit.base.service.AuditEventBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.notification.base.entity.MsgMessage;
import com.unique.unexamine.notification.base.entity.MsgRecipient;
import com.unique.unexamine.notification.base.entity.TodoItem;
import com.unique.unexamine.notification.base.service.MsgMessageBaseService;
import com.unique.unexamine.notification.base.service.MsgRecipientBaseService;
import com.unique.unexamine.notification.base.service.TodoItemBaseService;
import com.unique.unexamine.platform.base.entity.PlatformRole;
import com.unique.unexamine.platform.base.service.PlatformRoleBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMemberReportingLine;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.service.SystemMemberReportingLineBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.work.base.entity.WorkLog;
import com.unique.unexamine.work.base.entity.WorkLogComment;
import com.unique.unexamine.work.base.entity.WorkLogMaterial;
import com.unique.unexamine.work.base.entity.WorkLogRevision;
import com.unique.unexamine.work.base.entity.WorkLogTaskLink;
import com.unique.unexamine.work.base.entity.WorkProject;
import com.unique.unexamine.work.base.entity.WorkProjectMember;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.entity.WorkTaskMember;
import com.unique.unexamine.work.base.service.WorkLogBaseService;
import com.unique.unexamine.work.base.service.WorkLogCommentBaseService;
import com.unique.unexamine.work.base.service.WorkLogMaterialBaseService;
import com.unique.unexamine.work.base.service.WorkLogRevisionBaseService;
import com.unique.unexamine.work.base.service.WorkLogTaskLinkBaseService;
import com.unique.unexamine.work.base.service.WorkProjectBaseService;
import com.unique.unexamine.work.base.service.WorkProjectMemberBaseService;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.base.service.WorkTaskMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 工作日志业务层：只允许用户主动形成日志，状态动作、权限和历史均由服务端约束。 */
@Service
public class WorkLogService {
    private static final Set<String> VISIBILITY_TYPES = Set.of("SELF", "MANAGER", "PROJECT", "TEAM", "ROLE");
    private static final Set<String> MATERIAL_TYPES = Set.of("TASK", "TODO", "MESSAGE", "BUSINESS_OPERATION");
    private static final Set<String> COMMENT_TYPES = Set.of("COMMENT", "FEEDBACK");

    private final WorkLogBaseService logService;
    private final WorkLogRevisionBaseService revisionService;
    private final WorkLogTaskLinkBaseService logTaskLinkService;
    private final WorkLogMaterialBaseService materialService;
    private final WorkLogCommentBaseService commentService;
    private final WorkProjectBaseService projectService;
    private final WorkProjectMemberBaseService projectMemberService;
    private final WorkTaskBaseService taskService;
    private final WorkTaskMemberBaseService taskMemberService;
    private final TodoItemBaseService todoService;
    private final MsgMessageBaseService messageService;
    private final MsgRecipientBaseService recipientService;
    private final AuditEventBaseService auditEventService;
    private final SystemRoleBaseService systemRoleService;
    private final PlatformRoleBaseService platformRoleService;
    private final SystemMemberReportingLineBaseService reportingLineService;
    private final WorkParticipantResolver participantResolver;
    private final RuntimeDataService runtimeDataService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final WorkConfigurationService workConfigurationService;

    public WorkLogService(
            WorkLogBaseService logService,
            WorkLogRevisionBaseService revisionService,
            WorkLogTaskLinkBaseService logTaskLinkService,
            WorkLogMaterialBaseService materialService,
            WorkLogCommentBaseService commentService,
            WorkProjectBaseService projectService,
            WorkProjectMemberBaseService projectMemberService,
            WorkTaskBaseService taskService,
            WorkTaskMemberBaseService taskMemberService,
            TodoItemBaseService todoService,
            MsgMessageBaseService messageService,
            MsgRecipientBaseService recipientService,
            AuditEventBaseService auditEventService,
            SystemRoleBaseService systemRoleService,
            PlatformRoleBaseService platformRoleService,
            SystemMemberReportingLineBaseService reportingLineService,
            WorkParticipantResolver participantResolver,
            RuntimeDataService runtimeDataService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            WorkConfigurationService workConfigurationService) {
        this.logService = logService;
        this.revisionService = revisionService;
        this.logTaskLinkService = logTaskLinkService;
        this.materialService = materialService;
        this.commentService = commentService;
        this.projectService = projectService;
        this.projectMemberService = projectMemberService;
        this.taskService = taskService;
        this.taskMemberService = taskMemberService;
        this.todoService = todoService;
        this.messageService = messageService;
        this.recipientService = recipientService;
        this.auditEventService = auditEventService;
        this.systemRoleService = systemRoleService;
        this.platformRoleService = platformRoleService;
        this.reportingLineService = reportingLineService;
        this.participantResolver = participantResolver;
        this.runtimeDataService = runtimeDataService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.workConfigurationService = workConfigurationService;
    }

    @Transactional(readOnly = true)
    public List<WorkLogModels.LogView> list(
            AuthenticatedContext context, LocalDate workDate, String status, Long projectId, String keyword,
            Long authorTenantMemberId, Long compatibilityAuthorAccountId) {
        requireAction(context, "VIEW");
        WorkParticipantResolver.ResolvedPerson requestedAuthor = authorTenantMemberId == null
                && compatibilityAuthorAccountId == null ? participantResolver.current(context)
                : participantResolver.require(context, authorTenantMemberId, compatibilityAuthorAccountId);
        if (!Objects.equals(requestedAuthor.accountId(), context.accountId()) && !allowed(context, "VIEW_OTHERS")) {
            throw new DomainException("WORK_LOG_VIEW_OTHERS_DENIED", "查看他人工作日志需要明确权限", HttpStatus.FORBIDDEN);
        }
        String normalizedStatus = blankToNull(status) == null ? null : status.strip().toUpperCase(Locale.ROOT);
        String normalizedKeyword = blankToNull(keyword) == null ? null : keyword.strip().toLowerCase(Locale.ROOT);
        return scopedLogs(context).stream()
                .filter(log -> Objects.equals(log.getAuthorAccountId(), requestedAuthor.accountId()))
                .filter(log -> canViewLog(context, log))
                .filter(log -> workDate == null || Objects.equals(log.getWorkDate(), workDate))
                .filter(log -> normalizedStatus == null || normalizedStatus.equals(log.getStatus()))
                .filter(log -> projectId == null || Objects.equals(projectId, log.getProjectId()))
                .filter(log -> normalizedKeyword == null || searchableText(log).contains(normalizedKeyword))
                .sorted(Comparator.comparing(WorkLog::getWorkDate).reversed()
                        .thenComparing(WorkLog::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(log -> view(context, log, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<WorkLogModels.MaterialCandidate> materialCandidates(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        List<WorkLogModels.MaterialCandidate> result = new ArrayList<>();
        scopedTasks(context).stream().filter(task -> canReferenceTask(context.accountId(), task))
                .filter(task -> task.getDeleteTime() == null)
                .forEach(task -> result.add(candidate("TASK", task.getId(), "任务", task.getTitle(),
                        task.getDescription(), task.getUpdatedAt())));

        todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                        .eq(TodoItem::getAssigneeAccountId, context.accountId())
                        .orderByDesc(TodoItem::getUpdatedAt).last("LIMIT 100"))
                .stream().filter(todo -> sameContext(context, todo))
                .forEach(todo -> result.add(candidate("TODO", todo.getId(), "待办", todo.getTitle(),
                        todo.getSummary(), todo.getUpdatedAt())));

        List<Long> messageIds = recipientService.selectList(Wrappers.<MsgRecipient>lambdaQuery()
                        .eq(MsgRecipient::getAccountId, context.accountId())
                        .orderByDesc(MsgRecipient::getUpdatedAt).last("LIMIT 100"))
                .stream().map(MsgRecipient::getMessageId).distinct().toList();
        messageIds.stream().map(messageService::selectById).filter(Objects::nonNull)
                .filter(message -> sameContext(context, message))
                .forEach(message -> result.add(candidate("MESSAGE", message.getId(), "消息", message.getSubject(),
                        message.getContentText(), message.getCreatedAt())));

        auditEventService.selectList(Wrappers.<AuditEvent>lambdaQuery()
                        .eq(AuditEvent::getActorAccountId, context.accountId())
                        .eq(AuditEvent::getResultCode, "SUCCESS")
                        .orderByDesc(AuditEvent::getOccurredAt).last("LIMIT 100"))
                .stream().filter(event -> sameContext(context, event))
                .forEach(event -> result.add(candidate("BUSINESS_OPERATION", event.getId(), "业务操作",
                        event.getEventCode(), safeJoin(event.getObjectType(), event.getObjectId()), event.getOccurredAt())));
        return result.stream().sorted(Comparator.comparing(WorkLogModels.MaterialCandidate::sourceTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(250).toList();
    }

    @Transactional(readOnly = true)
    public WorkLogModels.LogOptionsView options(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        List<WorkLogModels.RoleOption> roles;
        if (context.systemId() == null) {
            roles = platformRoleService.selectList(Wrappers.<PlatformRole>lambdaQuery()
                            .eq(PlatformRole::getPlatformId, context.platformId())
                            .eq(PlatformRole::getStatus, "ACTIVE").orderByAsc(PlatformRole::getName))
                    .stream().map(role -> new WorkLogModels.RoleOption(role.getId(), role.getCode(), role.getName())).toList();
        } else {
            roles = systemRoleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                            .eq(SystemRole::getSystemId, context.systemId())
                            .eq(SystemRole::getTenantId, context.tenantId())
                            .eq(SystemRole::getStatus, "ACTIVE").orderByAsc(SystemRole::getName))
                    .stream().map(role -> new WorkLogModels.RoleOption(role.getId(), role.getCode(), role.getName())).toList();
        }
        return new WorkLogModels.LogOptionsView(roles);
    }

    @Transactional
    public WorkLogModels.LogView create(
            AuthenticatedContext context, WorkLogModels.CreateLogRequest input, String traceId) {
        requireAction(context, "CREATE_LOG");
        validateDate(input.workDate());
        ReferenceBundle references = validateReferences(context, input.projectId(), input.taskIds(),
                input.businessType(), input.businessId(), input.businessTitle(), traceId);
        Visibility visibility = validateVisibility(context, input.visibilityType(), input.visibleRoleIds(), references.project());
        WorkParticipantResolver.ResolvedPerson author = participantResolver.current(context);
        WorkLog log = new WorkLog();
        bindContext(log, context);
        log.setAuthorAccountId(context.accountId());
        log.setAuthorTenantMemberId(author.tenantMemberId());
        applyContent(log, input.workDate(), input.title(), input.completedText(), input.inProgressText(),
                input.nextPlanText(), input.riskText(), input.assistanceText(), input.durationMinutes());
        log.setProjectId(references.project() == null ? null : references.project().getId());
        log.setStatus("DRAFT");
        log.setVisibilityType(visibility.type());
        log.setVisibleRoleIdsJson(toJson(visibility.roleIds()));
        log.setCustomValuesJson(toJson(workConfigurationService.validateConfiguredValues(
                context, "LOG", input.configuredValues())));
        applyBusiness(log, references.business());
        log.setVersion(0);
        logService.insert(log);
        replaceTaskLinks(log.getId(), references.tasks());
        replaceMaterials(log.getId(), context, input.materials(), Map.of());
        insertRevision(log, 1, "用户主动创建日志", context.accountId());
        audit(context, traceId, "WORK_LOG_CREATED", log.getId(), Map.of(
                "workDate", input.workDate().toString(), "status", "DRAFT", "manual", true));
        auditBusinessRecordLog(context, log, "CREATED", traceId);
        return view(context, log, true);
    }

    @Transactional(readOnly = true)
    public WorkLogModels.LogView detail(AuthenticatedContext context, Long logId) {
        requireAction(context, "VIEW");
        WorkLog log = requireScoped(context, logId);
        requireCanViewLog(context, log);
        return view(context, log, true);
    }

    /** 文件服务复用日志权限；附件写入只允许日志作者在未提交状态执行。 */
    @Transactional(readOnly = true)
    public void requireAttachmentAccess(AuthenticatedContext context, Long logId, boolean write) {
        requireAction(context, write ? "UPDATE_LOG" : "VIEW");
        WorkLog log = requireScoped(context, logId);
        if (write) {
            if (!Objects.equals(log.getAuthorAccountId(), context.accountId())) {
                throw new DomainException("WORK_LOG_AUTHOR_REQUIRED", "只能维护本人工作日志的附件", HttpStatus.FORBIDDEN);
            }
            if ("SUBMITTED".equals(log.getStatus())) {
                throw invalid("WORK_LOG_SUBMITTED_IMMUTABLE", "已提交日志需先撤回再维护附件");
            }
        } else {
            requireCanViewLog(context, log);
        }
    }

    @Transactional
    public WorkLogModels.LogView update(
            AuthenticatedContext context, Long logId, WorkLogModels.UpdateLogRequest input, String traceId) {
        requireAction(context, "UPDATE_LOG");
        WorkLog log = requireOwned(context, logId);
        requireVersion(log, input.expectedVersion());
        if ("SUBMITTED".equals(log.getStatus())) {
            throw invalid("WORK_LOG_SUBMITTED_IMMUTABLE", "已提交日志需先撤回再修订内容");
        }
        validateDate(input.workDate());
        ReferenceBundle references = validateReferences(context, input.projectId(), input.taskIds(),
                input.businessType(), input.businessId(), input.businessTitle(), traceId);
        Visibility visibility = validateVisibility(context, input.visibilityType(), input.visibleRoleIds(), references.project());
        Map<String, WorkLogModels.MaterialView> previous = materialViews(logId).stream()
                .collect(Collectors.toMap(item -> materialKey(item.materialType(), item.materialId()),
                        Function.identity(), (left, right) -> left, LinkedHashMap::new));
        applyContent(log, input.workDate(), input.title(), input.completedText(), input.inProgressText(),
                input.nextPlanText(), input.riskText(), input.assistanceText(), input.durationMinutes());
        log.setProjectId(references.project() == null ? null : references.project().getId());
        log.setVisibilityType(visibility.type());
        log.setVisibleRoleIdsJson(toJson(visibility.roleIds()));
        log.setCustomValuesJson(toJson(workConfigurationService.validateConfiguredValues(
                context, "LOG", input.configuredValues())));
        applyBusiness(log, references.business());
        log.setUpdatedAt(LocalDateTime.now());
        if (logService.updateById(log) != 1) throw conflict();
        replaceTaskLinks(log.getId(), references.tasks());
        replaceMaterials(log.getId(), context, input.materials(), previous);
        insertRevision(log, nextRevision(logId), input.revisionReason().strip(), context.accountId());
        audit(context, traceId, "WORK_LOG_REVISED", log.getId(), Map.of("status", log.getStatus()));
        auditBusinessRecordLog(context, log, "UPDATED", traceId);
        return view(context, logService.selectById(logId), true);
    }

    @Transactional
    public WorkLogModels.LogView submit(
            AuthenticatedContext context, Long logId, WorkLogModels.LifecycleRequest input, String traceId) {
        WorkLog log = requireLifecycle(context, logId, input.expectedVersion(), "DRAFT");
        if (!hasMeaningfulContent(log)) {
            throw invalid("WORK_LOG_CONTENT_REQUIRED", "提交前至少填写已完成、进行中、明日计划、风险或协助事项中的一项");
        }
        log.setStatus("SUBMITTED");
        log.setSubmittedTime(LocalDateTime.now());
        return finishLifecycle(context, log, input.reason(), "WORK_LOG_SUBMITTED", traceId);
    }

    @Transactional
    public WorkLogModels.LogView withdraw(
            AuthenticatedContext context, Long logId, WorkLogModels.LifecycleRequest input, String traceId) {
        WorkLog log = requireLifecycle(context, logId, input.expectedVersion(), "SUBMITTED");
        log.setStatus("WITHDRAWN");
        log.setWithdrawnTime(LocalDateTime.now());
        return finishLifecycle(context, log, input.reason(), "WORK_LOG_WITHDRAWN", traceId);
    }

    @Transactional
    public WorkLogModels.LogView restoreDraft(
            AuthenticatedContext context, Long logId, WorkLogModels.LifecycleRequest input, String traceId) {
        WorkLog log = requireLifecycle(context, logId, input.expectedVersion(), "WITHDRAWN");
        log.setStatus("DRAFT");
        return finishLifecycle(context, log, input.reason(), "WORK_LOG_DRAFT_RESTORED", traceId);
    }

    @Transactional
    public WorkLogModels.LogView addComment(
            AuthenticatedContext context, Long logId, WorkLogModels.AddCommentRequest input, String traceId) {
        requireAction(context, "VIEW");
        WorkLog log = requireScoped(context, logId);
        requireCanViewLog(context, log);
        String type = input.commentType().strip().toUpperCase(Locale.ROOT);
        if (!COMMENT_TYPES.contains(type)) throw invalid("WORK_LOG_COMMENT_TYPE_INVALID", "评论类型无效");
        if ("FEEDBACK".equals(type) && (Objects.equals(log.getAuthorAccountId(), context.accountId())
                || !allowed(context, "VIEW_OTHERS"))) {
            throw new DomainException("WORK_LOG_FEEDBACK_DENIED", "管理反馈只能由有权查看他人日志的成员填写", HttpStatus.FORBIDDEN);
        }
        WorkLogComment comment = new WorkLogComment();
        comment.setWorkLogId(logId);
        comment.setCommentType(type);
        comment.setCommentText(input.comment().strip());
        comment.setCreatedByAccountId(context.accountId());
        commentService.insert(comment);
        audit(context, traceId, "WORK_LOG_COMMENT_ADDED", logId, Map.of("commentType", type));
        return view(context, log, true);
    }

    private WorkLogModels.LogView finishLifecycle(
            AuthenticatedContext context, WorkLog log, String reason, String eventCode, String traceId) {
        log.setUpdatedAt(LocalDateTime.now());
        if (logService.updateById(log) != 1) throw conflict();
        insertRevision(log, nextRevision(log.getId()), reason.strip(), context.accountId());
        audit(context, traceId, eventCode, log.getId(), Map.of("status", log.getStatus()));
        auditBusinessRecordLog(context, log, "UPDATED", traceId);
        return view(context, logService.selectById(log.getId()), true);
    }

    private WorkLog requireLifecycle(
            AuthenticatedContext context, Long logId, Integer expectedVersion, String expectedStatus) {
        requireAction(context, "UPDATE_LOG");
        WorkLog log = requireOwned(context, logId);
        requireVersion(log, expectedVersion);
        if (!expectedStatus.equals(log.getStatus())) {
            throw invalid("WORK_LOG_STATUS_TRANSITION_INVALID", "当前状态不能执行该日志动作");
        }
        return log;
    }

    private WorkLog requireOwned(AuthenticatedContext context, Long logId) {
        WorkLog log = requireScoped(context, logId);
        if (!Objects.equals(log.getAuthorAccountId(), context.accountId())) {
            throw new DomainException("WORK_LOG_AUTHOR_REQUIRED", "只能维护本人主动创建的工作日志", HttpStatus.FORBIDDEN);
        }
        return log;
    }

    private void requireVersion(WorkLog log, Integer expectedVersion) {
        if (!Objects.equals(log.getVersion(), expectedVersion)) throw conflict();
    }

    private void applyContent(
            WorkLog log, LocalDate workDate, String title, String completedText, String inProgressText,
            String nextPlanText, String riskText, String assistanceText, Integer durationMinutes) {
        log.setWorkDate(workDate);
        log.setTitle(title.strip());
        log.setCompletedText(clean(completedText));
        log.setInProgressText(clean(inProgressText));
        log.setNextPlanText(clean(nextPlanText));
        log.setRiskText(clean(riskText));
        log.setAssistanceText(clean(assistanceText));
        log.setContentText(contentSummary(log));
        log.setDurationMinutes(durationMinutes);
    }

    private void applyBusiness(WorkLog log, BusinessReference business) {
        log.setBusinessType(business.type());
        log.setBusinessId(business.id());
        log.setBusinessTitle(business.title());
    }

    private String contentSummary(WorkLog log) {
        for (String value : List.of(nullToEmpty(log.getCompletedText()), nullToEmpty(log.getInProgressText()),
                nullToEmpty(log.getNextPlanText()), nullToEmpty(log.getRiskText()), nullToEmpty(log.getAssistanceText()))) {
            if (!value.isBlank()) return value;
        }
        return "";
    }

    private boolean hasMeaningfulContent(WorkLog log) {
        return !contentSummary(log).isBlank();
    }

    private Visibility validateVisibility(
            AuthenticatedContext context, String suppliedType, List<Long> suppliedRoleIds, WorkProject project) {
        String type = blankToNull(suppliedType) == null ? "SELF" : suppliedType.strip().toUpperCase(Locale.ROOT);
        if (!VISIBILITY_TYPES.contains(type)) throw invalid("WORK_LOG_VISIBILITY_INVALID", "日志可见范围无效");
        if (context.systemId() == null && Set.of("MANAGER", "TEAM", "PROJECT").contains(type)) {
            throw invalid("WORK_LOG_VISIBILITY_CONTEXT_INVALID", "平台日志不支持该组织可见范围");
        }
        if ("PROJECT".equals(type) && project == null) {
            throw invalid("WORK_LOG_PROJECT_VISIBILITY_REQUIRES_PROJECT", "选择项目成员可见时必须关联项目");
        }
        List<Long> roleIds = normalizeIds(suppliedRoleIds);
        if (!"ROLE".equals(type)) return new Visibility(type, List.of());
        if (roleIds.isEmpty()) throw invalid("WORK_LOG_VISIBLE_ROLE_REQUIRED", "指定角色可见时至少选择一个角色");
        Set<Long> valid = options(context).roles().stream().map(WorkLogModels.RoleOption::id).collect(Collectors.toSet());
        if (!valid.containsAll(roleIds)) throw invalid("WORK_LOG_VISIBLE_ROLE_INVALID", "所选角色不存在或不属于当前范围");
        return new Visibility(type, roleIds);
    }

    private WorkLogModels.LogView view(AuthenticatedContext context, WorkLog log, boolean includeDetails) {
        List<WorkLogModels.RevisionView> revisionViews = includeDetails
                ? revisions(log.getId()).stream().sorted(Comparator.comparing(WorkLogRevision::getRevisionNumber).reversed())
                .map(revision -> new WorkLogModels.RevisionView(
                        revision.getId(), revision.getRevisionNumber(), readMap(revision.getSnapshotJson()),
                        revision.getRevisionReason(), displayName(context, revision.getRevisedByAccountId()),
                        revision.getRevisedAt())).toList() : List.of();
        WorkParticipantResolver.ResolvedPerson author = participantResolver.find(
                context, log.getAuthorTenantMemberId(), log.getAuthorAccountId());
        WorkProject project = log.getProjectId() == null ? null : projectService.selectById(log.getProjectId());
        List<WorkLogModels.TaskReference> tasks = linkedTaskIds(log.getId()).stream().map(taskService::selectById)
                .filter(Objects::nonNull)
                .map(task -> new WorkLogModels.TaskReference(task.getId(), task.getTitle(), task.getStatus())).toList();
        return new WorkLogModels.LogView(
                log.getId(), log.getContextType(), log.getPlatformId(), log.getSystemId(), log.getTenantId(),
                personView(author), log.getWorkDate(), log.getTitle(), log.getContentText(),
                legacyCompleted(log), log.getInProgressText(), log.getNextPlanText(), log.getRiskText(),
                log.getAssistanceText(), log.getDurationMinutes(), log.getStatus(),
                Objects.equals(log.getAuthorAccountId(), context.accountId()) && allowed(context, "UPDATE_LOG"),
                nullToDefault(log.getVisibilityType(), "SELF"), readLongList(log.getVisibleRoleIdsJson()),
                log.getSubmittedTime(), log.getWithdrawnTime(), log.getProjectId(),
                project == null ? null : project.getName(), tasks, log.getBusinessType(), log.getBusinessId(),
                log.getBusinessTitle(), readMap(log.getCustomValuesJson()), log.getVersion(),
                log.getCreatedAt(), log.getUpdatedAt(),
                includeDetails ? materialViews(log.getId()) : List.of(),
                includeDetails ? commentViews(context, log.getId()) : List.of(), revisionViews);
    }

    private String legacyCompleted(WorkLog log) {
        return log.getCompletedText() == null ? log.getContentText() : log.getCompletedText();
    }

    private List<WorkLogModels.CommentView> commentViews(AuthenticatedContext context, Long logId) {
        return commentService.selectList(Wrappers.<WorkLogComment>lambdaQuery()
                        .eq(WorkLogComment::getWorkLogId, logId).orderByAsc(WorkLogComment::getCreatedTime))
                .stream().map(comment -> new WorkLogModels.CommentView(
                        comment.getId(), comment.getCommentType(), comment.getCommentText(),
                        personView(participantResolver.find(context, null, comment.getCreatedByAccountId())),
                        comment.getCreatedTime())).toList();
    }

    private List<WorkLogModels.MaterialView> materialViews(Long logId) {
        return materialRows(logId).stream().map(material -> new WorkLogModels.MaterialView(
                material.getId(), material.getMaterialType(), material.getMaterialId(), material.getMaterialTitle(),
                material.getMaterialSummary(), material.getSourceTime())).toList();
    }

    private void replaceMaterials(
            Long logId, AuthenticatedContext context, List<WorkLogModels.MaterialSelection> selections,
            Map<String, WorkLogModels.MaterialView> previous) {
        List<WorkLogModels.MaterialSelection> normalized = selections == null ? List.of() : selections.stream()
                .filter(Objects::nonNull).toList();
        Map<String, WorkLogModels.MaterialCandidate> available = materialCandidates(context).stream()
                .collect(Collectors.toMap(WorkLogModels.MaterialCandidate::key, Function.identity(), (a, b) -> a));
        LinkedHashMap<String, WorkLogModels.MaterialView> selected = new LinkedHashMap<>();
        for (WorkLogModels.MaterialSelection selection : normalized) {
            String type = selection.materialType().strip().toUpperCase(Locale.ROOT);
            if (!MATERIAL_TYPES.contains(type)) throw invalid("WORK_LOG_MATERIAL_TYPE_INVALID", "日志材料类型无效");
            String key = materialKey(type, selection.materialId());
            WorkLogModels.MaterialCandidate candidate = available.get(key);
            WorkLogModels.MaterialView retained = previous.get(key);
            if (candidate == null && retained == null) {
                throw invalid("WORK_LOG_MATERIAL_UNAVAILABLE", "所选材料不存在、已失效或当前无权访问");
            }
            selected.putIfAbsent(key, candidate == null ? retained : new WorkLogModels.MaterialView(
                    null, candidate.materialType(), candidate.materialId(), candidate.title(),
                    candidate.summary(), candidate.sourceTime()));
        }
        for (WorkLogMaterial old : materialRows(logId)) materialService.deleteById(old.getId());
        for (WorkLogModels.MaterialView value : selected.values()) {
            WorkLogMaterial material = new WorkLogMaterial();
            material.setWorkLogId(logId);
            material.setMaterialType(value.materialType());
            material.setMaterialId(value.materialId());
            material.setMaterialTitle(value.title());
            material.setMaterialSummary(value.summary());
            material.setSourceTime(value.sourceTime());
            material.setCreatedByAccountId(context.accountId());
            materialService.insert(material);
        }
    }

    private List<WorkLogMaterial> materialRows(Long logId) {
        return materialService.selectList(Wrappers.<WorkLogMaterial>lambdaQuery()
                .eq(WorkLogMaterial::getWorkLogId, logId).orderByAsc(WorkLogMaterial::getId));
    }

    private WorkLogModels.MaterialCandidate candidate(
            String type, Object id, String typeName, String title, String summary, LocalDateTime sourceTime) {
        String materialId = String.valueOf(id);
        return new WorkLogModels.MaterialCandidate(materialKey(type, materialId), type, materialId, typeName,
                blankToDefault(title, typeName + " #" + materialId), clip(summary, 500), sourceTime);
    }

    private String materialKey(String type, String id) {
        return type + ":" + id;
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
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("id", log.getId());
        value.put("workDate", log.getWorkDate().toString());
        value.put("title", log.getTitle());
        value.put("completedText", legacyCompleted(log));
        value.put("inProgressText", log.getInProgressText());
        value.put("nextPlanText", log.getNextPlanText());
        value.put("riskText", log.getRiskText());
        value.put("assistanceText", log.getAssistanceText());
        value.put("durationMinutes", log.getDurationMinutes());
        value.put("status", log.getStatus());
        value.put("visibilityType", log.getVisibilityType());
        value.put("visibleRoleIds", readLongList(log.getVisibleRoleIdsJson()));
        value.put("projectId", log.getProjectId());
        value.put("taskIds", linkedTaskIds(log.getId()));
        value.put("materials", materialViews(log.getId()));
        value.put("businessType", log.getBusinessType());
        value.put("businessId", log.getBusinessId());
        value.put("businessTitle", log.getBusinessTitle());
        value.put("configuredValues", readMap(log.getCustomValuesJson()));
        value.put("version", log.getVersion());
        return value;
    }

    private int nextRevision(Long logId) {
        return revisions(logId).stream().map(WorkLogRevision::getRevisionNumber).max(Integer::compareTo).orElse(0) + 1;
    }

    private List<WorkLogRevision> revisions(Long logId) {
        return revisionService.selectList(Wrappers.<WorkLogRevision>lambdaQuery()
                .eq(WorkLogRevision::getWorkLogId, logId));
    }

    private boolean canViewLog(AuthenticatedContext context, WorkLog log) {
        if (Objects.equals(log.getAuthorAccountId(), context.accountId()) || allowed(context, "VIEW_ALL")) return true;
        if (!allowed(context, "VIEW_OTHERS")) return false;
        String visibility = nullToDefault(log.getVisibilityType(), "SELF");
        if ("MANAGER".equals(visibility)) return isDirectManager(context, log);
        if ("PROJECT".equals(visibility)) {
            WorkProject project = log.getProjectId() == null ? null : projectService.selectById(log.getProjectId());
            return project != null && canReferenceProject(context.accountId(), project);
        }
        if ("TEAM".equals(visibility)) {
            WorkParticipantResolver.ResolvedPerson author = participantResolver.find(
                    context, log.getAuthorTenantMemberId(), log.getAuthorAccountId());
            WorkParticipantResolver.ResolvedPerson viewer = participantResolver.current(context);
            return author != null && author.departmentName() != null
                    && Objects.equals(author.departmentName(), viewer.departmentName());
        }
        if ("ROLE".equals(visibility)) {
            Set<Long> viewerRoles = new LinkedHashSet<>(context.roleIds() == null ? List.of() : context.roleIds());
            return readLongList(log.getVisibleRoleIdsJson()).stream().anyMatch(viewerRoles::contains);
        }
        return false;
    }

    private boolean isDirectManager(AuthenticatedContext context, WorkLog log) {
        if (context.systemId() == null || context.tenantMemberId() == null || log.getAuthorTenantMemberId() == null) return false;
        return !reportingLineService.selectList(Wrappers.<SystemMemberReportingLine>lambdaQuery()
                .eq(SystemMemberReportingLine::getSystemId, context.systemId())
                .eq(SystemMemberReportingLine::getTenantId, context.tenantId())
                .eq(SystemMemberReportingLine::getTenantMemberId, log.getAuthorTenantMemberId())
                .eq(SystemMemberReportingLine::getManagerTenantMemberId, context.tenantMemberId())).isEmpty();
    }

    private void requireCanViewLog(AuthenticatedContext context, WorkLog log) {
        if (!canViewLog(context, log)) {
            throw new DomainException("WORK_LOG_VISIBILITY_DENIED", "该日志未向当前成员开放", HttpStatus.FORBIDDEN);
        }
    }

    private List<WorkLog> scopedLogs(AuthenticatedContext context) {
        if (context.systemId() == null) {
            return logService.selectList(Wrappers.<WorkLog>lambdaQuery()
                    .eq(WorkLog::getContextType, "PLATFORM").eq(WorkLog::getPlatformId, context.platformId())
                    .isNull(WorkLog::getSystemId).isNull(WorkLog::getTenantId));
        }
        return logService.selectList(Wrappers.<WorkLog>lambdaQuery()
                .eq(WorkLog::getContextType, "SYSTEM").eq(WorkLog::getPlatformId, context.platformId())
                .eq(WorkLog::getSystemId, context.systemId()).eq(WorkLog::getTenantId, context.tenantId()));
    }

    private List<WorkTask> scopedTasks(AuthenticatedContext context) {
        var query = Wrappers.<WorkTask>lambdaQuery().eq(WorkTask::getPlatformId, context.platformId());
        if (context.systemId() == null) query.isNull(WorkTask::getSystemId).isNull(WorkTask::getTenantId);
        else query.eq(WorkTask::getSystemId, context.systemId()).eq(WorkTask::getTenantId, context.tenantId());
        return taskService.selectList(query.orderByDesc(WorkTask::getUpdatedAt).last("LIMIT 200"));
    }

    private WorkLog requireScoped(AuthenticatedContext context, Long logId) {
        WorkLog log = logService.selectById(logId);
        if (log == null || !scoped(context, log)) {
            throw new DomainException("WORK_LOG_NOT_FOUND", "工作日志不存在或不在当前范围", HttpStatus.NOT_FOUND);
        }
        return log;
    }

    private boolean scoped(AuthenticatedContext context, WorkLog log) {
        if (!Objects.equals(log.getPlatformId(), context.platformId())) return false;
        if (context.systemId() == null) {
            return "PLATFORM".equals(log.getContextType()) && log.getSystemId() == null && log.getTenantId() == null;
        }
        return "SYSTEM".equals(log.getContextType()) && Objects.equals(log.getSystemId(), context.systemId())
                && Objects.equals(log.getTenantId(), context.tenantId());
    }

    private void bindContext(WorkLog log, AuthenticatedContext context) {
        log.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        log.setPlatformId(context.platformId());
        log.setSystemId(context.systemId());
        log.setTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private void validateDate(LocalDate workDate) {
        if (workDate.isAfter(LocalDate.now())) {
            throw invalid("WORK_LOG_FUTURE_DATE_INVALID", "工作日志只能填写当天或补写过去日期");
        }
    }

    private ReferenceBundle validateReferences(
            AuthenticatedContext context, Long projectId, List<Long> taskIds,
            String businessType, String businessId, String suppliedBusinessTitle, String traceId) {
        WorkProject project = null;
        if (projectId != null) {
            project = projectService.selectById(projectId);
            if (project == null || !sameContext(context, project) || !canReferenceProject(context.accountId(), project)) {
                throw invalid("WORK_LOG_PROJECT_REFERENCE_INVALID", "关联项目不存在、越权或不在当前上下文");
            }
        }
        List<Long> normalizedTaskIds = normalizeIds(taskIds);
        List<WorkTask> tasks = new ArrayList<>();
        for (Long taskId : normalizedTaskIds) {
            WorkTask task = taskService.selectById(taskId);
            if (task == null || task.getDeleteTime() != null || !sameContext(context, task)
                    || !canReferenceTask(context.accountId(), task)) {
                throw invalid("WORK_LOG_TASK_REFERENCE_INVALID", "关联任务不存在、越权或不在当前上下文");
            }
            if (projectId != null && !Objects.equals(task.getProjectId(), projectId)) {
                throw invalid("WORK_LOG_TASK_PROJECT_MISMATCH", "关联任务不属于所选项目");
            }
            tasks.add(task);
        }
        return new ReferenceBundle(project, List.copyOf(tasks), validateBusinessReference(
                context, businessType, businessId, suppliedBusinessTitle, traceId));
    }

    private BusinessReference validateBusinessReference(
            AuthenticatedContext context, String businessType, String businessId,
            String suppliedTitle, String traceId) {
        String type = blankToNull(businessType);
        String id = blankToNull(businessId);
        if (type == null && id == null) return new BusinessReference(null, null, null);
        if (type == null || id == null) throw invalid("WORK_LOG_BUSINESS_REFERENCE_INCOMPLETE", "关联业务对象必须同时选择模块和记录");
        String moduleCode = type.startsWith("MODULE:") ? type.substring(7) : type;
        Long recordId = asLong(id);
        if (recordId == null) throw invalid("WORK_LOG_BUSINESS_REFERENCE_INVALID", "关联业务记录标识无效");
        var record = runtimeDataService.detail(context, moduleCode, recordId, traceId);
        String title = record.title() == null || record.title().isBlank() ? blankToNull(suppliedTitle) : record.title();
        return new BusinessReference("MODULE:" + moduleCode, String.valueOf(record.id()), title);
    }

    private void replaceTaskLinks(Long logId, List<WorkTask> tasks) {
        for (WorkLogTaskLink link : taskLinks(logId)) logTaskLinkService.deleteById(link.getId());
        for (WorkTask task : tasks) {
            WorkLogTaskLink link = new WorkLogTaskLink();
            link.setWorkLogId(logId);
            link.setTaskId(task.getId());
            logTaskLinkService.insert(link);
        }
    }

    private List<WorkLogTaskLink> taskLinks(Long logId) {
        return logTaskLinkService.selectList(Wrappers.<WorkLogTaskLink>lambdaQuery()
                .eq(WorkLogTaskLink::getWorkLogId, logId).orderByAsc(WorkLogTaskLink::getId));
    }

    private List<Long> linkedTaskIds(Long logId) {
        return taskLinks(logId).stream().map(WorkLogTaskLink::getTaskId).toList();
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

    private boolean sameContext(AuthenticatedContext context, TodoItem item) {
        return Objects.equals(item.getPlatformId(), context.platformId())
                && Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.systemId() == null ? null : context.tenantId());
    }

    private boolean sameContext(AuthenticatedContext context, MsgMessage item) {
        return Objects.equals(item.getPlatformId(), context.platformId())
                && Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.systemId() == null ? null : context.tenantId());
    }

    private boolean sameContext(AuthenticatedContext context, AuditEvent item) {
        if (context.systemId() == null) {
            return item.getSystemId() == null && item.getTenantId() == null
                    && (item.getPlatformId() == null || Objects.equals(item.getPlatformId(), context.platformId()));
        }
        return Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.tenantId());
    }

    private boolean canReferenceProject(Long accountId, WorkProject project) {
        return Objects.equals(project.getOwnerAccountId(), accountId)
                || !projectMemberService.selectList(Wrappers.<WorkProjectMember>lambdaQuery()
                .eq(WorkProjectMember::getProjectId, project.getId())
                .eq(WorkProjectMember::getAccountId, accountId)
                .eq(WorkProjectMember::getStatus, "ACTIVE")).isEmpty();
    }

    private boolean canReferenceTask(Long accountId, WorkTask task) {
        if (Objects.equals(task.getOwnerAccountId(), accountId)) return true;
        if (!taskMemberService.selectList(Wrappers.<WorkTaskMember>lambdaQuery()
                .eq(WorkTaskMember::getTaskId, task.getId())
                .eq(WorkTaskMember::getAccountId, accountId)).isEmpty()) return true;
        WorkProject project = task.getProjectId() == null ? null : projectService.selectById(task.getProjectId());
        return project != null && canReferenceProject(accountId, project);
    }

    private void auditBusinessRecordLog(AuthenticatedContext context, WorkLog log, String eventType, String traceId) {
        if (log.getBusinessType() == null || log.getBusinessId() == null) return;
        Long recordId = asLong(log.getBusinessId());
        if (recordId == null) return;
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("logId", log.getId());
        detail.put("logTitle", log.getTitle());
        detail.put("logStatus", log.getStatus());
        detail.put("workDate", log.getWorkDate().toString());
        detail.put("durationMinutes", log.getDurationMinutes());
        detail.put("authorTenantMemberId", log.getAuthorTenantMemberId());
        detail.put("taskIds", linkedTaskIds(log.getId()));
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "CREATED".equals(eventType) ? "BUSINESS_RECORD_LOG_CREATED" : "BUSINESS_RECORD_LOG_UPDATED",
                "BUSINESS_RECORD", recordId.toString(), "SUCCESS", detail);
    }

    private String searchableText(WorkLog log) {
        return String.join(" ", List.of(nullToEmpty(log.getTitle()), nullToEmpty(legacyCompleted(log)),
                nullToEmpty(log.getInProgressText()), nullToEmpty(log.getNextPlanText()),
                nullToEmpty(log.getRiskText()), nullToEmpty(log.getAssistanceText()),
                nullToEmpty(log.getBusinessTitle()))).toLowerCase(Locale.ROOT);
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

    private void audit(AuthenticatedContext context, String traceId, String eventCode, Object objectId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, "WORK_LOG", String.valueOf(objectId), "SUCCESS", detail);
    }

    private Map<String, Object> readMap(String json) {
        try {
            return json == null || json.isBlank() ? Map.of() : objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot read work log JSON", exception);
        }
    }

    private List<Long> readLongList(String json) {
        try {
            return json == null || json.isBlank() ? List.of() : objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot read work log role JSON", exception);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize work log JSON", exception);
        }
    }

    private List<Long> normalizeIds(List<Long> ids) {
        if (ids == null) return List.of();
        return List.copyOf(new LinkedHashSet<>(ids.stream().filter(Objects::nonNull).toList()));
    }

    private Long asLong(Object value) {
        if (value == null) return null;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException exception) { return null; }
    }

    private WorkManagementModels.PersonView personView(WorkParticipantResolver.ResolvedPerson person) {
        if (person == null) return new WorkManagementModels.PersonView(null, "已失效成员", null, null);
        return new WorkManagementModels.PersonView(person.tenantMemberId(), person.displayName(),
                person.departmentName(), person.positionTitle());
    }

    private String displayName(AuthenticatedContext context, Long accountId) {
        WorkParticipantResolver.ResolvedPerson person = participantResolver.find(context, null, accountId);
        return person == null ? "已失效成员" : person.displayName();
    }

    private String clean(String value) { return value == null ? null : value.strip(); }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.strip(); }
    private String nullToEmpty(String value) { return value == null ? "" : value; }
    private String nullToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String blankToDefault(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private String safeJoin(String first, String second) { return blankToDefault(first, "对象") + " · " + blankToDefault(second, "-"); }
    private String clip(String value, int maximum) {
        if (value == null) return null;
        String clean = value.strip();
        return clean.length() <= maximum ? clean : clean.substring(0, maximum) + "…";
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict() {
        return new DomainException("WORK_LOG_VERSION_CONFLICT", "工作日志已被其他人修订，请刷新后重试", HttpStatus.CONFLICT);
    }

    private record ReferenceBundle(WorkProject project, List<WorkTask> tasks, BusinessReference business) { }
    private record BusinessReference(String type, String id, String title) { }
    private record Visibility(String type, List<Long> roleIds) { }
}
