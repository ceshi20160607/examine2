package com.unique.examine.plat.manage.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.plat.api.AuthenticatedSession;
import com.unique.examine.plat.base.entity.AccountRole;
import com.unique.examine.plat.base.entity.AuthzVersion;
import com.unique.examine.plat.base.entity.DataScope;
import com.unique.examine.plat.base.entity.Permission;
import com.unique.examine.plat.base.entity.Role;
import com.unique.examine.plat.base.entity.RoleDraft;
import com.unique.examine.plat.base.entity.RolePermission;
import com.unique.examine.plat.base.mapper.PlatAccountRoleMapper;
import com.unique.examine.plat.base.mapper.PlatAuthzVersionMapper;
import com.unique.examine.plat.base.mapper.PlatDataScopeMapper;
import com.unique.examine.plat.base.mapper.PlatPermissionMapper;
import com.unique.examine.plat.base.mapper.PlatRoleDraftMapper;
import com.unique.examine.plat.base.mapper.PlatRoleMapper;
import com.unique.examine.plat.base.mapper.PlatRolePermissionMapper;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PageResult;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.PermissionView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.RoleCreate;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.RoleDraftInput;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.RoleView;
import com.unique.examine.plat.manage.dto.PlatformAdminModels.VersionInput;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PlatformRoleAdminService {
    private static final Set<String> ROLE_STATUSES = Set.of("ACTIVE", "DISABLED");
    private static final Set<String> PERMISSION_STATUSES = Set.of("ACTIVE", "DISABLED");

    private final PlatRoleMapper roleMapper;
    private final PlatRoleDraftMapper roleDraftMapper;
    private final PlatPermissionMapper permissionMapper;
    private final PlatRolePermissionMapper rolePermissionMapper;
    private final PlatAccountRoleMapper accountRoleMapper;
    private final PlatDataScopeMapper dataScopeMapper;
    private final PlatAuthzVersionMapper authzVersionMapper;
    private final AuthzEpochService epochService;
    private final IdService idService;
    private final PlatformMutationSupport mutations;
    private final ObjectMapper objectMapper;

    public PlatformRoleAdminService(
            PlatRoleMapper roleMapper,
            PlatRoleDraftMapper roleDraftMapper,
            PlatPermissionMapper permissionMapper,
            PlatRolePermissionMapper rolePermissionMapper,
            PlatAccountRoleMapper accountRoleMapper,
            PlatDataScopeMapper dataScopeMapper,
            PlatAuthzVersionMapper authzVersionMapper,
            AuthzEpochService epochService,
            IdService idService,
            PlatformMutationSupport mutations,
            ObjectMapper objectMapper
    ) {
        this.roleMapper = roleMapper;
        this.roleDraftMapper = roleDraftMapper;
        this.permissionMapper = permissionMapper;
        this.rolePermissionMapper = rolePermissionMapper;
        this.accountRoleMapper = accountRoleMapper;
        this.dataScopeMapper = dataScopeMapper;
        this.authzVersionMapper = authzVersionMapper;
        this.epochService = epochService;
        this.idService = idService;
        this.mutations = mutations;
        this.objectMapper = objectMapper;
    }

    public PageResult<RoleView> listRoles(
            Integer pageValue,
            Integer sizeValue,
            String keyword,
            String status
    ) {
        var page = PlatformMutationSupport.page(pageValue);
        var size = expandedSize(sizeValue);
        var normalizedStatus = normalizeStatus(status, ROLE_STATUSES);
        var query = Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "PLATFORM")
                .eq(Role::getScopeKey, 0L)
                .eq(normalizedStatus != null, Role::getStatus, normalizedStatus)
                .and(keyword != null && !keyword.isBlank(), value -> value
                        .like(Role::getRoleCode, keyword.trim())
                        .or()
                        .like(Role::getName, keyword.trim()))
                .orderByDesc(Role::getIsBuiltin)
                .orderByAsc(Role::getRoleCode)
                .orderByAsc(Role::getId);
        var result = roleMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::view).toList(), page, size, result.getTotal());
    }

    public PageResult<PermissionView> listPermissions(
            Integer pageValue,
            Integer sizeValue,
            String keyword,
            String status
    ) {
        var page = PlatformMutationSupport.page(pageValue);
        var size = expandedSize(sizeValue);
        var normalizedStatus = normalizeStatus(status, PERMISSION_STATUSES);
        var query = Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "PLATFORM")
                .eq(Permission::getScopeKey, 0L)
                .eq(normalizedStatus != null, Permission::getStatus, normalizedStatus)
                .and(keyword != null && !keyword.isBlank(), value -> value
                        .like(Permission::getPermissionCode, keyword.trim())
                        .or()
                        .like(Permission::getName, keyword.trim()))
                .orderByAsc(Permission::getResourceType)
                .orderByAsc(Permission::getPermissionCode)
                .orderByAsc(Permission::getId);
        var result = permissionMapper.selectPage(new Page<>(page, size), query);
        return new PageResult<>(result.getRecords().stream().map(this::permissionView).toList(), page, size, result.getTotal());
    }

    @Transactional
    public RoleView createRole(
            AuthenticatedSession session,
            RoleCreate request,
            String idempotencyKey,
            ClientRequest client
    ) {
        return mutations.idempotent(
                session.accountId(),
                "PLATFORM_ROLE_CREATE",
                "new",
                idempotencyKey,
                request,
                RoleView.class,
                () -> createRoleOnce(session, request, idempotencyKey, client)
        );
    }

    @Transactional
    public RoleView saveDraft(
            AuthenticatedSession session,
            String roleId,
            RoleDraftInput request,
            ClientRequest client
    ) {
        var role = requireRole(PlatformMutationSupport.id(roleId));
        requireVersion(role.getVersion(), request.version());
        var before = view(role);
        var allowedPermissions = requirePermissions(request.permissionCodes());
        var deniedPermissions = requirePermissions(
                request.deniedPermissionCodes() == null ? List.of() : request.deniedPermissionCodes()
        );
        requireDisjointPermissions(allowedPermissions.keySet(), deniedPermissions.keySet());
        requireRootPermissions(role, allowedPermissions.keySet(), deniedPermissions.keySet());
        var dataScope = resolveDataScope(role, request.dataScopeId());
        var document = document(
                role,
                PlatformMutationSupport.required(request.name(), "name", 120),
                PlatformMutationSupport.optional(request.description(), "description", 1000),
                dataScope,
                allowedPermissions.values().stream().toList(),
                deniedPermissions.values().stream().toList()
        );
        var now = LocalDateTime.now();
        var draft = findDraft(role.getId());
        if (draft == null) {
            draft = new RoleDraft();
            draft.setId(idService.nextId());
            draft.setScopeType("PLATFORM");
            draft.setScopeKey(0L);
            draft.setRoleId(role.getId());
            draft.setDraftVersion(1L);
            draft.setBasePublishedVersion(role.getPublishedVersion());
            draft.setStatus("DRAFT");
            draft.setDraftJson(document.json());
            draft.setCreatedAt(now);
            draft.setCreatedBy(session.accountId());
            draft.setUpdatedAt(now);
            draft.setUpdatedBy(session.accountId());
            draft.setVersion(0L);
            roleDraftMapper.insert(draft);
        } else {
            draft.setDraftVersion(draft.getDraftVersion() + 1);
            draft.setBasePublishedVersion(role.getPublishedVersion());
            draft.setStatus("DRAFT");
            draft.setDraftJson(document.json());
            draft.setCheckResultJson(null);
            draft.setChecksum(null);
            draft.setCheckedAt(null);
            draft.setCheckedBy(null);
            draft.setPublishedAt(null);
            draft.setPublishedBy(null);
            draft.setUpdatedAt(now);
            draft.setUpdatedBy(session.accountId());
            if (roleDraftMapper.update(draft, Wrappers.<RoleDraft>lambdaUpdate()                 .eq(RoleDraft::getId, draft.getId())                 .set(RoleDraft::getCheckResultJson, null)                 .set(RoleDraft::getChecksum, null)                 .set(RoleDraft::getCheckedAt, null)                 .set(RoleDraft::getCheckedBy, null)                 .set(RoleDraft::getPublishedAt, null)                 .set(RoleDraft::getPublishedBy, null)) != 1) {
                throw PlatformMutationSupport.versionConflict();
            }
        }
        touchRole(role, session.accountId(), now);
        var after = view(requireRole(role.getId()));
        mutations.success(
                session, client, "PLATFORM_ROLE", after.id(), "PLATFORM_ROLE_DRAFT_SAVED",
                before, after, null
        );
        return after;
    }

    @Transactional
    public RoleView checkDraft(
            AuthenticatedSession session,
            String roleId,
            VersionInput request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var targetId = PlatformMutationSupport.id(roleId);
        return mutations.idempotent(
                session.accountId(),
                "PLATFORM_ROLE_DRAFT_CHECK",
                Long.toString(targetId),
                idempotencyKey,
                request,
                RoleView.class,
                () -> checkDraftOnce(session, targetId, request, idempotencyKey, client)
        );
    }

    @Transactional
    public RoleView publishDraft(
            AuthenticatedSession session,
            String roleId,
            VersionInput request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var targetId = PlatformMutationSupport.id(roleId);
        return mutations.idempotent(
                session.accountId(),
                "PLATFORM_ROLE_DRAFT_PUBLISH",
                Long.toString(targetId),
                idempotencyKey,
                request,
                RoleView.class,
                () -> publishDraftOnce(session, targetId, request, idempotencyKey, client)
        );
    }

    private RoleView createRoleOnce(
            AuthenticatedSession session,
            RoleCreate request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var code = normalizeCode(request.code());
        var name = PlatformMutationSupport.required(request.name(), "name", 120);
        var description = PlatformMutationSupport.optional(request.description(), "description", 1000);
        if (roleMapper.selectCount(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "PLATFORM")
                .eq(Role::getScopeKey, 0L)
                .eq(Role::getRoleCode, code)) > 0) {
            throw new BusinessException("DATA_CONFLICT", "Role code already exists", HttpStatus.CONFLICT);
        }

        var now = LocalDateTime.now();
        var role = new Role();
        role.setId(idService.nextId());
        role.setScopeType("PLATFORM");
        role.setScopeKey(0L);
        role.setRoleCode(code);
        role.setName(name);
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
        roleMapper.insert(role);

        var document = document(role, name, description, null, List.of(), List.of());
        var draft = new RoleDraft();
        draft.setId(idService.nextId());
        draft.setScopeType("PLATFORM");
        draft.setScopeKey(0L);
        draft.setRoleId(role.getId());
        draft.setDraftVersion(1L);
        draft.setBasePublishedVersion(0L);
        draft.setStatus("DRAFT");
        draft.setDraftJson(document.json());
        draft.setCreatedAt(now);
        draft.setCreatedBy(session.accountId());
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        draft.setVersion(0L);
        roleDraftMapper.insert(draft);

        var after = view(role);
        mutations.success(
                session, client, "PLATFORM_ROLE", after.id(), "PLATFORM_ROLE_CREATED",
                null, after, idempotencyKey
        );
        return after;
    }

    private RoleView checkDraftOnce(
            AuthenticatedSession session,
            long roleId,
            VersionInput request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var role = requireRole(roleId);
        requireVersion(role.getVersion(), request.version());
        var draft = requireDraft(roleId);
        if (!"DRAFT".equals(draft.getStatus())) {
            throw invalidState("Only a DRAFT role document can be checked");
        }
        if (!draft.getBasePublishedVersion().equals(role.getPublishedVersion())) {
            throw PlatformMutationSupport.versionConflict();
        }
        var before = view(role);
        var document = validateDocument(role, draft.getDraftJson());
        var now = LocalDateTime.now();
        draft.setDraftJson(document.json());
        draft.setStatus("CHECKED");
        draft.setCheckResultJson(write(Map.of(
                "valid", true,
                "permissionCount", document.allowedPermissions().size() + document.deniedPermissions().size(),
                "checkedAt", now.toString()
        )));
        draft.setChecksum(document.checksum());
        draft.setCheckedAt(now);
        draft.setCheckedBy(session.accountId());
        draft.setPublishedAt(null);
        draft.setPublishedBy(null);
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        if (roleDraftMapper.updateById(draft) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        touchRole(role, session.accountId(), now);
        var after = view(requireRole(roleId));
        mutations.success(
                session, client, "PLATFORM_ROLE", after.id(), "PLATFORM_ROLE_DRAFT_CHECKED",
                before, after, idempotencyKey
        );
        return after;
    }

    private RoleView publishDraftOnce(
            AuthenticatedSession session,
            long roleId,
            VersionInput request,
            String idempotencyKey,
            ClientRequest client
    ) {
        var role = requireRole(roleId);
        requireVersion(role.getVersion(), request.version());
        var draft = requireDraft(roleId);
        if (!"CHECKED".equals(draft.getStatus())) {
            throw invalidState("Only a CHECKED role document can be published");
        }
        if (!draft.getBasePublishedVersion().equals(role.getPublishedVersion())) {
            throw PlatformMutationSupport.versionConflict();
        }
        var document = validateDocument(role, draft.getDraftJson());
        if (draft.getChecksum() == null || !draft.getChecksum().equals(document.checksum())) {
            throw invalidState("The checked role document has changed; check it again before publishing");
        }

        var before = view(role);
        var now = LocalDateTime.now();
        rolePermissionMapper.delete(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "PLATFORM")
                .eq(RolePermission::getScopeKey, 0L)
                .eq(RolePermission::getRoleId, roleId));
        for (var permission : document.allowedPermissions()) {
            var link = new RolePermission();
            link.setId(idService.nextId());
            link.setScopeType("PLATFORM");
            link.setScopeKey(0L);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            link.setEffect("ALLOW");
            link.setCreatedAt(now);
            link.setCreatedBy(session.accountId());
            rolePermissionMapper.insert(link);
        }
        for (var permission : document.deniedPermissions()) {
            var link = new RolePermission();
            link.setId(idService.nextId());
            link.setScopeType("PLATFORM");
            link.setScopeKey(0L);
            link.setRoleId(roleId);
            link.setPermissionId(permission.getId());
            link.setEffect("DENY");
            link.setCreatedAt(now);
            link.setCreatedBy(session.accountId());
            rolePermissionMapper.insert(link);
        }

        var nextPublishedVersion = role.getPublishedVersion() + 1;
        var version = new AuthzVersion();
        version.setId(idService.nextId());
        version.setScopeType("PLATFORM");
        version.setScopeKey(0L);
        version.setRoleId(roleId);
        version.setVersionNo(nextPublishedVersion);
        version.setSnapshotJson(document.json());
        version.setChecksum(document.checksum());
        version.setPublishedAt(now);
        version.setPublishedBy(session.accountId());
        version.setCreatedAt(now);
        authzVersionMapper.insert(version);

        role.setName(document.name());
        role.setDataScopeId(document.dataScope() == null ? null : document.dataScope().getId());
        role.setPermissionVersion(nextPublishedVersion);
        role.setPublishedVersion(nextPublishedVersion);
        role.setUpdatedAt(now);
        role.setUpdatedBy(session.accountId());
        if (roleMapper.updateById(role) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        if (document.dataScope() == null) {
            roleMapper.update(null, Wrappers.<Role>lambdaUpdate()
                    .eq(Role::getScopeType, "PLATFORM")
                    .eq(Role::getScopeKey, 0L)
                    .eq(Role::getId, roleId)
                    .set(Role::getDataScopeId, null));
        }

        draft.setStatus("PUBLISHED");
        draft.setPublishedAt(now);
        draft.setPublishedBy(session.accountId());
        draft.setUpdatedAt(now);
        draft.setUpdatedBy(session.accountId());
        if (roleDraftMapper.updateById(draft) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
        epochService.bumpPlatform(session.accountId());
        var after = view(requireRole(roleId));
        mutations.success(
                session, client, "PLATFORM_ROLE", after.id(), "PLATFORM_ROLE_DRAFT_PUBLISHED",
                before,
                Map.of("role", after, "publishedVersion", Long.toString(nextPublishedVersion)),
                idempotencyKey
        );
        return after;
    }

    private DraftDocument validateDocument(Role role, String json) {
        var root = readTree(json);
        var name = PlatformMutationSupport.required(text(root, "name", role.getName()), "name", 120);
        var description = PlatformMutationSupport.optional(text(root, "description", null), "description", 1000);
        var allowedCodes = new ArrayList<String>();
        var deniedCodes = new ArrayList<String>();
        var permissionsNode = root.path("permissions");
        if (!permissionsNode.isArray()) {
            throw PlatformMutationSupport.validation("permissions must be an array");
        }
        for (var permission : permissionsNode) {
            var code = permission.isTextual() ? permission.asText() : permission.path("code").asText(null);
            var normalizedCode = PlatformMutationSupport.required(code, "permissionCode", 128);
            var effect = permission.isTextual() ? "ALLOW" : permission.path("effect").asText("ALLOW");
            if ("DENY".equals(effect)) {
                deniedCodes.add(normalizedCode);
            } else if ("ALLOW".equals(effect)) {
                allowedCodes.add(normalizedCode);
            } else {
                throw PlatformMutationSupport.validation("permission effect must be ALLOW or DENY");
            }
        }
        var allowedPermissions = requirePermissions(allowedCodes);
        var deniedPermissions = requirePermissions(deniedCodes);
        requireDisjointPermissions(allowedPermissions.keySet(), deniedPermissions.keySet());
        requireRootPermissions(role, allowedPermissions.keySet(), deniedPermissions.keySet());

        DataScope dataScope = null;
        var dataScopeNode = root.path("dataScope");
        if (dataScopeNode.isObject() && dataScopeNode.path("id").isValueNode()) {
            dataScope = requireDataScope(PlatformMutationSupport.id(dataScopeNode.path("id").asText()));
        }
        return document(
                role, name, description, dataScope,
                allowedPermissions.values().stream().toList(),
                deniedPermissions.values().stream().toList()
        );
    }

    private DraftDocument document(
            Role role,
            String name,
            String description,
            DataScope dataScope,
            List<Permission> allowedPermissions,
            List<Permission> deniedPermissions
    ) {
        var sortedAllowed = allowedPermissions.stream()
                .sorted(java.util.Comparator.comparing(Permission::getPermissionCode))
                .toList();
        var sortedDenied = deniedPermissions.stream()
                .sorted(java.util.Comparator.comparing(Permission::getPermissionCode))
                .toList();
        var value = new LinkedHashMap<String, Object>();
        value.put("roleId", Long.toString(role.getId()));
        value.put("roleCode", role.getRoleCode());
        value.put("scopeType", "PLATFORM");
        value.put("scopeKey", "0");
        value.put("name", name);
        value.put("description", description == null ? "" : description);
        value.put("dataScope", dataScope == null ? null : Map.of(
                "id", Long.toString(dataScope.getId()),
                "kind", dataScope.getScopeKind()
        ));
        var grants = new ArrayList<Map<String, String>>();
        grants.addAll(sortedAllowed.stream().map(permission -> Map.of(
                "permissionId", Long.toString(permission.getId()),
                "code", permission.getPermissionCode(),
                "effect", "ALLOW"
        )).toList());
        grants.addAll(sortedDenied.stream().map(permission -> Map.of(
                "permissionId", Long.toString(permission.getId()),
                "code", permission.getPermissionCode(),
                "effect", "DENY"
        )).toList());
        value.put("permissions", grants);
        var json = write(value);
        return new DraftDocument(
                name, description, dataScope, sortedAllowed, sortedDenied,
                json, PlatformMutationSupport.sha256(json)
        );
    }

    private Map<String, Permission> requirePermissions(List<String> values) {
        if (values == null || values.size() > 300) {
            throw PlatformMutationSupport.validation("permissionCodes must contain at most 300 items");
        }
        var codes = new TreeSet<String>();
        for (var value : values) {
            codes.add(PlatformMutationSupport.required(value, "permissionCode", 128));
        }
        if (codes.isEmpty()) {
            return Map.of();
        }
        var permissions = permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                .eq(Permission::getScopeType, "PLATFORM")
                .eq(Permission::getScopeKey, 0L)
                .eq(Permission::getStatus, "ACTIVE")
                .in(Permission::getPermissionCode, codes));
        var byCode = permissions.stream().collect(Collectors.toMap(
                Permission::getPermissionCode,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new
        ));
        if (!byCode.keySet().equals(codes)) {
            throw PlatformMutationSupport.validation("One or more platform permissions are unknown or disabled");
        }
        return byCode;
    }

    private void requireRootPermissions(
            Role role,
            Set<String> allowedPermissionCodes,
            Set<String> deniedPermissionCodes
    ) {
        if (!"ROOT".equals(role.getRoleType())) {
            return;
        }
        var required = PermissionCatalog.PLATFORM.stream()
                .map(PermissionCatalog.Definition::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!allowedPermissionCodes.containsAll(required) || !deniedPermissionCodes.isEmpty()) {
            throw new BusinessException(
                    "ROOT_ROLE_INVARIANT",
                    "The built-in ROOT role must retain every platform permission",
                    HttpStatus.UNPROCESSABLE_ENTITY
            );
        }
    }

    private static void requireDisjointPermissions(Set<String> allowed, Set<String> denied) {
        if (allowed.stream().anyMatch(denied::contains)) {
            throw PlatformMutationSupport.validation("A permission cannot be both ALLOW and DENY in one role");
        }
    }

    private DataScope resolveDataScope(Role role, String requestedId) {
        if (requestedId == null || requestedId.isBlank()) {
            return role.getDataScopeId() == null ? null : requireDataScope(role.getDataScopeId());
        }
        return requireDataScope(PlatformMutationSupport.id(requestedId));
    }

    private DataScope requireDataScope(long id) {
        var dataScope = dataScopeMapper.selectOne(Wrappers.<DataScope>lambdaQuery()
                .eq(DataScope::getScopeType, "PLATFORM")
                .eq(DataScope::getScopeKey, 0L)
                .eq(DataScope::getStatus, "ACTIVE")
                .eq(DataScope::getId, id));
        if (dataScope == null) {
            throw PlatformMutationSupport.notFound("Platform data scope");
        }
        return dataScope;
    }

    private RoleView view(Role role) {
        var draft = findDraft(role.getId());
        var root = draft == null ? null : readTree(draft.getDraftJson());
        var name = root == null ? role.getName() : text(root, "name", role.getName());
        var description = root == null ? null : text(root, "description", null);
        var permissionCodes = new ArrayList<String>();
        var deniedPermissionCodes = new ArrayList<String>();
        if (root != null && root.path("permissions").isArray()) {
            for (var permission : root.path("permissions")) {
                var code = permission.isTextual() ? permission.asText() : permission.path("code").asText(null);
                if (code != null && !code.isBlank()) {
                    var effect = permission.isTextual() ? "ALLOW" : permission.path("effect").asText("ALLOW");
                    ("DENY".equals(effect) ? deniedPermissionCodes : permissionCodes).add(code);
                }
            }
        } else {
            permissionCodes.addAll(publishedPermissionCodes(role.getId(), "ALLOW"));
            deniedPermissionCodes.addAll(publishedPermissionCodes(role.getId(), "DENY"));
        }
        permissionCodes.sort(String::compareTo);
        deniedPermissionCodes.sort(String::compareTo);
        var dataScopeId = root == null ? null : dataScopeId(root);
        if (dataScopeId == null && role.getDataScopeId() != null) {
            dataScopeId = Long.toString(role.getDataScopeId());
        }
        var now = LocalDateTime.now();
        var memberCount = accountRoleMapper.selectCount(Wrappers.<AccountRole>lambdaQuery()
                .eq(AccountRole::getScopeType, "PLATFORM")
                .eq(AccountRole::getScopeKey, 0L)
                .eq(AccountRole::getRoleId, role.getId())
                .le(AccountRole::getValidFrom, now)
                .and(value -> value.isNull(AccountRole::getValidUntil).or().gt(AccountRole::getValidUntil, now)));
        return new RoleView(
                Long.toString(role.getId()),
                role.getRoleCode(),
                name,
                description,
                role.getStatus(),
                Boolean.TRUE.equals(role.getIsBuiltin()),
                memberCount,
                permissionCodes,
                deniedPermissionCodes,
                dataScopeId,
                draft == null ? "PUBLISHED" : draft.getStatus(),
                role.getPublishedVersion() == null || role.getPublishedVersion() == 0
                        ? null : Long.toString(role.getPublishedVersion()),
                Long.toString(role.getVersion())
        );
    }

    private List<String> publishedPermissionCodes(long roleId, String effect) {
        var links = rolePermissionMapper.selectList(Wrappers.<RolePermission>lambdaQuery()
                .eq(RolePermission::getScopeType, "PLATFORM")
                .eq(RolePermission::getScopeKey, 0L)
                .eq(RolePermission::getRoleId, roleId)
                .eq(RolePermission::getEffect, effect));
        if (links.isEmpty()) {
            return List.of();
        }
        return permissionMapper.selectList(Wrappers.<Permission>lambdaQuery()
                        .eq(Permission::getScopeType, "PLATFORM")
                        .eq(Permission::getScopeKey, 0L)
                        .in(Permission::getId, links.stream().map(RolePermission::getPermissionId).toList()))
                .stream()
                .map(Permission::getPermissionCode)
                .sorted()
                .toList();
    }

    private PermissionView permissionView(Permission permission) {
        return new PermissionView(
                Long.toString(permission.getId()),
                permission.getPermissionCode(),
                permission.getName(),
                permission.getResourceType(),
                null
        );
    }

    private Role requireRole(long id) {
        var role = roleMapper.selectOne(Wrappers.<Role>lambdaQuery()
                .eq(Role::getScopeType, "PLATFORM")
                .eq(Role::getScopeKey, 0L)
                .eq(Role::getId, id));
        if (role == null) {
            throw PlatformMutationSupport.notFound("Platform role");
        }
        return role;
    }

    private RoleDraft requireDraft(long roleId) {
        var draft = findDraft(roleId);
        if (draft == null) {
            throw PlatformMutationSupport.notFound("Platform role draft");
        }
        return draft;
    }

    private RoleDraft findDraft(long roleId) {
        return roleDraftMapper.selectOne(Wrappers.<RoleDraft>lambdaQuery()
                .eq(RoleDraft::getScopeType, "PLATFORM")
                .eq(RoleDraft::getScopeKey, 0L)
                .eq(RoleDraft::getRoleId, roleId));
    }

    private void touchRole(Role role, long actorAccountId, LocalDateTime now) {
        role.setUpdatedAt(now);
        role.setUpdatedBy(actorAccountId);
        if (roleMapper.updateById(role) != 1) {
            throw PlatformMutationSupport.versionConflict();
        }
    }

    private JsonNode readTree(String json) {
        try {
            var value = objectMapper.readTree(json);
            if (value == null || !value.isObject()) {
                throw PlatformMutationSupport.validation("Role draft must be a JSON object");
            }
            return value;
        } catch (JsonProcessingException exception) {
            throw PlatformMutationSupport.validation("Role draft JSON is invalid");
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize platform role document", exception);
        }
    }

    private static String text(JsonNode root, String field, String fallback) {
        var value = root.path(field);
        return value.isTextual() ? value.asText() : fallback;
    }

    private static String dataScopeId(JsonNode root) {
        var value = root.path("dataScope").path("id");
        return value.isValueNode() && !value.asText().isBlank() ? value.asText() : null;
    }

    private static String normalizeCode(String value) {
        var code = PlatformMutationSupport.required(value, "code", 64);
        code = Normalizer.normalize(code, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        if (!code.matches("[a-z0-9][a-z0-9._-]{0,63}")) {
            throw PlatformMutationSupport.validation("code format is invalid");
        }
        return code;
    }

    private static int expandedSize(Integer value) {
        if (value == null) {
            return 20;
        }
        if (value < 1 || value > 500) {
            throw PlatformMutationSupport.validation("size must be between 1 and 500");
        }
        return value;
    }

    private static String normalizeStatus(String value, Set<String> allowed) {
        if (value == null || value.isBlank()) {
            return null;
        }
        var status = value.trim().toUpperCase(Locale.ROOT);
        if (!allowed.contains(status)) {
            throw PlatformMutationSupport.validation("status is invalid");
        }
        return status;
    }

    private static void requireVersion(long actual, String requested) {
        if (actual != PlatformMutationSupport.version(requested)) {
            throw PlatformMutationSupport.versionConflict();
        }
    }

    private static BusinessException invalidState(String message) {
        return new BusinessException("STATE_TRANSITION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private record DraftDocument(
            String name,
            String description,
            DataScope dataScope,
            List<Permission> allowedPermissions,
            List<Permission> deniedPermissions,
            String json,
            String checksum
    ) {
    }
}
