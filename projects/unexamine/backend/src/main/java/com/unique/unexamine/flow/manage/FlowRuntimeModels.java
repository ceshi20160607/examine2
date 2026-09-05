package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class FlowRuntimeModels {
    private FlowRuntimeModels() {
    }

    public record StartRequest(
            @NotNull Long flowId,
            @NotBlank @Size(max = 500) String title,
            @Size(max = 100) String businessType,
            @Size(max = 100) String businessId,
            Map<String, Object> businessSnapshot,
            Map<String, Object> variables,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record HandleTaskRequest(
            @NotBlank @Size(max = 64) String actionCode,
            @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey,
            Map<String, Object> variables,
            @JsonAlias("targetAccountId") Long targetTenantMemberId) {
    }

    public record InstanceActionRequest(
            @NotBlank @Size(max = 64) String actionCode,
            @NotBlank @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record IncidentActionRequest(
            @NotBlank @Size(max = 32) String actionCode,
            @NotBlank @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record ManualNodeRequest(
            @NotNull @JsonAlias("assigneeAccountId") Long assigneeTenantMemberId,
            @NotBlank @Size(max = 32) String position,
            @NotBlank @Size(max = 2000) String reason,
            Map<String, Map<String, String>> statusMappings,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record ManualNodePreview(
            Long instanceId, String instanceStatus, String currentNodeKey, Long suspendedTaskId,
            Long assigneeTenantMemberId, String assigneeName, String assigneeDepartment,
            String position, String reason,
            Map<String, Map<String, String>> statusMappings,
            boolean allowed, List<String> checks) {
    }

    public record ManualNodeResult(Long taskId, Long actionId, InstanceView instance) {
    }

    public record CandidateView(Long id, String candidateType, Long tenantMemberId,
                                String displayName, String departmentName, String positionTitle,
                                String resolutionReason, LocalDateTime createdAt) {
    }

    public record TaskView(Long id, String nodeKey, String taskType, String status,
                           Long assigneeTenantMemberId, String assigneeName,
                           String assigneeDepartment, String assigneePosition,
                           Map<String, Object> assigneeSnapshot,
                           LocalDateTime dueAt, LocalDateTime claimedAt, LocalDateTime completedAt,
                           Integer version, List<CandidateView> candidates) {
    }

    public record ActionView(Long id, Long taskId, String nodeKey, String actionCode,
                             String comment, Map<String, Object> input, Map<String, Object> result,
                             String idempotencyKey, Long actedByTenantMemberId, String actedByName,
                             LocalDateTime actedAt) {
    }

    public record ExceptionView(Long id, String nodeKey, String exceptionType, String errorCode,
                                String errorMessage, String policyAction, String status,
                                Long resolvedByTenantMemberId, String resolvedByName, String resolutionComment,
                                LocalDateTime occurredAt, LocalDateTime resolvedAt, Integer version) {
    }

    public record ExecutionView(Long id, Long parentExecutionId, String nodeKey, String nodeType,
                                String status, LocalDateTime enteredAt, LocalDateTime leftAt,
                                String resultCode, Integer version) {
    }

    public record HistoryEventView(Long id, Long executionId, Long taskId, String nodeKey,
                                   String eventType, String eventName, Long actorTenantMemberId,
                                   String actorName, String beforeStatus, String afterStatus,
                                   Map<String, Object> detail, LocalDateTime occurredAt) {
    }

    public record JobView(Long id, Long executionId, String nodeKey, String jobType, String status,
                          Integer attemptCount, Integer maxAttempts, LocalDateTime nextRunAt,
                          String lastErrorCode, String lastErrorMessage, LocalDateTime completedAt,
                          Integer version) {
    }

    public record InstanceView(
            Long id,
            String contextType,
            Long platformId,
            Long systemId,
            Long tenantId,
            Long flowId,
            Long flowVersionId,
            Integer flowVersionNumber,
            String definitionHash,
            Map<String, Object> definitionSnapshot,
            String businessType,
            String businessId,
            Map<String, Object> businessSnapshot,
            String title,
            String currentNodeKey,
            String currentNodeName,
            String status,
            Long startedByTenantMemberId,
            String startedByName,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorCode,
            String errorMessage,
            Integer version,
            Map<String, Object> variables,
            List<TaskView> tasks,
            List<ActionView> actions,
            List<ExceptionView> exceptions,
            List<ExecutionView> executions,
            List<HistoryEventView> history,
            List<JobView> jobs,
            List<String> allowedActions,
            String nextStep) {
    }

    public record ActionResult(Long actionId, boolean replayed, InstanceView instance) {
    }
}
