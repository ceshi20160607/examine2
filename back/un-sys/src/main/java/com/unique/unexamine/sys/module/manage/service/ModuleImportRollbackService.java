package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.module.ModuleRecordProcessHandler;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeJob;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeRow;
import com.unique.unexamine.sys.module.base.entity.UnModuleLog;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecord;
import com.unique.unexamine.sys.module.base.entity.UnModuleRecordData;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeRowService;
import com.unique.unexamine.sys.module.base.service.IUnModuleLogService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordDataService;
import com.unique.unexamine.sys.module.base.service.IUnModuleRecordService;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportRollbackDto.Check;
import com.unique.unexamine.sys.module.manage.dto.ModuleImportRollbackDto.Result;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 导入整批回滚；仅还原本批成功行，遇到后续修改或流程关联则整批拒绝。 */
@Service
public class ModuleImportRollbackService {
    private final ModuleImportService imports;
    private final ModulePermissionService permissions;
    private final ModuleConfigService config;
    private final IUnModuleExchangeJobService jobs;
    private final IUnModuleExchangeRowService rows;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService data;
    private final IUnModuleLogService logs;
    private final ModuleFileBatchService files;
    private final List<ModuleRecordProcessHandler> handlers;
    private final BusinessEventPublisher events;
    private final ObjectMapper json;

    public ModuleImportRollbackService(ModuleImportService imports,ModulePermissionService permissions,
            ModuleConfigService config,IUnModuleExchangeJobService jobs,IUnModuleExchangeRowService rows,
            IUnModuleRecordService records,IUnModuleRecordDataService data,IUnModuleLogService logs,
            ModuleFileBatchService files,List<ModuleRecordProcessHandler> handlers,
            BusinessEventPublisher events,ObjectMapper json) {
        this.imports=imports; this.permissions=permissions; this.config=config; this.jobs=jobs;
        this.rows=rows; this.records=records; this.data=data; this.logs=logs;
        this.files=files; this.handlers=handlers; this.events=events; this.json=json;
    }

    /** 预先展示整批可回滚性；正式提交仍在行锁下重新检查。 */
    public Check check(String token,Long systemId,Long tenantId,Long moduleId,Long jobId) {
        UnModuleExchangeJob job=imports.ownJob(token,systemId,tenantId,moduleId,jobId);
        permissions.require(token,systemId,tenantId,moduleId,"IMPORT");
        var successful=successful(jobId);
        List<String> reasons=inspect(job,successful,false);
        return new Check(jobId,reasons.isEmpty(),successful.size(),reasons.stream().limit(20).toList());
    }

    /** 所有受影响记录先完成版本和流程检查，再在一个事务中逐条还原。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Result rollback(String token,Long systemId,Long tenantId,Long moduleId,Long jobId) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"IMPORT");
        imports.ownJob(token,systemId,tenantId,moduleId,jobId);
        var job=jobs.getOne(new LambdaQueryWrapper<UnModuleExchangeJob>()
                .eq(UnModuleExchangeJob::getId,jobId).last("FOR UPDATE"),false);
        var successful=successful(jobId);
        List<String> reasons=inspect(job,successful,true);
        if (!reasons.isEmpty()) throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,
                "导入批次不可回滚："+String.join("；",reasons.stream().limit(3).toList()));
        for (UnModuleExchangeRow row:successful) {
            var snapshot=parse(row.getDetailJson());
            UnModuleRecord current=record(row.getRecordId(),true);
            if ("CREATE".equals(row.getOperationCode())) {
                boolean updated=records.update(new UpdateWrapper<UnModuleRecord>()
                        .eq("id",current.getId()).eq("lock_version",current.getLockVersion())
                        .set("deleted_time",LocalDateTime.now())
                        .set("lock_version",current.getLockVersion()+1)
                        .set("actual_operator_id",access.accountId())
                        .set("update_user_id",access.accountId()));
                if (!updated) conflict("业务记录在回滚时发生并发修改");
                files.deleteForRecord(moduleId,current.getId());
            } else restore(current,snapshot,access.accountId());
            UnModuleLog log=new UnModuleLog(); log.setSystemId(systemId); log.setTenantId(current.getTenantId());
            log.setActorTenantId(access.context().tenantId()); log.setModuleId(moduleId);
            log.setRecordId(current.getId()); log.setOperatorAccountId(access.accountId());
            log.setActionCode("RECORD_IMPORT_ROLLBACK");
            log.setDetailJson(write(Map.of("jobId",jobId,"rowNo",row.getRowNo(),
                    "operation",row.getOperationCode())));
            log.setCreateTime(LocalDateTime.now()); logs.addOrUpdate(log);
            row.setStatus("ROLLED_BACK"); rows.updateById(row);
        }
        job.setStatus("ROLLED_BACK"); job.setUpdateTime(LocalDateTime.now()); jobs.updateById(job);
        events.publish(new BusinessEventContext("IMPORT_ROLLED_BACK",systemId,access.context().tenantId(),moduleId,
                null,access.accountId(),access.definition().menu().getId(),Map.of(),
                Map.of("jobId",jobId,"rolledBackCount",successful.size())));
        return new Result(jobId,"ROLLED_BACK",successful.size());
    }

    /** 不可回滚原因既用于预检也用于提交；后者对记录加行锁。 */
    private List<String> inspect(UnModuleExchangeJob job,List<UnModuleExchangeRow> successful,boolean lock) {
        List<String> reasons=new ArrayList<>();
        if (job==null || !Set.of("SUCCESS","PARTIAL").contains(job.getStatus())) reasons.add("作业状态不允许回滚");
        if (successful.isEmpty()) reasons.add("没有成功导入的记录");
        if (!reasons.isEmpty()) return reasons;
        var fieldRevision=config.currentFields(job.getModuleId()).stream()
                .map(field->field.getId()+":"+field.getFieldKey()+":"+field.getUpdateTime()).toList();
        var jobConfig=parse(job.getConfigJson());
        for (var mapping:jobConfig.path("fieldMapping").properties()) {
            String code=mapping.getValue().asText();
            if (config.currentFields(job.getModuleId()).stream()
                    .anyMatch(field->code.equals(field.getFieldKey()) && field.getFieldType()!=null && field.getFieldType()==9)) {
                reasons.add("附件字段参与导入，文件引用不能直接还原"); break;
            }
        }
        for (UnModuleExchangeRow row:successful) {
            UnModuleRecord current=record(row.getRecordId(),lock);
            if (current==null || !job.getSystemId().equals(current.getSystemId())
                    || !job.getTenantId().equals(current.getTenantId())
                    || !job.getModuleId().equals(current.getModuleId()) || current.getDeletedTime()!=null) {
                reasons.add("第"+row.getRowNo()+"行记录已删除或归属变化"); continue;
            }
            if (!current.getLockVersion().equals(row.getRecordVersion())) {
                reasons.add("第"+row.getRowNo()+"行导入后又被修改"); continue;
            }
            JsonNode snapshot=parse(row.getDetailJson());
            if (!snapshot.path("fieldRevision").equals(json.valueToTree(fieldRevision)))
                reasons.add("第"+row.getRowNo()+"行字段配置已变化");
            if (handlers.stream().anyMatch(handler->handler.hasAssociatedInstance(job.getSystemId(),job.getModuleId(),row.getRecordId())))
                reasons.add("第"+row.getRowNo()+"行已关联 Flow 或审批实例");
        }
        return reasons;
    }

    private List<UnModuleExchangeRow> successful(Long jobId) {
        return rows.list(new LambdaQueryWrapper<UnModuleExchangeRow>()
                .eq(UnModuleExchangeRow::getJobId,jobId).eq(UnModuleExchangeRow::getStatus,"SUCCESS")
                .orderByAsc(UnModuleExchangeRow::getRowNo));
    }

    private UnModuleRecord record(Long id,boolean lock) {
        var where=new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getId,id);
        if (lock) where.last("FOR UPDATE");
        return records.getOne(where,false);
    }

    /** 乐观锁版本递增，业务字段从导入前快照恢复；扩展行只调整本记录。 */
    private void restore(UnModuleRecord current,JsonNode snapshot,Long actor) {
        if (snapshot.path("beforeRecord").isNull() || snapshot.path("beforeRecord").isMissingNode())
            conflict("更新行缺少导入前快照");
        UnModuleRecord before=json.convertValue(snapshot.path("beforeRecord"),UnModuleRecord.class);
        UpdateWrapper<UnModuleRecord> update=new UpdateWrapper<UnModuleRecord>()
                .eq("id",current.getId()).eq("lock_version",current.getLockVersion());
        try {
            for (Field field:UnModuleRecord.class.getDeclaredFields()) {
                var column=field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
                if (column==null || Set.of("system_id","tenant_id","module_id","lock_version",
                        "create_time","update_time","deleted_time","actual_operator_id","update_user_id").contains(column.value())) continue;
                field.setAccessible(true); update.set(column.value(),field.get(before));
            }
        } catch (IllegalAccessException ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"导入前记录无法还原",ex); }
        update.set("lock_version",current.getLockVersion()+1)
                .set("actual_operator_id",actor).set("update_user_id",actor)
                .set("update_time",LocalDateTime.now());
        if (!records.update(update)) conflict("业务记录在回滚时发生并发修改");
        List<UnModuleRecordData> previous=json.convertValue(snapshot.path("beforeData"),
                new TypeReference<List<UnModuleRecordData>>(){});
        Map<Long,UnModuleRecordData> byId=new HashMap<>();
        for (var item:previous) byId.put(item.getId(),item);
        for (var item:data.list(new LambdaQueryWrapper<UnModuleRecordData>()
                .eq(UnModuleRecordData::getRecordId,current.getId()))) {
            var old=byId.remove(item.getId());
            if (old==null) { data.removeById(item.getId()); continue; }
            boolean restored=data.update(new UpdateWrapper<UnModuleRecordData>().eq("id",item.getId())
                    .set("value_varchar",old.getValueVarchar()).set("value_text",old.getValueText())
                    .set("value_number",old.getValueNumber()).set("value_time",old.getValueTime())
                    .set("value_json",old.getValueJson()).set("update_time",LocalDateTime.now()));
            if (!restored) conflict("导入前字段值已无法还原");
        }
        if (!byId.isEmpty()) conflict("导入前字段值已无法还原");
    }

    private JsonNode parse(String value) {
        try { return json.readTree(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导入快照无法读取",ex); }
    }
    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"导入回滚日志无法编码",ex); }
    }
    private void conflict(String reason) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,reason); }
}
