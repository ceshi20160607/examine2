package com.unique.unexamine.module.manage;

import com.unique.unexamine.sys.base.entity.UnSysTenant;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.mapper.UnSysTenantMapper;
import com.unique.unexamine.module.base.entity.UnModule;
import com.unique.unexamine.module.base.entity.UnModuleField;
import com.unique.unexamine.module.base.entity.UnModuleTenant;
import com.unique.unexamine.module.base.entity.UnModuleTenantField;
import com.unique.unexamine.module.base.mapper.UnModuleFieldMapper;
import com.unique.unexamine.module.base.mapper.UnModuleMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTenantFieldMapper;
import com.unique.unexamine.module.base.mapper.UnModuleTenantMapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 模块接口共用的租户归属和有效字段检查。 */
@Component
public class ModuleSupport {
    private static final Set<String> FIELD_TYPES = Set.of("text", "textarea", "number", "integer", "date", "datetime", "select", "multiselect", "boolean");

    private final SystemSessionAccess sessions;
    private final UnSysTenantMapper tenants;
    private final UnModuleMapper modules;
    private final UnModuleTenantMapper tenantModules;
    private final UnModuleFieldMapper fields;
    private final UnModuleTenantFieldMapper tenantFields;

    public ModuleSupport(SystemSessionAccess sessions, UnSysTenantMapper tenants, UnModuleMapper modules,
                         UnModuleTenantMapper tenantModules, UnModuleFieldMapper fields,
                         UnModuleTenantFieldMapper tenantFields) {
        this.sessions = sessions;
        this.tenants = tenants;
        this.modules = modules;
        this.tenantModules = tenantModules;
        this.fields = fields;
        this.tenantFields = tenantFields;
    }

    /** 先校验会话，再确认租户仍有效，避免通过路径租户号读取别人的模块。 */
    public UnSysTenantMember member(HttpServletRequest request, long tenantId) {
        UnSysTenantMember member = sessions.tenantMember(request, tenantId);
        tenant(tenantId);
        return member;
    }

    /** 修改模块配置只允许租户管理员。 */
    public UnSysTenantMember admin(HttpServletRequest request, long tenantId) {
        UnSysTenantMember member = sessions.tenantAdmin(request, tenantId);
        tenant(tenantId);
        return member;
    }

    public UnSysTenant tenant(long tenantId) {
        UnSysTenant tenant = tenants.selectById(tenantId);
        if (tenant == null || !enabled(tenant.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "租户不存在或已停用");
        }
        return tenant;
    }

    /** 模块必须属于租户的业务系统，启用给该租户后才能读取业务数据。 */
    public UnModule module(long tenantId, long moduleId) {
        UnModule module = modules.selectById(moduleId);
        if (module == null || !module.getSystemId().equals(tenant(tenantId).getSystemId()) || !enabled(module.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "模块不存在或已停用");
        }
        UnModuleTenant relation = tenantModules.selectOne(new LambdaQueryWrapper<UnModuleTenant>()
                .eq(UnModuleTenant::getTenantId, tenantId).eq(UnModuleTenant::getModuleId, moduleId));
        if (relation == null || !enabled(relation.getStatus())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "租户未启用此模块");
        }
        return module;
    }

    /** 基础字段与租户覆盖按编码合并；租户不能改变基础字段的存储类型。 */
    public Map<String, FieldSpec> effectiveFields(long tenantId, long moduleId) {
        Map<String, FieldSpec> result = new LinkedHashMap<>();
        List<UnModuleField> base = fields.selectList(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId, moduleId).eq(UnModuleField::getStatus, 1));
        for (UnModuleField field : base) {
            result.put(field.getFieldName(), new FieldSpec(field.getFieldName(), field.getName(), field.getType(),
                    enabled(field.getMustFlag()), enabled(field.getUnionFlag()), enabled(field.getHiddenFlag()),
                    enabled(field.getAddFlag()), enabled(field.getIndexFlag()), enabled(field.getDetailFlag()),
                    enabled(field.getSortableFlag()), field.getMaxLength(), field.getPrecisionScale(),
                    field.getMinNumRestrict(), field.getMaxNumRestrict(), field.getDictCode(), field.getOptionData()));
        }
        List<UnModuleTenantField> custom = tenantFields.selectList(new LambdaQueryWrapper<UnModuleTenantField>()
                .eq(UnModuleTenantField::getTenantId, tenantId).eq(UnModuleTenantField::getModuleId, moduleId)
                .eq(UnModuleTenantField::getStatus, 1));
        for (UnModuleTenantField field : custom) {
            FieldSpec original = result.get(field.getFieldName());
            if (field.getBaseFieldId() != null && original == null) {
                continue;
            }
            String type = original == null ? field.getType() : original.type();
            result.put(field.getFieldName(), new FieldSpec(field.getFieldName(), field.getName(), type,
                    enabled(field.getMustFlag()), enabled(field.getUnionFlag()), enabled(field.getHiddenFlag()),
                    enabled(field.getAddFlag()), enabled(field.getIndexFlag()), enabled(field.getDetailFlag()),
                    enabled(field.getSortableFlag()), field.getMaxLength(), field.getPrecisionScale(),
                    field.getMinNumRestrict(), field.getMaxNumRestrict(), field.getDictCode(), field.getOptionData()));
        }
        return result;
    }

    public void validateFieldType(String type) {
        if (type == null || !FIELD_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的字段类型");
        }
    }

    public static boolean enabled(Byte value) {
        return value != null && value == 1;
    }

    public record FieldSpec(String fieldName, String name, String type, boolean required, boolean unique,
                            boolean hidden, boolean add, boolean index, boolean detail, boolean sortable,
                            Integer maxLength, Byte precisionScale, BigDecimal minNumber, BigDecimal maxNumber,
                            String dictCode, String optionData) { }
}
