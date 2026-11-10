package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.unexamine.core.module.ModuleRecordAccess;
import com.unique.unexamine.core.module.ExamineBusinessDataUpdater;
import com.unique.unexamine.core.module.ExamineBusinessUpdateContext;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ExamineCompletionHandler;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.core.module.ExamineTodoNotifier;
import com.unique.unexamine.core.module.ExamineStartContext;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.NotificationAfterCommit;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import com.unique.unexamine.examine.base.entity.UnExamineExampleNode;
import com.unique.unexamine.examine.base.entity.UnExamineExampleNodeUser;
import com.unique.unexamine.examine.base.service.IUnExamineExampleNodeService;
import com.unique.unexamine.examine.base.service.IUnExamineExampleNodeUserService;
import com.unique.unexamine.examine.base.service.IUnExamineExampleLogService;
import com.unique.unexamine.examine.base.service.IUnExamineExampleService;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionRequest;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ActionView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExampleView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.NodeView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.UserView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.LogView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.FieldChangeView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.ExternalTodoView;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.SystemExternalTodoView;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.examine.manage.strategy.ExamineNodeStrategy;
import com.unique.unexamine.examine.manage.strategy.ExamineNodeStrategyResolver;
import com.unique.unexamine.examine.manage.strategy.ExamineAutoNodeContext;
import com.unique.unexamine.examine.manage.strategy.ExamineAutoNodeStrategy;
import com.unique.unexamine.examine.manage.dto.ExamineRuntimeDto.StartRequest;
import com.unique.unexamine.plat.manage.service.AuthManageService;
import com.unique.unexamine.plat.base.entity.UnPlatAccount;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
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
 * 审批运行实例查询与办理。
 *
 * <p>审批实例只使用发起时保存的发布快照。办理人必须是当前节点的待处理人；节点完成后按快照
 * 连线条件创建下一节点，实例状态、节点状态和处理人快照在一个事务内提交。</p>
 */
@Service
public class ExamineRuntimeService {
    private final ModuleRecordAccess recordAccess;
    private final IUnExamineExampleService examples;
    private final IUnExamineExampleNodeService nodes;
    private final IUnExamineExampleNodeUserService users;
    private final IUnExamineExampleLogService logs;
    private final ObjectMapper json;
    private final ExamineNodeStrategyResolver strategies;
    private final List<ExamineCompletionHandler> completionHandlers;
    private final List<ExamineCandidateResolver> candidateResolvers;
    private final List<ExamineAutoNodeStrategy> autoNodeStrategies;
    private final List<ExamineBusinessDataUpdater> dataUpdaters;
    private final AuthManageService auth;
    private final IUnPlatAccountService accounts;
    private final ModuleExamineProcessHandler processHandler;
    private final BusinessEventPublisher eventPublisher;
    private final List<ExamineTodoNotifier> todoNotifiers;

    public ExamineRuntimeService(ModuleRecordAccess recordAccess, IUnExamineExampleService examples,
                                 IUnExamineExampleNodeService nodes,
                                 IUnExamineExampleNodeUserService users, ObjectMapper json,
                                 ExamineNodeStrategyResolver strategies,
                                 List<ExamineCompletionHandler> completionHandlers,
                                 IUnExamineExampleLogService logs,
                                 List<ExamineCandidateResolver> candidateResolvers,
                                 List<ExamineAutoNodeStrategy> autoNodeStrategies,
                                 List<ExamineBusinessDataUpdater> dataUpdaters,
                                 AuthManageService auth, IUnPlatAccountService accounts,
                                 ModuleExamineProcessHandler processHandler,
                                 BusinessEventPublisher eventPublisher,
                                 List<ExamineTodoNotifier> todoNotifiers) {
        this.recordAccess = recordAccess;
        this.examples = examples;
        this.nodes = nodes;
        this.users = users;
        this.logs = logs;
        this.json = json;
        this.strategies = strategies;
        this.completionHandlers = completionHandlers == null ? List.of() : List.copyOf(completionHandlers);
        this.candidateResolvers = candidateResolvers == null ? List.of() : List.copyOf(candidateResolvers);
        this.autoNodeStrategies = autoNodeStrategies == null ? List.of() : List.copyOf(autoNodeStrategies);
        this.dataUpdaters = dataUpdaters == null ? List.of() : List.copyOf(dataUpdaters);
        this.auth = auth;
        this.accounts = accounts;
        this.processHandler = processHandler;
        this.eventPublisher = eventPublisher;
        this.todoNotifiers = todoNotifiers == null ? List.of() : List.copyOf(todoNotifiers);
    }

    /** 查询记录关联的审批实例。 */
    public List<ExampleView> listForRecord(String token, Long systemId, Long tenantId,
                                           Long moduleId, Long recordId) {
        ModuleRecordAccess.Access access = recordAccess.require(token, systemId, tenantId, moduleId, recordId);
        Actor actor = new Actor(access.accountId(), null, null);
        return examples.list(new LambdaQueryWrapper<UnExamineExample>()
                        .eq(UnExamineExample::getSystemId, systemId)
                        .eq(UnExamineExample::getTenantId, access.tenantId())
                        .eq(UnExamineExample::getModuleId, moduleId)
                        .eq(UnExamineExample::getRecordId, recordId)
                        .orderByDesc(UnExamineExample::getStartTime))
                .stream().map(example -> viewForActor(example, actor, access.owner() || access.tenantAdmin())).toList();
    }

    /**
     * 查询当前系统租户中登录人的审批待办。
     *
     * <p>实例节点的可见字段仍由发起时快照决定；已被逻辑删除的业务记录在返回前过滤，
     * 但审批实例和日志不被删除。分页使用公共 SearchBO 和 MyBatis-Plus Page。</p>
     */
    public Page<ExampleView> pending(
            String token, Long systemId, Long tenantId, SearchBO search) {
        ModuleRecordAccess.Access context = recordAccess.requireProcessContext(token, systemId, tenantId);
        Long accountId = context.accountId();
        var where = new LambdaQueryWrapper<UnExamineExample>()
                .eq(UnExamineExample::getSystemId, context.systemId())
                .eq(UnExamineExample::getTenantId, context.tenantId())
                .eq(UnExamineExample::getStatus, "RUNNING")
                // 先在数据库分页阶段排除已删除业务记录，避免审批待办 total 包含不可办理实例。
                .apply("EXISTS (SELECT 1 FROM un_module_record r WHERE r.id=un_examine_example.record_id "
                        + "AND r.system_id={0} AND r.tenant_id={1} AND r.module_id=un_examine_example.module_id "
                        + "AND r.deleted_time IS NULL)", context.systemId(), context.tenantId())
                .inSql(UnExamineExample::getId,
                        "SELECT n.example_id FROM un_examine_example_node n "
                                + "JOIN un_examine_example_node_user u ON u.example_node_id=n.id "
                                + "WHERE n.deleted_time IS NULL AND n.status='PENDING' "
                                + "AND u.deleted_time IS NULL AND u.status='PENDING' AND u.account_id=" + accountId)
                .orderByDesc(UnExamineExample::getStartTime)
                .orderByDesc(UnExamineExample::getId);
        // 审批记录权限按实例所属模块动态判断，先扫描候选待办再切页，保证 total 与可见记录一致。
        Page<ExampleView> requested = search == null ? new SearchBO().parse() : search.parse();
        List<UnExamineExample> visible = new ArrayList<>();
        long rawTotal = 0;
        for (long rawPageNo = 1; ; rawPageNo++) {
            Page<UnExamineExample> rawPage = Page.of(rawPageNo, 100);
            rawPage.setSearchCount(rawPageNo == 1);
            rawPage = examples.page(rawPage, where);
            if (rawPageNo == 1) rawTotal = rawPage.getTotal();
            rawPage.getRecords().stream()
                    .filter(example -> recordInScope(token, context, example))
                    .forEach(visible::add);
            if (rawPage.getRecords().isEmpty() || rawPageNo * 100 >= rawTotal) break;
        }
        long from = Math.min((requested.getCurrent() - 1) * requested.getSize(), visible.size());
        long to = Math.min(from + requested.getSize(), visible.size());
        List<ExampleView> views = visible.subList((int) from, (int) to).stream()
                .map(example -> viewForActor(example, new Actor(accountId, null, null),
                        context.owner() || context.tenantAdmin())).toList();
        return new Page<ExampleView>(requested.getCurrent(), requested.getSize(), visible.size()).setRecords(views);
    }

    /** 业务记录被删除或实例快照边界异常时只从工作台隐藏，不吞掉其他配置错误。 */
    private boolean recordInScope(String token, ModuleRecordAccess.Access context,
                                  UnExamineExample example) {
        if (example.getModuleId() == null || example.getRecordId() == null) return false;
        try {
            recordAccess.requireProcessBoundary(token, context.systemId(), context.tenantId(),
                    example.getModuleId(), example.getRecordId());
            return true;
        } catch (BaseException ex) {
            if (ex.getCode() == SystemCodeEnum.SYSTEM_NO_FOUND.getCode()
                    || ex.getCode() == SystemCodeEnum.SYSTEM_NO_AUTH.getCode()) return false;
            throw ex;
        }
    }

    /** 查询单个审批实例。 */
    public ExampleView query(String token, Long systemId, Long tenantId, Long moduleId, Long recordId,
                             Long exampleId) {
        // 工作台打开审批实例时同样按审批快照读取字段，不要求办理人拥有详情菜单。
        ModuleRecordAccess.Access access = recordAccess.requireProcessBoundary(token, systemId, tenantId,
                moduleId, recordId);
        return viewForActor(example(exampleId, systemId, access.tenantId(), moduleId, recordId),
                new Actor(access.accountId(), null, null), access.owner() || access.tenantAdmin());
    }

    /**
     * 应用授权后的外部审批实例查询；调用方的应用数据范围在接入层校验，实例边界仍必须完全匹配请求上下文。
     */
    public ExampleView queryExternal(Long applicationId, String externalId, Long systemId, Long tenantId,
                                     Long moduleId, Long recordId, Long examineId, Long exampleId) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部审批身份无效");
        requireExternalContext(systemId, tenantId, moduleId, recordId);
        requirePositive(examineId, "审批定义主键无效");
        requirePositive(exampleId, "审批实例主键无效");
        UnExamineExample current = example(exampleId, systemId, tenantId, moduleId, recordId);
        if (!Objects.equals(current.getExamineId(), examineId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批实例不属于该定义");
        Actor actor = new Actor(null, applicationId, externalId);
        requireExternalParticipant(current, actor);
        return viewForActor(current, actor, false);
    }

    /** 平台应用按审批定义授权查询实例，实例主键不能代替定义授权主键。 */
    public ExampleView queryPlatformExternal(Long applicationId, String externalId, Long examineId,
                                             Long exampleId) {
        requirePositive(examineId, "平台审批定义主键无效");
        requirePositive(exampleId, "平台审批实例主键无效");
        return queryExternal(applicationId, externalId, null, null, null, null, examineId, exampleId);
    }

    /** 办理审批节点并继续审批线路。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView action(String token, Long systemId, Long tenantId, Long moduleId, Long recordId,
                             Long exampleId, Long nodeId, ActionRequest request) {
        // 审批办理按节点快照执行，不能再次叠加模块详情动作和业务数据权限。
        ModuleRecordAccess.Access access = recordAccess.requireProcessBoundary(token, systemId, tenantId,
                moduleId, recordId);
        return actionWithAccess(access, systemId, access.tenantId(), moduleId, recordId, exampleId, nodeId,
                request, new Actor(access.accountId(), null, null));
    }

    /**
     * 应用授权后的外部审批办理；外部身份必须与实例节点快照中的应用和标识同时匹配。
     * 记录边界由应用接入层先校验，审批服务只接受已核对的系统租户上下文。
     */
    @Transactional(rollbackFor = Exception.class)
    public ActionView actionExternal(Long applicationId, String externalId, Long systemId, Long tenantId,
                                     Long moduleId, Long recordId, Long examineId, Long exampleId, Long nodeId,
                                     ActionRequest request) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部审批身份无效");
        requireExternalContext(systemId, tenantId, moduleId, recordId);
        requirePositive(examineId, "审批定义主键无效");
        requirePositive(exampleId, "审批实例主键无效");
        requirePositive(nodeId, "审批节点主键无效");
        UnExamineExample current = example(exampleId, systemId, tenantId, moduleId, recordId);
        if (!Objects.equals(current.getExamineId(), examineId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批实例不属于该定义");
        ModuleRecordAccess.Access access = new ModuleRecordAccess.Access(systemId, tenantId, null, false, false);
        return actionWithAccess(access, systemId, tenantId, moduleId, recordId, exampleId, nodeId,
                request, new Actor(null, applicationId, externalId));
    }

    /** 平台应用办理前核对实例所属定义，避免用一条定义的授权办理另一条审批。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView actionPlatformExternal(Long applicationId, String externalId, Long examineId,
                                             Long exampleId, Long nodeId, ActionRequest request) {
        requirePositive(examineId, "平台审批定义主键无效");
        requirePositive(exampleId, "平台审批实例主键无效");
        return actionExternal(applicationId, externalId, null, null, null, null,
                examineId, exampleId, nodeId, request);
    }

    /** 平台审批没有业务记录；系统审批必须同时给出完整的系统、租户、模块和记录边界。 */
    private void requireExternalContext(Long systemId, Long tenantId, Long moduleId, Long recordId) {
        if (systemId == null && tenantId == null && moduleId == null && recordId == null) return;
        requirePositive(systemId, "系统主键无效");
        requirePositive(tenantId, "租户主键无效");
        requirePositive(moduleId, "模块主键无效");
        requirePositive(recordId, "业务记录主键无效");
    }

    /** 应用授权之外还须属于该实例的发起人或已保存的节点处理人，历史处理人仍可查看轨迹。 */
    private void requireExternalParticipant(UnExamineExample example, Actor actor) {
        if (actor.matchesInitiator(example)) return;
        List<Long> nodeIds = nodes.list(new LambdaQueryWrapper<UnExamineExampleNode>()
                        .eq(UnExamineExampleNode::getExampleId, example.getId()))
                .stream().map(UnExamineExampleNode::getId).toList();
        if (!nodeIds.isEmpty() && users.count(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .in(UnExamineExampleNodeUser::getExampleNodeId, nodeIds)
                .eq(UnExamineExampleNodeUser::getApplicationId, actor.applicationId())
                .eq(UnExamineExampleNodeUser::getExternalId, actor.externalId())) > 0) return;
        throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "外部人员不属于该审批实例");
    }

    /** 平台审批没有业务记录边界，仍使用相同的节点策略、候选人快照和幂等动作链。 */
    @Transactional(rollbackFor = Exception.class)
    public ActionView actionPlatform(String token, Long exampleId, Long nodeId, ActionRequest request) {
        Long accountId = auth.requireAccount(token).getId();
        requirePositive(exampleId, "审批实例主键无效");
        requirePositive(nodeId, "审批节点主键无效");
        boolean administrator = platformAdmin(token);
        ModuleRecordAccess.Access access = new ModuleRecordAccess.Access(null, null, accountId,
                administrator, administrator);
        return actionWithAccess(access, null, null, null, null, exampleId, nodeId, request,
                new Actor(accountId, null, null));
    }

    /** 平台管理员查询平台审批实例；实例仍按审批定义快照保留。 */
    public List<ExampleView> listPlatform(String token) {
        Long accountId = auth.requireAccount(token).getId();
        LambdaQueryWrapper<UnExamineExample> where = new LambdaQueryWrapper<UnExamineExample>()
                .isNull(UnExamineExample::getSystemId).isNull(UnExamineExample::getTenantId);
        if (!platformAdmin(token)) {
            where.and(q -> q.eq(UnExamineExample::getInitiatorAccountId, accountId)
                    .or().inSql(UnExamineExample::getId,
                            "SELECT n.example_id FROM un_examine_example_node n "
                                    + "JOIN un_examine_example_node_user u ON u.example_node_id=n.id "
                                    + "WHERE n.deleted_time IS NULL AND u.deleted_time IS NULL "
                                    + "AND u.account_id=" + accountId));
        }
        return examples.list(where.orderByDesc(UnExamineExample::getStartTime))
                .stream().map(example -> viewForActor(example, new Actor(accountId, null, null),
                        platformAdmin(token))).toList();
    }

    /** 平台审批工作台只返回当前账号仍待处理的运行实例。 */
    public Page<ExampleView> pendingPlatform(
            String token, SearchBO search) {
        Long accountId = auth.requireAccount(token).getId();
        var where = new LambdaQueryWrapper<UnExamineExample>()
                .isNull(UnExamineExample::getSystemId)
                .isNull(UnExamineExample::getTenantId)
                .eq(UnExamineExample::getStatus, "RUNNING")
                .inSql(UnExamineExample::getId,
                        "SELECT n.example_id FROM un_examine_example_node n "
                                + "JOIN un_examine_example_node_user u ON u.example_node_id=n.id "
                                + "WHERE n.deleted_time IS NULL AND n.status='PENDING' "
                                + "AND u.deleted_time IS NULL AND u.status='PENDING' AND u.account_id=" + accountId)
                .orderByDesc(UnExamineExample::getStartTime)
                .orderByDesc(UnExamineExample::getId);
        var page = examples.page(search == null ? new SearchBO().parse() : search.parse(), where);
        return new Page<ExampleView>(
                page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(example -> viewForActor(example,
                        new Actor(accountId, null, null), platformAdmin(token))).toList());
    }

    /** 平台应用的外部待办按应用和外部身份分页，只返回仍有效的节点处理人。 */
    public Page<ExternalTodoView> pendingPlatformExternal(Long applicationId, String externalId,
                                                          Long examineId, SearchBO search) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || examineId != null && examineId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台审批外部身份无效");
        var where = new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getApplicationId, applicationId)
                .eq(UnExamineExampleNodeUser::getExternalId, externalId)
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING")
                .apply("EXISTS (SELECT 1 FROM un_examine_example_node n "
                        + "JOIN un_examine_example e ON e.id=n.example_id "
                        + "WHERE n.id=un_examine_example_node_user.example_node_id "
                        + "AND n.deleted_time IS NULL AND n.status='PENDING' "
                        + "AND e.deleted_time IS NULL AND e.status='RUNNING' "
                        + "AND e.system_id IS NULL AND e.tenant_id IS NULL "
                        + "AND e.module_id IS NULL AND e.record_id IS NULL)");
        if (examineId != null) where.apply("EXISTS (SELECT 1 FROM un_examine_example_node n "
                + "JOIN un_examine_example e ON e.id=n.example_id "
                + "WHERE n.id=un_examine_example_node_user.example_node_id "
                + "AND n.deleted_time IS NULL AND e.deleted_time IS NULL AND e.examine_id={0})", examineId);
        where.orderByDesc(UnExamineExampleNodeUser::getId);
        Page<UnExamineExampleNodeUser> page = users.page(
                search == null ? new SearchBO().parse() : search.parse(), where);
        Map<Long, UnExamineExampleNode> nodeById = page.getRecords().isEmpty() ? Map.of()
                : nodes.listByIds(page.getRecords().stream().map(UnExamineExampleNodeUser::getExampleNodeId)
                                .distinct().toList()).stream()
                        .collect(Collectors.toMap(UnExamineExampleNode::getId, node -> node));
        Map<Long, UnExamineExample> exampleById = nodeById.isEmpty() ? Map.of()
                : examples.listByIds(nodeById.values().stream().map(UnExamineExampleNode::getExampleId)
                                .distinct().toList()).stream()
                        .collect(Collectors.toMap(UnExamineExample::getId, example -> example));
        return new Page<ExternalTodoView>(page.getCurrent(), page.getSize(), page.getTotal())
                .setRecords(page.getRecords().stream().map(user -> {
                    UnExamineExampleNode node = nodeById.get(user.getExampleNodeId());
                    UnExamineExample example = node == null ? null : exampleById.get(node.getExampleId());
                    if (example == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                            "审批待办实例已变化，请重新查询");
                    return new ExternalTodoView(user.getId(), example.getId(), example.getExamineId(),
                            node.getId(), node.getNodeCode(), node.getRoundNo(), node.getNodeType(),
                            example.getStartTime());
                }).toList());
    }

    /**
     * 系统审批外部待办按当前处理人、审批定义、有效记录及负责人范围依次收敛。
     * 批量读取记录归属并在分页计数前过滤，不能把已删除或跨租户记录混入待办。
     */
    @Transactional(readOnly = true)
    public Page<SystemExternalTodoView> pendingSystemExternal(Long applicationId, String externalId,
            Long systemId, Long tenantId, Long examineId, SearchBO search,
            BiPredicate<Long, Long> ownerScope) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || systemId == null || systemId < 1
                || tenantId == null || tenantId < 1 || examineId != null && examineId < 1
                || ownerScope == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "系统审批外部待办参数无效");
        Page<SystemExternalTodoView> requested = search == null ? new SearchBO().parse() : search.parse();
        LambdaQueryWrapper<UnExamineExampleNodeUser> where = new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getApplicationId, applicationId)
                .eq(UnExamineExampleNodeUser::getExternalId, externalId)
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING")
                .apply("EXISTS (SELECT 1 FROM un_examine_example_node n "
                        + "JOIN un_examine_example e ON e.id=n.example_id "
                        + "WHERE n.id=un_examine_example_node_user.example_node_id "
                        + "AND n.deleted_time IS NULL AND n.status='PENDING' "
                        + "AND e.deleted_time IS NULL AND e.status='RUNNING' "
                        + "AND e.system_id={0} AND e.tenant_id={1} "
                        + "AND e.module_id IS NOT NULL AND e.record_id IS NOT NULL)", systemId, tenantId);
        if (examineId != null) where.apply("EXISTS (SELECT 1 FROM un_examine_example_node n "
                + "JOIN un_examine_example e ON e.id=n.example_id "
                + "WHERE n.id=un_examine_example_node_user.example_node_id "
                + "AND n.deleted_time IS NULL AND e.deleted_time IS NULL AND e.examine_id={0})", examineId);
        where.orderByDesc(UnExamineExampleNodeUser::getId);
        long offset = (requested.getCurrent() - 1) * requested.getSize();
        long visibleTotal = 0;
        List<SystemExternalTodoView> selected = new ArrayList<>();
        long rawPageNo = 1;
        while (true) {
            Page<UnExamineExampleNodeUser> raw = users.page(Page.of(rawPageNo, 100), where);
            if (raw.getRecords().isEmpty()) break;
            Map<Long, UnExamineExampleNode> byNode = nodes.listByIds(raw.getRecords().stream()
                            .map(UnExamineExampleNodeUser::getExampleNodeId).distinct().toList()).stream()
                    .collect(Collectors.toMap(UnExamineExampleNode::getId, item -> item));
            Map<Long, UnExamineExample> byExample = byNode.isEmpty() ? Map.of()
                    : examples.listByIds(byNode.values().stream().map(UnExamineExampleNode::getExampleId)
                                    .distinct().toList()).stream()
                            .collect(Collectors.toMap(UnExamineExample::getId, item -> item));
            Map<Long, ModuleRecordAccess.RecordOwner> byRecord = byExample.isEmpty() ? Map.of()
                    : recordAccess.activeRecordOwners(systemId, tenantId, byExample.values().stream()
                                    .map(UnExamineExample::getRecordId).filter(Objects::nonNull).distinct().toList())
                            .stream().collect(Collectors.toMap(ModuleRecordAccess.RecordOwner::recordId,
                                    item -> item));
            for (UnExamineExampleNodeUser user : raw.getRecords()) {
                UnExamineExampleNode node = byNode.get(user.getExampleNodeId());
                UnExamineExample example = node == null ? null : byExample.get(node.getExampleId());
                ModuleRecordAccess.RecordOwner record = example == null ? null : byRecord.get(example.getRecordId());
                if (record == null || !Objects.equals(example.getSystemId(), systemId)
                        || !Objects.equals(example.getTenantId(), tenantId)
                        || examineId != null && !Objects.equals(example.getExamineId(), examineId)
                        || !Objects.equals(record.moduleId(), example.getModuleId())
                        || !ownerScope.test(record.ownerAccountId(), record.ownerDeptId())) continue;
                visibleTotal++;
                if (visibleTotal > offset && selected.size() < requested.getSize())
                    selected.add(new SystemExternalTodoView(user.getId(), example.getId(),
                            example.getExamineId(), example.getModuleId(), example.getRecordId(),
                            node.getId(), node.getNodeCode(), node.getRoundNo(), node.getNodeType(),
                            example.getStartTime()));
            }
            if (rawPageNo * 100 >= raw.getTotal()) break;
            rawPageNo++;
        }
        return new Page<SystemExternalTodoView>(requested.getCurrent(), requested.getSize(), visibleTotal)
                .setRecords(selected);
    }

    /** 平台管理员或当前审批处理人查询单个审批实例。 */
    public ExampleView queryPlatform(String token, Long exampleId) {
        Long accountId = auth.requireAccount(token).getId();
        requirePositive(exampleId, "审批实例主键无效");
        UnExamineExample current = example(exampleId, null, null, null, null);
        if (!platformAdmin(token) && !Objects.equals(current.getInitiatorAccountId(), accountId)
                && users.count(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                        .eq(UnExamineExampleNodeUser::getAccountId, accountId)
                        .inSql(UnExamineExampleNodeUser::getExampleNodeId,
                                "SELECT id FROM un_examine_example_node "
                                        + "WHERE deleted_time IS NULL AND example_id=" + exampleId)) == 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "无权查看该平台审批实例");
        return viewForActor(current, new Actor(accountId, null, null), platformAdmin(token));
    }

    private boolean platformAdmin(String token) {
        try {
            auth.requirePlatformAdmin(token);
            return true;
        } catch (BaseException ex) {
            return false;
        }
    }

    /** 平台账号按已启用平台审批定义发起实例，业务值只作为审批快照输入保存。 */
    @Transactional(rollbackFor = Exception.class)
    public ExampleView startPlatform(String token, StartRequest request) {
        Long accountId = auth.requireAccount(token).getId();
        if (request == null || request.examineId() == null || request.examineId() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批发起资料不能为空");
        Long exampleId = processHandler.startPlatform(new ExamineStartContext(null, null, null, null,
                accountId, request.examineId(), request.values(), Map.of("platform", true)));
        return viewForActor(example(exampleId, null, null, null, null),
                new Actor(accountId, null, null), platformAdmin(token));
    }

    /** 应用授权后按平台审批发布快照发起独立实例，外部发起人保存在应用命名空间内。 */
    @Transactional(rollbackFor = Exception.class)
    public ExampleView startPlatformExternal(Long applicationId, String externalId, Long examineId,
                                             String requestId, Map<String, Object> values) {
        Long exampleId = processHandler.startPlatformExternal(examineId, applicationId, externalId,
                requestId, values);
        return viewForActor(example(exampleId, null, null, null, null),
                new Actor(null, applicationId, externalId), false);
    }

    private ActionView actionWithAccess(ModuleRecordAccess.Access access, Long systemId, Long tenantId,
                                        Long moduleId, Long recordId, Long exampleId, Long nodeId,
                                        ActionRequest request, Actor actor) {
        requirePositive(exampleId, "审批实例主键无效");
        requirePositive(nodeId, "审批节点主键无效");
        if (request == null || request.requestId() == null || request.requestId().isBlank()
                || request.requestId().length() > 100 || request.actionCode() == null
                || request.actionCode().isBlank() || request.actionCode().length() > 40)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批办理资料无效");
        // 实例行锁把候选人检查、动作幂等记录和线路推进放在同一串行边界内。
        UnExamineExample example = lockedExample(exampleId, systemId, access.tenantId(), moduleId, recordId);
        UnExamineExampleNode node = nodes.getById(nodeId);
        if (node == null || !exampleId.equals(node.getExampleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批节点不存在");
        ExamineNodeStrategy strategy = strategies.resolve(node.getNodeType());
        String actionCode = request.actionCode().trim().toUpperCase(Locale.ROOT);
        boolean cancel = "CANCEL".equals(actionCode);
        if ((!cancel && !strategy.interactive()) || (!cancel && !strategy.supportsAction(actionCode)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批操作不支持: " + actionCode);
        if (cancel && !(actor.matchesInitiator(example)
                || access.owner() || access.tenantAdmin()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "只有审批发起人或管理人员可以取消审批");

        UnExamineExampleNodeUser user = actor.external()
                ? findExternalRequest(nodeId, actor.applicationId(), actor.externalId(), request.requestId())
                : findRequest(nodeId, actor.accountId(), request.requestId());
        if (user != null && !"PENDING".equals(user.getStatus())) {
            return new ActionView(exampleId, nodeId, user.getId(),
                    actionCodeFrom(user.getActionJson(), actionCode), "SUCCESS",
                    viewForActor(example, actor, access.owner() || access.tenantAdmin()));
        }
        if (!"RUNNING".equals(example.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批实例已结束");
        if (!"PENDING".equals(node.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批节点已处理");
        if (user == null) {
            user = cancel
                    ? actor.external() ? cancellationUserExternal(nodeId, actor.applicationId(), actor.externalId())
                    : cancellationUser(nodeId, actor.accountId())
                    : actor.external() ? currentExternalUser(nodeId, actor.applicationId(), actor.externalId())
                    : currentUser(nodeId, actor.accountId());
        }
        if (user == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "当前账号不是该审批节点处理人");
        if (!cancel && !"PENDING".equals(user.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批办理已完成");

        if ("TRANSFER".equals(actionCode)) {
            transfer(example, node, user, actor.accountId(), request);
            example.setUpdateTime(LocalDateTime.now());
            examples.addOrUpdate(example);
            return new ActionView(exampleId, nodeId, user.getId(), actionCode, "SUCCESS",
                    viewForActor(example, actor, access.owner() || access.tenantAdmin()));
        }
        if ("RETURN".equals(actionCode)) {
            returnNode(example, node, user, actor.accountId(), request);
            boolean completed = pendingNodes(exampleId).isEmpty();
            if (completed) {
                example.setStatus("COMPLETED");
                example.setFinishTime(LocalDateTime.now());
            }
            example.setUpdateTime(LocalDateTime.now());
            examples.addOrUpdate(example);
            if (completed) notifyCompletion(example, actor);
            return new ActionView(exampleId, nodeId, user.getId(), actionCode, "SUCCESS",
                    viewForActor(example, actor, access.owner() || access.tenantAdmin()));
        }

        applyNodeFieldUpdates(example, node, actor, request);

        user.setStatus(cancel ? "CANCELLED" : strategy.userStatus(actionCode));
        user.setOpinion(request.opinion());
        user.setActionJson(actionJson(request, actionCode));
        user.setActionTime(LocalDateTime.now());
        users.addOrUpdate(user);
        log(example, node, actor.accountId(), "NODE_" + actionCode, actionDetail(request, actor));

        boolean terminal = false;
        if (cancel || actionCode.equals("REJECT")) {
            node.setStatus(cancel ? "CANCELLED" : "REJECTED");
            node.setFinishTime(LocalDateTime.now());
            nodes.addOrUpdate(node);
            closeNodePendingUsers(nodeId, user.getId(), "CANCELLED");
            cancelPending(exampleId, nodeId);
            example.setStatus(cancel ? "CANCELLED" : "REJECTED");
            example.setFinishTime(LocalDateTime.now());
            log(example, node, actor.accountId(), cancel ? "EXAMPLE_CANCELLED" : "EXAMPLE_REJECTED",
                    actionDetail(request, actor));
            terminal = true;
        } else if (nodeComplete(node, strategy)) {
            node.setStatus(strategy.nodeStatus(actionCode));
            node.setFinishTime(LocalDateTime.now());
            nodes.addOrUpdate(node);
            // 或签完成后关闭同一节点其他人的待办，避免已完成实例仍显示可办理。
            closeNodePendingUsers(nodeId, user.getId(), "SKIPPED");
            Map<String, Object> routeValues = runtimeValues(example);
            routeValues.put("__examineAction", actionCode);
            route(example, node, routeValues);
            if (pendingNodes(exampleId).isEmpty()) {
                example.setStatus("COMPLETED");
                example.setFinishTime(LocalDateTime.now());
                log(example, node, actor.accountId(), "EXAMPLE_COMPLETED", actionDetail(request, actor));
                terminal = true;
            }
        }
        example.setUpdateTime(LocalDateTime.now());
        examples.addOrUpdate(example);
        if (terminal) notifyCompletion(example, actor);
        return new ActionView(exampleId, nodeId, user.getId(), actionCode, "SUCCESS",
                viewForActor(example, actor, access.owner() || access.tenantAdmin()));
    }

    /** 审批运行服务被模块内部直接调用时也必须拒绝空或负数主键。 */
    private void requirePositive(Long value, String message) {
        if (value == null || value < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, message);
    }

    private UnExamineExample example(Long exampleId, Long systemId, Long tenantId,
                                     Long moduleId, Long recordId) {
        UnExamineExample example = examples.getById(exampleId);
        if (example == null || !Objects.equals(example.getSystemId(), systemId)
                || !Objects.equals(example.getTenantId(), tenantId)
                || !Objects.equals(example.getModuleId(), moduleId)
                || !Objects.equals(example.getRecordId(), recordId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批实例不存在");
        return example;
    }

    /** 审批办理必须锁定实例，避免并发审批人同时推进同一节点或重复提交终态写回。 */
    private UnExamineExample lockedExample(Long exampleId, Long systemId, Long tenantId,
                                           Long moduleId, Long recordId) {
        UnExamineExample example = examples.getOne(new LambdaQueryWrapper<UnExamineExample>()
                .eq(UnExamineExample::getId, exampleId).last("FOR UPDATE"), false);
        if (example == null || !Objects.equals(example.getSystemId(), systemId)
                || !Objects.equals(example.getTenantId(), tenantId)
                || !Objects.equals(example.getModuleId(), moduleId)
                || !Objects.equals(example.getRecordId(), recordId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批实例不存在");
        return example;
    }

    private UnExamineExampleNodeUser findRequest(Long nodeId, Long accountId, String requestId) {
        for (UnExamineExampleNodeUser user : users.list(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                .eq(UnExamineExampleNodeUser::getAccountId, accountId))) {
            if (user.getActionJson() == null) continue;
            try {
                if (requestId.equals(json.readTree(user.getActionJson()).path("requestId").asText())) return user;
            } catch (Exception ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批办理快照无效", ex);
            }
        }
        return null;
    }

    /** 外部办理按应用命名空间和外部身份查找幂等操作。 */
    private UnExamineExampleNodeUser findExternalRequest(Long nodeId, Long applicationId,
                                                         String externalId, String requestId) {
        for (UnExamineExampleNodeUser user : users.list(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                .eq(UnExamineExampleNodeUser::getApplicationId, applicationId)
                .eq(UnExamineExampleNodeUser::getExternalId, externalId))) {
            if (user.getActionJson() == null) continue;
            try {
                if (requestId.equals(json.readTree(user.getActionJson()).path("requestId").asText())) return user;
            } catch (Exception ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批办理快照无效", ex);
            }
        }
        return null;
    }

    /** 只允许实例启动时解析出的内部候选人办理，管理员不能在运行时占用空节点。 */
    private UnExamineExampleNodeUser currentUser(Long nodeId, Long accountId) {
        return users.getOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                .eq(UnExamineExampleNodeUser::getAccountId, accountId)
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING"));
    }

    /** 外部身份只能办理实例中预先解析出的同应用候选人，不能动态占用内部节点。 */
    private UnExamineExampleNodeUser currentExternalUser(Long nodeId, Long applicationId, String externalId) {
        return users.getOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                .eq(UnExamineExampleNodeUser::getApplicationId, applicationId)
                .eq(UnExamineExampleNodeUser::getExternalId, externalId)
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING"));
    }

    /** 取消审批需要留下发起人操作快照，即使发起人不是当前节点的候选人。 */
    private UnExamineExampleNodeUser cancellationUser(Long nodeId, Long accountId) {
        UnExamineExampleNodeUser user = users.getOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                .eq(UnExamineExampleNodeUser::getAccountId, accountId));
        if (user != null) return user;
        user = new UnExamineExampleNodeUser();
        user.setExampleNodeId(nodeId);
        user.setAccountId(accountId);
        return user;
    }

    private UnExamineExampleNodeUser cancellationUserExternal(Long nodeId, Long applicationId, String externalId) {
        UnExamineExampleNodeUser user = users.getOne(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                .eq(UnExamineExampleNodeUser::getApplicationId, applicationId)
                .eq(UnExamineExampleNodeUser::getExternalId, externalId));
        if (user != null) return user;
        user = new UnExamineExampleNodeUser();
        user.setExampleNodeId(nodeId);
        user.setApplicationId(applicationId);
        user.setExternalId(externalId);
        return user;
    }

    private String actionCodeFrom(String actionJson, String fallback) {
        try {
            String actionCode = json.readTree(actionJson).path("actionCode").asText();
            return actionCode.isBlank() ? fallback : actionCode;
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批办理快照无效", ex);
        }
    }

    /** 将当前审批待办转交给指定内部账号或邮件外部身份，并保留原处理人历史。 */
    private void transfer(UnExamineExample example, UnExamineExampleNode node, UnExamineExampleNodeUser current,
                          Long actorAccountId, ActionRequest request) {
        Map<String, Object> payload = request.payload() == null ? Map.of() : request.payload();
        Long targetAccountId = longValue(payload.get("targetAccountId"));
        String targetExternalId = textValue(payload.get("targetExternalId"));
        Long applicationId = longValue(payload.get("applicationId"));
        if (targetAccountId == null && (targetExternalId == null || targetExternalId.isBlank()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "转交审批必须指定内部账号或外部审批人");
        if (targetAccountId != null && targetAccountId < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "转交目标账号无效");
        if (targetAccountId != null && (targetExternalId != null || applicationId != null))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "内部账号不能同时携带外部审批身份");
        if (targetExternalId != null && (applicationId == null || applicationId < 1))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部审批人必须指定有效应用身份");
        if (targetExternalId != null && targetExternalId.length() > 160)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部审批人标识过长");
        if (targetAccountId != null && Objects.equals(targetAccountId, actorAccountId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "不能把审批转交给当前账号");
        if (targetAccountId != null) validateTransferAccount(example, targetAccountId);
        current.setStatus("TRANSFERRED");
        current.setOpinion(request.opinion());
        current.setActionJson(actionJson(request, "TRANSFER"));
        current.setActionTime(LocalDateTime.now());
        users.addOrUpdate(current);
        UnExamineExampleNodeUser target = new UnExamineExampleNodeUser();
        target.setExampleNodeId(node.getId());
        target.setAccountId(targetAccountId);
        target.setExternalId(targetExternalId);
        target.setApplicationId(applicationId);
        target.setStatus("PENDING");
        target.setCreateTime(LocalDateTime.now());
        users.addOrUpdate(target);
        log(example, node, actorAccountId, "NODE_TRANSFER",
                new LinkedHashMap<>(Map.of("targetAccountId", targetAccountId == null ? 0L : targetAccountId,
                        "targetExternalId", targetExternalId == null ? "" : targetExternalId)));
    }

    /**
     * 转交目标沿用发起时的候选人边界；系统审批必须是当前系统租户的有效成员，
     * 平台审批必须是当前仍可登录的平台账号，避免把无效账号写成永久待办。
     */
    private void validateTransferAccount(UnExamineExample example, Long targetAccountId) {
        if (example.getSystemId() == null || example.getTenantId() == null) {
            UnPlatAccount account = accounts.queryById(targetAccountId);
            if (account == null || !"ACTIVE".equals(account.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "转交目标平台账号不存在或已停用");
            return;
        }
        ObjectNode candidate = json.createObjectNode();
        candidate.put("type", "ACCOUNT");
        candidate.putArray("candidateAccountIds").add(targetAccountId);
        Set<Long> resolved = new java.util.LinkedHashSet<>();
        ExamineCandidateContext context = new ExamineCandidateContext(example.getSystemId(), example.getTenantId(),
                example.getModuleId(), example.getRecordId(), example.getInitiatorAccountId(), runtimeValues(example));
        for (ExamineCandidateResolver resolver : candidateResolvers) {
            Set<Long> values = resolver.resolve(candidate, context);
            if (values != null) resolved.addAll(values);
        }
        if (!resolved.contains(targetAccountId))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "转交目标账号不是当前系统租户的有效成员");
    }

    /** 将当前节点回退到发布快照中的指定节点，并以新的轮次重新生成处理人快照。 */
    private void returnNode(UnExamineExample example, UnExamineExampleNode current,
                            UnExamineExampleNodeUser user, Long actorAccountId, ActionRequest request) {
        String targetCode = textValue(request.payload() == null ? null : request.payload().get("targetNodeCode"));
        if (targetCode == null || targetCode.isBlank()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "回退审批必须指定目标节点");
        JsonNode definition = runtimeDefinition(example);
        Map<String, JsonNode> nodeByCode = PublishedProcessSnapshot.nodes(json, definition.toString()).stream()
                .collect(Collectors.toMap(PublishedProcessSnapshot::code, item -> item,
                        (left, right) -> left, LinkedHashMap::new));
        JsonNode target = nodeByCode.get(targetCode);
        if (target == null || Set.of("END", "FINISH", "TERMINATE").contains(
                PublishedProcessSnapshot.type(target).toUpperCase(Locale.ROOT)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "回退目标节点不存在或不可执行");
        user.setStatus("RETURNED");
        user.setOpinion(request.opinion());
        user.setActionJson(actionJson(request, "RETURN"));
        user.setActionTime(LocalDateTime.now());
        users.addOrUpdate(user);
        current.setStatus("RETURNED");
        current.setFinishTime(LocalDateTime.now());
        nodes.addOrUpdate(current);
        // 回退会重置后续线路，关闭并行分支及当前节点其他处理人的旧待办，
        // 避免旧分支在新轮次建立后仍可继续办理并覆盖审批快照。
        closeNodePendingUsers(current.getId(), user.getId(), "CANCELLED");
        cancelPending(example.getId(), current.getId());
        int roundNo = nodes.list(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, example.getId())
                .eq(UnExamineExampleNode::getNodeCode, targetCode)).stream()
                .map(UnExamineExampleNode::getRoundNo).filter(Objects::nonNull).max(Integer::compareTo).orElse(0) + 1;
        createNode(example, target, new HashSet<>(), runtimeValues(example), roundNo);
        log(example, current, actorAccountId, "NODE_RETURN",
                Map.of("targetNodeCode", targetCode, "roundNo", roundNo));
    }

    private String textValue(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    /** 审批节点提交时只允许写入快照声明的可编辑字段，并复用模块保存链落库。 */
    /** 审批节点编辑只更新运行快照，终态通过后才一次性写入业务记录。 */
    private void applyNodeFieldUpdates(UnExamineExample example, UnExamineExampleNode node,
                                       Actor actor, ActionRequest request) {
        if (request.payload() == null || !(request.payload().get("fieldValues") instanceof Map<?, ?> raw)) return;
        Map<String, Object> values = new LinkedHashMap<>();
        raw.forEach((key, value) -> { if (key != null) values.put(key.toString(), value); });
        if (values.isEmpty()) return;
        Set<String> writable = writableFields(node);
        if (writable.isEmpty() || !writable.containsAll(values.keySet()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "审批节点没有提交字段的编辑权限");
        if (example.getSystemId() != null && example.getTenantId() != null
                && example.getModuleId() != null && example.getRecordId() != null)
            recordAccess.validateProcessValues(example.getSystemId(), example.getTenantId(), example.getModuleId(),
                    example.getRecordId(), actor.accountId(), values);
        List<Map<String, Object>> changes = mergePendingValues(example, values, node.getId(),
                node.getNodeCode(), actor.accountId(), actor.applicationId(), actor.externalId());
        if (!changes.isEmpty()) log(example, node, actor.accountId(), "FIELD_CHANGED", Map.of("changes", changes));
    }

    private Set<String> writableFields(UnExamineExampleNode node) {
        try {
            JsonNode snapshot = json.readTree(node.getNodeSnapshotJson());
            Set<String> result = new java.util.LinkedHashSet<>();
            addFieldKeys(result, snapshot.get("writableFieldKeys"));
            addFieldKeys(result, snapshot.get("editableFieldKeys"));
            addFieldKeys(result, snapshot.path("config").get("writableFieldKeys"));
            JsonNode fields = snapshot.get("fields");
            if (fields != null && fields.isArray()) fields.forEach(field -> {
                if (field.path("writableFlag").asBoolean(field.path("writable").asBoolean(false))) {
                    String key = field.path("fieldKey").asText(field.path("field_key").asText(""));
                    if (!key.isBlank()) result.add(key);
                }
            });
            return result;
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批节点字段权限快照无效", ex);
        }
    }

    private List<String> fieldKeys(UnExamineExampleNode node, boolean writable) {
        try {
            JsonNode snapshot = json.readTree(node.getNodeSnapshotJson());
            Set<String> result = new java.util.LinkedHashSet<>();
            addFieldKeys(result, snapshot.get(writable ? "writableFieldKeys" : "readableFieldKeys"));
            addFieldKeys(result, snapshot.path("config").get(writable ? "writableFieldKeys" : "readableFieldKeys"));
            JsonNode fields = snapshot.get("fields");
            if (fields != null && fields.isArray()) fields.forEach(field -> {
                boolean allowed = writable
                        ? field.path("writableFlag").asBoolean(field.path("writable").asBoolean(false))
                        : field.path("readableFlag").asBoolean(field.path("readable").asBoolean(false));
                String key = field.path("fieldKey").asText(field.path("field_key").asText(""));
                if (allowed && !key.isBlank()) result.add(key);
            });
            return List.copyOf(result);
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批节点字段权限快照无效", ex);
        }
    }

    private void addFieldKeys(Set<String> result, JsonNode values) {
        if (values == null || !values.isArray()) return;
        values.forEach(value -> { if (value.isTextual() && !value.asText().isBlank()) result.add(value.asText()); });
    }

    private boolean nodeComplete(UnExamineExampleNode node, ExamineNodeStrategy strategy) {
        JsonNode snapshot;
        try { snapshot = json.readTree(node.getNodeSnapshotJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批节点快照无效", ex); }
        String mode = snapshot.path("approvalMode").asText(snapshot.path("approval_mode").asText(""));
        if (mode.isBlank()) mode = snapshot.path("config").path("approvalMode")
                .asText(snapshot.path("config").path("approval_mode").asText(""));
        if (mode.isBlank()) {
            String nodeType = PublishedProcessSnapshot.type(snapshot).toUpperCase(Locale.ROOT);
            mode = Set.of("AND_SIGN", "COUNTERSIGN", "PARALLEL").contains(nodeType) ? "ALL" : "ANY";
        }
        if (snapshot.path("orSign").asBoolean(snapshot.path("config").path("orSign").asBoolean(false))) mode = "ANY";
        if (snapshot.path("andSign").asBoolean(snapshot.path("config").path("andSign").asBoolean(false))) mode = "ALL";
        long pending = users.count(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId())
                .eq(UnExamineExampleNodeUser::getStatus, "PENDING"));
        return strategy.complete(mode, pending);
    }

    private void route(UnExamineExample example, UnExamineExampleNode current) {
        route(example, current, runtimeValues(example));
    }

    private void route(UnExamineExample example, UnExamineExampleNode current, Map<String, Object> values) {
        route(example, current, new HashSet<>(), values);
    }

    private void route(UnExamineExample example, UnExamineExampleNode current, Set<String> autoVisited) {
        route(example, current, autoVisited, runtimeValues(example));
    }

    /** 按发布快照推进线路；自动节点递归推进并用访问集合防止配置环路。 */
    private void route(UnExamineExample example, UnExamineExampleNode current, Set<String> autoVisited,
                       Map<String, Object> values) {
        JsonNode definition = runtimeDefinition(example);
        Map<String, JsonNode> nodeByCode = PublishedProcessSnapshot.nodes(json, definition.toString()).stream()
                .collect(Collectors.toMap(PublishedProcessSnapshot::code, node -> node, (left, right) -> left, LinkedHashMap::new));
        JsonNode edges = PublishedProcessSnapshot.edges(json, definition.toString());
        List<JsonNode> next = new ArrayList<>();
        if (edges != null && edges.isArray()) {
            List<JsonNode> matched = new ArrayList<>();
            edges.forEach(edge -> {
                if (current.getNodeCode().equals(edge.path("sourceNodeCode").asText(""))) matched.add(edge);
            });
            matched.sort(java.util.Comparator.comparingInt(edge -> edge.path("priorityNo").asInt(0)));
            Set<String> added = new HashSet<>();
            for (JsonNode edge : matched) {
                String condition = edge.has("condition") ? edge.get("condition").toString() : null;
                if (!matchesCondition(condition, values)) continue;
                String target = edge.path("targetNodeCode").asText("");
                JsonNode targetNode = nodeByCode.get(target);
                if (targetNode != null && added.add(target)) next.add(targetNode);
                if (edge.path("exclusiveFlag").asBoolean(true)) break;
            }
        }
        for (JsonNode target : next) {
            String type = PublishedProcessSnapshot.type(target).toUpperCase(Locale.ROOT);
            if (Set.of("END", "FINISH", "TERMINATE").contains(type)) continue;
            createNode(example, target, autoVisited, values);
        }
    }

    private UnExamineExampleNode createNode(UnExamineExample example, JsonNode node, Set<String> autoVisited) {
        return createNode(example, node, autoVisited, runtimeValues(example), 1);
    }

    private UnExamineExampleNode createNode(UnExamineExample example, JsonNode node, Set<String> autoVisited,
                                            Map<String, Object> values) {
        return createNode(example, node, autoVisited, values, 1);
    }

    private UnExamineExampleNode createNode(UnExamineExample example, JsonNode node, Set<String> autoVisited,
                                            Map<String, Object> values, int roundNo) {
        UnExamineExampleNode current = new UnExamineExampleNode();
        current.setExampleId(example.getId());
        current.setNodeCode(PublishedProcessSnapshot.code(node));
        current.setRoundNo(roundNo);
        current.setNodeType(PublishedProcessSnapshot.type(node).toUpperCase(Locale.ROOT));
        current.setNodeSnapshotJson(node.toString());
        ExamineNodeStrategy strategy = strategies.resolve(current.getNodeType());
        current.setStatus(strategy.interactive() ? "PENDING" : strategy.nodeStatus(""));
        current.setStartTime(LocalDateTime.now());
        if (!strategy.interactive()) current.setFinishTime(LocalDateTime.now());
        nodes.addOrUpdate(current);
        Set<Long> accountCandidates = candidateAccounts(example, node);
        Set<PublishedProcessSnapshot.ExternalCandidate> externalCandidates =
                PublishedProcessSnapshot.externalCandidates(node);
        if (strategy.interactive() && accountCandidates.isEmpty() && externalCandidates.isEmpty()) {
            accountCandidates = ExamineEmptyApproverPolicy.resolve(json, example, node, values, candidateResolvers);
            if (accountCandidates.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    "审批节点兜底规则未解析出有效处理人: " + current.getNodeCode());
            log(example, current, null, "EMPTY_APPROVER_FALLBACK",
                    Map.of("ruleType", ExamineEmptyApproverPolicy.ruleType(node), "accountIds", accountCandidates));
        }
        if (!strategy.interactive()) {
            for (Long accountId : accountCandidates) {
                UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
                user.setExampleNodeId(current.getId());
                user.setAccountId(accountId);
                user.setStatus(strategy.userStatus(""));
                user.setActionTime(LocalDateTime.now());
                users.addOrUpdate(user);
            }
            for (PublishedProcessSnapshot.ExternalCandidate external : externalCandidates) {
                UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
                user.setExampleNodeId(current.getId());
                user.setApplicationId(external.applicationId());
                user.setExternalId(external.externalId());
                user.setStatus(strategy.userStatus(""));
                user.setActionTime(LocalDateTime.now());
                users.addOrUpdate(user);
            }
            executeAutoNode(example, current, node, values);
            if (!autoVisited.add(current.getNodeCode()))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点存在循环线路");
            route(example, current, autoVisited, runtimeValues(example));
            return current;
        }
        for (Long accountId : accountCandidates) {
            UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
            user.setExampleNodeId(current.getId());
            user.setAccountId(accountId);
            user.setStatus("PENDING");
            users.addOrUpdate(user);
            for (ExamineTodoNotifier notifier : todoNotifiers)
                NotificationAfterCommit.dispatch(() -> notifier.notifyPending(
                        example.getSystemId(), example.getTenantId(), accountId,
                        example.getId(), current.getId(), current.getNodeCode()));
        }
        for (PublishedProcessSnapshot.ExternalCandidate external : externalCandidates) {
            UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
            user.setExampleNodeId(current.getId());
            user.setApplicationId(external.applicationId());
            user.setExternalId(external.externalId());
            user.setStatus("PENDING");
            users.addOrUpdate(user);
        }
        return current;
    }

    private void cancelPending(Long exampleId, Long currentNodeId) {
        nodes.list(new LambdaQueryWrapper<UnExamineExampleNode>()
                        .eq(UnExamineExampleNode::getExampleId, exampleId)
                        .eq(UnExamineExampleNode::getStatus, "PENDING")
                        .ne(UnExamineExampleNode::getId, currentNodeId))
                .forEach(node -> {
                    node.setStatus("CANCELLED");
                    node.setFinishTime(LocalDateTime.now());
                    nodes.addOrUpdate(node);
                    users.list(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                                    .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId())
                                    .eq(UnExamineExampleNodeUser::getStatus, "PENDING"))
                            .forEach(user -> { user.setStatus("CANCELLED"); users.addOrUpdate(user); });
                });
    }

    /** 关闭当前节点未处理的其他人员快照，并保留其处理人记录供实例日志展示。 */
    private void closeNodePendingUsers(Long nodeId, Long handledUserId, String status) {
        users.list(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                        .eq(UnExamineExampleNodeUser::getExampleNodeId, nodeId)
                        .eq(UnExamineExampleNodeUser::getStatus, "PENDING")
                        .ne(handledUserId != null, UnExamineExampleNodeUser::getId, handledUserId))
                .forEach(item -> {
                    item.setStatus(status);
                    item.setActionTime(LocalDateTime.now());
                    users.addOrUpdate(item);
                });
    }

    /** 按节点配置合并固定人、角色、部门和上级解析结果，实例只保存解析后的人员快照。 */
    private Set<Long> candidateAccounts(UnExamineExample example, JsonNode node) {
        ExamineCandidateContext context = new ExamineCandidateContext(example.getSystemId(), example.getTenantId(),
                example.getModuleId(), example.getRecordId(), example.getInitiatorAccountId(), runtimeValues(example));
        Set<Long> result = new java.util.LinkedHashSet<>();
        for (ExamineCandidateResolver resolver : candidateResolvers) {
            Set<Long> resolved = resolver.resolve(node, context);
            if (resolved != null) result.addAll(resolved);
        }
        return result;
    }

    /** 执行抄送、API 等自动节点；策略结果写入节点原因和实例日志。 */
    private void executeAutoNode(UnExamineExample example, UnExamineExampleNode current,
                                 JsonNode node, Map<String, Object> values) {
        for (ExamineAutoNodeStrategy strategy : autoNodeStrategies) {
            if (!strategy.supports(current.getNodeType())) continue;
            Map<String, Object> result = strategy.execute(new ExamineAutoNodeContext(example.getSystemId(),
                    example.getTenantId(), example.getModuleId(), example.getRecordId(),
                    example.getInitiatorAccountId(), node, values));
            try { current.setAutoReason(json.writeValueAsString(result == null ? Map.of() : result)); }
            catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点结果无法保存", ex); }
            Map<String, Object> fieldValues = fieldValues(result == null ? Map.of() : result);
            if (!fieldValues.isEmpty() && example.getSystemId() != null && example.getTenantId() != null
                    && example.getModuleId() != null && example.getRecordId() != null)
                recordAccess.validateProcessValues(example.getSystemId(), example.getTenantId(),
                        example.getModuleId(), example.getRecordId(), example.getInitiatorAccountId(), fieldValues);
            List<Map<String, Object>> changes = mergePendingValues(example,
                    fieldValues, current.getId(),
                    current.getNodeCode(), example.getInitiatorAccountId(), null, null);
            if (!changes.isEmpty()) log(example, current, example.getInitiatorAccountId(), "FIELD_CHANGED",
                    Map.of("changes", changes));
            nodes.addOrUpdate(current);
            log(example, current, example.getInitiatorAccountId(), "AUTO_NODE_EXECUTED", result);
            return;
        }
        if (!"COPY".equalsIgnoreCase(current.getNodeType()) && !"CC".equalsIgnoreCase(current.getNodeType()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点没有注册执行策略: " + current.getNodeType());
    }

    private List<UnExamineExampleNode> pendingNodes(Long exampleId) {
        return nodes.list(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, exampleId)
                .eq(UnExamineExampleNode::getStatus, "PENDING"));
    }

    /** 审批实例进入最终状态后通知 Flow 等业务编排方；普通模块审批没有回调接收方时直接结束。 */
    /**
     * 审批终态回调保留实际办理人的应用外部身份；Flow 回调据此写入外部审计，不能把外部办理人误记为内部账号。
     */
    private void notifyCompletion(UnExamineExample example, Actor actor) {
        commitPendingValues(example, actor.accountId());
        Map<String, Object> values = runtimeValues(example);
        Map<String, Object> metadata = new LinkedHashMap<>(runtimeMetadata(example));
        if (actor.external()) {
            metadata.put("completionApplicationId", actor.applicationId());
            metadata.put("completionExternalId", actor.externalId());
        }
        ExamineCompletionContext context = new ExamineCompletionContext(example.getSystemId(), example.getTenantId(),
                example.getModuleId(), example.getRecordId(), actor.accountId(), example.getExamineId(),
                example.getId(), example.getStatus(), values, metadata);
        for (ExamineCompletionHandler handler : completionHandlers) handler.afterCompletion(context);
        Map<String, Object> eventMetadata = new LinkedHashMap<>(metadata);
        eventMetadata.put("examineExampleId", example.getId());
        eventMetadata.put("examineId", example.getExamineId());
        eventMetadata.put("applicationId", actor.applicationId());
        eventMetadata.put("externalId", actor.externalId());
        eventPublisher.publish(new BusinessEventContext("EXAMINE_" + example.getStatus(), example.getSystemId(),
                example.getTenantId(), example.getModuleId(), example.getRecordId(), actor.accountId(), null,
                values, eventMetadata));
    }

    /** 只在审批通过并进入终态后写回业务主记录，审批中间态始终保留在运行快照。 */
    private void commitPendingValues(UnExamineExample example, Long actorAccountId) {
        if (!"COMPLETED".equals(example.getStatus())) return;
        try {
            JsonNode rootNode = json.readTree(example.getRuntimeSnapshotJson());
            if (rootNode.path("pendingCommitted").asBoolean(false)) return;
            Map<String, Object> pending = json.convertValue(rootNode.path("pendingValues"), Map.class);
            if (pending.isEmpty()) return;
            if (example.getSystemId() == null && example.getTenantId() == null
                    && example.getModuleId() == null && example.getRecordId() == null) {
                // 平台审批没有业务记录，字段快照只作为平台审批上下文保留，不调用模块业务写回端口。
                ObjectNode mutable = (ObjectNode) rootNode;
                mutable.put("pendingCommitted", true);
                mutable.set("pendingValues", json.createObjectNode());
                example.setRuntimeSnapshotJson(json.writeValueAsString(mutable));
                log(example, null, actorAccountId, "EXAMPLE_DATA_COMMITTED", pending);
                examples.addOrUpdate(example);
                return;
            }
            if (example.getSystemId() == null || example.getTenantId() == null
                    || example.getModuleId() == null || example.getRecordId() == null)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批业务作用域不完整，不能写回业务记录");
            ExamineBusinessUpdateContext context = new ExamineBusinessUpdateContext(example.getSystemId(),
                    example.getTenantId(), example.getModuleId(), example.getRecordId(), actorAccountId,
                    "EXAMINE_APPROVAL_COMMIT", pending);
            if (dataUpdaters.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批终态未配置业务写回端口");
            for (ExamineBusinessDataUpdater updater : dataUpdaters) updater.update(context);
            ObjectNode root = (ObjectNode) rootNode;
            root.put("pendingCommitted", true);
            root.set("pendingValues", json.createObjectNode());
            example.setRuntimeSnapshotJson(json.writeValueAsString(root));
            log(example, null, actorAccountId, "EXAMPLE_DATA_COMMITTED", pending);
            examples.addOrUpdate(example);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批终态业务数据写回失败", ex);
        }
    }

    private ExampleView view(UnExamineExample example) {
        return viewForActor(example, null, true);
    }

    /**
     * 按审批实例中的节点处理人和字段快照裁剪返回值。
     *
     * <p>审批节点的可见字段是节点级配置。当前处理人可以看到自己的节点配置字段，
     * 并据此查看前序节点留下的变更；其他节点的字段值、可编辑字段和变更历史不能
     * 因为同一个实例详情接口而泄露。系统管理员、租户管理员和发起人仍可查看完整实例。</p>
     */
    private ExampleView viewForActor(UnExamineExample example, Actor actor, boolean elevated) {
        Map<String, Object> runtimeValues = runtimeValues(example);
        boolean fullView = elevated || actor == null || actor.matchesInitiator(example);
        Set<String> visibleChangeKeys = new java.util.LinkedHashSet<>();
        List<NodeView> nodeViews = nodes.list(new LambdaQueryWrapper<UnExamineExampleNode>()
                        .eq(UnExamineExampleNode::getExampleId, example.getId()).orderByAsc(UnExamineExampleNode::getId)).stream()
                .map(node -> {
                    boolean nodeVisible = fullView || nodeHasActor(node, actor);
                    List<String> readableKeys = nodeVisible ? fieldKeys(node, false) : List.of();
                    List<String> writableKeys = nodeVisible ? fieldKeys(node, true) : List.of();
                    visibleChangeKeys.addAll(readableKeys);
                    Map<String, Object> readableValues = new LinkedHashMap<>();
                    readableKeys.forEach(key -> {
                        if (runtimeValues.containsKey(key)) readableValues.put(key, runtimeValues.get(key));
                    });
                    return new NodeView(node.getId(), node.getNodeCode(), node.getNodeType(), node.getStatus(),
                        node.getStartTime(), node.getFinishTime(), users.list(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                                .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId())
                                .orderByAsc(UnExamineExampleNodeUser::getId)).stream()
                                .map(user -> new UserView(user.getId(), user.getAccountId(), user.getExternalId(),
                                        user.getApplicationId(), user.getStatus(), user.getOpinion(), user.getActionTime())).toList(),
                        readableKeys, writableKeys, readableValues);
                })
                .toList();
        return new ExampleView(example.getId(), example.getExamineId(), example.getPublishedVersion(),
                example.getModuleId(), example.getRecordId(), example.getStatus(), example.getStartTime(),
                example.getFinishTime(), nodeViews,
                logs.list(new LambdaQueryWrapper<com.unique.unexamine.examine.base.entity.UnExamineExampleLog>()
                                .eq(com.unique.unexamine.examine.base.entity.UnExamineExampleLog::getExampleId, example.getId())
                                .orderByAsc(com.unique.unexamine.examine.base.entity.UnExamineExampleLog::getId))
                        .stream().map(log -> new LogView(log.getId(), log.getExampleNodeId(), log.getAccountId(),
                                log.getEventCode(), visibleLogDetail(log.getDetailJson(), fullView ? null : visibleChangeKeys),
                                log.getCreateTime())).toList(),
                fieldChanges(example, fullView ? null : visibleChangeKeys));
    }

    /** 节点处理人匹配内部账号或应用外部身份；候选人状态不影响历史查看。 */
    private boolean nodeHasActor(UnExamineExampleNode node, Actor actor) {
        if (actor == null) return false;
        return users.list(new LambdaQueryWrapper<UnExamineExampleNodeUser>()
                        .eq(UnExamineExampleNodeUser::getExampleNodeId, node.getId()))
                .stream().anyMatch(user -> actor.external()
                        ? Objects.equals(user.getApplicationId(), actor.applicationId())
                            && Objects.equals(user.getExternalId(), actor.externalId())
                        : Objects.equals(user.getAccountId(), actor.accountId()));
    }

    /** 从审批运行快照读取按时间顺序排列的字段变更历史。 */
    private List<FieldChangeView> fieldChanges(UnExamineExample example, Set<String> visibleKeys) {
        try {
            JsonNode values = json.readTree(example.getRuntimeSnapshotJson()).path("fieldChanges");
            if (!values.isArray()) return List.of();
            List<FieldChangeView> result = new ArrayList<>();
            values.forEach(item -> {
                String fieldKey = item.path("fieldKey").asText();
                if (visibleKeys != null && !visibleKeys.contains(fieldKey)) return;
                result.add(new FieldChangeView(
                    fieldKey,
                    item.has("beforeValue") && !item.get("beforeValue").isNull()
                            ? json.convertValue(item.get("beforeValue"), Object.class) : null,
                    item.has("afterValue") && !item.get("afterValue").isNull()
                            ? json.convertValue(item.get("afterValue"), Object.class) : null,
                    item.has("nodeId") ? item.get("nodeId").asLong() : null,
                    item.has("nodeCode") ? item.get("nodeCode").asText() : null,
                    item.has("accountId") ? item.get("accountId").asLong() : null,
                    item.has("applicationId") ? item.get("applicationId").asLong() : null,
                    item.has("externalId") ? item.get("externalId").asText() : null,
                    item.has("changeTime") ? LocalDateTime.parse(item.get("changeTime").asText()) : null));
            });
            return result;
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批字段变更历史无效", ex);
        }
    }

    /** 审批日志可能带有字段修改载荷，和字段历史使用同一组节点可见字段进行裁剪。 */
    private String visibleLogDetail(String detailJson, Set<String> visibleKeys) {
        if (visibleKeys == null || detailJson == null || detailJson.isBlank()) return detailJson;
        try {
            JsonNode root = json.readTree(detailJson);
            maskFieldMap(root, "fieldValues", visibleKeys);
            maskFieldMap(root, "pendingValues", visibleKeys);
            maskFieldMap(root, "values", visibleKeys);
            maskChanges(root, visibleKeys);
            return json.writeValueAsString(root);
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批日志详情无效", ex);
        }
    }

    private void maskFieldMap(JsonNode root, String name, Set<String> visibleKeys) {
        if (!root.isObject()) return;
        JsonNode child = root.get(name);
        if (child != null && child.isObject()) {
            List<String> keys = new ArrayList<>();
            child.fieldNames().forEachRemaining(keys::add);
            keys.stream().filter(key -> !visibleKeys.contains(key))
                    .forEach(key -> ((ObjectNode) child).remove(key));
        }
        root.elements().forEachRemaining(childNode -> maskFieldMap(childNode, name, visibleKeys));
    }

    private void maskChanges(JsonNode root, Set<String> visibleKeys) {
        if (root.isObject()) {
            JsonNode child = root.get("changes");
            if (child != null && child.isArray()) {
                for (int index = child.size() - 1; index >= 0; index--) {
                    String key = child.get(index).path("fieldKey").asText();
                    if (!visibleKeys.contains(key)) ((com.fasterxml.jackson.databind.node.ArrayNode) child).remove(index);
                }
            }
            root.elements().forEachRemaining(childNode -> maskChanges(childNode, visibleKeys));
        } else if (root.isArray()) {
            root.elements().forEachRemaining(childNode -> maskChanges(childNode, visibleKeys));
        }
    }

    private JsonNode runtimeDefinition(UnExamineExample example) {
        try { return json.readTree(example.getRuntimeSnapshotJson()).path("definition"); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的定义无效", ex); }
    }

    private Map<String, Object> runtimeValues(UnExamineExample example) {
        try { return json.convertValue(json.readTree(example.getRuntimeSnapshotJson()).path("values"), new TypeReference<>() {}); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的业务值无效", ex); }
    }

    /** 合并节点编辑到运行快照，供后续条件和节点实时读取；业务主表在审批终态才更新。 */
    private List<Map<String, Object>> mergePendingValues(UnExamineExample example, Map<String, Object> updates,
                                                         Long nodeId, String nodeCode, Long actorAccountId,
                                                         Long applicationId, String externalId) {
        if (updates == null || updates.isEmpty()) return List.of();
        try {
            ObjectNode root = (ObjectNode) json.readTree(example.getRuntimeSnapshotJson());
            ObjectNode values = root.with("values");
            ObjectNode pending = root.with("pendingValues");
            var history = root.withArray("fieldChanges");
            List<Map<String, Object>> changes = new ArrayList<>();
            updates.forEach((key, value) -> {
                JsonNode before = values.get(key);
                JsonNode after = json.valueToTree(value);
                if (Objects.equals(before, after)) return;
                ObjectNode item = json.createObjectNode();
                item.put("fieldKey", key);
                item.set("beforeValue", before == null ? json.nullNode() : before.deepCopy());
                item.set("afterValue", after);
                if (nodeId != null) item.put("nodeId", nodeId);
                if (nodeCode != null) item.put("nodeCode", nodeCode);
                if (actorAccountId != null) item.put("accountId", actorAccountId);
                if (applicationId != null) item.put("applicationId", applicationId);
                if (externalId != null) item.put("externalId", externalId);
                item.put("changeTime", LocalDateTime.now().toString());
                history.add(item);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("fieldKey", key); detail.put("beforeValue", before == null ? null : before);
                detail.put("afterValue", after); detail.put("nodeId", nodeId);
                detail.put("nodeCode", nodeCode); detail.put("accountId", actorAccountId);
                if (applicationId != null) detail.put("applicationId", applicationId);
                if (externalId != null) detail.put("externalId", externalId);
                changes.add(detail);
                values.set(key, after);
                pending.set(key, after);
            });
            example.setRuntimeSnapshotJson(json.writeValueAsString(root));
            return changes;
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照字段变更无法保存", ex);
        }
    }

    private Map<String, Object> pendingValues(UnExamineExample example) {
        try {
            return json.convertValue(json.readTree(example.getRuntimeSnapshotJson()).path("pendingValues"),
                    new TypeReference<>() {});
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的待提交字段无效", ex);
        }
    }

    private Map<String, Object> fieldValues(Map<String, Object> result) {
        Object raw = result.get("fieldValues");
        if (!(raw instanceof Map<?, ?> map)) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, value) -> { if (key != null) values.put(key.toString(), value); });
        return values;
    }

    private Map<String, Object> runtimeMetadata(UnExamineExample example) {
        try { return json.convertValue(json.readTree(example.getRuntimeSnapshotJson()).path("metadata"), new TypeReference<>() {}); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的回调上下文无效", ex); }
    }

    /** 运行实例只读取发布快照；快照条件损坏时按运行配置冲突处理。 */
    private boolean matchesCondition(String condition, Map<String, Object> values) {
        try {
            return TriggerConditionMatcher.matches(json, condition, values);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的线路条件无效", ex);
        }
    }

    private String actionJson(ActionRequest request, String actionCode) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("requestId", request.requestId());
        payload.put("actionCode", actionCode);
        payload.put("opinion", request.opinion());
        payload.put("payload", request.payload());
        try { return json.writeValueAsString(payload); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批办理内容格式无效", ex); }
    }

    /** 运行时办理人；内部账号和外部应用身份不能混用。 */
    private record Actor(Long accountId, Long applicationId, String externalId) {
        boolean external() { return applicationId != null && externalId != null; }

        boolean matchesInitiator(UnExamineExample example) {
            return external()
                    ? Objects.equals(example.getApplicationId(), applicationId)
                        && Objects.equals(example.getInitiatorExternalId(), externalId)
                    : Objects.equals(example.getInitiatorAccountId(), accountId);
        }
    }

    /** 构造允许空意见和空载荷的日志详情，避免 Map.of 拒绝业务请求中的空值。 */
    private Map<String, Object> actionDetail(ActionRequest request) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("requestId", request.requestId());
        detail.put("actionCode", request.actionCode());
        detail.put("opinion", request.opinion());
        detail.put("payload", request.payload());
        return detail;
    }

    /** 外部审批日志额外记录应用和外部身份，便于与内部账号操作区分。 */
    private Map<String, Object> actionDetail(ActionRequest request, Actor actor) {
        Map<String, Object> detail = actionDetail(request);
        if (actor.external()) {
            detail.put("applicationId", actor.applicationId());
            detail.put("externalId", actor.externalId());
        }
        return detail;
    }

    /** 所有审批动作写入独立实例日志，处理人历史和实例状态仍分别保存。 */
    private void log(UnExamineExample example, UnExamineExampleNode node, Long accountId,
                     String eventCode, Object detail) {
        var row = new com.unique.unexamine.examine.base.entity.UnExamineExampleLog();
        row.setExampleId(example.getId());
        row.setExampleNodeId(node == null ? null : node.getId());
        row.setAccountId(accountId);
        row.setEventCode(eventCode);
        try { row.setDetailJson(json.writeValueAsString(detail == null ? Map.of() : detail)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批日志详情无法保存", ex); }
        row.setCreateTime(LocalDateTime.now());
        logs.addOrUpdate(row);
    }
}
