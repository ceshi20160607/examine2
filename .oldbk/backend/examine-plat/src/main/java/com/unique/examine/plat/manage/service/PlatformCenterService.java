package com.unique.examine.plat.manage.service;

import java.util.List;

import com.unique.examine.plat.manage.bo.PlatformAccountResetPasswordBO;
import com.unique.examine.plat.manage.bo.PlatformAccountRoleAssignBO;
import com.unique.examine.plat.manage.bo.PlatformAccountSaveBO;
import com.unique.examine.plat.manage.bo.PlatformAccountUpdateBO;
import com.unique.examine.plat.manage.bo.PlatformConfigUpdateBO;
import com.unique.examine.plat.manage.bo.PlatformRolePermissionBO;
import com.unique.examine.plat.manage.bo.PlatformRoleSaveBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.vo.PlatformAccountVO;
import com.unique.examine.plat.manage.vo.PlatformConfigVO;
import com.unique.examine.plat.manage.vo.PlatformPermissionCatalogVO;
import com.unique.examine.plat.manage.vo.PlatformRoleVO;
import com.unique.examine.plat.manage.vo.PlatformSystemVO;

/**
 * 平台中心服务。
 */
public interface PlatformCenterService {

    /**
     * 查询当前账号的系统列表。
     *
     * @param accountId 平台账号 ID
     * @return 系统列表
     */
    List<PlatformSystemVO> mySystems(Long accountId);

    /**
     * 创建自定义系统。
     *
     * @param accountId 创建人平台账号 ID
     * @param saveBO 创建入参
     * @return 系统信息
     */
    PlatformSystemVO createSystem(Long accountId, PlatformSystemSaveBO saveBO);

    /**
     * 查询平台系统列表。
     *
     * @return 系统列表
     */
    List<PlatformSystemVO> listSystems();

    /**
     * 查询平台系统详情。
     *
     * @param systemId 系统 ID
     * @return 系统详情
     */
    PlatformSystemVO getSystem(Long systemId);

    /**
     * 变更系统状态。
     *
     * @param systemId 系统 ID
     * @param statusBO 状态入参
     * @return 系统详情
     */
    PlatformSystemVO changeSystemStatus(Long systemId, PlatformStatusBO statusBO);

    /**
     * 查询平台账号列表。
     *
     * @return 平台账号列表
     */
    List<PlatformAccountVO> listAccounts();

    /**
     * 创建平台账号。
     *
     * @param saveBO 创建入参
     * @return 平台账号
     */
    PlatformAccountVO createAccount(PlatformAccountSaveBO saveBO);

    /**
     * 查询平台账号详情。
     *
     * @param accountId 平台账号 ID
     * @return 平台账号
     */
    PlatformAccountVO getAccount(Long accountId);

    /**
     * 更新平台账号。
     *
     * @param accountId 平台账号 ID
     * @param updateBO 更新入参
     * @return 平台账号
     */
    PlatformAccountVO updateAccount(Long accountId, PlatformAccountUpdateBO updateBO);

    /**
     * 变更账号状态。
     *
     * @param accountId 平台账号 ID
     * @param statusBO 状态入参
     * @return 平台账号
     */
    PlatformAccountVO changeAccountStatus(Long accountId, PlatformStatusBO statusBO);

    /**
     * 重置账号密码。
     *
     * @param accountId 平台账号 ID
     * @param resetPasswordBO 重置入参
     */
    void resetPassword(Long accountId, PlatformAccountResetPasswordBO resetPasswordBO);

    /**
     * 分配平台账号角色。
     *
     * @param accountId 平台账号 ID
     * @param assignBO 分配入参
     * @return 平台账号
     */
    PlatformAccountVO assignAccountRoles(Long accountId, PlatformAccountRoleAssignBO assignBO);

    /**
     * 查询平台角色列表。
     *
     * @return 平台角色列表
     */
    List<PlatformRoleVO> listRoles();

    /**
     * 创建平台角色。
     *
     * @param saveBO 保存入参
     * @return 平台角色
     */
    PlatformRoleVO createRole(PlatformRoleSaveBO saveBO);

    /**
     * 更新平台角色。
     *
     * @param roleId 角色 ID
     * @param saveBO 保存入参
     * @return 平台角色
     */
    PlatformRoleVO updateRole(Long roleId, PlatformRoleSaveBO saveBO);

    /**
     * 变更平台角色状态。
     *
     * @param roleId 角色 ID
     * @param statusBO 状态入参
     * @return 平台角色
     */
    PlatformRoleVO changeRoleStatus(Long roleId, PlatformStatusBO statusBO);

    /**
     * 保存平台角色权限。
     *
     * @param roleId 角色 ID
     * @param permissionBO 权限入参
     * @return 平台角色
     */
    PlatformRoleVO saveRolePermissions(Long roleId, PlatformRolePermissionBO permissionBO);

    /**
     * 查询平台配置列表。
     *
     * @return 配置列表
     */
    List<PlatformConfigVO> listConfigs();

    /**
     * 更新平台配置。
     *
     * @param configKey 配置 key
     * @param updateBO 更新入参
     * @return 配置
     */
    PlatformConfigVO updateConfig(String configKey, PlatformConfigUpdateBO updateBO);

    /**
     * 查询平台权限目录。
     *
     * @return 权限目录
     */
    PlatformPermissionCatalogVO permissionCatalog();
}
