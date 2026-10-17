package com.unique.unexamine.flow.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.flow.base.entity.*;
import com.unique.unexamine.flow.base.service.*;
import com.unique.unexamine.shared.manage.web.DomainException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/** 当前节点前插的持久暂停协议。调用方必须先锁实例，与人工处理及作业共享同一事务。 */
@Service
public class FlowNodeSuspensionService {
    private final FlowTaskBaseService tasks;
    private final FlowJobBaseService jobs;
    private final FlowExecutionBaseService executions;
    private final FlowActionBaseService actions;
    private final ObjectMapper mapper;

    public FlowNodeSuspensionService(FlowTaskBaseService tasks, FlowJobBaseService jobs,
            FlowExecutionBaseService executions, FlowActionBaseService actions, ObjectMapper mapper) {
        this.tasks = tasks; this.jobs = jobs; this.executions = executions; this.actions = actions;
        this.mapper = FlowJsonStorage.mapper(mapper);
    }

    /** 同一任务的身份、候选、字段提交和已完成事实不复制；这里只保存恢复所需的原状态与剩余时限。 */
    public record TaskPause(Long taskId, String originalStatus, Integer pausedVersion, Long remainingMillis) { }
    public record JobPause(Long jobId, Integer pausedVersion, long remainingMillis) { }
    public record Frame(String anchorNodeKey, Long executionId, Integer executionVersion,
            String pauseTime, List<TaskPause> tasks, List<JobPause> jobs) { }
    public record Restoration(Long frameActionId, Long executionId, List<Long> taskIds) { }
    private record ActiveFrame(FlowAction action, Frame frame) { }

    /** 已同意项保持原样，排队项仍是排队项；暂停任务与其尚未执行的超时行，不能取消后重新安排同键作业。 */
    public Map<String, Object> pause(FlowInstance instance) {
        String anchor = instance.getCurrentNodeKey();
        LocalDateTime now = LocalDateTime.now();
        List<FlowTask> group = tasks.selectList(Wrappers.<FlowTask>lambdaQuery()
                .eq(FlowTask::getInstanceId, instance.getId()).eq(FlowTask::getNodeKey, anchor)
                .in(FlowTask::getStatus, "PENDING", "QUEUED").orderByAsc(FlowTask::getId).last("FOR UPDATE"));
        if (group.isEmpty()) throw invalid();
        Set<Long> ids = new LinkedHashSet<>();
        List<TaskPause> pausedTasks = new ArrayList<>();
        for (FlowTask task : group) {
            if (!Set.of("APPROVAL", "FORM_TASK").contains(task.getTaskType())) throw invalid();
            String status = task.getStatus();
            if ("QUEUED".equals(status) && task.getDueTime() != null) throw invalid();
            Long remaining = task.getDueTime() == null ? null : remaining(now, task.getDueTime());
            task.setStatus("SUSPENDED"); task.setUpdateTime(now);
            if (tasks.updateById(task) != 1) throw invalid();
            ids.add(task.getId());
            pausedTasks.add(new TaskPause(task.getId(), status, task.getVersion(), remaining));
        }
        List<JobPause> pausedJobs = new ArrayList<>();
        for (FlowJob job : jobs.selectList(Wrappers.<FlowJob>lambdaQuery()
                .eq(FlowJob::getInstanceId, instance.getId()).eq(FlowJob::getNodeKey, anchor)
                .eq(FlowJob::getJobType, "TASK_TIMEOUT").eq(FlowJob::getStatus, "PENDING")
                .orderByAsc(FlowJob::getId).last("FOR UPDATE"))) {
            Long taskId = mapper.convertValue(read(job.getPayloadJson()).get("taskId"), Long.class);
            if (!ids.contains(taskId)) continue;
            long remaining = remaining(now, job.getNextRunTime());
            job.setStatus("SUSPENDED"); job.setUpdateTime(now);
            if (jobs.updateById(job) != 1) throw invalid();
            pausedJobs.add(new JobPause(job.getId(), job.getVersion(), remaining));
        }
        List<FlowExecution> active = executions.selectList(Wrappers.<FlowExecution>lambdaQuery()
                .eq(FlowExecution::getInstanceId, instance.getId()).eq(FlowExecution::getNodeKey, anchor)
                .eq(FlowExecution::getStatus, "ACTIVE").orderByAsc(FlowExecution::getId).last("FOR UPDATE"));
        if (active.size() != 1) throw invalid();
        FlowExecution execution = active.getFirst();
        execution.setStatus("SUSPENDED");
        if (executions.updateById(execution) != 1) throw invalid();
        return mapper.convertValue(new Frame(anchor, execution.getId(), execution.getVersion(), now.toString(),
                pausedTasks, pausedJobs), new TypeReference<>() { });
    }

    /** 到达原锚点时恢复同一执行及整组任务，不再创建任务或重跑自动通过；恢复标识由调用方同事务写入。 */
    public Restoration restore(FlowInstance instance, String anchor) {
        ActiveFrame selected = activeFrames(instance).stream()
                .filter(item -> Objects.equals(anchor, item.frame().anchorNodeKey())).findFirst().orElse(null);
        if (selected == null) return null;
        Frame frame = selected.frame();
        LocalDateTime now = LocalDateTime.now();
        for (TaskPause paused : frame.tasks()) {
            FlowTask task = tasks.selectList(Wrappers.<FlowTask>lambdaQuery().eq(FlowTask::getId, paused.taskId())
                    .eq(FlowTask::getInstanceId, instance.getId()).last("FOR UPDATE")).stream().findFirst().orElseThrow(this::invalid);
            if (!Objects.equals(anchor, task.getNodeKey()) || !"SUSPENDED".equals(task.getStatus())
                    || !Objects.equals(paused.pausedVersion(), task.getVersion())
                    || !Set.of("PENDING", "QUEUED").contains(paused.originalStatus())) throw invalid();
            task.setStatus(paused.originalStatus());
            if (paused.remainingMillis() != null) task.setDueTime(now.plusNanos(paused.remainingMillis() * 1_000_000));
            else if (task.getDueTime() != null) throw invalid();
            task.setUpdateTime(now);
            if (tasks.updateById(task) != 1) throw invalid();
        }
        for (JobPause paused : frame.jobs()) {
            FlowJob job = jobs.selectList(Wrappers.<FlowJob>lambdaQuery().eq(FlowJob::getId, paused.jobId())
                    .eq(FlowJob::getInstanceId, instance.getId()).last("FOR UPDATE")).stream().findFirst().orElseThrow(this::invalid);
            if (!Objects.equals(anchor, job.getNodeKey()) || !"TASK_TIMEOUT".equals(job.getJobType())
                    || !"SUSPENDED".equals(job.getStatus()) || !Objects.equals(paused.pausedVersion(), job.getVersion())) throw invalid();
            job.setStatus("PENDING"); job.setNextRunTime(now.plusNanos(paused.remainingMillis() * 1_000_000));
            job.setUpdateTime(now);
            if (jobs.updateById(job) != 1) throw invalid();
        }
        FlowExecution execution = executions.selectById(frame.executionId());
        requireExecution(instance, frame, execution);
        execution.setStatus("ACTIVE");
        if (executions.updateById(execution) != 1) throw invalid();
        return new Restoration(selected.action().getId(), execution.getId(), frame.tasks().stream().map(TaskPause::taskId).toList());
    }

    /** 暂停的历史节点只可作为通向未来的中转，不可重新作为指定插入锚点。 */
    public Set<String> transitAnchors(FlowInstance instance) {
        Set<String> result = new LinkedHashSet<>();
        for (ActiveFrame item : activeFrames(instance)) {
            requireExecution(instance, item.frame(), executions.selectById(item.frame().executionId()));
            result.add(item.frame().anchorNodeKey());
        }
        return result;
    }

    /** 终态关闭全部未完成执行（含当前新增节点）；任务及作业由运行服务统一清理，已完成事实不动。 */
    public void cancel(FlowInstance instance) {
        for (FlowExecution execution : executions.selectList(Wrappers.<FlowExecution>lambdaQuery()
                .eq(FlowExecution::getInstanceId, instance.getId()).in(FlowExecution::getStatus, "ACTIVE", "SUSPENDED")
                .orderByAsc(FlowExecution::getId).last("FOR UPDATE"))) {
            execution.setStatus("CANCELLED"); execution.setResultCode(instance.getStatus());
            execution.setLeaveTime(LocalDateTime.now());
            if (executions.updateById(execution) != 1) throw invalid();
        }
    }

    private List<ActiveFrame> activeFrames(FlowInstance instance) {
        List<FlowAction> rows = actions.selectList(Wrappers.<FlowAction>lambdaQuery()
                .eq(FlowAction::getInstanceId, instance.getId())
                .in(FlowAction::getActionCode, "RUNTIME_NODE_ADDED", "RUNTIME_NODE_RESUMED").orderByDesc(FlowAction::getId));
        Set<Long> resumed = new HashSet<>();
        for (FlowAction row : rows) if ("RUNTIME_NODE_RESUMED".equals(row.getActionCode()))
            resumed.add(mapper.convertValue(read(row.getInputJson()).get("frameActionId"), Long.class));
        List<ActiveFrame> result = new ArrayList<>();
        for (FlowAction row : rows) {
            if (!"RUNTIME_NODE_ADDED".equals(row.getActionCode()) || resumed.contains(row.getId())) continue;
            Map<String, Object> change = read(row.getInputJson());
            if (!"BEFORE_CURRENT".equals(change.get("position"))) continue;
            Frame frame;
            try { frame = mapper.convertValue(change.get("suspensionFrame"), Frame.class); }
            catch (IllegalArgumentException exception) { throw invalid(); }
            if (frame == null || frame.tasks() == null || frame.tasks().isEmpty() || frame.jobs() == null
                    || !Objects.equals(frame.anchorNodeKey(), change.get("anchorNodeKey"))
                    || !Objects.equals(String.valueOf(instance.getId()), String.valueOf(change.get("instanceId")))
                    || !Objects.equals(String.valueOf(instance.getFlowVersionId()), String.valueOf(change.get("flowVersionId")))
                    || row.getActedByAccountId() == null) throw invalid();
            // 终止或退回已把整帧任务与执行一并取消；后续重提必须创建新执行，不能尝试恢复旧帧。
            FlowExecution execution = executions.selectById(frame.executionId());
            if (execution != null && "CANCELLED".equals(execution.getStatus())) {
                boolean fullyCancelled = frame.tasks().stream().allMatch(paused -> {
                    FlowTask task = tasks.selectById(paused.taskId());
                    return task != null && "CANCELLED".equals(task.getStatus());
                });
                if (fullyCancelled) continue;
                throw invalid();
            }
            result.add(new ActiveFrame(row, frame));
        }
        return result;
    }

    private void requireExecution(FlowInstance instance, Frame frame, FlowExecution execution) {
        if (execution == null || !Objects.equals(instance.getId(), execution.getInstanceId())
                || !Objects.equals(frame.anchorNodeKey(), execution.getNodeKey())
                || !"SUSPENDED".equals(execution.getStatus())
                || !Objects.equals(frame.executionVersion(), execution.getVersion())) throw invalid();
    }

    private long remaining(LocalDateTime now, LocalDateTime due) {
        if (due == null) throw invalid();
        return Math.max(0, Duration.between(now, due).toMillis());
    }

    private Map<String, Object> read(String value) {
        try { return mapper.readValue(value, new TypeReference<>() { }); }
        catch (Exception exception) { throw invalid(); }
    }

    private DomainException invalid() {
        return new DomainException("FLOW_NODE_SUSPENSION_INVALID", "原节点暂停记录已变化或不完整，请重新读取实例或联系管理员处理", HttpStatus.CONFLICT);
    }
}
