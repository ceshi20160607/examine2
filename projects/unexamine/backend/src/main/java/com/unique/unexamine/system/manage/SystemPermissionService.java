package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.moduleconfig.base.entity.BusinessModule;
import com.unique.unexamine.moduleconfig.base.entity.ModuleMenu;
import com.unique.unexamine.moduleconfig.base.service.BusinessModuleBaseService;
import com.unique.unexamine.moduleconfig.base.service.ModuleMenuBaseService;
import com.unique.unexamine.system.base.entity.MemberRole;
import com.unique.unexamine.system.base.entity.RoleMenuGrant;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.TenantMember;
import com.unique.unexamine.system.base.service.MemberRoleBaseService;
import com.unique.unexamine.system.base.service.RoleMenuGrantBaseService;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.TenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 系统与主租户上下文及菜单动作权限；所属人隐式拥有全权限。 */
@Service
public class SystemPermissionService {
    private final SystemDefinitionBaseService systems;
    private final SystemTenantBaseService tenants;
    private final SystemMemberBaseService members;
    private final TenantMemberBaseService tenantMembers;
    private final SystemRoleBaseService roles;
    private final MemberRoleBaseService memberRoles;
    private final ModuleMenuBaseService menus;
    private final RoleMenuGrantBaseService grants;
    private final BusinessModuleBaseService modules;

    public SystemPermissionService(SystemDefinitionBaseService systems, SystemTenantBaseService tenants,
                                   SystemMemberBaseService members, TenantMemberBaseService tenantMembers,
                                   SystemRoleBaseService roles, MemberRoleBaseService memberRoles,
                                   ModuleMenuBaseService menus, RoleMenuGrantBaseService grants,
                                   BusinessModuleBaseService modules) {
        this.systems = systems;
        this.tenants = tenants;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.roles = roles;
        this.memberRoles = memberRoles;
        this.menus = menus;
        this.grants = grants;
        this.modules = modules;
    }

    /** 进入系统时形成明确的系统成员和主租户成员上下文。 */
    public Context requireMembership(Long accountId, Long systemId) {
        SystemDefinition system = systems.selectById(systemId);
        if (system == null || !"ACTIVE".equals(system.getStatus())) throw denied();
        SystemMember member = members.selectOne(new LambdaQueryWrapper<SystemMember>()
                .eq(SystemMember::getSystemId, systemId).eq(SystemMember::getAccountId, accountId)
                .eq(SystemMember::getStatus, "ACTIVE"));
        if (member == null) throw denied();
        SystemTenant tenant = tenants.selectOne(new LambdaQueryWrapper<SystemTenant>()
                .eq(SystemTenant::getSystemId, systemId).eq(SystemTenant::getMainFlag, true)
                .eq(SystemTenant::getStatus, "ACTIVE"));
        if (tenant == null) throw denied();
        TenantMember tenantMember = tenantMembers.selectOne(new LambdaQueryWrapper<TenantMember>()
                .eq(TenantMember::getSystemId, systemId).eq(TenantMember::getTenantId, tenant.getId())
                .eq(TenantMember::getMemberId, member.getId()).eq(TenantMember::getStatus, "ACTIVE"));
        if (tenantMember == null) throw denied();
        return new Context(system, tenant, member, accountId.equals(system.getOwnerAccountId()));
    }

    /** 当前主租户全部有效角色权限求并集；未发布模块和跨系统菜单一律不可访问。 */
    public boolean canModule(Long accountId, Long systemId, Long moduleId, String action) {
        Context context = requireMembership(accountId, systemId);
        BusinessModule module = modules.selectById(moduleId);
        if (module == null || !systemId.equals(module.getSystemId()) || !"PUBLISHED".equals(module.getStatus())) return false;
        if (context.owner()) return true;
        List<Long> assigned = memberRoles.selectList(new LambdaQueryWrapper<MemberRole>()
                        .eq(MemberRole::getTenantId, context.tenant().getId())
                        .eq(MemberRole::getMemberId, context.member().getId()))
                .stream().map(MemberRole::getRoleId).toList();
        if (assigned.isEmpty()) return false;
        List<Long> activeRoles = roles.selectList(new LambdaQueryWrapper<SystemRole>()
                        .in(SystemRole::getId, assigned).eq(SystemRole::getSystemId, systemId)
                        .eq(SystemRole::getTenantId, context.tenant().getId()).eq(SystemRole::getStatus, "ACTIVE"))
                .stream().map(SystemRole::getId).toList();
        if (activeRoles.isEmpty()) return false;
        List<Long> menuIds = menus.selectList(new LambdaQueryWrapper<ModuleMenu>()
                        .eq(ModuleMenu::getModuleId, moduleId).eq(ModuleMenu::getStatus, "ACTIVE"))
                .stream().map(ModuleMenu::getId).toList();
        if (menuIds.isEmpty()) return false;
        return grants.selectList(new LambdaQueryWrapper<RoleMenuGrant>()
                .in(RoleMenuGrant::getRoleId, activeRoles).in(RoleMenuGrant::getMenuId, menuIds))
                .stream().anyMatch(grant -> switch (action) {
                    case "VIEW" -> Boolean.TRUE.equals(grant.getCanView());
                    case "CREATE" -> Boolean.TRUE.equals(grant.getCanCreate());
                    case "UPDATE" -> Boolean.TRUE.equals(grant.getCanUpdate());
                    case "DELETE" -> Boolean.TRUE.equals(grant.getCanDelete());
                    case "EXPORT" -> Boolean.TRUE.equals(grant.getCanExport());
                    default -> false;
                });
    }

    /** 页面与 API 共用同一拒绝规则。 */
    public Context requireModule(Long accountId, Long systemId, Long moduleId, String action) {
        Context context = requireMembership(accountId, systemId);
        if (!canModule(accountId, systemId, moduleId, action)) throw denied();
        return context;
    }

    private ResponseStatusException denied() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "系统或模块不存在或无访问权限");
    }

    /** 当前仅支持自动创建的主租户；多租户选择将在对应阶段扩展。 */
    public record Context(SystemDefinition system, SystemTenant tenant, SystemMember member, boolean owner) {}
}
