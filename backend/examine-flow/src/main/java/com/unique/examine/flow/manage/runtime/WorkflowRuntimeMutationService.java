package com.unique.examine.flow.manage.runtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.CurrentRequestHeaders;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.base.entity.FlowApprovalTask;
import com.unique.examine.flow.base.entity.FlowDefinition;
import com.unique.examine.flow.base.entity.FlowInstance;
import com.unique.examine.flow.base.entity.FlowSnapshot;
import com.unique.examine.flow.base.service.FlowApprovalTaskBaseService;
import com.unique.examine.flow.base.service.FlowDefinitionBaseService;
import com.unique.examine.flow.base.service.FlowInstanceBaseService;
import com.unique.examine.flow.base.service.FlowSnapshotBaseService;
import com.unique.examine.messagelog.base.entity.MessageMessage;
import com.unique.examine.messagelog.base.entity.MessageTodo;
import com.unique.examine.messagelog.base.service.MessageMessageBaseService;
import com.unique.examine.messagelog.base.service.MessageTodoBaseService;
import com.unique.examine.plat.base.entity.PlatAccountMemberBinding;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.service.PlatAccountMemberBindingBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workflow runtime mutation service used by business modules to create real approval instances.
 */
@Service
public class WorkflowRuntimeMutationService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String INSTANCE_RUNNING = "RUNNING";
    private static final String INSTANCE_APPROVED = "APPROVED";
    private static final String INSTANCE_REJECTED = "REJECTED";
    private static final String TASK_PENDING = "PENDING";

    private final FlowDefinitionBaseService flowDefinitionBaseService;
    private final FlowSnapshotBaseService flowSnapshotBaseService;
    private final FlowInstanceBaseService flowInstanceBaseService;
    private final FlowApprovalTaskBaseService flowApprovalTaskBaseService;
    private final MessageTodoBaseService messageTodoBaseService;
    private final MessageMessageBaseService messageMessageBaseService;
    private final PlatAccountMemberBindingBaseService accountMemberBindingBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final ObjectMapper objectMapper;

    public WorkflowRuntimeMutationService(FlowDefinitionBaseService flowDefinitionBaseService,
                                          FlowSnapshotBaseService flowSnapshotBaseService,
                                          FlowInstanceBaseService flowInstanceBaseService,
                                           FlowApprovalTaskBaseService flowApprovalTaskBaseService,
                                           MessageTodoBaseService messageTodoBaseService,
                                           MessageMessageBaseService messageMessageBaseService,
                                           PlatAccountMemberBindingBaseService accountMemberBindingBaseService,
                                           PlatRoleMemberBaseService roleMemberBaseService,
                                           ObjectMapper objectMapper) {
        this.flowDefinitionBaseService = flowDefinitionBaseService;
        this.flowSnapshotBaseService = flowSnapshotBaseService;
        this.flowInstanceBaseService = flowInstanceBaseService;
        this.flowApprovalTaskBaseService = flowApprovalTaskBaseService;
        this.messageTodoBaseService = messageTodoBaseService;
        this.messageMessageBaseService = messageMessageBaseService;
        this.accountMemberBindingBaseService = accountMemberBindingBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Start or reuse an approval workflow instance for one business record.
     *
     * @param systemId system id
     * @param tenantId tenant id
     * @param moduleId module id
     * @param recordId record id
     * @param starterMemberId starter system member id
     * @param idempotencyKey idempotency key
     * @return started or reused instance result
     */
    @Transactional(rollbackFor = Exception.class)
    public WorkflowStartResult startForRecord(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                              Long starterMemberId, String idempotencyKey) {
        WorkflowApprovalHook existing = runningApprovalHook(systemId, tenantId, moduleId, recordId);
        if (existing.visible()) {
            return new WorkflowStartResult(existing.flowInstanceId(), existing.pendingTaskId(), false,
                    "已存在运行中的审批实例，未重复创建。");
        }
        FlowDefinition flow = requirePublishedFlow(systemId, tenantId, moduleId);
        FlowSnapshot snapshot = requireSnapshot(flow);
        NodeRef firstNode = firstRuntimeNode(snapshot);
        Long assigneeMemberId = resolveAssigneeMemberId(systemId, tenantId, starterMemberId, firstNode);
        FlowInstance instance = new FlowInstance();
        instance.setSystemId(systemId);
        instance.setTenantId(tenantId);
        instance.setFlowId(flow.getId());
        instance.setFlowVersion(snapshot.getVersionNo());
        instance.setModuleId(moduleId);
        instance.setRecordId(recordId);
        instance.setStatus(INSTANCE_RUNNING);
        instance.setCurrentNodeIds(toJson(List.of(firstNode.nodeKey())));
        instance.setStartedBy(starterMemberId);
        instance.setStartedAt(LocalDateTime.now());
        instance.setTraceId(RequestContext.current().traceId());
        flowInstanceBaseService.saveEntity(instance);

        FlowApprovalTask task = new FlowApprovalTask();
        task.setInstanceId(instance.getId());
        task.setTaskNo("AT-" + System.currentTimeMillis() + "-" + shortId());
        task.setNodeId(firstNode.nodeKey());
        task.setNodeName(firstNode.nodeName());
        task.setAssigneeMemberId(assigneeMemberId);
        task.setStatus(TASK_PENDING);
        task.setFieldPermissionSnapshot(toJson(List.of("approve", "reject", "transfer")));
        task.setDueAt(LocalDateTime.now().plusHours(8));
        task.setIdempotencyKey(idempotencyKey);
        task.setTraceId(RequestContext.current().traceId());
        task.setCreatedAt(LocalDateTime.now());
        flowApprovalTaskBaseService.saveEntity(task);
        createApprovalTodoAndMessage(systemId, tenantId, moduleId, recordId, assigneeMemberId, instance, task);
        return new WorkflowStartResult(String.valueOf(instance.getId()), String.valueOf(task.getId()), true,
                "审批实例已创建，当前任务已派发。");
    }

    private void createApprovalTodoAndMessage(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                               Long assigneeMemberId, FlowInstance instance,
                                               FlowApprovalTask task) {
        Long receiverAccountId = receiverAccountId(systemId, tenantId, assigneeMemberId);
        Long assigneeId = Objects.nonNull(receiverAccountId)
                ? receiverAccountId
                : CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.isNull(assigneeId)) {
            assigneeId = assigneeMemberId;
        }
        LocalDateTime now = LocalDateTime.now();
        String traceId = RequestContext.current().traceId();

        Map<String, Object> todoTarget = new LinkedHashMap<>();
        todoTarget.put("targetType", "SYSTEM_RECORD_DETAIL");
        todoTarget.put("routeName", "SystemModuleRecordDetail");
        todoTarget.put("requiresSystemSwitch", false);
        todoTarget.put("params", targetParams(systemId, tenantId, moduleId, recordId, instance, task));

        MessageTodo todo = new MessageTodo();
        todo.setScope("system");
        todo.setSystemId(systemId);
        todo.setTenantId(tenantId);
        todo.setTodoType("flow_approval");
        todo.setTitle("待审批记录：" + recordId);
        todo.setSourceName("审批流程：" + task.getNodeName());
        todo.setAssigneeId(assigneeId);
        todo.setStatus("PENDING");
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
        messageTarget.put("targetSystemId", String.valueOf(systemId));
        messageTarget.put("targetTenantId", String.valueOf(tenantId));
        messageTarget.put("requiresSystemSwitch", false);
        messageTarget.put("fallbackAction", "OPEN_SYSTEM_TODO");

        MessageMessage message = new MessageMessage();
        message.setScope("system");
        message.setSystemId(systemId);
        message.setTenantId(tenantId);
        message.setTemplateCode("tpl_flow_approval_pending");
        message.setReceiverId(assigneeId);
        message.setTitle("你有一条新的审批待办");
        message.setContent("记录 " + recordId + " 已提交到 " + task.getNodeName() + "，请及时处理。");
        message.setMessageType("approval");
        message.setTargetPayload(toJson(messageTarget));
        message.setReadStatus(0);
        message.setArchiveStatus(0);
        message.setCreatedAt(now);
        messageMessageBaseService.saveEntity(message);
    }

    private Map<String, String> targetParams(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                             FlowInstance instance, FlowApprovalTask task) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("systemId", String.valueOf(systemId));
        params.put("tenantId", String.valueOf(tenantId));
        params.put("moduleId", String.valueOf(moduleId));
        params.put("recordId", String.valueOf(recordId));
        params.put("flowInstanceId", String.valueOf(instance.getId()));
        params.put("approvalTaskId", String.valueOf(task.getId()));
        params.put("objectTitle", "记录 " + recordId);
        return params;
    }

    /**
     * Resolve current approval sidebar hook for a business record.
     *
     * @param systemId system id
     * @param tenantId tenant id
     * @param moduleId module id
     * @param recordId record id
     * @return approval hook
     */
    public WorkflowApprovalHook approvalHook(Long systemId, Long tenantId, Long moduleId, Long recordId) {
        return approvalHook(systemId, tenantId, moduleId, recordId,
                List.of(INSTANCE_RUNNING, INSTANCE_APPROVED, INSTANCE_REJECTED));
    }

    /**
     * Resolve the running approval hook for duplicate-start protection.
     *
     * @param systemId system id
     * @param tenantId tenant id
     * @param moduleId module id
     * @param recordId record id
     * @return running approval hook
     */
    public WorkflowApprovalHook runningApprovalHook(Long systemId, Long tenantId, Long moduleId, Long recordId) {
        return approvalHook(systemId, tenantId, moduleId, recordId, List.of(INSTANCE_RUNNING));
    }

    private WorkflowApprovalHook approvalHook(Long systemId, Long tenantId, Long moduleId, Long recordId,
                                              List<String> statuses) {
        FlowInstance instance = flowInstanceBaseService.getOne(new LambdaQueryWrapper<FlowInstance>()
                .eq(FlowInstance::getSystemId, systemId)
                .eq(FlowInstance::getTenantId, tenantId)
                .eq(FlowInstance::getModuleId, moduleId)
                .eq(FlowInstance::getRecordId, recordId)
                .in(FlowInstance::getStatus, statuses)
                .orderByDesc(FlowInstance::getId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(instance)) {
            return new WorkflowApprovalHook(false, null, null, null, "NONE", null);
        }
        FlowApprovalTask task = flowApprovalTaskBaseService.getOne(new LambdaQueryWrapper<FlowApprovalTask>()
                .eq(FlowApprovalTask::getInstanceId, instance.getId())
                .eq(FlowApprovalTask::getStatus, TASK_PENDING)
                .orderByAsc(FlowApprovalTask::getId)
                .last("LIMIT 1"), false);
        return new WorkflowApprovalHook(true, String.valueOf(instance.getId()),
                Objects.isNull(task) ? terminalNodeName(instance.getStatus()) : task.getNodeName(),
                Objects.isNull(task) ? null : String.valueOf(task.getId()),
                instance.getStatus(), "approvalTaskDrawer");
    }

    private String terminalNodeName(String status) {
        if (INSTANCE_APPROVED.equals(status)) {
            return "审批已通过";
        }
        if (INSTANCE_REJECTED.equals(status)) {
            return "审批已拒绝";
        }
        return "流程处理中";
    }

    /**
     * Return whether a status is terminal.
     *
     * @param status instance status
     * @return true when terminal
     */
    public boolean terminalStatus(String status) {
        return INSTANCE_APPROVED.equals(status) || INSTANCE_REJECTED.equals(status);
    }

    private FlowDefinition requirePublishedFlow(Long systemId, Long tenantId, Long moduleId) {
        FlowDefinition exact = flowDefinitionBaseService.getOne(new LambdaQueryWrapper<FlowDefinition>()
                .eq(FlowDefinition::getSystemId, systemId)
                .eq(FlowDefinition::getTenantId, tenantId)
                .eq(FlowDefinition::getBoundModuleId, moduleId)
                .eq(FlowDefinition::getStatus, ENABLED)
                .eq(FlowDefinition::getDeleted, DELETED_NO)
                .isNotNull(FlowDefinition::getCurrentVersion)
                .orderByDesc(FlowDefinition::getUpdatedAt)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(exact)) {
            return exact;
        }
        throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "当前模块没有已发布的审批流程");
    }

    private FlowSnapshot requireSnapshot(FlowDefinition flow) {
        FlowSnapshot snapshot = flowSnapshotBaseService.getOne(new LambdaQueryWrapper<FlowSnapshot>()
                .eq(FlowSnapshot::getFlowId, flow.getId())
                .eq(FlowSnapshot::getVersionNo, flow.getCurrentVersion())
                .last("LIMIT 1"), false);
        if (Objects.isNull(snapshot)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程发布快照不存在");
        }
        return snapshot;
    }

    private NodeRef firstRuntimeNode(FlowSnapshot snapshot) {
        try {
            JsonNode nodes = objectMapper.readTree(snapshot.getNodePayload());
            NodeRef fallback = null;
            if (nodes.isArray()) {
                for (JsonNode node : nodes) {
                    String nodeKey = text(node, "nodeKey", "node_start");
                    String nodeType = text(node, "nodeType", "approval");
                    String nodeName = text(node, "nodeName", "审批");
                    JsonNode propertyPayload = node.get("propertyPayload");
                    NodeRef current = new NodeRef(nodeKey, nodeName, nodeType, propertyPayload);
                    if (fallback == null) {
                        fallback = current;
                    }
                    if ("approval".equals(nodeType)) {
                        return current;
                    }
                }
            }
            if (Objects.nonNull(fallback)) {
                return fallback;
            }
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "流程快照节点解析失败");
        }
        throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "流程快照没有可执行节点");
    }

    private Long resolveAssigneeMemberId(Long systemId, Long tenantId, Long starterMemberId, NodeRef node) {
        JsonNode payload = node.propertyPayload();
        String assigneeType = text(payload, "assigneeType", "").toUpperCase();
        if ("MEMBER".equals(assigneeType)) {
            Long memberId = firstBoundMember(systemId, tenantId, payload, "assigneeIds", "memberIds",
                    "assigneeMemberIds", "systemMemberIds");
            if (Objects.nonNull(memberId)) {
                return memberId;
            }
        }
        if ("ROLE".equals(assigneeType)) {
            Long memberId = firstRoleAssignee(systemId, tenantId, starterMemberId, payload, "assigneeIds",
                    "roleIds", "assigneeRoleIds", "assigneeTargets");
            if (Objects.nonNull(memberId)) {
                return memberId;
            }
        }
        Long explicitMemberId = firstBoundMember(systemId, tenantId, payload, "assigneeMemberId",
                "systemMemberId", "memberId");
        return Objects.nonNull(explicitMemberId) ? explicitMemberId : starterMemberId;
    }

    private Long firstBoundMember(Long systemId, Long tenantId, JsonNode payload, String... fields) {
        for (String field : fields) {
            for (Long memberId : longValues(payload, field)) {
                if (isBoundMember(systemId, tenantId, memberId)) {
                    return memberId;
                }
            }
        }
        return null;
    }

    private Long firstRoleAssignee(Long systemId, Long tenantId, Long starterMemberId, JsonNode payload,
                                   String... fields) {
        Long fallback = null;
        for (String field : fields) {
            for (Long roleId : longValues(payload, field)) {
                List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                        .eq(PlatRoleMember::getSystemId, systemId)
                        .eq(PlatRoleMember::getTenantId, tenantId)
                        .eq(PlatRoleMember::getRoleId, roleId)
                        .isNotNull(PlatRoleMember::getSystemMemberId)
                        .orderByAsc(PlatRoleMember::getId));
                for (PlatRoleMember roleMember : roleMembers) {
                    Long memberId = roleMember.getSystemMemberId();
                    if (!isBoundMember(systemId, tenantId, memberId)) {
                        continue;
                    }
                    if (!Objects.equals(memberId, starterMemberId)) {
                        return memberId;
                    }
                    fallback = memberId;
                }
            }
        }
        return fallback;
    }

    private boolean isBoundMember(Long systemId, Long tenantId, Long memberId) {
        if (Objects.isNull(memberId)) {
            return false;
        }
        return accountMemberBindingBaseService.count(new LambdaQueryWrapper<PlatAccountMemberBinding>()
                .eq(PlatAccountMemberBinding::getSystemId, systemId)
                .eq(PlatAccountMemberBinding::getTenantId, tenantId)
                .eq(PlatAccountMemberBinding::getSystemMemberId, memberId)
                .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)) > 0;
    }

    private Long receiverAccountId(Long systemId, Long tenantId, Long memberId) {
        if (Objects.isNull(memberId)) {
            return null;
        }
        PlatAccountMemberBinding binding = accountMemberBindingBaseService.getOne(
                new LambdaQueryWrapper<PlatAccountMemberBinding>()
                        .eq(PlatAccountMemberBinding::getSystemId, systemId)
                        .eq(PlatAccountMemberBinding::getTenantId, tenantId)
                        .eq(PlatAccountMemberBinding::getSystemMemberId, memberId)
                        .eq(PlatAccountMemberBinding::getBindingStatus, ENABLED)
                        .orderByAsc(PlatAccountMemberBinding::getId)
                        .last("LIMIT 1"), false);
        return Objects.isNull(binding) ? null : binding.getAccountId();
    }

    private List<Long> longValues(JsonNode payload, String field) {
        if (Objects.isNull(payload) || payload.isNull() || !payload.has(field)) {
            return List.of();
        }
        JsonNode value = payload.get(field);
        if (value.isArray()) {
            List<Long> values = new ArrayList<>();
            for (JsonNode item : value) {
                Long parsed = asLong(item);
                if (Objects.nonNull(parsed)) {
                    values.add(parsed);
                }
            }
            return values;
        }
        Long parsed = asLong(value);
        return Objects.isNull(parsed) ? List.of() : List.of(parsed);
    }

    private Long asLong(JsonNode value) {
        if (Objects.isNull(value) || value.isNull()) {
            return null;
        }
        if (value.isIntegralNumber()) {
            return value.asLong();
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            try {
                return Long.parseLong(value.asText());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String text(JsonNode node, String field, String fallback) {
        if (Objects.isNull(node) || node.isNull()) {
            return fallback;
        }
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isTextual() && !value.asText().isBlank()
                ? value.asText()
                : fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "流程运行态序列化失败");
        }
    }

    private String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private record NodeRef(String nodeKey, String nodeName, String nodeType, JsonNode propertyPayload) {
    }

    /**
     * Start workflow result.
     *
     * @param flowInstanceId flow instance id
     * @param pendingTaskId pending task id
     * @param created whether a new instance was created
     * @param message result message
     */
    public record WorkflowStartResult(String flowInstanceId, String pendingTaskId, boolean created, String message) {
    }

    /**
     * Approval sidebar hook for business record detail.
     *
     * @param visible whether approval sidebar is visible
     * @param flowInstanceId flow instance id
     * @param currentNodeName current node name
     * @param pendingTaskId pending approval task id
     * @param status instance status
     * @param actionTarget frontend action target
     */
    public record WorkflowApprovalHook(boolean visible, String flowInstanceId, String currentNodeName,
                                       String pendingTaskId, String status, String actionTarget) {
    }
}
