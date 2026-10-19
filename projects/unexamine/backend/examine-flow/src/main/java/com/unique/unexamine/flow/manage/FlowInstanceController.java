package com.unique.unexamine.flow.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.flow.base.entity.UnFlow;
import com.unique.unexamine.flow.base.entity.UnFlowAction;
import com.unique.unexamine.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.flow.base.entity.UnFlowEvent;
import com.unique.unexamine.flow.base.entity.UnFlowExample;
import com.unique.unexamine.flow.base.entity.UnFlowException;
import com.unique.unexamine.flow.base.entity.UnFlowJob;
import com.unique.unexamine.flow.base.entity.UnFlowTask;
import com.unique.unexamine.flow.base.entity.UnFlowTaskCandidate;
import com.unique.unexamine.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.flow.base.mapper.UnFlowActionMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowBindingMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowEventMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowExampleMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowExceptionMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowJobMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowTaskCandidateMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowTaskMapper;
import com.unique.unexamine.flow.base.mapper.UnFlowVersionMapper;
import com.unique.unexamine.module.base.entity.UnModuleRecord;
import com.unique.unexamine.module.base.mapper.UnModuleRecordMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import com.unique.unexamine.module.manage.ModuleRecordAccess;
import com.unique.unexamine.module.manage.ModuleRecordFlowTrigger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

import static com.unique.unexamine.flow.manage.FlowOperations.*;
import com.unique.unexamine.flow.manage.FlowOperations.DraftSnapshot;

/** 流程实例自动或人工发起、待办处理与运行历史入口。 */
@Tag(name = "流程实例与待办", description = "流程实例、待办和处理动作。")
@RestController
@RequestMapping("/api/tenants/{tenantId}/flows")
public class FlowInstanceController implements ModuleRecordFlowTrigger {
    private final SystemSessionAccess access;
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
    private final ModuleRecordAccess recordAccess;
    private final JdbcTemplate jdbc;
    private final FlowOperations ops;

    public FlowInstanceController(SystemSessionAccess access, UnFlowVersionMapper versions,
                                  UnFlowExampleMapper examples, UnFlowTaskMapper tasks,
                                  UnFlowTaskCandidateMapper candidates, UnFlowActionMapper actions,
                                  UnFlowEventMapper events, UnFlowBindingMapper bindings,
                                  UnFlowJobMapper jobs, UnFlowExceptionMapper exceptions,
                                  UnModuleRecordMapper records, ModuleRecordAccess recordAccess,
                                  JdbcTemplate jdbc, FlowOperations ops) {
        this.access = access;
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
        this.recordAccess = recordAccess;
        this.jdbc = jdbc;
        this.ops = ops;
    }

    /** 模块域在同一事务内调用；记录版本进入唯一请求号，重复调用不会产生第二个实例。 */
    @Override
    @Transactional
    public void triggerRecordEvent(long tenantId, long moduleId, long recordId,
                                   int recordVersion, long actorMemberId, String eventType) {
        if (!Set.of("CREATE", "UPDATE").contains(eventType) || recordVersion < 1) bad("记录事件或版本无效");
        ops.requireTenantMember(tenantId, actorMemberId);
        UnModuleRecord record = records.selectById(recordId);
        if (record == null || !flowIdEquals(record.getTenantId(), tenantId)
                || !flowIdEquals(record.getModuleId(), moduleId)) bad("自动触发的记录或模块不匹配");
        for (UnFlowBinding found : bindings.selectList(new LambdaQueryWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getTenantId, tenantId).eq(UnFlowBinding::getModuleId, moduleId)
                .eq(UnFlowBinding::getTriggerEvent, eventType).eq(UnFlowBinding::getStatus, "ENABLED")
                .orderByAsc(UnFlowBinding::getId))) {
            UnFlowBinding binding = ops.lockBinding(tenantId, found.getId());
            if (!"ENABLED".equals(binding.getStatus())) continue;
            UnFlow flow = ops.flowInTenant(tenantId, binding.getFlowId());
            if (!"ENABLED".equals(flow.getStatus())) continue;
            String key = "AUTO:" + Long.toString(binding.getId(), 36) + ":" + Long.toString(recordId, 36)
                    + ":" + Integer.toString(recordVersion, 36);
            UnFlowAction previous = actions.selectOne(new LambdaQueryWrapper<UnFlowAction>()
                    .eq(UnFlowAction::getTenantId, tenantId).eq(UnFlowAction::getRequestKey, key));
            if (previous != null) {
                continue;
            }
            if (record.getVersion() == null || record.getVersion() != recordVersion
                    || (record.getRecordStatus() != null && record.getRecordStatus() == 2))
                bad("自动触发的记录版本无效或记录已删除");
            UnFlowVersion version = versions.selectById(binding.getVersionId());
            if (version == null || !flowIdEquals(version.getFlowId(), flow.getId())) conflict("绑定发布版本不存在");
            DraftSnapshot snapshot = ops.readJson(version.getDefinitionSnapshot());
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
            ops.event(example.getId(), null, action.getId(), "AUTO_TRIGGERED", actorMemberId, eventType);
            try {
                String next = ops.nextNode(snapshot, startKey, tenantId, moduleId, recordId);
                example.setCurrentNodeKey(next);
                examples.updateById(example);
                ops.enterNode(tenantId, example, snapshot, next);
            } catch (ResponseStatusException ex) {
                LocalDateTime now = LocalDateTime.now();
                example.setStatus("FAILED");
                example.setCurrentNodeKey(null);
                example.setFinishTime(now);
                examples.updateById(example);
                action.setResultStatus("FAILED");
                actions.updateById(action);
                String detail = ex.getReason() == null ? "自动流程执行失败" : ex.getReason();
                ops.recordException(example.getId(), null, detail.contains("处理人") ? "NO_ASSIGNEE" : "RULE_ERROR", detail);
                ops.event(example.getId(), null, action.getId(), "FAILED", actorMemberId, detail);
            }
        }
    }

    /** 从发布版本启动实例；关联业务记录时同时校验租户、模块和发起人的修改权限。 */
    @Operation(summary = "发起流程（流程实例与待办）")
    @PostMapping("/{flowId}/examples")
    @Transactional
    public UnFlowExample start(@PathVariable long tenantId, @PathVariable long flowId,
                               @RequestBody StartInput body, HttpServletRequest request) {
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnFlow flow = ops.flowInTenant(tenantId, flowId);
        if (!"ENABLED".equals(flow.getStatus()) || flow.getCurrentVersionId() == null) conflict("流程尚未发布或已停用");
        if ((body.moduleId() == null) != (body.recordId() == null)) bad("模块和记录应同时提供");
        if (body.recordId() != null) ops.requireRecord(tenantId, body.moduleId(), body.recordId(), request);
        UnFlowVersion version = versions.selectById(flow.getCurrentVersionId());
        if (version == null || !flowIdEquals(version.getFlowId(), flowId)) conflict("流程发布版本不存在");
        DraftSnapshot snapshot = ops.readJson(version.getDefinitionSnapshot());
        if (body.recordId() == null && snapshot.edges().stream().anyMatch(e -> ops.hasRule(e.getConditionRule())))
            bad("条件流程发起时须关联模块记录");
        String first = ops.nextNode(snapshot, snapshot.nodes().stream().filter(n -> "START".equals(n.getNodeType()))
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
        ops.event(example.getId(), null, null, "STARTED", memberId, "流程已发起");
        ops.enterNode(tenantId, example, snapshot, first);
        return examples.selectById(example.getId());
    }

    /** 待办从候选人和实例租户交叉核对，不接受客户端传入处理人编号。 */
    @Operation(summary = "查询待办（流程实例与待办）")
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
    @Operation(summary = "查询记录流程实例（流程实例与待办）")
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
    @Operation(summary = "处理流程待办（流程实例与待办）")
    @PostMapping("/examples/{exampleId}/actions")
    @Transactional
    public UnFlowAction act(@PathVariable long tenantId, @PathVariable long exampleId,
                            @Valid @RequestBody ActionInput body, HttpServletRequest request) {
        if (body.requestKey().startsWith("AUTO:")) bad("请求标识前缀保留给自动流程");
        long memberId = access.tenantMember(request, tenantId).getMemberId();
        UnFlowExample example = ops.lockExample(tenantId, exampleId);
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
                ops.cancelCandidates(open.getId(), now);
                ops.cancelJobs(open.getId(), now);
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
            ops.cancelCandidates(task.getId(), now);
            task.setStatus("COMPLETED");
            task.setClaimMemberId(memberId);
            task.setClaimTime(now);
            task.setFinishTime(now);
            tasks.updateById(task);
            ops.cancelJobs(task.getId(), now);
            if ("REJECT".equals(body.actionType())) {
                example.setStatus("REJECTED");
                example.setCurrentNodeKey(null);
                example.setFinishTime(now);
                eventType = "REJECTED";
            } else {
                DraftSnapshot snapshot = ops.readJson(example.getDefinitionSnapshot());
                String next = ops.nextNode(snapshot, task.getNodeKey(), tenantId, example.getModuleId(), example.getRecordId());
                example.setCurrentNodeKey(next);
                ops.enterNode(tenantId, example, snapshot, next);
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
        ops.event(exampleId, task == null ? null : task.getId(), action.getId(), eventType, memberId, body.remark());
        return action;
    }

    /** 实例历史包含冻结的定义、操作和不可变事件。 */
    @Operation(summary = "查询处理历史（流程实例与待办）")
    @GetMapping("/examples/{exampleId}")
    public ExampleHistory history(@PathVariable long tenantId, @PathVariable long exampleId, HttpServletRequest request) {
        long actorId = access.tenantMember(request, tenantId).getMemberId();
        UnFlowExample example = ops.exampleInTenant(tenantId, exampleId);
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

    /** 人工发起、操作和记录页返回结构。 */
    public record StartInput(Long moduleId, Long recordId) {}
    public record ActionInput(@NotBlank String actionType, Long taskId,
                              @NotBlank @Size(max = 64) String requestKey, @Size(max = 1000) String remark) {}
    public record ExampleHistory(UnFlowExample example, List<UnFlowTask> tasks,
                                 List<UnFlowAction> actions, List<UnFlowEvent> events,
                                 List<UnFlowJob> jobs, List<UnFlowException> exceptions) {}
    public record ExampleBrief(Long id, Long flowId, Long versionId, Long bindingId, String status,
                               LocalDateTime startTime, LocalDateTime finishTime) {}
    public record ExamplePage(List<ExampleBrief> records, long total, long page, long size) {}
}
