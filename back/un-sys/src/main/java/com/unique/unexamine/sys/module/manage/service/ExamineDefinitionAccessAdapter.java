package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.module.ExamineDefinitionAccess;
import com.unique.unexamine.core.module.TriggerConditionMatcher;
import com.unique.unexamine.core.field.FieldValidationService;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.base.entity.UnSysMenu;
import com.unique.unexamine.sys.base.service.IUnSysMenuService;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.UnModuleMenu;
import com.unique.unexamine.sys.module.base.service.IUnModuleMenuService;
import com.unique.unexamine.sys.module.manage.service.ModuleConfigService;
import java.util.Set;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 将系统管理员上下文提供给 examine 定义管理服务。 */
@Component
public class ExamineDefinitionAccessAdapter implements ExamineDefinitionAccess {
    private final SystemManageService systems;
    private final ModuleConfigService modules;
    private final IUnSysMenuService menus;
    private final IUnModuleMenuService moduleMenus;
    private final ModuleFieldService fieldRules;
    private final FieldValidationService validation;
    private final ObjectMapper json;

    public ExamineDefinitionAccessAdapter(SystemManageService systems, ModuleConfigService modules,
                                          IUnSysMenuService menus, IUnModuleMenuService moduleMenus,
                                          ModuleFieldService fieldRules, FieldValidationService validation,
                                          ObjectMapper json) {
        this.systems = systems;
        this.modules = modules;
        this.menus = menus;
        this.moduleMenus = moduleMenus;
        this.fieldRules = fieldRules;
        this.validation = validation;
        this.json = json;
    }

    @Override
    public Context requireAdmin(String token, Long systemId, Long tenantId) {
        var context = systems.requireAdminContext(token, systemId, tenantId);
        return new Context(context.systemId(), context.tenantId(), context.accountId(),
                context.owner(), context.tenantAdmin());
    }

    @Override
    public void validateModule(Context context, Long moduleId) {
        if (moduleId != null) modules.activeModule(context.systemId(), moduleId, context.tenantId());
    }

    @Override
    public void validateFieldKeys(Context context, Long moduleId, Set<String> fieldKeys) {
        ExamineDefinitionAccess.super.validateFieldKeys(context, moduleId, fieldKeys);
        if (moduleId == null || fieldKeys == null || fieldKeys.isEmpty()) return;
        Set<String> available = new java.util.HashSet<>();
        modules.currentFields(moduleId).forEach(field -> available.add(field.getFieldKey()));
        fieldKeys.stream().filter(key -> !available.contains(key)).findFirst()
                .ifPresent(key -> { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "审批节点引用了不存在的模块字段: " + key); });
    }

    @Override
    public void validateActionMenu(Context context, Long moduleId, Long actionMenuId) {
        ExamineDefinitionAccess.super.validateActionMenu(context, moduleId, actionMenuId);
        if (actionMenuId == null) return;
        var module = modules.activeModule(context.systemId(), moduleId, context.tenantId());
        UnSysMenu menu = menus.queryById(actionMenuId);
        if (menu == null || !context.systemId().equals(menu.getSystemId())
                || !List.of(context.tenantId(), module.getTenantId()).contains(menu.getTenantId())
                || !"ACTIVE".equals(menu.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "审批动作菜单不存在或已停用");
        long count = moduleMenus.count(new LambdaQueryWrapper<UnModuleMenu>()
                .eq(UnModuleMenu::getModuleId, moduleId)
                .eq(UnModuleMenu::getMenuId, actionMenuId)
                .in(UnModuleMenu::getTenantId, List.of(context.tenantId(), module.getTenantId()))
                .eq(UnModuleMenu::getStatus, "ACTIVE"));
        if (count == 0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "审批动作菜单未关联到当前模块");
    }

    /** 审批绑定和发布时复用模块字段类型校验，成员与关联记录范围仍由运行保存链检查。 */
    @Override
    public void validateConditionValues(Context context, Long moduleId, String conditionJson) {
        if (moduleId == null || conditionJson == null || conditionJson.isBlank()) return;
        Map<String, com.unique.unexamine.core.field.FieldDefinition> definitions = new java.util.LinkedHashMap<>();
        modules.currentFields(moduleId).forEach(field -> definitions.put(field.getFieldKey(), fieldRules.configured(field)));
        try {
            TriggerConditionMatcher.validateFieldValues(json, conditionJson, definitions,
                    validation);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
    }
}
