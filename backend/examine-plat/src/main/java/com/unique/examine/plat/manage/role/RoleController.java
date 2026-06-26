package com.unique.examine.plat.manage.role;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.core.api.PageRequest;
import com.unique.examine.core.api.PageResult;
import com.unique.examine.plat.manage.role.RoleModels.RoleMemberAssignRequest;
import com.unique.examine.plat.manage.role.RoleModels.RoleMemberAssignResult;
import com.unique.examine.plat.manage.role.RoleModels.RoleQueryRequest;
import com.unique.examine.plat.manage.role.RoleModels.RoleSaveRequest;
import com.unique.examine.plat.manage.role.RoleModels.RoleVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Role management API controller.
 */
@RestController
public class RoleController {

    private final RoleService roleService;

    public RoleController(RoleService roleService) {
        this.roleService = roleService;
    }

    @GetMapping("/api/v1/platform/roles")
    public ApiResponse<PageResult<RoleVO>> platformRoles(@RequestParam(defaultValue = "1") int pageNo,
                                                         @RequestParam(defaultValue = "20") int pageSize,
                                                         RoleQueryRequest query) {
        return ApiResponse.success(roleService.platformRoles(new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/platform/roles")
    public ApiResponse<RoleVO> createPlatformRole(@RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleService.createPlatformRole(request));
    }

    @PatchMapping("/api/v1/platform/roles/{roleId}")
    public ApiResponse<RoleVO> updatePlatformRole(@PathVariable String roleId, @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleService.updatePlatformRole(roleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/roles")
    public ApiResponse<PageResult<RoleVO>> systemRoles(@PathVariable String systemId,
                                                       @RequestParam(defaultValue = "1") int pageNo,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       RoleQueryRequest query) {
        return ApiResponse.success(roleService.systemRoles(systemId, new PageRequest(pageNo, pageSize, null,
                List.of(), List.of()), query));
    }

    @PostMapping("/api/v1/systems/{systemId}/roles")
    public ApiResponse<RoleVO> createSystemRole(@PathVariable String systemId, @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleService.createSystemRole(systemId, request));
    }

    @PatchMapping("/api/v1/systems/{systemId}/roles/{roleId}")
    public ApiResponse<RoleVO> updateSystemRole(@PathVariable String systemId, @PathVariable String roleId,
                                                @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(roleService.updateSystemRole(systemId, roleId, request));
    }

    @PostMapping("/api/v1/systems/{systemId}/roles/{roleId}/assign-members")
    public ApiResponse<RoleMemberAssignResult> assignMembers(@PathVariable String systemId, @PathVariable String roleId,
                                                             @RequestBody RoleMemberAssignRequest request) {
        return ApiResponse.success(roleService.assignMembers(systemId, roleId, request));
    }
}
