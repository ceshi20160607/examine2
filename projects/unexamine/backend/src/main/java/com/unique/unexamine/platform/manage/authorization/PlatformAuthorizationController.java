package com.unique.unexamine.platform.manage.authorization;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
import com.unique.unexamine.authorization.manage.RequirePermission;
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

/** 平台角色授权独立于系统、租户角色授权。 */
@RestController
@RequestMapping("/api/admin/platform/authorization")
@RequirePermission(resourceType = "PLATFORM", resourceCode = "AUTHORIZATION", actionCode = "VIEW")
public class PlatformAuthorizationController {
    private final PlatformAuthorizationService service;

    public PlatformAuthorizationController(PlatformAuthorizationService service) { this.service = service; }

    /** 平台角色、成员与可分配动作清单。 */
    @GetMapping
    public ApiResult<PlatformAuthorizationService.Overview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    /** 建立无权限的新平台角色。 */
    @PostMapping("/roles")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "AUTHORIZATION", actionCode = "MANAGE")
    public ApiResult<PlatformAuthorizationService.RoleView> create(
            @Valid @RequestBody PlatformAuthorizationService.RoleInput input, HttpServletRequest request) {
        return ApiResult.ok(service.create(AuthenticationContextHolder.require(), input, TraceIdFilter.current(request)));
    }

    /** 修改角色名称及用途。 */
    @PutMapping("/roles/{id}")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "AUTHORIZATION", actionCode = "MANAGE")
    public ApiResult<PlatformAuthorizationService.RoleView> rename(@PathVariable Long id,
            @Valid @RequestBody PlatformAuthorizationService.RenameInput input, HttpServletRequest request) {
        return ApiResult.ok(service.rename(AuthenticationContextHolder.require(), id, input, TraceIdFilter.current(request)));
    }

    /** 用页面确认过的授权动作替换普通角色的已知授权。 */
    @PutMapping("/roles/{id}/grants")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "AUTHORIZATION", actionCode = "MANAGE")
    public ApiResult<Void> grants(@PathVariable Long id,
            @Valid @RequestBody PlatformAuthorizationService.GrantsInput input, HttpServletRequest request) {
        service.replaceGrants(AuthenticationContextHolder.require(), id, input, TraceIdFilter.current(request));
        return ApiResult.ok(null);
    }

    /** 调整现有平台成员的普通平台角色，不触及系统权限。 */
    @PutMapping("/members/{id}/roles")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "AUTHORIZATION", actionCode = "MANAGE")
    public ApiResult<Void> memberRoles(@PathVariable Long id,
            @Valid @RequestBody PlatformAuthorizationService.MemberRolesInput input, HttpServletRequest request) {
        service.assignMember(AuthenticationContextHolder.require(), id, input, TraceIdFilter.current(request));
        return ApiResult.ok(null);
    }
}
