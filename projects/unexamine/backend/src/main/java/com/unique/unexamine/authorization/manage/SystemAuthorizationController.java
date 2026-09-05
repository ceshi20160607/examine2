package com.unique.unexamine.authorization.manage;

import com.unique.unexamine.authentication.manage.AuthenticationContextHolder;
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

@RestController
@RequestMapping("/api/admin/system/authorization")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class SystemAuthorizationController {
    private final SystemAuthorizationManagementService service;

    public SystemAuthorizationController(SystemAuthorizationManagementService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResult<SystemAuthorizationModels.Overview> overview() {
        return ApiResult.ok(service.overview(AuthenticationContextHolder.require()));
    }

    @PostMapping("/departments")
    public ApiResult<SystemAuthorizationModels.DepartmentView> saveDepartment(
            @Valid @RequestBody SystemAuthorizationModels.DepartmentRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveDepartment(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/members/{tenantMemberId}/assignment")
    public ApiResult<SystemAuthorizationModels.MemberView> assignMember(
            @PathVariable Long tenantMemberId,
            @Valid @RequestBody SystemAuthorizationModels.MemberAssignmentRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.assignMember(AuthenticationContextHolder.require(), tenantMemberId, input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/members")
    public ApiResult<SystemAuthorizationModels.MemberView> addMember(
            @Valid @RequestBody SystemAuthorizationModels.AddMemberRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.addMember(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/roles")
    public ApiResult<SystemAuthorizationModels.RoleView> saveRole(
            @Valid @RequestBody SystemAuthorizationModels.SaveRoleRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.saveRole(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/roles/{roleId}/publish")
    public ApiResult<SystemAuthorizationModels.RoleView> publishRole(
            @PathVariable Long roleId,
            @Valid @RequestBody SystemAuthorizationModels.PublishRoleRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.publishRole(AuthenticationContextHolder.require(), roleId, input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/preview")
    public ApiResult<SystemAuthorizationModels.PermissionPreview> preview(
            @Valid @RequestBody SystemAuthorizationModels.PreviewRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.preview(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }
}
