package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.MemberRole;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatMemberRoleMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashSet;

@Component
public class SystemAdminScopeSupport {
    private final PlatMemberRoleMapper memberRoleMapper;
    private final PlatRoleMapper roleMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatPermissionMapper permissionMapper;

    public SystemAdminScopeSupport(
            PlatMemberRoleMapper memberRoleMapper,
            PlatRoleMapper roleMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatPermissionMapper permissionMapper
    ) {
        this.memberRoleMapper = memberRoleMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
    }

    public long tenantId(AuthenticatedSession session) {
        if (session.tenantId() == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return session.tenantId();
    }

    public boolean systemWide(AuthenticatedSession session, long systemId, String permissionCode) {
        var now = LocalDateTime.now();
        var bindings = memberRoleMapper.selectList(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, session.memberId())
                .isNull(MemberRole::getTenantId)
                .le(MemberRole::getValidFrom, now)
                .and(query -> query.isNull(MemberRole::getValidUntil).or().gt(MemberRole::getValidUntil, now)));
        if (bindings.isEmpty()) {
            return false;
        }
        var bindingRoleIds = bindings.stream().map(MemberRole::getRoleId).distinct().toList();
        var roles = roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .isNull(Role::getTenantId)
                .eq(Role::getStatus, "ACTIVE")
                .in(Role::getId, bindingRoleIds));
        if (roles.isEmpty()) {
            return false;
        }
        var permission = permissionMapper.selectOne(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "SYSTEM")
                .eq(Permission::getScopeKey, systemId)
                .eq(Permission::getSystemId, systemId)
                .eq(Permission::getPermissionCode, permissionCode)
                .eq(Permission::getStatus, "ACTIVE"));
        if (permission == null) {
            return false;
        }
        var roleIds = roles.stream().map(Role::getId).toList();
        var links = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "SYSTEM")
                .eq(RolePermission::getScopeKey, systemId)
                .eq(RolePermission::getPermissionId, permission.getId())
                .in(RolePermission::getRoleId, roleIds));
        var effects = new HashSet<String>();
        links.forEach(link -> effects.add(link.getEffect()));
        return effects.contains("ALLOW") && !effects.contains("DENY");
    }

    public void requireTenant(
            AuthenticatedSession session,
            long systemId,
            long targetTenantId,
            String permissionCode
    ) {
        if (targetTenantId != tenantId(session) && !systemWide(session, systemId, permissionCode)) {
            throw SystemAdminMutationSupport.notFound();
        }
    }
}
