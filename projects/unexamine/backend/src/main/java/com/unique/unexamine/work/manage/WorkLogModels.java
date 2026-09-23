package com.unique.unexamine.work.manage;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** 工作日志页面与业务服务之间的命名契约，页面不传内部 JSON。 */
public final class WorkLogModels {
    private WorkLogModels() {
    }

    public record MaterialSelection(
            @NotBlank @Size(max = 32) String materialType,
            @NotBlank @Size(max = 100) String materialId) {
    }

    public record CreateLogRequest(
            @NotNull LocalDate workDate,
            @NotBlank @Size(max = 500) String title,
            @JsonAlias("content") @Size(max = 20000) String completedText,
            @Size(max = 20000) String inProgressText,
            @Size(max = 20000) String nextPlanText,
            @Size(max = 20000) String riskText,
            @Size(max = 20000) String assistanceText,
            @Min(1) @Max(1440) Integer durationMinutes,
            Long projectId,
            List<Long> taskIds,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @Size(max = 32) String visibilityType,
            List<Long> visibleRoleIds,
            List<@Valid MaterialSelection> materials,
            @JsonAlias("customValues") Map<String, Object> configuredValues) {
    }

    public record UpdateLogRequest(
            @NotNull Integer expectedVersion,
            @NotNull LocalDate workDate,
            @NotBlank @Size(max = 500) String title,
            @JsonAlias("content") @Size(max = 20000) String completedText,
            @Size(max = 20000) String inProgressText,
            @Size(max = 20000) String nextPlanText,
            @Size(max = 20000) String riskText,
            @Size(max = 20000) String assistanceText,
            @Min(1) @Max(1440) Integer durationMinutes,
            Long projectId,
            List<Long> taskIds,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @Size(max = 32) String visibilityType,
            List<Long> visibleRoleIds,
            List<@Valid MaterialSelection> materials,
            @JsonAlias("customValues") Map<String, Object> configuredValues,
            @NotBlank @Size(max = 1000) String revisionReason) {
    }

    public record LifecycleRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record AddCommentRequest(
            @NotBlank @Size(max = 32) String commentType,
            @NotBlank @Size(max = 2000) String comment) {
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

    public record MaterialView(
            Long id, String materialType, String materialId, String title,
            String summary, LocalDateTime sourceTime) {
    }

    public record MaterialCandidate(
            String key, String materialType, String materialId, String typeName,
            String title, String summary, LocalDateTime sourceTime) {
    }

    public record CommentView(
            Long id, String commentType, String comment,
            WorkManagementModels.PersonView author, LocalDateTime createdTime) {
    }

    public record RoleOption(Long id, String code, String name) {
    }

    public record LogOptionsView(List<RoleOption> roles) {
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
            String completedText,
            String inProgressText,
            String nextPlanText,
            String riskText,
            String assistanceText,
            Integer durationMinutes,
            String status,
            boolean editable,
            String visibilityType,
            List<Long> visibleRoleIds,
            LocalDateTime submittedTime,
            LocalDateTime withdrawnTime,
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
            List<MaterialView> materials,
            List<CommentView> comments,
            List<RevisionView> revisions) {
    }
}
