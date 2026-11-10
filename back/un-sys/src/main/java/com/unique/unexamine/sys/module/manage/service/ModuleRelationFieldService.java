package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleFieldService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
import com.unique.unexamine.sys.module.manage.dto.ModuleRelationFieldDto.Option;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 关联字段以目标模块真实记录为值，统一检查目标数据范围与展示字段权限。 */
@Service
public class ModuleRelationFieldService {
    private final ModulePermissionService permissions;
    private final ModuleShareService shares;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService values;
    private final IUnModuleFieldService fields;
    private final ModuleFieldService fieldRules;
    private final ModulePageService pages;
    private final ModulePageStatusService pageStatuses;

    public ModuleRelationFieldService(ModulePermissionService permissions,ModuleShareService shares,
            IUnModuleRecordService records,IUnModuleRecordDataService values,
            IUnModuleFieldService fields,ModuleFieldService fieldRules,ModulePageService pages,
            ModulePageStatusService pageStatuses) {
        this.permissions=permissions; this.shares=shares; this.records=records;
        this.values=values; this.fields=fields; this.fieldRules=fieldRules;
        this.pages=pages; this.pageStatuses=pageStatuses;
    }

    /** 记录保存时逐个检查关联主键；多选仍引用目标模块记录，不写入嵌套子表数据。 */
    public void validate(String token,Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()!=FieldType.RELATION || value==null) return;
        var access=target(token,systemId,tenantId,field,"DETAIL");
        for (Object raw:ids(value)) readable(access,id(raw));
    }

    /** 审批自动节点没有登录 token 时，仍按系统、租户、模块和逻辑删除状态校验关联记录。 */
    public void validateByProcess(Long systemId, Long tenantId, FieldDefinition field, Object value) {
        if (field.type() != FieldType.RELATION || value == null) return;
        Long targetModuleId = targetModule(field);
        for (Object raw : ids(value)) {
            UnModuleRecord record = records.getById(id(raw));
            if (record == null || !systemId.equals(record.getSystemId())
                    || !targetModuleId.equals(record.getModuleId()) || record.getDeletedTime() != null
                    || !tenantId.equals(record.getTenantId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND, "关联字段目标记录不存在");
        }
    }

    /** 已选摘要和选择列表使用同一份目标字段配置，并在读取时再次过滤权限。 */
    public Object display(String token,Long systemId,Long tenantId,UnModuleField sourceField,FieldDefinition field,Object value) {
        if (field.type()!=FieldType.RELATION || value==null) return null;
        ModulePermissionService.Access access;
        try { access=target(token,systemId,tenantId,field,"DETAIL"); }
        catch (BaseException ex) {
            if (ex.getCode() >= 400 && ex.getCode() < 500) return null;
            throw ex;
        }
        List<Option> selected=new ArrayList<>();
        for (Object raw:ids(value)) {
            try { selected.add(option(access,readable(access,id(raw)),sourceField)); }
            catch (BaseException ex) {
                if (!(ex.getCode() >= 400 && ex.getCode() < 500)) throw ex;
            }
        }
        return value instanceof List<?> ? selected : selected.isEmpty()?null:selected.getFirst();
    }

    /** 选择弹窗仅分页返回有权读取的目标记录与配置的展示字段。 */
    public Page<Option> options(String token,Long systemId,Long tenantId,Long sourceModuleId,
            String fieldKey,String operation,Long sourceRecordId,ModuleRecordSearchBO search) {
        if (!List.of("CREATE","EDIT").contains(operation))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "来源动作只能是新建或编辑");
        // 该服务也会被流程和内部业务调用，不能依赖 Controller 一定传入分页对象。
        ModuleRecordSearchBO criteria = search == null ? new ModuleRecordSearchBO() : search;
        if (criteria.getKeyword() != null && criteria.getKeyword().length() > 100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "搜索文本过长");
        var source=permissions.require(token,systemId,tenantId,sourceModuleId,operation);
        UnModuleField field=fields.getOne(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,sourceModuleId).eq(UnModuleField::getFieldKey,fieldKey)
                .eq(UnModuleField::getStatus,"ACTIVE"),false);
        if (field == null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"关联字段不存在");
        FieldType fieldType;
        try {
            fieldType = FieldType.parse(field.getFieldType());
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "关联字段类型配置损坏", ex);
        }
        if (fieldType!=FieldType.RELATION)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"关联字段不存在");
        var sourceFields=source.policy();
        if (sourceRecordId!=null) {
            UnModuleRecord sourceRecord=records.getById(sourceRecordId);
            if (sourceRecord==null || sourceRecord.getDeletedTime()!=null
                    || !systemId.equals(sourceRecord.getSystemId())
                    || !source.context().tenantId().equals(sourceRecord.getTenantId())
                    || !sourceModuleId.equals(sourceRecord.getModuleId()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"编辑的来源记录不存在");
            if (permissions.teamAllows(source,sourceRecord)) sourceFields=permissions.recordPolicy(source,sourceRecord);
            else source.policy().requireOwner(sourceRecord.getOwnerUserId());
        }
        sourceFields.requireWritable(java.util.Set.of(fieldKey));
        fieldRules.requireEditable(field);
        FieldDefinition definition;
        try {
            definition=fieldRules.configured(field);
        } catch (IllegalArgumentException ex) {
            // 历史配置即使绕过当前保存入口，也必须在 HTTP 边界转换为统一业务异常。
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"关联字段配置损坏",ex);
        }
        var access=target(token,systemId,source.context().tenantId(),definition,"LIST");
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("module_id",targetModule(definition)).isNull("deleted_time");
        pageStatuses.applyFilters(where,systemId,targetModule(definition),criteria.getApprovalStatusFilters());
        if (!shares.applyRecordScope(where,access,"ALL"))
            return new Page<Option>(criteria.parse().getCurrent(),criteria.parse().getSize(),0).setRecords(List.of());
        if (criteria.getKeyword()!=null && !criteria.getKeyword().isBlank())
            where.apply("LOCATE({0}, name) > 0",criteria.getKeyword().trim());
        Page<UnModuleRecord> page=records.page(criteria.parse(),where.orderByDesc("id"));
        Page<Option> result=new Page<>(page.getCurrent(),page.getSize(),page.getTotal());
        result.setRecords(page.getRecords().stream().map(row->option(access,row,field)).toList());
        return result;
    }

    private ModulePermissionService.Access target(String token,Long systemId,Long tenantId,
            FieldDefinition field,String operation) {
        return permissions.require(token,systemId,tenantId,targetModule(field),operation);
    }
    private Long targetModule(FieldDefinition field) {
        return id(field.rules().get("relationModuleId"));
    }
    private Long id(Object raw) {
        if (!(raw instanceof Number number) || number.longValue()<1
                || number.doubleValue()!=number.longValue())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"关联记录主键无效");
        return number.longValue();
    }
    private List<?> ids(Object value) { return value instanceof List<?> list?list:List.of(value); }

    private UnModuleRecord readable(ModulePermissionService.Access access,Long id) {
        UnModuleRecord row=records.getById(id);
        if (row==null || row.getDeletedTime()!=null
                || !access.context().systemId().equals(row.getSystemId())
                || !access.definition().module().getId().equals(row.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"关联目标记录不存在");
        if (access.context().tenantId().equals(row.getTenantId())) {
            if (!permissions.teamAllows(access,row)) access.policy().requireOwner(row.getOwnerUserId());
        }
        else shares.requireShared(access,row);
        return row;
    }

    private Option option(ModulePermissionService.Access access,UnModuleRecord row,UnModuleField sourceField) {
        List<Long> ids=pages.activeFields(sourceField.getModuleId(),"RELATION",sourceField.getId()).stream()
                .map(com.unique.unexamine.sys.module.base.entity.UnModulePageField::getFieldId).toList();
        List<UnModuleField> selected=ids.isEmpty()?List.of():fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,row.getModuleId()).eq(UnModuleField::getStatus,"ACTIVE")
                .in(UnModuleField::getId,ids));
        Map<Long,UnModuleField> byId=new HashMap<>();
        selected.forEach(field->byId.put(field.getId(),field));
        Map<Long,UnModuleRecordData> data=new HashMap<>();
        if (!selected.isEmpty()) values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId,row.getId()).in(UnModuleRecordData::getFieldId,
                        selected.stream().map(UnModuleField::getId).toList()))
                .forEach(item->data.put(item.getFieldId(),item));
        Map<String,Object> visible=new LinkedHashMap<>();
        var policy=permissions.recordPolicy(access,row);
        for (Long fieldId:ids) {
            UnModuleField field=byId.get(fieldId);
            if (field==null || !policy.readable(field.getFieldKey())) continue;
            FieldDefinition definition=fieldRules.configured(field);
            Object value="name".equals(field.getFieldKey())?row.getName()
                    :ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(row,definition)
                    :fieldRules.stored(data.get(field.getId()),definition);
            if (definition.type().options()) value=fieldRules.display(row.getSystemId(),row.getTenantId(),definition,value);
            else if (definition.type()!=FieldType.RELATION)
                value=fieldRules.present(row.getSystemId(),row.getTenantId(),definition,value);
            visible.put(field.getFieldKey(),value);
        }
        return new Option(row.getId(),visible);
    }

}

