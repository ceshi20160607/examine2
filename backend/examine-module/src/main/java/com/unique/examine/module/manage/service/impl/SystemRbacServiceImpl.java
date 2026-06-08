package com.unique.examine.module.manage.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.context.RequestContextHolder;
import com.unique.examine.core.exception.BusinessException;
import com.unique.examine.core.permission.DataScopeRuleVO;
import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.core.permission.FieldPermissionVO;
import com.unique.examine.core.permission.PermissionService;
import com.unique.examine.module.base.entity.Dept;
import com.unique.examine.module.base.entity.Field;
import com.unique.examine.module.base.entity.Member;
import com.unique.examine.module.base.entity.MemberDept;
import com.unique.examine.module.base.entity.MemberRole;
import com.unique.examine.module.base.entity.MemberTenant;
import com.unique.examine.module.base.entity.PermissionVersion;
import com.unique.examine.module.base.entity.Role;
import com.unique.examine.module.base.entity.RoleDataScope;
import com.unique.examine.module.base.entity.RoleFieldPermission;
import com.unique.examine.module.base.entity.RoleMenu;
import com.unique.examine.module.base.entity.RoleOpenapiScope;
import com.unique.examine.module.base.entity.RoleOperation;
import com.unique.examine.module.base.entity.SystemMenu;
import com.unique.examine.module.base.entity.SystemOperation;
import com.unique.examine.module.base.service.IDeptService;
import com.unique.examine.module.base.service.IFieldService;
import com.unique.examine.module.base.service.IMemberDeptService;
import com.unique.examine.module.base.service.IMemberRoleService;
import com.unique.examine.module.base.service.IMemberService;
import com.unique.examine.module.base.service.IMemberTenantService;
import com.unique.examine.module.base.service.IPermissionVersionService;
import com.unique.examine.module.base.service.IRoleDataScopeService;
import com.unique.examine.module.base.service.IRoleFieldPermissionService;
import com.unique.examine.module.base.service.IRoleMenuService;
import com.unique.examine.module.base.service.IRoleOpenapiScopeService;
import com.unique.examine.module.base.service.IRoleOperationService;
import com.unique.examine.module.base.service.IRoleService;
import com.unique.examine.module.base.service.ISystemMenuService;
import com.unique.examine.module.base.service.ISystemOperationService;
import com.unique.examine.module.manage.bo.DataScopeRuleBO;
import com.unique.examine.module.manage.bo.DeptSaveBO;
import com.unique.examine.module.manage.bo.MemberInviteBO;
import com.unique.examine.module.manage.bo.MemberRoleAssignBO;
import com.unique.examine.module.manage.bo.MemberUpdateBO;
import com.unique.examine.module.manage.bo.OpenapiScopeBO;
import com.unique.examine.module.manage.bo.RoleFieldPermissionBO;
import com.unique.examine.module.manage.bo.RolePermissionSaveBO;
import com.unique.examine.module.manage.bo.RoleSaveBO;
import com.unique.examine.module.manage.bo.StatusChangeBO;
import com.unique.examine.module.manage.bo.SystemEnterBO;
import com.unique.examine.module.manage.bo.SystemProfileUpdateBO;
import com.unique.examine.module.manage.bo.TenantSaveBO;
import com.unique.examine.module.manage.bo.TenantSwitchBO;
import com.unique.examine.module.manage.enums.SystemManageErrorCode;
import com.unique.examine.module.manage.service.SystemRbacService;
import com.unique.examine.module.manage.vo.DeptTreeVO;
import com.unique.examine.module.manage.vo.MemberVO;
import com.unique.examine.module.manage.vo.PermissionCatalogVO;
import com.unique.examine.module.manage.vo.RolePermissionDetailVO;
import com.unique.examine.module.manage.vo.RoleVO;
import com.unique.examine.module.manage.vo.SystemContextVO;
import com.unique.examine.module.manage.vo.SystemMenuTreeVO;
import com.unique.examine.module.manage.vo.SystemOperationVO;
import com.unique.examine.module.manage.vo.TenantVO;
import com.unique.examine.plat.base.entity.Account;
import com.unique.examine.plat.base.entity.System;
import com.unique.examine.plat.base.entity.Tenant;
import com.unique.examine.plat.base.service.IAccountService;
import com.unique.examine.plat.base.service.ISystemService;
import com.unique.examine.plat.base.service.ITenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * 系统成员、租户和 RBAC 服务实现。
 */
@Service
@RequiredArgsConstructor
public class SystemRbacServiceImpl implements SystemRbacService {

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private static final long SYSTEM_LEVEL_TENANT_ID = 0L;

    private static final long ROOT_ID = 0L;

    private static final String ENABLED = "ENABLED";

    private static final String DISABLED = "DISABLED";

    private final IAccountService accountService;

    private final ISystemService systemService;

    private final ITenantService tenantService;

    private final IMemberService memberService;

    private final IMemberTenantService memberTenantService;

    private final IMemberDeptService memberDeptService;

    private final IMemberRoleService memberRoleService;

    private final IDeptService deptService;

    private final IRoleService roleService;

    private final ISystemMenuService systemMenuService;

    private final ISystemOperationService systemOperationService;

    private final IRoleMenuService roleMenuService;

    private final IRoleOperationService roleOperationService;

    private final IRoleFieldPermissionService roleFieldPermissionService;

    private final IRoleDataScopeService roleDataScopeService;

    private final IRoleOpenapiScopeService roleOpenapiScopeService;

    private final IPermissionVersionService permissionVersionService;

    private final IFieldService fieldService;

    private final PermissionService permissionService;

    /**
     * 进入系统并建立成员上下文。
     *
     * @param accountId 平台账号 ID
     * @param systemId 系统 ID
     * @param enterBO 进入系统入参
     * @return 系统上下文
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemContextVO enterSystem(Long accountId, Long systemId, SystemEnterBO enterBO) {
        System system = activeSystem(systemId);
        Member member = enabledMemberByAccount(systemId, accountId);
        Long tenantId = resolveTenantForMember(member, parseLong(valueOrNull(enterBO, SystemEnterBO::getTenantId)));
        member.setLastEnterAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        memberService.updateById(member);
        bindRequestContext(accountId, systemId, member.getId(), tenantId);
        return toSystemContext(system, member, tenantId);
    }

    /**
     * 查询系统上下文资料。
     *
     * @param systemId 系统 ID
     * @return 系统上下文
     */
    @Override
    public SystemContextVO profile(Long systemId) {
        permissionService.requireOperation("SYS_PROFILE_VIEW");
        System system = activeSystem(systemId);
        return toSystemContext(system, currentMemberOrNull(systemId), currentTenantId());
    }

    /**
     * 更新系统基础资料。
     *
     * @param systemId 系统 ID
     * @param updateBO 更新入参
     * @return 系统上下文
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SystemContextVO updateProfile(Long systemId, SystemProfileUpdateBO updateBO) {
        permissionService.requireOperation("SYS_PROFILE_EDIT");
        System system = activeSystem(systemId);
        system.setName(updateBO.getName())
                .setDescription(updateBO.getDescription())
                .setDomain(updateBO.getDomain())
                .setUpdatedAt(LocalDateTime.now());
        systemService.updateById(system);
        return toSystemContext(system, currentMemberOrNull(systemId), currentTenantId());
    }

    /**
     * 查询系统租户。
     *
     * @param systemId 系统 ID
     * @return 租户列表
     */
    @Override
    public List<TenantVO> listTenants(Long systemId) {
        permissionService.requireOperation("SYS_TENANT_VIEW");
        activeSystem(systemId);
        return tenants(systemId).stream()
                .map(this::toTenantVO)
                .toList();
    }

    /**
     * 创建系统租户。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 租户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantVO createTenant(Long systemId, TenantSaveBO saveBO) {
        permissionService.requireOperation("SYS_TENANT_CREATE");
        activeSystem(systemId);
        Tenant tenant = new Tenant()
                .setSystemId(systemId)
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setStatus(ENABLED)
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        tenantService.save(tenant);
        return toTenantVO(tenant);
    }

    /**
     * 变更租户状态。
     *
     * @param systemId 系统 ID
     * @param tenantId 租户 ID
     * @param statusBO 状态入参
     * @return 租户
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TenantVO changeTenantStatus(Long systemId, Long tenantId, StatusChangeBO statusBO) {
        permissionService.requireOperation("SYS_TENANT_STATUS");
        validateEnableStatus(statusBO.getTargetStatus());
        Tenant tenant = tenantById(systemId, tenantId);
        tenant.setStatus(statusBO.getTargetStatus())
                .setUpdatedAt(LocalDateTime.now());
        tenantService.updateById(tenant);
        return toTenantVO(tenant);
    }

    /**
     * 切换当前成员租户上下文。
     *
     * @param accountId 平台账号 ID
     * @param systemId 系统 ID
     * @param switchBO 切换入参
     * @return 系统上下文
     */
    @Override
    public SystemContextVO switchTenant(Long accountId, Long systemId, TenantSwitchBO switchBO) {
        System system = activeSystem(systemId);
        Member member = enabledMemberByAccount(systemId, accountId);
        Long tenantId = parseRequiredLong(switchBO.getTenantId());
        resolveTenantForMember(member, tenantId);
        bindRequestContext(accountId, systemId, member.getId(), tenantId);
        return toSystemContext(system, member, tenantId);
    }

    /**
     * 查询系统成员列表。
     *
     * @param systemId 系统 ID
     * @param keyword 关键字
     * @param status 成员状态
     * @return 成员列表
     */
    @Override
    public List<MemberVO> listMembers(Long systemId, String keyword, String status) {
        permissionService.requireOperation("SYS_MEMBER_VIEW");
        activeSystem(systemId);
        List<Member> members = memberService.lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .filter(member -> !StringUtils.hasText(status) || Objects.equals(member.getStatus(), status))
                .filter(member -> !StringUtils.hasText(keyword) || contains(member.getMemberCode(), keyword)
                        || contains(member.getDisplayNameSnapshot(), keyword))
                .toList();
        return members.stream().map(this::toMemberVO).toList();
    }

    /**
     * 邀请或绑定系统成员。
     *
     * @param systemId 系统 ID
     * @param inviteBO 邀请入参
     * @return 成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO inviteMember(Long systemId, MemberInviteBO inviteBO) {
        permissionService.requireOperation("SYS_MEMBER_INVITE");
        System system = activeSystem(systemId);
        Account account = resolveAccount(inviteBO);
        ensureMemberNotExists(systemId, account.getId());
        Long defaultTenantId = parseLong(inviteBO.getDefaultTenantId());
        if (Objects.isNull(defaultTenantId)) {
            defaultTenantId = system.getDefaultTenantId();
        }
        LocalDateTime now = LocalDateTime.now();
        Member member = new Member()
                .setSystemId(systemId)
                .setAccountId(account.getId())
                .setMemberCode(defaultText(inviteBO.getMemberCode(), account.getLoginName()))
                .setDisplayNameSnapshot(defaultText(account.getDisplayName(), account.getLoginName()))
                .setDefaultTenantId(defaultTenantId)
                .setPostName(inviteBO.getPostName())
                .setStatus(ENABLED)
                .setSuperAdminFlag((byte) 0)
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(now)
                .setUpdatedAt(now);
        memberService.save(member);
        replaceMemberTenants(systemId, member.getId(), tenantIdsOrDefault(systemId, defaultTenantId,
                inviteBO.getTenantIds()));
        replaceMemberDepts(systemId, member.getId(), toLongSet(inviteBO.getDeptIds()));
        replaceMemberRoles(systemId, member.getId(), toLongSet(inviteBO.getRoleIds()));
        return toMemberVO(member);
    }

    /**
     * 查询系统成员详情。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @return 成员
     */
    @Override
    public MemberVO getMember(Long systemId, Long memberId) {
        permissionService.requireOperation("SYS_MEMBER_VIEW");
        return toMemberVO(activeMember(systemId, memberId));
    }

    /**
     * 更新系统成员扩展信息。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param updateBO 更新入参
     * @return 成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO updateMember(Long systemId, Long memberId, MemberUpdateBO updateBO) {
        permissionService.requireOperation("SYS_MEMBER_EDIT");
        Member member = activeMember(systemId, memberId);
        Long defaultTenantId = parseLong(updateBO.getDefaultTenantId());
        member.setPostName(updateBO.getPostName())
                .setDefaultTenantId(defaultTenantId)
                .setUpdatedAt(LocalDateTime.now());
        memberService.updateById(member);
        replaceMemberTenants(systemId, memberId, tenantIdsOrDefault(systemId, defaultTenantId, updateBO.getTenantIds()));
        replaceMemberDepts(systemId, memberId, toLongSet(updateBO.getDeptIds()));
        return toMemberVO(member);
    }

    /**
     * 变更成员状态。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param statusBO 状态入参
     * @return 成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO changeMemberStatus(Long systemId, Long memberId, StatusChangeBO statusBO) {
        permissionService.requireOperation("SYS_MEMBER_STATUS");
        validateEnableStatus(statusBO.getTargetStatus());
        Member member = activeMember(systemId, memberId);
        member.setStatus(statusBO.getTargetStatus())
                .setUpdatedAt(LocalDateTime.now());
        memberService.updateById(member);
        return toMemberVO(member);
    }

    /**
     * 分配成员角色。
     *
     * @param systemId 系统 ID
     * @param memberId 成员 ID
     * @param assignBO 分配入参
     * @return 成员
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MemberVO assignMemberRoles(Long systemId, Long memberId, MemberRoleAssignBO assignBO) {
        permissionService.requireOperation("SYS_ROLE_ASSIGN");
        Member member = activeMember(systemId, memberId);
        replaceMemberRoles(systemId, memberId, toLongSet(assignBO.getRoleIds()));
        return toMemberVO(member);
    }

    /**
     * 查询当前系统成员。
     *
     * @param accountId 平台账号 ID
     * @param systemId 系统 ID
     * @return 成员
     */
    @Override
    public MemberVO currentMember(Long accountId, Long systemId) {
        return toMemberVO(enabledMemberByAccount(systemId, accountId));
    }

    /**
     * 查询部门树。
     *
     * @param systemId 系统 ID
     * @return 部门树
     */
    @Override
    public List<DeptTreeVO> departmentTree(Long systemId) {
        permissionService.requireOperation("SYS_DEPT_VIEW");
        activeSystem(systemId);
        List<Dept> depts = deptService.lambdaQuery()
                .eq(Dept::getSystemId, systemId)
                .eq(Dept::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        return toDeptTree(depts);
    }

    /**
     * 创建部门。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 部门树节点
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeptTreeVO createDepartment(Long systemId, DeptSaveBO saveBO) {
        permissionService.requireOperation("SYS_DEPT_CREATE");
        activeSystem(systemId);
        Long parentId = parseLongOrDefault(saveBO.getParentId(), ROOT_ID);
        Dept parent = parentId.equals(ROOT_ID) ? null : activeDept(systemId, parentId);
        Dept dept = new Dept()
                .setSystemId(systemId)
                .setTenantId(parseLongOrDefault(saveBO.getTenantId(), SYSTEM_LEVEL_TENANT_ID))
                .setParentId(parentId)
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setStatus(defaultText(saveBO.getStatus(), ENABLED))
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setDepthLevel(Objects.isNull(parent) ? 1 : parent.getDepthLevel() + 1)
                .setDepthPath(Objects.isNull(parent) ? "/" : parent.getDepthPath() + parent.getId() + "/")
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        deptService.save(dept);
        return toDeptVO(dept, List.of());
    }

    /**
     * 更新部门。
     *
     * @param systemId 系统 ID
     * @param deptId 部门 ID
     * @param saveBO 保存入参
     * @return 部门树节点
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeptTreeVO updateDepartment(Long systemId, Long deptId, DeptSaveBO saveBO) {
        permissionService.requireOperation("SYS_DEPT_EDIT");
        Dept dept = activeDept(systemId, deptId);
        Long parentId = parseLongOrDefault(saveBO.getParentId(), ROOT_ID);
        Dept parent = parentId.equals(ROOT_ID) ? null : activeDept(systemId, parentId);
        dept.setTenantId(parseLongOrDefault(saveBO.getTenantId(), SYSTEM_LEVEL_TENANT_ID))
                .setParentId(parentId)
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setStatus(defaultText(saveBO.getStatus(), ENABLED))
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setDepthLevel(Objects.isNull(parent) ? 1 : parent.getDepthLevel() + 1)
                .setDepthPath(Objects.isNull(parent) ? "/" : parent.getDepthPath() + parent.getId() + "/")
                .setUpdatedAt(LocalDateTime.now());
        deptService.updateById(dept);
        return toDeptVO(dept, List.of());
    }

    /**
     * 删除部门。
     *
     * @param systemId 系统 ID
     * @param deptId 部门 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDepartment(Long systemId, Long deptId) {
        permissionService.requireOperation("SYS_DEPT_DELETE");
        Dept dept = activeDept(systemId, deptId);
        boolean hasChild = deptService.lambdaQuery()
                .eq(Dept::getSystemId, systemId)
                .eq(Dept::getParentId, deptId)
                .eq(Dept::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .count() > 0;
        boolean hasMember = memberDeptService.lambdaQuery()
                .eq(MemberDept::getSystemId, systemId)
                .eq(MemberDept::getDeptId, deptId)
                .eq(MemberDept::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .count() > 0;
        if (hasChild || hasMember) {
            throw new BusinessException(SystemManageErrorCode.DEPT_HAS_MEMBER);
        }
        dept.setDeleteToken(dept.getId())
                .setUpdatedAt(LocalDateTime.now());
        deptService.updateById(dept);
    }

    /**
     * 查询系统角色。
     *
     * @param systemId 系统 ID
     * @return 角色列表
     */
    @Override
    public List<RoleVO> listRoles(Long systemId) {
        permissionService.requireOperation("SYS_ROLE_VIEW");
        activeSystem(systemId);
        return roleService.lambdaQuery()
                .eq(Role::getSystemId, systemId)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(this::toRoleVO)
                .toList();
    }

    /**
     * 创建系统角色。
     *
     * @param systemId 系统 ID
     * @param saveBO 保存入参
     * @return 角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO createRole(Long systemId, RoleSaveBO saveBO) {
        permissionService.requireOperation("SYS_ROLE_CREATE");
        activeSystem(systemId);
        ensureRoleCodeAvailable(systemId, parseLongOrDefault(saveBO.getTenantId(), SYSTEM_LEVEL_TENANT_ID),
                saveBO.getCode(), null);
        Role role = new Role()
                .setSystemId(systemId)
                .setTenantId(parseLongOrDefault(saveBO.getTenantId(), SYSTEM_LEVEL_TENANT_ID))
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setStatus(ENABLED)
                .setProtectedFlag((byte) 0)
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), 100))
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        roleService.save(role);
        return toRoleVO(role);
    }

    /**
     * 更新系统角色。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param saveBO 保存入参
     * @return 角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO updateRole(Long systemId, Long roleId, RoleSaveBO saveBO) {
        permissionService.requireOperation("SYS_ROLE_EDIT");
        Role role = activeRole(systemId, roleId);
        ensureRoleWritable(role);
        Long tenantId = parseLongOrDefault(saveBO.getTenantId(), SYSTEM_LEVEL_TENANT_ID);
        ensureRoleCodeAvailable(systemId, tenantId, saveBO.getCode(), roleId);
        role.setTenantId(tenantId)
                .setCode(saveBO.getCode())
                .setName(saveBO.getName())
                .setDescription(saveBO.getDescription())
                .setSortOrder(defaultInteger(saveBO.getSortOrder(), role.getSortOrder()))
                .setUpdatedAt(LocalDateTime.now());
        roleService.updateById(role);
        return toRoleVO(role);
    }

    /**
     * 变更系统角色状态。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param statusBO 状态入参
     * @return 角色
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoleVO changeRoleStatus(Long systemId, Long roleId, StatusChangeBO statusBO) {
        permissionService.requireOperation("SYS_ROLE_STATUS");
        validateEnableStatus(statusBO.getTargetStatus());
        Role role = activeRole(systemId, roleId);
        ensureRoleWritable(role);
        role.setStatus(statusBO.getTargetStatus())
                .setUpdatedAt(LocalDateTime.now());
        roleService.updateById(role);
        bumpPermissionVersion(systemId, role.getTenantId(), "ROLE_STATUS_CHANGE");
        return toRoleVO(role);
    }

    /**
     * 保存角色权限。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @param saveBO 权限入参
     * @return 角色权限详情
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RolePermissionDetailVO saveRolePermissions(Long systemId, Long roleId, RolePermissionSaveBO saveBO) {
        permissionService.requireOperation("SYS_ROLE_PERMISSION_EDIT");
        Role role = activeRole(systemId, roleId);
        replaceRoleMenus(systemId, roleId, toLongSet(saveBO.getMenuIds()));
        replaceRoleOperations(systemId, roleId, toStringSet(saveBO.getOperationCodes()));
        replaceRoleFieldPermissions(systemId, roleId, safeList(saveBO.getFieldPermissions()));
        replaceRoleDataScopes(systemId, roleId, safeList(saveBO.getDataScopes()));
        replaceRoleOpenapiScopes(systemId, roleId, safeList(saveBO.getOpenapiScopes()));
        bumpPermissionVersion(systemId, role.getTenantId(), "ROLE_AUTH_SAVE");
        return rolePermissions(systemId, roleId);
    }

    /**
     * 查询角色权限详情。
     *
     * @param systemId 系统 ID
     * @param roleId 角色 ID
     * @return 角色权限详情
     */
    @Override
    public RolePermissionDetailVO rolePermissions(Long systemId, Long roleId) {
        permissionService.requireOperation("SYS_ROLE_VIEW");
        activeRole(systemId, roleId);
        return RolePermissionDetailVO.builder()
                .roleId(toId(roleId))
                .menuIds(menuIdsByRole(systemId, roleId))
                .operationCodes(operationCodesByRole(systemId, roleId))
                .fieldPermissions(fieldPermissionsByRole(systemId, roleId))
                .dataScopes(dataScopesByRole(systemId, roleId))
                .openapiScopes(openapiScopesByRole(systemId, roleId))
                .build();
    }

    /**
     * 查询当前成员有效权限。
     *
     * @return 有效权限
     */
    @Override
    public EffectivePermissionVO effectivePermissions() {
        return permissionService.currentPermission();
    }

    /**
     * 查询运行菜单树。
     *
     * @param systemId 系统 ID
     * @return 运行菜单树
     */
    @Override
    public List<SystemMenuTreeVO> runtimeMenus(Long systemId) {
        activeSystem(systemId);
        Set<String> visibleMenus = permissionService.currentPermission().getMenus();
        List<SystemMenu> menus = systemMenuService.lambdaQuery()
                .eq(SystemMenu::getSystemId, systemId)
                .eq(SystemMenu::getMenuType, "RUNTIME")
                .eq(SystemMenu::getStatus, ENABLED)
                .eq(SystemMenu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .filter(menu -> visibleMenus.contains(menu.getCode()))
                .toList();
        return toMenuTree(menus);
    }

    /**
     * 查询权限目录。
     *
     * @param systemId 系统 ID
     * @return 权限目录
     */
    @Override
    public PermissionCatalogVO permissionCatalog(Long systemId) {
        permissionService.requireOperation("SYS_ROLE_PERMISSION_EDIT");
        activeSystem(systemId);
        List<SystemMenu> menus = systemMenuService.lambdaQuery()
                .eq(SystemMenu::getSystemId, systemId)
                .eq(SystemMenu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        List<SystemOperationVO> operations = systemOperationService.lambdaQuery()
                .eq(SystemOperation::getSystemId, systemId)
                .eq(SystemOperation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(this::toOperationVO)
                .toList();
        List<String> fieldCodes = fieldService.lambdaQuery()
                .eq(Field::getSystemId, systemId)
                .list()
                .stream()
                .map(Field::getCode)
                .filter(StringUtils::hasText)
                .distinct()
                .sorted()
                .toList();
        List<String> openapiScopes = operations.stream()
                .filter(operation -> Objects.equals("OPENAPI_SCOPE", operation.getOperationType()))
                .map(SystemOperationVO::getCode)
                .toList();
        return PermissionCatalogVO.builder()
                .menus(toMenuTree(menus))
                .operations(operations)
                .fieldCodes(fieldCodes)
                .openapiScopes(openapiScopes)
                .dataScopeTypes(List.of("SELF", "DEPT", "DEPT_TREE", "ALL", "CUSTOM"))
                .build();
    }

    private System activeSystem(Long systemId) {
        System system = systemService.lambdaQuery()
                .eq(System::getId, systemId)
                .eq(System::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(system)) {
            throw new BusinessException(SystemManageErrorCode.SYSTEM_NOT_FOUND);
        }
        if (!ENABLED.equals(system.getStatus())) {
            throw new BusinessException(SystemManageErrorCode.SYSTEM_DISABLED);
        }
        return system;
    }

    private Tenant activeTenant(Long systemId, Long tenantId) {
        Tenant tenant = tenantById(systemId, tenantId);
        if (!ENABLED.equals(tenant.getStatus())) {
            throw new BusinessException(SystemManageErrorCode.TENANT_DISABLED);
        }
        return tenant;
    }

    private Tenant tenantById(Long systemId, Long tenantId) {
        Tenant tenant = tenantService.lambdaQuery()
                .eq(Tenant::getSystemId, systemId)
                .eq(Tenant::getId, tenantId)
                .eq(Tenant::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(tenant)) {
            throw new BusinessException(SystemManageErrorCode.TENANT_NOT_FOUND);
        }
        return tenant;
    }

    private Member activeMember(Long systemId, Long memberId) {
        Member member = memberService.lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getId, memberId)
                .eq(Member::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(member)) {
            throw new BusinessException(SystemManageErrorCode.MEMBER_NOT_FOUND);
        }
        return member;
    }

    private Member enabledMemberByAccount(Long systemId, Long accountId) {
        Member member = memberService.lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getAccountId, accountId)
                .eq(Member::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(member)) {
            throw new BusinessException(SystemManageErrorCode.MEMBER_NOT_FOUND);
        }
        if (!ENABLED.equals(member.getStatus())) {
            throw new BusinessException(SystemManageErrorCode.MEMBER_DISABLED);
        }
        return member;
    }

    private Member currentMemberOrNull(Long systemId) {
        RequestContext context = RequestContextHolder.get();
        if (Objects.isNull(context) || !StringUtils.hasText(context.getMemberId())) {
            return null;
        }
        return activeMember(systemId, Long.valueOf(context.getMemberId()));
    }

    private Dept activeDept(Long systemId, Long deptId) {
        Dept dept = deptService.lambdaQuery()
                .eq(Dept::getSystemId, systemId)
                .eq(Dept::getId, deptId)
                .eq(Dept::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(dept)) {
            throw new BusinessException(SystemManageErrorCode.DEPT_NOT_FOUND);
        }
        return dept;
    }

    private Role activeRole(Long systemId, Long roleId) {
        Role role = roleService.lambdaQuery()
                .eq(Role::getSystemId, systemId)
                .eq(Role::getId, roleId)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.isNull(role)) {
            throw new BusinessException(SystemManageErrorCode.ROLE_NOT_FOUND);
        }
        return role;
    }

    private Account resolveAccount(MemberInviteBO inviteBO) {
        if (StringUtils.hasText(inviteBO.getAccountId())) {
            return accountService.lambdaQuery()
                    .eq(Account::getId, Long.valueOf(inviteBO.getAccountId()))
                    .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                    .oneOpt()
                    .orElseThrow(() -> new BusinessException(SystemManageErrorCode.MEMBER_ACCOUNT_REQUIRED));
        }
        if (StringUtils.hasText(inviteBO.getLoginName())) {
            return accountService.lambdaQuery()
                    .eq(Account::getLoginName, inviteBO.getLoginName())
                    .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                    .oneOpt()
                    .orElseThrow(() -> new BusinessException(SystemManageErrorCode.MEMBER_ACCOUNT_REQUIRED));
        }
        throw new BusinessException(SystemManageErrorCode.MEMBER_ACCOUNT_REQUIRED);
    }

    private void ensureMemberNotExists(Long systemId, Long accountId) {
        long count = memberService.lambdaQuery()
                .eq(Member::getSystemId, systemId)
                .eq(Member::getAccountId, accountId)
                .eq(Member::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .count();
        if (count > 0) {
            throw new BusinessException(SystemManageErrorCode.MEMBER_DUPLICATED);
        }
    }

    private void ensureRoleCodeAvailable(Long systemId, Long tenantId, String code, Long excludeRoleId) {
        Role role = roleService.lambdaQuery()
                .eq(Role::getSystemId, systemId)
                .eq(Role::getTenantId, tenantId)
                .eq(Role::getCode, code)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        if (Objects.nonNull(role) && !Objects.equals(role.getId(), excludeRoleId)) {
            throw new BusinessException(SystemManageErrorCode.ROLE_CODE_DUPLICATED);
        }
    }

    private void ensureRoleWritable(Role role) {
        if (Objects.equals(role.getProtectedFlag(), (byte) 1)) {
            throw new BusinessException(SystemManageErrorCode.ROLE_PROTECTED);
        }
    }

    private void ensureRolesEnabled(Long systemId, Set<Long> roleIds) {
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        List<Role> roles = roleService.lambdaQuery()
                .eq(Role::getSystemId, systemId)
                .in(Role::getId, roleIds)
                .eq(Role::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        if (roles.size() != roleIds.size()) {
            throw new BusinessException(SystemManageErrorCode.ROLE_NOT_FOUND);
        }
        boolean disabled = roles.stream().anyMatch(role -> !ENABLED.equals(role.getStatus()));
        if (disabled) {
            throw new BusinessException(SystemManageErrorCode.ROLE_DISABLED);
        }
    }

    private void replaceMemberTenants(Long systemId, Long memberId, Set<Long> tenantIds) {
        memberTenantService.lambdaUpdate()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getMemberId, memberId)
                .remove();
        if (CollectionUtils.isEmpty(tenantIds)) {
            return;
        }
        tenantIds.forEach(tenantId -> activeTenant(systemId, tenantId));
        LocalDateTime now = LocalDateTime.now();
        List<MemberTenant> rows = tenantIds.stream()
                .map(tenantId -> new MemberTenant()
                        .setSystemId(systemId)
                        .setMemberId(memberId)
                        .setTenantId(tenantId)
                        .setPrimaryFlag((byte) 0)
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        memberTenantService.saveBatch(rows);
    }

    private void replaceMemberDepts(Long systemId, Long memberId, Set<Long> deptIds) {
        memberDeptService.lambdaUpdate()
                .eq(MemberDept::getSystemId, systemId)
                .eq(MemberDept::getMemberId, memberId)
                .remove();
        if (CollectionUtils.isEmpty(deptIds)) {
            return;
        }
        deptIds.forEach(deptId -> activeDept(systemId, deptId));
        LocalDateTime now = LocalDateTime.now();
        List<MemberDept> rows = deptIds.stream()
                .map(deptId -> new MemberDept()
                        .setSystemId(systemId)
                        .setMemberId(memberId)
                        .setDeptId(deptId)
                        .setPrimaryFlag((byte) 0)
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        memberDeptService.saveBatch(rows);
    }

    private void replaceMemberRoles(Long systemId, Long memberId, Set<Long> roleIds) {
        ensureRolesEnabled(systemId, roleIds);
        memberRoleService.lambdaUpdate()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, memberId)
                .remove();
        if (CollectionUtils.isEmpty(roleIds)) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<MemberRole> rows = roleIds.stream()
                .map(roleId -> new MemberRole()
                        .setSystemId(systemId)
                        .setMemberId(memberId)
                        .setRoleId(roleId)
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList();
        memberRoleService.saveBatch(rows);
    }

    private void replaceRoleMenus(Long systemId, Long roleId, Set<Long> menuIds) {
        roleMenuService.lambdaUpdate()
                .eq(RoleMenu::getSystemId, systemId)
                .eq(RoleMenu::getRoleId, roleId)
                .remove();
        if (CollectionUtils.isEmpty(menuIds)) {
            return;
        }
        Set<Long> existing = systemMenuService.lambdaQuery()
                .eq(SystemMenu::getSystemId, systemId)
                .in(SystemMenu::getId, menuIds)
                .eq(SystemMenu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(SystemMenu::getId)
                .collect(Collectors.toSet());
        if (!existing.containsAll(menuIds)) {
            throw new BusinessException(SystemManageErrorCode.PERMISSION_TARGET_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        roleMenuService.saveBatch(menuIds.stream()
                .map(menuId -> new RoleMenu()
                        .setSystemId(systemId)
                        .setRoleId(roleId)
                        .setMenuId(menuId)
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList());
    }

    private void replaceRoleOperations(Long systemId, Long roleId, Set<String> operationCodes) {
        roleOperationService.lambdaUpdate()
                .eq(RoleOperation::getSystemId, systemId)
                .eq(RoleOperation::getRoleId, roleId)
                .remove();
        if (CollectionUtils.isEmpty(operationCodes)) {
            return;
        }
        List<SystemOperation> operations = systemOperationService.lambdaQuery()
                .eq(SystemOperation::getSystemId, systemId)
                .in(SystemOperation::getCode, operationCodes)
                .eq(SystemOperation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
        Set<String> existing = operations.stream().map(SystemOperation::getCode).collect(Collectors.toSet());
        if (!existing.containsAll(operationCodes)) {
            throw new BusinessException(SystemManageErrorCode.PERMISSION_TARGET_NOT_FOUND);
        }
        LocalDateTime now = LocalDateTime.now();
        roleOperationService.saveBatch(operations.stream()
                .map(operation -> new RoleOperation()
                        .setSystemId(systemId)
                        .setRoleId(roleId)
                        .setOperationId(operation.getId())
                        .setOperationCode(operation.getCode())
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList());
    }

    private void replaceRoleFieldPermissions(Long systemId, Long roleId, List<RoleFieldPermissionBO> permissions) {
        roleFieldPermissionService.lambdaUpdate()
                .eq(RoleFieldPermission::getSystemId, systemId)
                .eq(RoleFieldPermission::getRoleId, roleId)
                .remove();
        if (permissions.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        roleFieldPermissionService.saveBatch(permissions.stream()
                .map(permission -> new RoleFieldPermission()
                        .setSystemId(systemId)
                        .setTenantId(parseLongOrDefault(permission.getTenantId(), SYSTEM_LEVEL_TENANT_ID))
                        .setRoleId(roleId)
                        .setModuleId(parseLong(permission.getModuleId()))
                        .setFieldId(parseLong(permission.getFieldId()))
                        .setFieldCode(permission.getFieldCode())
                        .setVisible(bool(permission.getVisible()))
                        .setWritable(bool(permission.getWritable()))
                        .setExportPlain(bool(permission.getExportPlain()))
                        .setOpenapiReadable(bool(permission.getOpenapiReadable()))
                        .setOpenapiWritable(bool(permission.getOpenapiWritable()))
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList());
    }

    private void replaceRoleDataScopes(Long systemId, Long roleId, List<DataScopeRuleBO> scopes) {
        roleDataScopeService.lambdaUpdate()
                .eq(RoleDataScope::getSystemId, systemId)
                .eq(RoleDataScope::getRoleId, roleId)
                .remove();
        if (scopes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        roleDataScopeService.saveBatch(scopes.stream()
                .map(scope -> new RoleDataScope()
                        .setSystemId(systemId)
                        .setTenantId(parseLongOrDefault(scope.getTenantId(), SYSTEM_LEVEL_TENANT_ID))
                        .setRoleId(roleId)
                        .setResourceType(scope.getResourceType())
                        .setResourceId(parseLongOrDefault(scope.getResourceId(), ROOT_ID))
                        .setScopeType(scope.getScopeType())
                        .setDeptIdsJson(toJsonArray(scope.getDeptIds()))
                        .setMemberIdsJson(toJsonArray(scope.getMemberIds()))
                        .setCustomConditions(scope.getCustomConditions())
                        .setMinVisibleRule(defaultText(scope.getMinVisibleRule(), "UNION_LIMITED"))
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList());
    }

    private void replaceRoleOpenapiScopes(Long systemId, Long roleId, List<OpenapiScopeBO> scopes) {
        roleOpenapiScopeService.lambdaUpdate()
                .eq(RoleOpenapiScope::getSystemId, systemId)
                .eq(RoleOpenapiScope::getRoleId, roleId)
                .remove();
        if (scopes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        roleOpenapiScopeService.saveBatch(scopes.stream()
                .map(scope -> new RoleOpenapiScope()
                        .setSystemId(systemId)
                        .setTenantId(parseLongOrDefault(scope.getTenantId(), SYSTEM_LEVEL_TENANT_ID))
                        .setRoleId(roleId)
                        .setScopeCode(scope.getScopeCode())
                        .setModuleId(parseLong(scope.getModuleId()))
                        .setFieldCodesJson(toJsonArray(scope.getFieldCodes()))
                        .setScopeAction(scope.getScopeAction())
                        .setDeleteToken(ACTIVE_DELETE_TOKEN)
                        .setCreatedAt(now)
                        .setUpdatedAt(now))
                .toList());
    }

    private void bumpPermissionVersion(Long systemId, Long tenantId, String reason) {
        PermissionVersion latest = permissionVersionService.lambdaQuery()
                .eq(PermissionVersion::getSystemId, systemId)
                .eq(PermissionVersion::getTenantId, tenantId)
                .eq(PermissionVersion::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .max(Comparator.comparing(PermissionVersion::getVersionNo, Comparator.nullsLast(Long::compareTo)))
                .orElse(null);
        Long nextVersion = Objects.isNull(latest) || Objects.isNull(latest.getVersionNo())
                ? 1L : latest.getVersionNo() + 1;
        PermissionVersion version = new PermissionVersion()
                .setSystemId(systemId)
                .setTenantId(tenantId)
                .setVersionNo(nextVersion)
                .setChangedReason(reason)
                .setChangedAt(LocalDateTime.now())
                .setDeleteToken(ACTIVE_DELETE_TOKEN)
                .setCreatedAt(LocalDateTime.now())
                .setUpdatedAt(LocalDateTime.now());
        permissionVersionService.save(version);
    }

    private SystemContextVO toSystemContext(System system, Member member, Long tenantId) {
        List<TenantVO> tenantVOList = tenants(system.getId()).stream().map(this::toTenantVO).toList();
        TenantVO currentTenant = tenantVOList.stream()
                .filter(tenant -> Objects.equals(tenant.getTenantId(), toId(tenantId)))
                .findFirst()
                .orElse(null);
        return SystemContextVO.builder()
                .systemId(toId(system.getId()))
                .systemCode(system.getCode())
                .systemName(system.getName())
                .tenantMode(system.getTenantMode())
                .status(system.getStatus())
                .currentTenant(currentTenant)
                .currentMember(Objects.isNull(member) ? null : toMemberVO(member))
                .tenants(tenantVOList)
                .permissions(permissionService.currentPermission())
                .build();
    }

    private MemberVO toMemberVO(Member member) {
        Account account = accountService.lambdaQuery()
                .eq(Account::getId, member.getAccountId())
                .eq(Account::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .one();
        return MemberVO.builder()
                .memberId(toId(member.getId()))
                .systemId(toId(member.getSystemId()))
                .accountId(toId(member.getAccountId()))
                .loginName(Objects.isNull(account) ? null : account.getLoginName())
                .displayName(Objects.isNull(account) ? member.getDisplayNameSnapshot() : account.getDisplayName())
                .memberCode(member.getMemberCode())
                .defaultTenantId(toId(member.getDefaultTenantId()))
                .postName(member.getPostName())
                .status(member.getStatus())
                .superAdmin(Objects.equals(member.getSuperAdminFlag(), (byte) 1))
                .tenantIds(tenantIdsByMember(member.getSystemId(), member.getId()))
                .deptIds(deptIdsByMember(member.getSystemId(), member.getId()))
                .roleIds(roleIdsByMember(member.getSystemId(), member.getId()))
                .build();
    }

    private TenantVO toTenantVO(Tenant tenant) {
        return TenantVO.builder()
                .tenantId(toId(tenant.getId()))
                .systemId(toId(tenant.getSystemId()))
                .code(tenant.getCode())
                .name(tenant.getName())
                .status(tenant.getStatus())
                .description(tenant.getDescription())
                .build();
    }

    private RoleVO toRoleVO(Role role) {
        return RoleVO.builder()
                .roleId(toId(role.getId()))
                .systemId(toId(role.getSystemId()))
                .tenantId(toId(role.getTenantId()))
                .code(role.getCode())
                .name(role.getName())
                .description(role.getDescription())
                .status(role.getStatus())
                .protectedFlag(Objects.equals(role.getProtectedFlag(), (byte) 1))
                .sortOrder(role.getSortOrder())
                .menuIds(menuIdsByRole(role.getSystemId(), role.getId()))
                .operationCodes(operationCodesByRole(role.getSystemId(), role.getId()))
                .build();
    }

    private DeptTreeVO toDeptVO(Dept dept, List<DeptTreeVO> children) {
        return DeptTreeVO.builder()
                .deptId(toId(dept.getId()))
                .systemId(toId(dept.getSystemId()))
                .tenantId(toId(dept.getTenantId()))
                .parentId(toId(dept.getParentId()))
                .code(dept.getCode())
                .name(dept.getName())
                .status(dept.getStatus())
                .sortOrder(dept.getSortOrder())
                .depthLevel(dept.getDepthLevel())
                .depthPath(dept.getDepthPath())
                .children(children)
                .build();
    }

    private List<DeptTreeVO> toDeptTree(List<Dept> depts) {
        Map<Long, List<Dept>> children = depts.stream()
                .sorted(Comparator.comparing(Dept::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.groupingBy(Dept::getParentId, LinkedHashMap::new, Collectors.toList()));
        return toDeptTree(children, ROOT_ID);
    }

    private List<DeptTreeVO> toDeptTree(Map<Long, List<Dept>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of())
                .stream()
                .map(dept -> toDeptVO(dept, toDeptTree(children, dept.getId())))
                .toList();
    }

    private List<SystemMenuTreeVO> toMenuTree(List<SystemMenu> menus) {
        Map<Long, List<SystemMenu>> children = menus.stream()
                .sorted(Comparator.comparing(SystemMenu::getSortOrder, Comparator.nullsLast(Integer::compareTo)))
                .collect(Collectors.groupingBy(SystemMenu::getParentId, LinkedHashMap::new, Collectors.toList()));
        return toMenuTree(children, ROOT_ID);
    }

    private List<SystemMenuTreeVO> toMenuTree(Map<Long, List<SystemMenu>> children, Long parentId) {
        return children.getOrDefault(parentId, List.of())
                .stream()
                .map(menu -> SystemMenuTreeVO.builder()
                        .menuId(toId(menu.getId()))
                        .parentId(toId(menu.getParentId()))
                        .code(menu.getCode())
                        .name(menu.getName())
                        .menuType(menu.getMenuType())
                        .sourceType(menu.getSourceType())
                        .path(menu.getPath())
                        .status(menu.getStatus())
                        .children(toMenuTree(children, menu.getId()))
                        .build())
                .toList();
    }

    private SystemOperationVO toOperationVO(SystemOperation operation) {
        return SystemOperationVO.builder()
                .operationId(toId(operation.getId()))
                .menuId(toId(operation.getMenuId()))
                .code(operation.getCode())
                .name(operation.getName())
                .operationType(operation.getOperationType())
                .resourceType(operation.getResourceType())
                .apiPattern(operation.getApiPattern())
                .method(operation.getMethod())
                .status(operation.getStatus())
                .build();
    }

    private List<Tenant> tenants(Long systemId) {
        return tenantService.lambdaQuery()
                .eq(Tenant::getSystemId, systemId)
                .eq(Tenant::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list();
    }

    private Long resolveTenantForMember(Member member, Long requestedTenantId) {
        Set<Long> tenantIds = tenantIdsByMember(member.getSystemId(), member.getId()).stream()
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Long tenantId = Objects.nonNull(requestedTenantId) ? requestedTenantId : member.getDefaultTenantId();
        if (!tenantIds.contains(tenantId)) {
            throw new BusinessException(SystemManageErrorCode.TENANT_NOT_FOUND);
        }
        activeTenant(member.getSystemId(), tenantId);
        return tenantId;
    }

    private Set<Long> tenantIdsOrDefault(Long systemId, Long defaultTenantId, List<String> tenantIds) {
        Set<Long> resolved = toLongSet(tenantIds);
        if (CollectionUtils.isEmpty(resolved) && Objects.nonNull(defaultTenantId)) {
            resolved.add(defaultTenantId);
        }
        resolved.forEach(tenantId -> activeTenant(systemId, tenantId));
        return resolved;
    }

    private List<String> tenantIdsByMember(Long systemId, Long memberId) {
        return memberTenantService.lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getMemberId, memberId)
                .eq(MemberTenant::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(MemberTenant::getTenantId)
                .map(this::toId)
                .toList();
    }

    private List<String> deptIdsByMember(Long systemId, Long memberId) {
        return memberDeptService.lambdaQuery()
                .eq(MemberDept::getSystemId, systemId)
                .eq(MemberDept::getMemberId, memberId)
                .eq(MemberDept::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(MemberDept::getDeptId)
                .map(this::toId)
                .toList();
    }

    private List<String> roleIdsByMember(Long systemId, Long memberId) {
        return memberRoleService.lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, memberId)
                .eq(MemberRole::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(MemberRole::getRoleId)
                .map(this::toId)
                .toList();
    }

    private List<String> menuIdsByRole(Long systemId, Long roleId) {
        return roleMenuService.lambdaQuery()
                .eq(RoleMenu::getSystemId, systemId)
                .eq(RoleMenu::getRoleId, roleId)
                .eq(RoleMenu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleMenu::getMenuId)
                .map(this::toId)
                .toList();
    }

    private List<String> operationCodesByRole(Long systemId, Long roleId) {
        return roleOperationService.lambdaQuery()
                .eq(RoleOperation::getSystemId, systemId)
                .eq(RoleOperation::getRoleId, roleId)
                .eq(RoleOperation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleOperation::getOperationCode)
                .toList();
    }

    private List<FieldPermissionVO> fieldPermissionsByRole(Long systemId, Long roleId) {
        return roleFieldPermissionService.lambdaQuery()
                .eq(RoleFieldPermission::getSystemId, systemId)
                .eq(RoleFieldPermission::getRoleId, roleId)
                .eq(RoleFieldPermission::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(permission -> FieldPermissionVO.builder()
                        .fieldCode(permission.getFieldCode())
                        .visible(Objects.equals(permission.getVisible(), (byte) 1))
                        .writable(Objects.equals(permission.getWritable(), (byte) 1))
                        .exportPlain(Objects.equals(permission.getExportPlain(), (byte) 1))
                        .openapiReadable(Objects.equals(permission.getOpenapiReadable(), (byte) 1))
                        .openapiWritable(Objects.equals(permission.getOpenapiWritable(), (byte) 1))
                        .build())
                .toList();
    }

    private List<DataScopeRuleVO> dataScopesByRole(Long systemId, Long roleId) {
        return roleDataScopeService.lambdaQuery()
                .eq(RoleDataScope::getSystemId, systemId)
                .eq(RoleDataScope::getRoleId, roleId)
                .eq(RoleDataScope::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(scope -> DataScopeRuleVO.builder()
                        .resourceType(scope.getResourceType())
                        .resourceId(toId(scope.getResourceId()))
                        .scopeType(scope.getScopeType())
                        .deptIds(parseJsonIds(scope.getDeptIdsJson()))
                        .memberIds(parseJsonIds(scope.getMemberIdsJson()))
                        .customConditions(scope.getCustomConditions())
                        .minVisibleRule(scope.getMinVisibleRule())
                        .build())
                .toList();
    }

    private List<String> openapiScopesByRole(Long systemId, Long roleId) {
        return roleOpenapiScopeService.lambdaQuery()
                .eq(RoleOpenapiScope::getSystemId, systemId)
                .eq(RoleOpenapiScope::getRoleId, roleId)
                .eq(RoleOpenapiScope::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleOpenapiScope::getScopeCode)
                .filter(StringUtils::hasText)
                .toList();
    }

    private void bindRequestContext(Long accountId, Long systemId, Long memberId, Long tenantId) {
        RequestContext context = RequestContextHolder.get();
        if (Objects.nonNull(context)) {
            context.setAccountId(toId(accountId));
            context.setSystemId(toId(systemId));
            context.setMemberId(toId(memberId));
            context.setTenantId(toId(tenantId));
        }
    }

    private Long currentTenantId() {
        RequestContext context = RequestContextHolder.get();
        if (Objects.isNull(context) || !StringUtils.hasText(context.getTenantId())) {
            return null;
        }
        return Long.valueOf(context.getTenantId());
    }

    private void validateEnableStatus(String status) {
        if (!ENABLED.equals(status) && !DISABLED.equals(status)) {
            throw new BusinessException(SystemManageErrorCode.STATUS_INVALID);
        }
    }

    private Long parseRequiredLong(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(SystemManageErrorCode.STATUS_INVALID, "ID 不能为空");
        }
        return Long.valueOf(value);
    }

    private Long parseLong(String value) {
        return StringUtils.hasText(value) ? Long.valueOf(value) : null;
    }

    private Long parseLongOrDefault(String value, Long defaultValue) {
        Long parsed = parseLong(value);
        return Objects.nonNull(parsed) ? parsed : defaultValue;
    }

    private Set<Long> toLongSet(Collection<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return new LinkedHashSet<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(Long::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> toStringSet(Collection<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return Set.of();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> parseJsonIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        return java.util.Arrays.stream(json.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split(","))
                .map(String::strip)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String toJsonArray(Collection<String> values) {
        if (CollectionUtils.isEmpty(values)) {
            return "[]";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> "\"" + value.replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }

    private byte bool(Boolean value) {
        return Boolean.TRUE.equals(value) ? (byte) 1 : (byte) 0;
    }

    private String defaultText(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value : defaultValue;
    }

    private Integer defaultInteger(Integer value, Integer defaultValue) {
        return Objects.nonNull(value) ? value : defaultValue;
    }

    private boolean contains(String source, String keyword) {
        return StringUtils.hasText(source) && source.contains(keyword);
    }

    private <T> List<T> safeList(List<T> values) {
        return Objects.isNull(values) ? List.of() : values;
    }

    private <T, R> R valueOrNull(T source, Function<T, R> getter) {
        return Objects.isNull(source) ? null : getter.apply(source);
    }

    private String toId(Long id) {
        return Objects.isNull(id) ? null : String.valueOf(id);
    }
}
