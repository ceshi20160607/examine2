package com.unique.unexamine.sys.flow.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.entity.UnSysSystem;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.sys.base.service.IUnSysMenuService;
import com.unique.unexamine.sys.base.service.IUnSysSystemService;
import com.unique.unexamine.sys.flow.base.entity.UnFlow;
import com.unique.unexamine.sys.flow.base.entity.UnFlowBinding;
import com.unique.unexamine.sys.flow.base.entity.UnFlowVersion;
import com.unique.unexamine.sys.flow.base.service.IUnFlowBindingService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowService;
import com.unique.unexamine.sys.flow.base.service.IUnFlowVersionService;
import com.unique.unexamine.sys.flow.manage.dto.FlowBindingDto.BindingView;
import com.unique.unexamine.sys.flow.manage.dto.FlowBindingDto.SaveBindingRequest;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.module.ExamineStarter;
import com.unique.unexamine.core.module.PublishedProcessSnapshot;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldValidationService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleMenu;
import com.unique.unexamine.sys.module.base.entity.UnModule;
import com.unique.unexamine.sys.module.base.service.IUnModuleMenuService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import com.unique.unexamine.sys.module.manage.service.ModuleFieldService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Flow 触发绑定管理；绑定保存即生效，历史实例继续使用自己的版本快照。 */
@Service
public class FlowBindingService {
    private final SystemManageService systems;
    private final IUnFlowBindingService bindings;
    private final IUnFlowService flows;
    private final IUnFlowVersionService versions;
    private final ModuleConfigService moduleConfig;
    private final IUnSysMenuService menus;
    private final IUnModuleMenuService moduleMenus;
    private final IUnSysLogOptService logs;
    private final IUnSysSystemService systemRows;
    private final ObjectMapper json;
    private final ModuleFieldService fieldRules;
    private final FieldValidationService validation;
    private final ExamineStarter examineStarter;

    public FlowBindingService(SystemManageService systems, IUnFlowBindingService bindings,
                              IUnFlowService flows, IUnFlowVersionService versions,
                              IUnSysMenuService menus,
                              IUnModuleMenuService moduleMenus, ModuleConfigService moduleConfig,
                              IUnSysLogOptService logs,
                              ObjectMapper json, IUnSysSystemService systemRows,
                              ModuleFieldService fieldRules, FieldValidationService validation,
                              ExamineStarter examineStarter) {
        this.systems = systems;
        this.bindings = bindings;
        this.flows = flows;
        this.versions = versions;
        this.moduleConfig = moduleConfig;
        this.menus = menus;
        this.moduleMenus = moduleMenus;
        this.logs = logs;
        this.json = json;
        this.systemRows = systemRows;
        this.fieldRules = fieldRules;
        this.validation = validation;
        this.examineStarter = examineStarter;
    }

    /** 查询当前租户的 Flow 触发绑定。 */
    public List<BindingView> list(String token, Long systemId, Long tenantId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        UnSysSystem system = systemRows.getById(context.systemId());
        Long rootTenantId = system == null ? null : system.getRootTenantId();
        List<UnFlowBinding> candidates = bindings.list(new LambdaQueryWrapper<UnFlowBinding>()
                        .eq(UnFlowBinding::getSystemId, context.systemId())
                        .and(w -> {
                            w.isNull(UnFlowBinding::getTenantId).or().eq(UnFlowBinding::getTenantId, context.tenantId());
                            if (rootTenantId != null) w.or().eq(UnFlowBinding::getTenantId, rootTenantId);
                        })
                        .in(UnFlowBinding::getStatus, List.of("ACTIVE", "PUBLISHED"))
                        .orderByAsc(UnFlowBinding::getPriorityNo).orderByAsc(UnFlowBinding::getId));
        return effectiveBindings(candidates, context.tenantId(), rootTenantId)
                .stream().map(this::view).toList();
    }

    /** 保存绑定并固定当前 Flow 发布版本。 */
    @Transactional(rollbackFor = Exception.class)
    public BindingView save(String token, Long systemId, Long tenantId, SaveBindingRequest request) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        if (request == null || request.flowId() == null || request.flowId() < 1
                || request.moduleId() != null && request.moduleId() < 1
                || request.actionMenuId() != null && request.actionMenuId() < 1
                || request.eventCode() == null || request.eventCode().isBlank() || request.eventCode().length() > 40
                || request.executionMode() != null && request.executionMode().length() > 30
                || request.priorityNo() != null && request.priorityNo() < 0)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 绑定资料无效");
        // 同一系统内相同事件绑定需要先停用旧绑定再写入新绑定；锁住系统行避免并发保存留下多个有效绑定。
        if (systemRows.getOne(new LambdaQueryWrapper<UnSysSystem>()
                .eq(UnSysSystem::getId, context.systemId()).last("FOR UPDATE"), false) == null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "业务系统不存在");
        UnFlow flow = ownedFlow(context.systemId(), context.tenantId(), request.flowId());
        UnFlowVersion version = versions.getOne(new LambdaQueryWrapper<UnFlowVersion>()
                .eq(UnFlowVersion::getFlowId, flow.getId())
                .eq(UnFlowVersion::getVersionNo, flow.getLatestVersion()), false);
        if (version == null) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 尚未有可绑定的发布版本");
        validateModuleAndMenu(context.systemId(), context.tenantId(), request.moduleId(), request.actionMenuId());
        // 绑定时已知业务模块，审批定义若限定模块必须与绑定模块一致。
        for (JsonNode node : PublishedProcessSnapshot.nodes(json, version.getSnapshotJson())) {
            Long examineId = PublishedProcessSnapshot.examineId(node);
            if (examineId != null) {
                if (request.moduleId() == null)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "引用审批的 Flow 必须绑定业务模块");
                examineStarter.validateReference(context.systemId(), context.tenantId(), request.moduleId(), examineId);
            }
        }
        validateEvent(request.eventCode());
        UnFlowBinding binding = request.id() == null ? null : bindings.getById(request.id());
        BindingView before = null;
        if (binding != null) {
            ownedBinding(context.systemId(), context.tenantId(), binding);
            before = view(binding);
        }
        if (binding == null) {
            binding = new UnFlowBinding();
            binding.setScopeType("SYSTEM");
            binding.setSystemId(context.systemId());
            binding.setTenantId(context.tenantId());
            binding.setPublishedVersion(0);
            binding.setCreateTime(LocalDateTime.now());
        } else {
            ownedBinding(context.systemId(), context.tenantId(), binding);
        }
        binding.setModuleId(request.moduleId());
        binding.setActionMenuId(request.actionMenuId());
        binding.setEventCode(request.eventCode().trim().toUpperCase(Locale.ROOT));
        binding.setFlowId(flow.getId());
        binding.setFlowVersionId(version.getId());
        binding.setExecutionMode(blankOrDefault(request.executionMode(), "AFTER_SAVE"));
        binding.setPriorityNo(request.priorityNo() == null ? 0 : request.priorityNo());
        binding.setExclusiveFlag(request.exclusiveFlag() == null || request.exclusiveFlag());
        String conditionJson = writeJson(request.condition());
        try {
            TriggerConditionMatcher.validate(json, conditionJson);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        if (request.moduleId() != null) {
            Set<String> fieldKeys = moduleConfig.currentFields(request.moduleId()).stream()
                    .map(item -> item.getFieldKey()).collect(Collectors.toSet());
            try {
                TriggerConditionMatcher.validateFieldKeys(json, conditionJson, fieldKeys);
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
            }
            validateConditionValues(request.moduleId(), conditionJson);
        }
        binding.setConditionJson(conditionJson);
        binding.setStatus("ACTIVE");
        binding.setPublishedVersion((binding.getPublishedVersion() == null ? 0 : binding.getPublishedVersion()) + 1);
        binding.setUpdateTime(LocalDateTime.now());
        // 同一租户同一模块动作事件只保留一个有效绑定；旧绑定停用后，已启动实例仍按自身快照运行。
        LambdaUpdateWrapper<UnFlowBinding> oldBindings = new LambdaUpdateWrapper<UnFlowBinding>()
                .eq(UnFlowBinding::getSystemId, context.systemId())
                .eq(UnFlowBinding::getTenantId, context.tenantId())
                .eq(UnFlowBinding::getEventCode, request.eventCode().trim().toUpperCase(Locale.ROOT));
        if (request.moduleId() == null) oldBindings.isNull(UnFlowBinding::getModuleId);
        else oldBindings.eq(UnFlowBinding::getModuleId, request.moduleId());
        if (request.actionMenuId() == null) oldBindings.isNull(UnFlowBinding::getActionMenuId);
        else oldBindings.eq(UnFlowBinding::getActionMenuId, request.actionMenuId());
        oldBindings.ne(binding.getId() != null, UnFlowBinding::getId, binding.getId())
                .in(UnFlowBinding::getStatus, List.of("ACTIVE", "PUBLISHED"))
                .set(UnFlowBinding::getStatus, "DISABLED")
                .set(UnFlowBinding::getUpdateTime, LocalDateTime.now());
        bindings.update(oldBindings);
        bindings.addOrUpdate(binding);
        BindingView after = view(binding);
        audit(context, "FLOW_BINDING_SAVE", binding.getId(), before, after);
        return after;
    }

    /** Flow 绑定保存时校验条件值的类型，避免运行线路因配置值形态错误而静默不命中。 */
    private void validateConditionValues(Long moduleId, String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank() || moduleId == null) return;
        Map<String, FieldDefinition> definitions = new LinkedHashMap<>();
        moduleConfig.currentFields(moduleId).forEach(field -> definitions.put(field.getFieldKey(), fieldRules.configured(field)));
        try {
            TriggerConditionMatcher.validateFieldValues(json, conditionJson, definitions, validation);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
    }

    /** 按触发键让当前租户覆盖默认绑定；没有覆盖时保留租户为空的默认绑定。 */
    private List<UnFlowBinding> effectiveBindings(List<UnFlowBinding> candidates, Long tenantId, Long rootTenantId) {
        Map<String, List<UnFlowBinding>> grouped = candidates.stream()
                .collect(Collectors.groupingBy(this::bindingKey, LinkedHashMap::new, Collectors.toList()));
        List<UnFlowBinding> result = new java.util.ArrayList<>();
        for (List<UnFlowBinding> group : grouped.values()) {
            Long selectedTenant = group.stream().anyMatch(item -> java.util.Objects.equals(item.getTenantId(), tenantId))
                    ? tenantId
                    : group.stream().anyMatch(item -> java.util.Objects.equals(item.getTenantId(), rootTenantId))
                    ? rootTenantId : null;
            group.stream().filter(item -> java.util.Objects.equals(item.getTenantId(), selectedTenant))
                    .forEach(result::add);
        }
        return result.stream().sorted(java.util.Comparator.comparing(UnFlowBinding::getPriorityNo,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                .thenComparing(UnFlowBinding::getId, java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))).toList();
    }

    private String bindingKey(UnFlowBinding binding) {
        return String.valueOf(binding.getModuleId()) + '|'
                + binding.getActionMenuId() + '|' + binding.getEventCode();
    }

    /** 停用绑定但不改写已运行实例。 */
    @Transactional(rollbackFor = Exception.class)
    public BindingView disable(String token, Long systemId, Long tenantId, Long bindingId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        UnFlowBinding binding = bindings.getById(bindingId);
        ownedBinding(context.systemId(), context.tenantId(), binding);
        BindingView before = view(binding);
        binding.setStatus("DISABLED");
        binding.setUpdateTime(LocalDateTime.now());
        bindings.addOrUpdate(binding);
        BindingView after = view(binding);
        audit(context, "FLOW_BINDING_DISABLE", bindingId, before, after);
        return after;
    }

    private UnFlow ownedFlow(Long systemId, Long tenantId, Long flowId) {
        UnFlow flow = flows.getById(flowId);
        if (flow == null || !systemId.equals(flow.getSystemId()) || !tenantId.equals(flow.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 定义不存在");
        if (!"ACTIVE".equals(flow.getStatus())) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "Flow 定义未启用");
        return flow;
    }

    private UnFlowBinding ownedBinding(Long systemId, Long tenantId, UnFlowBinding binding) {
        if (binding == null || !systemId.equals(binding.getSystemId()) || !tenantId.equals(binding.getTenantId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "Flow 绑定不存在");
        return binding;
    }

    private void validateModuleAndMenu(Long systemId, Long tenantId, Long moduleId, Long actionMenuId) {
        if (moduleId == null && actionMenuId != null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "动作菜单必须属于指定模块");
        UnModule module = null;
        if (moduleId != null) {
            // 模块域允许当前租户使用根租户继承模块，这里不能重复收窄为 tenant_id 等于当前租户。
            module = moduleConfig.activeModule(systemId, moduleId, tenantId);
        }
        if (actionMenuId != null) {
            UnSysMenu menu = menus.getById(actionMenuId);
            Set<Long> allowedTenants = module == null ? Set.of(tenantId)
                    : Set.of(tenantId, module.getTenantId());
            if (menu == null || !systemId.equals(menu.getSystemId()) || !allowedTenants.contains(menu.getTenantId())
                    || !"ACTIVE".equals(menu.getStatus()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "动作菜单不存在或未启用");
            long count = moduleMenus.count(new LambdaQueryWrapper<UnModuleMenu>()
                    .eq(UnModuleMenu::getModuleId, moduleId).eq(UnModuleMenu::getMenuId, actionMenuId)
                    .in(UnModuleMenu::getTenantId, allowedTenants)
                    .eq(UnModuleMenu::getStatus, "ACTIVE"));
            if (count == 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "动作菜单未关联到业务模块");
        }
    }

    private void validateEvent(String eventCode) {
        if (eventCode == null || !eventCode.trim().matches("[A-Za-z][A-Za-z0-9_]{1,39}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 事件编码只能使用字母、数字和下划线");
    }

    private String blankOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim().toUpperCase(Locale.ROOT);
    }

    private String writeJson(JsonNode value) {
        if (value == null || value.isNull()) return null;
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 条件无法保存", ex); }
    }

    private BindingView view(UnFlowBinding binding) {
        JsonNode condition = null;
        try { if (binding.getConditionJson() != null) condition = json.readTree(binding.getConditionJson()); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "Flow 绑定条件无效", ex); }
        return new BindingView(binding.getId(), binding.getScopeType(), binding.getSystemId(), binding.getTenantId(),
                binding.getModuleId(), binding.getActionMenuId(), binding.getEventCode(), binding.getFlowId(),
                binding.getFlowVersionId(), binding.getExecutionMode(), binding.getPriorityNo(),
                binding.getExclusiveFlag(), condition, binding.getStatus(), binding.getPublishedVersion(),
                binding.getUpdateTime());
    }

    private void audit(com.unique.unexamine.sys.manage.dto.SystemManageDto.SystemContext context,
                       String actionCode, Long bindingId, BindingView before, BindingView after) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("before", before);
        detail.put("after", after);
        UnSysLogOpt log = new UnSysLogOpt();
        log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(context.accountId()); log.setActionCode(actionCode);
        log.setObjectType("FLOW_BINDING"); log.setObjectId(bindingId);
        try { log.setDetailJson(json.writeValueAsString(detail)); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR, "Flow 绑定审计内容无法保存", ex); }
        logs.addOrUpdate(log);
    }
}
