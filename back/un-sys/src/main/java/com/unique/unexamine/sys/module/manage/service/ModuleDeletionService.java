package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.cache.ConfigurationCache;
import com.unique.unexamine.core.field.FileBatchId;
import com.unique.unexamine.core.file.base.entity.UnCommonFile;
import com.unique.unexamine.core.file.base.service.IUnCommonFileService;
import com.unique.unexamine.file.FileStorage;
import com.unique.unexamine.sys.base.entity.*;
import com.unique.unexamine.sys.base.service.*;
import com.unique.unexamine.sys.manage.dto.SystemManageDto;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.module.base.entity.*;
import com.unique.unexamine.sys.module.base.service.*;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Backup;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Check;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Choice;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.DeleteRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleDeletionDto.Deleted;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

/** 模块删除检查、完整数据备份与逻辑删除；不改动关联流程、审批和历史日志。 */
@Service
public class ModuleDeletionService {
    private final SystemManageService systems;
    private final ModuleConfigService configurations;
    private final ModulePermissionService permissions;
    private final IUnModuleService modules;
    private final IUnModuleRecordService records;
    private final IUnModuleRecordDataService values;
    private final IUnModuleFieldService fields;
    private final IUnModuleExchangeJobService jobs;
    private final IUnCommonFileService files;
    private final IUnModuleLogService recordLogs;
    private final IUnSysLogOptService logs;
    private final IUnSysMenuService menus;
    private final IUnModuleMenuService bindings;
    private final IUnSysSystemConfigService systemConfigs;
    private final ConfigurationCache cache;
    private final FileStorage storage;
    private final ObjectMapper json;

    private record Snapshot(String sha256,long count) {}

    /** 所有数据查询和变更复用生成的基础服务，字节存储沿用公共文件接入。 */
    public ModuleDeletionService(SystemManageService systems,ModuleConfigService configurations,
            ModulePermissionService permissions,IUnModuleService modules,IUnModuleRecordService records,
            IUnModuleRecordDataService values,IUnModuleFieldService fields,IUnModuleExchangeJobService jobs,
            IUnCommonFileService files,IUnModuleLogService recordLogs,IUnSysLogOptService logs,
            IUnSysMenuService menus,IUnModuleMenuService bindings,IUnSysSystemConfigService systemConfigs,
            ConfigurationCache cache,FileStorage storage,ObjectMapper json) {
        this.systems=systems; this.configurations=configurations; this.permissions=permissions; this.modules=modules;
        this.records=records; this.values=values; this.fields=fields; this.jobs=jobs; this.files=files;
        this.recordLogs=recordLogs; this.logs=logs; this.menus=menus; this.bindings=bindings;
        this.systemConfigs=systemConfigs; this.cache=cache; this.storage=storage; this.json=json;
    }

    /** 统计模块全部有效数据，包含继承此模块的租户，避免只检查管理员当前租户。 */
    public Check check(String token,Long systemId,Long tenantId,Long moduleId) {
        var context=systems.requireAdminContext(token,systemId,tenantId); owned(context,moduleId);
        long count=records.count(recordQuery(systemId,moduleId)); return new Check(moduleId,count,count>0);
    }

    /** 分页导出全部有效记录与字段原值；落盘成功后才返回备份标识，此步骤不删除模块。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Backup export(String token,Long systemId,Long tenantId,Long moduleId) throws IOException {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        configurations.lockModule(systemId,moduleId); var module=owned(context,moduleId);
        Long actor=systems.accountId(token,systemId);
        Path temporary=Files.createTempFile("module-delete-backup-",".json");
        try {
            Snapshot snapshot;
            try (OutputStream output=Files.newOutputStream(temporary)) {
                snapshot=snapshot(module,output,token,true);
            }
            FileStorage.StoredFile stored;
            try (InputStream input=Files.newInputStream(temporary)) { stored=storage.store(input); }
            // 元数据事务失败时清理未引用的临时备份；成功的备份始终保留供下载。
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {
                    if (status!=STATUS_COMMITTED) try { storage.delete(stored.key()); }
                    catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(ModuleDeletionService.class).warn("未提交备份清理失败",ex); }
                }
            });
            var file=new UnCommonFile(); file.setScopeType("SYSTEM"); file.setSystemId(systemId);
            file.setTenantId(context.tenantId()); file.setUploaderAccountId(actor);
            file.setBatchId(FileBatchId.create()); file.setOriginalName("module-"+moduleId+"-backup.json");
            file.setContentType("application/json"); file.setSizeBytes(stored.size()); file.setSha256(stored.sha256());
            file.setStorageType("LOCAL"); file.setStorageKey(stored.key()); file.setStatus("ACTIVE"); files.addOrUpdate(file);
            var job=new UnModuleExchangeJob(); job.setSystemId(systemId); job.setTenantId(context.tenantId());
            job.setModuleId(moduleId); job.setOperatorAccountId(actor); job.setJobType("DELETE_BACKUP");
            job.setStatus("SUCCESS"); job.setResultFileId(file.getId());
            job.setConfigJson(json.writeValueAsString(Map.of("sha256",snapshot.sha256())));
            job.setTotalCount(Math.toIntExact(snapshot.count())); job.setSuccessCount(job.getTotalCount());
            job.setFailureCount(0); job.setStartTime(LocalDateTime.now()); job.setFinishTime(LocalDateTime.now()); jobs.addOrUpdate(job);
            audit(context,actor,moduleId,"MODULE_DELETE_EXPORT",Map.of("jobId",job.getId(),"count",snapshot.count()));
            return new Backup(job.getId(),file.getId(),snapshot.count());
        } finally { Files.deleteIfExists(temporary); }
    }

    /** 删除前重查数据并核对备份；模块行锁与记录写入共用，防止检查后又写入业务数据。 */
    @Transactional(rollbackFor=Exception.class,isolation=Isolation.READ_COMMITTED)
    public Deleted delete(String token,Long systemId,Long tenantId,Long moduleId,DeleteRequest request) throws IOException {
        var context=systems.requireAdminContext(token,systemId,tenantId);
        configurations.lockModule(systemId,moduleId); var module=owned(context,moduleId);
        Long actor=systems.accountId(token,systemId); long count=records.count(recordQuery(systemId,moduleId));
        Choice choice=request==null || request.choice()==null?Choice.EMPTY:request.choice();
        if (count>0 && choice==Choice.EMPTY) conflict("模块存在业务数据，请选择导出数据后删除或直接删除");
        if (choice==Choice.EXPORTED) verifyBackup(context,actor,module,request.exportJobId());
        long afterId=0;
        while (true) {
            var batch=records.list(recordQuery(systemId,moduleId).gt(UnModuleRecord::getId,afterId)
                    .orderByAsc(UnModuleRecord::getId).last("LIMIT 100"));
            if (batch.isEmpty()) break;
            var ids=batch.stream().map(UnModuleRecord::getId).toList();
            for (var row:batch) {
                var log=new UnModuleLog(); log.setSystemId(systemId); log.setTenantId(row.getTenantId());
                log.setModuleId(moduleId); log.setRecordId(row.getId()); log.setOperatorAccountId(actor);
                log.setActionCode("MODULE_DELETE"); log.setDetailJson(json.writeValueAsString(Map.of("choice",choice.name(),"name",row.getName())));
                recordLogs.addOrUpdate(log);
            }
            values.remove(new LambdaQueryWrapper<UnModuleRecordData>().in(UnModuleRecordData::getRecordId,ids));
            records.remove(new LambdaQueryWrapper<UnModuleRecord>().in(UnModuleRecord::getId,ids));
            afterId=batch.get(batch.size()-1).getId();
        }
        // 菜单失效但保留其配置及关联身份，不触碰流程配置或已关联的审批实例。
        var menuIds=bindings.list(new LambdaQueryWrapper<UnModuleMenu>().eq(UnModuleMenu::getModuleId,moduleId))
                .stream().map(UnModuleMenu::getMenuId).toList();
        if (!menuIds.isEmpty()) menus.update(new LambdaUpdateWrapper<UnSysMenu>().in(UnSysMenu::getId,menuIds).set(UnSysMenu::getStatus,"DISABLED"));
        menus.update(new LambdaUpdateWrapper<UnSysMenu>().eq(UnSysMenu::getSystemId,systemId)
                .eq(UnSysMenu::getCode,"MODULE_"+moduleId).set(UnSysMenu::getStatus,"DISABLED"));
        modules.removeById(moduleId);
        audit(context,actor,moduleId,"MODULE_DELETE",Map.of("count",count,"choice",choice.name()));
        systemConfigs.update(new LambdaUpdateWrapper<UnSysSystemConfig>().eq(UnSysSystemConfig::getSystemId,systemId)
                .setSql("permission_revision=permission_revision+1"));
        cache.invalidateAfterCommit("permission:"+systemId+":"+context.tenantId());
        return new Deleted(moduleId,count);
    }

    /** 备份仅供同一管理员删除同一模块；内容、当前数据或文件缺失均拒绝继续。 */
    private void verifyBackup(SystemManageDto.SystemContext context,Long actor,UnModule module,Long jobId) throws IOException {
        var job=jobId==null?null:jobs.getById(jobId);
        if (job==null || !context.systemId().equals(job.getSystemId()) || !context.tenantId().equals(job.getTenantId())
                || !actor.equals(job.getOperatorAccountId()) || !module.getId().equals(job.getModuleId())
                || !"DELETE_BACKUP".equals(job.getJobType()) || !"SUCCESS".equals(job.getStatus())) conflict("完整备份不存在或不属于当前操作");
        var file=files.getById(job.getResultFileId());
        if (file==null || !"ACTIVE".equals(file.getStatus())) conflict("备份文件不可用，请重新导出");
        var digest=digest();
        try (InputStream input=storage.open(file.getStorageKey()); var output=new DigestOutputStream(OutputStream.nullOutputStream(),digest)) {
            input.transferTo(output);
        }
        if (!file.getSha256().equals(HexFormat.of().formatHex(digest.digest()))) conflict("备份文件校验失败，请重新导出");
        var current=snapshot(module,OutputStream.nullOutputStream(),null,false);
        if (!current.sha256().equals(json.readTree(job.getConfigJson()).path("sha256").asText()))
            conflict("导出后模块配置或业务数据已变化，请重新导出后删除");
    }

    /** 稳定顺序流式写出字段及原始存储值，既用于备份也用于删除前摘要核对。 */
    private Snapshot snapshot(UnModule module,OutputStream output,String token,boolean authorize) throws IOException {
        var digest=digest(); long count=0; long afterId=0;
        var definitions=fields.list(new LambdaQueryWrapper<UnModuleField>().eq(UnModuleField::getModuleId,module.getId()).orderByAsc(UnModuleField::getId));
        Map<Long,ModulePermissionService.Access> access=new HashMap<>();
        try (var hashed=new DigestOutputStream(output,digest); JsonGenerator writer=json.getFactory().createGenerator(hashed)) {
            writer.writeStartObject(); writer.writeObjectField("module",module); writer.writeObjectField("fields",definitions);
            writer.writeArrayFieldStart("records");
            while (true) {
                var batch=records.list(recordQuery(module.getSystemId(),module.getId()).gt(UnModuleRecord::getId,afterId)
                        .orderByAsc(UnModuleRecord::getId).last("LIMIT 100"));
                if (batch.isEmpty()) break;
                for (var row:batch) {
                    if (authorize) {
                        var granted=access.computeIfAbsent(row.getTenantId(),tenant->permissions.require(token,module.getSystemId(),tenant,module.getId(),"EXPORT"));
                        granted.policy().requireOwner(row.getOwnerUserId());
                        if (definitions.stream().anyMatch(field->!granted.policy().exportable(field.getFieldKey())))
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"完整备份需要全部业务字段的导出权限");
                    }
                    writer.writeStartObject(); writer.writeObjectField("record",row);
                    writer.writeObjectField("values",definitions.isEmpty()?List.of():values.list(new LambdaQueryWrapper<UnModuleRecordData>()
                            .eq(UnModuleRecordData::getRecordId,row.getId())
                            .in(UnModuleRecordData::getFieldId,definitions.stream().map(UnModuleField::getId).toList())
                            .orderByAsc(UnModuleRecordData::getId)));
                    writer.writeEndObject(); count++;
                }
                afterId=batch.get(batch.size()-1).getId();
            }
            writer.writeEndArray(); writer.writeEndObject();
        }
        return new Snapshot(HexFormat.of().formatHex(digest.digest()),count);
    }

    private UnModule owned(SystemManageDto.SystemContext context,Long moduleId) {
        var module=modules.queryById(moduleId);
        if (!context.systemId().equals(module.getSystemId()) || !context.tenantId().equals(module.getTenantId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"只能删除当前租户拥有的模块");
        return module;
    }
    private LambdaQueryWrapper<UnModuleRecord> recordQuery(Long systemId,Long moduleId) {
        return new LambdaQueryWrapper<UnModuleRecord>().eq(UnModuleRecord::getSystemId,systemId).eq(UnModuleRecord::getModuleId,moduleId);
    }
    private MessageDigest digest() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
    private void audit(SystemManageDto.SystemContext context,Long actor,Long moduleId,String action,Object detail) throws IOException {
        var log=new UnSysLogOpt(); log.setSystemId(context.systemId()); log.setTenantId(context.tenantId());
        log.setAccountId(actor); log.setActionCode(action); log.setObjectType("MODULE"); log.setObjectId(moduleId);
        log.setDetailJson(json.writeValueAsString(detail)); logs.addOrUpdate(log);
    }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT,message); }
}
