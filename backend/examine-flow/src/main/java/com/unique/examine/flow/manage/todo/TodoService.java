package com.unique.examine.flow.manage.todo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.context.CurrentRequestHeaders;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActionRequest;
import com.unique.examine.flow.manage.approval.ApprovalModels.ApprovalActionResult;
import com.unique.examine.flow.manage.approval.ApprovalService;
import com.unique.examine.flow.manage.todo.TodoModels.TodoAction;
import com.unique.examine.flow.manage.todo.TodoModels.TodoActionRequest;
import com.unique.examine.flow.manage.todo.TodoModels.TodoActionResult;
import com.unique.examine.flow.manage.todo.TodoModels.TodoRowView;
import com.unique.examine.flow.manage.todo.TodoModels.TodoSearchRequest;
import com.unique.examine.flow.manage.todo.TodoModels.TodoSearchResult;
import com.unique.examine.flow.manage.todo.TodoModels.TodoTarget;
import com.unique.examine.flow.manage.todo.TodoModels.TodoTypeNode;
import com.unique.examine.messagelog.base.entity.MessageTodo;
import com.unique.examine.messagelog.base.service.MessageTodoBaseService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Todo workspace service backed by persisted todo rows.
 */
@Service
public class TodoService {

    private static final String SCOPE_PLATFORM = "platform";
    private static final String SCOPE_SYSTEM = "system";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_HANDLED = "HANDLED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";

    private final MessageTodoBaseService todoBaseService;
    private final ApprovalService approvalService;
    private final ObjectMapper objectMapper;

    public TodoService(MessageTodoBaseService todoBaseService, ApprovalService approvalService,
                       ObjectMapper objectMapper) {
        this.todoBaseService = todoBaseService;
        this.approvalService = approvalService;
        this.objectMapper = objectMapper;
    }

    /**
     * Search platform todos.
     *
     * @param pageRequest page request
     * @param request query request
     * @return platform todo type tree and paged rows
     */
    public TodoSearchResult searchPlatform(PageRequest pageRequest, TodoSearchRequest request) {
        return search(SCOPE_PLATFORM, null, pageRequest, request);
    }

    /**
     * Search system todos.
     *
     * @param systemId system id
     * @param pageRequest page request
     * @param request query request
     * @return system todo type tree and paged rows
     */
    public TodoSearchResult searchSystem(String systemId, PageRequest pageRequest, TodoSearchRequest request) {
        return search(SCOPE_SYSTEM, requireSystemId(systemId), pageRequest, request);
    }

    /**
     * Execute a system todo action.
     *
     * @param systemId system id
     * @param todoId todo id
     * @param actionCode action code
     * @param request action request
     * @return action result
     */
    public TodoActionResult executeSystemAction(String systemId, String todoId, String actionCode,
                                                TodoActionRequest request) {
        Long parsedSystemId = requireSystemId(systemId);
        Long parsedTodoId = parseLong(todoId);
        if (Objects.isNull(parsedTodoId)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Todo id must be numeric.");
        }
        MessageTodo todo = todoBaseService.getOne(new LambdaQueryWrapper<MessageTodo>()
                .eq(MessageTodo::getId, parsedTodoId)
                .eq(MessageTodo::getScope, SCOPE_SYSTEM)
                .eq(MessageTodo::getSystemId, parsedSystemId)
                .last("LIMIT 1"), false);
        if (Objects.isNull(todo)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "Todo does not exist.");
        }
        Long currentAccountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (!Objects.equals(todo.getAssigneeId(), currentAccountId)) {
            throw new BusinessException(CommonErrorCode.PERMISSION_DENIED, "只能处理分配给当前账号的待办");
        }
        TodoTarget target = target(todo);
        if (isApprovalAction(actionCode)) {
            return executeApprovalAction(systemId, todo, target, actionCode, request);
        }
        todo.setStatus(actionStatus(actionCode));
        todoBaseService.updateById(todo);
        RequestContext context = RequestContext.current();
        return new TodoActionResult(String.valueOf(todo.getId()), safeText(actionCode, "open"), todo.getStatus(),
                actionMessage(actionCode), context.traceId(), "aud_" + shortTrace(context.traceId()),
                target, LocalDateTime.now());
    }

    private TodoActionResult executeApprovalAction(String systemId, MessageTodo todo, TodoTarget target,
                                                   String actionCode, TodoActionRequest request) {
        String approvalTaskId = target.params().get("approvalTaskId");
        if (!StringUtils.hasText(approvalTaskId)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "审批待办缺少审批任务 ID，不能推进流程");
        }
        ApprovalActionResult approvalResult = switch (safeText(actionCode, "approve")) {
            case "reject" -> approvalService.reject(systemId, approvalTaskId, toApprovalRequest(actionCode, request));
            case "transfer" -> approvalService.transfer(systemId, approvalTaskId, toApprovalRequest(actionCode, request));
            default -> approvalService.approve(systemId, approvalTaskId, toApprovalRequest(actionCode, request));
        };
        todo.setStatus(STATUS_HANDLED);
        todoBaseService.updateById(todo);
        return new TodoActionResult(String.valueOf(todo.getId()), actionCode, todo.getStatus(),
                approvalResult.message(), approvalResult.traceId(), approvalResult.auditLogId(),
                target, approvalResult.operatedAt());
    }

    private ApprovalActionRequest toApprovalRequest(String actionCode, TodoActionRequest request) {
        String defaultReason = "reject".equals(actionCode) ? "拒绝审批" : "转交审批";
        return new ApprovalActionRequest(
                Objects.isNull(request) ? null : request.idempotencyKey(),
                Objects.isNull(request) ? "同意" : safeText(request.comment(), "同意"),
                Objects.isNull(request) ? defaultReason : safeText(request.reason(), defaultReason),
                Objects.isNull(request) ? null : request.transferTargetId(),
                Objects.isNull(request) ? null : request.transferTargetName(),
                Map.of(),
                null
        );
    }

    private TodoSearchResult search(String scope, Long systemId, PageRequest pageRequest, TodoSearchRequest request) {
        RequestContext context = RequestContext.current();
        int pageNo = pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
        int pageSize = pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
        int offset = (pageNo - 1) * pageSize;
        String selectedType = safeText(request == null ? null : request.typeCode(), "all");
        long total = todoBaseService.count(queryWrapper(scope, systemId, request));
        List<TodoRowView> rows = todoBaseService.list(queryWrapper(scope, systemId, request)
                        .orderByAsc(MessageTodo::getDueAt)
                        .orderByDesc(MessageTodo::getCreatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toRow)
                .toList();
        Long tenantId = rows.isEmpty() ? null : parseLong(rows.get(0).target().params().get("tenantId"));
        return new TodoSearchResult(scope, stringId(systemId), stringId(tenantId), selectedType,
                typeTree(scope, systemId), new PageResult<>(rows, pageNo, pageSize, total,
                offset + rows.size() < total), context.traceId());
    }

    private LambdaQueryWrapper<MessageTodo> queryWrapper(String scope, Long systemId, TodoSearchRequest request) {
        LambdaQueryWrapper<MessageTodo> wrapper = new LambdaQueryWrapper<MessageTodo>()
                .eq(MessageTodo::getScope, scope);
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.nonNull(accountId)) {
            wrapper.eq(MessageTodo::getAssigneeId, accountId);
        }
        if (Objects.nonNull(systemId)) {
            wrapper.eq(MessageTodo::getSystemId, systemId);
        }
        if (Objects.nonNull(request)) {
            if (StringUtils.hasText(request.typeCode()) && !"all".equalsIgnoreCase(request.typeCode())) {
                wrapper.eq(MessageTodo::getTodoType, request.typeCode());
            }
            if (StringUtils.hasText(request.status())) {
                wrapper.eq(MessageTodo::getStatus, request.status());
            }
            if (StringUtils.hasText(request.priority())) {
                wrapper.eq(MessageTodo::getPriority, request.priority());
            }
            Long assigneeId = parseLong(request.assigneeId());
            if (Objects.nonNull(assigneeId)) {
                wrapper.eq(MessageTodo::getAssigneeId, assigneeId);
            }
            if (StringUtils.hasText(request.keyword())) {
                wrapper.and(value -> value.like(MessageTodo::getTitle, request.keyword())
                        .or().like(MessageTodo::getSourceName, request.keyword()));
            }
        }
        return wrapper;
    }

    private List<TodoTypeNode> typeTree(String scope, Long systemId) {
        List<MessageTodo> todos = todoBaseService.list(typeTreeWrapper(scope, systemId));
        Map<String, Long> counts = todos.stream()
                .collect(Collectors.groupingBy(MessageTodo::getTodoType, LinkedHashMap::new, Collectors.counting()));
        if (SCOPE_PLATFORM.equals(scope)) {
            return List.of(
                    node("platform_task", "平台任务", counts, "clipboard-list",
                            List.of(leaf("ops_health", "体检与运维", counts, "activity"),
                                    leaf("secret_rotation", "密钥轮换", counts, "key-round"))),
                    leaf("platform_authorization", "平台授权", counts, "shield-check")
            );
        }
        return List.of(
                node("approval", "审批待办", counts, "user-check",
                        List.of(leaf("flow_approval", "流程审批", counts, "git-pull-request"),
                                leaf("approval_timeout", "超时提醒", counts, "bell-ring"))),
                node("work", "工作待办", counts, "briefcase",
                        List.of(leaf("today", "今日需处理", counts, "calendar-check"),
                                leaf("reminder", "提醒待办", counts, "alarm-clock")))
        );
    }

    private LambdaQueryWrapper<MessageTodo> typeTreeWrapper(String scope, Long systemId) {
        LambdaQueryWrapper<MessageTodo> wrapper = new LambdaQueryWrapper<MessageTodo>()
                .eq(MessageTodo::getScope, scope);
        Long accountId = CurrentRequestHeaders.currentAccountIdOrNull();
        if (Objects.nonNull(accountId)) {
            wrapper.eq(MessageTodo::getAssigneeId, accountId);
        }
        if (Objects.nonNull(systemId)) {
            wrapper.eq(MessageTodo::getSystemId, systemId);
        }
        return wrapper;
    }

    private TodoTypeNode node(String typeCode, String typeName, Map<String, Long> counts, String icon,
                              List<TodoTypeNode> children) {
        int count = children.stream().mapToInt(TodoTypeNode::count).sum()
                + Math.toIntExact(counts.getOrDefault(typeCode, 0L));
        return new TodoTypeNode(typeCode, typeName, count, icon, children);
    }

    private TodoTypeNode leaf(String typeCode, String typeName, Map<String, Long> counts, String icon) {
        return new TodoTypeNode(typeCode, typeName, Math.toIntExact(counts.getOrDefault(typeCode, 0L)),
                icon, List.of());
    }

    private TodoRowView toRow(MessageTodo todo) {
        TodoTarget target = target(todo);
        String primaryActionCode = primaryActionCode(todo.getPrimaryAction());
        TodoAction primaryAction = action(primaryActionCode, true, null);
        return new TodoRowView(
                String.valueOf(todo.getId()),
                todo.getScope(),
                todo.getTodoType(),
                todo.getTitle(),
                todo.getSourceName(),
                target.params().get("moduleId"),
                target.params().get("objectTitle"),
                stringId(todo.getAssigneeId()),
                todo.getDueAt(),
                todo.getStatus(),
                todo.getPriority(),
                target,
                primaryAction,
                actionPermissions(todo),
                safeText(todo.getTraceId(), RequestContext.current().traceId())
        );
    }

    private TodoTarget target(MessageTodo todo) {
        JsonNode node = readPayload(todo.getTargetPayload());
        String targetType = text(node, "targetType",
                SCOPE_PLATFORM.equals(todo.getScope()) ? "PLATFORM_OPERATION" : "SYSTEM_RECORD_DETAIL");
        String routeName = text(node, "routeName",
                SCOPE_PLATFORM.equals(todo.getScope()) ? "PlatformTodoTarget" : "SystemModuleRecordDetail");
        boolean requiresSystemSwitch = bool(node, "requiresSystemSwitch",
                SCOPE_PLATFORM.equals(todo.getScope()) && Objects.nonNull(todo.getSystemId()));
        Map<String, String> params = new LinkedHashMap<>();
        params.put("systemId", stringId(todo.getSystemId()));
        params.put("tenantId", stringId(todo.getTenantId()));
        params.put("todoId", stringId(todo.getId()));
        JsonNode payloadParams = node.get("params");
        if (Objects.nonNull(payloadParams) && payloadParams.isObject()) {
            payloadParams.fields().forEachRemaining(entry -> params.put(entry.getKey(), entry.getValue().asText()));
        }
        return new TodoTarget(targetType, routeName, params, requiresSystemSwitch);
    }

    private List<TodoAction> actionPermissions(MessageTodo todo) {
        String primaryAction = primaryActionCode(todo.getPrimaryAction());
        boolean pending = STATUS_PENDING.equalsIgnoreCase(safeText(todo.getStatus(), STATUS_PENDING));
        if (!pending) {
            return List.of(action("open", true, null), action("archive", true, null));
        }
        if ("approve".equals(primaryAction) || "reject".equals(primaryAction) || "transfer".equals(primaryAction)) {
            return List.of(action("approve", true, null), action("reject", true, null),
                    action("transfer", true, null));
        }
        return List.of(action(primaryAction, true, null), action("archive", true, null));
    }

    private TodoAction action(String actionCode, boolean enabled, String disabledReason) {
        return new TodoAction(actionCode, actionName(actionCode), enabled, disabledReason, "todoActionDrawer");
    }

    private String actionName(String actionCode) {
        return switch (safeText(actionCode, "open")) {
            case "approve" -> "审批";
            case "reject" -> "拒绝";
            case "transfer" -> "转交";
            case "remind" -> "提醒";
            case "archive" -> "归档";
            default -> "打开";
        };
    }

    private String actionStatus(String actionCode) {
        String resolvedActionCode = safeText(actionCode, "open");
        if ("archive".equals(resolvedActionCode)) {
            return STATUS_ARCHIVED;
        }
        if ("approve".equals(resolvedActionCode) || "reject".equals(resolvedActionCode)
                || "transfer".equals(resolvedActionCode)) {
            return STATUS_HANDLED;
        }
        return STATUS_PENDING;
    }

    private boolean isApprovalAction(String actionCode) {
        String resolvedActionCode = safeText(actionCode, "open");
        return "approve".equals(resolvedActionCode)
                || "reject".equals(resolvedActionCode)
                || "transfer".equals(resolvedActionCode);
    }

    private String actionMessage(String actionCode) {
        return switch (safeText(actionCode, "open")) {
            case "approve" -> "Todo entered approval handling.";
            case "reject" -> "Todo entered rejection handling.";
            case "transfer" -> "Todo entered transfer handling.";
            case "remind" -> "Todo reminder has been recorded.";
            case "archive" -> "Todo has been archived.";
            default -> "Todo target opened.";
        };
    }

    private JsonNode readPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String primaryActionCode(String primaryAction) {
        if (!StringUtils.hasText(primaryAction)) {
            return "open";
        }
        try {
            JsonNode node = objectMapper.readTree(primaryAction);
            if (node.isTextual()) {
                return safeText(node.asText(), "open");
            }
            return text(node, "actionCode", "open");
        } catch (Exception ex) {
            return primaryAction;
        }
    }

    private Long requireSystemId(String systemId) {
        Long parsed = parseLong(systemId);
        if (Objects.isNull(parsed)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED,
                    "System todo APIs require a numeric system id.");
        }
        return parsed;
    }

    private String text(JsonNode node, String field, String fallback) {
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isTextual() && StringUtils.hasText(value.asText())
                ? value.asText()
                : fallback;
    }

    private boolean bool(JsonNode node, String field, boolean fallback) {
        JsonNode value = node.get(field);
        return Objects.nonNull(value) && value.isBoolean() ? value.asBoolean() : fallback;
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String stringId(Long value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String shortTrace(String traceId) {
        String value = safeText(traceId, "trace_missing");
        return value.length() <= 12 ? value : value.substring(0, 12);
    }
}
