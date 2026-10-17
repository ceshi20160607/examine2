package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.ai.base.entity.AiModel;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrant;
import com.unique.unexamine.ai.base.entity.AiSystemModelGrantVersion;
import com.unique.unexamine.ai.base.service.AiModelBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantBaseService;
import com.unique.unexamine.ai.base.service.AiSystemModelGrantVersionBaseService;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.application.manage.ApplicationFlowCallbackService;
import com.unique.unexamine.application.manage.ApplicationModels;
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
            "START", "END", "APPROVAL", "GATEWAY", "SUBFLOW", "FORM_TASK", "CC", "NOTIFICATION", "MESSAGE",
            "UPDATE_FIELD", "DATA_ACTION", "WAIT", "TIMER", "WAIT_TIMER", "WEBHOOK", "EXTERNAL", "AI");
    /** 人工节点时限的冻结动作；提醒和转交只执行一次，不循环自动推进。 */
    private static final Set<String> TIMEOUT_ACTIONS = Set.of(
            "INCIDENT", "REMIND", "ESCALATE_MANAGER", "AUTO_APPROVE", "AUTO_REJECT", "TRANSFER");

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
    private final FlowConditionService flowConditions;
    private final FlowNodeFormService nodeForms;
    private final AiSystemModelGrantBaseService aiGrants;
    private final AiSystemModelGrantVersionBaseService aiGrantVersions;
    private final AiModelBaseService aiModels;
    /** 外部节点只选择应用模块的当前发布回调，Flow 不复制应用草稿能力。 */
    private final ApplicationFlowCallbackService applicationCallbacks;

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
            ObjectMapper objectMapper, FlowConditionService flowConditions, FlowNodeFormService nodeForms,
            ApplicationFlowCallbackService applicationCallbacks,
            AiSystemModelGrantBaseService aiGrants,
            AiSystemModelGrantVersionBaseService aiGrantVersions,
            AiModelBaseService aiModels) {
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
        this.flowConditions = flowConditions;
        this.nodeForms = nodeForms;
        this.applicationCallbacks = applicationCallbacks;
        this.aiGrants = aiGrants;
        this.aiGrantVersions = aiGrantVersions;
        this.aiModels = aiModels;
    }

    /** Flow 设计者只读取当前系统可用模型名称，不暴露密钥和系统 AI 配置权限。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> aiModels(AuthenticatedContext context) {
        requireAction(context, "DESIGN");
        if (context.systemId() == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (AiSystemModelGrant grant : aiGrants.selectList(Wrappers.<AiSystemModelGrant>lambdaQuery()
                .eq(AiSystemModelGrant::getSystemId, context.systemId())
                .eq(AiSystemModelGrant::getStatus, "ACTIVE").orderByAsc(AiSystemModelGrant::getId))) {
            AiModel model = aiModels.selectById(grant.getModelId());
            if (model == null || !"ACTIVE".equals(model.getStatus())
                    || !Objects.equals(model.getPlatformId(), context.platformId())
                    || "SYSTEM".equals(model.getScopeType())
                    && !Objects.equals(model.getSystemId(), context.systemId())) continue;
            result.add(Map.of("grantId", grant.getId(), "name", model.getName(),
                    "provider", model.getProvider()));
        }
        return List.copyOf(result);
    }

    /** 条件目录可供查看或设计流程的人读取，模块和字段权限仍由公共目录独立检查。 */
    @Transactional(readOnly = true)
    public List<FlowBindingModels.ConditionField> conditionFields(AuthenticatedContext context, Long moduleId) {
        String resource = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "FLOW", resource, "VIEW")
                && !permissionChecker.allows(context, "FLOW", resource, "DESIGN")) {
            throw new DomainException("PERMISSION_DENIED", "没有查看或设计 Flow 的权限", HttpStatus.FORBIDDEN);
        }
        return flowConditions.fields(context, moduleId);
    }

    /** 节点表单目录覆盖所有字段类型，仍要求当前 Flow 查看或设计以及业务模块权限。 */
    @Transactional(readOnly = true)
    public List<FlowDesignModels.NodeFormField> nodeFormFields(AuthenticatedContext context, Long moduleId) {
        String resource = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "FLOW", resource, "VIEW")
                && !permissionChecker.allows(context, "FLOW", resource, "DESIGN")) {
            throw new DomainException("PERMISSION_DENIED", "没有查看或设计 Flow 的权限", HttpStatus.FORBIDDEN);
        }
        return nodeForms.fields(context, moduleId);
    }

    /** 设计器不接收地址和密钥，只展示当前工作空间可用的已发布应用回调。 */
    @Transactional(readOnly = true)
    public List<ApplicationModels.FlowCallbackOption> callbackCatalog(AuthenticatedContext context) {
        requireAction(context, "DESIGN");
        return applicationCallbacks.catalog(context);
    }

    @Transactional(readOnly = true)
    public List<FlowDesignModels.FlowView> list(AuthenticatedContext context) {
        requireAction(context, "VIEW");
        return scopedDefinitions(context).stream()
                .sorted(Comparator.comparing(FlowDefinition::getUpdateTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(flow -> view(flow, false)).toList();
    }

    @Transactional
    public FlowDesignModels.FlowView create(
            AuthenticatedContext context, FlowDesignModels.CreateFlowRequest input, String traceId) {
        return create(context, input, null, traceId);
    }

    /** 外部应用建立流程时固化所属应用，后续所有草稿操作据此限制在同一应用。 */
    @Transactional
    public FlowDesignModels.FlowView createApplication(
            AuthenticatedContext context, FlowDesignModels.CreateFlowRequest input,
            Long applicationId, String traceId) {
        if (applicationId == null) {
            throw invalid("FLOW_APPLICATION_REQUIRED", "缺少配置流程的应用");
        }
        return create(context, input, applicationId, traceId);
    }

    private FlowDesignModels.FlowView create(
            AuthenticatedContext context, FlowDesignModels.CreateFlowRequest input,
            Long applicationId, String traceId) {
        requireAction(context, "DESIGN");
        String code = input.code() == null || input.code().isBlank()
                ? "flow_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : normalizeCode(input.code());
        if (scopedDefinitions(context).stream().anyMatch(flow -> code.equals(flow.getCode()))) {
            throw new DomainException("FLOW_CODE_CONFLICT", "当前范围已存在相同 Flow 编码", HttpStatus.CONFLICT);
        }
        FlowDefinition flow = new FlowDefinition();
        bindContext(flow, context);
        flow.setOwnerApplicationId(applicationId);
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

    /** 修改流程显示名，不改变外部应用及运行实例使用的稳定编码。 */
    @Transactional
    public FlowDesignModels.FlowView rename(AuthenticatedContext context, Long flowId,
                                            FlowDesignModels.RenameFlowRequest input, String traceId) {
        requireAction(context, "DESIGN");
        FlowDefinition flow = requireOwned(context, flowId);
        if (!Objects.equals(flow.getVersion(), input.expectedVersion())) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "Flow 草稿已被其他人修改，请刷新后重试");
        }
        flow.setName(input.name().strip());
        flow.setDraftRevision(flow.getDraftRevision() + 1);
        if (definitionService.updateById(flow) != 1) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "Flow 草稿已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "FLOW_NAME_CHANGED", "FLOW", flowId,
                Map.of("code", flow.getCode(), "name", flow.getName()));
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
        // 修改草稿不撤销现行发布版本；校验失败时原版本仍可继续发起。
        flow.setStatus("DISABLED".equals(flow.getStatus()) ? "DISABLED"
                : publication(flowId) == null ? "DRAFT" : "PUBLISHED");
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
        Map<String, Object> snapshot = snapshot(context, flow, nodes, edges);
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
        // 停用期间允许准备新版本，但发布本身不能暗中恢复新发起资格。
        if (!"DISABLED".equals(flow.getStatus())) flow.setStatus("PUBLISHED");
        if (definitionService.updateById(flow) != 1) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "Flow 草稿已被其他人修改，请重试");
        }
        audit(context, traceId, "FLOW_VERSION_PUBLISHED", "FLOW_VERSION", version.getId(),
                Map.of("flowId", flowId, "versionNumber", versionNumber, "definitionHash", hash));
        return new FlowDesignModels.PublicationResult(
                flowId, version.getId(), versionNumber, flow.getDraftRevision(), hash, previousVersionId, simulation);
    }

    /** 停用只阻断新实例和绑定命中，在途实例继续按发起时的不可变快照办理。 */
    @Transactional
    public FlowDesignModels.FlowView changeStatus(AuthenticatedContext context, Long flowId,
            FlowDesignModels.ChangeStatusRequest input, String traceId) {
        requireAction(context, "PUBLISH");
        FlowDefinition flow = requireOwned(context, flowId);
        if (!Objects.equals(flow.getVersion(), input.expectedVersion())) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "流程配置已被其他人修改，请刷新后重试");
        }
        String target = input.status().strip().toUpperCase(Locale.ROOT);
        if (!Set.of("PUBLISHED", "DISABLED").contains(target) || publication(flowId) == null) {
            throw invalid("FLOW_STATUS_INVALID", "只能停用或重新启用已有发布版本的流程");
        }
        if (target.equals(flow.getStatus())) return view(flow, true);
        flow.setStatus(target);
        if (definitionService.updateById(flow) != 1) {
            throw conflict("FLOW_DRAFT_VERSION_CONFLICT", "流程配置已被其他人修改，请刷新后重试");
        }
        audit(context, traceId, "DISABLED".equals(target) ? "FLOW_DISABLED" : "FLOW_ENABLED",
                "FLOW", flowId, Map.of("status", target));
        return view(requireOwned(context, flowId), true);
    }

    private FlowDesignModels.SimulationResult simulateGraph(
            AuthenticatedContext context, FlowDefinition flow, List<FlowNode> nodes, List<FlowEdge> edges,
            Map<String, Object> variables) {
        List<FlowDesignModels.Issue> issues = validateGraph(context, flow, nodes, edges);
        if (!issues.isEmpty()) {
            return new FlowDesignModels.SimulationResult(
                    flow.getId(), flow.getDraftRevision(), false, List.of(), issues, variables);
        }
        Map<String, Object> conditionContext = flowConditions.context(context, nodes);
        Map<String, FlowNode> byKey = new HashMap<>();
        nodes.forEach(node -> byKey.put(node.getNodeKey(), node));
        Map<String, List<FlowEdge>> outgoing = outgoing(edges);
        String start = nodes.stream().filter(node -> "START".equals(node.getNodeType()))
                .findFirst().orElseThrow().getNodeKey();
        List<FlowDesignModels.SimulationStep> steps = new ArrayList<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(start);
        Map<String, Integer> expectedArrivals = new HashMap<>();
        Map<String, Integer> actualArrivals = new HashMap<>();
        int endCount = 0;
        int limit = Math.max(100, nodes.size() * Math.max(4, edges.size() + 1));
        for (int count = 0; !pending.isEmpty() && count < limit; count++) {
            String current = pending.removeFirst();
            FlowNode node = byKey.get(current);
            if (node == null) {
                issues.add(issue("FLOW_SIMULATION_NODE_MISSING", "node:" + current, "模拟路径引用了不存在的节点"));
                break;
            }
            if ("END".equals(node.getNodeType())) {
                steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(), "COMPLETED"));
                endCount++;
                continue;
            }
            List<FlowEdge> candidates = outgoing.getOrDefault(current, List.of());
            String gatewayMode = "GATEWAY".equals(node.getNodeType())
                    ? FlowGatewayPolicy.mode(fromJson(node.getConfigJson())) : FlowGatewayPolicy.EXCLUSIVE;
            if (FlowGatewayPolicy.split(gatewayMode)) {
                List<FlowEdge> selected = selectedGatewayEdges(gatewayMode, candidates, conditionContext, variables);
                if (selected.isEmpty()) {
                    issues.add(issue("NO_MATCHING_PATH", "node:" + current, "当前模拟数据没有命中任何包容分支"));
                    break;
                }
                String mergeNodeKey = String.valueOf(fromJson(node.getConfigJson()).get(FlowGatewayPolicy.MERGE_NODE_KEY));
                expectedArrivals.put(mergeNodeKey, selected.size());
                steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(), "SPLIT_" + selected.size()));
                selected.forEach(edge -> pending.addLast(edge.getTargetNodeKey()));
                continue;
            }
            if (FlowGatewayPolicy.MERGE.equals(gatewayMode)) {
                int arrived = actualArrivals.merge(current, 1, Integer::sum);
                int expected = expectedArrivals.getOrDefault(current, Integer.MAX_VALUE);
                if (arrived < expected) {
                    steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(),
                            "JOIN_WAITING_" + arrived + "_OF_" + expected));
                    continue;
                }
                steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(), "JOINED_" + arrived));
            } else if ("SUBFLOW".equals(node.getNodeType())) {
                FlowSubflowPolicy.Config subflow = FlowSubflowPolicy.parse(fromJson(node.getConfigJson()));
                steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(),
                        "CHILD_FLOW_" + subflow.flowId()));
            } else {
                steps.add(new FlowDesignModels.SimulationStep(current, node.getNodeType(), "VISITED"));
            }
            FlowEdge chosen = firstMatchingEdge(candidates, conditionContext, variables);
            if (chosen == null) {
                issues.add(issue("NO_MATCHING_PATH", "node:" + current, "当前模拟数据没有匹配的后续路径"));
                break;
            }
            pending.addLast(chosen.getTargetNodeKey());
        }
        if (!pending.isEmpty() && issues.isEmpty()) {
            issues.add(issue("FLOW_CYCLE_DETECTED", "graph", "模拟路径超过安全步数，请检查流程中的循环连线"));
        }
        if (issues.isEmpty() && endCount > 0 && actualArrivals.entrySet().stream()
                .allMatch(entry -> Objects.equals(entry.getValue(), expectedArrivals.get(entry.getKey())))) {
            return new FlowDesignModels.SimulationResult(
                    flow.getId(), flow.getDraftRevision(), true, steps, List.of(), variables);
        }
        return new FlowDesignModels.SimulationResult(
                flow.getId(), flow.getDraftRevision(), false, steps, issues, variables);
    }

    private List<FlowDesignModels.Issue> validateGraph(
            AuthenticatedContext context, FlowDefinition flow, List<FlowNode> nodes, List<FlowEdge> edges) {
        List<FlowDesignModels.Issue> issues = new ArrayList<>();
        issues.addAll(flowConditions.inspect(context, nodes, edges));
        issues.addAll(nodeForms.inspect(context, nodes));
        validateAutomaticActions(context, nodes, edges, issues);
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
        validateGateways(nodes, edges, byKey, outgoing, issues);
        for (FlowNode node : nodes) {
            validateRepeatApproval(node, byKey, outgoing, issues);
            validateReturnPolicy(node, byKey, outgoing, issues);
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

    /** 发布前固定分支与汇合配对，运行时不再猜测哪几条路径属于同一组。 */
    private void validateGateways(List<FlowNode> nodes, List<FlowEdge> edges, Map<String, FlowNode> byKey,
                                  Map<String, List<FlowEdge>> outgoing, List<FlowDesignModels.Issue> issues) {
        Map<String, List<FlowEdge>> incoming = new HashMap<>();
        edges.forEach(edge -> incoming.computeIfAbsent(edge.getTargetNodeKey(), ignored -> new ArrayList<>()).add(edge));
        Map<String, String> claimedMerges = new HashMap<>();
        for (FlowNode node : nodes) {
            if (!"GATEWAY".equals(node.getNodeType())) continue;
            Map<String, Object> config = fromJson(node.getConfigJson());
            String mode = FlowGatewayPolicy.mode(config);
            if (!FlowGatewayPolicy.MODES.contains(mode)) {
                issues.add(issue("FLOW_GATEWAY_MODE_INVALID", node.getNodeKey(), "请选择排他、并行、包容或汇合网关"));
                continue;
            }
            List<FlowEdge> outputs = outgoing.getOrDefault(node.getNodeKey(), List.of());
            List<FlowEdge> inputs = incoming.getOrDefault(node.getNodeKey(), List.of());
            if (FlowGatewayPolicy.EXCLUSIVE.equals(mode)) continue;
            if (FlowGatewayPolicy.MERGE.equals(mode)) {
                if (inputs.size() < 2 || outputs.size() != 1) {
                    issues.add(issue("FLOW_GATEWAY_MERGE_TOPOLOGY_INVALID", node.getNodeKey(), "汇合网关至少需要两条入线且只能有一条出线"));
                }
                if (outputs.stream().anyMatch(this::conditionalEdge)) {
                    issues.add(issue("FLOW_GATEWAY_MERGE_CONDITION_INVALID", node.getNodeKey(), "汇合后的出线不能配置分支条件"));
                }
                String splitKey = text(config.get(FlowGatewayPolicy.SPLIT_NODE_KEY));
                FlowNode split = byKey.get(splitKey);
                if (split == null || !"GATEWAY".equals(split.getNodeType())
                        || !FlowGatewayPolicy.split(FlowGatewayPolicy.mode(fromJson(split.getConfigJson())))) {
                    issues.add(issue("FLOW_GATEWAY_SPLIT_MISSING", node.getNodeKey(), "汇合网关必须选择配对的并行或包容网关"));
                }
                continue;
            }
            if (inputs.size() != 1 || outputs.size() < 2) {
                issues.add(issue("FLOW_GATEWAY_SPLIT_TOPOLOGY_INVALID", node.getNodeKey(), "并行或包容网关必须有一条入线和至少两条出线"));
            }
            if (outputs.stream().map(FlowEdge::getTargetNodeKey).distinct().count() != outputs.size()) {
                issues.add(issue("FLOW_GATEWAY_BRANCH_DUPLICATE", node.getNodeKey(), "每条分支必须进入不同的首节点"));
            }
            String mergeKey = text(config.get(FlowGatewayPolicy.MERGE_NODE_KEY));
            FlowNode merge = byKey.get(mergeKey);
            if (merge == null || !"GATEWAY".equals(merge.getNodeType())
                    || !FlowGatewayPolicy.MERGE.equals(FlowGatewayPolicy.mode(fromJson(merge.getConfigJson())))) {
                issues.add(issue("FLOW_GATEWAY_MERGE_MISSING", node.getNodeKey(), "并行或包容网关必须选择配对的汇合网关"));
                continue;
            }
            String previous = claimedMerges.putIfAbsent(mergeKey, node.getNodeKey());
            if (previous != null && !previous.equals(node.getNodeKey())) {
                issues.add(issue("FLOW_GATEWAY_MERGE_REUSED", node.getNodeKey(), "一个汇合网关只能配对一个分支网关"));
            }
            if (!node.getNodeKey().equals(text(fromJson(merge.getConfigJson()).get(FlowGatewayPolicy.SPLIT_NODE_KEY)))) {
                issues.add(issue("FLOW_GATEWAY_PAIR_MISMATCH", node.getNodeKey(), "分支网关与汇合网关必须相互选择"));
            }
            if (FlowGatewayPolicy.PARALLEL.equals(mode) && outputs.stream().anyMatch(this::conditionalEdge)) {
                issues.add(issue("FLOW_GATEWAY_PARALLEL_CONDITION_INVALID", node.getNodeKey(), "并行网关会执行全部分支，出线不能配置条件"));
            }
            if (FlowGatewayPolicy.INCLUSIVE.equals(mode)) {
                long defaults = outputs.stream().filter(edge -> !conditionalEdge(edge)).count();
                if (defaults != 1) {
                    issues.add(issue("FLOW_GATEWAY_INCLUSIVE_DEFAULT_INVALID", node.getNodeKey(), "包容网关必须且只能保留一条无条件兜底分支"));
                }
            }
            validateBranchRegions(node.getNodeKey(), mergeKey, outputs, outgoing, issues);
        }
    }

    /** 各分支在汇合前不能交叉或提前结束，避免同一任务被多个执行游标争用。 */
    private void validateBranchRegions(String splitKey, String mergeKey, List<FlowEdge> outputs,
                                       Map<String, List<FlowEdge>> outgoing, List<FlowDesignModels.Issue> issues) {
        Set<String> claimed = new HashSet<>();
        for (FlowEdge output : outputs) {
            Set<String> region = reachableBeforeMerge(output.getTargetNodeKey(), mergeKey, outgoing);
            if (!region.remove(mergeKey) || region.contains(splitKey)
                    || region.stream().anyMatch(key -> !graphReaches(key, mergeKey, outgoing))) {
                issues.add(issue("FLOW_GATEWAY_BRANCH_CANNOT_MERGE", output.getEdgeKey(), "每条分支都必须在结束前到达配对的汇合网关"));
            }
            if (hasCycleBeforeMerge(output.getTargetNodeKey(), mergeKey, outgoing, new HashSet<>(), new HashSet<>())) {
                issues.add(issue("FLOW_GATEWAY_BRANCH_CYCLE", output.getEdgeKey(), "分支在汇合前不能形成循环"));
            }
            Set<String> overlap = new HashSet<>(region);
            overlap.retainAll(claimed);
            if (!overlap.isEmpty()) {
                issues.add(issue("FLOW_GATEWAY_BRANCH_OVERLAP", output.getEdgeKey(), "不同分支不能在配对汇合网关之前交叉"));
            }
            claimed.addAll(region);
        }
    }

    private Set<String> reachableBeforeMerge(String start, String mergeKey, Map<String, List<FlowEdge>> outgoing) {
        Set<String> reached = new LinkedHashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!reached.add(current) || current.equals(mergeKey)) continue;
            outgoing.getOrDefault(current, List.of()).forEach(edge -> pending.add(edge.getTargetNodeKey()));
        }
        return reached;
    }

    private boolean hasCycleBeforeMerge(String current, String mergeKey, Map<String, List<FlowEdge>> outgoing,
                                        Set<String> visiting, Set<String> visited) {
        if (current.equals(mergeKey) || visited.contains(current)) return false;
        if (!visiting.add(current)) return true;
        for (FlowEdge edge : outgoing.getOrDefault(current, List.of())) {
            if (hasCycleBeforeMerge(edge.getTargetNodeKey(), mergeKey, outgoing, visiting, visited)) return true;
        }
        visiting.remove(current);
        visited.add(current);
        return false;
    }

    private List<FlowEdge> selectedGatewayEdges(String mode, List<FlowEdge> candidates,
                                                Map<String, Object> conditionContext, Map<String, Object> variables) {
        if (FlowGatewayPolicy.PARALLEL.equals(mode)) return candidates;
        List<FlowEdge> matched = candidates.stream().filter(this::conditionalEdge)
                .filter(edge -> matches(edge, conditionContext, variables)).toList();
        if (!matched.isEmpty()) return matched;
        return candidates.stream().filter(edge -> !conditionalEdge(edge)).findFirst().map(List::of).orElse(List.of());
    }

    private FlowEdge firstMatchingEdge(List<FlowEdge> candidates, Map<String, Object> conditionContext,
                                       Map<String, Object> variables) {
        return candidates.stream().filter(edge -> matches(edge, conditionContext, variables)).findFirst().orElse(null);
    }

    private boolean matches(FlowEdge edge, Map<String, Object> conditionContext, Map<String, Object> variables) {
        return flowConditions.matches(conditionContext, objectMapper.valueToTree(fromJson(edge.getConfigJson())),
                edge.getConditionExpression(), variables);
    }

    private boolean conditionalEdge(FlowEdge edge) {
        return fromJson(edge.getConfigJson()).containsKey("condition")
                || edge.getConditionExpression() != null && !edge.getConditionExpression().isBlank();
    }

    private String text(Object value) {
        return value instanceof String text ? text.strip() : "";
    }

    /** 同人后续审批只引用图中真正前序的审批节点；变更候选或变量后必须重新人工审批。 */
    private void validateRepeatApproval(FlowNode node, Map<String, FlowNode> byKey,
                                        Map<String, List<FlowEdge>> outgoing, List<FlowDesignModels.Issue> issues) {
        Object configured = fromJson(node.getConfigJson()).get("repeatApproval");
        if (configured == null) return;
        if (!(configured instanceof Map<?, ?> policy)
                || policy.containsKey("enabled") && !(policy.get("enabled") instanceof Boolean)) {
            issues.add(issue("FLOW_REPEAT_APPROVAL_CONFIG_INVALID", node.getNodeKey(), "同人后续审批配置无效，请使用节点中的结构化设置"));
            return;
        }
        if (!Boolean.TRUE.equals(policy.get("enabled"))) return;
        if (!"APPROVAL".equals(node.getNodeType())) {
            issues.add(issue("FLOW_REPEAT_APPROVAL_NODE_INVALID", node.getNodeKey(), "同人后续审批只能用于审批节点，不能自动完成填写任务"));
            return;
        }
        if (!"UNCHANGED".equals(policy.get("formChangeRule"))) {
            issues.add(issue("FLOW_REPEAT_APPROVAL_CHANGE_RULE_INVALID", node.getNodeKey(), "必须启用候选内容及流程变量未变化规则，变化后重新人工审批"));
        }
        if (!(policy.get("sourceNodeKeys") instanceof List<?> sources) || sources.isEmpty()) {
            issues.add(issue("FLOW_REPEAT_APPROVAL_SOURCE_MISSING", node.getNodeKey(), "启用同人后续审批时必须选择至少一个前序审批节点"));
            return;
        }
        Set<String> selected = new HashSet<>();
        for (Object value : sources) {
            if (!(value instanceof String key) || key.isBlank() || !selected.add(key)) {
                issues.add(issue("FLOW_REPEAT_APPROVAL_SOURCE_INVALID", node.getNodeKey(), "前序范围必须是非空且不重复的审批节点"));
                continue;
            }
            FlowNode source = byKey.get(key);
            if (source == null || !"APPROVAL".equals(source.getNodeType()) || key.equals(node.getNodeKey())
                    || !graphReaches(key, node.getNodeKey(), outgoing) || graphReaches(node.getNodeKey(), key, outgoing)) {
                issues.add(issue("FLOW_REPEAT_APPROVAL_SOURCE_INVALID", node.getNodeKey(), "所选范围必须是可到达当前节点的前序审批，不能选择自身、后续、填写或失效节点"));
            }
        }
        // 附加业务条件的结构、非空约束及字段权限由 FlowConditionService 统一检查和冻结。
    }

    /** 按真实连线判断前序关系，访问集合避免循环图把后续节点伪装成前序。 */
    private boolean graphReaches(String source, String target, Map<String, List<FlowEdge>> outgoing) {
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty()) {
            String key = pending.removeFirst();
            if (!visited.add(key)) continue;
            if (key.equals(target)) return true;
            outgoing.getOrDefault(key, List.of()).forEach(edge -> pending.add(edge.getTargetNodeKey()));
        }
        return false;
    }

    /** 退回目标只允许当前节点之前可达的人工节点；运行时“上一节点”还会按实例实际路径再次解析。 */
    private void validateReturnPolicy(FlowNode node, Map<String, FlowNode> byKey,
                                      Map<String, List<FlowEdge>> outgoing, List<FlowDesignModels.Issue> issues) {
        Object configured = fromJson(node.getConfigJson()).get("returnPolicy");
        if (configured == null) return;
        if (!Set.of("APPROVAL", "FORM_TASK").contains(node.getNodeType()) || !(configured instanceof Map<?, ?> policy)
                || !(policy.get("target") instanceof String target)
                || !(policy.get("resubmitRoute") instanceof String route)
                || !Set.of("STARTER", "PREVIOUS", "SPECIFIED").contains(target)
                || !Set.of("FROM_START", "RETURN_NODE").contains(route)) {
            issues.add(issue("FLOW_RETURN_POLICY_INVALID", node.getNodeKey(), "请使用节点中的结构化设置选择退回目标和重新提交路径"));
            return;
        }
        if ("SPECIFIED".equals(target)) {
            if (!policy.keySet().equals(Set.of("target", "targetNodeKey", "resubmitRoute"))
                    || !(policy.get("targetNodeKey") instanceof String targetKey) || targetKey.isBlank()) {
                issues.add(issue("FLOW_RETURN_TARGET_INVALID", node.getNodeKey(), "退回到指定节点时必须选择一个前序人工节点"));
                return;
            }
            FlowNode selected = byKey.get(targetKey);
            if (selected == null || !Set.of("APPROVAL", "FORM_TASK").contains(selected.getNodeType())
                    || targetKey.equals(node.getNodeKey()) || !graphReaches(targetKey, node.getNodeKey(), outgoing)
                    || graphReaches(node.getNodeKey(), targetKey, outgoing)) {
                issues.add(issue("FLOW_RETURN_TARGET_INVALID", node.getNodeKey(), "指定退回目标必须是可到达当前节点的前序审批或填写节点"));
            }
            return;
        }
        if (!policy.keySet().equals(Set.of("target", "resubmitRoute"))) {
            issues.add(issue("FLOW_RETURN_POLICY_INVALID", node.getNodeKey(), "当前退回方式不能保留指定节点，请重新选择"));
            return;
        }
        if ("PREVIOUS".equals(target)) {
            boolean hasPrevious = byKey.values().stream().anyMatch(candidate -> !candidate.getNodeKey().equals(node.getNodeKey())
                    && Set.of("APPROVAL", "FORM_TASK").contains(candidate.getNodeType())
                    && graphReaches(candidate.getNodeKey(), node.getNodeKey(), outgoing)
                    && !graphReaches(node.getNodeKey(), candidate.getNodeKey(), outgoing));
            if (!hasPrevious) issues.add(issue("FLOW_RETURN_PREVIOUS_MISSING", node.getNodeKey(), "当前节点之前没有可退回的人工节点"));
        }
    }

    private void validateNode(
            AuthenticatedContext context, FlowDefinition flow, FlowNode node,
            List<FlowDesignModels.Issue> issues) {
        String type = node.getNodeType();
        Map<String, Object> config = fromJson(node.getConfigJson());
        Map<String, Object> assignee = fromJson(node.getAssigneePolicyJson());
        Map<String, Object> form = fromJson(node.getFormPolicyJson());
        if ("START".equals(type) && config.containsKey("runtimeRules")) {
            if (!(config.get("runtimeRules") instanceof Map<?, ?> rules)
                    || rules.containsKey("allowAddNodes") && !(rules.get("allowAddNodes") instanceof Boolean)) {
                issues.add(issue("FLOW_RUNTIME_RULES_INVALID", node.getNodeKey(), "是否允许动态增加节点必须使用开关配置"));
            }
        }
        if (!NODE_TYPES.contains(type)) {
            issues.add(issue("NODE_TYPE_INVALID", node.getNodeKey(), "不支持的节点类型：" + type));
            return;
        }
        // 人工操作策略独立校验，不能因未配置审批人而跳过；发布、模拟与检查共用此入口。
        if (Set.of("APPROVAL", "FORM_TASK").contains(type)) {
            try {
                FlowManualOperationPolicy operations = FlowManualOperationPolicy.fromConfig(config);
                if (operations.requireClaim() && !"OR_SIGN".equals(config.getOrDefault("approvalMode", "OR_SIGN"))) {
                    issues.add(issue("FLOW_TASK_CLAIM_MODE_INVALID", node.getNodeKey(), "必须领取仅适用于或签任务，请调整审批方式或关闭领取要求"));
                }
            } catch (DomainException failure) { issues.add(issue(failure.code(), node.getNodeKey(), failure.getMessage())); }
            try {
                FlowApprovalAttachmentPolicy.fromConfig(config);
            } catch (DomainException failure) { issues.add(issue(failure.code(), node.getNodeKey(), failure.getMessage())); }
        } else if (config.containsKey(FlowManualOperationPolicy.CONFIG_KEY)) {
            issues.add(issue("FLOW_MANUAL_OPERATION_POLICY_INVALID", node.getNodeKey(), "只有人工节点可以配置人工操作"));
        } else if (config.containsKey(FlowApprovalAttachmentPolicy.CONFIG_KEY)) {
            issues.add(issue("FLOW_APPROVAL_ATTACHMENT_POLICY_INVALID", node.getNodeKey(), "只有审批或填写节点可以配置附件要求"));
        }
        if (Set.of("APPROVAL", "FORM_TASK").contains(type) && assignee.isEmpty()) {
            issues.add(issue("APPROVER_MISSING", node.getNodeKey(), "FORM_TASK".equals(type)
                    ? "填写表单节点必须配置处理人策略" : "审批节点必须配置审批人策略"));
        } else if (Set.of("APPROVAL", "FORM_TASK").contains(type)) {
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
            if ("EXTERNAL_PERSON".equals(assignee.get("type"))) {
                // 外部身份只由应用签名和标识比较；复杂会签与内部任务转交不能借用平台人员身份。
                if (!"APPROVAL".equals(type) || !"OR_SIGN".equals(approvalMode)) {
                    issues.add(issue("FLOW_EXTERNAL_APPROVER_MODE_INVALID", node.getNodeKey(),
                            "外部应用审批人当前只支持单人或签审批节点"));
                }
                FlowManualOperationPolicy externalOperations = FlowManualOperationPolicy.fromConfig(config);
                if (externalOperations.requireClaim()
                        || !Set.of("APPROVE", "REJECT").containsAll(externalOperations.allowedActions())) {
                    issues.add(issue("FLOW_EXTERNAL_APPROVER_ACTION_INVALID", node.getNodeKey(),
                            "外部应用审批人只允许同意或拒绝，不能领取、转交或加减签"));
                }
            }
            try {
                FlowApprovalCompletionPolicy.fromConfig(config, approvalMode);
            } catch (DomainException failure) {
                issues.add(issue(failure.code(), node.getNodeKey(), failure.getMessage()));
            }
            // 审批会签已支持候选版本核对；填写节点的并发输入协议仍未接通。
            if ("FORM_TASK".equals(type) && "ALL_SIGN".equals(approvalMode)) {
                issues.add(issue("FORM_TASK_MODE_NOT_AVAILABLE", node.getNodeKey(), "填写节点的并发会签字段合并尚未接通，请使用或签或顺序填写"));
            }
        }
        if ("FORM_TASK".equals(type) && form.isEmpty()) {
            issues.add(issue("FORM_POLICY_MISSING", node.getNodeKey(), "表单任务必须配置字段权限"));
        }
        if ("FORM_TASK".equals(type) && (!(form.get("editable") instanceof List<?> editable) || editable.isEmpty())) {
            issues.add(issue("FORM_EDITABLE_MISSING", node.getNodeKey(), "填写表单节点必须选择可编辑字段"));
        }
        validateTimeout(context, flow, node, issues);
        if (FlowMessageNodePolicy.supports(type)) {
            if (assignee.isEmpty()) {
                issues.add(issue("FLOW_MESSAGE_RECIPIENT_MISSING", node.getNodeKey(),
                        FlowMessageNodePolicy.kindLabel(type) + "节点必须配置接收人策略"));
            } else {
                String policyIssue = participantResolver.validatePolicy(context, assignee);
                if (policyIssue != null) {
                    issues.add(issue("FLOW_MESSAGE_RECIPIENT_INVALID", node.getNodeKey(), policyIssue));
                }
            }
            try {
                FlowMessageNodePolicy.parse(type, config);
            } catch (IllegalArgumentException failure) {
                issues.add(issue("FLOW_MESSAGE_CONFIG_INVALID", node.getNodeKey(), failure.getMessage()));
            }
        }
        validateSubflow(context, flow, node, config, issues);
        if (FlowExternalCallbackPolicy.supports(type)) {
            try {
                FlowExternalCallbackPolicy.Selection selection = FlowExternalCallbackPolicy.parseSelection(config);
                applicationCallbacks.requirePublished(context, selection.applicationId(), selection.callbackId());
            } catch (IllegalArgumentException | DomainException failure) {
                issues.add(issue("FLOW_CALLBACK_SELECTION_INVALID", node.getNodeKey(), failure.getMessage()));
            }
        }
        if ("AI".equals(type)) {
            try {
                requireAiBinding(context, config);
            } catch (DomainException | IllegalArgumentException failure) {
                issues.add(issue("FLOW_AI_MODEL_INVALID", node.getNodeKey(), failure.getMessage()));
            }
        }
        if (FlowWaitTimerPolicy.supports(type)) {
            try {
                FlowWaitTimerPolicy.parse(type, config);
            } catch (IllegalArgumentException failure) {
                issues.add(issue("FLOW_WAIT_TIMER_CONFIG_INVALID", node.getNodeKey(), failure.getMessage()));
            }
        }
        Long moduleId = longValue(config.get("moduleId"));
        // 开始节点采用当前有效发布配置，允许合法继承主租户模块；动作节点仍保留原归属限制。
        if (moduleId != null && !"START".equals(type)) {
            ConfiguredModule module = moduleService.selectById(moduleId);
            if (context.systemId() == null || module == null
                    || !Objects.equals(module.getSystemId(), context.systemId())
                    || !Objects.equals(module.getOwnerTenantId(), context.tenantId())) {
                issues.add(issue("MODULE_REFERENCE_FORBIDDEN", node.getNodeKey(), "引用的模块不在当前系统租户范围"));
            }
        }
    }

    /** 自动业务节点在发布前核对当前模块、动作权限、字段身份和结构化取值来源。 */
    private void validateAutomaticActions(AuthenticatedContext context, List<FlowNode> nodes, List<FlowEdge> edges,
                                          List<FlowDesignModels.Issue> issues) {
        Long startModuleId = startModuleId(nodes);
        Map<String, FlowNode> byKey = new LinkedHashMap<>();
        nodes.forEach(node -> byKey.put(node.getNodeKey(), node));
        List<FlowDesignModels.NodeFormField> sourceFields = List.of();
        if (startModuleId != null) {
            try {
                sourceFields = nodeForms.fields(context, startModuleId);
            } catch (DomainException ignored) {
                // 开始节点的模块错误已由条件/表单公共检查给出，这里不重复生成另一条错误。
            }
        }
        Map<String, FlowDesignModels.NodeFormField> sourceByCode = sourceFields.stream()
                .collect(java.util.stream.Collectors.toMap(FlowDesignModels.NodeFormField::code, field -> field,
                        (left, right) -> left, LinkedHashMap::new));
        for (FlowNode node : nodes) {
            if (!Set.of(FlowAutomaticActionPolicy.UPDATE_FIELD, FlowAutomaticActionPolicy.DATA_ACTION)
                    .contains(node.getNodeType())) continue;
            try {
                FlowAutomaticActionPolicy.Config policy = FlowAutomaticActionPolicy.parse(
                        node.getNodeType(), fromJson(node.getConfigJson()));
                Long moduleId = policy.moduleId() == null && FlowAutomaticActionPolicy.UPDATE_FIELD.equals(node.getNodeType())
                        ? startModuleId : policy.moduleId();
                if (moduleId == null) throw new IllegalArgumentException("请选择自动业务节点的目标模块");
                ConfiguredModule module = moduleService.selectById(moduleId);
                if (module == null || !"ACTIVE".equals(module.getStatus()) || context.systemId() == null
                        || !Objects.equals(module.getSystemId(), context.systemId())
                        || !Objects.equals(module.getOwnerTenantId(), context.tenantId())) {
                    throw new IllegalArgumentException("目标模块不在当前系统租户或已停用");
                }
                if (!permissionChecker.allows(context, "MODULE", module.getCode(), policy.operation())) {
                    throw new DomainException("PERMISSION_DENIED", "没有目标模块的"
                            + ("CREATE".equals(policy.operation()) ? "新建" : "更新") + "权限", HttpStatus.FORBIDDEN);
                }
                flowConditions.businessPublication(context, moduleId);
                Map<String, FlowDesignModels.NodeFormField> targetFields = nodeForms.fields(context, moduleId).stream()
                        .filter(FlowDesignModels.NodeFormField::editable)
                        .collect(java.util.stream.Collectors.toMap(FlowDesignModels.NodeFormField::code, field -> field,
                                (left, right) -> left, LinkedHashMap::new));
                for (FlowAutomaticActionPolicy.Assignment assignment : policy.assignments()) {
                    FlowDesignModels.NodeFormField targetField = targetFields.get(assignment.fieldCode());
                    if (targetField == null) {
                        throw new IllegalArgumentException("目标字段“" + assignment.fieldCode() + "”不存在、只读或当前不可写");
                    }
                    validateAutomaticValue(assignment.value(), targetField, sourceByCode,
                            "字段“" + assignment.fieldCode() + "”");
                }
                validateAutomaticVariable(policy.title(), sourceByCode, "新建数据标题");
                if (policy.title() != null && "FIXED".equals(policy.title().source())
                        && (policy.title().value() == null || String.valueOf(policy.title().value()).isBlank())) {
                    throw new IllegalArgumentException("新建数据标题不能为空");
                }
                validateAutomaticTarget(node, policy, moduleId, startModuleId, sourceByCode, byKey, edges);
            } catch (DomainException | IllegalArgumentException invalid) {
                issues.add(issue("FLOW_AUTOMATIC_ACTION_INVALID", node.getNodeKey(), invalid.getMessage()));
            }
        }
    }

    private void validateAutomaticVariable(FlowAutomaticActionPolicy.ValueSource value,
                                           Map<String, FlowDesignModels.NodeFormField> sourceFields, String label) {
        if (value != null && "VARIABLE".equals(value.source()) && !sourceFields.containsKey(value.variable())) {
            throw new IllegalArgumentException(label + "引用的业务字段不存在或当前不可读");
        }
    }

    private void validateAutomaticValue(FlowAutomaticActionPolicy.ValueSource value,
                                        FlowDesignModels.NodeFormField target,
                                        Map<String, FlowDesignModels.NodeFormField> sourceFields, String label) {
        if ("VARIABLE".equals(value.source())) {
            FlowDesignModels.NodeFormField source = sourceFields.get(value.variable());
            if (source == null) throw new IllegalArgumentException(label + "引用的业务字段不存在或当前不可读");
            boolean numeric = Set.of("NUMBER", "MONEY").contains(source.fieldType())
                    && Set.of("NUMBER", "MONEY").contains(target.fieldType());
            if (!numeric && !Objects.equals(source.fieldType(), target.fieldType())) {
                throw new IllegalArgumentException(label + "的来源字段类型与目标字段不一致");
            }
            return;
        }
        Object fixed = value.value();
        String type = target.fieldType();
        boolean valid = fixed == null || switch (type) {
            case "NUMBER", "MONEY" -> fixed instanceof Number;
            case "BOOLEAN" -> fixed instanceof Boolean;
            case "MULTI_SELECT" -> fixed instanceof List<?>;
            case "DATE" -> fixed instanceof String text && text.matches("\\d{4}-\\d{2}-\\d{2}");
            case "DATETIME" -> fixed instanceof String text && text.matches("\\d{4}-\\d{2}-\\d{2}T.+");
            default -> fixed instanceof String;
        };
        if (!valid) throw new IllegalArgumentException(label + "的固定值类型与目标字段不一致");
    }

    private void validateAutomaticTarget(FlowNode node, FlowAutomaticActionPolicy.Config policy, Long moduleId,
                                         Long startModuleId,
                                         Map<String, FlowDesignModels.NodeFormField> sourceFields,
                                         Map<String, FlowNode> nodes, List<FlowEdge> edges) {
        if (!"UPDATE".equals(policy.operation())) return;
        if ("CURRENT_RECORD".equals(policy.targetRecordSource())) {
            if (!Objects.equals(moduleId, startModuleId)) {
                throw new IllegalArgumentException("当前业务记录只能更新流程开始节点选择的模块");
            }
            return;
        }
        if ("BUSINESS_FIELD".equals(policy.targetRecordSource())) {
            FlowDesignModels.NodeFormField field = sourceFields.get(policy.recordIdField());
            if (field == null || !Objects.equals(field.referenceModuleId(), moduleId)) {
                throw new IllegalArgumentException("请选择引用目标模块记录的业务字段");
            }
            return;
        }
        FlowNode sourceNode = nodes.get(policy.recordNodeKey());
        if (sourceNode == null || sourceNode == node || !FlowAutomaticActionPolicy.DATA_ACTION.equals(sourceNode.getNodeType())) {
            throw new IllegalArgumentException("请选择有效的前序数据节点结果");
        }
        if (!reachable(sourceNode.getNodeKey(), node.getNodeKey(), edges)) {
            throw new IllegalArgumentException("所选数据节点不在当前节点之前的执行路径上");
        }
        FlowAutomaticActionPolicy.Config source = FlowAutomaticActionPolicy.parse(
                sourceNode.getNodeType(), fromJson(sourceNode.getConfigJson()));
        if (!Objects.equals(source.moduleId(), moduleId)) {
            throw new IllegalArgumentException("前序数据节点的目标模块与当前节点不一致");
        }
    }

    /** 节点结果只能引用图上真实可先到达的节点，不能借节点列表顺序引用后续或无关分支。 */
    private boolean reachable(String source, String target, List<FlowEdge> edges) {
        Map<String, List<String>> outgoing = new LinkedHashMap<>();
        for (FlowEdge edge : edges) {
            outgoing.computeIfAbsent(edge.getSourceNodeKey(), ignored -> new ArrayList<>())
                    .add(edge.getTargetNodeKey());
        }
        Set<String> visited = new HashSet<>();
        java.util.ArrayDeque<String> pending = new java.util.ArrayDeque<>();
        pending.add(source);
        while (!pending.isEmpty()) {
            String current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (Objects.equals(current, target)) return true;
            pending.addAll(outgoing.getOrDefault(current, List.of()));
        }
        return false;
    }

    private Long startModuleId(List<FlowNode> nodes) {
        FlowNode start = nodes.stream().filter(node -> "START".equals(node.getNodeType())).findFirst().orElse(null);
        return start == null ? null : longValue(fromJson(start.getConfigJson()).get("moduleId"));
    }

    /** 发布前检查人工节点时限和同域转交目标；无期限的旧动作保留但不启用。 */
    /** 子流程只固定当前作用域内已发布的目标版本，并在发布时阻止直接或间接递归调用。 */
    private void validateSubflow(AuthenticatedContext context, FlowDefinition flow, FlowNode node,
                                 Map<String, Object> config, List<FlowDesignModels.Issue> issues) {
        if (!"SUBFLOW".equals(node.getNodeType())) return;
        FlowSubflowPolicy.Config policy;
        try {
            policy = FlowSubflowPolicy.parse(config);
        } catch (IllegalArgumentException invalid) {
            issues.add(issue("SUBFLOW_CONFIG_INVALID", node.getNodeKey(), invalid.getMessage()));
            return;
        }
        if (policy.flowId() == null) {
            issues.add(issue("SUBFLOW_TARGET_REQUIRED", node.getNodeKey(), "子流程节点必须选择已发布的目标流程"));
            return;
        }
        if (Objects.equals(policy.flowId(), flow.getId())) {
            issues.add(issue("SUBFLOW_SELF_REFERENCE", node.getNodeKey(), "子流程不能引用自身"));
            return;
        }
        FlowDefinition referenced = definitionService.selectById(policy.flowId());
        if (referenced == null || !owned(context, referenced)) {
            issues.add(issue("FLOW_REFERENCE_FORBIDDEN", node.getNodeKey(), "引用的流程不在当前平台、系统和租户范围"));
            return;
        }
        FlowPublication publication = publication(policy.flowId());
        FlowVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getFlowId(), policy.flowId())
                || version.getDefinitionHash() == null || version.getSnapshotJson() == null) {
            issues.add(issue("SUBFLOW_VERSION_REQUIRED", node.getNodeKey(), "目标子流程必须先发布可运行版本"));
            return;
        }
        if (unsafePublishedSubflow(policy.flowId(), flow.getId(), new LinkedHashSet<>(), new HashSet<>())) {
            issues.add(issue("SUBFLOW_CYCLE", node.getNodeKey(), "子流程调用不能形成直接或间接循环"));
        }
    }

    /** 旧发布版本如果已经包含不完整循环，也拒绝继续把它接入新的父流程。 */
    private boolean unsafePublishedSubflow(Long currentFlowId, Long requestedParentFlowId,
                                           Set<Long> visiting, Set<Long> visited) {
        if (Objects.equals(currentFlowId, requestedParentFlowId)) return true;
        if (!visiting.add(currentFlowId)) return true;
        if (!visited.add(currentFlowId)) {
            visiting.remove(currentFlowId);
            return false;
        }
        FlowPublication publication = publication(currentFlowId);
        FlowVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        if (version == null || !Objects.equals(version.getFlowId(), currentFlowId)) return true;
        for (Map<String, Object> childNode : snapshotNodes(fromJson(version.getSnapshotJson()))) {
            if (!"SUBFLOW".equals(childNode.get("nodeType"))) continue;
            Long childFlowId = longValue(mapValue(childNode.get("config")).get(FlowSubflowPolicy.FLOW_ID));
            if (childFlowId == null || unsafePublishedSubflow(childFlowId, requestedParentFlowId, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(currentFlowId);
        return false;
    }

    private List<Map<String, Object>> snapshotNodes(Map<String, Object> snapshot) {
        Object value = snapshot.get("nodes");
        if (!(value instanceof List<?>)) return List.of();
        return objectMapper.convertValue(value, new TypeReference<>() { });
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?>)) return Map.of();
        return objectMapper.convertValue(value, new TypeReference<>() { });
    }

    private void validateTimeout(AuthenticatedContext context, FlowDefinition flow, FlowNode node,
                                 List<FlowDesignModels.Issue> issues) {
        Map<String, Object> timeout = fromJson(node.getTimeoutPolicyJson());
        Object duration = timeout.containsKey("timeoutMinutes") ? timeout.get("timeoutMinutes") : timeout.get("minutes");
        // 旧 minutes 只在规范字段不存在时兼容，冲突配置不能因解析顺序而启用另一期限。
        if (timeout.containsKey("timeoutMinutes") && timeout.get("minutes") != null
                && !Objects.equals(positiveInteger(timeout.get("timeoutMinutes")), positiveInteger(timeout.get("minutes")))) {
            issues.add(issue("FLOW_TIMEOUT_DURATION_CONFLICT", node.getNodeKey(),
                    "新旧处理时限不一致，请重新设置处理时限，移除旧期限后再发布"));
            return;
        }
        if (duration == null || duration instanceof String text && text.isBlank()) return;
        Long minutes = positiveInteger(duration);
        if (minutes == null || minutes > 525600) {
            issues.add(issue("FLOW_TIMEOUT_DURATION_INVALID", node.getNodeKey(),
                    "处理时限必须是 1 至 525600 的整数分钟；不启用时请留空"));
            return;
        }
        if (!Set.of("APPROVAL", "FORM_TASK").contains(node.getNodeType())) {
            issues.add(issue("FLOW_TIMEOUT_NODE_INVALID", node.getNodeKey(), "当前处理时限仅适用于审批或填写节点"));
            return;
        }
        Object configuredAction = timeout.get("action");
        String action = configuredAction == null ? "INCIDENT" : String.valueOf(configuredAction).strip().toUpperCase(Locale.ROOT);
        if (action.isBlank()) action = "INCIDENT";
        if (!TIMEOUT_ACTIONS.contains(action)) {
            issues.add(issue("FLOW_TIMEOUT_ACTION_INVALID", node.getNodeKey(), "请选择受支持的超时处理方式"));
            return;
        }
        // 自动同意不能代替填写人提交输入，即使暂存候选已具备部分必填值。
        if ("FORM_TASK".equals(node.getNodeType()) && "AUTO_APPROVE".equals(action)) {
            issues.add(issue("FORM_TASK_TIMEOUT_APPROVE_NOT_AVAILABLE", node.getNodeKey(),
                    "填写表单节点不能超时自动完成，请使用提醒、拒绝或转交处理"));
        }
        if ("ESCALATE_MANAGER".equals(action) && context.systemId() == null) {
            issues.add(issue("FLOW_TIMEOUT_ACTION_NOT_AVAILABLE", node.getNodeKey(),
                    "平台审批的直属上级来源尚未接通，请选择指定平台人员转交"));
        }
        if (!"TRANSFER".equals(action)) return;
        String targetKey = context.systemId() == null ? "targetPlatformAccountId" : "targetTenantMemberId";
        String otherKey = context.systemId() == null ? "targetTenantMemberId" : "targetPlatformAccountId";
        Long target = positiveInteger(timeout.get(targetKey));
        if (target == null || timeout.containsKey(otherKey)
                || Set.of("targetTenantMemberIds", "targetPlatformAccountIds", "targetIds").stream().anyMatch(timeout::containsKey)) {
            issues.add(issue("FLOW_TIMEOUT_TARGET_INVALID", node.getNodeKey(), "超时转交必须选择当前范围的一名有效处理人，不能使用另一范围身份或多人目标"));
            return;
        }
        try {
            if (context.systemId() == null) participantResolver.requirePlatformPerson(context, target);
            else {
                // requireOwned 已把发布者范围限定为定义来源租户，不能借用其他工作空间人员。
                if (!Objects.equals(flow.getOwnerTenantId(), context.tenantId())) {
                    issues.add(issue("FLOW_TIMEOUT_TARGET_INVALID", node.getNodeKey(), "超时处理人必须来自流程定义所属工作空间"));
                    return;
                }
                participantResolver.requirePerson(context, target);
            }
        } catch (DomainException invalidTarget) {
            issues.add(issue("FLOW_TIMEOUT_TARGET_INVALID", node.getNodeKey(), invalidTarget.getMessage()));
        }
    }

    /** 只接受数值型正整数，拒绝小数截断、字符串、布尔及多值身份。 */
    private Long positiveInteger(Object value) {
        if (!(value instanceof Number number)) return null;
        try {
            long integer = new BigDecimal(number.toString()).longValueExact();
            return integer > 0 ? integer : null;
        } catch (ArithmeticException | NumberFormatException invalidNumber) {
            return null;
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
                version.getPublishTime(), publicVersionSnapshot(fromJson(version.getSnapshotJson())),
                read(version.getSimulationResultJson(), FlowDesignModels.SimulationResult.class));
    }

    /**
     * 设计页可查看已发布版本，但受控回调的真实地址、密钥引用和内部校验摘要只供运行端使用。
     * 数据库仍保存完整冻结快照，避免后续应用改版改变在途流程行为。
     */
    private Map<String, Object> publicVersionSnapshot(Map<String, Object> snapshot) {
        Map<String, Object> result = objectMapper.convertValue(snapshot, new TypeReference<>() { });
        Object value = result.get("nodes");
        if (!(value instanceof List<?> nodes)) return result;
        List<Object> safeNodes = new ArrayList<>();
        for (Object item : nodes) {
            Map<String, Object> node = new LinkedHashMap<>(mapValue(item));
            if (FlowExternalCallbackPolicy.supports(String.valueOf(node.get("nodeType")))) {
                Map<String, Object> config = new LinkedHashMap<>(mapValue(node.get("config")));
                config.remove("url");
                config.remove("signingSecretRef");
                config.remove("applicationSnapshotHash");
                config.put("managedCallback", true);
                node.put("config", config);
            }
            safeNodes.add(node);
        }
        result.put("nodes", safeNodes);
        return result;
    }

    private Map<String, Object> snapshot(AuthenticatedContext context, FlowDefinition flow, List<FlowNode> nodes, List<FlowEdge> edges) {
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
        Long startModuleId = startModuleId(nodes);
        snapshot.put("nodes", nodes.stream().map(node -> frozenNodeView(context, node, startModuleId)).toList());
        snapshot.put("edges", edges.stream().map(this::view).toList());
        // 分支使用发布当时的字段类型和安全选项；后续修改模块或流程不能改变在途实例。
        snapshot.put("conditionContext", flowConditions.snapshotContext(context, nodes, edges));
        // 节点可见/编辑策略使用独立的字段身份快照，不借分支的部分字段目录补全。
        snapshot.put("formContext", nodeForms.snapshot(context, nodes));
        return snapshot;
    }

    /** 子流程目标的发布版本在父流程发布时冻结，之后切换子流程当前版本不会影响旧实例。 */
    private FlowDesignModels.NodeView frozenNodeView(AuthenticatedContext context, FlowNode node, Long startModuleId) {
        FlowDesignModels.NodeView base = view(node);
        if ("AI".equals(node.getNodeType())) {
            AiBinding binding = requireAiBinding(context, base.config());
            Map<String, Object> frozen = FlowAiNodePolicy.frozen(binding.config(), binding.model().getId(),
                    binding.model().getVersion(), binding.grantVersion().getId());
            return new FlowDesignModels.NodeView(base.id(), base.nodeKey(), base.nodeType(), base.name(),
                    base.positionX(), base.positionY(), base.assigneePolicy(), base.formPolicy(),
                    base.timeoutPolicy(), base.exceptionPolicy(), frozen, base.version());
        }
        if (Set.of(FlowAutomaticActionPolicy.UPDATE_FIELD, FlowAutomaticActionPolicy.DATA_ACTION)
                .contains(node.getNodeType())) {
            FlowAutomaticActionPolicy.Config policy = FlowAutomaticActionPolicy.parse(node.getNodeType(), base.config());
            Long moduleId = policy.moduleId() == null && FlowAutomaticActionPolicy.UPDATE_FIELD.equals(node.getNodeType())
                    ? startModuleId : policy.moduleId();
            ConfiguredModule module = moduleService.selectById(moduleId);
            var publication = flowConditions.businessPublication(context, moduleId);
            Map<String, FlowDesignModels.NodeFormField> fields = nodeForms.fields(context, moduleId).stream()
                    .collect(java.util.stream.Collectors.toMap(FlowDesignModels.NodeFormField::code, field -> field,
                            (left, right) -> left, LinkedHashMap::new));
            List<FlowAutomaticActionPolicy.FieldBinding> bindings = policy.assignments().stream().map(assignment -> {
                FlowDesignModels.NodeFormField field = fields.get(assignment.fieldCode());
                if (field == null) throw conflict("FLOW_AUTOMATIC_FIELD_CHANGED", "自动业务节点字段已变化，请重新检查后发布");
                return new FlowAutomaticActionPolicy.FieldBinding(field.id(), field.code(), field.name(),
                        field.fieldType(), field.dictionaryId(), field.referenceModuleId());
            }).toList();
            Map<String, Object> frozen = FlowAutomaticActionPolicy.frozen(policy, moduleId, module.getCode(),
                    module.getName(), publication.versionId(), bindings);
            return new FlowDesignModels.NodeView(base.id(), base.nodeKey(), base.nodeType(), base.name(),
                    base.positionX(), base.positionY(), base.assigneePolicy(), base.formPolicy(),
                    base.timeoutPolicy(), base.exceptionPolicy(), frozen, base.version());
        }
        if (FlowMessageNodePolicy.supports(node.getNodeType())) {
            FlowMessageNodePolicy.Config policy = FlowMessageNodePolicy.parse(node.getNodeType(), base.config());
            return new FlowDesignModels.NodeView(base.id(), base.nodeKey(), base.nodeType(), base.name(),
                    base.positionX(), base.positionY(), base.assigneePolicy(), base.formPolicy(),
                    base.timeoutPolicy(), base.exceptionPolicy(), FlowMessageNodePolicy.frozen(policy), base.version());
        }
        if (FlowWaitTimerPolicy.supports(node.getNodeType())) {
            FlowWaitTimerPolicy.Config policy = FlowWaitTimerPolicy.parse(node.getNodeType(), base.config());
            return new FlowDesignModels.NodeView(base.id(), base.nodeKey(), base.nodeType(), base.name(),
                    base.positionX(), base.positionY(), base.assigneePolicy(), base.formPolicy(),
                    base.timeoutPolicy(), base.exceptionPolicy(), FlowWaitTimerPolicy.frozen(policy), base.version());
        }
        if ("SUBFLOW".equals(node.getNodeType())) {
            FlowSubflowPolicy.Config policy = FlowSubflowPolicy.parse(base.config());
            FlowDefinition child = definitionService.selectById(policy.flowId());
            FlowPublication publication = publication(policy.flowId());
            FlowVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
            if (child == null || version == null || !Objects.equals(version.getFlowId(), policy.flowId())) {
                throw conflict("SUBFLOW_VERSION_CHANGED", "目标子流程的发布版本已变化，请重新检查后发布");
            }
            Map<String, Object> frozen = FlowSubflowPolicy.frozen(policy, version.getId(),
                    version.getDefinitionHash(), child.getName(), version.getVersionNumber());
            return new FlowDesignModels.NodeView(base.id(), base.nodeKey(), base.nodeType(), base.name(),
                    base.positionX(), base.positionY(), base.assigneePolicy(), base.formPolicy(),
                    base.timeoutPolicy(), base.exceptionPolicy(), frozen, base.version());
        }
        if (FlowExternalCallbackPolicy.supports(node.getNodeType())) {
            FlowExternalCallbackPolicy.Selection selection = FlowExternalCallbackPolicy.parseSelection(base.config());
            Map<String, Object> frozen = FlowExternalCallbackPolicy.frozen(applicationCallbacks.requirePublished(
                    context, selection.applicationId(), selection.callbackId()));
            return new FlowDesignModels.NodeView(base.id(), base.nodeKey(), base.nodeType(), base.name(),
                    base.positionX(), base.positionY(), base.assigneePolicy(), base.formPolicy(),
                    base.timeoutPolicy(), base.exceptionPolicy(), frozen, base.version());
        }
        return base;
    }

    /** 发布前按当前系统授权验证模型，平台 Flow 不使用系统额度。 */
    private AiBinding requireAiBinding(AuthenticatedContext context, Map<String, Object> config) {
        if (context.systemId() == null) {
            throw invalid("FLOW_AI_PLATFORM_UNSUPPORTED", "平台 Flow AI 节点暂不支持发布");
        }
        FlowAiNodePolicy.Config policy = FlowAiNodePolicy.parse(config);
        AiSystemModelGrant grant = aiGrants.selectById(policy.modelGrantId());
        AiModel model = grant == null ? null : aiModels.selectById(grant.getModelId());
        if (grant == null || model == null || !"ACTIVE".equals(grant.getStatus())
                || !"ACTIVE".equals(model.getStatus())
                || !Objects.equals(grant.getSystemId(), context.systemId())
                || !Objects.equals(grant.getPlatformId(), context.platformId())
                || !Objects.equals(model.getPlatformId(), context.platformId())
                || "SYSTEM".equals(model.getScopeType())
                && !Objects.equals(model.getSystemId(), context.systemId())
                || !Objects.equals(model.getScopeType(), model.getCredentialScopeType())) {
            throw invalid("FLOW_AI_MODEL_NOT_GRANTED", "AI 节点模型授权已失效或不属于当前系统");
        }
        AiSystemModelGrantVersion version = aiGrantVersions.selectList(
                Wrappers.<AiSystemModelGrantVersion>lambdaQuery()
                        .eq(AiSystemModelGrantVersion::getGrantId, grant.getId())
                        .orderByDesc(AiSystemModelGrantVersion::getVersionNumber).last("limit 1"))
                .stream().findFirst().orElse(null);
        if (version == null) throw invalid("FLOW_AI_GRANT_VERSION_MISSING", "模型授权缺少当前发布版本");
        return new AiBinding(policy, model, version);
    }

    private record AiBinding(FlowAiNodePolicy.Config config, AiModel model,
                             AiSystemModelGrantVersion grantVersion) { }

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
        if (!normalized.matches("[a-z][a-z0-9_]{1,99}")) {
            throw invalid("FLOW_CODE_INVALID", "Flow 编码需以字母开头，只能包含小写字母、数字和下划线");
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
