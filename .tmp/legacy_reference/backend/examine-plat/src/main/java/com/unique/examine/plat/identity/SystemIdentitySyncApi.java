package com.unique.examine.plat.identity;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class SystemIdentitySyncApi {
    private SystemIdentitySyncApi() { }

    public record InheritedProvider(
            String id, String providerCode, String name, String protocol,
            List<String> providerDomains, boolean jitAccount, String mfaPolicy, long version
    ) { }

    public record PolicyCommand(
            String tenantId,
            String providerId,
            List<String> allowedDomains,
            boolean jitSystemMember,
            String unmatchedAction,
            boolean scheduleEnabled,
            Integer scheduleIntervalMinutes,
            Long expectedVersion
    ) { }

    public record PolicyView(
            String id, String systemId, String tenantId, String providerId,
            String providerCode, String providerName, List<String> allowedDomains,
            boolean jitSystemMember, String unmatchedAction, boolean scheduleEnabled,
            Integer scheduleIntervalMinutes, Instant nextSyncAt, String status,
            String lastConfirmedSnapshotId, Instant lastSyncAt, long version
    ) { }

    public record ExternalDepartment(
            String externalId, String name, String parentExternalId,
            String targetDepartmentId, Map<String, Object> attributes
    ) { }

    public record ExternalEmployee(
            String externalUserId, String email, String displayName, String employeeNo,
            String departmentExternalId, String targetMemberId, Map<String, Object> attributes
    ) { }

    public record PreflightCommand(
            String sourceVersion,
            long expectedPolicyVersion,
            List<ExternalDepartment> departments,
            List<ExternalEmployee> employees
    ) { }

    public record SyncItemView(
            String id, String kind, String externalId, String displayName,
            String parentExternalId, String email, String departmentExternalId,
            String targetDepartmentId, String targetAccountId, String targetMemberId,
            String proposedAction, String issueCode, Map<String, Object> attributes
    ) { }

    public record SnapshotView(
            String id, String policyId, String sourceVersion, String status,
            int departmentCount, int employeeCount, int matchedCount,
            int createCount, int unmatchedCount, Instant confirmedAt,
            String confirmedBy, Instant appliedAt, long version, List<SyncItemView> items
    ) { }

    public record ConfirmCommand(
            long expectedPolicyVersion, long expectedSnapshotVersion, boolean approveUnmatched
    ) { }

    public record StartCommand(long expectedSnapshotVersion) { }

    public record FailureView(
            String itemId, String kind, String externalId, String failureCode,
            String failureMessage, Instant createdAt
    ) { }

    public record TaskView(
            String id, String snapshotId, String trigger, String status, int progressPercent,
            int attemptCount, int maxAttempts, String lastError, Map<String, Object> result,
            LocalDateTime availableAt, LocalDateTime startedAt, LocalDateTime finishedAt,
            LocalDateTime createdAt, List<FailureView> failures
    ) { }
}
