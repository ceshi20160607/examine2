package com.unique.unexamine.sys.module.manage.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.unique.unexamine.core.bo.SearchBO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.core.enums.SystemCodeEnum;
import com.unique.unexamine.core.exception.BaseException;
import com.unique.unexamine.core.field.FileBatchId;
import com.unique.unexamine.core.event.BusinessEventContext;
import com.unique.unexamine.core.event.BusinessEventPublisher;
import com.unique.unexamine.core.file.base.entity.UnCommonFile;
import com.unique.unexamine.core.file.base.service.IUnCommonFileService;
import com.unique.unexamine.sys.base.entity.UnSysLogOpt;
import com.unique.unexamine.sys.base.service.IUnSysLogOptService;
import com.unique.unexamine.file.FileStorage;
import com.unique.unexamine.sys.manage.service.SystemManageService;
import com.unique.unexamine.sys.manage.service.SystemMessageTemplateService;
import com.unique.unexamine.sys.module.base.entity.UnModuleExchangeJob;
import com.unique.unexamine.sys.module.base.service.IUnModuleExchangeJobService;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportRequest;
import com.unique.unexamine.sys.module.manage.dto.ModuleExportDto.ExportJobView;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 模块 CSV 后台导出作业；先持久化提交，再由有限工作线程生成私有结果文件。 */
@Service
public class ModuleExportJobService {
    private static final String QUEUE="examine:module-export:queue";
    private final ModuleExportService exports;
    private final ModulePermissionService permissions;
    private final ModuleConfigService config;
    private final SystemManageService systems;
    private final IUnModuleExchangeJobService jobs;
    private final IUnCommonFileService files;
    private final IUnSysLogOptService logs;
    private final FileStorage storage;
    private final ObjectMapper json;
    private final BusinessEventPublisher events;
    private final StringRedisTemplate redis;
    private final TransactionTemplate transaction;
    private final SystemMessageTemplateService messages;
    private volatile boolean running;
    private Thread worker;

    /** 导出权限在提交、执行和查看结果时分别检查。 */
    public ModuleExportJobService(ModuleExportService exports, ModulePermissionService permissions,ModuleConfigService config,
            SystemManageService systems, IUnModuleExchangeJobService jobs, IUnCommonFileService files,
            IUnSysLogOptService logs,FileStorage storage, ObjectMapper json,
            BusinessEventPublisher events, StringRedisTemplate redis,PlatformTransactionManager manager,
            SystemMessageTemplateService messages) {
        this.exports=exports; this.permissions=permissions; this.config=config;
        this.systems=systems; this.jobs=jobs;
        this.files=files; this.logs=logs; this.storage=storage; this.json=json; this.events=events; this.redis=redis;
        this.transaction=new TransactionTemplate(manager);
        this.messages=messages;
    }

    /** 请求入库后把作业 ID 放入 Redis 队列，凭据只在 Redis 短期保存。 */
    public ExportJobView submit(String token, Long systemId, Long tenantId, Long moduleId, ExportRequest request) {
        if (request==null || request.query()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_VALID,"导出查询不能为空");
        var access=permissions.require(token,systemId,tenantId,moduleId,"EXPORT");
        var job=new UnModuleExchangeJob();
        job.setSystemId(systemId); job.setTenantId(access.context().tenantId()); job.setModuleId(moduleId);
        job.setOperatorAccountId(access.accountId()); job.setJobType("EXPORT"); job.setStatus("PENDING");
        job.setConfigJson(write(Map.of("request",request)));
        job.setTotalCount(0); job.setSuccessCount(0); job.setFailureCount(0);
        job.setCreateTime(LocalDateTime.now()); job.setUpdateTime(LocalDateTime.now()); jobs.addOrUpdate(job);
        try {
            Long queued=redis.opsForList().size(QUEUE);
            if (queued!=null && queued>100)
                throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"后台导出队列已满");
            redis.opsForValue().set(tokenKey(job.getId()),token,Duration.ofHours(4));
            redis.opsForList().rightPush(QUEUE,job.getId().toString());
        } catch (RuntimeException ex) { fail(job.getId(),"后台导出队列不可用，请重新提交"); }
        return view(jobs.getById(job.getId()));
    }

    /** 结果仅由原操作人或本租户管理员查看，字段与动作授权仍须有效。 */
    public ExportJobView job(String token, Long systemId, Long tenantId, Long moduleId, Long jobId) {
        var context=systems.context(token,systemId,tenantId);
        var row=jobs.getById(jobId);
        if (row==null || !"EXPORT".equals(row.getJobType()) || !systemId.equals(row.getSystemId())
                || !context.tenantId().equals(row.getTenantId()) || !moduleId.equals(row.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"导出作业不存在");
        if (!context.owner() && !context.tenantAdmin() && !context.accountId().equals(row.getOperatorAccountId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"没有导出作业读取权限");
        permissions.require(token,systemId,context.tenantId(),moduleId,"EXPORT");
        return view(row);
    }

    /** 作业列表按租户隔离，普通成员仅看本人提交的导出。 */
    public Page<ExportJobView> listJobs(String token,Long systemId,Long tenantId,Long moduleId,SearchBO search) {
        var access=permissions.require(token,systemId,tenantId,moduleId,"EXPORT");
        var context=access.context();
        var where=new LambdaQueryWrapper<UnModuleExchangeJob>()
                .eq(UnModuleExchangeJob::getSystemId,systemId)
                .eq(UnModuleExchangeJob::getTenantId,context.tenantId())
                .eq(UnModuleExchangeJob::getModuleId,moduleId)
                .eq(UnModuleExchangeJob::getJobType,"EXPORT")
                .orderByDesc(UnModuleExchangeJob::getId);
        if (!context.owner() && !context.tenantAdmin())
            where.eq(UnModuleExchangeJob::getOperatorAccountId,context.accountId());
        var source=jobs.page(search==null?new SearchBO().parse():search.parse(),where);
        return new Page<ExportJobView>(source.getCurrent(),source.getSize(),source.getTotal())
                .setRecords(source.getRecords().stream().map(this::view).toList());
    }

    /** 下载时再次验证作业、导出动作和文件归属，不依赖公共上传文件的访问规则。 */
    public ExportContent content(String token,Long systemId,Long tenantId,Long moduleId,Long jobId) throws IOException {
        ExportJobView result=job(token,systemId,tenantId,moduleId,jobId);
        var context=systems.context(token,systemId,tenantId);
        if (!"SUCCESS".equals(result.status()) || result.resultFileId()==null)
            throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导出结果尚不可下载");
        var file=files.getById(result.resultFileId());
        if (file==null || !"EXPORT_RESULT".equals(file.getStatus()) || !systemId.equals(file.getSystemId())
                || !context.tenantId().equals(file.getTenantId()) || !moduleId.equals(file.getModuleId()))
            throw new BaseException(SystemCodeEnum.SYSTEM_NO_FOUND,"导出结果文件不存在");
        // 文件生成后若字段或动作授权被收回，不允许通过旧作业取回已撤销的列。
        var access=permissions.require(token,systemId,context.tenantId(),moduleId,"EXPORT");
        var current=config.currentFields(moduleId).stream()
                .collect(java.util.stream.Collectors.toMap(com.unique.unexamine.sys.module.base.entity.UnModuleField::getId,
                        field->field));
        var allowed=access.definition().settings().fieldIds();
        try {
            var columns=json.readTree(jobs.getById(jobId).getConfigJson()).path("columns");
            if (!columns.isArray()) throw new IllegalArgumentException("导出列快照缺失");
            for (var column:columns) {
                long fieldId=column.path("fieldId").asLong();
                String fieldKey=column.path("code").asText();
                var field=current.get(fieldId);
                if (field==null || !fieldKey.equals(field.getFieldKey())
                        || !access.policy().exportable(fieldKey)
                        || !allowed.isEmpty() && !allowed.contains(fieldId))
                    throw new BaseException(SystemCodeEnum.SYSTEM_NO_AUTH,"导出结果包含当前无权下载的字段");
            }
        } catch (BaseException ex) { throw ex; }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_CONFLICT,"导出列快照无效",ex); }
        return new ExportContent(file.getOriginalName(),file.getSizeBytes(),storage.open(file.getStorageKey()));
    }

    /** 文件流由下载响应关闭。 */
    public record ExportContent(String name,Long size,InputStream stream) {}

    /** 导出使用原提交者身份重新鉴权；失败原因留在作业中供页面展示。 */
    private void run(Long jobId,String token,Long systemId,Long tenantId,Long moduleId,ExportRequest request) {
        Path temporary=null;
        try {
            var started=LocalDateTime.now();
            boolean claimed=jobs.update(new LambdaUpdateWrapper<UnModuleExchangeJob>()
                    .eq(UnModuleExchangeJob::getId,jobId).eq(UnModuleExchangeJob::getStatus,"PENDING")
                    .set(UnModuleExchangeJob::getStatus,"RUNNING")
                    .set(UnModuleExchangeJob::getStartTime,started)
                    .set(UnModuleExchangeJob::getUpdateTime,started));
            if (!claimed) return;
            var job=jobs.getById(jobId);
            temporary=Files.createTempFile("module-export-",".csv");
            ModuleExportService.ExportResult result;
            try (var output=Files.newOutputStream(temporary)) {
                result=exports.exportTo(token,systemId,tenantId,moduleId,request,output);
            }
            FileStorage.StoredFile stored;
            try (var input=Files.newInputStream(temporary)) { stored=storage.store(input); }
            try {
                transaction.executeWithoutResult(status -> {
                    var file=new UnCommonFile(); file.setScopeType("SYSTEM"); file.setSystemId(systemId);
                    file.setTenantId(tenantId); file.setModuleId(moduleId);
                    file.setUploaderAccountId(job.getOperatorAccountId()); file.setBatchId(FileBatchId.create());
                    file.setOriginalName("module-"+moduleId+"-export-"+jobId+".csv");
                    file.setContentType("text/csv"); file.setSizeBytes(stored.size()); file.setSha256(stored.sha256());
                    // 导出结果只经专用下载口重新鉴权，不能被公共上传文件接口直接读取。
                    file.setStorageType("LOCAL"); file.setStorageKey(stored.key()); file.setStatus("EXPORT_RESULT");
                    files.addOrUpdate(file);
                    job.setResultFileId(file.getId()); job.setStatus("SUCCESS");
                    job.setConfigJson(write(Map.of("request",request,"columns",result.columns())));
                    job.setTotalCount(Math.toIntExact(result.recordCount()));
                    job.setSuccessCount(Math.toIntExact(result.recordCount()));
                    job.setFinishTime(LocalDateTime.now()); job.setUpdateTime(LocalDateTime.now()); jobs.updateById(job);
                    var log=new UnSysLogOpt(); log.setSystemId(systemId); log.setTenantId(tenantId);
                    log.setAccountId(job.getOperatorAccountId()); log.setActionCode("MODULE_EXPORT");
                    log.setObjectType("MODULE"); log.setObjectId(moduleId);
                    log.setDetailJson(write(Map.of("jobId",jobId,"recordCount",result.recordCount(),"fileId",file.getId())));
                    log.setCreateTime(LocalDateTime.now()); logs.addOrUpdate(log);
                    events.publish(new BusinessEventContext("EXPORT_COMPLETED",systemId,tenantId,moduleId,
                            null,job.getOperatorAccountId(),null,Map.of(),
                            Map.of("jobId",jobId,"moduleId",moduleId,"recordCount",result.recordCount())));
                });
            } catch (RuntimeException ex) {
                storage.delete(stored.key());
                throw ex;
            }
            notifyResult(jobId);
        } catch (Exception ex) {
            fail(jobId,ex.getMessage()==null?"导出失败":ex.getMessage());
        } finally {
            try { redis.delete(tokenKey(jobId)); }
            catch (RuntimeException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("导出凭据清理失败",ex); }
            if (temporary!=null) try { Files.deleteIfExists(temporary); }
            catch (IOException ex) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("导出临时文件清理失败",ex); }
        }
    }

    private void fail(Long jobId,String reason) {
        var job=jobs.getById(jobId);
        job.setStatus("FAILED"); job.setFinishTime(LocalDateTime.now()); job.setUpdateTime(LocalDateTime.now());
        try {
            var detail=(com.fasterxml.jackson.databind.node.ObjectNode)json.readTree(job.getConfigJson());
            detail.put("failureReason",reason.length()>500?reason.substring(0,500):reason);
            job.setConfigJson(write(detail));
        } catch (Exception ex) {
            job.setConfigJson(write(Map.of("failureReason",reason.length()>500?reason.substring(0,500):reason)));
        }
        jobs.updateById(job);
        notifyResult(jobId);
    }

    /** 通知独立于已提交的导出结果，模板配置异常只写日志，不改写作业终态。 */
    private void notifyResult(Long jobId) {
        try {
            UnModuleExchangeJob job=jobs.getById(jobId);
            if (job==null || !Set.of("SUCCESS","FAILED").contains(job.getStatus())) return;
            messages.exchangeCompleted(job.getSystemId(),job.getTenantId(),job.getOperatorAccountId(),
                    job.getModuleId(),job.getId(),"EXPORT",job.getStatus(),
                    job.getTotalCount()==null?0:job.getTotalCount(),
                    job.getSuccessCount()==null?0:job.getSuccessCount(),
                    job.getFailureCount()==null?0:job.getFailureCount());
        } catch (RuntimeException ex) {
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("导出结果通知失败，作业编号 {}",jobId,ex);
        }
    }

    /** 启动时重新入队仍有凭据的等待作业，并标记上次进程中断的执行中作业。 */
    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterrupted() {
        for (var row:jobs.list(new LambdaQueryWrapper<UnModuleExchangeJob>()
                .eq(UnModuleExchangeJob::getJobType,"EXPORT")
                .eq(UnModuleExchangeJob::getStatus,"RUNNING")))
            fail(row.getId(),"服务重启中断了导出，请重新提交");
        for (var row:jobs.list(new LambdaQueryWrapper<UnModuleExchangeJob>()
                .eq(UnModuleExchangeJob::getJobType,"EXPORT")
                .eq(UnModuleExchangeJob::getStatus,"PENDING"))) {
            if (Boolean.TRUE.equals(redis.hasKey(tokenKey(row.getId()))))
                redis.opsForList().rightPush(QUEUE,row.getId().toString());
            else fail(row.getId(),"导出凭据已过期，请重新提交");
        }
        running=true;
        worker=new Thread(this::consume,"module-export"); worker.setDaemon(true); worker.start();
    }

    /** 单个后端进程从 Redis 持续取出作业；重复入队由数据库状态条件更新抵消。 */
    private void consume() {
        while (running) {
            String value=null;
            try {
            value=redis.opsForList().leftPop(QUEUE,Duration.ofSeconds(5));
            if (value==null) continue;
            Long jobId=Long.valueOf(value);
            var job=jobs.getById(jobId);
            if (job==null || !"PENDING".equals(job.getStatus())) continue;
            String token=redis.opsForValue().get(tokenKey(jobId));
            if (token==null) { fail(jobId,"导出凭据已过期，请重新提交"); continue; }
            ExportRequest request;
            try { request=json.treeToValue(json.readTree(job.getConfigJson()).path("request"),ExportRequest.class); }
            catch (Exception ex) { fail(jobId,"导出请求快照无效"); continue; }
            run(jobId,token,job.getSystemId(),job.getTenantId(),job.getModuleId(),request);
        } catch (Exception ex) {
            if (!running || Thread.currentThread().isInterrupted()) break;
            org.slf4j.LoggerFactory.getLogger(getClass()).warn("后台导出消费失败",ex);
            if (value!=null) try { redis.opsForList().rightPush(QUEUE,value); }
            catch (RuntimeException retry) { org.slf4j.LoggerFactory.getLogger(getClass()).warn("导出作业重新入队失败",retry); }
            try { Thread.sleep(1000); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
        }
        }
    }

    private String tokenKey(Long jobId) { return "examine:module-export:token:"+jobId; }

    private ExportJobView view(UnModuleExchangeJob job) {
        String reason=null;
        if ("FAILED".equals(job.getStatus())) try {
            reason=json.readTree(job.getConfigJson()).path("failureReason").asText(null);
        } catch (Exception ex) { reason="导出失败"; }
        return new ExportJobView(job.getId(),job.getStatus(),job.getTotalCount(),job.getResultFileId(),
                reason,job.getCreateTime(),job.getFinishTime());
    }

    private String write(Object value) {
        try { return json.writeValueAsString(value); }
        catch (Exception ex) { throw new BaseException(SystemCodeEnum.SYSTEM_ERROR,"导出作业无法编码",ex); }
    }

    /** 关闭进程时停止消费，未完成的等待作业由下一次启动恢复。 */
    @PreDestroy public void shutdown() { running=false; if (worker!=null) worker.interrupt(); }
}
