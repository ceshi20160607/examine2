package com.unique.unexamine.flow.manage;

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
            Long targetAccountId) {
    }

    public record InstanceActionRequest(
            @NotBlank @Size(max = 64) String actionCode,
            @NotBlank @Size(max = 2000) String comment,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record ManualNodeRequest(
            @NotNull Long assigneeAccountId,
            @NotBlank @Size(max = 32) String position,
            @NotBlank @Size(max = 2000) String reason,
            Map<String, Map<String, String>> statusMappings,
            @NotBlank @Size(max = 255) String idempotencyKey) {
    }

    public record ManualNodePreview(
            Long instanceId, String instanceStatus, String currentNodeKey, Long suspendedTaskId,
            Long assigneeAccountId, String position, String reason,
            Map<String, Map<String, String>> statusMappings,
            boolean allowed, List<String> checks) {
    }

    public record ManualNodeResult(Long taskId, Long actionId, InstanceView instance) {
    }

    public record CandidateView(Long id, String candidateType, String candidateId,
                                String resolutionReason, LocalDateTime createdAt) {
    }

    public record TaskView(Long id, String nodeKey, String taskType, String status,
                           Long assigneeAccountId, Map<String, Object> assigneeSnapshot,
                           LocalDateTime dueAt, LocalDateTime claimedAt, LocalDateTime completedAt,
                           Integer version, List<CandidateView> candidates) {
    }

    public record ActionView(Long id, Long taskId, String nodeKey, String actionCode,
                             String comment, Map<String, Object> input, Map<String, Object> result,
                             String idempotencyKey, Long actedByAccountId, LocalDateTime actedAt) {
    }

    public record ExceptionView(Long id, String nodeKey, String exceptionType, String errorCode,
                                String errorMessage, String policyAction, String status,
                                Long resolvedByAccountId, String resolutionComment,
                                LocalDateTime occurredAt, LocalDateTime resolvedAt, Integer version) {
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
            Long startedByAccountId,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String errorCode,
            String errorMessage,
            Integer version,
            Map<String, Object> variables,
            List<TaskView> tasks,
            List<ActionView> actions,
            List<ExceptionView> exceptions,
            List<String> allowedActions,
            String nextStep) {
    }

    public record ActionResult(Long actionId, boolean replayed, InstanceView instance) {
    }
}
