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

public final class WorkConfigurationModels {
    private WorkConfigurationModels() {
    }

    public record SaveFieldRequest(
            Integer expectedVersion,
            @NotBlank @Size(max = 200) String fieldName,
            @NotBlank @Size(max = 64) String fieldType,
            @NotNull Boolean required,
            @Min(0) @Max(100000) Integer sortOrder,
            Map<String, Object> settings) {
    }

    public record PublishFieldRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 1000) String changeSummary) {
    }

    public record RollbackFieldRequest(
            @NotNull Integer expectedVersion,
            @NotNull @Min(1) Integer targetPublicationVersion,
            @NotBlank @Size(max = 1000) String reason) {
    }

    public record PublicationVersionView(
            Integer version,
            Integer basedOnVersion,
            String changeSummary,
            LocalDateTime publishedAt,
            Long publishedByAccountId,
            Map<String, Object> snapshot) {
    }

    public record FieldView(
            Long id,
            String contextType,
            Long platformId,
            Long systemId,
            Long tenantId,
            String targetType,
            String fieldCode,
            String fieldName,
            String fieldType,
            boolean required,
            int sortOrder,
            Map<String, Object> settings,
            String status,
            int rowVersion,
            Integer currentPublicationVersion,
            List<PublicationVersionView> publicationVersions,
            String source) {
    }

    public record ConfigurationView(
            List<FieldView> ownDrafts,
            List<FieldView> inheritedPlatformDefaults,
            List<FieldView> effectivePublished) {
    }

    public record TaskCalendarItem(Long id, String title, String status, String priority,
                                   Long projectId, WorkManagementModels.PersonView owner,
                                   String businessType, String businessId, String businessTitle,
                                   LocalDateTime dueAt, LocalDateTime completedAt) {
    }

    public record LogCalendarItem(Long id, String title, String status, WorkManagementModels.PersonView author,
                                  int durationMinutes, LocalDate workDate) {
    }

    public record ProjectMilestoneItem(Long id, String name, String status, LocalDate dueDate) {
    }

    public record CalendarDay(
            LocalDate date,
            int tasksDue,
            int tasksCompleted,
            int overdueTasks,
            int riskTasks,
            int logCount,
            int logMinutes,
            int projectMilestones,
            boolean detailAvailable,
            List<TaskCalendarItem> tasks,
            List<LogCalendarItem> logs,
            List<ProjectMilestoneItem> milestones) {
    }

    public record CalendarSummary(int tasksDue, int tasksCompleted, int overdueTasks,
                                  int riskTasks, int logCount, int logMinutes, int projectMilestones) {
    }

    public record CalendarView(
            String contextType,
            Long systemId,
            Long tenantId,
            LocalDate from,
            LocalDate to,
            Long projectId,
            Long tenantMemberId,
            String personName,
            boolean detailAvailable,
            CalendarSummary summary,
            List<CalendarDay> days) {
    }
}
