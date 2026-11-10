package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.NotificationAfterCommit;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.module.ExamineBusinessUpdateContext;
import com.unique.unexamine.core.module.ExamineStartContext;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ExamineStarter;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.sys.flow.base.entity.UnFlowAction;
import com.unique.unexamine.sys.flow.base.entity.UnFlowEvent;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTaskCandidate;
import com.unique.unexamine.sys.flow.base.service.IUnFlowActionService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowEventService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskCandidateService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.service.ModulePermissionService;
import com.unique.unexamine.sys.module.manage.service.ModuleRecordService;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;
import com.unique.unexamine.plat.manage.service.PlatformMessageTemplateService;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionRequest;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ActionView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExampleView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.TaskView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExternalCandidateView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.ExternalTodoView;
import com.unique.unexamine.sys.flow.manage.dto.FlowRuntimeDto.SystemExternalTodoView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Flow 运行实例查询与节点办理。
 *
 * <p>运行只读取实例保存的发布快照，不重新读取流程草稿。每次办理先写幂等操作，再关闭当前
 * 待办、按快照连线条件创建下一批待办，并写入不可变事件；整个动作处于同一事务。</p>
 */
@Service
public class FlowRuntimeService {
    private final ModulePermissionService permissions;
    private final IUnModuleRecordService records;
    private final IUnFlowExampleService examples;
    private final IUnFlowTaskService tasks;
    private final IUnFlowTaskCandidateService candidates;
    private final IUnFlowActionService actions;
    private final IUnFlowEventService events;
    private final ObjectMapper json;
    private final List<ExamineStarter> examineStarters;
    private final ModuleRecordService moduleRecords;
    private final ModuleFlowProcessHandler processHandler;
    private final BusinessEventPublisher eventPublisher;
    private final AuthManageService auth;
    private final SystemManageService systems;
    private final FlowCandidateResolver candidateResolver;
    private final SystemMessageTemplateService systemMessages;
    private final PlatformMessageTemplateService platformMessages;

    public FlowRuntimeService(ModulePermissionService permissions,
                              IUnModuleRecordService records, IUnFlowExampleService examples,
                              IUnFlowTaskService tasks, IUnFlowTaskCandidateService candidates,
                              IUnFlowActionService actions, IUnFlowEventService events,
                              ObjectMapper json, List<ExamineStarter> examineStarters,
                              ModuleRecordService moduleRecords, ModuleFlowProcessHandler processHandler,
                              BusinessEventPublisher eventPublisher, AuthManageService auth,
                              SystemManageService systems, FlowCandidateResolver candidateResolver,
                              SystemMessageTemplateService systemMessages,
                              PlatformMessageTemplateService platformMessages) {
        this.permissions = permissions;
        this.records = records;
        this.examples = examples;
        this.tasks = tasks;
        this.candidates = candidates;
        this.actions = actions;
        this.events = events;
        this.json = json;
        this.examineStarters = examineStarters == null ? List.of() : List.copyOf(examineStarters);
        this.moduleRecords = moduleRecords;
        this.processHandler = processHandler;
        this.eventPublisher = eventPublisher;
        this.auth = auth;
        this.systems = systems;
        this.candidateResolver = candidateResolver;
        this.systemMessages = systemMessages;
        this.platformMessages = platformMessages;
    }

    /** 查询当前记录仍在运行中的 Flow 实例，详情权限在业务入口重新校验。 */
    public List<ExampleView> listForRecord(String token, Long systemId, Long tenantId,
                                           Long moduleId, Long recordId) {
        var access = accessRecord(token, systemId, tenantId, moduleId, recordId);
        Long actualTenantId = access.context().tenantId();
        return examples.list(new LambdaQueryWrapper<UnFlowExample>()
                        .eq(UnFlowExample::getSystemId, systemId)
                        .eq(UnFlowExample::getTenantId, actualTenantId)
                        .eq(UnFlowExample::getModuleId, moduleId)
                        .eq(UnFlowExample::getRecordId, recordId)
                        .orderByDesc(UnFlowExample::getStartTime))
                .stream().map(this::view).toList();
    }

    /** 查询当前系统租户的 Flow 待办；候选人和业务记录权限同时生效。 */
    public Page<ExampleView> pending(
            String token, Long systemId, Long tenantId, SearchBO search) {
        SystemContext context = systems.context(token, systemId, tenantId);
        var where = new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getScopeType, "SYSTEM")
                .eq(UnFlowExample::getSystemId, context.systemId())
                .eq(UnFlowExample::getTenantId, context.tenantId())
                .eq(UnFlowExample::getStatus, "RUNNING")
                // 先在数据库分页阶段排除已删除业务记录，避免待办 total 包含不可办理实例。
                .apply("EXISTS (SELECT 1 FROM un_module_record r WHERE r.id=un_flow_example.record_id "
                        + "AND r.system_id={0} AND r.tenant_id={1} AND r.module_id=un_flow_example.module_id "
                        + "AND r.deleted_time IS NULL)", context.systemId(), context.tenantId())
                .inSql(UnFlowExample::getId,
                        "SELECT t.example_id FROM un_flow_task t "
                                + "JOIN un_flow_task_candidate c ON c.task_id=t.id "
                                + "WHERE t.deleted_time IS NULL AND t.status='PENDING' "
                                + "AND c.deleted_time IS NULL AND c.status='PENDING' AND c.account_id="
                                + context.accountId())
                .orderByDesc(UnFlowExample::getStartTime)
                .orderByDesc(UnFlowExample::getId);
        // 记录权限只能按实例所属模块动态判断，先扫描候选待办再切页，保证 total 与可见记录一致。
        Page<ExampleView> requested = search == null ? new SearchBO().parse() : search.parse();
        List<UnFlowExample> visible = new ArrayList<>();
        long rawTotal = 0;
        for (long rawPageNo = 1; ; rawPageNo++) {
            Page<UnFlowExample> rawPage = Page.of(rawPageNo, 100);
            rawPage.setSearchCount(rawPageNo == 1);
            rawPage = examples.page(rawPage, where);
            if (rawPageNo == 1) rawTotal = rawPage.getTotal();
            rawPage.getRecords().stream()
                    .filter(example -> flowRecordVisible(token, context, example))
                    .forEach(visible::add);
            if (rawPage.getRecords().isEmpty() || rawPageNo * 100 >= rawTotal) break;
        }
        long from = Math.min((requested.getCurrent() - 1) * requested.getSize(), visible.size());
        long to = Math.min(from + requested.getSize(), visible.size());
        List<ExampleView> views = visible.subList((int) from, (int) to).stream().map(this::view).toList();
        return new Page<ExampleView>(requested.getCurrent(), requested.getSize(), visible.size()).setRecords(views);
    }

    /** Flow 业务记录已删除或当前账号失去详情范围时，从待办列表隐藏。 */
    private boolean flowRecordVisible(String token, SystemContext context, UnFlowExample example) {
        if (example.getModuleId() == null || example.getRecordId() == null) return false;
        try {
            accessRecord(token, context.systemId(), context.tenantId(), example.getModuleId(), example.getRecordId());
            return true;
        } catch (BaseException ex) {
            if (ex.getCode() == SystemCodeEnum.SYSTEM_NO_FOUND.getCode()
                    || ex.getCode() == SystemCodeEnum.SYSTEM_NO_AUTH.getCode()) return false;
            throw ex;
        }
    }

    /** 查询单个实例；系统和记录边界必须与实例一致。 */
    public ExampleView query(String token, Long systemId, Long tenantId, Long moduleId, Long recordId,
                             Long exampleId) {
        var access = accessRecord(token, systemId, tenantId, moduleId, recordId);
        return view(example(exampleId, systemId, access.context().tenantId(), moduleId, recordId));
    }

    /** 应用授权后的外部 Flow 实例查询；业务记录仍需存在且属于请求的系统租户模块。 */
    public ExampleView queryExternal(Long applicationId, String externalId, Long systemId, Long tenantId,
                                     Long moduleId, Long recordId, Long bindingId, Long exampleId) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部 Flow 身份无效");
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(moduleId, "模块主键无效");
        requirePositive(recordId, "业务记录主键无效");
        requirePositive(bindingId, "Flow 绑定主键无效");
        requirePositive(exampleId, "Flow 实例主键无效");
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !Objects.equals(record.getSystemId(), systemId)
                || !Objects.equals(record.getTenantId(), tenantId)
                || !Objects.equals(record.getModuleId(), moduleId) || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        UnFlowExample current = example(exampleId, systemId, tenantId, moduleId, recordId);
        if (!Objects.equals(current.getBindingId(), bindingId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 实例不属于该绑定");
        requireExternalParticipant(current, applicationId, externalId);
        return view(current, new Actor(null, applicationId, externalId));
    }

    /** 外部查询只允许发起人或流程待办中的外部候选人，保留已办理人的历史查看权。 */
    private void requireExternalParticipant(UnFlowExample example, Long applicationId, String externalId) {
        if (Objects.equals(example.getApplicationId(), applicationId)
                && Objects.equals(example.getInitiatorExternalId(), externalId)) return;
        List<Long> taskIds = tasks.list(new LambdaQueryWrapper<UnFlowTask>()
                        .eq(UnFlowTask::getExampleId, example.getId()))
                .stream().map(UnFlowTask::getId).toList();
        if (!taskIds.isEmpty() && candidates.count(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                .in(UnFlowTaskCandidate::getTaskId, taskIds)
                .eq(UnFlowTaskCandidate::getCandidateType, "EXTERNAL")
                .eq(UnFlowTaskCandidate::getApplicationId, applicationId)
                .eq(UnFlowTaskCandidate::getExternalId, externalId)) > 0) return;
        throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "外部人员不属于该 Flow 实例");
    }

    /** 外部应用按已授权绑定发起 Flow，并返回已保存发布版本对应的运行实例。 */
    @Transactional(rollbackFor = Exception.class)
    public ExampleView startExternal(Long applicationId, String externalId, Long systemId, Long tenantId,
                                     Long moduleId, Long recordId, Long bindingId, String requestId,
                                     Map<String, Object> values) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || requestId == null || requestId.isBlank()
                || requestId.length() > 100 || !requestId.matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部 Flow 身份或请求号无效");
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(moduleId, "模块主键无效");
        requirePositive(recordId, "业务记录主键无效");
        requirePositive(bindingId, "Flow 绑定主键无效");
        // 以业务记录为并发边界，串行化同一外部身份的幂等检查和 Flow 实例创建。
        UnModuleRecord lockedRecord = records.getOne(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getId, recordId)
                .eq(UnModuleRecord::getSystemId, systemId)
                .eq(UnModuleRecord::getTenantId, tenantId)
                .eq(UnModuleRecord::getModuleId, moduleId)
                .isNull(UnModuleRecord::getDeletedTime)
                .last("FOR UPDATE"), false);
        if (lockedRecord == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        UnFlowExample previous = examples.list(new LambdaQueryWrapper<UnFlowExample>()
                        .eq(UnFlowExample::getSystemId, systemId)
                        .eq(UnFlowExample::getTenantId, tenantId)
                        .eq(UnFlowExample::getModuleId, moduleId)
                        .eq(UnFlowExample::getRecordId, recordId)
                        .eq(UnFlowExample::getApplicationId, applicationId)
                        .eq(UnFlowExample::getInitiatorExternalId, externalId)
                        .orderByDesc(UnFlowExample::getId))
                .stream().filter(item -> requestId.equals(runtimeRequestId(item))).findFirst().orElse(null);
        if (previous != null) return view(previous, new Actor(null, applicationId, externalId));
        Long exampleId = processHandler.startExternal(applicationId, externalId, systemId, tenantId, moduleId,
                recordId, bindingId, requestId, values);
        return view(example(exampleId, systemId, tenantId, moduleId, recordId),
                new Actor(null, applicationId, externalId));
    }

    /** 平台管理员或平台 Flow 当前待办人发起独立 Flow；请求号用于同一发起动作幂等。 */
    @Transactional(rollbackFor = Exception.class)
    public ExampleView startPlatform(String token, Long flowId, String requestId, Map<String, Object> values) {
        Long accountId = auth.requireAccount(token).getId();
        if (flowId == null || flowId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 定义无效");
        if (requestId == null || requestId.isBlank() || requestId.length() > 100
                || !requestId.matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 请求号不能为空");
        UnFlowExample previous = examples.list(new LambdaQueryWrapper<UnFlowExample>()
                        .isNull(UnFlowExample::getSystemId).isNull(UnFlowExample::getTenantId)
                        .isNull(UnFlowExample::getModuleId).isNull(UnFlowExample::getRecordId)
                        .eq(UnFlowExample::getFlowId, flowId)
                        .eq(UnFlowExample::getInitiatorAccountId, accountId)
                        .orderByDesc(UnFlowExample::getId))
                .stream().filter(item -> requestId.equals(runtimeRequestId(item))).findFirst().orElse(null);
        if (previous != null) return view(previous);
        Long exampleId = processHandler.startPlatform(flowId, accountId, requestId, values);
        return view(platformExample(exampleId));
    }

    /** 外部应用按授权的平台 Flow 定义发起，处理器在定义行锁内核对幂等请求。 */
    @Transactional(rollbackFor = Exception.class)
    public ExampleView startPlatformExternal(Long applicationId, String externalId, Long flowId,
                                             String requestId, Map<String, Object> values) {
        requirePositive(flowId, "平台 Flow 定义主键无效");
        Long exampleId = processHandler.startPlatformExternal(flowId, applicationId, externalId, requestId, values);
        return view(platformExample(exampleId), new Actor(null, applicationId, externalId));
    }

    /** 平台 Flow 外部查询仍要求发起人或历史节点候选身份，应用授权不能代替实例参与身份。 */
    public ExampleView queryPlatformExternal(Long applicationId, String externalId, Long flowId, Long exampleId) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 外部身份无效");
        requirePositive(exampleId, "平台 Flow 实例主键无效");
        requirePositive(flowId, "平台 Flow 定义主键无效");
        UnFlowExample current = platformExample(exampleId);
        if (!Objects.equals(current.getFlowId(), flowId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 实例不属于该定义");
        requireExternalParticipant(current, applicationId, externalId);
        return view(current, new Actor(null, applicationId, externalId));
    }

    /** 平台 Flow 外部待办与模块 Flow 使用相同的候选人快照和事务办理链。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView actionPlatformExternal(Long applicationId, String externalId, Long flowId, Long exampleId,
                                             Long taskId, ActionRequest request) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 外部身份无效");
        requirePositive(exampleId, "平台 Flow 实例主键无效");
        requirePositive(flowId, "平台 Flow 定义主键无效");
        requirePositive(taskId, "平台 Flow 待办主键无效");
        UnFlowExample example = lockedPlatformExample(exampleId);
        if (!Objects.equals(example.getFlowId(), flowId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 实例不属于该定义");
        UnFlowTask task = tasks.getById(taskId);
        if (task == null || !exampleId.equals(task.getExampleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 待办不存在");
        return executeAction(null, null, null, null, example, task, request,
                new Actor(null, applicationId, externalId));
    }

    /** 查询平台 Flow 实例；非管理员只能查看本人发起或当前待办实例。 */
    public List<ExampleView> listPlatform(String token) {
        Long accountId = auth.requireAccount(token).getId();
        LambdaQueryWrapper<UnFlowExample> where = new LambdaQueryWrapper<>();
        where.isNull(UnFlowExample::getSystemId);
        where.isNull(UnFlowExample::getTenantId);
        where.isNull(UnFlowExample::getModuleId);
        where.isNull(UnFlowExample::getRecordId);
        if (!platformAdmin(token)) {
            where.and(q -> q.eq(UnFlowExample::getInitiatorAccountId, accountId)
                    .or().inSql(UnFlowExample::getId,
                            "SELECT t.example_id FROM un_flow_task t JOIN un_flow_task_candidate c "
                                    + "ON c.task_id=t.id WHERE t.deleted_time IS NULL AND t.status='PENDING' "
                                    + "AND c.deleted_time IS NULL AND c.status='PENDING' "
                                    + "AND c.account_id=" + accountId));
        }
        return examples.list(where.orderByDesc(UnFlowExample::getStartTime)).stream().map(this::view).toList();
    }

    /** 平台 Flow 工作台只返回当前账号仍待处理的实例。 */
    public Page<ExampleView> pendingPlatform(
            String token, SearchBO search) {
        Long accountId = auth.requireAccount(token).getId();
        var where = new LambdaQueryWrapper<UnFlowExample>()
                .isNull(UnFlowExample::getSystemId)
                .isNull(UnFlowExample::getTenantId)
                .isNull(UnFlowExample::getModuleId)
                .isNull(UnFlowExample::getRecordId)
                .eq(UnFlowExample::getStatus, "RUNNING")
                .inSql(UnFlowExample::getId,
                        "SELECT t.example_id FROM un_flow_task t "
                                + "JOIN un_flow_task_candidate c ON c.task_id=t.id "
                                + "WHERE t.deleted_time IS NULL AND t.status='PENDING' "
                                + "AND c.deleted_time IS NULL AND c.status='PENDING' AND c.account_id=" + accountId)
                .orderByDesc(UnFlowExample::getStartTime)
                .orderByDesc(UnFlowExample::getId);
        var page = examples.page(search == null ? new SearchBO().parse() : search.parse(), where);
        return new Page<ExampleView>(
                page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(this::view).toList());
    }

    /**
     * 平台应用外部身份的待办只依据当前未删除候选关系、待办和运行实例查询。
     * 分页在数据库完成，响应不暴露同一节点上的其他候选人。
     */
    public Page<ExternalTodoView> pendingPlatformExternal(Long applicationId, String externalId,
                                                          Long flowId, SearchBO search) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || flowId != null && flowId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 外部身份无效");
        var where = new LambdaQueryWrapper<UnFlowTask>()
                .eq(UnFlowTask::getStatus, "PENDING")
                .apply("EXISTS (SELECT 1 FROM un_flow_task_candidate c "
                        + "WHERE c.task_id=un_flow_task.id AND c.deleted_time IS NULL "
                        + "AND c.status='PENDING' AND c.application_id={0} AND c.external_id={1})",
                        applicationId, externalId)
                .apply("EXISTS (SELECT 1 FROM un_flow_example e "
                        + "WHERE e.id=un_flow_task.example_id AND e.deleted_time IS NULL "
                        + "AND e.status='RUNNING' AND e.system_id IS NULL AND e.tenant_id IS NULL "
                        + "AND e.module_id IS NULL AND e.record_id IS NULL)");
        if (flowId != null) where.apply("EXISTS (SELECT 1 FROM un_flow_example e "
                + "WHERE e.id=un_flow_task.example_id AND e.flow_id={0} AND e.deleted_time IS NULL)", flowId);
        where.orderByDesc(UnFlowTask::getId);
        Page<UnFlowTask> page = tasks.page(search == null ? new SearchBO().parse() : search.parse(), where);
        Map<Long, UnFlowExample> byId = page.getRecords().isEmpty() ? Map.of()
                : examples.listByIds(page.getRecords().stream().map(UnFlowTask::getExampleId).distinct().toList())
                        .stream().collect(Collectors.toMap(UnFlowExample::getId, example -> example));
        return new Page<ExternalTodoView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(task -> {
                    UnFlowExample example = byId.get(task.getExampleId());
                    if (example == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 待办实例已变化，请重新查询");
                    return new ExternalTodoView(task.getId(), example.getId(), example.getFlowId(),
                            task.getNodeCode(), task.getRoundNo(), task.getTaskType(), task.getDueTime(),
                            example.getStartTime());
                }).toList());
    }

    /**
     * 系统模块外部待办先按候选身份、绑定和未删除记录取数，再以记录当前负责人范围过滤。
     * 过滤发生在分页计数之前，避免授权范围外的行占据页面或泄露总数。
     */
    @Transactional(readOnly = true)
    public Page<SystemExternalTodoView> pendingSystemExternal(Long applicationId, String externalId,
            Long systemId, Long tenantId, Long bindingId, SearchBO search,
            BiPredicate<Long, Long> ownerScope) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || systemId == null || systemId < 1
                || tenantId == null || tenantId < 1 || bindingId != null && bindingId < 1
                || ownerScope == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统 Flow 外部待办参数无效");
        Page<SystemExternalTodoView> requested = search == null ? new SearchBO().parse() : search.parse();
        LambdaQueryWrapper<UnFlowTask> where = new LambdaQueryWrapper<UnFlowTask>()
                .eq(UnFlowTask::getStatus, "PENDING")
                .apply("EXISTS (SELECT 1 FROM un_flow_task_candidate c "
                        + "WHERE c.task_id=un_flow_task.id AND c.deleted_time IS NULL "
                        + "AND c.status='PENDING' AND c.application_id={0} AND c.external_id={1})",
                        applicationId, externalId)
                .apply("EXISTS (SELECT 1 FROM un_flow_example e JOIN un_module_record r ON r.id=e.record_id "
                        + "WHERE e.id=un_flow_task.example_id AND e.deleted_time IS NULL "
                        + "AND e.status='RUNNING' AND e.scope_type='SYSTEM' "
                        + "AND e.system_id={0} AND e.tenant_id={1} "
                        + "AND r.deleted_time IS NULL AND r.system_id=e.system_id "
                        + "AND r.tenant_id=e.tenant_id AND r.module_id=e.module_id)", systemId, tenantId);
        if (bindingId != null) where.apply("EXISTS (SELECT 1 FROM un_flow_example e "
                + "WHERE e.id=un_flow_task.example_id AND e.deleted_time IS NULL AND e.binding_id={0})",
                bindingId);
        where.orderByDesc(UnFlowTask::getId);
        long offset = (requested.getCurrent() - 1) * requested.getSize();
        long visibleTotal = 0;
        List<SystemExternalTodoView> selected = new ArrayList<>();
        long rawPageNo = 1;
        while (true) {
            Page<UnFlowTask> raw = tasks.page(Page.of(rawPageNo, 100), where);
            if (raw.getRecords().isEmpty()) break;
            Map<Long, UnFlowExample> byExample = examples.listByIds(raw.getRecords().stream()
                            .map(UnFlowTask::getExampleId).distinct().toList()).stream()
                    .collect(Collectors.toMap(UnFlowExample::getId, item -> item));
            Map<Long, UnModuleRecord> byRecord = byExample.isEmpty() ? Map.of()
                    : records.listByIds(byExample.values().stream().map(UnFlowExample::getRecordId)
                                    .filter(Objects::nonNull).distinct().toList()).stream()
                            .collect(Collectors.toMap(UnModuleRecord::getId, item -> item));
            for (UnFlowTask task : raw.getRecords()) {
                UnFlowExample example = byExample.get(task.getExampleId());
                UnModuleRecord record = example == null ? null : byRecord.get(example.getRecordId());
                if (record == null || !Objects.equals(example.getSystemId(), systemId)
                        || !Objects.equals(example.getTenantId(), tenantId)
                        || bindingId != null && !Objects.equals(example.getBindingId(), bindingId)
                        || !Objects.equals(record.getSystemId(), systemId)
                        || !Objects.equals(record.getTenantId(), tenantId)
                        || !Objects.equals(record.getModuleId(), example.getModuleId())
                        || !ownerScope.test(record.getOwnerUserId(), record.getOwnerDeptId())) continue;
                visibleTotal++;
                if (visibleTotal > offset && selected.size() < requested.getSize())
                    selected.add(new SystemExternalTodoView(task.getId(), example.getId(),
                            example.getBindingId(), example.getModuleId(), example.getRecordId(),
                            task.getNodeCode(), task.getRoundNo(), task.getTaskType(), example.getStartTime()));
            }
            if (rawPageNo * 100 >= raw.getTotal()) break;
            rawPageNo++;
        }
        return new Page<SystemExternalTodoView>(requested.getCurrent(), requested.getSize(), visibleTotal)
                .setRecords(selected);
    }

    /** 查询平台 Flow 实例详情并重新核对当前账号的发起人或待办身份。 */
    public ExampleView queryPlatform(String token, Long exampleId) {
        Long accountId = auth.requireAccount(token).getId();
        requirePositive(exampleId, "Flow 实例主键无效");
        UnFlowExample current = platformExample(exampleId);
        if (!platformAdmin(token) && !Objects.equals(current.getInitiatorAccountId(), accountId)
                && candidates.count(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                        .eq(UnFlowTaskCandidate::getAccountId, accountId)
                        .eq(UnFlowTaskCandidate::getStatus, "PENDING")
                        .inSql(UnFlowTaskCandidate::getTaskId,
                                "SELECT id FROM un_flow_task "
                                        + "WHERE deleted_time IS NULL AND status='PENDING' "
                                        + "AND example_id=" + exampleId)) == 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "无权查看该平台 Flow 实例");
        return view(current);
    }

    /** 办理平台 Flow 普通节点；平台管理员可处理任意当前待办，其他账号必须是候选人。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView actionPlatform(String token, Long exampleId, Long taskId, ActionRequest request) {
        Long accountId = auth.requireAccount(token).getId();
        requirePositive(exampleId, "Flow 实例主键无效");
        requirePositive(taskId, "Flow 待办主键无效");
        UnFlowExample example = lockedPlatformExample(exampleId);
        UnFlowTask task = tasks.getById(taskId);
        if (task == null || !exampleId.equals(task.getExampleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 待办不存在");
        boolean administrator = platformAdmin(token);
        ModulePermissionService.Access platformAccess = new ModulePermissionService.Access(
                new SystemContext(null, null, accountId, null, null, administrator, administrator),
                accountId, null, null);
        return executeAction(platformAccess, null, null, null, example, task, request,
                new Actor(accountId, null, null));
    }

    private boolean platformAdmin(String token) {
        try {
            auth.requirePlatformAdmin(token);
            return true;
        } catch (BaseException ex) {
            return false;
        }
    }

    /** 办理 Flow 节点并按发布快照推进后续线路。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView action(String token, Long systemId, Long tenantId, Long moduleId, Long recordId,
                             Long exampleId, Long taskId, ActionRequest request) {
        var access = accessRecord(token, systemId, tenantId, moduleId, recordId);
        UnFlowExample example = lockedExample(exampleId, systemId, access.context().tenantId(), moduleId, recordId);
        UnFlowTask task = tasks.getById(taskId);
        if (task == null || !exampleId.equals(task.getExampleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 待办不存在");
        return executeAction(access, access.context().tenantId(), moduleId, recordId, example, task, request,
                new Actor(access.accountId(), null, null));
    }

    /** 应用授权后的外部 Flow 办理；外部身份必须与运行待办中的应用候选人匹配。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView actionExternal(Long applicationId, String externalId, Long systemId, Long tenantId,
                                     Long moduleId, Long recordId, Long bindingId, Long exampleId, Long taskId,
                                     ActionRequest request) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部 Flow 身份无效");
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(moduleId, "模块主键无效");
        requirePositive(recordId, "业务记录主键无效");
        requirePositive(bindingId, "Flow 绑定主键无效");
        requirePositive(exampleId, "Flow 实例主键无效");
        requirePositive(taskId, "Flow 待办主键无效");
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !Objects.equals(record.getSystemId(), systemId)
                || !Objects.equals(record.getTenantId(), tenantId)
                || !Objects.equals(record.getModuleId(), moduleId) || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        UnFlowExample example = lockedExample(exampleId, systemId, tenantId, moduleId, recordId);
        if (!Objects.equals(example.getBindingId(), bindingId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 实例不属于该绑定");
        UnFlowTask task = tasks.getById(taskId);
        if (task == null || !exampleId.equals(task.getExampleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 待办不存在");
        return executeAction(null, tenantId, moduleId, recordId, example, task, request,
                new Actor(null, applicationId, externalId));
    }

    /** 模块阶段按配置的业务事件自动推进；事件监听器已经核对来源记录或阶段补充关联。 */
    @Transactional(rollbackFor = Exception.class)
    public void advanceByEvent(Long exampleId, Long taskId, BusinessEventContext event) {
        if (exampleId==null || taskId==null || event==null || event.systemId()==null
                || event.tenantId()==null || event.moduleId()==null || event.recordId()==null
                || event.eventCode()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"自动阶段事件上下文无效");
        UnFlowExample example=examples.getOne(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getId,exampleId).last("FOR UPDATE"),false);
        if (example==null || !Objects.equals(example.getSystemId(),event.systemId())
                || !Objects.equals(example.getTenantId(),event.tenantId())
                || !"RUNNING".equals(example.getStatus())) return;
        // 删除事件发生在软删除之后；Flow 历史继续保留，但已删除记录不再推进阶段。
        if ("RECORD_DELETE".equals(event.eventCode())) return;
        UnModuleRecord subject=records.getById(example.getRecordId());
        // MyBatis-Plus 逻辑删除会使 getById 返回空，阶段补充记录的事件也须跳过旧来源记录。
        if (subject==null || subject.getDeletedTime()!=null) return;
        if (!Objects.equals(subject.getSystemId(),example.getSystemId())
                || !Objects.equals(subject.getTenantId(),example.getTenantId())
                || !Objects.equals(subject.getModuleId(),example.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"自动阶段关联的业务记录不存在");
        UnFlowTask task=tasks.getById(taskId);
        if (task==null || !exampleId.equals(task.getExampleId())
                || !"PENDING".equals(task.getStatus()) || !"STAGE".equals(task.getTaskType())) return;
        Map<String,Object> routeValues=new LinkedHashMap<>(runtimeValues(example));
        boolean sourceRecord=Objects.equals(event.moduleId(),example.getModuleId())
                && Objects.equals(event.recordId(),example.getRecordId());
        if (sourceRecord) routeValues.putAll(event.values());
        routeValues.put("__flowEvent",event.eventCode());
        JsonNode edges=PublishedProcessSnapshot.edges(json,runtimeDefinition(example).toString());
        boolean configured=false;
        if (edges!=null && edges.isArray()) for (JsonNode edge:edges) {
            JsonNode condition=edge.path("condition");
            if (task.getNodeCode().equals(edge.path("sourceNodeCode").asText())
                    && (("__flowEvent".equals(condition.path("fieldKey").asText())
                            && event.eventCode().equals(condition.path("value").asText()))
                        || condition.isArray() && java.util.stream.StreamSupport.stream(
                                condition.spliterator(),false).anyMatch(item->
                                "__flowEvent".equals(item.path("fieldKey").asText())
                                        && event.eventCode().equals(item.path("value").asText())))
                    && TriggerConditionMatcher.matches(json,condition.toString(),routeValues))
                configured=true;
        }
        if (!configured) return;
        String requestId="auto:"+taskId;
        if (actions.count(new LambdaQueryWrapper<UnFlowAction>()
                .eq(UnFlowAction::getExampleId,exampleId).eq(UnFlowAction::getRequestId,requestId))>0) return;
        Actor actor=new Actor(event.accountId(),null,null);
        UnFlowAction action=new UnFlowAction(); action.setExampleId(exampleId); action.setTaskId(taskId);
        action.setActorAccountId(event.accountId()); action.setActionCode("AUTO_EVENT");
        action.setRequestId(requestId); action.setResultCode("SUCCESS");
        action.setPayloadJson(writePayload(Map.of("eventCode",event.eventCode(),
                "sourceModuleId",event.moduleId(),"sourceRecordId",event.recordId())));
        action.setCreateTime(LocalDateTime.now()); actions.addOrUpdate(action);
        task.setStatus("COMPLETED"); task.setFinishTime(LocalDateTime.now()); tasks.addOrUpdate(task);
        completeAllCandidates(taskId);
        if (sourceRecord) mergeRuntimeValues(example,event.values());
        List<ExamineStartContext> examineStarts=route(example,task,action,actor,routeValues);
        List<String> pending=pendingNodeCodes(exampleId);
        example.setCurrentNodesJson(writePayload(pending));
        if (pending.isEmpty()) {
            example.setStatus("COMPLETED"); example.setFinishTime(LocalDateTime.now());
            addEvent(example,task,action,"FLOW_COMPLETE",actor.accountId(),null,
                    Map.of("eventCode",event.eventCode()));
        }
        example.setUpdateTime(LocalDateTime.now()); examples.addOrUpdate(example);
        if (pending.isEmpty()) publishFlowCompleted(example,actor,routeValues,
                Map.of("eventCode",event.eventCode()));
        startExamine(examineStarts);
    }

    private ActionView executeAction(ModulePermissionService.Access access, Long tenantId, Long moduleId,
                                      Long recordId, UnFlowExample example, UnFlowTask task,
                                      ActionRequest request, Actor actor) {
        if (request == null || request.requestId() == null || request.requestId().isBlank()
                || request.requestId().length() > 100 || request.actionCode() == null
                || request.actionCode().isBlank() || request.actionCode().length() > 40)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 办理资料无效");
        Long exampleId = example.getId();
        Long systemId = example.getSystemId();
        UnFlowAction previous = actions.getOne(new LambdaQueryWrapper<UnFlowAction>()
                .eq(UnFlowAction::getExampleId, exampleId).eq(UnFlowAction::getRequestId, request.requestId()));
        if (previous != null) {
            if (!sameActionActor(previous, access, actor))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "请求号已被其他 Flow 处理人使用");
            return new ActionView(previous.getId(), exampleId, previous.getTaskId(),
                    previous.getActionCode(), previous.getResultCode(), view(example, actor));
        }
        if (!"RUNNING".equals(example.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 实例已结束");
        if (!"PENDING".equals(task.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 待办已处理");
        String actionCode = request.actionCode().trim().toUpperCase(Locale.ROOT);
        if (!Set.of("EXECUTE", "CONTINUE").contains(actionCode))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 操作不支持: " + actionCode);
        if ("EXAMINE_WAIT".equalsIgnoreCase(task.getTaskType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "当前节点正在等待 examine 审批");
        if ("STAGE".equalsIgnoreCase(task.getTaskType()) && !manualStageEnabled(task))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "当前阶段仅允许按配置的业务事件自动推进");
        if (actor.external()) requireExternalCandidate(task.getId(), actor);
        else requireCandidate(access, task.getId());

        // Flow 节点字段变更仍走统一记录保存链，继续执行字段权限、校验、日志和后续绑定。
        Map<String, Object> configuredUpdates = applyConfiguredFieldUpdates(
                systemId, tenantId, moduleId, recordId, actor.accountId(), task);

        UnFlowAction action = new UnFlowAction();
        action.setExampleId(exampleId);
        action.setTaskId(task.getId());
        action.setApplicationId(actor.applicationId());
        action.setActorAccountId(actor.accountId());
        action.setActorExternalId(actor.externalId());
        action.setActionCode(actionCode);
        action.setRequestId(request.requestId());
        action.setResultCode("SUCCESS");
        action.setPayloadJson(writePayload(request.payload()));
        action.setCreateTime(LocalDateTime.now());
        actions.addOrUpdate(action);

        task.setStatus("COMPLETED");
        task.setFinishTime(LocalDateTime.now());
        tasks.addOrUpdate(task);
        if (actor.external()) completeExternalCandidate(task.getId(), actor);
        else completeCandidate(task.getId(), actor.accountId());
        Map<String, Object> routeValues = new LinkedHashMap<>(runtimeValues(example));
        routeValues.putAll(configuredUpdates);
        // Flow 节点字段变更先写入业务记录，同时更新 Flow 自己的运行快照，后续节点和审批回调不能读回旧值。
        mergeRuntimeValues(example, configuredUpdates);
        routeValues.put("__flowAction", actionCode);
        List<ExamineStartContext> examineStarts = route(example, task, action, actor, routeValues);
        List<String> pending = pendingNodeCodes(exampleId);
        example.setCurrentNodesJson(writePayload(pending));
        if (pending.isEmpty() && "RUNNING".equals(example.getStatus())) {
            example.setStatus("COMPLETED");
            example.setFinishTime(LocalDateTime.now());
            addEvent(example, task, action, "FLOW_COMPLETE", actor.accountId(), actor.externalId(),
                    Map.of("nodeCode", task.getNodeCode(), "actionCode", actionCode));
        }
        example.setUpdateTime(LocalDateTime.now());
        examples.addOrUpdate(example);
        if (pending.isEmpty()) publishFlowCompleted(example, actor, routeValues,
                Map.of("nodeCode", task.getNodeCode(), "actionCode", actionCode));
        startExamine(examineStarts);
        return new ActionView(action.getId(), exampleId, task.getId(), actionCode, "SUCCESS",
                view(examples.getById(exampleId), actor));
    }

    /** Flow 运行服务被模块内部直接调用时也必须拒绝空或负数主键。 */
    private void requirePositive(Long value, String message) {
        if (value == null || value < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, message);
    }

    /** 阶段发布快照决定是否开放人工办理，普通 Flow 节点不受此配置影响。 */
    private boolean manualStageEnabled(UnFlowTask task) {
        try {
            JsonNode node=json.readTree(task.getNodeSnapshotJson());
            return node==null || node.path("manualEnabled").asBoolean(true);
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"阶段待办快照无效",ex);
        }
    }

    /** 幂等重试只能由原处理人继续读取；系统或租户管理员可按管理身份代查。 */
    private boolean sameActionActor(UnFlowAction previous, ModulePermissionService.Access access,
                                    Actor actor) {
        if (actor.external()) {
            return Objects.equals(previous.getApplicationId(), actor.applicationId())
                    && Objects.equals(previous.getActorExternalId(), actor.externalId());
        }
        if (access != null && (access.context().owner() || access.context().tenantAdmin())) return true;
        return Objects.equals(previous.getActorAccountId(), actor.accountId());
    }

    private ModulePermissionService.Access accessRecord(String token, Long systemId, Long tenantId,
                                                        Long moduleId, Long recordId) {
        var access = permissions.require(token, systemId, tenantId, moduleId, "DETAIL");
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !Objects.equals(record.getSystemId(), systemId)
                || !Objects.equals(record.getTenantId(), access.context().tenantId())
                || !Objects.equals(record.getModuleId(), moduleId)
                || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务记录不存在");
        return access;
    }

    private UnFlowExample example(Long exampleId, Long systemId, Long tenantId, Long moduleId, Long recordId) {
        UnFlowExample example = examples.getById(exampleId);
        if (example == null || !Objects.equals(example.getSystemId(), systemId)
                || !Objects.equals(example.getTenantId(), tenantId)
                || !Objects.equals(example.getModuleId(), moduleId)
                || !Objects.equals(example.getRecordId(), recordId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 实例不存在");
        return example;
    }

    /** 办理动作在同一实例行上串行化，避免两个处理人同时完成同一个待办并重复推进线路。 */
    private UnFlowExample lockedExample(Long exampleId, Long systemId, Long tenantId,
                                        Long moduleId, Long recordId) {
        UnFlowExample example = examples.getOne(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getId, exampleId).last("FOR UPDATE"), false);
        if (example == null || !Objects.equals(example.getSystemId(), systemId)
                || !Objects.equals(example.getTenantId(), tenantId)
                || !Objects.equals(example.getModuleId(), moduleId)
                || !Objects.equals(example.getRecordId(), recordId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 实例不存在");
        return example;
    }

    private UnFlowExample platformExample(Long exampleId) {
        UnFlowExample example = examples.getById(exampleId);
        if (example == null || !"PLATFORM".equals(example.getScopeType())
                || example.getSystemId() != null || example.getTenantId() != null
                || example.getModuleId() != null || example.getRecordId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 实例不存在");
        return example;
    }

    /** 平台 Flow 办理与系统 Flow 使用相同的实例行锁语义。 */
    private UnFlowExample lockedPlatformExample(Long exampleId) {
        UnFlowExample example = examples.getOne(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getId, exampleId).last("FOR UPDATE"), false);
        if (example == null || !"PLATFORM".equals(example.getScopeType())
                || example.getSystemId() != null || example.getTenantId() != null
                || example.getModuleId() != null || example.getRecordId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 实例不存在");
        return example;
    }

    private void requireCandidate(ModulePermissionService.Access access, Long taskId) {
        if (access.context().owner() || access.context().tenantAdmin()) return;
        long accountId = access.accountId();
        if (candidates.count(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                .eq(UnFlowTaskCandidate::getTaskId, taskId)
                .eq(UnFlowTaskCandidate::getAccountId, accountId)
                .eq(UnFlowTaskCandidate::getStatus, "PENDING")) == 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号不是该 Flow 待办处理人");
    }

    /** 外部 Flow 只能办理发布快照中属于本应用的同一外部身份。 */
    private void requireExternalCandidate(Long taskId, Actor actor) {
        if (candidates.count(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                .eq(UnFlowTaskCandidate::getTaskId, taskId)
                .eq(UnFlowTaskCandidate::getApplicationId, actor.applicationId())
                .eq(UnFlowTaskCandidate::getExternalId, actor.externalId())
                .eq(UnFlowTaskCandidate::getStatus, "PENDING")) == 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前外部身份不是该 Flow 待办处理人");
    }

    private List<ExamineStartContext> route(UnFlowExample example, UnFlowTask task, UnFlowAction action, Actor actor,
                                           Map<String, Object> values) {
        JsonNode definition = runtimeDefinition(example);
        Map<String, JsonNode> nodeByCode = PublishedProcessSnapshot.nodes(json, definition.toString()).stream()
                .collect(Collectors.toMap(PublishedProcessSnapshot::code, node -> node, (left, right) -> left, LinkedHashMap::new));
        List<JsonNode> next = new ArrayList<>();
        JsonNode edgesNode = PublishedProcessSnapshot.edges(json, definition.toString());
        if (edgesNode != null && edgesNode.isArray()) {
            List<JsonNode> candidatesEdges = new ArrayList<>();
            edgesNode.forEach(edge -> {
                if (task.getNodeCode().equals(edge.path("sourceNodeCode").asText("")))
                    candidatesEdges.add(edge);
            });
            candidatesEdges.sort(java.util.Comparator.comparingInt(edge -> edge.path("priorityNo").asInt(0)));
            Set<String> added = new HashSet<>();
            for (JsonNode edge : candidatesEdges) {
                String condition = edge.has("condition") ? edge.get("condition").toString() : null;
                if (!matchesCondition(condition, values)) continue;
                String target = edge.path("targetNodeCode").asText("");
                JsonNode node = nodeByCode.get(target);
                if (node != null && added.add(target)) next.add(node);
                if (edge.path("exclusiveFlag").asBoolean(true)) break;
            }
        }
        List<ExamineStartContext> examineStarts = new ArrayList<>();
        for (JsonNode node : next) {
            String type = PublishedProcessSnapshot.type(node).toUpperCase(Locale.ROOT);
            if (Set.of("END", "FINISH", "TERMINATE").contains(type)) continue;
            ExamineStartContext examineStart = createTask(example, node, values, actor);
            if (examineStart != null) examineStarts.add(examineStart);
            addEvent(example, null, action, "NODE_ENTER", actor.accountId(), actor.externalId(), node);
        }
        return examineStarts;
    }

    /** 在 Flow 实例状态已持久化为完成后发布本地扩展事件，监听器读取到的是最终运行态。 */
    private void publishFlowCompleted(UnFlowExample example, Actor actor, Map<String, Object> values,
                                      Map<String, Object> detail) {
        Map<String, Object> eventDetail = new LinkedHashMap<>(detail == null ? Map.of() : detail);
        eventDetail.put("flowId", example.getFlowId());
        eventDetail.put("flowVersionId", example.getFlowVersionId());
        eventPublisher.publish(new BusinessEventContext("FLOW_COMPLETED", example.getSystemId(),
                example.getTenantId(), example.getModuleId(), example.getRecordId(), actor.accountId(),
                null, values, eventDetail));
    }

    private ExamineStartContext createTask(UnFlowExample example, JsonNode node, Map<String, Object> values,
                                           Actor actor) {
        UnFlowTask task = new UnFlowTask();
        task.setExampleId(example.getId());
        task.setNodeCode(PublishedProcessSnapshot.code(node));
        // 审批拒绝等线路可重新进入同一阶段；轮次按当前实例已有节点递增，保留每次待办历史。
        int previousRound=tasks.list(new LambdaQueryWrapper<UnFlowTask>()
                .eq(UnFlowTask::getExampleId,example.getId())
                .eq(UnFlowTask::getNodeCode,task.getNodeCode()))
                .stream().map(UnFlowTask::getRoundNo).filter(Objects::nonNull)
                .mapToInt(Integer::intValue).max().orElse(0);
        task.setRoundNo(previousRound+1);
        Long examineId = PublishedProcessSnapshot.examineId(node);
        String configuredType = node.path("taskType").asText(
                PublishedProcessSnapshot.type(node).toUpperCase(Locale.ROOT));
        task.setTaskType(examineId == null ? configuredType : "EXAMINE_WAIT");
        task.setStatus("PENDING");
        task.setNodeSnapshotJson(node.toString());
        task.setCreateTime(LocalDateTime.now());
        tasks.addOrUpdate(task);
        if (examineId != null) {
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("flowExampleId", example.getId());
            metadata.put("flowTaskId", task.getId());
            metadata.put("flowNodeCode", task.getNodeCode());
            // 后续节点由实际办理人发起；外部应用身份必须进入审批实例和终态 Flow 回调。
            if (actor.external()) {
                metadata.put("initiatorApplicationId", actor.applicationId());
                metadata.put("initiatorExternalId", actor.externalId());
            }
            return new ExamineStartContext(example.getSystemId(), example.getTenantId(),
                    example.getModuleId(), example.getRecordId(), actor.accountId(), examineId, values, metadata);
        }
        Set<Long> accountCandidates = candidateResolver.resolve(node,
                new ExamineCandidateContext(example.getSystemId(), example.getTenantId(), example.getModuleId(),
                        example.getRecordId(), example.getInitiatorAccountId(), values));
        List<PublishedProcessSnapshot.ExternalCandidate> externalCandidates =
                PublishedProcessSnapshot.externalCandidates(node).stream().toList();
        if (accountCandidates.isEmpty() && externalCandidates.isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    "Flow 节点未解析出有效处理人: " + PublishedProcessSnapshot.code(node));
        for (Long accountId : accountCandidates) {
            UnFlowTaskCandidate candidate = new UnFlowTaskCandidate();
            candidate.setTaskId(task.getId());
            candidate.setCandidateType("ACCOUNT");
            candidate.setAccountId(accountId);
            candidate.setStatus("PENDING");
            candidates.addOrUpdate(candidate);
            if ("PLATFORM".equals(example.getScopeType()))
                NotificationAfterCommit.dispatch(() ->
                        platformMessages.flowTaskAvailable(accountId, task.getId(), task.getNodeCode()));
            else
                NotificationAfterCommit.dispatch(() ->
                        systemMessages.flowTaskAvailable(example.getSystemId(), example.getTenantId(),
                                accountId, task.getId(), task.getNodeCode()));
        }
        for (PublishedProcessSnapshot.ExternalCandidate external : externalCandidates) {
            UnFlowTaskCandidate candidate = new UnFlowTaskCandidate();
            candidate.setTaskId(task.getId());
            candidate.setCandidateType("EXTERNAL");
            candidate.setApplicationId(external.applicationId());
            candidate.setExternalId(external.externalId());
            candidate.setStatus("PENDING");
            candidates.addOrUpdate(candidate);
        }
        return null;
    }

    /** 当前 Flow 状态写入同一事务后再发起审批，允许自动节点同步回调并继续推进。 */
    private void startExamine(List<ExamineStartContext> examineStarts) {
        for (ExamineStartContext examineStart : examineStarts)
            for (ExamineStarter starter : examineStarters) starter.start(examineStart);
    }

    /**
     * examine 结束后由跨模块端口回调；Flow 只把审批结果当作事件条件，继续自己的线路。
     * 审批通过、拒绝和取消的业务含义由 examine 负责，这里不再提供对应的 Flow 办理动作。
     */
    @Transactional(rollbackFor = Exception.class)
    public void afterExamine(ExamineCompletionContext context) {
        if (context.status() == null || !Set.of("COMPLETED", "REJECTED", "CANCELLED")
                .contains(context.status().toUpperCase(Locale.ROOT)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批终态不支持 Flow 回调: " + context.status());
        Long flowExampleId = longValue(context.metadata().get("flowExampleId"));
        Long flowTaskId = longValue(context.metadata().get("flowTaskId"));
        if (flowExampleId == null || flowTaskId == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批回调缺少 Flow 实例上下文");
        Long completionApplicationId = longValue(context.metadata().get("completionApplicationId"));
        String completionExternalId = textValue(context.metadata().get("completionExternalId"));
        if ((completionApplicationId == null) != (completionExternalId == null))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批回调的外部办理身份不完整");
        Actor completionActor = new Actor(context.accountId(), completionApplicationId, completionExternalId);
        // 审批终态与人工办理共用实例行锁，多个并行审批节点完成时不能互相覆盖当前节点快照。
        UnFlowExample example = examples.getOne(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getId, flowExampleId).last("FOR UPDATE"), false);
        if (example == null || !Objects.equals(example.getSystemId(), context.systemId())
                || !Objects.equals(example.getTenantId(), context.tenantId())
                || !Objects.equals(example.getModuleId(), context.moduleId())
                || !Objects.equals(example.getRecordId(), context.recordId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批回调关联的 Flow 实例不存在");
        UnFlowTask task = tasks.getById(flowTaskId);
        if (task == null || !flowExampleId.equals(task.getExampleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批回调关联的 Flow 待办不存在");
        String requestId = "examine:" + context.exampleId() + ":" + context.status();
        UnFlowAction previous = actions.getOne(new LambdaQueryWrapper<UnFlowAction>()
                .eq(UnFlowAction::getExampleId, flowExampleId).eq(UnFlowAction::getRequestId, requestId));
        if (previous != null) return;
        if (!"RUNNING".equals(example.getStatus())) return;
        if (!"EXAMINE_WAIT".equalsIgnoreCase(task.getTaskType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 待办不是 examine 等待节点");
        if (!"PENDING".equals(task.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 审批等待节点已处理");

        UnFlowAction action = new UnFlowAction();
        action.setExampleId(flowExampleId);
        action.setTaskId(flowTaskId);
        action.setApplicationId(completionActor.applicationId());
        action.setActorAccountId(completionActor.accountId());
        action.setActorExternalId(completionActor.externalId());
        action.setActionCode("EXAMINE_" + context.status().toUpperCase(Locale.ROOT));
        action.setRequestId(requestId);
        action.setResultCode("SUCCESS");
        action.setPayloadJson(writePayload(Map.of("examineExampleId", context.exampleId(),
                "examineStatus", context.status(), "metadata", context.metadata())));
        action.setCreateTime(LocalDateTime.now());
        actions.addOrUpdate(action);

        Map<String, Object> completionDetail = new LinkedHashMap<>();
        completionDetail.put("examineExampleId", context.exampleId());
        completionDetail.put("examineStatus", context.status());
        completionDetail.put("metadata", context.metadata());
        addEvent(example, task, action, "EXAMINE_" + context.status().toUpperCase(Locale.ROOT),
                completionActor.accountId(), completionActor.externalId(), completionDetail);

        task.setStatus("COMPLETED");
        task.setFinishTime(LocalDateTime.now());
        tasks.addOrUpdate(task);
        completeAllCandidates(flowTaskId);
        // 审批通过后才执行 examine 所在 Flow 节点配置的字段变更；拒绝或取消只按线路事件回退/结束，
        // 不把未配置为业务结果的节点变更写入记录；写回复用审批自动节点入口，保持类型、唯一、槽位和日志规则一致。
        Map<String, Object> configuredUpdates = "COMPLETED".equalsIgnoreCase(context.status())
                ? applyConfiguredFieldUpdates(context.systemId(), context.tenantId(), context.moduleId(),
                        context.recordId(), completionActor.accountId(), task)
                : Map.of();
        boolean approved = "COMPLETED".equalsIgnoreCase(context.status());
        // 审批拒绝或取消时，节点期间的临时修改不进入 Flow 快照，回退线路继续使用审批前的业务值。
        Map<String, Object> values = new LinkedHashMap<>(approved
                ? context.values() == null ? Map.of() : context.values() : runtimeValues(example));
        values.putAll(configuredUpdates);
        values.put("__examineStatus", context.status());
        values.put("__flowEvent", "EXAMINE_" + context.status().toUpperCase(Locale.ROOT));
        // 审批通过时把审批快照和节点变更的最终值写入 Flow 运行快照，后续节点读取同一份最终值。
        if (approved) mergeRuntimeValues(example, context.values());
        mergeRuntimeValues(example, configuredUpdates);
        List<ExamineStartContext> examineStarts = route(example, task, action, completionActor, values);
        List<String> pending = pendingNodeCodes(flowExampleId);
        example.setCurrentNodesJson(writePayload(pending));
        if (pending.isEmpty()) {
            example.setStatus("COMPLETED");
            example.setFinishTime(LocalDateTime.now());
            addEvent(example, task, action, "FLOW_COMPLETE", completionActor.accountId(),
                    completionActor.externalId(), completionDetail);
        }
        example.setUpdateTime(LocalDateTime.now());
        examples.addOrUpdate(example);
        if (pending.isEmpty()) publishFlowCompleted(example, completionActor, values, completionDetail);
        startExamine(examineStarts);
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    /** 读取回调元数据中的外部身份文本，空白值按缺失处理。 */
    private String textValue(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** Flow 节点配置的字段变更走受控内部写回链，保证校验、槽位、日志和公式一致，并避免再次触发同一 UPDATE Flow。 */
    private Map<String, Object> applyConfiguredFieldUpdates(Long systemId, Long tenantId, Long moduleId,
                                             Long recordId, Long accountId, UnFlowTask task) {
        // 平台 Flow 没有关联业务模块，节点字段变更只保留在运行快照，不能写入模块记录。
        if (systemId == null || moduleId == null || recordId == null) return Map.of();
        try {
            JsonNode node = json.readTree(task.getNodeSnapshotJson());
            JsonNode updates = node.get("fieldUpdates");
            if (updates == null) updates = node.get("field_updates");
            if (updates == null) updates = node.path("config").get("fieldUpdates");
            if (updates == null) updates = node.path("config").get("field_updates");
            if (updates == null || !updates.isObject()) return Map.of();
            Map<String, Object> values = new LinkedHashMap<>();
            updates.fields().forEachRemaining(entry -> values.put(entry.getKey(), json.convertValue(entry.getValue(), Object.class)));
            if (!values.isEmpty()) return moduleRecords.saveByProcess(new ExamineBusinessUpdateContext(
                    systemId, tenantId, moduleId, recordId, accountId,
                    "FLOW_FIELD_UPDATE", values));
            return Map.of();
        } catch (BaseException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 节点字段变更配置无效", ex);
        }
    }

    private void completeAllCandidates(Long taskId) {
        candidates.list(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                        .eq(UnFlowTaskCandidate::getTaskId, taskId)
                        .eq(UnFlowTaskCandidate::getStatus, "PENDING"))
                .forEach(candidate -> { candidate.setStatus("COMPLETED"); candidates.addOrUpdate(candidate); });
    }


    /** 办理后关闭当前账号的候选待办，历史关系仍保留。 */
    private void completeCandidate(Long taskId, Long accountId) {
        candidates.list(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                        .eq(UnFlowTaskCandidate::getTaskId, taskId)
                        .eq(UnFlowTaskCandidate::getAccountId, accountId)
                        .eq(UnFlowTaskCandidate::getStatus, "PENDING"))
                .forEach(candidate -> { candidate.setStatus("COMPLETED"); candidates.addOrUpdate(candidate); });
    }

    private void completeExternalCandidate(Long taskId, Actor actor) {
        candidates.list(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                        .eq(UnFlowTaskCandidate::getTaskId, taskId)
                        .eq(UnFlowTaskCandidate::getApplicationId, actor.applicationId())
                        .eq(UnFlowTaskCandidate::getExternalId, actor.externalId())
                        .eq(UnFlowTaskCandidate::getStatus, "PENDING"))
                .forEach(candidate -> { candidate.setStatus("COMPLETED"); candidates.addOrUpdate(candidate); });
    }

    private List<String> pendingNodeCodes(Long exampleId) {
        return tasks.list(new LambdaQueryWrapper<UnFlowTask>().eq(UnFlowTask::getExampleId, exampleId)
                        .eq(UnFlowTask::getStatus, "PENDING")).stream().map(UnFlowTask::getNodeCode).toList();
    }

    private void addEvent(UnFlowExample example, UnFlowTask task, UnFlowAction action,
                          String code, Long actorAccountId, Object detail) {
        addEvent(example, task, action, code, actorAccountId, null, detail);
    }

    private void addEvent(UnFlowExample example, UnFlowTask task, UnFlowAction action,
                          String code, Long actorAccountId, String actorExternalId, Object detail) {
        UnFlowEvent event = new UnFlowEvent();
        event.setExampleId(example.getId());
        event.setTaskId(task == null ? null : task.getId());
        event.setActionId(action == null ? null : action.getId());
        event.setEventCode(code);
        event.setActorAccountId(actorAccountId);
        event.setActorExternalId(actorExternalId);
        event.setDetailJson(writePayload(detail));
        event.setCreateTime(LocalDateTime.now());
        events.addOrUpdate(event);
    }

    /** Flow 节点办理人；外部身份必须带应用命名空间。 */
    private record Actor(Long accountId, Long applicationId, String externalId) {
        boolean external() { return applicationId != null && externalId != null; }
    }

    private ExampleView view(UnFlowExample example) {
        return view(example, null);
    }

    /** 外部实例视图只展示调用者自己的候选身份，不能暴露同节点其他处理人。 */
    private ExampleView view(UnFlowExample example, Actor actor) {
        List<TaskView> taskViews = tasks.list(new LambdaQueryWrapper<UnFlowTask>()
                        .eq(UnFlowTask::getExampleId, example.getId()).orderByAsc(UnFlowTask::getId)).stream()
                .map(task -> {
                    List<UnFlowTaskCandidate> pending = candidates.list(new LambdaQueryWrapper<UnFlowTaskCandidate>()
                            .eq(UnFlowTaskCandidate::getTaskId, task.getId())
                            .eq(UnFlowTaskCandidate::getStatus, "PENDING"));
                    return new TaskView(task.getId(), task.getNodeCode(), task.getRoundNo(), task.getTaskType(),
                            task.getStatus(), task.getDueTime(), pending.stream()
                                    .filter(item -> actor == null || !actor.external())
                                    .map(UnFlowTaskCandidate::getAccountId)
                                    .filter(Objects::nonNull).toList(),
                            pending.stream().filter(item -> item.getExternalId() != null
                                            && (actor == null || !actor.external()
                                            || Objects.equals(item.getApplicationId(), actor.applicationId())
                                            && Objects.equals(item.getExternalId(), actor.externalId())))
                                    .map(item -> new ExternalCandidateView(item.getApplicationId(), item.getExternalId())).toList());
                })
                .toList();
        return new ExampleView(example.getId(), example.getFlowId(), example.getFlowVersionId(),
                example.getBindingId(), example.getModuleId(),
                example.getRecordId(), example.getStatus(), example.getStartTime(), example.getFinishTime(), taskViews);
    }

    private Map<String, Object> runtimeValues(UnFlowExample example) {
        try {
            JsonNode values = json.readTree(example.getRuntimeSnapshotJson()).path("values");
            return json.convertValue(values, new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 运行快照中的业务值无效", ex);
        }
    }

    /** 读取 Flow 启动快照中的外部应用幂等请求号。 */
    private String runtimeRequestId(UnFlowExample example) {
        try { return json.readTree(example.getRuntimeSnapshotJson()).path("requestId").asText(null); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 运行快照无效"); }
    }

    /** 合并当前节点产生的业务值到不可变定义对应的运行快照，供后续节点继续使用。 */
    private void mergeRuntimeValues(UnFlowExample example, Map<String, Object> updates) {
        if (updates == null || updates.isEmpty()) return;
        try {
            ObjectNode root = (ObjectNode) json.readTree(example.getRuntimeSnapshotJson());
            ObjectNode values = root.with("values");
            updates.forEach((key, value) -> values.set(key, json.valueToTree(value)));
            example.setRuntimeSnapshotJson(json.writeValueAsString(root));
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 运行快照业务值无法更新", ex);
        }
    }

    private JsonNode runtimeDefinition(UnFlowExample example) {
        try {
            return json.readTree(example.getRuntimeSnapshotJson()).path("definition");
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 运行快照中的流程定义无效", ex);
        }
    }

    /** 运行时条件已经来自发布快照；快照损坏时统一报告为业务配置冲突。 */
    private boolean matchesCondition(String condition, Map<String, Object> values) {
        try {
            return TriggerConditionMatcher.matches(json, condition, values);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 运行快照中的线路条件无效", ex);
        }
    }

    private String writePayload(Object payload) {
        try { return json.writeValueAsString(payload == null ? Map.of() : payload); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 操作内容格式无效", ex); }
    }
}
