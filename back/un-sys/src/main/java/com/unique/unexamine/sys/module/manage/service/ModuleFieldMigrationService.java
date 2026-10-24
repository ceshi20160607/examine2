package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldMigrationDto.Impact;
import com.unique.unexamine.sys.module.manage.dto.ModuleFieldMigrationDto.Preview;
import com.unique.unexamine.sys.module.manage.dto.ModuleRecordDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 字段选项保存前检查旧记录，并在模块保存事务中执行显式映射及逐条审计。 */
@Service
public class ModuleFieldMigrationService {
    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService values;
    private final IUnModuleLogService logs;
    private final IUnPlatAccountService accounts;
    private final DictionaryService dictionaries;
    private final ModuleFieldService fieldRules;
    private final ModulePermissionService permissions;
    private final ObjectMapper json;

    private record Current(UnModuleRecord record, UnModuleRecordData data, Object value) {}

    /** 注入生成的基础 Service；配置鉴权和模块行锁由调用方完成。 */
    public ModuleFieldMigrationService(IUnModuleRecordService records, IUnModuleRecordDataService values,
            IUnModuleLogService logs, IUnPlatAccountService accounts, DictionaryService dictionaries,
            ModuleFieldService fieldRules, ModulePermissionService permissions, ObjectMapper json) {
        this.records=records; this.values=values; this.logs=logs; this.accounts=accounts;
        this.dictionaries=dictionaries; this.fieldRules=fieldRules; this.permissions=permissions; this.json=json;
    }

    /** 按拟保存配置的有效选项和规则检查记录；只读预览不更改保存快照。 */
    public Preview preview(String token, Long systemId, Long moduleId, UnModuleField field, FieldDefinition proposed) {
        List<Impact> impacted=new ArrayList<>();
        for (Current current:currentValues(systemId,moduleId,field)) {
            String reason=invalidReason(systemId,moduleId,proposed,current);
            if (reason!=null) {
                requireMigrationAccess(token,systemId,moduleId,field,current.record());
                impacted.add(new Impact(current.record().getId(),current.record().getTenantId(),
                        current.value(),proposed.type().options()?display(systemId,current.record().getTenantId(),current.value()):Map.of(),reason));
            }
        }
        return new Preview(impacted.size(),impacted);
    }

    /** 保存事务持有模块行锁；映射只处理受新配置影响的旧值，值和日志一起提交。 */
    public void apply(Long systemId, Long moduleId, UnModuleField field, FieldDefinition proposed,
            Map<Long,Long> mappings, Long operatorId, String token) {
        if (mappings==null || mappings.isEmpty()) return;
        if (!proposed.type().options() || field.getId()==null)
            throw new IllegalArgumentException("只有已保存选项字段才能指定旧值迁移");
        if (mappings.entrySet().stream().anyMatch(entry -> entry.getKey()==null || entry.getValue()==null
                || entry.getKey()<1 || entry.getValue()<1 || entry.getKey().equals(entry.getValue())))
            throw new IllegalArgumentException("旧值与新值必须是不同的有效字典项 ID");
        String operatorName=accounts.queryById(operatorId).getDisplayName();
        Set<Long> used=new LinkedHashSet<>();
        for (Current current:currentValues(systemId,moduleId,field)) {
            if (invalidReason(systemId,moduleId,proposed,current)==null) continue;
            requireMigrationAccess(token,systemId,moduleId,field,current.record());
            if (current.data()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "记录 "+current.record().getId()+" 没有可迁移的旧字段值");
            Object after=replace(current.value(),mappings,used);
            try {
                fieldRules.validateConfiguredValue(systemId,current.record().getTenantId(),proposed,after);
                fieldRules.validateUnique(systemId,current.record().getTenantId(),moduleId,current.record().getId(),proposed,after);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "记录 "+current.record().getId()+" 的迁移目标不符合新字段规则："+ex.getMessage(),ex);
            }
            if (after.equals(current.value())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "记录 "+current.record().getId()+" 未指定完整的旧值映射");
            Long recordTenantId=current.record().getTenantId();
            Map<String,String> beforeNames=display(systemId,recordTenantId,current.value());
            Map<String,String> afterNames=display(systemId,recordTenantId,after);
            fieldRules.store(current.data(),proposed,after);
            boolean updated=values.update(new LambdaUpdateWrapper<UnModuleRecordData>()
                    .eq(UnModuleRecordData::getId,current.data().getId())
                    .set(UnModuleRecordData::getValueVarchar,current.data().getValueVarchar())
                    .set(UnModuleRecordData::getValueText,current.data().getValueText())
                    .set(UnModuleRecordData::getValueNumber,current.data().getValueNumber())
                    .set(UnModuleRecordData::getValueTime,current.data().getValueTime())
                    .set(UnModuleRecordData::getValueJson,current.data().getValueJson()));
            if (!updated) throw new ResponseStatusException(HttpStatus.CONFLICT,"待迁移字段值已变化，请重新预览");
            long version=current.record().getLockVersion()==null?0:current.record().getLockVersion();
            boolean recordUpdated=records.update(new LambdaUpdateWrapper<UnModuleRecord>()
                    .eq(UnModuleRecord::getId,current.record().getId())
                    .eq(UnModuleRecord::getLockVersion,version)
                    .set(UnModuleRecord::getLockVersion,version+1)
                    .set(UnModuleRecord::getUpdateUserId,operatorId)
                    .set(UnModuleRecord::getActualOperatorId,operatorId));
            if (!recordUpdated) throw new ResponseStatusException(HttpStatus.CONFLICT,"业务记录已变化，请重新预览迁移");
            LocalDateTime operationTime=LocalDateTime.now();
            String message="模块字段变更："+operatorName+"于"+LOG_TIME.format(operationTime)
                    +"将「"+field.getFieldName()+"」从「"+String.join("、",beforeNames.values())
                    +"」迁移到「"+String.join("、",afterNames.values())+"」。";
            var change=new ModuleRecordDto.FieldChange(field.getId(),field.getFieldCode(),field.getFieldName(),
                    current.value(),after,beforeNames,afterNames);
            var detail=new ModuleRecordDto.AuditDetail("模块字段变更",operatorName,List.of(change),null,null,message);
            UnModuleLog log=new UnModuleLog();
            log.setSystemId(systemId); log.setTenantId(recordTenantId); log.setModuleId(moduleId);
            log.setRecordId(current.record().getId()); log.setOperatorAccountId(operatorId);
            log.setActionCode("MODULE_FIELD_CHANGE"); log.setDetailJson(write(detail)); log.setCreateTime(operationTime);
            logs.addOrUpdate(log);
        }
        if (!used.equals(mappings.keySet())) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "映射包含未受本次变更影响的旧值："+mappings.keySet().stream().filter(id->!used.contains(id)).toList());
    }

    /** 迁移修改业务值，也必须具备对应租户的编辑动作、字段和记录数据范围。 */
    private void requireMigrationAccess(String token,Long systemId,Long moduleId,UnModuleField field,UnModuleRecord record) {
        var access=permissions.require(token,systemId,record.getTenantId(),moduleId,"EDIT");
        access.policy().requireWritable(Set.of(field.getFieldCode()));
        access.policy().requireOwner(record.getOwnerUserId());
    }

    /** 扫描模块全部有效记录；字段值仅通过生成的基础 Service 读取。 */
    private List<Current> currentValues(Long systemId,Long moduleId,UnModuleField field) {
        List<UnModuleRecord> existing=records.list(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getSystemId,systemId).eq(UnModuleRecord::getModuleId,moduleId)
                .isNull(UnModuleRecord::getDeletedTime).orderByAsc(UnModuleRecord::getId));
        if (existing.isEmpty()) return List.of();
        Map<Long,UnModuleRecordData> stored=new HashMap<>();
        if (!"name".equals(field.getFieldCode())) for (var row:values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getFieldId,field.getId())
                .in(UnModuleRecordData::getRecordId,existing.stream().map(UnModuleRecord::getId).toList()))) {
            stored.put(row.getRecordId(),row);
        }
        List<Current> result=new ArrayList<>(existing.size());
        for (var record:existing) {
            UnModuleRecordData row=stored.get(record.getId());
            result.add(new Current(record,row,"name".equals(field.getFieldCode())?record.getName()
                    :fieldRules.stored(row,fieldRules.storedType(field))));
        }
        return result;
    }

    /** 复用字段共用校验，同时纳入当前租户的唯一性约束。 */
    private String invalidReason(Long systemId,Long moduleId,FieldDefinition proposed,Current current) {
        try {
            fieldRules.validateConfiguredValue(systemId,current.record().getTenantId(),proposed,current.value());
            fieldRules.validateUnique(systemId,current.record().getTenantId(),moduleId,current.record().getId(),proposed,current.value());
            return null;
        } catch (IllegalArgumentException ex) { return ex.getMessage(); }
    }

    /** 选项基础项 ID 由字典统一解析，旧名称和目标名称在日志写入时冻结。 */
    private Map<String,String> display(Long systemId,Long tenantId,Object value) {
        Set<Long> ids=new LinkedHashSet<>();
        if (value instanceof Map<?,?> object && object.get("path") instanceof List<?> path)
            path.forEach(item->ids.add(optionId(item)));
        else if (value instanceof List<?> list) list.forEach(item->ids.add(optionId(item)));
        else if (value!=null) ids.add(optionId(value));
        Map<String,String> names=new LinkedHashMap<>();
        if (!ids.isEmpty()) {
            var items=dictionaries.items(systemId,tenantId,ids);
            for (Long id:ids) names.put(String.valueOf(id),items.get(id).name());
        }
        return names;
    }

    /** 单选、多选及级联路径均按管理员明确给出的 ID 映射替换。 */
    private Object replace(Object before,Map<Long,Long> mappings,Set<Long> used) {
        if (before instanceof Map<?,?> object && object.get("path") instanceof List<?> path) {
            List<Long> after=new ArrayList<>(path.size());
            for (Object item:path) after.add(mapped(item,mappings,used));
            return Map.of("path",after);
        }
        if (before instanceof List<?> list) {
            List<Long> after=new ArrayList<>(list.size());
            for (Object item:list) after.add(mapped(item,mappings,used));
            return after;
        }
        if (before instanceof Number) return mapped(before,mappings,used);
        throw new ResponseStatusException(HttpStatus.CONFLICT,"旧字段值不是可迁移的字典项 ID");
    }

    private Long mapped(Object before,Map<Long,Long> mappings,Set<Long> used) {
        Long id=optionId(before); Long target=mappings.get(id);
        if (target!=null) { used.add(id); return target; }
        return id;
    }

    private Long optionId(Object value) {
        if (!(value instanceof Number)) throw new ResponseStatusException(HttpStatus.CONFLICT,"旧字典项 ID 无效");
        try { return new BigDecimal(value.toString()).longValueExact(); }
        catch (ArithmeticException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT,"旧字典项 ID 无效",ex); }
    }

    private Object read(String source) {
        if (source==null) return null;
        try { return json.readValue(source,Object.class); }
        catch (JsonProcessingException ex) { throw new IllegalStateException("已有字段值无法读取",ex); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalArgumentException("字段迁移结果无法保存",ex); }
    }
}
