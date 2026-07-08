package com.unique.examine.flow.manage.definition;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.base.entity.FlowDefinition;
import com.unique.examine.flow.base.entity.FlowEdge;
import com.unique.examine.flow.base.entity.FlowInstance;
import com.unique.examine.flow.base.entity.FlowNode;
import com.unique.examine.flow.base.entity.FlowSimulationLog;
import com.unique.examine.flow.base.entity.FlowSnapshot;
import com.unique.examine.flow.base.service.FlowDefinitionBaseService;
import com.unique.examine.flow.base.service.FlowEdgeBaseService;
import com.unique.examine.flow.base.service.FlowInstanceBaseService;
import com.unique.examine.flow.base.service.FlowNodeBaseService;
import com.unique.examine.flow.base.service.FlowSimulationLogBaseService;
import com.unique.examine.flow.base.service.FlowSnapshotBaseService;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ApprovalPropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.BranchLabelVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.CanvasSaveRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.CanvasVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.CanvasValidationSummary;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ConditionDecisionVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ConditionExpression;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ConditionPropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.EdgeSaveRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.EndPropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ExternalApiPropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FieldPermissionPolicy;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FieldUpdatePropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FieldUpdateRule;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowDefinitionVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowEdgeVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowNodeConfigVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowQueryRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSaveRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSimulationRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSimulationResult;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowSnapshotVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ImpactAnalysisVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.ImpactRef;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.NodeLibraryItem;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.NodePropertyPanelVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.NodeSaveRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PositionVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PropertyFieldMeta;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishCheckItem;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishCheckResultVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishRequest;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.PublishResult;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.RetryPolicy;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.SimulationApproverVO;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.SimulationStepTrace;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.TimeoutPolicy;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.TimeoutReminderPropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.TimerPropertyPayload;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.TriggerRule;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contract-first flow definition and configuration service.
 */
@Service
public class FlowDefinitionService {

    private static final String DEFAULT_FLOW_ID = "flow_business_approval";
    private static final String DEFAULT_TENANT_ID = "tenant_default";
    private static final String DEFAULT_MODULE_ID = "mod_business";
    private static final String DEFAULT_VERSION = "flow_v20260623_001";
    private static final String DEFAULT_PERMISSION_VERSION = "perm_20260623_001";
    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String PUBLISH_STATUS_DRAFT = "DRAFT";
    private static final String PUBLISH_STATUS_PUBLISHED = "PUBLISHED";
    private static final TypeReference<List<FlowNodeConfigVO>> NODE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<FlowEdgeVO>> EDGE_LIST_TYPE = new TypeReference<>() {
    };

    private final SystemMemberContextResolver contextResolver;
    private final FlowDefinitionBaseService flowDefinitionBaseService;
    private final FlowNodeBaseService flowNodeBaseService;
    private final FlowEdgeBaseService flowEdgeBaseService;
    private final FlowInstanceBaseService flowInstanceBaseService;
    private final FlowSnapshotBaseService flowSnapshotBaseService;
    private final FlowSimulationLogBaseService flowSimulationLogBaseService;
    private final ObjectMapper objectMapper;

    public FlowDefinitionService(SystemMemberContextResolver contextResolver,
                                 FlowDefinitionBaseService flowDefinitionBaseService,
                                 FlowNodeBaseService flowNodeBaseService,
                                 FlowEdgeBaseService flowEdgeBaseService,
                                 FlowInstanceBaseService flowInstanceBaseService,
                                 FlowSnapshotBaseService flowSnapshotBaseService,
                                 FlowSimulationLogBaseService flowSimulationLogBaseService,
                                 ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.flowDefinitionBaseService = flowDefinitionBaseService;
        this.flowNodeBaseService = flowNodeBaseService;
        this.flowEdgeBaseService = flowEdgeBaseService;
        this.flowInstanceBaseService = flowInstanceBaseService;
        this.flowSnapshotBaseService = flowSnapshotBaseService;
        this.flowSimulationLogBaseService = flowSimulationLogBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Search flow definitions with canvas metadata.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param query query filters
     * @return flow definition page
     */
    public PageResult<FlowDefinitionVO> flows(String systemId, PageRequest pageRequest, FlowQueryRequest query) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        LambdaQueryWrapper<FlowDefinition> wrapper = flowQuery(context, query);
        long total = flowDefinitionBaseService.count(wrapper);
        List<FlowDefinitionVO> records = flowDefinitionBaseService.list(flowQuery(context, query)
                        .orderByDesc(FlowDefinition::getUpdatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Save a flow header and optional canvas draft.
     *
     * @param systemId system id
     * @param flowId optional flow id
     * @param request save request
     * @return saved flow definition
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDefinitionVO saveFlow(String systemId, String flowId, FlowSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        requireText(request == null ? null : request.flowCode(), "流程编码不能为空");
        requireText(request.flowName(), "流程名称不能为空");
        FlowDefinition flow = hasText(flowId) ? requireFlow(context, flowId) : new FlowDefinition();
        if (Objects.isNull(flow.getId())) {
            ensureFlowCodeUnique(context, request.flowCode());
            flow.setSystemId(context.systemId());
            flow.setTenantId(context.tenantId());
            flow.setFlowCode(request.flowCode());
            flow.setCreatedAt(LocalDateTime.now());
            flow.setDeleted(DELETED_NO);
        }
        if (!Objects.equals(flow.getFlowCode(), request.flowCode())) {
            ensureFlowCodeUnique(context, request.flowCode());
            flow.setFlowCode(request.flowCode());
        }
        flow.setFlowName(request.flowName());
        flow.setBoundModuleId(parseOptionalId(request.boundModuleId()));
        flow.setTriggerRule(toJson(request.triggerRule() == null ? defaultTriggerRule() : request.triggerRule()));
        flow.setStatus(request.status() == null ? ENABLED : request.status());
        flow.setUpdatedAt(LocalDateTime.now());
        if (Objects.isNull(flow.getId())) {
            flowDefinitionBaseService.saveEntity(flow);
            replaceCanvas(flow.getId(), canvasFromRequest(String.valueOf(flow.getId()), request.canvas()));
        } else {
            flowDefinitionBaseService.updateById(flow);
            if (request.canvas() != null) {
                replaceCanvas(flow.getId(), canvasFromRequest(String.valueOf(flow.getId()), request.canvas()));
            }
        }
        return toVO(flow);
    }

    /**
     * Return a flow detail.
     *
     * @param systemId system id
     * @param flowId flow id
     * @return flow definition detail
     */
    public FlowDefinitionVO flowDetail(String systemId, String flowId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        return toVO(requireFlow(context, flowId));
    }

    /**
     * Return node library metadata for the flow designer.
     *
     * @param systemId system id
     * @return node library items
     */
    public List<NodeLibraryItem> nodeLibrary(String systemId) {
        return List.of(
                libraryItem("approval", "审批", "人工处理", "user-check", "按成员、角色或部门派发审批任务",
                        approvalPayload(), List.of("FLOW_APPROVAL_TASK")),
                libraryItem("condition", "条件分支", "流转控制", "git-branch", "按字段表达式生成分支标签和流转路径",
                        conditionPayload(), List.of("FLOW_CONDITION")),
                libraryItem("field_update", "字段更新", "自动动作", "replace", "在流程节点中写回指定业务字段",
                        fieldUpdatePayload(), List.of("MODULE_FIELD_WRITE")),
                libraryItem("external_api", "外部 API", "集成动作", "plug", "调用对外应用授权范围内的外部接口",
                        externalApiPayload(), List.of("OPENAPI_APP_CALL")),
                libraryItem("timer", "定时器", "时间控制", "clock", "按延迟或计划时间继续推进流程",
                        timerPayload(), List.of("FLOW_TIMER")),
                libraryItem("timeout_reminder", "超时提醒", "提醒动作", "bell-ring", "对指定审批节点发送超时提醒或升级",
                        timeoutReminderPayload(), List.of("MESSAGE_TEMPLATE")),
                libraryItem("end", "结束", "终止节点", "circle-stop", "结束当前发布版本的流程路径",
                        endPayload(), List.of("FLOW_END"))
        );
    }

    /**
     * Return canvas nodes, edges, and branch labels.
     *
     * @param systemId system id
     * @param flowId flow id
     * @return canvas view
     */
    public CanvasVO canvas(String systemId, String flowId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(context, flowId);
        return canvasFromDb(flow);
    }

    /**
     * Save canvas draft without starting workflow runtime.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param request canvas request
     * @return flow detail with updated canvas
     */
    @Transactional(rollbackFor = Exception.class)
    public FlowDefinitionVO saveCanvas(String systemId, String flowId, CanvasSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(context, flowId);
        replaceCanvas(flow.getId(), canvasFromRequest(String.valueOf(flow.getId()), request));
        flow.setUpdatedAt(LocalDateTime.now());
        flowDefinitionBaseService.updateById(flow);
        return toVO(flow);
    }

    /**
     * Return the property panel schema for a node.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param nodeKey node key
     * @return property panel
     */
    public NodePropertyPanelVO nodeProperties(String systemId, String flowId, String nodeKey) {
        return nodePanel(nodeTypeByNodeKey(nodeKey));
    }

    /**
     * Simulate the draft or selected snapshot and return a non-mutating path prediction.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param request simulation request
     * @return simulation result
     */
    public FlowSimulationResult simulate(String systemId, String flowId, FlowSimulationRequest request) {
        SystemMemberContext systemContext = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(systemContext, flowId);
        RequestContext context = RequestContext.current();
        long instanceCountBefore = flowInstanceCount(flow);
        FlowSimulationRequest safeRequest = request == null
                ? new FlowSimulationRequest(null, null, "DRAFT", Map.of(), null, null, null)
                : request;
        CanvasVO canvas = simulationCanvas(flow, safeRequest);
        List<PublishCheckItem> blockers = new ArrayList<>();
        if (canvas.nodes().isEmpty()) {
            blockers.add(simulationBlocker(flow, "E_SIM_NO_NODE", "模拟缺少节点", "流程画布没有可模拟节点。", "添加审批、条件或结束节点"));
        }
        if (!canvas.validationSummary().hasEndNode()) {
            blockers.add(simulationBlocker(flow, "E_SIM_NO_END", "模拟缺少结束节点", "流程模拟必须存在结束节点。", "添加结束节点"));
        }
        if (!canvas.validationSummary().connected()) {
            blockers.add(simulationBlocker(flow, "E_SIM_NOT_CONNECTED", "模拟连线不完整", "流程模拟需要至少一条可执行连线。", "补齐节点连线"));
        }

        Map<String, FlowNodeConfigVO> nodeMap = new LinkedHashMap<>();
        canvas.nodes().forEach(node -> nodeMap.put(node.nodeKey(), node));
        Map<String, List<FlowEdgeVO>> outgoing = outgoingEdges(canvas.edges());
        List<SimulationStepTrace> traces = new ArrayList<>();
        List<ConditionDecisionVO> decisions = new ArrayList<>();
        List<SimulationApproverVO> approvers = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String currentNodeKey = blockers.isEmpty() ? startNodeKey(canvas, safeRequest) : null;
        int sequence = 1;
        boolean reachedEnd = false;

        while (hasText(currentNodeKey) && sequence <= canvas.nodes().size() + 5) {
            FlowNodeConfigVO node = nodeMap.get(currentNodeKey);
            if (node == null) {
                blockers.add(simulationBlocker(flow, "E_SIM_NODE_MISSING", "模拟节点不存在",
                        "节点 " + currentNodeKey + " 不存在，无法继续预测。", "检查连线目标节点"));
                break;
            }
            if (!visited.add(currentNodeKey)) {
                blockers.add(simulationBlocker(flow, "E_SIM_LOOP", "模拟检测到循环",
                        "节点 " + node.nodeName() + " 被重复访问，当前 first-loop 模拟停止。", "检查循环条件或添加终止条件"));
                break;
            }
            if ("approval".equals(node.nodeType())) {
                SimulationApproverVO predicted = predictedApprover(node);
                approvers.add(predicted);
                if (predicted.assigneeIds().isEmpty()) {
                    blockers.add(simulationBlocker(flow, "E_SIM_APPROVER_EMPTY", "审批人缺失",
                            "审批节点「" + node.nodeName() + "」没有配置审批人。", "在节点属性中配置 assigneeIds"));
                }
            }
            List<FlowEdgeVO> nextEdges = outgoing.getOrDefault(node.nodeKey(), List.of());
            FlowEdgeVO selected = selectSimulationEdge(node, nextEdges, safeRequest, decisions);
            List<String> nextNodeKeys = selected == null ? List.of() : List.of(selected.targetNodeKey());
            traces.add(new SimulationStepTrace(sequence, node.nodeKey(), node.nodeName(), node.nodeType(),
                    simulationInputSummary(node, safeRequest), simulationOutputSummary(node, selected, safeRequest),
                    nextNodeKeys, elapsedMs(node.nodeType())));
            if ("end".equals(node.nodeType())) {
                reachedEnd = true;
                break;
            }
            if (selected == null) {
                blockers.add(simulationBlocker(flow, "E_SIM_NO_BRANCH", "没有可用分支",
                        "节点「" + node.nodeName() + "」没有匹配的下一步。", "补充默认分支或调整条件表达式"));
                break;
            }
            currentNodeKey = selected.targetNodeKey();
            sequence++;
        }
        if (sequence > canvas.nodes().size() + 5) {
            blockers.add(simulationBlocker(flow, "E_SIM_STEP_LIMIT", "模拟步数超限",
                    "流程路径超过当前画布节点数量，可能存在循环。", "检查循环路径"));
        }
        if (!blockers.isEmpty() || (!traces.isEmpty() && !reachedEnd)) {
            blockers.addAll(reachedEnd ? List.of() : List.of(simulationBlocker(flow, "E_SIM_NOT_REACHED_END",
                    "未到达结束节点", "当前样例数据未能预测到结束节点。", "检查分支或结束节点连线")));
        }

        String versionNo = safeText(safeRequest.versionNo(), "DRAFT");
        long instanceCountAfter = flowInstanceCount(flow);
        FlowSimulationResult result = new FlowSimulationResult("sim_" + context.traceId(), String.valueOf(flow.getId()),
                versionNo, blockers.isEmpty(), traces, decisions, approvers, blockers, blockers,
                impactRefs(flow), instanceCountAfter > instanceCountBefore, context.traceId(), LocalDateTime.now());
        FlowSimulationLog log = new FlowSimulationLog();
        log.setFlowId(flow.getId());
        log.setVersionNo(result.versionNo());
        log.setInputPayload(toJson(safeRequest));
        log.setOutputPayload(toJson(result));
        log.setFailureItems(toJson(result.failureItems()));
        log.setSimulatedBy(systemContext.accountId());
        log.setTraceId(context.traceId());
        log.setCreatedAt(LocalDateTime.now());
        flowSimulationLogBaseService.saveEntity(log);
        return result;
    }

    /**
     * Run publish checks for flow definition and return blockers, warnings, impacts, trace id, and audit id.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param request publish request
     * @return publish check result
     */
    public PublishCheckResultVO publishCheck(String systemId, String flowId, PublishRequest request) {
        SystemMemberContext systemContext = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(systemContext, flowId);
        RequestContext requestContext = RequestContext.current();
        CanvasVO canvas = canvasFromDb(flow);
        List<PublishCheckItem> failures = new ArrayList<>();
        if (canvas.nodes().isEmpty()) {
            failures.add(new PublishCheckItem("E_FLOW_NO_NODE", "流程没有节点", "ERROR",
                    "FLOW_DEFINITION", String.valueOf(flow.getId()), "发布前至少需要一个节点。", "添加审批或结束节点"));
        }
        if (!canvas.validationSummary().hasEndNode()) {
            failures.add(new PublishCheckItem("E_FLOW_NO_END", "流程缺少结束节点", "ERROR",
                    "FLOW_DEFINITION", String.valueOf(flow.getId()), "发布流程必须存在结束节点。", "添加结束节点并连线"));
        }
        if (!canvas.validationSummary().connected()) {
            failures.add(new PublishCheckItem("E_FLOW_NOT_CONNECTED", "流程连线不完整", "ERROR",
                    "FLOW_DEFINITION", String.valueOf(flow.getId()), "发布流程必须存在可执行连线。", "补齐节点间连线"));
        }
        List<PublishCheckItem> warnings = publishWarnings(flow, canvas);
        return new PublishCheckResultVO(failures.isEmpty(), failures, warnings,
                impactRefs(flow), requestContext.traceId(), "aud_" + requestContext.traceId());
    }

    /**
     * Publish a flow definition and return an immutable snapshot view.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param request publish request
     * @return publish result
     */
    @Transactional(rollbackFor = Exception.class)
    public PublishResult publish(String systemId, String flowId, PublishRequest request) {
        SystemMemberContext systemContext = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(systemContext, flowId);
        RequestContext requestContext = RequestContext.current();
        PublishCheckResultVO check = publishCheck(systemId, flowId, request);
        if (!check.passed()) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程发布检查未通过");
        }
        String version = "flow_v" + System.currentTimeMillis();
        CanvasVO canvas = canvasFromDb(flow);
        FlowSnapshot snapshotEntity = new FlowSnapshot();
        snapshotEntity.setFlowId(flow.getId());
        snapshotEntity.setVersionNo(version);
        snapshotEntity.setNodePayload(toJson(canvas.nodes()));
        snapshotEntity.setEdgePayload(toJson(canvas.edges()));
        snapshotEntity.setPublishCheckResult(toJson(check));
        snapshotEntity.setPublishedBy(systemContext.accountId());
        snapshotEntity.setPublishedAt(LocalDateTime.now());
        snapshotEntity.setTraceId(requestContext.traceId());
        flowSnapshotBaseService.saveEntity(snapshotEntity);
        flow.setCurrentVersion(version);
        flow.setUpdatedAt(LocalDateTime.now());
        flowDefinitionBaseService.updateById(flow);
        FlowSnapshotVO snapshot = toSnapshotVO(snapshotEntity, flow, check);
        return new PublishResult(PUBLISH_STATUS_PUBLISHED, String.valueOf(flow.getId()), version,
                snapshot.snapshotId(), requestContext.traceId(), "aud_" + requestContext.traceId(), null,
                snapshot, LocalDateTime.now());
    }

    /**
     * Return published flow snapshots.
     *
     * @param systemId system id
     * @param flowId flow id
     * @return snapshot list
     */
    public List<FlowSnapshotVO> snapshots(String systemId, String flowId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(context, flowId);
        return flowSnapshotBaseService.list(new LambdaQueryWrapper<FlowSnapshot>()
                        .eq(FlowSnapshot::getFlowId, flow.getId())
                        .orderByDesc(FlowSnapshot::getPublishedAt))
                .stream()
                .map(snapshot -> toSnapshotVO(snapshot, flow, readPublishCheck(snapshot.getPublishCheckResult())))
                .toList();
    }

    /**
     * Return one immutable snapshot detail.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param versionNo version no
     * @return snapshot detail
     */
    public FlowSnapshotVO snapshotDetail(String systemId, String flowId, String versionNo) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(context, flowId);
        FlowSnapshot snapshot = flowSnapshotBaseService.getOne(new LambdaQueryWrapper<FlowSnapshot>()
                .eq(FlowSnapshot::getFlowId, flow.getId())
                .eq(FlowSnapshot::getVersionNo, versionNo)
                .last("LIMIT 1"), false);
        if (Objects.isNull(snapshot)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程快照不存在");
        }
        return toSnapshotVO(snapshot, flow, readPublishCheck(snapshot.getPublishCheckResult()));
    }

    /**
     * Analyze downstream impact before publishing or rolling out a version.
     *
     * @param systemId system id
     * @param flowId flow id
     * @param versionNo optional version no
     * @return impact analysis
     */
    public ImpactAnalysisVO impactAnalysis(String systemId, String flowId, String versionNo) {
        SystemMemberContext systemContext = contextResolver.resolve(systemId);
        FlowDefinition flow = requireFlow(systemContext, flowId);
        RequestContext requestContext = RequestContext.current();
        long snapshotCount = flowSnapshotBaseService.count(new LambdaQueryWrapper<FlowSnapshot>()
                .eq(FlowSnapshot::getFlowId, flow.getId()));
        return new ImpactAnalysisVO(String.valueOf(flow.getId()),
                safeText(versionNo, safeText(flow.getCurrentVersion(), "DRAFT")),
                impactRefs(flow), Objects.isNull(flow.getBoundModuleId()) ? 0 : 1,
                snapshotCount > 0 ? 128 : 0, 0,
                List.of("当前影响分析已基于真实流程定义和快照数量，运行中实例扫描将在运行态恢复批次接入。"),
                requestContext.traceId(), LocalDateTime.now());
    }

    private LambdaQueryWrapper<FlowDefinition> flowQuery(SystemMemberContext context, FlowQueryRequest query) {
        LambdaQueryWrapper<FlowDefinition> wrapper = new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getSystemId, context.systemId())
                .eq(FlowDefinition::getTenantId, context.tenantId())
                .eq(FlowDefinition::getDeleted, DELETED_NO);
        if (Objects.nonNull(query)) {
            if (hasText(query.status())) {
                Integer status = parseOptionalInteger(query.status());
                if (Objects.nonNull(status)) {
                    wrapper.eq(FlowDefinition::getStatus, status);
                }
            }
            if (hasText(query.publishStatus())) {
                if (PUBLISH_STATUS_PUBLISHED.equalsIgnoreCase(query.publishStatus())) {
                    wrapper.isNotNull(FlowDefinition::getCurrentVersion);
                } else if (PUBLISH_STATUS_DRAFT.equalsIgnoreCase(query.publishStatus())) {
                    wrapper.isNull(FlowDefinition::getCurrentVersion);
                }
            }
            Long moduleId = parseOptionalId(query.boundModuleId());
            if (Objects.nonNull(moduleId)) {
                wrapper.eq(FlowDefinition::getBoundModuleId, moduleId);
            }
            if (hasText(query.keyword())) {
                wrapper.and(value -> value.like(FlowDefinition::getFlowCode, query.keyword())
                        .or().like(FlowDefinition::getFlowName, query.keyword()));
            }
        }
        return wrapper;
    }

    private FlowDefinitionVO toVO(FlowDefinition flow) {
        return new FlowDefinitionVO(String.valueOf(flow.getId()), String.valueOf(flow.getSystemId()),
                String.valueOf(flow.getTenantId()), flow.getFlowCode(), flow.getFlowName(),
                Objects.isNull(flow.getBoundModuleId()) ? null : String.valueOf(flow.getBoundModuleId()),
                readJson(flow.getTriggerRule(), TriggerRule.class, defaultTriggerRule()), flow.getStatus(),
                hasText(flow.getCurrentVersion()) ? PUBLISH_STATUS_PUBLISHED : PUBLISH_STATUS_DRAFT,
                safeText(flow.getCurrentVersion(), "DRAFT"), canvasFromDb(flow), propertyPanels(),
                flow.getCreatedAt(), flow.getUpdatedAt());
    }

    private FlowDefinition requireFlow(SystemMemberContext context, String flowId) {
        FlowDefinition flow;
        Long id = parseOptionalId(flowId);
        LambdaQueryWrapper<FlowDefinition> wrapper = new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getSystemId, context.systemId())
                .eq(FlowDefinition::getTenantId, context.tenantId())
                .eq(FlowDefinition::getDeleted, DELETED_NO)
                .last("LIMIT 1");
        if (Objects.nonNull(id)) {
            flow = flowDefinitionBaseService.getOne(wrapper.eq(FlowDefinition::getId, id), false);
        } else {
            flow = flowDefinitionBaseService.getOne(wrapper.eq(FlowDefinition::getFlowCode, flowId), false);
        }
        if (Objects.isNull(flow)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程不存在");
        }
        return flow;
    }

    private void ensureFlowCodeUnique(SystemMemberContext context, String flowCode) {
        if (flowDefinitionBaseService.count(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getSystemId, context.systemId())
                .eq(FlowDefinition::getTenantId, context.tenantId())
                .eq(FlowDefinition::getFlowCode, flowCode)
                .eq(FlowDefinition::getDeleted, DELETED_NO)) > 0) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程编码已存在");
        }
    }

    private CanvasVO canvasFromDb(FlowDefinition flow) {
        List<FlowNodeConfigVO> nodes = flowNodeBaseService.list(new LambdaQueryWrapper<FlowNode>()
                        .eq(FlowNode::getFlowId, flow.getId())
                        .orderByAsc(FlowNode::getId))
                .stream()
                .map(this::nodeFromDb)
                .toList();
        List<FlowEdgeVO> edges = flowEdgeBaseService.list(new LambdaQueryWrapper<FlowEdge>()
                        .eq(FlowEdge::getFlowId, flow.getId())
                        .orderByAsc(FlowEdge::getId))
                .stream()
                .map(this::edgeFromDb)
                .toList();
        return new CanvasVO(String.valueOf(flow.getId()), nodes, edges, branchLabels(edges),
                new CanvasValidationSummary(!nodes.isEmpty() && !edges.isEmpty(), hasEndNode(nodes), List.of()));
    }

    private FlowNodeConfigVO nodeFromDb(FlowNode node) {
        return new FlowNodeConfigVO(node.getNodeKey(), node.getNodeType(), node.getNodeName(),
                readJson(node.getPositionPayload(), PositionVO.class, new PositionVO(100, 100, 180, 72)),
                readJson(node.getPropertyPayload(), Object.class, Map.of()), node.getStatus(),
                node.getNodeType() + "PropertyPanel", !"timeout_reminder".equals(node.getNodeType()));
    }

    private FlowEdgeVO edgeFromDb(FlowEdge edge) {
        return new FlowEdgeVO(edge.getEdgeKey(), edge.getSourceNodeKey(), edge.getTargetNodeKey(),
                edge.getBranchLabel(), readJson(edge.getConditionPayload(), ConditionExpression.class, null));
    }

    private void replaceCanvas(Long flowId, CanvasVO canvas) {
        flowNodeBaseService.remove(new LambdaQueryWrapper<FlowNode>().eq(FlowNode::getFlowId, flowId));
        flowEdgeBaseService.remove(new LambdaQueryWrapper<FlowEdge>().eq(FlowEdge::getFlowId, flowId));
        LocalDateTime now = LocalDateTime.now();
        for (FlowNodeConfigVO node : canvas.nodes()) {
            FlowNode entity = new FlowNode();
            entity.setFlowId(flowId);
            entity.setNodeKey(node.nodeKey());
            entity.setNodeType(node.nodeType());
            entity.setNodeName(node.nodeName());
            entity.setPositionPayload(toJson(node.position()));
            entity.setPropertyPayload(toJson(node.propertyPayload()));
            entity.setStatus(node.status() == null ? ENABLED : node.status());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            flowNodeBaseService.saveEntity(entity);
        }
        for (FlowEdgeVO edge : canvas.edges()) {
            FlowEdge entity = new FlowEdge();
            entity.setFlowId(flowId);
            entity.setEdgeKey(edge.edgeKey());
            entity.setSourceNodeKey(edge.sourceNodeKey());
            entity.setTargetNodeKey(edge.targetNodeKey());
            entity.setBranchLabel(edge.branchLabel());
            entity.setConditionPayload(edge.conditionPayload() == null ? null : toJson(edge.conditionPayload()));
            entity.setCreatedAt(now);
            flowEdgeBaseService.saveEntity(entity);
        }
    }

    private FlowSnapshotVO toSnapshotVO(FlowSnapshot snapshot, FlowDefinition flow, PublishCheckResultVO check) {
        return new FlowSnapshotVO("snap_" + snapshot.getId(), String.valueOf(flow.getId()), snapshot.getVersionNo(),
                readJson(snapshot.getNodePayload(), NODE_LIST_TYPE, List.of()),
                readJson(snapshot.getEdgePayload(), EDGE_LIST_TYPE, List.of()),
                check, String.valueOf(snapshot.getPublishedBy()), snapshot.getPublishedAt(),
                snapshot.getTraceId(), true);
    }

    private PublishCheckResultVO readPublishCheck(String value) {
        return readJson(value, PublishCheckResultVO.class,
                new PublishCheckResultVO(true, List.of(), List.of(), List.of(), RequestContext.current().traceId(),
                        "aud_" + RequestContext.current().traceId()));
    }

    private FlowDefinitionVO legacyPreviewFlow(String systemId, String flowId) {
        return new FlowDefinitionVO(flowId, systemId, DEFAULT_TENANT_ID, "business_approval",
                "业务数据审批流程", DEFAULT_MODULE_ID, defaultTriggerRule(), 1, "PUBLISHED",
                DEFAULT_VERSION, legacyPreviewCanvas(flowId), propertyPanels(), LocalDateTime.now().minusDays(3),
                LocalDateTime.now().minusMinutes(30));
    }

    private CanvasVO legacyPreviewCanvas(String flowId) {
        List<FlowNodeConfigVO> nodes = List.of(
                node("node_approval_leader", "approval", "直属负责人审批", new PositionVO(120, 140, 180, 72),
                        approvalPayload(), true),
                node("node_condition_amount", "condition", "金额条件分支", new PositionVO(380, 140, 180, 72),
                        conditionPayload(), true),
                node("node_external_api_sync", "external_api", "同步外部业务系统",
                        new PositionVO(650, 70, 200, 72), externalApiPayload(), true),
                node("node_timer_archive", "timer", "归档等待", new PositionVO(920, 70, 160, 72),
                        timerPayload(), true),
                node("node_field_update_status", "field_update", "更新业务状态",
                        new PositionVO(650, 210, 200, 72), fieldUpdatePayload(), true),
                node("node_timeout_reminder", "timeout_reminder", "审批超时提醒",
                        new PositionVO(380, 300, 200, 72), timeoutReminderPayload(), false),
                node("node_end_passed", "end", "审批通过结束", new PositionVO(1160, 140, 160, 72),
                        endPayload(), true)
        );
        List<FlowEdgeVO> edges = legacyPreviewEdges(flowId);
        return new CanvasVO(flowId, nodes, edges, branchLabels(edges),
                new CanvasValidationSummary(true, true, List.of("超时提醒节点为提醒配置，不参与主路径流转。")));
    }

    private CanvasVO canvasFromRequest(String flowId, CanvasSaveRequest request) {
        if (Objects.isNull(request)) {
            return emptyCanvas(flowId);
        }
        List<FlowNodeConfigVO> nodes = safeList(request.nodes(), List.<NodeSaveRequest>of()).stream()
                .map(this::nodeFromRequest)
                .toList();
        List<FlowEdgeVO> edges = safeList(request.edges(), List.<EdgeSaveRequest>of()).stream()
                .map(edge -> new FlowEdgeVO(safeText(edge.edgeKey(), "edge_draft"),
                        safeText(edge.sourceNodeKey(), "node_start"),
                        safeText(edge.targetNodeKey(), "node_end"),
                        safeText(edge.branchLabel(), "默认分支"), edge.conditionPayload()))
                .toList();
        if (nodes.isEmpty()) {
            return emptyCanvas(flowId);
        }
        return new CanvasVO(flowId, nodes, edges, branchLabels(edges),
                new CanvasValidationSummary(!edges.isEmpty(), hasEndNode(nodes), List.of()));
    }

    private CanvasVO emptyCanvas(String flowId) {
        return new CanvasVO(flowId, List.of(), List.of(), List.of(),
                new CanvasValidationSummary(false, false, List.of()));
    }

    private FlowNodeConfigVO nodeFromRequest(NodeSaveRequest request) {
        String nodeType = safeText(request.nodeType(), "approval");
        Object payload = request.propertyPayload() == null || request.propertyPayload().isEmpty()
                ? propertyPayload(nodeType) : request.propertyPayload();
        return node(safeText(request.nodeKey(), "node_" + nodeType), nodeType,
                safeText(request.nodeName(), nodeName(nodeType)),
                request.position() == null ? new PositionVO(100, 100, 180, 72) : request.position(),
                payload, !"timeout_reminder".equals(nodeType));
    }

    private FlowNodeConfigVO node(String nodeKey, String nodeType, String nodeName, PositionVO position,
                                  Object propertyPayload, boolean runtimeExecutable) {
        return new FlowNodeConfigVO(nodeKey, nodeType, nodeName, position, propertyPayload, 1,
                nodeType + "PropertyPanel", runtimeExecutable);
    }

    private List<FlowEdgeVO> legacyPreviewEdges(String flowId) {
        return List.of(
                new FlowEdgeVO("edge_approval_condition", "node_approval_leader", "node_condition_amount",
                        "审批通过", null),
                new FlowEdgeVO("edge_condition_manager", "node_condition_amount", "node_external_api_sync",
                        "金额大于等于 10 万", conditionExpression("expr_amount_threshold", "amount", "GTE", 100000)),
                new FlowEdgeVO("edge_condition_auto", "node_condition_amount", "node_field_update_status",
                        "金额小于 10 万", conditionExpression("expr_amount_low", "amount", "LT", 100000)),
                new FlowEdgeVO("edge_api_timer", "node_external_api_sync", "node_timer_archive",
                        "外部同步成功", null),
                new FlowEdgeVO("edge_timer_end", "node_timer_archive", "node_end_passed",
                        "等待完成", null),
                new FlowEdgeVO("edge_update_end", "node_field_update_status", "node_end_passed",
                        "状态已更新", null)
        );
    }

    private List<BranchLabelVO> branchLabels(List<FlowEdgeVO> edges) {
        return edges.stream()
                .map(edge -> new BranchLabelVO(edge.edgeKey(), edge.sourceNodeKey(), edge.branchLabel(),
                        edge.conditionPayload() == null, edges.indexOf(edge) + 1))
                .toList();
    }

    private List<NodePropertyPanelVO> propertyPanels() {
        return List.of(nodePanel("approval"), nodePanel("condition"), nodePanel("field_update"),
                nodePanel("external_api"), nodePanel("timer"), nodePanel("timeout_reminder"), nodePanel("end"));
    }

    private NodePropertyPanelVO nodePanel(String nodeType) {
        return new NodePropertyPanelVO(nodeType, nodeType + "PropertyPanel", nodeName(nodeType) + "属性",
                payloadType(nodeType), propertySchema(nodeType), supportedActions(nodeType));
    }

    private NodeLibraryItem libraryItem(String nodeType, String nodeTypeName, String category, String icon,
                                        String description, Object payload, List<String> capabilities) {
        return new NodeLibraryItem(nodeType, nodeTypeName, category, icon, description, payload,
                propertySchema(nodeType), capabilities);
    }

    private List<PropertyFieldMeta> propertySchema(String nodeType) {
        return switch (nodeType) {
            case "approval" -> List.of(
                    field("approvalType", "审批类型", "SELECT", true, List.of("OR_SIGN", "AND_SIGN"), "OR_SIGN",
                            "会签或或签策略"),
                    field("assigneeType", "审批人来源", "SELECT", true, List.of("MEMBER", "ROLE", "DEPARTMENT"),
                            "ROLE", "审批任务接收人来源"),
                    field("timeoutPolicy", "超时策略", "OBJECT", false, List.of(), null, "审批超时后的提醒或转交配置"));
            case "condition" -> List.of(
                    field("ruleMode", "规则模式", "SELECT", true, List.of("FIRST_MATCH", "ALL_MATCH"),
                            "FIRST_MATCH", "条件分支匹配策略"),
                    field("expressions", "条件表达式", "EXPRESSION_LIST", true, List.of(), null, "字段条件表达式列表"),
                    field("defaultBranchLabel", "默认分支标签", "TEXT", true, List.of(), "其他", "未命中时的分支标签"));
            case "field_update" -> List.of(
                    field("updateMode", "更新模式", "SELECT", true, List.of("SET", "APPEND", "CLEAR"), "SET",
                            "业务字段写回方式"),
                    field("updates", "字段更新规则", "FIELD_UPDATE_LIST", true, List.of(), null, "待写回字段和值表达式"),
                    field("permissionCheckRequired", "校验字段权限", "BOOLEAN", true, List.of(), true,
                            "运行时是否校验字段写权限"));
            case "external_api" -> List.of(
                    field("externalAppRefId", "外部应用引用", "SECRET_REF", true, List.of(), "openapi_business_app",
                            "只保存外部应用引用，不回显密钥"),
                    field("endpointCode", "接口编码", "TEXT", true, List.of(), "business.sync", "外部接口配置编码"),
                    field("retryPolicy", "重试策略", "OBJECT", false, List.of(), null, "失败重试和补偿边界"));
            case "timer" -> List.of(
                    field("timerMode", "计时模式", "SELECT", true, List.of("DELAY", "CRON"), "DELAY",
                            "延迟或计划任务模式"),
                    field("delayMinutes", "延迟分钟", "NUMBER", false, List.of(), 30, "DELAY 模式的等待时长"),
                    field("businessCalendarCode", "业务日历", "TEXT", false, List.of(), "default_workday",
                            "按工作日历计算触发时间"));
            case "timeout_reminder" -> List.of(
                    field("sourceApprovalNodeKey", "关联审批节点", "NODE_REF", true, List.of(), "node_approval_leader",
                            "被提醒的审批节点"),
                    field("reminderChannels", "提醒渠道", "MULTI_SELECT", true, List.of("MESSAGE", "TODO", "SMS"),
                            List.of("MESSAGE", "TODO"), "通知模板可用渠道"),
                    field("maxRemindTimes", "最大提醒次数", "NUMBER", true, List.of(), 3, "避免重复打扰"));
            case "end" -> List.of(
                    field("endStatus", "结束状态", "SELECT", true, List.of("APPROVED", "REJECTED", "TERMINATED"),
                            "APPROVED", "流程路径最终状态"),
                    field("closeTodos", "关闭待办", "BOOLEAN", true, List.of(), true, "发布快照供运行时关闭剩余待办"),
                    field("writeBackFieldCode", "回写字段", "FIELD_REF", false, List.of(), "approvalStatus",
                            "结束后写回业务字段"));
            default -> List.of();
        };
    }

    private PropertyFieldMeta field(String fieldCode, String fieldName, String fieldType, boolean required,
                                    List<String> options, Object defaultValue, String description) {
        return new PropertyFieldMeta(fieldCode, fieldName, fieldType, required, options, defaultValue, description);
    }

    private List<String> supportedActions(String nodeType) {
        return switch (nodeType) {
            case "approval" -> List.of("approve", "reject", "transfer", "timeout");
            case "condition" -> List.of("evaluate", "selectBranch");
            case "field_update" -> List.of("writeField", "auditDiff");
            case "external_api" -> List.of("call", "retry", "compensate");
            case "timer" -> List.of("schedule", "wake");
            case "timeout_reminder" -> List.of("remind", "escalate");
            case "end" -> List.of("finish", "writeBack");
            default -> List.of();
        };
    }

    private Object propertyPayload(String nodeType) {
        return switch (nodeType) {
            case "condition" -> conditionPayload();
            case "field_update" -> fieldUpdatePayload();
            case "external_api" -> externalApiPayload();
            case "timer" -> timerPayload();
            case "timeout_reminder" -> timeoutReminderPayload();
            case "end" -> endPayload();
            default -> approvalPayload();
        };
    }

    private ApprovalPropertyPayload approvalPayload() {
        return new ApprovalPropertyPayload("OR_SIGN", "ROLE", List.of("role_flow_approver"),
                true, true, true,
                new FieldPermissionPolicy(List.of("businessCode", "amount", "ownerDept"),
                        List.of("approvalComment"), "MASK_DENIED_FIELDS", DEFAULT_PERMISSION_VERSION),
                new TimeoutPolicy(1440, "REMIND_ONLY", "node_timeout_reminder"));
    }

    private ConditionPropertyPayload conditionPayload() {
        return new ConditionPropertyPayload("FIRST_MATCH",
                List.of(conditionExpression("expr_amount_threshold", "amount", "GTE", 100000),
                        conditionExpression("expr_amount_low", "amount", "LT", 100000)),
                "其他", "FOLLOW_DEFAULT_BRANCH");
    }

    private ConditionExpression conditionExpression(String expressionId, String fieldCode, String operator,
                                                    Object expectedValue) {
        return new ConditionExpression(expressionId, fieldCode, operator, expectedValue,
                fieldCode + " " + operator + " " + expectedValue);
    }

    private FieldUpdatePropertyPayload fieldUpdatePayload() {
        return new FieldUpdatePropertyPayload("SET",
                List.of(new FieldUpdateRule("approvalStatus", "CONST", "APPROVED", null),
                        new FieldUpdateRule("approvedAt", "EXPRESSION", null, "now()")),
                true, true);
    }

    private ExternalApiPropertyPayload externalApiPayload() {
        Map<String, String> requestMapping = orderedMap("recordId", "$.recordId", "businessCode", "$.fields.businessCode");
        Map<String, String> responseMapping = orderedMap("externalSyncId", "$.data.syncId", "syncStatus",
                "$.data.status");
        return new ExternalApiPropertyPayload("openapi_business_app", "business.sync", "POST",
                requestMapping, responseMapping, new RetryPolicy(3, 30, true),
                "recordId", "STOP_AND_REPORT");
    }

    private TimerPropertyPayload timerPayload() {
        return new TimerPropertyPayload("DELAY", 30, null, "Asia/Shanghai", 1, "default_workday");
    }

    private TimeoutReminderPropertyPayload timeoutReminderPayload() {
        return new TimeoutReminderPropertyPayload("node_approval_leader", 1440,
                List.of("MESSAGE", "TODO"), 3, "ROLE", "tpl_flow_timeout_reminder");
    }

    private EndPropertyPayload endPayload() {
        return new EndPropertyPayload("APPROVED", true, "approvalStatus", "tpl_flow_finished");
    }

    private FlowSnapshotVO snapshot(String systemId, String flowId, String versionNo, PublishCheckResultVO check) {
        CanvasVO canvas = legacyPreviewCanvas(flowId);
        return new FlowSnapshotVO("snap_" + versionNo, flowId, versionNo, canvas.nodes(), canvas.edges(),
                check, "system_member_admin", LocalDateTime.now(), RequestContext.current().traceId(), true);
    }

    private List<PublishCheckItem> publishWarnings(FlowDefinition flow, CanvasVO canvas) {
        List<PublishCheckItem> warnings = new ArrayList<>();
        Set<String> nodeTypes = new HashSet<>();
        canvas.nodes().forEach(node -> nodeTypes.add(safeText(node.nodeType(), "")));
        if (nodeTypes.contains("external_api")) {
            warnings.add(new PublishCheckItem("W_EXTERNAL_API_SCOPE", "外部 API scope 需运行时校验", "WARNING",
                    "FLOW_NODE", String.valueOf(flow.getId()),
                    "流程包含外部 API 节点，运行时会继续校验外部应用授权、SecretRef 和限流策略。", "确认 OpenAPI 应用已发布"));
        }
        if (nodeTypes.contains("timeout_reminder")) {
            warnings.add(new PublishCheckItem("W_TIMEOUT_TEMPLATE", "超时提醒模板需可用", "WARNING",
                    "FLOW_NODE", String.valueOf(flow.getId()),
                    "流程包含超时提醒节点，运行时会检查通知模板、渠道和免打扰策略。", "确认消息模板已启用"));
        }
        if (nodeTypes.contains("timer")) {
            warnings.add(new PublishCheckItem("W_TIMER_SCHEDULE", "定时器调度需发布后校验", "WARNING",
                    "FLOW_NODE", String.valueOf(flow.getId()),
                    "流程包含定时器节点，发布后需要按业务日历和触发次数校验调度边界。", "确认业务日历和触发限制"));
        }
        if (nodeTypes.contains("field_update")) {
            warnings.add(new PublishCheckItem("W_FIELD_UPDATE_PERMISSION", "字段更新需校验字段权限", "WARNING",
                    "FLOW_NODE", String.valueOf(flow.getId()),
                    "流程包含字段更新节点，运行时会校验字段写权限并记录审计原因。", "确认字段权限和回写字段"));
        }
        return warnings;
    }

    private List<ImpactRef> impactRefs(FlowDefinition flow) {
        List<ImpactRef> refs = new ArrayList<>();
        if (Objects.nonNull(flow.getBoundModuleId())) {
            refs.add(new ImpactRef("MODULE", String.valueOf(flow.getBoundModuleId()),
                    "Bound module " + flow.getBoundModuleId(), "BIND_FLOW_VERSION"));
        }
        refs.add(new ImpactRef("FLOW_DEFINITION", String.valueOf(flow.getId()), flow.getFlowName(),
                "PUBLISH_SNAPSHOT"));
        for (FlowNodeConfigVO node : canvasFromDb(flow).nodes()) {
            switch (safeText(node.nodeType(), "")) {
                case "field_update" -> refs.add(new ImpactRef("MODULE_FIELD", node.nodeKey(),
                        node.nodeName(), "WRITE_BACK_FIELD"));
                case "external_api" -> refs.add(new ImpactRef("OPENAPI_APP", node.nodeKey(),
                        node.nodeName(), "VERIFY_SCOPE"));
                case "timer" -> refs.add(new ImpactRef("FLOW_TIMER", node.nodeKey(),
                        node.nodeName(), "SCHEDULE_WAKE"));
                case "timeout_reminder" -> refs.add(new ImpactRef("MESSAGE_TEMPLATE", node.nodeKey(),
                        node.nodeName(), "VERIFY_CHANNEL"));
                case "approval" -> refs.add(new ImpactRef("TODO_MESSAGE", node.nodeKey(),
                        node.nodeName(), "CREATE_APPROVAL_TODO"));
                default -> {
                }
            }
        }
        return refs;
    }
    private List<ImpactRef> legacyPreviewImpactRefs(String flowId) {
        return List.of(
                new ImpactRef("MODULE", DEFAULT_MODULE_ID, "业务数据", "BIND_FLOW_VERSION"),
                new ImpactRef("FLOW_DEFINITION", flowId, "业务数据审批流程", "PUBLISH_SNAPSHOT"),
                new ImpactRef("PERMISSION_SNAPSHOT", "eps_flow_business_001", "流程节点字段权限", "RECALCULATE_CACHE"),
                new ImpactRef("OPENAPI_APP", "openapi_business_app", "外部业务系统", "VERIFY_SCOPE"),
                new ImpactRef("MESSAGE_TEMPLATE", "tpl_flow_timeout_reminder", "流程超时提醒", "VERIFY_CHANNEL"))
        ;
    }

    private TriggerRule defaultTriggerRule() {
        return new TriggerRule("MODULE_ACTION", List.of("record.submitApproval"),
                "approvalStatus == 'DRAFT'", true, true);
    }

    private String nodeTypeByNodeKey(String nodeKey) {
        String key = safeText(nodeKey, "node_approval_leader");
        if (key.contains("condition")) {
            return "condition";
        }
        if (key.contains("field_update")) {
            return "field_update";
        }
        if (key.contains("external_api")) {
            return "external_api";
        }
        if (key.contains("timer")) {
            return "timer";
        }
        if (key.contains("timeout")) {
            return "timeout_reminder";
        }
        if (key.contains("end")) {
            return "end";
        }
        return "approval";
    }

    private String nodeName(String nodeType) {
        return switch (nodeType) {
            case "condition" -> "条件分支";
            case "field_update" -> "字段更新";
            case "external_api" -> "外部 API";
            case "timer" -> "定时器";
            case "timeout_reminder" -> "超时提醒";
            case "end" -> "结束";
            default -> "审批";
        };
    }

    private String payloadType(String nodeType) {
        return switch (nodeType) {
            case "condition" -> "ConditionPropertyPayload";
            case "field_update" -> "FieldUpdatePropertyPayload";
            case "external_api" -> "ExternalApiPropertyPayload";
            case "timer" -> "TimerPropertyPayload";
            case "timeout_reminder" -> "TimeoutReminderPropertyPayload";
            case "end" -> "EndPropertyPayload";
            default -> "ApprovalPropertyPayload";
        };
    }

    private boolean hasEndNode(List<FlowNodeConfigVO> nodes) {
        return nodes.stream().anyMatch(node -> "end".equals(node.nodeType()));
    }

    private CanvasVO simulationCanvas(FlowDefinition flow, FlowSimulationRequest request) {
        String versionNo = request == null ? null : request.versionNo();
        if (hasText(versionNo) && !"DRAFT".equalsIgnoreCase(versionNo)) {
            FlowSnapshot snapshot = flowSnapshotBaseService.getOne(new LambdaQueryWrapper<FlowSnapshot>()
                    .eq(FlowSnapshot::getFlowId, flow.getId())
                    .eq(FlowSnapshot::getVersionNo, versionNo)
                    .last("LIMIT 1"), false);
            if (snapshot != null) {
                List<FlowNodeConfigVO> nodes = readJson(snapshot.getNodePayload(), NODE_LIST_TYPE, List.of());
                List<FlowEdgeVO> edges = readJson(snapshot.getEdgePayload(), EDGE_LIST_TYPE, List.of());
                return new CanvasVO(String.valueOf(flow.getId()), nodes, edges, branchLabels(edges),
                        new CanvasValidationSummary(!nodes.isEmpty() && !edges.isEmpty(), hasEndNode(nodes), List.of()));
            }
        }
        return canvasFromDb(flow);
    }

    private Map<String, List<FlowEdgeVO>> outgoingEdges(List<FlowEdgeVO> edges) {
        Map<String, List<FlowEdgeVO>> grouped = new LinkedHashMap<>();
        for (FlowEdgeVO edge : edges) {
            grouped.computeIfAbsent(edge.sourceNodeKey(), ignored -> new ArrayList<>()).add(edge);
        }
        return grouped;
    }

    private long flowInstanceCount(FlowDefinition flow) {
        return flowInstanceBaseService.count(new LambdaQueryWrapper<FlowInstance>()
                .eq(FlowInstance::getFlowId, flow.getId()));
    }

    private String startNodeKey(CanvasVO canvas, FlowSimulationRequest request) {
        if (request != null && hasText(request.startNodeKey())
                && canvas.nodes().stream().anyMatch(node -> Objects.equals(node.nodeKey(), request.startNodeKey()))) {
            return request.startNodeKey();
        }
        Set<String> targets = new HashSet<>();
        canvas.edges().forEach(edge -> targets.add(edge.targetNodeKey()));
        return canvas.nodes().stream()
                .filter(node -> !targets.contains(node.nodeKey()))
                .filter(FlowNodeConfigVO::runtimeExecutable)
                .findFirst()
                .or(() -> canvas.nodes().stream().findFirst())
                .map(FlowNodeConfigVO::nodeKey)
                .orElse(null);
    }

    private FlowEdgeVO selectSimulationEdge(FlowNodeConfigVO node, List<FlowEdgeVO> edges,
                                            FlowSimulationRequest request, List<ConditionDecisionVO> decisions) {
        if (edges.isEmpty()) {
            return null;
        }
        if (!"condition".equals(node.nodeType())) {
            return edges.get(0);
        }
        FlowEdgeVO defaultEdge = null;
        for (FlowEdgeVO edge : edges) {
            ConditionExpression expression = edge.conditionPayload();
            if (expression == null) {
                defaultEdge = defaultEdge == null ? edge : defaultEdge;
                continue;
            }
            boolean matched = conditionMatches(expression, request == null ? Map.of() : request.fieldValues());
            decisions.add(new ConditionDecisionVO(node.nodeKey(), expression.expressionId(), edge.edgeKey(),
                    safeText(edge.branchLabel(), expression.expressionText()), matched,
                    Map.of(safeText(expression.fieldCode(), "field"), fieldValue(request, expression.fieldCode()))));
            if (matched) {
                return edge;
            }
        }
        if (defaultEdge != null) {
            decisions.add(new ConditionDecisionVO(node.nodeKey(), null, defaultEdge.edgeKey(),
                    safeText(defaultEdge.branchLabel(), "默认分支"), true, Map.of()));
        }
        return defaultEdge;
    }

    private boolean conditionMatches(ConditionExpression expression, Map<String, Object> fieldValues) {
        Object actual = fieldValues == null ? null : fieldValues.get(expression.fieldCode());
        Object expected = expression.expectedValue();
        String operator = safeText(expression.operator(), "EQ").toUpperCase();
        BigDecimal actualDecimal = decimalValue(actual);
        BigDecimal expectedDecimal = decimalValue(expected);
        if (actualDecimal != null && expectedDecimal != null) {
            int compared = actualDecimal.compareTo(expectedDecimal);
            return switch (operator) {
                case "GT" -> compared > 0;
                case "GTE", ">=" -> compared >= 0;
                case "LT" -> compared < 0;
                case "LTE", "<=" -> compared <= 0;
                case "NE", "!=" -> compared != 0;
                default -> compared == 0;
            };
        }
        String actualText = Objects.toString(actual, "");
        String expectedText = Objects.toString(expected, "");
        return switch (operator) {
            case "NE", "!=" -> !Objects.equals(actualText, expectedText);
            case "CONTAINS" -> actualText.contains(expectedText);
            default -> Objects.equals(actualText, expectedText);
        };
    }

    private Object fieldValue(FlowSimulationRequest request, String fieldCode) {
        if (request == null || request.fieldValues() == null || !hasText(fieldCode)) {
            return "";
        }
        Object value = request.fieldValues().get(fieldCode);
        return value == null ? "" : value;
    }

    private SimulationApproverVO predictedApprover(FlowNodeConfigVO node) {
        Map<String, Object> payload = objectMap(node.propertyPayload());
        String assigneeType = Objects.toString(payload.getOrDefault("assigneeType", "MEMBER"), "MEMBER");
        List<String> assigneeIds = stringList(payload.get("assigneeIds"));
        String displayName = assigneeIds.isEmpty()
                ? "未配置审批人"
                : assigneeType + ":" + String.join(",", assigneeIds);
        return new SimulationApproverVO(node.nodeKey(), node.nodeName(), assigneeType, assigneeIds, displayName);
    }

    private String simulationInputSummary(FlowNodeConfigVO node, FlowSimulationRequest request) {
        return switch (node.nodeType()) {
            case "condition" -> "fields=" + toJson(request == null ? Map.of() : request.fieldValues());
            case "approval" -> "actor=" + safeText(request == null ? null : request.actorMemberId(), "current_member")
                    + ", recordId=" + safeText(request == null ? null : request.recordId(), "preview_record");
            default -> "version=" + safeText(request == null ? null : request.versionNo(), "DRAFT");
        };
    }

    private String simulationOutputSummary(FlowNodeConfigVO node, FlowEdgeVO selected, FlowSimulationRequest request) {
        if ("end".equals(node.nodeType())) {
            return "模拟路径到达结束节点，不创建运行实例";
        }
        if ("approval".equals(node.nodeType())) {
            return "预计生成审批任务：" + predictedApprover(node).displayName();
        }
        if ("condition".equals(node.nodeType())) {
            return selected == null ? "没有命中分支" : "命中分支：" + safeText(selected.branchLabel(), selected.edgeKey());
        }
        if ("external_api".equals(node.nodeType())) {
            return "仅预测外部 API 调用，不发送请求";
        }
        if ("field_update".equals(node.nodeType())) {
            return "仅预测字段更新，不写入业务数据";
        }
        return selected == null ? "无下一步" : "下一步：" + selected.targetNodeKey();
    }

    private long elapsedMs(String nodeType) {
        return switch (safeText(nodeType, "")) {
            case "approval" -> 12L;
            case "condition" -> 5L;
            case "external_api" -> 18L;
            case "field_update" -> 9L;
            default -> 2L;
        };
    }

    private PublishCheckItem simulationBlocker(FlowDefinition flow, String code, String name, String message,
                                               String fixAction) {
        return new PublishCheckItem(code, name, "ERROR", "FLOW_DEFINITION", String.valueOf(flow.getId()),
                message, fixAction);
    }

    private Map<String, Object> objectMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> result = new LinkedHashMap<>();
            raw.forEach((key, mapValue) -> result.put(Objects.toString(key, ""), mapValue));
            return result;
        }
        return objectMapper.convertValue(value == null ? Map.of() : value, new TypeReference<Map<String, Object>>() {
        });
    }

    private List<String> stringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(item -> Objects.toString(item, "")).filter(this::hasText).toList();
        }
        if (value instanceof String text && hasText(text)) {
            return List.of(text);
        }
        return List.of();
    }

    private BigDecimal decimalValue(Object value) {
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value instanceof String text && hasText(text)) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private long simulationAmount(FlowSimulationRequest request) {
        Object value = request == null || request.fieldValues() == null ? null : request.fieldValues().get("amount");
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 120000L;
    }

    private int pageNo(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
    }

    private int pageSize(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Long parseOptionalId(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Integer parseOptionalInteger(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "流程配置序列化失败");
        }
    }

    private <T> T readJson(String value, Class<T> type, T fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (!hasText(value)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private <T> List<T> safeList(List<T> value, List<T> fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    private Map<String, String> orderedMap(String firstKey, String firstValue, String secondKey, String secondValue) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }
}
