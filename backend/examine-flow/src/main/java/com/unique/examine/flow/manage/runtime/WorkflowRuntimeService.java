package com.unique.examine.flow.manage.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.base.entity.FlowApprovalActionLog;
import com.unique.examine.flow.base.entity.FlowApprovalTask;
import com.unique.examine.flow.base.entity.FlowDefinition;
import com.unique.examine.flow.base.entity.FlowInstance;
import com.unique.examine.flow.base.entity.FlowSnapshot;
import com.unique.examine.flow.base.service.FlowApprovalActionLogBaseService;
import com.unique.examine.flow.base.service.FlowApprovalTaskBaseService;
import com.unique.examine.flow.base.service.FlowDefinitionBaseService;
import com.unique.examine.flow.base.service.FlowInstanceBaseService;
import com.unique.examine.flow.base.service.FlowSnapshotBaseService;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActor;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalTaskSnapshot;
import com.unique.examine.flow.manage.definition.FlowDefinitionModels.FlowNodeConfigVO;
import com.unique.examine.flow.manage.runtime.RuntimeModels.AuditInfo;
import com.unique.examine.flow.manage.runtime.RuntimeModels.BusinessTargetRef;
import com.unique.examine.flow.manage.runtime.RuntimeModels.TargetRef;
import com.unique.examine.flow.manage.runtime.RuntimeModels.TraceInfo;
import com.unique.examine.flow.manage.runtime.RuntimeModels.WorkflowHistoryItem;
import com.unique.examine.flow.manage.runtime.RuntimeModels.WorkflowInstanceSnapshot;
import com.unique.examine.flow.manage.runtime.RuntimeModels.WorkflowInstanceVO;
import com.unique.examine.flow.manage.runtime.RuntimeModels.WorkflowNodeSnapshot;
import com.unique.examine.flow.manage.runtime.RuntimeModels.WorkflowPermissionSnapshot;
import com.unique.examine.plat.base.entity.PlatDepartment;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.service.PlatDepartmentBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 流程实例运行态查询服务。
 */
@Service
public class WorkflowRuntimeService {

    private static final String TASK_PENDING = "PENDING";
    private static final String INSTANCE_RUNNING = "RUNNING";
    private static final TypeReference<List<FlowNodeConfigVO>> NODE_LIST_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final FlowInstanceBaseService instanceBaseService;
    private final FlowDefinitionBaseService flowDefinitionBaseService;
    private final FlowSnapshotBaseService flowSnapshotBaseService;
    private final FlowApprovalTaskBaseService taskBaseService;
    private final FlowApprovalActionLogBaseService actionLogBaseService;
    private final PlatMemberBaseService memberBaseService;
    private final PlatDepartmentBaseService departmentBaseService;
    private final ObjectMapper objectMapper;

    public WorkflowRuntimeService(FlowInstanceBaseService instanceBaseService,
                                  FlowDefinitionBaseService flowDefinitionBaseService,
                                  FlowSnapshotBaseService flowSnapshotBaseService,
                                  FlowApprovalTaskBaseService taskBaseService,
                                  FlowApprovalActionLogBaseService actionLogBaseService,
                                  PlatMemberBaseService memberBaseService,
                                  PlatDepartmentBaseService departmentBaseService,
                                  ObjectMapper objectMapper) {
        this.instanceBaseService = instanceBaseService;
        this.flowDefinitionBaseService = flowDefinitionBaseService;
        this.flowSnapshotBaseService = flowSnapshotBaseService;
        this.taskBaseService = taskBaseService;
        this.actionLogBaseService = actionLogBaseService;
        this.memberBaseService = memberBaseService;
        this.departmentBaseService = departmentBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询流程实例快照，包含当前任务、历史、业务目标、权限、追踪和审计信息。
     *
     * @param systemId 系统 ID
     * @param instanceId 流程实例 ID
     * @return 流程实例快照
     */
    public WorkflowInstanceSnapshot snapshot(String systemId, String instanceId) {
        RequestContext context = RequestContext.current();
        FlowInstance instance = requireInstance(systemId, instanceId);
        FlowDefinition flow = flowDefinitionBaseService.findById(instance.getFlowId()).orElse(null);
        FlowSnapshot snapshot = currentSnapshot(instance);
        List<FlowApprovalTask> tasks = tasks(instance.getId());
        List<FlowApprovalActionLog> logs = actionLogs(instance.getId());
        List<ApprovalTaskSnapshot> currentTasks = tasks.stream()
                .filter(task -> TASK_PENDING.equals(task.getStatus()))
                .map(this::taskSnapshot)
                .toList();
        List<WorkflowHistoryItem> history = history(instance, logs);
        return new WorkflowInstanceSnapshot(instanceVO(systemId, instance, flow, logs), nodes(snapshot, tasks),
                currentTasks, history, businessTarget(systemId, instance, flow),
                permission(currentTasks, tasks), new TraceInfo(context.traceId(), context.requestId(),
                "WEB", submitIdempotency(tasks)), audit(instance, logs));
    }

    private FlowInstance requireInstance(String systemId, String instanceId) {
        Long parsedInstanceId = parseLong(instanceId, "流程实例 ID 不合法");
        FlowInstance instance = instanceBaseService.findById(parsedInstanceId).orElse(null);
        if (Objects.isNull(instance) || !Objects.equals(instance.getSystemId(), parseLong(systemId, "系统 ID 不合法"))) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程实例不存在或不属于当前系统");
        }
        return instance;
    }

    private FlowSnapshot currentSnapshot(FlowInstance instance) {
        return flowSnapshotBaseService.getOne(new LambdaQueryWrapper<FlowSnapshot>()
                .eq(FlowSnapshot::getFlowId, instance.getFlowId())
                .eq(FlowSnapshot::getVersionNo, instance.getFlowVersion())
                .last("LIMIT 1"), false);
    }

    private List<FlowApprovalTask> tasks(Long instanceId) {
        return taskBaseService.list(new LambdaQueryWrapper<FlowApprovalTask>()
                .eq(FlowApprovalTask::getInstanceId, instanceId)
                .orderByAsc(FlowApprovalTask::getId));
    }

    private List<FlowApprovalActionLog> actionLogs(Long instanceId) {
        return actionLogBaseService.list(new LambdaQueryWrapper<FlowApprovalActionLog>()
                .eq(FlowApprovalActionLog::getInstanceId, instanceId)
                .orderByAsc(FlowApprovalActionLog::getOperatedAt));
    }

    private WorkflowInstanceVO instanceVO(String systemId, FlowInstance instance, FlowDefinition flow,
                                          List<FlowApprovalActionLog> logs) {
        return new WorkflowInstanceVO(String.valueOf(instance.getId()), systemId, String.valueOf(instance.getTenantId()),
                String.valueOf(instance.getFlowId()), Objects.isNull(flow) ? "审批流程" : flow.getFlowName(),
                instance.getFlowVersion(), instance.getStatus(), actor(instance.getStartedBy(), "发起人"),
                instance.getStartedAt(), updatedAt(instance, logs));
    }

    private List<WorkflowNodeSnapshot> nodes(FlowSnapshot snapshot, List<FlowApprovalTask> tasks) {
        List<FlowNodeConfigVO> canvasNodes = readNodes(Objects.isNull(snapshot) ? null : snapshot.getNodePayload());
        if (canvasNodes.isEmpty()) {
            return tasks.stream()
                    .map(task -> new WorkflowNodeSnapshot(task.getNodeId(), task.getNodeName(), "approval",
                            nodeStatus(task), task.getCreatedAt(), task.getOperatedAt(),
                            List.of(String.valueOf(task.getAssigneeMemberId())), null, nodeSummary(task)))
                    .toList();
        }
        Set<String> taskNodeKeys = new HashSet<>();
        Map<String, List<FlowApprovalTask>> nodeTasks = new LinkedHashMap<>();
        for (FlowApprovalTask task : tasks) {
            taskNodeKeys.add(task.getNodeId());
            nodeTasks.computeIfAbsent(task.getNodeId(), key -> List.of());
        }
        return canvasNodes.stream()
                .map(node -> nodeSnapshot(node, tasks.stream()
                        .filter(task -> Objects.equals(task.getNodeId(), node.nodeKey()))
                        .toList(), taskNodeKeys))
                .toList();
    }

    private WorkflowNodeSnapshot nodeSnapshot(FlowNodeConfigVO node, List<FlowApprovalTask> nodeTasks,
                                              Set<String> taskNodeKeys) {
        FlowApprovalTask pending = nodeTasks.stream()
                .filter(task -> TASK_PENDING.equals(task.getStatus()))
                .findFirst()
                .orElse(null);
        FlowApprovalTask latest = nodeTasks.stream()
                .max(Comparator.comparing(FlowApprovalTask::getId))
                .orElse(null);
        String status;
        if (Objects.nonNull(pending)) {
            status = "ACTIVE";
        } else if (Objects.nonNull(latest)) {
            status = "COMPLETED";
        } else {
            status = "WAITING";
        }
        LocalDateTime enteredAt = Objects.isNull(latest) ? null : latest.getCreatedAt();
        LocalDateTime completedAt = Objects.isNull(latest) ? null : latest.getOperatedAt();
        List<String> assignees = nodeTasks.stream()
                .map(FlowApprovalTask::getAssigneeMemberId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
        return new WorkflowNodeSnapshot(node.nodeKey(), node.nodeName(), node.nodeType(), status, enteredAt,
                completedAt, assignees, null, nodeOutputSummary(status, latest, taskNodeKeys.contains(node.nodeKey())));
    }

    private List<WorkflowHistoryItem> history(FlowInstance instance, List<FlowApprovalActionLog> logs) {
        WorkflowHistoryItem start = new WorkflowHistoryItem("start_" + instance.getId(), null, "submit",
                "提交审批", actor(instance.getStartedBy(), "发起人"), "业务记录已提交审批。", null,
                true, instance.getTraceId(), "aud_" + instance.getTraceId(), instance.getStartedAt());
        List<WorkflowHistoryItem> actionItems = logs.stream()
                .map(this::historyItem)
                .toList();
        return appendStart(start, actionItems);
    }

    private WorkflowHistoryItem historyItem(FlowApprovalActionLog log) {
        FlowApprovalTask task = taskBaseService.findById(log.getTaskId()).orElse(null);
        String nodeKey = Objects.isNull(task) ? null : task.getNodeId();
        return new WorkflowHistoryItem(String.valueOf(log.getId()), nodeKey, log.getActionCode(),
                actionName(log.getActionCode()), actor(log.getOperatorMemberId(), "审批人"),
                log.getActionReason(), transferTarget(log), true, log.getTraceId(), log.getAuditLogId(),
                log.getOperatedAt());
    }

    private BusinessTargetRef businessTarget(String systemId, FlowInstance instance, FlowDefinition flow) {
        String moduleId = String.valueOf(instance.getModuleId());
        String recordId = String.valueOf(instance.getRecordId());
        return new BusinessTargetRef(moduleId, "module_" + moduleId,
                Objects.isNull(flow) ? "业务模块" : "流程绑定模块", recordId,
                "业务记录 " + recordId, new TargetRef("SYSTEM_RECORD_DETAIL", "SystemModuleRecordDetail",
                Map.of("systemId", systemId, "moduleId", moduleId, "recordId", recordId)),
                Map.of("moduleId", moduleId, "recordId", recordId, "approvalStatus", instance.getStatus()));
    }

    private WorkflowPermissionSnapshot permission(List<ApprovalTaskSnapshot> currentTasks,
                                                  List<FlowApprovalTask> tasks) {
        boolean hasPending = !currentTasks.isEmpty();
        return new WorkflowPermissionSnapshot("eps_flow_" + (tasks.isEmpty() ? "empty" : tasks.get(0).getInstanceId()),
                "perm_live", List.of(), List.of(),
                Map.of("approve", hasPending, "reject", hasPending, "transfer", hasPending, "terminate", false),
                Map.of(), hasPending ? Map.of("terminate", "当前节点不允许终止。")
                : Map.of("approve", "当前没有待审批任务。", "reject", "当前没有待审批任务。",
                "transfer", "当前没有待审批任务。", "terminate", "当前节点不允许终止。"));
    }

    private AuditInfo audit(FlowInstance instance, List<FlowApprovalActionLog> logs) {
        String auditLogId = logs.isEmpty() ? "aud_" + instance.getTraceId() : logs.get(logs.size() - 1).getAuditLogId();
        String updatedBy = logs.isEmpty() ? String.valueOf(instance.getStartedBy())
                : String.valueOf(logs.get(logs.size() - 1).getOperatorMemberId());
        return new AuditInfo(auditLogId, String.valueOf(instance.getStartedBy()), updatedBy,
                logs.stream().map(log -> String.valueOf(log.getId())).toList());
    }

    private ApprovalTaskSnapshot taskSnapshot(FlowApprovalTask task) {
        return new ApprovalTaskSnapshot(String.valueOf(task.getId()), task.getTaskNo(),
                String.valueOf(task.getInstanceId()), task.getNodeId(), task.getNodeName(),
                actor(task.getAssigneeMemberId(), "审批人"), task.getStatus(), task.getDueAt(),
                actions(task.getFieldPermissionSnapshot()), "fps_task_" + task.getId(), task.getTraceId());
    }

    private ApprovalActor actor(Long memberId, String roleName) {
        if (Objects.isNull(memberId)) {
            return new ApprovalActor(null, "未分配", roleName, null);
        }
        PlatMember member = memberBaseService.findById(memberId).orElse(null);
        if (Objects.isNull(member)) {
            return new ApprovalActor(String.valueOf(memberId), "成员 " + memberId, roleName, null);
        }
        return new ApprovalActor(String.valueOf(memberId), member.getMemberName(), roleName,
                departmentName(member.getDeptId()));
    }

    private ApprovalActor transferTarget(FlowApprovalActionLog log) {
        if (!"transfer".equals(log.getActionCode())) {
            return null;
        }
        List<Long> nextTaskIds = readLongIds(log.getNextNodePayload());
        if (nextTaskIds.isEmpty()) {
            return null;
        }
        FlowApprovalTask nextTask = taskBaseService.findById(nextTaskIds.get(0)).orElse(null);
        return Objects.isNull(nextTask) ? null : actor(nextTask.getAssigneeMemberId(), "转交审批人");
    }

    private String departmentName(Long deptId) {
        if (Objects.isNull(deptId)) {
            return null;
        }
        PlatDepartment department = departmentBaseService.findById(deptId).orElse(null);
        return Objects.isNull(department) ? null : department.getDeptName();
    }

    private LocalDateTime updatedAt(FlowInstance instance, List<FlowApprovalActionLog> logs) {
        if (!logs.isEmpty()) {
            return logs.get(logs.size() - 1).getOperatedAt();
        }
        return Objects.nonNull(instance.getEndedAt()) ? instance.getEndedAt() : instance.getStartedAt();
    }

    private String submitIdempotency(List<FlowApprovalTask> tasks) {
        return tasks.stream()
                .map(FlowApprovalTask::getIdempotencyKey)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private List<String> actions(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of("approve", "reject", "transfer");
        }
        try {
            List<String> actions = objectMapper.readValue(json, STRING_LIST_TYPE);
            return actions.isEmpty() ? List.of("approve", "reject", "transfer") : actions;
        } catch (JsonProcessingException ex) {
            return List.of("approve", "reject", "transfer");
        }
    }

    private List<FlowNodeConfigVO> readNodes(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, NODE_LIST_TYPE);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "流程发布快照节点解析失败");
        }
    }

    private List<Long> readLongIds(String json) {
        if (!StringUtils.hasText(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private List<WorkflowHistoryItem> appendStart(WorkflowHistoryItem start, List<WorkflowHistoryItem> actionItems) {
        List<WorkflowHistoryItem> items = new java.util.ArrayList<>();
        items.add(start);
        items.addAll(actionItems);
        return items;
    }

    private String nodeStatus(FlowApprovalTask task) {
        return TASK_PENDING.equals(task.getStatus()) ? "ACTIVE" : "COMPLETED";
    }

    private String nodeSummary(FlowApprovalTask task) {
        return TASK_PENDING.equals(task.getStatus()) ? "等待审批人处理" : "节点已处理：" + task.getActionResult();
    }

    private String nodeOutputSummary(String status, FlowApprovalTask latest, boolean hasTask) {
        if ("ACTIVE".equals(status)) {
            return "等待审批人处理";
        }
        if ("COMPLETED".equals(status) && Objects.nonNull(latest)) {
            return "节点已处理：" + latest.getActionResult();
        }
        return hasTask ? "节点任务已产生" : "等待流程推进";
    }

    private String actionName(String actionCode) {
        if ("reject".equals(actionCode)) {
            return "拒绝审批";
        }
        if ("transfer".equals(actionCode)) {
            return "转交审批";
        }
        if ("submit".equals(actionCode)) {
            return "提交审批";
        }
        return "审批通过";
    }

    private Long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }
}
