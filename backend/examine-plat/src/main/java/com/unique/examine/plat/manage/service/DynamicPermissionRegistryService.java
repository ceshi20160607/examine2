package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.unique.examine.core.api.DynamicPermissionFacade;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class DynamicPermissionRegistryService implements DynamicPermissionFacade {
    private static final Set<String> RESOURCE_TYPES = Set.of("MENU", "ACTION", "FIELD", "DATA");
    private static final Set<String> STATIC_PERMISSION_CODES = PermissionCatalog.SYSTEM.stream()
            .map(PermissionCatalog.Definition::code)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    private final PlatPermissionMapper permissionMapper;
    private final PlatRoleMapper roleMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final AuthzEpochService epochService;
    private final IdService idService;

    public DynamicPermissionRegistryService(
            PlatPermissionMapper permissionMapper,
            PlatRoleMapper roleMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            AuthzEpochService epochService,
            IdService idService
    ) {
        this.permissionMapper = permissionMapper;
        this.roleMapper = roleMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.epochService = epochService;
        this.idService = idService;
    }

    @Override
    @Transactional
    public SyncResult synchronizeSystem(
            long systemId,
            long actorAccountId,
            String namespace,
            List<Definition> definitions
    ) {
        var normalizedNamespace = normalizeNamespace(namespace);
        var desired = normalizeDefinitions(normalizedNamespace, definitions);
        var existing = permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "SYSTEM")
                .eq(Permission::getScopeKey, systemId)
                .eq(Permission::getSystemId, systemId)
                .likeRight(Permission::getPermissionCode, normalizedNamespace));
        var byCode = new LinkedHashMap<String, Permission>();
        existing.forEach(permission -> byCode.put(permission.getPermissionCode(), permission));

        var now = LocalDateTime.now();
        var changed = false;
        for (var definition : desired.values()) {
            var permission = byCode.get(definition.code());
            if (permission == null) {
                permission = new Permission();
                permission.setId(idService.nextId());
                permission.setScopeType("SYSTEM");
                permission.setScopeKey(systemId);
                permission.setSystemId(systemId);
                permission.setPermissionCode(definition.code());
                permission.setName(definition.name());
                permission.setResourceType(definition.resourceType());
                permission.setStatus("ACTIVE");
                permission.setCreatedAt(now);
                permission.setCreatedBy(actorAccountId);
                permission.setUpdatedAt(now);
                permission.setUpdatedBy(actorAccountId);
                permission.setVersion(0L);
                permissionMapper.insert(permission);
                byCode.put(permission.getPermissionCode(), permission);
                changed = true;
            } else if (!definition.name().equals(permission.getName())
                    || !definition.resourceType().equals(permission.getResourceType())
                    || !"ACTIVE".equals(permission.getStatus())) {
                permission.setName(definition.name());
                permission.setResourceType(definition.resourceType());
                permission.setStatus("ACTIVE");
                permission.setUpdatedAt(now);
                permission.setUpdatedBy(actorAccountId);
                if (permissionMapper.updateById(permission) != 1) {
                    throw SystemAdminMutationSupport.versionConflict();
                }
                changed = true;
            }
        }

        for (var permission : existing) {
            if (!desired.containsKey(permission.getPermissionCode())
                    && !isStaticPermission(permission.getPermissionCode())
                    && "ACTIVE".equals(permission.getStatus())) {
                permission.setStatus("DISABLED");
                permission.setUpdatedAt(now);
                permission.setUpdatedBy(actorAccountId);
                if (permissionMapper.updateById(permission) != 1) {
                    throw SystemAdminMutationSupport.versionConflict();
                }
                changed = true;
            }
        }

        var roots = roleMapper.selectList(Wrappers.<com.unique.examine.plat.base.entity.Role>lambdaQuery()
                .eq(com.unique.examine.plat.base.entity.Role::getScopeType, "SYSTEM")
                .eq(com.unique.examine.plat.base.entity.Role::getScopeKey, systemId)
                .eq(com.unique.examine.plat.base.entity.Role::getSystemId, systemId)
                .eq(com.unique.examine.plat.base.entity.Role::getRoleType, "ROOT")
                .eq(com.unique.examine.plat.base.entity.Role::getStatus, "ACTIVE")
                .isNull(com.unique.examine.plat.base.entity.Role::getDeletedAt));
        for (var root : roots) {
            for (var definition : desired.values()) {
                var permission = byCode.get(definition.code());
                var count = rolePermissionMapper.selectCount(Wrappers.<RolePermission>lambdaQuery()
                        .eq(RolePermission::getScopeType, "SYSTEM")
                        .eq(RolePermission::getScopeKey, systemId)
                        .eq(RolePermission::getRoleId, root.getId())
                        .eq(RolePermission::getPermissionId, permission.getId()));
                if (count == 0) {
                    var link = new RolePermission();
                    link.setId(idService.nextId());
                    link.setScopeType("SYSTEM");
                    link.setScopeKey(systemId);
                    link.setRoleId(root.getId());
                    link.setPermissionId(permission.getId());
                    link.setEffect("ALLOW");
                    link.setCreatedAt(now);
                    link.setCreatedBy(actorAccountId);
                    rolePermissionMapper.insert(link);
                    changed = true;
                }
            }
        }

        var epoch = changed
                ? epochService.bumpSystem(systemId, actorAccountId)
                : epochService.currentSystem(systemId);
        var ids = new LinkedHashMap<String, String>();
        desired.keySet().forEach(code -> ids.put(code, Long.toString(byCode.get(code).getId())));
        return new SyncResult(ids, epoch);
    }

    private static String normalizeNamespace(String namespace) {
        if (namespace == null) {
            throw new IllegalArgumentException("Permission namespace is required");
        }
        var value = namespace.trim().toLowerCase(Locale.ROOT);
        if (!value.matches("[a-z][a-z0-9_.]{0,62}\\.")) {
            throw new IllegalArgumentException("Invalid permission namespace: " + namespace);
        }
        return value;
    }

    private static LinkedHashMap<String, Definition> normalizeDefinitions(
            String namespace,
            List<Definition> definitions
    ) {
        var result = new LinkedHashMap<String, Definition>();
        for (var definition : List.copyOf(definitions)) {
            var code = definition.code().trim().toLowerCase(Locale.ROOT);
            var resourceType = definition.resourceType().trim().toUpperCase(Locale.ROOT);
            if (!code.startsWith(namespace) || !code.matches("[a-z][a-z0-9_.]{2,159}")) {
                throw new IllegalArgumentException("Permission code is outside namespace: " + definition.code());
            }
            if (isStaticPermission(code)) {
                throw new IllegalArgumentException("Dynamic permission conflicts with static permission: " + code);
            }
            if (!RESOURCE_TYPES.contains(resourceType)) {
                throw new IllegalArgumentException("Unsupported permission resource type: " + resourceType);
            }
            var normalized = new Definition(code, definition.name().trim(), resourceType);
            if (result.putIfAbsent(code, normalized) != null) {
                throw new IllegalArgumentException("Duplicate dynamic permission code: " + code);
            }
        }
        return result;
    }

    static boolean isStaticPermission(String code) {
        return STATIC_PERMISSION_CODES.contains(code);
    }
}
