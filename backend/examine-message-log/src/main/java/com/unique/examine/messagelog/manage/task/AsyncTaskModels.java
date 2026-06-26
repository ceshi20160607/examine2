package com.unique.examine.messagelog.manage.task;

import java.time.LocalDateTime;

/**
 * Async task API models.
 */
public final class AsyncTaskModels {

    private AsyncTaskModels() {
    }

    public record AsyncTaskQueryRequest(String bizType, String status, Boolean retryable, Boolean cancelable,
                                        String createdBy, String timeRange, String traceId) {
    }

    public record AsyncTaskVO(String taskId, String bizType, String idempotencyKey, String status, Integer progress,
                              boolean retryable, boolean cancelable, String resultFile, String errorFile,
                              String failureReason, Integer partialSuccessCount, Integer partialFailureCount,
                              boolean rollbackSupported, String traceId, String auditLogId, String createdBy,
                              LocalDateTime createdAt) {
    }

    public record AsyncTaskActionResult(String taskId, String action, String status, String traceId,
                                        String auditLogId, LocalDateTime operatedAt) {
    }
}
