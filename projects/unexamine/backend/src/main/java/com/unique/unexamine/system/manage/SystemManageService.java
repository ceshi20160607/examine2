package com.unique.unexamine.system.manage;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.unique.unexamine.system.base.entity.SystemDefinition;
import com.unique.unexamine.system.base.entity.SystemMember;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemTenant;
import com.unique.unexamine.system.base.entity.TenantMember;
import com.unique.unexamine.system.base.service.SystemDefinitionBaseService;
import com.unique.unexamine.system.base.service.SystemMemberBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemTenantBaseService;
import com.unique.unexamine.system.base.service.TenantMemberBaseService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/** 建系统的最小业务事务：系统、默认主租户、创建人成员及租户准入必须一起成功。 */
@Service
public class SystemManageService {
    private final SystemDefinitionBaseService systems;
    private final SystemTenantBaseService tenants;
    private final SystemMemberBaseService members;
    private final TenantMemberBaseService tenantMembers;
    private final SystemRoleBaseService roles;
    private final SystemPermissionService access;

    public SystemManageService(SystemDefinitionBaseService systems, SystemTenantBaseService tenants,
                               SystemMemberBaseService members, TenantMemberBaseService tenantMembers,
                               SystemRoleBaseService roles, SystemPermissionService access) {
        this.systems = systems;
        this.tenants = tenants;
        this.members = members;
        this.tenantMembers = tenantMembers;
        this.roles = roles;
        this.access = access;
    }

    /** 系统编码全平台唯一；主租户使用系统内固定编码 main。 */
    @Transactional
    public SystemDefinition create(Long accountId, String code, String name, String description) {
        if (systems.selectOne(new LambdaQueryWrapper<SystemDefinition>().eq(SystemDefinition::getCode, code)) != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "系统编码已存在");
        }
        SystemDefinition system = new SystemDefinition();
        system.setOwnerAccountId(accountId);
        system.setCode(code);
        system.setName(name);
        system.setDescription(description);
        system.setStatus("ACTIVE");
        systems.insert(system);

        SystemTenant tenant = new SystemTenant();
        tenant.setSystemId(system.getId());
        tenant.setCode("main");
        tenant.setName("主租户");
        tenant.setMainFlag(true);
        tenant.setStatus("ACTIVE");
        tenants.insert(tenant);

        SystemRole memberRole = new SystemRole();
        memberRole.setSystemId(system.getId());
        memberRole.setTenantId(tenant.getId());
        memberRole.setCode("member");
        memberRole.setName("普通成员");
        memberRole.setStatus("ACTIVE");
        roles.insert(memberRole);

        SystemMember member = new SystemMember();
        member.setSystemId(system.getId());
        member.setAccountId(accountId);
        member.setStatus("ACTIVE");
        members.insert(member);

        TenantMember tenantMember = new TenantMember();
        tenantMember.setSystemId(system.getId());
        tenantMember.setTenantId(tenant.getId());
        tenantMember.setMemberId(member.getId());
        tenantMember.setStatus("ACTIVE");
        tenantMembers.insert(tenantMember);
        return system;
    }

    /** 只列出当前账号实际加入的启用系统。 */
    public List<SystemDefinition> list(Long accountId) {
        List<Long> systemIds = members.selectList(new LambdaQueryWrapper<SystemMember>()
                        .eq(SystemMember::getAccountId, accountId).eq(SystemMember::getStatus, "ACTIVE"))
                .stream().map(SystemMember::getSystemId).toList();
        if (systemIds.isEmpty()) return List.of();
        return systems.selectList(new LambdaQueryWrapper<SystemDefinition>()
                .in(SystemDefinition::getId, systemIds).eq(SystemDefinition::getStatus, "ACTIVE")
                .orderByDesc(SystemDefinition::getCreateTime));
    }

    /** 进入系统必须有启用的系统成员关系；其他账号不能凭系统主键读取。 */
    public SystemDefinition enter(Long accountId, Long systemId) {
        return access.requireMembership(accountId, systemId).system();
    }

    /** 系统配置只允许当前所属人执行；成员运行权限在后续角色阶段单独判定。 */
    public SystemDefinition requireOwner(Long accountId, Long systemId) {
        SystemDefinition system = enter(accountId, systemId);
        if (!accountId.equals(system.getOwnerAccountId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅系统所属人可配置");
        }
        return system;
    }
}
