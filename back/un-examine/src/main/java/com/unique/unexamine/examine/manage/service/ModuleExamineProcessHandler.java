package com.unique.unexamine.examine.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.ModuleRecordProcessContext;
import com.unique.unexamine.core.module.ModuleRecordProcessHandler;
import com.unique.unexamine.core.module.ModuleDeleteApprovalStarter;
import com.unique.unexamine.core.module.ExamineCompletionContext;
import com.unique.unexamine.core.module.ExamineCompletionHandler;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.ExamineCandidateResolver;
import com.unique.unexamine.core.module.ExamineTodoNotifier;
import com.unique.unexamine.core.module.ExamineBusinessDataUpdater;
import com.unique.unexamine.core.module.ExamineBusinessUpdateContext;
import com.unique.unexamine.core.module.ExamineStartContext;
import com.unique.unexamine.core.module.ExamineStarter;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.NotificationAfterCommit;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.examine.base.entity.UnExamine;
import com.unique.unexamine.examine.base.entity.UnExamineExample;
import com.unique.unexamine.examine.base.entity.UnExamineExampleNode;
import com.unique.unexamine.examine.base.entity.UnExamineExampleNodeUser;
import com.unique.unexamine.examine.base.entity.UnModuleExamine;
import com.unique.unexamine.examine.base.service.IUnExamineExampleNodeService;
import com.unique.unexamine.examine.base.service.IUnExamineExampleNodeUserService;
import com.unique.unexamine.examine.base.service.IUnExamineExampleLogService;
import com.unique.unexamine.examine.base.service.IUnExamineExampleService;
import com.unique.unexamine.examine.base.service.IUnExamineService;
import com.unique.unexamine.examine.base.service.IUnModuleExamineService;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.unique.unexamine.examine.manage.strategy.ExamineNodeStrategy;
import com.unique.unexamine.examine.manage.strategy.ExamineNodeStrategyResolver;
import com.unique.unexamine.examine.manage.strategy.ExamineAutoNodeContext;
import com.unique.unexamine.examine.manage.strategy.ExamineAutoNodeStrategy;

/**
 * 模块记录保存后的审批启动器。
 *
 * <p>审批实例保存发布版本和业务值快照，并为发布快照中的首节点建立待办。审批配置未命中
 * 条件时不产生实例，配置错误则让记录保存事务回滚，避免出现没有审批上下文的业务数据。</p>
 */
@Component
public class ModuleExamineProcessHandler implements ModuleRecordProcessHandler, ModuleDeleteApprovalStarter, ExamineStarter {
    /** 存在审批实例时拒绝直接还原业务值，避免留下与记录不一致的审批历史。 */
    @Override public boolean hasAssociatedInstance(Long systemId,Long moduleId,Long recordId) {
        return examples.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.unique.unexamine.examine.base.entity.UnExamineExample>()
                .eq(com.unique.unexamine.examine.base.entity.UnExamineExample::getSystemId,systemId)
                .eq(com.unique.unexamine.examine.base.entity.UnExamineExample::getModuleId,moduleId)
                .eq(com.unique.unexamine.examine.base.entity.UnExamineExample::getRecordId,recordId))>0;
    }
    private final IUnModuleExamineService moduleExamines;
    private final IUnExamineService examines;
    private final IUnExamineExampleService examples;
    private final IUnModuleRecordService records;
    private final IUnExamineExampleNodeService nodes;
    private final IUnExamineExampleNodeUserService nodeUsers;
    private final ObjectMapper json;
    private final ExamineNodeStrategyResolver strategies;
    private final ObjectProvider<ExamineCompletionHandler> completionHandlers;
    private final IUnExamineExampleLogService logs;
    private final List<ExamineCandidateResolver> candidateResolvers;
    private final List<ExamineAutoNodeStrategy> autoNodeStrategies;
    private final List<ExamineBusinessDataUpdater> dataUpdaters;
    private final BusinessEventPublisher eventPublisher;
    private final List<ExamineTodoNotifier> todoNotifiers;

    public ModuleExamineProcessHandler(IUnModuleExamineService moduleExamines, IUnExamineService examines,
                                       IUnExamineExampleService examples,IUnModuleRecordService records,IUnExamineExampleNodeService nodes,
                                       IUnExamineExampleNodeUserService nodeUsers, ObjectMapper json,
                                       ExamineNodeStrategyResolver strategies,
                                       ObjectProvider<ExamineCompletionHandler> completionHandlers,
                                       IUnExamineExampleLogService logs,
                                       List<ExamineCandidateResolver> candidateResolvers,
                                       List<ExamineAutoNodeStrategy> autoNodeStrategies,
                                       List<ExamineBusinessDataUpdater> dataUpdaters,
                                       BusinessEventPublisher eventPublisher,
                                       List<ExamineTodoNotifier> todoNotifiers) {
        this.moduleExamines = moduleExamines;
        this.examines = examines;
        this.examples = examples;
        this.records = records;
        this.nodes = nodes;
        this.nodeUsers = nodeUsers;
        this.json = json;
        this.strategies = strategies;
        this.completionHandlers = completionHandlers;
        this.logs = logs;
        this.candidateResolvers = candidateResolvers == null ? List.of() : List.copyOf(candidateResolvers);
        this.autoNodeStrategies = autoNodeStrategies == null ? List.of() : List.copyOf(autoNodeStrategies);
        this.dataUpdaters = dataUpdaters == null ? List.of() : List.copyOf(dataUpdaters);
        this.eventPublisher = eventPublisher;
        this.todoNotifiers = todoNotifiers == null ? List.of() : List.copyOf(todoNotifiers);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void afterSave(ModuleRecordProcessContext context) {
        for (UnModuleExamine binding : matchedBindings(context)) {
            try {
                if (!TriggerConditionMatcher.matches(json, binding.getConditionJson(), context.values())) continue;
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批绑定条件无效", ex);
            }
            start(context, binding);
        }
    }

    /** 删除审批先创建实例，业务记录在审批期间保持可读可办；同一记录不能重复提交未结束的删除审批。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean startDeleteApproval(ModuleRecordProcessContext context, Map<String, Object> metadata) {
        // 任一动作的审批进行中时，不能通过删除动作绕开单记录审批约束。
        if (examples.count(new LambdaQueryWrapper<UnExamineExample>()
                .eq(UnExamineExample::getSystemId, context.systemId())
                .eq(UnExamineExample::getTenantId, context.tenantId())
                .eq(UnExamineExample::getModuleId, context.moduleId())
                .eq(UnExamineExample::getRecordId, context.recordId())
                .eq(UnExamineExample::getStatus, "RUNNING"))>0)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"该记录已有未结束的审批");
        List<UnModuleExamine> matched = new ArrayList<>();
        for (UnModuleExamine binding : matchedBindings(context)) {
            try {
                if (TriggerConditionMatcher.matches(json, binding.getConditionJson(), context.values())) matched.add(binding);
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "删除审批绑定条件无效", ex);
            }
        }
        if (matched.isEmpty()) return false;
        if (matched.size() != 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "同一删除动作命中多个审批，请调整绑定条件");
        UnModuleExamine binding = matched.get(0);
        Map<String, Object> snapshot = new LinkedHashMap<>(metadata == null ? Map.of() : metadata);
        snapshot.put("deleteRequest", true);
        snapshot.put("moduleExamineId", binding.getId());
        startRuntime(examines.getById(binding.getExamineId()), context.systemId(), context.tenantId(),
                context.moduleId(), context.recordId(), context.accountId(), context.values(), snapshot,
                context.eventCode(), context.operation());
        return true;
    }

    /** 只读取当前系统和租户可继承的启用审批绑定。 */
    private List<UnModuleExamine> matchedBindings(ModuleRecordProcessContext context) {
        List<String> events = new ArrayList<>();
        events.add(context.eventCode());
        if (context.operation() != null && !events.contains(context.operation())) events.add(context.operation());
        if ("CREATE".equals(context.eventCode())) events.add("RECORD_CREATE");
        if ("UPDATE".equals(context.eventCode())) events.add("RECORD_UPDATE");
        return moduleExamines.list(new LambdaQueryWrapper<UnModuleExamine>()
                .eq(UnModuleExamine::getSystemId, context.systemId())
                .and(w -> w.isNull(UnModuleExamine::getTenantId).or().eq(UnModuleExamine::getTenantId, context.tenantId()))
                .eq(UnModuleExamine::getModuleId, context.moduleId())
                .and(w -> w.isNull(UnModuleExamine::getActionMenuId).or().eq(UnModuleExamine::getActionMenuId, context.actionMenuId()))
                .in(UnModuleExamine::getTriggerCode, events)
                .in(UnModuleExamine::getStatus, List.of("ACTIVE", "PUBLISHED"))
                .orderByAsc(UnModuleExamine::getId));
    }

    private void start(ModuleRecordProcessContext context, UnModuleExamine binding) {
        UnExamine examine = examines.getById(binding.getExamineId());
        startRuntime(examine, context.systemId(), context.tenantId(), context.moduleId(), context.recordId(),
                context.accountId(), context.values(), Map.of("moduleExamineId",binding.getId()), context.eventCode(), context.operation());
    }

    /** Flow 保存与发起时共用审批定义的系统、租户和模块边界。 */
    @Override
    public void validateReference(Long systemId, Long tenantId, Long moduleId, Long examineId) {
        UnExamine examine = examines.getById(examineId);
        if (examine == null || !java.util.Objects.equals(examine.getSystemId(), systemId)
                || !java.util.Objects.equals(examine.getTenantId(), tenantId)
                || moduleId != null && examine.getModuleId() != null
                && !moduleId.equals(examine.getModuleId())
                || !"ACTIVE".equals(examine.getStatus())
                || examine.getPublishedVersion() == null || examine.getPublishedVersion() < 1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 引用的审批定义不存在、未启用或不属于当前模块");
    }

    /** Flow 节点使用同一个启动端口发起审批，元数据用于审批结束后回到原 Flow 待办。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void start(ExamineStartContext context) {
        validateReference(context.systemId(), context.tenantId(), context.moduleId(), context.examineId());
        UnExamine examine = examines.getById(context.examineId());
        Long applicationId = longValue(context.metadata().get("initiatorApplicationId"));
        String externalId = textValue(context.metadata().get("initiatorExternalId"));
        startRuntime(examine, context.systemId(), context.tenantId(), context.moduleId(), context.recordId(),
                context.accountId(), applicationId, externalId, context.values(), context.metadata(),
                "FLOW_EXAMINE", "FLOW_NODE");
    }

    /** 平台审批没有业务模块记录，仍沿用发布快照和实例节点创建流程。 */
    @Transactional(rollbackFor = Exception.class)
    public Long startPlatform(ExamineStartContext context) {
        UnExamine examine = examines.getById(context.examineId());
        if (examine == null || examine.getSystemId() != null || examine.getTenantId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台审批定义不存在");
        return startRuntime(examine, null, null, null, null, context.accountId(), context.values(),
                context.metadata(), "PLATFORM_START", "PLATFORM");
    }

    /** 外部应用发起平台审批；定义行锁串行化同一请求号的并发重试。 */
    @Transactional(rollbackFor = Exception.class)
    public Long startPlatformExternal(Long examineId, Long applicationId, String externalId,
                                      String requestId, Map<String, Object> values) {
        if (examineId == null || examineId < 1 || applicationId == null || applicationId < 1
                || externalId == null || externalId.isBlank() || externalId.length() > 160
                || requestId == null || requestId.isBlank() || requestId.length() > 100
                || !requestId.matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台审批外部身份或请求号无效");
        UnExamine examine = examines.getOne(new LambdaQueryWrapper<UnExamine>()
                .eq(UnExamine::getId, examineId).last("FOR UPDATE"), false);
        if (examine == null || examine.getSystemId() != null || examine.getTenantId() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台审批定义不存在");
        if (!List.of("ACTIVE", "PUBLISHED").contains(examine.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台审批定义未启用");
        for (UnExamineExample previous : examples.list(new LambdaQueryWrapper<UnExamineExample>()
                .eq(UnExamineExample::getExamineId, examineId)
                .eq(UnExamineExample::getApplicationId, applicationId)
                .eq(UnExamineExample::getInitiatorExternalId, externalId)
                .isNull(UnExamineExample::getSystemId).isNull(UnExamineExample::getTenantId)
                .orderByDesc(UnExamineExample::getId))) {
            try {
                if (requestId.equals(json.readTree(previous.getRuntimeSnapshotJson())
                        .path("metadata").path("requestId").asText())) return previous.getId();
            } catch (Exception ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台审批运行快照无法读取", ex);
            }
        }
        return startRuntime(examine, null, null, null, null, null, applicationId, externalId,
                values == null ? Map.of() : values,
                Map.of("platform", true, "requestId", requestId), "PLATFORM_START", "PLATFORM");
    }

    private Long startRuntime(UnExamine examine, Long systemId, Long tenantId, Long moduleId, Long recordId,
                              Long accountId, Map<String, Object> values, Map<String, Object> metadata,
                              String eventCode, String operation) {
        return startRuntime(examine, systemId, tenantId, moduleId, recordId, accountId, null, null,
                values, metadata, eventCode, operation);
    }

    private Long startRuntime(UnExamine examine, Long systemId, Long tenantId, Long moduleId, Long recordId,
                              Long accountId, Long applicationId, String externalId,
                              Map<String, Object> values, Map<String, Object> metadata,
                              String eventCode, String operation) {
        if (examine == null || !List.of("ACTIVE", "PUBLISHED").contains(examine.getStatus())
                || examine.getPublishedVersion() == null || examine.getPublishedVersion() < 1
                || examine.getPublishedSnapshotJson() == null || examine.getPublishedSnapshotJson().isBlank())
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批绑定的发布定义不存在");
        List<JsonNode> allNodes;
        try { allNodes = PublishedProcessSnapshot.nodes(json, examine.getPublishedSnapshotJson()); }
        catch (RuntimeException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批发布快照无效", ex); }
        List<JsonNode> starts = PublishedProcessSnapshot.startNodes(allNodes);
        if (starts.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批发布快照没有可执行节点");

        // 业务记录行锁使不同动作的并发启动串行化，实例终态后才能发起下一次审批。
        if (recordId!=null) {
            UnModuleRecord record=records.getOne(new LambdaQueryWrapper<UnModuleRecord>()
                    .eq(UnModuleRecord::getId,recordId).last("FOR UPDATE"),false);
            if (record==null || !java.util.Objects.equals(record.getSystemId(),systemId)
                    || !java.util.Objects.equals(record.getTenantId(),tenantId)
                    || !java.util.Objects.equals(record.getModuleId(),moduleId))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"审批关联的业务记录不存在");
            if (examples.count(new LambdaQueryWrapper<UnExamineExample>()
                    .eq(UnExamineExample::getSystemId,systemId).eq(UnExamineExample::getTenantId,tenantId)
                    .eq(UnExamineExample::getModuleId,moduleId).eq(UnExamineExample::getRecordId,recordId)
                    .eq(UnExamineExample::getStatus,"RUNNING"))>0)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"该记录已有未结束的审批");
        }

        UnExamineExample example = new UnExamineExample();
        example.setExamineId(examine.getId());
        if (metadata.get("moduleExamineId") instanceof Number bindingId)
            example.setModuleExamineId(bindingId.longValue());
        example.setPublishedVersion(examine.getPublishedVersion());
        example.setSystemId(systemId);
        example.setTenantId(tenantId);
        example.setModuleId(moduleId);
        example.setRecordId(recordId);
        example.setApplicationId(applicationId);
        example.setInitiatorAccountId(accountId);
        example.setInitiatorExternalId(externalId);
        example.setStatus("RUNNING");
        example.setRuntimeSnapshotJson(runtimeSnapshot(examine, eventCode, operation, values, metadata));
        example.setStartTime(LocalDateTime.now());
        examples.addOrUpdate(example);
        eventPublisher.publish(new BusinessEventContext("EXAMINE_STARTED", systemId, tenantId, moduleId, recordId,
                accountId, null, values, Map.of("examineExampleId", example.getId(), "examineId", examine.getId(),
                        "publishedVersion", examine.getPublishedVersion(), "eventCode", eventCode,
                        "operation", operation == null ? "" : operation,
                        "externalApplicationId", applicationId == null ? "" : applicationId,
                        "externalId", externalId == null ? "" : externalId)));
        Map<String, Object> startDetail = new LinkedHashMap<>();
        startDetail.put("eventCode", eventCode); startDetail.put("operation", operation);
        log(example, null, accountId, "EXAMPLE_STARTED", startDetail);
        Set<String> autoVisited = new HashSet<>();
        for (JsonNode node : starts) createNode(example, node, autoVisited);
        if (nodes.list(new LambdaQueryWrapper<UnExamineExampleNode>()
                .eq(UnExamineExampleNode::getExampleId, example.getId())
                .eq(UnExamineExampleNode::getStatus, "PENDING")).isEmpty()) {
            example.setStatus("COMPLETED");
            example.setFinishTime(LocalDateTime.now());
            examples.addOrUpdate(example);
            notifyCompletion(example);
        }
        return example.getId();
    }

    /** 记录审批实例启动和自动节点处理，便于详情页查看完整运行轨迹。 */
    private void log(UnExamineExample example, Long nodeId, Long accountId, String eventCode, Object detail) {
        var row = new com.unique.unexamine.examine.base.entity.UnExamineExampleLog();
        row.setExampleId(example.getId());
        row.setExampleNodeId(nodeId);
        row.setAccountId(accountId);
        row.setEventCode(eventCode);
        try { row.setDetailJson(json.writeValueAsString(detail == null ? Map.of() : detail)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批日志详情无法保存", ex); }
        row.setCreateTime(LocalDateTime.now());
        logs.addOrUpdate(row);
    }

    private String runtimeSnapshot(UnExamine examine, String eventCode, String operation,
                                   Map<String, Object> values, Map<String, Object> metadata) {
        try {
            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("examineId", examine.getId());
            runtime.put("publishedVersion", examine.getPublishedVersion());
            runtime.put("administratorAccountId", examine.getCreateAccountId());
            runtime.put("eventCode", eventCode);
            runtime.put("operation", operation);
            runtime.put("values", values == null ? Map.of() : values);
            runtime.put("pendingValues", Map.of());
            runtime.put("fieldChanges", List.of());
            runtime.put("metadata", metadata == null ? Map.of() : metadata);
            runtime.put("definition", json.readTree(examine.getPublishedSnapshotJson()));
            return json.writeValueAsString(runtime);
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批发布快照无法生成运行快照", ex);
        }
    }

    private void createNode(UnExamineExample example, JsonNode node, Set<String> autoVisited) {
        UnExamineExampleNode current = new UnExamineExampleNode();
        current.setExampleId(example.getId());
        current.setNodeCode(PublishedProcessSnapshot.code(node));
        current.setRoundNo(1);
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
            accountCandidates = ExamineEmptyApproverPolicy.resolve(json, example, node, runtimeValues(example), candidateResolvers);
            if (accountCandidates.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    "审批节点兜底规则未解析出有效处理人: " + current.getNodeCode());
            log(example, current.getId(), null, "EMPTY_APPROVER_FALLBACK",
                    Map.of("ruleType", ExamineEmptyApproverPolicy.ruleType(node), "accountIds", accountCandidates));
        }
        if (!strategy.interactive()) {
            for (Long accountId : accountCandidates) {
                UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
                user.setExampleNodeId(current.getId());
                user.setAccountId(accountId);
                user.setStatus(strategy.userStatus(""));
                user.setActionTime(LocalDateTime.now());
                nodeUsers.addOrUpdate(user);
            }
            for (PublishedProcessSnapshot.ExternalCandidate external : externalCandidates) {
                UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
                user.setExampleNodeId(current.getId());
                user.setApplicationId(external.applicationId());
                user.setExternalId(external.externalId());
                user.setStatus(strategy.userStatus(""));
                user.setActionTime(LocalDateTime.now());
                nodeUsers.addOrUpdate(user);
            }
            executeAutoNode(example, current, node);
            String code = current.getNodeCode();
            if (!autoVisited.add(code)) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点存在循环线路");
            route(example, current, autoVisited);
            return;
        }
        for (Long accountId : accountCandidates) {
            UnExamineExampleNodeUser user = new UnExamineExampleNodeUser();
            user.setExampleNodeId(current.getId());
            user.setAccountId(accountId);
            user.setStatus("PENDING");
            user.setActionTime(null);
            nodeUsers.addOrUpdate(user);
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
            nodeUsers.addOrUpdate(user);
        }
    }

    /** 首节点为抄送等自动节点时立即沿审批快照推进，避免自动节点制造悬挂待办。 */
    private void route(UnExamineExample example, UnExamineExampleNode current, Set<String> autoVisited) {
        JsonNode definition;
        try { definition = json.readTree(example.getRuntimeSnapshotJson()).path("definition"); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的定义无效", ex); }
        Map<String, JsonNode> nodeByCode = PublishedProcessSnapshot.nodes(json, definition.toString()).stream()
                .collect(Collectors.toMap(PublishedProcessSnapshot::code, node -> node, (left, right) -> left, LinkedHashMap::new));
        JsonNode edges = PublishedProcessSnapshot.edges(json, definition.toString());
        if (edges == null || !edges.isArray()) return;
        List<JsonNode> matched = new ArrayList<>();
        edges.forEach(edge -> {
            if (current.getNodeCode().equals(edge.path("sourceNodeCode").asText(""))) matched.add(edge);
        });
        matched.sort(java.util.Comparator.comparingInt(edge -> edge.path("priorityNo").asInt(0)));
        List<JsonNode> next = new ArrayList<>();
        Map<String, Object> values = runtimeValues(example);
        for (JsonNode edge : matched) {
            String condition = edge.has("condition") ? edge.get("condition").toString() : null;
            try {
                if (!TriggerConditionMatcher.matches(json, condition, values)) continue;
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批线路条件无效", ex);
            }
            String target = edge.path("targetNodeCode").asText("");
            JsonNode targetNode = nodeByCode.get(target);
            if (targetNode != null && next.stream().noneMatch(item -> target.equals(PublishedProcessSnapshot.code(item)))) next.add(targetNode);
            if (edge.path("exclusiveFlag").asBoolean(true)) break;
        }
        for (JsonNode target : next) {
            String type = PublishedProcessSnapshot.type(target).toUpperCase(Locale.ROOT);
            if (List.of("END", "FINISH", "TERMINATE").contains(type)) continue;
            createNode(example, target, autoVisited);
        }
    }

    private Map<String, Object> runtimeValues(UnExamineExample example) {
        try { return json.convertValue(json.readTree(example.getRuntimeSnapshotJson()).path("values"), Map.class); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的业务值无效", ex); }
    }

    /** 读取 Flow 回调元数据中的外部发起应用主键。 */
    private Long longValue(Object value) {
        if (value instanceof Number number) return number.longValue();
        if (value == null) return null;
        try { return Long.valueOf(value.toString()); }
        catch (NumberFormatException ex) { return null; }
    }

    /** 读取 Flow 回调元数据中的外部发起身份。 */
    private String textValue(Object value) {
        if (value == null) return null;
        String text = value.toString().trim();
        return text.isEmpty() ? null : text;
    }

    /** 发起时解析指定人、角色、部门和固定上级，并把结果写入实例人员快照。 */
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

    /** 发起时执行 API 等自动节点，确保首节点自动完成后仍沿快照继续。 */
    private void executeAutoNode(UnExamineExample example, UnExamineExampleNode current, JsonNode node) {
        for (ExamineAutoNodeStrategy strategy : autoNodeStrategies) {
            if (!strategy.supports(current.getNodeType())) continue;
            Map<String, Object> values = runtimeValues(example);
            Map<String, Object> result = strategy.execute(new ExamineAutoNodeContext(example.getSystemId(),
                    example.getTenantId(), example.getModuleId(), example.getRecordId(),
                    example.getInitiatorAccountId(), node, values));
            try { current.setAutoReason(json.writeValueAsString(result == null ? Map.of() : result)); }
            catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点结果无法保存", ex); }
            Map<String, Object> fieldValues = fieldValues(result == null ? Map.of() : result);
            validateAutoFieldValues(example, fieldValues);
            List<Map<String, Object>> changes = mergePendingValues(example, result == null ? Map.of() : result,
                    current.getId(), current.getNodeCode(), example.getInitiatorAccountId());
            nodes.addOrUpdate(current);
            if (!changes.isEmpty()) log(example, current.getId(), example.getInitiatorAccountId(), "FIELD_CHANGED",
                    Map.of("changes", changes));
            log(example, current.getId(), example.getInitiatorAccountId(), "AUTO_NODE_EXECUTED", result);
            return;
        }
        if (!Set.of("COPY", "CC").contains(current.getNodeType().toUpperCase(Locale.ROOT)))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点没有注册执行策略: " + current.getNodeType());
    }

    /** API 等自动节点的返回字段先经过模块字段校验，再进入审批运行快照。 */
    private void validateAutoFieldValues(UnExamineExample example, Map<String, Object> fieldValues) {
        if (fieldValues.isEmpty() || example.getSystemId() == null || example.getTenantId() == null
                || example.getModuleId() == null || example.getRecordId() == null)
            return;
        if (dataUpdaters.isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批自动节点未配置业务字段校验端口");
        ExamineBusinessUpdateContext context = new ExamineBusinessUpdateContext(example.getSystemId(),
                example.getTenantId(), example.getModuleId(), example.getRecordId(),
                example.getInitiatorAccountId(), "EXAMINE_AUTO_NODE_VALIDATE", fieldValues);
        for (ExamineBusinessDataUpdater updater : dataUpdaters) updater.validate(context);
    }

    /** 只提取 API 等自动节点声明的业务字段，避免把调用结果元数据写入记录。 */
    private Map<String, Object> fieldValues(Map<String, Object> result) {
        Object raw = result.get("fieldValues");
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) return Map.of();
        Map<String, Object> values = new LinkedHashMap<>();
        map.forEach((key, value) -> { if (key != null) values.put(key.toString(), value); });
        return values;
    }

    /** 自动审批节点直接完成时回传 Flow 并发布终态事件，与人工办理保持同一结果链。 */
    private void notifyCompletion(UnExamineExample example) {
        commitPendingValues(example);
        Map<String, Object> values = runtimeValues(example);
        Map<String, Object> metadata;
        try { metadata = new LinkedHashMap<>(json.convertValue(
                json.readTree(example.getRuntimeSnapshotJson()).path("metadata"), Map.class)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批运行快照中的回调上下文无效", ex); }
        if ((example.getApplicationId() == null) != (example.getInitiatorExternalId() == null))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "自动审批的外部发起身份不完整");
        if (example.getApplicationId() != null) {
            metadata.put("completionApplicationId", example.getApplicationId());
            metadata.put("completionExternalId", example.getInitiatorExternalId());
        }
        ExamineCompletionContext context = new ExamineCompletionContext(example.getSystemId(), example.getTenantId(),
                example.getModuleId(), example.getRecordId(), example.getInitiatorAccountId(), example.getExamineId(),
                example.getId(), example.getStatus(), values, metadata);
        completionHandlers.orderedStream().forEach(handler -> handler.afterCompletion(context));
        Map<String, Object> eventMetadata = new LinkedHashMap<>(metadata);
        eventMetadata.put("examineExampleId", example.getId());
        eventMetadata.put("examineId", example.getExamineId());
        eventMetadata.put("applicationId", example.getApplicationId());
        eventMetadata.put("externalId", example.getInitiatorExternalId());
        eventPublisher.publish(new BusinessEventContext("EXAMINE_" + example.getStatus(), example.getSystemId(),
                example.getTenantId(), example.getModuleId(), example.getRecordId(),
                example.getInitiatorAccountId(), null, values, eventMetadata));
    }

    /** 自动节点的字段变更先写审批运行快照，审批自动完成后再提交业务记录。 */
    private List<Map<String, Object>> mergePendingValues(UnExamineExample example, Map<String, Object> result,
                                                         Long nodeId, String nodeCode, Long actorAccountId) {
        Object raw = result.get("fieldValues");
        if (!(raw instanceof Map<?, ?> updates) || updates.isEmpty()) return List.of();
        try {
            var root = (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(example.getRuntimeSnapshotJson());
            var values = root.with("values");
            var pending = root.with("pendingValues");
            var history = root.withArray("fieldChanges");
            List<Map<String, Object>> changes = new java.util.ArrayList<>();
            updates.forEach((key, value) -> {
                if (key == null) return;
                String fieldKey = key.toString();
                JsonNode before = values.get(fieldKey);
                JsonNode after = json.valueToTree(value);
                if (java.util.Objects.equals(before, after)) return;
                var item = json.createObjectNode();
                item.put("fieldKey", fieldKey);
                item.set("beforeValue", before == null ? json.nullNode() : before.deepCopy());
                item.set("afterValue", after);
                if (nodeId != null) item.put("nodeId", nodeId);
                if (nodeCode != null) item.put("nodeCode", nodeCode);
                if (actorAccountId != null) item.put("accountId", actorAccountId);
                item.put("changeTime", LocalDateTime.now().toString());
                history.add(item);
                Map<String, Object> detail = new LinkedHashMap<>();
                detail.put("fieldKey", fieldKey); detail.put("beforeValue", before == null ? null : before);
                detail.put("afterValue", after); detail.put("nodeId", nodeId);
                detail.put("nodeCode", nodeCode); detail.put("accountId", actorAccountId);
                changes.add(detail);
                values.set(fieldKey, after);
                pending.set(fieldKey, after);
            });
            example.setRuntimeSnapshotJson(json.writeValueAsString(root));
            return changes;
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批自动节点字段变更无法保存", ex);
        }
    }

    /** 自动审批直接完成时提交所有待变更字段；普通人工审批由运行服务完成同样的提交。 */
    private void commitPendingValues(UnExamineExample example) {
        if (!"COMPLETED".equals(example.getStatus())) return;
        try {
            var root = json.readTree(example.getRuntimeSnapshotJson());
            if (root.path("pendingCommitted").asBoolean(false)) return;
            Map<String, Object> pending = json.convertValue(root.path("pendingValues"), Map.class);
            if (pending.isEmpty()) return;
            if (example.getSystemId() == null && example.getTenantId() == null
                    && example.getModuleId() == null && example.getRecordId() == null) {
                // 平台审批没有业务记录，终态只关闭运行快照中的待写值，不调用模块业务写回端口。
                var mutable = (com.fasterxml.jackson.databind.node.ObjectNode) root;
                mutable.put("pendingCommitted", true);
                mutable.set("pendingValues", json.createObjectNode());
                example.setRuntimeSnapshotJson(json.writeValueAsString(mutable));
                log(example, null, example.getInitiatorAccountId(), "EXAMPLE_DATA_COMMITTED", pending);
                examples.addOrUpdate(example);
                return;
            }
            if (example.getSystemId() == null || example.getTenantId() == null
                    || example.getModuleId() == null || example.getRecordId() == null)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批业务作用域不完整，不能写回业务记录");
            if (dataUpdaters.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批终态未配置业务写回端口");
            ExamineBusinessUpdateContext context = new ExamineBusinessUpdateContext(example.getSystemId(),
                    example.getTenantId(), example.getModuleId(), example.getRecordId(),
                    example.getInitiatorAccountId(), "EXAMINE_APPROVAL_COMMIT", pending);
            for (ExamineBusinessDataUpdater updater : dataUpdaters) updater.update(context);
            var mutable = (com.fasterxml.jackson.databind.node.ObjectNode) root;
            mutable.put("pendingCommitted", true);
            mutable.set("pendingValues", json.createObjectNode());
            example.setRuntimeSnapshotJson(json.writeValueAsString(mutable));
            log(example, null, example.getInitiatorAccountId(), "EXAMPLE_DATA_COMMITTED", pending);
            examples.addOrUpdate(example);
        } catch (Exception ex) {
            if (ex instanceof RuntimeException runtime) throw runtime;
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "审批终态业务数据写回失败", ex);
        }
    }

}
