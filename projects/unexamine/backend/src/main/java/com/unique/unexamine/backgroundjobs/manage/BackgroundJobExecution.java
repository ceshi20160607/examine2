package com.unique.unexamine.backgroundjobs.manage;

import java.util.Map;

public final class BackgroundJobExecution {
    private final long jobId;
    private final int attemptNumber;
    private final Reporter reporter;

    BackgroundJobExecution(long jobId, int attemptNumber, Reporter reporter) {
        this.jobId = jobId;
        this.attemptNumber = attemptNumber;
        this.reporter = reporter;
    }

    public long jobId() {
        return jobId;
    }

    public int attemptNumber() {
        return attemptNumber;
    }

    public void heartbeat(long processed, long total) {
        reporter.heartbeat(processed, total);
    }

    public void itemSucceeded(String itemKey, Long rowNumber, Map<String, Object> result,
                              long processed, long total) {
        reporter.item(itemKey, rowNumber, "SUCCEEDED", result, null, null, processed, total);
    }

    public void itemFailed(String itemKey, Long rowNumber, String errorCode, String errorMessage,
                           long processed, long total) {
        reporter.item(itemKey, rowNumber, "FAILED", Map.of(), errorCode, errorMessage, processed, total);
    }

    interface Reporter {
        void heartbeat(long processed, long total);

        void item(String itemKey, Long rowNumber, String status, Map<String, Object> result,
                  String errorCode, String errorMessage, long processed, long total);
    }
}
