package com.unique.examine.core.task;

/**
 * Shared async task view used by import, export, publish, secret rotation, and Agent actions.
 */
public record AsyncTaskView(
        String taskId,
        String bizType,
        String idempotencyKey,
        AsyncTaskStatus status,
        int progress,
        boolean retryable,
        boolean cancelable,
        boolean rollbackSupported,
        String resultFileId,
        String errorFileId,
        String failureReason,
        Integer partialSuccessCount,
        Integer partialFailureCount,
        String traceId,
        String auditLogId,
        String createdBy,
        String createdAt
) {
}

