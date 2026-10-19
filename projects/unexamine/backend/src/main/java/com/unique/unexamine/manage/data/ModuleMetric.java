package com.unique.unexamine.manage.data;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.base.entity.UnModuleField;
import com.unique.unexamine.base.entity.UnModuleTenantField;
import com.unique.unexamine.base.mapper.UnModuleFieldMapper;
import com.unique.unexamine.base.mapper.UnModuleTenantFieldMapper;
import com.unique.unexamine.manage.module.ModuleRecordAccess;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** 仪表组件与 KPI 共享模块记录的最小统计能力。 */
@Component
public class ModuleMetric {
    private final JdbcTemplate jdbc;
    private final UnModuleFieldMapper fields;
    private final UnModuleTenantFieldMapper tenantFields;
    private final ModuleRecordAccess recordAccess;

    public ModuleMetric(JdbcTemplate jdbc, UnModuleFieldMapper fields, UnModuleTenantFieldMapper tenantFields,
                        ModuleRecordAccess recordAccess) {
        this.jdbc = jdbc;
        this.fields = fields;
        this.tenantFields = tenantFields;
        this.recordAccess = recordAccess;
    }

    /** 管理员配置时验证指标；实际向成员展示时必须传入请求以应用记录范围。 */
    public BigDecimal value(long tenantId, long moduleId, String fieldName, String aggregation) {
        return value(tenantId, moduleId, fieldName, aggregation, null);
    }

    /** 面向成员的汇总与模块记录列表使用同一数据范围。 */
    public BigDecimal value(long tenantId, long moduleId, String fieldName, String aggregation,
                            HttpServletRequest request) {
        ModuleRecordAccess.Scope scope = request == null ? null : recordAccess.scope(request, tenantId);
        StringBuilder where = new StringBuilder(" r.tenant_id = ? AND r.module_id = ? AND r.record_status <> 2");
        int baseLength = where.length();
        List<Object> scopeParams = new ArrayList<>();
        if (scope != null) recordAccess.appendReadPredicate(where, scopeParams, scope);
        if ("count".equals(aggregation)) {
            List<Object> params = new ArrayList<>(List.of(tenantId, moduleId));
            params.addAll(scopeParams);
            Long result = jdbc.queryForObject("SELECT COUNT(*) FROM un_module_record r WHERE " + where,
                    Long.class, params.toArray());
            return BigDecimal.valueOf(result == null ? 0 : result);
        }
        if (!"sum".equals(aggregation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "仅支持 count 或 sum 统计");
        }
        UnModuleTenantField tenantField = tenantFields.selectOne(new LambdaQueryWrapper<UnModuleTenantField>()
                .eq(UnModuleTenantField::getTenantId, tenantId)
                .eq(UnModuleTenantField::getModuleId, moduleId)
                .eq(UnModuleTenantField::getFieldName, fieldName)
                .eq(UnModuleTenantField::getStatus, 1));
        String type = tenantField == null ? null : tenantField.getType();
        if (type == null) {
            UnModuleField field = fields.selectOne(new LambdaQueryWrapper<UnModuleField>()
                    .eq(UnModuleField::getModuleId, moduleId)
                    .eq(UnModuleField::getFieldName, fieldName)
                    .eq(UnModuleField::getStatus, 1));
            type = field == null ? null : field.getType();
        }
        if (!"number".equals(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "求和字段必须是启用的数值字段");
        }
        // 通过记录主键关联并再次限制租户/模块，避免扩展值孤儿数据或其他范围数据进入结果。
        List<Object> params = new ArrayList<>(List.of(tenantId, moduleId, tenantId, moduleId, fieldName));
        params.addAll(scopeParams);
        BigDecimal result = jdbc.queryForObject("""
                SELECT COALESCE(SUM(d.value_number), 0)
                FROM un_module_record_data d
                JOIN un_module_record r ON r.id = d.record_id
                WHERE r.tenant_id = ? AND r.module_id = ? AND r.record_status <> 2
                  AND d.tenant_id = ? AND d.module_id = ? AND d.field_name = ?
                """ + where.substring(baseLength),
                BigDecimal.class, params.toArray());
        return result == null ? BigDecimal.ZERO : result;
    }
}
