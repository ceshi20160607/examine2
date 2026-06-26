package com.unique.examine.messagelog.manage.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.core.base.entity.SysAsyncTask;
import com.unique.examine.core.base.service.SysAsyncTaskBaseService;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.manage.task.PersistedAsyncTaskService;
import com.unique.examine.core.task.AsyncTaskView;
import com.unique.examine.messagelog.manage.task.AsyncTaskModels.AsyncTaskActionResult;
import com.unique.examine.messagelog.manage.task.AsyncTaskModels.AsyncTaskQueryRequest;
import com.unique.examine.messagelog.manage.task.AsyncTaskModels.AsyncTaskVO;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Async task query and operation service backed by persisted task tables.
 */
@Service
public class AsyncTaskManageService {

    private final SysAsyncTaskBaseService taskBaseService;
    private final PersistedAsyncTaskService persistedAsyncTaskService;

    public AsyncTaskManageService(SysAsyncTaskBaseService taskBaseService,
                                  PersistedAsyncTaskService persistedAsyncTaskService) {
        this.taskBaseService = taskBaseService;
        this.persistedAsyncTaskService = persistedAsyncTaskService;
    }

    /**
     * Search async tasks.
     *
     * @param pageRequest page request
     * @param query query request
     * @return task page
     */
    public PageResult<AsyncTaskVO> search(PageRequest pageRequest, AsyncTaskQueryRequest query) {
        int pageNo = pageNo(pageRequest);
        int pageSize = pageSize(pageRequest);
        int offset = (pageNo - 1) * pageSize;
        long total = taskBaseService.count(queryWrapper(query));
        List<AsyncTaskVO> records = taskBaseService.list(queryWrapper(query)
                        .orderByDesc(SysAsyncTask::getCreatedAt)
                        .last("LIMIT " + offset + "," + pageSize))
                .stream()
                .map(this::toVO)
                .toList();
        return new PageResult<>(records, pageNo, pageSize, total, offset + records.size() < total);
    }

    /**
     * Return task detail.
     *
     * @param taskId task id
     * @return task detail
     */
    public AsyncTaskVO detail(String taskId) {
        return toVO(persistedAsyncTaskService.requireView(taskId));
    }

    /**
     * Cancel a task.
     *
     * @param taskId task id
     * @return action result
     */
    public AsyncTaskActionResult cancel(String taskId) {
        AsyncTaskView task = persistedAsyncTaskService.cancel(taskId);
        return action(task.taskId(), "cancel", task.status().name());
    }

    /**
     * Retry a task.
     *
     * @param taskId task id
     * @return action result
     */
    public AsyncTaskActionResult retry(String taskId) {
        AsyncTaskView task = persistedAsyncTaskService.retry(taskId);
        return action(task.taskId(), "retry", task.status().name());
    }

    private LambdaQueryWrapper<SysAsyncTask> queryWrapper(AsyncTaskQueryRequest query) {
        LambdaQueryWrapper<SysAsyncTask> wrapper = new LambdaQueryWrapper<>();
        if (Objects.nonNull(query)) {
            if (StringUtils.hasText(query.bizType())) {
                wrapper.eq(SysAsyncTask::getBizType, query.bizType());
            }
            if (StringUtils.hasText(query.status())) {
                wrapper.eq(SysAsyncTask::getStatus, query.status());
            }
            if (Objects.nonNull(query.retryable())) {
                wrapper.eq(SysAsyncTask::getRetryable, query.retryable() ? 1 : 0);
            }
            if (Objects.nonNull(query.cancelable())) {
                wrapper.eq(SysAsyncTask::getCancelable, query.cancelable() ? 1 : 0);
            }
            Long createdBy = parseLong(query.createdBy());
            if (Objects.nonNull(createdBy)) {
                wrapper.eq(SysAsyncTask::getCreatedBy, createdBy);
            }
            if (StringUtils.hasText(query.traceId())) {
                wrapper.eq(SysAsyncTask::getTraceId, query.traceId());
            }
        }
        return wrapper;
    }

    private AsyncTaskVO toVO(SysAsyncTask task) {
        return new AsyncTaskVO(task.getTaskId(), task.getBizType(), task.getIdempotencyKey(), task.getStatus(),
                task.getProgress(), Objects.equals(task.getRetryable(), 1), Objects.equals(task.getCancelable(), 1),
                task.getResultFileId(), task.getErrorFileId(), task.getFailureReason(),
                task.getPartialSuccessCount(), task.getPartialFailureCount(),
                Objects.equals(task.getRollbackSupported(), 1), task.getTraceId(), task.getAuditLogId(),
                String.valueOf(task.getCreatedBy()), task.getCreatedAt());
    }

    private AsyncTaskVO toVO(AsyncTaskView task) {
        return new AsyncTaskVO(task.taskId(), task.bizType(), task.idempotencyKey(), task.status().name(),
                task.progress(), task.retryable(), task.cancelable(), task.resultFileId(), task.errorFileId(),
                task.failureReason(), task.partialSuccessCount(), task.partialFailureCount(),
                task.rollbackSupported(), task.traceId(), task.auditLogId(), task.createdBy(),
                StringUtils.hasText(task.createdAt()) ? LocalDateTime.parse(task.createdAt()) : null);
    }

    private AsyncTaskActionResult action(String taskId, String action, String status) {
        RequestContext context = RequestContext.current();
        return new AsyncTaskActionResult(taskId, action, status, context.traceId(),
                auditLogId(context), LocalDateTime.now());
    }

    private int pageNo(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageNo() <= 0 ? 1 : pageRequest.pageNo();
    }

    private int pageSize(PageRequest pageRequest) {
        return pageRequest == null || pageRequest.pageSize() <= 0 ? 20 : pageRequest.pageSize();
    }

    private Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String auditLogId(RequestContext context) {
        return StringUtils.hasText(context.auditLogId()) ? context.auditLogId() : "aud_" + context.traceId();
    }
}
