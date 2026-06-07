package com.unique.examine.module.manage.controller;

import java.util.List;
import java.util.Map;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.module.manage.bo.DeptSaveBO;
import com.unique.examine.module.manage.bo.RolePermissionSaveBO;
import com.unique.examine.module.manage.bo.RoleSaveBO;
import com.unique.examine.module.manage.bo.StatusChangeBO;
import com.unique.examine.module.manage.service.SystemRbacService;
import com.unique.examine.module.manage.vo.DeptTreeVO;
import com.unique.examine.module.manage.vo.PermissionCatalogVO;
import com.unique.examine.module.manage.vo.RolePermissionDetailVO;
import com.unique.examine.module.manage.vo.RoleVO;
import com.unique.examine.module.manage.vo.SystemMenuTreeVO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统 RBAC 接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/systems/{systemId}/rbac")
public class SystemRbacController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final SystemRbacService systemRbacService;

    private final AuthSessionService authSessionService;

    /**
     * 查询部门树。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 部门树
     */
    @Operation(summary = "查询部门树")
    @GetMapping("/departments/tree")
    public List<DeptTreeVO> departmentTree(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.departmentTree(systemId);
    }

    /**
     * 创建部门。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 部门
     */
    @Operation(summary = "创建部门")
    @PostMapping("/departments")
    public DeptTreeVO createDepartment(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody DeptSaveBO saveBO) {
        validateLogin(authorization);
        return systemRbacService.createDepartment(systemId, saveBO);
    }

    /**
     * 更新部门。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param deptId 部门 ID
     * @param saveBO 保存入参
     * @return 部门
     */
    @Operation(summary = "更新部门")
    @PutMapping("/departments/{deptId}")
    public DeptTreeVO updateDepartment(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long deptId, @Valid @RequestBody DeptSaveBO saveBO) {
        validateLogin(authorization);
        return systemRbacService.updateDepartment(systemId, deptId, saveBO);
    }

    /**
     * 删除部门。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param deptId 部门 ID
     * @return 删除结果
     */
    @Operation(summary = "删除部门")
    @DeleteMapping("/departments/{deptId}")
    public Map<String, Boolean> deleteDepartment(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long deptId) {
        validateLogin(authorization);
        systemRbacService.deleteDepartment(systemId, deptId);
        return Map.of("deleted", true);
    }

    /**
     * 查询角色列表。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 角色列表
     */
    @Operation(summary = "查询角色列表")
    @GetMapping("/roles")
    public List<RoleVO> listRoles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.listRoles(systemId);
    }

    /**
     * 创建角色。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 角色
     */
    @Operation(summary = "创建角色")
    @PostMapping("/roles")
    public RoleVO createRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @Valid @RequestBody RoleSaveBO saveBO) {
        validateLogin(authorization);
        return systemRbacService.createRole(systemId, saveBO);
    }

    /**
     * 更新角色。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param saveBO 保存入参
     * @return 角色
     */
    @Operation(summary = "更新角色")
    @PutMapping("/roles/{roleId}")
    public RoleVO updateRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long roleId, @Valid @RequestBody RoleSaveBO saveBO) {
        validateLogin(authorization);
        return systemRbacService.updateRole(systemId, roleId, saveBO);
    }

    /**
     * 变更角色状态。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param statusBO 状态入参
     * @return 角色
     */
    @Operation(summary = "变更角色状态")
    @PatchMapping("/roles/{roleId}/status")
    public RoleVO changeRoleStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long roleId, @Valid @RequestBody StatusChangeBO statusBO) {
        validateLogin(authorization);
        return systemRbacService.changeRoleStatus(systemId, roleId, statusBO);
    }

    /**
     * 保存角色权限。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param saveBO 权限入参
     * @return 角色权限详情
     */
    @Operation(summary = "保存角色权限")
    @PutMapping("/roles/{roleId}/permissions")
    public RolePermissionDetailVO saveRolePermissions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long roleId,
            @Valid @RequestBody RolePermissionSaveBO saveBO) {
        validateLogin(authorization);
        return systemRbacService.saveRolePermissions(systemId, roleId, saveBO);
    }

    /**
     * 查询角色权限。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @return 角色权限详情
     */
    @Operation(summary = "查询角色权限")
    @GetMapping("/roles/{roleId}/permissions")
    public RolePermissionDetailVO rolePermissions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId, @PathVariable Long roleId) {
        validateLogin(authorization);
        return systemRbacService.rolePermissions(systemId, roleId);
    }

    /**
     * 查询有效权限。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 有效权限
     */
    @Operation(summary = "查询有效权限")
    @GetMapping("/effective-permissions")
    public EffectivePermissionVO effectivePermissions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.effectivePermissions();
    }

    /**
     * 查询运行菜单。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 运行菜单
     */
    @Operation(summary = "查询运行菜单")
    @GetMapping("/runtime-menus")
    public List<SystemMenuTreeVO> runtimeMenus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.runtimeMenus(systemId);
    }

    /**
     * 查询权限目录。
     *
     * @param authorization Authorization 请求头
     * @param systemId 系统 ID
     * @return 权限目录
     */
    @Operation(summary = "查询权限目录")
    @GetMapping("/permission-catalog")
    public PermissionCatalogVO permissionCatalog(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return systemRbacService.permissionCatalog(systemId);
    }

    private void validateLogin(String authorization) {
        authSessionService.me(resolveBearer(authorization));
    }

    private static String resolveBearer(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        String token = authorization.substring(BEARER_PREFIX.length()).strip();
        if (!StringUtils.hasText(token)) {
            throw new BusinessException(CommonErrorCode.UNAUTHORIZED);
        }
        return token;
    }
}
