package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.ai.manage.AiModelGateway;
import com.unique.unexamine.application.base.entity.AppDefinition;
import com.unique.unexamine.application.base.service.AppDefinitionBaseService;
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
import com.unique.unexamine.flow.manage.repository.FlowRuntimeStateRepository;
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
import com.unique.unexamine.file.base.entity.FileObject;
import com.unique.unexamine.file.base.entity.FileReference;
import com.unique.unexamine.file.base.service.FileObjectBaseService;
import com.unique.unexamine.file.base.service.FileReferenceBaseService;
import com.unique.unexamine.foundation.manage.control.BusinessActionApprovalCoordinator;
import com.unique.unexamine.foundation.manage.control.BusinessAction;
import com.unique.unexamine.foundation.manage.event.LocalBusinessEvent;
import com.unique.unexamine.foundation.manage.event.LocalBusinessEventPublisher;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.support.TransactionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.UUID;

@Service
public class FlowRuntimeService {
    private static final Logger LOG = LoggerFactory.getLogger(FlowRuntimeService.class);
    private static final Set<String> TASK_ACTIONS = Set.of("APPROVE", "REJECT", "RETURN", "TRANSFER", "DELEGATE",
            "REVOKE_DELEGATION", "CLAIM", "UNCLAIM", "ADD_SIGN", "REMOVE_SIGN", "RESUBMIT");
    private static final Set<String> INSTANCE_ACTIONS = Set.of("WITHDRAW", "TERMINATE", "COMMENT", "REMIND");
    private static final Set<String> ACTIVE_INSTANCE_STATUSES = Set.of("RUNNING", "WAITING", "EXCEPTION");

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
    private final AppDefinitionBaseService applicationService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final SystemMemberBaseService systemMemberService;
    private final PlatformMemberBaseService platformMemberService;
    private final RuntimeDataService runtimeDataService;
    /** 模块审批从当前租户的已发布动作绑定解析 Flow，不接受客户端传入 Flow 编号。 */
    private final FlowBindingService flowBindingService;
    private final MessageService messageService;
    private final LocalBusinessEventPublisher localEvents;
    /** Webhook 使用应用发布快照和运维密钥签名，Flow 本身不管理地址与密钥。 */
    private final FlowWebhookDeliveryService webhookDeliveryService;
    private final PermissionResolver permissionResolver;
    private final PlatformPermissionResolver platformPermissionResolver;
    private final FlowRuntimeStateRepository stateRepository;
    /** 统一恢复原动作的真实发起人，审批目录与业务授权分别使用各自租户范围。 */
    private final BusinessActionApprovalCoordinator actionCoordinator;
    private final FlowConditionService flowConditions;
    /** AI 节点使用系统当前授权模型，不借用平台或 Agent 身份。 */
    private final AiModelGateway aiModelGateway;
    private final AiSystemModelGrantBaseService aiGrants;
    private final AiModelBaseService aiModels;
    /** 在途实例拥有自己的执行图；模板与其他实例不会被动态节点改写。 */
    private final FlowInstanceGraphService instanceGraphs;
    /** 当前节点前插暂停/恢复原组，人工审批及worker共用持久协议。 */
    private final FlowNodeSuspensionService nodeSuspensions;
    /** 审批只读取现有文件事实，不经文件管理服务反向调用流程服务形成循环。 */
    private final FileReferenceBaseService fileReferenceService;
    private final FileObjectBaseService fileObjectService;
    /** 原动作意图与节点提交分开保存，批准后由公共动作执行器合并续跑。 */
    private final com.unique.unexamine.foundation.manage.control.BusinessActionFormSubmissions formSubmissions;
    /** 每个持久化作业独立提交，失败作业不能回滚或阻塞同批其他流程。 */
    private final TransactionTemplate jobTransaction;

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
            AppDefinitionBaseService applicationService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper,
            SystemMemberBaseService systemMemberService,
            PlatformMemberBaseService platformMemberService,
             RuntimeDataService runtimeDataService,
             FlowBindingService flowBindingService,
            MessageService messageService,
            LocalBusinessEventPublisher localEvents,
            FlowWebhookDeliveryService webhookDeliveryService,
            PermissionResolver permissionResolver,
            PlatformPermissionResolver platformPermissionResolver,
            FlowRuntimeStateRepository stateRepository,
            BusinessActionApprovalCoordinator actionCoordinator, FlowConditionService flowConditions,
            com.unique.unexamine.foundation.manage.control.BusinessActionFormSubmissions formSubmissions,
             PlatformTransactionManager transactionManager,
             FlowInstanceGraphService instanceGraphs, FlowNodeSuspensionService nodeSuspensions,
             FileReferenceBaseService fileReferenceService, FileObjectBaseService fileObjectService,
             AiModelGateway aiModelGateway, AiSystemModelGrantBaseService aiGrants,
             AiModelBaseService aiModels) {
        this.definitionService = definitionService;
        this.instanceGraphs = instanceGraphs;
        this.nodeSuspensions = nodeSuspensions;
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
        this.applicationService = applicationService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = FlowJsonStorage.mapper(objectMapper);
        this.systemMemberService = systemMemberService;
        this.platformMemberService = platformMemberService;
        this.runtimeDataService = runtimeDataService;
        this.flowBindingService = flowBindingService;
        this.messageService = messageService;
        this.localEvents = localEvents;
        this.webhookDeliveryService = webhookDeliveryService;
        this.permissionResolver = permissionResolver;
        this.platformPermissionResolver = platformPermissionResolver;
        this.stateRepository = stateRepository;
        this.actionCoordinator = actionCoordinator;
        this.flowConditions = flowConditions;
        this.aiModelGateway = aiModelGateway;
        this.aiGrants = aiGrants;
        this.aiModels = aiModels;
        this.formSubmissions = formSubmissions;
        this.jobTransaction = new TransactionTemplate(transactionManager);
        this.jobTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // 扫描作业后可能等待人工动作的实例锁；目录和权限必须看锁等待结束后已提交的撤权、停用事实。
        this.jobTransaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.fileReferenceService = fileReferenceService;
        this.fileObjectService = fileObjectService;
    }

    /** 设计、发起及任务处理共用最小目录；纯实例浏览不额外获得人员目录权限。 */
    @Transactional(readOnly = true)
    public FlowRuntimeModels.ParticipantDirectory participants(AuthenticatedContext context) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        if (!allowed(context, "DESIGN") && !allowed(context, "HANDLE") && !allowed(context, "START")
                && !allowed(context, "ADD_MANUAL_NODE")) {
            throw forbidden("PERMISSION_DENIED", "没有流程配置、发起或任务处理权限");
        }
        return participantResolver.directory(context);
    }

    /** 当前租户的已发布目录只提供运行所需摘要；查看实例与处理事项仍分别检查业务及参与人权限。 */
    @Transactional(readOnly = true)
    public List<FlowRuntimeModels.FlowCatalogEntry> flows(AuthenticatedContext context) {
        requireAction(context, "VIEW_RUNTIME");
        boolean platform = context.systemId() == null;
        var query = Wrappers.<FlowDefinition>lambdaQuery()
                .eq(FlowDefinition::getContextType, platform ? "PLATFORM" : "SYSTEM")
                .eq(FlowDefinition::getPlatformId, context.platformId())
                .isNull(platform, FlowDefinition::getSystemId)
                .eq(!platform, FlowDefinition::getSystemId, context.systemId())
                .isNull(platform, FlowDefinition::getOwnerTenantId)
                .eq(!platform, FlowDefinition::getOwnerTenantId, context.tenantId())
                .orderByAsc(FlowDefinition::getName).orderByAsc(FlowDefinition::getId);
        List<FlowRuntimeModels.FlowCatalogEntry> result = new ArrayList<>();
        for (FlowDefinition flow : definitionService.selectList(query)) {
            if (!"PUBLISHED".equals(flow.getStatus())) continue;
            FlowPublication publication = publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                    .eq(FlowPublication::getFlowId, flow.getId())).stream().findFirst().orElse(null);
            if (publication == null || publication.getCurrentVersionId() == null) continue;
            FlowVersion version = versionService.selectById(publication.getCurrentVersionId());
            if (version != null && Objects.equals(version.getFlowId(), flow.getId())) {
                result.add(new FlowRuntimeModels.FlowCatalogEntry(flow.getId(), flow.getName(),
                        version.getId(), version.getVersionNumber()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<FlowRuntimeModels.InstanceView> instances(AuthenticatedContext context) {
        requireAction(context, "VIEW_RUNTIME");
        return scopedInstances(context).stream()
                .filter(instance -> canView(context, instance))
                .sorted(Comparator.comparing(FlowInstance::getStartTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(instance -> view(context, instance)).toList();
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FlowRuntimeModels.InstanceView instance(AuthenticatedContext context, Long instanceId) {
        requireAction(context, "VIEW_RUNTIME");
        FlowInstance instance = requireScoped(context, instanceId);
        if (!canView(context, instance)) {
            throw forbidden("FLOW_INSTANCE_VIEW_DENIED", "实例与业务对象权限交集不允许查看");
        }
        return view(context, instance);
    }

    /** 外部应用只读取自己的发起实例或分配给本应用审批人的实例摘要。 */
    @Transactional(readOnly = true)
    public Map<String, Object> applicationInstance(AuthenticatedContext context, Long expectedFlowId,
            Long applicationId, String externalPrincipalId, Long instanceId) {
        if (externalPrincipalId == null || externalPrincipalId.isBlank() || externalPrincipalId.length() > 100) {
            throw forbidden("FLOW_EXTERNAL_PRINCIPAL_REQUIRED", "查询外部实例必须提供审批人标识");
        }
        FlowInstance instance = requireScoped(context, instanceId);
        if (!Objects.equals(instance.getFlowId(), expectedFlowId)) {
            throw forbidden("FLOW_EXTERNAL_FLOW_DENIED", "当前应用未获权查询此流程");
        }
        List<FlowTask> ownTasks = tasks(instanceId).stream()
                .filter(task -> Objects.equals(task.getAssigneeApplicationId(), applicationId)
                        && Objects.equals(task.getAssigneeExternalPrincipalId(), externalPrincipalId)).toList();
        if ((!Objects.equals(instance.getSourceApplicationId(), applicationId)
                || !Objects.equals(instance.getSourceExternalPrincipalId(), externalPrincipalId))
                && ownTasks.isEmpty()) {
            throw forbidden("FLOW_EXTERNAL_INSTANCE_DENIED", "该实例不属于当前应用外部身份范围");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("instanceId", instance.getId());
        result.put("title", instance.getTitle());
        result.put("status", instance.getStatus());
        result.put("flowVersionId", instance.getFlowVersionId());
        result.put("startTime", instance.getStartTime());
        result.put("finishTime", instance.getFinishTime());
        result.put("tasks", ownTasks.stream().map(this::externalTaskSummary).toList());
        return result;
    }

    /** 外部待办按应用与审批人双键查询，不能借应用发布范围读取系统成员的待办。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> applicationTasks(AuthenticatedContext context, Long expectedFlowId,
            Long applicationId, String externalPrincipalId) {
        if (externalPrincipalId == null || externalPrincipalId.isBlank() || externalPrincipalId.length() > 100) {
            throw forbidden("FLOW_EXTERNAL_PRINCIPAL_REQUIRED", "查询外部待办必须提供审批人标识");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (FlowTask task : taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getAssigneeApplicationId, applicationId)
                .eq(FlowTask::getAssigneeExternalPrincipalId, externalPrincipalId)
                .eq(FlowTask::getStatus, "PENDING").orderByDesc(FlowTask::getId))) {
            FlowInstance instance = instanceService.selectById(task.getInstanceId());
            if (instance == null || !Objects.equals(instance.getFlowId(), expectedFlowId)
                    || !Objects.equals(instance.getSystemId(), context.systemId())
                    || !Objects.equals(instance.getTenantId(), context.tenantId())
                    || !Set.of("WAITING", "RUNNING").contains(instance.getStatus())
                    || !hasActiveTaskExecution(task)) continue;
            Map<String, Object> row = new LinkedHashMap<>(externalTaskSummary(task));
            row.put("title", instance.getTitle());
            result.add(row);
        }
        return result;
    }

    private Map<String, Object> externalTaskSummary(FlowTask task) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("taskId", task.getId());
        row.put("instanceId", task.getInstanceId());
        row.put("nodeKey", task.getNodeKey());
        row.put("status", task.getStatus());
        row.put("dueTime", task.getDueTime());
        row.put("completeTime", task.getCompleteTime());
        return row;
    }

    /** 从本人真实动作目录恢复申请，当前共享和业务权限失效的条目立即不再展示。 */
    public FlowRuntimeModels.BusinessActionApplicationsPage businessActionApplications(
            AuthenticatedContext viewer, Long cursor, int pageSize, String traceId) {
        if (viewer.systemId() == null || viewer.tenantMemberId() == null) {
            throw new DomainException("ACTION_APPLICATION_CONTEXT_INVALID", "请先进入系统和工作空间查看申请", HttpStatus.FORBIDDEN);
        }
        if (cursor != null && cursor <= 0 || pageSize < 1 || pageSize > 100) {
            throw invalid("ACTION_APPLICATION_PAGE_INVALID", "申请分页参数不正确，请刷新后重试");
        }
        // 列表逐项独立读取；预期的无权条目被过滤时，不能把整个目录事务标记回滚并最终返回 500。
        var candidates = actionCoordinator.ownFlowEvents(viewer, cursor, pageSize);
        List<FlowRuntimeModels.BusinessActionApplication> items = new ArrayList<>();
        for (var event : candidates) {
            Map<String, Object> action = map(readMap(event.getPayloadJson()).get("action"));
            try {
                var progress = businessActionProgress(viewer, string(action.get("moduleCode")),
                        Long.parseLong(event.getAggregateId()), traceId);
                items.add(new FlowRuntimeModels.BusinessActionApplication(progress.actionId(), progress.moduleCode(),
                        progress.actionCode(), progress.flowName(), progress.status(), progress.businessStatus(), progress.mode(),
                        progress.currentNodeName(), progress.startTime(), progress.finishTime()));
            } catch (DomainException exception) {
                // 无权或已撤销共享仅过滤该条；服务错误、快照损坏等不能伪装成没有申请。
                if (exception.status() != HttpStatus.FORBIDDEN && exception.status() != HttpStatus.NOT_FOUND) throw exception;
            }
        }
        Long nextCursor = candidates.size() == pageSize ? candidates.get(candidates.size() - 1).getId() : null;
        return new FlowRuntimeModels.BusinessActionApplicationsPage(items, nextCursor);
    }

    /** 本人业务动作关联的窄只读视图；独立只读事务使消息可将失权目标标为不可打开而仍正常已读。 */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public FlowRuntimeModels.BusinessActionProgress businessActionProgress(
            AuthenticatedContext viewer, String moduleCode, long actionId, String traceId) {
        var delivery = runtimeDataService.actionDelivery(viewer, moduleCode, actionId, traceId);
        if (delivery.flowInstanceId() == null) {
            throw new DomainException("ACTION_FLOW_NOT_STARTED", "审批流程尚未启动，请刷新处理结果", HttpStatus.CONFLICT);
        }
        FlowInstance instance = instanceService.selectById(delivery.flowInstanceId());
        if (instance == null) {
            throw new DomainException("ACTION_FLOW_NOT_FOUND", "该操作的审批进度不存在或无权访问", HttpStatus.NOT_FOUND);
        }
        var input = actionCoordinator.actionForProgress(instance, viewer, moduleCode, actionId);
        AuthenticatedContext actor = actionCoordinator.currentActor(input.actor());
        runtimeDataService.authorizeActionProgress(actor, input, delivery.recordId(), traceId);
        FlowVersion version = versionService.selectById(instance.getFlowVersionId());
        FlowDefinition definition = definitionService.selectById(instance.getFlowId());
        if (version == null || definition == null || !Objects.equals(version.getFlowId(), instance.getFlowId())) {
            throw invalid("ACTION_FLOW_SNAPSHOT_INVALID", "该操作的审批快照无法读取");
        }
        Map<String, Map<String, Object>> nodeIndex = new LinkedHashMap<>();
        nodes(snapshot(instance)).forEach(node -> nodeIndex.put(string(node.get("nodeKey")), node));
        // 仅返回进入过的节点和系统生成历史，不沿用包含候选人、变量及原始错误的完整 InstanceView。
        List<FlowRuntimeModels.ProgressNode> progressNodes = executionService.selectList(
                        Wrappers.<FlowExecution>lambdaQuery().eq(FlowExecution::getInstanceId, instance.getId())
                                .orderByAsc(FlowExecution::getEnterTime).orderByAsc(FlowExecution::getId)).stream()
                .map(execution -> new FlowRuntimeModels.ProgressNode(execution.getNodeKey(),
                        progressNodeName(nodeIndex, execution.getNodeKey()), execution.getNodeType(), execution.getStatus(),
                        execution.getEnterTime(), execution.getLeaveTime())).toList();
        List<FlowRuntimeModels.ProgressEvent> progressHistory = historyService.selectList(
                        Wrappers.<FlowHistoryEvent>lambdaQuery().eq(FlowHistoryEvent::getInstanceId, instance.getId())
                                .orderByAsc(FlowHistoryEvent::getOccurTime).orderByAsc(FlowHistoryEvent::getId)).stream()
                .map(event -> new FlowRuntimeModels.ProgressEvent(progressNodeName(nodeIndex, event.getNodeKey()),
                        event.getEventName(), event.getBeforeStatus(), event.getAfterStatus(), event.getOccurTime())).toList();
        String message = switch (instance.getStatus()) {
            case "EXCEPTION" -> "审批需要处理异常，请等待管理员处理。";
            case "WAITING" -> "正在等待审批处理，可刷新查看进度。";
            case "REJECTED", "RETURNED" -> "审批未通过，原操作是否生效请查看处理结果。";
            case "COMPLETED" -> "审批已结束，原操作是否生效请查看处理结果。";
            case "WITHDRAWN", "TERMINATED" -> "审批已结束。";
            default -> "审批正在处理中，可刷新查看进度。";
        };
        var businessView = businessReadView(viewer, instance, nodeIndex.getOrDefault(instance.getCurrentNodeKey(), Map.of()), List.of("PAGE"));
        return new FlowRuntimeModels.BusinessActionProgress(actionId, moduleCode, input.actionCode(),
                delivery.mode().name(), delivery.businessStatus(), instance.getId(), definition.getName(),
                version.getVersionNumber(), instance.getStatus(), progressNodeName(nodeIndex, instance.getCurrentNodeKey()),
                instance.getStartTime(), instance.getFinishTime(), progressNodes, progressHistory,
                businessView == null ? null : businessView.current(), businessView, message);
    }

    /** 未在原快照中的节点只提供通用动态节点名称，不暴露其配置或组织身份。 */
    private String progressNodeName(Map<String, Map<String, Object>> nodeIndex, String key) {
        if (key == null) return null;
        Map<String, Object> node = nodeIndex.get(key);
        return node == null ? "动态审批节点" : string(node.get("name"));
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
        suspended.setUpdateTime(LocalDateTime.now());
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
        FlowExecution manualExecution = recordExecution(instance, nodeKey, "MANUAL_APPROVAL", "ACTIVE", null,
                suspended.getExecutionId());
        FlowTask manual = new FlowTask();
        manual.setInstanceId(instanceId); manual.setExecutionId(manualExecution.getId());
        manual.setNodeKey(nodeKey); manual.setTaskType("MANUAL_APPROVAL");
        manual.setStatus("PENDING"); manual.setAssigneeAccountId(manualAssignee.accountId());
        manual.setAssigneeTenantMemberId(manualAssignee.tenantMemberId());
        manual.setAssigneeSnapshotJson(toJson(manualSnapshot)); manual.setVersion(0);
        taskService.insert(manual);
        addCandidate(manual.getId(), manualAssignee,
                "由" + displayName(context, participantResolver.currentTenantMemberId(context))
                        + "手动加签：" + input.reason().strip());
        instance.setCurrentNodeKey(nodeKey); instance.setStatus("WAITING"); instance.setUpdateTime(LocalDateTime.now());
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

    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public FlowRuntimeModels.RuntimeNodePreview previewRuntimeNode(AuthenticatedContext context,
            Long instanceId, FlowRuntimeModels.RuntimeNodeRequest input) {
        FlowInstance instance = requireScoped(context, instanceId);
        AuthenticatedContext current = runtimeNodeActor(context, instance);
        requireAction(current, "ADD_MANUAL_NODE");
        requireRuntimeNodeView(current, instance);
        return validateRuntimeNode(current, instance, instanceGraph(instance), input);
    }

    /** 实例锁串行化图变更和人工审批；同键异内容或异主体不能借旧回执修改执行图。 */
    /** 父子关系先于子流程推进落库，子流程即使同步完成也能找到等待中的父执行。 */
    private void recordSubflowRelation(FlowInstance child, FlowVersion childVersion, SubflowParentLink link) {
        FlowInstance parent = instanceService.selectById(link.parentInstanceId());
        FlowExecution parentExecution = executionService.selectById(link.parentExecutionId());
        if (parent == null || parentExecution == null
                || !Objects.equals(parentExecution.getInstanceId(), parent.getId())
                || !Objects.equals(parentExecution.getNodeKey(), link.parentNodeKey())
                || !"ACTIVE".equals(parentExecution.getStatus())) {
            throw conflict("FLOW_SUBFLOW_PARENT_INVALID", "父流程等待执行已变化，未启动子流程");
        }
        FlowDefinition childDefinition = definitionService.selectById(childVersion.getFlowId());
        Map<String, Object> relation = new LinkedHashMap<>();
        relation.put("parentInstanceId", parent.getId());
        relation.put("parentExecutionId", parentExecution.getId());
        relation.put("parentNodeKey", link.parentNodeKey());
        relation.put("childInstanceId", child.getId());
        relation.put("childFlowId", child.getFlowId());
        relation.put("childFlowVersionId", child.getFlowVersionId());
        relation.put("childFlowVersionNumber", childVersion.getVersionNumber());
        relation.put("childFlowName", childDefinition == null ? "子流程" : childDefinition.getName());
        insertAction(child.getId(), null, "INSTANCE", "SUBFLOW_PARENT", null, relation,
                result(child), "SUBFLOW_PARENT:" + child.getId(), parent.getStartedByAccountId());
        insertAction(parent.getId(), null, link.parentNodeKey(), "SUBFLOW_STARTED", null, relation,
                result(parent), "SUBFLOW_START:" + parentExecution.getId(), parent.getStartedByAccountId());
        recordHistory(parent, parentExecution.getId(), null, link.parentNodeKey(), "SUBFLOW_STARTED",
                "子流程已启动，父流程等待", "RUNNING", "WAITING", null, null,
                Map.of("childInstanceId", child.getId(), "childFlowId", child.getFlowId(),
                        "childFlowName", relation.get("childFlowName"),
                        "childFlowVersionId", child.getFlowVersionId(),
                        "childFlowVersionNumber", childVersion.getVersionNumber(), "status", child.getStatus()));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FlowRuntimeModels.ActionResult addRuntimeNode(AuthenticatedContext context, Long instanceId,
            FlowRuntimeModels.RuntimeNodeRequest input, String traceId) {
        requireAction(context, "ADD_MANUAL_NODE");
        FlowInstance instance = requireScopedForUpdate(context, instanceId);
        AuthenticatedContext current = runtimeNodeActor(context, instance);
        requireAction(current, "ADD_MANUAL_NODE");
        requireRuntimeNodeView(current, instance);
        Map<String, Object> request = readMap(toJson(objectMapper.convertValue(input, new TypeReference<Map<String, Object>>() { })));
        // 旧后插请求没有锚点字段；缺省值不加入幂等内容，保证升级后相同旧请求仍可重放。
        if (input.anchorNodeKey() == null) request.remove("anchorNodeKey");
        FlowAction replay = findAction(instanceId, input.idempotencyKey());
        if (replay != null) {
            if (!"RUNTIME_NODE_ADDED".equals(replay.getActionCode())
                    || !Objects.equals(replay.getActedByAccountId(), current.accountId())
                    || !request.equals(map(readMap(replay.getInputJson()).get("request")))) {
                throw conflict("FLOW_RUNTIME_NODE_IDEMPOTENCY_CONFLICT", "该请求标识已用于其他人或不同内容，请重新预览");
            }
            return new FlowRuntimeModels.ActionResult(replay.getId(), true, view(current, instance));
        }
        FlowInstanceGraphService.Graph graph = instanceGraph(instance);
        FlowRuntimeModels.RuntimeNodePreview preview = validateRuntimeNode(current, instance, graph, input);
        Map<String, Object> anchor = nodes(graph.snapshot()).stream()
                .filter(node -> Objects.equals(preview.anchorNodeKey(), node.get("nodeKey"))).findFirst().orElseThrow();
        // 指定人工节点沿用该节点的冻结策略；条件/结束等没有人工策略的锚点沿用当前人工节点。
        if (!Set.of("APPROVAL", "FORM_TASK").contains(anchor.get("nodeType"))) {
            anchor = nodes(graph.snapshot()).stream().filter(candidate -> Objects.equals(instance.getCurrentNodeKey(),
                    candidate.get("nodeKey"))).findFirst().orElseThrow();
        }
        String key = "runtime_" + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("nodeKey", key); node.put("name", preview.name()); node.put("nodeType", preview.nodeType());
        node.put("assigneePolicy", runtimeNodePolicy(instance, input.assigneeIds()));
        node.put("approvalMode", preview.approvalMode());
        boolean approval = "APPROVAL".equals(preview.nodeType());
        node.put("formPolicy", approval ? map(anchor.get("formPolicy")) : Map.of());
        node.put("timeoutPolicy", approval ? map(anchor.get("timeoutPolicy")) : Map.of());
        node.put("exceptionPolicy", approval ? map(anchor.get("exceptionPolicy")) : Map.of());
        if (approval) {
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("approvalMode", preview.approvalMode());
            config.put(FlowManualOperationPolicy.CONFIG_KEY,
                    FlowManualOperationPolicy.fromConfig(map(anchor.get("config"))).snapshot());
            config.put("returnPolicy", returnPolicy(map(anchor.get("config"))).snapshot());
            if ("ALL_SIGN".equals(preview.approvalMode())) {
                String anchorMode = string(map(anchor.get("config")).getOrDefault("approvalMode", anchor.get("approvalMode")));
                config.put(FlowApprovalCompletionPolicy.CONFIG_KEY,
                        FlowApprovalCompletionPolicy.fromConfig(map(anchor.get("config")), anchorMode).snapshot());
            }
            node.put("config", config);
        } else node.put("config", Map.of("subject", preview.name(), "content", preview.reason()));
        Map<String, Object> change = instanceGraphs.insert(instance, graph, node, preview.position(), preview.anchorNodeKey());
        change.put("request", request);
        change.put("reason", preview.reason());
        change.put("addedByAccountId", current.accountId());
        change.put("addedByTenantMemberId", current.tenantMemberId());
        change.put("assignees", preview.assignees());
        boolean beforeCurrent = "BEFORE_CURRENT".equals(preview.position());
        if (beforeCurrent) change.put("suspensionFrame", nodeSuspensions.pause(instance));
        FlowAction action = insertAction(instanceId, null, key, "RUNTIME_NODE_ADDED", preview.reason(), change,
                result(instance), input.idempotencyKey(), current.accountId());
        if (beforeCurrent) {
            recordHistory(instance, null, null, preview.anchorNodeKey(), "NODE_SUSPENDED", "原节点整组待办与剩余时限已暂停",
                    "ACTIVE", "SUSPENDED", current.accountId(), current.tenantMemberId(), Map.of("frameActionId", action.getId()));
            instance.setStatus("RUNNING");
            // 原节点已经进入，不能重新判断过去的入边条件；直接进入新增节点，再沿新桥恢复原组。
            advance(systemContext(instance), instance, instanceGraph(instance).snapshot(), preview.anchorNodeKey(),
                    variables(instanceId), null, key);
        }
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "实例已变化，请重新预览");
        action.setResultJson(toJson(result(instance)));
        if (actionService.updateById(action) != 1) throw conflict("FLOW_RUNTIME_NODE_SAVE_CONFLICT", "新增节点结果未保存，请重新读取实例");
        audit(current, traceId, "FLOW_RUNTIME_NODE_ADDED", "FLOW_INSTANCE", instanceId,
                Map.of("nodeKey", key, "nodeType", preview.nodeType(), "position", preview.position(),
                        "anchorNodeKey", preview.anchorNodeKey(), "reason", preview.reason(), "graphRevision", action.getId()));
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(current, instance));
    }

    private FlowRuntimeModels.RuntimeNodePreview validateRuntimeNode(AuthenticatedContext context, FlowInstance instance,
            FlowInstanceGraphService.Graph graph, FlowRuntimeModels.RuntimeNodeRequest input) {
        requireDynamicNodesEnabled(graph.snapshot());
        if (!Objects.equals(instance.getVersion(), input.expectedInstanceVersion())
                || !Objects.equals(graph.revision(), input.expectedGraphRevision())) {
            throw conflict("FLOW_RUNTIME_NODE_PREVIEW_STALE", "实例或执行图已变化，请重新读取并预览");
        }
        if (!runtimeNodeAnchor(instance, graph.snapshot())) throw conflict("FLOW_RUNTIME_NODE_STATE_INVALID", "只能在当前普通审批或填写节点等待处理时增加节点");
        String type = input.nodeType().strip().toUpperCase(Locale.ROOT);
        String mode = input.approvalMode().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("APPROVAL", "NOTIFICATION").contains(type)) throw invalid("FLOW_RUNTIME_NODE_TYPE_INVALID", "请选择审批或抄送节点");
        String position = input.position().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("AFTER_CURRENT", "BEFORE_CURRENT", "BEFORE_NODE", "AFTER_NODE").contains(position)) {
            throw invalid("FLOW_RUNTIME_NODE_POSITION_INVALID", "请选择当前节点前后或指定未进入节点前后");
        }
        boolean currentPosition = Set.of("AFTER_CURRENT", "BEFORE_CURRENT").contains(position);
        String anchorKey = currentPosition ? instance.getCurrentNodeKey() : input.anchorNodeKey();
        if (currentPosition && input.anchorNodeKey() != null
                && !Objects.equals(anchorKey, input.anchorNodeKey())) {
            throw invalid("FLOW_RUNTIME_NODE_ANCHOR_INVALID", "当前节点前后的位置不能指定其他节点");
        }
        if (!currentPosition) {
            if (anchorKey == null || anchorKey.isBlank()) throw invalid("FLOW_RUNTIME_NODE_ANCHOR_REQUIRED", "请选择指定节点");
            FlowRuntimeModels.RuntimeNodeAnchor choice = runtimeNodeAnchors(instance, graph.snapshot()).stream()
                    .filter(candidate -> Objects.equals(anchorKey, candidate.nodeKey())).findFirst().orElse(null);
            if (choice == null || ("BEFORE_NODE".equals(position) ? !choice.beforeAllowed() : !choice.afterAllowed())) {
                throw conflict("FLOW_RUNTIME_NODE_ANCHOR_INVALID", "指定节点已进入、不可从当前路径到达或不支持该位置，请重新选择");
            }
        }
        if (!Set.of("OR_SIGN", "ALL_SIGN", "SEQUENTIAL").contains(mode)) throw invalid("FLOW_RUNTIME_NODE_MODE_INVALID", "请选择或签、会签或顺序会签");
        if (input.assigneeIds() == null || input.assigneeIds().isEmpty() || input.assigneeIds().size() > 100
                || input.assigneeIds().stream().anyMatch(id -> id == null || id <= 0)
                || input.assigneeIds().stream().distinct().count() != input.assigneeIds().size()) {
            throw invalid("FLOW_RUNTIME_NODE_PEOPLE_INVALID", "请选择不重复的当前范围有效人员");
        }
        Map<String, Object> policy = runtimeNodePolicy(instance, input.assigneeIds());
        AuthenticatedContext directory = participantContext(context, instance);
        String problem = participantResolver.validatePolicy(directory, policy);
        if (problem != null) throw invalid("FLOW_RUNTIME_NODE_PEOPLE_INVALID", problem);
        List<FlowParticipantResolver.ResolvedPerson> people = participantResolver.resolve(directory, policy, instance, variables(instance.getId()), null);
        if (people.size() != input.assigneeIds().size()) throw invalid("FLOW_RUNTIME_NODE_PEOPLE_INVALID", "选中人员已失效或不属于当前范围，请重新选择");
        Map<String, Object> anchor = nodes(graph.snapshot()).stream()
                .filter(node -> Objects.equals(instance.getCurrentNodeKey(), node.get("nodeKey"))).findFirst().orElseThrow();
        Map<String, Object> target = nodes(graph.snapshot()).stream()
                .filter(node -> Objects.equals(anchorKey, node.get("nodeKey"))).findFirst().orElseThrow();
        Map<String, Object> inherited = Set.of("APPROVAL", "FORM_TASK").contains(target.get("nodeType")) ? target : anchor;
        if ("APPROVAL".equals(type) && FlowManualOperationPolicy.fromConfig(map(inherited.get("config"))).requireClaim()
                && !"OR_SIGN".equals(mode)) {
            throw invalid("FLOW_TASK_CLAIM_MODE_INVALID", "插入位置必须领取，请选择或签；不能用新审批方式绕过冻结领取要求");
        }
        // 校验后续路径和容量，但预览不制造任务、通知或图变更。
        instanceGraphs.insert(instance, graph, Map.of("nodeKey", "runtime_preview", "nodeType", type), position, anchorKey);
        return new FlowRuntimeModels.RuntimeNodePreview(instance.getId(), instance.getVersion(), graph.revision(),
                instance.getCurrentNodeKey(), string(anchor.get("name")), anchorKey, string(target.get("name")),
                type, input.name().strip(), position, mode,
                people.stream().map(person -> new FlowRuntimeModels.ParticipantView(person.tenantMemberId(), person.accountId(),
                        person.displayName(), person.departmentId(), person.departmentName(), person.positionTitle())).toList(),
                input.reason().strip(), true, List.of("只修改本次实例，原模板与已完成历史保留",
                        "BEFORE_CURRENT".equals(position) ? "暂停原节点整组未完成任务与剩余时限，新增节点处理后恢复相同原任务；已完成审批与字段提交保留"
                                : "AFTER_CURRENT".equals(position) ? "新增节点紧接当前节点，已有后续节点顺延"
                                : "新增节点位于指定节点" + ("BEFORE_NODE".equals(position) ? "之前" : "之后") + "，仅在原路径实际到达时执行，保留原分支条件与优先级",
                        "APPROVAL".equals(type) ? (Set.of("APPROVAL", "FORM_TASK").contains(target.get("nodeType"))
                                ? "继承插入位置人工节点的冻结表单权限与超时设置" : "指定位置没有人工策略，继承当前人工节点的冻结表单权限与超时设置")
                                + "；需重新人工审批，不继承后续自动通过规则" : "按选中人员实际抄送，不生成审批任务"));
    }

    private Map<String, Object> runtimeNodePolicy(FlowInstance instance, List<Long> ids) {
        return Map.of("type", "PERSON", instance.getSystemId() == null ? "platformAccountIds" : "tenantMemberIds", ids);
    }

    /** 图修改ACTION不是进入事实；实际执行记录或任何历史任务都禁止再作为未来插入锚点。 */
    private List<FlowRuntimeModels.RuntimeNodeAnchor> runtimeNodeAnchors(FlowInstance instance, Map<String, Object> graph) {
        Set<String> entered = new LinkedHashSet<>();
        executionService.selectList(Wrappers.<FlowExecution>lambdaQuery().eq(FlowExecution::getInstanceId, instance.getId()))
                .forEach(execution -> entered.add(execution.getNodeKey()));
        tasks(instance.getId()).forEach(task -> entered.add(task.getNodeKey()));
        return instanceGraphs.anchors(graph, instance.getCurrentNodeKey(), entered, nodeSuspensions.transitAnchors(instance));
    }

    /** 加节点权限不扩大实例查看范围；不能靠猜同域实例编号获得图及人员信息。 */
    private void requireRuntimeNodeView(AuthenticatedContext actor, FlowInstance instance) {
        requireAction(actor, "VIEW_RUNTIME");
        if (!canView(actor, instance)) throw forbidden("FLOW_INSTANCE_VIEW_DENIED", "实例与业务对象权限交集不允许修改流程图");
    }

    /** 取锁后再次核验身份及当前角色，等待锁期间的撤权不能沿用请求开始时的缓存授权。 */
    private AuthenticatedContext runtimeNodeActor(AuthenticatedContext actor, FlowInstance instance) {
        AuthenticatedContext directory = participantContext(actor, instance);
        FlowParticipantResolver.ResolvedPerson person = instance.getSystemId() == null
                ? participantResolver.requirePlatformPerson(directory, actor.accountId())
                : participantResolver.requirePerson(directory, actor.tenantMemberId());
        if (!Objects.equals(person.accountId(), actor.accountId())) throw forbidden("FLOW_RUNTIME_NODE_ACTOR_INVALID", "当前人员身份已变化");
        ResolvedPermissions permissions = instance.getSystemId() == null
                ? platformPermissionResolver.resolve(instance.getPlatformId(), actor.accountId())
                : permissionResolver.resolve(instance.getSystemId(), instance.getTenantId(), person.tenantMemberId());
        return new AuthenticatedContext(actor.sessionId(), actor.accountId(), actor.platformId(), actor.systemId(), actor.tenantId(),
                person.systemMemberId(), person.tenantMemberId(), actor.username(), actor.displayName(), actor.mfaLevel(),
                permissions.roleIds(), permissions.permissions(), permissions.dataScopes());
    }

    private boolean runtimeNodeAnchor(FlowInstance instance, Map<String, Object> graph) {
        return "WAITING".equals(instance.getStatus()) && nodes(graph).stream().anyMatch(node ->
                Objects.equals(instance.getCurrentNodeKey(), node.get("nodeKey"))
                        && Set.of("APPROVAL", "FORM_TASK").contains(node.get("nodeType")))
                && tasks(instance.getId()).stream().anyMatch(task -> Objects.equals(instance.getCurrentNodeKey(), task.getNodeKey())
                        && "PENDING".equals(task.getStatus()) && Set.of("APPROVAL", "FORM_TASK").contains(task.getTaskType()));
    }

    private boolean dynamicNodesEnabled(Map<String, Object> graph) {
        Map<String, Object> start = nodes(graph).stream().filter(node -> "START".equals(node.get("nodeType"))).findFirst().orElse(Map.of());
        return !Boolean.FALSE.equals(map(map(start.get("config")).get("runtimeRules")).get("allowAddNodes"));
    }

    private void requireDynamicNodesEnabled(Map<String, Object> graph) {
        if (!dynamicNodesEnabled(graph)) throw forbidden("FLOW_RUNTIME_NODE_DISABLED", "本次流程的冻结规则不允许动态增加节点");
    }

    @Transactional
    public FlowRuntimeModels.ActionResult start(
            AuthenticatedContext context, FlowRuntimeModels.StartRequest input, String traceId) {
        if (input.idempotencyKey().startsWith("business-action:")
                || input.idempotencyKey().startsWith("module-approval:")) {
            throw invalid("FLOW_START_KEY_RESERVED", "该请求标识由业务动作保留使用");
        }
        requireAction(context, "START");
        FlowDefinition definition = requireFlow(context, input.flowId());
        requireBusinessAccess(context, input.businessType());
        FlowAction replay = findStartReplay(context.accountId(), input.idempotencyKey());
        if (replay != null) {
            FlowInstance existing = requireScoped(context, replay.getInstanceId());
            return new FlowRuntimeModels.ActionResult(replay.getId(), true, view(context, existing));
        }
        if (!"PUBLISHED".equals(definition.getStatus())) {
            throw conflict("FLOW_RUNTIME_FLOW_DISABLED", "流程已停用，不能发起新实例");
        }
        FlowPublication publication = publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                .eq(FlowPublication::getFlowId, definition.getId())).stream().findFirst()
                .orElseThrow(() -> invalid("FLOW_RUNTIME_VERSION_REQUIRED", "Flow 没有可用于发起的发布版本"));
        FlowVersion version = versionService.selectById(publication.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getFlowId(), definition.getId())) {
            throw invalid("FLOW_RUNTIME_VERSION_INCOMPATIBLE", "Flow 当前发布版本不存在或不兼容");
        }
        return startVersion(context, input, traceId, version, context.tenantId());
    }

    /** 模块审批以发布动作和记录权限为入口，使用当前有效绑定的不可变 Flow 版本启动实例。 */
    @Transactional
    public FlowRuntimeModels.ActionResult startModuleApproval(
            AuthenticatedContext context, FlowRuntimeModels.StartModuleApprovalRequest input, String traceId) {
        RuntimeDataService.ApprovalRecord record = runtimeDataService.requireApprovalRecord(
                context, input.moduleCode(), input.recordId(), traceId);
        String key = "module-approval:" + input.idempotencyKey();
        FlowAction replay = findStartReplay(context.accountId(), key);
        if (replay != null) {
            FlowInstance existing = requireScoped(context, replay.getInstanceId());
            if (!Objects.equals(existing.getBusinessType(), "MODULE:" + input.moduleCode())
                    || !Objects.equals(existing.getBusinessId(), record.recordId().toString())) {
                throw conflict("MODULE_APPROVAL_KEY_CONFLICT", "幂等键已用于其他业务记录");
            }
            return new FlowRuntimeModels.ActionResult(replay.getId(), true, view(context, existing));
        }
        // 审批发起时冻结当前有权读取的业务字段，供绑定条件和 Flow 分支使用。
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("recordId", record.recordId());
        variables.put("fields", record.conditionFields());
        record.conditionFields().forEach(variables::putIfAbsent);
        FlowBindingModels.ResolutionView binding = flowBindingService.resolveManualAction(
                context, record.moduleId(), record.actionId(), variables, traceId);
        if (binding == null || !"FLOW_ONLY".equals(binding.executionMode()) || binding.flowId() == null) {
            throw conflict("MODULE_APPROVAL_FLOW_REQUIRED", "审批动作尚未绑定可发起的系统 Flow");
        }
        FlowDefinition definition = definitionService.selectById(binding.flowId());
        FlowVersion version = versionService.selectById(binding.flowVersionId());
        if (definition == null || version == null || !"PUBLISHED".equals(definition.getStatus())
                || !"SYSTEM".equals(definition.getContextType())
                || !Objects.equals(definition.getSystemId(), context.systemId())
                || !Objects.equals(definition.getOwnerTenantId(), binding.sourceTenantId())
                || !Objects.equals(binding.requestedTenantId(), context.tenantId())
                || !Objects.equals(version.getFlowId(), definition.getId())
                || !Objects.equals(version.getDefinitionHash(), binding.definitionHash())
                || !readMap(version.getSnapshotJson()).equals(binding.definitionSnapshot())) {
            throw conflict("MODULE_APPROVAL_FLOW_INVALID", "审批绑定的 Flow 版本或租户范围已失效");
        }
        Map<String, Object> snapshot = Map.of("moduleId", record.moduleId(), "actionId", record.actionId(),
                "actionCode", "APPROVAL", "recordId", record.recordId(), "tenantId", context.tenantId());
        String title = record.title() == null || record.title().isBlank()
                ? definition.getName() + " · 审批" : record.title() + " · 审批";
        FlowRuntimeModels.StartRequest request = new FlowRuntimeModels.StartRequest(binding.flowId(), title,
                "MODULE:" + input.moduleCode(), record.recordId().toString(), snapshot, variables, key);
        return startVersion(context, request, traceId, version, context.tenantId());
    }

    /**
     * 已发布应用只能发起应用版本冻结的 Flow 版本；Flow 后续改版不能悄悄改变原应用授权行为。
     */
    @Transactional
    public FlowRuntimeModels.ActionResult startApplication(
            AuthenticatedContext context, FlowRuntimeModels.StartRequest input,
            Long flowVersionId, String definitionHash, Long applicationId,
            String externalPrincipalId, String traceId) {
        if (!input.idempotencyKey().startsWith("app-call:")) {
            throw invalid("APPLICATION_FLOW_START_KEY_INVALID", "应用发起缺少可信调用标识");
        }
        requireAction(context, "START");
        FlowDefinition definition = requireFlow(context, input.flowId());
        requireBusinessAccess(context, input.businessType());
        FlowAction replay = findStartReplay(context.accountId(), input.idempotencyKey());
        if (replay != null) {
            FlowInstance existing = requireScoped(context, replay.getInstanceId());
            return new FlowRuntimeModels.ActionResult(replay.getId(), true, view(context, existing));
        }
        if (!"PUBLISHED".equals(definition.getStatus())) {
            throw conflict("APPLICATION_TARGET_FLOW_DISABLED", "应用授权的目标 Flow 已停用");
        }
        FlowVersion version = versionService.selectById(flowVersionId);
        if (version == null || !Objects.equals(version.getFlowId(), definition.getId())
                || !Objects.equals(version.getDefinitionHash(), definitionHash)) {
            throw conflict("APPLICATION_TARGET_FLOW_VERSION_INVALID", "应用冻结的目标 Flow 版本不存在或已损坏");
        }
        return startVersion(context, input, traceId, version, context.tenantId(), null,
                new ApplicationOrigin(applicationId, blankToNull(externalPrincipalId)));
    }

    /** 内部入口使用动作已校验的来源发布快照；发起人仍保留其实际租户身份和当前权限。 */
    @Transactional
    public Long startBoundAction(com.unique.unexamine.foundation.manage.control.BusinessAction.Input action,
                                 FlowBindingModels.ResolutionView binding, Long actionId, Map<String, Object> businessResult) {
        AuthenticatedContext actor = action.actor();
        FlowVersion version = versionService.selectById(binding.flowVersionId());
        FlowDefinition definition = definitionService.selectById(binding.flowId());
        if (version == null || definition == null || !"SYSTEM".equals(definition.getContextType())
                || !Objects.equals(definition.getSystemId(), actor.systemId())
                || !Objects.equals(definition.getOwnerTenantId(), binding.sourceTenantId())
                || !Objects.equals(action.dataTenantId(), binding.requestedTenantId())
                || !Objects.equals(version.getFlowId(), definition.getId())
                || !Objects.equals(version.getDefinitionHash(), binding.definitionHash())
                || !readMap(version.getSnapshotJson()).equals(binding.definitionSnapshot())) {
            throw invalid("FLOW_ACTION_SNAPSHOT_INVALID", "动作绑定的流程快照不存在或不属于当前业务范围");
        }
        String key = "business-action:" + actionId;
        FlowAction replay = findStartReplay(actor.accountId(), key);
        if (replay != null) return replay.getInstanceId();
        // 绑定解析与实例创建之间可能有管理员停用操作，发起前再次核对当前流程状态。
        if (!"PUBLISHED".equals(definition.getStatus())) {
            throw conflict("FLOW_RUNTIME_FLOW_DISABLED", "流程已停用，不能发起新实例");
        }
        AuthenticatedContext context = actionCoordinator.currentActor(actor);
        Map<String, Object> intent = objectMapper.convertValue(action.payload(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
        Map<String, Object> business = businessResult.isEmpty() ? intent : businessResult;
        Map<String, Object> variables = new LinkedHashMap<>(business);
        if (business.get("fields") instanceof Map<?, ?> fields) {
            fields.forEach((code, value) -> variables.putIfAbsent(String.valueOf(code), value));
        }
        // Trusted metadata is separate from user variables; a field cannot replace the action identity.
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("actionId", actionId);
        snapshot.put("actionCode", action.actionCode());
        snapshot.put("executionMode", binding.executionMode());
        snapshot.put("targetRecordId", action.recordId());
        if (Set.of("CREATE", "UPDATE").contains(action.actionCode())) {
            snapshot.put("proposedOwnerMemberId", intent.get("ownerMemberId") == null ? actor.memberId() : intent.get("ownerMemberId"));
        }
        snapshot.put("moduleId", action.moduleId());
        snapshot.put("configVersionId", action.configVersionId());
        snapshot.put("actorTenantId", actor.tenantId());
        snapshot.put("dataTenantId", action.dataTenantId());
        snapshot.put("channel", action.options().channel());
        snapshot.put("causationId", action.options().causationId());
        snapshot.put("depth", action.options().depth());
        snapshot.put("intent", intent);
        snapshot.put("record", businessResult);
        Object recordId = businessResult.getOrDefault("id", action.recordId());
        // The Flow reviews an intent. Its writeback/status nodes must not mutate the effective target before approval.
        if ("AFTER_APPROVAL".equals(binding.executionMode())) recordId = null;
        // 命名动作只提交变动字段时没有业务标题，待办须使用可读流程名，不能把模块和动作编码展示给成员。
        String title = business.get("title") instanceof String value && !value.isBlank()
                ? value : definition.getName() + " · 业务动作";
        var request = new FlowRuntimeModels.StartRequest(binding.flowId(), title, "MODULE:" + action.moduleCode(),
                recordId == null ? null : recordId.toString(), snapshot, variables, key);
        return startVersion(context, request, action.traceId(), version, action.dataTenantId()).instance().id();
    }

    /**
     * 可靠业务事件的内部发起入口。绑定使用投递时的当前发布版本，稳定事件键保证重复投递最多产生一个实例。
     */
    @Transactional
    public Long startBoundEvent(AuthenticatedContext context, FlowBindingModels.ResolutionView binding,
            String eventKey, String eventType, String sourceType, String sourceId,
            Long moduleId, String moduleCode, Map<String, Object> variables) {
        FlowVersion version = versionService.selectById(binding.flowVersionId());
        FlowDefinition definition = definitionService.selectById(binding.flowId());
        if (version == null || definition == null || !"SYSTEM".equals(definition.getContextType())
                || !Objects.equals(definition.getSystemId(), context.systemId())
                || !Objects.equals(definition.getOwnerTenantId(), binding.sourceTenantId())
                || !Objects.equals(context.tenantId(), binding.requestedTenantId())
                || !Objects.equals(version.getFlowId(), definition.getId())
                || !Objects.equals(version.getDefinitionHash(), binding.definitionHash())
                || !readMap(version.getSnapshotJson()).equals(binding.definitionSnapshot())
                || !"FLOW_ONLY".equals(binding.executionMode())) {
            throw invalid("FLOW_EVENT_SNAPSHOT_INVALID", "事件绑定的流程快照不存在或不属于当前业务范围");
        }
        String key = "flow-event:" + eventKey;
        FlowAction replay = findStartReplay(context.accountId(), key);
        if (replay != null) return replay.getInstanceId();
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("eventType", eventType);
        snapshot.put("sourceType", sourceType);
        snapshot.put("sourceId", sourceId);
        snapshot.put("moduleId", moduleId);
        snapshot.put("moduleCode", moduleCode);
        snapshot.put("eventVariables", safeMap(variables));
        String title = moduleCode + switch (eventType) {
            case "EXCEPTION" -> "MODULE_VALIDATION".equals(sourceType) ? " · 模块数据校验异常" : " · 导入数据异常";
            case "SCHEDULED" -> " · 周期触发";
            default -> " · 导入完成";
        };
        var request = new FlowRuntimeModels.StartRequest(binding.flowId(), title, "MODULE:" + moduleCode,
                null, snapshot, safeMap(variables), key);
        // 完整事件键继续负责实例去重；审计追踪号受数据库长度约束，只使用稳定键的有界尾段。
        String traceKey = eventKey.length() <= 48 ? eventKey : eventKey.substring(eventKey.length() - 48);
        return startVersion(context, request, "flow-event-" + traceKey, version, context.tenantId()).instance().id();
    }

    private record SubflowParentLink(Long parentInstanceId, Long parentExecutionId, String parentNodeKey) { }
    /** 外部身份仅在应用命名空间内有效，技术执行账号不作为外部审批人的身份。 */
    private record ApplicationOrigin(Long applicationId, String externalPrincipalId) { }

    private FlowRuntimeModels.ActionResult startVersion(AuthenticatedContext context, FlowRuntimeModels.StartRequest input,
                                                        String traceId, FlowVersion version, Long dataTenantId) {
        return startVersion(context, input, traceId, version, dataTenantId, null, null);
    }

    private FlowRuntimeModels.ActionResult startVersion(AuthenticatedContext context, FlowRuntimeModels.StartRequest input,
                                                        String traceId, FlowVersion version, Long dataTenantId,
                                                        SubflowParentLink parentLink) {
        return startVersion(context, input, traceId, version, dataTenantId, parentLink, null);
    }

    private FlowRuntimeModels.ActionResult startVersion(AuthenticatedContext context, FlowRuntimeModels.StartRequest input,
                                                        String traceId, FlowVersion version, Long dataTenantId,
                                                        SubflowParentLink parentLink, ApplicationOrigin origin) {
        Map<String, Object> snapshot = readMap(version.getSnapshotJson());
        Map<String, Object> startNode = nodes(snapshot).stream()
                .filter(node -> "START".equals(string(node.get("nodeType")))).findFirst()
                .orElseThrow(() -> invalid("FLOW_RUNTIME_START_NODE_MISSING", "发布快照缺少开始节点"));

        FlowInstance instance = new FlowInstance();
        instance.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        instance.setPlatformId(context.platformId());
        instance.setSystemId(context.systemId());
        instance.setTenantId(dataTenantId);
        instance.setFlowId(version.getFlowId());
        instance.setFlowVersionId(version.getId());
        instance.setBusinessType(blankToNull(input.businessType()));
        instance.setBusinessId(blankToNull(input.businessId()));
        instance.setBusinessSnapshotJson(toJson(safeMap(input.businessSnapshot())));
        // 实例固定启动时的流程配置，后续发布版本不能改变已运行实例。
        instance.setRuntimeSnapshotJson(toJson(snapshot));
        instance.setTitle(input.title().strip());
        instance.setCurrentNodeKey(string(startNode.get("nodeKey")));
        instance.setStatus("RUNNING");
        instance.setStartedByAccountId(context.accountId());
        if (origin != null) {
            instance.setSourceApplicationId(origin.applicationId());
            instance.setSourceExternalPrincipalId(origin.externalPrincipalId());
        }
        // 来源目录身份可为空；真实账号必填，不能为启动共享流程添加来源成员。
        instance.setStartedByTenantMemberId(participantResolver.tenantMemberIdForAccount(
                context.systemId(), dataTenantId, context.accountId()));
        instance.setVersion(0);
        instanceService.insert(instance);
        if (parentLink != null) recordSubflowRelation(instance, version, parentLink);
        FlowExecution startExecution = recordExecution(
                instance, string(startNode.get("nodeKey")), "START", "ACTIVE", null);
        completeExecution(startExecution, "STARTED");
        recordHistory(instance, null, null, string(startNode.get("nodeKey")), "INSTANCE", "流程已发起",
                null, "RUNNING", context.accountId(), instance.getStartedByTenantMemberId(),
                Map.of("flowVersionId", version.getId(), "definitionHash", version.getDefinitionHash()));
        saveVariables(instance.getId(), safeMap(input.variables()));
        // 先写入内部 START 关联，初次异常通知也能从可信原动作恢复发起人。
        FlowAction action = insertAction(instance.getId(), null, string(startNode.get("nodeKey")), "START",
                null, Map.of("flowId", version.getFlowId(), "variables", safeMap(input.variables())),
                result(instance), input.idempotencyKey(), context.accountId());
        if (origin != null) {
            action.setActorApplicationId(origin.applicationId());
            action.setActorExternalPrincipalId(origin.externalPrincipalId());
        }
        // 同一事务内的纯自动流程也先发起再通过；失败回滚时两个事件一并撤销。
        publishFlowEvent(context, instance, LocalBusinessEvent.Type.FLOW_STARTED);
        advanceFromExecution(context, instance, snapshot, string(startNode.get("nodeKey")),
                variables(instance.getId()), null, startExecution.getId());
        action.setResultJson(toJson(result(instance)));
        actionService.updateById(action);
        audit(context, traceId, "FLOW_INSTANCE_STARTED", "FLOW_INSTANCE", instance.getId(),
                Map.of("flowId", version.getFlowId(), "flowVersionId", version.getId(),
                        "status", instance.getStatus(), "dataTenantId", dataTenantId == null ? "" : dataTenantId));
        return new FlowRuntimeModels.ActionResult(action.getId(), false,
                view(context, instanceService.selectById(instance.getId())));
    }

    // 人工请求也可能先定位任务再等待实例锁，等待之后必须看见刚提交的撤权与停用状态。
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FlowRuntimeModels.ActionResult handle(
            AuthenticatedContext context, Long taskId, FlowRuntimeModels.HandleTaskRequest input, String traceId) {
        return handleTask(context, taskId, input, traceId, null);
    }

    /** 应用签名已校验动作授权；审批身份仅以应用 ID 和调用方传入的标识匹配冻结待办。 */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FlowRuntimeModels.ActionResult handleApplication(AuthenticatedContext context, Long expectedFlowId,
            Long applicationId, String externalPrincipalId, Long taskId,
            FlowRuntimeModels.HandleTaskRequest input, String traceId) {
        if (externalPrincipalId == null || externalPrincipalId.isBlank() || externalPrincipalId.length() > 100
                || !Set.of("APPROVE", "REJECT").contains(input.actionCode().strip().toUpperCase(Locale.ROOT))) {
            throw forbidden("FLOW_EXTERNAL_ACTION_INVALID", "外部审批必须提供有效审批人标识及同意或拒绝动作");
        }
        return handleTask(context, taskId, input, traceId,
                new ExternalActor(expectedFlowId, applicationId, externalPrincipalId));
    }

    private record ExternalActor(Long flowId, Long applicationId, String principalId) { }

    private FlowRuntimeModels.ActionResult handleTask(
            AuthenticatedContext context, Long taskId, FlowRuntimeModels.HandleTaskRequest input,
            String traceId, ExternalActor external) {
        requireAction(context, "HANDLE");
        String actionCode = input.actionCode().strip().toUpperCase(Locale.ROOT);
        if (!TASK_ACTIONS.contains(actionCode)) {
            throw invalid("FLOW_TASK_ACTION_INVALID", "审批任务动作无效");
        }
        FlowTask initial = taskService.selectById(taskId);
        if (initial == null) throw notFound("FLOW_TASK_NOT_FOUND", "审批任务不存在");
        if (external != null && !externalTaskMatches(initial, external)) {
            throw forbidden("FLOW_EXTERNAL_APPROVER_DENIED", "外部审批人不匹配当前应用待办");
        }
        // 同一实例的人工处理先串行，再锁具体任务；会签不能各自看到另一人仍待办而永久等待。
        FlowInstance scoped = requireScopedForUpdate(context, initial.getInstanceId());
        if (external != null && !Objects.equals(scoped.getFlowId(), external.flowId())) {
            throw forbidden("FLOW_EXTERNAL_FLOW_DENIED", "当前应用未获权办理此流程");
        }
        FlowAction replay = findAction(scoped.getId(), input.idempotencyKey());
        if (replay != null) return replayTask(context, replay, taskId, input);

        FlowTask task = taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getId, taskId).last("FOR UPDATE")).stream().findFirst()
                .orElseThrow(() -> notFound("FLOW_TASK_NOT_FOUND", "审批任务不存在"));
        if (external != null && !externalTaskMatches(task, external)) {
            throw forbidden("FLOW_EXTERNAL_APPROVER_DENIED", "外部审批人不匹配当前应用待办");
        }
        FlowAction lockedReplay = findAction(scoped.getId(), input.idempotencyKey());
        if (lockedReplay != null) return replayTask(context, lockedReplay, taskId, input);
        if (!"PENDING".equals(task.getStatus())) {
            throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已处理，请读取实例最新结果");
        }
        Long currentTenantMemberId = external == null ? participantResolver.currentTenantMemberId(context) : null;
        FlowExecution taskExecution = taskExecution(task);
        if (!Set.of("WAITING", "RUNNING").contains(scoped.getStatus()) || taskExecution == null
                || !"ACTIVE".equals(taskExecution.getStatus())) {
            throw conflict("FLOW_TASK_NOT_CURRENT", "该任务不是当前人工节点待办，请刷新实例");
        }
        Map<String, Object> snapshot = snapshot(scoped);
        FlowManualOperationPolicy operations = manualOperationPolicy(task, snapshot);
        if ("RETURN_TASK".equals(task.getTaskType())) {
            if (!"RESUBMIT".equals(actionCode)) {
                throw forbidden("FLOW_RETURN_TASK_ACTION_INVALID", "退回修正任务只能由发起人重新提交");
            }
            return resubmitReturned(context, scoped, task, input, traceId);
        }
        if (Set.of("CLAIM", "UNCLAIM").contains(actionCode)) {
            return handleClaim(context, scoped, task, input, actionCode, operations, traceId);
        }
        if (external == null ? !isCurrentHandler(context, task) : !externalTaskMatches(task, external)) {
            throw forbidden("FLOW_TASK_HANDLER_DENIED", "只有当前有效处理人可以处理此任务");
        }
        if (operations.requireClaim() && task.getAssigneeAccountId() == null) {
            throw conflict("FLOW_TASK_CLAIM_REQUIRED", "请先领取该待办再处理或填写表单");
        }
        if ("REVOKE_DELEGATION".equals(actionCode)) {
            return handleDelegation(context, scoped, task, input, actionCode, traceId);
        }
        operations.requireOperation(actionCode, input.comment());
        if ("DELEGATE".equals(actionCode)) {
            return handleDelegation(context, scoped, task, input, actionCode, traceId);
        }
        if (Set.of("ADD_SIGN", "REMOVE_SIGN").contains(actionCode)) {
            return handleSignParticipants(context, scoped, task, input, actionCode, traceId);
        }
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        boolean manualTask = Boolean.TRUE.equals(taskSnapshot.get("manual"));
        var bound = actionCoordinator.businessReadForFlow(scoped);
        if (bound != null && !safeMap(input.variables()).isEmpty()) {
            throw invalid("FLOW_BUSINESS_VARIABLES_DENIED", "业务审批请通过节点表单修改字段，不能直接提交流程变量");
        }
        Map<String, Object> actionInput = new LinkedHashMap<>();
        FlowAction recordedApproval = null;
        actionInput.put("variables", bound == null ? safeMap(input.variables()) : Map.of());
        actionInput.put("targetTenantMemberId", input.targetTenantMemberId() == null ? "" : input.targetTenantMemberId());
        if (input.targetPlatformAccountId() != null) actionInput.put("targetPlatformAccountId", input.targetPlatformAccountId());
        actionInput.put("requestFingerprint", taskRequestFingerprint(input));
        if (external != null) {
            actionInput.put("applicationId", external.applicationId());
            actionInput.put("externalPrincipalId", external.principalId());
        }
        if (!"APPROVE".equals(actionCode) && input.attachmentIds() != null && !input.attachmentIds().isEmpty()) {
            throw invalid("FLOW_APPROVAL_ATTACHMENT_ACTION_INVALID", "审批附件只能随同意或完成填写一起提交");
        }
        if ("APPROVE".equals(actionCode)) {
            List<Long> attachments = validateApprovalAttachments(scoped, task, snapshot, input.attachmentIds());
            if (!attachments.isEmpty()) actionInput.put("attachmentIds", attachments);
        }
        Map<String, com.fasterxml.jackson.databind.JsonNode> submitted = input.fieldValues() == null ? Map.of() : input.fieldValues();
        if (!submitted.isEmpty() && !"APPROVE".equals(actionCode)) {
            throw invalid("FLOW_NODE_FORM_APPROVAL_REQUIRED", "只有同意操作可以提交节点字段修改");
        }
        var origin = "APPROVE".equals(actionCode) ? actionCoordinator.formOriginForFlow(scoped) : null;
        // 填写任务不能沿普通无表单审批路径空提交，未接通业务关联时保留任务等待修正。
        if ("FORM_TASK".equals(task.getTaskType()) && "APPROVE".equals(actionCode)
                && (origin == null || !supportedFormOrigin(origin))) {
            throw invalid("FLOW_NODE_FORM_NOT_AVAILABLE", "填写节点尚未关联可提交的待审批业务动作，请联系管理员调整执行方式");
        }
        if ("APPROVE".equals(actionCode) && origin != null && !manualTask
                && supportedFormOrigin(origin)
                && taskFieldPolicy(scoped, task).get("editable") instanceof List<?> editable && !editable.isEmpty()) {
            // 会签确认当前候选，不允许陈旧页面按到达顺序覆盖其他已批准修改；冲突事务完整回滚并保留待办。
            if ("ALL_SIGN".equals(string(taskSnapshot.get("approvalMode")))
                    && !Objects.equals(input.expectedFormRevision(), formSubmissions.formRevision(scoped))) {
                throw conflict("FLOW_NODE_FORM_CONFLICT", "其他处理人已提交新的表单内容，请重新读取并核对后再提交；本次修改尚未保存");
            }
            var candidate = formSubmissions.mergeForExecution(origin.input(), scoped);
            Map<String, com.fasterxml.jackson.databind.JsonNode> normalized = runtimeDataService.validateApprovalFields(
                    context, candidate, origin.configurationSnapshot(), taskFieldPolicy(scoped, task), submitted, traceId);
            if (!normalized.isEmpty()) {
                actionInput.put("formSubmission", formSubmissions.snapshot(origin.input(), origin.configurationSnapshot(),
                        scoped, task, normalized));
                Map<String, Object> updated = new LinkedHashMap<>(map(variables(scoped.getId()).get("fields")));
                Set<String> secrets = new LinkedHashSet<>();
                origin.configurationSnapshot().path("fields").forEach(field -> {
                    if ("SECRET".equals(field.path("fieldType").asText())) secrets.add(field.path("code").asText());
                });
                Map<String, Object> flowValues = new LinkedHashMap<>();
                normalized.forEach((code, value) -> {
                    if (secrets.contains(code)) return;
                    Object safe = objectMapper.convertValue(value, Object.class);
                    updated.put(code, safe);
                    if (!"fields".equals(code)) flowValues.put(code, safe);
                });
                flowValues.put("fields", updated);
                saveVariables(scoped.getId(), flowValues);
            }
        } else if (!submitted.isEmpty()) {
            throw invalid("FLOW_NODE_FORM_NOT_AVAILABLE", "当前动作或审批方式尚未接通节点表单提交");
        }
        if (bound == null) saveVariables(scoped.getId(), safeMap(input.variables()));
        if (manualTask && !"TRANSFER".equals(actionCode)) {
            applyManualStatusMapping(systemContext(scoped), scoped, taskSnapshot, actionCode, traceId);
        }

        if ("TRANSFER".equals(actionCode)) {
            boolean platform = context.systemId() == null;
            Long selected = platform ? input.targetPlatformAccountId() : input.targetTenantMemberId();
            if (selected == null || selected <= 0
                    || platform && (input.targetTenantMemberId() != null || Objects.equals(selected, context.accountId()))
                    || !platform && (input.targetPlatformAccountId() != null || Objects.equals(selected, currentTenantMemberId))) {
                throw invalid("FLOW_TASK_TRANSFER_TARGET_INVALID", platform
                        ? "转交必须选择当前平台中的其他成员，不能使用系统成员身份" : "转交必须选择当前工作空间中的其他成员");
            }
            FlowParticipantResolver.ResolvedPerson target = platform
                    ? participantResolver.requirePlatformPerson(context, selected)
                    : participantResolver.requirePerson(context, selected);
            task.setAssigneeAccountId(target.accountId());
            task.setAssigneeTenantMemberId(target.tenantMemberId());
            Map<String, Object> transferredSnapshot = new LinkedHashMap<>(taskSnapshot);
            transferredSnapshot.put("type", "PERSON");
            transferredSnapshot.remove(platform ? "tenantMemberIds" : "platformAccountIds");
            transferredSnapshot.put(platform ? "platformAccountIds" : "tenantMemberIds", List.of(selected));
            transferredSnapshot.put("resolvedPeople", List.of(personSnapshot(target)));
            transferredSnapshot.put(platform ? "transferredByAccountId" : "transferredByTenantMemberId",
                    platform ? context.accountId() : currentTenantMemberId);
            // 转交替代领取归属，原池仅留审计，受让人不得取消领取回到原候选人。
            transferredSnapshot.remove("claimActorAccountId"); transferredSnapshot.remove("claimActorTenantMemberId");
            task.setClaimTime(null);
            task.setAssigneeSnapshotJson(toJson(transferredSnapshot));
            task.setUpdateTime(LocalDateTime.now());
            if (taskService.updateById(task) != 1) {
                throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新");
            }
            stateRepository.clearTransferredClaim(task);
            addCandidate(task.getId(), target,
                    "任务由" + actorName(context, currentTenantMemberId, context.accountId()) + "转交");
            messageService.notifyFlowEvent(context, scoped.getId(),
                    "task-" + task.getId() + "-transfer-" + selected + "-v" + task.getVersion(),
                    "审批已转交：" + scoped.getTitle(),
                    "流程已转交给你，当前节点：" + task.getNodeKey() + "。请在待办或流程运行页处理。",
                    List.of(target.accountId()));
        } else {
            task.setStatus(actionCode);
            task.setCompleteTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            if (taskService.updateById(task) != 1) {
                throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新");
            }
            // 真实同意和受保护补丁必须先成为事务内事实，再进入后续节点；否则后续节点读不到本次批准内容。
            if ("APPROVE".equals(actionCode)) {
                recordedApproval = insertAction(scoped.getId(), task.getId(), task.getNodeKey(), actionCode,
                        blankToNull(input.comment()), actionInput, result(scoped), input.idempotencyKey(), context.accountId());
                rememberHumanApproval(scoped, task, recordedApproval, actionInput);
            }
            resolveTaskTimeoutIncidents(context, scoped, task);
            cancelPendingJobs(scoped.getId(), task.getId());
            if ("APPROVE".equals(actionCode)) {
                if (manualTask) {
                    completeExecution(taskExecution, actionCode);
                    resumeAfterManualNode(scoped, taskSnapshot);
                } else if (activateNextApprovalTaskOrWait(scoped, task, taskSnapshot)) {
                    scoped.setStatus("WAITING");
                    scoped.setUpdateTime(LocalDateTime.now());
                    updateHandledInstance(scoped);
                } else {
                    completeExecution(taskExecution, actionCode);
                    scoped.setStatus("RUNNING");
                    scoped.setUpdateTime(LocalDateTime.now());
                    updateHandledInstance(scoped);
                    advanceFromExecution(systemContext(scoped), scoped, snapshot, task.getNodeKey(),
                            variables(scoped.getId()), currentTenantMemberId, taskExecution.getId());
                }
            } else if ("RETURN".equals(actionCode)) {
                completeExecution(taskExecution, actionCode);
                ReturnPolicy returnPolicy = returnPolicy(map(nodes(snapshot).stream()
                        .filter(node -> Objects.equals(task.getNodeKey(), node.get("nodeKey")))
                        .findFirst().orElseThrow(() -> conflict("FLOW_RETURN_NODE_INVALID", "退回节点已不在冻结流程图中"))
                        .get("config")));
                String returnNodeKey = resolveReturnNode(scoped, task, returnPolicy, snapshot);
                // 先以退回结果关闭可能存在的前插暂停帧，再建立发起人修正任务；事务提交后实例仍是等待中。
                scoped.setStatus("RETURNED");
                cancelUnfinishedTasks(scoped);
                cancelPendingJobs(scoped.getId(), null);
                createReturnTask(scoped, task, taskSnapshot, returnPolicy, returnNodeKey, input.comment());
                scoped.setStatus("WAITING");
                scoped.setFinishTime(null);
                scoped.setUpdateTime(LocalDateTime.now());
                updateHandledInstance(scoped);
            } else {
                completeExecution(taskExecution, actionCode);
                scoped.setStatus("REJECTED");
                cancelUnfinishedTasks(scoped);
                scoped.setFinishTime(LocalDateTime.now());
                scoped.setUpdateTime(LocalDateTime.now());
                updateHandledInstance(scoped);
                cancelPendingJobs(scoped.getId(), null);
                cancelActiveExecutions(scoped.getId(), "INSTANCE_REJECTED");
                terminateActiveSubflows(scoped);
            }
        }
        FlowInstance latest = requireScopedForUpdate(context, scoped.getId());
        FlowAction action = recordedApproval;
        if (action != null) {
            action.setResultJson(toJson(result(latest)));
            if (actionService.updateById(action) != 1) throw conflict("FLOW_ACTION_VERSION_CONFLICT", "审批结果保存失败，请刷新后重试");
        } else try {
            action = insertAction(latest.getId(), task.getId(), task.getNodeKey(), actionCode,
                    blankToNull(input.comment()), actionInput,
                    result(latest), input.idempotencyKey(), context.accountId());
        } catch (DuplicateKeyException duplicate) {
            FlowAction duplicateReplay = findAction(latest.getId(), input.idempotencyKey());
            if (duplicateReplay != null) return replayTask(context, duplicateReplay, taskId, input);
            throw duplicate;
        }
        if (external != null) {
            action.setActorApplicationId(external.applicationId());
            action.setActorExternalPrincipalId(external.principalId());
            if (actionService.updateById(action) != 1) {
                throw conflict("FLOW_ACTION_VERSION_CONFLICT", "外部审批来源保存失败，请重试");
            }
        }
        if ("RETURN".equals(actionCode)) {
            FlowTask correction = tasks(latest.getId()).stream()
                    .filter(item -> "RETURN_TASK".equals(item.getTaskType()) && "PENDING".equals(item.getStatus()))
                    .max(Comparator.comparing(FlowTask::getId)).orElseThrow(() -> conflict(
                            "FLOW_RETURN_TASK_INVALID", "退回修正任务未创建，审批退回未保存"));
            Map<String, Object> correctionSnapshot = readMap(correction.getAssigneeSnapshotJson());
            recordHistory(latest, taskExecutionId(task), correction.getId(), correction.getNodeKey(), "TASK",
                    "发起人修正任务已创建", "RETURN", "PENDING", null, null,
                    Map.of("sourceTaskId", task.getId(), "returnNodeKey", correction.getNodeKey(),
                            "resubmitRoute", string(map(correctionSnapshot.get("returnPolicy")).get("resubmitRoute"))));
        }
        audit(context, traceId, "FLOW_TASK_" + actionCode, "FLOW_TASK", task.getId(),
                Map.of("instanceId", latest.getId(), "status", latest.getStatus()));
        if ("REJECTED".equals(latest.getStatus())) {
            messageService.notifyFlowEvent(systemContext(latest), latest.getId(), "action-" + action.getId() + "-result",
                    "流程结果：" + latest.getTitle(),
                    "流程已" + flowStatusName(latest.getStatus()) + "，可打开消息查看完整审批历史。",
                    List.of(latest.getStartedByAccountId()));
        } else if ("RETURN".equals(actionCode)) {
            messageService.notifyFlowEvent(systemContext(latest), latest.getId(), "action-" + action.getId() + "-return",
                    "申请已退回修正：" + latest.getTitle(),
                    "审批人已退回申请，请核对原因、修正可编辑内容后重新提交。",
                    List.of(latest.getStartedByAccountId()));
        }
        if ("REJECTED".equals(latest.getStatus())) failParentForTerminalSubflow(latest);
        if ("REJECTED".equals(latest.getStatus())) {
            publishFlowEvent(context, latest, LocalBusinessEvent.Type.FLOW_REJECTED);
        }
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, latest));
    }

    private boolean externalTaskMatches(FlowTask task, ExternalActor external) {
        return Objects.equals(task.getAssigneeApplicationId(), external.applicationId())
                && Objects.equals(task.getAssigneeExternalPrincipalId(), external.principalId());
    }

    /** 退回配置随发布版本冻结；旧版本按退回发起人、重提后从头执行兼容。 */
    private record ReturnPolicy(String target, String targetNodeKey, String resubmitRoute) {
        Map<String, Object> snapshot() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("target", target);
            result.put("resubmitRoute", resubmitRoute);
            if (targetNodeKey != null) result.put("targetNodeKey", targetNodeKey);
            return result;
        }
    }

    private ReturnPolicy returnPolicy(Map<String, Object> config) {
        Object configured = config.get("returnPolicy");
        if (configured == null) return new ReturnPolicy("STARTER", null, "FROM_START");
        if (!(configured instanceof Map<?, ?> value)
                || !(value.get("target") instanceof String target)
                || !(value.get("resubmitRoute") instanceof String route)
                || !Set.of("STARTER", "PREVIOUS", "SPECIFIED").contains(target)
                || !Set.of("FROM_START", "RETURN_NODE").contains(route)) {
            throw invalid("FLOW_RETURN_POLICY_INVALID", "冻结退回配置无效，请联系管理员重新发布流程");
        }
        if ("SPECIFIED".equals(target)) {
            if (!value.keySet().equals(Set.of("target", "targetNodeKey", "resubmitRoute"))
                    || !(value.get("targetNodeKey") instanceof String nodeKey) || nodeKey.isBlank()) {
                throw invalid("FLOW_RETURN_POLICY_INVALID", "指定退回节点配置不完整，请联系管理员重新发布流程");
            }
            return new ReturnPolicy(target, (String) value.get("targetNodeKey"), route);
        }
        if (!value.keySet().equals(Set.of("target", "resubmitRoute"))) {
            throw invalid("FLOW_RETURN_POLICY_INVALID", "退回配置包含不受支持的属性，请联系管理员重新发布流程");
        }
        return new ReturnPolicy(target, null, route);
    }

    /** 上一节点以本实例真实进入记录为准；分支未经过的模板节点不能成为退回目标。 */
    private String resolveReturnNode(FlowInstance instance, FlowTask source, ReturnPolicy policy,
                                     Map<String, Object> graph) {
        if ("STARTER".equals(policy.target())) return source.getNodeKey();
        if ("SPECIFIED".equals(policy.target())) {
            boolean valid = nodes(graph).stream().anyMatch(node -> Objects.equals(policy.targetNodeKey(), node.get("nodeKey"))
                    && Set.of("APPROVAL", "FORM_TASK").contains(node.get("nodeType")));
            if (!valid) throw conflict("FLOW_RETURN_TARGET_INVALID", "冻结的指定退回节点已不在当前实例图中");
            return policy.targetNodeKey();
        }
        Long executionId = taskExecutionId(source);
        if (executionId == null) {
            executionId = executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                    .eq(FlowExecution::getInstanceId, instance.getId()).eq(FlowExecution::getNodeKey, source.getNodeKey())
                    .orderByDesc(FlowExecution::getId).last("LIMIT 1 FOR UPDATE")).stream()
                    .map(FlowExecution::getId).findFirst().orElse(null);
        }
        if (executionId == null) throw conflict("FLOW_RETURN_TARGET_UNAVAILABLE", "当前执行记录不完整，不能安全退回上一节点");
        Set<String> available = nodes(graph).stream().filter(node -> Set.of("APPROVAL", "FORM_TASK").contains(node.get("nodeType")))
                .map(node -> string(node.get("nodeKey"))).collect(java.util.stream.Collectors.toSet());
        Long before = executionId;
        return executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                        .eq(FlowExecution::getInstanceId, instance.getId()).lt(FlowExecution::getId, before)
                        .in(FlowExecution::getNodeType, "APPROVAL", "FORM_TASK")
                        .in(FlowExecution::getStatus, "COMPLETED", "SUSPENDED")
                        .orderByDesc(FlowExecution::getId).last("FOR UPDATE")).stream()
                .map(FlowExecution::getNodeKey).filter(available::contains).filter(key -> !Objects.equals(key, source.getNodeKey()))
                .findFirst().orElseThrow(() -> conflict("FLOW_RETURN_TARGET_UNAVAILABLE", "本次实际路径没有可退回的上一人工节点"));
    }

    /** 退回任务只保存受信路径和冻结字段策略，发起人仍须通过当前身份与原业务权限校验。 */
    private FlowTask createReturnTask(FlowInstance instance, FlowTask source, Map<String, Object> sourceSnapshot,
                                      ReturnPolicy policy, String returnNodeKey, String reason) {
        AuthenticatedContext directory = participantContext(systemContext(instance), instance);
        FlowParticipantResolver.ResolvedPerson starter = instance.getSystemId() == null
                ? participantResolver.requirePlatformPerson(directory, instance.getStartedByAccountId())
                : participantResolver.requirePerson(directory, instance.getStartedByTenantMemberId());
        Map<String, Object> targetNode = nodes(snapshot(instance)).stream()
                .filter(node -> Objects.equals(returnNodeKey, node.get("nodeKey"))).findFirst()
                .orElseThrow(() -> conflict("FLOW_RETURN_TARGET_INVALID", "退回目标已不在当前实例图中"));
        FlowExecution returnExecution = recordExecution(instance, returnNodeKey, "RETURN_TASK", "ACTIVE", null,
                taskExecutionId(source));
        FlowTask correction = new FlowTask();
        correction.setInstanceId(instance.getId());
        correction.setExecutionId(returnExecution.getId());
        correction.setNodeKey(returnNodeKey);
        correction.setTaskType("RETURN_TASK");
        correction.setStatus("PENDING");
        correction.setAssigneeAccountId(starter.accountId());
        correction.setAssigneeTenantMemberId(starter.tenantMemberId());
        Map<String, Object> stored = new LinkedHashMap<>();
        stored.put("returnTask", true);
        stored.put("returnPolicy", policy.snapshot());
        stored.put("returnNodeKey", returnNodeKey);
        stored.put("returnNodeName", string(targetNode.get("name")));
        stored.put("sourceNodeKey", source.getNodeKey());
        stored.put("sourceTaskId", source.getId());
        stored.put("sourceExecutionId", Objects.requireNonNullElse(taskExecutionId(source), 0L));
        stored.put("formPolicy", taskFieldPolicy(instance, source));
        stored.put("reason", Objects.toString(reason, ""));
        stored.put("resolvedPeople", List.of(personSnapshot(starter)));
        correction.setAssigneeSnapshotJson(toJson(stored));
        correction.setVersion(0);
        taskService.insert(correction);
        addCandidate(correction.getId(), starter, "审批退回，由原发起人修正后重新提交");
        instance.setCurrentNodeKey(returnNodeKey);
        return correction;
    }

    /** 发起人重提沿用原动作、冻结表单和实例图，不接受客户端提供执行路径或流程变量。 */
    private FlowRuntimeModels.ActionResult resubmitReturned(AuthenticatedContext context, FlowInstance instance,
            FlowTask task, FlowRuntimeModels.HandleTaskRequest input, String traceId) {
        if (!Objects.equals(instance.getStartedByAccountId(), context.accountId())
                || instance.getSystemId() != null && !Objects.equals(instance.getStartedByTenantMemberId(),
                participantResolver.currentTenantMemberId(context))) {
            throw forbidden("FLOW_RETURN_RESUBMIT_DENIED", "只有当前有效发起人可以修正并重新提交");
        }
        if (!safeMap(input.variables()).isEmpty() || input.targetTenantMemberId() != null
                || input.targetPlatformAccountId() != null) {
            throw invalid("FLOW_RETURN_RESUBMIT_INPUT_INVALID", "重新提交只能修改冻结表单允许的字段，不能指定处理人或流程变量");
        }
        Map<String, Object> stored = readMap(task.getAssigneeSnapshotJson());
        if (!Boolean.TRUE.equals(stored.get("returnTask")) || !(stored.get("returnPolicy") instanceof Map<?, ?>)) {
            throw conflict("FLOW_RETURN_TASK_INVALID", "退回修正任务记录不完整，未重新执行流程");
        }
        ReturnPolicy policy = returnPolicy(Map.of("returnPolicy", map(stored.get("returnPolicy"))));
        String returnNodeKey = string(stored.get("returnNodeKey"));
        if (returnNodeKey.isBlank()) throw conflict("FLOW_RETURN_TASK_INVALID", "退回目标记录不完整，未重新执行流程");
        Map<String, Object> actionInput = new LinkedHashMap<>();
        actionInput.put("requestFingerprint", taskRequestFingerprint(input));
        actionInput.put("returnNodeKey", returnNodeKey);
        actionInput.put("resubmitRoute", policy.resubmitRoute());
        Map<String, com.fasterxml.jackson.databind.JsonNode> submitted = input.fieldValues() == null ? Map.of() : input.fieldValues();
        var origin = actionCoordinator.formOriginForFlow(instance);
        Map<String, Object> fieldPolicy = taskFieldPolicy(instance, task);
        if (origin != null && supportedFormOrigin(origin)
                && fieldPolicy.get("editable") instanceof List<?> editable && !editable.isEmpty()) {
            long currentRevision = formSubmissions.formRevision(instance);
            if (!Objects.equals(input.expectedFormRevision(), currentRevision)) {
                throw conflict("FLOW_NODE_FORM_CONFLICT", "退回内容已被更新，请重新读取后再提交；本次修改尚未保存");
            }
            var candidate = formSubmissions.mergeForExecution(origin.input(), instance);
            Map<String, com.fasterxml.jackson.databind.JsonNode> normalized = runtimeDataService.validateApprovalFields(
                    context, candidate, origin.configurationSnapshot(), fieldPolicy, submitted, traceId);
            if (!normalized.isEmpty()) {
                actionInput.put("formSubmission", formSubmissions.snapshot(origin.input(), origin.configurationSnapshot(),
                        instance, task, normalized));
                Map<String, Object> updated = new LinkedHashMap<>(map(variables(instance.getId()).get("fields")));
                Set<String> secrets = new LinkedHashSet<>();
                origin.configurationSnapshot().path("fields").forEach(field -> {
                    if ("SECRET".equals(field.path("fieldType").asText())) secrets.add(field.path("code").asText());
                });
                Map<String, Object> flowValues = new LinkedHashMap<>();
                normalized.forEach((code, value) -> {
                    if (secrets.contains(code)) return;
                    Object safe = objectMapper.convertValue(value, Object.class);
                    updated.put(code, safe);
                    if (!"fields".equals(code)) flowValues.put(code, safe);
                });
                flowValues.put("fields", updated);
                saveVariables(instance.getId(), flowValues);
            }
        } else if (!submitted.isEmpty()) {
            throw invalid("FLOW_NODE_FORM_NOT_AVAILABLE", "当前退回任务没有可安全修改的业务字段");
        }
        task.setStatus("RESUBMIT");
        task.setCompleteTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        if (taskService.updateById(task) != 1) throw conflict("FLOW_TASK_ALREADY_HANDLED", "退回任务已变化，请刷新后重试");
        FlowAction action = insertAction(instance.getId(), task.getId(), task.getNodeKey(), "RESUBMIT",
                blankToNull(input.comment()), actionInput, result(instance), input.idempotencyKey(), context.accountId());
        instance.setStatus("RUNNING");
        instance.setFinishTime(null);
        instance.setUpdateTime(LocalDateTime.now());
        updateHandledInstance(instance);
        Map<String, Object> graph = snapshot(instance);
        if ("FROM_START".equals(policy.resubmitRoute())) {
            String start = nodes(graph).stream().filter(node -> "START".equals(node.get("nodeType")))
                    .map(node -> string(node.get("nodeKey"))).findFirst()
                    .orElseThrow(() -> conflict("FLOW_RETURN_START_MISSING", "冻结流程图没有唯一开始节点，未重新执行"));
            advance(systemContext(instance), instance, graph, start, variables(instance.getId()), null);
        } else {
            advance(systemContext(instance), instance, graph, task.getNodeKey(), variables(instance.getId()), null, returnNodeKey);
        }
        FlowInstance latest = requireScopedForUpdate(context, instance.getId());
        action.setResultJson(toJson(result(latest)));
        if (actionService.updateById(action) != 1) throw conflict("FLOW_ACTION_VERSION_CONFLICT", "重新提交结果保存失败，请刷新后重试");
        audit(context, traceId, "FLOW_TASK_RESUBMIT", "FLOW_TASK", task.getId(),
                Map.of("instanceId", latest.getId(), "returnNodeKey", returnNodeKey, "status", latest.getStatus()));
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, latest));
    }

    /**
     * 委托只给当前任务增加一名有截止时间的代理人；任务归属、节点策略和业务字段权限仍以原审批人为边界。
     * 原审批人可随时撤销，代理人不能再次委托或借转交形成多级代理。
     */
    private FlowRuntimeModels.ActionResult handleDelegation(AuthenticatedContext context, FlowInstance instance,
            FlowTask task, FlowRuntimeModels.HandleTaskRequest input, String action, String traceId) {
        if (!safeMap(input.variables()).isEmpty() || input.fieldValues() != null && !input.fieldValues().isEmpty()
                || input.expectedFormRevision() != null) {
            throw invalid("FLOW_DELEGATION_PAYLOAD_INVALID", "审批委托不能同时修改流程变量或业务表单");
        }
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> stored = new LinkedHashMap<>(readMap(task.getAssigneeSnapshotJson()));
        Map<String, Object> current = map(stored.get("delegation"));
        boolean active = activeDelegation(current, now);
        Map<String, Object> delegation;
        FlowParticipantResolver.ResolvedPerson target = null;
        if ("DELEGATE".equals(action)) {
            if (active) throw conflict("FLOW_DELEGATION_ALREADY_ACTIVE", "当前任务已有生效中的代理人，请先撤销再重新委托");
            if (input.delegationEndTime() == null || !input.delegationEndTime().isAfter(now)) {
                throw invalid("FLOW_DELEGATION_END_TIME_INVALID", "委托截止时间必须晚于当前时间");
            }
            boolean platform = context.systemId() == null;
            Long selected = platform ? input.targetPlatformAccountId() : input.targetTenantMemberId();
            Long principalTenantMemberId = participantResolver.currentTenantMemberId(context);
            if (selected == null || selected <= 0
                    || platform && input.targetTenantMemberId() != null
                    || !platform && input.targetPlatformAccountId() != null) {
                throw invalid("FLOW_DELEGATION_TARGET_INVALID", platform
                        ? "代理人必须选择当前平台中的有效成员" : "代理人必须选择当前工作空间中的有效成员");
            }
            FlowParticipantResolver.ResolvedPerson principal = platform
                    ? participantResolver.requirePlatformPerson(context, context.accountId())
                    : participantResolver.requirePerson(context, principalTenantMemberId);
            target = platform ? participantResolver.requirePlatformPerson(context, selected)
                    : participantResolver.requirePerson(context, selected);
            if (Objects.equals(principal.accountId(), target.accountId())) {
                throw invalid("FLOW_DELEGATION_SELF_DENIED", "不能把审批委托给自己");
            }
            // 候选池任务在委托时固定当前处理人为原审批人，避免其他候选人与代理人同时扩大处理范围。
            if (task.getAssigneeAccountId() == null) {
                task.setAssigneeAccountId(principal.accountId());
                task.setAssigneeTenantMemberId(principal.tenantMemberId());
            }
            delegation = new LinkedHashMap<>();
            delegation.put("status", "ACTIVE");
            delegation.put("scope", "TASK");
            delegation.put("contextType", instance.getContextType());
            delegation.put("platformId", instance.getPlatformId());
            delegation.put("systemId", instance.getSystemId());
            delegation.put("tenantId", instance.getTenantId());
            delegation.put("instanceId", instance.getId());
            delegation.put("taskId", task.getId());
            delegation.put("principal", personSnapshot(principal));
            delegation.put("delegate", personSnapshot(target));
            delegation.put("startTime", now.toString());
            delegation.put("endTime", input.delegationEndTime().toString());
            delegation.put("reason", input.comment().strip());
        } else {
            if (!active) throw conflict("FLOW_DELEGATION_NOT_ACTIVE", "当前任务没有可撤销的生效委托");
            if (!delegationPrincipal(context, current)) {
                throw forbidden("FLOW_DELEGATION_REVOKE_DENIED", "只有原审批人可以撤销委托");
            }
            delegation = new LinkedHashMap<>(current);
            delegation.put("status", "REVOKED");
            delegation.put("revokedTime", now.toString());
            delegation.put("revokedByAccountId", context.accountId());
        }
        stored.put("delegation", delegation);
        task.setAssigneeSnapshotJson(toJson(stored));
        task.setUpdateTime(now);
        if (taskService.updateById(task) != 1) {
            throw conflict("FLOW_TASK_ALREADY_HANDLED", "审批任务已被其他处理人更新，请刷新后重试");
        }
        if (target != null) addCandidate(task.getId(), target, "审批任务代理人，有效期至 " + input.delegationEndTime());

        Map<String, Object> actionInput = new LinkedHashMap<>();
        actionInput.put("requestFingerprint", taskRequestFingerprint(input));
        actionInput.put("delegation", publicDelegation(delegation, now));
        actionInput.put("targetName", string(map(delegation.get("delegate")).get("displayName")));
        FlowAction recorded = insertAction(instance.getId(), task.getId(), task.getNodeKey(), action,
                blankToNull(input.comment()), actionInput, result(instance), input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_TASK_" + action, "FLOW_TASK", task.getId(),
                Map.of("instanceId", instance.getId(), "delegate", actionInput.get("targetName")));
        if (target != null) {
            messageService.notifyFlowEvent(context, instance.getId(), "task-" + task.getId() + "-delegate-" + recorded.getId(),
                    "审批已委托：" + instance.getTitle(),
                    "你可在 " + input.delegationEndTime() + " 前代理处理节点 " + task.getNodeKey() + "，权限仍受节点和业务规则限制。",
                    List.of(target.accountId()));
        }
        return new FlowRuntimeModels.ActionResult(recorded.getId(), false,
                view(context, instanceService.selectById(instance.getId())));
    }

    /** 加减当前节点参与人，不新增执行节点，不修改已完成审批或提交业务表单。 */
    private FlowRuntimeModels.ActionResult handleSignParticipants(AuthenticatedContext context, FlowInstance instance,
            FlowTask task, FlowRuntimeModels.HandleTaskRequest input, String action, String traceId) {
        if (!Set.of("APPROVAL", "FORM_TASK").contains(task.getTaskType())
                || !safeMap(input.variables()).isEmpty() || input.fieldValues() != null && !input.fieldValues().isEmpty()
                || input.expectedFormRevision() != null) {
            throw invalid("FLOW_SIGN_INPUT_INVALID", "加减签仅调整当前审批节点参与人，不能同时提交表单或变量");
        }
        boolean platform = context.systemId() == null;
        Long targetId = platform ? input.targetPlatformAccountId() : input.targetTenantMemberId();
        Long actorId = platform ? context.accountId() : participantResolver.currentTenantMemberId(context);
        if (targetId == null || targetId <= 0 || Objects.equals(targetId, actorId)
                || platform && input.targetTenantMemberId() != null || !platform && input.targetPlatformAccountId() != null) {
            throw invalid("FLOW_SIGN_TARGET_INVALID", "请选择当前工作空间中的其他参与人，不能混用平台与系统身份");
        }
        String candidateType = platform ? "PLATFORM_ACCOUNT" : "TENANT_MEMBER";
        Map<String, Object> stored = new LinkedHashMap<>(readMap(task.getAssigneeSnapshotJson()));
        String mode = string(stored.get("approvalMode"));
        if (!Set.of("OR_SIGN", "ALL_SIGN", "SEQUENTIAL").contains(mode)) {
            throw conflict("FLOW_SIGN_MODE_INVALID", "当前审批模式不支持同节点加减签，请刷新或联系管理员");
        }
        List<FlowTask> group = tasks(instance.getId()).stream()
                .filter(item -> Objects.equals(item.getNodeKey(), task.getNodeKey())
                        && Set.of("APPROVAL", "FORM_TASK").contains(item.getTaskType())
                        && sameTaskExecution(task, item)).toList();
        List<FlowTask> matches = group.stream().filter(item -> !"CANCELLED".equals(item.getStatus())
                && candidates(item.getId()).stream().anyMatch(candidate -> candidateType.equals(candidate.getCandidateType())
                && String.valueOf(targetId).equals(candidate.getCandidateId()))).toList();
        boolean add = "ADD_SIGN".equals(action);
        FlowParticipantResolver.ResolvedPerson target = null;
        FlowTask affected = task;
        String targetName;
        if (add) {
            if (!matches.isEmpty()) throw conflict("FLOW_SIGN_PARTICIPANT_EXISTS", "该人员已参与当前节点，不能重复加签或改写其审批事实");
            target = platform ? participantResolver.requirePlatformPerson(context, targetId)
                    : participantResolver.requirePerson(context, targetId);
            targetName = target.displayName();
        } else {
            affected = matches.stream().filter(item -> Set.of("PENDING", "QUEUED").contains(item.getStatus()))
                    .findFirst().orElseThrow(() -> conflict("FLOW_SIGN_PARTICIPANT_NOT_PENDING", "只能减去当前节点尚未完成的参与人"));
            Long assignedId = platform ? affected.getAssigneeAccountId() : affected.getAssigneeTenantMemberId();
            if ("OR_SIGN".equals(mode) && Objects.equals(assignedId, targetId)
                    || !"OR_SIGN".equals(mode) && Objects.equals(affected.getId(), task.getId())) {
                throw conflict("FLOW_SIGN_CURRENT_OWNER_PROTECTED", "不能减去当前处理或领取的人员，请先按转交或取消领取处理归属");
            }
            // 已失效的既有参与人也可移除；目标来自受信当前任务，不能以客户端身份扩大目录范围。
            FlowParticipantResolver.ResolvedPerson known = platform ? platformPerson(context, targetId) : person(context, targetId);
            targetName = known == null ? "已失效成员" : known.displayName();
        }
        LocalDateTime now = LocalDateTime.now();
        if ("OR_SIGN".equals(mode)) {
            List<Map<String, Object>> people = new ArrayList<>();
            Map<String, Map<String, Object>> knownPeople = new LinkedHashMap<>();
            for (String key : List.of("resolvedPeople", "claimPool")) {
                if (stored.get(key) instanceof List<?> pool) for (Object value : pool) {
                    Map<String, Object> person = map(value);
                    knownPeople.put(string(person.get(platform ? "accountId" : "tenantMemberId")), person);
                }
            }
            for (FlowTaskCandidate candidate : candidates(task.getId())) {
                if (!candidateType.equals(candidate.getCandidateType()) || !add && String.valueOf(targetId).equals(candidate.getCandidateId())) continue;
                Map<String, Object> known = knownPeople.get(candidate.getCandidateId());
                if (known == null) {
                    FlowParticipantResolver.ResolvedPerson resolved = platform ? platformPerson(context, numericBusinessId(candidate.getCandidateId()))
                            : person(context, numericBusinessId(candidate.getCandidateId()));
                    known = resolved == null ? Map.of(platform ? "accountId" : "tenantMemberId", numericBusinessId(candidate.getCandidateId()),
                            "displayName", "已失效成员") : personSnapshot(resolved);
                }
                people.add(known);
            }
            if (add) {
                people.add(personSnapshot(target));
                addCandidate(task.getId(), target, "同节点加签：" + targetName);
            } else {
                for (FlowTaskCandidate candidate : candidates(task.getId())) {
                    if (candidateType.equals(candidate.getCandidateType()) && String.valueOf(targetId).equals(candidate.getCandidateId())
                            && candidateService.deleteById(candidate.getId()) != 1) {
                        throw conflict("FLOW_SIGN_CONCURRENT_UPDATE", "候选人已变化，本次减签未保存");
                    }
                }
            }
            // 授权候选、领取池和提醒目录必须同步；当前有效处理人始终保留，不借减签跳过审批。
            stored.put("resolvedPeople", people);
            if (stored.containsKey("claimPool")) stored.put("claimPool", people);
            task.setAssigneeSnapshotJson(toJson(stored));
        } else if (add) {
            int sequence = group.stream().map(item -> readMap(item.getAssigneeSnapshotJson()).get("sequence"))
                    .filter(Number.class::isInstance).map(Number.class::cast).mapToInt(Number::intValue).max().orElse(0) + 1;
            Map<String, Object> addedSnapshot = new LinkedHashMap<>(stored);
            addedSnapshot.put("sequence", sequence); addedSnapshot.put("sequenceCount", sequence);
            addedSnapshot.put("resolvedPeople", List.of(personSnapshot(target)));
            FlowTask added = new FlowTask();
            added.setInstanceId(instance.getId()); added.setExecutionId(task.getExecutionId());
            added.setNodeKey(task.getNodeKey()); added.setTaskType(task.getTaskType());
            added.setStatus("SEQUENTIAL".equals(mode) ? "QUEUED" : "PENDING");
            added.setAssigneeAccountId(target.accountId()); added.setAssigneeTenantMemberId(target.tenantMemberId());
            added.setAssigneeSnapshotJson(toJson(addedSnapshot)); added.setVersion(0);
            // 会签新增人沿用当前绝对期限（含无期限）；顺序追加人沿用正常轮到他时的计时语义。
            if ("ALL_SIGN".equals(mode)) added.setDueTime(task.getDueTime());
            taskService.insert(added); affected = added;
            addCandidate(added.getId(), target, "同节点加签：" + targetName);
            if (added.getDueTime() != null) scheduleJob(instance, activeExecution(instance.getId(), task.getNodeKey()), task.getNodeKey(),
                    "TASK_TIMEOUT", Map.of("taskId", added.getId()), "TASK_TIMEOUT:" + added.getId(), 1, added.getDueTime());
        } else {
            affected.setStatus("CANCELLED"); affected.setCompleteTime(now); affected.setUpdateTime(now);
            if (taskService.updateById(affected) != 1) throw conflict("FLOW_SIGN_CONCURRENT_UPDATE", "目标任务已变化，本次减签未保存");
            resolveTaskTimeoutIncidents(context, instance, affected);
            cancelPendingJobs(instance.getId(), affected.getId());
        }
        task.setUpdateTime(now);
        if (taskService.updateById(task) != 1) throw conflict("FLOW_SIGN_CONCURRENT_UPDATE", "当前任务已变化，本次加减签未保存");
        instance.setUpdateTime(now); updateHandledInstance(instance);
        FlowAction recorded = insertAction(instance.getId(), task.getId(), task.getNodeKey(), action, blankToNull(input.comment()),
                Map.of("requestFingerprint", taskRequestFingerprint(input), "targetId", targetId, "targetName", targetName,
                        "affectedTaskId", affected.getId(), "approvalMode", mode), result(instance), input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_TASK_" + action, "FLOW_TASK", task.getId(),
                Map.of("instanceId", instance.getId(), "affectedTaskId", affected.getId(), "targetId", targetId));
        return new FlowRuntimeModels.ActionResult(recorded.getId(), false, view(context, instance));
    }

    /** 在实例、任务锁内领取或释放同一待办；不提交候选字段，不重启作业，不推进节点。 */
    private FlowRuntimeModels.ActionResult handleClaim(AuthenticatedContext context, FlowInstance instance, FlowTask task,
            FlowRuntimeModels.HandleTaskRequest input, String action, FlowManualOperationPolicy policy, String traceId) {
        if (!policy.requireClaim()) throw conflict("FLOW_TASK_CLAIM_NOT_ENABLED", "该节点未启用必须领取，请按当前允许操作处理");
        if (!safeMap(input.variables()).isEmpty() || input.fieldValues() != null && !input.fieldValues().isEmpty()
                || input.targetTenantMemberId() != null || input.targetPlatformAccountId() != null || input.expectedFormRevision() != null) {
            throw invalid("FLOW_TASK_CLAIM_INPUT_INVALID", "领取或取消领取不能同时提交字段、变量或转交目标");
        }
        Map<String, Object> stored = new LinkedHashMap<>(readMap(task.getAssigneeSnapshotJson()));
        boolean claim = "CLAIM".equals(action);
        FlowParticipantResolver.ResolvedPerson actor = context.systemId() == null
                ? participantResolver.requirePlatformPerson(context, context.accountId())
                : participantResolver.requirePerson(context, participantResolver.currentTenantMemberId(context));
        if (claim) {
            if (!(stored.get("claimPool") instanceof List<?> pool) || pool.stream().noneMatch(value -> {
                Map<String, Object> person = map(value);
                return Objects.equals(string(person.get(context.systemId() == null ? "accountId" : "tenantMemberId")),
                        String.valueOf(context.systemId() == null ? actor.accountId() : actor.tenantMemberId()));
            })) throw forbidden("FLOW_TASK_CLAIM_CANDIDATE_DENIED", "只有该待办当前候选池中的有效人员可以领取");
            if (task.getAssigneeAccountId() != null || task.getAssigneeTenantMemberId() != null) {
                throw conflict("FLOW_TASK_ALREADY_CLAIMED", "该待办已领取或已转交，请刷新待办");
            }
            stored.put("claimActorAccountId", actor.accountId());
            if (actor.tenantMemberId() != null) stored.put("claimActorTenantMemberId", actor.tenantMemberId());
        } else {
            if (!isCurrentHandler(context, task) || !activeClaim(task)) {
                throw forbidden("FLOW_TASK_UNCLAIM_DENIED", "只有实际领取人可以取消领取；转交后的待办不能退回原候选池");
            }
            stored.remove("claimActorAccountId"); stored.remove("claimActorTenantMemberId");
        }
        LocalDateTime now = LocalDateTime.now();
        // Base 非空更新不清空处理人；一次带版本条件的显式更新同时处理领取和释放的空值。
        int changed = stateRepository.changeClaim(task, claim, actor.accountId(), actor.tenantMemberId(),
                toJson(stored), now);
        if (changed != 1) throw conflict("FLOW_TASK_CONCURRENT_UPDATE", "待办归属已变化，本次领取操作未保存");
        instance.setUpdateTime(now); updateHandledInstance(instance);
        FlowAction result = insertAction(instance.getId(), task.getId(), task.getNodeKey(), action, input.comment(),
                Map.of("requestFingerprint", taskRequestFingerprint(input)), result(instance), input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_TASK_" + action, "FLOW_TASK", task.getId(), Map.of("instanceId", instance.getId()));
        return new FlowRuntimeModels.ActionResult(result.getId(), false, view(context, instance));
    }

    /** 领取归属必须同时匹配受信标记和当前处理人；转交不会凭旧领取时间获得释放权。 */
    private boolean activeClaim(FlowTask task) {
        Map<String, Object> stored = readMap(task.getAssigneeSnapshotJson());
        return task.getClaimTime() != null && task.getAssigneeAccountId() != null
                && Objects.equals(string(stored.get("claimActorAccountId")), String.valueOf(task.getAssigneeAccountId()))
                && (task.getAssigneeTenantMemberId() == null || Objects.equals(string(stored.get("claimActorTenantMemberId")),
                String.valueOf(task.getAssigneeTenantMemberId())));
    }

    /** 页面与工作台共用实际任务的操作投影，不在工作台另造固定按钮。 */
    private List<String> manualTaskActions(
            AuthenticatedContext context, FlowTask task, FlowManualOperationPolicy policy) {
        if (policy.requireClaim() && task.getAssigneeAccountId() == null) return List.of("CLAIM");
        ArrayList<String> actions = new ArrayList<>(policy.allowedActions());
        Map<String, Object> delegation = map(readMap(task.getAssigneeSnapshotJson()).get("delegation"));
        if (activeDelegation(delegation, LocalDateTime.now())) {
            // 生效关系不可叠加委托或转交；原审批人先撤销，代理人不能形成多级代理。
            actions.remove("DELEGATE");
            actions.remove("TRANSFER");
            if (delegationPrincipal(context, delegation)) actions.add("REVOKE_DELEGATION");
        }
        if (activeClaim(task) && !activeDelegationDelegate(context, task)) actions.add("UNCLAIM");
        return List.copyOf(actions);
    }

    /** 窄只读投影仍核对当前范围与实例可见性，不把完整配置或业务内容给待办服务。 */
    @Transactional(readOnly = true)
    public List<String> taskOperations(AuthenticatedContext context, Long taskId) {
        if (!allowed(context, "VIEW_RUNTIME") || !allowed(context, "HANDLE")) return List.of();
        FlowTask task = taskService.selectById(taskId);
        if (task == null) return List.of();
        FlowInstance instance = requireScoped(context, task.getInstanceId());
        if (!canView(context, instance) || currentHandlerTask(context, instance, List.of(task)) == null) return List.of();
        return "RETURN_TASK".equals(task.getTaskType()) ? List.of("RESUBMIT")
                : manualTaskActions(context, task, manualOperationPolicy(task, snapshot(instance)));
    }

    private void updateHandledInstance(FlowInstance instance) {
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_CONCURRENT_UPDATE", "流程状态已变化，本次处理未保存，请刷新后重试");
        }
    }

    /** 只有冻结后续规则真正引用的人工审批才封存事实；旧实例不追认新增规则。 */
    private void rememberHumanApproval(FlowInstance instance, FlowTask task, FlowAction action, Map<String, Object> input) {
        if (!"APPROVAL".equals(task.getTaskType()) || task.getAssigneeApplicationId() != null
                || Boolean.TRUE.equals(readMap(task.getAssigneeSnapshotJson()).get("manual"))) return;
        boolean referenced = nodes(snapshot(instance)).stream().anyMatch(node -> {
            Map<String, Object> repeat = map(map(node.get("config")).get("repeatApproval"));
            return Boolean.TRUE.equals(repeat.get("enabled")) && repeat.get("sourceNodeKeys") instanceof List<?> keys
                    && keys.contains(task.getNodeKey());
        });
        if (!referenced) return;
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("instanceId", instance.getId()); evidence.put("flowVersionId", instance.getFlowVersionId());
        evidence.put("taskId", task.getId()); evidence.put("formRevision", formSubmissions.formRevision(instance));
        evidence.put("variableRevisions", variableRevisions(instance.getId()));
        try {
            var origin = actionCoordinator.formOriginForFlow(instance);
            if (origin != null && supportedFormOrigin(origin)) {
                evidence.put("candidateSeal", runtimeDataService.sealApprovalCandidate(systemContext(instance),
                        formSubmissions.mergeForExecution(origin.input(), instance), origin.configurationSnapshot(),
                        repeatEvidenceBinding(instance, task, action), "repeat-evidence-" + action.getId()));
                evidence.put("businessCandidate", true);
            } else if (origin != null || actionCoordinator.businessReadForFlow(instance) != null) {
                evidence.put("available", false);
            }
            evidence.putIfAbsent("available", true);
        } catch (DomainException unavailable) {
            // 不能核验候选时保留人工同意事实，但不把它作为自动审批授权；不吞掉任何写入失败。
            evidence.put("available", false);
        }
        input.put("approvalEvidence", evidence); action.setInputJson(toJson(input));
        if (actionService.updateById(action) != 1) throw conflict("FLOW_ACTION_VERSION_CONFLICT", "审批事实保存失败，请重试");
    }

    /** 不含变量内容；新增、删除和相同值的重新提交也会改变集合或版本，强制重新确认。 */
    private Map<String, String> variableRevisions(Long instanceId) {
        Map<String, String> revisions = new LinkedHashMap<>();
        for (FlowInstanceVariable variable : variableService.selectList(Wrappers.<FlowInstanceVariable>lambdaQuery()
                .eq(FlowInstanceVariable::getInstanceId, instanceId).orderByAsc(FlowInstanceVariable::getVariableKey)
                .last(currentRuntimeRead(), "FOR SHARE"))) {
            revisions.put(variable.getVariableKey(), variable.getId() + ":" + variable.getVersion());
        }
        return revisions;
    }

    /** 密文绑定实例、冻结版本和真实人工动作，禁止把其他审批的密文复制成当前事实。 */
    private String repeatEvidenceBinding(FlowInstance instance, FlowTask source, FlowAction action) {
        String binding = "repeat-approval:" + instance.getId() + ":" + instance.getFlowVersionId() + ":" + source.getId() + ":" + action.getId();
        var origin = actionCoordinator.formOriginForFlow(instance);
        if (origin == null) return binding;
        var input = origin.input();
        return binding + ":" + input.moduleId() + ":" + input.configVersionId() + ":" + input.dataTenantId()
                + ":" + input.recordId() + ":" + input.actor().accountId() + ":" + input.actor().tenantId();
    }

    /** 自动处理只改变任务与自动历史，不制造人工动作；不能核验时回到正常待办而非跳过。 */
    private boolean tryRepeatApproval(FlowInstance instance, FlowTask task) {
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        Map<String, Object> repeat = map(taskSnapshot.get("repeatApproval"));
        if (!"PENDING".equals(task.getStatus()) || !"APPROVAL".equals(task.getTaskType())
                || task.getAssigneeApplicationId() != null
                || Boolean.TRUE.equals(taskSnapshot.get("manual")) || !Boolean.TRUE.equals(repeat.get("enabled"))) return false;
        String manualReason = "指定前序范围内没有同一有效审批人的人工同意事实";
        boolean writing = false;
        try {
            if (!"UNCHANGED".equals(repeat.get("formChangeRule"))
                    || !(repeat.get("sourceNodeKeys") instanceof List<?> sourceKeys) || sourceKeys.isEmpty())
                throw invalid("FLOW_REPEAT_APPROVAL_INVALID", "冻结自动审批规则不完整，需人工审批");
            if (repeat.containsKey("condition") && !flowConditions.matches(map(snapshot(instance).get("conditionContext")),
                    objectMapper.valueToTree(repeat), null, variables(instance.getId()))) {
                manualReason = "配置的业务附加条件未命中";
            } else {
                Long resubmitBarrier = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                                .eq(FlowAction::getInstanceId, instance.getId()).eq(FlowAction::getActionCode, "RESUBMIT")
                                .orderByDesc(FlowAction::getId).last(currentRuntimeRead() ? "LIMIT 1 FOR SHARE" : "LIMIT 1"))
                        .stream().map(FlowAction::getId).findFirst().orElse(null);
                for (FlowAction approved : actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                        .eq(FlowAction::getInstanceId, instance.getId()).eq(FlowAction::getActionCode, "APPROVE")
                        .isNotNull(FlowAction::getTaskId).orderByDesc(FlowAction::getId).last(currentRuntimeRead(), "FOR SHARE"))) {
                    if (resubmitBarrier != null && approved.getId() < resubmitBarrier) continue;
                    if (!sourceKeys.contains(approved.getNodeKey()) || Objects.equals(task.getNodeKey(), approved.getNodeKey())) continue;
                    FlowTask source = tasks(instance.getId()).stream().filter(item -> Objects.equals(item.getId(), approved.getTaskId())).findFirst().orElse(null);
                    if (source == null || !Objects.equals(source.getNodeKey(), approved.getNodeKey())
                            || !"APPROVAL".equals(source.getTaskType()) || !"APPROVE".equals(source.getStatus())
                            || source.getAssigneeApplicationId() != null || approved.getActorApplicationId() != null
                            || source.getCompleteTime() == null || approved.getActedByAccountId() == null
                            || Boolean.TRUE.equals(readMap(source.getAssigneeSnapshotJson()).get("manual"))) continue;
                    if (source.getAssigneeAccountId() != null && !Objects.equals(source.getAssigneeAccountId(), approved.getActedByAccountId())
                            || source.getAssigneeTenantMemberId() != null && !Objects.equals(source.getAssigneeTenantMemberId(), approved.getActedByTenantMemberId())) continue;
                    Map<String, Object> evidence = map(readMap(approved.getInputJson()).get("approvalEvidence"));
                    if (!Boolean.TRUE.equals(evidence.get("available"))
                            || objectMapper.valueToTree(evidence).path("instanceId").asLong() != instance.getId()
                            || objectMapper.valueToTree(evidence).path("flowVersionId").asLong() != instance.getFlowVersionId()
                            || objectMapper.valueToTree(evidence).path("taskId").asLong() != source.getId()) continue;
                    AuthenticatedContext handler = repeatHandlerContext(instance, approved);
                    if (!isCurrentHandler(handler, task)) continue;
                    requireAction(handler, "HANDLE");
                    if (!variableRevisions(instance.getId()).equals(map(evidence.get("variableRevisions")))
                            || objectMapper.valueToTree(evidence).path("formRevision").asLong(-1) != formSubmissions.formRevision(instance)) {
                        manualReason = "业务候选或流程变量在原审批后发生变化，需要重新确认";
                        continue;
                    }
                    AuthenticatedContext originActor = systemContext(instance);
                    var origin = actionCoordinator.formOriginForFlow(instance);
                    if (Boolean.TRUE.equals(evidence.get("businessCandidate"))) {
                        if (origin == null || !supportedFormOrigin(origin)
                                || !runtimeDataService.approvalCandidateUnchanged(originActor,
                                formSubmissions.mergeForExecution(origin.input(), instance), origin.configurationSnapshot(),
                                string(evidence.get("candidateSeal")), repeatEvidenceBinding(instance, source, approved),
                                "repeat-compare-" + task.getId())) {
                            manualReason = "原审批之后业务内容或正式记录版本已改变，需要重新确认";
                            continue;
                        }
                    } else if (origin != null || actionCoordinator.businessReadForFlow(instance) != null) continue;
                    validateTimeoutApproval(originActor, instance, task);
                    writing = true;
                    task.setStatus("APPROVE"); task.setCompleteTime(LocalDateTime.now()); task.setUpdateTime(LocalDateTime.now());
                    updateTimeoutTask(task); cancelPendingJobs(instance.getId(), task.getId());
                    FlowExecution execution = activeExecution(instance.getId(), task.getNodeKey());
                    recordHistory(instance, execution == null ? null : execution.getId(), task.getId(), task.getNodeKey(), "TASK",
                            "同一审批人已在指定前序节点同意，候选和变量未变化，按冻结规则自动通过",
                            "PENDING", "APPROVE", null, null,
                            Map.of("sourceActionId", approved.getId(), "sourceTaskId", source.getId(), "sourceNodeKey", source.getNodeKey(), "formChangeRule", "UNCHANGED"));
                    return true;
                }
            }
        } catch (DomainException unavailable) {
            if (writing) throw unavailable;
            manualReason = "当前人员、业务权限、必填内容或候选记录无法核验，需要人工审批";
        }
        String event = "后续自动审批条件未满足，保留人工待办";
        boolean recorded = !historyService.selectList(Wrappers.<FlowHistoryEvent>lambdaQuery()
                .eq(FlowHistoryEvent::getInstanceId, instance.getId()).eq(FlowHistoryEvent::getTaskId, task.getId())
                .likeRight(FlowHistoryEvent::getEventName, event)).isEmpty();
        if (!recorded) recordHistory(instance, null, task.getId(), task.getNodeKey(), "TASK", event + "；" + manualReason,
                "PENDING", "PENDING", null, null, Map.of("reason", manualReason));
        return false;
    }

    /** 审批人沿用真实来源身份并读取当前角色；目录上下文不能代替本人处理能力。 */
    private AuthenticatedContext repeatHandlerContext(FlowInstance instance, FlowAction action) {
        AuthenticatedContext directory = participantContext(systemContext(instance), instance);
        FlowParticipantResolver.ResolvedPerson person = instance.getSystemId() == null
                ? participantResolver.requirePlatformPerson(directory, action.getActedByAccountId())
                : participantResolver.requirePerson(directory, action.getActedByTenantMemberId());
        if (!Objects.equals(person.accountId(), action.getActedByAccountId())) throw forbidden("FLOW_REPEAT_APPROVAL_HANDLER_INVALID", "原审批人员身份已变化");
        ResolvedPermissions permissions = instance.getSystemId() == null
                ? platformPermissionResolver.resolve(instance.getPlatformId(), person.accountId())
                : permissionResolver.resolve(instance.getSystemId(), instance.getTenantId(), person.tenantMemberId());
        return new AuthenticatedContext(null, person.accountId(), instance.getPlatformId(), instance.getSystemId(), instance.getTenantId(),
                person.systemMemberId(), person.tenantMemberId(), "repeat-approval", "自动审批资格核验", "SYSTEM",
                permissions.roleIds(), permissions.permissions(), permissions.dataScopes());
    }

    /** 回放必须属于同一任务和动作；旧无指纹回执兼容只读，不重新执行已经完成的审批。 */
    private FlowRuntimeModels.ActionResult replayTask(AuthenticatedContext context, FlowAction action,
            Long taskId, FlowRuntimeModels.HandleTaskRequest input) {
        String fingerprint = string(readMap(action.getInputJson()).get("requestFingerprint"));
        if (!Objects.equals(action.getTaskId(), taskId)
                || !Objects.equals(action.getActionCode(), input.actionCode().strip().toUpperCase(Locale.ROOT)) || !fingerprint.isBlank()
                && !fingerprint.equals(taskRequestFingerprint(input))) {
            throw conflict("FLOW_TASK_IDEMPOTENCY_CONFLICT", "同一请求标识已用于其他任务或不同内容，请刷新后重新提交");
        }
        return replay(context, action);
    }

    private String taskRequestFingerprint(FlowRuntimeModels.HandleTaskRequest input) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("actionCode", input.actionCode().strip().toUpperCase(Locale.ROOT));
        request.put("comment", blankToNull(input.comment()));
        request.put("variables", safeMap(input.variables()));
        request.put("target", input.targetTenantMemberId());
        // 未提供平台身份时保持已有系统任务的幂等指纹，已有回执仍能正常重放。
        if (input.targetPlatformAccountId() != null) request.put("platformTarget", input.targetPlatformAccountId());
        if (input.expectedFormRevision() != null) request.put("formRevision", input.expectedFormRevision());
        if (input.delegationEndTime() != null) request.put("delegationEndTime", input.delegationEndTime().toString());
        request.put("attachmentIds", input.attachmentIds() == null ? List.of() : input.attachmentIds().stream().sorted().toList());
        request.put("fieldValues", input.fieldValues() == null ? Map.of() : input.fieldValues());
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(
                    canonicalTaskRequest(objectMapper.valueToTree(request)).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private com.fasterxml.jackson.databind.JsonNode canonicalTaskRequest(com.fasterxml.jackson.databind.JsonNode value) {
        if (value.isObject()) {
            var object = objectMapper.createObjectNode();
            Map<String, com.fasterxml.jackson.databind.JsonNode> sorted = new java.util.TreeMap<>();
            value.fields().forEachRemaining(entry -> sorted.put(entry.getKey(), entry.getValue()));
            sorted.forEach((key, child) -> object.set(key, canonicalTaskRequest(child)));
            return object;
        }
        if (value.isArray()) {
            var array = objectMapper.createArrayNode();
            value.forEach(child -> array.add(canonicalTaskRequest(child)));
            return array;
        }
        return value;
    }

    /**
     * 校验页面自动提交的附件引用，只接受当前流程实例内仍有效且已通过安全扫描的附件。
     * 引用标识由附件组件生成，用户不需要也不能手工填写。
     */
    private List<Long> validateApprovalAttachments(
            FlowInstance instance, FlowTask task, Map<String, Object> definitionSnapshot, List<Long> submittedIds) {
        FlowApprovalAttachmentPolicy policy = approvalAttachmentPolicy(task, definitionSnapshot);
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (submittedIds != null) for (Long id : submittedIds) {
            if (id == null || id <= 0 || !ids.add(id)) {
                throw invalid("FLOW_APPROVAL_ATTACHMENT_INVALID", "审批附件引用无效，请刷新附件后重新提交");
            }
        }
        if (policy.required() && ids.size() < policy.minimumCount()) {
            throw invalid("FLOW_APPROVAL_ATTACHMENT_REQUIRED",
                    "当前审批节点要求至少上传 " + policy.minimumCount() + " 个有效附件");
        }
        if (ids.isEmpty()) return List.of();
        List<FileReference> references = fileReferenceService.selectList(Wrappers.<FileReference>lambdaQuery()
                .in(FileReference::getId, ids));
        Map<Long, FileReference> byId = references.stream().collect(java.util.stream.Collectors.toMap(
                FileReference::getId, reference -> reference));
        for (Long id : ids) {
            FileReference reference = byId.get(id);
            boolean referenceValid = reference != null
                    && Objects.equals(reference.getContextType(), instance.getContextType())
                    && Objects.equals(reference.getSystemId(), instance.getSystemId())
                    && Objects.equals(reference.getTenantId(), instance.getTenantId())
                    && "FLOW_INSTANCE".equals(reference.getOwnerType())
                    && Objects.equals(reference.getOwnerId(), String.valueOf(instance.getId()))
                    && "attachments".equals(reference.getFieldCode());
            FileObject file = referenceValid ? fileObjectService.selectById(reference.getFileId()) : null;
            boolean fileValid = file != null
                    && Objects.equals(file.getContextType(), instance.getContextType())
                    && Objects.equals(file.getPlatformId(), instance.getPlatformId())
                    && Objects.equals(file.getSystemId(), instance.getSystemId())
                    && Objects.equals(file.getTenantId(), instance.getTenantId())
                    && "ACTIVE".equals(file.getStatus()) && "CLEAN".equals(file.getScanStatus());
            if (!referenceValid || !fileValid) {
                throw invalid("FLOW_APPROVAL_ATTACHMENT_INVALID",
                        "附件已删除、无权访问或不属于当前流程，请刷新附件后重新提交");
            }
        }
        return List.copyOf(ids);
    }

    /** 表单和选项均绑定当前待办；历史候选人、发起人或查看者不取得处理人的字段权限。 */
    private FlowTask requireFormTask(AuthenticatedContext context, Long taskId) {
        requireAction(context, "HANDLE");
        FlowTask task = taskService.selectById(taskId);
        if (task == null) throw notFound("FLOW_TASK_NOT_FOUND", "审批任务不存在");
        FlowInstance instance = requireScoped(context, task.getInstanceId());
        if (!"RETURN_TASK".equals(task.getTaskType())
                && manualOperationPolicy(task, snapshot(instance)).requireClaim() && task.getAssigneeAccountId() == null) {
            throw conflict("FLOW_TASK_CLAIM_REQUIRED", "请先领取该待办再填写节点表单");
        }
        if (!"PENDING".equals(task.getStatus())) throw conflict("FLOW_TASK_ALREADY_HANDLED", "任务已处理，请刷新待办");
        if (!isCurrentHandler(context, task)) {
            throw forbidden("FLOW_TASK_HANDLER_DENIED", "只有当前有效处理人可以填写节点表单");
        }
        return task;
    }

    /** 字段身份只取冻结版本，客户端及节点自报 fieldBindings 均不可用作授权依据。 */
    private Map<String, Object> taskFieldPolicy(FlowInstance instance, FlowTask task) {
        Map<String, Object> frozen = snapshot(instance);
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        Map<String, Object> node = nodes(frozen).stream().filter(value -> task.getNodeKey().equals(string(value.get("nodeKey"))))
                .findFirst().orElse(Map.of());
        Map<String, Object> policy = new LinkedHashMap<>("RETURN_TASK".equals(task.getTaskType())
                ? map(taskSnapshot.get("formPolicy")) : map(node.get("formPolicy")));
        policy.remove("fieldBindings");
        Map<String, Object> form = map(frozen.get("formContext"));
        if (!form.isEmpty()) policy.put("fieldBindings", ("MODULE:" + string(form.get("moduleCode"))).equals(instance.getBusinessType())
                ? form.getOrDefault("fields", List.of()) : List.of());
        return policy;
    }

    @Transactional(readOnly = true)
    public FlowRuntimeModels.TaskFormContext taskForm(AuthenticatedContext context, Long taskId, String traceId) {
        FlowTask task = requireFormTask(context, taskId);
        FlowInstance instance = requireScoped(context, task.getInstanceId());
        var origin = actionCoordinator.formOriginForFlow(instance);
        String module = origin == null ? null : origin.input().moduleCode();
        String action = origin == null ? null : origin.input().actionCode();
        Long record = origin == null ? null : origin.recordId();
        Map<String, Object> policy = taskFieldPolicy(instance, task);
        if (!(policy.get("editable") instanceof List<?> editable) || editable.isEmpty()) {
            return new FlowRuntimeModels.TaskFormContext(taskId, instance.getId(), module, record, action, null, null);
        }
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        if (origin == null || !supportedFormOrigin(origin) || Boolean.TRUE.equals(taskSnapshot.get("manual"))) {
            return new FlowRuntimeModels.TaskFormContext(taskId, instance.getId(), module, record, action, null,
                    "FORM_TASK".equals(task.getTaskType())
                            ? "填写节点尚未关联可提交的待审批业务动作，当前不能完成填写，请联系管理员调整执行方式。"
                            : "当前业务动作或审批方式尚未接通节点表单提交，请按现有审批信息处理。");
        }
        try {
            var candidate = formSubmissions.mergeForExecution(origin.input(), instance);
            var form = runtimeDataService.approvalEditContext(context, candidate, origin.configurationSnapshot(), policy, traceId);
            return new FlowRuntimeModels.TaskFormContext(taskId, instance.getId(), module, record, action,
                    form, form.configuration().configuration().path("readOnlyAtNode").asBoolean(false)
                    ? "当前节点仅可核对已有候选，提交不会修改业务字段；缺失必填内容须由有编辑权限的处理人补充。"
                    : form.configuration().configuration().path("fileInputsPending").asBoolean(false)
                    ? "文件字段的节点输入尚未接通，当前保持已有内容。" : null, formSubmissions.formRevision(instance));
        } catch (DomainException exception) {
            if (!"FLOW_NODE_FORM_NOT_AVAILABLE".equals(exception.code())) throw exception;
            return new FlowRuntimeModels.TaskFormContext(taskId, instance.getId(), module, record, action, null, exception.getMessage());
        }
    }

    private FlowRuntimeModels.TaskFormContext requireOptionForm(AuthenticatedContext context, Long taskId,
            String fieldCode, String traceId) {
        var form = taskForm(context, taskId, traceId);
        boolean eligible = false;
        if (form.form() != null) for (var field : form.form().configuration().configuration().path("fields")) {
            if (fieldCode.equals(field.path("code").asText())) eligible = true;
        }
        if (!eligible) throw forbidden("FLOW_NODE_FIELD_DENIED", "该字段当前不可编辑，不能读取来源选项");
        return form;
    }

    private BusinessActionApprovalCoordinator.BoundBusinessRead requireOptionOrigin(AuthenticatedContext context,
            FlowInstance instance, Long taskId, String fieldCode, String traceId) {
        requireOptionForm(context, taskId, fieldCode, traceId);
        var origin = actionCoordinator.formOriginForFlow(instance);
        if (origin == null) throw forbidden("FLOW_NODE_FIELD_DENIED", "当前任务没有可编辑的业务申请");
        return new BusinessActionApprovalCoordinator.BoundBusinessRead(
                formSubmissions.mergeForExecution(origin.input(), instance), origin.configurationSnapshot(), origin.recordId());
    }

    private boolean supportedFormOrigin(BusinessActionApprovalCoordinator.BoundBusinessRead origin) {
        if (Set.of("CREATE", "UPDATE").contains(origin.input().actionCode())) return true;
        for (var action : origin.configurationSnapshot().path("actions")) {
            if (!origin.input().actionCode().equals(action.path("code").asText())
                    || !"CUSTOM".equals(action.path("actionType").asText())) continue;
            var config = readMap(action.path("configJson").asText("{}"));
            if (!(config.get("parameterFieldCodes") instanceof List<?> parameters)) return false;
            if ("UPDATE_FIELDS".equals(config.get("operation"))) return !parameters.isEmpty() && parameters.size() <= 20;
            if (!"SET_STATUS".equals(config.get("operation")) || !parameters.isEmpty()
                    || !(config.get("statusValue") instanceof String status)) return false;
            return !status.isBlank() && status.length() <= 64 && status.equals(status.strip())
                    && status.chars().noneMatch(Character::isISOControl);
        }
        return false;
    }

    /** 节点目录复用正式记录来源解析，客户端只能指定已授权字段和查询条件。 */
    @Transactional(readOnly = true)
    public com.unique.unexamine.moduleconfig.manage.DictionaryModels.RuntimeDictionary taskDictionary(
            AuthenticatedContext context, Long taskId, String fieldCode, Long parentId, String traceId) {
        var task = requireFormTask(context, taskId);
        var instance = requireScoped(context, task.getInstanceId());
        var origin = requireOptionOrigin(context, instance, taskId, fieldCode, traceId);
        return runtimeDataService.approvalFieldDictionary(context, origin.input(), origin.configurationSnapshot(),
                taskFieldPolicy(instance, task), fieldCode, parentId, traceId);
    }

    @Transactional(readOnly = true)
    public List<com.unique.unexamine.runtimedata.manage.RuntimeReferenceOption> taskReferences(
            AuthenticatedContext context, Long taskId, String fieldCode, String search, String traceId) {
        var task = requireFormTask(context, taskId);
        var instance = requireScoped(context, task.getInstanceId());
        var origin = requireOptionOrigin(context, instance, taskId, fieldCode, traceId);
        return runtimeDataService.approvalReferenceOptions(context, origin.input(), origin.configurationSnapshot(),
                taskFieldPolicy(instance, task), fieldCode, search, traceId);
    }

    @Transactional(readOnly = true)
    public com.unique.unexamine.authorization.manage.SystemPeopleDirectoryModels.Directory taskOrganization(
            AuthenticatedContext context, Long taskId, String fieldCode, String traceId) {
        var task = requireFormTask(context, taskId);
        var instance = requireScoped(context, task.getInstanceId());
        var origin = requireOptionOrigin(context, instance, taskId, fieldCode, traceId);
        return runtimeDataService.approvalOrganizationOptions(context, origin.input(), origin.configurationSnapshot(),
                taskFieldPolicy(instance, task), fieldCode, traceId);
    }

    @Transactional
    public FlowRuntimeModels.ActionResult actOnInstance(
            AuthenticatedContext context, Long instanceId,
            FlowRuntimeModels.InstanceActionRequest input, String traceId) {
        String actionCode = input.actionCode().strip().toUpperCase(Locale.ROOT);
        if (!INSTANCE_ACTIONS.contains(actionCode)) {
            throw invalid("FLOW_INSTANCE_ACTION_INVALID", "实例动作无效");
        }
        boolean collaboration = Set.of("COMMENT", "REMIND").contains(actionCode);
        if (collaboration) requireAction(context, "VIEW_RUNTIME");
        else if ("TERMINATE".equals(actionCode)) requireAction(context, "TERMINATE");
        else requireAction(context, "HANDLE");
        FlowInstance instance = requireScopedForUpdate(context, instanceId);
        FlowAction existing = findAction(instanceId, input.idempotencyKey());
        if (existing != null) {
            if (collaboration && (!Objects.equals(existing.getActionCode(), actionCode)
                    || !Objects.equals(existing.getCommentText(), input.comment().strip()))) {
                throw conflict("FLOW_ACTION_IDEMPOTENCY_CONFLICT", "同一幂等标识已用于其他评论或催办内容");
            }
            return replay(context, existing);
        }
        if (collaboration) return collaborate(context, instance, input, actionCode, traceId);
        if (!ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())) {
            throw conflict("FLOW_INSTANCE_ALREADY_FINISHED", "实例已结束，请读取最新结果");
        }
        if ("WITHDRAW".equals(actionCode) && !Objects.equals(instance.getStartedByAccountId(), context.accountId())) {
            throw forbidden("FLOW_INSTANCE_WITHDRAW_DENIED", "只有发起人可以撤回实例");
        }
        String beforeStatus = instance.getStatus();
        instance.setStatus("WITHDRAW".equals(actionCode) ? "WITHDRAWN" : "TERMINATED");
        instance.setFinishTime(LocalDateTime.now());
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "实例已被其他操作更新");
        }
        cancelUnfinishedTasks(instance);
        cancelPendingJobs(instanceId, null);
        cancelActiveExecutions(instanceId, "INSTANCE_" + instance.getStatus());
        terminateActiveSubflows(instance);
        FlowAction action = insertAction(instanceId, null,
                instance.getCurrentNodeKey() == null ? "INSTANCE" : instance.getCurrentNodeKey(),
                actionCode, input.comment().strip(), Map.of("historyBeforeStatus", beforeStatus), result(instance),
                input.idempotencyKey(), context.accountId());
        audit(context, traceId, "FLOW_INSTANCE_" + actionCode, "FLOW_INSTANCE", instanceId,
                Map.of("status", instance.getStatus()));
        messageService.notifyFlowEvent(systemContext(instance), instanceId, "action-" + action.getId() + "-result",
                "流程已" + flowStatusName(instance.getStatus()) + "：" + instance.getTitle(),
                "流程已" + flowStatusName(instance.getStatus()) + "，处理意见：" + input.comment().strip(),
                List.of(instance.getStartedByAccountId()));
        failParentForTerminalSubflow(instance);
        return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, instance));
    }

    /** 评论不改变流程状态；催办只通知当前真实待办接收人，并与动作记录处于同一事务。 */
    private FlowRuntimeModels.ActionResult collaborate(
            AuthenticatedContext context, FlowInstance instance,
            FlowRuntimeModels.InstanceActionRequest input, String actionCode, String traceId) {
        if (!canView(context, instance)) {
            throw forbidden("FLOW_INSTANCE_VIEW_DENIED", "当前身份不能参与该流程实例");
        }
        String comment = input.comment().strip();
        String nodeKey = instance.getCurrentNodeKey() == null ? "INSTANCE" : instance.getCurrentNodeKey();
        if ("COMMENT".equals(actionCode)) {
            FlowAction action = insertAction(instance.getId(), null, nodeKey, actionCode, comment,
                    Map.of("historyBeforeStatus", instance.getStatus()), result(instance),
                    input.idempotencyKey(), context.accountId());
            audit(context, traceId, "FLOW_INSTANCE_COMMENT", "FLOW_INSTANCE", instance.getId(),
                    Map.of("actionId", action.getId()));
            return new FlowRuntimeModels.ActionResult(action.getId(), false, view(context, instance));
        }
        if (!ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())) {
            throw conflict("FLOW_INSTANCE_ALREADY_FINISHED", "流程已结束，不能继续催办");
        }
        List<FlowTask> current = tasks(instance.getId()).stream()
                .filter(task -> "PENDING".equals(task.getStatus()) && task.getCompleteTime() == null
                        && Objects.equals(task.getNodeKey(), instance.getCurrentNodeKey())).toList();
        boolean starter = Objects.equals(instance.getStartedByAccountId(), context.accountId());
        boolean handler = allowed(context, "HANDLE") && current.stream().anyMatch(task -> isCurrentHandler(context, task));
        if (!starter && !handler) {
            throw forbidden("FLOW_INSTANCE_REMIND_DENIED", "只有流程发起人或当前处理人可以催办");
        }
        AuthenticatedContext directory = participantContext(context, instance);
        LinkedHashSet<Long> recipientIds = new LinkedHashSet<>();
        for (FlowTask task : current) {
            recipientIds.addAll(currentTaskRecipients(directory, task, readMap(task.getAssigneeSnapshotJson())));
        }
        if (recipientIds.isEmpty()) {
            throw conflict("FLOW_INSTANCE_REMIND_RECIPIENT_MISSING", "当前没有可接收催办的有效处理人");
        }
        Map<String, Object> actionInput = Map.of("historyBeforeStatus", instance.getStatus(),
                "recipientCount", recipientIds.size());
        LinkedHashMap<String, Object> actionResult = new LinkedHashMap<>(result(instance));
        actionResult.put("recipientCount", recipientIds.size());
        FlowAction action = insertAction(instance.getId(), null, nodeKey, actionCode, comment,
                actionInput, actionResult, input.idempotencyKey(), context.accountId());
        messageService.notifyFlowEvent(directory, instance.getId(), "manual-reminder-action-" + action.getId(),
                "审批催办：" + instance.getTitle(),
                "当前流程仍在等待处理。催办说明：" + comment, List.copyOf(recipientIds));
        audit(context, traceId, "FLOW_INSTANCE_REMIND", "FLOW_INSTANCE", instance.getId(),
                Map.of("actionId", action.getId(), "recipientCount", recipientIds.size()));
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
        FlowException observedIncident = exceptionService.selectById(incidentId);
        if (observedIncident == null) throw notFound("FLOW_INCIDENT_NOT_FOUND", "流程异常不存在");
        FlowInstance instance = requireScopedForUpdate(context, observedIncident.getInstanceId());
        FlowException incident = exceptionService.selectList(Wrappers.<FlowException>lambdaQuery()
                        .eq(FlowException::getId, incidentId).last("FOR UPDATE"))
                .stream().findFirst().orElseThrow(() -> notFound("FLOW_INCIDENT_NOT_FOUND", "流程异常不存在"));
        FlowAction replay = findAction(instance.getId(), input.idempotencyKey());
        if (replay != null) return replay(context, replay);
        if (!"OPEN".equals(incident.getStatus()) && !"RETRY_SCHEDULED".equals(incident.getStatus())) {
            throw conflict("FLOW_INCIDENT_ALREADY_RESOLVED", "流程异常已经处理，请读取最新结果");
        }
        if (tasks(instance.getId()).stream().anyMatch(task -> Objects.equals(task.getNodeKey(), incident.getNodeKey())
                && Set.of("PENDING", "QUEUED", "SUSPENDED").contains(task.getStatus()))) {
            throw conflict("FLOW_INCIDENT_TASK_PENDING",
                    "此异常保留了未完成审批任务，请由当前处理人通过原待办提交或转交；不能跳过节点或用通用重试代替填写");
        }
        Long actorTenantMemberId = participantResolver.currentTenantMemberId(context);
        if ("RETRY".equals(actionCode)) {
            if ("AI_COMPLETION".equals(incident.getExceptionType())) {
                // AI 异常只重排原模型作业，保留原执行号并重新经过当前授权校验，不能走通用自动节点补偿。
                FlowExecution execution = activeExecution(instance.getId(), incident.getNodeKey());
                FlowJob aiJob = execution == null ? null : jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                                .eq(FlowJob::getInstanceId, instance.getId())
                                .eq(FlowJob::getExecutionId, execution.getId())
                                .eq(FlowJob::getJobType, "AI_COMPLETION")
                                .eq(FlowJob::getStatus, "FAILED")
                                .orderByDesc(FlowJob::getId).last("LIMIT 1 FOR UPDATE"))
                        .stream().findFirst().orElse(null);
                if (aiJob == null) throw conflict("FLOW_AI_RETRY_JOB_MISSING", "AI 节点原作业已变化，请读取最新状态");
                aiJob.setPayloadJson(toJson(Map.of("exceptionId", incident.getId())));
                aiJob.setStatus("PENDING");
                aiJob.setNextRunTime(LocalDateTime.now());
                aiJob.setCompleteTime(null);
                aiJob.setLastErrorCode(null);
                aiJob.setLastErrorMessage(null);
                aiJob.setLockTime(null);
                aiJob.setLockedBy(null);
                if (jobService.updateById(aiJob) != 1) throw conflict("FLOW_AI_RETRY_JOB_CONFLICT", "AI 节点作业已变化");
            } else {
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
                    job.setNextRunTime(LocalDateTime.now());
                    job.setLastErrorCode(null);
                    job.setLastErrorMessage(null);
                    jobService.updateById(job);
                }
            }
            incident.setStatus("RETRY_SCHEDULED");
            instance.setStatus("WAITING");
        } else {
            incident.setStatus("RESOLVED");
            incident.setResolveTime(LocalDateTime.now());
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
            advance(systemContext(instance), instance, snapshot(instance), incident.getNodeKey(), variables(instance.getId()),
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

    /** 超时异常只能经原任务的合法处理消除，不能以异常补偿跳过必填和审批。 */
    private void resolveTaskTimeoutIncidents(AuthenticatedContext actor, FlowInstance instance, FlowTask handled) {
        List<FlowException> incidents = exceptionService.selectList(Wrappers.<FlowException>lambdaQuery()
                .eq(FlowException::getInstanceId, instance.getId()).eq(FlowException::getNodeKey, handled.getNodeKey())
                .in(FlowException::getExceptionType, List.of("JOB_EXECUTION", "TIMEOUT"))
                .in(FlowException::getStatus, List.of("OPEN", "RETRY_SCHEDULED")).last("FOR UPDATE"));
        for (FlowException incident : incidents) {
            incident.setStatus("RESOLVED"); incident.setResolveTime(LocalDateTime.now());
            incident.setResolvedByAccountId(actor.accountId());
            incident.setResolvedByTenantMemberId(participantResolver.currentTenantMemberId(actor));
            incident.setResolutionComment("保留的原待办已由当前合法处理人处理");
            if (exceptionService.updateById(incident) != 1) throw conflict("FLOW_INCIDENT_VERSION_CONFLICT", "流程异常已变化");
        }
        if (!incidents.isEmpty()) {
            if ("EXCEPTION".equals(instance.getStatus())) instance.setStatus("WAITING");
            instance.setErrorCode(null); instance.setErrorMessage(null);
            clearInstanceError(instance.getId());
        }
    }

    /** 既有 worker 逐条处理持久化作业；候选扫描不持锁，事务统一先锁实例再锁作业。 */
    public int runDueJobs(String workerId, int requestedLimit) {
        int limit = Math.max(1, Math.min(requestedLimit, 50));
        List<Long> dueIds = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                        .eq(FlowJob::getStatus, "PENDING")
                        .le(FlowJob::getNextRunTime, LocalDateTime.now())
                        .orderByAsc(FlowJob::getNextRunTime).orderByAsc(FlowJob::getId)
                        .last("LIMIT " + limit)).stream().map(FlowJob::getId).toList();
        int processed = 0;
        for (Long jobId : dueIds) {
            try {
                Boolean executed = jobTransaction.execute(transaction -> {
                    FlowJob job = lockPendingJob(jobId);
                    if (job == null) return false;
                    runJob(job, workerId == null ? "flow-worker" : workerId);
                    return true;
                });
                if (Boolean.TRUE.equals(executed)) processed++;
            } catch (RuntimeException failure) {
                // 原事务完整回滚后再记录异常，避免代理方法将事务标为 rollback-only 时丢失错误。
                try {
                    jobTransaction.executeWithoutResult(transaction -> recordJobFailure(jobId, failure));
                    processed++;
                } catch (RuntimeException unavailable) {
                    LOG.error("流程作业 {} 的异常暂未持久化，保留待执行状态供下轮恢复", jobId, unavailable);
                }
            }
        }
        return processed;
    }

    /** 人工动作和后台作业均先锁实例，避免任务、作业与实例反向等待。 */
    private FlowJob lockPendingJob(Long jobId) {
        FlowJob observed = jobService.selectById(jobId);
        if (observed == null) return null;
        instanceService.selectList(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getId, observed.getInstanceId()).last("FOR UPDATE"));
        FlowJob current = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                .eq(FlowJob::getId, jobId).last("FOR UPDATE")).stream().findFirst().orElse(null);
        return current != null && "PENDING".equals(current.getStatus()) && current.getNextRunTime() != null
                && !current.getNextRunTime().isAfter(LocalDateTime.now()) ? current : null;
    }

    /** 不恢复失效发起人权限、不伪造审批结果；异常保留待办并允许其他作业继续。 */
    private void recordJobFailure(Long jobId, RuntimeException failure) {
        FlowJob job = lockPendingJob(jobId);
        if (job == null) return;
        FlowInstance instance = instanceService.selectList(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getId, job.getInstanceId()).last("FOR UPDATE")).stream().findFirst().orElse(null);
        if (instance == null || !ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())) {
            finishJob(job, "CANCELLED", null, null);
            return;
        }
        Long taskId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("taskId")));
        if ("TASK_TIMEOUT".equals(job.getJobType())) {
            FlowTask task = taskId == null ? null : taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                    .eq(FlowTask::getId, taskId).last("FOR UPDATE")).stream().findFirst().orElse(null);
            if (task == null || !Objects.equals(task.getInstanceId(), instance.getId()) || !"PENDING".equals(task.getStatus())) {
                finishJob(job, "CANCELLED", null, null);
                return;
            }
        }
        String code = failure instanceof DomainException domain ? domain.code() : "FLOW_JOB_EXECUTION_FAILED";
        String message = publicErrorMessage(code);
        job.setAttemptCount((job.getAttemptCount() == null ? 0 : job.getAttemptCount()) + 1);
        boolean messageDelivery = "MESSAGE_DELIVERY".equals(job.getJobType());
        boolean webhookDelivery = "WEBHOOK_DELIVERY".equals(job.getJobType());
        Long linkedIncidentId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("exceptionId")));
        FlowException linkedIncident = linkedIncidentId == null ? null : exceptionService.selectById(linkedIncidentId);
        boolean aiCompletion = "AI_COMPLETION".equals(job.getJobType())
                || "RETRY".equals(job.getJobType()) && linkedIncident != null
                && "AI_COMPLETION".equals(linkedIncident.getExceptionType());
        if ((messageDelivery || webhookDelivery) && job.getAttemptCount() < job.getMaxAttempts()) {
            int retryDelayMinutes = Math.max(1, number(readMap(job.getPayloadJson()).get("retryDelayMinutes"), 5));
            job.setStatus("PENDING");
            job.setNextRunTime(LocalDateTime.now().plusMinutes(retryDelayMinutes));
            job.setLastErrorCode(code);
            job.setLastErrorMessage(message);
            job.setLockTime(null);
            job.setLockedBy(null);
            jobService.updateById(job);
            instance.setStatus("WAITING");
            instance.setErrorCode(null);
            instance.setErrorMessage(null);
            instance.setUpdateTime(LocalDateTime.now());
            instanceService.updateById(instance);
            recordHistory(instance, job.getExecutionId(), null, job.getNodeKey(),
                    webhookDelivery ? "FLOW_WEBHOOK_RETRY" : "FLOW_MESSAGE_RETRY",
                    webhookDelivery ? "应用回调失败，已安排重试" : "站内消息投递失败，已安排重试",
                    "WAITING", "WAITING", null, null,
                    Map.of("jobId", job.getId(), "attemptCount", job.getAttemptCount(),
                            "maxAttempts", job.getMaxAttempts(), "errorCode", code));
            return;
        }
        finishJob(job, "FAILED", code, message);
        FlowException incident = aiCompletion ? linkedIncident : null;
        if (incident != null && (!Objects.equals(incident.getInstanceId(), instance.getId())
                || !Objects.equals(incident.getNodeKey(), job.getNodeKey())
                || !"AI_COMPLETION".equals(incident.getExceptionType()))) incident = null;
        if (incident == null) {
            incident = new FlowException();
            incident.setInstanceId(instance.getId()); incident.setNodeKey(job.getNodeKey());
            incident.setExceptionType(messageDelivery ? "MESSAGE_DELIVERY"
                    : webhookDelivery ? "WEBHOOK_DELIVERY" : aiCompletion ? "AI_COMPLETION" : "JOB_EXECUTION");
            incident.setErrorCode(code); incident.setErrorMessage(message);
            incident.setPolicyAction("MANUAL"); incident.setStatus("OPEN"); incident.setVersion(0);
            exceptionService.insert(incident);
        } else {
            // 人工重试失败恢复原 AI 异常，避免生成一个仍在“等待重试”的旧异常和一个新异常。
            incident.setStatus("OPEN");
            incident.setErrorCode(code);
            incident.setErrorMessage(message);
            if (exceptionService.updateById(incident) != 1) {
                throw conflict("FLOW_INCIDENT_VERSION_CONFLICT", "流程异常已变化");
            }
        }
        instance.setStatus("EXCEPTION"); instance.setErrorCode(code); instance.setErrorMessage(message);
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已被其他操作更新");
        recordHistory(instance, job.getExecutionId(), taskId, job.getNodeKey(), "INCIDENT",
                (messageDelivery ? "站内消息投递失败："
                        : webhookDelivery ? "应用回调投递失败："
                        : aiCompletion ? "AI 节点模型调用失败：" : "超时或后台作业未执行：") + message,
                null, "EXCEPTION", null, null,
                Map.of("jobId", job.getId(), "errorCode", code));
        if (aiCompletion) {
            auditRecorder.record("flow-ai:" + instance.getId() + ":" + job.getId() + ":" + job.getAttemptCount(),
                    instance.getStartedByAccountId(), instance.getSystemId(), instance.getTenantId(),
                    null, "FLOW_AI_MODEL_CALLED", "FLOW_INSTANCE",
                    String.valueOf(instance.getId()), "FAILED",
                    Map.of("nodeKey", job.getNodeKey(), "errorCode", code, "jobId", job.getId()));
        }
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
        job.setLockTime(LocalDateTime.now());
        job.setLockedBy(workerId);
        jobService.updateById(job);
        AuthenticatedContext context = systemContext(instance);
        if ("TIMER".equals(job.getJobType())) {
            Map<String, Object> payload = readMap(job.getPayloadJson());
            boolean absolute = FlowWaitTimerPolicy.ABSOLUTE.equals(string(payload.get("waitMode")));
            completeExecution(executionService.selectById(job.getExecutionId()), "TIMER_ELAPSED");
            finishJob(job, "COMPLETED", null, null);
            instance.setStatus("RUNNING");
            instance.setErrorCode(null);
            instance.setErrorMessage(null);
            instance.setUpdateTime(LocalDateTime.now());
            instanceService.updateById(instance);
            clearInstanceError(instance.getId());
            recordHistory(instance, job.getExecutionId(), null, job.getNodeKey(), "FLOW_WAIT_RESUMED",
                    absolute ? "定时器已到达，流程继续" : "等待已结束，流程继续",
                    "WAITING", "RUNNING", null, null, Map.of("jobId", job.getId(),
                            "waitMode", absolute ? FlowWaitTimerPolicy.ABSOLUTE : FlowWaitTimerPolicy.DURATION,
                            "nextRunAt", job.getNextRunTime().toString()));
            advanceFromExecution(context, instance, snapshot(instance), job.getNodeKey(),
                    variables(instance.getId()), null, job.getExecutionId());
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
        if ("MESSAGE_DELIVERY".equals(job.getJobType())) {
            runMessageDelivery(context, instance, job);
            return;
        }
        if ("WEBHOOK_DELIVERY".equals(job.getJobType())) {
            runWebhookDelivery(context, instance, job);
            return;
        }
        if ("AI_COMPLETION".equals(job.getJobType())) {
            runAiCompletion(context, instance, job);
            return;
        }
        finishJob(job, "FAILED", "FLOW_JOB_TYPE_UNSUPPORTED", "不支持的流程作业类型");
        instance.setStatus("EXCEPTION");
        instance.setErrorCode("FLOW_JOB_TYPE_UNSUPPORTED");
        instance.setErrorMessage("不支持的流程作业类型");
        instanceService.updateById(instance);
    }

    /**
     * 站内消息在独立持久化作业中投递；消息、收件人、投递结果和作业完成使用同一短事务提交。
     * 作业成功后才完成节点并继续流程，服务重启或重复扫描不会重复生成收件消息。
     */
    private void runMessageDelivery(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        Map<String, Object> definition = snapshot(instance);
        Map<String, Object> node = nodes(definition).stream()
                .filter(item -> Objects.equals(job.getNodeKey(), string(item.get("nodeKey"))))
                .findFirst().orElseThrow(() -> invalid("FLOW_MESSAGE_NODE_INVALID", "消息节点的冻结配置不存在"));
        Map<String, Object> payload = readMap(job.getPayloadJson());
        Object previousValue = payload.get("previousHandlerTenantMemberId");
        Long previousHandlerTenantMemberId = previousValue instanceof Number number ? number.longValue() : null;
        int recipientCount = deliverMessage(context, instance, node, variables(instance.getId()),
                job.getExecutionId(), previousHandlerTenantMemberId);
        FlowExecution execution = job.getExecutionId() == null ? null : executionService.selectById(job.getExecutionId());
        if (execution == null || !Objects.equals(execution.getInstanceId(), instance.getId())
                || !Objects.equals(execution.getNodeKey(), job.getNodeKey()) || !"ACTIVE".equals(execution.getStatus())) {
            throw conflict("FLOW_MESSAGE_EXECUTION_INVALID", "消息节点执行现场已变化，请读取流程最新状态");
        }
        completeExecution(execution, "MESSAGE_DELIVERED");
        finishJob(job, "COMPLETED", null, null);
        instance.setStatus("RUNNING");
        instance.setErrorCode(null);
        instance.setErrorMessage(null);
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已被其他操作更新");
        }
        clearInstanceError(instance.getId());
        String nodeType = string(node.get("nodeType"));
        recordHistory(instance, execution.getId(), null, job.getNodeKey(), "FLOW_MESSAGE_DELIVERED",
                FlowMessageNodePolicy.kindLabel(nodeType) + "已送达", "WAITING", "RUNNING", null, null,
                Map.of("jobId", job.getId(), "messageKind", nodeType, "recipientCount", recipientCount,
                        "attemptCount", job.getAttemptCount(), "deliveryStatus", "DELIVERED", "replayed", false));
        advanceFromExecution(context, instance, definition, job.getNodeKey(),
                variables(instance.getId()), null, execution.getId());
    }

    /** 回调成功、作业完成和流程推进同事务提交；重复扫描已完成作业不会再次推进实例。 */
    private void runWebhookDelivery(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        Map<String, Object> definition = snapshot(instance);
        Map<String, Object> node = nodes(definition).stream()
                .filter(item -> Objects.equals(job.getNodeKey(), string(item.get("nodeKey"))))
                .findFirst().orElseThrow(() -> invalid("FLOW_WEBHOOK_CONFIG_INVALID", "外部节点的冻结配置不存在"));
        Map<String, Object> payload = readMap(job.getPayloadJson());
        String eventId = string(payload.get("eventId"));
        String occurredTime = string(payload.get("occurredTime"));
        if (eventId.isBlank() || occurredTime.isBlank()) {
            throw invalid("FLOW_WEBHOOK_JOB_INVALID", "应用回调作业缺少稳定事件标识");
        }
        FlowWebhookDeliveryService.DeliveryResult delivered = webhookDeliveryService.deliver(
                instance.getSystemId(), instance, job.getNodeKey(), string(node.get("name")),
                map(node.get("config")), eventId, occurredTime);
        FlowExecution execution = job.getExecutionId() == null ? null : executionService.selectById(job.getExecutionId());
        if (execution == null || !Objects.equals(execution.getInstanceId(), instance.getId())
                || !Objects.equals(execution.getNodeKey(), job.getNodeKey()) || !"ACTIVE".equals(execution.getStatus())) {
            throw conflict("FLOW_WEBHOOK_EXECUTION_INVALID", "应用回调节点执行现场已变化，请读取流程最新状态");
        }
        completeExecution(execution, "WEBHOOK_DELIVERED");
        finishJob(job, "COMPLETED", null, null);
        instance.setStatus("RUNNING");
        instance.setErrorCode(null);
        instance.setErrorMessage(null);
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已被其他操作更新");
        }
        clearInstanceError(instance.getId());
        recordHistory(instance, execution.getId(), null, job.getNodeKey(), "FLOW_WEBHOOK_DELIVERED",
                "已发布应用回调投递成功", "WAITING", "RUNNING", null, null,
                Map.of("jobId", job.getId(), "applicationCode", delivered.applicationCode(),
                        "applicationVersion", delivered.applicationVersion(), "callbackId", delivered.callbackId(),
                        "responseStatus", delivered.responseStatus(), "attemptCount", job.getAttemptCount(),
                        "deliveryStatus", "DELIVERED", "replayed", false));
        advanceFromExecution(context, instance, definition, job.getNodeKey(),
                variables(instance.getId()), null, execution.getId());
    }

    /** 模型结果与 Flow 历史同事务提交；模型失败由作业异常入口记录，绝不当作自动节点成功。 */
    private void runAiCompletion(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        if (instance.getSystemId() == null) {
            throw invalid("FLOW_AI_PLATFORM_UNSUPPORTED", "平台 Flow AI 节点不能使用系统模型");
        }
        Map<String, Object> definition = snapshot(instance);
        Map<String, Object> node = nodes(definition).stream()
                .filter(item -> Objects.equals(job.getNodeKey(), string(item.get("nodeKey")))
                        && "AI".equals(string(item.get("nodeType"))))
                .findFirst().orElseThrow(() -> invalid("FLOW_AI_NODE_INVALID", "AI 节点冻结配置不存在"));
        FlowExecution execution = job.getExecutionId() == null ? null : executionService.selectById(job.getExecutionId());
        if (execution == null || !Objects.equals(execution.getInstanceId(), instance.getId())
                || !Objects.equals(execution.getNodeKey(), job.getNodeKey())
                || !"ACTIVE".equals(execution.getStatus()) || !"WAITING".equals(instance.getStatus())) {
            throw conflict("FLOW_AI_EXECUTION_INVALID", "AI 节点执行现场已变化");
        }
        Map<String, Object> config = map(node.get("config"));
        FlowAiNodePolicy.Config policy;
        try {
            policy = FlowAiNodePolicy.parse(config);
        } catch (IllegalArgumentException failure) {
            throw invalid("FLOW_AI_CONFIG_INVALID", failure.getMessage());
        }
        AiSystemModelGrant grant = aiGrants.selectById(policy.modelGrantId());
        AiModel model = grant == null ? null : aiModels.selectById(grant.getModelId());
        if (grant == null || model == null || !"ACTIVE".equals(grant.getStatus())
                || !"ACTIVE".equals(model.getStatus())
                || !Objects.equals(grant.getSystemId(), instance.getSystemId())
                || !Objects.equals(grant.getPlatformId(), instance.getPlatformId())
                || !Objects.equals(model.getId(), storedLong(config.get("modelId")))
                || !Objects.equals(model.getVersion(), number(config.get("modelConfigVersion"), -1))) {
            throw forbidden("FLOW_AI_MODEL_NOT_GRANTED", "AI 节点模型授权或发布时模型版本已失效");
        }
        // 提示模板只能展开实例标题和流程名；网关继续执行个人信息脱敏、模型外发及额度约束。
        String prompt = FlowAiNodePolicy.render(policy, Objects.toString(instance.getTitle(), ""),
                Objects.toString(definition.get("name"), ""));
        AiModelGateway.Completion completion = aiModelGateway.complete(context, policy.modelGrantId(),
                "你是流程中的受控辅助节点。只根据给定流程元数据回答，不推断或请求未提供的业务字段。",
                prompt, true, true);
        String output = completion.text();
        if (!policy.outputVariable().isEmpty()) {
            saveVariables(instance.getId(), Map.of(policy.outputVariable(), output));
        }
        completeExecution(execution, "AI_COMPLETED");
        finishJob(job, "COMPLETED", null, null);
        instance.setStatus("RUNNING");
        instance.setErrorCode(null);
        instance.setErrorMessage(null);
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程实例已变化，请重试");
        }
        clearInstanceError(instance.getId());
        resolveJobIncident(job, "AI 模型重新调用成功");
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("jobId", job.getId());
        detail.put("modelGrantId", grant.getId());
        detail.put("modelId", model.getId());
        detail.put("modelConfigVersion", config.get("modelConfigVersion"));
        detail.put("modelGrantVersionId", config.get("modelGrantVersionId"));
        detail.put("promptTokens", completion.promptTokens());
        detail.put("completionTokens", completion.completionTokens());
        detail.put("outputVariable", policy.outputVariable());
        detail.put("output", output);
        recordHistory(instance, execution.getId(), null, job.getNodeKey(), "FLOW_AI_COMPLETED",
                "AI 节点已生成结果", "WAITING", "RUNNING", null, null, detail);
        auditRecorder.recordWithPermissionSnapshot("flow-ai:" + instance.getId() + ":" + execution.getId(),
                context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                "FLOW_AI_MODEL_CALLED", "FLOW_INSTANCE", String.valueOf(instance.getId()), "SUCCESS",
                Map.of("roleIds", context.roleIds(), "permissions", context.permissions(),
                        "dataScopes", context.dataScopes()),
                Map.of("nodeKey", job.getNodeKey(), "modelGrantId", grant.getId(),
                        "modelId", model.getId(), "executionId", execution.getId(),
                        "outputLength", output.length(), "promptTokens", completion.promptTokens(),
                        "completionTokens", completion.completionTokens()));
        advanceFromExecution(context, instance, definition, job.getNodeKey(),
                variables(instance.getId()), null, execution.getId());
    }

    private void runRetryJob(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        Map<String, Object> definition = snapshot(instance);
        Map<String, Object> node = nodes(definition).stream()
                .filter(item -> Objects.equals(job.getNodeKey(), string(item.get("nodeKey"))))
                .findFirst().orElse(Map.of());
        if ("AI".equals(string(node.get("nodeType")))) {
            // 兼容既有通用 RETRY 作业：仍走受控 AI 作业入口，不能按普通自动节点空操作推进。
            runAiCompletion(context, instance, job);
            return;
        }
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
                job.setNextRunTime(LocalDateTime.now().plusMinutes(
                        Math.max(1, number(map(node.get("exceptionPolicy")).get("retryDelayMinutes"), 5))));
                job.setLastErrorCode(retryErrorCode);
                job.setLastErrorMessage(retryErrorMessage);
                job.setLockTime(null);
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
        if (FlowMessageNodePolicy.supports(string(node.get("nodeType")))
                || FlowExternalCallbackPolicy.supports(string(node.get("nodeType")))) {
            FlowExecution original = job.getExecutionId() == null ? null : executionService.selectById(job.getExecutionId());
            if (original != null && "ACTIVE".equals(original.getStatus())) {
                completeExecution(original, "MESSAGE_DELIVERED_AFTER_RETRY");
            }
        }
        FlowExecution retryExecution = recordExecution(
                instance, job.getNodeKey(), "RETRY", "ACTIVE", null, job.getExecutionId());
        completeExecution(retryExecution, "RETRY_SUCCEEDED");
        finishJob(job, "COMPLETED", null, null);
        instance.setStatus("RUNNING");
        instance.setErrorCode(null);
        instance.setErrorMessage(null);
        instance.setUpdateTime(LocalDateTime.now());
        instanceService.updateById(instance);
        clearInstanceError(instance.getId());
        recordHistory(instance, retryExecution.getId(), null, job.getNodeKey(), "JOB", "自动重试成功",
                "WAITING", "RUNNING", null, null, Map.of("jobId", job.getId()));
        advanceFromExecution(context, instance, definition, job.getNodeKey(),
                variables(instance.getId()), null, retryExecution.getId());
    }

    private void replayAutomaticNode(
            AuthenticatedContext context, FlowInstance instance, Map<String, Object> node,
            Map<String, Object> config, Map<String, Object> variables, FlowJob job) {
        String nodeType = string(node.get("nodeType"));
        if (Set.of(FlowAutomaticActionPolicy.UPDATE_FIELD, FlowAutomaticActionPolicy.DATA_ACTION).contains(nodeType)) {
            executeAutomaticBusinessNode(context, instance, node, config, variables,
                    job.getExecutionId(), true);
            return;
        }
        if (FlowMessageNodePolicy.supports(nodeType)) {
            int recipientCount = deliverMessage(context, instance, node, variables, job.getExecutionId(), null);
            recordHistory(instance, job.getExecutionId(), null, job.getNodeKey(), "FLOW_MESSAGE_DELIVERED",
                    FlowMessageNodePolicy.kindLabel(nodeType) + "重试后已送达", "WAITING", "WAITING", null, null,
                    Map.of("jobId", job.getId(), "messageKind", nodeType, "recipientCount", recipientCount,
                            "attemptCount", job.getAttemptCount(), "deliveryStatus", "DELIVERED", "replayed", true));
            return;
        }
        if (FlowExternalCallbackPolicy.supports(nodeType)) {
            FlowJob original = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                            .eq(FlowJob::getInstanceId, instance.getId())
                            .eq(FlowJob::getNodeKey, job.getNodeKey())
                            .eq(FlowJob::getJobType, "WEBHOOK_DELIVERY")
                            .orderByDesc(FlowJob::getId).last("limit 1"))
                    .stream().findFirst().orElse(null);
            Map<String, Object> originalPayload = original == null ? Map.of() : readMap(original.getPayloadJson());
            String eventId = string(originalPayload.get("eventId"));
            if (eventId.isBlank()) eventId = "flow-webhook:" + instance.getId() + ":" + job.getExecutionId();
            String occurredTime = string(originalPayload.get("occurredTime"));
            if (occurredTime.isBlank()) occurredTime = instance.getStartTime().toString();
            FlowWebhookDeliveryService.DeliveryResult delivered = webhookDeliveryService.deliver(
                    instance.getSystemId(), instance, job.getNodeKey(), string(node.get("name")), config,
                    eventId, occurredTime);
            recordHistory(instance, job.getExecutionId(), null, job.getNodeKey(), "FLOW_WEBHOOK_DELIVERED",
                    "应用回调重试后投递成功", "WAITING", "WAITING", null, null,
                    Map.of("jobId", job.getId(), "applicationCode", delivered.applicationCode(),
                            "applicationVersion", delivered.applicationVersion(), "callbackId", delivered.callbackId(),
                            "responseStatus", delivered.responseStatus(), "attemptCount", job.getAttemptCount(),
                            "deliveryStatus", "DELIVERED", "replayed", true));
        }
    }

    /** 解析冻结接收人并调用现有消息中心，账号、通道和目标地址均不接受节点自定义输入。 */
    private int deliverMessage(
            AuthenticatedContext context, FlowInstance instance, Map<String, Object> node,
            Map<String, Object> variables, Long executionId, Long previousHandlerTenantMemberId) {
        String nodeType = string(node.get("nodeType"));
        FlowMessageNodePolicy.Config policy;
        try {
            policy = FlowMessageNodePolicy.parse(nodeType, map(node.get("config")));
        } catch (IllegalArgumentException malformed) {
            throw invalid("FLOW_MESSAGE_NODE_INVALID", malformed.getMessage());
        }
        List<Long> recipientIds;
        if (context.systemId() == null && map(node.get("assigneePolicy")).isEmpty()) {
            recipientIds = List.of(instance.getStartedByAccountId());
        } else {
            List<FlowParticipantResolver.ResolvedPerson> people = participantResolver.resolve(
                    participantContext(context, instance), map(node.get("assigneePolicy")), instance, variables,
                    previousHandlerTenantMemberId);
            if (people.isEmpty()) {
                throw invalid("FLOW_MESSAGE_RECIPIENT_UNRESOLVED",
                        FlowMessageNodePolicy.kindLabel(nodeType) + "节点没有可解析的当前有效接收人");
            }
            recipientIds = people.stream().map(FlowParticipantResolver.ResolvedPerson::accountId).distinct().toList();
        }
        String subject = policy.legacy()
                ? notificationText(node, policy.subject(), instance, variables) : policy.subject();
        String content = policy.legacy()
                ? notificationText(node, policy.content(), instance, variables) : policy.content();
        if (!policy.legacy() && policy.appendInstanceTitle() && instance.getTitle() != null
                && !instance.getTitle().isBlank()) subject = subject + "：" + instance.getTitle();
        messageService.notifyFlowEvent(participantContext(context, instance), instance.getId(),
                "instance-" + instance.getId() + "-message-" + executionId,
                subject, content, recipientIds);
        return recipientIds.size();
    }

    /** 自动字段和数据动作共用同一持久化动作入口，结果只公开记录引用和字段名称。 */
    private void executeAutomaticBusinessNode(
            AuthenticatedContext context, FlowInstance instance, Map<String, Object> node,
            Map<String, Object> config, Map<String, Object> variables, Long executionId, boolean retry) {
        String nodeType = string(node.get("nodeType"));
        FlowAutomaticActionPolicy.Config policy;
        try {
            policy = FlowAutomaticActionPolicy.parse(nodeType, config);
        } catch (IllegalArgumentException malformed) {
            throw invalid("FLOW_AUTOMATIC_ACTION_INVALID", malformed.getMessage());
        }
        BusinessAction.Options options = automaticActionOptions(instance, executionId);
        String traceId = "flow-node-" + instance.getId() + "-" + executionId;
        if (policy.legacy()) {
            String moduleCode = moduleCode(instance.getBusinessType());
            Long recordId = numericBusinessId(instance.getBusinessId());
            if (moduleCode == null || recordId == null) {
                throw invalid("FLOW_WRITEBACK_BUSINESS_REQUIRED", "字段更新节点要求实例关联模块业务记录");
            }
            BusinessAction.Result<com.unique.unexamine.runtimedata.manage.RuntimeRecordView> result =
                    runtimeDataService.applyFlowWritebackAction(context, moduleCode, recordId,
                            string(config.get("businessAction")),
                            blankToNull(string(config.get("expectedCurrentStatus"))),
                            blankToNull(string(config.get("targetStatus"))),
                            resolveWritebacks(map(config.get("fieldUpdates")), variables), options, traceId);
            recordAutomaticHistory(instance, executionId, string(node.get("nodeKey")), retry,
                    "UPDATE", moduleCode, null, recordId,
                    policy.assignments().stream().map(FlowAutomaticActionPolicy.Assignment::fieldCode).toList(), result);
            return;
        }
        if (policy.moduleVersionId() == null || policy.moduleCode().isBlank()
                || policy.fieldBindings().size() != policy.assignments().size()) {
            throw invalid("FLOW_AUTOMATIC_SNAPSHOT_INVALID", "自动业务节点缺少已发布模块和字段快照");
        }
        Long recordId = resolveAutomaticRecordId(instance, policy, variables);
        Map<String, Object> values;
        String title;
        try {
            values = policy.assignmentValues(variables);
            title = policy.resolvedTitle(variables);
        } catch (IllegalArgumentException missing) {
            throw invalid("FLOW_AUTOMATIC_VARIABLE_MISSING", missing.getMessage());
        }
        BusinessAction.Result<com.unique.unexamine.runtimedata.manage.RuntimeRecordView> result =
                runtimeDataService.applyFlowAutomaticAction(context, policy.operation(), policy.moduleCode(), recordId,
                        title, values, policy.moduleVersionId(), policy.fieldBindings(), options, traceId);
        var saved = result.value();
        if (saved == null) throw invalid("FLOW_AUTOMATIC_RESULT_MISSING", "自动业务动作没有返回业务记录");
        Map<String, Object> nodeResult = Map.of(
                "node." + string(node.get("nodeKey")) + ".recordId", saved.id(),
                "node." + string(node.get("nodeKey")) + ".actionId", result.idempotencyRecordId());
        saveVariables(instance.getId(), nodeResult);
        // 同一次自动推进会继续复用内存中的变量；同步节点结果，后续更新节点才能立即引用本次新建记录。
        variables.putAll(nodeResult);
        recordAutomaticHistory(instance, executionId, string(node.get("nodeKey")), retry,
                policy.operation(), policy.moduleCode(), policy.moduleName(), saved.id(),
                policy.fieldBindings().stream().map(FlowAutomaticActionPolicy.FieldBinding::name).toList(), result);
    }

    private Long resolveAutomaticRecordId(FlowInstance instance, FlowAutomaticActionPolicy.Config policy,
                                          Map<String, Object> variables) {
        if ("CREATE".equals(policy.operation())) return null;
        Object raw;
        if ("CURRENT_RECORD".equals(policy.targetRecordSource())) {
            if (!Objects.equals(policy.moduleCode(), moduleCode(instance.getBusinessType()))) {
                throw invalid("FLOW_AUTOMATIC_CURRENT_MODULE_INVALID", "当前业务记录与自动更新目标模块不一致");
            }
            raw = instance.getBusinessId();
        } else if ("BUSINESS_FIELD".equals(policy.targetRecordSource())) {
            raw = variables.get(policy.recordIdField());
        } else {
            raw = variables.get("node." + policy.recordNodeKey() + ".recordId");
        }
        Long recordId = numericBusinessId(raw == null ? null : String.valueOf(raw));
        if (recordId == null) throw invalid("FLOW_AUTOMATIC_RECORD_REQUIRED", "更新数据节点没有得到有效目标记录");
        return recordId;
    }

    private BusinessAction.Options automaticActionOptions(FlowInstance instance, Long executionId) {
        if (executionId == null) throw invalid("FLOW_AUTOMATIC_EXECUTION_REQUIRED", "自动业务节点缺少执行游标");
        Map<String, Object> origin = readMap(instance.getBusinessSnapshotJson());
        int depth = number(origin.get("depth"), 0) + 1;
        String causation = blankToNull(string(origin.get("causationId")));
        if (causation == null) causation = "flow-instance:" + instance.getId();
        return new BusinessAction.Options("flow-node:" + instance.getId() + ":" + executionId,
                "FLOW", "flow-runtime", causation, depth);
    }

    private void recordAutomaticHistory(
            FlowInstance instance, Long executionId, String nodeKey, boolean retry,
            String operation, String moduleCode, String moduleName, Long recordId, List<String> fields,
            BusinessAction.Result<?> result) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("operation", operation);
        detail.put("moduleCode", moduleCode);
        if (moduleName != null && !moduleName.isBlank()) detail.put("moduleName", moduleName);
        detail.put("recordId", recordId);
        detail.put("fieldNames", fields);
        detail.put("actionId", result.idempotencyRecordId());
        detail.put("replayed", result.replayed());
        detail.put("resultStatus", result.status());
        recordHistory(instance, executionId, null, nodeKey, "BUSINESS_DATA_ACTION",
                retry ? "自动业务动作重试完成" : "自动业务动作已完成",
                retry ? "FAILED" : null, "COMPLETED", null, instance.getStartedByTenantMemberId(), detail);
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

    /** 到期作业固定使用任务创建时的策略；自动处理不冒用候选人身份，也不提交字段补丁。 */
    private void runTaskTimeout(AuthenticatedContext context, FlowInstance instance, FlowJob job) {
        Long taskId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("taskId")));
        FlowTask task = taskId == null ? null : taskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getId, taskId).last("FOR UPDATE")).stream().findFirst().orElse(null);
        if (task == null || !Objects.equals(task.getInstanceId(), instance.getId()) || !"PENDING".equals(task.getStatus())
                || task.getCompleteTime() != null || task.getDueTime() == null || "EXCEPTION".equals(instance.getStatus())) {
            finishJob(job, "CANCELLED", null, null);
            return;
        }
        // 未到真实任务时限不能仅凭旧作业的调度时间处理；人工转交保留或取消时限后也走当前事实。
        if (task.getDueTime().isAfter(LocalDateTime.now())) {
            job.setNextRunTime(task.getDueTime());
            jobService.updateById(job);
            finishJob(job, "PENDING", null, null);
            return;
        }
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        Map<String, Object> timeoutPolicy = map(taskSnapshot.get("timeoutPolicy"));
        String action = string(timeoutPolicy.getOrDefault("action", "INCIDENT")).toUpperCase(Locale.ROOT);
        AuthenticatedContext directory = participantContext(context, instance);
        if ("REMIND".equals(action)) {
            List<Long> recipients = currentTaskRecipients(directory, task, taskSnapshot);
            if (recipients.isEmpty()) throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "超时提醒没有有效处理人，请人工处理");
            messageService.notifyFlowEvent(directory, instance.getId(), "timeout-job-" + job.getId() + "-reminder",
                    "审批超时提醒：" + instance.getTitle(), "当前待办已超过处理时限，请打开流程处理；提醒不会自动同意或拒绝。", recipients);
            finishJob(job, "COMPLETED", null, null);
            recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                    "处理超时，已提醒当前处理人（任务继续待处理）", "PENDING", "PENDING", null, null, Map.of("jobId", job.getId()));
            return;
        }
        if ("TRANSFER".equals(action) || "ESCALATE_MANAGER".equals(action)) {
            FlowParticipantResolver.ResolvedPerson target;
            if ("TRANSFER".equals(action)) {
                boolean platform = instance.getSystemId() == null;
                Long selected = timeoutIdentity(timeoutPolicy.get(platform ? "targetPlatformAccountId" : "targetTenantMemberId"));
                if (timeoutPolicy.get(platform ? "targetTenantMemberId" : "targetPlatformAccountId") != null) {
                    throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "超时转交不能使用其他身份范围的人员");
                }
                target = platform ? participantResolver.requirePlatformPerson(directory, selected)
                        : participantResolver.requirePerson(directory, selected);
            } else {
                if (instance.getSystemId() == null) throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "平台流程暂不支持直属上级来源，请指定平台成员");
                List<FlowParticipantResolver.ResolvedPerson> managers = participantResolver.resolve(directory,
                        Map.of("type", "MANAGER", "source", "PREVIOUS_HANDLER"), instance,
                        variables(instance.getId()), task.getAssigneeTenantMemberId());
                if (managers.isEmpty()) throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "当前处理人没有有效直属上级，请人工处理");
                target = managers.getFirst();
            }
            if (Objects.equals(target.accountId(), task.getAssigneeAccountId())) {
                throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "超时转交目标仍是当前处理人，请人工处理");
            }
            task.setAssigneeTenantMemberId(target.tenantMemberId());
            task.setAssigneeAccountId(target.accountId());
            Map<String, Object> assigned = new LinkedHashMap<>(taskSnapshot);
            assigned.put("policyType", "PERSON");
            assigned.put("resolvedPeople", List.of(personSnapshot(target)));
            assigned.put("timeoutTransferred", true);
            assigned.remove("claimActorAccountId"); assigned.remove("claimActorTenantMemberId");
            task.setClaimTime(null);
            task.setAssigneeSnapshotJson(toJson(assigned));
            task.setDueTime(null);
            task.setUpdateTime(LocalDateTime.now());
            updateTimeoutTask(task);
            // Base 的非空更新策略不会清空旧截止时间，此处只对已锁定任务显式清空，不修改其他任务。
            stateRepository.clearTimeoutAndClaim(task);
            addCandidate(task.getId(), target, "处理超时，自动转交指定处理人");
            finishJob(job, "COMPLETED", null, null);
            recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                    "处理超时，已自动转交指定处理人（不再重复超时转交）", "PENDING", "PENDING", null, null,
                    Map.of("jobId", job.getId(), "assignee", personSnapshot(target)));
            messageService.notifyFlowEvent(directory, instance.getId(), "timeout-job-" + job.getId() + "-transfer",
                    "超时审批已转交：" + instance.getTitle(), "超时待办已转交给你，请打开流程处理；本次转交取消原处理时限。",
                    List.of(target.accountId()));
            return;
        }
        if ("AUTO_APPROVE".equals(action)) {
            validateTimeoutApproval(context, instance, task);
            task.setStatus("APPROVE");
            task.setCompleteTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            updateTimeoutTask(task);
            finishJob(job, "COMPLETED", null, null);
            recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                    "处理超时，按冻结策略自动同意（未代填或修改表单）", "PENDING", "APPROVE", null, null, Map.of("jobId", job.getId()));
            if (!activateNextApprovalTaskOrWait(instance, task, taskSnapshot)) {
                cancelSiblingApprovalTasks(instance.getId(), task);
                FlowExecution timedExecution = taskExecution(task);
                if (timedExecution == null || !"ACTIVE".equals(timedExecution.getStatus())) {
                    throw conflict("FLOW_TASK_EXECUTION_MISSING", "超时任务对应的执行记录已变化，请重新调度");
                }
                completeExecution(timedExecution, "TIMEOUT_AUTO_APPROVE");
                instance.setStatus("RUNNING");
                if (instanceService.updateById(instance) != 1) throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已被其他操作更新");
                advanceFromExecution(context, instance, snapshot(instance), task.getNodeKey(),
                        variables(instance.getId()), task.getAssigneeTenantMemberId(), timedExecution.getId());
            }
            return;
        }
        if ("AUTO_REJECT".equals(action)) {
            task.setStatus("REJECT");
            task.setCompleteTime(LocalDateTime.now());
            task.setUpdateTime(LocalDateTime.now());
            updateTimeoutTask(task);
            FlowExecution timedExecution = taskExecution(task);
            if (timedExecution == null || !"ACTIVE".equals(timedExecution.getStatus())) {
                throw conflict("FLOW_TASK_EXECUTION_MISSING", "超时任务对应的执行记录已变化，请重新调度");
            }
            completeExecution(timedExecution, "TIMEOUT_AUTO_REJECT");
            instance.setStatus("REJECTED");
            cancelUnfinishedTasks(instance);
            instance.setFinishTime(LocalDateTime.now());
            if (instanceService.updateById(instance) != 1) throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已被其他操作更新");
            finishJob(job, "COMPLETED", null, null);
            recordHistory(instance, job.getExecutionId(), task.getId(), task.getNodeKey(), "TASK",
                    "处理超时，按冻结策略自动拒绝；原因：超过配置处理时限", "PENDING", "REJECTED", null, null, Map.of("jobId", job.getId()));
            cancelPendingJobs(instance.getId(), null);
            cancelActiveExecutions(instance.getId(), "TIMEOUT_AUTO_REJECT");
            terminateActiveSubflows(instance);
            messageService.notifyFlowEvent(directory, instance.getId(), "timeout-job-" + job.getId() + "-reject",
                    "审批超时已拒绝：" + instance.getTitle(), "流程已按发布时配置自动拒绝，原业务操作不会生效；原因：超过处理时限。",
                    List.of(instance.getStartedByAccountId()));
            failParentForTerminalSubflow(instance);
            publishFlowEvent(context, instance, LocalBusinessEvent.Type.FLOW_REJECTED);
            return;
        }
        if (!"INCIDENT".equals(action)) throw invalid("FLOW_TIMEOUT_POLICY_INVALID", "冻结超时策略无效，请人工处理");
        throw invalid("FLOW_TASK_TIMEOUT", "审批任务超时，需要人工处理");
    }

    /** 只核对可信业务候选；未知业务表单、填写任务及手动映射不能被自动审批跳过。 */
    private void validateTimeoutApproval(AuthenticatedContext actor, FlowInstance instance, FlowTask task) {
        Map<String, Object> taskSnapshot = readMap(task.getAssigneeSnapshotJson());
        Map<String, Object> policy = taskFieldPolicy(instance, task);
        if ("FORM_TASK".equals(task.getTaskType()) || Boolean.TRUE.equals(taskSnapshot.get("manual"))) {
            throw invalid("FLOW_NODE_FORM_NOT_AVAILABLE", "填写或手动节点不能由超时作业代替提交");
        }
        var origin = actionCoordinator.formOriginForFlow(instance);
        if (origin == null) {
            var bound = actionCoordinator.businessReadForFlow(instance);
            if (bound != null) runtimeDataService.authorizeActionProgress(actor, bound.input(), bound.recordId(), "timeout-" + task.getId());
            if (policy.get("editable") instanceof List<?> editable && !editable.isEmpty()
                    || policy.get("required") instanceof List<?> required && !required.isEmpty()) {
                throw invalid("FLOW_NODE_FORM_NOT_AVAILABLE", "当前业务来源尚未接通可信候选校验，不能超时自动同意");
            }
            return;
        }
        runtimeDataService.authorizeActionProgress(actor, origin.input(), null, "timeout-" + task.getId());
        if (!supportedFormOrigin(origin)) {
            if (policy.get("editable") instanceof List<?> editable && !editable.isEmpty()
                    || policy.get("required") instanceof List<?> required && !required.isEmpty()) {
                throw invalid("FLOW_NODE_FORM_NOT_AVAILABLE", "该动作尚未接通节点候选校验，不能超时自动同意");
            }
            return;
        }
        runtimeDataService.validateAutomaticApproval(actor, formSubmissions.mergeForExecution(origin.input(), instance),
                origin.configurationSnapshot(), policy, "timeout-" + task.getId());
    }

    private void updateTimeoutTask(FlowTask task) {
        if (taskService.updateById(task) != 1) throw conflict("FLOW_TASK_ALREADY_HANDLED", "任务已由其他操作处理");
    }

    /** 固定处理人优先于历史候选；无固定人时只通知快照中当前仍有效的同域候选。 */
    private List<Long> currentTaskRecipients(AuthenticatedContext directory, FlowTask task, Map<String, Object> taskSnapshot) {
        Set<Long> ids = new LinkedHashSet<>();
        if (directory.systemId() == null && task.getAssigneeAccountId() != null) {
            ids.add(participantResolver.requirePlatformPerson(directory, task.getAssigneeAccountId()).accountId());
        }
        if (directory.systemId() != null && task.getAssigneeTenantMemberId() != null) {
            ids.add(participantResolver.requirePerson(directory, task.getAssigneeTenantMemberId()).accountId());
        }
        Map<String, Object> delegation = map(taskSnapshot.get("delegation"));
        if (activeDelegation(delegation, LocalDateTime.now())) {
            Map<String, Object> delegate = map(delegation.get("delegate"));
            try {
                FlowParticipantResolver.ResolvedPerson person = directory.systemId() == null
                        ? participantResolver.requirePlatformPerson(directory, storedLong(delegate.get("accountId")))
                        : participantResolver.requirePerson(directory, storedLong(delegate.get("tenantMemberId")));
                ids.add(person.accountId());
            } catch (DomainException invalidDelegate) {
                // 代理人停用后不再收提醒，任务仍保留给原审批人。
            }
        }
        if (!ids.isEmpty()) return List.copyOf(ids);
        if (taskSnapshot.get("resolvedPeople") instanceof List<?> people) for (Object value : people) {
            Map<String, Object> person = map(value);
            try {
                var resolved = directory.systemId() == null
                        ? participantResolver.requirePlatformPerson(directory, timeoutIdentity(person.get("accountId")))
                        : participantResolver.requirePerson(directory, timeoutIdentity(person.get("tenantMemberId")));
                ids.add(resolved.accountId());
            } catch (DomainException invalidPerson) {
                // 停用候选不收提醒；全部候选失效则交人工异常，不自动通过。
            }
        }
        return List.copyOf(ids);
    }

    private Long timeoutIdentity(Object value) {
        if (value == null || !String.valueOf(value).matches("[1-9][0-9]*")) throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "请选择有效的超时转交成员");
        try { return Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException invalidId) { throw invalid("FLOW_TIMEOUT_TARGET_INVALID", "超时转交成员标识无效"); }
    }

    /** 新键明确为空代表关闭时限，不允许回退到旧 minutes 意外启用。 */
    private int timeoutMinutes(Map<String, Object> policy) {
        return number(policy.containsKey("timeoutMinutes") ? policy.get("timeoutMinutes") : policy.get("minutes"), 0);
    }

    private void resolveJobIncident(FlowJob job, String comment) {
        Long incidentId = numericBusinessId(String.valueOf(readMap(job.getPayloadJson()).get("exceptionId")));
        FlowException incident = incidentId == null ? null : exceptionService.selectById(incidentId);
        if (incident == null || "RESOLVED".equals(incident.getStatus())) return;
        incident.setStatus("RESOLVED");
        incident.setResolutionComment(comment);
        incident.setResolveTime(LocalDateTime.now());
        exceptionService.updateById(incident);
    }

    private void finishJob(FlowJob job, String status, String errorCode, String errorMessage) {
        job.setStatus(status);
        job.setLastErrorCode(errorCode);
        job.setLastErrorMessage(errorMessage);
        job.setCompleteTime(Set.of("COMPLETED", "FAILED", "CANCELLED").contains(status)
                ? LocalDateTime.now() : null);
        job.setLockTime(null);
        job.setLockedBy(null);
        jobService.updateById(job);
        if (errorCode == null && errorMessage == null) {
            stateRepository.clearFinishedJobMetadata(job);
        }
    }

    /** 人工事项完成即取消其到期作业；终态取消全部未执行作业，页面和调度都不能保留无效时限。 */
    private void cancelPendingJobs(Long instanceId, Long taskId) {
        // 所有调用点均已锁定实例；只清理本实例的未执行行，不改已完成作业及历史失败事实。
        stateRepository.cancelPendingJobs(instanceId, taskId);
    }

    private void clearInstanceError(Long instanceId) {
        stateRepository.clearInstanceError(instanceId);
    }

    /** 发起、完成和拒绝共用同一安全事件事实；模块外流程不伪造模块编码。 */
    private void publishFlowEvent(AuthenticatedContext actor, FlowInstance instance, LocalBusinessEvent.Type type) {
        String businessType = instance.getBusinessType();
        String moduleCode = businessType != null && businessType.startsWith("MODULE:")
                ? businessType.substring("MODULE:".length()) : null;
        localEvents.publish(actor, instance.getTenantId(), type,
                "FLOW_INSTANCE", instance.getId().toString(), moduleCode);
    }

    private AuthenticatedContext systemContext(FlowInstance instance) {
        boolean bound = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId()).eq(FlowAction::getActionCode, "START"))
                .stream().anyMatch(action -> action.getIdempotencyKey().startsWith("business-action:"));
        if (bound) return actionCoordinator.actorForBoundFlow(instance);
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
        AuthenticatedContext restored = new AuthenticatedContext(null, instance.getStartedByAccountId(), instance.getPlatformId(),
                instance.getSystemId(), instance.getTenantId(),
                member == null ? platformMember == null ? null : platformMember.getId() : member.getId(),
                instance.getStartedByTenantMemberId(), "flow-job", "流程作业", "SYSTEM",
                permissions.roleIds(), permissions.permissions(), permissions.dataScopes());
        // 独立实例同样只恢复当前有效发起人；业务绑定实例已在 coordinator 核验原身份。
        if (instance.getSystemId() == null) participantResolver.requirePlatformPerson(restored, instance.getStartedByAccountId());
        else participantResolver.requirePerson(restored, instance.getStartedByTenantMemberId());
        return restored;
    }

    /** 只供审批人/组织目录和来源通知使用；不携带角色权限，不能传入业务执行器。 */
    private AuthenticatedContext participantContext(AuthenticatedContext actor, FlowInstance instance) {
        return new AuthenticatedContext(null, actor.accountId(), instance.getPlatformId(), instance.getSystemId(),
                instance.getTenantId(), actor.memberId(), instance.getStartedByTenantMemberId(), actor.username(),
                actor.displayName(), actor.mfaLevel(), List.of(), List.of(), Map.of());
    }

    private void advance(AuthenticatedContext context, FlowInstance instance, Map<String, Object> snapshot,
                         String sourceNodeKey, Map<String, Object> variables,
                         Long previousHandlerTenantMemberId) {
        advancePath(context, instance, snapshot, sourceNodeKey, variables, previousHandlerTenantMemberId, null, null);
    }

    /** 前插直接进入新增节点；正常后续仍统一经过条件选择和暂停组恢复。 */
    private void advance(AuthenticatedContext context, FlowInstance instance, Map<String, Object> snapshot,
                         String sourceNodeKey, Map<String, Object> variables,
                         Long previousHandlerTenantMemberId, String firstNodeKey) {
        advancePath(context, instance, snapshot, sourceNodeKey, variables, previousHandlerTenantMemberId, firstNodeKey, null);
    }

    /** 正常续跑携带真实父执行，分支任务处理后才能回溯到所属拆分网关。 */
    /** 启动冻结版本的子流程；父执行保持 ACTIVE，直到对应子实例成功完成或明确失败。 */
    private void startSubflow(AuthenticatedContext context, FlowInstance parent, FlowExecution parentExecution,
                              Map<String, Object> node, Map<String, Object> parentVariables) {
        Map<String, Object> config = map(node.get("config"));
        FlowSubflowPolicy.Config policy;
        try {
            policy = FlowSubflowPolicy.parse(config);
        } catch (IllegalArgumentException invalid) {
            fail(parent, parentExecution.getNodeKey(), node, "SUBFLOW", "FLOW_SUBFLOW_CONFIG_INVALID", invalid.getMessage());
            return;
        }
        Long versionId = storedLong(config.get(FlowSubflowPolicy.FLOW_VERSION_ID));
        String definitionHash = string(config.get(FlowSubflowPolicy.DEFINITION_HASH));
        FlowVersion version = versionId == null ? null : versionService.selectById(versionId);
        Map<String, Object> childSnapshot = version == null ? Map.of() : readMap(version.getSnapshotJson());
        if (policy.flowId() == null || version == null || !Objects.equals(version.getFlowId(), policy.flowId())
                || !Objects.equals(version.getDefinitionHash(), definitionHash)
                || !Objects.equals(childSnapshot.get("contextType"), parent.getContextType())
                || !Objects.equals(storedLong(childSnapshot.get("platformId")), parent.getPlatformId())
                || !Objects.equals(storedLong(childSnapshot.get("systemId")), parent.getSystemId())
                || !Objects.equals(storedLong(childSnapshot.get("tenantId")), parent.getTenantId())) {
            fail(parent, parentExecution.getNodeKey(), node, "SUBFLOW", "FLOW_SUBFLOW_VERSION_INVALID",
                    "子流程冻结版本不存在或不属于父流程的当前作用域");
            return;
        }
        if (subflowDepth(parent) >= 20) {
            fail(parent, parentExecution.getNodeKey(), node, "SUBFLOW", "FLOW_SUBFLOW_DEPTH_EXCEEDED",
                    "子流程调用层级超过 20 层，已停止继续启动");
            return;
        }
        Map<String, Object> childVariables;
        try {
            childVariables = FlowSubflowPolicy.childVariables(policy, parentVariables);
        } catch (IllegalArgumentException missingVariable) {
            fail(parent, parentExecution.getNodeKey(), node, "SUBFLOW", "FLOW_SUBFLOW_INPUT_MISSING",
                    missingVariable.getMessage());
            return;
        }
        FlowAction existing = findAction(parent.getId(), "SUBFLOW_START:" + parentExecution.getId());
        if (existing != null) return;
        parent.setStatus("WAITING");
        parent.setCurrentNodeKey(parentExecution.getNodeKey());
        parent.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(parent) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "父流程状态已变化，未重复启动子流程");
        }
        String childName = string(config.get(FlowSubflowPolicy.FLOW_NAME));
        String title = parent.getTitle() + " / " + (childName.isBlank() ? "子流程" : childName);
        if (title.length() > 500) title = title.substring(0, 500);
        FlowRuntimeModels.StartRequest request = new FlowRuntimeModels.StartRequest(policy.flowId(), title,
                parent.getBusinessType(), parent.getBusinessId(), readMap(parent.getBusinessSnapshotJson()),
                childVariables, "subflow:" + parent.getId() + ":" + parentExecution.getId());
        startVersion(context, request, "subflow-" + parent.getId() + "-" + parentExecution.getId(),
                version, parent.getTenantId(),
                new SubflowParentLink(parent.getId(), parentExecution.getId(), parentExecution.getNodeKey()));
    }

    /** 子实例到达结束节点后，按父实例锁、父执行和幂等动作只恢复一次。 */
    private void resumeParentAfterSubflow(FlowInstance child) {
        FlowAction relationAction = subflowParentAction(child.getId());
        if (relationAction == null || !"COMPLETED".equals(child.getStatus())) return;
        Map<String, Object> relation = readMap(relationAction.getInputJson());
        Long parentId = storedLong(relation.get("parentInstanceId"));
        Long executionId = storedLong(relation.get("parentExecutionId"));
        if (parentId == null || executionId == null) return;
        FlowInstance parent = lockInstance(parentId);
        FlowExecution parentExecution = executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                .eq(FlowExecution::getId, executionId).last("FOR UPDATE")).stream().findFirst().orElse(null);
        if (parent == null || parentExecution == null
                || !Objects.equals(parentExecution.getInstanceId(), parentId)
                || !"ACTIVE".equals(parentExecution.getStatus())
                || !ACTIVE_INSTANCE_STATUSES.contains(parent.getStatus())) return;
        String resumeKey = "SUBFLOW_RESUME:" + executionId + ":" + child.getId();
        if (findAction(parentId, resumeKey) != null) return;
        Map<String, Object> node = subflowNode(parent, parentExecution.getNodeKey());
        FlowSubflowPolicy.Config policy;
        Map<String, Object> outputs;
        try {
            policy = FlowSubflowPolicy.parse(map(node.get("config")));
            outputs = FlowSubflowPolicy.parentOutputs(policy, variables(child.getId()));
        } catch (IllegalArgumentException invalidOutput) {
            fail(parent, parentExecution.getNodeKey(), node, "SUBFLOW", "FLOW_SUBFLOW_OUTPUT_MISSING",
                    invalidOutput.getMessage());
            return;
        }
        if (!outputs.isEmpty()) saveVariables(parentId, outputs);
        completeExecution(parentExecution, "SUBFLOW_COMPLETED");
        parent.setStatus("RUNNING");
        parent.setCurrentNodeKey(parentExecution.getNodeKey());
        parent.setErrorCode(null);
        parent.setErrorMessage(null);
        parent.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(parent) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "父流程状态已变化，子流程完成结果未重复应用");
        }
        clearInstanceError(parentId);
        insertAction(parentId, null, parentExecution.getNodeKey(), "SUBFLOW_COMPLETED", null,
                Map.of("childInstanceId", child.getId(), "childFlowId", child.getFlowId(),
                        "childFlowVersionId", child.getFlowVersionId(), "outputCount", outputs.size()),
                result(parent), resumeKey, parent.getStartedByAccountId());
        recordHistory(parent, parentExecution.getId(), null, parentExecution.getNodeKey(), "SUBFLOW_COMPLETED",
                "子流程已完成，父流程继续", "WAITING", "RUNNING", null, null,
                Map.of("childInstanceId", child.getId(), "childFlowId", child.getFlowId(),
                        "childFlowVersionId", child.getFlowVersionId(), "status", child.getStatus(),
                        "outputCount", outputs.size()));
        advanceFromExecution(systemContext(parent), parent, snapshot(parent), parentExecution.getNodeKey(),
                variables(parentId), null, parentExecution.getId());
    }

    /** 子流程异常保持父流程可见等待；修复子实例后仍由同一父执行继续。 */
    private void notifyParentSubflowException(FlowInstance child, String errorCode) {
        FlowAction relationAction = subflowParentAction(child.getId());
        if (relationAction == null) return;
        Map<String, Object> relation = readMap(relationAction.getInputJson());
        Long parentId = storedLong(relation.get("parentInstanceId"));
        Long executionId = storedLong(relation.get("parentExecutionId"));
        if (parentId == null || executionId == null) return;
        FlowInstance parent = lockInstance(parentId);
        FlowExecution parentExecution = executionService.selectById(executionId);
        if (parent == null || parentExecution == null || !"ACTIVE".equals(parentExecution.getStatus())
                || !ACTIVE_INSTANCE_STATUSES.contains(parent.getStatus())) return;
        String key = "SUBFLOW_CHILD_EXCEPTION:" + executionId + ":" + child.getId() + ":" + errorCode;
        if (findAction(parentId, key) != null) return;
        insertAction(parentId, null, parentExecution.getNodeKey(), "SUBFLOW_CHILD_EXCEPTION", null,
                Map.of("childInstanceId", child.getId(), "childFlowId", child.getFlowId(),
                        "childFlowVersionId", child.getFlowVersionId(), "status", child.getStatus(),
                        "errorCode", Objects.requireNonNullElse(errorCode, "FLOW_SUBFLOW_CHILD_EXCEPTION")),
                result(parent), key, parent.getStartedByAccountId());
        recordHistory(parent, parentExecution.getId(), null, parentExecution.getNodeKey(), "SUBFLOW_CHILD_EXCEPTION",
                "子流程发生异常，父流程保持等待", "WAITING", "WAITING", null, null,
                Map.of("childInstanceId", child.getId(), "childFlowId", child.getFlowId(),
                        "status", child.getStatus(),
                        "errorCode", Objects.requireNonNullElse(errorCode, "FLOW_SUBFLOW_CHILD_EXCEPTION")));
    }

    /** 子流程不可恢复的终态转成父流程异常，管理员可以明确跳过后继续，不能静默卡住。 */
    private void failParentForTerminalSubflow(FlowInstance child) {
        if (!Set.of("REJECTED", "TERMINATED", "WITHDRAWN").contains(child.getStatus())) return;
        FlowAction relationAction = subflowParentAction(child.getId());
        if (relationAction == null) return;
        Map<String, Object> relation = readMap(relationAction.getInputJson());
        Long parentId = storedLong(relation.get("parentInstanceId"));
        Long executionId = storedLong(relation.get("parentExecutionId"));
        FlowInstance parent = parentId == null ? null : lockInstance(parentId);
        FlowExecution parentExecution = executionId == null ? null : executionService.selectById(executionId);
        if (parent == null || parentExecution == null || !"ACTIVE".equals(parentExecution.getStatus())
                || !ACTIVE_INSTANCE_STATUSES.contains(parent.getStatus())) return;
        Map<String, Object> node = subflowNode(parent, parentExecution.getNodeKey());
        fail(parent, parentExecution.getNodeKey(), node, "SUBFLOW",
                "FLOW_SUBFLOW_CHILD_" + child.getStatus(), "子流程已结束为" + child.getStatus() + "，请人工恢复父流程");
    }

    /** 父流程进入终态时同步终止仍在运行的子流程，避免遗留待办、作业或无限等待链。 */
    private void terminateActiveSubflows(FlowInstance parent) {
        terminateActiveSubflows(parent, new LinkedHashSet<>());
    }

    private void terminateActiveSubflows(FlowInstance parent, Set<Long> visited) {
        if (parent == null || !visited.add(parent.getId())) return;
        List<FlowAction> relations = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, parent.getId())
                .eq(FlowAction::getActionCode, "SUBFLOW_STARTED")
                .orderByAsc(FlowAction::getId));
        for (FlowAction relationAction : relations) {
            Long childId = storedLong(readMap(relationAction.getInputJson()).get("childInstanceId"));
            if (childId == null || visited.contains(childId)) continue;
            FlowInstance child = lockInstance(childId);
            if (child == null || !ACTIVE_INSTANCE_STATUSES.contains(child.getStatus())) continue;
            String beforeStatus = child.getStatus();
            child.setStatus("TERMINATED");
            child.setFinishTime(LocalDateTime.now());
            child.setUpdateTime(LocalDateTime.now());
            if (instanceService.updateById(child) != 1) {
                throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "子流程状态已变化，请刷新后重试");
            }
            cancelUnfinishedTasks(child);
            cancelPendingJobs(child.getId(), null);
            cancelActiveExecutions(child.getId(), "PARENT_TERMINATED");
            String key = "SUBFLOW_PARENT_TERMINATED:" + parent.getId();
            if (findAction(child.getId(), key) == null) {
                insertAction(child.getId(), null,
                        child.getCurrentNodeKey() == null ? "INSTANCE" : child.getCurrentNodeKey(),
                        "SUBFLOW_PARENT_TERMINATED", "父流程已结束，子流程同步终止",
                        Map.of("parentInstanceId", parent.getId(), "parentStatus", parent.getStatus()),
                        result(child), key, parent.getStartedByAccountId());
                recordHistory(child, null, null, child.getCurrentNodeKey(), "SUBFLOW_PARENT_TERMINATED",
                        "父流程已结束，子流程同步终止", beforeStatus, "TERMINATED", null, null,
                        Map.of("parentInstanceId", parent.getId(), "parentStatus", parent.getStatus()));
            }
            terminateActiveSubflows(child, visited);
        }
    }

    private FlowAction subflowParentAction(Long childInstanceId) {
        return actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, childInstanceId).eq(FlowAction::getActionCode, "SUBFLOW_PARENT")
                .orderByDesc(FlowAction::getId).last(currentRuntimeRead(), "LIMIT 1 FOR UPDATE"))
                .stream().findFirst().orElse(null);
    }

    private FlowInstance lockInstance(Long instanceId) {
        return instanceService.selectList(Wrappers.<FlowInstance>lambdaQuery()
                .eq(FlowInstance::getId, instanceId).last("FOR UPDATE")).stream().findFirst().orElse(null);
    }

    private Map<String, Object> subflowNode(FlowInstance parent, String nodeKey) {
        return nodes(snapshot(parent)).stream().filter(node -> Objects.equals(nodeKey, string(node.get("nodeKey")))
                && "SUBFLOW".equals(node.get("nodeType"))).findFirst()
                .orElseThrow(() -> conflict("FLOW_SUBFLOW_NODE_MISSING", "父流程冻结快照中不存在对应子流程节点"));
    }

    private int subflowDepth(FlowInstance instance) {
        int depth = 0;
        FlowInstance current = instance;
        Set<Long> visited = new LinkedHashSet<>();
        while (current != null && visited.add(current.getId())) {
            FlowAction relation = subflowParentAction(current.getId());
            if (relation == null) break;
            Long parentId = storedLong(readMap(relation.getInputJson()).get("parentInstanceId"));
            if (parentId == null) break;
            depth++;
            current = instanceService.selectById(parentId);
        }
        return depth;
    }

    private void advanceFromExecution(AuthenticatedContext context, FlowInstance instance, Map<String, Object> snapshot,
                                      String sourceNodeKey, Map<String, Object> variables,
                                      Long previousHandlerTenantMemberId, Long parentExecutionId) {
        advancePath(context, instance, snapshot, sourceNodeKey, variables, previousHandlerTenantMemberId, null, parentExecutionId);
    }

    private void advancePath(AuthenticatedContext context, FlowInstance instance, Map<String, Object> snapshot,
                             String sourceNodeKey, Map<String, Object> variables,
                             Long previousHandlerTenantMemberId, String firstNodeKey, Long parentExecutionId) {
        String current = sourceNodeKey;
        Long parent = parentExecutionId;
        for (int step = 0; step < 100; step++) {
            Map<String, Object> next = step == 0 && firstNodeKey != null
                    ? nodes(snapshot).stream().filter(node -> Objects.equals(firstNodeKey, node.get("nodeKey"))).findFirst().orElse(null)
                    : nextNode(snapshot, current, variables);
            if (next == null) {
                fail(instance, current, Map.of(), "PATH_RESOLUTION", "FLOW_RUNTIME_PATH_NOT_FOUND",
                        "当前节点没有命中的唯一后续路径");
                return;
            }
            current = string(next.get("nodeKey"));
            String nodeType = string(next.get("nodeType"));
            FlowNodeSuspensionService.Restoration restored = nodeSuspensions.restore(instance, current);
            if (restored != null) {
                instance.setCurrentNodeKey(current); instance.setStatus("WAITING"); instance.setUpdateTime(LocalDateTime.now());
                if (instanceService.updateById(instance) != 1) throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已变化，请重新读取");
                insertAction(instance.getId(), null, current, "RUNTIME_NODE_RESUMED", "新增节点完成，继续原节点整组待办",
                        Map.of("frameActionId", restored.frameActionId()), result(instance), "RUNTIME_NODE_RESUMED:" + restored.frameActionId(), context.accountId());
                recordHistory(instance, restored.executionId(), null, current, "NODE_RESUMED", "原节点整组待办与剩余时限已恢复",
                        "SUSPENDED", "ACTIVE", context.accountId(), context.tenantMemberId(),
                        Map.of("frameActionId", restored.frameActionId(), "taskIds", restored.taskIds()));
                return;
            }
            FlowExecution execution = recordExecution(instance, current, nodeType, "ACTIVE", null, parent);
            instance.setCurrentNodeKey(current);
            instance.setUpdateTime(LocalDateTime.now());
            Map<String, Object> config = map(next.get("config"));
            if ("GATEWAY".equals(nodeType)) {
                String gatewayMode = FlowGatewayPolicy.mode(config);
                if (FlowGatewayPolicy.split(gatewayMode)) {
                    List<Map<String, Object>> selected = selectedGatewayEdges(snapshot, current, gatewayMode, variables);
                    if (selected.isEmpty()) {
                        fail(instance, current, next, "PATH_RESOLUTION", "FLOW_RUNTIME_PATH_NOT_FOUND",
                                "包容网关没有命中分支且未配置兜底路径");
                        return;
                    }
                    completeExecution(execution, "SPLIT_" + selected.size());
                    List<String> edgeKeys = selected.stream().map(edge -> string(edge.get("edgeKey"))).toList();
                    List<String> targets = selected.stream().map(edge -> string(edge.get("targetNodeKey"))).toList();
                    recordHistory(instance, execution.getId(), null, current, "GATEWAY_SPLIT",
                            (FlowGatewayPolicy.PARALLEL.equals(gatewayMode) ? "并行分支已启动" : "包容分支已选择"),
                            "RUNNING", "RUNNING", null, null,
                            Map.of("gatewayMode", gatewayMode, "mergeNodeKey", string(config.get(FlowGatewayPolicy.MERGE_NODE_KEY)),
                                    "selectedEdgeKeys", edgeKeys, "selectedTargetNodeKeys", targets,
                                    "selectedCount", selected.size()));
                    for (Map<String, Object> edge : selected) {
                        advancePath(context, instance, snapshot, current, variables, previousHandlerTenantMemberId,
                                string(edge.get("targetNodeKey")), execution.getId());
                        if (Set.of("EXCEPTION", "REJECTED", "TERMINATED", "WITHDRAWN", "COMPLETED")
                                .contains(instance.getStatus())) return;
                    }
                    normalizeWaitingState(instance);
                    return;
                }
                if (FlowGatewayPolicy.MERGE.equals(gatewayMode)) {
                    GatewayJoinResult join = arriveAtGatewayMerge(instance, execution, config);
                    if (join != GatewayJoinResult.JOINED) return;
                    parent = execution.getId();
                    continue;
                }
            }
            if ("SUBFLOW".equals(nodeType)) {
                startSubflow(context, instance, execution, next, variables);
                return;
            }
            if ("END".equals(nodeType)) {
                instance.setStatus("COMPLETED");
                instance.setFinishTime(LocalDateTime.now());
                if (instanceService.updateById(instance) != 1) {
                    throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程已变化，结束节点未重复提交");
                }
                completeExecution(execution, "COMPLETED");
                cancelPendingJobs(instance.getId(), null);
                insertAction(instance.getId(), null, current, "AUTO_END", null, Map.of(), result(instance),
                        "AUTO:" + current + ":" + UUID.randomUUID(), instance.getStartedByAccountId());
                resumeParentAfterSubflow(instance);
                messageService.notifyFlowEvent(context, instance.getId(),
                        "instance-" + instance.getId() + "-completed",
                        "流程已完成：" + instance.getTitle(),
                        "AFTER_APPROVAL".equals(readMap(instance.getBusinessSnapshotJson()).get("executionMode"))
                                ? "审批流程已完成，业务操作将按当前权限和数据版本执行，请在模块页面查看是否生效。"
                                : "流程已到达结束节点，业务结果和完整处理历史可以在流程运行页查看。",
                        List.of(instance.getStartedByAccountId()));
                publishFlowEvent(context, instance, LocalBusinessEvent.Type.FLOW_APPROVED);
                return;
            }
            // 填写节点与审批共用人工任务锁和候选人解析，必须等待提交，不能落到自动执行分支。
            if (Set.of("APPROVAL", "FORM_TASK").contains(nodeType)) {
                String taskResult = createApprovalTask(context, instance, next, variables, previousHandlerTenantMemberId);
                if ("FAILED".equals(taskResult)) return;
                if ("APPROVED".equals(taskResult)) {
                    completeExecution(execution, "REPEAT_APPROVAL");
                    parent = execution.getId();
                    variables = variables(instance.getId());
                    continue;
                }
                instance.setStatus("WAITING");
                instanceService.updateById(instance);
                return;
            }
            if (FlowMessageNodePolicy.supports(nodeType)) {
                Map<String, Object> exceptionPolicy = map(next.get("exceptionPolicy"));
                boolean retry = "RETRY".equals(string(exceptionPolicy.get("action")).toUpperCase(Locale.ROOT));
                int maxAttempts = retry ? Math.max(1, number(exceptionPolicy.get("maxAttempts"), 3)) : 1;
                int retryDelayMinutes = Math.max(1, number(exceptionPolicy.get("retryDelayMinutes"), 5));
                LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
                payload.put("retryDelayMinutes", retryDelayMinutes);
                payload.put("messageKind", nodeType);
                if (previousHandlerTenantMemberId != null) {
                    payload.put("previousHandlerTenantMemberId", previousHandlerTenantMemberId);
                }
                scheduleJob(instance, execution, current, "MESSAGE_DELIVERY", payload,
                        "MESSAGE:" + instance.getId() + ":" + execution.getId(), maxAttempts, LocalDateTime.now());
                instance.setStatus("WAITING");
                instance.setUpdateTime(LocalDateTime.now());
                instanceService.updateById(instance);
                return;
            }
            if (Set.of(FlowAutomaticActionPolicy.UPDATE_FIELD, FlowAutomaticActionPolicy.DATA_ACTION).contains(nodeType)) {
                try {
                    executeAutomaticBusinessNode(context, instance, next, config, variables,
                            execution.getId(), false);
                } catch (DomainException exception) {
                    fail(instance, current, next, "BUSINESS_ACTION", exception.code(), exception.getMessage());
                    return;
                }
            }
            if (FlowWaitTimerPolicy.supports(nodeType)) {
                try {
                    FlowWaitTimerPolicy.Config wait = FlowWaitTimerPolicy.readFrozen(nodeType, config);
                    Map<String, Object> payload = FlowWaitTimerPolicy.frozen(wait);
                    LocalDateTime dueTime = wait.dueTime(LocalDateTime.now());
                    scheduleJob(instance, execution, current, "TIMER", payload,
                            "TIMER:" + instance.getId() + ":" + execution.getId(), 1, dueTime);
                    instance.setStatus("WAITING");
                    instance.setUpdateTime(LocalDateTime.now());
                    instanceService.updateById(instance);
                    return;
                } catch (IllegalArgumentException failure) {
                    fail(instance, current, next, "WAIT_TIMER", "FLOW_WAIT_TIMER_CONFIG_INVALID", failure.getMessage());
                    return;
                }
            }
            if (FlowExternalCallbackPolicy.supports(nodeType)) {
                try {
                    FlowExternalCallbackPolicy.Frozen callback = FlowExternalCallbackPolicy.readFrozen(config);
                    String eventId = "flow-webhook:" + instance.getId() + ":" + execution.getId();
                    scheduleJob(instance, execution, current, "WEBHOOK_DELIVERY",
                            Map.of("eventId", eventId, "occurredTime", LocalDateTime.now().toString(),
                                    "retryDelayMinutes", 1), eventId, callback.maxAttempts(), LocalDateTime.now());
                    instance.setStatus("WAITING");
                    instance.setUpdateTime(LocalDateTime.now());
                    instanceService.updateById(instance);
                    return;
                } catch (IllegalArgumentException failure) {
                    fail(instance, current, next, "WEBHOOK", "FLOW_WEBHOOK_CONFIG_INVALID", failure.getMessage());
                    return;
                }
            }
            if ("AI".equals(nodeType)) {
                scheduleJob(instance, execution, current, "AI_COMPLETION", Map.of(),
                        "AI:" + instance.getId() + ":" + execution.getId(), 1, LocalDateTime.now());
                instance.setStatus("WAITING");
                instance.setUpdateTime(LocalDateTime.now());
                instanceService.updateById(instance);
                return;
            }
            instanceService.updateById(instance);
            completeExecution(execution, "COMPLETED");
            parent = execution.getId();
            insertAction(instance.getId(), null, current, "AUTO_EXECUTED", null,
                    Map.of("nodeType", nodeType), result(instance),
                    "AUTO:" + current + ":" + UUID.randomUUID(), instance.getStartedByAccountId());
        }
        fail(instance, current, Map.of(), "ENGINE", "FLOW_RUNTIME_STEP_LIMIT",
                "运行路径超过最大自动步数，已停止等待人工处理");
    }

    private String createApprovalTask(
            AuthenticatedContext context, FlowInstance instance, Map<String, Object> node,
            Map<String, Object> variables, Long previousHandlerTenantMemberId) {
        Map<String, Object> policy = map(node.get("assigneePolicy"));
        if ("EXTERNAL_PERSON".equals(string(policy.get("type")))) {
            return createExternalApprovalTask(instance, node, policy);
        }
        List<FlowParticipantResolver.ResolvedPerson> people = participantResolver.resolve(
                participantContext(context, instance), policy, instance, variables, previousHandlerTenantMemberId);
        if (people.isEmpty()) {
            fail(instance, string(node.get("nodeKey")), node, "ASSIGNEE",
                    "FLOW_RUNTIME_ASSIGNEE_UNRESOLVED", "审批节点没有可解析的有效处理人");
            return "FAILED";
        }
        String approvalMode = string(node.get("approvalMode")).toUpperCase(Locale.ROOT);
        if (approvalMode.isBlank()) approvalMode = string(map(node.get("config")).get("approvalMode")).toUpperCase(Locale.ROOT);
        if (!Set.of("OR_SIGN", "ALL_SIGN", "SEQUENTIAL").contains(approvalMode)) approvalMode = "OR_SIGN";
        ArrayList<FlowTask> created = new ArrayList<>();
        if ("OR_SIGN".equals(approvalMode)) {
            FlowTask task = newApprovalTask(instance, node, policy, people, approvalMode, 1, 1,
                    people.size() == 1 && !FlowManualOperationPolicy.fromConfig(map(node.get("config"))).requireClaim()
                            ? people.getFirst() : null, "PENDING");
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
        boolean formTask = "FORM_TASK".equals(string(node.get("nodeType")));
        recordHistory(instance, null, created.getFirst().getId(), string(node.get("nodeKey")), "TASK",
                formTask ? "填写任务已创建" : "审批任务已创建",
                "RUNNING", "WAITING", null, null,
                Map.of("approvalMode", approvalMode,
                        "assignees", people.stream().map(this::personSnapshot).toList(),
                        "taskCount", created.size()));
        for (FlowTask task : created) {
            if ("PENDING".equals(task.getStatus()) && tryRepeatApproval(instance, task)
                    && "SEQUENTIAL".equals(approvalMode)) activateNextApprovalTaskOrWait(instance, task, readMap(task.getAssigneeSnapshotJson()));
        }
        List<FlowTask> pending = tasks(instance.getId()).stream()
                .filter(task -> Objects.equals(task.getNodeKey(), string(node.get("nodeKey")))
                        && sameTaskExecution(created.getFirst(), task)
                        && Set.of("PENDING", "QUEUED").contains(task.getStatus())).toList();
        if (pending.isEmpty()) return "APPROVED";
        // 与顺序激活沿用实际任务ID去重，首人自动通过后不能再以首人ID重复通知下一位。
        for (FlowTask pendingTask : pending) {
            if (!"PENDING".equals(pendingTask.getStatus())) continue;
            List<Long> recipients = "OR_SIGN".equals(approvalMode)
                    ? people.stream().map(FlowParticipantResolver.ResolvedPerson::accountId).toList()
                    : List.of(pendingTask.getAssigneeAccountId());
            messageService.notifyFlowEvent(participantContext(context, instance), instance.getId(),
                    "instance-" + instance.getId() + "-task-" + pendingTask.getId(),
                    (formTask ? "待填写：" : "待审批：") + instance.getTitle(),
                    "流程已进入“" + string(node.get("name")) + "”，请在待办或流程运行页处理。", recipients);
        }
        return "WAITING";
    }

    /** 外部待办仍属于当前实例的人工节点，仅身份在应用命名空间内比较，不生成平台人员映射。 */
    private String createExternalApprovalTask(FlowInstance instance, Map<String, Object> node,
                                              Map<String, Object> policy) {
        Long applicationId = numericBusinessId(string(policy.get("applicationId")));
        String externalId = string(policy.get("externalApproverId"));
        AppDefinition application = applicationId == null ? null : applicationService.selectById(applicationId);
        if (application == null || !"ACTIVE".equals(application.getStatus())
                || !Objects.equals(application.getPlatformId(), instance.getPlatformId())
                || !Objects.equals(application.getOwnerSystemId(), instance.getSystemId())
                || !Objects.equals(application.getOwnerTenantId(), instance.getTenantId())
                || externalId.isBlank()) {
            fail(instance, string(node.get("nodeKey")), node, "ASSIGNEE",
                    "FLOW_EXTERNAL_APPROVER_UNAVAILABLE", "外部审批应用或审批人标识已失效，请修正配置后处理异常");
            return "FAILED";
        }
        String nodeKey = string(node.get("nodeKey"));
        FlowExecution execution = activeExecution(instance.getId(), nodeKey);
        if (execution == null) throw conflict("FLOW_TASK_EXECUTION_MISSING", "当前外部审批节点没有执行轮次");
        FlowTask task = new FlowTask();
        task.setInstanceId(instance.getId());
        task.setExecutionId(execution.getId());
        task.setNodeKey(nodeKey);
        task.setTaskType("APPROVAL");
        task.setStatus("PENDING");
        task.setAssigneeApplicationId(applicationId);
        task.setAssigneeExternalPrincipalId(externalId);
        task.setAssigneeSnapshotJson(toJson(Map.of("policyType", "EXTERNAL_PERSON",
                "applicationId", applicationId, "externalApproverId", externalId,
                "approvalMode", "OR_SIGN", "executionId", execution.getId(),
                "timeoutPolicy", map(node.get("timeoutPolicy")))));
        int timeoutMinutes = timeoutMinutes(map(node.get("timeoutPolicy")));
        if (timeoutMinutes > 0) task.setDueTime(LocalDateTime.now().plusMinutes(timeoutMinutes));
        task.setVersion(0);
        taskService.insert(task);
        if (task.getDueTime() != null) scheduleJob(instance, execution, nodeKey, "TASK_TIMEOUT",
                Map.of("taskId", task.getId()), "TASK_TIMEOUT:" + task.getId(), 1, task.getDueTime());
        recordHistory(instance, execution.getId(), task.getId(), nodeKey, "TASK", "外部审批任务已创建",
                "RUNNING", "WAITING", null, null,
                Map.of("applicationId", applicationId, "externalApproverId", externalId));
        return "WAITING";
    }

    private FlowTask newApprovalTask(
            FlowInstance instance, Map<String, Object> node, Map<String, Object> policy,
            List<FlowParticipantResolver.ResolvedPerson> people, String approvalMode,
            int sequence, int sequenceCount, FlowParticipantResolver.ResolvedPerson assignee, String status) {
        FlowTask task = new FlowTask();
        task.setInstanceId(instance.getId());
        task.setNodeKey(string(node.get("nodeKey")));
        task.setTaskType("FORM_TASK".equals(string(node.get("nodeType"))) ? "FORM_TASK" : "APPROVAL");
        task.setStatus(status);
        task.setAssigneeAccountId(assignee == null ? null : assignee.accountId());
        task.setAssigneeTenantMemberId(assignee == null ? null : assignee.tenantMemberId());
        Map<String, Object> taskSnapshot = new LinkedHashMap<>();
        taskSnapshot.put("policyType", string(policy.get("type")));
        taskSnapshot.put("approvalMode", approvalMode);
        taskSnapshot.put("sequence", sequence); taskSnapshot.put("sequenceCount", sequenceCount);
        taskSnapshot.put("formPolicy", map(node.get("formPolicy")));
        taskSnapshot.put("timeoutPolicy", map(node.get("timeoutPolicy")));
        taskSnapshot.put("repeatApproval", map(map(node.get("config")).get("repeatApproval")));
        FlowExecution execution = activeExecution(instance.getId(), task.getNodeKey());
        if (execution == null) throw conflict("FLOW_TASK_EXECUTION_MISSING", "当前节点执行记录不存在，未创建审批任务");
        task.setExecutionId(execution.getId());
        taskSnapshot.put("executionId", execution.getId());
        taskSnapshot.put("resolvedPeople", people.stream().map(this::personSnapshot).toList());
        if ("ALL_SIGN".equals(approvalMode)) {
            taskSnapshot.put(FlowApprovalCompletionPolicy.CONFIG_KEY,
                    FlowApprovalCompletionPolicy.fromConfig(map(node.get("config")), approvalMode).snapshot());
        }
        task.setAssigneeSnapshotJson(toJson(taskSnapshot));
        FlowManualOperationPolicy operations = FlowManualOperationPolicy.fromConfig(map(node.get("config")));
        if (operations.requireClaim()) {
            Map<String, Object> queue = new LinkedHashMap<>(readMap(task.getAssigneeSnapshotJson()));
            queue.put("claimPool", people.stream().map(this::personSnapshot).toList());
            task.setAssigneeSnapshotJson(toJson(queue));
        }
        Map<String, Object> timeoutPolicy = map(node.get("timeoutPolicy"));
        int timeoutMinutes = timeoutMinutes(timeoutPolicy);
        if (timeoutMinutes > 0 && "PENDING".equals(status)) {
            task.setDueTime(LocalDateTime.now().plusMinutes(timeoutMinutes));
        }
        task.setVersion(0);
        taskService.insert(task);
        if (task.getDueTime() != null && "PENDING".equals(task.getStatus())) {
            scheduleJob(instance, activeExecution(instance.getId(), task.getNodeKey()), task.getNodeKey(),
                    "TASK_TIMEOUT", Map.of("taskId", task.getId()), "TASK_TIMEOUT:" + task.getId(),
                    1, task.getDueTime());
        }
        return task;
    }

    private boolean activateNextApprovalTaskOrWait(
            FlowInstance instance, FlowTask completed, Map<String, Object> taskSnapshot) {
        String mode = string(taskSnapshot.get("approvalMode")).toUpperCase(Locale.ROOT);
        List<FlowTask> siblings = tasks(instance.getId()).stream()
                .filter(item -> Objects.equals(item.getNodeKey(), completed.getNodeKey())
                        && sameTaskExecution(completed, item)
                        && Set.of("APPROVAL", "FORM_TASK").contains(item.getTaskType()) && !Objects.equals(item.getId(), completed.getId()))
                .toList();
        if ("ALL_SIGN".equals(mode)) {
            for (FlowTask sibling : siblings) if ("PENDING".equals(sibling.getStatus()))
                tryRepeatApproval(instance, sibling);
            List<FlowTask> effective = tasks(instance.getId()).stream()
                    .filter(item -> Objects.equals(item.getNodeKey(), completed.getNodeKey())
                            && sameTaskExecution(completed, item)
                            && "APPROVAL".equals(item.getTaskType()) && !"CANCELLED".equals(item.getStatus())).toList();
            Map<String, Object> completionConfig = taskSnapshot.get(FlowApprovalCompletionPolicy.CONFIG_KEY) instanceof Map<?, ?>
                    ? Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY,
                            map(taskSnapshot.get(FlowApprovalCompletionPolicy.CONFIG_KEY))) : Map.of();
            FlowApprovalCompletionPolicy completion = FlowApprovalCompletionPolicy.fromConfig(completionConfig, "ALL_SIGN");
            long approved = effective.stream().filter(item -> "APPROVE".equals(item.getStatus())).count();
            if (!completion.reached((int) approved, effective.size())) {
                return effective.stream().anyMatch(item -> "PENDING".equals(item.getStatus()));
            }
            for (FlowTask pending : effective) {
                if (!"PENDING".equals(pending.getStatus())) continue;
                pending.setStatus("CANCELLED"); pending.setCompleteTime(LocalDateTime.now()); pending.setUpdateTime(LocalDateTime.now());
                if (taskService.updateById(pending) != 1) throw conflict("FLOW_TASK_CONCURRENT_UPDATE", "会签参与任务已变化，请刷新后重试");
                resolveTaskTimeoutIncidents(systemContext(instance), instance, pending);
                cancelPendingJobs(instance.getId(), pending.getId());
                recordHistory(instance, null, pending.getId(), pending.getNodeKey(), "TASK", "会签比例已达到，剩余待办已结束",
                        "PENDING", "CANCELLED", null, null,
                        Map.of("approved", approved, "total", effective.size(), "ratioPercent", completion.ratioPercent()));
            }
            return false;
        }
        if ("SEQUENTIAL".equals(mode)) {
            while (true) {
                siblings = tasks(instance.getId()).stream().filter(item -> Objects.equals(item.getNodeKey(), completed.getNodeKey())
                        && sameTaskExecution(completed, item)).toList();
                FlowTask next = siblings.stream().filter(item -> "QUEUED".equals(item.getStatus()))
                        .min(Comparator.comparing(FlowTask::getId)).orElse(null);
                if (next == null) return false;
                next.setStatus("PENDING");
                Map<String, Object> nextSnapshot = readMap(next.getAssigneeSnapshotJson());
                Map<String, Object> timeout = map(nextSnapshot.get("timeoutPolicy"));
                int timeoutMinutes = timeoutMinutes(timeout);
                if (timeoutMinutes > 0) next.setDueTime(LocalDateTime.now().plusMinutes(timeoutMinutes));
                next.setUpdateTime(LocalDateTime.now());
                taskService.updateById(next);
                if (next.getDueTime() != null) {
                    scheduleJob(instance, activeExecution(instance.getId(), next.getNodeKey()), next.getNodeKey(),
                            "TASK_TIMEOUT", Map.of("taskId", next.getId()), "TASK_TIMEOUT:" + next.getId(),
                            1, next.getDueTime());
                }
                recordHistory(instance, null, next.getId(), next.getNodeKey(), "TASK", "顺序会签已进入下一处理人",
                        "QUEUED", "PENDING", null, null, Map.of());
                if (tryRepeatApproval(instance, next)) continue;
                messageService.notifyFlowEvent(participantContext(systemContext(instance), instance), instance.getId(),
                        "instance-" + instance.getId() + "-task-" + next.getId(),
                        "待审批：" + instance.getTitle(),
                        "顺序会签已轮到你处理，当前节点：" + next.getNodeKey() + "。",
                        List.of(next.getAssigneeAccountId()));
                return true;
            }
        }
        return false;
    }

    /** 所有终态共用整实例清理，包含前插期间暂停的原组；已审批及字段提交事实不修改。 */
    private void cancelUnfinishedTasks(FlowInstance instance) {
        for (FlowTask task : tasks(instance.getId())) {
            if (!Set.of("PENDING", "QUEUED", "SUSPENDED").contains(task.getStatus())) continue;
            task.setStatus("CANCELLED"); task.setCompleteTime(LocalDateTime.now()); task.setUpdateTime(LocalDateTime.now());
            if (taskService.updateById(task) != 1) throw conflict("FLOW_TASK_CONCURRENT_UPDATE", "流程任务已变化，请刷新后重试");
        }
        nodeSuspensions.cancel(instance);
    }

    /** 实例终态关闭其全部活动执行，运行页和重启扫描不能再把旧节点解释为可继续现场。 */
    private void cancelActiveExecutions(Long instanceId, String resultCode) {
        for (FlowExecution execution : executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                .eq(FlowExecution::getInstanceId, instanceId).eq(FlowExecution::getStatus, "ACTIVE"))) {
            execution.setStatus("CANCELLED");
            execution.setResultCode(resultCode);
            execution.setLeaveTime(LocalDateTime.now());
            executionService.updateById(execution);
        }
    }

    private void cancelSiblingApprovalTasks(Long instanceId, FlowTask handled) {
        for (FlowTask sibling : tasks(instanceId)) {
            if (!Objects.equals(sibling.getNodeKey(), handled.getNodeKey())
                    || Objects.equals(sibling.getId(), handled.getId())
                    || !sameTaskExecution(handled, sibling)
                    || !Set.of("PENDING", "QUEUED").contains(sibling.getStatus())) continue;
            sibling.setStatus("CANCELLED");
            sibling.setCompleteTime(LocalDateTime.now());
            sibling.setUpdateTime(LocalDateTime.now());
            taskService.updateById(sibling);
            cancelPendingJobs(instanceId, sibling.getId());
        }
    }

    private void addCandidate(Long taskId, FlowParticipantResolver.ResolvedPerson person, String reason) {
        String type = person.tenantMemberId() == null ? "PLATFORM_ACCOUNT" : "TENANT_MEMBER";
        Long identity = person.tenantMemberId() == null ? person.accountId() : person.tenantMemberId();
        boolean exists = !candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, taskId)
                .eq(FlowTaskCandidate::getCandidateType, type)
                .eq(FlowTaskCandidate::getCandidateId, String.valueOf(identity))).isEmpty();
        if (exists) return;
        FlowTaskCandidate candidate = new FlowTaskCandidate();
        candidate.setTaskId(taskId);
        candidate.setCandidateType(type);
        candidate.setCandidateId(String.valueOf(identity));
        candidate.setResolutionReason(reason);
        candidateService.insert(candidate);
    }

    private void fail(FlowInstance instance, String nodeKey, Map<String, Object> node,
                      String exceptionType, String errorCode, String message) {
        FlowExecution failedExecution = activeExecution(instance.getId(), nodeKey);
        if (failedExecution != null) {
            failedExecution.setStatus("FAILED");
            failedExecution.setResultCode(errorCode);
            failedExecution.setLeaveTime(LocalDateTime.now());
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
        instance.setUpdateTime(LocalDateTime.now());
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
        notifyParentSubflowException(instance, errorCode);
    }

    private Map<String, Object> nextNode(
            Map<String, Object> snapshot, String sourceNodeKey, Map<String, Object> variables) {
        List<Map<String, Object>> outgoing = outgoingEdges(snapshot, sourceNodeKey);
        Map<String, Object> edge = outgoing.stream()
                .filter(item -> flowConditions.matches(map(snapshot.get("conditionContext")),
                        objectMapper.valueToTree(map(item.get("config"))),
                        blankToNull(string(item.get("conditionExpression"))), variables))
                .findFirst().orElse(null);
        if (edge == null) return null;
        String target = string(edge.get("targetNodeKey"));
        return nodes(snapshot).stream().filter(node -> Objects.equals(target, string(node.get("nodeKey"))))
                .findFirst().orElse(null);
    }

    /** 并行执行全部出线；包容网关执行所有命中的条件分支，无命中时才走唯一兜底边。 */
    private List<Map<String, Object>> selectedGatewayEdges(
            Map<String, Object> snapshot, String sourceNodeKey, String gatewayMode, Map<String, Object> variables) {
        List<Map<String, Object>> outgoing = outgoingEdges(snapshot, sourceNodeKey);
        if (FlowGatewayPolicy.PARALLEL.equals(gatewayMode)) return outgoing;
        List<Map<String, Object>> matched = outgoing.stream().filter(this::conditionalEdge)
                .filter(edge -> flowConditions.matches(map(snapshot.get("conditionContext")),
                        objectMapper.valueToTree(map(edge.get("config"))),
                        blankToNull(string(edge.get("conditionExpression"))), variables)).toList();
        if (!matched.isEmpty()) return matched;
        return outgoing.stream().filter(edge -> !conditionalEdge(edge)).findFirst().map(List::of).orElse(List.of());
    }

    private List<Map<String, Object>> outgoingEdges(Map<String, Object> snapshot, String sourceNodeKey) {
        return edges(snapshot).stream()
                .filter(edge -> Objects.equals(sourceNodeKey, string(edge.get("sourceNodeKey"))))
                .sorted(Comparator.<Map<String, Object>, Integer>comparing(edge -> number(edge.get("priorityOrder"), 0))
                        .thenComparing(edge -> string(edge.get("edgeKey")))).toList();
    }

    private boolean conditionalEdge(Map<String, Object> edge) {
        return map(edge.get("config")).containsKey("condition")
                || !string(edge.get("conditionExpression")).isBlank();
    }

    private enum GatewayJoinResult { WAITING, JOINED, FAILED }

    /**
     * 每次到达都保存独立执行；沿 parent_execution_id 找回冻结拆分执行和分支根。
     * 实例行锁串行化到达计数，FlowAction 唯一键再保证同一拆分只放行一次。
     */
    private GatewayJoinResult arriveAtGatewayMerge(
            FlowInstance instance, FlowExecution arrival, Map<String, Object> mergeConfig) {
        String splitNodeKey = string(mergeConfig.get(FlowGatewayPolicy.SPLIT_NODE_KEY));
        FlowExecution split = ancestorExecution(arrival, splitNodeKey);
        if (split == null) {
            fail(instance, arrival.getNodeKey(), Map.of(), "GATEWAY", "FLOW_GATEWAY_SPLIT_EXECUTION_MISSING",
                    "汇合节点无法找到所属分支执行，请检查发布版本");
            return GatewayJoinResult.FAILED;
        }
        FlowHistoryEvent splitEvent = historyService.selectList(Wrappers.<FlowHistoryEvent>lambdaQuery()
                        .eq(FlowHistoryEvent::getInstanceId, instance.getId())
                        .eq(FlowHistoryEvent::getExecutionId, split.getId())
                        .eq(FlowHistoryEvent::getEventType, "GATEWAY_SPLIT")
                        .orderByDesc(FlowHistoryEvent::getId).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        Map<String, Object> splitDetail = splitEvent == null ? Map.of() : readMap(splitEvent.getDetailJson());
        int expected = number(splitDetail.get("selectedCount"), 0);
        Long branchRoot = branchRootExecutionId(arrival, split.getId());
        if (expected < 1 || branchRoot == null) {
            fail(instance, arrival.getNodeKey(), Map.of(), "GATEWAY", "FLOW_GATEWAY_SPLIT_SNAPSHOT_MISSING",
                    "分支选择快照不完整，已停止自动汇合");
            return GatewayJoinResult.FAILED;
        }

        Set<Long> arrivedBranches = new LinkedHashSet<>();
        boolean duplicate = false;
        List<FlowExecution> arrivals = executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                .eq(FlowExecution::getInstanceId, instance.getId())
                .eq(FlowExecution::getNodeKey, arrival.getNodeKey())
                .eq(FlowExecution::getNodeType, "GATEWAY").orderByAsc(FlowExecution::getId));
        for (FlowExecution candidate : arrivals) {
            FlowExecution candidateSplit = ancestorExecution(candidate, splitNodeKey);
            if (candidateSplit == null || !Objects.equals(candidateSplit.getId(), split.getId())) continue;
            Long candidateRoot = branchRootExecutionId(candidate, split.getId());
            if (candidateRoot == null) continue;
            if (!Objects.equals(candidate.getId(), arrival.getId()) && Objects.equals(candidateRoot, branchRoot)) duplicate = true;
            arrivedBranches.add(candidateRoot);
        }
        int arrived = arrivedBranches.size();
        String resultCode = duplicate ? "JOIN_DUPLICATE" : arrived < expected ? "JOIN_WAITING" : "JOINED";
        completeExecution(arrival, resultCode);
        recordHistory(instance, arrival.getId(), null, arrival.getNodeKey(), "GATEWAY_JOIN_ARRIVAL",
                duplicate ? "重复分支到达已忽略" : arrived < expected ? "分支已到达，等待其他分支" : "必需分支已全部到达",
                "RUNNING", arrived < expected ? "WAITING" : "RUNNING", null, null,
                Map.of("splitExecutionId", split.getId(), "branchExecutionId", branchRoot,
                        "arrivedCount", arrived, "expectedCount", expected, "duplicate", duplicate));
        if (duplicate || arrived < expected) {
            instance.setStatus("WAITING");
            instance.setCurrentNodeKey(arrival.getNodeKey());
            instance.setUpdateTime(LocalDateTime.now());
            if (instanceService.updateById(instance) != 1) {
                throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程汇合状态已变化，请重新读取");
            }
            return GatewayJoinResult.WAITING;
        }

        String joinKey = "GATEWAY_JOIN:" + split.getId() + ":" + arrival.getNodeKey();
        if (findAction(instance.getId(), joinKey) != null) return GatewayJoinResult.WAITING;
        instance.setStatus("RUNNING");
        instance.setCurrentNodeKey(arrival.getNodeKey());
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程汇合状态已变化，请重新读取");
        }
        insertAction(instance.getId(), null, arrival.getNodeKey(), "GATEWAY_JOIN", null,
                Map.of("splitExecutionId", split.getId(), "arrivedCount", arrived, "expectedCount", expected),
                result(instance), joinKey, instance.getStartedByAccountId());
        recordHistory(instance, arrival.getId(), null, arrival.getNodeKey(), "GATEWAY_JOIN",
                "分支汇合完成，流程继续", "WAITING", "RUNNING", null, null,
                Map.of("splitExecutionId", split.getId(), "arrivedCount", arrived, "expectedCount", expected));
        return GatewayJoinResult.JOINED;
    }

    private FlowExecution ancestorExecution(FlowExecution execution, String nodeKey) {
        Long cursor = execution == null ? null : execution.getParentExecutionId();
        for (int depth = 0; cursor != null && depth < 200; depth++) {
            FlowExecution candidate = executionService.selectById(cursor);
            if (candidate == null) return null;
            if (Objects.equals(candidate.getNodeKey(), nodeKey)) return candidate;
            cursor = candidate.getParentExecutionId();
        }
        return null;
    }

    private Long branchRootExecutionId(FlowExecution arrival, Long splitExecutionId) {
        Long child = arrival == null ? null : arrival.getId();
        Long cursor = arrival == null ? null : arrival.getParentExecutionId();
        for (int depth = 0; cursor != null && depth < 200; depth++) {
            if (Objects.equals(cursor, splitExecutionId)) return child;
            FlowExecution parent = executionService.selectById(cursor);
            if (parent == null) return null;
            child = parent.getId();
            cursor = parent.getParentExecutionId();
        }
        return null;
    }

    /** 多分支只有一个实例摘要状态；真实当前位置以执行和任务集合为准。 */
    private void normalizeWaitingState(FlowInstance instance) {
        if (!Set.of("RUNNING", "WAITING").contains(instance.getStatus())) return;
        FlowTask pendingTask = tasks(instance.getId()).stream()
                .filter(task -> Set.of("PENDING", "QUEUED", "SUSPENDED").contains(task.getStatus()))
                .findFirst().orElse(null);
        boolean pendingJob = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                        .eq(FlowJob::getInstanceId, instance.getId())
                        .in(FlowJob::getStatus, List.of("PENDING", "RUNNING", "SUSPENDED")))
                .stream().findAny().isPresent();
        if (pendingTask != null) instance.setCurrentNodeKey(pendingTask.getNodeKey());
        instance.setStatus(pendingTask != null || pendingJob ? "WAITING" : "RUNNING");
        instance.setUpdateTime(LocalDateTime.now());
        if (instanceService.updateById(instance) != 1) {
            throw conflict("FLOW_INSTANCE_VERSION_CONFLICT", "流程分支状态已变化，请重新读取");
        }
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

    private boolean isCurrentHandler(AuthenticatedContext context, FlowTask task) {
        Long tenantMemberId = participantResolver.currentTenantMemberId(context);
        Long accountId = context.accountId();
        if (context.systemId() == null && platformPerson(context, accountId) == null) return false;
        if (task.getAssigneeTenantMemberId() != null) {
            if (Objects.equals(task.getAssigneeTenantMemberId(), tenantMemberId)) return true;
            return activeDelegationDelegate(context, task);
        }
        if (task.getAssigneeAccountId() != null) {
            if (Objects.equals(task.getAssigneeAccountId(), accountId)) return true;
            return activeDelegationDelegate(context, task);
        }
        boolean candidate = candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, task.getId())
                .eq(FlowTaskCandidate::getCandidateType, context.systemId() == null ? "PLATFORM_ACCOUNT" : "TENANT_MEMBER")
                .eq(FlowTaskCandidate::getCandidateId, String.valueOf(context.systemId() == null ? accountId : tenantMemberId))).size() == 1;
        return candidate || activeDelegationDelegate(context, task);
    }

    /** 代理身份只按任务快照中的当前有效关系匹配；历史候选记录本身永远不直接恢复代理权限。 */
    private boolean activeDelegationDelegate(AuthenticatedContext context, FlowTask task) {
        Map<String, Object> delegation = map(readMap(task.getAssigneeSnapshotJson()).get("delegation"));
        return activeDelegation(delegation, LocalDateTime.now()) && delegationIdentity(context, delegation, "delegate");
    }

    private boolean delegationPrincipal(AuthenticatedContext context, Map<String, Object> delegation) {
        return delegationIdentity(context, delegation, "principal");
    }

    private boolean delegationIdentity(AuthenticatedContext context, Map<String, Object> delegation, String key) {
        Map<String, Object> identity = map(delegation.get(key));
        Long accountId = storedLong(identity.get("accountId"));
        Long tenantMemberId = storedLong(identity.get("tenantMemberId"));
        if (!Objects.equals(accountId, context.accountId())) return false;
        return context.systemId() == null || Objects.equals(tenantMemberId, participantResolver.currentTenantMemberId(context));
    }

    private boolean activeDelegation(Map<String, Object> delegation, LocalDateTime now) {
        if (!"ACTIVE".equals(string(delegation.get("status")))) return false;
        LocalDateTime start = storedTime(delegation.get("startTime"));
        LocalDateTime end = storedTime(delegation.get("endTime"));
        return start != null && end != null && !start.isAfter(now) && end.isAfter(now);
    }

    private LocalDateTime storedTime(Object value) {
        try { return value == null ? null : LocalDateTime.parse(String.valueOf(value)); }
        catch (RuntimeException invalidTime) { return null; }
    }

    private Long storedLong(Object value) {
        if (value instanceof Number number) return number.longValue();
        try { return value == null ? null : Long.valueOf(String.valueOf(value)); }
        catch (NumberFormatException invalidId) { return null; }
    }

    /** 页面只获得可解释的委托关系，不暴露平台、系统、租户或账号内部标识。 */
    /** 运行页从持久动作关系读取父子实例，只返回安全摘要和当前查看权。 */
    private List<FlowRuntimeModels.SubflowView> subflowLinks(AuthenticatedContext context, FlowInstance instance) {
        List<FlowRuntimeModels.SubflowView> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        List<FlowAction> relations = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId())
                .in(FlowAction::getActionCode, "SUBFLOW_STARTED", "SUBFLOW_PARENT")
                .orderByAsc(FlowAction::getId));
        for (FlowAction action : relations) {
            Map<String, Object> stored = readMap(action.getInputJson());
            boolean childRelation = "SUBFLOW_STARTED".equals(action.getActionCode());
            Long relatedId = storedLong(stored.get(childRelation ? "childInstanceId" : "parentInstanceId"));
            Long executionId = storedLong(stored.get("parentExecutionId"));
            if (relatedId == null || !seen.add((childRelation ? "CHILD:" : "PARENT:") + relatedId)) continue;
            FlowInstance related = instanceService.selectById(relatedId);
            if (related == null) continue;
            FlowDefinition definition = definitionService.selectById(related.getFlowId());
            FlowVersion version = versionService.selectById(related.getFlowVersionId());
            boolean sameScope = Objects.equals(related.getContextType(), instance.getContextType())
                    && Objects.equals(related.getPlatformId(), instance.getPlatformId())
                    && Objects.equals(related.getSystemId(), instance.getSystemId())
                    && Objects.equals(related.getTenantId(), instance.getTenantId());
            result.add(new FlowRuntimeModels.SubflowView(childRelation ? "CHILD" : "PARENT", related.getId(),
                    executionId, string(stored.get("parentNodeKey")), related.getFlowId(),
                    definition == null ? "子流程" : definition.getName(), related.getFlowVersionId(),
                    version == null ? null : version.getVersionNumber(), related.getStatus(),
                    related.getErrorCode(), sameScope && canView(context, related)));
        }
        return List.copyOf(result);
    }

    private Map<String, Object> publicDelegation(Map<String, Object> delegation, LocalDateTime now) {
        Map<String, Object> result = new LinkedHashMap<>();
        String storedStatus = string(delegation.get("status"));
        String status = "ACTIVE".equals(storedStatus) && !activeDelegation(delegation, now) ? "EXPIRED" : storedStatus;
        result.put("status", status);
        result.put("effective", "ACTIVE".equals(status));
        result.put("scope", "TASK");
        result.put("scopeName", "当前审批任务");
        result.put("contextName", "PLATFORM".equals(delegation.get("contextType")) ? "平台" : "当前系统");
        result.put("principalName", string(map(delegation.get("principal")).get("displayName")));
        result.put("delegateName", string(map(delegation.get("delegate")).get("displayName")));
        for (String key : List.of("startTime", "endTime", "revokedTime", "reason")) {
            if (delegation.get(key) != null) result.put(key, delegation.get(key));
        }
        return result;
    }

    private FlowRuntimeModels.ActionResult replay(AuthenticatedContext context, FlowAction action) {
        if (!Objects.equals(action.getActedByAccountId(), context.accountId())) {
            throw conflict("FLOW_ACTION_IDEMPOTENCY_CONFLICT", "幂等键已被其他处理请求使用");
        }
        FlowInstance instance = requireScoped(context, action.getInstanceId());
        // 回执读取仍受当前查看权限约束；已完成任务无需再次满足待办状态，但撤权不能借旧键读取实例。
        requireAction(context, "VIEW_RUNTIME");
        participantResolver.currentTenantMemberId(context);
        if (context.systemId() == null && platformPerson(context, context.accountId()) == null || !canView(context, instance)) {
            throw forbidden("FLOW_INSTANCE_VIEW_DENIED", "当前身份或实例查看权限已失效");
        }
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
                .eq(FlowAction::getIdempotencyKey, idempotencyKey).last(currentRuntimeRead(), "FOR UPDATE"))
                .stream().findFirst().orElse(null);
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
        // START 已有一条包含真实 RUNNING 状态的 INSTANCE 事件，不能再生成同名 ACTION。
        if (instance != null && !"START".equals(actionCode)) {
            FlowTask handled = taskId == null ? null : taskService.selectById(taskId);
            String eventName = handled != null && "FORM_TASK".equals(handled.getTaskType()) && "APPROVE".equals(actionCode)
                    ? "表单已提交" : actionName(actionCode);
            if ("REMIND".equals(actionCode) && input.get("recipientCount") instanceof Number count) {
                eventName += "（已通知 " + count.intValue() + " 人）";
            }
            if (Set.of("ADD_SIGN", "REMOVE_SIGN", "DELEGATE", "REVOKE_DELEGATION").contains(actionCode)
                    && input.get("targetName") instanceof String targetName && !targetName.isBlank()) {
                eventName += "：" + targetName;
            }
            boolean graphChange = "RUNTIME_NODE_ADDED".equals(actionCode);
            if (graphChange) eventName = ("NOTIFICATION".equals(map(input.get("node")).get("nodeType"))
                    ? "已增加抄送：" : "已增加审批：") + string(map(input.get("node")).get("name"))
                    + "（" + switch (string(input.get("position"))) {
                        case "BEFORE_CURRENT" -> "当前节点之前";
                        case "AFTER_CURRENT" -> "当前节点之后";
                        case "BEFORE_NODE" -> "指定节点之前";
                        default -> "指定节点之后";
                    } + "）";
            String afterStatus = handled == null ? string(result.get("status")) : handled.getStatus();
            String beforeStatus = handled == null && input.get("historyBeforeStatus") instanceof String status ? status
                    : handled != null && !"MANUAL_NODE_ADDED".equals(actionCode) ? "PENDING"
                    : graphChange ? instance.getStatus() : null;
            recordHistory(instance, null, taskId, nodeKey, "ACTION", eventName,
                    beforeStatus, afterStatus, actorAccountId, actorTenantMemberId,
                    Map.of("actionId", action.getId(), "actionCode", actionCode,
                            "comment", comment == null ? "" : comment));
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
                variable.setUpdateTime(LocalDateTime.now());
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
                        .orderByAsc(FlowInstanceVariable::getVariableKey).last(currentRuntimeRead(), "FOR UPDATE"))) {
            values.put(variable.getVariableKey(), readValue(variable.getValueJson()));
        }
        return values;
    }

    private FlowRuntimeModels.InstanceView view(AuthenticatedContext context, FlowInstance instance) {
        FlowVersion version = versionService.selectById(instance.getFlowVersionId());
        FlowInstanceGraphService.Graph graph = instanceGraph(instance);
        Map<String, Object> definitionSnapshot = graph.snapshot();
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
            FlowParticipantResolver.ResolvedPerson assignee = context.systemId() == null
                    ? platformPerson(context, task.getAssigneeAccountId()) : person(context, task.getAssigneeTenantMemberId());
            List<FlowRuntimeModels.CandidateView> candidateViews = candidates(task.getId()).stream().map(candidate -> {
                Long tenantMemberId = "TENANT_MEMBER".equals(candidate.getCandidateType())
                        ? numericBusinessId(candidate.getCandidateId()) : null;
                Long platformAccountId = "PLATFORM_ACCOUNT".equals(candidate.getCandidateType())
                        ? numericBusinessId(candidate.getCandidateId()) : null;
                FlowParticipantResolver.ResolvedPerson candidatePerson = context.systemId() == null
                        ? platformPerson(context, platformAccountId) : person(context, tenantMemberId);
                return new FlowRuntimeModels.CandidateView(candidate.getId(), candidate.getCandidateType(), tenantMemberId, platformAccountId,
                        candidatePerson == null ? "已失效成员" : candidatePerson.displayName(),
                        candidatePerson == null ? null : candidatePerson.departmentName(),
                        candidatePerson == null ? null : candidatePerson.positionTitle(),
                        candidate.getResolutionReason(), candidate.getCreateTime());
            }).toList();
            FlowApprovalAttachmentPolicy attachmentPolicy = approvalAttachmentPolicy(task, definitionSnapshot);
            String assigneeName = task.getAssigneeApplicationId() == null
                    ? assignee == null ? null : assignee.displayName()
                    : externalActorName(task.getAssigneeApplicationId(), task.getAssigneeExternalPrincipalId());
            return new FlowRuntimeModels.TaskView(task.getId(), task.getNodeKey(), task.getTaskType(), task.getStatus(),
                    "PENDING".equals(task.getStatus()) && task.getCompleteTime() == null
                            && allowed(context, "HANDLE") && isCurrentHandler(context, task),
                    task.getAssigneeTenantMemberId(), assigneeName,
                    task.getAssigneeApplicationId() == null
                            ? assignee == null ? null : assignee.departmentName() : "外部应用审批",
                    assignee == null ? null : assignee.positionTitle(), publicAssigneeSnapshot(task),
                    task.getDueTime(), task.getClaimTime(), task.getCompleteTime(), task.getVersion(),
                    attachmentPolicy.required(), attachmentPolicy.minimumCount(), candidateViews);
        }).toList();
        List<FlowRuntimeModels.ActionView> actions = actionService.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId()).orderByAsc(FlowAction::getActTime)
                .orderByAsc(FlowAction::getId)).stream().map(action -> {
            FlowParticipantResolver.ResolvedPerson actor = person(context, action.getActedByTenantMemberId());
            return new FlowRuntimeModels.ActionView(action.getId(), action.getTaskId(), action.getNodeKey(),
                    action.getActionCode(), action.getCommentText(), Map.of(),
                    Map.of(), action.getIdempotencyKey(), action.getActedByTenantMemberId(),
                    action.getActorApplicationId() == null
                            ? actorName(context, action.getActedByTenantMemberId(), action.getActedByAccountId())
                            : externalActorName(action.getActorApplicationId(), action.getActorExternalPrincipalId()),
                    action.getActTime());
        }).toList();
        List<FlowRuntimeModels.ExceptionView> exceptions = exceptionService.selectList(
                Wrappers.<FlowException>lambdaQuery().eq(FlowException::getInstanceId, instance.getId())
                        .orderByAsc(FlowException::getOccurTime)).stream().map(exception -> {
            FlowParticipantResolver.ResolvedPerson resolver = person(context, exception.getResolvedByTenantMemberId());
            return new FlowRuntimeModels.ExceptionView(exception.getId(), exception.getNodeKey(),
                    exception.getExceptionType(), exception.getErrorCode(), publicErrorMessage(exception.getErrorCode()),
                    exception.getPolicyAction(), exception.getStatus(), exception.getResolvedByTenantMemberId(),
                    resolver == null ? null : resolver.displayName(), exception.getResolutionComment(),
                    exception.getOccurTime(), exception.getResolveTime(), exception.getVersion());
        }).toList();
        List<FlowRuntimeModels.ExecutionView> executions = executionService.selectList(
                Wrappers.<FlowExecution>lambdaQuery().eq(FlowExecution::getInstanceId, instance.getId())
                        .orderByAsc(FlowExecution::getEnterTime).orderByAsc(FlowExecution::getId)).stream()
                .map(execution -> new FlowRuntimeModels.ExecutionView(execution.getId(), execution.getParentExecutionId(),
                        execution.getNodeKey(), execution.getNodeType(), execution.getStatus(), execution.getEnterTime(),
                        execution.getLeaveTime(), execution.getResultCode(), execution.getVersion())).toList();
        List<FlowRuntimeModels.SubflowView> subflows = subflowLinks(context, instance);
        List<FlowHistoryEvent> storedHistory = historyService.selectList(
                Wrappers.<FlowHistoryEvent>lambdaQuery().eq(FlowHistoryEvent::getInstanceId, instance.getId())
                        .orderByAsc(FlowHistoryEvent::getOccurTime).orderByAsc(FlowHistoryEvent::getId));
        List<FlowRuntimeModels.HistoryEventView> history = java.util.stream.IntStream.range(0, storedHistory.size())
                .filter(index -> !redundantLegacyAction(storedHistory, index)).mapToObj(storedHistory::get)
                 .map(event -> new FlowRuntimeModels.HistoryEventView(event.getId(), event.getExecutionId(),
                         event.getTaskId(), event.getNodeKey(), event.getEventType(), event.getEventName(),
                         event.getActorTenantMemberId(), historyActorName(context, instance, tasks, event),
                        event.getBeforeStatus(), event.getAfterStatus(), publicHistoryDetail(event),
                        event.getOccurTime())).toList();
        List<FlowRuntimeModels.JobView> jobs = jobService.selectList(Wrappers.<FlowJob>lambdaQuery()
                        .eq(FlowJob::getInstanceId, instance.getId()).orderByAsc(FlowJob::getCreateTime)).stream()
                .map(job -> new FlowRuntimeModels.JobView(job.getId(), job.getExecutionId(), job.getNodeKey(),
                        job.getJobType(), job.getStatus(), job.getAttemptCount(), job.getMaxAttempts(), job.getNextRunTime(),
                        job.getLastErrorCode(), publicErrorMessage(job.getLastErrorCode()), job.getCompleteTime(), job.getVersion())).toList();
        FlowTask handler = currentHandlerTask(context, instance, tasks);
        FlowManualOperationPolicy operationPolicy = handler == null || "RETURN_TASK".equals(handler.getTaskType())
                ? null : manualOperationPolicy(handler, definitionSnapshot);
        List<String> allowed = allowedActions(context, instance, handler, operationPolicy);
        FlowParticipantResolver.ResolvedPerson starter = person(context, instance.getStartedByTenantMemberId());
        // 共享发起人可没有来源成员，历史仍展示真实系统成员姓名，不借权限或相信业务快照。
        String starterName = starter == null ? "用户 " + instance.getStartedByAccountId() : starter.displayName();
        if (instance.getSystemId() == null) starterName = actorName(context, null, instance.getStartedByAccountId());
        if (starter == null && instance.getSystemId() != null) {
            starterName = systemMemberService.selectList(Wrappers.<SystemMember>lambdaQuery()
                    .eq(SystemMember::getSystemId, instance.getSystemId())
                    .eq(SystemMember::getAccountId, instance.getStartedByAccountId())).stream()
                    .map(SystemMember::getDisplayName).filter(name -> name != null && !name.isBlank())
                    .findFirst().orElse(starterName);
        }
        if (instance.getSourceApplicationId() != null) {
            starterName = externalActorName(instance.getSourceApplicationId(),
                    instance.getSourceExternalPrincipalId());
        }
        boolean externalPending = tasks.stream().anyMatch(task -> task.getAssigneeApplicationId() != null
                && "PENDING".equals(task.getStatus()) && hasActiveTaskExecution(task));
        Map<String, Object> viewerNode = handler == null ? currentNode : nodes(definitionSnapshot).stream()
                .filter(node -> Objects.equals(handler.getNodeKey(), string(node.get("nodeKey"))))
                .findFirst().orElse(currentNode);
        var businessView = businessReadView(context, instance, viewerNode);
        String businessLabel = businessView == null ? "未关联业务记录"
                : businessView.current() != null ? businessView.current().title()
                : businessView.proposed() != null ? businessView.proposed().title() : "业务详情当前不可用";
        // 行范围或共享失效时，流程标题也可能来源于原业务标题，不能从流程列表继续泄露。
        String publicTitle = businessView != null && businessView.current() == null && businessView.proposed() == null
                ? "业务审批申请" : instance.getTitle();
        return new FlowRuntimeModels.InstanceView(
                instance.getId(), instance.getContextType(), instance.getPlatformId(), instance.getSystemId(),
                instance.getTenantId(), instance.getFlowId(), instance.getFlowVersionId(),
                version == null ? null : version.getVersionNumber(), version == null ? null : version.getDefinitionHash(),
                publicDefinitionSnapshot(definitionSnapshot), instance.getBusinessType(), instance.getBusinessId(),
                Map.of("label", businessLabel == null ? "已关联业务" : businessLabel), businessView, publicTitle, instance.getCurrentNodeKey(),
                string(currentNode.get("name")), instance.getStatus(), instance.getStartedByTenantMemberId(),
                Objects.equals(instance.getStartedByAccountId(), context.accountId()),
                starterName,
                instance.getStartTime(), instance.getFinishTime(), instance.getErrorCode(), publicErrorMessage(instance.getErrorCode()),
                instance.getVersion(), graph.revision(), allowed.contains("ADD_RUNTIME_NODE")
                        ? runtimeNodeAnchors(instance, definitionSnapshot) : List.of(), Map.of(), taskViews, actions, exceptions,
                executions, subflows, history, jobs, allowed,
                operationPolicy == null ? List.of() : operationPolicy.opinionRequiredActions().stream().filter(allowed::contains).toList(),
                 nextStep(instance, currentNode, handler, allowed, externalPending));
    }

    /** 运行页显示真实外部来源，应用技术执行账号不冒充外部发起人与审批人。 */
    private String externalActorName(Long applicationId, String principalId) {
        AppDefinition application = applicationService.selectById(applicationId);
        String name = application == null ? "应用 #" + applicationId : application.getName();
        return principalId == null || principalId.isBlank() ? name : name + " · " + principalId;
    }

    private String historyActorName(AuthenticatedContext context, FlowInstance instance,
            List<FlowTask> tasks, FlowHistoryEvent event) {
        if ("INSTANCE".equals(event.getEventType()) && instance.getSourceApplicationId() != null) {
            return externalActorName(instance.getSourceApplicationId(), instance.getSourceExternalPrincipalId());
        }
        if ("ACTION".equals(event.getEventType()) && event.getTaskId() != null) {
            FlowTask task = tasks.stream().filter(item -> Objects.equals(item.getId(), event.getTaskId()))
                    .findFirst().orElse(null);
            if (task != null && task.getAssigneeApplicationId() != null) {
                return externalActorName(task.getAssigneeApplicationId(), task.getAssigneeExternalPrincipalId());
            }
        }
        if ("ACTION".equals(event.getEventType()) && event.getTaskId() == null
                && "COMPLETED".equals(event.getAfterStatus())
                && instance.getSourceApplicationId() != null) return "系统自动处理";
        return actorName(context, event.getActorTenantMemberId(), event.getActorAccountId());
    }

    /** 引擎仍读取原始冻结快照；对外业务内容只走当前权限投影，绝不从 variables 补回。 */
    private com.unique.unexamine.runtimedata.manage.RuntimeBusinessView businessReadView(
            AuthenticatedContext viewer, FlowInstance instance, Map<String, Object> node) {
        return businessReadView(viewer, instance, node, List.of("PAGE", "FLOW"));
    }

    /** 本人窄进度是 PAGE 业务读入口；完整审批页另与 FLOW 交集，均受同一冻结节点策略限制。 */
    private com.unique.unexamine.runtimedata.manage.RuntimeBusinessView businessReadView(
            AuthenticatedContext viewer, FlowInstance instance, Map<String, Object> node, List<String> channels) {
        var bound = actionCoordinator.businessReadForFlow(instance);
        Map<String, Object> policy = new LinkedHashMap<>(map(node.get("formPolicy")));
        // 身份绑定只从发布版本补入，节点自定义配置不能伪造绑定来扩大访问。
        policy.remove("fieldBindings");
        Map<String, Object> formContext = map(snapshot(instance).get("formContext"));
        if (!formContext.isEmpty()) {
            policy.put("fieldBindings", ("MODULE:" + string(formContext.get("moduleCode"))).equals(instance.getBusinessType())
                    ? formContext.getOrDefault("fields", List.of()) : List.of());
        }
        if (bound != null) {
            var original = runtimeDataService.approvalBusinessView(viewer, bound.input(), bound.configurationSnapshot(),
                    bound.recordId(), policy, channels, "flow-read:" + instance.getId());
            var submitted = formSubmissions.submittedForRead(bound.input(), instance);
            var projection = submitted == null ? null : runtimeDataService.approvalBusinessView(viewer, submitted,
                    bound.configurationSnapshot(), bound.recordId(), policy, channels, "flow-submitted-read:" + instance.getId());
            return new com.unique.unexamine.runtimedata.manage.RuntimeBusinessView(original.moduleCode(), original.actionCode(),
                    original.proposed(), original.current(), original.message(), projection == null ? null : projection.proposed());
        }
        if (instance.getBusinessType() != null && instance.getBusinessType().startsWith("MODULE:")) {
            Long recordId = numericBusinessId(instance.getBusinessId());
            if (recordId != null) return runtimeDataService.flowRecordView(viewer,
                    instance.getBusinessType().substring("MODULE:".length()), recordId, instance.getTenantId(), policy,
                    "flow-read:" + instance.getId());
        }
        return null;
    }

    /** 普通运行详情仅返回流程拓扑，不返回条件常量、字段默认值、请求或业务动作参数。 */
    private Map<String, Object> publicDefinitionSnapshot(Map<String, Object> snapshot) {
        List<Map<String, Object>> publicNodes = nodes(snapshot).stream().map(node -> {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String key : List.of("nodeKey", "name", "nodeType", "position")) if (node.get(key) != null) safe.put(key, node.get(key));
            return safe;
        }).toList();
        List<Map<String, Object>> publicEdges = edges(snapshot).stream().map(edge -> {
            Map<String, Object> safe = new LinkedHashMap<>();
            for (String key : List.of("edgeKey", "sourceNodeKey", "targetNodeKey")) if (edge.get(key) != null) safe.put(key, edge.get(key));
            return safe;
        }).toList();
        return Map.of("nodes", publicNodes, "edges", publicEdges);
    }

    /** 分配说明可展示，原候选表达式和节点表单策略不作为业务数据旁路返回。 */
    private Map<String, Object> publicAssigneeSnapshot(FlowTask task) {
        Map<String, Object> stored = readMap(task.getAssigneeSnapshotJson());
        Map<String, Object> result = new LinkedHashMap<>();
        if (stored.get("reason") instanceof String text) result.put("reason", text);
        if (stored.get("executionId") instanceof Number executionId) result.put("executionId", executionId.longValue());
        String mode = string(stored.get("approvalMode"));
        if (Set.of("OR_SIGN", "ALL_SIGN", "SEQUENTIAL").contains(mode)) result.put("approvalMode", mode);
        if ("ALL_SIGN".equals(mode)) {
            Map<String, Object> config = stored.get(FlowApprovalCompletionPolicy.CONFIG_KEY) instanceof Map<?, ?>
                    ? Map.of(FlowApprovalCompletionPolicy.CONFIG_KEY,
                            map(stored.get(FlowApprovalCompletionPolicy.CONFIG_KEY))) : Map.of();
            result.put(FlowApprovalCompletionPolicy.CONFIG_KEY,
                    FlowApprovalCompletionPolicy.fromConfig(config, mode).snapshot());
        }
        if ("RETURN_TASK".equals(task.getTaskType())) {
            result.put("returnTask", true);
            result.put("returnNodeName", string(stored.get("returnNodeName")));
            Map<String, Object> policy = map(stored.get("returnPolicy"));
            result.put("returnTarget", string(policy.get("target")));
            result.put("resubmitRoute", string(policy.get("resubmitRoute")));
        }
        Map<String, Object> delegation = map(stored.get("delegation"));
        if (!delegation.isEmpty()) result.put("delegation", publicDelegation(delegation, LocalDateTime.now()));
        return result;
    }

    /** 旧版本同时写入 ACTION 与相邻 INSTANCE/TASK；只隐藏重复投影，持久事实仍完整保留。 */
    private boolean redundantLegacyAction(List<FlowHistoryEvent> events, int index) {
        FlowHistoryEvent current = events.get(index);
        if (!"ACTION".equals(current.getEventType())) return false;
        for (int neighbor : List.of(index - 1, index + 1)) {
            if (neighbor < 0 || neighbor >= events.size()) continue;
            FlowHistoryEvent other = events.get(neighbor);
            if (!Set.of("INSTANCE", "TASK").contains(other.getEventType())
                    || !Objects.equals(current.getNodeKey(), other.getNodeKey())
                    || !Objects.equals(current.getActorAccountId(), other.getActorAccountId())) continue;
            String left = current.getEventName(); String right = other.getEventName();
            if ("流程已发起".equals(left) && left.equals(right)) return true;
            if (List.of("待办已领取", "已取消领取", "同节点加签", "同节点减签").stream()
                    .anyMatch(prefix -> left.startsWith(prefix) && right.startsWith(prefix))) return true;
        }
        return false;
    }

    /** 原异常可能包含字段值或远程响应；普通详情仅提示错误类型和恢复入口。 */
    private String publicErrorMessage(String code) {
        if (code == null || code.isBlank()) return null;
        return switch (code) {
            case "FLOW_NODE_FIELD_REQUIRED" -> "候选内容仍有必填项，请由有权处理人补充后审批。";
            case "FLOW_TASK_TIMEOUT" -> "处理已超时，按配置等待人工处理。";
            case "FLOW_TIMEOUT_TARGET_INVALID", "FLOW_PARTICIPANT_INVALID" -> "超时处理人员已失效或不属于当前范围，请重新指定有效人员。";
            case "PERMISSION_DENIED", "ACTION_ACTOR_INACTIVE" -> "原发起人当前身份或业务权限已失效，未执行自动处理。";
            case "ACTION_CONFIGURATION_VERSION_CONFLICT" -> "业务或节点字段配置已变化，未执行旧配置下的自动处理。";
            case "FLOW_NODE_FORM_NOT_AVAILABLE" -> "该节点需要人工提交，或尚未关联可安全核对的候选表单。";
            case "FLOW_JOB_EXECUTION_FAILED" -> "后台作业执行失败，待办已保留，请人工处理或联系管理员。";
            case "FLOW_WAIT_TIMER_CONFIG_INVALID" -> "等待或定时器配置已经失效，请重新检查并发布流程。";
            case "FLOW_MESSAGE_RECIPIENT_UNRESOLVED", "MESSAGE_RECIPIENT_INVALID", "MESSAGE_RECIPIENT_CONTEXT_INVALID" ->
                    "消息接收人已失效或不属于当前范围，请重新选择有效人员后处理异常。";
            case "FLOW_MESSAGE_NODE_INVALID", "FLOW_MESSAGE_EXECUTION_INVALID" ->
                    "消息节点冻结配置或执行现场已变化，请重新检查流程配置。";
            case "FLOW_WEBHOOK_TIMEOUT" -> "已发布应用回调响应超时，系统会按冻结策略重试。";
            case "FLOW_WEBHOOK_UNAVAILABLE", "FLOW_WEBHOOK_INTERRUPTED" ->
                    "已发布应用回调当前不可用，系统会按冻结策略重试。";
            case "FLOW_WEBHOOK_RESPONSE_REJECTED" -> "已发布应用回调拒绝本次请求，请检查接收服务后重试。";
            case "FLOW_WEBHOOK_CONFIG_INVALID", "FLOW_WEBHOOK_JOB_INVALID", "FLOW_WEBHOOK_EXECUTION_INVALID",
                    "WEBHOOK_SECRET_REFERENCE_INVALID", "WEBHOOK_SECRET_REFERENCE_SCOPE_INVALID",
                    "WEBHOOK_SECRET_REFERENCE_NOT_FOUND", "WEBHOOK_SECRET_VERSION_NOT_AVAILABLE",
                    "WEBHOOK_SECRET_MATERIAL_NOT_AVAILABLE", "WEBHOOK_SECRET_MATERIAL_INVALID" ->
                    "应用回调发布快照或签名密钥已失效，请修正应用配置并重新发布流程。";
            case "FLOW_SUBFLOW_CONFIG_INVALID" -> "子流程配置无效，请修正节点配置并重新发布。";
            case "FLOW_SUBFLOW_VERSION_INVALID" -> "子流程冻结版本已失效或不属于当前范围，请重新发布父流程。";
            case "FLOW_SUBFLOW_DEPTH_EXCEEDED" -> "子流程调用层级超过系统上限，已停止继续启动。";
            case "FLOW_SUBFLOW_INPUT_MISSING" -> "父流程缺少子流程所需的传入变量，请补充数据后重试。";
            case "FLOW_SUBFLOW_OUTPUT_MISSING" -> "子流程未返回父流程要求的变量，可检查子流程结果后恢复。";
            case "FLOW_SUBFLOW_CHILD_REJECTED" -> "子流程已拒绝，父流程等待人工确认后恢复。";
            case "FLOW_SUBFLOW_CHILD_TERMINATED", "FLOW_SUBFLOW_CHILD_WITHDRAWN" -> "子流程已终止，父流程等待人工确认后恢复。";
            default -> "流程处理发生异常，请按错误类型处理或联系管理员。";
        };
    }

    /** 并行分支可同时存在多个真实待办；页面选择当前查看者可处理的一条，暂停任务不获得权限。 */
    private FlowTask currentHandlerTask(AuthenticatedContext context, FlowInstance instance, List<FlowTask> tasks) {
        if (!"WAITING".equals(instance.getStatus()) || !allowed(context, "HANDLE")) return null;
        return tasks.stream().filter(task -> "PENDING".equals(task.getStatus()) && task.getCompleteTime() == null
                && isCurrentHandler(context, task) && hasActiveTaskExecution(task))
                .findFirst().orElse(null);
    }

    private boolean hasActiveTaskExecution(FlowTask task) {
        FlowExecution execution = taskExecution(task);
        return execution != null && "ACTIVE".equals(execution.getStatus());
    }

    /** 运行页只公开网关计数和节点键，不把条件值、业务变量或内部异常详情带给查看者。 */
    private Map<String, Object> publicHistoryDetail(FlowHistoryEvent event) {
        if (event == null || event.getEventType() == null) return Map.of();
        boolean gateway = event.getEventType().startsWith("GATEWAY_");
        boolean subflow = event.getEventType().startsWith("SUBFLOW_");
        boolean automatic = "BUSINESS_DATA_ACTION".equals(event.getEventType());
        boolean message = event.getEventType().startsWith("FLOW_MESSAGE_");
        boolean webhook = event.getEventType().startsWith("FLOW_WEBHOOK_");
        boolean wait = event.getEventType().startsWith("FLOW_WAIT_");
        if (!gateway && !subflow && !automatic && !message && !webhook && !wait) return Map.of();
        Map<String, Object> stored = readMap(event.getDetailJson());
        LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
        List<String> keys = gateway
                ? List.of("gatewayMode", "mergeNodeKey", "selectedTargetNodeKeys",
                        "selectedCount", "arrivedCount", "expectedCount", "duplicate")
                : subflow ? List.of("childInstanceId", "childFlowId", "childFlowName", "childFlowVersionId",
                        "childFlowVersionNumber", "status", "errorCode", "outputCount")
                : automatic ? List.of("operation", "moduleCode", "moduleName", "recordId", "fieldNames",
                        "actionId", "replayed", "resultStatus")
                : wait ? List.of("jobId", "waitMode", "durationMinutes", "nextRunAt")
                : webhook ? List.of("jobId", "applicationCode", "applicationVersion", "callbackId",
                        "responseStatus", "attemptCount", "maxAttempts", "deliveryStatus", "replayed", "errorCode")
                : List.of("jobId", "messageKind", "recipientCount", "attemptCount", "maxAttempts",
                        "deliveryStatus", "replayed", "errorCode");
        for (String key : keys) {
            Object value = stored.get(key);
            if (value instanceof String || value instanceof Number || value instanceof Boolean
                    || value instanceof List<?> list && list.stream().allMatch(String.class::isInstance)) safe.put(key, value);
        }
        return safe;
    }

    /** 只读取受信运行图；原有手动加签没有图节点时保留明确兼容，普通节点缺失则拒绝。 */
    private FlowManualOperationPolicy manualOperationPolicy(FlowTask task, Map<String, Object> snapshot) {
        Map<String, Object> node = nodes(snapshot).stream()
                .filter(candidate -> Objects.equals(task.getNodeKey(), candidate.get("nodeKey"))).findFirst().orElse(null);
        if (node == null && "MANUAL_APPROVAL".equals(task.getTaskType())) return FlowManualOperationPolicy.fromConfig(Map.of());
        if (node == null || !Set.of("APPROVAL", "FORM_TASK").contains(node.get("nodeType"))) {
            throw conflict("FLOW_TASK_NODE_INVALID", "任务的冻结人工节点不存在，请读取实例最新状态");
        }
        return FlowManualOperationPolicy.fromConfig(map(node.get("config")));
    }

    /** 返回任务冻结节点的附件要求；退回修正和运行时旧任务默认不要求附件。 */
    private FlowApprovalAttachmentPolicy approvalAttachmentPolicy(FlowTask task, Map<String, Object> snapshot) {
        if (task == null || "RETURN_TASK".equals(task.getTaskType())) {
            return FlowApprovalAttachmentPolicy.fromConfig(Map.of());
        }
        Map<String, Object> node = nodes(snapshot).stream()
                .filter(candidate -> Objects.equals(task.getNodeKey(), candidate.get("nodeKey"))).findFirst().orElse(null);
        if (node == null || !Set.of("APPROVAL", "FORM_TASK").contains(node.get("nodeType"))) {
            return FlowApprovalAttachmentPolicy.fromConfig(Map.of());
        }
        return FlowApprovalAttachmentPolicy.fromConfig(map(node.get("config")));
    }

    private List<String> allowedActions(
            AuthenticatedContext context, FlowInstance instance, FlowTask handler, FlowManualOperationPolicy policy) {
        ArrayList<String> actions = new ArrayList<>();
        if (handler != null && "RETURN_TASK".equals(handler.getTaskType())) actions.add("RESUBMIT");
        else if (policy != null) actions.addAll(manualTaskActions(context, handler, policy));
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())
                && Objects.equals(instance.getStartedByAccountId(), context.accountId())) actions.add("WITHDRAW");
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus()) && allowed(context, "TERMINATE")) actions.add("TERMINATE");
        actions.add("COMMENT");
        if (ACTIVE_INSTANCE_STATUSES.contains(instance.getStatus())
                && (Objects.equals(instance.getStartedByAccountId(), context.accountId())
                || handler != null && allowed(context, "HANDLE"))) actions.add("REMIND");
        Map<String, Object> graph = snapshot(instance);
        if (handler != null && !"RETURN_TASK".equals(handler.getTaskType())
                && allowed(context, "ADD_MANUAL_NODE") && dynamicNodesEnabled(graph) && runtimeNodeAnchor(instance, graph)) actions.add("ADD_RUNTIME_NODE");
        return List.copyOf(actions);
    }

    private String nextStep(FlowInstance instance, Map<String, Object> node, FlowTask handler,
            List<String> allowed, boolean externalPending) {
        if ("COMPLETED".equals(instance.getStatus())) return "Flow 已完成";
        if (allowed.contains("RESUBMIT") && handler != null) {
            Map<String, Object> policy = map(readMap(handler.getAssigneeSnapshotJson()).get("returnPolicy"));
            return "请修正申请并重新提交，之后" + ("FROM_START".equals(policy.get("resubmitRoute"))
                    ? "从流程开始重新执行" : "回到“" + string(readMap(handler.getAssigneeSnapshotJson()).get("returnNodeName")) + "”继续");
        }
        if (allowed.contains("CLAIM")) return "请先领取待办，再处理节点“" + string(node.get("name")) + "”";
        if ("EXCEPTION".equals(instance.getStatus())) return "按异常策略等待人工处理";
        if (allowed.contains("APPROVE")) return ("FORM_TASK".equals(string(node.get("nodeType")))
                ? "请完成填写节点“" : "请处理审批节点“") + string(node.get("name")) + "”";
        if ("WAITING".equals(instance.getStatus())) {
            String nodeType = string(node.get("nodeType"));
            if (FlowWaitTimerPolicy.supports(nodeType)) {
                boolean absolute = FlowWaitTimerPolicy.ABSOLUTE.equals(string(map(node.get("config")).get("waitMode")));
                return absolute ? "将在指定时间到达后自动继续" : "等待时长结束后自动继续";
            }
            if (Set.of("CC", "NOTIFICATION", "MESSAGE").contains(nodeType)) return "等待站内消息可靠投递完成";
            if (FlowExternalCallbackPolicy.supports(nodeType)) return "等待已发布应用回调可靠投递完成";
            // 没有人工待办的等待态来自后台作业，不能展示成已有审批人在处理。
            if (handler == null && externalPending) return "等待外部应用审批人完成“" + string(node.get("name")) + "”";
            if (handler == null) return "等待后台作业完成后自动继续";
            return "等待处理人完成“" + string(node.get("name")) + "”";
        }
        return switch (instance.getStatus()) {
            case "REJECTED" -> "流程已拒绝";
            case "RETURNED" -> "流程已退回";
            case "WITHDRAWN" -> "流程已撤回";
            case "TERMINATED" -> "流程已终止";
            default -> "当前状态：" + instance.getStatus();
        };
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
        requireDynamicNodesEnabled(snapshot(instance));
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
        resume.setStatus("PENDING"); resume.setUpdateTime(LocalDateTime.now());
        if (taskService.updateById(resume) != 1) {
            throw conflict("FLOW_MANUAL_RESUME_TASK_INVALID", "原审批任务已被其他操作更新");
        }
        instance.setCurrentNodeKey(resume.getNodeKey()); instance.setStatus("WAITING");
        instance.setUpdateTime(LocalDateTime.now());
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
        return tasks(instance.getId()).stream().anyMatch(task -> isCurrentHandler(context, task));
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
                .eq(FlowTask::getInstanceId, instanceId).orderByAsc(FlowTask::getCreateTime)
                .orderByAsc(FlowTask::getId).last(currentRuntimeRead(), "FOR UPDATE"));
    }

    /** 同一节点可因退回重提多次进入；新任务按执行号分组，旧快照继续按节点兼容。 */
    private Long taskExecutionId(FlowTask task) {
        Object value = readMap(task.getAssigneeSnapshotJson()).get("executionId");
        if (value instanceof Number number && number.longValue() > 0) return number.longValue();
        return null;
    }

    /** 新任务保存确切执行号；旧任务才回退到节点活动执行读取。 */
    private FlowExecution taskExecution(FlowTask task) {
        Long executionId = taskExecutionId(task);
        FlowExecution execution = executionId == null ? activeExecution(task.getInstanceId(), task.getNodeKey())
                : executionService.selectById(executionId);
        if (execution == null || !Objects.equals(execution.getInstanceId(), task.getInstanceId())
                || !Objects.equals(execution.getNodeKey(), task.getNodeKey())) return null;
        return execution;
    }

    private boolean sameTaskExecution(FlowTask anchor, FlowTask candidate) {
        Long anchorExecution = taskExecutionId(anchor);
        Long candidateExecution = taskExecutionId(candidate);
        if (anchorExecution != null || candidateExecution != null) return Objects.equals(anchorExecution, candidateExecution);
        return Objects.equals(anchor.getNodeKey(), candidate.getNodeKey());
    }

    /** 仅写事务读取锁定实例的当前状态；普通详情/候选表单读取不持写锁。 */
    private boolean currentRuntimeRead() {
        return org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()
                && !org.springframework.transaction.support.TransactionSynchronizationManager.isCurrentTransactionReadOnly();
    }

    private List<FlowTaskCandidate> candidates(Long taskId) {
        return candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                .eq(FlowTaskCandidate::getTaskId, taskId).orderByAsc(FlowTaskCandidate::getId));
    }

    private Map<String, Object> snapshot(FlowInstance instance) {
        return instanceGraph(instance).snapshot();
    }

    /** 运行中录入的抄送说明按原文发送，不能借模板变量表达式读取审批业务的隐藏内容。 */
    private String notificationText(Map<String, Object> node, String text, FlowInstance instance, Map<String, Object> variables) {
        return string(node.get("nodeKey")).startsWith("runtime_") ? text : renderFlowText(text, instance, variables);
    }

    private FlowInstanceGraphService.Graph instanceGraph(FlowInstance instance) {
        FlowVersion version = versionService.selectById(instance.getFlowVersionId());
        if (version == null || !Objects.equals(version.getFlowId(), instance.getFlowId())) {
            throw invalid("FLOW_RUNTIME_VERSION_INCOMPATIBLE", "实例冻结版本不存在或不兼容");
        }
        return instanceGraphs.load(instance, readMap(version.getSnapshotJson()));
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
        return recordExecution(instance, nodeKey, nodeType, status, resultCode, null);
    }

    private FlowExecution recordExecution(
            FlowInstance instance, String nodeKey, String nodeType, String status, String resultCode,
            Long parentExecutionId) {
        FlowExecution execution = new FlowExecution();
        execution.setInstanceId(instance.getId());
        execution.setParentExecutionId(parentExecutionId);
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
        execution.setLeaveTime(LocalDateTime.now());
        executionService.updateById(execution);
    }

    private FlowExecution activeExecution(Long instanceId, String nodeKey) {
        return executionService.selectList(Wrappers.<FlowExecution>lambdaQuery()
                        .eq(FlowExecution::getInstanceId, instanceId)
                        .eq(FlowExecution::getNodeKey, nodeKey)
                        .eq(FlowExecution::getStatus, "ACTIVE")
                        .orderByDesc(FlowExecution::getEnterTime).last(currentRuntimeRead() ? "LIMIT 1 FOR UPDATE" : "LIMIT 1"))
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
        job.setNextRunTime(nextRunAt == null ? LocalDateTime.now() : nextRunAt);
        job.setVersion(0);
        jobService.insert(job);
        boolean waitJob = "TIMER".equals(jobType);
        String waitMode = payload == null ? "" : String.valueOf(payload.get("waitMode"));
        LinkedHashMap<String, Object> historyDetail = new LinkedHashMap<>();
        historyDetail.put("jobType", jobType);
        historyDetail.put("maxAttempts", job.getMaxAttempts());
        historyDetail.put("nextRunAt", job.getNextRunTime().toString());
        if (waitJob) {
            historyDetail.put("waitMode", waitMode);
            Object durationMinutes = payload == null ? null : payload.get("durationMinutes");
            if (durationMinutes != null) historyDetail.put("durationMinutes", durationMinutes);
        }
        recordHistory(instance, execution == null ? null : execution.getId(), null, nodeKey,
                waitJob ? "FLOW_WAIT_SCHEDULED" : "JOB", switch (jobType) {
                    case "TIMER" -> FlowWaitTimerPolicy.ABSOLUTE.equals(waitMode)
                            ? "已安排定时器恢复" : "已安排等待恢复";
                    case "TASK_TIMEOUT" -> "已安排审批超时检查";
                    case "MESSAGE_DELIVERY" -> "站内消息已进入可靠投递队列";
                    case "WEBHOOK_DELIVERY" -> "应用回调已进入可靠投递队列";
                    default -> "已安排失败重试";
                },
                instance.getStatus(), "WAITING", null, instance.getStartedByTenantMemberId(),
                historyDetail);
        return job;
    }

    private FlowParticipantResolver.ResolvedPerson person(AuthenticatedContext context, Long tenantMemberId) {
        if (tenantMemberId == null || context.systemId() == null) return null;
        try { return participantResolver.requirePerson(context, tenantMemberId); }
        catch (DomainException ignored) { return null; }
    }

    private FlowParticipantResolver.ResolvedPerson platformPerson(AuthenticatedContext context, Long accountId) {
        if (accountId == null || context.systemId() != null) return null;
        try { return participantResolver.requirePlatformPerson(context, accountId); }
        catch (DomainException ignored) { return null; }
    }

    /** 平台历史按真实账号解析显示名称，不把没有系统成员ID的人写成系统自动处理。 */
    private String actorName(AuthenticatedContext context, Long tenantMemberId, Long accountId) {
        if (context.systemId() != null) return displayName(context, tenantMemberId);
        if (accountId == null) return "系统自动处理";
        var actor = platformPerson(context, accountId);
        return actor == null ? "已失效平台成员" : actor.displayName();
    }

    private String displayName(AuthenticatedContext context, Long tenantMemberId) {
        FlowParticipantResolver.ResolvedPerson person = person(context, tenantMemberId);
        return person == null ? "系统自动处理" : person.displayName();
    }

    private Map<String, Object> personSnapshot(FlowParticipantResolver.ResolvedPerson person) {
        LinkedHashMap<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("tenantMemberId", person.tenantMemberId());
        snapshot.put("accountId", person.accountId());
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
            case "RETURN" -> "审批退回";
            case "RESUBMIT" -> "发起人已修正并重新提交";
            case "TRANSFER" -> "转交审批";
            case "DELEGATE" -> "审批已委托";
            case "REVOKE_DELEGATION" -> "审批委托已撤销";
            case "COMMENT" -> "发表了流程评论";
            case "REMIND" -> "催办当前处理人";
            case "CLAIM" -> "待办已领取";
            case "UNCLAIM" -> "已取消领取";
            case "ADD_SIGN" -> "同节点加签";
            case "REMOVE_SIGN" -> "同节点减签";
            case "WITHDRAW" -> "发起人撤回";
            case "TERMINATE" -> "管理员终止";
            case "MANUAL_NODE_ADDED" -> "增加临时审批";
            case "RUNTIME_NODE_ADDED" -> "实例已增加后续节点";
            case "RUNTIME_NODE_RESUMED" -> "新增节点完成，恢复原节点待办";
            case "INCIDENT_RETRY" -> "异常已安排重试";
            case "INCIDENT_RESUME" -> "异常已确认并继续";
            case "AUTO_END" -> "流程已完成";
            case "AUTO_EXECUTED" -> "自动节点已完成";
            case "GATEWAY_JOIN" -> "分支已汇合";
            case "SUBFLOW_PARENT" -> "已关联父流程";
            case "SUBFLOW_STARTED" -> "子流程已启动";
            case "SUBFLOW_COMPLETED" -> "子流程已完成";
            case "SUBFLOW_CHILD_EXCEPTION" -> "子流程发生异常";
            case "SUBFLOW_PARENT_TERMINATED" -> "父流程结束，子流程同步终止";
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
