package com.unique.examine.plat.manage.permission;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.error.CommonErrorCode;
import com.unique.examine.plat.base.entity.PlatDataScopeRule;
import com.unique.examine.plat.base.entity.PlatDenyPolicy;
import com.unique.examine.plat.base.entity.PlatEffectivePermissionSnapshot;
import com.unique.examine.plat.base.entity.PlatPermissionPreviewLog;
import com.unique.examine.plat.base.entity.PlatPermissionVersion;
import com.unique.examine.plat.base.entity.PlatRole;
import com.unique.examine.plat.base.entity.PlatRoleFieldPermission;
import com.unique.examine.plat.base.entity.PlatRoleMember;
import com.unique.examine.plat.base.entity.PlatRolePermission;
import com.unique.examine.plat.base.service.PlatDataScopeRuleBaseService;
import com.unique.examine.plat.base.service.PlatDenyPolicyBaseService;
import com.unique.examine.plat.base.service.PlatEffectivePermissionSnapshotBaseService;
import com.unique.examine.plat.base.service.PlatPermissionPreviewLogBaseService;
import com.unique.examine.plat.base.service.PlatPermissionVersionBaseService;
import com.unique.examine.plat.base.service.PlatRoleBaseService;
import com.unique.examine.plat.base.service.PlatRoleFieldPermissionBaseService;
import com.unique.examine.plat.base.service.PlatRoleMemberBaseService;
import com.unique.examine.plat.base.service.PlatRolePermissionBaseService;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver;
import com.unique.examine.plat.manage.common.SystemMemberContextResolver.SystemMemberContext;
import com.unique.examine.plat.manage.permission.PermissionModels.EffectivePermissionSnapshot;
import com.unique.examine.plat.manage.permission.PermissionModels.PermissionDecisionVO;
import com.unique.examine.plat.manage.permission.PermissionModels.PermissionPreviewRequest;
import com.unique.examine.plat.manage.permission.PermissionModels.RolePermissionSaveRequest;
import com.unique.examine.plat.manage.permission.PermissionModels.RolePermissionVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Permission management service backed by persisted RBAC permission tables.
 */
@Service
public class PermissionService {

    private static final int ENABLED = 1;
    private static final int DELETED_NO = 0;
    private static final String SCOPE_SYSTEM = "SYSTEM";
    private static final String SYSTEM_SUPER_ADMIN = "SYSTEM_SUPER_ADMIN";
    private static final String TARGET_ROLE_PAYLOAD = "ROLE_PAYLOAD";
    private static final String TARGET_ACTION = "ACTION";
    private static final String TARGET_MENU = "MENU";
    private static final String TARGET_MODULE = "MODULE";
    private static final String TARGET_FIELD = "FIELD";
    private static final String TARGET_DENY = "DENY_POLICY";
    private static final String EFFECT_ALLOW = "ALLOW";
    private static final String EFFECT_DENY = "DENY";
    private static final TypeReference<Map<String, Object>> MAP_OBJECT_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Boolean>> MAP_BOOLEAN_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> MAP_STRING_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> LIST_MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final SystemMemberContextResolver contextResolver;
    private final PlatRoleBaseService roleBaseService;
    private final PlatRoleMemberBaseService roleMemberBaseService;
    private final PlatPermissionVersionBaseService permissionVersionBaseService;
    private final PlatRolePermissionBaseService rolePermissionBaseService;
    private final PlatRoleFieldPermissionBaseService roleFieldPermissionBaseService;
    private final PlatDataScopeRuleBaseService dataScopeRuleBaseService;
    private final PlatDenyPolicyBaseService denyPolicyBaseService;
    private final PlatEffectivePermissionSnapshotBaseService effectivePermissionSnapshotBaseService;
    private final PlatPermissionPreviewLogBaseService permissionPreviewLogBaseService;
    private final ObjectMapper objectMapper;

    public PermissionService(SystemMemberContextResolver contextResolver,
                             PlatRoleBaseService roleBaseService,
                             PlatRoleMemberBaseService roleMemberBaseService,
                             PlatPermissionVersionBaseService permissionVersionBaseService,
                             PlatRolePermissionBaseService rolePermissionBaseService,
                             PlatRoleFieldPermissionBaseService roleFieldPermissionBaseService,
                             PlatDataScopeRuleBaseService dataScopeRuleBaseService,
                             PlatDenyPolicyBaseService denyPolicyBaseService,
                             PlatEffectivePermissionSnapshotBaseService effectivePermissionSnapshotBaseService,
                             PlatPermissionPreviewLogBaseService permissionPreviewLogBaseService,
                             ObjectMapper objectMapper) {
        this.contextResolver = contextResolver;
        this.roleBaseService = roleBaseService;
        this.roleMemberBaseService = roleMemberBaseService;
        this.permissionVersionBaseService = permissionVersionBaseService;
        this.rolePermissionBaseService = rolePermissionBaseService;
        this.roleFieldPermissionBaseService = roleFieldPermissionBaseService;
        this.dataScopeRuleBaseService = dataScopeRuleBaseService;
        this.denyPolicyBaseService = denyPolicyBaseService;
        this.effectivePermissionSnapshotBaseService = effectivePermissionSnapshotBaseService;
        this.permissionPreviewLogBaseService = permissionPreviewLogBaseService;
        this.objectMapper = objectMapper;
    }

    /**
     * Query role permissions.
     *
     * @param systemId system id
     * @param roleId role id
     * @return role permissions
     */
    public RolePermissionVO getRolePermissions(String systemId, String roleId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatRole role = requireSystemRole(context, roleId);
        Optional<PlatRolePermission> payloadRow = latestRolePayload(role.getId());
        if (payloadRow.isEmpty()) {
            return new RolePermissionVO(String.valueOf(role.getId()), latestPermissionVersion(context),
                    Map.of(), Map.of(), List.of(), List.of(), role.getUpdatedAt());
        }
        Map<String, Object> payload = readJson(payloadRow.get().getPermissionPayload(), MAP_OBJECT_TYPE, Map.of());
        return new RolePermissionVO(String.valueOf(role.getId()), payloadRow.get().getPermissionVersion(),
                asBooleanMap(payload.get("actionPermissions")),
                asStringMap(payload.get("fieldPermissions")),
                asListMap(payload.get("dataScopeRules")),
                asStringList(payload.get("denyPolicies")),
                payloadRow.get().getCreatedAt());
    }

    /**
     * Save role permissions and return the new permission version.
     *
     * @param systemId system id
     * @param roleId role id
     * @param request save request
     * @return role permissions
     */
    @Transactional(rollbackFor = Exception.class)
    public RolePermissionVO saveRolePermissions(String systemId, String roleId, RolePermissionSaveRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        PlatRole role = requireSystemRole(context, roleId);
        RolePermissionSaveRequest resolved = Objects.isNull(request)
                ? new RolePermissionSaveRequest(Map.of(), Map.of(), Map.of(), Map.of(), List.of(), List.of())
                : request;
        String version = newPermissionVersion();
        savePermissionVersion(context, version, "更新角色权限：" + role.getRoleName());

        // 同一个角色的权限配置整体发布，避免动作、字段、数据范围版本不一致。
        rolePermissionBaseService.remove(new LambdaQueryWrapper<PlatRolePermission>()
                .eq(PlatRolePermission::getRoleId, role.getId()));
        roleFieldPermissionBaseService.remove(new LambdaQueryWrapper<PlatRoleFieldPermission>()
                .eq(PlatRoleFieldPermission::getRoleId, role.getId()));
        dataScopeRuleBaseService.remove(new LambdaQueryWrapper<PlatDataScopeRule>()
                .eq(PlatDataScopeRule::getRoleId, role.getId()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("menuPermissions", safeBooleanMap(resolved.menuPermissions()));
        payload.put("modulePermissions", safeBooleanMap(resolved.modulePermissions()));
        payload.put("actionPermissions", safeBooleanMap(resolved.actionPermissions()));
        payload.put("fieldPermissions", safeStringMap(resolved.fieldPermissions()));
        payload.put("dataScopeRules", safeListMap(resolved.dataScopeRules()));
        payload.put("denyPolicies", safeStringList(resolved.denyPolicies()));
        saveRolePermission(role.getId(), version, TARGET_ROLE_PAYLOAD, "permissions", EFFECT_ALLOW, payload);
        saveBooleanPermissionRows(role.getId(), version, TARGET_MENU, safeBooleanMap(resolved.menuPermissions()));
        saveBooleanPermissionRows(role.getId(), version, TARGET_MODULE, safeBooleanMap(resolved.modulePermissions()));
        saveBooleanPermissionRows(role.getId(), version, TARGET_ACTION, safeBooleanMap(resolved.actionPermissions()));
        saveFieldPermissions(context, role.getId(), version, safeStringMap(resolved.fieldPermissions()));
        saveDataScopeRules(context, role.getId(), safeListMap(resolved.dataScopeRules()));
        saveDenyPolicies(context, role.getId(), version, safeStringList(resolved.denyPolicies()));

        return new RolePermissionVO(String.valueOf(role.getId()), version,
                safeBooleanMap(resolved.actionPermissions()), safeStringMap(resolved.fieldPermissions()),
                safeListMap(resolved.dataScopeRules()), safeStringList(resolved.denyPolicies()), LocalDateTime.now());
    }

    /**
     * Return effective permission for the current member context.
     *
     * @param systemId system id
     * @return effective snapshot
     */
    @Transactional(rollbackFor = Exception.class)
    public EffectivePermissionSnapshot effective(String systemId) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        List<PlatRole> roles = currentRoles(context);
        PermissionAggregate aggregate = aggregate(context, roles);
        EffectivePermissionSnapshot snapshot = toSnapshot(context, aggregate, null);
        persistSnapshot(context, snapshot);
        return snapshot;
    }

    /**
     * Preview effective permissions for a proposed role/member combination.
     *
     * @param systemId system id
     * @param request preview request
     * @return decision
     */
    @Transactional(rollbackFor = Exception.class)
    public PermissionDecisionVO preview(String systemId, PermissionPreviewRequest request) {
        SystemMemberContext context = contextResolver.resolve(systemId);
        List<PlatRole> roles = previewRoles(context, request);
        PermissionAggregate aggregate = aggregate(context, roles);
        String actionCode = request == null ? null : request.actionCode();
        PermissionDecisionVO decision = buildDecision(aggregate, actionCode);
        PlatPermissionPreviewLog log = new PlatPermissionPreviewLog();
        log.setSystemId(context.systemId());
        log.setTenantId(context.tenantId());
        log.setSystemMemberId(previewMemberId(context, request));
        log.setRoleIds(toJson(roleIds(roles)));
        log.setModuleId(parseOptionalId(request == null ? null : request.moduleId()));
        log.setRecordId(parseOptionalId(request == null ? null : request.recordId()));
        log.setActionCode(actionCode);
        log.setDecisionPayload(toJson(decision));
        log.setTraceId(RequestContext.current().traceId());
        log.setCreatedAt(LocalDateTime.now());
        permissionPreviewLogBaseService.saveEntity(log);
        return new PermissionDecisionVO(decision.allowed(), decision.disabledReason(), decision.missingPermissions(),
                decision.dataScopeExpression(), decision.fieldMaskRules(), decision.explain(),
                decision.permissionVersion(), decision.traceId(), "aud_" + log.getId());
    }

    private PermissionDecisionVO buildDecision(PermissionAggregate aggregate, String actionCode) {
        boolean hasAction = StringUtils.hasText(actionCode);
        boolean allowed = !hasAction || Boolean.TRUE.equals(aggregate.actionPermissions().get("*"))
                || Boolean.TRUE.equals(aggregate.actionPermissions().get(actionCode));
        if (hasAction && (aggregate.denyPolicyIds().contains(actionCode)
                || Boolean.FALSE.equals(aggregate.actionPermissions().get(actionCode)))) {
            allowed = false;
        }
        List<String> missing = allowed || !hasAction ? List.of() : List.of(actionCode);
        String disabledReason = allowed ? null : "当前角色未配置该动作权限或被拒绝策略限制";
        return new PermissionDecisionVO(allowed, disabledReason, missing, aggregate.dataScope(),
                maskedFields(aggregate.fieldPermissions()), aggregate.explain(), aggregate.permissionVersion(),
                RequestContext.current().traceId(), RequestContext.current().auditLogId());
    }

    private EffectivePermissionSnapshot toSnapshot(SystemMemberContext context, PermissionAggregate aggregate,
                                                   String disabledReason) {
        return new EffectivePermissionSnapshot("eps_" + UUID.randomUUID().toString().replace("-", ""),
                aggregate.permissionVersion(), String.valueOf(context.systemMemberId()),
                String.valueOf(context.tenantId()), aggregate.sourceRoleIds(), aggregate.denyPolicyIds(),
                aggregate.fieldPermissions(), aggregate.actionPermissions(), aggregate.dataScope(), disabledReason,
                aggregate.explain());
    }

    private void persistSnapshot(SystemMemberContext context, EffectivePermissionSnapshot snapshot) {
        PlatEffectivePermissionSnapshot entity = new PlatEffectivePermissionSnapshot();
        entity.setSnapshotId(snapshot.snapshotId());
        entity.setPermissionVersion(snapshot.permissionVersion());
        entity.setSystemId(context.systemId());
        entity.setTenantId(context.tenantId());
        entity.setSystemMemberId(context.systemMemberId());
        entity.setSourceRoleIds(toJson(snapshot.sourceRoleIds()));
        entity.setDenyPolicyIds(toJson(snapshot.denyPolicyIds()));
        entity.setFieldPermissions(toJson(snapshot.field()));
        entity.setActionPermissions(toJson(snapshot.action()));
        entity.setDataScope(toJson(snapshot.dataScope()));
        entity.setDisabledReason(snapshot.disabledReason());
        entity.setExplainPayload(toJson(snapshot.explain()));
        entity.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        entity.setCreatedAt(LocalDateTime.now());
        effectivePermissionSnapshotBaseService.saveEntity(entity);
    }

    private PermissionAggregate aggregate(SystemMemberContext context, List<PlatRole> roles) {
        List<String> sourceRoleIds = roleIds(roles);
        if (roles.stream().anyMatch(this::isSystemSuperAdmin)) {
            return new PermissionAggregate(latestPermissionVersion(context), sourceRoleIds, List.of(),
                    Map.of("*", "WRITABLE"), Map.of("*", true), Map.of("type", "ALL"),
                    List.of(Map.of("type", "SYSTEM_RULE", "target", "SYSTEM_SUPER_ADMIN")));
        }
        Map<String, Boolean> actionPermissions = new LinkedHashMap<>();
        Map<String, String> fieldPermissions = new LinkedHashMap<>();
        Set<String> denyPolicies = new LinkedHashSet<>();
        List<Map<String, Object>> dataScopeRules = new ArrayList<>();
        List<Map<String, Object>> explain = new ArrayList<>();
        Set<String> versions = new LinkedHashSet<>();
        for (PlatRole role : roles) {
            latestRolePayload(role.getId()).ifPresent(row -> {
                versions.add(row.getPermissionVersion());
                Map<String, Object> payload = readJson(row.getPermissionPayload(), MAP_OBJECT_TYPE, Map.of());
                mergeActions(actionPermissions, asBooleanMap(payload.get("actionPermissions")), role);
                mergeFields(fieldPermissions, asStringMap(payload.get("fieldPermissions")), role);
                denyPolicies.addAll(asStringList(payload.get("denyPolicies")));
                dataScopeRules.addAll(asListMap(payload.get("dataScopeRules")));
            });
        }
        Map<String, Object> dataScope = mergeDataScope(dataScopeRules);
        explain.addAll(actionPermissions.keySet().stream()
                .map(action -> Map.<String, Object>of("type", "ROLE_ALLOW", "target", action))
                .toList());
        explain.addAll(denyPolicies.stream()
                .map(policy -> Map.<String, Object>of("type", "DENY_POLICY", "target", policy))
                .toList());
        String version = versions.stream().reduce((first, second) -> second).orElseGet(() -> latestPermissionVersion(context));
        return new PermissionAggregate(version, sourceRoleIds, new ArrayList<>(denyPolicies), fieldPermissions,
                actionPermissions, dataScope, explain);
    }

    private void mergeActions(Map<String, Boolean> target, Map<String, Boolean> source, PlatRole role) {
        source.forEach((action, allowed) -> {
            if (Boolean.FALSE.equals(allowed)) {
                target.put(action, false);
            } else if (!Boolean.FALSE.equals(target.get(action))) {
                target.put(action, true);
            }
        });
    }

    private void mergeFields(Map<String, String> target, Map<String, String> source, PlatRole role) {
        source.forEach((field, mode) -> {
            String current = target.get(field);
            if (!StringUtils.hasText(current) || fieldModeWeight(mode) > fieldModeWeight(current)) {
                target.put(field, mode);
            }
        });
    }

    private int fieldModeWeight(String mode) {
        return switch (safeText(mode, "READABLE").toUpperCase(Locale.ROOT)) {
            case "HIDDEN" -> 4;
            case "MASKED" -> 3;
            case "READABLE" -> 2;
            case "WRITABLE" -> 1;
            default -> 2;
        };
    }

    private Map<String, Object> mergeDataScope(List<Map<String, Object>> rules) {
        if (rules.isEmpty()) {
            return Map.of("type", "ALL");
        }
        List<String> priority = List.of("SELF", "DEPARTMENT", "CUSTOM", "ALL");
        return rules.stream()
                .min(Comparator.comparingInt(rule -> {
                    String type = safeText(stringValue(rule.get("type")), safeText(stringValue(rule.get("scopeType")), "ALL"));
                    int index = priority.indexOf(type.toUpperCase(Locale.ROOT));
                    return index < 0 ? priority.indexOf("CUSTOM") : index;
                }))
                .map(rule -> {
                    Map<String, Object> merged = new LinkedHashMap<>(rule);
                    merged.putIfAbsent("type", safeText(stringValue(rule.get("scopeType")), "CUSTOM"));
                    return merged;
                })
                .orElse(Map.of("type", "ALL"));
    }

    private Map<String, String> maskedFields(Map<String, String> fields) {
        Map<String, String> masks = new LinkedHashMap<>();
        fields.forEach((field, mode) -> {
            if ("MASKED".equalsIgnoreCase(mode) || "HIDDEN".equalsIgnoreCase(mode)) {
                masks.put(field, mode);
            }
        });
        return masks;
    }

    private List<PlatRole> previewRoles(SystemMemberContext context, PermissionPreviewRequest request) {
        List<String> roleIds = request == null ? List.of() : safeStringList(request.roleIds());
        if (roleIds.isEmpty()) {
            return currentRoles(context);
        }
        return roleIds.stream()
                .map(roleId -> requireSystemRole(context, roleId))
                .toList();
    }

    private Long previewMemberId(SystemMemberContext context, PermissionPreviewRequest request) {
        Long requested = parseOptionalId(request == null ? null : request.systemMemberId());
        return Objects.isNull(requested) ? context.systemMemberId() : requested;
    }

    private List<PlatRole> currentRoles(SystemMemberContext context) {
        List<PlatRoleMember> roleMembers = roleMemberBaseService.list(new LambdaQueryWrapper<PlatRoleMember>()
                .eq(PlatRoleMember::getSystemId, context.systemId())
                .eq(PlatRoleMember::getTenantId, context.tenantId())
                .and(wrapper -> wrapper.eq(PlatRoleMember::getAccountId, context.accountId())
                        .or().eq(PlatRoleMember::getSystemMemberId, context.systemMemberId())));
        List<Long> ids = roleMembers.stream()
                .map(PlatRoleMember::getRoleId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return roleBaseService.list(new LambdaQueryWrapper<PlatRole>()
                .in(PlatRole::getId, ids)
                .eq(PlatRole::getScope, SCOPE_SYSTEM)
                .eq(PlatRole::getSystemId, context.systemId())
                .eq(PlatRole::getTenantId, context.tenantId())
                .eq(PlatRole::getStatus, ENABLED)
                .eq(PlatRole::getDeleted, DELETED_NO));
    }

    private List<String> roleIds(List<PlatRole> roles) {
        return roles.stream()
                .map(PlatRole::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .toList();
    }

    private boolean isSystemSuperAdmin(PlatRole role) {
        return Objects.equals(role.getBuiltin(), ENABLED)
                && (SYSTEM_SUPER_ADMIN.equals(role.getRoleCode()) || SYSTEM_SUPER_ADMIN.equals(role.getRoleType()));
    }

    private Optional<PlatRolePermission> latestRolePayload(Long roleId) {
        return Optional.ofNullable(rolePermissionBaseService.getOne(new LambdaQueryWrapper<PlatRolePermission>()
                .eq(PlatRolePermission::getRoleId, roleId)
                .eq(PlatRolePermission::getTargetType, TARGET_ROLE_PAYLOAD)
                .orderByDesc(PlatRolePermission::getId)
                .last("LIMIT 1"), false));
    }

    private PlatRole requireSystemRole(SystemMemberContext context, String roleId) {
        Long id = contextResolver.parseRequiredId(roleId, "角色ID格式不正确");
        PlatRole role = roleBaseService.getOne(new LambdaQueryWrapper<PlatRole>()
                .eq(PlatRole::getId, id)
                .eq(PlatRole::getScope, SCOPE_SYSTEM)
                .eq(PlatRole::getSystemId, context.systemId())
                .eq(PlatRole::getTenantId, context.tenantId())
                .eq(PlatRole::getDeleted, DELETED_NO)
                .last("LIMIT 1"), false);
        if (Objects.isNull(role)) {
            throw new BusinessException(CommonErrorCode.FIELD_VALIDATION_FAILED, "角色不存在");
        }
        return role;
    }

    private void savePermissionVersion(SystemMemberContext context, String version, String summary) {
        PlatPermissionVersion entity = new PlatPermissionVersion();
        entity.setScope(SCOPE_SYSTEM);
        entity.setSystemId(context.systemId());
        entity.setTenantId(context.tenantId());
        entity.setPermissionVersion(version);
        entity.setPublishedBy(context.accountId());
        entity.setPublishedAt(LocalDateTime.now());
        entity.setChangeSummary(summary);
        permissionVersionBaseService.saveEntity(entity);
    }

    private void saveBooleanPermissionRows(Long roleId, String version, String targetType, Map<String, Boolean> permissions) {
        permissions.forEach((code, allowed) -> saveRolePermission(roleId, version, targetType, code,
                Boolean.TRUE.equals(allowed) ? EFFECT_ALLOW : EFFECT_DENY, Map.of("allowed", allowed)));
    }

    private void saveFieldPermissions(SystemMemberContext context, Long roleId, String version,
                                      Map<String, String> permissions) {
        permissions.forEach((fieldCode, mode) -> {
            saveRolePermission(roleId, version, TARGET_FIELD, fieldCode, EFFECT_ALLOW, Map.of("mode", mode));
            NumericFieldKey key = parseNumericFieldKey(fieldCode);
            if (Objects.nonNull(key)) {
                PlatRoleFieldPermission fieldPermission = new PlatRoleFieldPermission();
                fieldPermission.setRoleId(roleId);
                fieldPermission.setSystemId(context.systemId());
                fieldPermission.setTenantId(context.tenantId());
                fieldPermission.setModuleId(key.moduleId());
                fieldPermission.setFieldId(key.fieldId());
                fieldPermission.setPermissionMode(mode);
                fieldPermission.setMaskRule(toJson(Map.of("fieldCode", fieldCode, "mode", mode)));
                fieldPermission.setDisabledReason("HIDDEN".equalsIgnoreCase(mode) ? "字段被角色权限隐藏" : null);
                fieldPermission.setCreatedAt(LocalDateTime.now());
                roleFieldPermissionBaseService.saveEntity(fieldPermission);
            }
        });
    }

    private void saveDataScopeRules(SystemMemberContext context, Long roleId, List<Map<String, Object>> rules) {
        for (Map<String, Object> rule : rules) {
            PlatDataScopeRule entity = new PlatDataScopeRule();
            entity.setRoleId(roleId);
            entity.setSystemId(context.systemId());
            entity.setTenantId(context.tenantId());
            entity.setModuleId(parseOptionalId(stringValue(rule.get("moduleId"))));
            entity.setScopeType(safeText(stringValue(rule.get("type")), safeText(stringValue(rule.get("scopeType")), "CUSTOM")));
            entity.setScopeExpression(toJson(rule));
            entity.setCreatedAt(LocalDateTime.now());
            dataScopeRuleBaseService.saveEntity(entity);
        }
    }

    private void saveDenyPolicies(SystemMemberContext context, Long roleId, String version, List<String> policies) {
        for (String policy : policies) {
            saveRolePermission(roleId, version, TARGET_DENY, policy, EFFECT_DENY, Map.of("policyCode", policy));
            ensureDenyPolicy(context, policy);
        }
    }

    private void ensureDenyPolicy(SystemMemberContext context, String policyCode) {
        if (!StringUtils.hasText(policyCode)) {
            return;
        }
        PlatDenyPolicy existing = denyPolicyBaseService.getOne(new LambdaQueryWrapper<PlatDenyPolicy>()
                .eq(PlatDenyPolicy::getSystemId, context.systemId())
                .eq(PlatDenyPolicy::getTenantId, context.tenantId())
                .eq(PlatDenyPolicy::getPolicyCode, policyCode)
                .last("LIMIT 1"), false);
        if (Objects.nonNull(existing)) {
            return;
        }
        PlatDenyPolicy policy = new PlatDenyPolicy();
        policy.setSystemId(context.systemId());
        policy.setTenantId(context.tenantId());
        policy.setPolicyCode(policyCode);
        policy.setPolicyName(policyCode);
        policy.setTargetType(TARGET_ACTION);
        policy.setTargetCode(policyCode);
        policy.setConditionPayload(toJson(Map.of("source", "role_permission_save")));
        policy.setStatus(ENABLED);
        policy.setCreatedAt(LocalDateTime.now());
        denyPolicyBaseService.saveEntity(policy);
    }

    private void saveRolePermission(Long roleId, String version, String targetType, String targetCode, String effect,
                                    Map<String, Object> payload) {
        if (!StringUtils.hasText(targetCode)) {
            return;
        }
        PlatRolePermission entity = new PlatRolePermission();
        entity.setRoleId(roleId);
        entity.setPermissionVersion(version);
        entity.setTargetType(targetType);
        entity.setTargetCode(targetCode);
        entity.setEffect(effect);
        entity.setPermissionPayload(toJson(payload));
        entity.setCreatedAt(LocalDateTime.now());
        rolePermissionBaseService.saveEntity(entity);
    }

    private String latestPermissionVersion(SystemMemberContext context) {
        PlatPermissionVersion version = permissionVersionBaseService.getOne(new LambdaQueryWrapper<PlatPermissionVersion>()
                .eq(PlatPermissionVersion::getScope, SCOPE_SYSTEM)
                .eq(PlatPermissionVersion::getSystemId, context.systemId())
                .eq(PlatPermissionVersion::getTenantId, context.tenantId())
                .orderByDesc(PlatPermissionVersion::getPublishedAt)
                .last("LIMIT 1"), false);
        return Objects.isNull(version) ? "perm_initial" : version.getPermissionVersion();
    }

    private NumericFieldKey parseNumericFieldKey(String fieldCode) {
        if (!StringUtils.hasText(fieldCode)) {
            return null;
        }
        String normalized = fieldCode.replace(":", ".").replace("/", ".");
        String[] parts = normalized.split("\\.");
        if (parts.length < 2) {
            return null;
        }
        Long moduleId = parseOptionalId(parts[0]);
        Long fieldId = parseOptionalId(parts[1]);
        return Objects.nonNull(moduleId) && Objects.nonNull(fieldId) ? new NumericFieldKey(moduleId, fieldId) : null;
    }

    private Long parseOptionalId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String newPermissionVersion() {
        return "perm_" + System.currentTimeMillis() + "_" + RequestContext.current().traceId()
                .replaceAll("[^A-Za-z0-9]", "")
                .substring(0, Math.min(8, RequestContext.current().traceId().replaceAll("[^A-Za-z0-9]", "").length()));
    }

    private Map<String, Boolean> safeBooleanMap(Map<String, Boolean> value) {
        return Objects.isNull(value) ? Map.of() : new LinkedHashMap<>(value);
    }

    private Map<String, String> safeStringMap(Map<String, String> value) {
        return Objects.isNull(value) ? Map.of() : new LinkedHashMap<>(value);
    }

    private List<Map<String, Object>> safeListMap(List<Map<String, Object>> value) {
        return Objects.isNull(value) ? List.of() : value;
    }

    private List<String> safeStringList(List<String> value) {
        return Objects.isNull(value) ? List.of() : value.stream().filter(StringUtils::hasText).toList();
    }

    private Map<String, Boolean> asBooleanMap(Object value) {
        return convert(value, MAP_BOOLEAN_TYPE, Map.of());
    }

    private Map<String, String> asStringMap(Object value) {
        return convert(value, MAP_STRING_TYPE, Map.of());
    }

    private List<Map<String, Object>> asListMap(Object value) {
        return convert(value, LIST_MAP_TYPE, List.of());
    }

    private List<String> asStringList(Object value) {
        return convert(value, STRING_LIST_TYPE, List.of());
    }

    private String safeText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    private String stringValue(Object value) {
        return Objects.isNull(value) ? null : String.valueOf(value);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new BusinessException(CommonErrorCode.OPS_INTERNAL_ERROR, "权限配置序列化失败");
        }
    }

    private <T> T readJson(String json, TypeReference<T> type, T fallback) {
        if (!StringUtils.hasText(json)) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }

    private <T> T convert(Object value, TypeReference<T> type, T fallback) {
        if (Objects.isNull(value)) {
            return fallback;
        }
        return objectMapper.convertValue(value, type);
    }

    private record NumericFieldKey(Long moduleId, Long fieldId) {
    }

    private record PermissionAggregate(String permissionVersion, List<String> sourceRoleIds, List<String> denyPolicyIds,
                                       Map<String, String> fieldPermissions, Map<String, Boolean> actionPermissions,
                                       Map<String, Object> dataScope, List<Map<String, Object>> explain) {
    }
}
