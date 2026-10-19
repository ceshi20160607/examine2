package com.unique.unexamine.manage.flow;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.base.entity.*;
import com.unique.unexamine.base.mapper.*;
import com.unique.unexamine.manage.common.SessionAccess;
import com.unique.unexamine.manage.module.ModuleRecordAccess;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;

/** 租户流程草稿、发布和人工任务入口；发布后的版本及事件只追加。 */
@RestController
@EnableScheduling
@RequestMapping("/api/tenants/{tenantId}/flows")
public class FlowController {
    private static final Logger log = LoggerFactory.getLogger(FlowController.class);
    private final SessionAccess access;
    private final UnFlowMapper flows;
    private final UnFlowNodeMapper nodes;
    private final UnFlowEdgeMapper edges;
    private final UnFlowVersionMapper versions;
    private final UnFlowExampleMapper examples;
    private final UnFlowTaskMapper tasks;
    private final UnFlowTaskCandidateMapper candidates;
    private final UnFlowActionMapper actions;
    private final UnFlowEventMapper events;
    private final UnFlowBindingMapper bindings;
    private final UnFlowJobMapper jobs;
    private final UnFlowExceptionMapper exceptions;
    private final UnModuleRecordMapper records;
    private final UnSysTenantMemberMapper tenantMembers;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ModuleRecordAccess recordAccess;
    private final TransactionTemplate transactions;

    public FlowController(SessionAccess access, UnFlowMapper flows, UnFlowNodeMapper nodes,
                          UnFlowEdgeMapper edges, UnFlowVersionMapper versions,
                          UnFlowExampleMapper examples, UnFlowTaskMapper tasks,
                          UnFlowTaskCandidateMapper candidates, UnFlowActionMapper actions,
                          UnFlowEventMapper events, UnFlowBindingMapper bindings,
                          UnFlowJobMapper jobs, UnFlowExceptionMapper exceptions,
                          UnModuleRecordMapper records,
                          UnSysTenantMemberMapper tenantMembers, JdbcTemplate jdbc, ObjectMapper json,
                          ModuleRecordAccess recordAccess, PlatformTransactionManager transactionManager) {
        this.access = access;
        this.flows = flows;
        this.nodes = nodes;
        this.edges = edges;
        this.versions = versions;
        this.examples = examples;
        this.tasks = tasks;
        this.candidates = candidates;
        this.actions = actions;
        this.events = events;
        this.bindings = bindings;
        this.jobs = jobs;
        this.exceptions = exceptions;
        this.records = records;
        this.tenantMembers = tenantMembers;
        this.jdbc = jdbc;
        this.json = json;
        this.recordAccess = recordAccess;
        this.transactions = new TransactionTemplate(transactionManager);
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
        UnFlow flow = flowInTenant(tenantId, flowId);
        return new FlowDetail(flow, draftNodes(flowId), draftEdges(flowId),
                versions.selectList(new LambdaQueryWrapper<UnFlowVersion>().eq(UnFlowVersion::getFlowId, flowId)
                        .orderByDesc(UnFlowVersion::getVersionNo)));
    }

    /** 整体替换草稿；实例仅引用发布快照，不会受此操作影响。 */
    @PutMapping("/{flowId}/draft")
    @Transactional
    public FlowDetail saveDraft(@PathVariable long tenantId, @PathVariable long flowId,
                                @Valid @RequestBody DraftInput body, HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlow flow = lockFlow(tenantId, flowId);
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
        UnFlow flow = lockFlow(tenantId, flowId);
        List<UnFlowNode> nodeList = draftNodes(flowId);
        List<UnFlowEdge> edgeList = draftEdges(flowId);
        validateGraph(nodeList, edgeList, tenantId);
        int next = versions.selectList(new LambdaQueryWrapper<UnFlowVersion>().eq(UnFlowVersion::getFlowId, flowId))
                .stream().mapToInt(UnFlowVersion::getVersionNo).max().orElse(0) + 1;
        UnFlowVersion version = new UnFlowVersion();
        version.setFlowId(flowId);
        version.setVersionNo(next);
        version.setDefinitionSnapshot(writeJson(new DraftSnapshot(nodeList, edgeList)));
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
        UnFlow flow = lockFlow(tenantId, flowId);
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
        requireModule(tenantId, body.moduleId());
        UnFlow flow = flowInTenant(tenantId, body.flowId());
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
        UnFlowBinding binding = lockBinding(tenantId, bindingId);
        if (body.enabled()) {
            requireModule(tenantId, binding.getModuleId());
            UnFlow flow = flowInTenant(tenantId, binding.getFlowId());
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
        UnFlowBinding binding = lockBinding(tenantId, bindingId);
        UnFlow flow = flowInTenant(tenantId, binding.getFlowId());
        if (!"ENABLED".equals(flow.getStatus()) || flow.getCurrentVersionId() == null) conflict("流程尚未发布或已停用");
        binding.setVersionId(flow.getCurrentVersionId());
        bindings.updateById(binding);
        return bindings.selectById(bindingId);
    }

    /** 模块域在同一事务内调用；记录版本进入唯一请求号，重复调用不会产生第二个实例。 */
    @Transactional
    public List<UnFlowExample> triggerRecordEvent(long tenantId, long moduleId, long recordId,
                                                   int recordVersion, long actorMemberId, String eventType) {
        if (!Set.of("CREATE", "UPDATE").contains(eventType) || recordVersion < 1) bad("记录事件或版本无效");
        requireTenantMember(tenantId, actorMemberId);
        UnModuleRecord record = records.selectById(recordId);
        if (record == null || !flowIdEquals(record.getTenantId(), tenantId)
                || !flowIdEquals(record.getModuleId(), moduleId)) bad("自动触发的记录或模块不匹配");
        List<UnFlowExample> result = new ArrayList<>();
        for (UnFlowBinding found : bindings.selectList(new LambdaQueryWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getTenantId, tenantId).eq(UnFlowBinding::getModuleId, moduleId)
                .eq(UnFlowBinding::getTriggerEvent, eventType).eq(UnFlowBinding::getStatus, "ENABLED")
                .orderByAsc(UnFlowBinding::getId))) {
            UnFlowBinding binding = lockBinding(tenantId, found.getId());
            if (!"ENABLED".equals(binding.getStatus())) continue;
            UnFlow flow = flowInTenant(tenantId, binding.getFlowId());
            if (!"ENABLED".equals(flow.getStatus())) continue;
            String key = "AUTO:" + Long.toString(binding.getId(), 36) + ":" + Long.toString(recordId, 36)
                    + ":" + Integer.toString(recordVersion, 36);
            UnFlowAction previous = actions.selectOne(new LambdaQueryWrapper<UnFlowAction>()
                    .eq(UnFlowAction::getTenantId, tenantId).eq(UnFlowAction::getRequestKey, key));
            if (previous != null) {
                result.add(exampleInTenant(tenantId, previous.getExampleId()));
                continue;
            }
            if (record.getVersion() == null || record.getVersion() != recordVersion
                    || (record.getRecordStatus() != null && record.getRecordStatus() == 2))
                bad("自动触发的记录版本无效或记录已删除");
            UnFlowVersion version = versions.selectById(binding.getVersionId());
            if (version == null || !flowIdEquals(version.getFlowId(), flow.getId())) conflict("绑定发布版本不存在");
            DraftSnapshot snapshot = readJson(version.getDefinitionSnapshot());
            String startKey = snapshot.nodes().stream().filter(n -> "START".equals(n.getNodeType()))
                    .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "绑定版本缺少开始节点"))
                    .getNodeKey();
            UnFlowExample example = new UnFlowExample();
            example.setTenantId(tenantId);
            example.setFlowId(flow.getId());
            example.setVersionId(version.getId());
            example.setBindingId(binding.getId());
            example.setModuleId(moduleId);
            example.setRecordId(recordId);
            example.setStarterMemberId(actorMemberId);
            example.setDefinitionSnapshot(version.getDefinitionSnapshot());
            example.setStatus("RUNNING");
            example.setCurrentNodeKey(startKey);
            examples.insert(example);
            UnFlowAction action = new UnFlowAction();
            action.setTenantId(tenantId);
            action.setExampleId(example.getId());
            action.setActorMemberId(actorMemberId);
            action.setActionType("AUTO_START");
            action.setRequestKey(key);
            action.setResultStatus("SUCCESS");
            action.setRemark(eventType + " recordVersion=" + recordVersion);
            actions.insert(action);
            event(example.getId(), null, action.getId(), "AUTO_TRIGGERED", actorMemberId, eventType);
            try {
                String next = nextNode(snapshot, startKey, tenantId, moduleId, recordId);
                example.setCurrentNodeKey(next);
                examples.updateById(example);
                enterNode(tenantId, example, snapshot, next);
            } catch (ResponseStatusException ex) {
                LocalDateTime now = LocalDateTime.now();
                example.setStatus("FAILED");
                example.setCurrentNodeKey(null);
                example.setFinishTime(now);
                examples.updateById(example);
                action.setResultStatus("FAILED");
                actions.updateById(action);
                String detail = ex.getReason() == null ? "自动流程执行失败" : ex.getReason();
                recordException(example.getId(), null, detail.contains("处理人") ? "NO_ASSIGNEE" : "RULE_ERROR", detail);
                event(example.getId(), null, action.getId(), "FAILED", actorMemberId, detail);
            }
            result.add(examples.selectById(example.getId()));
        }
        return result;
    }

    /** 从发布版本启动实例；关联业务记录时同时校验租户、模块和发起人的修改权限。 */
    @PostMapping("/{flowId}/examples")
    @Transactional
    public UnFlowExample start(@PathVariable long tenantId, @PathVariable long flowId,
                               @RequestBody StartInput body, HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnFlow flow = flowInTenant(tenantId, flowId);
        if (!"ENABLED".equals(flow.getStatus()) || flow.getCurrentVersionId() == null) conflict("流程尚未发布或已停用");
        if ((body.moduleId() == null) != (body.recordId() == null)) bad("模块和记录应同时提供");
        if (body.recordId() != null) requireRecord(tenantId, body.moduleId(), body.recordId(), request);
        UnFlowVersion version = versions.selectById(flow.getCurrentVersionId());
        if (version == null || !flowIdEquals(version.getFlowId(), flowId)) conflict("流程发布版本不存在");
        DraftSnapshot snapshot = readJson(version.getDefinitionSnapshot());
        if (body.recordId() == null && snapshot.edges().stream().anyMatch(e -> hasRule(e.getConditionRule())))
            bad("条件流程发起时须关联模块记录");
        String first = nextNode(snapshot, snapshot.nodes().stream().filter(n -> "START".equals(n.getNodeType()))
                .findFirst().orElseThrow().getNodeKey(), tenantId, body.moduleId(), body.recordId());
        UnFlowExample example = new UnFlowExample();
        example.setTenantId(tenantId);
        example.setFlowId(flowId);
        example.setVersionId(version.getId());
        example.setModuleId(body.moduleId());
        example.setRecordId(body.recordId());
        example.setStarterMemberId(memberId);
        example.setDefinitionSnapshot(version.getDefinitionSnapshot());
        example.setStatus("RUNNING");
        example.setCurrentNodeKey(first);
        examples.insert(example);
        event(example.getId(), null, null, "STARTED", memberId, "流程已发起");
        enterNode(tenantId, example, snapshot, first);
        return examples.selectById(example.getId());
    }

    /** 待办从候选人和实例租户交叉核对，不接受客户端传入处理人编号。 */
    @GetMapping("/tasks")
    public List<UnFlowTask> inbox(@PathVariable long tenantId, HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        List<UnFlowTask> result = new ArrayList<>();
        for (UnFlowTaskCandidate candidate : candidates.selectList(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                .eq(UnFlowTaskCandidate::getMemberId, memberId).eq(UnFlowTaskCandidate::getStatus, "PENDING"))) {
            UnFlowTask task = tasks.selectById(candidate.getTaskId());
            UnFlowExample example = task == null ? null : examples.selectById(task.getExampleId());
            if (example != null && flowIdEquals(example.getTenantId(), tenantId) && "PENDING".equals(task.getStatus())) result.add(task);
        }
        return result;
    }

    /** 模块记录详情按读取权限查询相关流程实例，供页面定位自动触发结果。 */
    @GetMapping("/examples")
    public ExamplePage recordExamples(@PathVariable long tenantId, @RequestParam long moduleId,
                                      @RequestParam long recordId, @RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "20") long size, HttpServletRequest request) {
        access.tenantMember(request, tenantId);
        if (page < 1 || size < 1 || size > 100) bad("分页范围无效");
        UnModuleRecord record = records.selectById(recordId);
        if (record == null || !flowIdEquals(record.getTenantId(), tenantId)
                || !flowIdEquals(record.getModuleId(), moduleId)) notFound("业务记录不存在");
        recordAccess.requireRead(recordAccess.scope(request, tenantId), record);
        Page<UnFlowExample> found = examples.selectPage(new Page<>(page, size),
                new LambdaQueryWrapper<UnFlowExample>().eq(UnFlowExample::getTenantId, tenantId)
                        .eq(UnFlowExample::getModuleId, moduleId).eq(UnFlowExample::getRecordId, recordId)
                        .orderByDesc(UnFlowExample::getStartTime).orderByDesc(UnFlowExample::getId));
        return new ExamplePage(found.getRecords().stream().map(e -> new ExampleBrief(e.getId(), e.getFlowId(),
                e.getVersionId(), e.getBindingId(), e.getStatus(), e.getStartTime(), e.getFinishTime())).toList(),
                found.getTotal(), page, size);
    }

    /** 审批、拒绝和撤回在实例行锁下完成，重复请求返回原操作，其他冲突返回 409。 */
    @PostMapping("/examples/{exampleId}/actions")
    @Transactional
    public UnFlowAction act(@PathVariable long tenantId, @PathVariable long exampleId,
                            @Valid @RequestBody ActionInput body, HttpServletRequest request) {
        if (body.requestKey().startsWith("AUTO:")) bad("请求标识前缀保留给自动流程");
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnFlowExample example = lockExample(tenantId, exampleId);
        UnFlowAction previous = actions.selectOne(new LambdaQueryWrapper<UnFlowAction>()
                .eq(UnFlowAction::getTenantId, tenantId).eq(UnFlowAction::getRequestKey, body.requestKey()));
        if (previous != null) {
            if (flowIdEquals(previous.getExampleId(), exampleId) && flowIdEquals(previous.getActorMemberId(), memberId)
                    && previous.getActionType().equals(body.actionType())) return previous;
            conflict("请求标识已被其他操作使用");
        }
        if (!"RUNNING".equals(example.getStatus())) conflict("流程实例已结束");
        LocalDateTime now = LocalDateTime.now();
        UnFlowTask task = null;
        String eventType;
        if ("WITHDRAW".equals(body.actionType())) {
            if (!flowIdEquals(example.getStarterMemberId(), memberId)) forbidden("只有发起人可以撤回");
            for (UnFlowTask open : tasks.selectList(new LambdaQueryWrapper<UnFlowTask>()
                    .eq(UnFlowTask::getExampleId, exampleId).eq(UnFlowTask::getStatus, "PENDING"))) {
                open.setStatus("CANCELLED");
                open.setFinishTime(now);
                tasks.updateById(open);
                cancelCandidates(open.getId(), now);
                cancelJobs(open.getId(), now);
            }
            example.setStatus("WITHDRAWN");
            example.setCurrentNodeKey(null);
            example.setFinishTime(now);
            eventType = "WITHDRAWN";
        } else {
            if (!Set.of("APPROVE", "REJECT").contains(body.actionType()) || body.taskId() == null) bad("操作类型或任务主键无效");
            task = tasks.selectById(body.taskId());
            if (task == null || !flowIdEquals(task.getExampleId(), exampleId) || !"PENDING".equals(task.getStatus())) conflict("任务不存在或已经处理");
            UnFlowTaskCandidate candidate = candidates.selectOne(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                    .eq(UnFlowTaskCandidate::getTaskId, task.getId()).eq(UnFlowTaskCandidate::getMemberId, memberId));
            if (candidate == null || !"PENDING".equals(candidate.getStatus())) forbidden("不是此任务的待办处理人");
            candidate.setStatus("ACTED");
            candidate.setActionTime(now);
            candidates.updateById(candidate);
            cancelCandidates(task.getId(), now);
            task.setStatus("COMPLETED");
            task.setClaimMemberId(memberId);
            task.setClaimTime(now);
            task.setFinishTime(now);
            tasks.updateById(task);
            cancelJobs(task.getId(), now);
            if ("REJECT".equals(body.actionType())) {
                example.setStatus("REJECTED");
                example.setCurrentNodeKey(null);
                example.setFinishTime(now);
                eventType = "REJECTED";
            } else {
                DraftSnapshot snapshot = readJson(example.getDefinitionSnapshot());
                String next = nextNode(snapshot, task.getNodeKey(), tenantId, example.getModuleId(), example.getRecordId());
                example.setCurrentNodeKey(next);
                enterNode(tenantId, example, snapshot, next);
                eventType = "TASK_COMPLETED";
            }
        }
        examples.updateById(example);
        UnFlowAction action = new UnFlowAction();
        action.setTenantId(tenantId);
        action.setExampleId(exampleId);
        action.setTaskId(task == null ? null : task.getId());
        action.setActorMemberId(memberId);
        action.setActionType(body.actionType());
        action.setRequestKey(body.requestKey());
        action.setResultStatus("SUCCESS");
        action.setRemark(body.remark());
        actions.insert(action);
        event(exampleId, task == null ? null : task.getId(), action.getId(), eventType, memberId, body.remark());
        return action;
    }

    /** 管理员为当前待办安排一次超时检查，同一待办只能保留一项待执行任务。 */
    @PostMapping("/examples/{exampleId}/tasks/{taskId}/timeout")
    @Transactional
    public UnFlowJob scheduleTimeout(@PathVariable long tenantId, @PathVariable long exampleId,
                                     @PathVariable long taskId, @Valid @RequestBody TimeoutInput body,
                                     HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlowExample example = lockExample(tenantId, exampleId);
        UnFlowTask task = tasks.selectById(taskId);
        if (!"RUNNING".equals(example.getStatus()) || task == null
                || !flowIdEquals(task.getExampleId(), exampleId) || !"PENDING".equals(task.getStatus()))
            conflict("只能给当前运行的待办安排超时");
        LocalDateTime executeTime = body.executeTime().truncatedTo(ChronoUnit.MILLIS);
        if (!executeTime.isAfter(LocalDateTime.now())) bad("执行时间须在当前时间之后");
        UnFlowJob existing = jobs.selectOne(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getTaskId, taskId).eq(UnFlowJob::getStatus, "PENDING"));
        if (existing != null) {
            if (existing.getExecuteTime().equals(executeTime)) return existing;
            conflict("此待办已有未执行的超时任务");
        }
        UnFlowJob job = new UnFlowJob();
        job.setExampleId(exampleId);
        job.setTaskId(taskId);
        job.setJobType("TIMEOUT");
        job.setExecuteTime(executeTime);
        job.setStatus("PENDING");
        job.setRetryCount(0);
        jobs.insert(job);
        task.setDueTime(executeTime);
        tasks.updateById(task);
        event(exampleId, taskId, null, "TIMEOUT_SCHEDULED", access.tenantMember(request, tenantId).getMemberId(),
                "处理期限：" + executeTime);
        return jobs.selectById(job.getId());
    }

    @GetMapping("/examples/{exampleId}/jobs")
    public List<UnFlowJob> jobList(@PathVariable long tenantId, @PathVariable long exampleId,
                                   HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        exampleInTenant(tenantId, exampleId);
        return jobs.selectList(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getExampleId, exampleId).orderByAsc(UnFlowJob::getId));
    }

    @GetMapping("/examples/{exampleId}/exceptions")
    public List<UnFlowException> exceptionList(@PathVariable long tenantId, @PathVariable long exampleId,
                                               HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        exampleInTenant(tenantId, exampleId);
        return exceptions.selectList(new LambdaQueryWrapper<UnFlowException>()
                .eq(UnFlowException::getExampleId, exampleId).orderByAsc(UnFlowException::getId));
    }

    /** 处置只确认异常并留下处理人和说明，不把失败实例伪装为恢复成功。 */
    @PostMapping("/examples/{exampleId}/exceptions/{exceptionId}/resolve")
    @Transactional
    public UnFlowException resolveException(@PathVariable long tenantId, @PathVariable long exampleId,
                                             @PathVariable long exceptionId,
                                             @Valid @RequestBody ResolveInput body, HttpServletRequest request) {
        long memberId = access.tenantAdmin(request, tenantId).getMemberId();
        lockExample(tenantId, exampleId);
        UnFlowException incident = exceptions.selectById(exceptionId);
        if (incident == null || !flowIdEquals(incident.getExampleId(), exampleId)) notFound("流程异常不存在");
        if ("RESOLVED".equals(incident.getStatus())) {
            if (flowIdEquals(incident.getHandleMemberId(), memberId)
                    && body.handleRemark().equals(incident.getHandleRemark())) return incident;
            conflict("异常已由其他处置记录确认");
        }
        if (!"OPEN".equals(incident.getStatus())) conflict("异常状态无效");
        incident.setStatus("RESOLVED");
        incident.setHandleMemberId(memberId);
        incident.setHandleRemark(body.handleRemark());
        incident.setHandleTime(LocalDateTime.now());
        exceptions.updateById(incident);
        event(exampleId, incident.getTaskId(), null, "EXCEPTION_RESOLVED", memberId, body.handleRemark());
        return exceptions.selectById(exceptionId);
    }

    /** 后台按时间扫描到期任务；每个任务在独立事务中锁住实例后处理。 */
    @Scheduled(fixedDelay = 2000)
    public void runDueJobs() {
        List<UnFlowJob> due = jobs.selectList(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getStatus, "PENDING").le(UnFlowJob::getExecuteTime, LocalDateTime.now())
                .orderByAsc(UnFlowJob::getExecuteTime).last("LIMIT 20"));
        for (UnFlowJob item : due) {
            try {
                transactions.executeWithoutResult(status -> processDueJob(item.getId()));
            } catch (RuntimeException ex) {
                log.error("流程定时任务 {} 执行失败", item.getId(), ex);
                try { transactions.executeWithoutResult(status -> failJob(item.getId(), ex)); }
                catch (RuntimeException writeError) { log.error("流程定时任务 {} 失败记录未写入", item.getId(), writeError); }
            }
        }
    }

    private void processDueJob(long jobId) {
        UnFlowJob initial = jobs.selectById(jobId);
        if (initial == null) return;
        UnFlowExample example = examples.selectById(initial.getExampleId());
        if (example == null) return;
        // 锁定读取得到最新状态，避免审批动作与定时扫描并发时读取到事务旧快照。
        List<Map<String, Object>> exampleRows = jdbc.queryForList(
                "SELECT status FROM un_flow_example WHERE id = ? AND tenant_id = ? FOR UPDATE",
                example.getId(), example.getTenantId());
        if (exampleRows.isEmpty()) return;
        List<Map<String, Object>> jobRows = jdbc.queryForList(
                "SELECT status FROM un_flow_job WHERE id = ? FOR UPDATE", jobId);
        if (jobRows.isEmpty()) return;
        UnFlowJob job = jobs.selectById(jobId);
        if (job == null || !"PENDING".equals(jobRows.getFirst().get("status"))
                || job.getExecuteTime().isAfter(LocalDateTime.now())) return;
        LocalDateTime now = LocalDateTime.now();
        UnFlowTask task = job.getTaskId() == null ? null : tasks.selectById(job.getTaskId());
        List<Map<String, Object>> taskRows = task == null ? List.of() : jdbc.queryForList(
                "SELECT status FROM un_flow_task WHERE id = ? AND example_id = ? FOR UPDATE",
                task.getId(), example.getId());
        if (!"RUNNING".equals(exampleRows.getFirst().get("status")) || taskRows.isEmpty()
                || !"PENDING".equals(taskRows.getFirst().get("status"))) {
            job.setStatus("CANCELLED");
            job.setFinishTime(now);
            jobs.updateById(job);
            return;
        }
        jdbc.update("UPDATE un_flow_task SET status = 'EXPIRED', finish_time = ? WHERE id = ?", now, task.getId());
        cancelCandidates(task.getId(), now);
        jdbc.update("UPDATE un_flow_example SET status = 'FAILED', current_node_key = NULL, finish_time = ? WHERE id = ?",
                now, example.getId());
        job.setStatus("DONE");
        job.setFinishTime(now);
        jobs.updateById(job);
        recordException(example.getId(), task.getId(), "TIMEOUT", "任务超过处理期限，流程已停止");
        event(example.getId(), task.getId(), null, "TASK_EXPIRED", null, "任务超时，流程已停止");
    }

    private void failJob(long jobId, RuntimeException error) {
        UnFlowJob job = jobs.selectById(jobId);
        if (job == null || !"PENDING".equals(job.getStatus())) return;
        UnFlowExample example = examples.selectById(job.getExampleId());
        if (example != null) lockExample(example.getTenantId(), example.getId());
        jdbc.queryForList("SELECT id FROM un_flow_job WHERE id = ? FOR UPDATE", jobId);
        job = jobs.selectById(jobId);
        if (job == null || !"PENDING".equals(job.getStatus())) return;
        job.setStatus("FAILED");
        job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
        job.setLastError(error.getMessage() == null ? "定时任务执行失败" : error.getMessage().substring(0,
                Math.min(1000, error.getMessage().length())));
        job.setFinishTime(LocalDateTime.now());
        jobs.updateById(job);
        if (example != null) {
            LocalDateTime now = LocalDateTime.now();
            UnFlowTask task = job.getTaskId() == null ? null : tasks.selectById(job.getTaskId());
            if (task != null && "PENDING".equals(task.getStatus())) {
                task.setStatus("EXPIRED");
                task.setFinishTime(now);
                tasks.updateById(task);
                cancelCandidates(task.getId(), now);
            }
            if ("RUNNING".equals(example.getStatus())) {
                example.setStatus("FAILED");
                example.setCurrentNodeKey(null);
                example.setFinishTime(now);
                examples.updateById(example);
            }
            recordException(example.getId(), job.getTaskId(), "JOB_ERROR", job.getLastError());
            event(example.getId(), job.getTaskId(), null, "JOB_FAILED", null, job.getLastError());
        }
    }

    /** 实例历史包含冻结的定义、操作和不可变事件。 */
    @GetMapping("/examples/{exampleId}")
    public ExampleHistory history(@PathVariable long tenantId, @PathVariable long exampleId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        UnFlowExample example = exampleInTenant(tenantId, exampleId);
        // 历史包含处理意见，只有发起人、曾经的候选处理人或管理员可查看。
        Long participation = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_flow_task_candidate c
                JOIN un_flow_task t ON t.id = c.task_id
                WHERE t.example_id = ? AND c.member_id = ?
                """, Long.class, exampleId, actorId);
        boolean participant = participation != null && participation > 0;
        if (!flowIdEquals(example.getStarterMemberId(), actorId) && !participant)
            access.tenantAdmin(request, tenantId);
        return new ExampleHistory(example,
                tasks.selectList(new LambdaQueryWrapper<UnFlowTask>().eq(UnFlowTask::getExampleId, exampleId).orderByAsc(UnFlowTask::getId)),
                actions.selectList(new LambdaQueryWrapper<UnFlowAction>().eq(UnFlowAction::getExampleId, exampleId).orderByAsc(UnFlowAction::getId)),
                events.selectList(new LambdaQueryWrapper<UnFlowEvent>().eq(UnFlowEvent::getExampleId, exampleId).orderByAsc(UnFlowEvent::getId)),
                jobs.selectList(new LambdaQueryWrapper<UnFlowJob>().eq(UnFlowJob::getExampleId, exampleId).orderByAsc(UnFlowJob::getId)),
                exceptions.selectList(new LambdaQueryWrapper<UnFlowException>().eq(UnFlowException::getExampleId, exampleId).orderByAsc(UnFlowException::getId)));
    }

    private UnFlow flowInTenant(long tenantId, long flowId) {
        UnFlow flow = flows.selectById(flowId);
        if (flow == null || !flowIdEquals(flow.getTenantId(), tenantId)) notFound("流程不存在");
        return flow;
    }

    private UnFlowExample exampleInTenant(long tenantId, long exampleId) {
        UnFlowExample example = examples.selectById(exampleId);
        if (example == null || !flowIdEquals(example.getTenantId(), tenantId)) notFound("流程实例不存在");
        return example;
    }

    /** 锁定主记录使同一实例的并发操作串行，事务同时提交状态与历史。 */
    private UnFlowExample lockExample(long tenantId, long exampleId) {
        jdbc.queryForList("SELECT id FROM un_flow_example WHERE id = ? AND tenant_id = ? FOR UPDATE", exampleId, tenantId);
        return exampleInTenant(tenantId, exampleId);
    }

    private UnFlow lockFlow(long tenantId, long flowId) {
        jdbc.queryForList("SELECT id FROM un_flow WHERE id = ? AND tenant_id = ? FOR UPDATE", flowId, tenantId);
        return flowInTenant(tenantId, flowId);
    }

    private UnFlowBinding lockBinding(long tenantId, long bindingId) {
        jdbc.queryForList("SELECT id FROM un_flow_binding WHERE id = ? AND tenant_id = ? FOR UPDATE", bindingId, tenantId);
        UnFlowBinding binding = bindings.selectById(bindingId);
        if (binding == null || !flowIdEquals(binding.getTenantId(), tenantId)) notFound("自动流程绑定不存在");
        return binding;
    }

    private void requireModule(long tenantId, Long moduleId) {
        if (moduleId == null) bad("模块主键不能为空");
        Long count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM un_module module
                JOIN un_sys_tenant tenant ON tenant.system_id = module.system_id
                WHERE module.id = ? AND tenant.id = ? AND module.status = 1 AND tenant.status = 1
                """, Long.class, moduleId, tenantId);
        if (count == null || count == 0) bad("模块不属于此租户系统或已停用");
    }

    private List<UnFlowNode> draftNodes(long flowId) {
        return nodes.selectList(new LambdaQueryWrapper<UnFlowNode>().eq(UnFlowNode::getFlowId, flowId).orderByAsc(UnFlowNode::getSortNum));
    }

    private List<UnFlowEdge> draftEdges(long flowId) {
        return edges.selectList(new LambdaQueryWrapper<UnFlowEdge>().eq(UnFlowEdge::getFlowId, flowId).orderByAsc(UnFlowEdge::getSortNum));
    }

    /** 每个分支留一条默认线，条件按 sortNum 顺序匹配，且所有节点必须可达并最终结束。 */
    private void validateGraph(List<UnFlowNode> nodeList, List<UnFlowEdge> edgeList, long tenantId) {
        if (nodeList.stream().filter(n -> "START".equals(n.getNodeType())).count() != 1
                || nodeList.stream().filter(n -> "END".equals(n.getNodeType())).count() != 1) bad("流程必须各有一个开始和结束节点");
        Map<String, UnFlowNode> byKey = new HashMap<>();
        for (UnFlowNode node : nodeList) {
            if (byKey.put(node.getNodeKey(), node) != null) bad("流程节点标识重复");
            if (!Set.of("START", "TASK", "END").contains(node.getNodeType())) bad("流程节点类型无效");
            if ("TASK".equals(node.getNodeType())) {
                if (node.getAssigneeType() == null || !Set.of("MEMBER", "STARTER", "ROLE").contains(node.getAssigneeType()))
                    bad("人工任务处理人来源无效");
                if ("MEMBER".equals(node.getAssigneeType())) requireTenantMember(tenantId, node.getAssigneeId());
                if ("ROLE".equals(node.getAssigneeType()) && roleMembers(tenantId, node.getAssigneeId()).isEmpty())
                    bad("角色不属于此租户、未启用或没有有效处理人");
            }
        }
        Map<String, List<UnFlowEdge>> outgoingByKey = new HashMap<>();
        for (UnFlowEdge edge : edgeList) {
            if (!byKey.containsKey(edge.getSourceNodeKey()) || !byKey.containsKey(edge.getTargetNodeKey())) bad("连线节点不存在");
            if ("START".equals(byKey.get(edge.getTargetNodeKey()).getNodeType())) bad("连线不能返回开始节点");
            if (hasRule(edge.getConditionRule())) rule(edge.getConditionRule());
            outgoingByKey.computeIfAbsent(edge.getSourceNodeKey(), key -> new ArrayList<>()).add(edge);
        }
        for (UnFlowNode node : nodeList) {
            List<UnFlowEdge> outgoing = outgoingByKey.getOrDefault(node.getNodeKey(), List.of());
            if ("END".equals(node.getNodeType())) {
                if (!outgoing.isEmpty()) bad("结束节点不能有连线");
                continue;
            }
            if (outgoing.isEmpty() || outgoing.stream().filter(e -> !hasRule(e.getConditionRule())).count() != 1)
                bad("每个非结束节点须有一条默认连线");
            Set<Integer> positions = new HashSet<>();
            for (UnFlowEdge edge : outgoing) if (!positions.add(edge.getSortNum())) bad("同一节点的连线顺序不能重复");
        }
        Set<String> visited = new HashSet<>();
        visit(nodeList.stream().filter(n -> "START".equals(n.getNodeType())).findFirst().orElseThrow().getNodeKey(),
                outgoingByKey, new HashSet<>(), visited);
        if (visited.size() != nodeList.size()) bad("流程存在不可达节点");
    }

    private void visit(String key, Map<String, List<UnFlowEdge>> outgoing, Set<String> active, Set<String> visited) {
        if (active.contains(key)) bad("流程不能形成循环");
        if (visited.contains(key)) return;
        active.add(key);
        for (UnFlowEdge edge : outgoing.getOrDefault(key, List.of())) visit(edge.getTargetNodeKey(), outgoing, active, visited);
        active.remove(key);
        visited.add(key);
    }

    /** 只接受字段等值或数字比较的固定 JSON 结构，禁止脚本和动态 SQL。 */
    private JsonNode rule(String source) {
        try {
            JsonNode value = json.readTree(source);
            if (!value.isObject() || value.size() != 3 || !value.has("field") || !value.has("operator") || !value.has("value")
                    || !value.get("field").isTextual() || !value.get("field").asText().matches("[a-zA-Z][a-zA-Z0-9_]{0,63}")
                    || !value.get("operator").isTextual()) bad("条件只支持 field、operator、value 三个字段");
            String operator = value.get("operator").asText().toUpperCase(Locale.ROOT);
            JsonNode expected = value.get("value");
            if (!Set.of("EQ", "NE", "GT", "GTE", "LT", "LTE").contains(operator)
                    || !(expected.isTextual() || expected.isNumber())
                    || (!Set.of("EQ", "NE").contains(operator) && !expected.isNumber())) bad("条件操作符或比较值无效");
            return value;
        } catch (ResponseStatusException ex) { throw ex; }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "条件不是有效 JSON", ex); }
    }

    private boolean hasRule(String source) { return source != null && !source.isBlank(); }

    /** 条件按顺序先匹配，未命中时走唯一默认线；缺少字段值不命中任何条件。 */
    private String nextNode(DraftSnapshot snapshot, String key, long tenantId, Long moduleId, Long recordId) {
        List<UnFlowEdge> outgoing = snapshot.edges().stream().filter(e -> key.equals(e.getSourceNodeKey()))
                .sorted(Comparator.comparingInt(UnFlowEdge::getSortNum)).toList();
        for (UnFlowEdge edge : outgoing) if (hasRule(edge.getConditionRule())
                && matches(rule(edge.getConditionRule()), tenantId, moduleId, recordId)) return edge.getTargetNodeKey();
        return outgoing.stream().filter(e -> !hasRule(e.getConditionRule())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "发布快照缺少默认连线")).getTargetNodeKey();
    }

    private boolean matches(JsonNode condition, long tenantId, Long moduleId, Long recordId) {
        if (moduleId == null || recordId == null) return false;
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT value_text, value_number FROM un_module_record_data
                WHERE tenant_id = ? AND module_id = ? AND record_id = ? AND field_name = ?
                """, tenantId, moduleId, recordId, condition.get("field").asText());
        if (rows.isEmpty()) return false;
        JsonNode expected = condition.get("value");
        String operator = condition.get("operator").asText().toUpperCase(Locale.ROOT);
        Object actual = rows.getFirst().get(expected.isNumber() ? "value_number" : "value_text");
        if (actual == null) return false;
        int comparison = expected.isNumber() ? new BigDecimal(actual.toString()).compareTo(expected.decimalValue())
                : actual.toString().compareTo(expected.asText());
        return switch (operator) {
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            case "GT" -> comparison > 0;
            case "GTE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LTE" -> comparison <= 0;
            default -> false;
        };
    }

    /** 进入人工节点时解析真实租户成员，结束节点直接关闭实例。 */
    private void enterNode(long tenantId, UnFlowExample example, DraftSnapshot snapshot, String key) {
        UnFlowNode node = snapshot.nodes().stream().filter(n -> key.equals(n.getNodeKey())).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "发布快照缺少节点"));
        if ("END".equals(node.getNodeType())) {
            example.setStatus("COMPLETED");
            example.setFinishTime(LocalDateTime.now());
            example.setCurrentNodeKey(null);
            examples.updateById(example);
            event(example.getId(), null, null, "FINISHED", null, "流程已完成");
            return;
        }
        if (!"TASK".equals(node.getNodeType())) conflict("运行节点类型无效");
        List<Long> assignees;
        if ("ROLE".equals(node.getAssigneeType())) assignees = roleMembers(tenantId, node.getAssigneeId());
        else {
            long assignee = "STARTER".equals(node.getAssigneeType()) ? example.getStarterMemberId() : node.getAssigneeId();
            requireTenantMember(tenantId, assignee);
            assignees = List.of(assignee);
        }
        if (assignees.isEmpty()) conflict("任务角色当前没有有效处理人");
        UnFlowTask task = new UnFlowTask();
        task.setExampleId(example.getId());
        task.setNodeKey(key);
        task.setRoundNo(1);
        task.setStatus("PENDING");
        tasks.insert(task);
        for (Long assignee : assignees) {
            UnFlowTaskCandidate candidate = new UnFlowTaskCandidate();
            candidate.setTaskId(task.getId());
            candidate.setMemberId(assignee);
            candidate.setStatus("PENDING");
            candidates.insert(candidate);
        }
        event(example.getId(), task.getId(), null, "TASK_CREATED", null, node.getName());
    }

    /** 角色成员同时核对角色、租户关系及系统账号的启用状态。 */
    private List<Long> roleMembers(long tenantId, Long roleId) {
        if (roleId == null) return List.of();
        return jdbc.queryForList("""
                SELECT DISTINCT tm.member_id FROM un_sys_role role
                JOIN un_sys_member_role relation ON relation.role_id = role.id
                JOIN un_sys_tenant_member tm ON tm.id = relation.tenant_member_id
                JOIN un_sys_member sm ON sm.id = tm.member_id
                WHERE role.id = ? AND role.tenant_id = ? AND role.status = 1
                  AND tm.tenant_id = ? AND tm.status = 1 AND sm.status = 1
                ORDER BY tm.member_id
                """, Long.class, roleId, tenantId, tenantId);
    }

    private void cancelCandidates(long taskId, LocalDateTime now) {
        for (UnFlowTaskCandidate candidate : candidates.selectList(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                .eq(UnFlowTaskCandidate::getTaskId, taskId).eq(UnFlowTaskCandidate::getStatus, "PENDING"))) {
            candidate.setStatus("CANCELLED");
            candidate.setActionTime(now);
            candidates.updateById(candidate);
        }
    }

    private void cancelJobs(long taskId, LocalDateTime now) {
        for (UnFlowJob job : jobs.selectList(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getTaskId, taskId).eq(UnFlowJob::getStatus, "PENDING"))) {
            job.setStatus("CANCELLED");
            job.setFinishTime(now);
            jobs.updateById(job);
        }
    }

    private UnFlowException recordException(long exampleId, Long taskId, String type, String detail) {
        UnFlowException incident = new UnFlowException();
        incident.setExampleId(exampleId);
        incident.setTaskId(taskId);
        incident.setExceptionType(type);
        incident.setDetail(detail == null ? "流程处理异常" : detail.substring(0, Math.min(1000, detail.length())));
        incident.setStatus("OPEN");
        exceptions.insert(incident);
        return exceptions.selectById(incident.getId());
    }

    private void requireTenantMember(long tenantId, Long memberId) {
        if (memberId == null || tenantMembers.selectCount(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getTenantId, tenantId).eq(UnSysTenantMember::getMemberId, memberId)
                .eq(UnSysTenantMember::getStatus, 1)) == 0) bad("处理人不是此租户的有效成员");
    }

    private void requireRecord(long tenantId, long moduleId, long recordId, HttpServletRequest request) {
        UnModuleRecord record = records.selectById(recordId);
        if (record == null || !flowIdEquals(record.getTenantId(), tenantId)
                || !flowIdEquals(record.getModuleId(), moduleId)
                || (record.getRecordStatus() != null && record.getRecordStatus() == 2))
            bad("业务记录不属于此租户和模块，或已删除");
        recordAccess.requireWrite(recordAccess.scope(request, tenantId), record);
    }

    private void event(long exampleId, Long taskId, Long actionId, String type, Long actor, String description) {
        UnFlowEvent event = new UnFlowEvent();
        event.setExampleId(exampleId);
        event.setTaskId(taskId);
        event.setActionId(actionId);
        event.setEventType(type);
        event.setActorMemberId(actor);
        event.setDescription(description);
        events.insert(event);
    }

    private String writeJson(DraftSnapshot snapshot) {
        try { return json.writeValueAsString(snapshot); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "流程快照生成失败", ex); }
    }

    private DraftSnapshot readJson(String value) {
        try { return json.readValue(value, DraftSnapshot.class); }
        catch (Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "流程发布快照无法读取", ex); }
    }

    private boolean flowIdEquals(Long id, long expected) { return id != null && id == expected; }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private void forbidden(String message) { throw new ResponseStatusException(HttpStatus.FORBIDDEN, message); }
    private void notFound(String message) { throw new ResponseStatusException(HttpStatus.NOT_FOUND, message); }

    /** 流程编辑输入不允许客户端指定租户或发布版本。 */
    public record FlowInput(@NotBlank @Size(max = 100) String name, @Size(max = 500) String description) {}
    public record NodeInput(String nodeKey, String name, String nodeType, String assigneeType, Long assigneeId, Integer sortNum) {}
    public record EdgeInput(String sourceNodeKey, String targetNodeKey, String conditionRule, Integer sortNum) {}
    public record DraftInput(String name, String description, List<NodeInput> nodes, List<EdgeInput> edges) {}
    public record StartInput(Long moduleId, Long recordId) {}
    public record ActionInput(@NotBlank String actionType, Long taskId,
                              @NotBlank @Size(max = 64) String requestKey, @Size(max = 1000) String remark) {}
    public record BindingInput(@NotNull Long moduleId, @NotNull Long flowId, @NotBlank String triggerEvent) {}
    public record BindingStatusInput(@NotNull Boolean enabled) {}
    public record TimeoutInput(@NotNull LocalDateTime executeTime) {}
    public record ResolveInput(@NotBlank @Size(max = 1000) String handleRemark) {}
    public record DraftSnapshot(List<UnFlowNode> nodes, List<UnFlowEdge> edges) {}
    public record FlowDetail(UnFlow flow, List<UnFlowNode> nodes, List<UnFlowEdge> edges, List<UnFlowVersion> versions) {}
    public record ExampleHistory(UnFlowExample example, List<UnFlowTask> tasks,
                                 List<UnFlowAction> actions, List<UnFlowEvent> events,
                                 List<UnFlowJob> jobs, List<UnFlowException> exceptions) {}
    public record ExampleBrief(Long id, Long flowId, Long versionId, Long bindingId, String status,
                               LocalDateTime startTime, LocalDateTime finishTime) {}
    public record ExamplePage(List<ExampleBrief> records, long total, long page, long size) {}
}
