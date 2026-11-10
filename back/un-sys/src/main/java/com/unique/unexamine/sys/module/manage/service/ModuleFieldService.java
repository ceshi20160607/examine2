package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.sys.base.entity.UnSysDepartment;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysDepartmentService;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto.DepartmentView;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto.MemberView;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto.TypeView;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
import java.math.BigDecimal;
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
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;
    private final IUnSysDepartmentService departments;

    /** 注入共用校验与模块范围数据服务。 */
    public ModuleFieldService(FieldValidationService validation,DictionaryService dictionaries,ObjectMapper json,
            IUnModuleRecordService records,IUnModuleRecordDataService values,
            IUnSysMemberService members,IUnSysTenantMemberService tenantMembers,
            IUnSysDepartmentService departments) {
        this.validation=validation; this.dictionaries=dictionaries; this.json=json;
        this.records=records; this.values=values; this.members=members; this.tenantMembers=tenantMembers;
        this.departments=departments;
    }

    /** 所有固定类型可识别；依赖引用、文件、公式引擎的类型在完整实现前禁止启用使用。 */
    public List<TypeView> types() { return Arrays.stream(FieldType.values())
            .map(t->new TypeView(t.code(),t.label(),t.valueColumn().sqlName(),supported(t))).toList(); }

    /** 配置保存共用同一条定义校验链，并验证绑定字典的租户边界。 */
    public FieldDefinition definition(Long systemId,Long tenantId,String code,String label,Integer type,
                                       boolean required,Map<String,Object> rules) {
        FieldType fieldType;
        try {
            fieldType=FieldType.parse(type);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        if (!supported(fieldType)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,fieldType.label()+"尚未具备完整的业务执行能力");
        Map<String,Object> effectiveRules=new LinkedHashMap<>(rules==null?Map.of():rules);
        // 自动编号的序列分配天然要求唯一，不能让表单配置把这一约束关闭。
        if (fieldType==FieldType.AUTO_NUMBER) effectiveRules.put("unique",true);
        FieldDefinition definition;
        try {
            definition=FieldDefinitionParser.parse(code,label,fieldType.code(),required,effectiveRules);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        FieldDefinition resolved=withOptions(systemId,tenantId,definition);
        try {
            validation.validateDefinition(resolved);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
        return definition;
    }

    /** 持久化列是字段规则的唯一来源；结构化规则统一放在字段的 config_json 中。 */
    public Map<String,Object> rules(UnModuleField field) {
        Map<String,Object> rules=new LinkedHashMap<>();
        rules.put("displayStyle",field.getDisplayStyle());
        put(rules,"hint",field.getHintText()); put(rules,"placeholder",field.getPlaceholderText());
        put(rules,"defaultValue",field.getDefaultValue());
        put(rules,"minLength",field.getMinLength()); put(rules,"maxLength",field.getMaxLength());
        put(rules,"pattern",field.getInputPattern()); put(rules,"min",field.getNumberMin());
        put(rules,"max",field.getNumberMax()); put(rules,"precision",field.getNumberPrecision());
        put(rules,"scale",field.getNumberScale()); put(rules,"unitDictCode",field.getUnitDictCode());
        put(rules,"currencyCode",field.getCurrencyCode()); put(rules,"minItems",field.getMinItems());
        put(rules,"maxItems",field.getMaxItems()); put(rules,"relationModuleId",field.getRelationModuleId());
        put(rules,"dictCode",field.getDictCode());
        if (field.getConfigJson()!=null) {
            Object structured=readJson(field.getConfigJson());
            if (structured instanceof Map<?,?> config) {
                if (config.get("options") instanceof List<?> items) rules.put("options",items);
                if (config.get("optionDisplayRules")!=null) rules.put("optionDisplayRules",config.get("optionDisplayRules"));
                if (config.get("autoNumberConfig")!=null) rules.put("autoNumberConfig",config.get("autoNumberConfig"));
                if (config.get("formulaConfig")!=null) rules.put("formulaConfig",config.get("formulaConfig"));
            }
        }
        if (Boolean.TRUE.equals(field.getUniqueFlag())) rules.put("unique",true);
        if (Boolean.TRUE.equals(field.getRoundingFlag())) rules.put("round",true);
        if (Boolean.TRUE.equals(field.getMultipleFlag())) rules.put("multiple",true);
        return rules;
    }

    /** 保存前已通过共用规则校验；普通属性写明确列，结构化规则统一写字段 config_json。 */
    public void writeRules(UnModuleField field,FieldDefinition definition) {
        Map<String,Object> rules=definition.rules();
        field.setUniqueFlag(Boolean.TRUE.equals(rules.get("unique")));
        field.setHintText((String)rules.get("hint")); field.setPlaceholderText((String)rules.get("placeholder"));
        field.setDefaultValue(rules.get("defaultValue")==null?null:String.valueOf(rules.get("defaultValue")));
        field.setDisplayStyle((String)rules.getOrDefault("displayStyle",definition.type().defaultStyle()));
        field.setMinLength(integer(rules,"minLength")); field.setMaxLength(integer(rules,"maxLength"));
        field.setInputPattern((String)rules.get("pattern"));
        field.setNumberMin(decimal(rules,"min")); field.setNumberMax(decimal(rules,"max"));
        field.setNumberPrecision(tiny(rules,"precision")); field.setNumberScale(tiny(rules,"scale"));
        field.setRoundingFlag(Boolean.TRUE.equals(rules.get("round")));
        field.setUnitDictCode((String)rules.get("unitDictCode"));
        field.setCurrencyCode((String)rules.get("currencyCode"));
        field.setMinItems(integer(rules,"minItems")); field.setMaxItems(integer(rules,"maxItems"));
        field.setMultipleFlag(Boolean.TRUE.equals(rules.get("multiple")));
        field.setDictCode((String)rules.get("dictCode"));
        Map<String,Object> structuredConfig=new LinkedHashMap<>();
        if (rules.containsKey("options")) structuredConfig.put("options",rules.get("options"));
        if (rules.containsKey("optionDisplayRules")) structuredConfig.put("optionDisplayRules",rules.get("optionDisplayRules"));
        if (rules.containsKey("autoNumberConfig")) structuredConfig.put("autoNumberConfig",rules.get("autoNumberConfig"));
        if (rules.containsKey("formulaConfig")) structuredConfig.put("formulaConfig",rules.get("formulaConfig"));
        field.setConfigJson(structuredConfig.isEmpty()?null:writeJson(structuredConfig));
        field.setRelationModuleId(longValue(rules,"relationModuleId"));
    }

    private void put(Map<String,Object> target,String key,Object value) { if (value!=null) target.put(key,value); }
    private Integer integer(Map<String,Object> rules,String key) {
        return rules.get(key) instanceof Number value?value.intValue():null;
    }
    private Byte tiny(Map<String,Object> rules,String key) {
        return rules.get(key) instanceof Number value?value.byteValue():null;
    }
    private Long longValue(Map<String,Object> rules,String key) {
        return rules.get(key) instanceof Number value?value.longValue():null;
    }
    private BigDecimal decimal(Map<String,Object> rules,String key) {
        return rules.get(key) instanceof Number value?new BigDecimal(value.toString()):null;
    }
    private Object readJson(String source) {
        try { return json.readValue(source,Object.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"字段配置损坏",ex); }
    }
    private String writeJson(Object value) {
        try { return json.writeValueAsString(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段配置无法保存",ex); }
    }

    /** 当前字段校验；旧停用选项仅可原样保留，新增选择仍然拒绝。 */
    public void validate(Long systemId,Long tenantId,FieldDefinition field,Object value,Object previous) {
        validate(systemId,tenantId,field,value,previous,false);
    }

    /** 草稿暂免必填，但已有输入仍按类型、选项和成员范围校验。 */
    public void validate(Long systemId,Long tenantId,FieldDefinition field,Object value,Object previous,boolean draft) {
        if (field.type()==FieldType.CASCADE && !validation.empty(value)) {
            if (value instanceof Map<?,?> object && object.get("path") instanceof List<?> path)
                path.forEach(this::optionId);
            else if (value instanceof List<?> selected) selected.forEach(this::optionId);
            else optionId(value);
        }
        FieldDefinition resolved=withOptions(systemId,tenantId,field);
        if (resolved.type().options() && resolved.rules().get("options") instanceof List<?> options) {
            Set<String> retained=selected(previous);
            List<Map<String,Object>> effective=new ArrayList<>();
            for (Object item:options) {
                Map<String,Object> option=json.convertValue(item,new TypeReference<LinkedHashMap<String,Object>>(){});
                String key=field.type()==FieldType.SELECT
                        ?String.valueOf(option.get("value")):String.valueOf(option.get("id"));
                if (retained.contains(key)) option.put("enabled",true);
                effective.add(option);
            }
            Map<String,Object> rules=new LinkedHashMap<>(resolved.rules()); rules.put("options",effective);
            resolved=new FieldDefinition(field.code(),field.label(),field.type(),field.required(),rules);
        }
        validateCoreValue(draft?new FieldDefinition(resolved.code(),resolved.label(),resolved.type(),false,resolved.rules()):resolved,value);
        validateMember(systemId,tenantId,field,value);
    }

    /** 数字按配置在写入前统一四舍五入，校验、唯一检查和实际保存使用同一结果。 */
    public Object normalize(FieldDefinition field,Object value) {
        if (value instanceof Number && field.type()==FieldType.NUMBER
                && Boolean.TRUE.equals(field.rules().get("round"))
                && field.rules().get("scale") instanceof Number scale)
            return new BigDecimal(value.toString()).setScale(scale.intValue(),java.math.RoundingMode.HALF_UP);
        return value;
    }

    /** 新建记录使用字段配置中的默认文本，并由 core 按字段类型解析。 */
    public Object defaultValue(FieldDefinition field) {
        return validation.defaultValue(field);
    }

    /** 外部导入文本使用与核心字段框架一致的类型解析，再进入正常保存校验链。 */
    public Object parseImportValue(FieldDefinition field, String raw) {
        return validation.parseTextValue(field, raw);
    }

    /** 字段配置保存和迁移使用严格的新配置范围，不允许沿用普通编辑的旧值保留规则。 */
    public void validateConfiguredValue(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        validateCoreValue(withOptions(systemId,tenantId,field),value);
        validateMember(systemId,tenantId,field,value);
    }

    /** core 的类型校验面向通用解析场景使用参数异常；进入模块业务边界后统一转换为项目业务异常。 */
    private void validateCoreValue(FieldDefinition field,Object value) {
        try {
            validation.validateValue(field,value);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,ex.getMessage(),ex);
        }
    }

    /** 校验自动编号和公式引擎生成的结果，禁止外部请求绕过计算字段规则。 */
    public void validateComputedValue(FieldDefinition field,Object value) {
        try {
            validation.validateComputedValue(field,value);
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID, ex.getMessage(), ex);
        }
    }

    /** 人员和部门字段必须引用当前系统、当前数据租户的有效主数据。 */
    private void validateMember(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (value==null || !Set.of(FieldType.USER,FieldType.DEPARTMENT).contains(field.type())) return;
        List<?> ids=value instanceof List<?> list?list:List.of(value);
        for (Object raw:ids) {
            Long id=new BigDecimal(raw.toString()).longValueExact();
            if (field.type()==FieldType.USER) {
                UnSysMember member=members.getById(id);
                if (member==null || !systemId.equals(member.getSystemId()) || !"ACTIVE".equals(member.getStatus())
                        || tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                        .eq(UnSysTenantMember::getSystemId,systemId).eq(UnSysTenantMember::getTenantId,tenantId)
                        .eq(UnSysTenantMember::getMemberId,id).eq(UnSysTenantMember::getStatus,"ACTIVE"))==0)
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,field.label()+"：所选人员不是当前租户的有效成员");
            } else {
                UnSysDepartment department=departments.queryById(id);
                if (department==null || !systemId.equals(department.getSystemId())
                        || !tenantId.equals(department.getTenantId()) || !"ACTIVE".equals(department.getStatus()))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,field.label()+"：所选部门不是当前租户的有效部门");
            }
        }
    }

    /** 展示阶段按保存的成员 ID 查当前资料，不把姓名快照混入业务值。 */
    public Object present(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()==FieldType.DATE_TIME && value instanceof String raw
                && "DATE".equals(field.rules().get("displayStyle")))
            return raw.length()>=10?raw.substring(0,10):raw;
        if (value == null) return null;
        if (field.type()==FieldType.USER) {
            if (value instanceof List<?> list) return list.stream().map(id->memberView(systemId,tenantId,id)).toList();
            return memberView(systemId,tenantId,value);
        }
        if (field.type()==FieldType.DEPARTMENT) {
            if (value instanceof List<?> list) return list.stream().map(id->departmentView(systemId,tenantId,id)).toList();
            return departmentView(systemId,tenantId,value);
        }
        return value;
    }

    private MemberView memberView(Long systemId,Long tenantId,Object raw) {
        Long id=new BigDecimal(raw.toString()).longValueExact();
        UnSysMember member=members.getById(id);
        if (member==null || !systemId.equals(member.getSystemId()) || !"ACTIVE".equals(member.getStatus())) return null;
        UnSysTenantMember link=tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId,systemId).eq(UnSysTenantMember::getTenantId,tenantId)
                .eq(UnSysTenantMember::getMemberId,id).eq(UnSysTenantMember::getStatus,"ACTIVE"),false);
        if (link==null) return null;
        return new MemberView(id,member.getAccountId(),member.getDisplayName(),member.getEmployeeNo(),link.getDepartmentId());
    }

    /** 部门展示只读取当前系统租户的有效组织资料，停用或越界值不回显名称。 */
    private DepartmentView departmentView(Long systemId,Long tenantId,Object raw) {
        Long id=new BigDecimal(raw.toString()).longValueExact();
        UnSysDepartment department=departments.queryById(id);
        if (department==null || !systemId.equals(department.getSystemId())
                || !tenantId.equals(department.getTenantId()) || !"ACTIVE".equals(department.getStatus())) return null;
        return new DepartmentView(id,department.getParentId(),department.getCode(),department.getName());
    }

    /** 按稳定 ID 查询当前字典名称；调用方仅在操作日志中保存当时结果。 */
    public Map<String,String> display(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (!field.type().options()) return Map.of();
        FieldDefinition resolved=withOptions(systemId,tenantId,field);
        Map<String,String> result=new LinkedHashMap<>(); Set<String> selected=selected(value);
        if (resolved.rules().get("options") instanceof List<?> options) for (Object item:options) {
            Map<?,?> option=(Map<?,?>)item;
            String key=field.type()==FieldType.SELECT?String.valueOf(option.get("value")):String.valueOf(option.get("id"));
            if (selected.contains(key)) result.put(key,String.valueOf(option.get("label")));
        }
        return result;
    }

    /** 写入已校验值，只占用与字段类型对应的物理值列。 */
    public void store(UnModuleRecordData row, FieldDefinition definition, Object value) {
        var cells=FieldValueCodec.encode(definition,value,json);
        row.setValueVarchar(cells.varchar()); row.setValueText(cells.text()); row.setValueNumber(cells.number());
        row.setValueTime(cells.time()); row.setValueJson(cells.json());
    }

    /** 按保存的字段类型还原原始值，不能从 SQL 列内容猜类型。 */
    public Object stored(UnModuleRecordData row,FieldType type) {
        if (row==null) return null;
        return decodeStored(type,new FieldValueCodec.Cells(row.getValueVarchar(),row.getValueText(),
                row.getValueNumber(),row.getValueTime(),row.getValueJson()));
    }

    /** 日期样式的业务值按配置还原，数据库时间列始终保存完整 DATETIME。 */
    public Object stored(UnModuleRecordData row,FieldDefinition field) {
        if (row==null) return null;
        Object value=decodeStored(field,new FieldValueCodec.Cells(row.getValueVarchar(),row.getValueText(),
                row.getValueNumber(),row.getValueTime(),row.getValueJson()));
        if (field.type()!=FieldType.DATE_TIME) return value;
        return present(null,null,field,value);
    }

    /** 扩展值损坏属于持久化冲突，统一转换为业务异常，不能把 JSON 解析栈泄露为接口 500。 */
    private Object decodeStored(Object definition,FieldValueCodec.Cells cells) {
        try {
            return definition instanceof FieldDefinition field
                    ? FieldValueCodec.decode(field,cells,json)
                    : FieldValueCodec.decode((FieldType)definition,cells,json);
        } catch (RuntimeException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"业务字段存储值损坏",ex);
        }
    }

    /** 配置即时生效，按当前保存类型解释已验证的数据。 */
    public FieldType storedType(UnModuleField field) {
        try {
            return FieldType.parse(field.getFieldType());
        } catch (IllegalArgumentException ex) {
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT, "字段类型配置损坏", ex);
        }
    }

    /** 字段只读是服务端业务约束；普通表单、导入和自定义动作都不能绕过。 */
    public void requireEditable(UnModuleField field) {
        if (field != null && Boolean.TRUE.equals(field.getReadonlyFlag())) {
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH, "字段“" + field.getFieldName() + "”已设为只读");
        }
    }

    /** 转化配置和执行共用值形态检查；选项共用基础项 ID，实际值仍须通过目标范围校验。 */
    public void validateMapping(FieldDefinition source,FieldDefinition target) {
        boolean textTarget=target.type().kind()==FieldType.Kind.TEXT
                && source.type().kind()==FieldType.Kind.DATE_TIME;
        if (source.type().kind()!=target.type().kind() && !textTarget)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"转化字段值形态不兼容："+source.label()+"到"+target.label());
    }

    /** 当前保存的配置转换为类型定义。 */
    public FieldDefinition configured(String code,Map<String,Object> configured) {
        return FieldDefinitionParser.parse(code,configured);
    }

    /** 从持久化字段实体读取公共定义，供关联、列表和记录服务复用同一解析入口。 */
    public FieldDefinition configured(UnModuleField field) {
        return FieldDefinitionParser.parse(field.getFieldKey(),field.getFieldName(),field.getFieldType(),
                Boolean.TRUE.equals(field.getRequiredFlag()),rules(field));
    }

    /** 在指定系统租户范围内解析字典和选项展示资料，供迁移及详情处理复用。 */
    public FieldDefinition resolved(Long systemId,Long tenantId,FieldDefinition field) {
        return withOptions(systemId,tenantId,field);
    }

    /** 从持久化字段读取包含当前字典选项名称和值的运行定义。 */
    public FieldDefinition resolved(Long systemId,Long tenantId,UnModuleField field) {
        return resolved(systemId,tenantId,configured(field));
    }
    /** 调用方持有模块行锁后检查唯一值，范围仅限当前系统租户的未删除记录。 */
    public void validateUnique(Long systemId,Long tenantId,Long moduleId,Long recordId,FieldDefinition field,Object value) {
        if (!Boolean.TRUE.equals(field.rules().get("unique")) || validation.empty(value)) return;
        var query=new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getTenantId,tenantId).eq(UnModuleRecord::getModuleId,moduleId)
                .isNull(UnModuleRecord::getDeletedTime).ne(recordId!=null,UnModuleRecord::getId,recordId);
        if ("name".equals(field.code())) query.eq(UnModuleRecord::getName,value);
        else if (ModuleRecordStorage.isMain(field)) query.apply("`"+ModuleRecordStorage.column(field.code())+"` = {0}",
                ModuleRecordStorage.sqlValue(field,value));
        else {
            var cells=FieldValueCodec.encode(field,value,json);
            var duplicateQuery=new LambdaQueryWrapper<UnModuleRecordData>().eq(UnModuleRecordData::getFieldKey,field.code());
            if (cells.number()!=null) duplicateQuery.eq(UnModuleRecordData::getValueNumber,cells.number());
            else if (cells.time()!=null) duplicateQuery.eq(UnModuleRecordData::getValueTime,cells.time());
            else if (cells.varchar()!=null) duplicateQuery.eq(UnModuleRecordData::getValueVarchar,cells.varchar());
            else if (cells.text()!=null) duplicateQuery.apply("HEX(value_text) = HEX({0})",cells.text());
            else duplicateQuery.apply("value_json = CAST({0} AS JSON)",cells.json());
            List<Long> duplicates=values.list(duplicateQuery)
                    .stream().map(UnModuleRecordData::getRecordId).toList();
            if (duplicates.isEmpty()) return;
            query.in(UnModuleRecord::getId,duplicates);
        }
        if (records.count(query)>0) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,field.label()+"：值已存在");
    }

    /** 保持原布局，额外补入当前可选择的选项。 */
    public Map<String,Object> configuration(Long systemId,Long tenantId,String code,Map<String,Object> configured) {
        Map<String,Object> result=new LinkedHashMap<>(configured);
        FieldDefinition field=withOptions(systemId,tenantId,configured(code,configured));
        result.put("config",field.rules()); return result;
    }
    /** 配置只保存稳定引用和字段自身设置，名称、基础值和父级始终从基础表解析。 */
    private FieldDefinition withOptions(Long systemId,Long tenantId,FieldDefinition field) {
        if (!field.type().options()) return field;
        if (!(field.rules().get("options") instanceof List<?> selected) || selected.isEmpty())
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,field.label()+"：选项字段至少选择一个字典基础项");
        List<Map<String,Object>> references=new ArrayList<>(); Set<Long> ids=new LinkedHashSet<>();
        for (Object raw:selected) {
            if (!(raw instanceof Map<?,?> option) || !Set.of("id","enabled","color","icon").containsAll(option.keySet()))
                throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段选项只保存字典项 ID 和字段展示设置，不复制基础名称和值");
            Long id=optionId(option.get("id"));
            if (!ids.add(id)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段重复选择了相同字典项");
            if (option.containsKey("enabled") && !(option.get("enabled") instanceof Boolean)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字段选项启用标记必须是开关值");
            Map<String,Object> reference=new LinkedHashMap<>(); option.forEach((key,value)->reference.put((String)key,value));
            reference.put("id",id); references.add(reference);
        }
        var items=dictionaries.items(systemId,tenantId,ids); List<Map<String,Object>> options=new ArrayList<>();
        if (items.values().stream().map(DictionaryDto.Item::dictCode).distinct().count()!=1)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"同一字段的选项必须来自同一字典分类");
        for (var reference:references) {
            var item=items.get((Long)reference.get("id")); Map<String,Object> option=new LinkedHashMap<>(reference);
            option.put("label",item.name());
            option.put("value",item.value());
            if (item.parentId()!=null) {
                if (field.type()==FieldType.CASCADE && !ids.contains(item.parentId())) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"级联选项必须包含完整父级引用");
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
            if (!(value instanceof Number)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字典项 ID 必须是正整数");
            long id=new BigDecimal(value.toString()).longValueExact();
            if (id<1) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字典项 ID 必须是正整数");
            return id;
        } catch (ArithmeticException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"字典项 ID 必须是正整数",ex); }
    }

    /** 保存前核对全部租户的现有记录，禁止缩小范围后留下无法解释的旧值。 */
    public void validateExisting(Long systemId,Long moduleId,UnModuleField field,FieldDefinition definition) {
        var existing=records.list(new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getModuleId,moduleId).isNull(UnModuleRecord::getDeletedTime));
        if (existing.isEmpty()) return;
        Map<Long,Object> stored=new HashMap<>();
        if (!"name".equals(definition.code()) && !ModuleRecordStorage.isMain(definition)) for (var row:values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getFieldId,field.getId()).in(UnModuleRecordData::getRecordId,existing.stream().map(UnModuleRecord::getId).toList()))) {
            stored.put(row.getRecordId(),stored(row,storedType(field)));
        }
        List<Long> impacted=new ArrayList<>();
        for (var record:existing) {
            Object value="name".equals(definition.code())?record.getName()
                    :ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition):stored.get(record.getId());
            if (definition.type()==FieldType.DATE_TIME)
                value=present(systemId,record.getTenantId(),definition,value);
            try {
                if (definition.type().computed()) validation.validateComputedValue(definition,value);
                else validateConfiguredValue(systemId,record.getTenantId(),definition,value);
                validateUnique(systemId,record.getTenantId(),moduleId,record.getId(),definition,value);
            } catch (RuntimeException ex) { impacted.add(record.getId()); }
        }
        if (!impacted.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                "字段变更影响 "+impacted.size()+" 条现有记录，须先查看字段迁移预览并确定数据迁移");
    }
    private Set<String> selected(Object value) {
        if (value==null) return Set.of(); Set<String> ids=new HashSet<>();
        if (value instanceof Map<?,?> object && object.get("path") instanceof List<?> path) {
            path.forEach(v->ids.add(String.valueOf(v))); return ids;
        }
        if (value instanceof List<?> list) list.forEach(v->ids.add(String.valueOf(v))); else ids.add(String.valueOf(value)); return ids;
    }
    private boolean supported(FieldType type) {
        return switch (type.kind()) {
            case TEXT,NUMBER,DATE_TIME,OPTION,OPTIONS -> true;
            case REFERENCES -> type==FieldType.ATTACHMENT;
            case REFERENCE -> type==FieldType.USER || type==FieldType.DEPARTMENT || type==FieldType.RELATION;
            case COMPUTED -> type==FieldType.AUTO_NUMBER || type==FieldType.FORMULA;
            default -> false;
        };
    }
}
