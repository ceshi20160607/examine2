package com.unique.unexamine.platform.manage.organization;

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

/** 平台组织的页面接口，平台角色和系统租户成员由各自后台管理。 */
@RestController
@RequestMapping("/api/admin/platform/organization")
@RequirePermission(resourceType = "PLATFORM", resourceCode = "ORGANIZATION", actionCode = "VIEW")
public class PlatformOrganizationController {
    private final PlatformOrganizationService organization;

    public PlatformOrganizationController(PlatformOrganizationService organization) {
        this.organization = organization;
    }

    /** 读取当前平台部门和成员，不返回账号敏感资料。 */
    @GetMapping
    public ApiResult<PlatformOrganizationService.Overview> overview() {
        return ApiResult.ok(organization.overview(AuthenticationContextHolder.require()));
    }

    /** 建立部门时由服务端生成编码与路径。 */
    @PostMapping("/departments")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "ORGANIZATION", actionCode = "MANAGE")
    public ApiResult<PlatformOrganizationService.DepartmentView> createDepartment(
            @Valid @RequestBody PlatformOrganizationService.DepartmentInput input, HttpServletRequest request) {
        return ApiResult.ok(organization.createDepartment(AuthenticationContextHolder.require(), input,
                TraceIdFilter.current(request)));
    }

    /** 更名使用乐观锁，避免覆盖其他管理员的调整。 */
    @PutMapping("/departments/{id}")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "ORGANIZATION", actionCode = "MANAGE")
    public ApiResult<PlatformOrganizationService.DepartmentView> renameDepartment(
            @PathVariable Long id, @Valid @RequestBody PlatformOrganizationService.RenameDepartmentInput input,
            HttpServletRequest request) {
        return ApiResult.ok(organization.renameDepartment(AuthenticationContextHolder.require(), id, input,
                TraceIdFilter.current(request)));
    }

    /** 调整当前平台成员归属，不变更其平台角色或系统成员资格。 */
    @PutMapping("/members/{id}/assignment")
    @RequirePermission(resourceType = "PLATFORM", resourceCode = "ORGANIZATION", actionCode = "MANAGE")
    public ApiResult<PlatformOrganizationService.MemberView> assignMember(
            @PathVariable Long id, @Valid @RequestBody PlatformOrganizationService.MemberAssignment input,
            HttpServletRequest request) {
        return ApiResult.ok(organization.assignMember(AuthenticationContextHolder.require(), id, input,
                TraceIdFilter.current(request)));
    }
}
