package com.unique.examine.core.manage.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.base.entity.SysAsyncTask;
import com.unique.examine.core.base.entity.SysAsyncTaskEvent;
import com.unique.examine.core.base.entity.SysAsyncTaskFile;
import com.unique.examine.core.base.service.SysAsyncTaskBaseService;
import com.unique.examine.core.base.service.SysAsyncTaskEventBaseService;
import com.unique.examine.core.base.service.SysAsyncTaskFileBaseService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.task.AsyncTaskStatus;
import com.unique.examine.core.task.AsyncTaskView;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Persists cross-module async tasks, task files and task events.
 */
@Service
public class PersistedAsyncTaskService {

    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final SysAsyncTaskBaseService taskBaseService;
    private final SysAsyncTaskFileBaseService taskFileBaseService;
    private final SysAsyncTaskEventBaseService taskEventBaseService;
    private final ObjectMapper objectMapper;

    public PersistedAsyncTaskService(SysAsyncTaskBaseService taskBaseService,
                                     SysAsyncTaskFileBaseService taskFileBaseService,
                                     SysAsyncTaskEventBaseService taskEventBaseService,
                                     ObjectMapper objectMapper) {
        this.taskBaseService = taskBaseService;
        this.taskFileBaseService = taskFileBaseService;
        this.taskEventBaseService = taskEventBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Create or update one async task by biz type and idempotency key.
     *
     * @param taskId task id
     * @param bizType business type
     * @param idempotencyKey idempotency key
     * @param status task status
     * @param progress task progress
     * @param retryable whether retry is allowed
     * @param cancelable whether cancel is allowed
     * @param rollbackSupported whether rollback is supported
     * @param resultFileId optional result file id
     * @param errorFileId optional error file id
     * @param failureReason optional failure reason
     * @param partialSuccessCount partial success count
     * @param partialFailureCount partial failure count
     * @param createdBy operator id
     * @param eventPayload event payload
     * @return persisted task view
     */
    public AsyncTaskView upsert(String taskId, String bizType, String idempotencyKey, AsyncTaskStatus status,
                                int progress, boolean retryable, boolean cancelable, boolean rollbackSupported,
                                String resultFileId, String errorFileId, String failureReason,
                                Integer partialSuccessCount, Integer partialFailureCount, Long createdBy,
                                Map<String, Object> eventPayload) {
        RequestContext requestContext = RequestContext.current();
        LocalDateTime now = LocalDateTime.now();
        String resolvedBizType = safeText(bizType, "GENERIC_TASK");
        String resolvedTaskId = safeText(taskId, "task_" + resolvedBizType.toLowerCase() + "_" + shortTrace());
        String resolvedIdempotencyKey = safeText(idempotencyKey, "idem_" + resolvedTaskId);
        SysAsyncTask entity = findByBizIdempotency(resolvedBizType, resolvedIdempotencyKey);
        if (Objects.isNull(entity)) {
            entity = findEntity(resolvedTaskId);
        }
        boolean created = Objects.isNull(entity);
        if (created) {
            entity = new SysAsyncTask();
            entity.setTaskId(resolvedTaskId);
            entity.setBizType(resolvedBizType);
            entity.setIdempotencyKey(resolvedIdempotencyKey);
            entity.setCreatedBy(Objects.isNull(createdBy) ? SYSTEM_OPERATOR_ID : createdBy);
            entity.setCreatedAt(now);
        }
        entity.setStatus(status.name());
        entity.setProgress(progress);
        entity.setRetryable(retryable ? 1 : 0);
        entity.setCancelable(cancelable ? 1 : 0);
        entity.setRollbackSupported(rollbackSupported ? 1 : 0);
        entity.setResultFileId(resultFileId);
        entity.setErrorFileId(errorFileId);
        entity.setFailureReason(failureReason);
        entity.setPartialSuccessCount(partialSuccessCount);
        entity.setPartialFailureCount(partialFailureCount);
        entity.setTraceId(requestContext.traceId());
        entity.setAuditLogId(auditLogId(requestContext));
        entity.setUpdatedAt(now);
        if (created) {
            taskBaseService.saveEntity(entity);
        } else {
            taskBaseService.updateById(entity);
        }
        saveTaskFile(entity.getTaskId(), "RESULT", resultFileId);
        saveTaskFile(entity.getTaskId(), "ERROR", errorFileId);
        saveEvent(entity.getTaskId(), created ? "TASK_CREATED" : "TASK_UPDATED",
                eventPayload(entity, eventPayload), entity.getCreatedBy(), requestContext.traceId());
        return toView(entity);
    }

    /**
     * Persist a domain event for an existing task.
     *
     * @param taskId task id
     * @param eventType event type
     * @param payload JSON payload object
     */
    public void saveEvent(String taskId, String eventType, Map<String, Object> payload) {
        SysAsyncTask task = requireEntity(taskId);
        saveEvent(task.getTaskId(), eventType, payload, task.getCreatedBy(), RequestContext.current().traceId());
    }

    /**
     * Return a persisted task view.
     *
     * @param taskId task id
     * @return task view
     */
    public AsyncTaskView requireView(String taskId) {
        return toView(requireEntity(taskId));
    }

    /**
     * Cancel a queued or running task.
     *
     * @param taskId task id
     * @return updated task
     */
    public AsyncTaskView cancel(String taskId) {
        SysAsyncTask task = requireEntity(taskId);
        if (task.getCancelable() == null || task.getCancelable() == 0 || isTerminal(task.getStatus())) {
            throw new BusinessException(CommonErrorCode.TASK_STATE_CONFLICT, "当前任务不允许取消");
        }
        task.setStatus(AsyncTaskStatus.CANCELED.name());
        task.setProgress(Math.max(0, Objects.isNull(task.getProgress()) ? 0 : task.getProgress()));
        task.setUpdatedAt(LocalDateTime.now());
        taskBaseService.updateById(task);
        saveEvent(task.getTaskId(), "TASK_CANCELED", Map.of("status", task.getStatus()),
                task.getCreatedBy(), RequestContext.current().traceId());
        return toView(task);
    }

    /**
     * Retry a failed or canceled task.
     *
     * @param taskId task id
     * @return updated task
     */
    public AsyncTaskView retry(String taskId) {
        SysAsyncTask task = requireEntity(taskId);
        if (task.getRetryable() == null || task.getRetryable() == 0) {
            throw new BusinessException(CommonErrorCode.TASK_STATE_CONFLICT, "当前任务不允许重试");
        }
        task.setStatus(AsyncTaskStatus.QUEUED.name());
        task.setProgress(0);
        task.setFailureReason(null);
        task.setUpdatedAt(LocalDateTime.now());
        taskBaseService.updateById(task);
        saveEvent(task.getTaskId(), "TASK_RETRIED", Map.of("status", task.getStatus()),
                task.getCreatedBy(), RequestContext.current().traceId());
        return toView(task);
    }

    private SysAsyncTask findByBizIdempotency(String bizType, String idempotencyKey) {
        return taskBaseService.getOne(new LambdaQueryWrapper<SysAsyncTask>()
                .eq(SysAsyncTask::getBizType, bizType)
                .eq(SysAsyncTask::getIdempotencyKey, idempotencyKey)
                .last("LIMIT 1"), false);
    }

    private SysAsyncTask requireEntity(String taskId) {
        SysAsyncTask entity = findEntity(taskId);
        if (Objects.isNull(entity)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "后台任务不存在");
        }
        return entity;
    }

    private SysAsyncTask findEntity(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }
        return taskBaseService.getOne(new LambdaQueryWrapper<SysAsyncTask>()
                .eq(SysAsyncTask::getTaskId, taskId)
                .last("LIMIT 1"), false);
    }

    private void saveTaskFile(String taskId, String fileType, String fileId) {
        if (!StringUtils.hasText(fileId)) {
            return;
        }
        SysAsyncTaskFile taskFile = taskFileBaseService.getOne(new LambdaQueryWrapper<SysAsyncTaskFile>()
                .eq(SysAsyncTaskFile::getTaskId, taskId)
                .eq(SysAsyncTaskFile::getFileType, fileType)
                .last("LIMIT 1"), false);
        if (Objects.isNull(taskFile)) {
            taskFile = new SysAsyncTaskFile();
            taskFile.setTaskId(taskId);
            taskFile.setFileType(fileType);
            taskFile.setCreatedAt(LocalDateTime.now());
        }
        taskFile.setFileId(fileId);
        taskFile.setFileName(fileId);
        if (Objects.isNull(taskFile.getId())) {
            taskFileBaseService.saveEntity(taskFile);
        } else {
            taskFileBaseService.updateById(taskFile);
        }
    }

    private void saveEvent(String taskId, String eventType, Map<String, Object> payload,
                           Long operatorId, String traceId) {
        SysAsyncTaskEvent event = new SysAsyncTaskEvent();
        event.setTaskId(taskId);
        event.setEventType(eventType);
        event.setEventPayload(toJson(payload));
        event.setOperatorId(operatorId);
        event.setTraceId(traceId);
        event.setCreatedAt(LocalDateTime.now());
        taskEventBaseService.saveEntity(event);
    }

    private Map<String, Object> eventPayload(SysAsyncTask task, Map<String, Object> payload) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("taskId", task.getTaskId());
        event.put("bizType", task.getBizType());
        event.put("status", task.getStatus());
        event.put("progress", task.getProgress());
        if (Objects.nonNull(payload) && !payload.isEmpty()) {
            event.putAll(payload);
        }
        return event;
    }

    private AsyncTaskView toView(SysAsyncTask task) {
        return new AsyncTaskView(task.getTaskId(), task.getBizType(), task.getIdempotencyKey(),
                AsyncTaskStatus.valueOf(task.getStatus()), Objects.isNull(task.getProgress()) ? 0 : task.getProgress(),
                Objects.equals(task.getRetryable(), 1), Objects.equals(task.getCancelable(), 1),
                Objects.equals(task.getRollbackSupported(), 1), task.getResultFileId(), task.getErrorFileId(),
                task.getFailureReason(), task.getPartialSuccessCount(), task.getPartialFailureCount(),
                task.getTraceId(), task.getAuditLogId(), String.valueOf(task.getCreatedBy()),
                Objects.isNull(task.getCreatedAt()) ? null : task.getCreatedAt().toString());
    }

    private boolean isTerminal(String status) {
        return AsyncTaskStatus.SUCCESS.name().equals(status)
                || AsyncTaskStatus.PARTIAL_SUCCESS.name().equals(status)
                || AsyncTaskStatus.FAILED.name().equals(status)
                || AsyncTaskStatus.CANCELED.name().equals(status)
                || AsyncTaskStatus.ROLLED_BACK.name().equals(status);
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }

    private String shortTrace() {
        String traceId = RequestContext.current().traceId();
        return traceId.length() <= 8 ? traceId : traceId.substring(traceId.length() - 8);
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "异步任务事件序列化失败");
        }
    }
}
