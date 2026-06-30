package com.unique.examine.flow.manage.approval;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.CurrentRequestHeaders;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.base.entity.FlowApprovalActionLog;
import com.unique.examine.flow.base.entity.FlowApprovalTask;
import com.unique.examine.flow.base.entity.FlowInstance;
import com.unique.examine.flow.base.service.FlowApprovalActionLogBaseService;
import com.unique.examine.flow.base.service.FlowApprovalTaskBaseService;
import com.unique.examine.flow.base.service.FlowInstanceBaseService;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActionRequest;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActionResult;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActor;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalTaskSnapshot;
import com.unique.examine.messagelog.base.entity.MessageMessage;
import com.unique.examine.messagelog.base.entity.MessageTodo;
import com.unique.examine.messagelog.base.service.MessageMessageBaseService;
import com.unique.examine.messagelog.base.service.MessageTodoBaseService;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatDepartment;
import com.unique.examine.plat.base.entity.PlatMember;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatDepartmentBaseService;
import com.unique.examine.plat.base.service.PlatMemberBaseService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 审批任务动作服务，负责真实更新审批任务、流程实例和动作日志。
 */
@Service
public class ApprovalService {

    private static final String TASK_PENDING = "PENDING";
    private static final String TASK_APPROVED = "APPROVED";
    private static final String TASK_REJECTED = "REJECTED";
    private static final String TASK_TRANSFERRED = "TRANSFERRED";
    private static final String TODO_HANDLED = "HANDLED";
    private static final String INSTANCE_RUNNING = "RUNNING";
    private static final String INSTANCE_APPROVED = "APPROVED";
    private static final String INSTANCE_REJECTED = "REJECTED";
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final FlowApprovalTaskBaseService taskBaseService;
    private final FlowInstanceBaseService instanceBaseService;
    private final FlowApprovalActionLogBaseService actionLogBaseService;
    private final PlatMemberBaseService memberBaseService;
    private final PlatDepartmentBaseService departmentBaseService;
    private final PlatAccountMemberBindingBaseService accountMemberBindingBaseService;
    private final MessageTodoBaseService messageTodoBaseService;
    private final MessageMessageBaseService messageMessageBaseService;
    private final ObjectMapper objectMapper;

    public ApprovalService(FlowApprovalTaskBaseService taskBaseService,
                           FlowInstanceBaseService instanceBaseService,
                           FlowApprovalActionLogBaseService actionLogBaseService,
                           PlatMemberBaseService memberBaseService,
                           PlatDepartmentBaseService departmentBaseService,
                           PlatAccountMemberBindingBaseService accountMemberBindingBaseService,
                           MessageTodoBaseService messageTodoBaseService,
                           MessageMessageBaseService messageMessageBaseService,
                           ObjectMapper objectMapper) {
        this.taskBaseService = taskBaseService;
        this.instanceBaseService = instanceBaseService;
        this.actionLogBaseService = actionLogBaseService;
        this.memberBaseService = memberBaseService;
        this.departmentBaseService = departmentBaseService;
        this.accountMemberBindingBaseService = accountMemberBindingBaseService;
        this.messageTodoBaseService = messageTodoBaseService;
        this.messageMessageBaseService = messageMessageBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * 审批通过当前任务。
     *
     * @param systemId 系统 ID
     * @param taskId 审批任务 ID
     * @param request 审批动作请求
     * @return 审批通过结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalActionResult approve(String systemId, String taskId, ApprovalActionRequest request) {
        return execute(systemId, taskId, "approve", request);
    }

    /**
     * 拒绝当前任务。
     *
     * @param systemId 系统 ID
     * @param taskId 审批任务 ID
     * @param request 审批动作请求
     * @return 拒绝结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalActionResult reject(String systemId, String taskId, ApprovalActionRequest request) {
        return execute(systemId, taskId, "reject", request);
    }

    /**
     * 转交当前任务给目标成员。
     *
     * @param systemId 系统 ID
     * @param taskId 审批任务 ID
     * @param request 审批动作请求
     * @return 转交结果
     */
    @Transactional(rollbackFor = Exception.class)
    public ApprovalActionResult transfer(String systemId, String taskId, ApprovalActionRequest request) {
        return execute(systemId, taskId, "transfer", request);
    }

    /**
     * 查询当前待处理任务快照。
     *
     * @param instanceId 流程实例 ID
     * @return 审批任务快照
     */
    public ApprovalTaskSnapshot currentTask(String instanceId) {
        Long parsedInstanceId = parseLong(instanceId, "流程实例 ID 不合法");
        FlowApprovalTask task = taskBaseService.getOne(new LambdaQueryWrapper<FlowApprovalTask>()
                .eq(FlowApprovalTask::getInstanceId, parsedInstanceId)
                .eq(FlowApprovalTask::getStatus, TASK_PENDING)
                .orderByAsc(FlowApprovalTask::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(task)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "当前流程实例没有待处理任务");
        }
        return taskSnapshot(task);
    }

    private ApprovalActionResult execute(String systemId, String taskId, String actionCode,
                                         ApprovalActionRequest request) {
        RequestContext context = RequestContext.current();
        FlowApprovalTask task = requireTask(taskId);
        FlowInstance instance = requireInstance(systemId, task.getInstanceId());
        requireCurrentAssignee(instance, task);
        String idempotencyKey = idempotencyKey(task, actionCode, request);
        FlowApprovalActionLog duplicate = duplicateLog(task, actionCode, idempotencyKey);
        if (Objects.nonNull(duplicate)) {
            closeRelatedApprovalTodo(instance, task);
            return duplicateView(systemId, task, instance, duplicate, actionCode, idempotencyKey);
        }
        if (!TASK_PENDING.equals(task.getStatus())) {
            throw new BusinessException(CommonErrorCode.TASK_STATE_CONFLICT, "审批任务已处理，不能重复推进");
        }

        LocalDateTime now = LocalDateTime.now();
        ApprovalActor transferTarget = transferTarget(actionCode, request);
        FlowApprovalTask nextTask = applyTaskAndInstance(task, instance, actionCode, request, transferTarget, now,
                idempotencyKey, context.traceId());
        FlowApprovalActionLog actionLog = saveActionLog(task, instance, nextTask, actionCode, request,
                idempotencyKey, context, now);
        closeRelatedApprovalTodo(instance, task);
        if ("transfer".equals(actionCode) && Objects.nonNull(nextTask)) {
            createTransferredApprovalTodoAndMessage(instance, nextTask, transferTarget, context.traceId(), now);
        }
        List<ApprovalTaskSnapshot> nextTasks = Objects.isNull(nextTask) ? List.of() : List.of(taskSnapshot(nextTask));
        return new ApprovalActionResult(String.valueOf(actionLog.getId()), systemId, String.valueOf(task.getId()),
                String.valueOf(instance.getId()), actionCode, task.getStatus(), instance.getStatus(), true,
                false, idempotencyKey, String.valueOf(actionLog.getId()), actionLog.getAuditLogId(),
                context.traceId(), actionMessage(actionCode, transferTarget), actionReason(actionCode, request),
                transferTarget, nextTasks, now);
    }

    private FlowApprovalTask applyTaskAndInstance(FlowApprovalTask task, FlowInstance instance, String actionCode,
                                                  ApprovalActionRequest request, ApprovalActor transferTarget,
                                                  LocalDateTime now, String idempotencyKey, String traceId) {
        task.setStatus(taskStatus(actionCode));
        task.setActionResult(taskStatus(actionCode));
        task.setActionReason(actionReason(actionCode, request));
        task.setOperatedAt(now);
        task.setIdempotencyKey(idempotencyKey);
        taskBaseService.updateById(task);

        if ("transfer".equals(actionCode)) {
            FlowApprovalTask nextTask = new FlowApprovalTask();
            nextTask.setInstanceId(instance.getId());
            nextTask.setTaskNo("AT-" + System.currentTimeMillis());
            nextTask.setNodeId(task.getNodeId());
            nextTask.setNodeName(task.getNodeName());
            nextTask.setAssigneeMemberId(parseLong(transferTarget.memberId(), "转交目标成员 ID 不合法"));
            nextTask.setStatus(TASK_PENDING);
            nextTask.setFieldPermissionSnapshot(task.getFieldPermissionSnapshot());
            nextTask.setDueAt(now.plusHours(8));
            nextTask.setIdempotencyKey(idempotencyKey);
            nextTask.setTraceId(traceId);
            nextTask.setCreatedAt(now);
            taskBaseService.saveEntity(nextTask);
            instance.setStatus(INSTANCE_RUNNING);
            instance.setCurrentNodeIds(toJson(List.of(task.getNodeId())));
            instanceBaseService.updateById(instance);
            return nextTask;
        }

        instance.setStatus("reject".equals(actionCode) ? INSTANCE_REJECTED : INSTANCE_APPROVED);
        instance.setCurrentNodeIds(toJson(List.of()));
        instance.setEndedAt(now);
        instanceBaseService.updateById(instance);
        return null;
    }

    private void closeRelatedApprovalTodo(FlowInstance instance, FlowApprovalTask task) {
        List<MessageTodo> todos = messageTodoBaseService.list(new LambdaQueryWrapper<MessageTodo>()
                .eq(MessageTodo::getScope, "system")
                .eq(MessageTodo::getSystemId, instance.getSystemId())
                .eq(MessageTodo::getTenantId, instance.getTenantId())
                .eq(MessageTodo::getTodoType, "flow_approval")
                .eq(MessageTodo::getStatus, TASK_PENDING)
                .like(MessageTodo::getTargetPayload, "approvalTaskId")
                .like(MessageTodo::getTargetPayload, String.valueOf(task.getId())));
        for (MessageTodo todo : todos) {
            todo.setStatus(TODO_HANDLED);
            messageTodoBaseService.updateById(todo);
        }
    }

    private FlowApprovalActionLog saveActionLog(FlowApprovalTask task, FlowInstance instance, FlowApprovalTask nextTask,
                                                String actionCode, ApprovalActionRequest request,
                                                String idempotencyKey, RequestContext context, LocalDateTime now) {
        FlowApprovalActionLog actionLog = new FlowApprovalActionLog();
        actionLog.setInstanceId(instance.getId());
        actionLog.setTaskId(task.getId());
        actionLog.setActionCode(actionCode);
        actionLog.setActionResult(task.getActionResult());
        actionLog.setActionReason(actionReason(actionCode, request));
        actionLog.setOperatorMemberId(task.getAssigneeMemberId());
        actionLog.setNextNodePayload(Objects.isNull(nextTask) ? toJson(List.of()) : toJson(List.of(nextTask.getId())));
        actionLog.setIdempotencyKey(idempotencyKey);
        actionLog.setTraceId(context.traceId());
        actionLog.setAuditLogId(auditLogId(context));
        actionLog.setOperatedAt(now);
        actionLogBaseService.saveEntity(actionLog);
        return actionLog;
    }

    private void createTransferredApprovalTodoAndMessage(FlowInstance instance, FlowApprovalTask task,
                                                         ApprovalActor transferTarget, String traceId,
                                                         LocalDateTime now) {
        Long receiverAccountId = receiverAccountId(instance, task.getAssigneeMemberId());
        Long assigneeId = Objects.nonNull(receiverAccountId) ? receiverAccountId : task.getAssigneeMemberId();
        Map<String, Object> todoTarget = new LinkedHashMap<>();
        todoTarget.put("targetType", "SYSTEM_RECORD_DETAIL");
        todoTarget.put("routeName", "SystemModuleRecordDetail");
        todoTarget.put("requiresSystemSwitch", false);
        todoTarget.put("params", targetParams(instance, task));

        MessageTodo todo = new MessageTodo();
        todo.setScope("system");
        todo.setSystemId(instance.getSystemId());
        todo.setTenantId(instance.getTenantId());
        todo.setTodoType("flow_approval");
        todo.setTitle("待审批记录：" + instance.getRecordId());
        todo.setSourceName("审批流程：" + task.getNodeName());
        todo.setAssigneeId(assigneeId);
        todo.setStatus(TASK_PENDING);
        todo.setPriority("HIGH");
        todo.setDueAt(task.getDueAt());
        todo.setTargetPayload(toJson(todoTarget));
        todo.setPrimaryAction(toJson(Map.of("actionCode", "approve")));
        todo.setTraceId(traceId);
        todo.setCreatedAt(now);
        messageTodoBaseService.saveEntity(todo);

        Map<String, Object> messageTarget = new LinkedHashMap<>();
        messageTarget.put("scope", "system");
        messageTarget.put("targetType", "approval_task");
        messageTarget.put("targetId", String.valueOf(task.getId()));
        messageTarget.put("targetSystemId", String.valueOf(instance.getSystemId()));
        messageTarget.put("targetTenantId", String.valueOf(instance.getTenantId()));
        messageTarget.put("requiresSystemSwitch", false);
        messageTarget.put("fallbackAction", "OPEN_SYSTEM_TODO");

        MessageMessage message = new MessageMessage();
        message.setScope("system");
        message.setSystemId(instance.getSystemId());
        message.setTenantId(instance.getTenantId());
        message.setTemplateCode("tpl_flow_approval_transferred");
        message.setReceiverId(assigneeId);
        message.setTitle("你收到一条转交审批");
        message.setContent("记录 " + instance.getRecordId() + " 已转交给 "
                + transferTarget.memberName() + "，请及时处理。");
        message.setMessageType("approval");
        message.setTargetPayload(toJson(messageTarget));
        message.setReadStatus(0);
        message.setArchiveStatus(0);
        message.setCreatedAt(now);
        messageMessageBaseService.saveEntity(message);
    }

    private Map<String, String> targetParams(FlowInstance instance, FlowApprovalTask task) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("systemId", String.valueOf(instance.getSystemId()));
        params.put("tenantId", String.valueOf(instance.getTenantId()));
        params.put("moduleId", String.valueOf(instance.getModuleId()));
        params.put("recordId", String.valueOf(instance.getRecordId()));
        params.put("flowInstanceId", String.valueOf(instance.getId()));
        params.put("approvalTaskId", String.valueOf(task.getId()));
        params.put("objectTitle", "记录 " + instance.getRecordId());
        return params;
    }

    private Long receiverAccountId(FlowInstance instance, Long systemMemberId) {
        if (Objects.isNull(systemMemberId)) {
            return null;
        }
        PlatAccountMemberBinding binding = accountMemberBindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getSystemId, instance.getSystemId())
                        .eq(PlatAccountMemberBinding::getTenantId, instance.getTenantId())
                        .eq(PlatAccountMemberBinding::getSystemMemberId, systemMemberId)
                        .eq(PlatAccountMemberBinding::getBindingStatus, 1)
                        .last("LIMIT 1"), false);
        return Objects.isNull(binding) ? null : binding.getAccountId();
    }

    private FlowApprovalTask requireTask(String taskId) {
        FlowApprovalTask task = taskBaseService.findById(parseLong(taskId, "审批任务 ID 不合法")).orElse(null);
        if (Objects.isNull(task)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "审批任务不存在");
        }
        return task;
    }

    private FlowInstance requireInstance(String systemId, Long instanceId) {
        FlowInstance instance = instanceBaseService.findById(instanceId).orElse(null);
        if (Objects.isNull(instance) || !Objects.equals(instance.getSystemId(), parseLong(systemId, "系统 ID 不合法"))) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程实例不存在或不属于当前系统");
        }
        return instance;
    }

    private void requireCurrentAssignee(FlowInstance instance, FlowApprovalTask task) {
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.isNull(accountId)) {
            throw new BusinessException(CommonErrorCode.AUTH_UNAUTHORIZED);
        }
        PlatAccountMemberBinding binding = accountMemberBindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getAccountId, accountId)
                        .eq(PlatAccountMemberBinding::getSystemId, instance.getSystemId())
                        .eq(PlatAccountMemberBinding::getTenantId, instance.getTenantId())
                        .eq(PlatAccountMemberBinding::getSystemMemberId, task.getAssigneeMemberId())
                        .eq(PlatAccountMemberBinding::getBindingStatus, 1)
                        .last("LIMIT 1"), false);
        if (Objects.isNull(binding)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "当前账号不是该审批任务处理人");
        }
    }

    private FlowApprovalActionLog duplicateLog(FlowApprovalTask task, String actionCode, String idempotencyKey) {
        return actionLogBaseService.getOne(new LambdaQueryWrapper<FlowApprovalActionLog>()
                .eq(FlowApprovalActionLog::getTaskId, task.getId())
                .eq(FlowApprovalActionLog::getActionCode, actionCode)
                .eq(FlowApprovalActionLog::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"), false);
    }

    private ApprovalActionResult duplicateView(String systemId, FlowApprovalTask task, FlowInstance instance,
                                               FlowApprovalActionLog actionLog, String actionCode,
                                               String idempotencyKey) {
        return new ApprovalActionResult(String.valueOf(actionLog.getId()), systemId, String.valueOf(task.getId()),
                String.valueOf(instance.getId()), actionCode, task.getStatus(), instance.getStatus(), false,
                true, idempotencyKey, String.valueOf(actionLog.getId()), actionLog.getAuditLogId(),
                actionLog.getTraceId(), "重复请求已按幂等键返回原处理结果，流程未再次推进。",
                actionLog.getActionReason(), null, List.of(), actionLog.getOperatedAt());
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

    private String departmentName(Long deptId) {
        if (Objects.isNull(deptId)) {
            return null;
        }
        PlatDepartment department = departmentBaseService.findById(deptId).orElse(null);
        return Objects.isNull(department) ? null : department.getDeptName();
    }

    private ApprovalActor transferTarget(String actionCode, ApprovalActionRequest request) {
        if (!"transfer".equals(actionCode)) {
            return null;
        }
        if (Objects.isNull(request) || !StringUtils.hasText(request.transferTargetId())) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "转交审批必须选择目标成员");
        }
        Long targetMemberId = parseLong(request.transferTargetId(), "转交目标成员 ID 不合法");
        PlatMember member = memberBaseService.findById(targetMemberId).orElse(null);
        String memberName = Objects.nonNull(member) ? member.getMemberName()
                : safeText(request.transferTargetName(), "成员 " + targetMemberId);
        String departmentName = Objects.isNull(member) ? null : departmentName(member.getDeptId());
        return new ApprovalActor(String.valueOf(targetMemberId), memberName, "转交审批人", departmentName);
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

    private String taskStatus(String actionCode) {
        if ("reject".equals(actionCode)) {
            return TASK_REJECTED;
        }
        if ("transfer".equals(actionCode)) {
            return TASK_TRANSFERRED;
        }
        return TASK_APPROVED;
    }

    private String actionMessage(String actionCode, ApprovalActor target) {
        if ("reject".equals(actionCode)) {
            return "审批已拒绝，原因已写入审批历史并关闭当前流程。";
        }
        if ("transfer".equals(actionCode)) {
            return "审批已转交给 " + target.memberName() + "。";
        }
        return "审批已通过，当前流程已完成。";
    }

    private String actionReason(String actionCode, ApprovalActionRequest request) {
        if ("approve".equals(actionCode)) {
            return safeText(Objects.isNull(request) ? null : request.comment(), "同意");
        }
        return safeText(Objects.isNull(request) ? null : request.reason(),
                "reject".equals(actionCode) ? "拒绝审批" : "转交审批");
    }

    private String idempotencyKey(FlowApprovalTask task, String actionCode, ApprovalActionRequest request) {
        return safeText(Objects.isNull(request) ? null : request.idempotencyKey(),
                "idem_" + task.getId() + "_" + actionCode);
    }

    private Long parseLong(String value, String message) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, message);
        }
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "审批流运行态序列化失败");
        }
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }
}
