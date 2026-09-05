package com.unique.unexamine.work.manage;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    public record ProjectMemberInput(
            Long tenantMemberId,
            @JsonAlias("accountId") Long compatibilityAccountId,
            @NotBlank String projectRole) {
    }

    public record CreateProjectRequest(
            @Size(max = 100) String code,
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
            Long ownerTenantMemberId,
            @JsonAlias("ownerAccountId") Long compatibilityOwnerAccountId,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            List<Long> collaboratorTenantMemberIds,
            @JsonAlias("collaboratorAccountIds") List<Long> compatibilityCollaboratorAccountIds,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @JsonAlias("customValues") Map<String, Object> configuredValues) {
    }

    public record UpdateTaskRequest(
            @NotNull Integer expectedVersion,
            @NotBlank String status,
            Long ownerTenantMemberId,
            @JsonAlias("ownerAccountId") Long compatibilityOwnerAccountId,
            @NotBlank String priority,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            List<Long> collaboratorTenantMemberIds,
            @JsonAlias("collaboratorAccountIds") List<Long> compatibilityCollaboratorAccountIds,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @JsonAlias("customValues") Map<String, Object> configuredValues,
            @Size(max = 2000) String comment) {
    }

    public record PersonView(Long tenantMemberId, String displayName,
                             String departmentName, String positionTitle) {
    }

    public record MemberView(Long id, PersonView person, String projectRole, String status) {
    }

    public record TaskGroupView(Long id, String name, Integer sortOrder, String status, Integer version) {
    }

    public record HistoryView(
            Long id, String actionCode, Map<String, Object> before,
            Map<String, Object> after, String comment, String changedByName,
            LocalDateTime changedAt) {
    }

    public record LinkedLogView(
            Long id, LocalDate workDate, String title, String content,
            Integer durationMinutes, String status, String authorName,
            LocalDateTime updatedAt) {
    }

    public record TaskView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            Long projectId, Long taskGroupId, Long parentTaskId, String title, String description,
            String taskType, String priority, String status, BigDecimal progressPercent,
            PersonView owner, LocalDateTime startAt, LocalDateTime dueAt, LocalDateTime completedAt,
            String businessType, String businessId, String businessTitle,
            Map<String, Object> configuredValues, Integer version,
            List<PersonView> collaborators, List<HistoryView> history,
            List<LinkedLogView> linkedLogs) {
    }

    public record ProjectView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String code, String name, String description, PersonView owner,
            LocalDate startDate, LocalDate dueDate, BigDecimal progressPercent,
            String status, Integer version, List<MemberView> members,
            List<TaskGroupView> taskGroups, List<TaskView> tasks) {
    }
}
