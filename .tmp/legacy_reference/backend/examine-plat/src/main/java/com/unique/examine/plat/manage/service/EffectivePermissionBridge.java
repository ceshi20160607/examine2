package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import org.springframework.stereotype.Service;

@Service
public class EffectivePermissionBridge implements EffectivePermissionFacade {
    private final AuthorizationService authorizationService;
    private final PlatRoleMapper roleMapper;

    public EffectivePermissionBridge(AuthorizationService authorizationService, PlatRoleMapper roleMapper) {
        this.authorizationService = authorizationService;
        this.roleMapper = roleMapper;
    }

    @Override
    public Evaluation evaluateSystem(long systemId, long tenantId, long memberId) {
        var snapshot = authorizationService.system(systemId, tenantId, memberId);
        var roleIds = snapshot.roles().stream().map(role -> Long.parseLong(role.id())).toList();
        var root = !roleIds.isEmpty() && roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .in(Role::getId, roleIds)
                .eq(Role::getRoleType, "ROOT")
                .eq(Role::getStatus, "ACTIVE")
                .isNull(Role::getDeletedAt)) > 0;
        return new Evaluation(
                snapshot.epoch(), root, snapshot.permissions(),
                snapshot.roles().stream().map(role -> new RoleSource(
                        role.id(), role.code(), role.name(), role.publishedVersion())).toList(),
                snapshot.dataScopes().stream().map(scope -> new DataScopeSource(
                        scope.roleId(), scope.id(), scope.code(), scope.kind())).toList()
        );
    }
}
