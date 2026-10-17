package com.unique.unexamine.dataexchange.manage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
            @Pattern(regexp = "CSV|XLSX") String format,
            @Size(max = 100) String idempotencyKey,
            List<@Size(max = 100) String> selectedFields,
            Long configVersionId) {
        public ExportQuery(String lifecycleState, String tenantScope, String search,
                           List<Map<String, String>> filters, String sortField, String sortDirection,
                           List<Long> selectedRecordIds, String format, String idempotencyKey,
                           List<String> selectedFields) {
            this(lifecycleState, tenantScope, search, filters, sortField, sortDirection,
                    selectedRecordIds, format, idempotencyKey, selectedFields, null);
        }
    }

    /** 管理员保存的模块导出草稿；字段顺序就是结果列顺序。 */
    public record SaveConfigRequest(
            @NotBlank @Size(max = 200) String name,
            @NotEmpty List<@NotBlank @Size(max = 100) String> selectedFields,
            @Pattern(regexp = "LABEL|ID") String relationDisplay,
            Integer expectedVersion) { }

    public record PublishConfigRequest(@NotNull Integer expectedDraftRevision) { }

    public record ConfigDraftView(Long id, String name, List<String> selectedFields,
                                  String relationDisplay, Integer draftRevision,
                                  Integer version, String status) { }

    public record ConfigPublishedView(Long versionId, Integer versionNumber,
                                      List<String> selectedFields, String relationDisplay) { }

    public record ConfigOverview(List<ExportFieldView> availableFields,
                                 ConfigDraftView draft, ConfigPublishedView published) { }

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
            Map<String, Object> filterSnapshot, String format, List<String> selectedFields,
            LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt, Integer version) {
    }
}
