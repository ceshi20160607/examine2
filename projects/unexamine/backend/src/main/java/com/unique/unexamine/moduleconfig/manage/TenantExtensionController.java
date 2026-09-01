package com.unique.unexamine.moduleconfig.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.PublishTenantExtensionRequest;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.RemoveTenantExtensionRequest;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.RollbackTenantExtensionRequest;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.SaveTenantExtensionRequest;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.TenantExtensionCheck;
import com.unique.unexamine.moduleconfig.manage.TenantExtensionModels.TenantExtensionModuleView;
import com.unique.unexamine.shared.manage.web.ApiResult;
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
@RequestMapping("/api/admin/tenant-extensions")
@RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "MANAGE")
public class TenantExtensionController {
    private final TenantExtensionService service;

    public TenantExtensionController(TenantExtensionService service) {
        this.service = service;
    }

    @GetMapping
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PREVIEW")
    public ApiResult<List<TenantExtensionModuleView>> list() {
        return ApiResult.ok(service.list(AuthenticationContextHolder.require()));
    }

    @GetMapping("/modules/{moduleId}")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PREVIEW")
    public ApiResult<TenantExtensionModuleView> get(@PathVariable Long moduleId) {
        return ApiResult.ok(service.get(AuthenticationContextHolder.require(), moduleId));
    }

    @PutMapping("/modules/{moduleId}")
    public ApiResult<TenantExtensionModuleView> save(
            @PathVariable Long moduleId,
            @Valid @RequestBody SaveTenantExtensionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.save(AuthenticationContextHolder.require(), moduleId, request,
                TraceIdFilter.current(servletRequest)));
    }

    @GetMapping("/modules/{moduleId}/publication-check")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PREVIEW")
    public ApiResult<TenantExtensionCheck> check(@PathVariable Long moduleId) {
        return ApiResult.ok(service.check(AuthenticationContextHolder.require(), moduleId));
    }

    @PostMapping("/modules/{moduleId}/publish")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "PUBLISH")
    public ApiResult<TenantExtensionModuleView> publish(
            @PathVariable Long moduleId,
            @Valid @RequestBody PublishTenantExtensionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.publish(AuthenticationContextHolder.require(), moduleId,
                request.expectedDraftRevision(), TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/modules/{moduleId}/remove")
    public ApiResult<TenantExtensionModuleView> remove(
            @PathVariable Long moduleId,
            @Valid @RequestBody RemoveTenantExtensionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.remove(AuthenticationContextHolder.require(), moduleId,
                request.expectedVersion(), TraceIdFilter.current(servletRequest)));
    }

    @PostMapping("/modules/{moduleId}/rollback")
    @RequirePermission(resourceType = "CONFIG", resourceCode = "MODULE", actionCode = "ROLLBACK")
    public ApiResult<TenantExtensionModuleView> rollback(
            @PathVariable Long moduleId,
            @Valid @RequestBody RollbackTenantExtensionRequest request,
            HttpServletRequest servletRequest) {
        return ApiResult.ok(service.rollback(AuthenticationContextHolder.require(), moduleId, request,
                TraceIdFilter.current(servletRequest)));
    }
}
