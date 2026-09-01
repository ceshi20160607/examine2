package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            ObjectMapper objectMapper) {
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
        FlowDefinition flow = requirePublishedFlow(context, input.flowId());
        FlowPublication publication = publication(flow.getId());
        FlowVersion version = requireVersion(publication.getCurrentVersionId(), flow.getId());
        int priority = input.priorityOrder() == null ? 0 : input.priorityOrder();

        List<FlowTriggerBinding> scope = scopeBindingsForUpdate(
                context.systemId(), context.tenantId(), module.getId(), action == null ? null : action.getId(), event);
        List<FlowTriggerBinding> active = scope.stream().filter(item -> "ACTIVE".equals(item.getStatus())).toList();
        boolean sameActive = active.size() == 1 && Objects.equals(active.getFirst().getFlowId(), flow.getId())
                && active.getFirst().getPriorityOrder() == priority
                && Objects.equals(policy(active.getFirst()).executionMode(), executionMode)
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
        binding.setFlowId(flow.getId());
        binding.setPriorityOrder(priority);
        binding.setConditionExpression(toPolicyJson(executionMode, input.conditionExpression(), input.mutuallyExclusive()));
        binding.setStatus("ACTIVE");
        bindingService.insert(binding);
        LinkedHashMap<String, Object> detail = new LinkedHashMap<>();
        detail.put("moduleId", module.getId());
        detail.put("actionId", action == null ? null : action.getId());
        detail.put("triggerEvent", event);
        detail.put("flowId", flow.getId());
        detail.put("flowVersionId", version.getId());
        detail.put("ownerTenantId", context.tenantId());
        audit(context, traceId, "FLOW_BINDING_PUBLISHED", "FLOW_TRIGGER_BINDING", binding.getId(), detail);
        return view(binding, mainTenant(context.systemId()).getId());
    }

    @Transactional
    public FlowBindingModels.ResolutionView resolve(
            AuthenticatedContext context, FlowBindingModels.ResolveBindingRequest input, String traceId) {
        requireSystem(context);
        requireAction(context, "TRIGGER");
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
            throw new DomainException("FLOW_BINDING_NOT_FOUND", "当前业务触发点没有可用 Flow 绑定", HttpStatus.NOT_FOUND);
        }
        BindingPolicy policy = policy(binding);
        if (!matches(policy.conditionExpression(), input.variables())) {
            throw invalid("FLOW_BINDING_CONDITION_NOT_MATCHED", "业务数据未命中 Flow 绑定条件");
        }
        FlowDefinition flow = definitionService.selectById(binding.getFlowId());
        if (flow == null || !Objects.equals(flow.getSystemId(), context.systemId())
                || !Objects.equals(flow.getOwnerTenantId(), binding.getOwnerTenantId())) {
            throw invalid("FLOW_BINDING_VERSION_INCOMPATIBLE", "Flow 绑定与生效租户不兼容");
        }
        FlowPublication publication = publication(flow.getId());
        FlowVersion version = requireVersion(publication.getCurrentVersionId(), flow.getId());
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
        FlowPublication publication = publicationService.selectList(Wrappers.<FlowPublication>lambdaQuery()
                        .eq(FlowPublication::getFlowId, binding.getFlowId())).stream().findFirst().orElse(null);
        FlowVersion version = publication == null ? null : versionService.selectById(publication.getCurrentVersionId());
        BindingPolicy policy = policy(binding);
        return new FlowBindingModels.BindingView(
                binding.getId(), binding.getSystemId(), binding.getOwnerTenantId(),
                Objects.equals(binding.getOwnerTenantId(), mainTenantId) ? "DEFAULT" : "TENANT_OVERRIDE",
                binding.getModuleId(), binding.getActionId(), binding.getTriggerEvent(), binding.getFlowId(),
                version == null ? null : version.getId(), version == null ? null : version.getVersionNumber(),
                policy.executionMode(), binding.getPriorityOrder(), policy.conditionExpression(),
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

    private String toPolicyJson(String mode, String expression, Boolean mutuallyExclusive) {
        LinkedHashMap<String, Object> value = new LinkedHashMap<>();
        value.put("executionMode", mode);
        value.put("conditionExpression", blankToNull(expression));
        value.put("mutuallyExclusive", !Boolean.FALSE.equals(mutuallyExclusive));
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize Flow binding policy", exception);
        }
    }

    private BindingPolicy policy(FlowTriggerBinding binding) {
        if (binding.getConditionExpression() == null || binding.getConditionExpression().isBlank()) {
            return new BindingPolicy("AFTER_EXECUTION", null, true);
        }
        try {
            Map<String, Object> value = objectMapper.readValue(binding.getConditionExpression(), new TypeReference<>() { });
            return new BindingPolicy(
                    String.valueOf(value.getOrDefault("executionMode", "AFTER_EXECUTION")),
                    blankToNull(value.get("conditionExpression") == null ? null
                            : String.valueOf(value.get("conditionExpression"))),
                    !Boolean.FALSE.equals(value.get("mutuallyExclusive")));
        } catch (JsonProcessingException ignored) {
            return new BindingPolicy("AFTER_EXECUTION", binding.getConditionExpression(), true);
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

    private record BindingPolicy(String executionMode, String conditionExpression, boolean mutuallyExclusive) {
    }
}
