package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.lifecycle.PlatformLifecycleApi;
import com.unique.examine.plat.lifecycle.PlatformLifecycleFailureRecorder;
import com.unique.examine.plat.lifecycle.PlatformLifecycleOperations;
import com.unique.examine.plat.manage.service.SessionGuard;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class PlatformLifecycleController {
    private static final String TENANT_PERMISSION = "system.tenant.manage";
    private final PlatformLifecycleOperations lifecycle;
    private final PlatformLifecycleFailureRecorder failures;

    public PlatformLifecycleController(
            PlatformLifecycleOperations lifecycle, PlatformLifecycleFailureRecorder failures) {
        this.lifecycle = lifecycle;
        this.failures = failures;
    }

    @PostMapping("/platform/admin/systems/{systemId}/deletion:preview")
    public ApiResponse<PlatformLifecycleApi.DeletePreview> previewSystemDeletion(
            @PathVariable long systemId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(lifecycle.previewDeletion(session, systemId, ControllerSupport.client(request)), request);
    }

    @PostMapping("/platform/admin/systems/{systemId}/deletion:confirm")
    public ApiResponse<PlatformLifecycleApi.DeleteResult> confirmSystemDeletion(
            @PathVariable long systemId,
            @RequestBody PlatformLifecycleApi.DeleteCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(lifecycle.deleteSystem(session, systemId, body, idempotencyKey,
                ControllerSupport.client(request)), request);
    }

    @GetMapping("/systems/{systemId}/admin/tenants/{tenantId}/domains")
    public ApiResponse<List<PlatformLifecycleApi.DomainView>> domains(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.domains(session, systemId, tenantId), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/domains")
    public ApiResponse<PlatformLifecycleApi.DomainView> addDomain(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.DomainCreate body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.addDomain(session, systemId, tenantId, body, idempotencyKey,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/domains/{domainId}:verify")
    public ApiResponse<PlatformLifecycleApi.DomainView> verifyDomain(
            @PathVariable long systemId, @PathVariable long tenantId, @PathVariable long domainId,
            @RequestBody PlatformLifecycleApi.DomainVerify body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.verifyDomain(session, systemId, tenantId, domainId, body,
                ControllerSupport.client(request)), request);
    }

    @PutMapping("/systems/{systemId}/admin/tenants/{tenantId}/domains/{domainId}:primary")
    public ApiResponse<PlatformLifecycleApi.DomainView> makePrimaryDomain(
            @PathVariable long systemId, @PathVariable long tenantId, @PathVariable long domainId,
            @RequestBody PlatformLifecycleApi.VersionCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.makePrimaryDomain(session, systemId, tenantId, domainId, body,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/domains/{domainId}:disable")
    public ApiResponse<PlatformLifecycleApi.DomainView> disableDomain(
            @PathVariable long systemId, @PathVariable long tenantId, @PathVariable long domainId,
            @RequestBody PlatformLifecycleApi.VersionCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.disableDomain(session, systemId, tenantId, domainId, body, idempotencyKey,
                ControllerSupport.client(request)), request);
    }

    @GetMapping("/systems/{systemId}/admin/tenants/{tenantId}/quotas")
    public ApiResponse<List<PlatformLifecycleApi.QuotaView>> quotas(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.quotas(session, systemId, tenantId), request);
    }

    @PutMapping("/systems/{systemId}/admin/tenants/{tenantId}/quotas")
    public ApiResponse<PlatformLifecycleApi.QuotaView> setQuota(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.QuotaSet body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.setQuota(session, systemId, tenantId, body, idempotencyKey,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/quotas/{quotaKey}:adjust")
    public ApiResponse<PlatformLifecycleApi.QuotaView> adjustQuota(
            @PathVariable long systemId, @PathVariable long tenantId, @PathVariable String quotaKey,
            @RequestBody PlatformLifecycleApi.QuotaAdjust body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.adjustQuota(session, systemId, tenantId, quotaKey, body, idempotencyKey,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/lifecycle:backup")
    public ApiResponse<PlatformLifecycleApi.OperationView> backupTenant(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.BackupCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
        HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        var client = ControllerSupport.client(request);
        try {
            return success(lifecycle.backupTenant(session, systemId, tenantId, body, idempotencyKey,
                    client), request);
        } catch (BusinessException failure) {
            recordFailure(session, systemId, tenantId, null, null, "BACKUP", failure.code(), client);
            throw failure;
        } catch (RuntimeException failure) {
            recordFailure(session, systemId, tenantId, null, null, "BACKUP", "INTERNAL_ERROR", client);
            throw failure;
        }
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/lifecycle:recover")
    public ApiResponse<PlatformLifecycleApi.OperationView> recoverTenant(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.RecoveryCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        var client = ControllerSupport.client(request);
        try {
            return success(lifecycle.recoverTenant(session, systemId, tenantId, body, idempotencyKey,
                    client), request);
        } catch (BusinessException failure) {
            recordFailure(session, systemId, tenantId, null,
                    body == null ? null : body.planOperationId(), "RECOVERY", failure.code(), client);
            throw failure;
        } catch (RuntimeException failure) {
            recordFailure(session, systemId, tenantId, null,
                    body == null ? null : body.planOperationId(), "RECOVERY", "INTERNAL_ERROR", client);
            throw failure;
        }
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/lifecycle:recovery-preview")
    public ApiResponse<PlatformLifecycleApi.PlanView> previewTenantRecovery(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.RecoveryPreviewCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.previewTenantRecovery(session, systemId, tenantId, body,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/lifecycle:migration-preview")
    public ApiResponse<PlatformLifecycleApi.PlanView> previewTenantMigration(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.MigrationPreviewCommand body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        return success(lifecycle.previewTenantMigration(session, systemId, tenantId, body,
                ControllerSupport.client(request)), request);
    }

    @PostMapping("/systems/{systemId}/admin/tenants/{tenantId}/lifecycle:migrate")
    public ApiResponse<PlatformLifecycleApi.OperationView> migrateTenant(
            @PathVariable long systemId, @PathVariable long tenantId,
            @RequestBody PlatformLifecycleApi.MigrationCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, TENANT_PERMISSION);
        var client = ControllerSupport.client(request);
        try {
            return success(lifecycle.migrateTenantConfiguration(session, systemId, tenantId, body, idempotencyKey,
                    client), request);
        } catch (BusinessException failure) {
            recordFailure(session, systemId, tenantId, body == null ? null : body.targetTenantId(),
                    body == null ? null : body.planOperationId(), "MIGRATION", failure.code(), client);
            throw failure;
        } catch (RuntimeException failure) {
            recordFailure(session, systemId, tenantId, body == null ? null : body.targetTenantId(),
                    body == null ? null : body.planOperationId(), "MIGRATION", "INTERNAL_ERROR", client);
            throw failure;
        }
    }

    private void recordFailure(AuthenticatedSession session, long systemId, long sourceTenantId,
                               String targetTenantId, String planOperationId, String type,
                               String failureCode, com.unique.examine.plat.manage.service.ClientRequest request) {
        try {
            failures.record(session, systemId, sourceTenantId, targetTenantId,
                    planOperationId, type, failureCode, request);
        } catch (RuntimeException ignored) {
            // Failure evidence must never replace the original lifecycle error.
        }
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(data, ControllerSupport.requestId(request), ControllerSupport.traceId(request));
    }
}
