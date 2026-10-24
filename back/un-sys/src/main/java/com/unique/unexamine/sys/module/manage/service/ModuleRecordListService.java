package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleColumnDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Column;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ListView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Row;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordFilter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 执行模块列表查询：场景条件、动态字段、数据范围和展示列在同一查询入口收敛。 */
@Service
public class ModuleRecordListService {
    private final ModulePermissionService permissions;
    private final ModuleConfigService modules;
    private final ModuleSceneService scenes;
    private final ModuleColumnService columnPreferences;
    private final ModuleFieldService fieldRules;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService data;
    private final ObjectMapper json;
    private final ModuleShareService shares;

    /** 注入生成的基础 Service；动态查询仍复用基础持久化入口。 */
    public ModuleRecordListService(ModulePermissionService permissions,ModuleConfigService modules,
            ModuleSceneService scenes,ModuleColumnService columnPreferences,ModuleFieldService fieldRules,IUnModuleRecordService records,
            IUnModuleRecordDataService data,ObjectMapper json,ModuleShareService shares) {
        this.permissions=permissions; this.modules=modules; this.scenes=scenes;
        this.columnPreferences=columnPreferences; this.fieldRules=fieldRules;
        this.records=records; this.data=data; this.json=json; this.shares=shares;
    }

    /** 列表场景与请求条件求交集，权限在条件、排序和返回列三处分别执行。 */
    public ListView query(String token,Long systemId,Long tenantId,Long moduleId,ModuleRecordSearchBO request) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        return queryWithAccess(access,systemId,moduleId,request,false);
    }

    /** 导出复用列表场景和筛选，但使用独立导出菜单及字段导出许可。 */
    public ListView export(String token,Long systemId,Long tenantId,Long moduleId,ModuleRecordSearchBO request) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"EXPORT");
        return queryWithAccess(access,systemId,moduleId,request,true);
    }

    private ListView queryWithAccess(ModulePermissionService.Access access,Long systemId,Long moduleId,
                                   ModuleRecordSearchBO request,boolean exporting) {
        var context=access.context(); modules.activeModule(systemId,moduleId,context.tenantId());
        if (request==null) throw new IllegalArgumentException("查询条件不能为空");
        Page<UnModuleRecord> requestedPage=request.parse(exporting?500:100);
        String scope=request.getScope()==null?"ALL":request.getScope();
        if (!Set.of("ALL","MINE","SHARED").contains(scope)) throw new IllegalArgumentException("列表范围无效");
        if (request.getKeyword()!=null && request.getKeyword().length()>100)
            throw new IllegalArgumentException("搜索文本过长");
        if (request.getKeyword()!=null && !request.getKeyword().isBlank() && !access.policy().readable("name"))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "没有标题字段的查询权限");
        var saved=scenes.resolve(moduleId,request.getSceneId(),access);
        List<ModuleSceneDto.Condition> conditions=new ArrayList<>(saved.filters());
        if (request.getFilters()!=null) conditions.addAll(request.getFilters());
        var settings=new ModuleSceneDto.Settings(conditions,
                request.getColumnFieldIds()==null?saved.columnFieldIds():request.getColumnFieldIds(),
                request.getSortFieldId()==null?saved.sortFieldId():request.getSortFieldId(),
                request.getDescending()==null?saved.descending():request.getDescending());
        scenes.check(systemId,context.tenantId(),moduleId,settings,access);
        List<UnModuleField> currentFields=modules.currentFields(moduleId);
        Map<Long,UnModuleField> fields=currentFields.stream().collect(Collectors.toMap(UnModuleField::getId,field->field));
        Map<Long,ModuleColumnDto.Preference> personal=columnPreferences.effective(moduleId,access);
        List<UnModuleField> selected=settings.columnFieldIds().isEmpty()?currentFields:settings.columnFieldIds()
                .stream().map(fields::get).toList();
        Set<Long> actionFields=exporting?Set.copyOf(access.definition().settings().fieldIds()):Set.of();
        if (exporting && request.getColumnFieldIds()!=null && !request.getColumnFieldIds().isEmpty()
                && selected.stream().anyMatch(field->
                !access.policy().exportable(field.getFieldCode())
                        || !actionFields.isEmpty() && !actionFields.contains(field.getId())))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN,
                    "导出列超出动作或字段权限");
        Map<Long,Integer> defaultPositions=new HashMap<>();
        for (int position=0;position<selected.size();position++) defaultPositions.put(selected.get(position).getId(),position);
        List<UnModuleField> columns=selected.stream()
                .filter(field->exporting?access.policy().exportable(field.getFieldCode())
                        && (actionFields.isEmpty() || actionFields.contains(field.getId()))
                        :access.policy().readable(field.getFieldCode()))
                .filter(field->exporting || !personal.containsKey(field.getId()) || !personal.get(field.getId()).hidden())
                .sorted(Comparator.comparingInt(field->personal.containsKey(field.getId())
                        ?personal.get(field.getId()).sortNo():1000+defaultPositions.get(field.getId())))
                .toList();
        List<Column> headers=columns.stream().map(field->new Column(field.getId(),field.getFieldCode(),
                field.getFieldName(),field.getFieldType(),personal.containsKey(field.getId())
                ?personal.get(field.getId()).widthPx():null,personal.containsKey(field.getId())
                ?personal.get(field.getId()).fixedSide():null)).toList();
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("module_id",moduleId)
                .isNull("deleted_time");
        if (!shares.applyRecordScope(where,access,scope))
            return new ListView(headers,new Page<Row>(requestedPage.getCurrent(),requestedPage.getSize(),0)
                    .setRecords(List.of()));
        if (request.getKeyword()!=null && !request.getKeyword().isBlank())
            where.apply("LOCATE({0}, name) > 0",request.getKeyword().trim());
        for (var condition:conditions) ModuleRecordFilter.apply(where,fields.get(condition.fieldId()),condition,json);
        if (settings.sortFieldId()==null) where.orderByDesc("id");
        else sort(where,fields.get(settings.sortFieldId()),settings.descending());
        Page<UnModuleRecord> page=records.page(requestedPage,where);
        if (page.getRecords().isEmpty())
            return new ListView(headers,new Page<Row>(page.getCurrent(),page.getSize(),page.getTotal()).setRecords(List.of()));
        List<Long> recordIds=page.getRecords().stream().map(UnModuleRecord::getId).toList();
        List<Long> extensionIds=columns.stream().filter(field->!"name".equals(field.getFieldCode()))
                .map(UnModuleField::getId).toList();
        Map<Long,Map<Long,Object>> values=new HashMap<>();
        if (!extensionIds.isEmpty()) for (var row:data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .in(UnModuleRecordData::getRecordId,recordIds).in(UnModuleRecordData::getFieldId,extensionIds))) {
            UnModuleField field=fields.get(row.getFieldId());
            values.computeIfAbsent(row.getRecordId(),id->new HashMap<>())
                    .put(row.getFieldId(),fieldRules.stored(row,FieldType.parse(field.getFieldType())));
        }
        List<Row> rows=new ArrayList<>();
        for (UnModuleRecord record:page.getRecords()) {
            Map<String,Object> cellValues=new LinkedHashMap<>(); Map<String,Object> display=new LinkedHashMap<>();
            for (UnModuleField field:columns) {
                Object value="name".equals(field.getFieldCode())?record.getName()
                        :values.getOrDefault(record.getId(),Map.of()).get(field.getId());
                cellValues.put(field.getFieldCode(),value);
                if (value!=null) {
                    FieldDefinition definition=fieldRules.configured(field.getFieldCode(),modules.fieldConfig(field).configuration());
                    if (definition.type().options()) display.put(field.getFieldCode(),
                            fieldRules.display(systemId,record.getTenantId(),definition,value));
                    else if (definition.type()==FieldType.USER) display.put(field.getFieldCode(),
                            fieldRules.present(systemId,record.getTenantId(),definition,value));
                }
            }
            rows.add(new Row(record.getId(),access.policy().readable("name")?record.getName():null,
                    access.policy().readable("status")?record.getStatus():null,
                    access.policy().readable("ownerUserId")?record.getOwnerUserId():null,
                    record.getTenantId(),!record.getTenantId().equals(context.tenantId()),cellValues,display));
        }
        return new ListView(headers,new Page<Row>(page.getCurrent(),page.getSize(),page.getTotal()).setRecords(rows));
    }

    /** 字段 ID 已从当前定义校验；相关子查询仅用于已授权的动态列排序。 */
    private void sort(QueryWrapper<UnModuleRecord> where,UnModuleField field,boolean descending) {
        if ("name".equals(field.getFieldCode())) where.orderBy(true,!descending,"name");
        else {
            FieldType type=FieldType.parse(field.getFieldType());
            if (Set.of(FieldType.Kind.OPTIONS,FieldType.Kind.OBJECT,FieldType.Kind.ROWS,
                    FieldType.Kind.REFERENCES).contains(type.kind()))
                throw new IllegalArgumentException("该字段类型暂不支持列表排序");
            FieldDefinition definition=fieldRules.configured(field.getFieldCode(),modules.fieldConfig(field).configuration());
            if (type.kind()==FieldType.Kind.REFERENCE && Boolean.TRUE.equals(definition.rules().get("multiple")))
                throw new IllegalArgumentException("多选人员字段暂不支持列表排序");
            String indexed=type.valueColumn().sqlName();
            if (indexed==null || "value_json".equals(indexed))
                throw new IllegalArgumentException("该字段类型暂不支持列表排序");
            String expression="d."+indexed;
            String value="(SELECT "+expression+" FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = "
                    +"un_module_record.id AND d.field_id = "+field.getId()+")";
            where.orderBy(true,!descending,value);
        }
        where.orderByDesc("id");
    }

}
