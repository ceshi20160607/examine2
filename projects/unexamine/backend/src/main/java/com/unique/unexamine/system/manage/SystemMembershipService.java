package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.moduleconfig.base.entity.BusinessModule;
import com.unique.unexamine.moduleconfig.base.entity.ModuleMenu;
import com.unique.unexamine.moduleconfig.base.service.BusinessModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleMenuBaseService;
import com.unique.unexamine.platform.base.entity.PlatformAccount;
import com.unique.unexamine.platform.base.service.PlatformAccountBaseService;
import com.unique.unexamine.system.base.entity.MemberRole;
import com.unique.unexamine.system.base.entity.RoleMenuGrant;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.TenantMember;
import com.unique.unexamine.system.base.service.MemberRoleBaseService;
import com.unique.unexamine.system.base.service.RoleMenuGrantBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.TenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 主租户成员邀请和菜单角色授权；当前仅接纳已注册的平台账号。 */
@Service
public class SystemMembershipService {
    private final SystemManageService systems;
    private final SystemPermissionService access;
    private final PlatformAccountBaseService accounts;
    private final SystemRoleBaseService roles;
    private final SystemMemberBaseService members;
    private final TenantMemberBaseService tenantMembers;
    private final MemberRoleBaseService memberRoles;
    private final RoleMenuGrantBaseService grants;
    private final BusinessModuleBaseService modules;
    private final ModuleMenuBaseService menus;

    public SystemMembershipService(SystemManageService systems, SystemPermissionService access,
                                   PlatformAccountBaseService accounts, SystemRoleBaseService roles,
                                   SystemMemberBaseService members, TenantMemberBaseService tenantMembers,
                                   MemberRoleBaseService memberRoles, RoleMenuGrantBaseService grants,
                                   BusinessModuleBaseService modules, ModuleMenuBaseService menus) {
        this.systems = systems;
        this.access = access;
        this.accounts = accounts;
        this.roles = roles;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.memberRoles = memberRoles;
        this.grants = grants;
        this.modules = modules;
        this.menus = menus;
    }

    /** 角色属于当前系统主租户；角色代码在租户内唯一。 */
    public SystemRole createRole(Long ownerId, Long systemId, String code, String name) {
        SystemPermissionService.Context context = ownerContext(ownerId, systemId);
        if (roles.selectOne(new LambdaQueryWrapper<SystemRole>().eq(SystemRole::getTenantId, context.tenant().getId())
                .eq(SystemRole::getCode, code)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "角色编码已存在");
        }
        SystemRole role = new SystemRole();
        role.setSystemId(systemId);
        role.setTenantId(context.tenant().getId());
        role.setCode(code);
        role.setName(name);
        role.setStatus("ACTIVE");
        roles.insert(role);
        return role;
    }

    /** 后台读取主租户角色列表。 */
    public List<SystemRole> roles(Long ownerId, Long systemId) {
        SystemPermissionService.Context context = ownerContext(ownerId, systemId);
        return roles.selectList(new LambdaQueryWrapper<SystemRole>().eq(SystemRole::getTenantId, context.tenant().getId())
                .eq(SystemRole::getStatus, "ACTIVE").orderByAsc(SystemRole::getId));
    }

    /** 将已注册账号加入当前系统与主租户，角色授权和成员关系在同一事务完成。 */
    @Transactional
    public SystemMember invite(Long ownerId, Long systemId, String username, Long roleId) {
        SystemPermissionService.Context context = ownerContext(ownerId, systemId);
        requireRole(context, roleId);
        PlatformAccount account = accounts.selectOne(new LambdaQueryWrapper<PlatformAccount>()
                .eq(PlatformAccount::getUsername, username).eq(PlatformAccount::getStatus, "ACTIVE"));
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "账号不存在，请先注册平台账号");
        SystemMember member = members.selectOne(new LambdaQueryWrapper<SystemMember>()
                .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, account.getId()));
        if (member == null) {
            member = new SystemMember();
            member.setSystemId(systemId);
            member.setAccountId(account.getId());
            member.setStatus("ACTIVE");
            members.insert(member);
        } else if (!"ACTIVE".equals(member.getStatus())) {
            member.setStatus("ACTIVE");
            members.updateById(member);
        }
        TenantMember tenantMember = tenantMembers.selectOne(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getTenantId, context.tenant().getId()).eq(TenantMember::getMemberId, member.getId()));
        if (tenantMember == null) {
            tenantMember = new TenantMember();
            tenantMember.setSystemId(systemId);
            tenantMember.setTenantId(context.tenant().getId());
            tenantMember.setMemberId(member.getId());
            tenantMember.setStatus("ACTIVE");
            tenantMembers.insert(tenantMember);
        } else if (!"ACTIVE".equals(tenantMember.getStatus())) {
            tenantMember.setStatus("ACTIVE");
            tenantMembers.updateById(tenantMember);
        }
        MemberRole link = memberRoles.selectOne(new LambdaQueryWrapper<MemberRole>()
                .eq(MemberRole::getTenantId, context.tenant().getId())
                .eq(MemberRole::getMemberId, member.getId()).eq(MemberRole::getRoleId, roleId));
        if (link == null) {
            link = new MemberRole();
            link.setTenantId(context.tenant().getId());
            link.setMemberId(member.getId());
            link.setRoleId(roleId);
            memberRoles.insert(link);
        }
        return member;
    }

    /** 为指定角色设置当前系统已发布模块的默认列表菜单权限。 */
    @Transactional
    public RoleMenuGrant grant(Long ownerId, Long systemId, Long roleId, Long moduleId,
                               boolean view, boolean create, boolean update, boolean delete, boolean export) {
        SystemPermissionService.Context context = ownerContext(ownerId, systemId);
        requireRole(context, roleId);
        BusinessModule module = modules.selectById(moduleId);
        if (module == null || !systemId.equals(module.getSystemId()) || !"PUBLISHED".equals(module.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只能授权本系统已发布模块");
        }
        if ((create || update || delete || export) && !view) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新增、编辑、删除或导出权限必须同时允许查看");
        }
        if (delete) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前模块尚未开放删除动作");
        ModuleMenu menu = menus.selectOne(new LambdaQueryWrapper<ModuleMenu>().eq(ModuleMenu::getModuleId, moduleId)
                .eq(ModuleMenu::getCode, "list").eq(ModuleMenu::getStatus, "ACTIVE"));
        if (menu == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "模块没有可授权的列表菜单");
        RoleMenuGrant grant = grants.selectOne(new LambdaQueryWrapper<RoleMenuGrant>()
                .eq(RoleMenuGrant::getRoleId, roleId).eq(RoleMenuGrant::getMenuId, menu.getId()));
        if (grant == null) {
            grant = new RoleMenuGrant();
            grant.setRoleId(roleId);
            grant.setMenuId(menu.getId());
        }
        grant.setCanView(view);
        grant.setCanCreate(create);
        grant.setCanUpdate(update);
        grant.setCanDelete(delete);
        grant.setCanExport(export);
        if (grant.getId() == null) grants.insert(grant);
        else grants.updateById(grant);
        return grant;
    }

    /** 后台读取角色已授予的模块菜单权限。 */
    public List<GrantView> grants(Long ownerId, Long systemId, Long roleId) {
        SystemPermissionService.Context context = ownerContext(ownerId, systemId);
        requireRole(context, roleId);
        return grants.selectList(new LambdaQueryWrapper<RoleMenuGrant>().eq(RoleMenuGrant::getRoleId, roleId))
                .stream().map(grant -> {
                    ModuleMenu menu = menus.selectById(grant.getMenuId());
                    return new GrantView(menu.getModuleId(), grant.getCanView(), grant.getCanCreate(),
                            grant.getCanUpdate(), grant.getCanDelete(), grant.getCanExport());
                }).toList();
    }

    /** 后台列出主租户成员账号；所属人也以系统成员关系展示。 */
    public List<MemberView> members(Long ownerId, Long systemId) {
        SystemPermissionService.Context context = ownerContext(ownerId, systemId);
        return tenantMembers.selectList(new LambdaQueryWrapper<TenantMember>()
                        .eq(TenantMember::getSystemId, systemId).eq(TenantMember::getTenantId, context.tenant().getId())
                        .eq(TenantMember::getStatus, "ACTIVE"))
                .stream().map(link -> {
                    SystemMember member = members.selectById(link.getMemberId());
                    PlatformAccount account = accounts.selectById(member.getAccountId());
                    return new MemberView(member.getId(), account.getUsername(), account.getDisplayName());
                }).toList();
    }

    private SystemPermissionService.Context ownerContext(Long ownerId, Long systemId) {
        systems.requireOwner(ownerId, systemId);
        return access.requireMembership(ownerId, systemId);
    }

    private SystemRole requireRole(SystemPermissionService.Context context, Long roleId) {
        SystemRole role = roles.selectById(roleId);
        if (role == null || !context.system().getId().equals(role.getSystemId())
                || !context.tenant().getId().equals(role.getTenantId()) || !"ACTIVE".equals(role.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        }
        return role;
    }

    /** 系统后台成员列表的必要字段。 */
    public record MemberView(Long id, String username, String displayName) {}
    /** 角色对单个模块菜单的有效配置。 */
    public record GrantView(Long moduleId, boolean canView, boolean canCreate, boolean canUpdate,
                            boolean canDelete, boolean canExport) {}
}
