package com.unique.examine.core.manage.ops;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Ops governance API models.
 */
public final class OpsGovernanceModels {

    private OpsGovernanceModels() {
    }

    public record HealthCheckRequest(String checkType, List<String> checkItems, String requestedBy,
                                     String idempotencyKey) {
    }

    public record OpsHealthCheckVO(String checkId, String scope, String systemId, String checkType, String status,
                                   List<HealthCheckItemVO> checks, List<RiskItemVO> risks,
                                   List<String> repairEntries, BackupRestoreVO latestBackup, String traceId,
                                   String auditLogId, LocalDateTime checkedAt) {
    }

    public record HealthCheckItemVO(String itemCode, String itemName, String status, String description,
                                    String repairEntry) {
    }

    public record RiskItemVO(String riskCode, String severity, String description, String disabledReason) {
    }

    public record FeatureFlagQueryRequest(String scope, String systemId, String tenantId, Integer status,
                                          String keyword) {
    }

    public record FeatureFlagUpdateRequest(Integer status, String rules, String rollbackVersion, String reason,
                                           String idempotencyKey) {
    }

    public record FeatureFlagVO(String flagId, String flagCode, String scope, String systemId, String tenantId,
                                Integer status, String rules, String rollbackVersion, String traceId,
                                String auditLogId, LocalDateTime updatedAt) {
    }

    public record QuotaQueryRequest(String scope, String systemId, String tenantId, String quotaType,
                                    String keyword) {
    }

    public record QuotaUpdateRequest(Long limit, Long warnThreshold, String reason, String idempotencyKey) {
    }

    public record QuotaVO(String quotaId, String scope, String systemId, String tenantId, String quotaType,
                          Long limit, Long used, Long warnThreshold, String status, String traceId,
                          String auditLogId, LocalDateTime updatedAt) {
    }

    public record RateLimitQueryRequest(String scope, String systemId, String tenantId, Integer status,
                                        String keyword) {
    }

    public record RateLimitUpdateRequest(String limitRule, Integer status, String reason, String idempotencyKey) {
    }

    public record RateLimitPolicyVO(String policyId, String scope, String systemId, String tenantId,
                                    String policyCode, String limitRule, Integer status, String traceId,
                                    String auditLogId, LocalDateTime updatedAt) {
    }

    public record BackupCreateRequest(String backupType, String scope, String systemId, List<String> boundaries,
                                      String reason, String idempotencyKey) {
    }

    public record BackupRestoreVO(String backupId, String backupNo, String backupType, String scope, String systemId,
                                  String status, List<String> boundaries, String resultSummary, String traceId,
                                  String auditLogId, LocalDateTime createdAt) {
    }

    public record RestoreDrillRequest(String drillScope, String reason, Boolean requireDestructiveOperations,
                                      String idempotencyKey) {
    }

    public record ArchiveRestoreRequest(String scope, String systemId, String objectType, String archiveCondition,
                                        String reason, String idempotencyKey) {
    }

    public record DeploymentQueryRequest(String envCode, String status, String keyword, String timeRange) {
    }

    public record DeploymentVO(String deploymentId, String deploymentNo, String envCode, String backendVersion,
                               String frontendVersion, String configVersion, String status, String rollbackPlan,
                               boolean destructiveScriptConfirmed, String traceId, String auditLogId,
                               LocalDateTime createdAt) {
    }

    public record DeploymentRollbackRequest(String rollbackTargetVersion, String reason,
                                            Boolean confirmNoDestructiveScript, String idempotencyKey) {
    }

    public record ApiCachePolicyUpdateRequest(String keyRule, String invalidationRule, Integer status, String reason,
                                              String idempotencyKey) {
    }

    public record ApiCachePolicyVO(String policyId, String policyCode, String cacheDomain, String keyRule,
                                   String invalidationRule, Integer status, String traceId, String auditLogId,
                                   LocalDateTime updatedAt) {
    }
}
