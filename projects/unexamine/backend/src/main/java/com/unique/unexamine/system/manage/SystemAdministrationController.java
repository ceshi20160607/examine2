package com.unique.unexamine.system.manage;

import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/system")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class SystemAdministrationController {
    private final SystemAdministrationService administrationService;

    public SystemAdministrationController(SystemAdministrationService administrationService) {
        this.administrationService = administrationService;
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

    @GetMapping("/tenants")
    public ApiResult<List<TenantView>> tenants() {
        return ApiResult.ok(administrationService.tenants(AuthenticationContextHolder.require()));
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
}
