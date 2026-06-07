package com.unique.examine.module.manage.permission;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.unique.examine.core.context.RequestContext;
import com.unique.examine.core.permission.DataScopeRuleVO;
import com.unique.examine.core.permission.EffectivePermissionVO;
import com.unique.examine.core.permission.FieldPermissionVO;
import com.unique.examine.core.permission.PermissionSnapshotProvider;
import com.unique.examine.module.base.entity.MemberRole;
import com.unique.examine.module.base.entity.PermissionVersion;
import com.unique.examine.module.base.entity.RoleDataScope;
import com.unique.examine.module.base.entity.RoleFieldPermission;
import com.unique.examine.module.base.entity.RoleMenu;
import com.unique.examine.module.base.entity.RoleOpenapiScope;
import com.unique.examine.module.base.entity.RoleOperation;
import com.unique.examine.module.base.entity.SystemMenu;
import com.unique.examine.module.base.service.IMemberRoleService;
import com.unique.examine.module.base.service.IPermissionVersionService;
import com.unique.examine.module.base.service.IRoleDataScopeService;
import com.unique.examine.module.base.service.IRoleFieldPermissionService;
import com.unique.examine.module.base.service.IRoleMenuService;
import com.unique.examine.module.base.service.IRoleOpenapiScopeService;
import com.unique.examine.module.base.service.IRoleOperationService;
import com.unique.examine.module.base.service.ISystemMenuService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 基于系统成员角色表的权限快照提供器。
 */
@Component
@RequiredArgsConstructor
public class ModulePermissionSnapshotProvider implements PermissionSnapshotProvider {

    private static final long ACTIVE_DELETE_TOKEN = 0L;

    private static final long SYSTEM_LEVEL_TENANT_ID = 0L;

    private final IMemberRoleService memberRoleService;

    private final IRoleOperationService roleOperationService;

    private final IRoleOpenapiScopeService roleOpenapiScopeService;

    private final IRoleMenuService roleMenuService;

    private final ISystemMenuService systemMenuService;

    private final IRoleFieldPermissionService roleFieldPermissionService;

    private final IRoleDataScopeService roleDataScopeService;

    private final IPermissionVersionService permissionVersionService;

    @Override
    public boolean supports(RequestContext context) {
        return StringUtils.hasText(context.getSystemId()) && StringUtils.hasText(context.getMemberId());
    }

    @Override
    public EffectivePermissionVO load(RequestContext context) {
        Long systemId = Long.valueOf(context.getSystemId());
        Long tenantId = parseTenantId(context.getTenantId());
        Long memberId = Long.valueOf(context.getMemberId());
        Set<Long> roleIds = roleIds(systemId, memberId);
        if (roleIds.isEmpty()) {
            return EffectivePermissionVO.empty();
        }
        return EffectivePermissionVO.builder()
                .systemId(context.getSystemId())
                .tenantId(context.getTenantId())
                .memberId(context.getMemberId())
                .roles(toStringSet(roleIds))
                .menus(menuCodes(systemId, roleIds))
                .operations(operationCodes(systemId, roleIds))
                .openapiScopes(openapiScopes(systemId, tenantId, roleIds))
                .fieldPermissions(fieldPermissions(systemId, tenantId, roleIds))
                .dataScopes(dataScopes(systemId, tenantId, roleIds))
                .version(permissionVersion(systemId, tenantId))
                .build();
    }

    private Set<Long> roleIds(Long systemId, Long memberId) {
        return memberRoleService.lambdaQuery()
                .eq(MemberRole::getSystemId, systemId)
                .eq(MemberRole::getMemberId, memberId)
                .eq(MemberRole::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(MemberRole::getRoleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> operationCodes(Long systemId, Set<Long> roleIds) {
        return roleOperationService.lambdaQuery()
                .eq(RoleOperation::getSystemId, systemId)
                .in(RoleOperation::getRoleId, roleIds)
                .eq(RoleOperation::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleOperation::getOperationCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> openapiScopes(Long systemId, Long tenantId, Set<Long> roleIds) {
        return roleOpenapiScopeService.lambdaQuery()
                .eq(RoleOpenapiScope::getSystemId, systemId)
                .in(RoleOpenapiScope::getTenantId, List.of(SYSTEM_LEVEL_TENANT_ID, tenantId))
                .in(RoleOpenapiScope::getRoleId, roleIds)
                .eq(RoleOpenapiScope::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleOpenapiScope::getScopeCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> menuCodes(Long systemId, Set<Long> roleIds) {
        Set<Long> menuIds = roleMenuService.lambdaQuery()
                .eq(RoleMenu::getSystemId, systemId)
                .in(RoleMenu::getRoleId, roleIds)
                .eq(RoleMenu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(RoleMenu::getMenuId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (menuIds.isEmpty()) {
            return Set.of();
        }
        return systemMenuService.lambdaQuery()
                .eq(SystemMenu::getSystemId, systemId)
                .in(SystemMenu::getId, menuIds)
                .eq(SystemMenu::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(SystemMenu::getCode)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, FieldPermissionVO> fieldPermissions(Long systemId, Long tenantId, Set<Long> roleIds) {
        return roleFieldPermissionService.lambdaQuery()
                .eq(RoleFieldPermission::getSystemId, systemId)
                .in(RoleFieldPermission::getTenantId, List.of(SYSTEM_LEVEL_TENANT_ID, tenantId))
                .in(RoleFieldPermission::getRoleId, roleIds)
                .eq(RoleFieldPermission::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .filter(permission -> StringUtils.hasText(permission.getFieldCode()))
                .collect(Collectors.groupingBy(RoleFieldPermission::getFieldCode, Collectors.collectingAndThen(
                        Collectors.toList(), this::mergeFieldPermission)));
    }

    private FieldPermissionVO mergeFieldPermission(List<RoleFieldPermission> permissions) {
        String fieldCode = permissions.get(0).getFieldCode();
        return FieldPermissionVO.builder()
                .fieldCode(fieldCode)
                .visible(anyTrue(permissions, RoleFieldPermission::getVisible))
                .writable(anyTrue(permissions, RoleFieldPermission::getWritable))
                .exportPlain(anyTrue(permissions, RoleFieldPermission::getExportPlain))
                .openapiReadable(anyTrue(permissions, RoleFieldPermission::getOpenapiReadable))
                .openapiWritable(anyTrue(permissions, RoleFieldPermission::getOpenapiWritable))
                .build();
    }

    private List<DataScopeRuleVO> dataScopes(Long systemId, Long tenantId, Set<Long> roleIds) {
        return roleDataScopeService.lambdaQuery()
                .eq(RoleDataScope::getSystemId, systemId)
                .in(RoleDataScope::getTenantId, List.of(SYSTEM_LEVEL_TENANT_ID, tenantId))
                .in(RoleDataScope::getRoleId, roleIds)
                .eq(RoleDataScope::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .map(this::toDataScopeRule)
                .toList();
    }

    private DataScopeRuleVO toDataScopeRule(RoleDataScope rule) {
        return DataScopeRuleVO.builder()
                .resourceType(rule.getResourceType())
                .resourceId(String.valueOf(rule.getResourceId()))
                .scopeType(rule.getScopeType())
                .deptIds(parseJsonIds(rule.getDeptIdsJson()))
                .memberIds(parseJsonIds(rule.getMemberIdsJson()))
                .customConditions(rule.getCustomConditions())
                .minVisibleRule(rule.getMinVisibleRule())
                .build();
    }

    private String permissionVersion(Long systemId, Long tenantId) {
        return permissionVersionService.lambdaQuery()
                .eq(PermissionVersion::getSystemId, systemId)
                .in(PermissionVersion::getTenantId, List.of(SYSTEM_LEVEL_TENANT_ID, tenantId))
                .eq(PermissionVersion::getDeleteToken, ACTIVE_DELETE_TOKEN)
                .list()
                .stream()
                .max(Comparator.comparing(PermissionVersion::getVersionNo,
                        Comparator.nullsLast(Long::compareTo)))
                .map(PermissionVersion::getVersionNo)
                .map(String::valueOf)
                .orElse("0");
    }

    private Long parseTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? Long.valueOf(tenantId) : SYSTEM_LEVEL_TENANT_ID;
    }

    private Set<String> toStringSet(Collection<Long> values) {
        return values.stream()
                .map(String::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> parseJsonIds(String json) {
        if (!StringUtils.hasText(json)) {
            return Set.of();
        }
        return java.util.Arrays.stream(json.replace("[", "")
                        .replace("]", "")
                        .replace("\"", "")
                        .split(","))
                .map(String::strip)
                .filter(StringUtils::hasText)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean anyTrue(List<RoleFieldPermission> permissions,
            java.util.function.Function<RoleFieldPermission, Byte> getter) {
        return permissions.stream().anyMatch(permission -> Byte.valueOf((byte) 1).equals(getter.apply(permission)));
    }
}
