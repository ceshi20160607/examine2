package com.unique.examine.plat.lifecycle;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PlatformLifecycleApi {
    private PlatformLifecycleApi() { }

    public record DeletePreview(
            String previewId,
            String systemId,
            String systemVersion,
            boolean eligible,
            Map<String, Long> dependencies,
            List<String> blockers,
            String impactFingerprint,
            String confirmationToken,
            LocalDateTime expiresAt
    ) {
        public DeletePreview {
            dependencies = Map.copyOf(dependencies);
            blockers = List.copyOf(blockers);
        }
    }

    public record DeleteCommand(
            String previewId,
            String confirmationToken,
            String expectedVersion,
            String reason,
            boolean impactConfirmed
    ) { }

    public record DeleteResult(
            String systemId,
            String status,
            int revokedSessions,
            int disabledTenants,
            String previewId,
            LocalDateTime deletedAt
    ) { }

    public record DomainCreate(String domainName) { }
    public record DomainVerify(String verificationToken, String expectedVersion) { }
    public record VersionCommand(String expectedVersion, String reason, boolean impactConfirmed) { }

    public record DomainView(
            String id,
            String systemId,
            String tenantId,
            String domainName,
            String status,
            boolean primary,
            String verificationToken,
            LocalDateTime verifiedAt,
            String version
    ) { }

    public record QuotaSet(
            String quotaKey,
            Long softLimit,
            Long hardLimit,
            String expectedVersion
    ) { }

    public record QuotaAdjust(
            long delta,
            String reason,
            String expectedVersion
    ) { }

    public record QuotaView(
            String id,
            String systemId,
            String tenantId,
            String quotaKey,
            Long softLimit,
            long hardLimit,
            long usedValue,
            boolean softLimitExceeded,
            String status,
            String version
    ) { }

    public record BackupCommand(String reason, String expectedTenantVersion) { }
    public record RecoveryPreviewCommand(
            String backupOperationId,
            String expectedTenantVersion
    ) { }
    public record RecoveryCommand(
            String planOperationId,
            String confirmationToken,
            String reason,
            String expectedTenantVersion,
            boolean impactConfirmed
    ) { }
    public record MigrationPreviewCommand(
            String targetTenantId,
            String expectedSourceVersion,
            String expectedTargetVersion
    ) { }
    public record MigrationCommand(
            String planOperationId,
            String confirmationToken,
            String targetTenantId,
            String reason,
            String expectedSourceVersion,
            String expectedTargetVersion,
            boolean impactConfirmed
    ) { }

    public record PlanView(
            String id,
            String systemId,
            String sourceTenantId,
            String targetTenantId,
            String operationType,
            String status,
            boolean eligible,
            List<String> blockers,
            Map<String, Object> tableImpacts,
            Map<String, Object> quotaProjection,
            String planFingerprint,
            String confirmationToken,
            LocalDateTime expiresAt,
            long rowCount,
            long estimatedBytes,
            String databaseMigrationVersion
    ) {
        public PlanView {
            blockers = List.copyOf(blockers);
            tableImpacts = Map.copyOf(tableImpacts);
            quotaProjection = Map.copyOf(quotaProjection);
        }
    }

    public record OperationView(
            String id,
            String systemId,
            String sourceTenantId,
            String targetTenantId,
            String planOperationId,
            String operationType,
            String status,
            String reason,
            String snapshotChecksum,
            Map<String, Object> result,
            long payloadRowCount,
            long payloadSizeBytes,
            String databaseMigrationVersion,
            LocalDateTime requestedAt,
            LocalDateTime finishedAt
    ) {
        public OperationView { result = Map.copyOf(result); }
    }
}
