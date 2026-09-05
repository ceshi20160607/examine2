package com.unique.unexamine.work.manage;

import com.fasterxml.jackson.annotation.JsonAlias;
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
            Long projectId,
            List<Long> taskIds,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @JsonAlias("customValues") Map<String, Object> configuredValues) {
    }

    public record UpdateLogRequest(
            @NotNull Integer expectedVersion,
            @NotNull LocalDate workDate,
            @NotBlank @Size(max = 500) String title,
            @NotBlank @Size(max = 20000) String content,
            @Min(1) @Max(1440) Integer durationMinutes,
            @NotBlank @Size(max = 32) String status,
            Long projectId,
            List<Long> taskIds,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @JsonAlias("customValues") Map<String, Object> configuredValues,
            @NotBlank @Size(max = 1000) String revisionReason) {
    }

    public record RevisionView(
            Long id,
            Integer revisionNumber,
            Map<String, Object> snapshot,
            String revisionReason,
            String revisedByName,
            LocalDateTime revisedAt) {
    }

    public record TaskReference(Long id, String title, String status) {
    }

    public record LogView(
            Long id,
            String contextType,
            Long platformId,
            Long systemId,
            Long tenantId,
            WorkManagementModels.PersonView author,
            LocalDate workDate,
            String title,
            String content,
            Integer durationMinutes,
            String status,
            Long projectId,
            String projectName,
            List<TaskReference> tasks,
            String businessType,
            String businessId,
            String businessTitle,
            Map<String, Object> configuredValues,
            Integer version,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            List<RevisionView> revisions) {
    }
}
