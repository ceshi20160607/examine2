package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.sys.action.print.base.entity.UnPrint;
import com.unique.unexamine.sys.action.print.base.service.IUnPrintService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintField;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintRequest;
import com.unique.unexamine.sys.module.manage.dto.ModulePrintDto.PrintView;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 模块打印动作执行服务；模板版本和字段读取均受当前打印菜单权限约束。 */
@Service
public class ModulePrintService {
    private final ModulePermissionService permissions;
    private final ModuleConfigService config;
    private final ModuleRecordService records;
    private final IUnPrintService templates;
    private final ObjectMapper json;

    /** 注入统一菜单授权、模块字段和打印模板基础服务。 */
    public ModulePrintService(ModulePermissionService permissions, ModuleConfigService config,
            ModuleRecordService records, IUnPrintService templates, ObjectMapper json) {
        this.permissions = permissions;
        this.config = config;
        this.records = records;
        this.templates = templates;
        this.json = json;
    }

    /** 选择最新已发布模板并返回已经脱敏的结构化打印数据。 */
    public PrintView print(String token, Long systemId, Long tenantId, Long moduleId, PrintRequest request) {
        if (request == null || request.recordId() == null || request.recordId() < 1
                || request.templateCode() == null || !request.templateCode().matches("[A-Za-z][A-Za-z0-9_]{0,79}"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印记录主键或模板编码无效");
        var access = permissions.require(token, systemId, tenantId, moduleId, "PRINT");
        UnPrint template = findTemplate(access, moduleId, request.templateCode());
        Map<String, Object> body = object(template.getBodyConfigJson(), "打印模板正文配置损坏");
        List<String> fieldKeys = fieldKeys(body);
        List<UnModuleField> current = config.currentFields(moduleId);
        Map<String, UnModuleField> fields = new LinkedHashMap<>();
        Long rootTenant = access.definition().module().getTenantId();
        for (UnModuleField field : current)
            if (rootTenant.equals(field.getTenantId())) fields.put(field.getFieldKey(), field);
        for (UnModuleField field : current)
            if (access.context().tenantId().equals(field.getTenantId())) fields.put(field.getFieldKey(), field);
        if (fieldKeys.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "打印模板没有配置字段");
        for (String key : fieldKeys) {
            if (!fields.containsKey(key)) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    "打印模板字段已失效：" + key);
            if (!access.policy().readable(key)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,
                    "没有打印字段读取权限：" + key);
        }
        var record = records.valuesByAuthorizedAction(token, access, request.recordId(), Set.copyOf(fieldKeys));
        List<PrintField> output = new ArrayList<>();
        for (String key : fieldKeys) {
            UnModuleField field = fields.get(key);
            output.add(new PrintField(key, field.getFieldName(), record.values().get(key)));
        }
        return new PrintView(template.getId(), template.getTemplateCode(), template.getName(), template.getVersionNo(),
                record.id(), record.name(), object(template.getPaperConfigJson(), "打印模板纸张配置损坏"), output,body);
    }

    /** 当前租户模板优先，停用后不回退旧版本或主租户模板。 */
    private UnPrint findTemplate(ModulePermissionService.Access access, Long moduleId, String code) {
        Long currentTenant = access.context().tenantId();
        UnPrint template = latestEffective(access.context().systemId(), currentTenant, moduleId, code);
        Long rootTenant = access.definition().module().getTenantId();
        if (template == null && !currentTenant.equals(rootTenant))
            template = latestEffective(access.context().systemId(), rootTenant, moduleId, code);
        if (template == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "没有可用的打印模板");
        if (!"PUBLISHED".equals(template.getStatus()))
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "打印模板已停用");
        return template;
    }

    private UnPrint latestEffective(Long systemId, Long tenantId, Long moduleId, String code) {
        return templates.getOne(new LambdaQueryWrapper<UnPrint>()
                .eq(UnPrint::getSystemId, systemId).eq(UnPrint::getTenantId, tenantId)
                .eq(UnPrint::getModuleId, moduleId).eq(UnPrint::getTemplateCode, code)
                .in(UnPrint::getStatus, "PUBLISHED", "DISABLED")
                .orderByDesc(UnPrint::getVersionNo).last("LIMIT 1"), false);
    }

    /** 模板正文只读取字段键列表，复杂布局仍由打印适配器按纸张配置处理。 */
    private List<String> fieldKeys(Map<String, Object> body) {
        Object raw = body.get("fieldKeys");
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String key = item instanceof String text ? text : null;
            if (key == null || !key.matches("[a-z][a-zA-Z0-9_]{0,79}") || !result.add(key))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"打印模板字段键无效或重复");
        }
        return List.copyOf(result);
    }

    private Map<String, Object> object(String source, String message) {
        if (source == null || source.isBlank()) return Map.of();
        try {
            Map<String, Object> value = json.readValue(source, new TypeReference<LinkedHashMap<String, Object>>() {});
            return value == null ? Map.of() : value;
        } catch (JsonProcessingException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, message, ex);
        }
    }
}
