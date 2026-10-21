package com.unique.unexamine.flow.manage;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.unique.unexamine.flow.base.entity.UnFlowExample;
import com.unique.unexamine.flow.base.entity.UnFlowException;
import com.unique.unexamine.flow.base.entity.UnFlowJob;
import com.unique.unexamine.flow.base.entity.UnFlowTask;
import com.unique.unexamine.flow.base.service.IUnFlowExampleService;
import com.unique.unexamine.flow.base.service.IUnFlowExceptionService;
import com.unique.unexamine.flow.base.service.IUnFlowJobService;
import com.unique.unexamine.flow.base.service.IUnFlowTaskService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.sys.manage.SystemSessionAccess;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.unique.unexamine.flow.manage.FlowOperations.*;

/** 流程超时任务与异常处置入口；到期任务逐项独立事务处理。 */
@Tag(name = "流程定时与异常", description = "流程定时任务和异常记录。")
@RestController
@EnableScheduling
@RequestMapping("/api/tenants/{tenantId}/flows")
public class FlowJobController {
    private static final Logger log = LoggerFactory.getLogger(FlowJobController.class);
    private final SystemSessionAccess access;
    private final IUnFlowExampleService examples;
    private final IUnFlowTaskService tasks;
    private final IUnFlowJobService jobs;
    private final IUnFlowExceptionService exceptions;
    private final JdbcTemplate jdbc;
    private final FlowOperations ops;
    private final TransactionTemplate transactions;

    public FlowJobController(SystemSessionAccess access, IUnFlowExampleService examples,
                             IUnFlowTaskService tasks, IUnFlowJobService jobs,
                             IUnFlowExceptionService exceptions, JdbcTemplate jdbc,
                             FlowOperations ops, PlatformTransactionManager transactionManager) {
        this.access = access;
        this.examples = examples;
        this.tasks = tasks;
        this.jobs = jobs;
        this.exceptions = exceptions;
        this.jdbc = jdbc;
        this.ops = ops;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    /** 管理员为当前待办安排一次超时检查，同一待办只能保留一项待执行任务。 */
    @Operation(summary = "处理定时任务（流程定时与异常）")
    @PostMapping("/examples/{exampleId}/tasks/{taskId}/timeout")
    @Transactional
    public UnFlowJob scheduleTimeout(@PathVariable long tenantId, @PathVariable long exampleId,
                                     @PathVariable long taskId, @Valid @RequestBody TimeoutInput body,
                                     HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        UnFlowExample example = ops.lockExample(tenantId, exampleId);
        UnFlowTask task = tasks.getById(taskId);
        if (!"RUNNING".equals(example.getStatus()) || task == null
                || !flowIdEquals(task.getExampleId(), exampleId) || !"PENDING".equals(task.getStatus()))
            conflict("只能给当前运行的待办安排超时");
        LocalDateTime executeTime = body.executeTime().truncatedTo(ChronoUnit.MILLIS);
        if (!executeTime.isAfter(LocalDateTime.now())) bad("执行时间须在当前时间之后");
        UnFlowJob existing = jobs.getOne(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getTaskId, taskId).eq(UnFlowJob::getStatus, "PENDING"));
        if (existing != null) {
            if (existing.getExecuteTime().equals(executeTime)) return existing;
            conflict("此待办已有未执行的超时任务");
        }
        UnFlowJob job = new UnFlowJob();
        job.setExampleId(exampleId);
        job.setTaskId(taskId);
        job.setJobType("TIMEOUT");
        job.setExecuteTime(executeTime);
        job.setStatus("PENDING");
        job.setRetryCount(0);
        jobs.save(job);
        task.setDueTime(executeTime);
        tasks.updateById(task);
        ops.event(exampleId, taskId, null, "TIMEOUT_SCHEDULED", access.tenantMember(request, tenantId).getMemberId(),
                "处理期限：" + executeTime);
        return jobs.getById(job.getId());
    }

    @Operation(summary = "查询定时任务（流程定时与异常）")
    @GetMapping("/examples/{exampleId}/jobs")
    public List<UnFlowJob> jobList(@PathVariable long tenantId, @PathVariable long exampleId,
                                   HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        ops.exampleInTenant(tenantId, exampleId);
        return jobs.list(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getExampleId, exampleId).orderByAsc(UnFlowJob::getId));
    }

    @Operation(summary = "查询流程异常（流程定时与异常）")
    @GetMapping("/examples/{exampleId}/exceptions")
    public List<UnFlowException> exceptionList(@PathVariable long tenantId, @PathVariable long exampleId,
                                               HttpServletRequest request) {
        access.tenantAdmin(request, tenantId);
        ops.exampleInTenant(tenantId, exampleId);
        return exceptions.list(new LambdaQueryWrapper<UnFlowException>()
                .eq(UnFlowException::getExampleId, exampleId).orderByAsc(UnFlowException::getId));
    }

    /** 处置只确认异常并留下处理人和说明，不把失败实例伪装为恢复成功。 */
    @Operation(summary = "处理流程异常（流程定时与异常）")
    @PostMapping("/examples/{exampleId}/exceptions/{exceptionId}/resolve")
    @Transactional
    public UnFlowException resolveException(@PathVariable long tenantId, @PathVariable long exampleId,
                                             @PathVariable long exceptionId,
                                             @Valid @RequestBody ResolveInput body, HttpServletRequest request) {
        long memberId = access.tenantAdmin(request, tenantId).getMemberId();
        ops.lockExample(tenantId, exampleId);
        UnFlowException incident = exceptions.getById(exceptionId);
        if (incident == null || !flowIdEquals(incident.getExampleId(), exampleId)) notFound("流程异常不存在");
        if ("RESOLVED".equals(incident.getStatus())) {
            if (flowIdEquals(incident.getHandleMemberId(), memberId)
                    && body.handleRemark().equals(incident.getHandleRemark())) return incident;
            conflict("异常已由其他处置记录确认");
        }
        if (!"OPEN".equals(incident.getStatus())) conflict("异常状态无效");
        incident.setStatus("RESOLVED");
        incident.setHandleMemberId(memberId);
        incident.setHandleRemark(body.handleRemark());
        incident.setHandleTime(LocalDateTime.now());
        exceptions.updateById(incident);
        ops.event(exampleId, incident.getTaskId(), null, "EXCEPTION_RESOLVED", memberId, body.handleRemark());
        return exceptions.getById(exceptionId);
    }

    /** 后台按时间扫描到期任务；每个任务在独立事务中锁住实例后处理。 */
    @Scheduled(fixedDelay = 2000)
    public void runDueJobs() {
        List<UnFlowJob> due = jobs.list(new LambdaQueryWrapper<UnFlowJob>()
                .eq(UnFlowJob::getStatus, "PENDING").le(UnFlowJob::getExecuteTime, LocalDateTime.now())
                .orderByAsc(UnFlowJob::getExecuteTime).last("LIMIT 20"));
        for (UnFlowJob item : due) {
            try {
                transactions.executeWithoutResult(status -> processDueJob(item.getId()));
            } catch (RuntimeException ex) {
                log.error("流程定时任务 {} 执行失败", item.getId(), ex);
                try { transactions.executeWithoutResult(status -> failJob(item.getId(), ex)); }
                catch (RuntimeException writeError) { log.error("流程定时任务 {} 失败记录未写入", item.getId(), writeError); }
            }
        }
    }

    private void processDueJob(long jobId) {
        UnFlowJob initial = jobs.getById(jobId);
        if (initial == null) return;
        UnFlowExample example = examples.getById(initial.getExampleId());
        if (example == null) return;
        // 锁定读取得到最新状态，避免审批动作与定时扫描并发时读取到事务旧快照。
        List<Map<String, Object>> exampleRows = jdbc.queryForList(
                "SELECT status FROM un_flow_example WHERE id = ? AND tenant_id = ? FOR UPDATE",
                example.getId(), example.getTenantId());
        if (exampleRows.isEmpty()) return;
        List<Map<String, Object>> jobRows = jdbc.queryForList(
                "SELECT status FROM un_flow_job WHERE id = ? FOR UPDATE", jobId);
        if (jobRows.isEmpty()) return;
        UnFlowJob job = jobs.getById(jobId);
        if (job == null || !"PENDING".equals(jobRows.getFirst().get("status"))
                || job.getExecuteTime().isAfter(LocalDateTime.now())) return;
        LocalDateTime now = LocalDateTime.now();
        UnFlowTask task = job.getTaskId() == null ? null : tasks.getById(job.getTaskId());
        List<Map<String, Object>> taskRows = task == null ? List.of() : jdbc.queryForList(
                "SELECT status FROM un_flow_task WHERE id = ? AND example_id = ? FOR UPDATE",
                task.getId(), example.getId());
        if (!"RUNNING".equals(exampleRows.getFirst().get("status")) || taskRows.isEmpty()
                || !"PENDING".equals(taskRows.getFirst().get("status"))) {
            job.setStatus("CANCELLED");
            job.setFinishTime(now);
            jobs.updateById(job);
            return;
        }
        jdbc.update("UPDATE un_flow_task SET status = 'EXPIRED', finish_time = ? WHERE id = ?", now, task.getId());
        ops.cancelCandidates(task.getId(), now);
        jdbc.update("UPDATE un_flow_example SET status = 'FAILED', current_node_key = NULL, finish_time = ? WHERE id = ?",
                now, example.getId());
        job.setStatus("DONE");
        job.setFinishTime(now);
        jobs.updateById(job);
        ops.recordException(example.getId(), task.getId(), "TIMEOUT", "任务超过处理期限，流程已停止");
        ops.event(example.getId(), task.getId(), null, "TASK_EXPIRED", null, "任务超时，流程已停止");
    }

    private void failJob(long jobId, RuntimeException error) {
        UnFlowJob job = jobs.getById(jobId);
        if (job == null || !"PENDING".equals(job.getStatus())) return;
        UnFlowExample example = examples.getById(job.getExampleId());
        if (example != null) ops.lockExample(example.getTenantId(), example.getId());
        jdbc.queryForList("SELECT id FROM un_flow_job WHERE id = ? FOR UPDATE", jobId);
        job = jobs.getById(jobId);
        if (job == null || !"PENDING".equals(job.getStatus())) return;
        job.setStatus("FAILED");
        job.setRetryCount((job.getRetryCount() == null ? 0 : job.getRetryCount()) + 1);
        job.setLastError(error.getMessage() == null ? "定时任务执行失败" : error.getMessage().substring(0,
                Math.min(1000, error.getMessage().length())));
        job.setFinishTime(LocalDateTime.now());
        jobs.updateById(job);
        if (example != null) {
            LocalDateTime now = LocalDateTime.now();
            UnFlowTask task = job.getTaskId() == null ? null : tasks.getById(job.getTaskId());
            if (task != null && "PENDING".equals(task.getStatus())) {
                task.setStatus("EXPIRED");
                task.setFinishTime(now);
                tasks.updateById(task);
                ops.cancelCandidates(task.getId(), now);
            }
            if ("RUNNING".equals(example.getStatus())) {
                example.setStatus("FAILED");
                example.setCurrentNodeKey(null);
                example.setFinishTime(now);
                examples.updateById(example);
            }
            ops.recordException(example.getId(), job.getTaskId(), "JOB_ERROR", job.getLastError());
            ops.event(example.getId(), job.getTaskId(), null, "JOB_FAILED", null, job.getLastError());
        }
    }

    /** 超时安排及异常处置仅接收业务所需参数。 */
    public record TimeoutInput(@NotNull LocalDateTime executeTime) {}
    public record ResolveInput(@NotBlank @Size(max = 1000) String handleRemark) {}
}
