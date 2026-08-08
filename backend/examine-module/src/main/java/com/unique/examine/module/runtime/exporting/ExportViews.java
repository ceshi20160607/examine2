package com.unique.examine.module.runtime.exporting;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;

public final class ExportViews {
    private ExportViews() { }

    public record CreateRequest(JsonNode query, List<String> fieldCodes) {
        public CreateRequest {
            fieldCodes = fieldCodes == null ? List.of() : List.copyOf(fieldCodes);
        }
    }

    public record Task(
            String exportId,
            String moduleCode,
            String schemaVersionId,
            String queryHash,
            List<String> fieldCodes,
            String status,
            Integer totalRows,
            int processedRows,
            String jobId,
            String resultFilename,
            Long resultSize,
            String failureCode,
            String failureMessage,
            LocalDateTime createdAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt
    ) {
        public Task { fieldCodes = List.copyOf(fieldCodes); }
    }

    public record TaskPage(List<Task> items, int page, int size, long total) {
        public TaskPage { items = List.copyOf(items); }
    }
}
