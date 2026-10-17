package com.unique.unexamine.flow.manage.repository;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.unexamine.flow.base.entity.FlowInstance;
import com.unique.unexamine.flow.base.entity.FlowJob;
import com.unique.unexamine.flow.base.entity.FlowTask;
import com.unique.unexamine.flow.base.service.FlowInstanceBaseService;
import com.unique.unexamine.flow.base.service.FlowJobBaseService;
import com.unique.unexamine.flow.base.service.FlowTaskBaseService;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

/**
 * Flow 运行态的条件更新入口。
 * 所有写入都经生成的 Base Service；这里仅封装空值清理、版本条件和批量状态更新的业务范围。
 */
@Repository
public class FlowRuntimeStateRepository {
    private final FlowInstanceBaseService instanceService;
    private final FlowJobBaseService jobService;
    private final FlowTaskBaseService taskService;

    public FlowRuntimeStateRepository(FlowInstanceBaseService instanceService, FlowJobBaseService jobService,
            FlowTaskBaseService taskService) {
        this.instanceService = instanceService;
        this.jobService = jobService;
        this.taskService = taskService;
    }

    /** 转交后清除旧领取时间；调用方已经在同一事务中更新任务并持有新版本。 */
    public void clearTransferredClaim(FlowTask task) {
        taskService.update(Wrappers.<FlowTask>lambdaUpdate().eq(FlowTask::getId, task.getId())
                .eq(FlowTask::getVersion, task.getVersion()).set(FlowTask::getClaimTime, null));
    }

    /** 领取或释放时一次更新处理人、领取标记和快照，避免 Base 非空更新遗漏需要清空的列。 */
    public int changeClaim(FlowTask task, boolean claim, Long accountId, Long tenantMemberId,
            String assigneeSnapshotJson, LocalDateTime now) {
        return taskService.update(Wrappers.<FlowTask>lambdaUpdate()
                .eq(FlowTask::getId, task.getId()).eq(FlowTask::getVersion, task.getVersion())
                .eq(FlowTask::getStatus, "PENDING")
                .set(FlowTask::getAssigneeAccountId, claim ? accountId : null)
                .set(FlowTask::getAssigneeTenantMemberId, claim ? tenantMemberId : null)
                .set(FlowTask::getClaimTime, claim ? now : null)
                .set(FlowTask::getAssigneeSnapshotJson, assigneeSnapshotJson)
                .set(FlowTask::getUpdateTime, now).set(FlowTask::getVersion, task.getVersion() + 1));
    }

    /** 超时转交完成后清除旧截止时间与领取标记，只作用于调用方刚更新的版本。 */
    public void clearTimeoutAndClaim(FlowTask task) {
        taskService.update(Wrappers.<FlowTask>lambdaUpdate().eq(FlowTask::getId, task.getId())
                .eq(FlowTask::getVersion, task.getVersion())
                .set(FlowTask::getDueTime, null).set(FlowTask::getClaimTime, null));
    }

    /** 作业成功结束时清除旧错误和锁信息，保留失败作业的错误事实。 */
    public void clearFinishedJobMetadata(FlowJob job) {
        jobService.update(Wrappers.<FlowJob>lambdaUpdate().eq(FlowJob::getId, job.getId())
                .set(FlowJob::getLastErrorCode, null).set(FlowJob::getLastErrorMessage, null)
                .set(FlowJob::getLockTime, null).set(FlowJob::getLockedBy, null));
    }

    /** 调用方已锁定实例；取消未执行作业，指定任务时仅取消该任务的到期作业。 */
    public void cancelPendingJobs(Long instanceId, Long taskId) {
        var pending = Wrappers.<FlowJob>lambdaUpdate().eq(FlowJob::getInstanceId, instanceId)
                .in(FlowJob::getStatus, "PENDING", "SUSPENDED");
        // 到期作业的任务 ID 保存在负载 JSON 中；只取消当前任务的待执行作业。
        if (taskId != null) pending.eq(FlowJob::getJobType, "TASK_TIMEOUT")
                .apply("JSON_EXTRACT(payload_json, '$.taskId') = {0}", taskId);
        jobService.update(pending.set(FlowJob::getStatus, "CANCELLED")
                .set(FlowJob::getCompleteTime, LocalDateTime.now()).set(FlowJob::getLockTime, null)
                .set(FlowJob::getLockedBy, null).setSql("version = version + 1"));
    }

    /** 异常恢复时清空实例错误，不改动已记录的异常历史。 */
    public void clearInstanceError(Long instanceId) {
        instanceService.update(Wrappers.<FlowInstance>lambdaUpdate()
                .eq(FlowInstance::getId, instanceId)
                .set(FlowInstance::getErrorCode, null).set(FlowInstance::getErrorMessage, null));
    }
}
