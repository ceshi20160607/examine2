package com.unique.examine.plat.manage.controller;

import com.unique.examine.core.common.ApiResult;
import com.unique.examine.plat.manage.bo.AccountRoleAssignBO;
import com.unique.examine.plat.manage.bo.PlatformAccountSaveBO;
import com.unique.examine.plat.manage.bo.PlatformPermissionSaveBO;
import com.unique.examine.plat.manage.bo.PlatformRoleSaveBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.bo.PlatformTenantSaveBO;
import com.unique.examine.plat.manage.bo.RolePermissionAssignBO;
import com.unique.examine.plat.manage.dto.PlatformLoginDTO;
import com.unique.examine.plat.manage.service.PlatformManageService;
import com.unique.examine.plat.manage.vo.PlatformManageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 平台中心业务接口。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/plat")
@Tag(name = "平台中心")
public class PlatformManageController {

    private final PlatformManageService platformManageService;

    /**
     * 登录。
     *
     * @param dto 登录 DTO
     * @return 账号信息
     */
    @PostMapping("/auth/login")
    @Operation(summary = "账号登录")
    public ApiResult<PlatformManageVO> login(@RequestBody PlatformLoginDTO dto) {
        return ApiResult.success(platformManageService.login(dto));
    }

    /**
     * 查询系统列表。
     *
     * @return 系统列表
     */
    @GetMapping("/systems")
    @Operation(summary = "查询系统列表")
    public ApiResult<List<PlatformManageVO>> listSystems() {
        return ApiResult.success(platformManageService.listSystems());
    }

    /**
     * 创建系统。
     *
     * @param bo 系统保存入参
     * @return 系统信息
     */
    @PostMapping("/systems")
    @Operation(summary = "创建系统")
    public ApiResult<PlatformManageVO> createSystem(@RequestBody PlatformSystemSaveBO bo) {
        return ApiResult.success(platformManageService.createSystem(bo));
    }

    /**
     * 变更系统状态。
     *
     * @param id 系统 ID
     * @param bo 状态入参
     * @return 系统信息
     */
    @PatchMapping("/systems/status")
    @Operation(summary = "变更系统状态")
    public ApiResult<PlatformManageVO> updateSystemStatus(@RequestParam Long id, @RequestBody PlatformStatusBO bo) {
        return ApiResult.success(platformManageService.updateSystemStatus(id, bo));
    }

    /**
     * 查询租户列表。
     *
     * @return 租户列表
     */
    @GetMapping("/tenants")
    @Operation(summary = "查询租户列表")
    public ApiResult<List<PlatformManageVO>> listTenants() {
        return ApiResult.success(platformManageService.listTenants());
    }

    /**
     * 创建租户。
     *
     * @param bo 租户保存入参
     * @return 租户信息
     */
    @PostMapping("/tenants")
    @Operation(summary = "创建租户")
    public ApiResult<PlatformManageVO> createTenant(@RequestBody PlatformTenantSaveBO bo) {
        return ApiResult.success(platformManageService.createTenant(bo));
    }

    /**
     * 变更租户状态。
     *
     * @param id 租户 ID
     * @param bo 状态入参
     * @return 租户信息
     */
    @PatchMapping("/tenants/status")
    @Operation(summary = "变更租户状态")
    public ApiResult<PlatformManageVO> updateTenantStatus(@RequestParam Long id, @RequestBody PlatformStatusBO bo) {
        return ApiResult.success(platformManageService.updateTenantStatus(id, bo));
    }

    /**
     * 查询账号列表。
     *
     * @return 账号列表
     */
    @GetMapping("/accounts")
    @Operation(summary = "查询账号列表")
    public ApiResult<List<PlatformManageVO>> listAccounts() {
        return ApiResult.success(platformManageService.listAccounts());
    }

    /**
     * 创建账号。
     *
     * @param bo 账号保存入参
     * @return 账号信息
     */
    @PostMapping("/accounts")
    @Operation(summary = "创建账号")
    public ApiResult<PlatformManageVO> createAccount(@RequestBody PlatformAccountSaveBO bo) {
        return ApiResult.success(platformManageService.createAccount(bo));
    }

    /**
     * 变更账号状态。
     *
     * @param id 账号 ID
     * @param bo 状态入参
     * @return 账号信息
     */
    @PatchMapping("/accounts/status")
    @Operation(summary = "变更账号状态")
    public ApiResult<PlatformManageVO> updateAccountStatus(@RequestParam Long id, @RequestBody PlatformStatusBO bo) {
        return ApiResult.success(platformManageService.updateAccountStatus(id, bo));
    }

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 角色列表
     */
    @GetMapping("/roles")
    @Operation(summary = "查询角色列表")
    public ApiResult<List<PlatformManageVO>> listRoles(@RequestParam(required = false) Long tenantId,
                                                       @RequestParam(required = false) Long systemId) {
        return ApiResult.success(platformManageService.listRoles(tenantId, systemId));
    }

    /**
     * 创建角色。
     *
     * @param bo 角色保存入参
     * @return 角色信息
     */
    @PostMapping("/roles")
    @Operation(summary = "创建角色")
    public ApiResult<PlatformManageVO> createRole(@RequestBody PlatformRoleSaveBO bo) {
        return ApiResult.success(platformManageService.createRole(bo));
    }

    /**
     * 查询权限点列表。
     *
     * @return 权限点列表
     */
    @GetMapping("/permissions")
    @Operation(summary = "查询权限点列表")
    public ApiResult<List<PlatformManageVO>> listPermissions() {
        return ApiResult.success(platformManageService.listPermissions());
    }

    /**
     * 创建权限点。
     *
     * @param bo 权限点保存入参
     * @return 权限点信息
     */
    @PostMapping("/permissions")
    @Operation(summary = "创建权限点")
    public ApiResult<PlatformManageVO> createPermission(@RequestBody PlatformPermissionSaveBO bo) {
        return ApiResult.success(platformManageService.createPermission(bo));
    }

    /**
     * 角色授权。
     *
     * @param bo 角色权限授权入参
     * @return 授权结果
     */
    @PostMapping("/roles/permissions")
    @Operation(summary = "角色授权")
    public ApiResult<PlatformManageVO> assignRolePermissions(@RequestBody RolePermissionAssignBO bo) {
        return ApiResult.success(platformManageService.assignRolePermissions(bo));
    }

    /**
     * 账号授权。
     *
     * @param bo 账号角色授权入参
     * @return 授权结果
     */
    @PostMapping("/accounts/roles")
    @Operation(summary = "账号授权")
    public ApiResult<PlatformManageVO> assignAccountRoles(@RequestBody AccountRoleAssignBO bo) {
        return ApiResult.success(platformManageService.assignAccountRoles(bo));
    }
}
