package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FieldDefinition;
import com.unique.unexamine.core.field.FieldType;
import com.unique.unexamine.plat.base.service.IUnPlatAccountService;
import com.unique.unexamine.sys.module.base.entity.UnModuleField;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.util.ModuleRecordStorage;
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
import org.springframework.stereotype.Service;

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

    private record Current(UnModuleRecord record, UnModuleRecordData data, Object value,
                           FieldDefinition definition) {}

    /** 注入生成的基础 Service；配置鉴权和模块行锁由调用方完成。 */
    public ModuleFieldMigrationService(IUnModuleRecordService records, IUnModuleRecordDataService values,
            IUnModuleLogService logs, IUnPlatAccountService accounts, DictionaryService dictionaries,
            ModuleFieldService fieldRules, ModulePermissionService permissions, ObjectMapper json) {
        this.records=records; this.values=values; this.logs=logs; this.accounts=accounts;
        this.dictionaries=dictionaries; this.fieldRules=fieldRules; this.permissions=permissions; this.json=json;
    }

    /**
     * 字段配置改变单值/多值后，按新路由迁移已有业务值。
     * 主表槽位和扩展行在同一事务中只保留一个有效存储位置，并复用逻辑删除行避免再次切回时撞唯一键。
     */
    public void migrateStorage(Long systemId, Long moduleId, UnModuleField field,
            FieldDefinition previous, FieldDefinition proposed, Long operatorId) {
        boolean previousMain = ModuleRecordStorage.isMain(previous);
        boolean proposedMain = ModuleRecordStorage.isMain(proposed);
        if (previousMain == proposedMain) return;
        List<UnModuleRecord> existing = records.list(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getSystemId, systemId)
                .eq(UnModuleRecord::getModuleId, moduleId)
                .isNull(UnModuleRecord::getDeletedTime)
                .orderByAsc(UnModuleRecord::getId));
        for (UnModuleRecord record : existing) {
            UnModuleRecordData active = values.getOne(new LambdaQueryWrapper<UnModuleRecordData>()
                    .eq(UnModuleRecordData::getRecordId, record.getId())
                    .eq(UnModuleRecordData::getFieldId, field.getId()), false);
            Object before = previousMain ? ModuleRecordStorage.read(record, previous)
                    : fieldRules.stored(active, previous);
            Object after = convertMultiplicity(before, previous, proposed, field.getFieldName());
            if (after != null) {
                try {
                    fieldRules.validateConfiguredValue(systemId, record.getTenantId(), proposed, after);
                } catch (IllegalArgumentException ex) {
                    throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                            "记录 " + record.getId() + " 的字段值无法迁移到新存储规则：" + ex.getMessage(), ex);
                }
            }
            long version = record.getLockVersion() == null ? 0 : record.getLockVersion();
            UpdateWrapper<UnModuleRecord> update = new UpdateWrapper<UnModuleRecord>()
                    .eq("id", record.getId()).eq("lock_version", version)
                    .set("lock_version", version + 1)
                    .set("update_user_id", operatorId).set("actual_operator_id", operatorId);
            if (proposedMain) {
                update.set(ModuleRecordStorage.column(proposed.code()),
                        ModuleRecordStorage.sqlValue(proposed, after));
                boolean updated = records.update(update);
                if (!updated) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "记录 " + record.getId() + " 在字段迁移期间已被修改");
                // 扩展行保留为清空的逻辑记录，避免以后切回扩展存储时违反唯一键；当前字段路由已只读主表值。
                if (active != null) values.update(new LambdaUpdateWrapper<UnModuleRecordData>()
                        .eq(UnModuleRecordData::getId, active.getId())
                        .set(UnModuleRecordData::getValueVarchar, null)
                        .set(UnModuleRecordData::getValueText, null)
                        .set(UnModuleRecordData::getValueNumber, null)
                        .set(UnModuleRecordData::getValueTime, null)
                        .set(UnModuleRecordData::getValueJson, null));
            } else {
                update.set(ModuleRecordStorage.column(previous.code()), null);
                boolean updated = records.update(update);
                if (!updated) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "记录 " + record.getId() + " 在字段迁移期间已被修改");
                UnModuleRecordData target = active;
                if (after == null && target == null) continue;
                if (target == null) target = new UnModuleRecordData();
                target.setRecordId(record.getId());
                target.setFieldId(field.getId());
                target.setFieldKey(field.getFieldKey());
                fieldRules.store(target, proposed, after);
                target.setDeletedTime(null);
                if (target.getId() == null) values.addOrUpdate(target);
                else values.update(new LambdaUpdateWrapper<UnModuleRecordData>()
                        .eq(UnModuleRecordData::getId, target.getId())
                        .set(UnModuleRecordData::getRecordId, target.getRecordId())
                        .set(UnModuleRecordData::getFieldId, target.getFieldId())
                        .set(UnModuleRecordData::getFieldKey, target.getFieldKey())
                        .set(UnModuleRecordData::getValueVarchar, target.getValueVarchar())
                        .set(UnModuleRecordData::getValueText, target.getValueText())
                        .set(UnModuleRecordData::getValueNumber, target.getValueNumber())
                        .set(UnModuleRecordData::getValueTime, target.getValueTime())
                        .set(UnModuleRecordData::getValueJson, target.getValueJson())
                        .set(UnModuleRecordData::getDeletedTime, null));
            }
            record.setLockVersion(version + 1);
            logStorageMigration(systemId, record, field, before, after, operatorId,
                    previousMain ? "MAIN_TO_DATA" : "DATA_TO_MAIN");
        }
    }

    /** 单值与多值切换只允许无损转换；多个旧值不能静默选一个。 */
    private Object convertMultiplicity(Object value, FieldDefinition previous,
                                       FieldDefinition proposed, String label) {
        if (value == null) return null;
        boolean beforeMultiple = Boolean.TRUE.equals(previous.rules().get("multiple"));
        boolean afterMultiple = Boolean.TRUE.equals(proposed.rules().get("multiple"));
        if (beforeMultiple == afterMultiple) return value;
        if (afterMultiple) return value instanceof List<?> ? value : List.of(value);
        if (!(value instanceof List<?> list)) return value;
        if (list.isEmpty()) return null;
        if (list.size() > 1) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                label + "包含多个值，不能自动改为单值，请先完成字段值迁移");
        return list.getFirst();
    }

    /** 字段存储迁移按记录留下可追溯操作历史。 */
    private void logStorageMigration(Long systemId, UnModuleRecord record, UnModuleField field,
                                     Object before, Object after, Long operatorId, String route) {
        UnModuleLog log = new UnModuleLog();
        log.setSystemId(systemId); log.setTenantId(record.getTenantId());
        log.setModuleId(record.getModuleId()); log.setRecordId(record.getId());
        log.setOperatorAccountId(operatorId); log.setActionCode("MODULE_FIELD_STORAGE_MIGRATE");
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("fieldId", field.getId()); detail.put("fieldKey", field.getFieldKey());
        detail.put("route", route); detail.put("before", before); detail.put("after", after);
        log.setDetailJson(write(detail)); log.setCreateTime(LocalDateTime.now());
        logs.addOrUpdate(log);
    }

    /** 按拟保存配置的有效选项和规则检查记录；只读预览不更改保存快照。 */
    public Preview preview(String token, Long systemId, Long moduleId, UnModuleField field, FieldDefinition proposed) {
        List<Impact> impacted=new ArrayList<>();
        for (Current current:currentValues(systemId,moduleId,field)) {
            String reason=invalidReason(systemId,moduleId,proposed,current);
            if (reason!=null) {
                requireMigrationAccess(token,systemId,moduleId,field,current.record());
                impacted.add(new Impact(current.record().getId(),current.record().getTenantId(),
                        current.value(),proposed.type().options()?display(systemId,current.record().getTenantId(),current.definition(),current.value()):Map.of(),reason));
            }
        }
        return new Preview(impacted.size(),impacted);
    }

    /** 保存事务持有模块行锁；映射只处理受新配置影响的旧值，值和日志一起提交。 */
    public void apply(Long systemId, Long moduleId, UnModuleField field, FieldDefinition proposed,
            Map<Long,Long> mappings, Long operatorId, String token) {
        if (mappings==null || mappings.isEmpty()) return;
        if (!proposed.type().options() || field.getId()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"只有已保存选项字段才能指定旧值迁移");
        if (mappings.entrySet().stream().anyMatch(entry -> entry.getKey()==null || entry.getValue()==null
                || entry.getKey()<1 || entry.getValue()<1 || entry.getKey().equals(entry.getValue())))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"旧值与新值必须是不同的有效字典项 ID");
        String operatorName=accounts.queryById(operatorId).getDisplayName();
        Set<Long> used=new LinkedHashSet<>();
        for (Current current:currentValues(systemId,moduleId,field)) {
            if (invalidReason(systemId,moduleId,proposed,current)==null) continue;
            requireMigrationAccess(token,systemId,moduleId,field,current.record());
            Object after=replace(current.value(),mappings,used,current.definition());
            try {
                fieldRules.validateConfiguredValue(systemId,current.record().getTenantId(),proposed,after);
                fieldRules.validateUnique(systemId,current.record().getTenantId(),moduleId,current.record().getId(),proposed,after);
            } catch (IllegalArgumentException ex) {
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                        "记录 "+current.record().getId()+" 的迁移目标不符合新字段规则："+ex.getMessage(),ex);
            }
            if (java.util.Objects.equals(after,current.value())) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                    "记录 "+current.record().getId()+" 未指定完整的旧值映射");
            Long recordTenantId=current.record().getTenantId();
            FieldDefinition currentDefinition=fieldRules.resolved(systemId,recordTenantId,current.definition());
            FieldDefinition proposedDefinition=fieldRules.resolved(systemId,recordTenantId,proposed);
            Map<String,String> beforeNames=display(systemId,recordTenantId,currentDefinition,current.value());
            Map<String,String> afterNames=display(systemId,recordTenantId,proposedDefinition,after);
            boolean targetMain=ModuleRecordStorage.isMain(proposedDefinition);
            boolean updated;
            if (targetMain) {
                ModuleRecordStorage.write(current.record(),proposedDefinition,after);
                UpdateWrapper<UnModuleRecord> update=new UpdateWrapper<UnModuleRecord>()
                        .eq("id",current.record().getId())
                        .eq("lock_version",current.record().getLockVersion()==null?0:current.record().getLockVersion())
                        .set("lock_version",(current.record().getLockVersion()==null?0:current.record().getLockVersion())+1)
                        .set("update_user_id",operatorId).set("actual_operator_id",operatorId);
                update.set(ModuleRecordStorage.column(proposedDefinition.code()),ModuleRecordStorage.sqlValue(proposedDefinition,after));
                updated=records.update(update);
                if (updated && current.data()!=null) values.deleteByIds(List.of(current.data().getId()));
            } else {
                UnModuleRecordData target=current.data()==null?new UnModuleRecordData():current.data();
                fieldRules.store(target,proposedDefinition,after); target.setRecordId(current.record().getId());
                target.setFieldId(field.getId()); target.setFieldKey(field.getFieldKey());
                updated=target.getId()==null?values.addOrUpdate(target)!=null:values.update(new LambdaUpdateWrapper<UnModuleRecordData>()
                        .eq(UnModuleRecordData::getId,target.getId())
                        .set(UnModuleRecordData::getValueVarchar,target.getValueVarchar())
                        .set(UnModuleRecordData::getValueText,target.getValueText())
                        .set(UnModuleRecordData::getValueNumber,target.getValueNumber())
                        .set(UnModuleRecordData::getValueTime,target.getValueTime())
                        .set(UnModuleRecordData::getValueJson,target.getValueJson()));
                if (updated) {
                    long version=current.record().getLockVersion()==null?0:current.record().getLockVersion();
                    updated=records.update(new LambdaUpdateWrapper<UnModuleRecord>()
                            .eq(UnModuleRecord::getId,current.record().getId()).eq(UnModuleRecord::getLockVersion,version)
                            .set(UnModuleRecord::getLockVersion,version+1)
                            .set(UnModuleRecord::getUpdateUserId,operatorId).set(UnModuleRecord::getActualOperatorId,operatorId));
                }
            }
            if (!updated) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"待迁移字段值已变化，请重新预览");
            LocalDateTime operationTime=LocalDateTime.now();
            String message="模块字段变更："+operatorName+"于"+LOG_TIME.format(operationTime)
                    +"将「"+field.getFieldName()+"」从「"+String.join("、",beforeNames.values())
                    +"」迁移到「"+String.join("、",afterNames.values())+"」。";
            var change=new ModuleRecordDto.FieldChange(field.getId(),field.getFieldKey(),field.getFieldName(),
                    current.value(),after,beforeNames,afterNames);
            var detail=new ModuleRecordDto.AuditDetail("模块字段变更",operatorName,List.of(change),null,null,message);
            UnModuleLog log=new UnModuleLog();
            log.setSystemId(systemId); log.setTenantId(recordTenantId); log.setModuleId(moduleId);
            log.setRecordId(current.record().getId()); log.setOperatorAccountId(operatorId);
            log.setActionCode("MODULE_FIELD_CHANGE"); log.setDetailJson(write(detail)); log.setCreateTime(operationTime);
            logs.addOrUpdate(log);
        }
        if (!used.equals(mappings.keySet())) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                "映射包含未受本次变更影响的旧值："+mappings.keySet().stream().filter(id->!used.contains(id)).toList());
    }

    /** 迁移修改业务值，也必须具备对应租户的编辑动作、字段和记录数据范围。 */
    private void requireMigrationAccess(String token,Long systemId,Long moduleId,UnModuleField field,UnModuleRecord record) {
        var access=permissions.require(token,systemId,record.getTenantId(),moduleId,"EDIT");
        access.policy().requireWritable(Set.of(field.getFieldKey()));
        access.policy().requireOwner(record.getOwnerUserId());
    }

    /** 扫描模块全部有效记录；字段值仅通过生成的基础 Service 读取。 */
    private List<Current> currentValues(Long systemId,Long moduleId,UnModuleField field) {
        List<UnModuleRecord> existing=records.list(new LambdaQueryWrapper<UnModuleRecord>()
                .eq(UnModuleRecord::getSystemId,systemId).eq(UnModuleRecord::getModuleId,moduleId)
                .isNull(UnModuleRecord::getDeletedTime).orderByAsc(UnModuleRecord::getId));
        if (existing.isEmpty()) return List.of();
        Map<Long,UnModuleRecordData> stored=new HashMap<>();
        if (!"name".equals(field.getFieldKey())) for (var row:values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getFieldId,field.getId())
                .in(UnModuleRecordData::getRecordId,existing.stream().map(UnModuleRecord::getId).toList()))) {
            stored.put(row.getRecordId(),row);
        }
        List<Current> result=new ArrayList<>(existing.size());
        for (var record:existing) {
            UnModuleRecordData row=stored.get(record.getId());
            FieldDefinition definition=fieldRules.resolved(systemId,record.getTenantId(),field);
            result.add(new Current(record,row,"name".equals(field.getFieldKey())?record.getName()
                    :ModuleRecordStorage.isMain(definition)?ModuleRecordStorage.read(record,definition)
                    :fieldRules.stored(row,definition),definition));
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
    private Map<String,String> display(Long systemId,Long tenantId,FieldDefinition field,Object value) {
        if (field.type()==FieldType.SELECT) {
            Map<String,String> names=new LinkedHashMap<>();
            Set<String> selected=value instanceof List<?> list?list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                    :value==null?Set.of():Set.of(String.valueOf(value));
            if (field.rules().get("options") instanceof List<?> options) for (Object item:options) if (item instanceof Map<?,?> option) {
                String optionValue=String.valueOf(option.get("value"));
                if (selected.contains(optionValue)) names.put(optionValue,String.valueOf(option.get("label")));
            }
            return names;
        }
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
    private Object replace(Object before,Map<Long,Long> mappings,Set<Long> used,FieldDefinition definition) {
        if (definition.type()==FieldType.SELECT) {
            Set<String> values=new LinkedHashSet<>();
            if (before instanceof List<?> list) list.forEach(item->values.add(String.valueOf(item)));
            else if (before!=null) values.add(String.valueOf(before));
            Map<String,String> mapping=new HashMap<>();
            if (definition.rules().get("options") instanceof List<?> options) for (Object item:options) if (item instanceof Map<?,?> option) {
                if (option.get("value")!=null && option.get("id")!=null) mapping.put(String.valueOf(option.get("id")),String.valueOf(option.get("value")));
            }
            List<String> after=new ArrayList<>();
            for (String value:values) {
                Long oldId=mapping.entrySet().stream().filter(entry->entry.getValue().equals(value)).map(Map.Entry::getKey)
                        .map(Long::valueOf).findFirst().orElse(null);
                Long target=oldId==null?null:mappings.get(oldId);
                if (target!=null) { used.add(oldId); after.add(target.toString()); }
                else after.add(value);
            }
            return Boolean.TRUE.equals(definition.rules().get("multiple"))?after:(after.isEmpty()?null:after.getFirst());
        }
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
        throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"旧字段值不是可迁移的字典项 ID");
    }

    private Long mapped(Object before,Map<Long,Long> mappings,Set<Long> used) {
        Long id=optionId(before); Long target=mappings.get(id);
        if (target!=null) { used.add(id); return target; }
        return id;
    }

    private Long optionId(Object value) {
        if (!(value instanceof Number)) throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"旧字典项 ID 无效");
        try { return new BigDecimal(value.toString()).longValueExact(); }
        catch (ArithmeticException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"旧字典项 ID 无效",ex); }
    }

    private Object read(String source) {
        if (source==null) return null;
        try { return json.readValue(source,Object.class); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"已有字段值无法读取",ex); }
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"字段迁移结果无法保存",ex); }
    }
}
