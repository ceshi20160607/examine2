package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.unique.unexamine.authorization.manage.ChannelFieldPolicyResolver;
import com.unique.unexamine.moduleconfig.manage.ModulePublicationService;
import com.unique.unexamine.moduleconfig.manage.StructuredRuleEvaluator;
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
            "CREATE", "UPDATE", "DELETE", "STATUS_CHANGED", "MANUAL", "SCHEDULED",
            "APPLICATION", "IMPORT_COMPLETED", "EXCEPTION");
    private static final Set<String> EXECUTION_MODES = Set.of(
            "DIRECT", "AFTER_EXECUTION", "AFTER_APPROVAL", "FLOW_ONLY");

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
            ChannelFieldPolicyResolver fieldPolicies, StructuredRuleEvaluator conditions) {
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
    }

    @Transactional(readOnly = true)
    public FlowBindingModels.BindingList list(
            AuthenticatedContext context, Long moduleId, String triggerEvent) {
        requireSystem(context);
        requireAction(context, "VIEW");
        ConfiguredModule module = requireModule(context, moduleId);
        String event = triggerEvent == null || triggerEvent.isBlank() ? null : normalizeEvent(triggerEvent);
        Long mainTenantId = mainTenant(context.systemId()).getId();
        List<FlowTriggerBinding> all = bindingService.selectList(Wrappers.<FlowTriggerBinding>lambdaQuery()
                .eq(FlowTriggerBinding::getSystemId, context.systemId())
                .eq(FlowTriggerBinding::getModuleId, module.getId())
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
        validateCondition(input.conditionExpression());
        if (input.condition() != null) {
            if (blankToNull(input.conditionExpression()) != null) throw invalid("FLOW_BINDING_CONDITION_INVALID", "不能同时配置两种条件协议");
            validateStructuredCondition(context, module, event, input.condition());
        }
        FlowDefinition flow = "DIRECT".equals(executionMode) ? null : requirePublishedFlow(context, input.flowId());
        Long flowId = flow == null ? null : flow.getId();
        FlowVersion version = flow == null ? null : requireVersion(publication(flowId).getCurrentVersionId(), flowId);
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
                && Objects.equals(policy(active.getFirst()).condition(), input.condition())
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
        binding.setConditionExpression(toPolicyJson(executionMode, input.conditionExpression(), input.condition(), input.mutuallyExclusive(), versionId));
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
        ConfiguredModuleAction action = actionService.selectList(Wrappers.<ConfiguredModuleAction>lambdaQuery()
                .eq(ConfiguredModuleAction::getModuleId, moduleId)
                .eq(ConfiguredModuleAction::getCode, actionCode)
                .eq(ConfiguredModuleAction::getStatus, "ACTIVE")).stream().findFirst().orElse(null);
        Long actionId = action == null ? null : action.getId();
        if (actionId != null && activeBindings(context.systemId(), context.tenantId(), moduleId, actionId, actionCode).isEmpty()
                && activeBindings(context.systemId(), mainTenantId, moduleId, actionId, actionCode).isEmpty()) {
            actionId = null;
        }
        return resolveInternal(context, new FlowBindingModels.ResolveBindingRequest(
                moduleId, actionId, actionCode, variables), traceId, true);
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
                : conditions.evaluateConditions(policy.condition(), objectMapper.valueToTree(input.variables() == null ? Map.of() : input.variables()), new ArrayList<>()))) {
            if (optional) return null;
            throw invalid("FLOW_BINDING_CONDITION_NOT_MATCHED", "业务数据未命中 Flow 绑定条件");
        }
        if ("DIRECT".equals(policy.executionMode())) return new FlowBindingModels.ResolutionView(
                null, binding.getId(), Objects.equals(binding.getOwnerTenantId(), mainTenantId) ? "DEFAULT" : "TENANT_OVERRIDE",
                binding.getOwnerTenantId(), context.tenantId(), null, null, null, null, Map.of(),
                "DIRECT", policy.mutuallyExclusive(), "DIRECT_CONFIGURATION", LocalDateTime.now());
        FlowDefinition flow = definitionService.selectById(binding.getFlowId());
        if (flow == null || !Objects.equals(flow.getSystemId(), context.systemId())
                || !Objects.equals(flow.getOwnerTenantId(), binding.getOwnerTenantId())) {
            throw invalid("FLOW_BINDING_VERSION_INCOMPATIBLE", "Flow 绑定与生效租户不兼容");
        }
        FlowVersion version = requireVersion(policy.flowVersionId() == null
                ? publication(flow.getId()).getCurrentVersionId() : policy.flowVersionId(), flow.getId());
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
                policy.executionMode(), policy.mutuallyExclusive(), reason, resolution.getResolvedAt());
    }

    @Transactional(readOnly = true)
    public List<FlowBindingModels.ConditionField> conditionFields(AuthenticatedContext context, Long moduleId, String event) {
        requireSystem(context);
        requireAction(context, "VIEW");
        return conditionFieldsInternal(context, requireModule(context, moduleId), normalizeEvent(event));
    }

    private List<FlowBindingModels.ConditionField> conditionFieldsInternal(AuthenticatedContext context, ConfiguredModule module, String event) {
        JsonNode snapshot = moduleConfigurations.published(context, module.getCode()).configuration();
        List<JsonNode> fields = new ArrayList<>();
        snapshot.path("fields").forEach(field -> {
            if ("ACTIVE".equals(field.path("status").asText()) && Set.of("TEXT", "MULTILINE_TEXT", "NUMBER", "MONEY", "BOOLEAN",
                    "DATE", "DATETIME", "PHONE", "EMAIL", "URL", "ID_CARD", "SINGLE_SELECT", "MULTI_SELECT", "STATUS").contains(field.path("fieldType").asText())) fields.add(field);
        });
        String action = Set.of("CREATE", "UPDATE", "DELETE").contains(event) ? event : "DETAIL";
        var access = fieldPolicies.resolveIntersection(context, module.getCode(), action, List.of("PAGE", "FLOW"),
                fields.stream().map(field -> field.path("code").asText()).toList());
        return fields.stream().filter(field -> {
            var decision = access.get(field.path("code").asText());
            return decision != null && decision.readable() && decision.maskStrategy() == null;
        }).map(field -> {
            JsonNode config = readJson(field.path("configJson").asText("{}"));
            var safeConfig = objectMapper.createObjectNode();
            for (String key : List.of("options", "placeholder", "maxLength")) if (config.has(key)) safeConfig.set(key, config.get(key));
            return new FlowBindingModels.ConditionField(field.path("code").asText(), field.path("name").asText(),
                    field.path("fieldType").asText(), safeConfig.toString());
        }).toList();
    }

    private JsonNode readJson(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException exception) { throw invalid("FLOW_BINDING_CONDITION_INVALID", "条件或字段配置无法读取"); }
    }

    private void validateStructuredCondition(AuthenticatedContext context, ConfiguredModule module, String event, JsonNode condition) {
        var fields = conditionFieldsInternal(context, module, event);
        var issues = conditions.inspectConditions(condition, fields.stream().map(FlowBindingModels.ConditionField::code).collect(java.util.stream.Collectors.toSet()));
        if (!issues.isEmpty()) throw invalid("FLOW_BINDING_CONDITION_INVALID", String.join("；", issues));
        validateConditionValues(condition, fields);
    }

    private void validateConditionValues(JsonNode node, List<FlowBindingModels.ConditionField> fields) {
        if (node.has("conditions")) { for (JsonNode child : node.path("conditions")) validateConditionValues(child, fields); return; }
        String op = node.path("operator").asText();
        if (Set.of("EMPTY", "NOT_EMPTY").contains(op)) return;
        var field = fields.stream().filter(item -> item.code().equals(node.path("field").asText())).findFirst().orElseThrow();
        JsonNode value = node.path("value");
        if (Set.of("GT", "GTE", "LT", "LTE").contains(op) && !Set.of("NUMBER", "MONEY", "DATE", "DATETIME").contains(field.fieldType()))
            throw invalid("FLOW_BINDING_CONDITION_INVALID", field.name() + "不支持大小比较");
        if ("CONTAINS".equals(op) && !Set.of("TEXT", "MULTILINE_TEXT", "MULTI_SELECT", "PHONE", "EMAIL", "URL", "ID_CARD").contains(field.fieldType()))
            throw invalid("FLOW_BINDING_CONDITION_INVALID", field.name() + "不支持包含比较");
        if ("IN".equals(op)) {
            if ("MULTI_SELECT".equals(field.fieldType())) throw invalid("FLOW_BINDING_CONDITION_INVALID", "多选字段请使用包含条件");
            for (JsonNode item : value) if (!validValue(field.fieldType(), item)) throw invalid("FLOW_BINDING_CONDITION_INVALID", field.name() + "的比较值类型不正确");
        } else if (!("CONTAINS".equals(op) && "MULTI_SELECT".equals(field.fieldType()) ? value.isTextual() : validValue(field.fieldType(), value)))
            throw invalid("FLOW_BINDING_CONDITION_INVALID", field.name() + "的比较值类型不正确");
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
        boolean matched = conditions.evaluateConditions(input.condition(), objectMapper.valueToTree(input.variables() == null ? Map.of() : input.variables()), results);
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
                || !Objects.equals(action.getModuleId(), module.getId()) || !"ACTIVE".equals(action.getStatus())) {
            throw invalid("FLOW_BINDING_ACTION_INVALID", "模块动作不存在、未启用或与模块不匹配");
        }
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
        FlowVersion version = policy.flowVersionId() != null ? versionService.selectById(policy.flowVersionId())
                : publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        return new FlowBindingModels.BindingView(
                binding.getId(), binding.getSystemId(), binding.getOwnerTenantId(),
                Objects.equals(binding.getOwnerTenantId(), mainTenantId) ? "DEFAULT" : "TENANT_OVERRIDE",
                binding.getModuleId(), binding.getActionId(), binding.getTriggerEvent(), binding.getFlowId(),
                version == null ? null : version.getId(), version == null ? null : version.getVersionNumber(),
                policy.executionMode(), binding.getPriorityOrder(), policy.conditionExpression(),
                policy.condition(),
                policy.mutuallyExclusive(), binding.getStatus(), binding.getVersion(), binding.getUpdatedAt());
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

    private String toPolicyJson(String mode, String expression, JsonNode condition, Boolean mutuallyExclusive, Long flowVersionId) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("executionMode", mode);
        value.put("conditionExpression", blankToNull(expression));
        value.put("condition", condition);
        value.put("flowVersionId", flowVersionId);
        value.put("mutuallyExclusive", !Boolean.FALSE.equals(mutuallyExclusive));
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize Flow binding policy", exception);
        }
    }

    private BindingPolicy policy(FlowTriggerBinding binding) {
        if (binding.getConditionExpression() == null || binding.getConditionExpression().isBlank()) {
            return new BindingPolicy("AFTER_EXECUTION", null, null, true, null);
        }
        try {
            Map<String, Object> value = objectMapper.readValue(binding.getConditionExpression(), new TypeReference<>() { });
            return new BindingPolicy(
                    String.valueOf(value.getOrDefault("executionMode", "AFTER_EXECUTION")),
                    blankToNull(value.get("conditionExpression") == null ? null
                            : String.valueOf(value.get("conditionExpression"))),
                    value.get("condition") == null ? null : objectMapper.valueToTree(value.get("condition")),
                    !Boolean.FALSE.equals(value.get("mutuallyExclusive")),
                    value.get("flowVersionId") == null ? null : ((Number) value.get("flowVersionId")).longValue());
        } catch (JsonProcessingException ignored) {
            return new BindingPolicy("AFTER_EXECUTION", binding.getConditionExpression(), null, true, null);
        }
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

    private record BindingPolicy(String executionMode, String conditionExpression, JsonNode condition, boolean mutuallyExclusive, Long flowVersionId) {
    }
}
