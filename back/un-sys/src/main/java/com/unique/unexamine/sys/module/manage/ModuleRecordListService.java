package com.unique.unexamine.sys.module.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
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

    /** 临时筛选与已保存场景同时生效，分页范围由服务端限定。 */
    public record Query(Long sceneId,String search,List<ModuleSceneService.Condition> filters,
                        Long sortFieldId,Boolean descending,List<Long> columnFieldIds,long page,long size) {}
    /** 列标识、字段名称和类型来自当前发布定义。 */
    public record Column(Long fieldId,String code,String name,String type,Integer widthPx,String fixedSide) {}
    /** 列表行含基础记录及当前有权读取的自定义字段值。 */
    public record Row(Long id,String name,String status,Long ownerAccountId,
                      Map<String,Object> values,Map<String,Map<String,String>> valueDisplay) {}
    public record Result(long total,long current,long size,List<Column> columns,List<Row> records) {}

    /** 注入生成的基础 Service；动态查询仍复用基础持久化入口。 */
    public ModuleRecordListService(ModulePermissionService permissions,ModuleConfigService modules,
            ModuleSceneService scenes,ModuleColumnService columnPreferences,ModuleFieldService fieldRules,IUnModuleRecordService records,
            IUnModuleRecordDataService data,ObjectMapper json) {
        this.permissions=permissions; this.modules=modules; this.scenes=scenes;
        this.columnPreferences=columnPreferences; this.fieldRules=fieldRules;
        this.records=records; this.data=data; this.json=json;
    }

    /** 列表场景与请求条件求交集，权限在条件、排序和返回列三处分别执行。 */
    public Result query(String token,Long systemId,Long tenantId,Long moduleId,Query request) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"LIST");
        var context=access.context(); modules.publishedModule(systemId,moduleId,context.tenantId());
        if (request==null || request.page()<1 || request.size()<1 || request.size()>100)
            throw new IllegalArgumentException("分页范围无效");
        if (request.search()!=null && request.search().length()>100)
            throw new IllegalArgumentException("搜索文本过长");
        var saved=scenes.resolve(moduleId,request.sceneId(),access);
        List<ModuleSceneService.Condition> conditions=new ArrayList<>(saved.filters());
        if (request.filters()!=null) conditions.addAll(request.filters());
        var settings=new ModuleSceneService.Settings(conditions,
                request.columnFieldIds()==null?saved.columnFieldIds():request.columnFieldIds(),
                request.sortFieldId()==null?saved.sortFieldId():request.sortFieldId(),
                request.descending()==null?saved.descending():request.descending());
        scenes.check(systemId,context.tenantId(),moduleId,settings,access);
        List<UnModuleField> published=modules.publishedFields(moduleId);
        Map<Long,UnModuleField> fields=published.stream().collect(Collectors.toMap(UnModuleField::getId,field->field));
        Map<Long,ModuleColumnService.Preference> personal=columnPreferences.effective(moduleId,access);
        List<UnModuleField> selected=settings.columnFieldIds().isEmpty()?published:settings.columnFieldIds()
                .stream().map(fields::get).toList();
        Map<Long,Integer> defaultPositions=new HashMap<>();
        for (int position=0;position<selected.size();position++) defaultPositions.put(selected.get(position).getId(),position);
        List<UnModuleField> columns=selected.stream()
                .filter(field->access.policy().readable(field.getFieldCode()))
                .filter(field->!personal.containsKey(field.getId()) || !personal.get(field.getId()).hidden())
                .sorted(Comparator.comparingInt(field->personal.containsKey(field.getId())
                        ?personal.get(field.getId()).sortNo():1000+defaultPositions.get(field.getId())))
                .toList();
        List<Column> headers=columns.stream().map(field->new Column(field.getId(),field.getFieldCode(),
                field.getFieldName(),field.getFieldType(),personal.containsKey(field.getId())
                ?personal.get(field.getId()).widthPx():null,personal.containsKey(field.getId())
                ?personal.get(field.getId()).fixedSide():null)).toList();
        if (!access.policy().allData() && access.policy().ownerAccountIds().isEmpty())
            return new Result(0,request.page(),request.size(),headers,List.of());
        QueryWrapper<UnModuleRecord> where=new QueryWrapper<UnModuleRecord>()
                .eq("system_id",systemId).eq("tenant_id",context.tenantId()).eq("module_id",moduleId)
                .isNull("deleted_time");
        if (!access.policy().allData()) where.in("owner_user_id",access.policy().ownerAccountIds());
        if (request.search()!=null && !request.search().isBlank())
            where.apply("LOCATE({0}, name) > 0",request.search().trim());
        for (var condition:conditions) filter(where,fields.get(condition.fieldId()),condition);
        if (settings.sortFieldId()==null) where.orderByDesc("id");
        else sort(where,fields.get(settings.sortFieldId()),settings.descending());
        Page<UnModuleRecord> page=records.page(Page.of(request.page(),request.size()),where);
        if (page.getRecords().isEmpty()) return new Result(page.getTotal(),page.getCurrent(),page.getSize(),headers,List.of());
        List<Long> recordIds=page.getRecords().stream().map(UnModuleRecord::getId).toList();
        List<Long> extensionIds=columns.stream().filter(field->!"name".equals(field.getFieldCode()))
                .map(UnModuleField::getId).toList();
        Map<Long,Map<Long,Object>> values=new HashMap<>();
        if (!extensionIds.isEmpty()) for (var row:data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .in(UnModuleRecordData::getRecordId,recordIds).in(UnModuleRecordData::getFieldId,extensionIds))) {
            values.computeIfAbsent(row.getRecordId(),id->new HashMap<>()).put(row.getFieldId(),read(row.getValueJson()));
        }
        List<Row> rows=new ArrayList<>();
        for (UnModuleRecord record:page.getRecords()) {
            Map<String,Object> cellValues=new LinkedHashMap<>(); Map<String,Map<String,String>> display=new LinkedHashMap<>();
            for (UnModuleField field:columns) {
                Object value="name".equals(field.getFieldCode())?record.getName()
                        :values.getOrDefault(record.getId(),Map.of()).get(field.getId());
                cellValues.put(field.getFieldCode(),value);
                if (value!=null) {
                    FieldDefinition definition=fieldRules.published(field.getFieldCode(),modules.fieldConfig(field).configuration());
                    if (definition.type().options()) display.put(field.getFieldCode(),
                            fieldRules.display(systemId,context.tenantId(),definition,value));
                }
            }
            rows.add(new Row(record.getId(),access.policy().readable("name")?record.getName():null,
                    record.getStatus(),record.getOwnerUserId(),cellValues,display));
        }
        return new Result(page.getTotal(),page.getCurrent(),page.getSize(),headers,rows);
    }

    /** 动态字段条件通过相关子查询在数据库端参与分页，避免先分页后筛选。 */
    private void filter(QueryWrapper<UnModuleRecord> where,UnModuleField field,ModuleSceneService.Condition condition) {
        String code=field.getFieldCode();
        if ("name".equals(code)) {
            if ("EQ".equals(condition.operator())) where.eq("name",condition.value());
            else where.apply("LOCATE({0}, name) > 0",condition.value());
            return;
        }
        if ("EQ".equals(condition.operator())) where.apply(
                "EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.record_id = un_module_record.id "
                        +"AND d.field_id = {0} AND JSON_CONTAINS(d.value_json, CAST({1} AS JSON)))",
                field.getId(),write(condition.value()));
        else where.apply("EXISTS (SELECT 1 FROM un_module_record_data d WHERE d.record_id = un_module_record.id "
                +"AND d.field_id = {0} AND LOCATE({1}, JSON_UNQUOTE(d.value_json)) > 0)",
                field.getId(),condition.value());
    }

    /** 字段 ID 已从发布定义校验；相关子查询仅用于已授权的动态列排序。 */
    private void sort(QueryWrapper<UnModuleRecord> where,UnModuleField field,boolean descending) {
        if ("name".equals(field.getFieldCode())) where.orderBy(true,!descending,"name");
        else {
            FieldType type=FieldType.parse(field.getFieldType());
            if (Set.of(FieldType.Kind.OPTIONS,FieldType.Kind.OBJECT,FieldType.Kind.ROWS,
                    FieldType.Kind.REFERENCES).contains(type.kind()))
                throw new IllegalArgumentException("该字段类型暂不支持列表排序");
            String value="(SELECT JSON_UNQUOTE(d.value_json) FROM un_module_record_data d WHERE d.record_id = "
                    +"un_module_record.id AND d.field_id = "+field.getId()+")";
            if (type.kind()==FieldType.Kind.NUMBER) value="CAST("+value+" AS DECIMAL(30,10))";
            where.orderBy(true,!descending,value);
        }
        where.orderByDesc("id");
    }

    private Object read(String source) {
        if (source==null) return null;
        try { return json.readValue(source,Object.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("列表字段值损坏",ex); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("筛选值无法保存",ex); }
    }
}
