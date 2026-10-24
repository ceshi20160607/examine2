package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleFieldSort;
import com.unique.unexamine.sys.module.base.service.IUnModuleFieldSortService;
import com.unique.unexamine.sys.module.manage.dto.ModuleColumnDto.Preference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 当前账号的模块列表表头偏好；字段权限撤销后不会继续暴露原列。 */
@Service
public class ModuleColumnService {
    private final ModulePermissionService permissions;
    private final ModuleConfigService modules;
    private final IUnModuleFieldSortService preferences;

    /** 注入生成的表头基础 Service 和模块权限入口。 */
    public ModuleColumnService(ModulePermissionService permissions,ModuleConfigService modules,
            IUnModuleFieldSortService preferences) {
        this.permissions=permissions; this.modules=modules; this.preferences=preferences;
    }

    /** 提交的是当前账号完整表头偏好，移除的旧项只删除本人的配置行。 */
    @Transactional(rollbackFor=Exception.class)
    public List<Preference> save(String token,Long systemId,Long tenantId,Long moduleId,List<Preference> requested) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        modules.activeModule(systemId,moduleId,access.context().tenantId());
        if (requested==null || requested.size()>100) throw new IllegalArgumentException("表头配置数量无效");
        Map<Long,UnModuleField> fields=new HashMap<>();
        for (var field:modules.currentFields(moduleId)) fields.put(field.getId(),field);
        Set<Long> ids=new HashSet<>(); Set<Integer> positions=new HashSet<>();
        for (Preference item:requested) {
            if (item==null || item.fieldId()==null || !ids.add(item.fieldId()) || !positions.add(item.sortNo())
                    || item.sortNo()<0 || item.sortNo()>=100 || item.widthPx()!=null && (item.widthPx()<60 || item.widthPx()>1000)
                    || item.fixedSide()!=null && !Set.of("LEFT","RIGHT").contains(item.fixedSide()))
                throw new IllegalArgumentException("表头列标识、顺序、宽度或固定位置无效");
            UnModuleField field=fields.get(item.fieldId());
            if (field==null || !access.policy().readable(field.getFieldCode()))
                throw new IllegalArgumentException("表头包含未配置或无权读取的字段");
        }
        List<UnModuleFieldSort> existing=rows(moduleId,access.context().tenantId(),access.accountId());
        Map<Long,UnModuleFieldSort> byField=new HashMap<>();
        existing.forEach(row->byField.put(row.getFieldId(),row));
        for (Preference item:requested) {
            UnModuleFieldSort row=byField.remove(item.fieldId());
            if (row==null) {
                row=new UnModuleFieldSort(); row.setModuleId(moduleId);
                row.setTenantId(access.context().tenantId()); row.setAccountId(access.accountId());
                row.setFieldId(item.fieldId());
            }
            row.setSortNo(item.sortNo()); row.setWidthPx(item.widthPx());
            row.setHiddenFlag(item.hidden()); row.setFixedSide(item.fixedSide());
            preferences.addOrUpdate(row);
        }
        if (!byField.isEmpty()) preferences.deleteByIds(byField.values().stream().map(UnModuleFieldSort::getId).toList());
        return list(token,systemId,tenantId,moduleId);
    }

    /** 查询只返回当前仍有权读取的当前字段的个人配置。 */
    public List<Preference> list(String token,Long systemId,Long tenantId,Long moduleId) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        modules.activeModule(systemId,moduleId,access.context().tenantId());
        return effective(moduleId,access).values().stream().sorted(java.util.Comparator.comparingInt(Preference::sortNo))
                .toList();
    }

    /** 列表服务调用同一份个人偏好，不使用请求体中的账号 ID。 */
    public Map<Long,Preference> effective(Long moduleId,ModulePermissionService.Access access) {
        Map<Long,UnModuleField> fields=new HashMap<>();
        for (var field:modules.currentFields(moduleId)) fields.put(field.getId(),field);
        Map<Long,Preference> result=new HashMap<>();
        for (var row:rows(moduleId,access.context().tenantId(),access.accountId())) {
            UnModuleField field=fields.get(row.getFieldId());
            if (field!=null && access.policy().readable(field.getFieldCode()))
                result.put(field.getId(),new Preference(field.getId(),row.getSortNo(),row.getWidthPx(),
                        Boolean.TRUE.equals(row.getHiddenFlag()),row.getFixedSide()));
        }
        return result;
    }

    private List<UnModuleFieldSort> rows(Long moduleId,Long tenantId,Long accountId) {
        return preferences.list(new LambdaQueryWrapper<UnModuleFieldSort>()
                .eq(UnModuleFieldSort::getModuleId,moduleId).eq(UnModuleFieldSort::getTenantId,tenantId)
                .eq(UnModuleFieldSort::getAccountId,accountId));
    }
}
