package com.unique.examine.plat.manage.service;

import com.unique.examine.plat.manage.bo.AccountRoleAssignBO;
import com.unique.examine.plat.manage.bo.PlatformAccountSaveBO;
import com.unique.examine.plat.manage.bo.PlatformPermissionSaveBO;
import com.unique.examine.plat.manage.bo.PlatformRoleSaveBO;
import com.unique.examine.plat.manage.bo.PlatformStatusBO;
import com.unique.examine.plat.manage.bo.PlatformSystemSaveBO;
import com.unique.examine.plat.manage.bo.PlatformTenantSaveBO;
import com.unique.examine.plat.manage.bo.RolePermissionAssignBO;
import com.unique.examine.plat.manage.dto.PlatformLoginDTO;
import com.unique.examine.plat.manage.vo.PlatformManageVO;

import java.util.List;

/**
 * 平台管理业务服务。
 */
public interface PlatformManageService {

    /**
     * 查询系统列表。
     *
     * @return 系统列表
     */
    List<PlatformManageVO> listSystems();

    /**
     * 创建系统。
     *
     * @param bo 系统保存入参
     * @return 系统信息
     */
    PlatformManageVO createSystem(PlatformSystemSaveBO bo);

    /**
     * 变更系统状态。
     *
     * @param id 系统 ID
     * @param bo 状态入参
     * @return 系统信息
     */
    PlatformManageVO updateSystemStatus(Long id, PlatformStatusBO bo);

    /**
     * 查询租户列表。
     *
     * @return 租户列表
     */
    List<PlatformManageVO> listTenants();

    /**
     * 创建租户。
     *
     * @param bo 租户保存入参
     * @return 租户信息
     */
    PlatformManageVO createTenant(PlatformTenantSaveBO bo);

    /**
     * 变更租户状态。
     *
     * @param id 租户 ID
     * @param bo 状态入参
     * @return 租户信息
     */
    PlatformManageVO updateTenantStatus(Long id, PlatformStatusBO bo);

    /**
     * 查询账号列表。
     *
     * @return 账号列表
     */
    List<PlatformManageVO> listAccounts();

    /**
     * 创建账号。
     *
     * @param bo 账号保存入参
     * @return 账号信息
     */
    PlatformManageVO createAccount(PlatformAccountSaveBO bo);

    /**
     * 变更账号状态。
     *
     * @param id 账号 ID
     * @param bo 状态入参
     * @return 账号信息
     */
    PlatformManageVO updateAccountStatus(Long id, PlatformStatusBO bo);

    /**
     * 查询角色列表。
     *
     * @param tenantId 租户 ID
     * @param systemId 系统 ID
     * @return 角色列表
     */
    List<PlatformManageVO> listRoles(Long tenantId, Long systemId);

    /**
     * 创建角色。
     *
     * @param bo 角色保存入参
     * @return 角色信息
     */
    PlatformManageVO createRole(PlatformRoleSaveBO bo);

    /**
     * 查询权限点列表。
     *
     * @return 权限点列表
     */
    List<PlatformManageVO> listPermissions();

    /**
     * 创建权限点。
     *
     * @param bo 权限点保存入参
     * @return 权限点信息
     */
    PlatformManageVO createPermission(PlatformPermissionSaveBO bo);

    /**
     * 替换角色权限关系。
     *
     * @param bo 授权入参
     * @return 角色与权限关系
     */
    PlatformManageVO assignRolePermissions(RolePermissionAssignBO bo);

    /**
     * 替换账号角色关系。
     *
     * @param bo 授权入参
     * @return 账号与角色关系
     */
    PlatformManageVO assignAccountRoles(AccountRoleAssignBO bo);

    /**
     * 账号登录。
     *
     * @param dto 登录入参
     * @return 账号信息
     */
    PlatformManageVO login(PlatformLoginDTO dto);
}
