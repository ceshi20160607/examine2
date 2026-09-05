package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.authorization.manage.PermissionResolver;
import com.unique.unexamine.authorization.manage.PlatformPermissionResolver;
import com.unique.unexamine.authorization.manage.ResolvedPermissions;
import com.unique.unexamine.flow.base.entity.FlowAction;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowException;
import com.unique.unexamine.flow.base.entity.FlowExecution;
import com.unique.unexamine.flow.base.entity.FlowHistoryEvent;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowInstanceVariable;
import com.unique.unexamine.flow.base.entity.FlowJob;
import com.unique.unexamine.flow.base.entity.FlowPublication;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.entity.FlowTaskCandidate;
import com.unique.unexamine.flow.base.entity.FlowVersion;
import com.unique.unexamine.flow.base.mapper.FlowInstanceMapper;
import com.unique.unexamine.flow.base.mapper.FlowJobMapper;
import com.unique.unexamine.flow.base.service.FlowActionBaseService;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowExceptionBaseService;
import com.unique.unexamine.flow.base.service.FlowExecutionBaseService;
import com.unique.unexamine.flow.base.service.FlowHistoryEventBaseService;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.flow.base.service.FlowInstanceVariableBaseService;
import com.unique.unexamine.flow.base.service.FlowJobBaseService;
import com.unique.unexamine.flow.base.service.FlowPublicationBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskCandidateBaseService;
import com.unique.unexamine.flow.base.service.FlowVersionBaseService;
import com.unique.unexamine.notification.manage.MessageService;
import com.unique.unexamine.platform.base.entity.PlatformMember;
import com.unique.unexamine.platform.base.service.PlatformMemberBaseService;
import com.unique.unexamine.runtimedata.manage.RuntimeDataService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class FlowRuntimeService {
    private static final Set<String> TASK_ACTIONS = Set.of("APPROVE", "REJECT", "RETURN", "TRANSFER");
    private static final Set<String> INSTANCE_ACTIONS = Set.of("WITHDRAW", "TERMINATE");
    private static final Set<String> ACTIVE_INSTANCE_STATUSES = Set.of("RUNNING", "WAITING", "EXCEPTION");
    private static final Pattern CONDITION = Pattern.compile(
            "^([A-Za-z_][A-Za-z0-9_.-]*)\\s*(==|!=|>=|<=|>|<)\\s*(.+)$");

    private final FlowDefinitionBaseService definitionService;
    private final FlowPublicationBaseService publicationService;
    private final FlowVersionBaseService versionService;
    private final FlowInstanceBaseService instanceService;
    private final FlowInstanceVariableBaseService variableService;
    private final FlowTaskBaseService taskService;
    private final FlowTaskCandidateBaseService candidateService;
    private final FlowActionBaseService actionService;
    private final FlowExceptionBaseService exceptionService;
    private final FlowExecutionBaseService executionService;
    private final FlowHistoryEventBaseService historyService;
    private final FlowJobBaseService jobService;
    private final FlowParticipantResolver participantResolver;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final SystemMemberBaseService systemMemberService;
    private final PlatformMemberBaseService platformMemberService;
    private final RuntimeDataService runtimeDataService;
    private final MessageService messageService;
    private final PermissionResolver permissionResolver;
    private final PlatformPermissionResolver platformPermissionResolver;
    private final FlowInstanceMapper instanceMapper;
    private final FlowJobMapper jobMapper;

    public FlowRuntimeService(
            FlowDefinitionBaseService definitionService,
            FlowPublicationBaseService publicationService,
            FlowVersionBaseService versionService,
            FlowInstanceBaseService instanceService,
            FlowInstanceVariableBaseService variableService,
            FlowTaskBaseService taskService,
            FlowTaskCandidateBaseService candidateService,
            FlowActionBaseService actionService,
            FlowExceptionBaseService exceptionService,
            FlowExecutionBaseService executionService,
            FlowHistoryEventBaseService historyService,
            FlowJobBaseService jobService,
            FlowParticipantResolver participantResolver,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            SystemMemberBaseService systemMemberService,
            PlatformMemberBaseService platformMemberService,
            RuntimeDataService runtimeDataService,
            MessageService messageService,
            PermissionResolver permissionResolver,
            PlatformPermissionResolver platformPermissionResolver,
            FlowInstanceMapper instanceMapper,
            FlowJobMapper jobMapper) {
        this.definitionService = definitionService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.variableService = variableService;
        this.taskService = taskService;
        this.candidateService = candidateService;
        this.actionService = actionService;
        this.exceptionService = exceptionService;
        this.executionService = executionService;
        this.historyService = historyService;
        this.jobService = jobService;
        this.participantResolver = participantResolver;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.systemMemberService = systemMemberService;
        this.platformMemberService = platformMemberService;
        this.runtimeDataService = runtimeDataService;
        this.messageService = messageService;
        this.permissionResolver = permissionResolver;
        this.platformPermissionResolver = platformPermissionResolver;
        this.instanceMapper = instanceMapper;
        this.jobMapper = jobMapper;
    }

    @Transactional(readOnly = true)
    public List<FlowRuntimeModels.InstanceView> instances(AuthenticatedContext context) {
        requireAction(context, "VIEW_RUNTIME");
        return scopedInstances(context).stream()
                .filter(instance -> canView(context, instance))
                .sorted(Comparator.comparing(FlowInstance::getStartedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(instance -> view(context, instance)).toList();
    }

    @Transactional(readOnly = true)
    public FlowRuntimeModels.InstanceView instance(AuthenticatedContext context, Long instanceId) {
        requireAction(context, "VIEW_RUNTIME");
        FlowInstance instance = requireScoped(context, instanceId);
        if (!canView(context, instance)) {
            throw forbidden("FLOW_INSTANCE_VIEW_DENIED", "实例与业务对象权限交集不允许查看");
        }
        return view(context, instance);
    }

    @Transactional(readOnly = true)
    public FlowRuntimeModels.ManualNodePreview previewManualNode(
            AuthenticatedContext context, Long instanceId, FlowRuntimeModels.ManualNodeRequest input) {
        requireAction(context, "ADD_MANUAL_NODE");
        FlowInstance instance = requireScoped(context, instanceId);
        return validateManualNode(context, instance, input);
    }

    @Transactional
    public FlowRuntimeModels.ManualNodeResult addManualNode(
            AuthenticatedContext context, Long instanceId, FlowRuntimeModels.ManualNodeRequest input,
            String traceId) {
        requireAction(context, "ADD_MANUAL_NODE");
        FlowInstance instance = requireScopedForUpdate(context, instanceId);
        FlowAction replay = findAction(instanceId, input.idempotencyKey());
        if (replay != null && replay.getTaskId() != null) {
            return new FlowRuntimeModels.ManualNodeResult(replay.getTaskId(), replay.getId(), view(context, instance));
        }
        FlowRuntimeModels.ManualNodePreview preview = validateManualNode(context, instance, input);
        FlowParticipantResolver.ResolvedPerson manualAssignee = participantResolver.requirePerson(
                context, input.assigneeTenantMemberId());
        FlowTask suspended = taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getId, preview.suspendedTaskId()).last("FOR UPDATE")).stream().findFirst()
                .orElseThrow(() -> conflict("FLOW_MANUAL_POSITION_CHANGED", "当前审批节点已变化，请重新预览"));
        if (!"PENDING".equals(suspended.getStatus())) {
            throw conflict("FLOW_MANUAL_POSITION_CHANGED", "当前审批节点已处理，请重新读取实例");
        }
        suspended.setStatus("SUSPENDED");
        suspended.setUpdatedAt(LocalDateTime.now());
        if (taskService.updateById(suspended) != 1) {
            throw conflict("FLOW_MANUAL_POSITION_CHANGED", "当前审批节点已被其他操作更新");
        }
        String nodeKey = "manual_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, Object> manualSnapshot = new LinkedHashMap<>();
        manualSnapshot.put("manual", true);
        manualSnapshot.put("position", "BEFORE_CURRENT");
        manualSnapshot.put("reason", input.reason().strip());
        manualSnapshot.put("addedByTenantMemberId", participantResolver.currentTenantMemberId(context));
        manualSnapshot.put("assigneeName", manualAssignee.displayName());
        manualSnapshot.put("resumeTaskId", suspended.getId());
        manualSnapshot.put("resumeNodeKey", suspended.getNodeKey());
        manualSnapshot.put("statusMappings", safeNestedMappings(input.statusMappings()));
        FlowTask manual = new FlowTask();
        manual.setInstanceId(instanceId); manual.setNodeKey(nodeKey); manual.setTaskType("MANUAL_APPROVAL");
        manual.setStatus("PENDING"); manual.setAssigneeAccountId(manualAssignee.accountId());
        manual.setAssigneeTenantMemberId(manualAssignee.tenantMemberId());
        manual.setAssigneeSnapshotJson(toJson(manualSnapshot)); manual.setVersion(0);
        taskService.insert(manual);
        addCandidate(manual.getId(), manualAssignee,
                "由" + displayName(context, participantResolver.currentTenantMemberId(context))
                        + "手动加签：" + input.reason().strip());
        instance.setCurrentNodeKey(nodeKey); instance.setStatus("WAITING"); instance.setUpdatedAt(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "实例已被其他操作更新");
        }
        FlowAction action = insertAction(instanceId, manual.getId(), nodeKey, "MANUAL_NODE_ADDED",
                input.reason().strip(), Map.of("assigneeTenantMemberId", input.assigneeTenantMemberId(),
                        "position", "BEFORE_CURRENT", "statusMappings", safeNestedMappings(input.statusMappings())),
                result(instance), input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_MANUAL_NODE_ADDED", "FLOW_TASK", manual.getId(),
                Map.of("instanceId", instanceId, "suspendedTaskId", suspended.getId(),
                        "assigneeTenantMemberId", input.assigneeTenantMemberId(), "reason", input.reason().strip()));
        return new FlowRuntimeModels.ManualNodeResult(manual.getId(), action.getId(),
                view(context, instanceService.selectById(instanceId)));
    }

    @Transactional
    public FlowRuntimeModels.ActionResult start(
            AuthenticatedContext context, FlowRuntimeModels.StartRequest input, String traceId) {
        requireAction(context, "START");
        FlowDefinition definition = requireFlow(context, input.flowId());
        requireBusinessAccess(context, input.businessType());
        FlowAction replay = findStartReplay(context.accountId(), input.idempotencyKey());
        if (replay != null) {
            FlowInstance existing = requireScoped(context, replay.getInstanceId());
            return new FlowRuntimeModels.ActionResult(replay.getId(), true, view(context, existing));
        }
        FlowPublication publication = publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                .eq(FlowPublication::getFlowId, definition.getId())).stream().findFirst()
                .orElseThrow(() -> invalid("FLOW_RUNTIME_VERSION_REQUIRED", "Flow 没有可用于发起的发布版本"));
        FlowVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getFlowId(), definition.getId())) {
            throw invalid("FLOW_RUNTIME_VERSION_INCOMPATIBLE", "Flow 当前发布版本不存在或不兼容");
        }
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        Map<String, Object> startNode = nodes(snapshot).stream()
                .filter(node -> "START".equals(string(node.get("nodeType")))).findFirst()
                .orElseThrow(() -> invalid("FLOW_RUNTIME_START_NODE_MISSING", "发布快照缺少开始节点"));

        FlowInstance instance = new FlowInstance();
        instance.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        instance.setPlatformId(context.platformId());
        instance.setSystemId(context.systemId());
        instance.setTenantId(context.tenantId());
        instance.setFlowId(definition.getId());
        instance.setFlowVersionId(version.getId());
        instance.setBusinessType(blankToNull(input.businessType()));
        instance.setBusinessId(blankToNull(input.businessId()));
        instance.setBusinessSnapshotJson(toJson(safeMap(input.businessSnapshot())));
        instance.setTitle(input.title().strip());
        instance.setCurrentNodeKey(string(startNode.get("nodeKey")));
        instance.setStatus("RUNNING");
        instance.setStartedByAccountId(context.accountId());
        instance.setStartedByTenantMemberId(participantResolver.currentTenantMemberId(context));
        instance.setVersion(0);
        instanceService.insert(instance);
        FlowExecution startExecution = recordExecution(
                instance, string(startNode.get("nodeKey")), "START", "ACTIVE", null);
        completeExecution(startExecution, "STARTED");
        recordHistory(instance, null, null, string(startNode.get("nodeKey")), "INSTANCE", "流程已发起",
                null, "RUNNING", context.accountId(), instance.getStartedByTenantMemberId(),
                Map.of("flowVersionId", version.getId(), "definitionHash", version.getDefinitionHash()));
        saveVariables(instance.getId(), safeMap(input.variables()));
        advance(context, instance, snapshot, string(startNode.get("nodeKey")),
                variables(instance.getId()), null);
        FlowAction action = insertAction(instance.getId(), null, string(startNode.get("nodeKey")), "START",
                null, Map.of("flowId", definition.getId(), "variables", safeMap(input.variables())),
                result(instance), input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_INSTANCE_STARTED", "FLOW_INSTANCE", instance.getId(),
                Map.of("flowId", definition.getId(), "flowVersionId", version.getId(),
                        "status", instance.getStatus()));
        return new FlowRuntimeModels.ActionResult(action.getId(), false,
                view(context, instanceService.selectById(instance.getId())));
    }

    @Transactional
    public FlowRuntimeModels.ActionResult handle(
            AuthenticatedContext context, Long taskId, FlowRuntimeModels.HandleTaskRequest input, String traceId) {
        requireAction(context, "HANDLE");
        String actionCode = input.actionCode().strip().toUpperCase(Locale.ROOT);
        if (!TASK_ACTIONS.contains(actionCode)) {
            throw invalid("FLOW_TASK_ACTION_INVALID", "审批任务动作无效");
        }
        FlowTask initial = taskService.selectById(taskId);
        if (initial == null) throw notFound("FLOW_TASK_NOT_FOUND", "审批任务不存在");
        FlowInstance scoped = requireScoped(context, initial.getInstanceId());
        FlowAction replay = findAction(scoped.getId(), input.idempotencyKey());
        if (replay != null) return replay(context, replay);

        FlowTask task = taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getId, taskId).last("FOR UPDATE")).stream().findFirst()
                .orElseThrow(() -> notFound("FLOW_TASK_NOT_FOUND", "审批任务不存在"));
        FlowAction lockedReplay = findAction(scoped.getId(), input.idempotencyKey());
        if (lockedReplay != null) return replay(context, lockedReplay);
        if (!"PENDING".equals(task.getStatus())) {
            throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已处理，请读取实例最新结果");
        }
        Long currentTenantMemberId = participantResolver.currentTenantMemberId(context);
        if (!isCurrentHandler(currentTenantMemberId, context.accountId(), task)) {
            throw forbidden("FLOW_TASK_HANDLER_DENIED", "只有当前有效处理人可以处理此任务");
        }
        if (("REJECT".equals(actionCode) || "RETURN".equals(actionCode))
                && (input.comment() == null || input.comment().isBlank())) {
            throw invalid("FLOW_TASK_REASON_REQUIRED", "拒绝或退回必须填写原因");
        }
        saveVariables(scoped.getId(), safeMap(input.variables()));
        Map<String, Object> snapshot = snapshot(scoped);
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        boolean manualTask = Boolean.TRUE.equals(taskSnapshot.get("manual"));
        if (manualTask && !"TRANSFER".equals(actionCode)) {
            applyManualStatusMapping(context, scoped, taskSnapshot, actionCode, traceId);
        }

        if ("TRANSFER".equals(actionCode)) {
            if (input.targetTenantMemberId() == null || input.targetTenantMemberId() <= 0
                    || Objects.equals(input.targetTenantMemberId(), currentTenantMemberId)) {
                throw invalid("FLOW_TASK_TRANSFER_TARGET_INVALID", "转交必须选择当前工作空间中的其他成员");
            }
            FlowParticipantResolver.ResolvedPerson target = participantResolver.requirePerson(
                    context, input.targetTenantMemberId());
            task.setAssigneeAccountId(target.accountId());
            task.setAssigneeTenantMemberId(target.tenantMemberId());
            Map<String, Object> transferredSnapshot = new LinkedHashMap<>(taskSnapshot);
            transferredSnapshot.put("type", "PERSON");
            transferredSnapshot.put("tenantMemberIds", List.of(target.tenantMemberId()));
            transferredSnapshot.put("resolvedPeople", List.of(personSnapshot(target)));
            transferredSnapshot.put("transferredByTenantMemberId", currentTenantMemberId);
            task.setAssigneeSnapshotJson(toJson(transferredSnapshot));
            task.setUpdatedAt(LocalDateTime.now());
            if (taskService.updateById(task) != 1) {
                throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新");
            }
            addCandidate(task.getId(), target,
                    "任务由" + displayName(context, currentTenantMemberId) + "转交");
            messageService.notifyFlowEvent(context, scoped.getId(),
                    "task-" + task.getId() + "-transfer-" + target.tenantMemberId() + "-v" + task.getVersion(),
                    "审批已转交：" + scoped.getTitle(),
                    "流程已转交给你，当前节点：" + task.getNodeKey() + "。请在待办或流程运行页处理。",
                    List.of(target.accountId()));
        } else {
            task.setStatus(actionCode);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            if (taskService.updateById(task) != 1) {
                throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新");
            }
            if ("APPROVE".equals(actionCode)) {
                if (manualTask) {
                    completeActiveExecution(scoped.getId(), task.getNodeKey(), actionCode);
                    resumeAfterManualNode(scoped, taskSnapshot);
                } else if (activateNextApprovalTaskOrWait(scoped, task, taskSnapshot)) {
                    scoped.setStatus("WAITING");
                    scoped.setUpdatedAt(LocalDateTime.now());
                    instanceService.updateById(scoped);
                } else {
                    completeActiveExecution(scoped.getId(), task.getNodeKey(), actionCode);
                    scoped.setStatus("RUNNING");
                    scoped.setUpdatedAt(LocalDateTime.now());
                    instanceService.updateById(scoped);
                    advance(context, scoped, snapshot, task.getNodeKey(), variables(scoped.getId()),
                            currentTenantMemberId);
                }
            } else {
                completeActiveExecution(scoped.getId(), task.getNodeKey(), actionCode);
                cancelSiblingApprovalTasks(scoped.getId(), task);
                scoped.setStatus("REJECT".equals(actionCode) ? "REJECTED" : "RETURNED");
                scoped.setFinishedAt(LocalDateTime.now());
                scoped.setUpdatedAt(LocalDateTime.now());
                instanceService.updateById(scoped);
            }
        }
        FlowInstance latest = instanceService.selectById(scoped.getId());
        FlowAction action;
        try {
            action = insertAction(latest.getId(), task.getId(), task.getNodeKey(), actionCode,
                    blankToNull(input.comment()), Map.of("variables", safeMap(input.variables()),
                            "targetTenantMemberId", input.targetTenantMemberId() == null ? "" : input.targetTenantMemberId()),
                    result(latest), input.idempotencyKey(), context.accountId());
        } catch (DuplicateKeyException duplicate) {
            FlowAction duplicateReplay = findAction(latest.getId(), input.idempotencyKey());
            if (duplicateReplay != null) return replay(context, duplicateReplay);
            throw duplicate;
        }
        audit(context, traceId, "FLOW_TASK_" + actionCode, "FLOW_TASK", task.getId(),
                Map.of("instanceId", latest.getId(), "status", latest.getStatus()));
        if (Set.of("REJECTED", "RETURNED").contains(latest.getStatus())) {
            messageService.notifyFlowEvent(context, latest.getId(), "action-" + action.getId() + "-result",
                    "流程结果：" + latest.getTitle(),
                    "流程已" + flowStatusName(latest.getStatus()) + "，可打开消息查看完整审批历史。",
                    List.of(latest.getStartedByAccountId()));
        }
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, latest));
    }

    @Transactional
    public FlowRuntimeModels.ActionResult actOnInstance(
            AuthenticatedContext context, Long instanceId,
            FlowRuntimeModels.InstanceActionRequest input, String traceId) {
        String actionCode = input.actionCode().strip().toUpperCase(Locale.ROOT);
        if (!INSTANCE_ACTIONS.contains(actionCode)) {
            throw invalid("FLOW_INSTANCE_ACTION_INVALID", "实例动作无效");
        }
        if ("TERMINATE".equals(actionCode)) requireAction(context, "TERMINATE");
        else requireAction(context, "HANDLE");
        FlowInstance instance = requireScopedForUpdate(context, instanceId);
        FlowAction existing = findAction(instanceId, input.idempotencyKey());
        if (existing != null) return replay(context, existing);
        if (!ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())) {
            throw conflict("FLOW_INSTANCE_ALREADY_FINISHED", "实例已结束，请读取最新结果");
        }
        if ("WITHDRAW".equals(actionCode) && !Objects.equals(instance.getStartedByAccountId(), context.accountId())) {
            throw forbidden("FLOW_INSTANCE_WITHDRAW_DENIED", "只有发起人可以撤回实例");
        }
        instance.setStatus("WITHDRAW".equals(actionCode) ? "WITHDRAWN" : "TERMINATED");
        instance.setFinishedAt(LocalDateTime.now());
        instance.setUpdatedAt(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "实例已被其他操作更新");
        }
        for (FlowTask task : tasks(instanceId)) {
            if ("PENDING".equals(task.getStatus())) {
                task.setStatus("CANCELLED");
                task.setCompletedAt(LocalDateTime.now());
                task.setUpdatedAt(LocalDateTime.now());
                taskService.updateById(task);
            }
        }
        FlowAction action = insertAction(instanceId, null,
                instance.getCurrentNodeKey() == null ? "INSTANCE" : instance.getCurrentNodeKey(),
                actionCode, input.comment().strip(), Map.of(), result(instance),
                input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_INSTANCE_" + actionCode, "FLOW_INSTANCE", instanceId,
                Map.of("status", instance.getStatus()));
        messageService.notifyFlowEvent(context, instanceId, "action-" + action.getId() + "-result",
                "流程已" + flowStatusName(instance.getStatus()) + "：" + instance.getTitle(),
                "流程已" + flowStatusName(instance.getStatus()) + "，处理意见：" + input.comment().strip(),
                List.of(instance.getStartedByAccountId()));
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, instance));
    }

    @Transactional
    public FlowRuntimeModels.ActionResult handleIncident(
            AuthenticatedContext context, Long incidentId,
            FlowRuntimeModels.IncidentActionRequest input, String traceId) {
        requireAction(context, "HANDLE");
        String actionCode = input.actionCode().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("RETRY", "RESUME").contains(actionCode)) {
            throw invalid("FLOW_INCIDENT_ACTION_INVALID", "异常处理只支持重试或人工确认后继续");
        }
        FlowException incident = exceptionService.selectList(Wrappers.<FlowException>lambdaQuery()
                        .eq(FlowException::getId, incidentId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("FLOW_INCIDENT_NOT_FOUND", "流程异常不存在"));
        FlowInstance instance = requireScopedForUpdate(context, incident.getInstanceId());
        FlowAction replay = findAction(instance.getId(), input.idempotencyKey());
        if (replay != null) return replay(context, replay);
        if (!"OPEN".equals(incident.getStatus()) && !"RETRY_SCHEDULED".equals(incident.getStatus())) {
            throw conflict("FLOW_INCIDENT_ALREADY_RESOLVED", "流程异常已经处理，请读取最新结果");
        }
        Long actorTenantMemberId = participantResolver.currentTenantMemberId(context);
        if ("RETRY".equals(actionCode)) {
            FlowJob job = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                            .eq(FlowJob::getInstanceId, instance.getId())
                            .eq(FlowJob::getIdempotencyKey, "RETRY:" + incident.getId()))
                    .stream().findFirst().orElse(null);
            if (job == null) {
                scheduleJob(instance, activeExecution(instance.getId(), incident.getNodeKey()), incident.getNodeKey(),
                        "RETRY", Map.of("exceptionId", incident.getId(), "errorCode", incident.getErrorCode()),
                        "RETRY:" + incident.getId(), 3, LocalDateTime.now());
            } else {
                job.setStatus("PENDING");
                job.setNextRunAt(LocalDateTime.now());
                job.setLastErrorCode(null);
                job.setLastErrorMessage(null);
                jobService.updateById(job);
            }
            incident.setStatus("RETRY_SCHEDULED");
            instance.setStatus("WAITING");
        } else {
            incident.setStatus("RESOLVED");
            incident.setResolvedAt(LocalDateTime.now());
            incident.setResolvedByAccountId(context.accountId());
            incident.setResolvedByTenantMemberId(actorTenantMemberId);
            incident.setResolutionComment(input.comment().strip());
            instance.setStatus("RUNNING");
            instance.setErrorCode(null);
            instance.setErrorMessage(null);
        }
        incident.setVersion(incident.getVersion());
        if (exceptionService.updateById(incident) != 1 || instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INCIDENT_VERSION_CONFLICT", "流程异常已被其他操作处理");
        }
        if ("RESUME".equals(actionCode)) clearInstanceError(instance.getId());
        if ("RESUME".equals(actionCode)) {
            FlowExecution compensation = recordExecution(
                    instance, incident.getNodeKey(), "COMPENSATION", "ACTIVE", null);
            completeExecution(compensation, "MANUAL_RESUME");
            advance(context, instance, snapshot(instance), incident.getNodeKey(), variables(instance.getId()),
                    actorTenantMemberId);
            instance = instanceService.selectById(instance.getId());
        }
        FlowAction action = insertAction(instance.getId(), null, incident.getNodeKey(),
                "INCIDENT_" + actionCode, input.comment().strip(), Map.of("incidentId", incidentId),
                result(instance), input.idempotencyKey(), context.accountId());
        recordHistory(instance, null, null, incident.getNodeKey(), "INCIDENT", actionName("INCIDENT_" + actionCode),
                "EXCEPTION", instance.getStatus(), context.accountId(), actorTenantMemberId,
                Map.of("incidentId", incidentId, "comment", input.comment().strip()));
        audit(context, traceId, "FLOW_INCIDENT_" + actionCode, "FLOW_EXCEPTION", incidentId,
                Map.of("instanceId", instance.getId(), "status", instance.getStatus()));
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, instance));
    }

    /** Runs persisted timers and retry jobs. Invoked by the dedicated worker, never by a browser request. */
    @Transactional
    public int runDueJobs(String workerId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        List<Long> dueIds = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                        .eq(FlowJob::getStatus, "PENDING")
                        .le(FlowJob::getNextRunAt, LocalDateTime.now())
                        .orderByAsc(FlowJob::getNextRunAt).orderByAsc(FlowJob::getId)
                        .last("LIMIT " + limit)).stream().map(FlowJob::getId).toList();
        int processed = 0;
        for (Long jobId : dueIds) {
            FlowJob job = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                            .eq(FlowJob::getId, jobId).last("FOR UPDATE"))
                    .stream().findFirst().orElse(null);
            if (job == null || !"PENDING".equals(job.getStatus())
                    || job.getNextRunAt().isAfter(LocalDateTime.now())) continue;
            runJob(job, workerId == null ? "flow-worker" : workerId);
            processed++;
        }
        return processed;
    }

    private void runJob(FlowJob job, String workerId) {
        FlowInstance instance = instanceService.selectList(Wrappers.<FlowInstance>lambdaQuery()
                        .eq(FlowInstance::getId, job.getInstanceId()).last("FOR UPDATE"))
                .stream().findFirst().orElse(null);
        if (instance == null || !Set.of("RUNNING", "WAITING", "EXCEPTION").contains(instance.getStatus())) {
            finishJob(job, "CANCELLED", "FLOW_JOB_INSTANCE_INACTIVE", "流程实例不存在或已结束");
            return;
        }
        job.setStatus("RUNNING");
        job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
        job.setLockedAt(LocalDateTime.now());
        job.setLockedBy(workerId);
        jobService.updateById(job);
        AuthenticatedContext context = systemContext(instance);
        if ("TIMER".equals(job.getJobType())) {
            completeExecution(executionService.selectById(job.getExecutionId()), "TIMER_ELAPSED");
            finishJob(job, "COMPLETED", null, null);
            instance.setStatus("RUNNING");
            instance.setErrorCode(null);
            instance.setErrorMessage(null);
            instance.setUpdatedAt(LocalDateTime.now());
            instanceService.updateById(instance);
            clearInstanceError(instance.getId());
            recordHistory(instance, job.getExecutionId(), null, job.getNodeKey(), "JOB", "定时等待已结束",
                    "WAITING", "RUNNING", null, null, Map.of("jobId", job.getId()));
            advance(context, instance, snapshot(instance), job.getNodeKey(), variables(instance.getId()), null);
            return;
        }
        if ("RETRY".equals(job.getJobType())) {
            runRetryJob(context, instance, job);
            return;
        }
        if ("TASK_TIMEOUT".equals(job.getJobType())) {
            runTaskTimeout(context, instance, job);
            return;
        }
        finishJob(job, "FAILED", "FLOW_JOB_TYPE_UNSUPPORTED", "不支持的流程作业类型");
        instance.setStatus("EXCEPTION");
        instance.setErrorCode("FLOW_JOB_TYPE_UNSUPPORTED");
        instance.setErrorMessage("不支持的流程作业类型");
        instanceService.updateById(instance);
    }

    private void runRetryJob(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        Map<String, Object> definition = snapshot(instance);
        Map<String, Object> node = nodes(definition).stream()
                .filter(item -> Objects.equals(job.getNodeKey(), string(item.get("nodeKey"))))
                .findFirst().orElse(Map.of());
        Map<String, Object> config = map(node.get("config"));
        String retryErrorCode = null;
        String retryErrorMessage = null;
        if (Boolean.TRUE.equals(config.get("simulateFailure"))) {
            retryErrorCode = "FLOW_RUNTIME_SERVICE_FAILED";
            retryErrorMessage = "节点服务重试仍失败";
        } else {
            try {
                replayAutomaticNode(context, instance, node, config, variables(instance.getId()), job);
            } catch (DomainException exception) {
                retryErrorCode = exception.code();
                retryErrorMessage = exception.getMessage();
            }
        }
        if (retryErrorCode != null) {
            if (job.getAttemptCount() < job.getMaxAttempts()) {
                job.setStatus("PENDING");
                job.setNextRunAt(LocalDateTime.now().plusMinutes(
                        Math.max(1, number(map(node.get("exceptionPolicy")).get("retryDelayMinutes"), 5))));
                job.setLastErrorCode(retryErrorCode);
                job.setLastErrorMessage(retryErrorMessage);
                job.setLockedAt(null);
                job.setLockedBy(null);
                jobService.updateById(job);
                instance.setStatus("WAITING");
                instanceService.updateById(instance);
                return;
            }
            finishJob(job, "FAILED", retryErrorCode, retryErrorMessage);
            instance.setStatus("EXCEPTION");
            instance.setErrorCode(retryErrorCode);
            instance.setErrorMessage("重试次数已用尽，请人工补偿或继续");
            instanceService.updateById(instance);
            reopenJobIncident(job, retryErrorCode, retryErrorMessage);
            recordHistory(instance, job.getExecutionId(), null, job.getNodeKey(), "JOB", "自动重试次数已用尽",
                    "WAITING", "EXCEPTION", null, null,
                    Map.of("jobId", job.getId(), "attemptCount", job.getAttemptCount()));
            return;
        }
        resolveJobIncident(job, "自动重试成功");
        FlowExecution retryExecution = recordExecution(instance, job.getNodeKey(), "RETRY", "ACTIVE", null);
        completeExecution(retryExecution, "RETRY_SUCCEEDED");
        finishJob(job, "COMPLETED", null, null);
        instance.setStatus("RUNNING");
        instance.setErrorCode(null);
        instance.setErrorMessage(null);
        instance.setUpdatedAt(LocalDateTime.now());
        instanceService.updateById(instance);
        clearInstanceError(instance.getId());
        recordHistory(instance, retryExecution.getId(), null, job.getNodeKey(), "JOB", "自动重试成功",
                "WAITING", "RUNNING", null, null, Map.of("jobId", job.getId()));
        advance(context, instance, definition, job.getNodeKey(), variables(instance.getId()), null);
    }

    private void replayAutomaticNode(
            AuthenticatedContext context, FlowInstance instance, Map<String, Object> node,
            Map<String, Object> config, Map<String, Object> variables, FlowJob job) {
        String nodeType = string(node.get("nodeType"));
        if ("UPDATE_FIELD".equals(nodeType)) {
            String moduleCode = moduleCode(instance.getBusinessType());
            Long recordId = numericBusinessId(instance.getBusinessId());
            if (moduleCode == null || recordId == null) {
                throw invalid("FLOW_WRITEBACK_BUSINESS_REQUIRED", "字段回写节点要求实例关联模块业务记录");
            }
            runtimeDataService.applyFlowWriteback(context, moduleCode, recordId,
                    string(config.get("businessAction")),
                    blankToNull(string(config.get("expectedCurrentStatus"))),
                    blankToNull(string(config.get("targetStatus"))),
                    resolveWritebacks(map(config.get("fieldUpdates")), variables),
                    "flow-retry-" + instance.getId() + "-" + job.getId());
            recordHistory(instance, null, null, job.getNodeKey(), "BUSINESS_WRITEBACK",
                    "业务字段重试回写成功", "FAILED", "COMPLETED", null, null,
                    Map.of("moduleCode", moduleCode, "recordId", recordId,
                            "updatedFields", map(config.get("fieldUpdates")).keySet()));
            return;
        }
        if ("NOTIFICATION".equals(nodeType)) {
            List<Long> recipientIds;
            if (context.systemId() == null) {
                recipientIds = List.of(instance.getStartedByAccountId());
            } else {
                List<FlowParticipantResolver.ResolvedPerson> people = participantResolver.resolve(
                        context, map(node.get("assigneePolicy")), instance, variables, null);
                if (people.isEmpty()) {
                    throw invalid("FLOW_NOTIFICATION_RECIPIENT_UNRESOLVED", "通知节点没有可解析的有效接收人");
                }
                recipientIds = people.stream().map(FlowParticipantResolver.ResolvedPerson::accountId).toList();
            }
            String subject = string(config.get("subject"));
            if (subject.isBlank()) subject = string(node.get("name"));
            messageService.notifyFlowEvent(context, instance.getId(),
                    "instance-" + instance.getId() + "-notification-retry-" + job.getId(),
                    renderFlowText(subject, instance, variables),
                    renderFlowText(string(config.get("content")), instance, variables), recipientIds);
        }
    }

    private void reopenJobIncident(FlowJob job, String errorCode, String errorMessage) {
        Long incidentId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("exceptionId")));
        FlowException incident = incidentId == null ? null : exceptionService.selectById(incidentId);
        if (incident == null) return;
        incident.setStatus("OPEN");
        incident.setErrorCode(errorCode);
        incident.setErrorMessage(errorMessage);
        incident.setVersion(incident.getVersion());
        exceptionService.updateById(incident);
    }

    private void runTaskTimeout(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        Long taskId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("taskId")));
        FlowTask task = taskId == null ? null : taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getId, taskId).last("FOR UPDATE")).stream().findFirst().orElse(null);
        if (task == null || !"PENDING".equals(task.getStatus())) {
            finishJob(job, "CANCELLED", null, null);
            return;
        }
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        Map<String, Object> timeoutPolicy = map(taskSnapshot.get("timeoutPolicy"));
        String action = string(timeoutPolicy.getOrDefault("action", "INCIDENT")).toUpperCase(Locale.ROOT);
        if ("ESCALATE_MANAGER".equals(action)) {
            List<FlowParticipantResolver.ResolvedPerson> managers = participantResolver.resolve(context,
                    Map.of("type", "MANAGER", "source", "PREVIOUS_HANDLER"), instance,
                    variables(instance.getId()), task.getAssigneeTenantMemberId());
            if (!managers.isEmpty()) {
                FlowParticipantResolver.ResolvedPerson manager = managers.getFirst();
                task.setAssigneeTenantMemberId(manager.tenantMemberId());
                task.setAssigneeAccountId(manager.accountId());
                task.setDueAt(null);
                task.setUpdatedAt(LocalDateTime.now());
                taskService.updateById(task);
                addCandidate(task.getId(), manager, "审批超时后升级至直属上级");
                finishJob(job, "COMPLETED", null, null);
                recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                        "审批超时已升级处理人", "PENDING", "PENDING", null, null,
                        Map.of("assignee", personSnapshot(manager)));
                return;
            }
            action = "INCIDENT";
        }
        if ("AUTO_APPROVE".equals(action)) {
            task.setStatus("APPROVE");
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskService.updateById(task);
            finishJob(job, "COMPLETED", null, null);
            if (!activateNextApprovalTaskOrWait(instance, task, taskSnapshot)) {
                completeActiveExecution(instance.getId(), task.getNodeKey(), "TIMEOUT_AUTO_APPROVE");
                instance.setStatus("RUNNING");
                instanceService.updateById(instance);
                advance(context, instance, snapshot(instance), task.getNodeKey(), variables(instance.getId()),
                        task.getAssigneeTenantMemberId());
            }
            recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                    "审批超时自动同意", "PENDING", task.getStatus(), null, null, Map.of());
            return;
        }
        if ("AUTO_REJECT".equals(action)) {
            task.setStatus("REJECT");
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            taskService.updateById(task);
            cancelSiblingApprovalTasks(instance.getId(), task);
            completeActiveExecution(instance.getId(), task.getNodeKey(), "TIMEOUT_AUTO_REJECT");
            instance.setStatus("REJECTED");
            instance.setFinishedAt(LocalDateTime.now());
            instanceService.updateById(instance);
            finishJob(job, "COMPLETED", null, null);
            recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                    "审批超时自动拒绝", "PENDING", "REJECTED", null, null, Map.of());
            return;
        }
        finishJob(job, "FAILED", "FLOW_TASK_TIMEOUT", "审批任务超时，需要人工处理");
        Map<String, Object> node = nodes(snapshot(instance)).stream()
                .filter(item -> Objects.equals(task.getNodeKey(), string(item.get("nodeKey"))))
                .findFirst().orElse(Map.of());
        fail(instance, task.getNodeKey(), node, "TIMEOUT", "FLOW_TASK_TIMEOUT", "审批任务超时，需要人工处理");
    }

    private void resolveJobIncident(FlowJob job, String comment) {
        Long incidentId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("exceptionId")));
        FlowException incident = incidentId == null ? null : exceptionService.selectById(incidentId);
        if (incident == null || "RESOLVED".equals(incident.getStatus())) return;
        incident.setStatus("RESOLVED");
        incident.setResolutionComment(comment);
        incident.setResolvedAt(LocalDateTime.now());
        exceptionService.updateById(incident);
    }

    private void finishJob(FlowJob job, String status, String errorCode, String errorMessage) {
        job.setStatus(status);
        job.setLastErrorCode(errorCode);
        job.setLastErrorMessage(errorMessage);
        job.setCompletedAt(Set.of("COMPLETED", "FAILED", "CANCELLED").contains(status)
                ? LocalDateTime.now() : null);
        job.setLockedAt(null);
        job.setLockedBy(null);
        jobService.updateById(job);
        if (errorCode == null && errorMessage == null) {
            jobMapper.update(null, Wrappers.<FlowJob>lambdaUpdate().eq(FlowJob::getId, job.getId())
                    .set(FlowJob::getLastErrorCode, null)
                    .set(FlowJob::getLastErrorMessage, null)
                    .set(FlowJob::getLockedAt, null)
                    .set(FlowJob::getLockedBy, null));
        }
    }

    private void clearInstanceError(Long instanceId) {
        instanceMapper.update(null, Wrappers.<FlowInstance>lambdaUpdate()
                .eq(FlowInstance::getId, instanceId)
                .set(FlowInstance::getErrorCode, null)
                .set(FlowInstance::getErrorMessage, null));
    }

    private AuthenticatedContext systemContext(FlowInstance instance) {
        SystemMember member = instance.getSystemId() == null ? null
                : systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                        .eq(SystemMember::getSystemId, instance.getSystemId())
                        .eq(SystemMember::getAccountId, instance.getStartedByAccountId())).stream()
                .findFirst().orElse(null);
        PlatformMember platformMember = instance.getSystemId() != null ? null
                : platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                        .eq(PlatformMember::getPlatformId, instance.getPlatformId())
                .eq(PlatformMember::getAccountId, instance.getStartedByAccountId())).stream()
                .findFirst().orElse(null);
        ResolvedPermissions permissions = instance.getSystemId() == null
                ? platformPermissionResolver.resolve(instance.getPlatformId(), instance.getStartedByAccountId())
                : permissionResolver.resolve(instance.getSystemId(), instance.getTenantId(),
                        instance.getStartedByTenantMemberId());
        return new AuthenticatedContext(null, instance.getStartedByAccountId(), instance.getPlatformId(),
                instance.getSystemId(), instance.getTenantId(),
                member == null ? platformMember == null ? null : platformMember.getId() : member.getId(),
                instance.getStartedByTenantMemberId(), "flow-job", "流程作业", "SYSTEM",
                permissions.roleIds(), permissions.permissions(), permissions.dataScopes());
    }

    private void advance(AuthenticatedContext context, FlowInstance instance, Map<String, Object> snapshot,
                         String sourceNodeKey, Map<String, Object> variables,
                         Long previousHandlerTenantMemberId) {
        String current = sourceNodeKey;
        for (int step = 0; step < 100; step++) {
            Map<String, Object> next = nextNode(snapshot, current, variables);
            if (next == null) {
                fail(instance, current, Map.of(), "PATH_RESOLUTION", "FLOW_RUNTIME_PATH_NOT_FOUND",
                        "当前节点没有命中的唯一后续路径");
                return;
            }
            current = string(next.get("nodeKey"));
            String nodeType = string(next.get("nodeType"));
            FlowExecution execution = recordExecution(instance, current, nodeType, "ACTIVE", null);
            instance.setCurrentNodeKey(current);
            instance.setUpdatedAt(LocalDateTime.now());
            if ("END".equals(nodeType)) {
                instance.setStatus("COMPLETED");
                instance.setFinishedAt(LocalDateTime.now());
                instanceService.updateById(instance);
                completeExecution(execution, "COMPLETED");
                insertAction(instance.getId(), null, current, "AUTO_END", null, Map.of(), result(instance),
                        "AUTO:" + current + ":" + UUID.randomUUID(), instance.getStartedByAccountId());
                messageService.notifyFlowEvent(context, instance.getId(),
                        "instance-" + instance.getId() + "-completed",
                        "流程已完成：" + instance.getTitle(),
                        "流程已到达结束节点，业务结果和完整处理历史可以在流程运行页查看。",
                        List.of(instance.getStartedByAccountId()));
                return;
            }
            if ("APPROVAL".equals(nodeType)) {
                if (!createApprovalTask(context, instance, next, variables, previousHandlerTenantMemberId)) return;
                instance.setStatus("WAITING");
                instanceService.updateById(instance);
                return;
            }
            Map<String, Object> config = map(next.get("config"));
            if ("NOTIFICATION".equals(nodeType)) {
                List<Long> recipientAccountIds;
                if (context.systemId() == null) {
                    recipientAccountIds = List.of(instance.getStartedByAccountId());
                } else {
                    List<FlowParticipantResolver.ResolvedPerson> recipients = participantResolver.resolve(
                            context, map(next.get("assigneePolicy")), instance, variables,
                            previousHandlerTenantMemberId);
                    if (recipients.isEmpty()) {
                        fail(instance, current, next, "NOTIFICATION", "FLOW_NOTIFICATION_RECIPIENT_UNRESOLVED",
                                "通知节点没有可解析的有效接收人");
                        return;
                    }
                    recipientAccountIds = recipients.stream()
                            .map(FlowParticipantResolver.ResolvedPerson::accountId).toList();
                }
                String subject = string(config.get("subject"));
                if (subject.isBlank()) subject = string(next.get("name"));
                messageService.notifyFlowEvent(context, instance.getId(),
                        "instance-" + instance.getId() + "-notification-" + execution.getId(),
                        renderFlowText(subject, instance, variables),
                        renderFlowText(string(config.get("content")), instance, variables),
                        recipientAccountIds);
            }
            if ("UPDATE_FIELD".equals(nodeType)) {
                String moduleCode = moduleCode(instance.getBusinessType());
                Long recordId = numericBusinessId(instance.getBusinessId());
                if (moduleCode == null || recordId == null) {
                    fail(instance, current, next, "WRITEBACK", "FLOW_WRITEBACK_BUSINESS_REQUIRED",
                            "字段回写节点要求实例关联模块业务记录");
                    return;
                }
                try {
                    runtimeDataService.applyFlowWriteback(context, moduleCode, recordId,
                            string(config.get("businessAction")),
                            blankToNull(string(config.get("expectedCurrentStatus"))),
                            blankToNull(string(config.get("targetStatus"))),
                            resolveWritebacks(map(config.get("fieldUpdates")), variables),
                            "flow-" + instance.getId() + "-" + current);
                    recordHistory(instance, execution.getId(), null, current, "BUSINESS_WRITEBACK",
                            "业务字段已回写", null, "COMPLETED", null,
                            instance.getStartedByTenantMemberId(),
                            Map.of("moduleCode", moduleCode, "recordId", recordId,
                                    "updatedFields", map(config.get("fieldUpdates")).keySet()));
                } catch (DomainException exception) {
                    fail(instance, current, next, "WRITEBACK", exception.code(), exception.getMessage());
                    return;
                }
            }
            if ("WAIT_TIMER".equals(nodeType)) {
                scheduleJob(instance, execution, current, "TIMER", config,
                        "TIMER:" + instance.getId() + ":" + current,
                        number(config.get("maxAttempts"), 1), timerDueAt(config));
                instance.setStatus("WAITING");
                instanceService.updateById(instance);
                return;
            }
            if (("WEBHOOK".equals(nodeType) || "AI".equals(nodeType))
                    && Boolean.TRUE.equals(config.get("simulateFailure"))) {
                fail(instance, current, next, "SERVICE", "FLOW_RUNTIME_SERVICE_FAILED",
                        "节点服务按测试配置返回失败");
                return;
            }
            instanceService.updateById(instance);
            completeExecution(execution, "COMPLETED");
            insertAction(instance.getId(), null, current, "AUTO_EXECUTED", null,
                    Map.of("nodeType", nodeType), result(instance),
                    "AUTO:" + current + ":" + UUID.randomUUID(), instance.getStartedByAccountId());
        }
        fail(instance, current, Map.of(), "ENGINE", "FLOW_RUNTIME_STEP_LIMIT",
                "运行路径超过最大自动步数，已停止等待人工处理");
    }

    private boolean createApprovalTask(
            AuthenticatedContext context, FlowInstance instance, Map<String, Object> node,
            Map<String, Object> variables, Long previousHandlerTenantMemberId) {
        Map<String, Object> policy = map(node.get("assigneePolicy"));
        List<FlowParticipantResolver.ResolvedPerson> people = participantResolver.resolve(
                context, policy, instance, variables, previousHandlerTenantMemberId);
        if (people.isEmpty()) {
            fail(instance, string(node.get("nodeKey")), node, "ASSIGNEE",
                    "FLOW_RUNTIME_ASSIGNEE_UNRESOLVED", "审批节点没有可解析的有效处理人");
            return false;
        }
        String approvalMode = string(node.get("approvalMode")).toUpperCase(Locale.ROOT);
        if (approvalMode.isBlank()) approvalMode = string(map(node.get("config")).get("approvalMode")).toUpperCase(Locale.ROOT);
        if (!Set.of("OR_SIGN", "ALL_SIGN", "SEQUENTIAL").contains(approvalMode)) approvalMode = "OR_SIGN";
        ArrayList<FlowTask> created = new ArrayList<>();
        if ("OR_SIGN".equals(approvalMode)) {
            FlowTask task = newApprovalTask(instance, node, policy, people, approvalMode, 1, 1,
                    people.size() == 1 ? people.getFirst() : null, "PENDING");
            for (FlowParticipantResolver.ResolvedPerson person : people) {
                addCandidate(task.getId(), person, "按发布版本中的" + assigneePolicyName(policy) + "解析");
            }
            created.add(task);
        } else {
            for (int index = 0; index < people.size(); index++) {
                FlowParticipantResolver.ResolvedPerson person = people.get(index);
                String status = "SEQUENTIAL".equals(approvalMode) && index > 0 ? "QUEUED" : "PENDING";
                FlowTask task = newApprovalTask(instance, node, policy, people, approvalMode,
                        index + 1, people.size(), person, status);
                addCandidate(task.getId(), person, "按发布版本中的" + assigneePolicyName(policy) + "解析");
                created.add(task);
            }
        }
        recordHistory(instance, null, created.getFirst().getId(), string(node.get("nodeKey")), "TASK", "审批任务已创建",
                "RUNNING", "WAITING", null, null,
                Map.of("approvalMode", approvalMode,
                        "assignees", people.stream().map(this::personSnapshot).toList(),
                        "taskCount", created.size()));
        List<FlowParticipantResolver.ResolvedPerson> notified = "SEQUENTIAL".equals(approvalMode)
                ? List.of(people.getFirst()) : people;
        messageService.notifyFlowEvent(context, instance.getId(),
                "instance-" + instance.getId() + "-task-" + created.getFirst().getId(),
                "待审批：" + instance.getTitle(),
                "流程已进入“" + string(node.get("name")) + "”，请在待办或流程运行页处理。",
                notified.stream().map(FlowParticipantResolver.ResolvedPerson::accountId).toList());
        return true;
    }

    private FlowTask newApprovalTask(
            FlowInstance instance, Map<String, Object> node, Map<String, Object> policy,
            List<FlowParticipantResolver.ResolvedPerson> people, String approvalMode,
            int sequence, int sequenceCount, FlowParticipantResolver.ResolvedPerson assignee, String status) {
        FlowTask task = new FlowTask();
        task.setInstanceId(instance.getId());
        task.setNodeKey(string(node.get("nodeKey")));
        task.setTaskType("APPROVAL");
        task.setStatus(status);
        task.setAssigneeAccountId(assignee == null ? null : assignee.accountId());
        task.setAssigneeTenantMemberId(assignee == null ? null : assignee.tenantMemberId());
        task.setAssigneeSnapshotJson(toJson(Map.of(
                "policyType", string(policy.get("type")),
                "approvalMode", approvalMode,
                "sequence", sequence,
                "sequenceCount", sequenceCount,
                "formPolicy", map(node.get("formPolicy")),
                "timeoutPolicy", map(node.get("timeoutPolicy")),
                "resolvedPeople", people.stream().map(this::personSnapshot).toList())));
        Map<String, Object> timeoutPolicy = map(node.get("timeoutPolicy"));
        int timeoutMinutes = number(timeoutPolicy.get("timeoutMinutes"),
                number(timeoutPolicy.get("minutes"), 0));
        if (timeoutMinutes > 0 && "PENDING".equals(status)) {
            task.setDueAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
        }
        task.setVersion(0);
        taskService.insert(task);
        if (task.getDueAt() != null && "PENDING".equals(task.getStatus())) {
            scheduleJob(instance, activeExecution(instance.getId(), task.getNodeKey()), task.getNodeKey(),
                    "TASK_TIMEOUT", Map.of("taskId", task.getId()), "TASK_TIMEOUT:" + task.getId(),
                    1, task.getDueAt());
        }
        return task;
    }

    private boolean activateNextApprovalTaskOrWait(
            FlowInstance instance, FlowTask completed, Map<String, Object> taskSnapshot) {
        String mode = string(taskSnapshot.get("approvalMode")).toUpperCase(Locale.ROOT);
        List<FlowTask> siblings = tasks(instance.getId()).stream()
                .filter(item -> Objects.equals(item.getNodeKey(), completed.getNodeKey())
                        && "APPROVAL".equals(item.getTaskType()) && !Objects.equals(item.getId(), completed.getId()))
                .toList();
        if ("ALL_SIGN".equals(mode)) {
            return siblings.stream().anyMatch(item -> "PENDING".equals(item.getStatus()));
        }
        if ("SEQUENTIAL".equals(mode)) {
            FlowTask next = siblings.stream().filter(item -> "QUEUED".equals(item.getStatus()))
                    .min(Comparator.comparing(FlowTask::getId)).orElse(null);
            if (next == null) return false;
            next.setStatus("PENDING");
            Map<String, Object> nextSnapshot = readMap(next.getAssigneeSnapshotJson());
            Map<String, Object> timeout = map(nextSnapshot.get("timeoutPolicy"));
            int timeoutMinutes = number(timeout.get("timeoutMinutes"), number(timeout.get("minutes"), 0));
            if (timeoutMinutes > 0) next.setDueAt(LocalDateTime.now().plusMinutes(timeoutMinutes));
            next.setUpdatedAt(LocalDateTime.now());
            taskService.updateById(next);
            if (next.getDueAt() != null) {
                scheduleJob(instance, activeExecution(instance.getId(), next.getNodeKey()), next.getNodeKey(),
                        "TASK_TIMEOUT", Map.of("taskId", next.getId()), "TASK_TIMEOUT:" + next.getId(),
                        1, next.getDueAt());
            }
            recordHistory(instance, null, next.getId(), next.getNodeKey(), "TASK", "顺序会签已进入下一处理人",
                    "QUEUED", "PENDING", null, null, Map.of());
            messageService.notifyFlowEvent(systemContext(instance), instance.getId(),
                    "instance-" + instance.getId() + "-task-" + next.getId(),
                    "待审批：" + instance.getTitle(),
                    "顺序会签已轮到你处理，当前节点：" + next.getNodeKey() + "。",
                    List.of(next.getAssigneeAccountId()));
            return true;
        }
        return false;
    }

    private void cancelSiblingApprovalTasks(Long instanceId, FlowTask handled) {
        for (FlowTask sibling : tasks(instanceId)) {
            if (!Objects.equals(sibling.getNodeKey(), handled.getNodeKey())
                    || Objects.equals(sibling.getId(), handled.getId())
                    || !Set.of("PENDING", "QUEUED").contains(sibling.getStatus())) continue;
            sibling.setStatus("CANCELLED");
            sibling.setCompletedAt(LocalDateTime.now());
            sibling.setUpdatedAt(LocalDateTime.now());
            taskService.updateById(sibling);
        }
    }

    private void addCandidate(Long taskId, FlowParticipantResolver.ResolvedPerson person, String reason) {
        boolean exists = !candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, taskId)
                .eq(FlowTaskCandidate::getCandidateType, "TENANT_MEMBER")
                .eq(FlowTaskCandidate::getCandidateId, String.valueOf(person.tenantMemberId()))).isEmpty();
        if (exists) return;
        FlowTaskCandidate candidate = new FlowTaskCandidate();
        candidate.setTaskId(taskId);
        candidate.setCandidateType("TENANT_MEMBER");
        candidate.setCandidateId(String.valueOf(person.tenantMemberId()));
        candidate.setResolutionReason(reason);
        candidateService.insert(candidate);
    }

    private void fail(FlowInstance instance, String nodeKey, Map<String, Object> node,
                      String exceptionType, String errorCode, String message) {
        FlowExecution failedExecution = activeExecution(instance.getId(), nodeKey);
        if (failedExecution != null) {
            failedExecution.setStatus("FAILED");
            failedExecution.setResultCode(errorCode);
            failedExecution.setLeftAt(LocalDateTime.now());
            executionService.updateById(failedExecution);
        }
        Map<String, Object> policy = map(node.get("exceptionPolicy"));
        String policyAction = string(policy.getOrDefault("action", "MANUAL"));
        if (policyAction.isBlank()) policyAction = "MANUAL";
        FlowException exception = new FlowException();
        exception.setInstanceId(instance.getId());
        exception.setNodeKey(nodeKey);
        exception.setExceptionType(exceptionType);
        exception.setErrorCode(errorCode);
        exception.setErrorMessage(message);
        exception.setPolicyAction(policyAction.toUpperCase(Locale.ROOT));
        exception.setStatus("OPEN");
        exception.setVersion(0);
        exceptionService.insert(exception);
        instance.setCurrentNodeKey(nodeKey);
        instance.setStatus("EXCEPTION");
        instance.setErrorCode(errorCode);
        instance.setErrorMessage(message);
        instance.setUpdatedAt(LocalDateTime.now());
        instanceService.updateById(instance);
        recordHistory(instance, null, null, nodeKey, "INCIDENT", "流程进入异常处理",
                null, "EXCEPTION", null, instance.getStartedByTenantMemberId(),
                Map.of("errorCode", errorCode, "policyAction", exception.getPolicyAction(), "message", message));
        messageService.notifyFlowEvent(systemContext(instance), instance.getId(),
                "instance-" + instance.getId() + "-incident-" + exception.getId(),
                "流程异常：" + instance.getTitle(),
                "流程在节点“" + Objects.toString(nodeKey, "ENGINE") + "”执行失败：" + message,
                List.of(instance.getStartedByAccountId()));
        if ("RETRY".equals(exception.getPolicyAction())) {
            int maxAttempts = number(policy.get("maxAttempts"), 3);
            int delayMinutes = Math.max(1, number(policy.get("retryDelayMinutes"), 5));
            scheduleJob(instance, failedExecution, nodeKey, "RETRY",
                    Map.of("exceptionId", exception.getId(), "errorCode", errorCode),
                    "RETRY:" + exception.getId(), maxAttempts, LocalDateTime.now().plusMinutes(delayMinutes));
        }
        insertAction(instance.getId(), null, nodeKey == null ? "ENGINE" : nodeKey,
                "NODE_EXCEPTION", message, Map.of("exceptionType", exceptionType), result(instance),
                "EXCEPTION:" + exception.getId(), instance.getStartedByAccountId());
    }

    private Map<String, Object> nextNode(
            Map<String, Object> snapshot, String sourceNodeKey, Map<String, Object> variables) {
        List<Map<String, Object>> outgoing = edges(snapshot).stream()
                .filter(edge -> Objects.equals(sourceNodeKey, string(edge.get("sourceNodeKey"))))
                .sorted(Comparator.comparing(edge -> number(edge.get("priorityOrder"), 0))).toList();
        Map<String, Object> edge = outgoing.stream()
                .filter(item -> matches(blankToNull(string(item.get("conditionExpression"))), variables))
                .findFirst().orElse(null);
        if (edge == null) return null;
        String target = string(edge.get("targetNodeKey"));
        return nodes(snapshot).stream().filter(node -> Objects.equals(target, string(node.get("nodeKey"))))
                .findFirst().orElse(null);
    }

    private boolean matches(String expression, Map<String, Object> variables) {
        if (expression == null) return true;
        Matcher matcher = CONDITION.matcher(expression.strip());
        if (!matcher.matches()) return false;
        Object actual = variables.get(matcher.group(1));
        String operator = matcher.group(2);
        Object expected = literal(matcher.group(3).strip());
        if (actual == null) return "==".equals(operator) && expected == null;
        if (actual instanceof Number || expected instanceof Number) {
            try {
                int compared = new BigDecimal(String.valueOf(actual))
                        .compareTo(new BigDecimal(String.valueOf(expected)));
                return compare(compared, operator);
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        int compared = String.valueOf(actual).compareTo(String.valueOf(expected));
        return compare(compared, operator);
    }

    private Map<String, Object> resolveWritebacks(
            Map<String, Object> updates, Map<String, Object> variables) {
        LinkedHashMap<String, Object> resolved = new LinkedHashMap<>();
        updates.forEach((field, raw) -> {
            if (raw instanceof String text && text.startsWith("${") && text.endsWith("}") && text.length() > 3) {
                resolved.put(field, variables.get(text.substring(2, text.length() - 1)));
            } else {
                resolved.put(field, raw);
            }
        });
        return resolved;
    }

    private String renderFlowText(
            String template, FlowInstance instance, Map<String, Object> variables) {
        String rendered = template == null ? "" : template;
        rendered = rendered.replace("${instanceTitle}", Objects.toString(instance.getTitle(), ""));
        rendered = rendered.replace("${businessId}", Objects.toString(instance.getBusinessId(), ""));
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            rendered = rendered.replace("${" + entry.getKey() + "}", Objects.toString(entry.getValue(), ""));
        }
        return rendered;
    }

    private boolean compare(int value, String operator) {
        return switch (operator) {
            case "==" -> value == 0;
            case "!=" -> value != 0;
            case ">" -> value > 0;
            case ">=" -> value >= 0;
            case "<" -> value < 0;
            case "<=" -> value <= 0;
            default -> false;
        };
    }

    private Object literal(String value) {
        if ("null".equalsIgnoreCase(value)) return null;
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return Boolean.valueOf(value);
        if ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'"))) return value.substring(1, value.length() - 1);
        try { return new BigDecimal(value); } catch (NumberFormatException ignored) { return value; }
    }

    private boolean isCurrentHandler(Long tenantMemberId, Long accountId, FlowTask task) {
        if (task.getAssigneeTenantMemberId() != null) {
            return Objects.equals(task.getAssigneeTenantMemberId(), tenantMemberId);
        }
        if (task.getAssigneeAccountId() != null) return Objects.equals(task.getAssigneeAccountId(), accountId);
        return candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, task.getId())
                .eq(FlowTaskCandidate::getCandidateType, "TENANT_MEMBER")
                .eq(FlowTaskCandidate::getCandidateId, String.valueOf(tenantMemberId))).size() == 1;
    }

    private FlowRuntimeModels.ActionResult replay(AuthenticatedContext context, FlowAction action) {
        if (!Objects.equals(action.getActedByAccountId(), context.accountId())) {
            throw conflict("FLOW_ACTION_IDEMPOTENCY_CONFLICT", "幂等键已被其他处理请求使用");
        }
        FlowInstance instance = requireScoped(context, action.getInstanceId());
        return new FlowRuntimeModels.ActionResult(action.getId(), true, view(context, instance));
    }

    private FlowAction findStartReplay(Long accountId, String idempotencyKey) {
        return actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getActedByAccountId, accountId)
                .eq(FlowAction::getActionCode, "START")
                .eq(FlowAction::getIdempotencyKey, idempotencyKey)
                .orderByDesc(FlowAction::getId)).stream().findFirst().orElse(null);
    }

    private FlowAction findAction(Long instanceId, String idempotencyKey) {
        return actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instanceId)
                .eq(FlowAction::getIdempotencyKey, idempotencyKey)).stream().findFirst().orElse(null);
    }

    private FlowAction insertAction(Long instanceId, Long taskId, String nodeKey, String actionCode,
                                    String comment, Map<String, Object> input, Map<String, Object> result,
                                    String idempotencyKey, Long actorAccountId) {
        FlowAction action = new FlowAction();
        action.setInstanceId(instanceId);
        action.setTaskId(taskId);
        action.setNodeKey(nodeKey);
        action.setActionCode(actionCode);
        action.setCommentText(comment);
        action.setInputJson(toJson(input));
        action.setResultJson(toJson(result));
        action.setIdempotencyKey(idempotencyKey);
        action.setActedByAccountId(actorAccountId);
        FlowInstance instance = instanceService.selectById(instanceId);
        Long actorTenantMemberId = instance == null ? null : participantResolver.tenantMemberIdForAccount(
                instance.getSystemId(), instance.getTenantId(), actorAccountId);
        action.setActedByTenantMemberId(actorTenantMemberId);
        actionService.insert(action);
        if (instance != null) {
            recordHistory(instance, null, taskId, nodeKey, "ACTION", actionName(actionCode),
                    null, string(result.get("status")), actorAccountId, actorTenantMemberId,
                    Map.of("actionCode", actionCode, "comment", comment == null ? "" : comment));
        }
        return action;
    }

    private Map<String, Object> result(FlowInstance instance) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instance.getId());
        result.put("status", instance.getStatus());
        result.put("currentNodeKey", instance.getCurrentNodeKey());
        result.put("version", instance.getVersion());
        return result;
    }

    private void saveVariables(Long instanceId, Map<String, Object> values) {
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getKey().length() > 100) {
                throw invalid("FLOW_VARIABLE_KEY_INVALID", "Flow 变量名无效");
            }
            FlowInstanceVariable variable = variableService.selectList(
                    Wrappers.<FlowInstanceVariable>lambdaQuery()
                            .eq(FlowInstanceVariable::getInstanceId, instanceId)
                            .eq(FlowInstanceVariable::getVariableKey, entry.getKey())).stream().findFirst().orElse(null);
            if (variable == null) {
                variable = new FlowInstanceVariable();
                variable.setInstanceId(instanceId);
                variable.setVariableKey(entry.getKey());
                variable.setValueType(valueType(entry.getValue()));
                variable.setValueJson(toJson(entry.getValue()));
                variable.setVersion(0);
                variableService.insert(variable);
            } else {
                variable.setValueType(valueType(entry.getValue()));
                variable.setValueJson(toJson(entry.getValue()));
                variable.setUpdatedAt(LocalDateTime.now());
                if (variableService.updateById(variable) != 1) {
                    throw conflict("FLOW_VARIABLE_VERSION_CONFLICT", "Flow 变量已被其他处理更新");
                }
            }
        }
    }

    private String valueType(Object value) {
        if (value == null) return "NULL";
        if (value instanceof Boolean) return "BOOLEAN";
        if (value instanceof Number) return "NUMBER";
        if (value instanceof List<?>) return "ARRAY";
        if (value instanceof Map<?, ?>) return "OBJECT";
        return "STRING";
    }

    private Map<String, Object> variables(Long instanceId) {
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (FlowInstanceVariable variable : variableService.selectList(
                Wrappers.<FlowInstanceVariable>lambdaQuery()
                        .eq(FlowInstanceVariable::getInstanceId, instanceId)
                        .orderByAsc(FlowInstanceVariable::getVariableKey))) {
            values.put(variable.getVariableKey(), readValue(variable.getValueJson()));
        }
        return values;
    }

    private FlowRuntimeModels.InstanceView view(AuthenticatedContext context, FlowInstance instance) {
        FlowVersion version = versionService.selectById(instance.getFlowVersionId());
        Map<String, Object> definitionSnapshot = version == null ? Map.of() : readMap(version.getSnapshotJson());
        Map<String, Object> currentNode = nodes(definitionSnapshot).stream()
                .filter(node -> Objects.equals(instance.getCurrentNodeKey(), string(node.get("nodeKey"))))
                .findFirst().orElse(Map.of());
        List<FlowTask> tasks = tasks(instance.getId());
        if (currentNode.isEmpty()) {
            FlowTask manual = tasks.stream().filter(task -> Objects.equals(task.getNodeKey(), instance.getCurrentNodeKey())
                    && "MANUAL_APPROVAL".equals(task.getTaskType())).findFirst().orElse(null);
            if (manual != null) currentNode = Map.of("nodeKey", manual.getNodeKey(), "name", "手动加签审批",
                    "nodeType", "APPROVAL");
        }
        List<FlowRuntimeModels.TaskView> taskViews = tasks.stream().map(task -> {
            FlowParticipantResolver.ResolvedPerson assignee = person(context, task.getAssigneeTenantMemberId());
            List<FlowRuntimeModels.CandidateView> candidateViews = candidates(task.getId()).stream().map(candidate -> {
                Long tenantMemberId = "TENANT_MEMBER".equals(candidate.getCandidateType())
                        ? numericBusinessId(candidate.getCandidateId()) : null;
                FlowParticipantResolver.ResolvedPerson candidatePerson = person(context, tenantMemberId);
                return new FlowRuntimeModels.CandidateView(candidate.getId(), candidate.getCandidateType(), tenantMemberId,
                        candidatePerson == null ? "已失效成员" : candidatePerson.displayName(),
                        candidatePerson == null ? null : candidatePerson.departmentName(),
                        candidatePerson == null ? null : candidatePerson.positionTitle(),
                        candidate.getResolutionReason(), candidate.getCreatedAt());
            }).toList();
            return new FlowRuntimeModels.TaskView(task.getId(), task.getNodeKey(), task.getTaskType(), task.getStatus(),
                    task.getAssigneeTenantMemberId(), assignee == null ? null : assignee.displayName(),
                    assignee == null ? null : assignee.departmentName(),
                    assignee == null ? null : assignee.positionTitle(), readMap(task.getAssigneeSnapshotJson()),
                    task.getDueAt(), task.getClaimedAt(), task.getCompletedAt(), task.getVersion(), candidateViews);
        }).toList();
        List<FlowRuntimeModels.ActionView> actions = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId()).orderByAsc(FlowAction::getActedAt)
                .orderByAsc(FlowAction::getId)).stream().map(action -> {
            FlowParticipantResolver.ResolvedPerson actor = person(context, action.getActedByTenantMemberId());
            return new FlowRuntimeModels.ActionView(action.getId(), action.getTaskId(), action.getNodeKey(),
                    action.getActionCode(), action.getCommentText(), readMap(action.getInputJson()),
                    readMap(action.getResultJson()), action.getIdempotencyKey(), action.getActedByTenantMemberId(),
                    actor == null ? "系统自动处理" : actor.displayName(), action.getActedAt());
        }).toList();
        List<FlowRuntimeModels.ExceptionView> exceptions = exceptionService.selectList(
                Wrappers.<FlowException>lambdaQuery().eq(FlowException::getInstanceId, instance.getId())
                        .orderByAsc(FlowException::getOccurredAt)).stream().map(exception -> {
            FlowParticipantResolver.ResolvedPerson resolver = person(context, exception.getResolvedByTenantMemberId());
            return new FlowRuntimeModels.ExceptionView(exception.getId(), exception.getNodeKey(),
                    exception.getExceptionType(), exception.getErrorCode(), exception.getErrorMessage(),
                    exception.getPolicyAction(), exception.getStatus(), exception.getResolvedByTenantMemberId(),
                    resolver == null ? null : resolver.displayName(), exception.getResolutionComment(),
                    exception.getOccurredAt(), exception.getResolvedAt(), exception.getVersion());
        }).toList();
        List<FlowRuntimeModels.ExecutionView> executions = executionService.selectList(
                Wrappers.<FlowExecution>lambdaQuery().eq(FlowExecution::getInstanceId, instance.getId())
                        .orderByAsc(FlowExecution::getEnteredAt).orderByAsc(FlowExecution::getId)).stream()
                .map(execution -> new FlowRuntimeModels.ExecutionView(execution.getId(), execution.getParentExecutionId(),
                        execution.getNodeKey(), execution.getNodeType(), execution.getStatus(), execution.getEnteredAt(),
                        execution.getLeftAt(), execution.getResultCode(), execution.getVersion())).toList();
        List<FlowRuntimeModels.HistoryEventView> history = historyService.selectList(
                Wrappers.<FlowHistoryEvent>lambdaQuery().eq(FlowHistoryEvent::getInstanceId, instance.getId())
                        .orderByAsc(FlowHistoryEvent::getOccurredAt).orderByAsc(FlowHistoryEvent::getId)).stream()
                .map(event -> new FlowRuntimeModels.HistoryEventView(event.getId(), event.getExecutionId(),
                        event.getTaskId(), event.getNodeKey(), event.getEventType(), event.getEventName(),
                        event.getActorTenantMemberId(), displayName(context, event.getActorTenantMemberId()),
                        event.getBeforeStatus(), event.getAfterStatus(), readMap(event.getDetailJson()),
                        event.getOccurredAt())).toList();
        List<FlowRuntimeModels.JobView> jobs = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                        .eq(FlowJob::getInstanceId, instance.getId()).orderByAsc(FlowJob::getCreatedAt)).stream()
                .map(job -> new FlowRuntimeModels.JobView(job.getId(), job.getExecutionId(), job.getNodeKey(),
                        job.getJobType(), job.getStatus(), job.getAttemptCount(), job.getMaxAttempts(), job.getNextRunAt(),
                        job.getLastErrorCode(), job.getLastErrorMessage(), job.getCompletedAt(), job.getVersion())).toList();
        List<String> allowed = allowedActions(context, instance, tasks);
        FlowParticipantResolver.ResolvedPerson starter = person(context, instance.getStartedByTenantMemberId());
        return new FlowRuntimeModels.InstanceView(
                instance.getId(), instance.getContextType(), instance.getPlatformId(), instance.getSystemId(),
                instance.getTenantId(), instance.getFlowId(), instance.getFlowVersionId(),
                version == null ? null : version.getVersionNumber(), version == null ? null : version.getDefinitionHash(),
                definitionSnapshot, instance.getBusinessType(), instance.getBusinessId(),
                readMap(instance.getBusinessSnapshotJson()), instance.getTitle(), instance.getCurrentNodeKey(),
                string(currentNode.get("name")), instance.getStatus(), instance.getStartedByTenantMemberId(),
                starter == null ? "系统自动发起" : starter.displayName(),
                instance.getStartedAt(), instance.getFinishedAt(), instance.getErrorCode(), instance.getErrorMessage(),
                instance.getVersion(), variables(instance.getId()), taskViews, actions, exceptions,
                executions, history, jobs, allowed,
                nextStep(instance, currentNode, allowed));
    }

    private List<String> allowedActions(
            AuthenticatedContext context, FlowInstance instance, List<FlowTask> tasks) {
        ArrayList<String> actions = new ArrayList<>();
        tasks.stream().filter(task -> "PENDING".equals(task.getStatus())
                        && isCurrentHandler(participantResolver.currentTenantMemberId(context), context.accountId(), task))
                .findFirst().ifPresent(task -> actions.addAll(List.of("APPROVE", "REJECT", "RETURN", "TRANSFER")));
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())
                && Objects.equals(instance.getStartedByAccountId(), context.accountId())) actions.add("WITHDRAW");
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus()) && allowed(context, "TERMINATE")) actions.add("TERMINATE");
        return List.copyOf(actions);
    }

    private String nextStep(FlowInstance instance, Map<String, Object> node, List<String> allowed) {
        if ("COMPLETED".equals(instance.getStatus())) return "Flow 已完成";
        if ("EXCEPTION".equals(instance.getStatus())) return "按异常策略等待人工处理";
        if (allowed.contains("APPROVE")) return "请处理审批节点“" + string(node.get("name")) + "”";
        if ("WAITING".equals(instance.getStatus())) return "等待已解析审批人处理“" + string(node.get("name")) + "”";
        return "当前状态：" + instance.getStatus();
    }

    private String flowStatusName(String status) {
        return switch (status) {
            case "COMPLETED" -> "完成";
            case "REJECTED" -> "拒绝";
            case "RETURNED" -> "退回";
            case "WITHDRAWN" -> "撤回";
            case "TERMINATED" -> "终止";
            default -> status;
        };
    }

    private FlowRuntimeModels.ManualNodePreview validateManualNode(
            AuthenticatedContext context, FlowInstance instance, FlowRuntimeModels.ManualNodeRequest input) {
        if (!"WAITING".equals(instance.getStatus())) {
            throw conflict("FLOW_MANUAL_INSTANCE_NOT_WAITING", "只能在等待审批的运行实例中手动加签");
        }
        if (!"BEFORE_CURRENT".equals(input.position().strip().toUpperCase(Locale.ROOT))) {
            throw invalid("FLOW_MANUAL_POSITION_INVALID", "当前只允许在当前审批节点之前加签");
        }
        FlowParticipantResolver.ResolvedPerson assignee = participantResolver.requirePerson(
                context, input.assigneeTenantMemberId());
        List<FlowTask> pending = tasks(instance.getId()).stream()
                .filter(task -> "PENDING".equals(task.getStatus())).toList();
        if (pending.size() != 1 || !Objects.equals(pending.getFirst().getNodeKey(), instance.getCurrentNodeKey())) {
            throw conflict("FLOW_MANUAL_CURRENT_TASK_INCOMPATIBLE", "实例当前审批任务不唯一或与当前节点不一致");
        }
        Map<String, Map<String, String>> mappings = safeNestedMappings(input.statusMappings());
        for (Map.Entry<String, Map<String, String>> entry : mappings.entrySet()) {
            if (!Set.of("APPROVE", "REJECT", "RETURN").contains(entry.getKey())) {
                throw invalid("FLOW_STATUS_MAPPING_ACTION_INVALID", "业务状态映射只支持同意、拒绝或退回");
            }
            String businessAction = entry.getValue().getOrDefault("businessAction", "").strip().toUpperCase(Locale.ROOT);
            String targetStatus = entry.getValue().getOrDefault("targetStatus", "").strip().toUpperCase(Locale.ROOT);
            if (businessAction.isBlank() || !targetStatus.matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw invalid("FLOW_STATUS_MAPPING_INVALID", "状态映射必须定义业务动作和有效目标状态");
            }
            String moduleCode = moduleCode(instance.getBusinessType());
            if (moduleCode == null || numericBusinessId(instance.getBusinessId()) == null) {
                throw invalid("FLOW_STATUS_MAPPING_BUSINESS_REQUIRED", "状态映射要求实例关联模块业务数据");
            }
            if (!permissionChecker.allows(context, "MODULE", moduleCode, businessAction)
                    && !permissionChecker.allows(context, "MODULE", moduleCode, "*")) {
                throw forbidden("FLOW_STATUS_MAPPING_ACTION_DENIED", "当前账号没有目标业务状态动作权限");
            }
        }
        return new FlowRuntimeModels.ManualNodePreview(instance.getId(), instance.getStatus(),
                instance.getCurrentNodeKey(), pending.getFirst().getId(), input.assigneeTenantMemberId(),
                assignee.displayName(), assignee.departmentName(), "BEFORE_CURRENT", input.reason().strip(), mappings, true,
                List.of("实例正在等待唯一审批任务", "目标处理人属于当前上下文",
                        mappings.isEmpty() ? "未配置业务状态联动" : "业务状态动作权限已通过"));
    }

    private void applyManualStatusMapping(AuthenticatedContext context, FlowInstance instance,
                                          Map<String, Object> taskSnapshot, String actionCode, String traceId) {
        Map<String, Object> mappings = map(taskSnapshot.get("statusMappings"));
        Map<String, Object> mapping = map(mappings.get(actionCode));
        if (mapping.isEmpty()) return;
        String moduleCode = moduleCode(instance.getBusinessType());
        Long recordId = numericBusinessId(instance.getBusinessId());
        if (moduleCode == null || recordId == null) {
            throw invalid("FLOW_STATUS_MAPPING_BUSINESS_REQUIRED", "实例业务引用不能用于状态联动");
        }
        runtimeDataService.applyFlowStatus(context, moduleCode, recordId,
                string(mapping.get("businessAction")), string(mapping.get("expectedCurrentStatus")),
                string(mapping.get("targetStatus")), traceId);
    }

    private void resumeAfterManualNode(FlowInstance instance, Map<String, Object> snapshot) {
        Object value = snapshot.get("resumeTaskId");
        Long resumeTaskId = value instanceof Number number ? number.longValue() : null;
        FlowTask resume = resumeTaskId == null ? null : taskService.selectById(resumeTaskId);
        if (resume == null || !"SUSPENDED".equals(resume.getStatus())) {
            throw conflict("FLOW_MANUAL_RESUME_TASK_INVALID", "原审批任务已变化，不能继续实例");
        }
        resume.setStatus("PENDING"); resume.setUpdatedAt(LocalDateTime.now());
        if (taskService.updateById(resume) != 1) {
            throw conflict("FLOW_MANUAL_RESUME_TASK_INVALID", "原审批任务已被其他操作更新");
        }
        instance.setCurrentNodeKey(resume.getNodeKey()); instance.setStatus("WAITING");
        instance.setUpdatedAt(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "实例已被其他操作更新");
        }
    }

    private boolean validContextAccount(AuthenticatedContext context, Long accountId) {
        if (accountId == null || accountId <= 0) return false;
        if (context.systemId() != null) return !systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                .eq(SystemMember::getSystemId, context.systemId()).eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE")).isEmpty();
        return !platformMemberService.selectList(Wrappers.<PlatformMember>lambdaQuery()
                .eq(PlatformMember::getPlatformId, context.platformId()).eq(PlatformMember::getAccountId, accountId)
                .eq(PlatformMember::getStatus, "ACTIVE")).isEmpty();
    }

    private Map<String, Map<String, String>> safeNestedMappings(Map<String, Map<String, String>> input) {
        LinkedHashMap<String, Map<String, String>> result = new LinkedHashMap<>();
        if (input == null) return result;
        input.forEach((key, value) -> {
            LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
            if (value != null) value.forEach((nestedKey, nestedValue) -> {
                if (nestedKey != null && nestedValue != null) normalized.put(nestedKey, nestedValue);
            });
            result.put(key == null ? "" : key.strip().toUpperCase(Locale.ROOT), Map.copyOf(normalized));
        });
        return result;
    }

    private String moduleCode(String businessType) {
        if (businessType == null || businessType.isBlank()) return null;
        return businessType.startsWith("MODULE:") ? businessType.substring(7) : businessType;
    }

    private Long numericBusinessId(String businessId) {
        try { return businessId == null ? null : Long.valueOf(businessId); }
        catch (NumberFormatException exception) { return null; }
    }

    private boolean canView(AuthenticatedContext context, FlowInstance instance) {
        if (!hasBusinessAccess(context, instance.getBusinessType())) return false;
        if (allowed(context, "VIEW_ALL") || Objects.equals(instance.getStartedByAccountId(), context.accountId())) return true;
        return tasks(instance.getId()).stream().anyMatch(task -> isCurrentHandler(
                participantResolver.currentTenantMemberId(context), context.accountId(), task));
    }

    private void requireBusinessAccess(AuthenticatedContext context, String businessType) {
        if (!hasBusinessAccess(context, businessType)) {
            throw forbidden("FLOW_BUSINESS_OBJECT_VIEW_DENIED", "没有关联业务对象的查看权限");
        }
    }

    private boolean hasBusinessAccess(AuthenticatedContext context, String businessType) {
        if (businessType == null || businessType.isBlank()) return true;
        String moduleCode = businessType.startsWith("MODULE:") ? businessType.substring(7) : businessType;
        return permissionChecker.allows(context, "MODULE", moduleCode, "DETAIL")
                || permissionChecker.allows(context, "MODULE", moduleCode, "*");
    }

    private FlowDefinition requireFlow(AuthenticatedContext context, Long flowId) {
        FlowDefinition flow = definitionService.selectById(flowId);
        boolean platform = context.systemId() == null;
        boolean scoped = flow != null && (platform
                ? "PLATFORM".equals(flow.getContextType()) && Objects.equals(flow.getPlatformId(), context.platformId())
                : "SYSTEM".equals(flow.getContextType()) && Objects.equals(flow.getSystemId(), context.systemId())
                && Objects.equals(flow.getOwnerTenantId(), context.tenantId()));
        if (!scoped) throw notFound("FLOW_RUNTIME_FLOW_NOT_FOUND", "Flow 不存在或不在当前上下文");
        return flow;
    }

    private List<FlowInstance> scopedInstances(AuthenticatedContext context) {
        return instanceService.selectList(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(FlowInstance::getPlatformId, context.platformId())
                .isNull(context.systemId() == null, FlowInstance::getSystemId)
                .eq(context.systemId() != null, FlowInstance::getSystemId, context.systemId())
                .isNull(context.tenantId() == null, FlowInstance::getTenantId)
                .eq(context.tenantId() != null, FlowInstance::getTenantId, context.tenantId()));
    }

    private FlowInstance requireScoped(AuthenticatedContext context, Long id) {
        FlowInstance instance = instanceService.selectById(id);
        if (instance == null || scopedInstances(context).stream().noneMatch(item -> Objects.equals(item.getId(), id))) {
            throw notFound("FLOW_INSTANCE_NOT_FOUND", "Flow 实例不存在或不在当前上下文");
        }
        return instance;
    }

    private FlowInstance requireScopedForUpdate(AuthenticatedContext context, Long id) {
        FlowInstance scoped = requireScoped(context, id);
        return instanceService.selectList(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getId, scoped.getId()).last("FOR UPDATE")).stream().findFirst()
                .orElseThrow(() -> notFound("FLOW_INSTANCE_NOT_FOUND", "Flow 实例不存在"));
    }

    private List<FlowTask> tasks(Long instanceId) {
        return taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getInstanceId, instanceId).orderByAsc(FlowTask::getCreatedAt)
                .orderByAsc(FlowTask::getId));
    }

    private List<FlowTaskCandidate> candidates(Long taskId) {
        return candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, taskId).orderByAsc(FlowTaskCandidate::getId));
    }

    private Map<String, Object> snapshot(FlowInstance instance) {
        FlowVersion version = versionService.selectById(instance.getFlowVersionId());
        if (version == null || !Objects.equals(version.getFlowId(), instance.getFlowId())) {
            throw invalid("FLOW_RUNTIME_VERSION_INCOMPATIBLE", "实例冻结版本不存在或不兼容");
        }
        return readMap(version.getSnapshotJson());
    }

    private List<Map<String, Object>> nodes(Map<String, Object> snapshot) {
        return maps(snapshot.get("nodes"));
    }

    private List<Map<String, Object>> edges(Map<String, Object> snapshot) {
        return maps(snapshot.get("edges"));
    }

    private List<Map<String, Object>> maps(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().filter(Map.class::isInstance).map(this::map).toList();
    }

    private List<Long> numbers(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        return list.stream().map(item -> {
            try { return Long.valueOf(String.valueOf(item)); } catch (NumberFormatException ignored) { return null; }
        }).filter(Objects::nonNull).distinct().toList();
    }

    private FlowExecution recordExecution(
            FlowInstance instance, String nodeKey, String nodeType, String status, String resultCode) {
        FlowExecution execution = new FlowExecution();
        execution.setInstanceId(instance.getId());
        execution.setNodeKey(nodeKey);
        execution.setNodeType(nodeType);
        execution.setStatus(status);
        execution.setResultCode(resultCode);
        execution.setVersion(0);
        executionService.insert(execution);
        return execution;
    }

    private void completeExecution(FlowExecution execution, String resultCode) {
        if (execution == null) return;
        execution.setStatus("COMPLETED");
        execution.setResultCode(resultCode);
        execution.setLeftAt(LocalDateTime.now());
        executionService.updateById(execution);
    }

    private FlowExecution activeExecution(Long instanceId, String nodeKey) {
        return executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                        .eq(FlowExecution::getInstanceId, instanceId)
                        .eq(FlowExecution::getNodeKey, nodeKey)
                        .eq(FlowExecution::getStatus, "ACTIVE")
                        .orderByDesc(FlowExecution::getEnteredAt).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
    }

    private void completeActiveExecution(Long instanceId, String nodeKey, String resultCode) {
        completeExecution(activeExecution(instanceId, nodeKey), resultCode);
    }

    private void recordHistory(
            FlowInstance instance, Long executionId, Long taskId, String nodeKey,
            String eventType, String eventName, String beforeStatus, String afterStatus,
            Long actorAccountId, Long actorTenantMemberId, Map<String, ?> detail) {
        FlowHistoryEvent event = new FlowHistoryEvent();
        event.setInstanceId(instance.getId());
        event.setExecutionId(executionId);
        event.setTaskId(taskId);
        event.setNodeKey(nodeKey);
        event.setEventType(eventType);
        event.setEventName(eventName);
        event.setActorAccountId(actorAccountId);
        event.setActorTenantMemberId(actorTenantMemberId);
        event.setBeforeStatus(beforeStatus);
        event.setAfterStatus(afterStatus);
        event.setDetailJson(toJson(detail == null ? Map.of() : detail));
        historyService.insert(event);
    }

    private FlowJob scheduleJob(
            FlowInstance instance, FlowExecution execution, String nodeKey, String jobType,
            Map<String, ?> payload, String idempotencyKey, int maxAttempts, LocalDateTime nextRunAt) {
        FlowJob existing = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                        .eq(FlowJob::getInstanceId, instance.getId())
                        .eq(FlowJob::getIdempotencyKey, idempotencyKey))
                .stream().findFirst().orElse(null);
        if (existing != null) return existing;
        FlowJob job = new FlowJob();
        job.setInstanceId(instance.getId());
        job.setExecutionId(execution == null ? null : execution.getId());
        job.setNodeKey(nodeKey);
        job.setJobType(jobType);
        job.setStatus("PENDING");
        job.setPayloadJson(toJson(payload == null ? Map.of() : payload));
        job.setIdempotencyKey(idempotencyKey);
        job.setAttemptCount(0);
        job.setMaxAttempts(Math.max(1, maxAttempts));
        job.setNextRunAt(nextRunAt == null ? LocalDateTime.now() : nextRunAt);
        job.setVersion(0);
        jobService.insert(job);
        recordHistory(instance, execution == null ? null : execution.getId(), null, nodeKey,
                "JOB", switch (jobType) {
                    case "TIMER" -> "已安排定时继续";
                    case "TASK_TIMEOUT" -> "已安排审批超时检查";
                    default -> "已安排失败重试";
                },
                instance.getStatus(), "WAITING", null, instance.getStartedByTenantMemberId(),
                Map.of("jobType", jobType, "maxAttempts", job.getMaxAttempts(),
                        "nextRunAt", job.getNextRunAt().toString()));
        return job;
    }

    private LocalDateTime timerDueAt(Map<String, Object> config) {
        String resumeAt = string(config.get("resumeAt"));
        if (!resumeAt.isBlank()) {
            try { return LocalDateTime.parse(resumeAt); } catch (java.time.format.DateTimeParseException ignored) { }
        }
        return LocalDateTime.now().plusMinutes(Math.max(1,
                number(config.get("durationMinutes"), number(config.get("duration"), 1))));
    }

    private FlowParticipantResolver.ResolvedPerson person(AuthenticatedContext context, Long tenantMemberId) {
        if (tenantMemberId == null || context.systemId() == null) return null;
        try { return participantResolver.requirePerson(context, tenantMemberId); }
        catch (DomainException ignored) { return null; }
    }

    private String displayName(AuthenticatedContext context, Long tenantMemberId) {
        FlowParticipantResolver.ResolvedPerson person = person(context, tenantMemberId);
        return person == null ? "系统自动处理" : person.displayName();
    }

    private Map<String, Object> personSnapshot(FlowParticipantResolver.ResolvedPerson person) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tenantMemberId", person.tenantMemberId());
        snapshot.put("displayName", person.displayName());
        snapshot.put("departmentName", person.departmentName());
        snapshot.put("positionTitle", person.positionTitle());
        return snapshot;
    }

    private String assigneePolicyName(Map<String, Object> policy) {
        return switch (string(policy.get("type")).toUpperCase(Locale.ROOT)) {
            case "PERSON" -> "指定人员";
            case "ROLE" -> "角色成员";
            case "DEPARTMENT" -> "部门成员";
            case "DEPARTMENT_MANAGER" -> "部门负责人";
            case "RECORD_OWNER" -> "记录负责人";
            case "MANAGER", "DIRECT_MANAGER" -> "直属上级";
            case "INITIATOR_MANAGER" -> "发起人上级";
            case "PERSON_FIELD" -> "业务人员字段";
            case "SUBORDINATES" -> "下属成员";
            case "INITIATOR" -> "流程发起人";
            case "STARTER_SELECTED" -> "发起人选择";
            case "PREVIOUS_HANDLER" -> "上一处理人";
            default -> "审批人规则";
        };
    }

    private String actionName(String actionCode) {
        return switch (actionCode) {
            case "START" -> "流程已发起";
            case "APPROVE" -> "审批通过";
            case "REJECT" -> "审批拒绝";
            case "RETURN" -> "退回上一步";
            case "TRANSFER" -> "转交审批";
            case "WITHDRAW" -> "发起人撤回";
            case "TERMINATE" -> "管理员终止";
            case "MANUAL_NODE_ADDED" -> "增加临时审批";
            case "INCIDENT_RETRY" -> "异常已安排重试";
            case "INCIDENT_RESUME" -> "异常已确认并继续";
            case "AUTO_END" -> "流程已完成";
            case "AUTO_EXECUTED" -> "自动节点已完成";
            case "NODE_EXCEPTION" -> "节点执行异常";
            default -> "流程状态已更新";
        };
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (!allowed(context, action)) {
            throw forbidden("PERMISSION_DENIED", "没有 Flow 运行 " + action + " 权限");
        }
    }

    private boolean allowed(AuthenticatedContext context, String action) {
        String resourceCode = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        return permissionChecker.allows(context, "FLOW", resourceCode, action)
                || permissionChecker.allows(context, "FLOW", "*", action);
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode,
                       String objectType, Object objectId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, objectType, String.valueOf(objectId), "SUCCESS", detail);
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : new LinkedHashMap<>(value);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> source ? (Map<String, Object>) source : Map.of();
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank() || "null".equals(json)) return Map.of();
        try {
            Object value = objectMapper.readValue(json, Object.class);
            return value instanceof Map<?, ?> ? objectMapper.convertValue(value, new TypeReference<>() { }) : Map.of();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot read Flow runtime JSON", exception);
        }
    }

    private Object readValue(String json) {
        try { return json == null ? null : objectMapper.readValue(json, Object.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("cannot read Flow variable JSON", exception); }
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalArgumentException("无法保存 Flow 运行数据", exception); }
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private int number(Object value, int fallback) {
        try { return value == null ? fallback : Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
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
}
