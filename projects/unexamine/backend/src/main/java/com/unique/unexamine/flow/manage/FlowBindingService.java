package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.StructuredRuleEvaluator;
import com.unique.unexamine.moduleconfig.manage.StructuredConditionProtocol;
import com.unique.unexamine.moduleconfig.manage.PublishedConditionCatalog;
import com.unique.unexamine.moduleconfig.manage.ModuleRuleModels.ConditionResult;
import com.unique.unexamine.audit.manage.AuditRecorder;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.authorization.manage.PermissionChecker;
import com.unique.unexamine.flow.base.entity.FlowBindingResolution;
import com.unique.unexamine.flow.base.entity.FlowDefinition;
import com.unique.unexamine.flow.base.entity.FlowPublication;
import com.unique.unexamine.flow.base.entity.FlowTriggerBinding;
import com.unique.unexamine.flow.base.entity.FlowVersion;
import com.unique.unexamine.flow.base.service.FlowBindingResolutionBaseService;
import com.unique.unexamine.flow.base.service.FlowDefinitionBaseService;
import com.unique.unexamine.flow.base.service.FlowPublicationBaseService;
import com.unique.unexamine.flow.base.service.FlowTriggerBindingBaseService;
import com.unique.unexamine.flow.base.service.FlowVersionBaseService;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModule;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModuleAction;
import com.unique.unexamine.moduleconfig.base.entity.ConfiguredModulePublication;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleActionBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ConfiguredModulePublicationBaseService;
import com.unique.unexamine.shared.manage.web.DomainException;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.ArrayList;

@Service
public class FlowBindingService {
    private static final Set<String> TRIGGER_EVENTS = Set.of(
            "CREATE", "UPDATE", "DELETE", "TRANSFER", "CONVERT", "CONVERSION_UNLINK", "STATUS_CHANGED", "MANUAL", "SCHEDULED",
            "APPLICATION", "IMPORT_COMPLETED", "EXCEPTION");
    private static final Set<String> EXECUTION_MODES = Set.of(
            "DIRECT", "AFTER_EXECUTION", "AFTER_APPROVAL", "FLOW_ONLY");
    private static final Set<String> EVENT_FLOW_ONLY = Set.of("SCHEDULED", "IMPORT_COMPLETED", "EXCEPTION");

    private final FlowTriggerBindingBaseService bindingService;
    private final FlowBindingResolutionBaseService resolutionService;
    private final FlowDefinitionBaseService definitionService;
    private final FlowPublicationBaseService publicationService;
    private final FlowVersionBaseService versionService;
    private final ConfiguredModuleBaseService moduleService;
    private final ConfiguredModulePublicationBaseService modulePublicationService;
    private final ConfiguredModuleActionBaseService actionService;
    private final SystemTenantBaseService tenantService;
    private final PermissionChecker permissionChecker;
    private final AuditRecorder auditRecorder;
    private final ObjectMapper objectMapper;
    private final ModulePublicationService moduleConfigurations;
    private final ChannelFieldPolicyResolver fieldPolicies;
    private final StructuredRuleEvaluator conditions;
    private final StructuredConditionProtocol typedConditions;
    private final PublishedConditionCatalog conditionCatalog;
    private final FlowConditionService flowConditions;

    public FlowBindingService(
            FlowTriggerBindingBaseService bindingService,
            FlowBindingResolutionBaseService resolutionService,
            FlowDefinitionBaseService definitionService,
            FlowPublicationBaseService publicationService,
            FlowVersionBaseService versionService,
            ConfiguredModuleBaseService moduleService,
            ConfiguredModulePublicationBaseService modulePublicationService,
            ConfiguredModuleActionBaseService actionService,
            SystemTenantBaseService tenantService,
            PermissionChecker permissionChecker,
            AuditRecorder auditRecorder,
            ObjectMapper objectMapper, ModulePublicationService moduleConfigurations,
            ChannelFieldPolicyResolver fieldPolicies, StructuredRuleEvaluator conditions,
            StructuredConditionProtocol typedConditions, PublishedConditionCatalog conditionCatalog, FlowConditionService flowConditions) {
        this.bindingService = bindingService;
        this.resolutionService = resolutionService;
        this.definitionService = definitionService;
        this.publicationService = publicationService;
        this.versionService = versionService;
        this.moduleService = moduleService;
        this.modulePublicationService = modulePublicationService;
        this.actionService = actionService;
        this.tenantService = tenantService;
        this.permissionChecker = permissionChecker;
        this.auditRecorder = auditRecorder;
        this.objectMapper = objectMapper;
        this.moduleConfigurations = moduleConfigurations;
        this.fieldPolicies = fieldPolicies;
        this.conditions = conditions;
        this.typedConditions = typedConditions;
        this.conditionCatalog = conditionCatalog;
        this.flowConditions = flowConditions;
    }

    @Transactional(readOnly = true)
    public FlowBindingModels.BindingList list(
            AuthenticatedContext context, Long moduleId, String triggerEvent) {
        return list(context, moduleId, null, triggerEvent);
    }

    @Transactional(readOnly = true)
    public FlowBindingModels.BindingList list(
            AuthenticatedContext context, Long moduleId, Long actionId, String triggerEvent) {
        requireSystem(context);
        requireAction(context, "VIEW");
        ConfiguredModule module = requireModule(context, moduleId);
        String event = triggerEvent == null || triggerEvent.isBlank() ? null : normalizeEvent(triggerEvent);
        Long mainTenantId = mainTenant(context.systemId()).getId();
        List<FlowTriggerBinding> all = bindingService.selectList(Wrappers.<FlowTriggerBinding>lambdaQuery()
                .eq(FlowTriggerBinding::getSystemId, context.systemId())
                .eq(FlowTriggerBinding::getModuleId, module.getId())
                .isNull(actionId == null, FlowTriggerBinding::getActionId)
                .eq(actionId != null, FlowTriggerBinding::getActionId, actionId)
                .eq(event != null, FlowTriggerBinding::getTriggerEvent, event)
                .orderByAsc(FlowTriggerBinding::getTriggerEvent)
                .orderByAsc(FlowTriggerBinding::getPriorityOrder));
        List<FlowBindingModels.BindingView> current = all.stream()
                .filter(binding -> Objects.equals(binding.getOwnerTenantId(), context.tenantId()))
                .map(binding -> view(binding, mainTenantId)).toList();
        List<FlowBindingModels.BindingView> inherited = Objects.equals(context.tenantId(), mainTenantId)
                ? List.of()
                : all.stream().filter(binding -> Objects.equals(binding.getOwnerTenantId(), mainTenantId))
                .map(binding -> view(binding, mainTenantId)).toList();
        return new FlowBindingModels.BindingList(current, inherited);
    }

    @Transactional
    public FlowBindingModels.BindingView publish(
            AuthenticatedContext context, FlowBindingModels.PublishBindingRequest input, String traceId) {
        requireSystem(context);
        requireAction(context, "BIND_PUBLISH");
        ConfiguredModule module = requireModule(context, input.moduleId());
        ConfiguredModuleAction action = requireActionTarget(context, module, input.actionId());
        String event = normalizeEvent(input.triggerEvent());
        String executionMode = input.executionMode().strip().toUpperCase(Locale.ROOT);
        if (!EXECUTION_MODES.contains(executionMode)) {
            throw invalid("FLOW_BINDING_EXECUTION_MODE_INVALID", "Flow 绑定执行模式无效");
        }
        if (action != null && "APPROVAL".equals(action.getCode())
                && (!"MANUAL".equals(event) || !"FLOW_ONLY".equals(executionMode))) {
            throw invalid("MODULE_APPROVAL_BINDING_INVALID", "审批动作只能绑定手动触发且仅运行 Flow 的流程");
        }
        if (EVENT_FLOW_ONLY.contains(event) && !"FLOW_ONLY".equals(executionMode)) {
            throw invalid("FLOW_BINDING_EXECUTION_MODE_INVALID", "周期、导入完成和数据异常事件只能配置为启动流程");
        }
        if (EVENT_FLOW_ONLY.contains(event) && action != null) {
            throw invalid("FLOW_BINDING_ACTION_INVALID", "周期、导入完成和数据异常事件不能绑定模块动作");
        }
        FlowBindingModels.ScheduleConfig schedule = "SCHEDULED".equals(event)
                ? FlowSchedulePolicy.normalize(input.schedule()) : null;
        if (!"SCHEDULED".equals(event) && input.schedule() != null) {
            throw invalid("FLOW_SCHEDULE_CONFIG_INVALID", "只有周期触发可以配置执行周期");
        }
        Map<String, String> statusMapping = approvalStatusMapping(input.approvalStatusMapping(), executionMode);
        if (!statusMapping.isEmpty() && (!Set.of("CREATE", "UPDATE").contains(event) || action != null)) {
            throw invalid("FLOW_APPROVAL_STATUS_MAPPING_ACTION_INVALID", "业务状态联动只适用于模块新建或编辑的审批绑定");
        }
        validateCondition(input.conditionExpression());
        JsonNode structuredCondition = input.condition();
        if (input.condition() != null) {
            if (blankToNull(input.conditionExpression()) != null) throw invalid("FLOW_BINDING_CONDITION_INVALID", "不能同时配置两种条件协议");
            structuredCondition = validateStructuredCondition(context, module, event, input.condition());
        }
        FlowDefinition flow = "DIRECT".equals(executionMode) ? null : requirePublishedFlow(context, input.flowId());
        Long flowId = flow == null ? null : flow.getId();
        FlowVersion version = flow == null ? null : requireVersion(publication(flowId).getCurrentVersionId(), flowId);
        if (action != null && "APPROVAL".equals(action.getCode()) && version != null) {
            // 审批动作必须进入真正的审批节点，避免选到只含开始/结束节点的普通自动流程。
            Object nodes = readMap(version.getSnapshotJson()).get("nodes");
            if (!(nodes instanceof List<?> list) || list.stream().noneMatch(node ->
                    node instanceof Map<?, ?> item && "APPROVAL".equals(item.get("nodeType")))) {
                throw invalid("MODULE_APPROVAL_FLOW_NODE_REQUIRED", "审批动作绑定的 Flow 必须包含审批节点");
            }
        }
        if (version != null) {
            validateFlowBusinessModule(module.getId(), version);
            flowConditions.validateCurrent(context, module.getId(), readMap(version.getSnapshotJson()));
        }
        Long versionId = version == null ? null : version.getId();
        int priority = input.priorityOrder() == null ? 0 : input.priorityOrder();

        List<FlowTriggerBinding> scope = scopeBindingsForUpdate(
                context.systemId(), context.tenantId(), module.getId(), action == null ? null : action.getId(), event);
        List<FlowTriggerBinding> active = scope.stream().filter(item -> "ACTIVE".equals(item.getStatus())).toList();
        boolean sameActive = active.size() == 1 && Objects.equals(active.getFirst().getFlowId(), flowId)
                && active.getFirst().getPriorityOrder() == priority
                && Objects.equals(policy(active.getFirst()).executionMode(), executionMode)
                && Objects.equals(policy(active.getFirst()).flowVersionId(), versionId)
                && policy(active.getFirst()).mutuallyExclusive() == !Boolean.FALSE.equals(input.mutuallyExclusive())
                && Objects.equals(policy(active.getFirst()).condition(), structuredCondition)
                && Objects.equals(policy(active.getFirst()).schedule(), schedule)
                && Objects.equals(policy(active.getFirst()).approvalStatusMapping(), statusMapping)
                && Objects.equals(policy(active.getFirst()).conditionExpression(), blankToNull(input.conditionExpression()));
        if (sameActive) {
            return view(active.getFirst(), mainTenant(context.systemId()).getId());
        }
        if (!active.isEmpty() && !Boolean.TRUE.equals(input.replaceExisting())) {
            throw conflict("FLOW_BINDING_REPLACEMENT_REQUIRED",
                    "相同生效范围已有启用绑定，确认替换后才会停用旧绑定");
        }
        for (FlowTriggerBinding old : active) {
            old.setStatus("DISABLED");
            if (bindingService.updateById(old) != 1) {
                throw conflict("FLOW_BINDING_VERSION_CONFLICT", "Flow 绑定已被其他人修改，请刷新后重试");
            }
        }

        FlowTriggerBinding occupiedPriority = scope.stream()
                .filter(item -> Objects.equals(item.getPriorityOrder(), priority)).findFirst().orElse(null);
        if (occupiedPriority != null) {
            int archivedPriority = scope.stream().map(FlowTriggerBinding::getPriorityOrder)
                    .max(Integer::compareTo).orElse(priority) + 1;
            occupiedPriority.setStatus("DISABLED");
            occupiedPriority.setPriorityOrder(archivedPriority);
            if (bindingService.updateById(occupiedPriority) != 1) {
                throw conflict("FLOW_BINDING_VERSION_CONFLICT", "Flow 绑定已被其他人修改，请刷新后重试");
            }
        }
        FlowTriggerBinding binding = new FlowTriggerBinding();
        binding.setSystemId(context.systemId());
        binding.setOwnerTenantId(context.tenantId());
        binding.setModuleId(module.getId());
        binding.setActionId(action == null ? null : action.getId());
        binding.setTriggerEvent(event);
        binding.setVersion(0);
        binding.setFlowId(flowId);
        binding.setPriorityOrder(priority);
        binding.setConditionExpression(toPolicyJson(executionMode, input.conditionExpression(), structuredCondition,
                input.mutuallyExclusive(), versionId, schedule, schedule == null ? null : context, statusMapping));
        binding.setStatus("ACTIVE");
        bindingService.insert(binding);
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("moduleId", module.getId());
        detail.put("actionId", action == null ? null : action.getId());
        detail.put("triggerEvent", event);
        detail.put("flowId", flowId);
        detail.put("flowVersionId", versionId);
        detail.put("ownerTenantId", context.tenantId());
        audit(context, traceId, "FLOW_BINDING_PUBLISHED", "FLOW_TRIGGER_BINDING", binding.getId(), detail);
        return view(binding, mainTenant(context.systemId()).getId());
    }

    @Transactional
    public FlowBindingModels.ResolutionView resolve(
            AuthenticatedContext context, FlowBindingModels.ResolveBindingRequest input, String traceId) {
        requireSystem(context);
        requireAction(context, "TRIGGER");
        return resolveInternal(context, input, traceId, false);
    }

    /** Called only after the business service has checked module/action/data permissions. */
    @Transactional
    public FlowBindingModels.ResolutionView resolveForAction(
            AuthenticatedContext context, Long moduleId, String actionCode,
            Map<String, Object> variables, String traceId) {
        requireSystem(context);
        Long mainTenantId = mainTenant(context.systemId()).getId();
        ConfiguredModule module = moduleService.selectById(moduleId);
        Long actionId = null;
        // 使用发布快照的逻辑编码及可信物理身份，不能按内部编码误选其他租户的动作。
        for (JsonNode action : moduleConfigurations.published(context, module.getCode()).configuration().path("actions")) {
            if (actionCode.equals(action.path("code").asText()) && "ACTIVE".equals(action.path("status").asText())) {
                actionId = action.path("id").asLong();
                break;
            }
        }
        if (actionId != null && activeBindings(context.systemId(), context.tenantId(), moduleId, actionId, actionCode).isEmpty()
                && activeBindings(context.systemId(), mainTenantId, moduleId, actionId, actionCode).isEmpty()) {
            actionId = null;
        }
        return resolveInternal(context, new FlowBindingModels.ResolveBindingRequest(
                moduleId, actionId, actionCode, variables), traceId, true);
    }

    /**
     * 解析已发布的手动动作绑定。业务权限仍使用动作自身编码，Flow 触发点统一使用 MANUAL。
     */
    @Transactional
    public FlowBindingModels.ResolutionView resolveManualAction(
            AuthenticatedContext context, Long moduleId, Long actionId,
            Map<String, Object> variables, String traceId) {
        requireSystem(context);
        return resolveInternal(context, new FlowBindingModels.ResolveBindingRequest(
                moduleId, actionId, "MANUAL", variables), traceId, true);
    }

    /**
     * 业务服务已确认事件来源和模块范围后使用的内部解析入口。没有启用绑定或条件不命中时安全返回空。
     */
    @Transactional
    public FlowBindingModels.ResolutionView resolveForEvent(
            AuthenticatedContext context, Long moduleId, String triggerEvent,
            Map<String, Object> variables, String traceId) {
        requireSystem(context);
        String event = normalizeEvent(triggerEvent);
        if (!EVENT_FLOW_ONLY.contains(event)) {
            throw invalid("FLOW_BINDING_TRIGGER_EVENT_INVALID", "该事件尚未接入可靠 Flow 触发入口");
        }
        return resolveInternal(context, new FlowBindingModels.ResolveBindingRequest(
                moduleId, null, event, variables), traceId, true);
    }

    private FlowBindingModels.ResolutionView resolveInternal(
            AuthenticatedContext context, FlowBindingModels.ResolveBindingRequest input, String traceId,
            boolean optional) {
        ConfiguredModule module = requireModule(context, input.moduleId());
        ConfiguredModuleAction action = requireActionTarget(context, module, input.actionId());
        String event = normalizeEvent(input.triggerEvent());
        Long mainTenantId = mainTenant(context.systemId()).getId();
        List<FlowTriggerBinding> own = activeBindings(
                context.systemId(), context.tenantId(), module.getId(), action == null ? null : action.getId(), event);
        List<FlowTriggerBinding> defaults = Objects.equals(mainTenantId, context.tenantId()) ? List.of()
                : activeBindings(context.systemId(), mainTenantId, module.getId(),
                action == null ? null : action.getId(), event);
        if (own.size() > 1 || defaults.size() > 1) {
            throw conflict("FLOW_BINDING_SCOPE_AMBIGUOUS", "同一生效范围存在多个启用绑定，必须先修复配置");
        }
        FlowTriggerBinding binding = !own.isEmpty() ? own.getFirst()
                : !defaults.isEmpty() ? defaults.getFirst() : null;
        if (binding == null) {
            if (optional) return null;
            throw new DomainException("FLOW_BINDING_NOT_FOUND", "当前业务触发点没有可用 Flow 绑定", HttpStatus.NOT_FOUND);
        }
        BindingPolicy policy = policy(binding);
        if (!optional && policy.condition() != null) {
            validateStructuredCondition(context, module, event, policy.condition());
            validateVariables(context, module, event, input.variables());
        }
        if (!(policy.condition() == null ? matches(policy.conditionExpression(), input.variables())
                : typedConditions.matches(policy.condition(), objectMapper.valueToTree(input.variables() == null ? Map.of() : input.variables()),
                    typedFields(conditionFieldsInternal(context, module, event))))) {
            if (optional) return null;
            throw invalid("FLOW_BINDING_CONDITION_NOT_MATCHED", "业务数据未命中 Flow 绑定条件");
        }
        if ("DIRECT".equals(policy.executionMode())) return new FlowBindingModels.ResolutionView(
                null, binding.getId(), Objects.equals(binding.getOwnerTenantId(), mainTenantId) ? "DEFAULT" : "TENANT_OVERRIDE",
                binding.getOwnerTenantId(), context.tenantId(), null, null, null, null, Map.of(),
                "DIRECT", policy.mutuallyExclusive(), policy.approvalStatusMapping(),
                "DIRECT_CONFIGURATION", LocalDateTime.now());
        FlowDefinition flow = definitionService.selectById(binding.getFlowId());
        if (flow == null || !Objects.equals(flow.getSystemId(), context.systemId())
                || !Objects.equals(flow.getOwnerTenantId(), binding.getOwnerTenantId())) {
            throw invalid("FLOW_BINDING_VERSION_INCOMPATIBLE", "Flow 绑定与生效租户不兼容");
        }
        if ("DISABLED".equals(flow.getStatus())) throw invalid("FLOW_BINDING_FLOW_DISABLED", "绑定流程已停用，不能发起新申请");
        // 新动作读取当前发布指针；本次解析结果随动作保存，后续审批/投递继续使用这份不可变版本。
        FlowVersion version = requireVersion(publication(flow.getId()).getCurrentVersionId(), flow.getId());
        validateFlowBusinessModule(module.getId(), version);
        flowConditions.validateCurrent(context, module.getId(), readMap(version.getSnapshotJson()));
        boolean override = !Objects.equals(binding.getOwnerTenantId(), mainTenantId);
        String reason = override ? "TENANT_OVERRIDE" : "MAIN_TENANT_DEFAULT";

        FlowBindingResolution resolution = new FlowBindingResolution();
        resolution.setSystemId(context.systemId());
        resolution.setTenantId(context.tenantId());
        resolution.setModuleId(module.getId());
        resolution.setTriggerEvent(event);
        resolution.setBindingId(binding.getId());
        resolution.setFlowVersionId(version.getId());
        resolution.setResolutionReason(reason);
        resolutionService.insert(resolution);
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("bindingId", binding.getId());
        detail.put("sourceTenantId", binding.getOwnerTenantId());
        detail.put("requestedTenantId", context.tenantId());
        detail.put("flowVersionId", version.getId());
        detail.put("resolutionReason", reason);
        audit(context, traceId, "FLOW_BINDING_RESOLVED", "FLOW_BINDING_RESOLUTION", resolution.getId(), detail);
        return new FlowBindingModels.ResolutionView(
                resolution.getId(), binding.getId(), override ? "TENANT_OVERRIDE" : "DEFAULT",
                binding.getOwnerTenantId(), context.tenantId(), flow.getId(), version.getId(),
                version.getVersionNumber(), version.getDefinitionHash(), readMap(version.getSnapshotJson()),
                policy.executionMode(), policy.mutuallyExclusive(), policy.approvalStatusMapping(),
                reason, resolution.getResolveTime());
    }

    @Transactional(readOnly = true)
    public List<FlowBindingModels.ConditionField> conditionFields(AuthenticatedContext context, Long moduleId, String event) {
        requireSystem(context);
        requireAction(context, "VIEW");
        return conditionFieldsInternal(context, requireModule(context, moduleId), normalizeEvent(event));
    }

    private List<FlowBindingModels.ConditionField> conditionFieldsInternal(AuthenticatedContext context, ConfiguredModule module, String event) {
        if ("SCHEDULED".equals(event)) {
            return List.of(
                    new FlowBindingModels.ConditionField("scheduledTime", "计划执行时间", "DATETIME", "{}"),
                    new FlowBindingModels.ConditionField("scheduleMode", "周期方式", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("misfire", "是否错过原计划", "BOOLEAN", "{}"));
        }
        if ("IMPORT_COMPLETED".equals(event)) {
            return List.of(
                    new FlowBindingModels.ConditionField("importStatus", "导入结果", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("totalRows", "总行数", "NUMBER", "{}"),
                    new FlowBindingModels.ConditionField("successRows", "成功行数", "NUMBER", "{}"),
                    new FlowBindingModels.ConditionField("failedRows", "失败行数", "NUMBER", "{}"));
        }
        if ("EXCEPTION".equals(event)) {
            return List.of(
                    new FlowBindingModels.ConditionField("exceptionSource", "异常来源", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("importStatus", "导入结果", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("totalRows", "总行数", "NUMBER", "{}"),
                    new FlowBindingModels.ConditionField("successRows", "成功行数", "NUMBER", "{}"),
                    new FlowBindingModels.ConditionField("failedRows", "失败行数", "NUMBER", "{}"),
                    new FlowBindingModels.ConditionField("actionCode", "模块动作", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("errorCategory", "校验错误类别", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("ruleType", "规则类型", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("ruleId", "规则编号", "NUMBER", "{}"),
                    new FlowBindingModels.ConditionField("ruleCode", "规则编码", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("ruleName", "规则名称", "TEXT", "{}"),
                    new FlowBindingModels.ConditionField("recordId", "业务记录编号", "NUMBER", "{}"));
        }
        String action = "CONVERSION_UNLINK".equals(event) ? "CONVERT"
                : Set.of("CREATE", "UPDATE", "DELETE", "TRANSFER", "CONVERT").contains(event) ? event : "DETAIL";
        var publication = moduleConfigurations.published(context, module.getCode());
        return conditionCatalog.readableFields(context, module.getCode(), publication, action, List.of("PAGE", "FLOW")).stream()
                .map(field -> new FlowBindingModels.ConditionField(field.code(), field.name(), field.fieldType(), field.config().toString())).toList();
    }

    /** 普通配置与预览使用有权字段；后台触发判定另用当前来源发布目录。 */
    /** 新定义绑定同一业务模块，避免把其他模块字段缺值静默当成默认分支；无目录的旧定义保持兼容。 */
    private void validateFlowBusinessModule(Long moduleId, FlowVersion version) {
        Object conditionContext = readMap(version.getSnapshotJson()).get("conditionContext");
        if (!(conditionContext instanceof Map<?, ?> profile) || profile.get("moduleId") == null) return;
        if (!Objects.equals(String.valueOf(moduleId), String.valueOf(profile.get("moduleId"))))
            throw invalid("FLOW_BINDING_BUSINESS_MODULE_MISMATCH", "流程业务模块与动作模块不一致，请重新选择流程");
    }

    private List<StructuredConditionProtocol.Field> typedFields(List<FlowBindingModels.ConditionField> fields) {
        return fields.stream().map(field -> new StructuredConditionProtocol.Field(
                field.code(), field.name(), field.fieldType(), readJson(field.configJson()))).toList();
    }

    private JsonNode readJson(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw invalid("FLOW_BINDING_CONDITION_INVALID", "条件或字段配置无法读取"); }
    }

    private JsonNode validateStructuredCondition(AuthenticatedContext context, ConfiguredModule module, String event, JsonNode condition) {
        return typedConditions.normalize(condition, typedFields(conditionFieldsInternal(context, module, event)), "FLOW_BINDING_CONDITION_INVALID");
    }

    private boolean validValue(String type, JsonNode value) {
        if (Set.of("NUMBER", "MONEY").contains(type)) return value.isNumber();
        if ("BOOLEAN".equals(type)) return value.isBoolean();
        if ("MULTI_SELECT".equals(type)) return value.isArray() && java.util.stream.StreamSupport.stream(value.spliterator(), false).allMatch(JsonNode::isTextual);
        if (!value.isTextual()) return false;
        try {
            if ("DATE".equals(type)) java.time.LocalDate.parse(value.asText());
            if ("DATETIME".equals(type)) {
                try { java.time.OffsetDateTime.parse(value.asText()); }
                catch (java.time.format.DateTimeParseException ignored) { java.time.LocalDateTime.parse(value.asText()); }
            }
            return true;
        } catch (java.time.format.DateTimeParseException exception) { return false; }
    }

    private void validateVariables(AuthenticatedContext context, ConfiguredModule module, String event, Map<String, Object> variables) {
        var allowed = conditionFieldsInternal(context, module, event);
        for (var entry : (variables == null ? Map.<String, Object>of() : variables).entrySet()) {
            var field = allowed.stream().filter(item -> item.code().equals(entry.getKey())).findFirst()
                    .orElseThrow(() -> invalid("FLOW_BINDING_FIELD_NOT_ALLOWED", "测试数据引用不可读取的字段"));
            JsonNode value = objectMapper.valueToTree(entry.getValue());
            if (!value.isNull() && !validValue(field.fieldType(), value)) throw invalid("FLOW_BINDING_CONDITION_INVALID", field.name() + "的测试值类型不正确");
        }
    }

    @Transactional(readOnly = true)
    public FlowBindingModels.ConditionPreview previewCondition(AuthenticatedContext context, FlowBindingModels.PreviewConditionRequest input) {
        requireSystem(context); requireAction(context, "TRIGGER");
        var module = requireModule(context, input.moduleId());
        String event = normalizeEvent(input.triggerEvent());
        validateStructuredCondition(context, module, event, input.condition());
        validateVariables(context, module, event, input.variables());
        List<ConditionResult> results = new ArrayList<>();
        boolean matched = typedConditions.matches(input.condition(), objectMapper.valueToTree(input.variables() == null ? Map.of() : input.variables()),
                typedFields(conditionFieldsInternal(context, module, event)), results);
        return new FlowBindingModels.ConditionPreview(matched, results);
    }

    private ConfiguredModule requireModule(AuthenticatedContext context, Long moduleId) {
        ConfiguredModule module = moduleService.selectById(moduleId);
        Long mainTenantId = mainTenant(context.systemId()).getId();
        if (module == null || !Objects.equals(module.getSystemId(), context.systemId())
                || !(Objects.equals(module.getOwnerTenantId(), context.tenantId())
                || Objects.equals(module.getOwnerTenantId(), mainTenantId))) {
            throw new DomainException("FLOW_BINDING_MODULE_NOT_FOUND", "模块不存在或不在当前范围", HttpStatus.NOT_FOUND);
        }
        ConfiguredModulePublication publication = modulePublicationService.selectList(
                Wrappers.<ConfiguredModulePublication>lambdaQuery()
                        .eq(ConfiguredModulePublication::getModuleId, module.getId())).stream().findFirst().orElse(null);
        if (publication == null || publication.getCurrentVersionId() == null) {
            throw invalid("FLOW_BINDING_MODULE_NOT_PUBLISHED", "模块发布后才能配置 Flow 绑定");
        }
        return module;
    }

    private ConfiguredModuleAction requireActionTarget(
            AuthenticatedContext context, ConfiguredModule module, Long actionId) {
        if (actionId == null) {
            return null;
        }
        ConfiguredModuleAction action = actionService.selectById(actionId);
        if (action == null || !Objects.equals(action.getSystemId(), context.systemId())
                || !Objects.equals(action.getModuleId(), module.getId())
                || !(Objects.equals(action.getOwnerTenantId(), context.tenantId())
                || Objects.equals(action.getOwnerTenantId(), module.getOwnerTenantId()))) {
            throw invalid("FLOW_BINDING_ACTION_INVALID", "模块动作不存在、未启用或与模块不匹配");
        }
        boolean published = false;
        for (JsonNode configured : moduleConfigurations.published(context, module.getCode()).configuration().path("actions")) {
            if (actionId.equals(configured.path("id").asLong())
                    && "ACTIVE".equals(configured.path("status").asText())) published = true;
        }
        if (!published) throw invalid("FLOW_BINDING_ACTION_INVALID", "动作不在当前租户已发布配置中");
        return action;
    }

    private FlowDefinition requirePublishedFlow(AuthenticatedContext context, Long flowId) {
        if (flowId == null) throw invalid("FLOW_BINDING_FLOW_NOT_PUBLISHED", "请选择已发布流程");
        FlowDefinition flow = definitionService.selectById(flowId);
        if (flow == null || !"SYSTEM".equals(flow.getContextType())
                || !Objects.equals(flow.getSystemId(), context.systemId())
                || !Objects.equals(flow.getOwnerTenantId(), context.tenantId())) {
            throw invalid("FLOW_BINDING_FLOW_SCOPE_INVALID", "只能绑定当前租户发布的系统 Flow");
        }
        publication(flowId);
        if ("DISABLED".equals(flow.getStatus())) throw invalid("FLOW_BINDING_FLOW_DISABLED", "流程已停用，不能发布动作绑定");
        return flow;
    }

    private FlowPublication publication(Long flowId) {
        return publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                        .eq(FlowPublication::getFlowId, flowId)).stream().findFirst()
                .orElseThrow(() -> invalid("FLOW_BINDING_FLOW_NOT_PUBLISHED", "Flow 发布版本不存在"));
    }

    private FlowVersion requireVersion(Long versionId, Long flowId) {
        FlowVersion version = versionService.selectById(versionId);
        if (version == null || !Objects.equals(version.getFlowId(), flowId)) {
            throw invalid("FLOW_BINDING_VERSION_INCOMPATIBLE", "Flow 当前发布版本不存在或不兼容");
        }
        return version;
    }

    private List<FlowTriggerBinding> scopeBindings(
            Long systemId, Long tenantId, Long moduleId, Long actionId, String event) {
        return bindingService.selectList(Wrappers.<FlowTriggerBinding>lambdaQuery()
                .eq(FlowTriggerBinding::getSystemId, systemId)
                .eq(FlowTriggerBinding::getOwnerTenantId, tenantId)
                .eq(FlowTriggerBinding::getModuleId, moduleId)
                .isNull(actionId == null, FlowTriggerBinding::getActionId)
                .eq(actionId != null, FlowTriggerBinding::getActionId, actionId)
                .eq(FlowTriggerBinding::getTriggerEvent, event)
                .orderByAsc(FlowTriggerBinding::getPriorityOrder));
    }

    private List<FlowTriggerBinding> scopeBindingsForUpdate(
            Long systemId, Long tenantId, Long moduleId, Long actionId, String event) {
        return bindingService.selectList(Wrappers.<FlowTriggerBinding>lambdaQuery()
                .eq(FlowTriggerBinding::getSystemId, systemId)
                .eq(FlowTriggerBinding::getOwnerTenantId, tenantId)
                .eq(FlowTriggerBinding::getModuleId, moduleId)
                .isNull(actionId == null, FlowTriggerBinding::getActionId)
                .eq(actionId != null, FlowTriggerBinding::getActionId, actionId)
                .eq(FlowTriggerBinding::getTriggerEvent, event)
                .orderByAsc(FlowTriggerBinding::getPriorityOrder)
                .last("FOR UPDATE"));
    }

    private List<FlowTriggerBinding> activeBindings(
            Long systemId, Long tenantId, Long moduleId, Long actionId, String event) {
        return scopeBindings(systemId, tenantId, moduleId, actionId, event).stream()
                .filter(binding -> "ACTIVE".equals(binding.getStatus()))
                .sorted(Comparator.comparing(FlowTriggerBinding::getPriorityOrder)).toList();
    }

    private FlowBindingModels.BindingView view(FlowTriggerBinding binding, Long mainTenantId) {
        BindingPolicy policy = policy(binding);
        FlowPublication publication = binding.getFlowId() == null ? null : publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                        .eq(FlowPublication::getFlowId, binding.getFlowId())).stream().findFirst().orElse(null);
        FlowVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        return new FlowBindingModels.BindingView(
                binding.getId(), binding.getSystemId(), binding.getOwnerTenantId(),
                Objects.equals(binding.getOwnerTenantId(), mainTenantId) ? "DEFAULT" : "TENANT_OVERRIDE",
                binding.getModuleId(), binding.getActionId(), binding.getTriggerEvent(), binding.getFlowId(),
                version == null ? null : version.getId(), version == null ? null : version.getVersionNumber(),
                policy.executionMode(), binding.getPriorityOrder(), policy.conditionExpression(),
                policy.condition(),
                policy.mutuallyExclusive(), policy.schedule(), policy.approvalStatusMapping(),
                binding.getStatus(), binding.getVersion(), binding.getUpdateTime());
    }

    private SystemTenant mainTenant(Long systemId) {
        return tenantService.selectList(Wrappers.<SystemTenant>lambdaQuery()
                        .eq(SystemTenant::getSystemId, systemId)
                        .eq(SystemTenant::getMain, true)
                        .eq(SystemTenant::getStatus, "ACTIVE")).stream().findFirst()
                .orElseThrow(() -> invalid("MAIN_TENANT_NOT_FOUND", "系统主租户不存在或未启用"));
    }

    private String normalizeEvent(String event) {
        String normalized = event.strip().toUpperCase(Locale.ROOT);
        if (!TRIGGER_EVENTS.contains(normalized)) {
            throw invalid("FLOW_BINDING_TRIGGER_EVENT_INVALID", "Flow 触发事件无效");
        }
        return normalized;
    }

    private String toPolicyJson(String mode, String expression, JsonNode condition, Boolean mutuallyExclusive,
                                Long flowVersionId, FlowBindingModels.ScheduleConfig schedule,
                                AuthenticatedContext scheduleActor, Map<String, String> approvalStatusMapping) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("executionMode", mode);
        value.put("conditionExpression", blankToNull(expression));
        value.put("condition", condition);
        value.put("flowVersionId", flowVersionId);
        value.put("mutuallyExclusive", !Boolean.FALSE.equals(mutuallyExclusive));
        value.put("schedule", schedule);
        value.put("scheduleActor", scheduleActor == null ? null : scheduleActor(scheduleActor));
        value.put("approvalStatusMapping", approvalStatusMapping);
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize Flow binding policy", exception);
        }
    }

    private BindingPolicy policy(FlowTriggerBinding binding) {
        if (binding.getConditionExpression() == null || binding.getConditionExpression().isBlank()) {
            return new BindingPolicy("AFTER_EXECUTION", null, null, true, null, null, Map.of(), Map.of());
        }
        try {
            Map<String, Object> value = objectMapper.readValue(binding.getConditionExpression(), new TypeReference<>() { });
            return new BindingPolicy(
                    String.valueOf(value.getOrDefault("executionMode", "AFTER_EXECUTION")),
                    blankToNull(value.get("conditionExpression") == null ? null
                            : String.valueOf(value.get("conditionExpression"))),
                    value.get("condition") == null ? null : objectMapper.valueToTree(value.get("condition")),
                    !Boolean.FALSE.equals(value.get("mutuallyExclusive")),
                    value.get("flowVersionId") == null ? null : ((Number) value.get("flowVersionId")).longValue(),
                    value.get("schedule") == null ? null
                            : objectMapper.convertValue(value.get("schedule"), FlowBindingModels.ScheduleConfig.class),
                    value.get("scheduleActor") instanceof Map<?, ?> actor
                            ? objectMapper.convertValue(actor, new TypeReference<Map<String, Object>>() { }) : Map.of(),
                    value.get("approvalStatusMapping") instanceof Map<?, ?> mapping
                            ? objectMapper.convertValue(mapping, new TypeReference<Map<String, String>>() { }) : Map.of());
        } catch (JsonProcessingException | IllegalArgumentException ignored) {
            return new BindingPolicy("AFTER_EXECUTION", binding.getConditionExpression(), null, true, null, null, Map.of(), Map.of());
        }
    }

    /** 审批结果只使用明确发布的状态编码；草稿和旧绑定缺省时不改变原有业务状态。 */
    private Map<String, String> approvalStatusMapping(Map<String, String> input, String executionMode) {
        if (input == null || input.isEmpty()) return Map.of();
        if (!"AFTER_APPROVAL".equals(executionMode)) {
            throw invalid("FLOW_APPROVAL_STATUS_MAPPING_MODE_INVALID", "只有审批通过后保存的绑定可以配置业务状态联动");
        }
        LinkedHashMap<String, String> mapping = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : input.entrySet()) {
            if (!Set.of("COMPLETED", "REJECTED", "WITHDRAWN").contains(entry.getKey())
                    || entry.getValue() == null || !entry.getValue().strip().matches("[A-Z][A-Z0-9_]{0,63}")) {
                throw invalid("FLOW_APPROVAL_STATUS_MAPPING_INVALID", "审批结果与目标业务状态编码无效");
            }
            mapping.put(entry.getKey(), entry.getValue().strip());
        }
        return Map.copyOf(mapping);
    }

    /** 内部调度器只读取启用的周期绑定和发布人身份引用，不暴露给普通页面。 */
    @Transactional(readOnly = true)
    public List<ScheduledBinding> activeSchedules() {
        return bindingService.selectList(Wrappers.<FlowTriggerBinding>lambdaQuery()
                        .eq(FlowTriggerBinding::getTriggerEvent, "SCHEDULED")
                        .eq(FlowTriggerBinding::getStatus, "ACTIVE")
                        .orderByAsc(FlowTriggerBinding::getId)).stream()
                .map(binding -> {
                    BindingPolicy policy = policy(binding);
                    ConfiguredModule module = moduleService.selectById(binding.getModuleId());
                    if (policy.schedule() == null || module == null || policy.scheduleActor().isEmpty()) return null;
                    return new ScheduledBinding(binding.getId(), binding.getSystemId(), binding.getOwnerTenantId(),
                            binding.getModuleId(), module.getCode(), binding.getUpdateTime(), policy.schedule(),
                            scheduleActor(policy.scheduleActor()));
                }).filter(Objects::nonNull).toList();
    }

    private Map<String, Object> scheduleActor(AuthenticatedContext context) {
        LinkedHashMap<String, Object> actor = new LinkedHashMap<>();
        actor.put("accountId", context.accountId()); actor.put("platformId", context.platformId());
        actor.put("systemId", context.systemId()); actor.put("tenantId", context.tenantId());
        actor.put("memberId", context.memberId()); actor.put("tenantMemberId", context.tenantMemberId());
        actor.put("username", context.username()); actor.put("displayName", context.displayName());
        actor.put("mfaLevel", context.mfaLevel());
        return actor;
    }

    private AuthenticatedContext scheduleActor(Map<String, Object> actor) {
        return new AuthenticatedContext(null, longValue(actor.get("accountId")), longValue(actor.get("platformId")),
                longValue(actor.get("systemId")), longValue(actor.get("tenantId")), longValue(actor.get("memberId")),
                longValue(actor.get("tenantMemberId")), String.valueOf(actor.getOrDefault("username", "schedule")),
                String.valueOf(actor.getOrDefault("displayName", "周期触发")),
                String.valueOf(actor.getOrDefault("mfaLevel", "PASSWORD")), List.of(), List.of(), Map.of());
    }

    private Long longValue(Object value) {
        return value == null ? null : Long.valueOf(value.toString());
    }

    private boolean matches(String expression, Map<String, Object> variables) {
        if (expression == null) {
            return true;
        }
        String[] operators = {">=", "<=", "!=", "==", ">", "<"};
        for (String operator : operators) {
            int index = expression.indexOf(operator);
            if (index > 0) {
                String key = expression.substring(0, index).strip();
                Object actual = variables == null ? null : variables.get(key);
                String expected = expression.substring(index + operator.length()).strip().replaceAll("^['\"]|['\"]$", "");
                if (actual == null) {
                    return false;
                }
                BigDecimal left = number(actual);
                BigDecimal right = number(expected);
                int compared = left != null && right != null ? left.compareTo(right)
                        : String.valueOf(actual).compareTo(expected);
                return switch (operator) {
                    case ">=" -> compared >= 0;
                    case "<=" -> compared <= 0;
                    case "!=" -> compared != 0;
                    case "==" -> compared == 0;
                    case ">" -> compared > 0;
                    case "<" -> compared < 0;
                    default -> false;
                };
            }
        }
        throw invalid("FLOW_BINDING_CONDITION_INVALID", "Flow 绑定条件格式无效");
    }

    private void validateCondition(String expression) {
        String value = blankToNull(expression);
        if (value == null) {
            return;
        }
        String[] operators = {">=", "<=", "!=", "==", ">", "<"};
        for (String operator : operators) {
            int index = value.indexOf(operator);
            if (index > 0 && !value.substring(0, index).isBlank()
                    && !value.substring(index + operator.length()).isBlank()) {
                return;
            }
        }
        throw invalid("FLOW_BINDING_CONDITION_INVALID", "Flow 绑定条件格式无效");
    }

    private BigDecimal number(Object value) {
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private Map<String, Object> readMap(String json) {
        try {
            return json == null ? Map.of() : objectMapper.readValue(json, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot read Flow version snapshot", exception);
        }
    }

    private void requireSystem(AuthenticatedContext context) {
        if (context == null || context.systemId() == null || context.tenantId() == null) {
            throw new DomainException("SYSTEM_CONTEXT_REQUIRED", "Flow 绑定只允许在系统租户上下文操作", HttpStatus.BAD_REQUEST);
        }
    }

    private void requireAction(AuthenticatedContext context, String action) {
        if (!permissionChecker.allows(context, "FLOW", "SYSTEM", action)
                && !permissionChecker.allows(context, "FLOW", "*", action)) {
            throw new DomainException("PERMISSION_DENIED", "没有 Flow " + action + " 权限", HttpStatus.FORBIDDEN);
        }
    }

    private void audit(AuthenticatedContext context, String traceId, String eventCode,
                       String objectType, Object objectId, Map<String, ?> detail) {
        auditRecorder.record(traceId, context.accountId(), context.systemId(), context.tenantId(), context.memberId(),
                eventCode, objectType, String.valueOf(objectId), "SUCCESS", detail);
    }

    private DomainException invalid(String code, String message) {
        return new DomainException(code, message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private DomainException conflict(String code, String message) {
        return new DomainException(code, message, HttpStatus.CONFLICT);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record ScheduledBinding(Long bindingId, Long systemId, Long tenantId, Long moduleId, String moduleCode,
                                   LocalDateTime updatedTime, FlowBindingModels.ScheduleConfig schedule,
                                   AuthenticatedContext actor) { }

    private record BindingPolicy(String executionMode, String conditionExpression, JsonNode condition,
                                 boolean mutuallyExclusive, Long flowVersionId,
                                 FlowBindingModels.ScheduleConfig schedule, Map<String, Object> scheduleActor,
                                 Map<String, String> approvalStatusMapping) {
    }
}
