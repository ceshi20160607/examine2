package com.unique.examine.plat.manage.controller;

import java.util.List;
import java.util.Map;

import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.plat.manage.bo.PlatformAccountResetPasswordBO;
import com.unique.examine.plat.manage.bo.PlatformAccountRoleAssignBO;
import com.unique.examine.plat.manage.bo.PlatformAccountSaveBO;
import com.unique.examine.plat.manage.bo.PlatformAccountUpdateBO;
import com.unique.examine.plat.manage.bo.PlatformConfigUpdateBO;
import com.unique.examine.plat.manage.bo.PlatformRolePermissionBO;
import com.unique.examine.plat.manage.bo.PlatformRoleSaveBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.service.AuthSessionService;
import com.unique.examine.plat.manage.service.PlatformCenterService;
import com.unique.examine.plat.manage.vo.CurrentUserVO;
import com.unique.examine.plat.manage.vo.PlatformAccountVO;
import com.unique.examine.plat.manage.vo.PlatformConfigVO;
import com.unique.examine.plat.manage.vo.PlatformPermissionCatalogVO;
import com.unique.examine.plat.manage.vo.PlatformRoleVO;
import com.unique.examine.plat.manage.vo.PlatformSystemVO;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
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
 * 平台中心接口。
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/platform")
public class PlatformController {

    private static final String BEARER_PREFIX = "Bearer ";

    private final PlatformCenterService platformCenterService;

    private final AuthSessionService authSessionService;

    /**
     * 查询我的系统。
     *
     * @param authorization Authorization 请求头
     * @return 系统列表
     */
    @Operation(summary = "查询我的系统")
    @GetMapping("/my-systems")
    public List<PlatformSystemVO> mySystems(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return platformCenterService.mySystems(currentAccountId(authorization));
    }

    /**
     * 创建自定义系统。
     *
     * @param authorization Authorization 请求头
     * @param saveBO 创建入参
     * @return 系统信息
     */
    @Operation(summary = "创建自定义系统")
    @PostMapping("/systems")
    public PlatformSystemVO createSystem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody PlatformSystemSaveBO saveBO) {
        return platformCenterService.createSystem(currentAccountId(authorization), saveBO);
    }

    /**
     * 查询平台系统列表。
     *
     * @return 系统列表
     */
    @Operation(summary = "查询平台系统列表")
    @GetMapping("/systems")
    public List<PlatformSystemVO> listSystems(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return platformCenterService.listSystems();
    }

    /**
     * 查询平台系统详情。
     *
     * @param systemId 系统 ID
     * @return 系统详情
     */
    @Operation(summary = "查询平台系统详情")
    @GetMapping("/systems/{systemId}")
    public PlatformSystemVO getSystem(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId) {
        validateLogin(authorization);
        return platformCenterService.getSystem(systemId);
    }

    /**
     * 变更平台系统状态。
     *
     * @param systemId 系统 ID
     * @param statusBO 状态入参
     * @return 系统详情
     */
    @Operation(summary = "变更平台系统状态")
    @PatchMapping("/systems/{systemId}/status")
    public PlatformSystemVO changeSystemStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long systemId,
            @Valid @RequestBody PlatformStatusBO statusBO) {
        validateLogin(authorization);
        return platformCenterService.changeSystemStatus(systemId, statusBO);
    }

    /**
     * 查询平台账号列表。
     *
     * @return 平台账号列表
     */
    @Operation(summary = "查询平台账号列表")
    @GetMapping("/accounts")
    public List<PlatformAccountVO> listAccounts(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return platformCenterService.listAccounts();
    }

    /**
     * 创建平台账号。
     *
     * @param saveBO 创建入参
     * @return 平台账号
     */
    @Operation(summary = "创建平台账号")
    @PostMapping("/accounts")
    public PlatformAccountVO createAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody PlatformAccountSaveBO saveBO) {
        validateLogin(authorization);
        return platformCenterService.createAccount(saveBO);
    }

    /**
     * 查询平台账号详情。
     *
     * @param accountId 账号 ID
     * @return 平台账号
     */
    @Operation(summary = "查询平台账号详情")
    @GetMapping("/accounts/{accountId}")
    public PlatformAccountVO getAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long accountId) {
        validateLogin(authorization);
        return platformCenterService.getAccount(accountId);
    }

    /**
     * 更新平台账号。
     *
     * @param accountId 账号 ID
     * @param updateBO 更新入参
     * @return 平台账号
     */
    @Operation(summary = "更新平台账号")
    @PutMapping("/accounts/{accountId}")
    public PlatformAccountVO updateAccount(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long accountId,
            @Valid @RequestBody PlatformAccountUpdateBO updateBO) {
        validateLogin(authorization);
        return platformCenterService.updateAccount(accountId, updateBO);
    }

    /**
     * 变更平台账号状态。
     *
     * @param accountId 账号 ID
     * @param statusBO 状态入参
     * @return 平台账号
     */
    @Operation(summary = "变更平台账号状态")
    @PatchMapping("/accounts/{accountId}/status")
    public PlatformAccountVO changeAccountStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long accountId,
            @Valid @RequestBody PlatformStatusBO statusBO) {
        validateLogin(authorization);
        return platformCenterService.changeAccountStatus(accountId, statusBO);
    }

    /**
     * 重置平台账号密码。
     *
     * @param accountId 账号 ID
     * @param resetPasswordBO 重置入参
     * @return 操作结果
     */
    @Operation(summary = "重置平台账号密码")
    @PostMapping("/accounts/{accountId}/password/reset")
    public Map<String, Boolean> resetPassword(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long accountId,
            @Valid @RequestBody PlatformAccountResetPasswordBO resetPasswordBO) {
        validateLogin(authorization);
        platformCenterService.resetPassword(accountId, resetPasswordBO);
        return Map.of("reset", true);
    }

    /**
     * 分配平台账号角色。
     *
     * @param accountId 账号 ID
     * @param assignBO 分配入参
     * @return 平台账号
     */
    @Operation(summary = "分配平台账号角色")
    @PutMapping("/accounts/{accountId}/roles")
    public PlatformAccountVO assignAccountRoles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long accountId,
            @Valid @RequestBody PlatformAccountRoleAssignBO assignBO) {
        validateLogin(authorization);
        return platformCenterService.assignAccountRoles(accountId, assignBO);
    }

    /**
     * 查询平台角色列表。
     *
     * @return 平台角色列表
     */
    @Operation(summary = "查询平台角色列表")
    @GetMapping("/roles")
    public List<PlatformRoleVO> listRoles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return platformCenterService.listRoles();
    }

    /**
     * 创建平台角色。
     *
     * @param saveBO 保存入参
     * @return 平台角色
     */
    @Operation(summary = "创建平台角色")
    @PostMapping("/roles")
    public PlatformRoleVO createRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Valid @RequestBody PlatformRoleSaveBO saveBO) {
        validateLogin(authorization);
        return platformCenterService.createRole(saveBO);
    }

    /**
     * 更新平台角色。
     *
     * @param roleId 角色 ID
     * @param saveBO 保存入参
     * @return 平台角色
     */
    @Operation(summary = "更新平台角色")
    @PutMapping("/roles/{roleId}")
    public PlatformRoleVO updateRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long roleId, @Valid @RequestBody PlatformRoleSaveBO saveBO) {
        validateLogin(authorization);
        return platformCenterService.updateRole(roleId, saveBO);
    }

    /**
     * 变更平台角色状态。
     *
     * @param roleId 角色 ID
     * @param statusBO 状态入参
     * @return 平台角色
     */
    @Operation(summary = "变更平台角色状态")
    @PatchMapping("/roles/{roleId}/status")
    public PlatformRoleVO changeRoleStatus(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long roleId,
            @Valid @RequestBody PlatformStatusBO statusBO) {
        validateLogin(authorization);
        return platformCenterService.changeRoleStatus(roleId, statusBO);
    }

    /**
     * 保存平台角色菜单和操作权限。
     *
     * @param roleId 角色 ID
     * @param permissionBO 权限入参
     * @return 平台角色
     */
    @Operation(summary = "保存平台角色菜单和操作权限")
    @PutMapping("/roles/{roleId}/menus")
    public PlatformRoleVO saveRolePermissions(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable Long roleId,
            @Valid @RequestBody PlatformRolePermissionBO permissionBO) {
        validateLogin(authorization);
        return platformCenterService.saveRolePermissions(roleId, permissionBO);
    }

    /**
     * 查询平台配置列表。
     *
     * @return 平台配置列表
     */
    @Operation(summary = "查询平台配置列表")
    @GetMapping("/configs")
    public List<PlatformConfigVO> listConfigs(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return platformCenterService.listConfigs();
    }

    /**
     * 更新平台配置。
     *
     * @param configKey 配置 key
     * @param updateBO 更新入参
     * @return 平台配置
     */
    @Operation(summary = "更新平台配置")
    @PutMapping("/configs/{configKey}")
    public PlatformConfigVO updateConfig(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @PathVariable String configKey,
            @Valid @RequestBody PlatformConfigUpdateBO updateBO) {
        validateLogin(authorization);
        return platformCenterService.updateConfig(configKey, updateBO);
    }

    /**
     * 查询平台权限目录。
     *
     * @return 平台权限目录
     */
    @Operation(summary = "查询平台权限目录")
    @GetMapping("/permission-catalog")
    public PlatformPermissionCatalogVO permissionCatalog(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        validateLogin(authorization);
        return platformCenterService.permissionCatalog();
    }

    private void validateLogin(String authorization) {
        currentAccountId(authorization);
    }

    private Long currentAccountId(String authorization) {
        CurrentUserVO currentUser = authSessionService.me(resolveBearer(authorization));
        return Long.valueOf(currentUser.getAccount().getAccountId());
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
