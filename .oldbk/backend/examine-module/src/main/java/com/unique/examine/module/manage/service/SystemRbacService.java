package com.unique.examine.module.manage.service;

import java.util.List;

import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.module.manage.bo.DeptSaveBO;
import com.unique.examine.module.manage.bo.MemberInviteBO;
import com.unique.examine.module.manage.bo.MemberRoleAssignBO;
import com.unique.examine.module.manage.bo.MemberUpdateBO;
import com.unique.examine.module.manage.bo.RolePermissionSaveBO;
import com.unique.examine.module.manage.bo.RoleSaveBO;
import com.unique.examine.module.manage.bo.StatusChangeBO;
import com.unique.examine.module.manage.bo.SystemEnterBO;
import com.unique.examine.module.manage.bo.SystemProfileUpdateBO;
import com.unique.examine.module.manage.bo.TenantSaveBO;
import com.unique.examine.module.manage.bo.TenantSwitchBO;
import com.unique.examine.module.manage.vo.DeptTreeVO;
import com.unique.examine.module.manage.vo.MemberVO;
import com.unique.examine.module.manage.vo.PermissionCatalogVO;
import com.unique.examine.module.manage.vo.RolePermissionDetailVO;
import com.unique.examine.module.manage.vo.RoleVO;
import com.unique.examine.module.manage.vo.SystemContextVO;
import com.unique.examine.module.manage.vo.SystemMenuTreeVO;
import com.unique.examine.module.manage.vo.TenantVO;

/**
 * 系统成员、租户和 RBAC 服务。
 */
public interface SystemRbacService {

    /**
     * 进入系统并建立成员上下文。
     *
     * @param accountId 平台账号 ID
     * @param systemId 系统 ID
     * @param enterBO 进入系统入参
     * @return 系统上下文
     */
    SystemContextVO enterSystem(Long accountId, Long systemId, SystemEnterBO enterBO);

    /**
     * 查询系统上下文资料。
     *
     * @param systemId 系统 ID
     * @return 系统上下文
     */
    SystemContextVO profile(Long systemId);

    /**
     * 更新系统基础资料。
     *
     * @param systemId 系统 ID
     * @param updateBO 更新入参
     * @return 系统上下文
     */
    SystemContextVO updateProfile(Long systemId, SystemProfileUpdateBO updateBO);

    /**
     * 查询系统租户。
     *
     * @param systemId 系统 ID
     * @return 租户列表
     */
    List<TenantVO> listTenants(Long systemId);

    /**
     * 创建系统租户。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 租户
     */
    TenantVO createTenant(Long systemId, TenantSaveBO saveBO);

    /**
     * 变更租户状态。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param statusBO 状态入参
     * @return 租户
     */
    TenantVO changeTenantStatus(Long systemId, Long tenantId, StatusChangeBO statusBO);

    /**
     * 切换当前成员租户上下文。
     *
     * @param accountId 平台账号 ID
     * @param systemId 系统 ID
     * @param switchBO 切换入参
     * @return 系统上下文
     */
    SystemContextVO switchTenant(Long accountId, Long systemId, TenantSwitchBO switchBO);

    /**
     * 查询系统成员列表。
     *
     * @param systemId 系统 ID
     * @param keyword 关键字
     * @param status 成员状态
     * @return 成员列表
     */
    List<MemberVO> listMembers(Long systemId, String keyword, String status);

    /**
     * 邀请或绑定系统成员。
     *
     * @param systemId 系统 ID
     * @param inviteBO 邀请入参
     * @return 成员
     */
    MemberVO inviteMember(Long systemId, MemberInviteBO inviteBO);

    /**
     * 查询系统成员详情。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @return 成员
     */
    MemberVO getMember(Long systemId, Long memberId);

    /**
     * 更新系统成员扩展信息。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param updateBO 更新入参
     * @return 成员
     */
    MemberVO updateMember(Long systemId, Long memberId, MemberUpdateBO updateBO);

    /**
     * 变更成员状态。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param statusBO 状态入参
     * @return 成员
     */
    MemberVO changeMemberStatus(Long systemId, Long memberId, StatusChangeBO statusBO);

    /**
     * 分配成员角色。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param assignBO 分配入参
     * @return 成员
     */
    MemberVO assignMemberRoles(Long systemId, Long memberId, MemberRoleAssignBO assignBO);

    /**
     * 查询当前系统成员。
     *
     * @param accountId 平台账号 ID
     * @param systemId 系统 ID
     * @return 成员
     */
    MemberVO currentMember(Long accountId, Long systemId);

    /**
     * 查询部门树。
     *
     * @param systemId 系统 ID
     * @return 部门树
     */
    List<DeptTreeVO> departmentTree(Long systemId);

    /**
     * 创建部门。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 部门树节点
     */
    DeptTreeVO createDepartment(Long systemId, DeptSaveBO saveBO);

    /**
     * 更新部门。
     *
     * @param systemId 系统 ID
     * @param deptId 部门 ID
     * @param saveBO 保存入参
     * @return 部门树节点
     */
    DeptTreeVO updateDepartment(Long systemId, Long deptId, DeptSaveBO saveBO);

    /**
     * 删除部门。
     *
     * @param systemId 系统 ID
     * @param deptId 部门 ID
     */
    void deleteDepartment(Long systemId, Long deptId);

    /**
     * 查询系统角色。
     *
     * @param systemId 系统 ID
     * @return 角色列表
     */
    List<RoleVO> listRoles(Long systemId);

    /**
     * 创建系统角色。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 角色
     */
    RoleVO createRole(Long systemId, RoleSaveBO saveBO);

    /**
     * 更新系统角色。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param saveBO 保存入参
     * @return 角色
     */
    RoleVO updateRole(Long systemId, Long roleId, RoleSaveBO saveBO);

    /**
     * 变更系统角色状态。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param statusBO 状态入参
     * @return 角色
     */
    RoleVO changeRoleStatus(Long systemId, Long roleId, StatusChangeBO statusBO);

    /**
     * 保存角色权限。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param saveBO 权限入参
     * @return 角色权限详情
     */
    RolePermissionDetailVO saveRolePermissions(Long systemId, Long roleId, RolePermissionSaveBO saveBO);

    /**
     * 查询角色权限详情。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @return 角色权限详情
     */
    RolePermissionDetailVO rolePermissions(Long systemId, Long roleId);

    /**
     * 查询当前成员有效权限。
     *
     * @return 有效权限
     */
    EffectivePermissionVO effectivePermissions();

    /**
     * 查询运行菜单树。
     *
     * @param systemId 系统 ID
     * @return 运行菜单树
     */
    List<SystemMenuTreeVO> runtimeMenus(Long systemId);

    /**
     * 查询权限目录。
     *
     * @param systemId 系统 ID
     * @return 权限目录
     */
    PermissionCatalogVO permissionCatalog(Long systemId);
}
