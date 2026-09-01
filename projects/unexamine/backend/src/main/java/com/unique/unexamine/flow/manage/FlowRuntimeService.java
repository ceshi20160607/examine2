package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowAction;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowException;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowInstanceVariable;
import com.unique.unexamine.flow.base.entity.FlowPublication;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.entity.FlowTaskCandidate;
import com.unique.unexamine.flow.base.entity.FlowVersion;
import com.unique.unexamine.flow.base.service.FlowActionBaseService;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowExceptionBaseService;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.flow.base.service.FlowInstanceVariableBaseService;
import com.unique.unexamine.flow.base.service.FlowPublicationBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskCandidateBaseService;
import com.unique.unexamine.flow.base.service.FlowVersionBaseService;
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
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final SystemMemberBaseService systemMemberService;
    private final PlatformMemberBaseService platformMemberService;
    private final RuntimeDataService runtimeDataService;

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
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            SystemMemberBaseService systemMemberService,
            PlatformMemberBaseService platformMemberService,
            RuntimeDataService runtimeDataService) {
        this.definitionService = definitionService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.instanceService = instanceService;
        this.variableService = variableService;
        this.taskService = taskService;
        this.candidateService = candidateService;
        this.actionService = actionService;
        this.exceptionService = exceptionService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.systemMemberService = systemMemberService;
        this.platformMemberService = platformMemberService;
        this.runtimeDataService = runtimeDataService;
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
        manualSnapshot.put("addedByAccountId", context.accountId());
        manualSnapshot.put("resumeTaskId", suspended.getId());
        manualSnapshot.put("resumeNodeKey", suspended.getNodeKey());
        manualSnapshot.put("statusMappings", safeNestedMappings(input.statusMappings()));
        FlowTask manual = new FlowTask();
        manual.setInstanceId(instanceId); manual.setNodeKey(nodeKey); manual.setTaskType("MANUAL_APPROVAL");
        manual.setStatus("PENDING"); manual.setAssigneeAccountId(input.assigneeAccountId());
        manual.setAssigneeSnapshotJson(toJson(manualSnapshot)); manual.setVersion(0);
        taskService.insert(manual);
        addCandidate(manual.getId(), input.assigneeAccountId(),
                "账号 #" + context.accountId() + " 手动加签：" + input.reason().strip());
        instance.setCurrentNodeKey(nodeKey); instance.setStatus("WAITING"); instance.setUpdatedAt(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "实例已被其他操作更新");
        }
        FlowAction action = insertAction(instanceId, manual.getId(), nodeKey, "MANUAL_NODE_ADDED",
                input.reason().strip(), Map.of("assigneeAccountId", input.assigneeAccountId(),
                        "position", "BEFORE_CURRENT", "statusMappings", safeNestedMappings(input.statusMappings())),
                result(instance), input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_MANUAL_NODE_ADDED", "FLOW_TASK", manual.getId(),
                Map.of("instanceId", instanceId, "suspendedTaskId", suspended.getId(),
                        "assigneeAccountId", input.assigneeAccountId(), "reason", input.reason().strip()));
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
        instance.setVersion(0);
        instanceService.insert(instance);
        saveVariables(instance.getId(), safeMap(input.variables()));
        advance(instance, snapshot, string(startNode.get("nodeKey")), variables(instance.getId()));
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
        if (!isCurrentHandler(context.accountId(), task)) {
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
            if (input.targetAccountId() == null || input.targetAccountId() <= 0
                    || Objects.equals(input.targetAccountId(), context.accountId())) {
                throw invalid("FLOW_TASK_TRANSFER_TARGET_INVALID", "转交必须选择其他有效账号");
            }
            task.setAssigneeAccountId(input.targetAccountId());
            Map<String, Object> transferredSnapshot = new LinkedHashMap<>(taskSnapshot);
            transferredSnapshot.put("type", "ACCOUNT");
            transferredSnapshot.put("accountIds", List.of(input.targetAccountId()));
            transferredSnapshot.put("transferredBy", context.accountId());
            task.setAssigneeSnapshotJson(toJson(transferredSnapshot));
            task.setUpdatedAt(LocalDateTime.now());
            if (taskService.updateById(task) != 1) {
                throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新");
            }
            addCandidate(task.getId(), input.targetAccountId(), "任务由账号 #" + context.accountId() + " 转交");
        } else {
            task.setStatus(actionCode);
            task.setCompletedAt(LocalDateTime.now());
            task.setUpdatedAt(LocalDateTime.now());
            if (taskService.updateById(task) != 1) {
                throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新");
            }
            if ("APPROVE".equals(actionCode)) {
                if (manualTask) {
                    resumeAfterManualNode(scoped, taskSnapshot);
                } else {
                    scoped.setStatus("RUNNING");
                    scoped.setUpdatedAt(LocalDateTime.now());
                    instanceService.updateById(scoped);
                    advance(scoped, snapshot, task.getNodeKey(), variables(scoped.getId()));
                }
            } else {
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
                            "targetAccountId", input.targetAccountId() == null ? "" : input.targetAccountId()),
                    result(latest), input.idempotencyKey(), context.accountId());
        } catch (DuplicateKeyException duplicate) {
            FlowAction duplicateReplay = findAction(latest.getId(), input.idempotencyKey());
            if (duplicateReplay != null) return replay(context, duplicateReplay);
            throw duplicate;
        }
        audit(context, traceId, "FLOW_TASK_" + actionCode, "FLOW_TASK", task.getId(),
                Map.of("instanceId", latest.getId(), "status", latest.getStatus()));
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
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, instance));
    }

    private void advance(FlowInstance instance, Map<String, Object> snapshot,
                         String sourceNodeKey, Map<String, Object> variables) {
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
            instance.setCurrentNodeKey(current);
            instance.setUpdatedAt(LocalDateTime.now());
            if ("END".equals(nodeType)) {
                instance.setStatus("COMPLETED");
                instance.setFinishedAt(LocalDateTime.now());
                instanceService.updateById(instance);
                insertAction(instance.getId(), null, current, "AUTO_END", null, Map.of(), result(instance),
                        "AUTO:" + current + ":" + UUID.randomUUID(), instance.getStartedByAccountId());
                return;
            }
            if ("APPROVAL".equals(nodeType)) {
                if (!createApprovalTask(instance, next)) return;
                instance.setStatus("WAITING");
                instanceService.updateById(instance);
                return;
            }
            Map<String, Object> config = map(next.get("config"));
            if (("WEBHOOK".equals(nodeType) || "AI".equals(nodeType))
                    && Boolean.TRUE.equals(config.get("simulateFailure"))) {
                fail(instance, current, next, "SERVICE", "FLOW_RUNTIME_SERVICE_FAILED",
                        "节点服务按测试配置返回失败");
                return;
            }
            instanceService.updateById(instance);
            insertAction(instance.getId(), null, current, "AUTO_EXECUTED", null,
                    Map.of("nodeType", nodeType), result(instance),
                    "AUTO:" + current + ":" + UUID.randomUUID(), instance.getStartedByAccountId());
        }
        fail(instance, current, Map.of(), "ENGINE", "FLOW_RUNTIME_STEP_LIMIT",
                "运行路径超过最大自动步数，已停止等待人工处理");
    }

    private boolean createApprovalTask(FlowInstance instance, Map<String, Object> node) {
        Map<String, Object> policy = map(node.get("assigneePolicy"));
        List<Long> accountIds = numbers(policy.get("accountIds"));
        if (accountIds.isEmpty()) {
            fail(instance, string(node.get("nodeKey")), node, "ASSIGNEE",
                    "FLOW_RUNTIME_ASSIGNEE_UNRESOLVED", "审批节点没有可解析的有效处理人");
            return false;
        }
        FlowTask task = new FlowTask();
        task.setInstanceId(instance.getId());
        task.setNodeKey(string(node.get("nodeKey")));
        task.setTaskType("APPROVAL");
        task.setStatus("PENDING");
        task.setAssigneeAccountId(accountIds.getFirst());
        task.setAssigneeSnapshotJson(toJson(Map.of(
                "assigneePolicy", policy,
                "formPolicy", map(node.get("formPolicy")),
                "timeoutPolicy", map(node.get("timeoutPolicy")),
                "resolvedAccountIds", accountIds)));
        task.setVersion(0);
        taskService.insert(task);
        for (Long accountId : accountIds) addCandidate(task.getId(), accountId, "发布快照 ACCOUNT 策略解析");
        return true;
    }

    private void addCandidate(Long taskId, Long accountId, String reason) {
        boolean exists = !candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, taskId)
                .eq(FlowTaskCandidate::getCandidateType, "ACCOUNT")
                .eq(FlowTaskCandidate::getCandidateId, String.valueOf(accountId))).isEmpty();
        if (exists) return;
        FlowTaskCandidate candidate = new FlowTaskCandidate();
        candidate.setTaskId(taskId);
        candidate.setCandidateType("ACCOUNT");
        candidate.setCandidateId(String.valueOf(accountId));
        candidate.setResolutionReason(reason);
        candidateService.insert(candidate);
    }

    private void fail(FlowInstance instance, String nodeKey, Map<String, Object> node,
                      String exceptionType, String errorCode, String message) {
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

    private boolean isCurrentHandler(Long accountId, FlowTask task) {
        if (task.getAssigneeAccountId() != null) return Objects.equals(task.getAssigneeAccountId(), accountId);
        return candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, task.getId())
                .eq(FlowTaskCandidate::getCandidateType, "ACCOUNT")
                .eq(FlowTaskCandidate::getCandidateId, String.valueOf(accountId))).size() == 1;
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
        actionService.insert(action);
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
        List<FlowRuntimeModels.TaskView> taskViews = tasks.stream().map(task -> new FlowRuntimeModels.TaskView(
                task.getId(), task.getNodeKey(), task.getTaskType(), task.getStatus(), task.getAssigneeAccountId(),
                readMap(task.getAssigneeSnapshotJson()), task.getDueAt(), task.getClaimedAt(), task.getCompletedAt(),
                task.getVersion(), candidates(task.getId()).stream().map(candidate -> new FlowRuntimeModels.CandidateView(
                        candidate.getId(), candidate.getCandidateType(), candidate.getCandidateId(),
                        candidate.getResolutionReason(), candidate.getCreatedAt())).toList())).toList();
        List<FlowRuntimeModels.ActionView> actions = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId()).orderByAsc(FlowAction::getActedAt)
                .orderByAsc(FlowAction::getId)).stream().map(action -> new FlowRuntimeModels.ActionView(
                action.getId(), action.getTaskId(), action.getNodeKey(), action.getActionCode(),
                action.getCommentText(), readMap(action.getInputJson()), readMap(action.getResultJson()),
                action.getIdempotencyKey(), action.getActedByAccountId(), action.getActedAt())).toList();
        List<FlowRuntimeModels.ExceptionView> exceptions = exceptionService.selectList(
                Wrappers.<FlowException>lambdaQuery().eq(FlowException::getInstanceId, instance.getId())
                        .orderByAsc(FlowException::getOccurredAt)).stream().map(exception ->
                new FlowRuntimeModels.ExceptionView(exception.getId(), exception.getNodeKey(),
                        exception.getExceptionType(), exception.getErrorCode(), exception.getErrorMessage(),
                        exception.getPolicyAction(), exception.getStatus(), exception.getResolvedByAccountId(),
                        exception.getResolutionComment(), exception.getOccurredAt(), exception.getResolvedAt(),
                        exception.getVersion())).toList();
        List<String> allowed = allowedActions(context, instance, tasks);
        return new FlowRuntimeModels.InstanceView(
                instance.getId(), instance.getContextType(), instance.getPlatformId(), instance.getSystemId(),
                instance.getTenantId(), instance.getFlowId(), instance.getFlowVersionId(),
                version == null ? null : version.getVersionNumber(), version == null ? null : version.getDefinitionHash(),
                definitionSnapshot, instance.getBusinessType(), instance.getBusinessId(),
                readMap(instance.getBusinessSnapshotJson()), instance.getTitle(), instance.getCurrentNodeKey(),
                string(currentNode.get("name")), instance.getStatus(), instance.getStartedByAccountId(),
                instance.getStartedAt(), instance.getFinishedAt(), instance.getErrorCode(), instance.getErrorMessage(),
                instance.getVersion(), variables(instance.getId()), taskViews, actions, exceptions, allowed,
                nextStep(instance, currentNode, allowed));
    }

    private List<String> allowedActions(
            AuthenticatedContext context, FlowInstance instance, List<FlowTask> tasks) {
        ArrayList<String> actions = new ArrayList<>();
        tasks.stream().filter(task -> "PENDING".equals(task.getStatus()) && isCurrentHandler(context.accountId(), task))
                .findFirst().ifPresent(task -> actions.addAll(List.of("APPROVE", "REJECT", "RETURN", "TRANSFER")));
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())
                && Objects.equals(instance.getStartedByAccountId(), context.accountId())) actions.add("WITHDRAW");
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus()) && allowed(context, "TERMINATE")) actions.add("TERMINATE");
        return List.copyOf(actions);
    }

    private String nextStep(FlowInstance instance, Map<String, Object> node, List<String> allowed) {
        if ("COMPLETED".equals(instance.getStatus())) return "Flow 已完成";
        if ("EXCEPTION".equals(instance.getStatus())) return "按异常策略等待人工处理";
        if (allowed.contains("APPROVE")) return "当前账号处理审批节点“" + string(node.get("name")) + "”";
        if ("WAITING".equals(instance.getStatus())) return "等待已解析审批人处理“" + string(node.get("name")) + "”";
        return "当前状态：" + instance.getStatus();
    }

    private FlowRuntimeModels.ManualNodePreview validateManualNode(
            AuthenticatedContext context, FlowInstance instance, FlowRuntimeModels.ManualNodeRequest input) {
        if (!"WAITING".equals(instance.getStatus())) {
            throw conflict("FLOW_MANUAL_INSTANCE_NOT_WAITING", "只能在等待审批的运行实例中手动加签");
        }
        if (!"BEFORE_CURRENT".equals(input.position().strip().toUpperCase(Locale.ROOT))) {
            throw invalid("FLOW_MANUAL_POSITION_INVALID", "当前只允许在当前审批节点之前加签");
        }
        if (!validContextAccount(context, input.assigneeAccountId())) {
            throw invalid("FLOW_MANUAL_ASSIGNEE_INVALID", "目标处理人不是当前上下文的有效成员");
        }
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
                instance.getCurrentNodeKey(), pending.getFirst().getId(), input.assigneeAccountId(),
                "BEFORE_CURRENT", input.reason().strip(), mappings, true,
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
        return tasks(instance.getId()).stream().anyMatch(task -> isCurrentHandler(context.accountId(), task));
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
