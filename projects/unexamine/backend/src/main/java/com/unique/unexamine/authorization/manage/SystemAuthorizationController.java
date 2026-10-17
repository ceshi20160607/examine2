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

import java.util.List;

@RestController
@RequestMapping("/api/admin/system/authorization")
@RequirePermission(resourceType = "CONFIG", resourceCode = "SYSTEM", actionCode = "MANAGE")
public class SystemAuthorizationController {
    private final SystemAuthorizationManagementService service;
    private final DepartmentLifecycleService departmentLifecycleService;
    private final SystemInvitationService invitationService;

    public SystemAuthorizationController(SystemAuthorizationManagementService service,
                                         DepartmentLifecycleService departmentLifecycleService,
                                         SystemInvitationService invitationService) {
        this.service = service;
        this.departmentLifecycleService = departmentLifecycleService;
        this.invitationService = invitationService;
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

    /** 一次完成部门归属或同级排序调整，所有校验由业务服务执行。 */
    @PostMapping("/departments/{departmentId}/move")
    public ApiResult<SystemAuthorizationModels.DepartmentView> moveDepartment(
            @PathVariable Long departmentId,
            @Valid @RequestBody SystemAuthorizationModels.DepartmentMoveRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(service.moveDepartment(AuthenticationContextHolder.require(), departmentId, input,
                TraceIdFilter.current(request)));
    }

    @GetMapping("/departments/{departmentId}/impact")
    public ApiResult<SystemAuthorizationModels.DepartmentImpact> departmentImpact(@PathVariable Long departmentId) {
        return ApiResult.ok(departmentLifecycleService.impact(AuthenticationContextHolder.require(), departmentId));
    }

    @PostMapping("/departments/{departmentId}/deactivate")
    public ApiResult<SystemAuthorizationModels.DepartmentView> deactivateDepartment(
            @PathVariable Long departmentId,
            @Valid @RequestBody SystemAuthorizationModels.DepartmentDeactivateRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(departmentLifecycleService.deactivate(AuthenticationContextHolder.require(), departmentId,
                input, TraceIdFilter.current(request)));
    }

    @PostMapping("/departments/{departmentId}/activate")
    public ApiResult<SystemAuthorizationModels.DepartmentView> activateDepartment(
            @PathVariable Long departmentId,
            @Valid @RequestBody SystemAuthorizationModels.DepartmentActivateRequest input,
            HttpServletRequest request) {
        return ApiResult.ok(departmentLifecycleService.activate(AuthenticationContextHolder.require(), departmentId,
                input, TraceIdFilter.current(request)));
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

    /** 邀请令牌只在创建响应中返回，管理员列表只显示业务状态。 */
    @GetMapping("/invitations")
    public ApiResult<List<SystemInvitationModels.InvitationView>> invitations() {
        return ApiResult.ok(invitationService.list(AuthenticationContextHolder.require()));
    }

    @PostMapping("/invitations")
    public ApiResult<SystemInvitationModels.CreatedInvitation> createInvitation(
            @Valid @RequestBody SystemInvitationModels.CreateRequest input, HttpServletRequest request) {
        return ApiResult.ok(invitationService.create(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    @PostMapping("/invitations/{invitationId}/revoke")
    public ApiResult<SystemInvitationModels.InvitationView> revokeInvitation(
            @PathVariable Long invitationId,
            @Valid @RequestBody SystemInvitationModels.RevokeRequest input, HttpServletRequest request) {
        return ApiResult.ok(invitationService.revoke(AuthenticationContextHolder.require(), invitationId, input,
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
