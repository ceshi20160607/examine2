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

/**
 * 工作管理接口契约。人员选择使用租户成员标识，账号标识只保留旧客户端兼容入口。
 */
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
            List<@Valid ProjectMemberInput> members,
            @Size(max = 200) String customerName,
            @Size(max = 200) String internalDepartmentName,
            @Size(max = 64) String stage,
            List<@Size(max = 100) String> labels,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle) {
    }

    public record UpdateProjectRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 2000) String description,
            LocalDate startDate,
            LocalDate dueDate,
            @NotBlank String status,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
            List<@Valid ProjectMemberInput> members,
            @Size(max = 200) String customerName,
            @Size(max = 200) String internalDepartmentName,
            @Size(max = 64) String stage,
            List<@Size(max = 100) String> labels,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle) {
    }

    public record CreateTaskGroupRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull Integer sortOrder,
            Long ownerTenantMemberId,
            @JsonAlias("ownerAccountId") Long compatibilityOwnerAccountId,
            LocalDateTime dueTime,
            @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent) {
    }

    public record UpdateTaskGroupRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 200) String name,
            @NotNull Integer sortOrder,
            @NotBlank String status,
            Long ownerTenantMemberId,
            @JsonAlias("ownerAccountId") Long compatibilityOwnerAccountId,
            LocalDateTime dueTime,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent) {
    }

    public record ChecklistItemInput(
            @Size(max = 64) String id,
            @NotBlank @Size(max = 500) String text,
            boolean completed) {
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
            List<Long> followerTenantMemberIds,
            List<Long> acceptanceTenantMemberIds,
            @DecimalMin("0") BigDecimal estimatedHours,
            List<@Size(max = 100) String> labels,
            List<@Valid ChecklistItemInput> checklist,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @JsonAlias("customValues") Map<String, Object> configuredValues) {
    }

    public record UpdateTaskRequest(
            @NotNull Integer expectedVersion,
            @NotBlank String status,
            @Size(max = 500) String title,
            String description,
            Long ownerTenantMemberId,
            @JsonAlias("ownerAccountId") Long compatibilityOwnerAccountId,
            @NotBlank String priority,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
            LocalDateTime startAt,
            LocalDateTime dueAt,
            List<Long> collaboratorTenantMemberIds,
            @JsonAlias("collaboratorAccountIds") List<Long> compatibilityCollaboratorAccountIds,
            List<Long> followerTenantMemberIds,
            List<Long> acceptanceTenantMemberIds,
            @DecimalMin("0") BigDecimal estimatedHours,
            @DecimalMin("0") BigDecimal actualHours,
            List<@Size(max = 100) String> labels,
            List<@Valid ChecklistItemInput> checklist,
            String businessType,
            String businessId,
            @Size(max = 500) String businessTitle,
            @JsonAlias("customValues") Map<String, Object> configuredValues,
            @Size(max = 2000) String comment) {
    }

    public record TransferTaskRequest(
            @NotNull Integer expectedVersion,
            Long ownerTenantMemberId,
            @JsonAlias("ownerAccountId") Long compatibilityOwnerAccountId,
            @Size(max = 2000) String comment) {
    }

    public record DelayTaskRequest(
            @NotNull Integer expectedVersion,
            @NotNull LocalDateTime dueTime,
            @NotBlank @Size(max = 2000) String reason) {
    }

    public record SubmitCompletionRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 2000) String completionNote,
            @DecimalMin("0") BigDecimal actualHours) {
    }

    public record TaskReasonRequest(
            @NotNull Integer expectedVersion,
            @NotBlank @Size(max = 2000) String comment) {
    }

    public record AddCommentRequest(@NotBlank @Size(max = 2000) String comment) {
    }

    public record RecordProgressRequest(
            @NotNull Integer expectedVersion,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent,
            @DecimalMin("0") BigDecimal actualHours,
            @Size(max = 2000) String comment) {
    }

    public record PersonView(Long tenantMemberId, String displayName,
                             String departmentName, String positionTitle) {
    }

    public record MemberView(Long id, PersonView person, String projectRole, String status) {
    }

    public record ChecklistItemView(String id, String text, boolean completed) {
    }

    public record TaskGroupView(
            Long id, String name, Integer sortOrder, String status,
            PersonView owner, LocalDateTime dueTime, BigDecimal progressPercent,
            Integer version) {
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

    /** 当前登录人可执行的任务动作，前端据此只呈现真实可用入口。 */
    public record TaskActionsView(
            boolean canEdit, boolean canTransfer, boolean canDelay,
            boolean canSubmitCompletion, boolean canAcceptCompletion,
            boolean canReopen, boolean canArchive, boolean canDelete,
            boolean canComment) {
    }

    public record TaskView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            Long projectId, Long taskGroupId, Long parentTaskId, String title, String description,
            String taskType, String priority, String status, String statusName, boolean overdue,
            BigDecimal progressPercent, BigDecimal estimatedHours, BigDecimal actualHours,
            List<String> labels, List<ChecklistItemView> checklist,
            PersonView owner, LocalDateTime startAt, LocalDateTime dueAt, LocalDateTime completedAt,
            String completionNote, LocalDateTime completionSubmittedTime,
            LocalDateTime completionAcceptedTime, LocalDateTime archiveTime,
            String businessType, String businessId, String businessTitle,
            Map<String, Object> configuredValues, Integer version,
            List<PersonView> collaborators, List<PersonView> followers, List<PersonView> acceptors,
            List<HistoryView> history, List<LinkedLogView> linkedLogs,
            List<TaskView> subtasks, TaskActionsView actions) {
    }

    public record ProjectView(
            Long id, String contextType, Long platformId, Long systemId, Long tenantId,
            String code, String name, String description, String customerName,
            String internalDepartmentName, String stage, List<String> labels,
            String businessType, String businessId, String businessTitle,
            PersonView owner, LocalDate startDate, LocalDate dueDate, BigDecimal progressPercent,
            String status, Integer version, List<MemberView> members,
            List<TaskGroupView> taskGroups, List<TaskView> tasks) {
    }
}
