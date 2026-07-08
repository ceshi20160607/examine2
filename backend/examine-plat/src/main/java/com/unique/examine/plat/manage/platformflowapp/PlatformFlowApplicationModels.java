package com.unique.examine.plat.manage.platformflowapp;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Platform Flow and Application readback API models.
 */
public final class PlatformFlowApplicationModels {

    private PlatformFlowApplicationModels() {
    }

    public record PlatformFlowQuery(String keyword, String status) {
    }

    public record PlatformFlowSaveRequest(String flowCode, String flowName, String triggerSource,
                                          List<String> affectedSystems, String nodeSummary,
                                          String retryPolicy, String compensationPolicy,
                                          String idempotencyKey) {
    }

    public record PlatformFlowActionRequest(String reason, String idempotencyKey) {
    }

    public record PlatformFlowView(String flowId, String flowCode, String flowName, String triggerSource,
                                   List<String> affectedSystems, String nodeSummary, String status,
                                   String retryPolicy, String compensationPolicy, String currentRunBatchId,
                                   String traceId, String auditLogId, String latestTaskId, String latestTodoId,
                                   String latestMessageId, String permissionMode, boolean canManage,
                                   LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PlatformFlowRunResult(String flowId, String runBatchId, String action, String status,
                                        String taskId, String retryTaskId, String compensationTaskId,
                                        String traceId, String auditLogId, String todoId, String messageId,
                                        String boundary, LocalDateTime createdAt) {
    }

    public record PlatformAuthorizationQuery(String keyword, String status, String approvalStatus) {
    }

    public record PlatformAuthorizationSaveRequest(String applicationName, String applicationType,
                                                   String targetSystemId, String targetTenantId,
                                                   List<String> moduleScope, List<String> scope,
                                                   String expiryAt, String dataIsolation,
                                                   String approvalStatus, String reason,
                                                   String idempotencyKey) {
    }

    public record PlatformAuthorizationActionRequest(String reason, List<String> scope, String expiryAt,
                                                     String approvalStatus, String idempotencyKey) {
    }

    public record PlatformAuthorizationView(String authorizationId, String applicationName, String applicationType,
                                            String targetSystemId, String targetTenantId,
                                            List<String> moduleScope, List<String> scope,
                                            String expiryAt, String dataIsolation, String requestId,
                                            String authorizationChangeId, String approvalStatus, String status,
                                            String traceId, String auditLogId, String latestTodoId,
                                            String latestMessageId, String permissionMode, boolean canManage,
                                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PlatformAuthorizationActionResult(String authorizationId, String requestId,
                                                    String authorizationChangeId, String action, String status,
                                                    String traceId, String auditLogId, String todoId,
                                                    String messageId, String boundary, LocalDateTime operatedAt) {
    }
}
