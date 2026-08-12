package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.DataScope;
import com.unique.examine.plat.base.entity.MemberRole;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatAccountRoleMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeMapper;
import com.unique.examine.plat.base.mapper.PlatMemberRoleMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class AuthorizationService {
    private final PlatAccountRoleMapper accountRoleMapper;
    private final PlatMemberRoleMapper memberRoleMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final PlatRoleMapper roleMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatPermissionMapper permissionMapper;
    private final PlatDataScopeMapper dataScopeMapper;
    private final AuthzEpochService epochService;

    public AuthorizationService(
            PlatAccountRoleMapper accountRoleMapper,
            PlatMemberRoleMapper memberRoleMapper,
            PlatMemberTenantMapper memberTenantMapper,
            PlatRoleMapper roleMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatPermissionMapper permissionMapper,
            PlatDataScopeMapper dataScopeMapper,
            AuthzEpochService epochService
    ) {
        this.accountRoleMapper = accountRoleMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.permissionMapper = permissionMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.epochService = epochService;
    }

    public AuthorizationSnapshot platform(long accountId) {
        var now = LocalDateTime.now();
        var bindings = accountRoleMapper.selectList(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getAccountId, accountId)
                .le(AccountRole::getValidFrom, now)
                .and(query -> query.isNull(AccountRole::getValidUntil).or().gt(AccountRole::getValidUntil, now)));
        var roleIds = bindings.stream().map(AccountRole::getRoleId).distinct().toList();
        var roles = activeRoles(roleIds, "PLATFORM", 0L, null, null);
        return snapshot(epochService.currentPlatform(), roles);
    }

    public AuthorizationSnapshot system(long systemId, long tenantId, long memberId) {
        var access = memberTenantMapper.selectOne(Wrappers.<MemberTenant>lambdaQuery()
                .eq(MemberTenant::getSystemId, systemId)
                .eq(MemberTenant::getMemberId, memberId)
                .eq(MemberTenant::getTenantId, tenantId));
        var now = LocalDateTime.now();
        if (access == null || !"ACTIVE".equals(access.getStatus())
                || access.getExpiresAt() != null && !access.getExpiresAt().isAfter(now)) {
            throw new BusinessException("TENANT_ACCESS_REQUIRED", "当前成员没有有效租户访问权限", HttpStatus.FORBIDDEN);
        }
        var bindings = memberRoleMapper.selectList(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, memberId)
                .le(MemberRole::getValidFrom, now)
                .and(query -> query.isNull(MemberRole::getValidUntil).or().gt(MemberRole::getValidUntil, now))
                .and(query -> query.isNull(MemberRole::getTenantId).or().eq(MemberRole::getTenantId, tenantId)));
        var roleIds = bindings.stream().map(MemberRole::getRoleId).distinct().toList();
        var roles = activeRoles(roleIds, "SYSTEM", systemId, systemId, tenantId);
        return snapshot(epochService.currentSystem(systemId), roles);
    }

    private List<Role> activeRoles(
            List<Long> roleIds,
            String scopeType,
            long scopeKey,
            Long systemId,
            Long tenantId
    ) {
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return roleMapper.selectList(Wrappers.<Role>lambdaQuery()
                .in(Role::getId, roleIds)
                .eq(Role::getScopeType, scopeType)
                .eq(Role::getScopeKey, scopeKey)
                .eq(systemId != null, Role::getSystemId, systemId)
                .eq(Role::getStatus, "ACTIVE")
                .and(tenantId != null, query -> query.isNull(Role::getTenantId).or().eq(Role::getTenantId, tenantId)));
    }

    private AuthorizationSnapshot snapshot(long epoch, List<Role> roles) {
        if (roles.isEmpty()) {
            return new AuthorizationSnapshot(epoch, Set.of(), List.of(), List.of());
        }
        var roleIds = roles.stream().map(Role::getId).toList();
        var links = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .in(RolePermission::getRoleId, roleIds));
        var permissionIds = links.stream().map(RolePermission::getPermissionId).distinct().toList();
        var permissions = permissionIds.isEmpty() ? List.<Permission>of() : permissionMapper.selectByIds(permissionIds);
        var permissionById = new HashMap<Long, Permission>();
        permissions.stream()
                .filter(permission -> "ACTIVE".equals(permission.getStatus()))
                .forEach(permission -> permissionById.put(permission.getId(), permission));
        var allowed = new LinkedHashSet<String>();
        var denied = new HashSet<String>();
        for (var link : links) {
            var permission = permissionById.get(link.getPermissionId());
            if (permission == null) {
                continue;
            }
            if ("DENY".equals(link.getEffect())) {
                denied.add(permission.getPermissionCode());
            } else {
                allowed.add(permission.getPermissionCode());
            }
        }
        allowed.removeAll(denied);

        var roleSources = roles.stream().map(role -> new AuthorizationSnapshot.RoleSource(
                Long.toString(role.getId()),
                role.getRoleCode(),
                role.getName(),
                Long.toString(role.getPublishedVersion())
        )).toList();
        var scopes = new ArrayList<AuthorizationSnapshot.DataScopeSource>();
        var scopeIds = roles.stream().map(Role::getDataScopeId).filter(java.util.Objects::nonNull).distinct().toList();
        var scopeById = new HashMap<Long, DataScope>();
        if (!scopeIds.isEmpty()) {
            dataScopeMapper.selectByIds(scopeIds).stream()
                    .filter(scope -> "ACTIVE".equals(scope.getStatus()))
                    .forEach(scope -> scopeById.put(scope.getId(), scope));
        }
        for (var role : roles) {
            var scope = scopeById.get(role.getDataScopeId());
            if (scope != null) {
                scopes.add(new AuthorizationSnapshot.DataScopeSource(
                        Long.toString(role.getId()),
                        Long.toString(scope.getId()),
                        scope.getScopeCode(),
                        scope.getScopeKind()
                ));
            }
        }
        return new AuthorizationSnapshot(epoch, allowed, roleSources, scopes);
    }
}
