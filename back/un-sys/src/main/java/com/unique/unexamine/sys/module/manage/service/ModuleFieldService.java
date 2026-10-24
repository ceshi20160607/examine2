package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.*;
import com.unique.unexamine.sys.base.entity.UnSysMember;
import com.unique.unexamine.sys.base.entity.UnSysTenantMember;
import com.unique.unexamine.sys.base.service.IUnSysMemberService;
import com.unique.unexamine.sys.base.service.IUnSysTenantMemberService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.dto.DictionaryDto;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto.MemberView;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldDto.TypeView;
import java.math.BigDecimal;
import java.util.*;
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
    private final IUnSysMemberService members;
    private final IUnSysTenantMemberService tenantMembers;

    /** 注入共用校验与模块范围数据服务。 */
    public ModuleFieldService(FieldValidationService validation,DictionaryService dictionaries,ObjectMapper json,
            IUnModuleRecordService records,IUnModuleRecordDataService values,
            IUnSysMemberService members,IUnSysTenantMemberService tenantMembers) {
        this.validation=validation; this.dictionaries=dictionaries; this.json=json;
        this.records=records; this.values=values; this.members=members; this.tenantMembers=tenantMembers;
    }

    /** 所有固定类型可识别；依赖引用、文件、公式引擎的类型在完整实现前禁止启用使用。 */
    public List<TypeView> types() { return Arrays.stream(FieldType.values())
            .map(t->new TypeView(t.name(),t.label(),t.valueColumn().sqlName(),supported(t))).toList(); }

    /** 配置保存共用同一条定义校验链，并验证绑定字典的租户边界。 */
    public FieldDefinition definition(Long systemId,Long tenantId,String code,String label,String type,
                                       boolean required,Map<String,Object> rules) {
        FieldType fieldType=FieldType.parse(type);
        if (!supported(fieldType)) throw new IllegalArgumentException(fieldType.label()+"尚未具备完整的业务执行能力");
        FieldDefinition definition=new FieldDefinition(code,label,fieldType,required,rules);
        FieldDefinition resolved=withOptions(systemId,tenantId,definition);
        validation.validateDefinition(resolved);
        return definition;
    }

    /** 当前字段校验；旧停用选项仅可原样保留，新增选择仍然拒绝。 */
    public void validate(Long systemId,Long tenantId,FieldDefinition field,Object value,Object previous) {
        if (field.type().options() && !validation.empty(value)) {
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
                if (retained.contains(String.valueOf(option.get("id")))) option.put("enabled",true);
                effective.add(option);
            }
            Map<String,Object> rules=new LinkedHashMap<>(resolved.rules()); rules.put("options",effective);
            resolved=new FieldDefinition(field.code(),field.label(),field.type(),field.required(),rules);
        }
        validation.validateValue(resolved,value);
        validateMember(systemId,tenantId,field,value);
    }

    /** 字段配置保存和迁移使用严格的新配置范围，不允许沿用普通编辑的旧值保留规则。 */
    public void validateConfiguredValue(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        validation.validateValue(withOptions(systemId,tenantId,field),value);
        validateMember(systemId,tenantId,field,value);
    }

    /** 选人字段必须引用当前系统、当前数据租户的有效成员。 */
    private void validateMember(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()!=FieldType.USER || value==null) return;
        List<?> ids=value instanceof List<?> list?list:List.of(value);
        for (Object raw:ids) {
            Long id=new BigDecimal(raw.toString()).longValueExact();
            UnSysMember member=members.getById(id);
            if (member==null || !systemId.equals(member.getSystemId()) || !"ACTIVE".equals(member.getStatus())
                    || tenantMembers.count(new LambdaQueryWrapper<UnSysTenantMember>()
                    .eq(UnSysTenantMember::getSystemId,systemId).eq(UnSysTenantMember::getTenantId,tenantId)
                    .eq(UnSysTenantMember::getMemberId,id).eq(UnSysTenantMember::getStatus,"ACTIVE"))==0)
                throw new IllegalArgumentException(field.label()+"：所选人员不是当前租户的有效成员");
        }
    }

    /** 展示阶段按保存的成员 ID 查当前资料，不把姓名快照混入业务值。 */
    public Object present(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()!=FieldType.USER || value==null) return value;
        if (value instanceof List<?> list) return list.stream().map(id->memberView(systemId,tenantId,id)).toList();
        return memberView(systemId,tenantId,value);
    }

    private MemberView memberView(Long systemId,Long tenantId,Object raw) {
        Long id=new BigDecimal(raw.toString()).longValueExact();
        UnSysMember member=members.getById(id);
        if (member==null || !systemId.equals(member.getSystemId())) return null;
        UnSysTenantMember link=tenantMembers.getOne(new LambdaQueryWrapper<UnSysTenantMember>()
                .eq(UnSysTenantMember::getSystemId,systemId).eq(UnSysTenantMember::getTenantId,tenantId)
                .eq(UnSysTenantMember::getMemberId,id),false);
        if (link==null) return null;
        return new MemberView(id,member.getAccountId(),member.getDisplayName(),member.getEmployeeNo(),link.getDepartmentId());
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

    /** 写入已校验值，只占用与字段类型对应的物理值列。 */
    public void store(UnModuleRecordData row, FieldDefinition definition, Object value) {
        var cells=FieldValueCodec.encode(definition.type(),value,json);
        row.setValueVarchar(cells.varchar()); row.setValueText(cells.text()); row.setValueNumber(cells.number());
        row.setValueTime(cells.time()); row.setValueJson(cells.json());
    }

    /** 按保存的字段类型还原原始值，不能从 SQL 列内容猜类型。 */
    public Object stored(UnModuleRecordData row,FieldType type) {
        if (row==null) return null;
        return FieldValueCodec.decode(type,new FieldValueCodec.Cells(row.getValueVarchar(),row.getValueText(),
                row.getValueNumber(),row.getValueTime(),row.getValueJson()),json);
    }

    /** 配置即时生效，按当前保存类型解释已验证的数据。 */
    public FieldType storedType(UnModuleField field) {
        return FieldType.parse(field.getFieldType());
    }

    /** 转化配置和执行共用值形态检查；选项共用基础项 ID，实际值仍须通过目标范围校验。 */
    public void validateMapping(FieldDefinition source,FieldDefinition target) {
        boolean textTarget=target.type().kind()==FieldType.Kind.TEXT
                && Set.of(FieldType.Kind.DATE,FieldType.Kind.DATETIME,FieldType.Kind.TIME).contains(source.type().kind());
        if (source.type().kind()!=target.type().kind() && !textTarget)
            throw new IllegalArgumentException("转化字段值形态不兼容："+source.label()+"到"+target.label());
    }

    /** 当前保存的配置转换为类型定义。 */
    public FieldDefinition configured(String code,Map<String,Object> configured) {
        Map<String,Object> rules=json.convertValue(configured.getOrDefault("config",Map.of()),new TypeReference<Map<String,Object>>(){});
        return new FieldDefinition(code,(String)configured.get("fieldName"),FieldType.parse((String)configured.get("fieldType")),
                Boolean.TRUE.equals(configured.get("required")),rules);
    }
    /** 调用方持有模块行锁后检查唯一值，范围仅限当前系统租户的未删除记录。 */
    public void validateUnique(Long systemId,Long tenantId,Long moduleId,Long recordId,FieldDefinition field,Object value) {
        if (!Boolean.TRUE.equals(field.rules().get("unique")) || validation.empty(value)) return;
        var query=new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getTenantId,tenantId).eq(UnModuleRecord::getModuleId,moduleId)
                .isNull(UnModuleRecord::getDeletedTime).ne(recordId!=null,UnModuleRecord::getId,recordId);
        if ("name".equals(field.code())) query.eq(UnModuleRecord::getName,value);
        else {
            var cells=FieldValueCodec.encode(field.type(),value,json);
            var duplicateQuery=new LambdaQueryWrapper<UnModuleRecordData>().eq(UnModuleRecordData::getFieldCode,field.code());
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
        if (records.count(query)>0) throw new IllegalArgumentException(field.label()+"：值已存在");
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
        if (items.values().stream().map(DictionaryDto.Item::dictCode).distinct().count()!=1)
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

    /** 保存前核对全部租户的现有记录，禁止缩小范围后留下无法解释的旧值。 */
    public void validateExisting(Long systemId,Long moduleId,UnModuleField field,FieldDefinition definition) {
        var existing=records.list(new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId)
                .eq(UnModuleRecord::getModuleId,moduleId).isNull(UnModuleRecord::getDeletedTime));
        if (existing.isEmpty()) return;
        Map<Long,Object> stored=new HashMap<>();
        if (!"name".equals(definition.code())) for (var row:values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getFieldId,field.getId()).in(UnModuleRecordData::getRecordId,existing.stream().map(UnModuleRecord::getId).toList()))) {
            stored.put(row.getRecordId(),stored(row,storedType(field)));
        }
        List<Long> impacted=new ArrayList<>();
        for (var record:existing) {
            Object value="name".equals(definition.code())?record.getName():stored.get(record.getId());
            try {
                validateConfiguredValue(systemId,record.getTenantId(),definition,value);
                validateUnique(systemId,record.getTenantId(),moduleId,record.getId(),definition,value);
            } catch (IllegalArgumentException ex) { impacted.add(record.getId()); }
        }
        if (!impacted.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,
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
            case TEXT -> !Set.of(FieldType.SECRET,FieldType.RICH_TEXT,FieldType.AI).contains(type);
            case NUMBER,DATE,DATETIME,DATE_RANGE,TIME,TIME_RANGE,BOOLEAN,OPTION,OPTIONS,OBJECT -> true;
            case REFERENCES -> Set.of(FieldType.ATTACHMENT,FieldType.IMAGE,FieldType.FILE_GROUP).contains(type);
            case REFERENCE -> type==FieldType.USER;
            default -> false;
        };
    }
}
