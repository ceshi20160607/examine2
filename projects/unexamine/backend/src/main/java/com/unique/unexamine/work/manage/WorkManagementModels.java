package com.unique.unexamine.work.manage;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class WorkManagementModels {
    private WorkManagementModels() {
    }

    public record ProjectMemberInput(@NotNull Long accountId, @NotBlank String projectRole) {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 100) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            LocalDate startDate,
            LocalDate dueDate,
            List<@Valid ProjectMemberInput> members) {
    }

    public record UpdateProjectRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            LocalDate startDate,
            LocalDate dueDate,
            @NotBlank String status,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent) {
    }

    public record CreateTaskGroupRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull Integer sortOrder) {
    }

    public record CreateTaskRequest(
            Long taskGroupId,
            Long parentTaskId,
            @NotBlank @Size(max = 500) String title,
            String description,
            @NotBlank String priority,
            @NotNull Long ownerAccountId,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            List<Long> collaboratorAccountIds,
            Map<String, Object> customValues) {
    }

    public record UpdateTaskRequest(
            @NotNull Integer expectedVersion,
            @NotBlank String status,
            @NotNull Long ownerAccountId,
            @NotBlank String priority,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            List<Long> collaboratorAccountIds,
            Map<String, Object> customValues,
            @Size(max = 2000) String comment) {
    }

    public record MemberView(Long id, Long accountId, String projectRole, String status) {
    }

    public record TaskGroupView(Long id, String name, Integer sortOrder, String status, Integer version) {
    }

    public record HistoryView(
            Long id, String actionCode, Map<String, Object> before,
            Map<String, Object> after, String comment, Long changedByAccountId,
            LocalDateTime changedAt) {
    }

    public record TaskView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            Long projectId, Long taskGroupId, Long parentTaskId, String title, String description,
            String taskType, String priority, String status, BigDecimal progressPercent,
            Long ownerAccountId, LocalDateTime startAt, LocalDateTime dueAt, LocalDateTime completedAt,
            Map<String, Object> customValues, Integer version,
            List<Long> collaboratorAccountIds, List<HistoryView> history) {
    }

    public record ProjectView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String code, String name, String description, Long ownerAccountId,
            LocalDate startDate, LocalDate dueDate, BigDecimal progressPercent,
            String status, Integer version, List<MemberView> members,
            List<TaskGroupView> taskGroups, List<TaskView> tasks) {
    }
}
