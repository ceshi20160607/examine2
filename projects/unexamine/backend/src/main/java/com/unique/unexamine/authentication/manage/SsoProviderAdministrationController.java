package com.unique.unexamine.authentication.manage;

import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.shared.manage.web.ApiResult;
import com.unique.unexamine.shared.manage.web.TraceIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/platform/sso-providers")
@RequirePermission(resourceType = "PLATFORM", resourceCode = "IDENTITY_PROVIDER", actionCode = "MANAGE")
public class SsoProviderAdministrationController {
    private final SsoProviderAdministrationService administrationService;

    public SsoProviderAdministrationController(SsoProviderAdministrationService administrationService) {
        this.administrationService = administrationService;
    }

    @GetMapping
    public ApiResult<List<SsoProviderAdminView>> list() {
        return ApiResult.ok(administrationService.list());
    }

    @PostMapping
    public ApiResult<SsoProviderAdminView> create(
            @Valid @RequestBody SsoProviderDraftRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(administrationService.create(
                AuthenticationContextHolder.require(), input, TraceIdFilter.current(request)));
    }

    @PostMapping("/{providerId}/versions")
    public ApiResult<SsoProviderVersionView> createVersion(
            @PathVariable Long providerId,
            @Valid @RequestBody SsoProviderVersionDraftRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(administrationService.createVersion(
                AuthenticationContextHolder.require(), providerId, input, TraceIdFilter.current(request)));
    }

    @PostMapping("/{providerId}/versions/{versionId}/test")
    public ApiResult<SsoProviderTestReport> test(
            @PathVariable Long providerId,
            @PathVariable Long versionId,
            HttpServletRequest request) {
        return ApiResult.ok(administrationService.test(
                AuthenticationContextHolder.require(), providerId, versionId, TraceIdFilter.current(request)));
    }

    @PostMapping("/{providerId}/versions/{versionId}/publish")
    public ApiResult<SsoProviderAdminView> publish(
            @PathVariable Long providerId,
            @PathVariable Long versionId,
            HttpServletRequest request) {
        return ApiResult.ok(administrationService.publish(
                AuthenticationContextHolder.require(), providerId, versionId, TraceIdFilter.current(request)));
    }
}
