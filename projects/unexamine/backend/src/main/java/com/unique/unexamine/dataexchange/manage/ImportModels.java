package com.unique.unexamine.dataexchange.manage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class ImportModels {
    private ImportModels() {
    }

    public record TemplateColumn(
            String code, String name, String fieldType, boolean required, boolean writable, boolean builtIn) {
    }

    public record TemplateView(
            String moduleCode, Long moduleId, Integer versionNumber, List<TemplateColumn> columns,
            String csvHeader, String sampleRow) {
    }

    public record PreviewRequest(
            @NotNull Long sourceFileId,
            @NotNull Map<@Size(max = 200) String, @Size(max = 100) String> columnMapping,
            @Pattern(regexp = "ERROR|SKIP|UPDATE") String conflictPolicy,
            @Size(max = 200) String mappingName) {
    }

    public record ExecuteRequest(@NotNull Integer version) {
    }

    public record RollbackRequest(@Size(max = 1000) String reason) {
    }

    public record ImportRowView(
            Long id, Long rowNumber, String operation, String status, Long targetRecordId,
            Integer expectedRecordVersion, Integer appliedRecordVersion,
            Map<String, Object> raw, Map<String, Object> normalized,
            String errorCode, String errorMessage, String rollbackStatus, Integer version) {
    }

    public record ImportBatchView(
            Long id, Long systemId, Long tenantId, Long moduleId, String moduleCode,
            Long mappingId, Long sourceFileId, Long jobId, String mode, String status,
            long totalRows, long validRows, long successRows, long failedRows,
            Map<String, String> columnMapping, String conflictPolicy,
            Map<String, Object> summary, List<ImportRowView> rows,
            LocalDateTime createdAt, LocalDateTime startedAt, LocalDateTime finishedAt, Integer version) {
    }

    public record ExecutionParameters(
            Long batchId, Long accountId, Long platformId, Long systemId, Long tenantId,
            Long memberId, Long tenantMemberId, String username, String displayName, String mfaLevel,
            List<Long> roleIds, JsonNode permissions, JsonNode dataScopes) {
    }
}
