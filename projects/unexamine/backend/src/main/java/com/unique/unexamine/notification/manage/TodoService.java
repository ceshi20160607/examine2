package com.unique.unexamine.notification.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.entity.FlowTaskCandidate;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskCandidateBaseService;
import com.unique.unexamine.flow.manage.FlowParticipantResolver;
import com.unique.unexamine.flow.manage.FlowRuntimeModels;
import com.unique.unexamine.flow.manage.FlowRuntimeService;
import com.unique.unexamine.notification.base.entity.TodoItem;
import com.unique.unexamine.notification.base.service.TodoItemBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Service
public class TodoService {
    private static final Set<String> STATUSES = Set.of("PENDING", "SUSPENDED", "COMPLETED", "INVALID", "REASSIGNED", "ALL");
    private final TodoItemBaseService todoService;
    private final FlowTaskBaseService flowTaskService;
    private final FlowTaskCandidateBaseService candidateService;
    private final FlowInstanceBaseService flowInstanceService;
    private final FlowRuntimeService flowRuntimeService;
    private final PermissionChecker permissionChecker;
    private final FlowParticipantResolver participantResolver;
    private final WorkTaskBaseService workTaskService;
    private final PlatformAccountBaseService accountService;

    public TodoService(TodoItemBaseService todoService, FlowTaskBaseService flowTaskService,
                       FlowTaskCandidateBaseService candidateService,
                       FlowInstanceBaseService flowInstanceService, FlowRuntimeService flowRuntimeService,
                       PermissionChecker permissionChecker, FlowParticipantResolver participantResolver,
                       WorkTaskBaseService workTaskService, PlatformAccountBaseService accountService) {
        this.todoService = todoService;
        this.flowTaskService = flowTaskService;
        this.candidateService = candidateService;
        this.flowInstanceService = flowInstanceService;
        this.flowRuntimeService = flowRuntimeService;
        this.permissionChecker = permissionChecker;
        this.participantResolver = participantResolver;
        this.workTaskService = workTaskService;
        this.accountService = accountService;
    }

    @Transactional
    public List<TodoModels.TodoView> list(AuthenticatedContext context, String requestedStatus, String requestedType) {
        require(context, "VIEW");
        String status = requestedStatus.strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw invalid("TODO_STATUS_INVALID", "待办状态筛选无效");
        String type = requestedType.strip().toUpperCase(Locale.ROOT);
        projectPendingFlowTasks(context);
        projectPendingWorkTasks(context);
        List<TodoItem> owned = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                .eq(TodoItem::getAssigneeAccountId, context.accountId()));
        owned.forEach(item -> refresh(item, context));
        return owned.stream().filter(item -> inContext(item, context))
                .filter(item -> "ALL".equals(status) || status.equals(item.getStatus()))
                .filter(item -> "ALL".equals(type) || type.equals(item.getTodoType()))
                .sorted(Comparator.comparing(TodoItem::getDueAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TodoItem::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> view(item, context)).toList();
    }

    @Transactional
    public TodoModels.HandleResult handle(AuthenticatedContext context, Long todoId,
                                           TodoModels.HandleRequest input, String traceId) {
        require(context, "HANDLE");
        TodoItem todo = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                .eq(TodoItem::getId, todoId).last("FOR UPDATE")).stream().findFirst()
                .orElseThrow(() -> notFound("TODO_NOT_FOUND", "待办不存在"));
        if (!Objects.equals(todo.getAssigneeAccountId(), context.accountId()) || !inContext(todo, context)) {
            throw forbidden("TODO_VIEW_DENIED", "待办不属于当前账号或当前上下文");
        }
        refresh(todo, context);
        if (!"PENDING".equals(todo.getStatus())) {
            throw conflict("TODO_TARGET_ALREADY_HANDLED", "目标已处理或已失效，请读取待办最新状态");
        }
        if (!"FLOW_TASK".equals(todo.getSourceType())) {
            mark(todo, "INVALID", "当前来源尚未提供可执行动作", null);
            throw conflict("TODO_SOURCE_ACTION_UNAVAILABLE", "待办来源没有可执行动作");
        }
        Long taskId;
        try { taskId = Long.valueOf(todo.getSourceId()); }
        catch (NumberFormatException exception) {
            mark(todo, "INVALID", "目标引用格式无效", null);
            throw conflict("TODO_TARGET_INVALID", "待办目标引用无效");
        }
        FlowRuntimeModels.ActionResult target = flowRuntimeService.handle(context, taskId,
                new FlowRuntimeModels.HandleTaskRequest(input.actionCode(), input.comment(),
                        input.idempotencyKey(), java.util.Map.of(), input.targetTenantMemberId()), traceId);
        String resultReference = "FLOW_INSTANCE:" + target.instance().id() + ":ACTION:" + target.actionId();
        mark(todo, "COMPLETED", "已处理，结果引用 " + resultReference,
                "/systems/" + context.systemId() + "?flowInstance=" + target.instance().id()
                        + "&actionId=" + target.actionId());
        return new TodoModels.HandleResult(view(todo, context), resultReference, target);
    }

    private void projectPendingFlowTasks(AuthenticatedContext context) {
        LinkedHashMap<Long, FlowTask> pending = new LinkedHashMap<>();
        flowTaskService.selectList(Wrappers.<FlowTask>lambdaQuery()
                        .eq(FlowTask::getAssigneeAccountId, context.accountId())
                        .eq(FlowTask::getStatus, "PENDING"))
                .forEach(task -> pending.put(task.getId(), task));
        Long tenantMemberId = participantResolver.currentTenantMemberId(context);
        if (tenantMemberId != null) {
            for (FlowTaskCandidate candidate : candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                    .eq(FlowTaskCandidate::getCandidateType, "TENANT_MEMBER")
                    .eq(FlowTaskCandidate::getCandidateId, String.valueOf(tenantMemberId)))) {
                FlowTask task = flowTaskService.selectById(candidate.getTaskId());
                if (task != null && "PENDING".equals(task.getStatus()) && task.getAssigneeAccountId() == null) {
                    pending.put(task.getId(), task);
                }
            }
        }
        for (FlowTask task : pending.values()) {
            FlowInstance instance = flowInstanceService.selectById(task.getInstanceId());
            if (instance == null || !instanceInContext(instance, context)) continue;
            TodoItem existing = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                    .eq(TodoItem::getAssigneeAccountId, context.accountId())
                    .eq(TodoItem::getSourceType, "FLOW_TASK").eq(TodoItem::getSourceId, String.valueOf(task.getId()))
                    .eq(TodoItem::getTodoType, "APPROVAL")).stream().findFirst().orElse(null);
            if (existing != null) continue;
            TodoItem item = new TodoItem();
            item.setContextType(instance.getContextType()); item.setPlatformId(instance.getPlatformId());
            item.setSystemId(instance.getSystemId()); item.setTenantId(instance.getTenantId());
            item.setAssigneeAccountId(context.accountId()); item.setTodoType("APPROVAL");
            item.setSourceType("FLOW_TASK"); item.setSourceId(String.valueOf(task.getId()));
            item.setTitle(instance.getTitle()); item.setSummary("Flow 审批节点 " + task.getNodeKey());
            item.setTargetRoute(instance.getSystemId() == null
                    ? "/platform/flows?instanceId=" + instance.getId()
                    : "/systems/" + instance.getSystemId() + "?workspace=flow&instanceId=" + instance.getId());
            item.setPriority("NORMAL"); item.setDueAt(task.getDueAt()); item.setStatus("PENDING"); item.setVersion(0);
            todoService.insert(item);
        }
    }

    private void projectPendingWorkTasks(AuthenticatedContext context) {
        var query = Wrappers.<WorkTask>lambdaQuery()
                .eq(WorkTask::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkTask::getPlatformId, context.platformId())
                .eq(WorkTask::getOwnerAccountId, context.accountId())
                .notIn(WorkTask::getStatus, "COMPLETED", "CANCELLED");
        if (context.systemId() == null) query.isNull(WorkTask::getSystemId).isNull(WorkTask::getTenantId);
        else query.eq(WorkTask::getSystemId, context.systemId()).eq(WorkTask::getTenantId, context.tenantId());
        for (WorkTask task : workTaskService.selectList(query)) {
            TodoItem existing = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                    .eq(TodoItem::getAssigneeAccountId, context.accountId())
                    .eq(TodoItem::getSourceType, "WORK_TASK")
                    .eq(TodoItem::getSourceId, String.valueOf(task.getId()))
                    .eq(TodoItem::getTodoType, "WORK")).stream().findFirst().orElse(null);
            if (existing != null) continue;
            TodoItem item = new TodoItem();
            item.setContextType(task.getContextType()); item.setPlatformId(task.getPlatformId());
            item.setSystemId(task.getSystemId()); item.setTenantId(task.getTenantId());
            item.setAssigneeAccountId(context.accountId()); item.setTodoType("WORK");
            item.setSourceType("WORK_TASK"); item.setSourceId(String.valueOf(task.getId()));
            item.setTitle(task.getTitle());
            item.setSummary("工作任务 · " + workStatusName(task.getStatus())
                    + (task.getBusinessTitle() == null ? "" : " · " + task.getBusinessTitle()));
            item.setTargetRoute(task.getSystemId() == null
                    ? "/platform/tasks?taskId=" + task.getId()
                    : "/systems/" + task.getSystemId() + "?workspace=tasks&taskId=" + task.getId());
            item.setPriority(task.getPriority()); item.setDueAt(task.getDueAt());
            item.setStatus("PENDING"); item.setVersion(0);
            todoService.insert(item);
        }
    }

    private void refresh(TodoItem todo, AuthenticatedContext context) {
        if ("WORK_TASK".equals(todo.getSourceType())) {
            refreshWorkTask(todo, context);
            return;
        }
        if (!"FLOW_TASK".equals(todo.getSourceType())) return;
        FlowTask task;
        try { task = flowTaskService.selectById(Long.valueOf(todo.getSourceId())); }
        catch (RuntimeException exception) { task = null; }
        if (task == null) { mark(todo, "INVALID", "目标审批任务不存在", null); return; }
        FlowInstance instance = flowInstanceService.selectById(task.getInstanceId());
        if (instance == null || !instanceInContext(instance, context)) { mark(todo, "INVALID", "目标实例不存在或不属于当前上下文", null); return; }
        boolean stillAssigned = Objects.equals(task.getAssigneeAccountId(), todo.getAssigneeAccountId());
        if (task.getAssigneeAccountId() == null && context.systemId() != null) {
            Long tenantMemberId = participantResolver.tenantMemberIdForAccount(
                    context.systemId(), context.tenantId(), todo.getAssigneeAccountId());
            stillAssigned = tenantMemberId != null && !candidateService.selectList(
                    Wrappers.<FlowTaskCandidate>lambdaQuery().eq(FlowTaskCandidate::getTaskId, task.getId())
                            .eq(FlowTaskCandidate::getCandidateType, "TENANT_MEMBER")
                            .eq(FlowTaskCandidate::getCandidateId, String.valueOf(tenantMemberId))).isEmpty();
        }
        if (!stillAssigned) { mark(todo, "REASSIGNED", "审批任务已转交其他处理人", null); return; }
        if ("PENDING".equals(task.getStatus())) {
            if (!"PENDING".equals(todo.getStatus())) mark(todo, "PENDING", "Flow 审批节点 " + task.getNodeKey(),
                    instance.getSystemId() == null ? "/platform/flows" : "/systems/" + instance.getSystemId());
            return;
        }
        if ("SUSPENDED".equals(task.getStatus())) {
            if (!"SUSPENDED".equals(todo.getStatus())) mark(todo, "SUSPENDED", "原审批任务因手动加签暂停", null);
            return;
        }
        mark(todo, "COMPLETED", "目标已处理，结果 " + task.getStatus(), null);
    }

    private void refreshWorkTask(TodoItem todo, AuthenticatedContext context) {
        WorkTask task;
        try { task = workTaskService.selectById(Long.valueOf(todo.getSourceId())); }
        catch (RuntimeException exception) { task = null; }
        if (task == null || !Objects.equals(task.getPlatformId(), context.platformId())
                || !Objects.equals(task.getSystemId(), context.systemId())
                || !Objects.equals(task.getTenantId(), context.tenantId())) {
            mark(todo, "INVALID", "目标工作任务不存在或不属于当前上下文", null);
            return;
        }
        if (!Objects.equals(task.getOwnerAccountId(), todo.getAssigneeAccountId())) {
            mark(todo, "REASSIGNED", "工作任务已转交其他负责人", null);
            return;
        }
        if (Set.of("COMPLETED", "CANCELLED").contains(task.getStatus())) {
            mark(todo, "COMPLETED", "工作任务已" + workStatusName(task.getStatus()), null);
            return;
        }
        String summary = "工作任务 · " + workStatusName(task.getStatus())
                + (task.getBusinessTitle() == null ? "" : " · " + task.getBusinessTitle());
        if (!"PENDING".equals(todo.getStatus()) || !Objects.equals(todo.getSummary(), summary)) {
            mark(todo, "PENDING", summary, task.getSystemId() == null
                    ? "/platform/tasks?taskId=" + task.getId()
                    : "/systems/" + task.getSystemId() + "?workspace=tasks&taskId=" + task.getId());
        }
    }

    private String workStatusName(String status) {
        return switch (status) {
            case "BACKLOG" -> "待规划";
            case "TODO" -> "待开始";
            case "IN_PROGRESS" -> "进行中";
            case "BLOCKED" -> "已阻塞";
            case "COMPLETED" -> "完成";
            case "CANCELLED" -> "取消";
            default -> status;
        };
    }

    private void mark(TodoItem item, String status, String reason, String route) {
        item.setStatus(status); item.setSummary(reason);
        if (route != null) item.setTargetRoute(route);
        item.setCompletedAt(Set.of("PENDING", "SUSPENDED").contains(status) ? null : LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());
        todoService.updateById(item);
    }

    private boolean inContext(TodoItem item, AuthenticatedContext context) {
        return Objects.equals(item.getContextType(), context.systemId() == null ? "PLATFORM" : "SYSTEM")
                && Objects.equals(item.getPlatformId(), context.platformId())
                && Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.tenantId());
    }
    private boolean instanceInContext(FlowInstance item, AuthenticatedContext context) {
        return Objects.equals(item.getContextType(), context.systemId() == null ? "PLATFORM" : "SYSTEM")
                && Objects.equals(item.getPlatformId(), context.platformId())
                && Objects.equals(item.getSystemId(), context.systemId())
                && Objects.equals(item.getTenantId(), context.tenantId());
    }
    private TodoModels.TodoView view(TodoItem item, AuthenticatedContext context) {
        List<String> actions = "PENDING".equals(item.getStatus()) && "FLOW_TASK".equals(item.getSourceType())
                ? List.of("APPROVE", "REJECT", "RETURN", "TRANSFER") : List.of();
        Long tenantMemberId = participantResolver.tenantMemberIdForAccount(
                item.getSystemId(), item.getTenantId(), item.getAssigneeAccountId());
        SourcePresentation source = sourcePresentation(item);
        return new TodoModels.TodoView(item.getId(), item.getContextType(), item.getPlatformId(), item.getSystemId(),
                item.getTenantId(), tenantMemberId,
                Objects.equals(item.getAssigneeAccountId(), context.accountId()) ? context.displayName() : "系统成员",
                item.getTodoType(), item.getSourceType(),
                item.getSourceId(), source.sourceLabel(), source.objectName(), source.initiatorName(),
                item.getTitle(), item.getSummary(), item.getTargetRoute(), item.getPriority(),
                item.getDueAt(), item.getStatus(), item.getCompletedAt(), item.getCreatedAt(), item.getUpdatedAt(),
                item.getVersion(), actions, item.getSummary());
    }

    private SourcePresentation sourcePresentation(TodoItem item) {
        if ("FLOW_TASK".equals(item.getSourceType())) {
            try {
                FlowTask task = flowTaskService.selectById(Long.valueOf(item.getSourceId()));
                FlowInstance instance = task == null ? null : flowInstanceService.selectById(task.getInstanceId());
                if (instance != null) {
                    return new SourcePresentation("审批流程", instance.getTitle(), accountName(instance.getStartedByAccountId()));
                }
            } catch (RuntimeException ignored) { /* Fall through to stable todo snapshot. */ }
        }
        if ("WORK_TASK".equals(item.getSourceType())) {
            try {
                WorkTask task = workTaskService.selectById(Long.valueOf(item.getSourceId()));
                if (task != null) {
                    String objectName = task.getBusinessTitle() == null || task.getBusinessTitle().isBlank()
                            ? task.getTitle() : task.getBusinessTitle();
                    return new SourcePresentation("工作任务", objectName, accountName(task.getCreatedByAccountId()));
                }
            } catch (RuntimeException ignored) { /* Fall through to stable todo snapshot. */ }
        }
        return new SourcePresentation("业务提醒", item.getTitle(), "系统");
    }

    private String accountName(Long accountId) {
        if (accountId == null) return "系统";
        PlatformAccount account = accountService.selectById(accountId);
        if (account == null) return "系统";
        return account.getDisplayName() == null || account.getDisplayName().isBlank()
                ? account.getUsername() : account.getDisplayName();
    }

    private record SourcePresentation(String sourceLabel, String objectName, String initiatorName) {
    }
    private void require(AuthenticatedContext context, String action) {
        String code = context.systemId() == null ? "PLATFORM" : "SYSTEM";
        if (!permissionChecker.allows(context, "TODO", code, action)
                && !permissionChecker.allows(context, "TODO", "*", action))
            throw forbidden("TODO_PERMISSION_DENIED", "当前上下文没有待办" + action + "权限");
    }
    private DomainException invalid(String code, String message) { return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY); }
    private DomainException forbidden(String code, String message) { return new DomainException(code, message, HttpStatus.FORBIDDEN); }
    private DomainException notFound(String code, String message) { return new DomainException(code, message, HttpStatus.NOT_FOUND); }
    private DomainException conflict(String code, String message) { return new DomainException(code, message, HttpStatus.CONFLICT); }
}
