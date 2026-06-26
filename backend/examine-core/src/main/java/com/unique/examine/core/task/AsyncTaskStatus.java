package com.unique.examine.core.task;

/**
 * Frozen async task state machine.
 */
public enum AsyncTaskStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
    CANCELED,
    ROLLBACKING,
    ROLLED_BACK
}

