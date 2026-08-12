package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.AccountUpdate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.AccountView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.DepartmentCreate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.DepartmentUpdate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.DepartmentView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.LifecycleCommand;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PageResult;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PermissionView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.RoleCreate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.RoleDraftInput;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.RoleView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemCreate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemUpdate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.SystemView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.VersionInput;
import com.unique.examine.plat.manage.service.PlatformOrganizationAdminService;
import com.unique.examine.plat.manage.service.PermissionEvaluationService;
import com.unique.examine.plat.manage.service.PlatformMutationSupport;
import com.unique.examine.plat.manage.service.PlatformRoleAdminService;
import com.unique.examine.plat.manage.service.PlatformSystemAdminService;
import com.unique.examine.plat.manage.service.SessionGuard;
import com.unique.examine.plat.manage.vo.PermissionEvaluationModels;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/platform/admin")
public class PlatformAdminController {
    private final PlatformSystemAdminService systemService;
    private final PlatformOrganizationAdminService organizationService;
    private final PlatformRoleAdminService roleService;
    private final PermissionEvaluationService permissionEvaluationService;

    public PlatformAdminController(
            PlatformSystemAdminService systemService,
            PlatformOrganizationAdminService organizationService,
            PlatformRoleAdminService roleService,
            PermissionEvaluationService permissionEvaluationService
    ) {
        this.systemService = systemService;
        this.organizationService = organizationService;
        this.roleService = roleService;
        this.permissionEvaluationService = permissionEvaluationService;
    }

    @GetMapping("/systems")
    public ApiResponse<PageResult<SystemView>> systems(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(systemService.list(page, size, keyword, status), request);
    }

    @PostMapping("/systems")
    public ApiResponse<SystemView> createSystem(
            @RequestBody SystemCreate body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(systemService.create(
                session, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/systems/{systemId}")
    public ApiResponse<SystemView> updateSystem(
            @PathVariable String systemId,
            @RequestBody SystemUpdate body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(systemService.update(
                session, systemId, body, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/systems/{systemId}:{command}")
    public ApiResponse<SystemView> commandSystem(
            @PathVariable String systemId,
            @PathVariable String command,
            @RequestBody LifecycleCommand body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.system.manage");
        return success(systemService.command(
                session, systemId, command, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/accounts")
    public ApiResponse<PageResult<AccountView>> accounts(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(organizationService.listAccounts(page, size, keyword, status), request);
    }

    @PutMapping("/accounts/{accountId}")
    public ApiResponse<AccountView> updateAccount(
            @PathVariable String accountId,
            @RequestBody AccountUpdate body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(organizationService.updateAccount(
                session, accountId, body, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/departments")
    public ApiResponse<PageResult<DepartmentView>> departments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(departmentsPage(page, size, keyword, status), request);
    }

    @PostMapping("/departments")
    public ApiResponse<DepartmentView> createDepartment(
            @RequestBody DepartmentCreate body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(organizationService.createDepartment(
                session, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/departments/{departmentId}")
    public ApiResponse<DepartmentView> updateDepartment(
            @PathVariable String departmentId,
            @RequestBody DepartmentUpdate body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.organization.manage");
        return success(organizationService.updateDepartment(
                session, departmentId, body, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/roles")
    public ApiResponse<PageResult<RoleView>> roles(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.role.manage");
        return success(roleService.listRoles(page, size, keyword, status), request);
    }

    @PostMapping("/roles")
    public ApiResponse<RoleView> createRole(
            @RequestBody RoleCreate body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.role.manage");
        return success(roleService.createRole(
                session, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PutMapping("/roles/{roleId}/draft")
    public ApiResponse<RoleView> saveRoleDraft(
            @PathVariable String roleId,
            @RequestBody RoleDraftInput body,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.role.manage");
        return success(roleService.saveDraft(
                session, roleId, body, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/roles/{roleId}/draft:check")
    public ApiResponse<RoleView> checkRoleDraft(
            @PathVariable String roleId,
            @RequestBody VersionInput body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.role.manage");
        return success(roleService.checkDraft(
                session, roleId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @PostMapping("/roles/{roleId}/draft:publish")
    public ApiResponse<RoleView> publishRoleDraft(
            @PathVariable String roleId,
            @RequestBody VersionInput body,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        var session = SessionGuard.requirePlatform(sessionValue, "platform.role.manage");
        return success(roleService.publishDraft(
                session, roleId, body, idempotencyKey, ControllerSupport.client(request)
        ), request);
    }

    @GetMapping("/permissions")
    public ApiResponse<PageResult<PermissionView>> permissions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.role.manage");
        return success(roleService.listPermissions(page, size, keyword, status), request);
    }

    @GetMapping("/permission-evaluations")
    public ApiResponse<PermissionEvaluationModels.Result> evaluatePermissions(
            @RequestParam String accountId,
            @RequestAttribute(value = AuthenticatedSession.REQUEST_ATTRIBUTE, required = false) Object sessionValue,
            HttpServletRequest request
    ) {
        SessionGuard.requirePlatform(sessionValue, "platform.permission.explain");
        return success(permissionEvaluationService.platform(PlatformMutationSupport.id(accountId)), request);
    }

    private static <T> ApiResponse<T> success(T data, HttpServletRequest request) {
        return ApiResponse.success(
                data, ControllerSupport.requestId(request), ControllerSupport.traceId(request)
        );
    }

    private PageResult<DepartmentView> departmentsPage(
            Integer pageValue,
            Integer sizeValue,
            String keyword,
            String status
    ) {
        var requestedPage = pageValue == null ? 1 : pageValue;
        var requestedSize = sizeValue == null ? 20 : sizeValue;
        if (requestedPage < 1 || requestedSize < 1 || requestedSize > 500) {
            throw com.unique.examine.plat.manage.service.PlatformMutationSupport.validation(
                    "page must be positive and size must be between 1 and 500"
            );
        }
        if (requestedSize <= 100) {
            return organizationService.listDepartments(
                    requestedPage, requestedSize, keyword, status
            );
        }

        var requestedOffset = (long) (requestedPage - 1) * requestedSize;
        var firstSourcePage = Math.toIntExact(requestedOffset / 100) + 1;
        var firstSourceOffset = Math.toIntExact(requestedOffset % 100);
        var items = new ArrayList<DepartmentView>(requestedSize);
        long total = 0;
        var sourcePage = firstSourcePage;
        while (items.size() < requestedSize) {
            var chunk = organizationService.listDepartments(sourcePage, 100, keyword, status);
            total = chunk.total();
            var start = sourcePage == firstSourcePage ? firstSourceOffset : 0;
            if (start >= chunk.items().size()) {
                break;
            }
            var remaining = requestedSize - items.size();
            var end = Math.min(chunk.items().size(), start + remaining);
            items.addAll(chunk.items().subList(start, end));
            if (chunk.items().size() < 100 || end < chunk.items().size()) {
                break;
            }
            sourcePage++;
        }
        return new PageResult<>(items, requestedPage, requestedSize, total);
    }
}
