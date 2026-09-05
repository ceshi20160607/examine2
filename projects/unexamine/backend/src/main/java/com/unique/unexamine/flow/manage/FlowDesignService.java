package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowEdge;
import com.unique.unexamine.flow.base.entity.FlowNode;
import com.unique.unexamine.flow.base.entity.FlowPublication;
import com.unique.unexamine.flow.base.entity.FlowVersion;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowEdgeBaseService;
import com.unique.unexamine.flow.base.service.FlowNodeBaseService;
import com.unique.unexamine.flow.base.service.FlowPublicationBaseService;
import com.unique.unexamine.flow.base.service.FlowVersionBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class FlowDesignService {
    private static final Set<String> NODE_TYPES = Set.of(
            "START", "END", "APPROVAL", "GATEWAY", "SUBFLOW", "FORM_TASK", "NOTIFICATION",
            "UPDATE_FIELD", "WAIT_TIMER", "WEBHOOK", "EXTERNAL", "AI");

    private final FlowDefinitionBaseService definitionService;
    private final FlowNodeBaseService nodeService;
    private final FlowEdgeBaseService edgeService;
    private final FlowVersionBaseService versionService;
    private final FlowPublicationBaseService publicationService;
    private final ConfiguredModuleBaseService moduleService;
    private final FlowParticipantResolver participantResolver;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;

    public FlowDesignService(
            FlowDefinitionBaseService definitionService,
            FlowNodeBaseService nodeService,
            FlowEdgeBaseService edgeService,
            FlowVersionBaseService versionService,
            FlowPublicationBaseService publicationService,
            ConfiguredModuleBaseService moduleService,
            FlowParticipantResolver participantResolver,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper) {
        this.definitionService = definitionService;
        this.nodeService = nodeService;
        this.edgeService = edgeService;
        this.versionService = versionService;
        this.publicationService = publicationService;
        this.moduleService = moduleService;
        this.participantResolver = participantResolver;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<FlowDesignModels.FlowView> list(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedDefinitions(context).stream()
                .sorted(Comparator.comparing(FlowDefinition::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(flow -> view(flow, false)).toList();
    }

    @Transactional
    public FlowDesignModels.FlowView create(
            AuthenticatedContext context, FlowDesignModels.CreateFlowRequest input, String traceId) {
        requireAction(context, "DESIGN");
        String code = input.code() == null || input.code().isBlank()
                ? "flow_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : normalizeCode(input.code());
        if (scopedDefinitions(context).stream().anyMatch(flow -> code.equals(flow.getCode()))) {
            throw new DomainException("FLOW_CODE_CONFLICT", "当前范围已存在相同 Flow 编码", HttpStatus.CONFLICT);
        }
        FlowDefinition flow = new FlowDefinition();
        bindContext(flow, context);
        flow.setCode(code);
        flow.setName(input.name().strip());
        flow.setDescription(blankToNull(input.description()));
        flow.setDraftRevision(1);
        flow.setStatus("DRAFT");
        flow.setCreatedByAccountId(context.accountId());
        flow.setVersion(0);
        definitionService.insert(flow);
        audit(context, traceId, "FLOW_DRAFT_CREATED", "FLOW", flow.getId(),
                Map.of("code", code, "contextType", flow.getContextType()));
        return view(flow, true);
    }

    @Transactional(readOnly = true)
    public FlowDesignModels.FlowView detail(AuthenticatedContext context, Long flowId) {
        requireAction(context, "VIEW");
        return view(requireOwned(context, flowId), true);
    }

    @Transactional
    public FlowDesignModels.FlowView saveDraft(
            AuthenticatedContext context, Long flowId, FlowDesignModels.SaveDraftRequest input, String traceId) {
        requireAction(context, "DESIGN");
        FlowDefinition flow = requireOwned(context, flowId);
        if (!Objects.equals(flow.getVersion(), input.expectedVersion())) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "Flow 草稿已被其他人修改，请刷新后重试");
        }
        List<FlowDesignModels.NodeInput> nodes = input.nodes().stream()
                .sorted(Comparator.comparing(FlowDesignModels.NodeInput::positionX)
                        .thenComparing(FlowDesignModels.NodeInput::positionY)
                        .thenComparing(FlowDesignModels.NodeInput::nodeKey)).toList();
        List<FlowDesignModels.EdgeInput> edges = input.edges() == null ? List.of() : input.edges().stream()
                .sorted(Comparator.comparing(FlowDesignModels.EdgeInput::edgeKey)).toList();
        validateDraftShape(nodes, edges);

        flow.setDraftRevision(flow.getDraftRevision() + 1);
        flow.setStatus("DRAFT");
        if (definitionService.updateById(flow) != 1) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "Flow 草稿已被其他人修改，请刷新后重试");
        }
        for (FlowEdge edge : edges(flowId)) {
            edgeService.deleteById(edge.getId());
        }
        for (FlowNode node : nodes(flowId)) {
            nodeService.deleteById(node.getId());
        }
        for (FlowDesignModels.NodeInput inputNode : nodes) {
            nodeService.insert(toEntity(flowId, inputNode));
        }
        for (FlowDesignModels.EdgeInput inputEdge : edges) {
            edgeService.insert(toEntity(flowId, inputEdge));
        }
        audit(context, traceId, "FLOW_DRAFT_SAVED", "FLOW", flowId,
                Map.of("draftRevision", flow.getDraftRevision(), "nodeCount", nodes.size(), "edgeCount", edges.size()));
        return view(requireOwned(context, flowId), true);
    }

    @Transactional(readOnly = true)
    public FlowDesignModels.PublicationCheck publicationCheck(AuthenticatedContext context, Long flowId) {
        requireAction(context, "PUBLISH");
        FlowDefinition flow = requireOwned(context, flowId);
        List<FlowDesignModels.Issue> issues = validateGraph(context, flow, nodes(flowId), edges(flowId));
        return new FlowDesignModels.PublicationCheck(flowId, flow.getDraftRevision(), issues.isEmpty(), issues);
    }

    @Transactional
    public FlowDesignModels.SimulationResult simulate(
            AuthenticatedContext context, Long flowId, Map<String, Object> variables, String traceId) {
        requireAction(context, "SIMULATE");
        FlowDefinition flow = requireOwned(context, flowId);
        FlowDesignModels.SimulationResult result = simulateGraph(
                context, flow, nodes(flowId), edges(flowId), safeMap(variables));
        audit(context, traceId, "FLOW_DRAFT_SIMULATED", "FLOW", flowId,
                Map.of("draftRevision", flow.getDraftRevision(), "successful", result.successful(),
                        "stepCount", result.steps().size(), "issueCount", result.issues().size()));
        return result;
    }

    @Transactional
    public FlowDesignModels.PublicationResult publish(
            AuthenticatedContext context, Long flowId, FlowDesignModels.PublishRequest input, String traceId) {
        requireAction(context, "PUBLISH");
        FlowDefinition flow = requireOwned(context, flowId);
        if (!Objects.equals(flow.getDraftRevision(), input.expectedDraftRevision())) {
            throw conflict("FLOW_DRAFT_REVISION_CONFLICT", "Flow 草稿修订号已变化，请重新检查后发布");
        }
        List<FlowNode> nodes = nodes(flowId);
        List<FlowEdge> edges = edges(flowId);
        FlowDesignModels.SimulationResult simulation = simulateGraph(
                context, flow, nodes, edges, safeMap(input.simulationVariables()));
        if (!simulation.successful()) {
            throw new DomainException("FLOW_PUBLICATION_INVALID", "Flow 发布检查未通过", HttpStatus.UNPROCESSABLE_ENTITY);
        }
        Map<String, Object> snapshot = snapshot(flow, nodes, edges);
        String snapshotJson = toJson(snapshot);
        String hash = sha256(snapshotJson);
        List<FlowVersion> existing = versions(flowId);
        int versionNumber = existing.stream().map(FlowVersion::getVersionNumber)
                .max(Integer::compareTo).orElse(0) + 1;
        FlowVersion version = new FlowVersion();
        version.setFlowId(flowId);
        version.setVersionNumber(versionNumber);
        version.setDraftRevision(flow.getDraftRevision());
        version.setDefinitionHash(hash);
        version.setSnapshotJson(snapshotJson);
        version.setSimulationResultJson(toJson(simulation));
        version.setChangeSummary(input.changeSummary().strip());
        version.setPublishedByAccountId(context.accountId());
        versionService.insert(version);

        FlowPublication publication = publication(flowId);
        Long previousVersionId = publication == null ? null : publication.getCurrentVersionId();
        if (publication == null) {
            publication = new FlowPublication();
            publication.setFlowId(flowId);
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            publication.setVersion(0);
            publicationService.insert(publication);
        } else {
            publication.setCurrentVersionId(version.getId());
            publication.setUpdatedByAccountId(context.accountId());
            if (publicationService.updateById(publication) != 1) {
                throw conflict("FLOW_PUBLICATION_VERSION_CONFLICT", "Flow 发布指针已变化，请重试");
            }
        }
        flow.setStatus("PUBLISHED");
        if (definitionService.updateById(flow) != 1) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "Flow 草稿已被其他人修改，请重试");
        }
        audit(context, traceId, "FLOW_VERSION_PUBLISHED", "FLOW_VERSION", version.getId(),
                Map.of("flowId", flowId, "versionNumber", versionNumber, "definitionHash", hash));
        return new FlowDesignModels.PublicationResult(
                flowId, version.getId(), versionNumber, flow.getDraftRevision(), hash, previousVersionId, simulation);
    }

    private FlowDesignModels.SimulationResult simulateGraph(
            AuthenticatedContext context, FlowDefinition flow, List<FlowNode> nodes, List<FlowEdge> edges,
            Map<String, Object> variables) {
        List<FlowDesignModels.Issue> issues = validateGraph(context, flow, nodes, edges);
        if (!issues.isEmpty()) {
            return new FlowDesignModels.SimulationResult(
                    flow.getId(), flow.getDraftRevision(), false, List.of(), issues, variables);
        }
        Map<String, FlowNode> byKey = new HashMap<>();
        nodes.forEach(node -> byKey.put(node.getNodeKey(), node));
        Map<String, List<FlowEdge>> outgoing = outgoing(edges);
        String current = nodes.stream().filter(node -> "START".equals(node.getNodeType()))
                .findFirst().orElseThrow().getNodeKey();
        List<FlowDesignModels.SimulationStep> steps = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        for (int count = 0; count <= nodes.size(); count++) {
            FlowNode node = byKey.get(current);
            steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(), "VISITED"));
            if ("END".equals(node.getNodeType())) {
                return new FlowDesignModels.SimulationResult(
                        flow.getId(), flow.getDraftRevision(), true, steps, List.of(), variables);
            }
            if (!visited.add(current)) {
                issues.add(new FlowDesignModels.Issue("FLOW_CYCLE_DETECTED", "node:" + current,
                        "模拟路径出现循环，无法到达结束节点"));
                break;
            }
            List<FlowEdge> candidates = outgoing.getOrDefault(current, List.of());
            FlowEdge chosen = candidates.stream().filter(edge -> conditionMatches(edge.getConditionExpression(), variables))
                    .findFirst().orElse(null);
            if (chosen == null) {
                issues.add(new FlowDesignModels.Issue("NO_MATCHING_PATH", "node:" + current,
                        "当前模拟变量没有匹配的后续路径"));
                break;
            }
            current = chosen.getTargetNodeKey();
        }
        return new FlowDesignModels.SimulationResult(
                flow.getId(), flow.getDraftRevision(), false, steps, issues, variables);
    }

    private boolean conditionMatches(String expression, Map<String, Object> variables) {
        if (expression == null || expression.isBlank() || "default".equalsIgnoreCase(expression.strip())) {
            return true;
        }
        String[] operators = {"==", "!="};
        for (String operator : operators) {
            int index = expression.indexOf(operator);
            if (index > 0) {
                String key = expression.substring(0, index).strip();
                String expected = expression.substring(index + operator.length()).strip()
                        .replaceAll("^['\"]|['\"]$", "");
                boolean equal = Objects.equals(String.valueOf(variables.get(key)), expected);
                return "==".equals(operator) ? equal : !equal;
            }
        }
        return false;
    }

    private List<FlowDesignModels.Issue> validateGraph(
            AuthenticatedContext context, FlowDefinition flow, List<FlowNode> nodes, List<FlowEdge> edges) {
        List<FlowDesignModels.Issue> issues = new ArrayList<>();
        Map<String, FlowNode> byKey = new LinkedHashMap<>();
        for (FlowNode node : nodes) {
            if (byKey.put(node.getNodeKey(), node) != null) {
                issues.add(issue("DUPLICATE_NODE_KEY", node.getNodeKey(), "节点标识重复"));
            }
            validateNode(context, flow, node, issues);
        }
        List<FlowNode> starts = nodes.stream().filter(node -> "START".equals(node.getNodeType())).toList();
        List<FlowNode> ends = nodes.stream().filter(node -> "END".equals(node.getNodeType())).toList();
        if (starts.size() != 1) {
            issues.add(issue("START_NODE_COUNT_INVALID", "graph", "Flow 必须且只能有一个开始节点"));
        }
        if (ends.isEmpty()) {
            issues.add(issue("END_NODE_MISSING", "graph", "Flow 至少需要一个结束节点"));
        }
        Set<String> edgeKeys = new HashSet<>();
        for (FlowEdge edge : edges) {
            if (!edgeKeys.add(edge.getEdgeKey())) {
                issues.add(issue("DUPLICATE_EDGE_KEY", edge.getEdgeKey(), "连线标识重复"));
            }
            if (!byKey.containsKey(edge.getSourceNodeKey()) || !byKey.containsKey(edge.getTargetNodeKey())) {
                issues.add(issue("EDGE_NODE_INVALID", edge.getEdgeKey(), "连线引用了不存在的节点"));
            }
        }
        Map<String, List<FlowEdge>> outgoing = outgoing(edges);
        for (FlowNode node : nodes) {
            if (!"END".equals(node.getNodeType()) && outgoing.getOrDefault(node.getNodeKey(), List.of()).isEmpty()) {
                issues.add(issue("DEAD_END_NODE", node.getNodeKey(), "非结束节点没有后续连线"));
            }
        }
        if (starts.size() == 1) {
            Set<String> reachable = new LinkedHashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            queue.add(starts.getFirst().getNodeKey());
            while (!queue.isEmpty()) {
                String key = queue.removeFirst();
                if (reachable.add(key)) {
                    outgoing.getOrDefault(key, List.of()).forEach(edge -> queue.add(edge.getTargetNodeKey()));
                }
            }
            for (FlowNode node : nodes) {
                if (!reachable.contains(node.getNodeKey())) {
                    issues.add(issue("UNREACHABLE_NODE", node.getNodeKey(), "节点无法从开始节点到达"));
                }
            }
        }
        return issues;
    }

    private void validateNode(
            AuthenticatedContext context, FlowDefinition flow, FlowNode node,
            List<FlowDesignModels.Issue> issues) {
        String type = node.getNodeType();
        Map<String, Object> config = fromJson(node.getConfigJson());
        Map<String, Object> assignee = fromJson(node.getAssigneePolicyJson());
        Map<String, Object> form = fromJson(node.getFormPolicyJson());
        if (!NODE_TYPES.contains(type)) {
            issues.add(issue("NODE_TYPE_INVALID", node.getNodeKey(), "不支持的节点类型：" + type));
            return;
        }
        if ("APPROVAL".equals(type) && assignee.isEmpty()) {
            issues.add(issue("APPROVER_MISSING", node.getNodeKey(), "审批节点必须配置审批人策略"));
        } else if ("APPROVAL".equals(type)) {
            String policyIssue = participantResolver.validatePolicy(context, assignee);
            if (policyIssue != null) {
                issues.add(issue("APPROVER_POLICY_INVALID", node.getNodeKey(), policyIssue));
            }
            String approvalMode = String.valueOf(config.getOrDefault("approvalMode", "OR_SIGN"))
                    .strip().toUpperCase(Locale.ROOT);
            if (!Set.of("OR_SIGN", "ALL_SIGN", "SEQUENTIAL").contains(approvalMode)) {
                issues.add(issue("APPROVAL_MODE_INVALID", node.getNodeKey(),
                        "审批方式必须是或签、会签或顺序会签"));
            }
        }
        if ("FORM_TASK".equals(type) && form.isEmpty()) {
            issues.add(issue("FORM_POLICY_MISSING", node.getNodeKey(), "表单任务必须配置字段权限"));
        }
        if ("NOTIFICATION".equals(type)) {
            if (assignee.isEmpty()) {
                issues.add(issue("NOTIFICATION_RECIPIENT_MISSING", node.getNodeKey(), "通知节点必须配置接收人策略"));
            } else {
                String policyIssue = participantResolver.validatePolicy(context, assignee);
                if (policyIssue != null) {
                    issues.add(issue("NOTIFICATION_RECIPIENT_INVALID", node.getNodeKey(), policyIssue));
                }
            }
        }
        requireConfig(type, node, config, issues, "SUBFLOW", "flowId", "子流程节点必须选择目标 Flow");
        requireConfig(type, node, config, issues, "WEBHOOK", "url", "Webhook 节点必须配置地址");
        requireConfig(type, node, config, issues, "AI", "model", "AI 节点必须配置受控模型");
        requireConfig(type, node, config, issues, "AI", "prompt", "AI 节点必须配置提示模板");
        requireAnyConfig(type, node, config, issues, "WAIT_TIMER", List.of("durationMinutes", "duration", "resumeAt"),
                "等待节点必须配置时长或恢复时间");
        requireConfig(type, node, config, issues, "UPDATE_FIELD", "fieldUpdates", "字段更新节点必须配置更新映射");
        requireConfig(type, node, config, issues, "UPDATE_FIELD", "businessAction", "字段更新节点必须配置业务动作权限");
        requireConfig(type, node, config, issues, "NOTIFICATION", "content", "通知节点必须配置消息内容");
        Long referencedFlowId = longValue(config.get("flowId"));
        if (referencedFlowId != null && "SUBFLOW".equals(type)) {
            if (Objects.equals(referencedFlowId, flow.getId())) {
                issues.add(issue("SUBFLOW_SELF_REFERENCE", node.getNodeKey(), "子流程不能引用自身"));
            } else {
                FlowDefinition referenced = definitionService.selectById(referencedFlowId);
                if (referenced == null || !owned(context, referenced)) {
                    issues.add(issue("FLOW_REFERENCE_FORBIDDEN", node.getNodeKey(), "引用的 Flow 不在当前授权范围"));
                }
            }
        }
        Long moduleId = longValue(config.get("moduleId"));
        if (moduleId != null) {
            ConfiguredModule module = moduleService.selectById(moduleId);
            if (context.systemId() == null || module == null
                    || !Objects.equals(module.getSystemId(), context.systemId())
                    || !Objects.equals(module.getOwnerTenantId(), context.tenantId())) {
                issues.add(issue("MODULE_REFERENCE_FORBIDDEN", node.getNodeKey(), "引用的模块不在当前系统租户范围"));
            }
        }
    }

    private void requireConfig(String type, FlowNode node, Map<String, Object> config,
                               List<FlowDesignModels.Issue> issues, String targetType, String key, String message) {
        if (targetType.equals(type) && empty(config.get(key))) {
            issues.add(issue("SPECIAL_PROPERTY_MISSING", node.getNodeKey() + "." + key, message));
        }
    }

    private void requireAnyConfig(String type, FlowNode node, Map<String, Object> config,
                                  List<FlowDesignModels.Issue> issues, String targetType,
                                  List<String> keys, String message) {
        if (targetType.equals(type) && keys.stream().allMatch(key -> empty(config.get(key)))) {
            issues.add(issue("SPECIAL_PROPERTY_MISSING", node.getNodeKey(), message));
        }
    }

    private void validateDraftShape(
            List<FlowDesignModels.NodeInput> nodes, List<FlowDesignModels.EdgeInput> edges) {
        Set<String> nodeKeys = new HashSet<>();
        if (nodes.stream().anyMatch(node -> !nodeKeys.add(node.nodeKey().strip()))) {
            throw invalid("FLOW_DRAFT_INVALID", "节点标识不能重复");
        }
        Set<String> edgeKeys = new HashSet<>();
        if (edges.stream().anyMatch(edge -> !edgeKeys.add(edge.edgeKey().strip()))) {
            throw invalid("FLOW_DRAFT_INVALID", "连线标识不能重复");
        }
    }

    private Map<String, List<FlowEdge>> outgoing(List<FlowEdge> edges) {
        Map<String, List<FlowEdge>> result = new HashMap<>();
        edges.stream().sorted(Comparator.comparing(FlowEdge::getPriorityOrder)
                        .thenComparing(FlowEdge::getEdgeKey))
                .forEach(edge -> result.computeIfAbsent(edge.getSourceNodeKey(), ignored -> new ArrayList<>()).add(edge));
        return result;
    }

    private FlowNode toEntity(Long flowId, FlowDesignModels.NodeInput input) {
        FlowNode node = new FlowNode();
        node.setFlowId(flowId);
        node.setNodeKey(input.nodeKey().strip());
        node.setNodeType(input.nodeType().strip().toUpperCase(Locale.ROOT));
        node.setName(input.name().strip());
        node.setPositionX(input.positionX());
        node.setPositionY(input.positionY());
        node.setAssigneePolicyJson(nullableJson(input.assigneePolicy()));
        node.setFormPolicyJson(nullableJson(input.formPolicy()));
        node.setTimeoutPolicyJson(nullableJson(input.timeoutPolicy()));
        node.setExceptionPolicyJson(nullableJson(input.exceptionPolicy()));
        node.setConfigJson(toJson(safeMap(input.config())));
        node.setVersion(0);
        return node;
    }

    private FlowEdge toEntity(Long flowId, FlowDesignModels.EdgeInput input) {
        FlowEdge edge = new FlowEdge();
        edge.setFlowId(flowId);
        edge.setEdgeKey(input.edgeKey().strip());
        edge.setSourceNodeKey(input.sourceNodeKey().strip());
        edge.setTargetNodeKey(input.targetNodeKey().strip());
        edge.setConditionExpression(blankToNull(input.conditionExpression()));
        edge.setPriorityOrder(input.priorityOrder() == null ? 0 : input.priorityOrder());
        edge.setConfigJson(toJson(safeMap(input.config())));
        edge.setVersion(0);
        return edge;
    }

    private FlowDesignModels.FlowView view(FlowDefinition flow, boolean includeGraph) {
        FlowPublication publication = publication(flow.getId());
        List<FlowDesignModels.NodeView> nodeViews = includeGraph ? nodes(flow.getId()).stream().map(this::view).toList() : List.of();
        List<FlowDesignModels.EdgeView> edgeViews = includeGraph ? edges(flow.getId()).stream().map(this::view).toList() : List.of();
        List<FlowDesignModels.VersionView> versionViews = includeGraph ? versions(flow.getId()).stream()
                .sorted(Comparator.comparing(FlowVersion::getVersionNumber).reversed())
                .map(this::view).toList() : List.of();
        return new FlowDesignModels.FlowView(
                flow.getId(), flow.getContextType(), flow.getPlatformId(), flow.getSystemId(), flow.getOwnerTenantId(),
                flow.getCode(), flow.getName(), flow.getDescription(), flow.getDraftRevision(), flow.getStatus(),
                flow.getVersion(), publication == null ? null : publication.getCurrentVersionId(),
                nodeViews, edgeViews, versionViews);
    }

    private FlowDesignModels.NodeView view(FlowNode node) {
        return new FlowDesignModels.NodeView(node.getId(), node.getNodeKey(), node.getNodeType(), node.getName(),
                node.getPositionX(), node.getPositionY(), fromJson(node.getAssigneePolicyJson()),
                fromJson(node.getFormPolicyJson()), fromJson(node.getTimeoutPolicyJson()),
                fromJson(node.getExceptionPolicyJson()), fromJson(node.getConfigJson()), node.getVersion());
    }

    private FlowDesignModels.EdgeView view(FlowEdge edge) {
        return new FlowDesignModels.EdgeView(edge.getId(), edge.getEdgeKey(), edge.getSourceNodeKey(),
                edge.getTargetNodeKey(), edge.getConditionExpression(), edge.getPriorityOrder(),
                fromJson(edge.getConfigJson()), edge.getVersion());
    }

    private FlowDesignModels.VersionView view(FlowVersion version) {
        return new FlowDesignModels.VersionView(version.getId(), version.getVersionNumber(), version.getDraftRevision(),
                version.getDefinitionHash(), version.getChangeSummary(), version.getPublishedByAccountId(),
                version.getPublishedAt(), fromJson(version.getSnapshotJson()),
                read(version.getSimulationResultJson(), FlowDesignModels.SimulationResult.class));
    }

    private Map<String, Object> snapshot(FlowDefinition flow, List<FlowNode> nodes, List<FlowEdge> edges) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("flowId", flow.getId());
        snapshot.put("contextType", flow.getContextType());
        snapshot.put("platformId", flow.getPlatformId());
        snapshot.put("systemId", flow.getSystemId());
        snapshot.put("tenantId", flow.getOwnerTenantId());
        snapshot.put("code", flow.getCode());
        snapshot.put("name", flow.getName());
        snapshot.put("draftRevision", flow.getDraftRevision());
        snapshot.put("nodes", nodes.stream().map(this::view).toList());
        snapshot.put("edges", edges.stream().map(this::view).toList());
        return snapshot;
    }

    private List<FlowDefinition> scopedDefinitions(AuthenticatedContext context) {
        var query = Wrappers.<FlowDefinition>lambdaQuery()
                .eq(FlowDefinition::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(FlowDefinition::getPlatformId, context.platformId());
        if (context.systemId() == null) {
            query.isNull(FlowDefinition::getSystemId).isNull(FlowDefinition::getOwnerTenantId);
        } else {
            query.eq(FlowDefinition::getSystemId, context.systemId())
                    .eq(FlowDefinition::getOwnerTenantId, context.tenantId());
        }
        return definitionService.selectList(query);
    }

    private FlowDefinition requireOwned(AuthenticatedContext context, Long id) {
        FlowDefinition flow = definitionService.selectById(id);
        if (flow == null || !owned(context, flow)) {
            throw new DomainException("FLOW_NOT_FOUND", "Flow 不存在或不在当前范围", HttpStatus.NOT_FOUND);
        }
        return flow;
    }

    private boolean owned(AuthenticatedContext context, FlowDefinition flow) {
        if (!Objects.equals(flow.getPlatformId(), context.platformId())) {
            return false;
        }
        if (context.systemId() == null) {
            return "PLATFORM".equals(flow.getContextType()) && flow.getSystemId() == null && flow.getOwnerTenantId() == null;
        }
        return "SYSTEM".equals(flow.getContextType()) && Objects.equals(flow.getSystemId(), context.systemId())
                && Objects.equals(flow.getOwnerTenantId(), context.tenantId());
    }

    private void bindContext(FlowDefinition flow, AuthenticatedContext context) {
        flow.setContextType(context.systemId() == null ? "PLATFORM" : "SYSTEM");
        flow.setPlatformId(context.platformId());
        flow.setSystemId(context.systemId());
        flow.setOwnerTenantId(context.systemId() == null ? null : context.tenantId());
    }

    private List<FlowNode> nodes(Long flowId) {
        return nodeService.selectList(Wrappers.<FlowNode>lambdaQuery().eq(FlowNode::getFlowId, flowId)
                .orderByAsc(FlowNode::getPositionX)
                .orderByAsc(FlowNode::getPositionY)
                .orderByAsc(FlowNode::getNodeKey));
    }

    private List<FlowEdge> edges(Long flowId) {
        return edgeService.selectList(Wrappers.<FlowEdge>lambdaQuery().eq(FlowEdge::getFlowId, flowId)
                .orderByAsc(FlowEdge::getPriorityOrder).orderByAsc(FlowEdge::getEdgeKey));
    }

    private List<FlowVersion> versions(Long flowId) {
        return versionService.selectList(Wrappers.<FlowVersion>lambdaQuery().eq(FlowVersion::getFlowId, flowId));
    }

    private FlowPublication publication(Long flowId) {
        return publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                .eq(FlowPublication::getFlowId, flowId)).stream().findFirst().orElse(null);
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (context == null || context.platformId() == null) {
            throw new DomainException("AUTHENTICATION_REQUIRED", "请先登录", HttpStatus.UNAUTHORIZED);
        }
        String resourceCode = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "FLOW", resourceCode, action)
                && !permissionChecker.allows(context, "FLOW", "*", action)) {
            throw new DomainException("PERMISSION_DENIED", "没有 Flow " + action + " 权限", HttpStatus.FORBIDDEN);
        }
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode,
                       String objectType, Object objectId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, objectType, String.valueOf(objectId), "SUCCESS", detail);
    }

    private String normalizeCode(String code) {
        String normalized = code.strip().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z][a-z0-9_-]{1,99}")) {
            throw invalid("FLOW_CODE_INVALID", "Flow 编码需以字母开头，只能包含小写字母、数字、下划线和横线");
        }
        return normalized;
    }

    private FlowDesignModels.Issue issue(String code, String location, String message) {
        return new FlowDesignModels.Issue(code, "node:" + location, message);
    }

    private boolean empty(Object value) {
        return value == null || value instanceof String string && string.isBlank()
                || value instanceof Map<?, ?> map && map.isEmpty()
                || value instanceof List<?> list && list.isEmpty();
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> safeMap(Map<String, Object> value) {
        return value == null ? Map.of() : value;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private String nullableJson(Map<String, Object> value) {
        return value == null || value.isEmpty() ? null : toJson(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Flow data", exception);
        }
    }

    private Map<String, Object> fromJson(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(value, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize Flow data", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot deserialize Flow data", exception);
        }
    }

    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }
}
