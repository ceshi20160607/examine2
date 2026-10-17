package com.unique.unexamine.notification.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.unique.unexamine.notification.base.entity.TodoContactRule;
import com.unique.unexamine.notification.base.service.TodoContactRuleBaseService;
import com.unique.unexamine.notification.base.service.TodoItemBaseService;
import com.unique.unexamine.notification.base.mapper.TodoItemMapper;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import com.unique.unexamine.moduleconfig.manage.DateTimeFieldProtocol;
import com.unique.unexamine.runtimedata.manage.RuntimeListFilter;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordList;
import com.unique.unexamine.runtimedata.manage.RuntimeRecordView;
import com.unique.unexamine.work.base.entity.WorkTask;
import com.unique.unexamine.work.base.service.WorkTaskBaseService;
import com.unique.unexamine.work.manage.WorkManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
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
    private final TodoContactRuleBaseService contactRuleService;
    private final TodoItemMapper todoMapper;
    private final FlowTaskBaseService flowTaskService;
    private final FlowTaskCandidateBaseService candidateService;
    private final FlowInstanceBaseService flowInstanceService;
    private final FlowRuntimeService flowRuntimeService;
    private final PermissionChecker permissionChecker;
    private final FlowParticipantResolver participantResolver;
    private final WorkTaskBaseService workTaskService;
    private final WorkManagementService workManagementService;
    private final PlatformAccountBaseService accountService;
    private final RuntimeTargetAccessReader contactReader;
    private final ObjectMapper objectMapper;

    public TodoService(TodoItemBaseService todoService, FlowTaskBaseService flowTaskService,
                       FlowTaskCandidateBaseService candidateService,
                       FlowInstanceBaseService flowInstanceService, FlowRuntimeService flowRuntimeService,
                       PermissionChecker permissionChecker, FlowParticipantResolver participantResolver,
                       WorkTaskBaseService workTaskService, WorkManagementService workManagementService,
                       PlatformAccountBaseService accountService, TodoItemMapper todoMapper,
                       TodoContactRuleBaseService contactRuleService, RuntimeTargetAccessReader contactReader,
                       ObjectMapper objectMapper) {
        this.todoService = todoService;
        this.contactRuleService = contactRuleService;
        this.todoMapper = todoMapper;
        this.flowTaskService = flowTaskService;
        this.candidateService = candidateService;
        this.flowInstanceService = flowInstanceService;
        this.flowRuntimeService = flowRuntimeService;
        this.permissionChecker = permissionChecker;
        this.participantResolver = participantResolver;
        this.workTaskService = workTaskService;
        this.workManagementService = workManagementService;
        this.accountService = accountService;
        this.contactReader = contactReader;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<TodoModels.TodoView> list(AuthenticatedContext context, String requestedStatus, String requestedType) {
        require(context, "VIEW");
        String status = requestedStatus.strip().toUpperCase(Locale.ROOT);
        if (!STATUSES.contains(status)) throw invalid("TODO_STATUS_INVALID", "待办状态筛选无效");
        String type = requestedType.strip().toUpperCase(Locale.ROOT);
        projectPendingFlowTasks(context);
        projectPendingWorkTasks(context);
        projectContactTodos(context);
        List<TodoItem> owned = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                .eq(TodoItem::getAssigneeAccountId, context.accountId()));
        // 同一账号可能属于多个系统；只刷新当前工作空间，不能把其他系统的待办误标为失效。
        List<TodoItem> current = owned.stream().filter(item -> inContext(item, context)).toList();
        current.forEach(item -> refresh(item, context));
        return current.stream()
                .filter(item -> "ALL".equals(status) || status.equals(item.getStatus()))
                .filter(item -> "ALL".equals(type) || type.equals(item.getTodoType()))
                .sorted(Comparator.comparing(TodoItem::getDueTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(TodoItem::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(item -> view(item, context)).toList();
    }

    /** 本人当前上下文的审批待办才可打开，目标地址由实际任务和实例计算。 */
    @Transactional(readOnly = true)
    public TodoModels.OpenResult open(AuthenticatedContext context, Long todoId) {
        require(context, "VIEW");
        TodoItem todo = todoService.selectById(todoId);
        if (todo == null || !Objects.equals(todo.getAssigneeAccountId(), context.accountId()) || !inContext(todo, context)) {
            throw notFound("TODO_NOT_FOUND", "待办不存在或不属于当前工作范围");
        }
        if ("WORK_TASK".equals(todo.getSourceType())) {
            Long taskId = numericSource(todo);
            workManagementService.task(context, taskId);
            return new TodoModels.OpenResult(todoId, context.systemId() == null
                    ? "/platform/tasks?taskId=" + taskId
                    : "/systems/" + context.systemId() + "?workspace=tasks&taskId=" + taskId);
        }
        if ("RUNTIME_RECORD".equals(todo.getSourceType())) {
            ContactSource source = contactSource(todo);
            RuntimeRecordView record = contactReader.detail(
                    context, source.moduleCode(), source.recordId(), TraceIdFilter.current());
            if (!Objects.equals(record.ownerMemberId(), context.memberId())) {
                throw notFound("TODO_TARGET_UNAVAILABLE", "业务记录已经转交其他负责人");
            }
            return new TodoModels.OpenResult(todoId, "/systems/" + context.systemId()
                    + "?workspace=runtime&module=" + source.moduleCode() + "&recordId=" + source.recordId());
        }
        if (!"FLOW_TASK".equals(todo.getSourceType())) {
            throw notFound("TODO_TARGET_UNAVAILABLE", "该待办没有可打开的业务目标");
        }
        FlowTask task = flowTaskService.selectById(numericSource(todo));
        if (task == null) throw notFound("TODO_TARGET_UNAVAILABLE", "关联审批不存在或已失效");
        var instance = flowRuntimeService.instance(context, task.getInstanceId());
        return new TodoModels.OpenResult(todoId, context.systemId() == null
                ? "/platform/flows?instanceId=" + instance.id() + "&taskId=" + task.getId()
                : "/systems/" + context.systemId() + "?workspace=flow&instanceId=" + instance.id() + "&taskId=" + task.getId());
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
        Long candidateId = context.systemId() == null ? context.accountId() : tenantMemberId;
        if (candidateId != null) {
            for (FlowTaskCandidate candidate : candidateService.selectList(Wrappers.<FlowTaskCandidate>lambdaQuery()
                    .eq(FlowTaskCandidate::getCandidateType, context.systemId() == null ? "PLATFORM_ACCOUNT" : "TENANT_MEMBER")
                    .eq(FlowTaskCandidate::getCandidateId, String.valueOf(candidateId)))) {
                FlowTask task = flowTaskService.selectById(candidate.getTaskId());
                if (task != null && "PENDING".equals(task.getStatus())) {
                    pending.put(task.getId(), task);
                }
            }
        }
        for (FlowTask task : pending.values()) {
            FlowInstance instance = flowInstanceService.selectById(task.getInstanceId());
            if (instance == null || !instanceInContext(instance, context)) continue;
            // 与流程详情使用同一当前任务、配置及身份守卫；候选领取不另造审批按钮。
            if (flowRuntimeService.taskOperations(context, task.getId()).isEmpty()) continue;
            TodoItem existing = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                    .eq(TodoItem::getAssigneeAccountId, context.accountId())
                    .eq(TodoItem::getSourceType, "FLOW_TASK").eq(TodoItem::getSourceId, String.valueOf(task.getId()))
                    .eq(TodoItem::getTodoType, "APPROVAL")).stream().findFirst().orElse(null);
            if (existing != null) continue;
            TodoItem item = new TodoItem();
            item.setContextType(instance.getContextType()); item.setPlatformId(instance.getPlatformId());
            item.setSystemId(instance.getSystemId()); item.setTenantId(instance.getTenantId());
            // 待办按当前成员投影，账号只用于跨系统身份识别。
            item.setAssigneeAccountId(context.accountId()); item.setAssigneeMemberId(context.memberId());
            item.setTodoType("APPROVAL");
            item.setSourceType("FLOW_TASK"); item.setSourceId(String.valueOf(task.getId()));
            item.setTitle(instance.getTitle());
            // 节点键是运行时标识，待办摘要只说明成员需要处理的事项。
            item.setSummary("FORM_TASK".equals(task.getTaskType()) ? "请填写流程表单" : "请处理流程审批");
            item.setTargetRoute(instance.getSystemId() == null
                    ? "/platform/flows?instanceId=" + instance.getId()
                    : "/systems/" + instance.getSystemId() + "?workspace=flow&instanceId=" + instance.getId());
            item.setPriority("NORMAL"); item.setDueTime(task.getDueTime()); item.setStatus("PENDING"); item.setVersion(0);
            todoService.insert(item);
        }
    }

    private void projectPendingWorkTasks(AuthenticatedContext context) {
        var query = Wrappers.<WorkTask>lambdaQuery()
                .eq(WorkTask::getContextType, context.systemId() == null ? "PLATFORM" : "SYSTEM")
                .eq(WorkTask::getPlatformId, context.platformId())
                .eq(WorkTask::getOwnerAccountId, context.accountId())
                .isNull(WorkTask::getDeleteTime)
                .notIn(WorkTask::getStatus, "COMPLETED", "CANCELLED", "ARCHIVED");
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
            item.setAssigneeAccountId(context.accountId()); item.setAssigneeMemberId(context.memberId());
            item.setTodoType("WORK");
            item.setSourceType("WORK_TASK"); item.setSourceId(String.valueOf(task.getId()));
            item.setTitle(task.getTitle());
            // 待办可由任务负责人读取，但关联业务标题仍须受业务记录权限保护。
            item.setSummary("工作任务 · " + workStatusName(task.getStatus()));
            item.setTargetRoute(task.getSystemId() == null
                    ? "/platform/tasks?taskId=" + task.getId()
                    : "/systems/" + task.getSystemId() + "?workspace=tasks&taskId=" + task.getId());
            item.setPriority(task.getPriority()); item.setDueTime(task.getDueTime());
            item.setStatus("PENDING"); item.setVersion(0);
            todoService.insert(item);
        }
    }

    /**
     * 发布规则只负责声明哪个业务日期需要联系；记录范围、字段读取和负责人仍由运行时模块实时裁剪。
     * 这样既不会复制一套“联系人模块”，也不会让待办绕过模块数据权限。
     */
    private void projectContactTodos(AuthenticatedContext context) {
        if (context.systemId() == null || context.tenantId() == null || context.memberId() == null) return;
        List<TodoContactRule> rules = contactRuleService.selectList(Wrappers.<TodoContactRule>lambdaQuery()
                .eq(TodoContactRule::getScopeKey, contactScopeKey(context))
                .eq(TodoContactRule::getStatus, "PUBLISHED"));
        LocalDate today = LocalDate.now();
        for (TodoContactRule rule : rules) {
            String filters = contactFilters(rule, today);
            int page = 1;
            RuntimeRecordList result;
            do {
                try {
                    result = contactReader.list(context, rule.getModuleCode(), filters, page, TraceIdFilter.current());
                } catch (DomainException inaccessibleRule) {
                    // 某成员无法读取规则字段或模块时，不为其生成待办；已有投影会在刷新时失效。
                    break;
                }
                for (RuntimeRecordView record : result.records()) {
                    if (Objects.equals(record.ownerMemberId(), context.memberId()) && matchesContactDate(rule, record, today)) {
                        projectContactTodo(context, rule, record);
                    }
                }
                page++;
            } while ((long) (page - 1) * 200L < result.total());
        }
    }

    private void projectContactTodo(
            AuthenticatedContext context, TodoContactRule rule, RuntimeRecordView record) {
        String sourceId = contactSourceId(rule, record);
        TodoItem item = todoService.selectList(Wrappers.<TodoItem>lambdaQuery()
                .eq(TodoItem::getAssigneeAccountId, context.accountId())
                .eq(TodoItem::getSourceType, "RUNTIME_RECORD")
                .eq(TodoItem::getSourceId, sourceId)
                .eq(TodoItem::getTodoType, "CONTACT")).stream().findFirst().orElse(null);
        LocalDateTime dueTime = contactDueTime(rule, record);
        String summary = rule.getName() + " · " + rule.getContactFieldNameSnapshot();
        String route = "/systems/" + context.systemId() + "?workspace=runtime&module="
                + rule.getModuleCode() + "&recordId=" + record.id();
        if (item == null) {
            item = new TodoItem();
            item.setContextType("SYSTEM");
            item.setPlatformId(context.platformId());
            item.setSystemId(context.systemId());
            item.setTenantId(context.tenantId());
            item.setAssigneeAccountId(context.accountId());
            item.setAssigneeMemberId(context.memberId());
            item.setTodoType("CONTACT");
            item.setSourceType("RUNTIME_RECORD");
            item.setSourceId(sourceId);
            item.setTitle(record.title());
            item.setSummary(summary);
            item.setTargetRoute(route);
            item.setPriority(rule.getPriority());
            item.setDueTime(dueTime);
            item.setStatus("PENDING");
            item.setVersion(0);
            todoService.insert(item);
            return;
        }
        boolean changed = !"PENDING".equals(item.getStatus()) || !Objects.equals(item.getTitle(), record.title())
                || !Objects.equals(item.getSummary(), summary) || !Objects.equals(item.getPriority(), rule.getPriority())
                || !Objects.equals(item.getDueTime(), dueTime) || !Objects.equals(item.getTargetRoute(), route);
        if (!changed) return;
        item.setTitle(record.title());
        item.setSummary(summary);
        item.setPriority(rule.getPriority());
        item.setDueTime(dueTime);
        item.setTargetRoute(route);
        mark(item, "PENDING", summary, route);
    }

    private String contactFilters(TodoContactRule rule, LocalDate today) {
        LocalDate target = today.plusDays(rule.getLeadDays());
        List<RuntimeListFilter> filters = "DATE".equals(rule.getContactFieldType())
                ? List.of(new RuntimeListFilter(rule.getContactFieldCode(), "EQ", target.toString()))
                : List.of(
                        new RuntimeListFilter(rule.getContactFieldCode(), "GTE", target.atStartOfDay().toString()),
                        new RuntimeListFilter(rule.getContactFieldCode(), "LT", target.plusDays(1).atStartOfDay().toString()));
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException("无法生成今日联系筛选条件", impossible);
        }
    }

    private boolean matchesContactDate(TodoContactRule rule, RuntimeRecordView record, LocalDate today) {
        LocalDateTime due = contactDueTime(rule, record);
        return due != null && due.toLocalDate().equals(today.plusDays(rule.getLeadDays()));
    }

    private LocalDateTime contactDueTime(TodoContactRule rule, RuntimeRecordView record) {
        JsonNode value = record.fields().get(rule.getContactFieldCode());
        if (value == null || value.isNull() || !value.isTextual()) return null;
        try {
            if ("DATE".equals(rule.getContactFieldType())) return LocalDate.parse(value.asText()).atStartOfDay();
            return LocalDateTime.parse(DateTimeFieldProtocol.normalize(value.asText()));
        } catch (IllegalArgumentException | DateTimeParseException invalidValue) {
            return null;
        }
    }

    private String contactSourceId(TodoContactRule rule, RuntimeRecordView record) {
        return rule.getId() + ":" + rule.getModuleCode() + ":" + record.id();
    }

    private void refresh(TodoItem todo, AuthenticatedContext context) {
        if ("RUNTIME_RECORD".equals(todo.getSourceType())) {
            refreshContactTodo(todo, context);
            return;
        }
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
        if ("PENDING".equals(task.getStatus())) {
            // 委托候选可能仍留在审计表中；只有运行时当前动作非空才代表此刻仍有处理权。
            if (flowRuntimeService.taskOperations(context, task.getId()).isEmpty()) {
                mark(todo, "REASSIGNED", "审批任务已转交、委托已失效或当前身份已无处理权", null);
                return;
            }
            if (!"PENDING".equals(todo.getStatus())) mark(todo, "PENDING",
                    "FORM_TASK".equals(task.getTaskType()) ? "请填写流程表单" : "请处理流程审批",
                    instance.getSystemId() == null ? "/platform/flows" : "/systems/" + instance.getSystemId());
            return;
        }
        if ("SUSPENDED".equals(task.getStatus())) {
            if (!"SUSPENDED".equals(todo.getStatus())) mark(todo, "SUSPENDED", "原审批任务因手动加签暂停", null);
            return;
        }
        mark(todo, "COMPLETED", "目标已处理，结果 " + task.getStatus(), null);
    }

    private void refreshContactTodo(TodoItem todo, AuthenticatedContext context) {
        ContactSource source;
        try {
            source = contactSource(todo);
        } catch (DomainException invalidSource) {
            mark(todo, "INVALID", "联系待办来源格式无效", null);
            return;
        }
        TodoContactRule rule = contactRuleService.selectById(source.ruleId());
        if (rule == null || !contactScopeKey(context).equals(rule.getScopeKey())) {
            mark(todo, "INVALID", "联系规则不存在或不属于当前工作空间", null);
            return;
        }
        if (!"PUBLISHED".equals(rule.getStatus())) {
            mark(todo, "COMPLETED", "联系规则已停止生效", null);
            return;
        }
        RuntimeRecordView record;
        try {
            record = contactReader.detail(context, source.moduleCode(), source.recordId(), TraceIdFilter.current());
        } catch (DomainException unavailable) {
            mark(todo, "INVALID", "业务记录已删除、撤权或当前不可查看", null);
            return;
        }
        if (!Objects.equals(record.ownerMemberId(), context.memberId())) {
            mark(todo, "REASSIGNED", "业务记录已转交其他负责人", null);
            return;
        }
        LocalDateTime dueTime = contactDueTime(rule, record);
        if (dueTime == null) {
            mark(todo, "INVALID", "联系日期已清空或格式无效", null);
            return;
        }
        if (!matchesContactDate(rule, record, LocalDate.now())) {
            mark(todo, "COMPLETED", "联系日期已调整或不再属于今天", null);
            return;
        }
        String summary = rule.getName() + " · " + rule.getContactFieldNameSnapshot();
        String route = "/systems/" + context.systemId() + "?workspace=runtime&module="
                + rule.getModuleCode() + "&recordId=" + record.id();
        boolean changed = !"PENDING".equals(todo.getStatus()) || !Objects.equals(todo.getTitle(), record.title())
                || !Objects.equals(todo.getSummary(), summary) || !Objects.equals(todo.getDueTime(), dueTime)
                || !Objects.equals(todo.getPriority(), rule.getPriority());
        if (changed) {
            todo.setTitle(record.title());
            todo.setDueTime(dueTime);
            todo.setPriority(rule.getPriority());
            mark(todo, "PENDING", summary, route);
        }
    }

    private void refreshWorkTask(TodoItem todo, AuthenticatedContext context) {
        WorkTask task;
        try { task = workTaskService.selectById(Long.valueOf(todo.getSourceId())); }
        catch (RuntimeException exception) { task = null; }
        if (task == null || task.getDeleteTime() != null || !Objects.equals(task.getPlatformId(), context.platformId())
                || !Objects.equals(task.getSystemId(), context.systemId())
                || !Objects.equals(task.getTenantId(), context.tenantId())) {
            mark(todo, "INVALID", "目标工作任务不存在或不属于当前上下文", null);
            return;
        }
        if (!Objects.equals(task.getOwnerAccountId(), todo.getAssigneeAccountId())) {
            mark(todo, "REASSIGNED", "工作任务已转交其他负责人", null);
            return;
        }
        if (Set.of("COMPLETED", "CANCELLED", "ARCHIVED").contains(task.getStatus())) {
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
        item.setCompleteTime(Set.of("PENDING", "SUSPENDED").contains(status) ? null : LocalDateTime.now());
        item.setUpdateTime(LocalDateTime.now());
        todoService.updateById(item);
        // 取消领取重新激活原待办时必须清除结束时间，Base 的非空策略不会写入 null。
        if (Set.of("PENDING", "SUSPENDED").contains(status)) {
            todoMapper.update(null, Wrappers.<TodoItem>lambdaUpdate().eq(TodoItem::getId, item.getId())
                    .eq(TodoItem::getVersion, item.getVersion()).set(TodoItem::getCompleteTime, null));
        }
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
                ? flowRuntimeService.taskOperations(context, Long.valueOf(item.getSourceId())) : List.of();
        Long tenantMemberId = participantResolver.tenantMemberIdForAccount(
                item.getSystemId(), item.getTenantId(), item.getAssigneeAccountId());
        SourcePresentation source = sourcePresentation(item);
        String summary = item.getSummary();
        if ("WORK_TASK".equals(item.getSourceType()) && summary != null) {
            // 历史待办可能保存了旧的关联业务标题，返回时也只保留任务状态。
            String[] parts = summary.split(" · ", 3);
            if (parts.length > 2) summary = parts[0] + " · " + parts[1];
        }
        return new TodoModels.TodoView(item.getId(), item.getContextType(), item.getPlatformId(), item.getSystemId(),
                item.getTenantId(), tenantMemberId,
                Objects.equals(item.getAssigneeAccountId(), context.accountId()) ? context.displayName() : "系统成员",
                item.getTodoType(), item.getSourceType(),
                item.getSourceId(), source.sourceLabel(), source.objectName(), source.initiatorName(),
                item.getTitle(), summary, item.getTargetRoute(), item.getPriority(),
                item.getDueTime(), item.getStatus(), item.getCompleteTime(), item.getCreateTime(), item.getUpdateTime(),
                item.getVersion(), actions, summary);
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
                    return new SourcePresentation("工作任务", task.getTitle(), accountName(task.getCreatedByAccountId()));
                }
            } catch (RuntimeException ignored) { /* Fall through to stable todo snapshot. */ }
        }
        if ("RUNTIME_RECORD".equals(item.getSourceType())) {
            try {
                ContactSource source = contactSource(item);
                TodoContactRule rule = contactRuleService.selectById(source.ruleId());
                if (rule != null) return new SourcePresentation("今日需联系", rule.getModuleNameSnapshot(), "系统规则");
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

    private Long numericSource(TodoItem todo) {
        try {
            return Long.valueOf(todo.getSourceId());
        } catch (NumberFormatException exception) {
            throw notFound("TODO_TARGET_UNAVAILABLE", "待办目标引用无效或已经失效");
        }
    }

    private ContactSource contactSource(TodoItem todo) {
        String[] parts = todo.getSourceId() == null ? new String[0] : todo.getSourceId().split(":", 3);
        try {
            if (parts.length != 3 || parts[1].isBlank()) throw new NumberFormatException();
            return new ContactSource(Long.valueOf(parts[0]), parts[1], Long.valueOf(parts[2]));
        } catch (NumberFormatException malformed) {
            throw notFound("TODO_TARGET_UNAVAILABLE", "联系待办目标引用无效或已经失效");
        }
    }

    private String contactScopeKey(AuthenticatedContext context) {
        return "P:" + context.platformId() + ":S:" + context.systemId() + ":T:" + context.tenantId();
    }

    private record SourcePresentation(String sourceLabel, String objectName, String initiatorName) {
    }
    private record ContactSource(Long ruleId, String moduleCode, Long recordId) { }
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
