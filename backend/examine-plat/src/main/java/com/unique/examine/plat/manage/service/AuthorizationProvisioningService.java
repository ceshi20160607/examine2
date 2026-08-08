package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.AuthzVersion;
import com.unique.examine.plat.base.entity.DataScope;
import com.unique.examine.plat.base.entity.MemberRole;
import com.unique.examine.plat.base.entity.MemberTenant;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RoleDraft;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatAccountRoleMapper;
import com.unique.examine.plat.base.mapper.PlatAuthzVersionMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeMapper;
import com.unique.examine.plat.base.mapper.PlatMemberRoleMapper;
import com.unique.examine.plat.base.mapper.PlatMemberTenantMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleDraftMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class AuthorizationProvisioningService {
    private static final String PLATFORM_PROVISION_LOCK = "examine2:platform-authorization-provision";
    private final PlatRoleMapper roleMapper;
    private final PlatPermissionMapper permissionMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatMemberRoleMapper memberRoleMapper;
    private final PlatAccountRoleMapper accountRoleMapper;
    private final PlatMemberTenantMapper memberTenantMapper;
    private final PlatDataScopeMapper dataScopeMapper;
    private final PlatAuthzVersionMapper authzVersionMapper;
    private final PlatRoleDraftMapper roleDraftMapper;
    private final AuthzEpochService epochService;
    private final IdService idService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    public AuthorizationProvisioningService(
            PlatRoleMapper roleMapper,
            PlatPermissionMapper permissionMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatMemberRoleMapper memberRoleMapper,
            PlatAccountRoleMapper accountRoleMapper,
            PlatMemberTenantMapper memberTenantMapper,
            PlatDataScopeMapper dataScopeMapper,
            PlatAuthzVersionMapper authzVersionMapper,
            PlatRoleDraftMapper roleDraftMapper,
            AuthzEpochService epochService,
            IdService idService,
            ObjectMapper objectMapper,
            JdbcTemplate jdbcTemplate
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.memberTenantMapper = memberTenantMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.authzVersionMapper = authzVersionMapper;
        this.roleDraftMapper = roleDraftMapper;
        this.epochService = epochService;
        this.idService = idService;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void provisionPlatformMember(long accountId, LocalDateTime now) {
        withPlatformProvisionLock(() -> provisionPlatformMemberLocked(accountId, now));
    }

    private void provisionPlatformMemberLocked(long accountId, LocalDateTime now) {
        epochService.ensurePlatform(accountId);
        var role = findRole("PLATFORM", 0L, "platform_member");
        if (role == null) {
            role = createRole(
                    "PLATFORM", 0L, null, "platform_member", "平台成员", "MEMBER",
                    List.of(PermissionCatalog.require("platform.runtime.access")), accountId, now
            );
        }
        ensureAccountRole(accountId, role.getId(), accountId, now);
    }

    @Transactional
    public void provisionPlatformRoot(long accountId, LocalDateTime now) {
        withPlatformProvisionLock(() -> provisionPlatformRootLocked(accountId, now));
    }

    private void provisionPlatformRootLocked(long accountId, LocalDateTime now) {
        epochService.ensurePlatform(accountId);
        var role = findRole("PLATFORM", 0L, "platform_root");
        if (role == null) {
            role = createRole(
                    "PLATFORM", 0L, null, "platform_root", "平台超级管理员", "ROOT",
                    PermissionCatalog.PLATFORM, accountId, now
            );
        }
        ensureAccountRole(accountId, role.getId(), accountId, now);
    }

    private void withPlatformProvisionLock(Runnable action) {
        var acquired = jdbcTemplate.queryForObject(
                "SELECT GET_LOCK(?, 15)", Integer.class, PLATFORM_PROVISION_LOCK
        );
        if (!Integer.valueOf(1).equals(acquired)) {
            throw new BusinessException(
                    "AUTHZ_PROVISION_BUSY",
                    "平台授权初始化繁忙，请稍后重试",
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }
        try {
            action.run();
        } finally {
            jdbcTemplate.queryForObject(
                    "SELECT RELEASE_LOCK(?)", Integer.class, PLATFORM_PROVISION_LOCK
            );
        }
    }

    @Transactional
    public void provisionSystemOwner(
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long roleId,
            LocalDateTime now
    ) {
        var role = createRole(
                "SYSTEM", systemId, systemId, null, roleId,
                "system_owner", "系统所有者", "ROOT", PermissionCatalog.SYSTEM, accountId, now
        );

        var tenantAccess = new MemberTenant();
        tenantAccess.setId(idService.nextId());
        tenantAccess.setSystemId(systemId);
        tenantAccess.setMemberId(memberId);
        tenantAccess.setTenantId(tenantId);
        tenantAccess.setStatus("ACTIVE");
        tenantAccess.setGrantedAt(now);
        tenantAccess.setGrantedBy(accountId);
        tenantAccess.setCreatedAt(now);
        tenantAccess.setCreatedBy(accountId);
        tenantAccess.setUpdatedAt(now);
        tenantAccess.setUpdatedBy(accountId);
        tenantAccess.setVersion(0L);
        memberTenantMapper.insert(tenantAccess);

        var memberRole = new MemberRole();
        memberRole.setId(idService.nextId());
        memberRole.setSystemId(systemId);
        memberRole.setMemberId(memberId);
        memberRole.setRoleId(role.getId());
        memberRole.setValidFrom(now);
        memberRole.setCreatedAt(now);
        memberRole.setCreatedBy(accountId);
        memberRoleMapper.insert(memberRole);
        epochService.ensureSystem(systemId, accountId);
    }

    private Role createRole(
            String scopeType,
            long scopeKey,
            Long systemId,
            String code,
            String name,
            String roleType,
            List<PermissionCatalog.Definition> definitions,
            long actorAccountId,
            LocalDateTime now
    ) {
        return createRole(
                scopeType, scopeKey, systemId, null, idService.nextId(), code, name, roleType,
                definitions, actorAccountId, now
        );
    }

    private Role createRole(
            String scopeType,
            long scopeKey,
            Long systemId,
            Long tenantId,
            long roleId,
            String code,
            String name,
            String roleType,
            List<PermissionCatalog.Definition> definitions,
            long actorAccountId,
            LocalDateTime now
    ) {
        var dataScope = new DataScope();
        dataScope.setId(idService.nextId());
        dataScope.setScopeType(scopeType);
        dataScope.setScopeKey(scopeKey);
        dataScope.setSystemId(systemId);
        dataScope.setTenantId(tenantId);
        dataScope.setScopeCode(code + "_all");
        dataScope.setName(name + "全部数据");
        dataScope.setScopeKind("ALL");
        dataScope.setIsBuiltin(true);
        dataScope.setStatus("ACTIVE");
        dataScope.setCreatedAt(now);
        dataScope.setCreatedBy(actorAccountId);
        dataScope.setUpdatedAt(now);
        dataScope.setUpdatedBy(actorAccountId);
        dataScope.setVersion(0L);
        dataScopeMapper.insert(dataScope);

        var role = new Role();
        role.setId(roleId);
        role.setScopeType(scopeType);
        role.setScopeKey(scopeKey);
        role.setSystemId(systemId);
        role.setTenantId(tenantId);
        role.setRoleCode(code);
        role.setName(name);
        role.setRoleType(roleType);
        role.setStatus("ACTIVE");
        role.setPermissionVersion(1L);
        role.setDataScopeId(dataScope.getId());
        role.setIsBuiltin(true);
        role.setPublishedVersion(1L);
        role.setCreatedAt(now);
        role.setCreatedBy(actorAccountId);
        role.setUpdatedAt(now);
        role.setUpdatedBy(actorAccountId);
        role.setVersion(0L);
        roleMapper.insert(role);

        var grants = new ArrayList<PermissionGrant>();
        for (var definition : definitions) {
            var permission = findPermission(scopeType, scopeKey, definition.code());
            if (permission == null) {
                permission = createPermission(scopeType, scopeKey, systemId, definition, actorAccountId, now);
            }
            var link = new RolePermission();
            link.setId(idService.nextId());
            link.setScopeType(scopeType);
            link.setScopeKey(scopeKey);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            link.setEffect("ALLOW");
            link.setCreatedAt(now);
            link.setCreatedBy(actorAccountId);
            rolePermissionMapper.insert(link);
            grants.add(new PermissionGrant(permission.getId(), permission.getPermissionCode(), "ALLOW"));
        }
        createPublishedDocuments(role, dataScope, grants, actorAccountId, now);
        return role;
    }

    private Permission createPermission(
            String scopeType,
            long scopeKey,
            Long systemId,
            PermissionCatalog.Definition definition,
            long actorAccountId,
            LocalDateTime now
    ) {
        var permission = new Permission();
        permission.setId(idService.nextId());
        permission.setScopeType(scopeType);
        permission.setScopeKey(scopeKey);
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
        return permission;
    }

    private void ensureAccountRole(long accountId, long roleId, long actorAccountId, LocalDateTime now) {
        var count = accountRoleMapper.selectCount(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getAccountId, accountId)
                .eq(AccountRole::getRoleId, roleId));
        if (count > 0) {
            return;
        }
        var binding = new AccountRole();
        binding.setId(idService.nextId());
        binding.setScopeType("PLATFORM");
        binding.setScopeKey(0L);
        binding.setAccountId(accountId);
        binding.setRoleId(roleId);
        binding.setValidFrom(now);
        binding.setCreatedAt(now);
        binding.setCreatedBy(actorAccountId);
        accountRoleMapper.insert(binding);
    }

    private void createPublishedDocuments(
            Role role,
            DataScope dataScope,
            List<PermissionGrant> grants,
            long actorAccountId,
            LocalDateTime now
    ) {
        var document = new LinkedHashMap<String, Object>();
        document.put("roleId", Long.toString(role.getId()));
        document.put("roleCode", role.getRoleCode());
        document.put("scopeType", role.getScopeType());
        document.put("scopeKey", Long.toString(role.getScopeKey()));
        document.put("dataScope", java.util.Map.of(
                "id", Long.toString(dataScope.getId()),
                "kind", dataScope.getScopeKind()
        ));
        document.put("permissions", grants.stream().map(grant -> java.util.Map.of(
                "permissionId", Long.toString(grant.permissionId()),
                "code", grant.code(),
                "effect", grant.effect()
        )).toList());
        var json = write(document);
        var checksum = sha256(json);

        var version = new AuthzVersion();
        version.setId(idService.nextId());
        version.setScopeType(role.getScopeType());
        version.setScopeKey(role.getScopeKey());
        version.setSystemId(role.getSystemId());
        version.setRoleId(role.getId());
        version.setVersionNo(1L);
        version.setSnapshotJson(json);
        version.setChecksum(checksum);
        version.setPublishedAt(now);
        version.setPublishedBy(actorAccountId);
        version.setCreatedAt(now);
        authzVersionMapper.insert(version);

        var draft = new RoleDraft();
        draft.setId(idService.nextId());
        draft.setScopeType(role.getScopeType());
        draft.setScopeKey(role.getScopeKey());
        draft.setSystemId(role.getSystemId());
        draft.setRoleId(role.getId());
        draft.setDraftVersion(1L);
        draft.setBasePublishedVersion(1L);
        draft.setStatus("PUBLISHED");
        draft.setDraftJson(json);
        draft.setCheckResultJson("{\"valid\":true,\"source\":\"INITIAL_PROVISION\"}");
        draft.setChecksum(checksum);
        draft.setCheckedAt(now);
        draft.setCheckedBy(actorAccountId);
        draft.setPublishedAt(now);
        draft.setPublishedBy(actorAccountId);
        draft.setCreatedAt(now);
        draft.setCreatedBy(actorAccountId);
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(actorAccountId);
        draft.setVersion(0L);
        roleDraftMapper.insert(draft);
    }

    private Role findRole(String scopeType, long scopeKey, String code) {
        return roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, scopeType)
                .eq(Role::getScopeKey, scopeKey)
                .eq(Role::getRoleCode, code));
    }

    private Permission findPermission(String scopeType, long scopeKey, String code) {
        return permissionMapper.selectOne(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, scopeType)
                .eq(Permission::getScopeKey, scopeKey)
                .eq(Permission::getPermissionCode, code));
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize authorization document", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record PermissionGrant(long permissionId, String code, String effect) {
    }
}
