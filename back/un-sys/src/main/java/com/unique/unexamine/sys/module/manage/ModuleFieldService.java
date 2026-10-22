package com.unique.unexamine.sys.module.manage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import java.util.*;
import org.springframework.stereotype.Service;

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
        if (rules.containsKey("dictionaryId") && rules.containsKey("options")) throw new IllegalArgumentException("字典与内嵌选项不能同时配置");
        FieldDefinition definition=new FieldDefinition(code,label,fieldType,required,rules);
        validation.validateDefinition(definition);
        FieldDefinition resolved=withOptions(systemId,tenantId,definition);
        validation.validateDefinition(resolved);
        return resolved;
    }

    /** 当前发布字段校验；旧停用选项仅可原样保留，新增选择仍然拒绝。 */
    public void validate(Long systemId,Long tenantId,FieldDefinition field,Object value,Object previous) {
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

    /** 转化配置和执行共用值形态检查；不同字典不能仅凭偶然相同的选项 ID 直接转化。 */
    public void validateMapping(FieldDefinition source,FieldDefinition target) {
        boolean textTarget=target.type().kind()==FieldType.Kind.TEXT
                && Set.of(FieldType.Kind.DATE,FieldType.Kind.DATETIME,FieldType.Kind.TIME).contains(source.type().kind());
        if (source.type().kind()!=target.type().kind() && !textTarget)
            throw new IllegalArgumentException("转化字段值形态不兼容："+source.label()+"到"+target.label());
        if (source.type().options() && target.type().options()
                && !java.util.Objects.equals(source.rules().get("dictionaryId"),target.rules().get("dictionaryId")))
            throw new IllegalArgumentException("不同字典之间的转化需要明确的选项映射");
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
    private FieldDefinition withOptions(Long systemId,Long tenantId,FieldDefinition field) {
        if (!(field.rules().get("dictionaryId") instanceof Number id)) return field;
        var dictionary=dictionaries.published(systemId,tenantId,id.longValue());
        if (field.type()==FieldType.CASCADE && !"CASCADE".equals(dictionary.type())) throw new IllegalArgumentException("级联字段须绑定级联字典");
        Map<String,Object> rules=new LinkedHashMap<>(field.rules()); rules.put("options",dictionary.options());
        return new FieldDefinition(field.code(),field.label(),field.type(),field.required(),rules);
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
