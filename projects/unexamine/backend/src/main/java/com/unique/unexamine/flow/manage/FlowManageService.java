package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowEvent;
import com.unique.unexamine.flow.base.entity.FlowExample;
import com.unique.unexamine.flow.base.entity.FlowNode;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.entity.FlowVersion;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowEventBaseService;
import com.unique.unexamine.flow.base.service.FlowExampleBaseService;
import com.unique.unexamine.flow.base.service.FlowNodeBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import com.unique.unexamine.flow.base.service.FlowVersionBaseService;
import com.unique.unexamine.moduleconfig.base.entity.BusinessModule;
import com.unique.unexamine.message.manage.MessageManageService;
import com.unique.unexamine.moduleconfig.base.service.BusinessModuleBaseService;
import com.unique.unexamine.runtimedata.base.entity.BusinessRecord;
import com.unique.unexamine.runtimedata.base.service.BusinessRecordBaseService;
import com.unique.unexamine.runtimedata.manage.RecordManageService;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.TenantMember;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.TenantMemberBaseService;
import com.unique.unexamine.system.manage.SystemManageService;
import com.unique.unexamine.system.manage.SystemPermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/** 基础审批：一个已发布模块绑定一个指定成员节点，发布快照驱动每轮实例与待办。 */
@Service
public class FlowManageService {
    private final SystemManageService systems;
    private final SystemPermissionService access;
    private final BusinessModuleBaseService modules;
    private final SystemMemberBaseService members;
    private final TenantMemberBaseService tenantMembers;
    private final BusinessRecordBaseService records;
    private final RecordManageService recordViews;
    private final FlowDefinitionBaseService flows;
    private final FlowNodeBaseService nodes;
    private final FlowVersionBaseService versions;
    private final FlowExampleBaseService examples;
    private final FlowTaskBaseService tasks;
    private final FlowEventBaseService events;
    private final MessageManageService messages;
    private final ObjectMapper json;

    public FlowManageService(SystemManageService systems, SystemPermissionService access,
                             BusinessModuleBaseService modules, SystemMemberBaseService members,
                             TenantMemberBaseService tenantMembers, BusinessRecordBaseService records,
                             RecordManageService recordViews, FlowDefinitionBaseService flows,
                             FlowNodeBaseService nodes, FlowVersionBaseService versions,
                             FlowExampleBaseService examples, FlowTaskBaseService tasks,
                             FlowEventBaseService events, MessageManageService messages, ObjectMapper json) {
        this.systems = systems;
        this.access = access;
        this.modules = modules;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.records = records;
        this.recordViews = recordViews;
        this.flows = flows;
        this.nodes = nodes;
        this.versions = versions;
        this.examples = examples;
        this.tasks = tasks;
        this.events = events;
        this.messages = messages;
        this.json = json;
    }

    /** 所属人读取当前模块的审批草稿和已发布版本，页面按此回显。 */
    public FlowConfig config(Long accountId, Long systemId, Long moduleId) {
        SystemPermissionService.Context context = ownerModule(accountId, systemId, moduleId);
        FlowDefinition flow = flow(context, moduleId);
        if (flow == null) return null;
        FlowNode node = node(flow.getId());
        FlowVersion version = flow.getPublishedVersionId() == null ? null : versions.selectById(flow.getPublishedVersionId());
        return new FlowConfig(flow.getId(), flow.getName(), node.getNodeName(), node.getApproverMemberId(),
                version == null ? null : version.getVersionNumber(), flow.getStatus());
    }

    /** 草稿保存只修改定义和节点；进行中实例继续使用原版本快照。 */
    @Transactional
    public FlowConfig saveDraft(Long accountId, Long systemId, Long moduleId, String name,
                                String nodeName, Long approverMemberId) {
        SystemPermissionService.Context context = ownerModule(accountId, systemId, moduleId);
        if (name == null || name.isBlank() || name.length() > 120 || nodeName == null || nodeName.isBlank()
                || nodeName.length() > 120) throw bad("审批名称或节点名称无效");
        requireApprover(context, approverMemberId);
        FlowDefinition flow = flow(context, moduleId);
        if (flow == null) {
            flow = new FlowDefinition();
            flow.setSystemId(systemId);
            flow.setTenantId(context.tenant().getId());
            flow.setModuleId(moduleId);
            flow.setStatus("DRAFT");
            flow.setName(name.trim());
            flows.insert(flow);
            FlowNode node = new FlowNode();
            node.setFlowId(flow.getId());
            node.setSortOrder(0);
            node.setNodeName(nodeName.trim());
            node.setApproverMemberId(approverMemberId);
            nodes.insert(node);
        } else {
            flow.setName(name.trim());
            flows.updateById(flow);
            FlowNode node = node(flow.getId());
            node.setNodeName(nodeName.trim());
            node.setApproverMemberId(approverMemberId);
            nodes.updateById(node);
        }
        return config(accountId, systemId, moduleId);
    }

    /** 发布校验审批人仍在主租户，保存不可变版本，再切换当前生效指针。 */
    @Transactional
    public FlowConfig publish(Long accountId, Long systemId, Long moduleId) {
        SystemPermissionService.Context context = ownerModule(accountId, systemId, moduleId);
        FlowDefinition flow = flow(context, moduleId);
        if (flow == null) throw bad("请先配置审批草稿");
        FlowNode node = node(flow.getId());
        requireApprover(context, node.getApproverMemberId());
        FlowVersion current = flow.getPublishedVersionId() == null ? null : versions.selectById(flow.getPublishedVersionId());
        FlowVersion version = new FlowVersion();
        version.setFlowId(flow.getId());
        version.setVersionNumber(current == null ? 1 : current.getVersionNumber() + 1);
        version.setSnapshotJson(write(new ApprovalSnapshot(flow.getName(), node.getNodeName(), node.getApproverMemberId())));
        version.setPublishedByAccountId(accountId);
        versions.insert(version);
        flow.setPublishedVersionId(version.getId());
        flow.setStatus("PUBLISHED");
        flows.updateById(flow);
        return config(accountId, systemId, moduleId);
    }

    /** 本人记录发起审批，记录状态、实例、任务与提交事件在同一事务提交。 */
    @Transactional
    public FlowRun submit(Long accountId, Long systemId, Long moduleId, Long recordId) {
        SystemPermissionService.Context context = access.requireModule(accountId, systemId, moduleId, "SUBMIT_APPROVAL");
        BusinessRecord record = recordViews.ownedForSubmission(context, moduleId, recordId);
        FlowDefinition flow = flow(context, moduleId);
        if (flow == null || flow.getPublishedVersionId() == null || !"PUBLISHED".equals(flow.getStatus())) {
            throw bad("模块尚未发布审批流程");
        }
        FlowVersion version = versions.selectById(flow.getPublishedVersionId());
        ApprovalSnapshot snapshot = read(version.getSnapshotJson());
        SystemMember approver = requireApprover(context, snapshot.approverMemberId());
        FlowExample previous = examples.selectList(new LambdaQueryWrapper<FlowExample>()
                .eq(FlowExample::getRecordId, recordId).orderByDesc(FlowExample::getAttemptNumber).last("LIMIT 1"))
                .stream().findFirst().orElse(null);
        record.setRecordStatus("PENDING");
        record.setUpdatedByAccountId(accountId);
        if (records.updateById(record) != 1) throw conflict("记录已被更新，请刷新后重试");
        FlowExample example = new FlowExample();
        example.setFlowId(flow.getId());
        example.setFlowVersionId(version.getId());
        example.setSystemId(systemId);
        example.setTenantId(context.tenant().getId());
        example.setModuleId(moduleId);
        example.setRecordId(recordId);
        example.setAttemptNumber(previous == null ? 1 : previous.getAttemptNumber() + 1);
        example.setApplicantMemberId(context.member().getId());
        example.setSnapshotJson(version.getSnapshotJson());
        example.setStatus("PENDING");
        examples.insert(example);
        FlowTask task = new FlowTask();
        task.setExampleId(example.getId());
        task.setApproverMemberId(snapshot.approverMemberId());
        task.setNodeName(snapshot.nodeName());
        task.setStatus("PENDING");
        tasks.insert(task);
        event(example.getId(), task.getId(), context.member().getId(), "SUBMIT", null);
        messages.sendApproval(systemId, context.tenant().getId(), approver.getAccountId(),
                "APPROVAL_TODO", moduleId, recordId, task.getId());
        return run(example, task, record.getTitle());
    }

    /** 当前登录成员只能看到自己尚未办理且仍有模块查看权限的待办。 */
    public List<FlowRun> todos(Long accountId, Long systemId) {
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        return tasks.selectList(new LambdaQueryWrapper<FlowTask>()
                .eq(FlowTask::getApproverMemberId, context.member().getId())
                .eq(FlowTask::getStatus, "PENDING").orderByDesc(FlowTask::getCreateTime))
                .stream().map(task -> {
                    FlowExample example = examples.selectById(task.getExampleId());
                    if (example == null || !systemId.equals(example.getSystemId())
                            || !context.tenant().getId().equals(example.getTenantId())
                            || !access.canModule(accountId, systemId, example.getModuleId(), "VIEW")) return null;
                    BusinessRecord record = records.selectById(example.getRecordId());
                    return record == null ? null : run(example, task, record.getTitle());
                }).filter(item -> item != null).toList();
    }

    /** 待办详情先校验审批人，再为其开放对应业务记录和实例历史。 */
    public TaskDetail detail(Long accountId, Long systemId, Long taskId) {
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        FlowTask task = tasks.selectById(taskId);
        if (task == null || !context.member().getId().equals(task.getApproverMemberId())) throw missing();
        FlowExample example = examples.selectById(task.getExampleId());
        if (example == null || !systemId.equals(example.getSystemId())
                || !context.tenant().getId().equals(example.getTenantId())) throw missing();
        access.requireModule(accountId, systemId, example.getModuleId(), "VIEW");
        RecordManageService.RecordView record = recordViews.approvalDetail(context, example.getModuleId(), example.getRecordId());
        List<FlowEvent> history = events.selectList(new LambdaQueryWrapper<FlowEvent>()
                .eq(FlowEvent::getExampleId, example.getId()).orderByAsc(FlowEvent::getId));
        return new TaskDetail(run(example, task, record.title()), record, history);
    }

    /** 同意或拒绝只能处理一次；乐观锁冲突回滚整笔事务。 */
    @Transactional
    public FlowRun decide(Long accountId, Long systemId, Long taskId, boolean approve, String opinion) {
        TaskDetail detail = detail(accountId, systemId, taskId);
        FlowTask task = tasks.selectById(taskId);
        FlowExample example = examples.selectById(task.getExampleId());
        if (!"PENDING".equals(task.getStatus()) || !"PENDING".equals(example.getStatus())) throw conflict("待办已办理");
        if (!approve && (opinion == null || opinion.isBlank())) throw bad("拒绝时必须填写原因");
        if (opinion != null && opinion.length() > 1000) throw bad("办理意见过长");
        BusinessRecord record = records.selectById(example.getRecordId());
        if (record == null || !"PENDING".equals(record.getRecordStatus())) throw conflict("业务记录状态已变化");
        String status = approve ? "APPROVED" : "REJECTED";
        task.setStatus(status);
        task.setOpinion(opinion == null ? null : opinion.trim());
        task.setFinishTime(LocalDateTime.now());
        if (tasks.updateById(task) != 1) throw conflict("待办已被其他操作处理");
        example.setStatus(status);
        example.setFinishTime(LocalDateTime.now());
        if (examples.updateById(example) != 1) throw conflict("流程实例已变化");
        record.setRecordStatus(status);
        record.setUpdatedByAccountId(accountId);
        if (records.updateById(record) != 1) throw conflict("业务记录已变化");
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        event(example.getId(), taskId, context.member().getId(), approve ? "APPROVE" : "REJECT", task.getOpinion());
        SystemMember applicant = members.selectById(example.getApplicantMemberId());
        if (applicant != null) {
            messages.sendApproval(systemId, context.tenant().getId(), applicant.getAccountId(),
                    approve ? "APPROVAL_APPROVED" : "APPROVAL_REJECTED",
                    example.getModuleId(), example.getRecordId(), taskId);
        }
        return run(example, task, detail.record().title());
    }

    private SystemPermissionService.Context ownerModule(Long accountId, Long systemId, Long moduleId) {
        systems.requireOwner(accountId, systemId);
        SystemPermissionService.Context context = access.requireMembership(accountId, systemId);
        BusinessModule module = modules.selectById(moduleId);
        if (module == null || !systemId.equals(module.getSystemId()) || !"PUBLISHED".equals(module.getStatus())) {
            throw bad("只能为本系统已发布模块配置审批");
        }
        return context;
    }

    private FlowDefinition flow(SystemPermissionService.Context context, Long moduleId) {
        return flows.selectOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getTenantId, context.tenant().getId()).eq(FlowDefinition::getModuleId, moduleId));
    }

    private FlowNode node(Long flowId) {
        FlowNode node = nodes.selectOne(new LambdaQueryWrapper<FlowNode>()
                .eq(FlowNode::getFlowId, flowId).eq(FlowNode::getSortOrder, 0));
        if (node == null) throw bad("审批节点尚未配置");
        return node;
    }

    private SystemMember requireApprover(SystemPermissionService.Context context, Long memberId) {
        SystemMember member = memberId == null ? null : members.selectById(memberId);
        if (member == null || !context.system().getId().equals(member.getSystemId())
                || !"ACTIVE".equals(member.getStatus()) || tenantMembers.selectOne(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, context.tenant().getId()).eq(TenantMember::getMemberId, memberId)
                .eq(TenantMember::getStatus, "ACTIVE")) == null) throw bad("审批人必须是当前系统主租户有效成员");
        return member;
    }

    private void event(Long exampleId, Long taskId, Long actorMemberId, String type, String opinion) {
        FlowEvent event = new FlowEvent();
        event.setExampleId(exampleId);
        event.setTaskId(taskId);
        event.setActorMemberId(actorMemberId);
        event.setEventType(type);
        event.setOpinion(opinion);
        events.insert(event);
    }

    private FlowRun run(FlowExample example, FlowTask task, String title) {
        return new FlowRun(task.getId(), example.getId(), example.getRecordId(), example.getModuleId(),
                title, task.getNodeName(), task.getStatus(), example.getFlowVersionId(), example.getAttemptNumber());
    }

    private String write(ApprovalSnapshot snapshot) {
        try { return json.writeValueAsString(snapshot); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("流程快照无法写入", exception); }
    }

    private ApprovalSnapshot read(String raw) {
        try { return json.readValue(raw, ApprovalSnapshot.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("流程快照无法读取", exception); }
    }

    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在或无访问权限"); }

    /** 发布版本和实例共用的指定审批节点快照。 */
    public record ApprovalSnapshot(String flowName, String nodeName, Long approverMemberId) {}
    /** 所属人页面的流程草稿和发布信息。 */
    public record FlowConfig(Long id, String name, String nodeName, Long approverMemberId,
                             Integer publishedVersionNumber, String status) {}
    /** 待办列表与办理结果的必要业务信息。 */
    public record FlowRun(Long taskId, Long exampleId, Long recordId, Long moduleId, String title,
                          String nodeName, String status, Long flowVersionId, Integer attemptNumber) {}
    /** 待办详情包含对应记录和不可变操作历史。 */
    public record TaskDetail(FlowRun task, RecordManageService.RecordView record, List<FlowEvent> history) {}
}
