package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.ModuleRecordProcessContext;
import com.unique.unexamine.core.module.ModuleRecordProcessHandler;
import com.unique.unexamine.core.module.ExamineStartContext;
import com.unique.unexamine.core.module.ExamineStarter;
import com.unique.unexamine.core.module.ExamineCandidateContext;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.NotificationAfterCommit;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.sys.flow.base.entity.UnFlowExample;
import com.unique.unexamine.sys.flow.base.entity.UnFlowEvent;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTask;
import com.unique.unexamine.sys.flow.base.entity.UnFlowTaskCandidate;
import com.unique.unexamine.sys.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.sys.flow.base.service.IUnFlowBindingService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowEventService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskCandidateService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowTaskService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowVersionService;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldService;
import com.unique.unexamine.sys.module.manage.service.ModuleRelationFieldService;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
import com.unique.unexamine.sys.base.entity.UnSysSystem;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模块记录保存后的 Flow 启动器。
 *
 * <p>这里只消费已发布的绑定和不可变版本，运行实例保存发布快照并创建首节点待办；草稿节点
 * 不会参与运行。账号、角色、部门和直系上级规则在发起时解析并写入候选人快照，运行中不再
 * 随组织资料变化。</p>
 */
@Component
public class ModuleFlowProcessHandler implements ModuleRecordProcessHandler {
    /** 存在 Flow 实例时数据回滚不能无视待办和节点历史。 */
    @Override public boolean hasAssociatedInstance(Long systemId,Long moduleId,Long recordId) {
        return examples.count(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.unique.unexamine.sys.flow.base.entity.UnFlowExample>()
                .eq(com.unique.unexamine.sys.flow.base.entity.UnFlowExample::getSystemId,systemId)
                .eq(com.unique.unexamine.sys.flow.base.entity.UnFlowExample::getModuleId,moduleId)
                .eq(com.unique.unexamine.sys.flow.base.entity.UnFlowExample::getRecordId,recordId))>0;
    }
    private final IUnFlowBindingService bindings;
    private final IUnFlowService flows;
    private final IUnFlowVersionService versions;
    private final IUnFlowExampleService examples;
    private final IUnFlowEventService flowEvents;
    private final IUnFlowTaskService tasks;
    private final IUnFlowTaskCandidateService candidates;
    private final ObjectMapper json;
    private final List<ExamineStarter> examineStarters;
    private final BusinessEventPublisher eventPublisher;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService recordData;
    private final ModuleConfigService moduleConfig;
    private final ModuleFieldService fieldRules;
    private final ModuleRelationFieldService relationFields;
    private final IUnSysSystemService systemRows;
    private final FlowCandidateResolver candidateResolver;
    private final SystemMessageTemplateService systemMessages;

    public ModuleFlowProcessHandler(IUnFlowBindingService bindings, IUnFlowVersionService versions,
                                    IUnFlowService flows,
                                    IUnFlowExampleService examples, IUnFlowEventService flowEvents,
                                    IUnFlowTaskService tasks,
                                    IUnFlowTaskCandidateService candidates, ObjectMapper json,
                                    List<ExamineStarter> examineStarters,
                                    BusinessEventPublisher eventPublisher,
                                    IUnModuleRecordService records, IUnModuleRecordDataService recordData,
                                    ModuleConfigService moduleConfig, ModuleFieldService fieldRules,
                                    ModuleRelationFieldService relationFields,
                                    IUnSysSystemService systemRows, FlowCandidateResolver candidateResolver,
                                    SystemMessageTemplateService systemMessages) {
        this.bindings = bindings;
        this.flows = flows;
        this.versions = versions;
        this.examples = examples;
        this.flowEvents = flowEvents;
        this.tasks = tasks;
        this.candidates = candidates;
        this.json = json;
        this.examineStarters = examineStarters == null ? List.of() : List.copyOf(examineStarters);
        this.eventPublisher = eventPublisher;
        this.records = records;
        this.recordData = recordData;
        this.moduleConfig = moduleConfig;
        this.fieldRules = fieldRules;
        this.relationFields = relationFields;
        this.systemRows = systemRows;
        this.candidateResolver = candidateResolver;
        this.systemMessages = systemMessages;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void afterSave(ModuleRecordProcessContext context) {
        for (UnFlowBinding binding : matchedBindings(context)) {
            if (!TriggerConditionMatcher.matches(json, binding.getConditionJson(), context.values())) continue;
            start(context, binding);
            if (Boolean.TRUE.equals(binding.getExclusiveFlag())) break;
        }
    }

    /**
     * 外部应用按已授权绑定发起模块 Flow。绑定主键由应用授权资源固定，避免调用方只靠请求参数挑选其他流程。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long startExternal(Long applicationId, String externalId, Long systemId, Long tenantId,
                              Long moduleId, Long recordId, Long bindingId, String requestId,
                              Map<String, Object> values) {
        if (applicationId == null || applicationId < 1 || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || requestId == null || requestId.isBlank()
                || requestId.length() > 100 || !requestId.matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "外部 Flow 身份无效");
        UnFlowBinding binding = bindings.getById(bindingId);
        if (binding == null || !java.util.Objects.equals(binding.getSystemId(), systemId)
                || !java.util.Objects.equals(binding.getTenantId(), tenantId)
                || !java.util.Objects.equals(binding.getModuleId(), moduleId)
                || !List.of("ACTIVE", "PUBLISHED").contains(binding.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 绑定不存在或未启用");
        Map<String, Object> effectiveValues = externalStartValues(systemId, tenantId, moduleId, recordId, values);
        ModuleRecordProcessContext context = new ModuleRecordProcessContext(systemId, tenantId, moduleId, recordId,
                null, "APPLICATION", binding.getActionMenuId(), "APPLICATION_FLOW_START",
                effectiveValues);
        return start(context, binding, applicationId, externalId, requestId);
    }

    /** 平台管理员从已启用的平台 Flow 发布版本发起独立运行实例。 */
    @Transactional(rollbackFor = Exception.class)
    public Long startPlatform(Long flowId, Long accountId, String requestId, Map<String, Object> values) {
        UnFlow flow = flows.getById(flowId);
        if (flow == null || !"PLATFORM".equals(flow.getScopeType()) || flow.getSystemId() != null
                || flow.getTenantId() != null || !"ACTIVE".equals(flow.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 不存在或未启用");
        if (accountId == null || accountId < 1 || requestId == null || requestId.isBlank()
                || requestId.length() > 100 || !requestId.matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 发起参数无效");
        UnFlowVersion version = versions.getOne(new LambdaQueryWrapper<UnFlowVersion>()
                .eq(UnFlowVersion::getFlowId, flow.getId())
                .eq(UnFlowVersion::getVersionNo, flow.getLatestVersion()), false);
        if (version == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台 Flow 尚未发布可运行版本");
        UnFlowBinding binding = new UnFlowBinding();
        binding.setScopeType("PLATFORM");
        binding.setFlowId(flow.getId());
        binding.setFlowVersionId(version.getId());
        binding.setStatus("ACTIVE");
        ModuleRecordProcessContext context = new ModuleRecordProcessContext(
                null, null, null, null, accountId, "PLATFORM_START", null, "PLATFORM_START",
                values == null ? Map.of() : values);
        return start(context, binding, null, null, requestId);
    }

    /** 应用按平台 Flow 定义发起独立实例；定义行锁保证同一外部请求号并发重试只创建一个实例。 */
    @Transactional(rollbackFor = Exception.class)
    public Long startPlatformExternal(Long flowId, Long applicationId, String externalId,
                                      String requestId, Map<String, Object> values) {
        if (flowId == null || flowId < 1 || applicationId == null || applicationId < 1
                || externalId == null || externalId.isBlank()
                || externalId.length() > 160 || requestId == null || requestId.isBlank()
                || requestId.length() > 100 || !requestId.matches("[A-Za-z0-9._:-]+"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "平台 Flow 外部身份或请求号无效");
        UnFlow flow = flows.getOne(new LambdaQueryWrapper<UnFlow>()
                .eq(UnFlow::getId, flowId).last("FOR UPDATE"), false);
        if (flow == null || !"PLATFORM".equals(flow.getScopeType()) || flow.getSystemId() != null
                || flow.getTenantId() != null || !"ACTIVE".equals(flow.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "平台 Flow 不存在或未启用");
        for (UnFlowExample previous : examples.list(new LambdaQueryWrapper<UnFlowExample>()
                .eq(UnFlowExample::getFlowId, flowId)
                .eq(UnFlowExample::getApplicationId, applicationId)
                .eq(UnFlowExample::getInitiatorExternalId, externalId)
                .isNull(UnFlowExample::getSystemId).isNull(UnFlowExample::getTenantId)
                .orderByDesc(UnFlowExample::getId))) {
            try {
                if (requestId.equals(json.readTree(previous.getRuntimeSnapshotJson()).path("requestId").asText()))
                    return previous.getId();
            } catch (Exception ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台 Flow 运行快照无法读取", ex);
            }
        }
        UnFlowVersion version = versions.getOne(new LambdaQueryWrapper<UnFlowVersion>()
                .eq(UnFlowVersion::getFlowId, flowId)
                .eq(UnFlowVersion::getVersionNo, flow.getLatestVersion()), false);
        if (version == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "平台 Flow 尚未发布可运行版本");
        UnFlowBinding binding = new UnFlowBinding();
        binding.setScopeType("PLATFORM");
        binding.setFlowId(flowId);
        binding.setFlowVersionId(version.getId());
        binding.setStatus("ACTIVE");
        ModuleRecordProcessContext context = new ModuleRecordProcessContext(
                null, null, null, null, null, "APPLICATION", null, "PLATFORM_START",
                values == null ? Map.of() : values);
        return start(context, binding, applicationId, externalId, requestId);
    }

    /**
     * 外部 Flow 以真实业务记录的当前值作为条件基准；调用方传入的值只作为事件补充值，
     * 仍须按字段当前配置完成类型、成员、关联模块和计算字段校验。
     */
    private Map<String, Object> externalStartValues(Long systemId, Long tenantId, Long moduleId,
                                                     Long recordId, Map<String, Object> supplied) {
        UnModuleRecord record = records.getById(recordId);
        if (record == null || !java.util.Objects.equals(record.getSystemId(), systemId)
                || !java.util.Objects.equals(record.getTenantId(), tenantId)
                || !java.util.Objects.equals(record.getModuleId(), moduleId)
                || record.getDeletedTime() != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 关联业务记录不存在");
        List<UnModuleField> fields = moduleConfig.currentFields(moduleId);
        Map<String, UnModuleField> byKey = fields.stream()
                .collect(java.util.stream.Collectors.toMap(UnModuleField::getFieldKey, item -> item,
                        (left, right) -> left, java.util.LinkedHashMap::new));
        Map<String, UnModuleRecordData> rows = recordData.list(new LambdaQueryWrapper<UnModuleRecordData>()
                        .eq(UnModuleRecordData::getRecordId, recordId)).stream()
                .collect(java.util.stream.Collectors.toMap(UnModuleRecordData::getFieldKey, item -> item,
                        (left, right) -> left, java.util.LinkedHashMap::new));
        Map<String, Object> result = new LinkedHashMap<>();
        for (UnModuleField field : fields) {
            var definition = fieldRules.configured(field.getFieldKey(), moduleConfig.fieldConfig(field).configuration());
            Object value = "name".equals(field.getFieldKey()) ? record.getName()
                    : ModuleRecordStorage.isMain(definition) ? ModuleRecordStorage.read(record, definition)
                    : fieldRules.stored(rows.get(field.getFieldKey()), definition);
            if (value != null) result.put(field.getFieldKey(), value);
        }
        if (supplied == null || supplied.isEmpty()) return result;
        for (Map.Entry<String, Object> entry : supplied.entrySet()) {
            UnModuleField field = byKey.get(entry.getKey());
            if (field == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                    "Flow 补充值包含未配置字段: " + entry.getKey());
            var definition = fieldRules.configured(field.getFieldKey(), moduleConfig.fieldConfig(field).configuration());
            if (definition.type().computed()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,
                    definition.label() + "由系统计算，不能作为 Flow 补充值");
            Object value = fieldRules.normalize(definition, entry.getValue());
            fieldRules.validateConfiguredValue(systemId, tenantId, definition, value);
            relationFields.validateByProcess(systemId, tenantId, definition, value);
            result.put(entry.getKey(), value);
        }
        return result;
    }

    /**
     * 绑定按作用域、模块、动作和事件收敛。当前租户存在覆盖绑定时只使用覆盖绑定，
     * 没有覆盖时才回退到租户为空的默认绑定，避免两套 Flow 同时启动。
     */
    private List<UnFlowBinding> matchedBindings(ModuleRecordProcessContext context) {
        List<String> events = new ArrayList<>();
        events.add(context.eventCode());
        if (context.operation() != null && !events.contains(context.operation())) events.add(context.operation());
        if ("CREATE".equals(context.eventCode())) events.add("RECORD_CREATE");
        if ("UPDATE".equals(context.eventCode())) events.add("RECORD_UPDATE");
        UnSysSystem system = systemRows.getById(context.systemId());
        Long rootTenantId = system == null ? null : system.getRootTenantId();
        List<UnFlowBinding> candidates = bindings.list(new LambdaQueryWrapper<UnFlowBinding>()
                // 模块记录只能触发本系统绑定；平台独立 Flow 的空系统作用域不参与业务记录保存。
                .eq(UnFlowBinding::getScopeType, "SYSTEM")
                .eq(UnFlowBinding::getSystemId, context.systemId())
                .and(w -> {
                    w.isNull(UnFlowBinding::getTenantId).or().eq(UnFlowBinding::getTenantId, context.tenantId());
                    if (rootTenantId != null) w.or().eq(UnFlowBinding::getTenantId, rootTenantId);
                })
                .and(w -> w.isNull(UnFlowBinding::getModuleId).or().eq(UnFlowBinding::getModuleId, context.moduleId()))
                .and(w -> w.isNull(UnFlowBinding::getActionMenuId).or().eq(UnFlowBinding::getActionMenuId, context.actionMenuId()))
                .in(UnFlowBinding::getEventCode, events)
                .in(UnFlowBinding::getStatus, List.of("ACTIVE", "PUBLISHED"))
                .orderByAsc(UnFlowBinding::getPriorityNo).orderByAsc(UnFlowBinding::getId));
        Map<String, List<UnFlowBinding>> grouped = candidates.stream()
                .collect(java.util.stream.Collectors.groupingBy(this::bindingKey,
                        LinkedHashMap::new, java.util.stream.Collectors.toList()));
        List<UnFlowBinding> effective = new ArrayList<>();
        for (List<UnFlowBinding> group : grouped.values()) {
            Long selectedTenant = group.stream().anyMatch(item ->
                    java.util.Objects.equals(item.getTenantId(), context.tenantId()))
                    ? context.tenantId()
                    : group.stream().anyMatch(item -> java.util.Objects.equals(item.getTenantId(), rootTenantId))
                    ? rootTenantId : null;
            group.stream().filter(item -> java.util.Objects.equals(item.getTenantId(), selectedTenant))
                    .forEach(effective::add);
        }
        return effective.stream().sorted(Comparator.comparing(UnFlowBinding::getPriorityNo,
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(UnFlowBinding::getId, Comparator.nullsFirst(Comparator.naturalOrder()))).toList();
    }

    private String bindingKey(UnFlowBinding binding) {
        return String.valueOf(binding.getModuleId()) + '|'
                + binding.getActionMenuId() + '|' + binding.getEventCode();
    }

    private Long start(ModuleRecordProcessContext context, UnFlowBinding binding) {
        return start(context, binding, null, null, null);
    }

    private Long start(ModuleRecordProcessContext context, UnFlowBinding binding,
                       Long applicationId, String externalId, String requestId) {
        if (binding.getFlowVersionId() == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"Flow 绑定缺少发布版本");
        UnFlowVersion version = versions.getById(binding.getFlowVersionId());
        if (version == null || !binding.getFlowId().equals(version.getFlowId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"Flow 绑定的发布版本不存在");
        List<JsonNode> nodes = PublishedProcessSnapshot.nodes(json, version.getSnapshotJson());
        List<JsonNode> starts = PublishedProcessSnapshot.startNodes(nodes);
        if (starts.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"Flow 发布快照没有可执行节点");
        JsonNode snapshot;
        try { snapshot = json.readTree(version.getSnapshotJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"Flow 发布快照无法读取", ex); }

        UnFlowExample example = new UnFlowExample();
        example.setFlowId(binding.getFlowId());
        example.setFlowVersionId(version.getId());
        example.setBindingId(binding.getId());
        example.setScopeType(binding.getScopeType());
        example.setSystemId(context.systemId());
        example.setTenantId(context.tenantId());
        example.setSourceTenantId(binding.getTenantId());
        example.setModuleId(context.moduleId());
        example.setRecordId(context.recordId());
        example.setApplicationId(applicationId);
        example.setInitiatorAccountId(context.accountId());
        example.setInitiatorExternalId(externalId);
        example.setStatus("RUNNING");
        example.setRuntimeSnapshotJson(runtimeSnapshot(version, context, applicationId, externalId, requestId));
        example.setStartTime(LocalDateTime.now());
        examples.addOrUpdate(example);
        JsonNode edges = PublishedProcessSnapshot.edges(json, version.getSnapshotJson());
        List<JsonNode> initialNodes = initialNodes(starts, nodes, edges, context.values());
        List<ExamineStartContext> examineStarts = new ArrayList<>();
        for (JsonNode node : initialNodes) {
            ExamineStartContext examineStart = createTask(example, node, context, applicationId, externalId);
            if (examineStart != null) examineStarts.add(examineStart);
        }
        try {
            example.setCurrentNodesJson(json.writeValueAsString(initialNodes.stream()
                    .map(PublishedProcessSnapshot::code).toList()));
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"Flow 首节点无法生成运行状态", ex);
        }
        if (initialNodes.isEmpty()) {
            example.setStatus("COMPLETED");
            example.setFinishTime(LocalDateTime.now());
        }
        examples.addOrUpdate(example);
        Map<String, Object> eventMetadata = new LinkedHashMap<>();
        eventMetadata.put("flowExampleId", example.getId());
        eventMetadata.put("flowId", binding.getFlowId());
        eventMetadata.put("flowVersionId", version.getId());
        // 平台 Flow 独立运行，没有模块绑定；事件元数据只携带实际存在的绑定 ID。
        if (binding.getId() != null) eventMetadata.put("bindingId", binding.getId());
        if (applicationId != null) eventMetadata.put("externalApplicationId", applicationId);
        if (externalId != null) eventMetadata.put("externalId", externalId);
        Map<String, Object> startDetail = new LinkedHashMap<>(eventMetadata);
        startDetail.put("initialNodeCodes", initialNodes.stream().map(PublishedProcessSnapshot::code).toList());
        startDetail.put("triggerEventCode", context.eventCode());
        recordEvent(example, "FLOW_START", context.accountId(), externalId, startDetail);
        eventPublisher.publish(new BusinessEventContext("FLOW_STARTED", context.systemId(), context.tenantId(),
                context.moduleId(), context.recordId(), context.accountId(), binding.getActionMenuId(),
                context.values(), eventMetadata));
        if (initialNodes.isEmpty()) {
            // 纯控制线路直接抵达结束节点时同样留下终态历史并通知扩展点。
            recordEvent(example, "FLOW_COMPLETE", context.accountId(), externalId, eventMetadata);
            eventPublisher.publish(new BusinessEventContext("FLOW_COMPLETED", context.systemId(), context.tenantId(),
                    context.moduleId(), context.recordId(), context.accountId(), binding.getActionMenuId(),
                    context.values(), eventMetadata));
        }
        // 全部首节点及运行状态先落库，再启动可同步完成的审批，防止回调终态被发起时的旧对象覆盖。
        for (ExamineStartContext examineStart : examineStarts)
            for (ExamineStarter starter : examineStarters) starter.start(examineStart);
        return example.getId();
    }

    /** 记录发起及无待办直接结束的 Flow 历史，和实例状态处于同一事务。 */
    private void recordEvent(UnFlowExample example, String eventCode, Long accountId,
                             String externalId, Map<String, Object> detail) {
        UnFlowEvent event = new UnFlowEvent();
        event.setExampleId(example.getId());
        event.setEventCode(eventCode);
        event.setActorAccountId(accountId);
        event.setActorExternalId(externalId);
        try { event.setDetailJson(json.writeValueAsString(detail)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "Flow 事件历史无法保存", ex); }
        event.setCreateTime(LocalDateTime.now());
        flowEvents.addOrUpdate(event);
    }

    private String runtimeSnapshot(UnFlowVersion version, ModuleRecordProcessContext context,
                                   Long applicationId, String externalId, String requestId) {
        try {
            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("versionId", version.getId());
            runtime.put("versionNo", version.getVersionNo());
            runtime.put("eventCode", context.eventCode());
            runtime.put("operation", context.operation());
            runtime.put("values", context.values());
            if (applicationId != null) runtime.put("applicationId", applicationId);
            if (externalId != null) runtime.put("initiatorExternalId", externalId);
            if (requestId != null) runtime.put("requestId", requestId);
            runtime.put("definition", json.readTree(version.getSnapshotJson()));
            return json.writeValueAsString(runtime);
        } catch (Exception ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"Flow 发布快照无法生成运行快照", ex);
        }
    }

    private ExamineStartContext createTask(UnFlowExample example, JsonNode node, ModuleRecordProcessContext context,
                            Long applicationId, String externalId) {
        UnFlowTask task = new UnFlowTask();
        task.setExampleId(example.getId());
        task.setNodeCode(PublishedProcessSnapshot.code(node));
        task.setRoundNo(1);
        Long examineId = PublishedProcessSnapshot.examineId(node);
        task.setTaskType(examineId == null ? node.path("taskType").asText(node.path("task_type").asText(
                PublishedProcessSnapshot.type(node).toUpperCase(Locale.ROOT))) : "EXAMINE_WAIT");
        task.setStatus("PENDING");
        task.setNodeSnapshotJson(node.toString());
        task.setCreateTime(LocalDateTime.now());
        tasks.addOrUpdate(task);
        if (examineId != null) {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("flowExampleId", example.getId());
            metadata.put("flowTaskId", task.getId());
            metadata.put("flowNodeCode", task.getNodeCode());
            if (applicationId != null) metadata.put("initiatorApplicationId", applicationId);
            if (externalId != null) metadata.put("initiatorExternalId", externalId);
            return new ExamineStartContext(example.getSystemId(), example.getTenantId(),
                    example.getModuleId(), example.getRecordId(), context.accountId(), examineId,
                    context.values(), metadata);
        }
        Set<Long> accountCandidates = candidateResolver.resolve(node,
                new ExamineCandidateContext(example.getSystemId(), example.getTenantId(), example.getModuleId(),
                        example.getRecordId(), example.getInitiatorAccountId(), context.values()));
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
            candidate.setClaimTime(null);
            candidate.setCreateTime(LocalDateTime.now());
            candidates.addOrUpdate(candidate);
            NotificationAfterCommit.dispatch(() ->
                    systemMessages.flowTaskAvailable(example.getSystemId(), example.getTenantId(),
                            accountId, task.getId(), task.getNodeCode()));
        }
        // 首批节点与后续节点统一保存外部应用候选人，外部办理时按应用命名空间和身份匹配。
        for (PublishedProcessSnapshot.ExternalCandidate external : externalCandidates) {
            UnFlowTaskCandidate candidate = new UnFlowTaskCandidate();
            candidate.setTaskId(task.getId());
            candidate.setCandidateType("EXTERNAL");
            candidate.setApplicationId(external.applicationId());
            candidate.setExternalId(external.externalId());
            candidate.setStatus("PENDING");
            candidate.setClaimTime(null);
            candidate.setCreateTime(LocalDateTime.now());
            candidates.addOrUpdate(candidate);
        }
        return null;
    }

    /** 显式开始节点是流程控制节点，按发布快照线路条件展开为首批实际待办。 */
    private List<JsonNode> initialNodes(List<JsonNode> starts, List<JsonNode> allNodes,
                                        JsonNode edges, Map<String, Object> values) {
        Map<String, JsonNode> byCode = allNodes.stream().collect(java.util.stream.Collectors.toMap(
                PublishedProcessSnapshot::code, node -> node, (left, right) -> left, LinkedHashMap::new));
        Deque<JsonNode> queue = new ArrayDeque<>(starts);
        Set<String> visitedControl = new LinkedHashSet<>();
        Set<String> added = new LinkedHashSet<>();
        List<JsonNode> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            JsonNode current = queue.removeFirst();
            String currentCode = PublishedProcessSnapshot.code(current);
            if (!isStartNode(current)) {
                if (!isEndNode(current) && added.add(currentCode)) result.add(current);
                continue;
            }
            if (!visitedControl.add(currentCode))
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"Flow 开始节点线路存在循环");
            if (edges == null || !edges.isArray()) continue;
            for (JsonNode edge : edges) {
                if (!currentCode.equals(edge.path("sourceNodeCode").asText(""))) continue;
                String condition = edge.has("condition") ? edge.get("condition").toString() : null;
                if (!TriggerConditionMatcher.matches(json, condition, values)) continue;
                String target = edge.path("targetNodeCode").asText("");
                JsonNode targetNode = byCode.get(target);
                if (targetNode != null) queue.addLast(targetNode);
                if (edge.path("exclusiveFlag").asBoolean(true)) break;
            }
        }
        return result;
    }

    private boolean isStartNode(JsonNode node) {
        return Set.of("START", "BEGIN", "START_EVENT")
                .contains(PublishedProcessSnapshot.type(node).toUpperCase(Locale.ROOT));
    }

    private boolean isEndNode(JsonNode node) {
        return Set.of("END", "FINISH", "TERMINATE")
                .contains(PublishedProcessSnapshot.type(node).toUpperCase(Locale.ROOT));
    }

}
