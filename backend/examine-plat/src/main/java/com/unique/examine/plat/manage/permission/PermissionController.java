package com.unique.examine.plat.manage.permission;

import com.unique.examine.core.api.ApiResponse;
import com.unique.examine.plat.manage.permission.PermissionModels.EffectivePermissionSnapshot;
import com.unique.examine.plat.manage.permission.PermissionModels.PermissionDecisionVO;
import com.unique.examine.plat.manage.permission.PermissionModels.PermissionPreviewRequest;
import com.unique.examine.plat.manage.permission.PermissionModels.RolePermissionSaveRequest;
import com.unique.examine.plat.manage.permission.PermissionModels.RolePermissionVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Permission management API controller.
 */
@RestController
public class PermissionController {

    private final PermissionService permissionService;

    public PermissionController(PermissionService permissionService) {
        this.permissionService = permissionService;
    }

    @GetMapping("/api/v1/systems/{systemId}/roles/{roleId}/permissions")
    public ApiResponse<RolePermissionVO> getRolePermissions(@PathVariable String systemId,
                                                            @PathVariable String roleId) {
        return ApiResponse.success(permissionService.getRolePermissions(systemId, roleId));
    }

    @PutMapping("/api/v1/systems/{systemId}/roles/{roleId}/permissions")
    public ApiResponse<RolePermissionVO> saveRolePermissions(@PathVariable String systemId,
                                                             @PathVariable String roleId,
                                                             @RequestBody RolePermissionSaveRequest request) {
        return ApiResponse.success(permissionService.saveRolePermissions(systemId, roleId, request));
    }

    @GetMapping("/api/v1/systems/{systemId}/permissions/effective")
    public ApiResponse<EffectivePermissionSnapshot> effective(@PathVariable String systemId) {
        return ApiResponse.success(permissionService.effective(systemId));
    }

    @PostMapping("/api/v1/systems/{systemId}/permissions/effective/preview")
    public ApiResponse<PermissionDecisionVO> preview(@PathVariable String systemId,
                                                     @RequestBody PermissionPreviewRequest request) {
        return ApiResponse.success(permissionService.preview(systemId, request));
    }
}
