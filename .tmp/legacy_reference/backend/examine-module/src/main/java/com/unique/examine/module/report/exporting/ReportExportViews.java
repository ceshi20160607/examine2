package com.unique.examine.module.report.exporting;

import java.time.LocalDateTime;
import java.util.List;

public final class ReportExportViews {
    private ReportExportViews() {
    }

    public record StartRequest(String requestKey) {
    }

    public record Task(
            String exportId,
            String reportCode,
            String reportVersionId,
            int reportVersionNumber,
            String dataSourceVersionId,
            List<String> fieldCodes,
            String status,
            Long totalRows,
            int processedRows,
            boolean truncated,
            String jobId,
            String resultFilename,
            Long resultSize,
            String failureCode,
            String failureMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        public Task {
            fieldCodes = List.copyOf(fieldCodes);
        }
    }

    public record TaskPage(
            List<Task> items,
            int page,
            int size,
            long total
    ) {
        public TaskPage {
            items = List.copyOf(items);
        }
    }
}
