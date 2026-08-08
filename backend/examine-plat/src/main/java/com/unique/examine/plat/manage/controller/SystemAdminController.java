package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.dto.SystemAdminRequests;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.service.PermissionEvaluationService;
import com.unique.examine.plat.manage.service.SystemAccessRequestAdminService;
import com.unique.examine.plat.manage.service.SystemAdminMutationSupport;
import com.unique.examine.plat.manage.service.SystemAdminOrganizationService;
import com.unique.examine.plat.manage.service.SystemAdminSettingsService;
import com.unique.examine.plat.manage.service.SystemRoleAdminService;
import com.unique.examine.plat.manage.vo.SystemAdminViews;
import com.unique.examine.plat.manage.vo.PermissionEvaluationModels;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/systems/{systemId}/admin")
public class SystemAdminController {
    static final String ORGANIZATION_PERMISSION = "system.organization.manage";
    static final String MEMBER_PERMISSION = "system.member.manage";

    private final SystemAdminSettingsService settingsService;
    private final SystemAdminOrganizationService organizationService;
    private final SystemRoleAdminService roleService;
    private final SystemAccessRequestAdminService accessRequestService;
    private final PermissionEvaluationService permissionEvaluationService;

    public SystemAdminController(
            SystemAdminSettingsService settingsService,
            SystemAdminOrganizationService organizationService,
            SystemRoleAdminService roleService,
            SystemAccessRequestAdminService accessRequestService,
            PermissionEvaluationService permissionEvaluationService
    ) {
        this.settingsService = settingsService;
        this.organizationService = organizationService;
        this.roleService = roleService;
        this.accessRequestService = accessRequestService;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @GetMapping("/settings")
    public ApiResponse<SystemAdminViews.Settings> settings(
            @PathVariable long systemId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.settings.manage");
        return success(settingsService.settings(session, systemId), request);
    }

    @PutMapping("/settings")
    public ApiResponse<SystemAdminViews.Settings> updateSettings(
            @PathVariable long systemId,
            @Valid @RequestBody SystemAdminRequests.UpdateSettings body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.settings.manage");
        return success(settingsService.updateSettings(session, systemId, body, ControllerSupport.client(request)), request);
    }

    @GetMapping("/tenants")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.Tenant>> tenants(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.tenant.manage");
        return success(settingsService.tenants(session, systemId, page, size, keyword, status), request);
    }

    @PostMapping("/tenants")
    public ApiResponse<SystemAdminViews.Tenant> createTenant(
            @PathVariable long systemId,
            @Valid @RequestBody SystemAdminRequests.CreateTenant body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.tenant.manage");
        return success(settingsService.createTenant(
                session, systemId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/tenants/{tenantId}")
    public ApiResponse<SystemAdminViews.Tenant> updateTenant(
            @PathVariable long systemId,
            @PathVariable long tenantId,
            @Valid @RequestBody SystemAdminRequests.UpdateTenant body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.tenant.manage");
        return success(settingsService.updateTenant(
                session, systemId, tenantId, body, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/tenants/{tenantId}:{command}")
    public ApiResponse<SystemAdminViews.Tenant> commandTenant(
            @PathVariable long systemId,
            @PathVariable long tenantId,
            @PathVariable String command,
            @Valid @RequestBody SystemAdminRequests.LifecycleCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.tenant.manage");
        return success(settingsService.commandTenant(
                session, systemId, tenantId, command, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/departments")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.Department>> departments(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, ORGANIZATION_PERMISSION);
        return success(organizationService.departments(session, systemId, page, size, keyword, status), request);
    }

    @PostMapping("/departments")
    public ApiResponse<SystemAdminViews.Department> createDepartment(
            @PathVariable long systemId,
            @Valid @RequestBody SystemAdminRequests.CreateDepartment body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, ORGANIZATION_PERMISSION);
        return success(organizationService.createDepartment(
                session, systemId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/departments/{departmentId}")
    public ApiResponse<SystemAdminViews.Department> updateDepartment(
            @PathVariable long systemId,
            @PathVariable long departmentId,
            @Valid @RequestBody SystemAdminRequests.UpdateDepartment body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, ORGANIZATION_PERMISSION);
        return success(organizationService.updateDepartment(
                session, systemId, departmentId, body, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/departments/{departmentId}/leader")
    public ApiResponse<SystemAdminViews.Department> updateDepartmentLeader(
            @PathVariable long systemId,
            @PathVariable long departmentId,
            @Valid @RequestBody SystemAdminRequests.UpdateDepartmentLeader body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, ORGANIZATION_PERMISSION);
        return success(organizationService.updateDepartmentLeader(
                session, systemId, departmentId, body, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/members")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.Member>> members(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.member.list");
        return success(organizationService.members(session, systemId, page, size, keyword, status), request);
    }

    @PutMapping("/members/{memberId}")
    public ApiResponse<SystemAdminViews.Member> updateMember(
            @PathVariable long systemId,
            @PathVariable long memberId,
            @Valid @RequestBody SystemAdminRequests.UpdateMember body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, MEMBER_PERMISSION);
        return success(organizationService.updateMember(
                session, systemId, memberId, body, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/members/{memberId}/manager")
    public ApiResponse<SystemAdminViews.Member> updateMemberManager(
            @PathVariable long systemId,
            @PathVariable long memberId,
            @Valid @RequestBody SystemAdminRequests.UpdateMemberManager body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, MEMBER_PERMISSION);
        return success(organizationService.updateMemberManager(
                session, systemId, memberId, body, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/roles")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.Role>> roles(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.role.manage");
        return success(roleService.roles(session, systemId, page, size, keyword, status), request);
    }

    @PostMapping("/roles")
    public ApiResponse<SystemAdminViews.Role> createRole(
            @PathVariable long systemId,
            @Valid @RequestBody SystemAdminRequests.CreateRole body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.role.manage");
        return success(roleService.createRole(
                session, systemId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/roles/{roleId}/draft")
    public ApiResponse<SystemAdminViews.Role> saveRoleDraft(
            @PathVariable long systemId,
            @PathVariable long roleId,
            @Valid @RequestBody SystemAdminRequests.SaveRoleDraft body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.role.manage");
        return success(roleService.saveDraft(
                session, systemId, roleId, body, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/roles/{roleId}/draft:check")
    public ApiResponse<SystemAdminViews.Role> checkRoleDraft(
            @PathVariable long systemId,
            @PathVariable long roleId,
            @Valid @RequestBody SystemAdminRequests.DraftCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.role.manage");
        return success(roleService.checkDraft(
                session, systemId, roleId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/roles/{roleId}/draft:publish")
    public ApiResponse<SystemAdminViews.Role> publishRoleDraft(
            @PathVariable long systemId,
            @PathVariable long roleId,
            @Valid @RequestBody SystemAdminRequests.DraftCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.role.manage");
        return success(roleService.publishDraft(
                session, systemId, roleId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/permissions")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.Permission>> permissions(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requireSystem(sessionValue, systemId, "system.permission.explain");
        return success(roleService.permissions(systemId, page, size, keyword, status), request);
    }

    @GetMapping("/data-scopes")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.DataScope>> dataScopes(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.role.manage");
        return success(roleService.dataScopes(session, systemId, page, size, keyword, status), request);
    }

    @GetMapping("/permission-evaluations")
    public ApiResponse<PermissionEvaluationModels.Result> evaluatePermissions(
            @PathVariable long systemId,
            @RequestParam String memberId,
            @RequestParam(required = false) String tenantId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.permission.explain");
        var targetTenantId = tenantId == null || tenantId.isBlank()
                ? session.tenantId()
                : SystemAdminMutationSupport.id(tenantId, "tenantId");
        return success(permissionEvaluationService.system(
                systemId,
                targetTenantId,
                SystemAdminMutationSupport.id(memberId, "memberId")
        ), request);
    }

    @GetMapping("/access-requests")
    public ApiResponse<SystemAdminViews.Page<SystemAdminViews.AccessRequest>> accessRequests(
            @PathVariable long systemId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.access.review");
        return success(accessRequestService.accessRequests(
                session, systemId, page, size, keyword, status
        ), request);
    }

    @PostMapping("/access-requests/{requestId}:approve")
    public ApiResponse<SystemAdminViews.AccessRequest> approveAccessRequest(
            @PathVariable long systemId,
            @PathVariable long requestId,
            @Valid @RequestBody SystemAdminRequests.ReviewAccessRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.access.review");
        return success(accessRequestService.approve(
                session, systemId, requestId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/access-requests/{requestId}:reject")
    public ApiResponse<SystemAdminViews.AccessRequest> rejectAccessRequest(
            @PathVariable long systemId,
            @PathVariable long requestId,
            @Valid @RequestBody SystemAdminRequests.ReviewAccessRequest body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requireSystem(sessionValue, systemId, "system.access.review");
        return success(accessRequestService.reject(
                session, systemId, requestId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data, ControllerSupport.requestId(request), ControllerSupport.traceId(request)
        );
    }
}
