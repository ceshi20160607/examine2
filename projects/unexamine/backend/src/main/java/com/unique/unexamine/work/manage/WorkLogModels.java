package com.unique.unexamine.work.manage;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WorkLogModels {
    private WorkLogModels() {
    }

    public record CreateLogRequest(
            @NotNull LocalDate workDate,
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 20000) String content,
            @Min(1) @Max(1440) Integer durationMinutes,
            Map<String, Object> customValues) {
    }

    public record UpdateLogRequest(
            @NotNull Integer expectedVersion,
            @NotNull LocalDate workDate,
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 20000) String content,
            @Min(1) @Max(1440) Integer durationMinutes,
            @NotBlank @Size(max = 32) String status,
            Map<String, Object> customValues,
            @NotBlank @Size(max = 1000) String revisionReason) {
    }

    public record RevisionView(
            Long id,
            Integer revisionNumber,
            Map<String, Object> snapshot,
            String revisionReason,
            Long revisedByAccountId,
            LocalDateTime revisedAt) {
    }

    public record LogView(
            Long id,
            String contextType,
            Long platformId,
            Long systemId,
            Long tenantId,
            Long authorAccountId,
            LocalDate workDate,
            String title,
            String content,
            Integer durationMinutes,
            String status,
            Map<String, Object> customValues,
            Integer version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<RevisionView> revisions) {
    }
}
