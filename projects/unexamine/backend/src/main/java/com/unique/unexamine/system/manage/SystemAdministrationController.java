package com.unique.unexamine.system.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import com.unique.unexamine.foundation.manage.configuration.ContextSettingCategory;
import com.unique.unexamine.foundation.manage.configuration.ContextSettingManagementService;
import com.unique.unexamine.foundation.manage.configuration.ContextSettingView;
import com.unique.unexamine.foundation.manage.configuration.SaveContextSettingRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class SystemAdministrationController {
    private final SystemAdministrationService administrationService;
    private final AccessRequestService accessRequestService;
    private final ContextSettingManagementService settingService;
    private final SystemIdentityMappingService identityMappingService;
    private final SystemDomainService domainService;

    public SystemAdministrationController(
            SystemAdministrationService administrationService,
            AccessRequestService accessRequestService,
            ContextSettingManagementService settingService,
            SystemIdentityMappingService identityMappingService,
            SystemDomainService domainService) {
        this.administrationService = administrationService;
        this.accessRequestService = accessRequestService;
        this.settingService = settingService;
        this.identityMappingService = identityMappingService;
        this.domainService = domainService;
    }

    @GetMapping("/domains")
    public ApiResult<List<SystemDomainView>> domains() {
        return ApiResult.ok(domainService.list(AuthenticationContextHolder.require()));
    }

    @PostMapping("/domains")
    public ApiResult<SystemDomainView> saveDomain(
            @Valid @RequestBody SaveSystemDomainRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(domainService.save(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/domains/{domainId}/verify")
    public ApiResult<SystemDomainView> verifyDomain(
            @PathVariable Long domainId,
            HttpServletRequest request) {
        return ApiResult.ok(domainService.verify(AuthenticationContextHolder.require(), domainId,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/domains/{domainId}/publish")
    public ApiResult<SystemDomainView> publishDomain(
            @PathVariable Long domainId,
            HttpServletRequest request) {
        return ApiResult.ok(domainService.publish(AuthenticationContextHolder.require(), domainId,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/identity-mapping/providers")
    public ApiResult<List<SystemIdentityProviderOption>> identityProviders() {
        return ApiResult.ok(identityMappingService.providers(AuthenticationContextHolder.require()));
    }

    @GetMapping("/identity-mapping/departments")
    public ApiResult<List<SystemDepartmentOption>> identityMappingDepartments() {
        return ApiResult.ok(identityMappingService.departments(AuthenticationContextHolder.require()));
    }

    @GetMapping("/identity-mapping/configuration")
    public ApiResult<SystemIdentityMappingView> identityMappingConfiguration() {
        return ApiResult.ok(identityMappingService.configuration(AuthenticationContextHolder.require()));
    }

    @PostMapping("/identity-mapping/configuration")
    public ApiResult<SystemIdentityMappingView> saveIdentityMappingConfiguration(
            @Valid @RequestBody SaveSystemIdentityMappingRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(identityMappingService.save(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/identity-mapping/preflight")
    public ApiResult<IdentityMappingPreflightReport> runIdentityMappingPreflight(
            @Valid @RequestBody RunIdentityMappingPreflightRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(identityMappingService.preflight(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/identity-mapping/confirm")
    public ApiResult<IdentityMappingJobView> confirmIdentityMapping(
            @Valid @RequestBody ConfirmIdentityMappingRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(identityMappingService.confirm(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/identity-mapping/jobs")
    public ApiResult<List<IdentityMappingJobView>> identityMappingJobs() {
        return ApiResult.ok(identityMappingService.jobs(AuthenticationContextHolder.require()));
    }

    @GetMapping("/identity-mapping/logs")
    public ApiResult<List<IdentityMappingLogView>> identityMappingLogs(
            @RequestParam(required = false) String identityProvider,
            @RequestParam(required = false) String externalUserId,
            @RequestParam(required = false) String mfaLevel,
            @RequestParam(required = false) String device,
            @RequestParam(required = false) String requestId,
            @RequestParam(required = false) String traceId,
            @RequestParam(required = false) String failureReason) {
        return ApiResult.ok(identityMappingService.logs(AuthenticationContextHolder.require(), identityProvider,
                externalUserId, mfaLevel, device, requestId, traceId, failureReason));
    }

    @GetMapping("/configurations/catalog")
    public ApiResult<List<ContextSettingCategory>> configurationCatalog() {
        return ApiResult.ok(settingService.systemCatalog());
    }

    @GetMapping("/configurations")
    public ApiResult<List<ContextSettingView>> configurations() {
        return ApiResult.ok(settingService.listSystem(AuthenticationContextHolder.require()));
    }

    @PostMapping("/configurations")
    public ApiResult<ContextSettingView> saveConfiguration(
            @Valid @RequestBody SaveContextSettingRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(settingService.saveSystem(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/settings")
    public ApiResult<SystemSettingsView> settings() {
        return ApiResult.ok(administrationService.settings(AuthenticationContextHolder.require()));
    }

    @PutMapping("/settings")
    public ApiResult<SystemSettingsView> updateSettings(
            @Valid @RequestBody UpdateSystemSettingsRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(administrationService.updateSettings(AuthenticationContextHolder.require(), request,
                TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/settings/owner-candidates")
    public ApiResult<List<SystemOwnerCandidateView>> ownerCandidates(@RequestParam String query) {
        return ApiResult.ok(administrationService.ownerCandidates(AuthenticationContextHolder.require(), query));
    }

    @PutMapping("/settings/owner")
    public ApiResult<SystemSettingsView> transferOwner(@Valid @RequestBody TransferSystemOwnerRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(administrationService.transferOwner(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/tenants")
    public ApiResult<List<TenantView>> tenants() {
        return ApiResult.ok(administrationService.tenants(AuthenticationContextHolder.require()));
    }

    @GetMapping("/access-requests")
    public ApiResult<List<AccessRequestView>> accessRequests(@RequestParam(required = false) String status) {
        return ApiResult.ok(accessRequestService.queue(AuthenticationContextHolder.require(), status));
    }

    @GetMapping("/access-request-roles")
    public ApiResult<List<SystemRoleOption>> accessRequestRoles() {
        return ApiResult.ok(accessRequestService.roles(AuthenticationContextHolder.require()));
    }

    @PostMapping("/access-requests/{requestId}/decision")
    public ApiResult<AccessRequestView> decideAccessRequest(
            @PathVariable Long requestId,
            @Valid @RequestBody DecideAccessRequest input,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(accessRequestService.decide(AuthenticationContextHolder.require(), requestId, input,
                TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/tenants")
    public ApiResult<TenantView> createTenant(
            @Valid @RequestBody CreateTenantRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(administrationService.createTenant(AuthenticationContextHolder.require(), request,
                TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/tenants/{tenantId}/status")
    public ApiResult<TenantView> updateTenantStatus(
            @PathVariable Long tenantId,
            @Valid @RequestBody UpdateTenantStatusRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(administrationService.updateTenantStatus(AuthenticationContextHolder.require(), tenantId,
                request, TraceIdFilter.current(servletRequest)));
    }

    @PutMapping("/tenants/{tenantId}/login-presentation")
    public ApiResult<TenantView> updateTenantLoginPresentation(@PathVariable Long tenantId,
            @Valid @RequestBody UpdateTenantLoginPresentationRequest request, HttpServletRequest servletRequest) {
        return ApiResult.ok(administrationService.updateTenantLoginPresentation(AuthenticationContextHolder.require(),
                tenantId, request, TraceIdFilter.current(servletRequest)));
    }
}
