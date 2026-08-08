package com.unique.examine.plat.lifecycle;

import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.service.ClientRequest;

import java.util.List;

public interface PlatformLifecycleOperations {
    PlatformLifecycleApi.DeletePreview previewDeletion(
            AuthenticatedSession session, long systemId, ClientRequest request);
    PlatformLifecycleApi.DeleteResult deleteSystem(
            AuthenticatedSession session, long systemId, PlatformLifecycleApi.DeleteCommand command,
            String idempotencyKey, ClientRequest request);

    List<PlatformLifecycleApi.DomainView> domains(
            AuthenticatedSession session, long systemId, long tenantId);
    PlatformLifecycleApi.DomainView addDomain(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.DomainCreate command, String idempotencyKey, ClientRequest request);
    PlatformLifecycleApi.DomainView verifyDomain(
            AuthenticatedSession session, long systemId, long tenantId, long domainId,
            PlatformLifecycleApi.DomainVerify command, ClientRequest request);
    PlatformLifecycleApi.DomainView makePrimaryDomain(
            AuthenticatedSession session, long systemId, long tenantId, long domainId,
            PlatformLifecycleApi.VersionCommand command, ClientRequest request);
    PlatformLifecycleApi.DomainView disableDomain(
            AuthenticatedSession session, long systemId, long tenantId, long domainId,
            PlatformLifecycleApi.VersionCommand command, String idempotencyKey, ClientRequest request);

    List<PlatformLifecycleApi.QuotaView> quotas(
            AuthenticatedSession session, long systemId, long tenantId);
    PlatformLifecycleApi.QuotaView setQuota(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.QuotaSet command, String idempotencyKey, ClientRequest request);
    PlatformLifecycleApi.QuotaView adjustQuota(
            AuthenticatedSession session, long systemId, long tenantId, String quotaKey,
            PlatformLifecycleApi.QuotaAdjust command, String idempotencyKey, ClientRequest request);

    PlatformLifecycleApi.OperationView backupTenant(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.BackupCommand command, String idempotencyKey, ClientRequest request);
    PlatformLifecycleApi.PlanView previewTenantRecovery(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.RecoveryPreviewCommand command, ClientRequest request);
    PlatformLifecycleApi.OperationView recoverTenant(
            AuthenticatedSession session, long systemId, long tenantId,
            PlatformLifecycleApi.RecoveryCommand command, String idempotencyKey, ClientRequest request);
    PlatformLifecycleApi.PlanView previewTenantMigration(
            AuthenticatedSession session, long systemId, long sourceTenantId,
            PlatformLifecycleApi.MigrationPreviewCommand command, ClientRequest request);
    PlatformLifecycleApi.OperationView migrateTenantConfiguration(
            AuthenticatedSession session, long systemId, long sourceTenantId,
            PlatformLifecycleApi.MigrationCommand command, String idempotencyKey, ClientRequest request);
}
