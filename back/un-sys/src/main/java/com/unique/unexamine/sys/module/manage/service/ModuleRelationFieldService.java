package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleFieldService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 关联字段以目标模块真实记录为值，统一检查目标数据范围与展示字段权限。 */
@Service
public class ModuleRelationFieldService {
    public record Option(Long id,Map<String,Object> fields) {}

    private final ModulePermissionService permissions;
    private final ModuleShareService shares;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService values;
    private final IUnModuleFieldService fields;
    private final ModuleFieldService fieldRules;

    public ModuleRelationFieldService(ModulePermissionService permissions,ModuleShareService shares,
            IUnModuleRecordService records,IUnModuleRecordDataService values,
            IUnModuleFieldService fields,ModuleFieldService fieldRules) {
        this.permissions=permissions; this.shares=shares; this.records=records;
        this.values=values; this.fields=fields; this.fieldRules=fieldRules;
    }

    /** 记录保存时逐个检查关联主键；多选仍引用目标模块记录，不写入嵌套子表数据。 */
    public void validate(String token,Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()!=FieldType.RELATION || value==null) return;
        var access=target(token,systemId,tenantId,field,"DETAIL");
        for (Object raw:ids(value)) readable(access,id(raw));
    }

    /** 已选摘要和选择列表使用同一份目标字段配置，并在读取时再次过滤权限。 */
    public Object display(String token,Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()!=FieldType.RELATION || value==null) return null;
        ModulePermissionService.Access access;
        try { access=target(token,systemId,tenantId,field,"DETAIL"); }
        catch (ResponseStatusException ex) {
            if (ex.getStatusCode().is4xxClientError()) return null;
            throw ex;
        }
        List<Option> selected=new ArrayList<>();
        for (Object raw:ids(value)) {
            try { selected.add(option(access,readable(access,id(raw)),field)); }
            catch (ResponseStatusException ex) {
                if (!ex.getStatusCode().is4xxClientError()) throw ex;
            }
        }
        return value instanceof List<?> ? selected : selected.isEmpty()?null:selected.getFirst();
    }

    /** 选择弹窗仅分页返回有权读取的目标记录与配置的展示字段。 */
    public Page<Option> options(String token,Long systemId,Long tenantId,Long sourceModuleId,
            String fieldKey,String operation,SearchBO search) {
        if (!List.of("CREATE","EDIT").contains(operation))
            throw new IllegalArgumentException("来源动作只能是新建或编辑");
        var source=permissions.require(token,systemId,tenantId,sourceModuleId,operation);
        UnModuleField field=fields.getOne(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,sourceModuleId).eq(UnModuleField::getFieldKey,fieldKey)
                .eq(UnModuleField::getStatus,"ACTIVE"),false);
        if (field==null || FieldType.parse(field.getFieldType())!=FieldType.RELATION)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"关联字段不存在");
        source.policy().requireWritable(java.util.Set.of(fieldKey));
        FieldDefinition definition=fieldRules.configured(field);
        var access=target(token,systemId,source.context().tenantId(),definition,"LIST");
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("module_id",targetModule(definition)).isNull("deleted_time");
        if (!shares.applyRecordScope(where,access,"ALL"))
            return new Page<Option>(search.parse().getCurrent(),search.parse().getSize(),0).setRecords(List.of());
        if (search.getKeyword()!=null && !search.getKeyword().isBlank())
            where.apply("LOCATE({0}, name) > 0",search.getKeyword().trim());
        Page<UnModuleRecord> page=records.page(search.parse(),where.orderByDesc("id"));
        Page<Option> result=new Page<>(page.getCurrent(),page.getSize(),page.getTotal());
        result.setRecords(page.getRecords().stream().map(row->option(access,row,definition)).toList());
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
            throw new IllegalArgumentException("关联记录主键无效");
        return number.longValue();
    }
    private List<?> ids(Object value) { return value instanceof List<?> list?list:List.of(value); }

    private UnModuleRecord readable(ModulePermissionService.Access access,Long id) {
        UnModuleRecord row=records.getById(id);
        if (row==null || row.getDeletedTime()!=null
                || !access.context().systemId().equals(row.getSystemId())
                || !access.definition().module().getId().equals(row.getModuleId()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"关联目标记录不存在");
        if (access.context().tenantId().equals(row.getTenantId()))
            access.policy().requireOwner(row.getOwnerUserId());
        else shares.requireShared(access,row);
        return row;
    }

    private Option option(ModulePermissionService.Access access,UnModuleRecord row,FieldDefinition relation) {
        Map<?,?> relationConfig=(Map<?,?>)relation.rules().get("relationConfig");
        List<String> keys=((List<?>)relationConfig.get("displayFieldKeys")).stream().map(String.class::cast).toList();
        List<UnModuleField> selected=fields.list(new LambdaQueryWrapper<UnModuleField>()
                .eq(UnModuleField::getModuleId,row.getModuleId()).eq(UnModuleField::getStatus,"ACTIVE")
                .in(UnModuleField::getFieldKey,keys));
        Map<String,UnModuleField> byKey=new HashMap<>();
        selected.forEach(field->byKey.put(field.getFieldKey(),field));
        Map<Long,UnModuleRecordData> data=new HashMap<>();
        if (!selected.isEmpty()) values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId,row.getId()).in(UnModuleRecordData::getFieldId,
                        selected.stream().map(UnModuleField::getId).toList()))
                .forEach(item->data.put(item.getFieldId(),item));
        Map<String,Object> visible=new LinkedHashMap<>();
        for (String key:keys) {
            UnModuleField field=byKey.get(key);
            if (field==null || !access.policy().readable(field.getFieldKey())) continue;
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

