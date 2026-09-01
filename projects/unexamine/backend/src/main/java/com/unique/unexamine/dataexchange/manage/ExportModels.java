package com.unique.unexamine.dataexchange.manage;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ExportModels {
    private ExportModels() {
    }

    public record ExportQuery(
            @Pattern(regexp = "ACTIVE|ARCHIVED|DELETED") String lifecycleState,
            @Pattern(regexp = "ALL|OWN|SHARED") String tenantScope,
            @Size(max = 200) String search,
            List<Map<String, String>> filters,
            @Size(max = 100) String sortField,
            @Pattern(regexp = "ASC|DESC") String sortDirection,
            List<Long> selectedRecordIds,
            @NotEmpty List<@Size(max = 100) String> selectedFields) {
    }

    public record ExportFieldView(
            String code, String name, String fieldType, boolean builtIn,
            boolean readable, boolean masked, String maskStrategy) {
    }

    public record EstimateView(
            String moduleCode, String scope, long estimatedRows, long maximumRows,
            boolean withinQuota, List<ExportFieldView> availableFields,
            List<ExportFieldView> selectedFields, List<String> maskedFields) {
    }

    public record ExportBatchView(
            Long id, String moduleCode, Long jobId, String status,
            long totalRows, long exportedRows, Long resultFileId, String errorMessage,
            Map<String, Object> filterSnapshot, List<String> selectedFields,
            LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt, Integer version) {
    }
}
