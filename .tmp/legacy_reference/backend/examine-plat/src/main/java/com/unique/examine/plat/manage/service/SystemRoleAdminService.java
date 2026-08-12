package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.AuthzVersion;
import com.unique.examine.plat.base.entity.DataScope;
import com.unique.examine.plat.base.entity.DataScopeTarget;
import com.unique.examine.plat.base.entity.MemberRole;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RoleDraft;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatAuthzVersionMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeTargetMapper;
import com.unique.examine.plat.base.mapper.PlatMemberRoleMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleDraftMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import com.unique.examine.plat.manage.dto.SystemAdminRequests;
import com.unique.examine.plat.manage.vo.SystemAdminViews;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class SystemRoleAdminService {
    private static final TypeReference<Map<String, Object>> JSON_OBJECT = new TypeReference<>() {
    };

    private final PlatRoleMapper roleMapper;
    private final PlatPermissionMapper permissionMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatDataScopeMapper dataScopeMapper;
    private final PlatDataScopeTargetMapper dataScopeTargetMapper;
    private final PlatRoleDraftMapper roleDraftMapper;
    private final PlatAuthzVersionMapper authzVersionMapper;
    private final PlatMemberRoleMapper memberRoleMapper;
    private final IdService idService;
    private final AuthzEpochService epochService;
    private final SystemAdminScopeSupport scopeSupport;
    private final SystemAdminMutationSupport mutationSupport;
    private final ObjectMapper objectMapper;

    public SystemRoleAdminService(
            PlatRoleMapper roleMapper,
            PlatPermissionMapper permissionMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatDataScopeMapper dataScopeMapper,
            PlatDataScopeTargetMapper dataScopeTargetMapper,
            PlatRoleDraftMapper roleDraftMapper,
            PlatAuthzVersionMapper authzVersionMapper,
            PlatMemberRoleMapper memberRoleMapper,
            IdService idService,
            AuthzEpochService epochService,
            SystemAdminScopeSupport scopeSupport,
            SystemAdminMutationSupport mutationSupport,
            ObjectMapper objectMapper
    ) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.dataScopeTargetMapper = dataScopeTargetMapper;
        this.roleDraftMapper = roleDraftMapper;
        this.authzVersionMapper = authzVersionMapper;
        this.memberRoleMapper = memberRoleMapper;
        this.idService = idService;
        this.epochService = epochService;
        this.scopeSupport = scopeSupport;
        this.mutationSupport = mutationSupport;
        this.objectMapper = objectMapper;
    }

    public SystemAdminViews.Page<SystemAdminViews.Role> roles(
            AuthenticatedSession session,
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        var systemWide = scopeSupport.systemWide(session, systemId, "system.role.manage");
        var query = Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .isNull(Role::getDeletedAt)
                .and(!systemWide, nested -> nested.isNull(Role::getTenantId)
                        .or().eq(Role::getTenantId, scopeSupport.tenantId(session)))
                .eq(status != null && !status.isBlank(), Role::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(Role::getName, keyword.trim())
                        .or().like(Role::getRoleCode, keyword.trim()))
                .orderByDesc(Role::getIsBuiltin)
                .orderByAsc(Role::getCreatedAt);
        var result = roleMapper.selectPage(new Page<>(page(page), size(size)), query);
        var items = result.getRecords().stream().map(this::roleView).toList();
        return new SystemAdminViews.Page<>(
                items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal()
        );
    }

    @Transactional
    public SystemAdminViews.Role createRole(
            AuthenticatedSession session,
            long systemId,
            SystemAdminRequests.CreateRole request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutationSupport.idempotent(
                systemId + ":role:create", idempotencyKey, request,
                SystemAdminViews.Role.class,
                () -> createRoleNow(session, systemId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.Role createRoleNow(
            AuthenticatedSession session,
            long systemId,
            SystemAdminRequests.CreateRole request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var systemWide = scopeSupport.systemWide(session, systemId, "system.role.manage");
        var tenantId = systemWide ? null : scopeSupport.tenantId(session);
        var now = LocalDateTime.now();
        var role = new Role();
        role.setId(idService.nextId());
        role.setScopeType("SYSTEM");
        role.setScopeKey(systemId);
        role.setSystemId(systemId);
        role.setTenantId(tenantId);
        role.setRoleCode(request.code().trim().toLowerCase(Locale.ROOT));
        role.setName(request.name().trim());
        role.setRoleType("CUSTOM");
        role.setStatus("ACTIVE");
        role.setPermissionVersion(1L);
        role.setIsBuiltin(false);
        role.setPublishedVersion(0L);
        role.setCreatedAt(now);
        role.setCreatedBy(session.accountId());
        role.setUpdatedAt(now);
        role.setUpdatedBy(session.accountId());
        role.setVersion(0L);
        try {
            roleMapper.insert(role);
        } catch (DataIntegrityViolationException exception) {
            throw SystemAdminMutationSupport.conflict("RESOURCE_CONFLICT", "角色编码已存在");
        }

        var draft = new RoleDraft();
        draft.setId(idService.nextId());
        draft.setScopeType("SYSTEM");
        draft.setScopeKey(systemId);
        draft.setSystemId(systemId);
        draft.setRoleId(role.getId());
        draft.setDraftVersion(1L);
        draft.setBasePublishedVersion(0L);
        draft.setStatus("DRAFT");
        draft.setDraftJson(write(editableDocument(
                request.name().trim(), nullToEmpty(request.description()), List.of(), List.of(), null
        )));
        draft.setCreatedAt(now);
        draft.setCreatedBy(session.accountId());
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        draft.setVersion(0L);
        roleDraftMapper.insert(draft);

        var after = roleView(role);
        mutationSupport.success(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_CREATE", null, after, client
        );
        mutationSupport.outbox(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_CREATED",
                after, idempotencyKey, client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Role saveDraft(
            AuthenticatedSession session,
            long systemId,
            long roleId,
            SystemAdminRequests.SaveRoleDraft request,
            ClientRequest client
    ) {
        var role = requireManageableRole(session, systemId, roleId);
        requireVersion(role.getVersion(), request.version());
        var draft = requireDraft(systemId, roleId);
        var before = roleView(role);
        var permissionCodes = normalizedCodes(request.permissionCodes());
        var deniedPermissionCodes = normalizedCodes(request.deniedPermissionCodes());
        requireDisjointPermissions(permissionCodes, deniedPermissionCodes);
        var dataScopeId = nullableId(request.dataScopeId(), "dataScopeId");
        var now = LocalDateTime.now();

        draft.setDraftVersion(draft.getDraftVersion() + 1);
        draft.setBasePublishedVersion(role.getPublishedVersion());
        draft.setStatus("DRAFT");
        draft.setDraftJson(write(editableDocument(
                request.name().trim(), nullToEmpty(request.description()),
                permissionCodes, deniedPermissionCodes, dataScopeId
        )));
        draft.setCheckResultJson(null);
        draft.setChecksum(null);
        draft.setCheckedAt(null);
        draft.setCheckedBy(null);
        draft.setPublishedAt(null);
        draft.setPublishedBy(null);
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        if (roleDraftMapper.update(draft, Wrappers.<RoleDraft>lambdaUpdate()             .eq(RoleDraft::getId, draft.getId())             .set(RoleDraft::getCheckResultJson, null)             .set(RoleDraft::getChecksum, null)             .set(RoleDraft::getCheckedAt, null)             .set(RoleDraft::getCheckedBy, null)             .set(RoleDraft::getPublishedAt, null)             .set(RoleDraft::getPublishedBy, null)) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        touchRole(role, session.accountId(), now);
        var after = roleView(role);
        mutationSupport.success(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_DRAFT_SAVE", before, after, client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Role checkDraft(
            AuthenticatedSession session,
            long systemId,
            long roleId,
            SystemAdminRequests.DraftCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutationSupport.idempotent(
                systemId + ":role:" + roleId + ":check", idempotencyKey, request,
                SystemAdminViews.Role.class,
                () -> checkDraftNow(session, systemId, roleId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.Role checkDraftNow(
            AuthenticatedSession session,
            long systemId,
            long roleId,
            SystemAdminRequests.DraftCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var role = requireManageableRole(session, systemId, roleId);
        requireVersion(role.getVersion(), request.version());
        var draft = requireDraft(systemId, roleId);
        if (!"DRAFT".equals(draft.getStatus())) {
            throw SystemAdminMutationSupport.invalidState("只有草稿状态可以执行检查");
        }
        if (!Objects.equals(draft.getBasePublishedVersion(), role.getPublishedVersion())) {
            throw SystemAdminMutationSupport.conflict("DRAFT_BASE_STALE", "草稿基线已过期，请重新保存后检查");
        }
        var validated = validateDocument(role, editableFrom(draft));
        protectBuiltinRole(role, validated);
        var canonical = write(validated.document());
        var checksum = sha256(canonical);
        var now = LocalDateTime.now();
        var before = roleView(role);
        draft.setStatus("CHECKED");
        draft.setDraftJson(canonical);
        draft.setCheckResultJson(write(Map.of(
                "valid", true,
                "permissionCount", validated.allowedPermissions().size() + validated.deniedPermissions().size(),
                "checkedDraftVersion", draft.getDraftVersion()
        )));
        draft.setChecksum(checksum);
        draft.setCheckedAt(now);
        draft.setCheckedBy(session.accountId());
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        if (roleDraftMapper.updateById(draft) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        touchRole(role, session.accountId(), now);
        var after = roleView(role);
        mutationSupport.success(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_DRAFT_CHECK", before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_DRAFT_CHECKED",
                Map.of("roleId", after.id(), "checksum", checksum), idempotencyKey, client
        );
        return after;
    }

    @Transactional
    public SystemAdminViews.Role publishDraft(
            AuthenticatedSession session,
            long systemId,
            long roleId,
            SystemAdminRequests.DraftCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutationSupport.idempotent(
                systemId + ":role:" + roleId + ":publish", idempotencyKey, request,
                SystemAdminViews.Role.class,
                () -> publishDraftNow(session, systemId, roleId, request, idempotencyKey, client)
        );
    }

    private SystemAdminViews.Role publishDraftNow(
            AuthenticatedSession session,
            long systemId,
            long roleId,
            SystemAdminRequests.DraftCommand request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var role = requireManageableRole(session, systemId, roleId);
        requireVersion(role.getVersion(), request.version());
        var draft = requireDraft(systemId, roleId);
        if (!"CHECKED".equals(draft.getStatus())) {
            throw SystemAdminMutationSupport.invalidState("角色草稿必须先检查通过才能发布");
        }
        if (!Objects.equals(draft.getBasePublishedVersion(), role.getPublishedVersion())) {
            throw SystemAdminMutationSupport.conflict("DRAFT_BASE_STALE", "已发布版本已变化，请重新检查草稿");
        }
        var validated = validateDocument(role, editableFrom(draft));
        protectBuiltinRole(role, validated);
        var canonical = write(validated.document());
        var checksum = sha256(canonical);
        if (!Objects.equals(checksum, draft.getChecksum())) {
            throw SystemAdminMutationSupport.conflict("DRAFT_CHECKSUM_MISMATCH", "草稿内容已变化，请重新检查");
        }

        var before = roleView(role);
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "SYSTEM")
                .eq(RolePermission::getScopeKey, systemId)
                .eq(RolePermission::getRoleId, roleId));
        var now = LocalDateTime.now();
        for (var permission : validated.allowedPermissions()) {
            var link = new RolePermission();
            link.setId(idService.nextId());
            link.setScopeType("SYSTEM");
            link.setScopeKey(systemId);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            link.setEffect("ALLOW");
            link.setCreatedAt(now);
            link.setCreatedBy(session.accountId());
            rolePermissionMapper.insert(link);
        }
        for (var permission : validated.deniedPermissions()) {
            var link = new RolePermission();
            link.setId(idService.nextId());
            link.setScopeType("SYSTEM");
            link.setScopeKey(systemId);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            link.setEffect("DENY");
            link.setCreatedAt(now);
            link.setCreatedBy(session.accountId());
            rolePermissionMapper.insert(link);
        }

        var nextPublishedVersion = role.getPublishedVersion() + 1;
        role.setName(validated.name());
        role.setDataScopeId(validated.dataScope() == null ? null : validated.dataScope().getId());
        role.setPermissionVersion(nextPublishedVersion);
        role.setPublishedVersion(nextPublishedVersion);
        role.setUpdatedAt(now);
        role.setUpdatedBy(session.accountId());
        if (roleMapper.updateById(role) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }

        var version = new AuthzVersion();
        version.setId(idService.nextId());
        version.setScopeType("SYSTEM");
        version.setScopeKey(systemId);
        version.setSystemId(systemId);
        version.setRoleId(roleId);
        version.setVersionNo(nextPublishedVersion);
        version.setSnapshotJson(canonical);
        version.setChecksum(checksum);
        version.setPublishedAt(now);
        version.setPublishedBy(session.accountId());
        version.setCreatedAt(now);
        authzVersionMapper.insert(version);

        draft.setStatus("PUBLISHED");
        draft.setPublishedAt(now);
        draft.setPublishedBy(session.accountId());
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        if (roleDraftMapper.updateById(draft) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
        epochService.bumpSystem(systemId, session.accountId());
        var after = roleView(role);
        mutationSupport.success(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_DRAFT_PUBLISH", before, after, client
        );
        mutationSupport.outbox(
                session, systemId, "ROLE", after.id(), "SYSTEM_ROLE_PUBLISHED",
                Map.of(
                        "roleId", after.id(),
                        "publishedVersion", Long.toString(nextPublishedVersion),
                        "checksum", checksum
                ), idempotencyKey, client
        );
        return after;
    }

    public SystemAdminViews.Page<SystemAdminViews.Permission> permissions(
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        var query = Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "SYSTEM")
                .eq(Permission::getScopeKey, systemId)
                .eq(Permission::getSystemId, systemId)
                .eq(status != null && !status.isBlank(), Permission::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(Permission::getName, keyword.trim())
                        .or().like(Permission::getPermissionCode, keyword.trim()))
                .orderByAsc(Permission::getResourceType)
                .orderByAsc(Permission::getPermissionCode);
        var result = permissionMapper.selectPage(new Page<>(page(page), size(size)), query);
        var items = result.getRecords().stream().map(permission -> new SystemAdminViews.Permission(
                Long.toString(permission.getId()), permission.getPermissionCode(), permission.getName(),
                permission.getResourceType(), null
        )).toList();
        return new SystemAdminViews.Page<>(
                items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal()
        );
    }

    public SystemAdminViews.Page<SystemAdminViews.DataScope> dataScopes(
            AuthenticatedSession session,
            long systemId,
            int page,
            int size,
            String keyword,
            String status
    ) {
        var systemWide = scopeSupport.systemWide(session, systemId, "system.role.manage");
        var query = Wrappers.<DataScope>lambdaQuery()
                .eq(DataScope::getScopeType, "SYSTEM")
                .eq(DataScope::getScopeKey, systemId)
                .eq(DataScope::getSystemId, systemId)
                .isNull(DataScope::getDeletedAt)
                .and(!systemWide, nested -> nested.isNull(DataScope::getTenantId)
                        .or().eq(DataScope::getTenantId, scopeSupport.tenantId(session)))
                .eq(status != null && !status.isBlank(), DataScope::getStatus, status)
                .and(keyword != null && !keyword.isBlank(), nested -> nested
                        .like(DataScope::getName, keyword.trim())
                        .or().like(DataScope::getScopeCode, keyword.trim()))
                .orderByDesc(DataScope::getIsBuiltin)
                .orderByAsc(DataScope::getCreatedAt);
        var result = dataScopeMapper.selectPage(new Page<>(page(page), size(size)), query);
        var items = result.getRecords().stream().map(this::dataScopeView).toList();
        return new SystemAdminViews.Page<>(
                items, (int) result.getCurrent(), (int) result.getSize(), result.getTotal()
        );
    }

    private ValidatedDraft validateDocument(Role role, EditableDraft editable) {
        var allowedCodes = normalizedCodes(editable.permissionCodes());
        var deniedCodes = normalizedCodes(editable.deniedPermissionCodes());
        requireDisjointPermissions(allowedCodes, deniedCodes);
        var allowedPermissions = requirePermissions(role, allowedCodes);
        var deniedPermissions = requirePermissions(role, deniedCodes);
        var dataScope = editable.dataScopeId() == null ? null : requireDataScope(role, editable.dataScopeId());

        var document = new LinkedHashMap<String, Object>();
        document.put("roleId", Long.toString(role.getId()));
        document.put("roleCode", role.getRoleCode());
        document.put("scopeType", "SYSTEM");
        document.put("scopeKey", Long.toString(role.getSystemId()));
        document.put("name", editable.name());
        document.put("description", editable.description());
        document.put("dataScope", dataScope == null ? null : Map.of(
                "id", Long.toString(dataScope.getId()),
                "kind", dataScope.getScopeKind()
        ));
        var grants = new ArrayList<Map<String, String>>();
        grants.addAll(allowedPermissions.stream().map(permission -> Map.of(
                "permissionId", Long.toString(permission.getId()),
                "code", permission.getPermissionCode(),
                "effect", "ALLOW"
        )).toList());
        grants.addAll(deniedPermissions.stream().map(permission -> Map.of(
                "permissionId", Long.toString(permission.getId()),
                "code", permission.getPermissionCode(),
                "effect", "DENY"
        )).toList());
        document.put("permissions", grants);
        return new ValidatedDraft(
                editable.name(), editable.description(), allowedPermissions, deniedPermissions, dataScope, document
        );
    }

    private List<Permission> requirePermissions(Role role, List<String> codes) {
        var permissions = codes.isEmpty() ? List.<Permission>of() : permissionMapper.selectList(
                Wrappers.<Permission>lambdaQuery()
                        .eq(Permission::getScopeType, "SYSTEM")
                        .eq(Permission::getScopeKey, role.getSystemId())
                        .eq(Permission::getSystemId, role.getSystemId())
                        .eq(Permission::getStatus, "ACTIVE")
                        .in(Permission::getPermissionCode, codes)
        );
        var byCode = permissions.stream().collect(Collectors.toMap(
                Permission::getPermissionCode, Function.identity()
        ));
        if (byCode.size() != codes.size()) {
            var missing = codes.stream().filter(code -> !byCode.containsKey(code)).toList();
            throw new BusinessException(
                    "ROLE_DRAFT_INVALID_PERMISSION",
                    "草稿包含无效权限: " + String.join(", ", missing),
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        return codes.stream().map(byCode::get).toList();
    }

    private void protectBuiltinRole(Role role, ValidatedDraft draft) {
        if (!"ROOT".equals(role.getRoleType())) {
            return;
        }
        var selected = draft.allowedPermissions().stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toSet());
        var required = activeSystemPermissionCodes(role.getSystemId());
        if (!selected.containsAll(required)
                || !draft.deniedPermissions().isEmpty()
                || draft.dataScope() == null
                || !"ALL".equals(draft.dataScope().getScopeKind())) {
            throw new BusinessException(
                    "BUILTIN_ROLE_PROTECTED",
                    "系统所有者角色必须保留全部系统权限和 ALL 数据范围",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private DataScope requireDataScope(Role role, long dataScopeId) {
        var scope = dataScopeMapper.selectOne(Wrappers.<DataScope>lambdaQuery()
                .eq(DataScope::getId, dataScopeId)
                .eq(DataScope::getScopeType, "SYSTEM")
                .eq(DataScope::getScopeKey, role.getSystemId())
                .eq(DataScope::getSystemId, role.getSystemId())
                .eq(DataScope::getStatus, "ACTIVE")
                .isNull(DataScope::getDeletedAt));
        if (scope == null || !Objects.equals(scope.getTenantId(), role.getTenantId())) {
            throw new BusinessException(
                    "ROLE_DRAFT_INVALID_DATA_SCOPE",
                    "数据范围不存在或与角色作用域不一致",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
        return scope;
    }

    private Role requireManageableRole(AuthenticatedSession session, long systemId, long roleId) {
        var role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getId, roleId)
                .eq(Role::getScopeType, "SYSTEM")
                .eq(Role::getScopeKey, systemId)
                .eq(Role::getSystemId, systemId)
                .isNull(Role::getDeletedAt));
        if (role == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        var systemWide = scopeSupport.systemWide(session, systemId, "system.role.manage");
        if (role.getTenantId() == null && !systemWide) {
            throw SystemAdminMutationSupport.notFound();
        }
        if (role.getTenantId() != null) {
            scopeSupport.requireTenant(session, systemId, role.getTenantId(), "system.role.manage");
        }
        return role;
    }

    private RoleDraft requireDraft(long systemId, long roleId) {
        var draft = roleDraftMapper.selectOne(Wrappers.<RoleDraft>lambdaQuery()
                .eq(RoleDraft::getScopeType, "SYSTEM")
                .eq(RoleDraft::getScopeKey, systemId)
                .eq(RoleDraft::getSystemId, systemId)
                .eq(RoleDraft::getRoleId, roleId));
        if (draft == null) {
            throw SystemAdminMutationSupport.notFound();
        }
        return draft;
    }

    private void touchRole(Role role, long accountId, LocalDateTime now) {
        role.setUpdatedAt(now);
        role.setUpdatedBy(accountId);
        if (roleMapper.updateById(role) != 1) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private SystemAdminViews.Role roleView(Role role) {
        var draft = roleDraftMapper.selectOne(Wrappers.<RoleDraft>lambdaQuery()
                .eq(RoleDraft::getScopeType, "SYSTEM")
                .eq(RoleDraft::getScopeKey, role.getSystemId())
                .eq(RoleDraft::getRoleId, role.getId()));
        var editable = draft == null ? null : editableFrom(draft);
        var permissions = editable == null
                ? publishedPermissionCodes(role, "ALLOW")
                : editable.permissionCodes();
        var deniedPermissions = editable == null
                ? publishedPermissionCodes(role, "DENY")
                : editable.deniedPermissionCodes();
        var activePermissionCodes = activeSystemPermissionCodes(role.getSystemId());
        permissions = onlyActive(permissions, activePermissionCodes);
        deniedPermissions = onlyActive(deniedPermissions, activePermissionCodes);
        var dataScopeId = editable == null ? role.getDataScopeId() : editable.dataScopeId();
        if ("ROOT".equals(role.getRoleType())) {
            permissions = new ArrayList<>(activePermissionCodes);
            deniedPermissions = List.of();
        }
        var memberCount = memberRoleMapper.selectCount(Wrappers.<MemberRole>lambdaQuery()
                .eq(MemberRole::getSystemId, role.getSystemId())
                .eq(MemberRole::getRoleId, role.getId())
                .le(MemberRole::getValidFrom, LocalDateTime.now())
                .and(query -> query.isNull(MemberRole::getValidUntil)
                        .or().gt(MemberRole::getValidUntil, LocalDateTime.now())));
        return new SystemAdminViews.Role(
                Long.toString(role.getId()),
                role.getRoleCode(),
                editable == null ? role.getName() : editable.name(),
                editable == null ? "" : editable.description(),
                role.getStatus(),
                Boolean.TRUE.equals(role.getIsBuiltin()),
                memberCount,
                permissions,
                deniedPermissions,
                string(dataScopeId),
                draft == null ? "PUBLISHED" : draft.getStatus(),
                role.getPublishedVersion() == null || role.getPublishedVersion() == 0
                        ? null : Long.toString(role.getPublishedVersion()),
                Long.toString(role.getVersion())
        );
    }

    private List<String> publishedPermissionCodes(Role role, String effect) {
        var links = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "SYSTEM")
                .eq(RolePermission::getScopeKey, role.getSystemId())
                .eq(RolePermission::getRoleId, role.getId())
                .eq(RolePermission::getEffect, effect));
        if (links.isEmpty()) {
            return List.of();
        }
        var byId = permissionMapper.selectByIds(
                links.stream().map(RolePermission::getPermissionId).distinct().toList()
        ).stream().collect(Collectors.toMap(Permission::getId, Function.identity()));
        return links.stream()
                .map(link -> byId.get(link.getPermissionId()))
                .filter(Objects::nonNull)
                .filter(permission -> "ACTIVE".equals(permission.getStatus()))
                .map(Permission::getPermissionCode)
                .distinct()
                .sorted()
                .toList();
    }

    static List<String> onlyActive(List<String> permissionCodes, java.util.Set<String> activePermissionCodes) {
        return permissionCodes.stream().filter(activePermissionCodes::contains).toList();
    }

    private LinkedHashSet<String> activeSystemPermissionCodes(long systemId) {
        return permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                        .eq(Permission::getScopeType, "SYSTEM")
                        .eq(Permission::getScopeKey, systemId)
                        .eq(Permission::getSystemId, systemId)
                        .eq(Permission::getStatus, "ACTIVE")
                        .orderByAsc(Permission::getPermissionCode))
                .stream()
                .map(Permission::getPermissionCode)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private SystemAdminViews.DataScope dataScopeView(DataScope scope) {
        var targets = dataScopeTargetMapper.selectList(Wrappers.<DataScopeTarget>lambdaQuery()
                .eq(DataScopeTarget::getScopeType, "SYSTEM")
                .eq(DataScopeTarget::getScopeKey, scope.getSystemId())
                .eq(DataScopeTarget::getDataScopeId, scope.getId())
                .isNull(DataScopeTarget::getDeletedAt));
        var ids = targets.stream().map(target -> {
            if (target.getDepartmentId() != null) {
                return Long.toString(target.getDepartmentId());
            }
            if (target.getMemberId() != null) {
                return Long.toString(target.getMemberId());
            }
            return string(target.getAccountId());
        }).filter(Objects::nonNull).distinct().toList();
        return new SystemAdminViews.DataScope(
                Long.toString(scope.getId()), scope.getName(), scope.getScopeKind(),
                dataScopeDescription(scope), ids, Long.toString(scope.getVersion())
        );
    }

    private String dataScopeDescription(DataScope scope) {
        if ("FIELD_RULE".equals(scope.getScopeKind())) {
            return nullToEmpty(scope.getFieldRuleJson());
        }
        return switch (scope.getScopeKind()) {
            case "ALL" -> "全部数据";
            case "SELF" -> "仅本人数据";
            case "PRIMARY_DEPARTMENT" -> "本人主部门数据";
            case "DEPARTMENT_TREE" -> "指定部门及下级数据";
            case "SELECTED_DEPARTMENTS" -> "指定部门数据";
            case "SELECTED_MEMBERS" -> "指定成员数据";
            default -> "";
        };
    }

    private EditableDraft editableFrom(RoleDraft draft) {
        Map<String, Object> document;
        try {
            document = objectMapper.readValue(draft.getDraftJson(), JSON_OBJECT);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Role draft JSON is invalid", exception);
        }
        var role = roleMapper.selectById(draft.getRoleId());
        var name = stringValue(document.get("name"), role == null ? "" : role.getName());
        var description = stringValue(document.get("description"), "");
        var dataScopeId = parseDocumentId(document.get("dataScopeId"));
        if (dataScopeId == null && document.get("dataScope") instanceof Map<?, ?> scope) {
            dataScopeId = parseDocumentId(scope.get("id"));
        }
        var codes = new ArrayList<String>();
        var deniedCodes = new ArrayList<String>();
        var permissionCodes = document.get("permissionCodes");
        if (permissionCodes instanceof List<?> values) {
            values.forEach(value -> codes.add(String.valueOf(value)));
            var deniedPermissionCodes = document.get("deniedPermissionCodes");
            if (deniedPermissionCodes instanceof List<?> deniedValues) {
                deniedValues.forEach(value -> deniedCodes.add(String.valueOf(value)));
            }
        } else if (document.get("permissions") instanceof List<?> values) {
            for (var value : values) {
                if (value instanceof Map<?, ?> permission && permission.get("code") != null) {
                    var target = "DENY".equals(String.valueOf(permission.get("effect")))
                            ? deniedCodes : codes;
                    target.add(String.valueOf(permission.get("code")));
                }
            }
        }
        return new EditableDraft(
                name, description, normalizedCodes(codes), normalizedCodes(deniedCodes), dataScopeId
        );
    }

    private static Map<String, Object> editableDocument(
            String name,
            String description,
            List<String> permissionCodes,
            List<String> deniedPermissionCodes,
            Long dataScopeId
    ) {
        var document = new LinkedHashMap<String, Object>();
        document.put("name", name);
        document.put("description", description);
        document.put("permissionCodes", permissionCodes);
        document.put("deniedPermissionCodes", deniedPermissionCodes);
        document.put("dataScopeId", string(dataScopeId));
        return document;
    }

    private static List<String> normalizedCodes(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private static void requireDisjointPermissions(List<String> allowed, List<String> denied) {
        if (allowed.stream().anyMatch(denied::contains)) {
            throw new BusinessException(
                    "ROLE_DRAFT_PERMISSION_CONFLICT",
                    "同一权限不能同时配置为允许和拒绝",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private static Long nullableId(String value, String field) {
        return value == null || value.isBlank() ? null : SystemAdminMutationSupport.id(value, field);
    }

    private static Long parseDocumentId(Object value) {
        if (value == null || String.valueOf(value).isBlank() || "null".equals(value)) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new BusinessException(
                    "ROLE_DRAFT_INVALID_DATA_SCOPE", "草稿数据范围格式无效", HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private static void requireVersion(long actual, String expected) {
        if (actual != SystemAdminMutationSupport.version(expected)) {
            throw SystemAdminMutationSupport.versionConflict();
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize role authorization document", exception);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String stringValue(Object value, String fallback) {
        return value == null ? fallback : String.valueOf(value);
    }

    private static String string(Long value) {
        return value == null ? null : Long.toString(value);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int page(int value) {
        return Math.max(value, 1);
    }

    private static int size(int value) {
        return Math.min(Math.max(value, 1), 500);
    }

    private record EditableDraft(
            String name,
            String description,
            List<String> permissionCodes,
            List<String> deniedPermissionCodes,
            Long dataScopeId
    ) {
    }

    private record ValidatedDraft(
            String name,
            String description,
            List<Permission> allowedPermissions,
            List<Permission> deniedPermissions,
            DataScope dataScope,
            Map<String, Object> document
    ) {
    }
}
