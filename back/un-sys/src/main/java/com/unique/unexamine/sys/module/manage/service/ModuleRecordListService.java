package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.permission.PermissionPolicy;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.bo.ModuleRecordSearchBO;
import com.unique.unexamine.sys.module.manage.dto.ModuleColumnDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Column;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ApprovalStatusColumn;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.ListView;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordListDto.Row;
import com.unique.unexamine.sys.module.manage.dto.ModuleSceneDto;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordFilter;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
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
    private final ModuleRelationFieldService relationFields;

    /** 注入生成的基础 Service；动态查询仍复用基础持久化入口。 */
    public ModuleRecordListService(ModulePermissionService permissions,ModuleConfigService modules,
            ModuleSceneService scenes,ModuleColumnService columnPreferences,ModuleFieldService fieldRules,IUnModuleRecordService records,
            IUnModuleRecordDataService data,ObjectMapper json,ModuleShareService shares,
            ModuleRelationFieldService relationFields) {
        this.permissions=permissions; this.modules=modules; this.scenes=scenes;
        this.columnPreferences=columnPreferences; this.fieldRules=fieldRules;
        this.records=records; this.data=data; this.json=json; this.shares=shares; this.relationFields=relationFields;
    }

    /** 列表场景与请求条件求交集，权限在条件、排序和返回列三处分别执行。 */
    public ListView query(String token,Long systemId,Long tenantId,Long moduleId,ModuleRecordSearchBO request) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        return queryWithAccess(token,access,systemId,moduleId,request,false);
    }

    /** 导出复用列表场景和筛选，但使用独立导出菜单及字段导出许可。 */
    public ListView export(String token,Long systemId,Long tenantId,Long moduleId,ModuleRecordSearchBO request) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"EXPORT");
        return queryWithAccess(token,access,systemId,moduleId,request,true);
    }

    private ListView queryWithAccess(String token,ModulePermissionService.Access access,Long systemId,Long moduleId,
                                   ModuleRecordSearchBO request,boolean exporting) {
        var context=access.context(); modules.activeModule(systemId,moduleId,context.tenantId());
        PermissionPolicy listFields=exporting?access.policy():permissions.listFieldsPolicy(access);
        if (request==null) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "查询条件不能为空");
        Page<UnModuleRecord> requestedPage=request.parse(exporting?500:100);
        String scope=request.getScope()==null?"ALL":request.getScope();
        if (!Set.of("ALL","MINE","SHARED").contains(scope)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "列表范围无效");
        if (request.getKeyword()!=null && request.getKeyword().length()>100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "搜索文本过长");
        if (request.getAdditionalKeyword()!=null && request.getAdditionalKeyword().length()>100)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "搜索文本过长");
        if ((request.getKeyword()!=null && !request.getKeyword().isBlank()
                || request.getAdditionalKeyword()!=null && !request.getAdditionalKeyword().isBlank())
                && !listFields.readable("name"))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "没有标题字段的查询权限");
        // 关联数据源只会分批回查主键；普通接口也必须拒绝无效或过大的主键集合，避免超大 IN 条件。
        if (request.getRecordIds()!=null && (request.getRecordIds().size()>5000
                || request.getRecordIds().stream().anyMatch(id -> id==null || id<1)))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "记录主键筛选无效或数量过多");
        var saved=scenes.resolve(moduleId,request.getSceneId(),access);
        List<ModuleSceneDto.Condition> conditions=new ArrayList<>(saved.filters());
        if (request.getAdditionalSceneId()!=null && !request.getAdditionalSceneId().equals(request.getSceneId()))
            conditions.addAll(scenes.resolve(moduleId,request.getAdditionalSceneId(),access).filters());
        if (request.getFilters()!=null) conditions.addAll(request.getFilters());
        var settings=new ModuleSceneDto.Settings(conditions,
                request.getColumnFieldIds()==null?saved.columnFieldIds():request.getColumnFieldIds(),
                request.getSortFieldId()==null?saved.sortFieldId():request.getSortFieldId(),
                request.getDescending()==null?saved.descending():request.getDescending());
        scenes.check(systemId,context.tenantId(),moduleId,settings,access);
        List<UnModuleField> currentFields=modules.currentFields(moduleId);
        Map<Long,UnModuleField> fields=currentFields.stream().collect(Collectors.toMap(UnModuleField::getId,field->field));
        Map<Long,ModuleColumnDto.Preference> personal=columnPreferences.effective(moduleId,access);
        List<UnModuleField> pageColumns=exporting?currentFields:modules.pageFields(moduleId,"LIST").stream()
                .map(slot->fields.get(slot.getFieldId())).filter(java.util.Objects::nonNull).toList();
        List<UnModuleField> selected=settings.columnFieldIds().isEmpty()?pageColumns:settings.columnFieldIds()
                .stream().map(fields::get).toList();
        Set<Long> actionFields=exporting?Set.copyOf(access.definition().settings().fieldIds()):Set.of();
        if (exporting && request.getColumnFieldIds()!=null && !request.getColumnFieldIds().isEmpty()
                && selected.stream().anyMatch(field->
                !access.policy().exportable(field.getFieldKey())
                        || !actionFields.isEmpty() && !actionFields.contains(field.getId())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "导出列超出动作或字段权限");
        Map<Long,Integer> defaultPositions=new HashMap<>();
        for (int position=0;position<selected.size();position++) defaultPositions.put(selected.get(position).getId(),position);
        List<UnModuleField> columns=selected.stream()
                .filter(field->exporting?access.policy().exportable(field.getFieldKey())
                        && (actionFields.isEmpty() || actionFields.contains(field.getId()))
                        :listFields.readable(field.getFieldKey()))
                .filter(field->exporting || !personal.containsKey(field.getId()) || !personal.get(field.getId()).hidden())
                .sorted(Comparator.comparingInt(field->personal.containsKey(field.getId())
                        ?personal.get(field.getId()).sortNo():1000+defaultPositions.get(field.getId())))
                .toList();
        List<Column> headers=columns.stream().map(field->new Column(field.getId(),field.getFieldKey(),
                field.getFieldName(),field.getFieldType(),personal.containsKey(field.getId())
                ?personal.get(field.getId()).widthPx():null,personal.containsKey(field.getId())
                ?personal.get(field.getId()).fixedSide():null)).toList();
        List<ApprovalStatusColumn> statusHeaders=exporting?List.of():modules.approvalStatusFields(moduleId).stream()
                .map(group->new ApprovalStatusColumn(group.id(),group.code(),group.name(),group.sortNo())).toList();
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("module_id",moduleId)
                .isNull("deleted_time");
        if (request.getStatusCode()!=null || request.getCheckStatus()!=null) {
            if (!listFields.readable("status")) throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"没有状态字段的查询权限");
            if (request.getStatusCode()!=null) {
                if (!request.getStatusCode().matches("[A-Z][A-Z0-9_]{0,39}"))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"业务状态编码无效");
                where.eq("status",request.getStatusCode());
            }
            if (request.getCheckStatus()!=null) {
                if (request.getCheckStatus()<0 || request.getCheckStatus()>4)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"审批状态值无效");
                where.eq("check_status",request.getCheckStatus());
            }
        }
        // 关联数据源只回查已知记录主键，但仍沿用当前模块的权限、租户和逻辑删除条件。
        // 空主键集合表示两组条件没有交集，不能把它解释成不筛选。
        if (request.getRecordIds()!=null) {
            if (!request.getRecordIds().isEmpty()) where.in("id",request.getRecordIds());
        }
        modules.applyApprovalStatusFilters(where,systemId,moduleId,request.getApprovalStatusFilters());
        if (request.getRecordIds()!=null && request.getRecordIds().isEmpty())
            return new ListView(headers,statusHeaders,new Page<Row>(requestedPage.getCurrent(),requestedPage.getSize(),0)
                    .setRecords(List.of()));
        if (!shares.applyRecordScope(where,access,scope))
            return new ListView(headers,statusHeaders,new Page<Row>(requestedPage.getCurrent(),requestedPage.getSize(),0)
                    .setRecords(List.of()));
        if (request.getKeyword()!=null && !request.getKeyword().isBlank())
            where.apply("LOCATE({0}, name) > 0",request.getKeyword().trim());
        if (request.getAdditionalKeyword()!=null && !request.getAdditionalKeyword().isBlank())
            where.apply("LOCATE({0}, name) > 0",request.getAdditionalKeyword().trim());
        for (var condition:conditions) {
            UnModuleField field = fields.get(condition.fieldId());
            FieldDefinition definition = fieldRules.configured(field.getFieldKey(), modules.fieldConfig(field).configuration());
            ModuleRecordFilter.apply(where, field, definition, condition, json);
        }
        if (settings.sortFieldId()==null) where.orderByDesc("id");
        else sort(where,fields.get(settings.sortFieldId()),settings.descending());
        Page<UnModuleRecord> page=records.page(requestedPage,where);
        if (page.getRecords().isEmpty())
            return new ListView(headers,statusHeaders,new Page<Row>(page.getCurrent(),page.getSize(),page.getTotal()).setRecords(List.of()));
        List<Long> recordIds=page.getRecords().stream().map(UnModuleRecord::getId).toList();
        Map<Long,Map<String,String>> approvalStatuses=new HashMap<>();
        if (!statusHeaders.isEmpty()) page.getRecords().stream().collect(Collectors.groupingBy(
                UnModuleRecord::getTenantId,Collectors.mapping(UnModuleRecord::getId,Collectors.toList())))
                .forEach((sourceTenant,ids)->approvalStatuses.putAll(
                        modules.approvalStatuses(systemId,sourceTenant,moduleId,ids)));
        List<UnModuleField> extensionFields=columns.stream().filter(field->!"name".equals(field.getFieldKey()))
                .filter(field->!ModuleRecordStorage.isMain(fieldRules.configured(field.getFieldKey(),modules.fieldConfig(field).configuration())))
                .toList();
        List<Long> extensionIds=extensionFields.stream().map(UnModuleField::getId).toList();
        Map<Long,Map<Long,Object>> values=new HashMap<>();
        if (!extensionIds.isEmpty()) for (var row:data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .in(UnModuleRecordData::getRecordId,recordIds).in(UnModuleRecordData::getFieldId,extensionIds))) {
            UnModuleField field=fields.get(row.getFieldId());
            values.computeIfAbsent(row.getRecordId(),id->new HashMap<>())
                    .put(row.getFieldId(),fieldRules.stored(row,
                            fieldRules.configured(field.getFieldKey(), modules.fieldConfig(field).configuration())));
        }
        List<Row> rows=new ArrayList<>();
        for (UnModuleRecord record:page.getRecords()) {
            PermissionPolicy rowPolicy=exporting?access.policy()
                    :permissions.teamAllows(access,record)?listFields:access.policy();
            Map<String,Object> cellValues=new LinkedHashMap<>(); Map<String,Object> display=new LinkedHashMap<>();
            for (UnModuleField field:columns) {
                if (!rowPolicy.readable(field.getFieldKey())) continue;
                FieldDefinition definition=fieldRules.configured(field.getFieldKey(),modules.fieldConfig(field).configuration());
                Object value="name".equals(field.getFieldKey())?record.getName()
                        :ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition)
                        :values.getOrDefault(record.getId(),Map.of()).get(field.getId());
                cellValues.put(field.getFieldKey(),value);
                if (value!=null) {
                    if (definition.type().options()) display.put(field.getFieldKey(),
                            fieldRules.display(systemId,record.getTenantId(),definition,value));
                    else if (Set.of(FieldType.USER, FieldType.DEPARTMENT, FieldType.DATE_TIME).contains(definition.type())) display.put(field.getFieldKey(),
                            fieldRules.present(systemId,record.getTenantId(),definition,value));
                    else if (definition.type()==FieldType.RELATION) display.put(field.getFieldKey(),
                            relationFields.display(token,systemId,context.tenantId(),field,definition,value));
                }
            }
            rows.add(new Row(record.getId(),rowPolicy.readable("name")?record.getName():null,
                    rowPolicy.readable("status")?record.getStatus():null,
                    rowPolicy.readable("status")?record.getCheckStatus():null,
                    rowPolicy.readable("ownerUserId")?record.getOwnerUserId():null,
                    record.getTenantId(),!record.getTenantId().equals(context.tenantId()),cellValues,display,
                    approvalStatuses.getOrDefault(record.getId(),Map.of())));
        }
        return new ListView(headers,statusHeaders,new Page<Row>(page.getCurrent(),page.getSize(),page.getTotal()).setRecords(rows));
    }

    /** 字段 ID 已从当前定义校验；相关子查询仅用于已授权的动态列排序。 */
    private void sort(QueryWrapper<UnModuleRecord> where,UnModuleField field,boolean descending) {
        if ("name".equals(field.getFieldKey())) where.orderBy(true,!descending,"name");
        else {
            FieldDefinition definition=fieldRules.configured(field.getFieldKey(),modules.fieldConfig(field).configuration());
            FieldType type=definition.type();
            if (Set.of(FieldType.Kind.OPTIONS,FieldType.Kind.REFERENCES).contains(type.kind()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "该字段类型暂不支持列表排序");
            if ((type.kind()==FieldType.Kind.REFERENCE || type==FieldType.SELECT)
                    && Boolean.TRUE.equals(definition.rules().get("multiple")))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "多选字段暂不支持列表排序");
            if (ModuleRecordStorage.isMain(definition)) {
                where.orderBy(true,!descending,ModuleRecordStorage.column(field.getFieldKey()));
                where.orderByDesc("id");
                return;
            }
            String indexed=com.unique.unexamine.core.field.FieldValueCodec.valueColumn(definition).sqlName();
            if (indexed==null || "value_json".equals(indexed))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, "该字段类型暂不支持列表排序");
            String expression="d."+indexed;
            String value="(SELECT "+expression+" FROM un_module_record_data d WHERE d.deleted_time IS NULL AND d.record_id = "
                    +"un_module_record.id AND d.field_id = "+field.getId()+")";
            where.orderBy(true,!descending,value);
        }
        where.orderByDesc("id");
    }

}
