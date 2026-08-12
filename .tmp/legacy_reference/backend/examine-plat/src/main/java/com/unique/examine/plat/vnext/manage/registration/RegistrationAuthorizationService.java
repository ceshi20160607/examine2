package com.unique.examine.plat.vnext.manage.registration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.vnext.base.entity.DataScope;
import com.unique.examine.plat.vnext.base.entity.MemberRole;
import com.unique.examine.plat.vnext.base.entity.Permission;
import com.unique.examine.plat.vnext.base.entity.Role;
import com.unique.examine.plat.vnext.base.entity.RolePermission;
import com.unique.examine.plat.vnext.base.service.IVNextPlatDataScopeService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatMemberRoleService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatPermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRolePermissionService;
import com.unique.examine.plat.vnext.base.service.IVNextPlatRoleService;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
final class RegistrationAuthorizationService {
    static final List<String> PERMISSIONS = List.of(
            "system.runtime.access", "system.admin.access"
    );

    private final IVNextPlatDataScopeService dataScopeService;
    private final IVNextPlatRoleService roleService;
    private final IVNextPlatPermissionService permissionService;
    private final IVNextPlatRolePermissionService rolePermissionService;
    private final IVNextPlatMemberRoleService memberRoleService;
    private final IdService idService;

    RegistrationAuthorizationService(
            IVNextPlatDataScopeService dataScopeService,
            IVNextPlatRoleService roleService,
            IVNextPlatPermissionService permissionService,
            IVNextPlatRolePermissionService rolePermissionService,
            IVNextPlatMemberRoleService memberRoleService,
            IdService idService
    ) {
        this.dataScopeService = dataScopeService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.rolePermissionService = rolePermissionService;
        this.memberRoleService = memberRoleService;
        this.idService = idService;
    }

    DataScope createDataScope(long accountId, long systemId, LocalDateTime now) {
        var value = new DataScope();
        value.setId(idService.nextId());
        value.setScopeType("SYSTEM");
        value.setScopeKey(systemId);
        value.setSystemId(systemId);
        value.setTenantId(null);
        value.setScopeCode("system_owner_all");
        value.setName("全系统数据");
        value.setScopeKind("ALL");
        value.setFieldRuleJson("{}");
        value.setIsBuiltin(true);
        value.setStatus("ACTIVE");
        value.setCreatedAt(now);
        value.setCreatedBy(accountId);
        value.setUpdatedAt(now);
        value.setUpdatedBy(accountId);
        value.setVersion(0L);
        require(dataScopeService.save(value), "Data scope persistence");
        return value;
    }

    Role createRole(long accountId, long systemId, long dataScopeId, LocalDateTime now) {
        var value = new Role();
        value.setId(idService.nextId());
        value.setScopeType("SYSTEM");
        value.setScopeKey(systemId);
        value.setSystemId(systemId);
        value.setTenantId(null);
        value.setRoleCode("system_owner");
        value.setName("系统超级管理员");
        value.setRoleType("ROOT");
        value.setStatus("ACTIVE");
        value.setPermissionVersion(1L);
        value.setDataScopeId(dataScopeId);
        value.setIsBuiltin(true);
        value.setPublishedVersion(1L);
        value.setCreatedAt(now);
        value.setCreatedBy(accountId);
        value.setUpdatedAt(now);
        value.setUpdatedBy(accountId);
        value.setVersion(0L);
        require(roleService.save(value), "Role persistence");
        return value;
    }

    void createPermissions(long accountId, long systemId, long roleId, LocalDateTime now) {
        for (var code : PERMISSIONS) {
            var permission = new Permission();
            permission.setId(idService.nextId());
            permission.setScopeType("SYSTEM");
            permission.setScopeKey(systemId);
            permission.setSystemId(systemId);
            permission.setPermissionCode(code);
            permission.setName(code.equals("system.runtime.access") ? "进入系统工作台" : "进入系统管理");
            permission.setResourceType("SHELL");
            permission.setStatus("ACTIVE");
            permission.setCreatedAt(now);
            permission.setCreatedBy(accountId);
            permission.setUpdatedAt(now);
            permission.setUpdatedBy(accountId);
            permission.setVersion(0L);
            require(permissionService.save(permission), "Permission persistence");

            var link = new RolePermission();
            link.setId(idService.nextId());
            link.setScopeType("SYSTEM");
            link.setScopeKey(systemId);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            link.setEffect("ALLOW");
            link.setCreatedAt(now);
            link.setCreatedBy(accountId);
            require(rolePermissionService.save(link), "Role permission persistence");
        }
    }

    void createMemberRole(long accountId, long systemId, long memberId, long roleId, LocalDateTime now) {
        var value = new MemberRole();
        value.setId(idService.nextId());
        value.setSystemId(systemId);
        value.setMemberId(memberId);
        value.setRoleId(roleId);
        value.setTenantId(null);
        value.setValidFrom(now);
        value.setValidUntil(null);
        value.setCreatedAt(now);
        value.setCreatedBy(accountId);
        require(memberRoleService.save(value), "Member role persistence");
    }

    AuthorizationGraph loadActive(RegistrationReceipt receipt) {
        var dataScope = dataScopeService.getById(receipt.dataScopeId());
        var role = roleService.getById(receipt.roleId());
        var memberRole = memberRoleService.getOne(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, receipt.systemId())
                .eq(MemberRole::getMemberId, receipt.memberId())
                .eq(MemberRole::getRoleId, receipt.roleId())
                .isNull(MemberRole::getTenantId), false);
        var grants = rolePermissionService.list(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "SYSTEM")
                .eq(RolePermission::getScopeKey, receipt.systemId())
                .eq(RolePermission::getRoleId, receipt.roleId())
                .eq(RolePermission::getEffect, "ALLOW"));
        var permissionIds = grants.stream().map(RolePermission::getPermissionId).toList();
        var permissions = permissionIds.isEmpty() ? List.<Permission>of()
                : permissionService.listByIds(permissionIds);
        var validPermissions = grants.size() == 2 && permissions.size() == 2
                && permissions.stream().allMatch(permission ->
                "ACTIVE".equals(permission.getStatus())
                        && "SYSTEM".equals(permission.getScopeType())
                        && Objects.equals(permission.getScopeKey(), receipt.systemId())
                        && Objects.equals(permission.getSystemId(), receipt.systemId())
                        && "SHELL".equals(permission.getResourceType()))
                && permissions.stream().map(Permission::getPermissionCode)
                .collect(Collectors.toSet()).equals(Set.copyOf(PERMISSIONS));
        var valid = dataScope != null && role != null && memberRole != null
                && Objects.equals(dataScope.getSystemId(), receipt.systemId())
                && Objects.equals(dataScope.getScopeKey(), receipt.systemId())
                && dataScope.getTenantId() == null
                && "SYSTEM".equals(dataScope.getScopeType())
                && "system_owner_all".equals(dataScope.getScopeCode())
                && "全系统数据".equals(dataScope.getName())
                && "ALL".equals(dataScope.getScopeKind())
                && Boolean.TRUE.equals(dataScope.getIsBuiltin())
                && "ACTIVE".equals(dataScope.getStatus())
                && Objects.equals(role.getSystemId(), receipt.systemId())
                && Objects.equals(role.getScopeKey(), receipt.systemId())
                && role.getTenantId() == null
                && "SYSTEM".equals(role.getScopeType())
                && "system_owner".equals(role.getRoleCode())
                && "系统超级管理员".equals(role.getName())
                && "ROOT".equals(role.getRoleType())
                && "ACTIVE".equals(role.getStatus())
                && Objects.equals(role.getPermissionVersion(), 1L)
                && Objects.equals(role.getPublishedVersion(), 1L)
                && Objects.equals(role.getDataScopeId(), receipt.dataScopeId())
                && Boolean.TRUE.equals(role.getIsBuiltin())
                && validPermissions;
        if (!valid) {
            throw RegistrationErrors.replayMismatch();
        }
        return new AuthorizationGraph(role, dataScope);
    }

    List<RoleSnapshot> roleSnapshot(Role role) {
        return List.of(new RoleSnapshot(
                role.getId(), role.getRoleCode(), role.getName(), role.getPublishedVersion()
        ));
    }

    List<DataScopeSnapshot> dataScopeSnapshot(Role role, DataScope scope) {
        return List.of(new DataScopeSnapshot(
                role.getId(), scope.getId(), scope.getScopeCode(), scope.getScopeKind()
        ));
    }

    private static void require(boolean result, String operation) {
        if (!result) {
            throw new IllegalStateException(operation + " was rejected");
        }
    }

    record AuthorizationGraph(Role role, DataScope dataScope) {
    }

    record RoleSnapshot(long id, String code, String name, long publishedVersion) {
    }

    record DataScopeSnapshot(long roleId, long id, String code, String kind) {
    }
}
