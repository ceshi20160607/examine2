package com.unique.unexamine.sys.module.manage.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import java.util.*;
import java.math.BigDecimal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 模块只补充字典作用域等业务规则，类型与值校验复用 core，供保存和后续导入转化调用。 */
@Service
public class ModuleFieldService {
    private final FieldValidationService validation;
    private final DictionaryService dictionaries;
    private final ObjectMapper json;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService values;
    /** 类型目录在服务中统一提供，Controller 不另维护枚举。 */
    public record TypeView(String code,String name,boolean available) {}
    /** 注入共用校验与模块范围数据服务。 */
    public ModuleFieldService(FieldValidationService validation,DictionaryService dictionaries,ObjectMapper json,
            IUnModuleRecordService records,IUnModuleRecordDataService values) {
        this.validation=validation; this.dictionaries=dictionaries; this.json=json;
        this.records=records; this.values=values;
    }

    /** 所有固定类型可识别；依赖引用、文件、公式引擎的类型在完整实现前禁止发布使用。 */
    public List<TypeView> types() { return Arrays.stream(FieldType.values())
            .map(t->new TypeView(t.name(),t.label(),supported(t))).toList(); }

    /** 配置和发布共用同一条定义校验链，并验证绑定字典的租户边界。 */
    public FieldDefinition definition(Long systemId,Long tenantId,String code,String label,String type,
                                       boolean required,Map<String,Object> rules) {
        FieldType fieldType=FieldType.parse(type);
        if (!supported(fieldType)) throw new IllegalArgumentException(fieldType.label()+"尚未具备完整的业务执行能力");
        FieldDefinition definition=new FieldDefinition(code,label,fieldType,required,rules);
        FieldDefinition resolved=withOptions(systemId,tenantId,definition);
        validation.validateDefinition(resolved);
        return definition;
    }

    /** 当前发布字段校验；旧停用选项仅可原样保留，新增选择仍然拒绝。 */
    public void validate(Long systemId,Long tenantId,FieldDefinition field,Object value,Object previous) {
        if (field.type().options() && !validation.empty(value)) {
            if (value instanceof List<?> selected) selected.forEach(this::optionId); else optionId(value);
        }
        FieldDefinition resolved=withOptions(systemId,tenantId,field);
        if (resolved.type().options() && resolved.rules().get("options") instanceof List<?> options) {
            Set<String> retained=selected(previous);
            List<Map<String,Object>> effective=new ArrayList<>();
            for (Object item:options) {
                Map<String,Object> option=json.convertValue(item,new TypeReference<LinkedHashMap<String,Object>>(){});
                if (retained.contains(String.valueOf(option.get("id")))) option.put("enabled",true);
                effective.add(option);
            }
            Map<String,Object> rules=new LinkedHashMap<>(resolved.rules()); rules.put("options",effective);
            resolved=new FieldDefinition(field.code(),field.label(),field.type(),field.required(),rules);
        }
        validation.validateValue(resolved,value);
    }

    /** 字段草稿发布和迁移使用严格的新配置范围，不允许沿用普通编辑的旧值保留规则。 */
    public void validateDraftValue(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        validation.validateValue(withOptions(systemId,tenantId,field),value);
    }

    /** 按稳定 ID 查询当前字典名称；调用方仅在操作日志中保存当时结果。 */
    public Map<String,String> display(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (!field.type().options()) return Map.of();
        FieldDefinition resolved=withOptions(systemId,tenantId,field);
        Map<String,String> result=new LinkedHashMap<>(); Set<String> selected=selected(value);
        if (resolved.rules().get("options") instanceof List<?> options) for (Object item:options) {
            Map<?,?> option=(Map<?,?>)item; String id=String.valueOf(option.get("id"));
            if (selected.contains(id)) result.put(id,String.valueOf(option.get("label")));
        }
        return result;
    }

    /** 转化配置和执行共用值形态检查；选项共用基础项 ID，实际值仍须通过目标范围校验。 */
    public void validateMapping(FieldDefinition source,FieldDefinition target) {
        boolean textTarget=target.type().kind()==FieldType.Kind.TEXT
                && Set.of(FieldType.Kind.DATE,FieldType.Kind.DATETIME,FieldType.Kind.TIME).contains(source.type().kind());
        if (source.type().kind()!=target.type().kind() && !textTarget)
            throw new IllegalArgumentException("转化字段值形态不兼容："+source.label()+"到"+target.label());
    }

    /** 已保存快照转换为类型定义，不读取未发布的字段草稿。 */
    public FieldDefinition published(String code,Map<String,Object> published) {
        Map<String,Object> rules=json.convertValue(published.getOrDefault("config",Map.of()),new TypeReference<Map<String,Object>>(){});
        return new FieldDefinition(code,(String)published.get("fieldName"),FieldType.parse((String)published.get("fieldType")),
                Boolean.TRUE.equals(published.get("required")),rules);
    }
    /** 调用方持有模块行锁后检查唯一值，范围仅限当前系统租户的未删除记录。 */
    public void validateUnique(Long systemId,Long tenantId,Long moduleId,Long recordId,FieldDefinition field,Object value) {
        if (!Boolean.TRUE.equals(field.rules().get("unique")) || validation.empty(value)) return;
        var query=new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getTenantId,tenantId).eq(UnModuleRecord::getModuleId,moduleId)
                .isNull(UnModuleRecord::getDeletedTime).ne(recordId!=null,UnModuleRecord::getId,recordId);
        if ("name".equals(field.code())) query.eq(UnModuleRecord::getName,value);
        else {
            String encoded;
            try { encoded=json.writeValueAsString(value); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalArgumentException("字段值无法校验",ex); }
            List<Long> duplicates=values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                    .eq(UnModuleRecordData::getFieldCode,field.code()).apply("value_json = CAST({0} AS JSON)",encoded))
                    .stream().map(UnModuleRecordData::getRecordId).toList();
            if (duplicates.isEmpty()) return;
            query.in(UnModuleRecord::getId,duplicates);
        }
        if (records.count(query)>0) throw new IllegalArgumentException(field.label()+"：值已存在");
    }

    /** 保持原布局，额外补入当前可选择的发布选项。 */
    public Map<String,Object> configuration(Long systemId,Long tenantId,String code,Map<String,Object> published) {
        Map<String,Object> result=new LinkedHashMap<>(published);
        FieldDefinition field=withOptions(systemId,tenantId,published(code,published));
        result.put("config",field.rules()); return result;
    }
    /** 配置只保存稳定引用和字段自身设置，名称、基础值和父级始终从基础表解析。 */
    private FieldDefinition withOptions(Long systemId,Long tenantId,FieldDefinition field) {
        if (!field.type().options()) return field;
        if (!(field.rules().get("options") instanceof List<?> selected) || selected.isEmpty())
            throw new IllegalArgumentException(field.label()+"：选项字段至少选择一个字典基础项");
        List<Map<String,Object>> references=new ArrayList<>(); Set<Long> ids=new LinkedHashSet<>();
        for (Object raw:selected) {
            if (!(raw instanceof Map<?,?> option) || !Set.of("id","enabled","color","icon").containsAll(option.keySet()))
                throw new IllegalArgumentException("字段选项只保存字典项 ID 和字段展示设置，不复制基础名称和值");
            Long id=optionId(option.get("id"));
            if (!ids.add(id)) throw new IllegalArgumentException("字段重复选择了相同字典项");
            if (option.containsKey("enabled") && !(option.get("enabled") instanceof Boolean)) throw new IllegalArgumentException("字段选项启用标记必须是开关值");
            Map<String,Object> reference=new LinkedHashMap<>(); option.forEach((key,value)->reference.put((String)key,value));
            reference.put("id",id); references.add(reference);
        }
        var items=dictionaries.items(systemId,tenantId,ids); List<Map<String,Object>> options=new ArrayList<>();
        if (items.values().stream().map(DictionaryService.Item::dictCode).distinct().count()!=1)
            throw new IllegalArgumentException("同一字段的选项必须来自同一字典分类");
        for (var reference:references) {
            var item=items.get((Long)reference.get("id")); Map<String,Object> option=new LinkedHashMap<>(reference);
            option.put("label",item.name());
            if (item.parentId()!=null) {
                if (field.type()==FieldType.CASCADE && !ids.contains(item.parentId())) throw new IllegalArgumentException("级联选项必须包含完整父级引用");
                option.put("parentId",item.parentId());
            }
            options.add(option);
        }
        Map<String,Object> rules=new LinkedHashMap<>(field.rules()); rules.put("options",options);
        return new FieldDefinition(field.code(),field.label(),field.type(),field.required(),rules);
    }
    /** 禁止把字典基础值或字符串标识当成基础项主键保存。 */
    private Long optionId(Object value) {
        try {
            if (!(value instanceof Number)) throw new IllegalArgumentException("字典项 ID 必须是正整数");
            long id=new BigDecimal(value.toString()).longValueExact();
            if (id<1) throw new IllegalArgumentException("字典项 ID 必须是正整数");
            return id;
        } catch (ArithmeticException ex) { throw new IllegalArgumentException("字典项 ID 必须是正整数",ex); }
    }

    /** 发布前核对全部租户的现有记录，禁止缩小范围后留下无法解释的旧值。 */
    public void validateExisting(Long systemId,Long moduleId,Long fieldId,FieldDefinition definition) {
        var existing=records.list(new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getModuleId,moduleId).isNull(UnModuleRecord::getDeletedTime));
        if (existing.isEmpty()) return;
        Map<Long,Object> stored=new HashMap<>();
        if (!"name".equals(definition.code())) for (var row:values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getFieldId,fieldId).in(UnModuleRecordData::getRecordId,existing.stream().map(UnModuleRecord::getId).toList()))) {
            try { stored.put(row.getRecordId(),row.getValueJson()==null?null:json.readValue(row.getValueJson(),Object.class)); }
            catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("已有字段值无法读取",ex); }
        }
        List<Long> impacted=new ArrayList<>();
        for (var record:existing) {
            Object value="name".equals(definition.code())?record.getName():stored.get(record.getId());
            try {
                validateDraftValue(systemId,record.getTenantId(),definition,value);
                validateUnique(systemId,record.getTenantId(),moduleId,record.getId(),definition,value);
            } catch (IllegalArgumentException ex) { impacted.add(record.getId()); }
        }
        if (!impacted.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "字段变更影响 "+impacted.size()+" 条现有记录，须先查看字段迁移预览并确定数据迁移");
    }
    private Set<String> selected(Object value) {
        if (value==null) return Set.of(); Set<String> ids=new HashSet<>();
        if (value instanceof List<?> list) list.forEach(v->ids.add(String.valueOf(v))); else ids.add(String.valueOf(value)); return ids;
    }
    private boolean supported(FieldType type) {
        return switch (type.kind()) {
            case TEXT -> !Set.of(FieldType.SECRET,FieldType.RICH_TEXT,FieldType.AI).contains(type);
            case NUMBER,DATE,DATETIME,DATE_RANGE,TIME,TIME_RANGE,BOOLEAN,OPTION,OPTIONS,OBJECT -> true;
            default -> false;
        };
    }
}
