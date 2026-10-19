package com.unique.unexamine.manage.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import com.unique.unexamine.manage.common.SessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import static com.unique.unexamine.manage.flow.FlowOperations.*;
import com.unique.unexamine.manage.flow.FlowOperations.DraftSnapshot;

/** 流程定义、草稿发布及模块自动触发绑定入口。 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/flows")
public class FlowDefinitionController {
    private final SessionAccess access;
    private final UnFlowMapper flows;
    private final UnFlowNodeMapper nodes;
    private final UnFlowEdgeMapper edges;
    private final UnFlowVersionMapper versions;
    private final UnFlowBindingMapper bindings;
    private final FlowOperations ops;

    public FlowDefinitionController(SessionAccess access, UnFlowMapper flows,
                                    UnFlowNodeMapper nodes, UnFlowEdgeMapper edges,
                                    UnFlowVersionMapper versions, UnFlowBindingMapper bindings,
                                    FlowOperations ops) {
        this.access = access;
        this.flows = flows;
        this.nodes = nodes;
        this.edges = edges;
        this.versions = versions;
        this.bindings = bindings;
        this.ops = ops;
    }

    /** 列出当前租户的流程，不返回其他租户的定义。 */
    @GetMapping
    public Page<UnFlow> list(@PathVariable long tenantId, @RequestParam(defaultValue = "1") long page,
                             @RequestParam(defaultValue = "20") long size,
                             @RequestParam(defaultValue = "createTime") String sortField,
                             @RequestParam(defaultValue = "desc") String direction, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        if (page < 1 || size < 1 || size > 100) bad("分页范围无效");
        if (!Set.of("asc", "desc").contains(direction)) bad("排序方向无效");
        boolean asc = "asc".equals(direction);
        LambdaQueryWrapper<UnFlow> query = new LambdaQueryWrapper<UnFlow>().eq(UnFlow::getTenantId, tenantId);
        switch (sortField) {
            case "name" -> query.orderBy(true, asc, UnFlow::getName);
            case "createTime" -> query.orderBy(true, asc, UnFlow::getCreateTime);
            default -> bad("排序字段无效");
        }
        query.orderByAsc(UnFlow::getId);
        return flows.selectPage(new Page<>(page, size), query);
    }

    /** 创建流程定义，节点及连线随后在草稿接口保存。 */
    @PostMapping
    public UnFlow create(@PathVariable long tenantId, @Valid @RequestBody FlowInput body,
                         HttpServletRequest request) {
        long memberId = access.tenantAdmin(request, tenantId).getMemberId();
        UnFlow flow = new UnFlow();
        flow.setTenantId(tenantId);
        flow.setName(body.name().trim());
        flow.setDescription(body.description());
        flow.setStatus("DRAFT");
        flow.setCreateMemberId(memberId);
        flows.insert(flow);
        return flow;
    }

    /** 读取定义、草稿和已发布版本，便于编辑器呈现。 */
    @GetMapping("/{flowId}")
    public FlowDetail detail(@PathVariable long tenantId, @PathVariable long flowId, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        UnFlow flow = ops.flowInTenant(tenantId, flowId);
        return new FlowDetail(flow, ops.draftNodes(flowId), ops.draftEdges(flowId),
                versions.selectList(new LambdaQueryWrapper<UnFlowVersion>().eq(UnFlowVersion::getFlowId, flowId)
                        .orderByDesc(UnFlowVersion::getVersionNo)));
    }

    /** 整体替换草稿；实例仅引用发布快照，不会受此操作影响。 */
    @PutMapping("/{flowId}/draft")
    @Transactional
    public FlowDetail saveDraft(@PathVariable long tenantId, @PathVariable long flowId,
                                @Valid @RequestBody DraftInput body, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlow flow = ops.lockFlow(tenantId, flowId);
        if (body.nodes() == null || body.edges() == null) bad("节点和连线不能为空");
        Set<String> keys = new HashSet<>();
        for (NodeInput node : body.nodes()) {
            if (node.nodeKey() == null || node.nodeKey().isBlank() || !keys.add(node.nodeKey().trim())) bad("节点标识缺失或重复");
            if (node.nodeType() == null || !Set.of("START", "TASK", "END").contains(node.nodeType()))
                bad("当前仅支持开始、人工任务和结束节点");
            if (node.name() == null || node.name().isBlank()) bad("节点名称不能为空");
        }
        Set<List<String>> edgePairs = new HashSet<>();
        for (EdgeInput edge : body.edges()) {
            if (!keys.contains(edge.sourceNodeKey()) || !keys.contains(edge.targetNodeKey())) bad("连线节点不存在");
            if (!edgePairs.add(List.of(edge.sourceNodeKey(), edge.targetNodeKey()))) bad("连线重复");
        }
        edges.delete(new LambdaQueryWrapper<UnFlowEdge>().eq(UnFlowEdge::getFlowId, flowId));
        nodes.delete(new LambdaQueryWrapper<UnFlowNode>().eq(UnFlowNode::getFlowId, flowId));
        for (NodeInput input : body.nodes()) {
            UnFlowNode node = new UnFlowNode();
            node.setFlowId(flowId);
            node.setNodeKey(input.nodeKey().trim());
            node.setName(input.name().trim());
            node.setNodeType(input.nodeType());
            node.setAssigneeType(input.assigneeType());
            node.setAssigneeId(input.assigneeId());
            node.setSortNum(input.sortNum() == null ? 0 : input.sortNum());
            nodes.insert(node);
        }
        for (EdgeInput input : body.edges()) {
            UnFlowEdge edge = new UnFlowEdge();
            edge.setFlowId(flowId);
            edge.setSourceNodeKey(input.sourceNodeKey());
            edge.setTargetNodeKey(input.targetNodeKey());
            edge.setConditionRule(input.conditionRule());
            edge.setSortNum(input.sortNum() == null ? 0 : input.sortNum());
            edges.insert(edge);
        }
        if (body.name() != null && body.name().trim().length() > 100) bad("流程名称过长");
        if (body.name() != null && !body.name().isBlank()) flow.setName(body.name().trim());
        flow.setDescription(body.description());
        flows.updateById(flow);
        return detail(tenantId, flowId, request);
    }

    /** 校验可到达的有向无环流程后发布新版本；已有版本不执行更新。 */
    @PostMapping("/{flowId}/publish")
    @Transactional
    public UnFlowVersion publish(@PathVariable long tenantId, @PathVariable long flowId, HttpServletRequest request) {
        long memberId = access.tenantAdmin(request, tenantId).getMemberId();
        UnFlow flow = ops.lockFlow(tenantId, flowId);
        List<UnFlowNode> nodeList = ops.draftNodes(flowId);
        List<UnFlowEdge> edgeList = ops.draftEdges(flowId);
        ops.validateGraph(nodeList, edgeList, tenantId);
        int next = versions.selectList(new LambdaQueryWrapper<UnFlowVersion>().eq(UnFlowVersion::getFlowId, flowId))
                .stream().mapToInt(UnFlowVersion::getVersionNo).max().orElse(0) + 1;
        UnFlowVersion version = new UnFlowVersion();
        version.setFlowId(flowId);
        version.setVersionNo(next);
        version.setDefinitionSnapshot(ops.writeJson(new DraftSnapshot(nodeList, edgeList)));
        version.setPublishMemberId(memberId);
        versions.insert(version);
        flow.setCurrentVersionId(version.getId());
        flow.setStatus("ENABLED");
        flows.updateById(flow);
        return version;
    }

    /** 停用流程阻止新实例发起，既有实例继续按已冻结的版本执行。 */
    @PostMapping("/{flowId}/disable")
    @Transactional
    public UnFlow disable(@PathVariable long tenantId, @PathVariable long flowId, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlow flow = ops.lockFlow(tenantId, flowId);
        flow.setStatus("DISABLED");
        flows.updateById(flow);
        return flow;
    }

    /** 自动触发绑定固定发布版本；仅模块记录成功创建或更新后由模块域调用。 */
    @GetMapping("/bindings")
    public List<UnFlowBinding> bindingList(@PathVariable long tenantId,
                                           @RequestParam(required = false) Long moduleId, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        LambdaQueryWrapper<UnFlowBinding> query = new LambdaQueryWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getTenantId, tenantId).orderByAsc(UnFlowBinding::getId);
        if (moduleId != null) query.eq(UnFlowBinding::getModuleId, moduleId);
        return bindings.selectList(query);
    }

    @PostMapping("/bindings")
    @Transactional
    public UnFlowBinding bind(@PathVariable long tenantId, @Valid @RequestBody BindingInput body,
                              HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        if (!Set.of("CREATE", "UPDATE").contains(body.triggerEvent())) bad("自动触发事件只支持 CREATE 或 UPDATE");
        ops.requireModule(tenantId, body.moduleId());
        UnFlow flow = ops.flowInTenant(tenantId, body.flowId());
        if (!"ENABLED".equals(flow.getStatus()) || flow.getCurrentVersionId() == null) conflict("流程尚未发布或已停用");
        if (bindings.selectCount(new LambdaQueryWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getTenantId, tenantId).eq(UnFlowBinding::getModuleId, body.moduleId())
                .eq(UnFlowBinding::getFlowId, body.flowId()).eq(UnFlowBinding::getTriggerEvent, body.triggerEvent())) > 0)
            conflict("相同模块、流程和触发事件的绑定已存在");
        UnFlowBinding binding = new UnFlowBinding();
        binding.setTenantId(tenantId);
        binding.setModuleId(body.moduleId());
        binding.setFlowId(body.flowId());
        binding.setVersionId(flow.getCurrentVersionId());
        binding.setTriggerEvent(body.triggerEvent());
        binding.setStatus("ENABLED");
        bindings.insert(binding);
        return bindings.selectById(binding.getId());
    }

    @PutMapping("/bindings/{bindingId}/status")
    @Transactional
    public UnFlowBinding bindingStatus(@PathVariable long tenantId, @PathVariable long bindingId,
                                       @Valid @RequestBody BindingStatusInput body, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlowBinding binding = ops.lockBinding(tenantId, bindingId);
        if (body.enabled()) {
            ops.requireModule(tenantId, binding.getModuleId());
            UnFlow flow = ops.flowInTenant(tenantId, binding.getFlowId());
            if (!"ENABLED".equals(flow.getStatus())) conflict("流程已经停用");
        }
        binding.setStatus(body.enabled() ? "ENABLED" : "DISABLED");
        bindings.updateById(binding);
        return bindings.selectById(bindingId);
    }

    /** 显式换用最新发布版本；已有自动实例仍保留旧版本。 */
    @PostMapping("/bindings/{bindingId}/refresh-version")
    @Transactional
    public UnFlowBinding refreshBindingVersion(@PathVariable long tenantId, @PathVariable long bindingId,
                                               HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlowBinding binding = ops.lockBinding(tenantId, bindingId);
        UnFlow flow = ops.flowInTenant(tenantId, binding.getFlowId());
        if (!"ENABLED".equals(flow.getStatus()) || flow.getCurrentVersionId() == null) conflict("流程尚未发布或已停用");
        binding.setVersionId(flow.getCurrentVersionId());
        bindings.updateById(binding);
        return bindings.selectById(bindingId);
    }

    /** 编辑输入不允许客户端指定租户或发布版本。 */
    public record FlowInput(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
    public record NodeInput(String nodeKey, String name, String nodeType, String assigneeType, Long assigneeId, Integer sortNum) {}
    public record EdgeInput(String sourceNodeKey, String targetNodeKey, String conditionRule, Integer sortNum) {}
    public record DraftInput(String name, String description, List<NodeInput> nodes, List<EdgeInput> edges) {}
    public record BindingInput(@NotNull Long moduleId, @NotNull Long flowId, @NotBlank String triggerEvent) {}
    public record BindingStatusInput(@NotNull Boolean enabled) {}
    public record FlowDetail(UnFlow flow, List<UnFlowNode> nodes, List<UnFlowEdge> edges, List<UnFlowVersion> versions) {}
}
