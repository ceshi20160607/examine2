package com.unique.unexamine.authorization.manage;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import com.unique.unexamine.system.base.entity.SystemMemberRole;
import com.unique.unexamine.system.base.entity.SystemRole;
import com.unique.unexamine.system.base.entity.SystemRolePermission;
import com.unique.unexamine.system.base.service.SystemMemberRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRoleBaseService;
import com.unique.unexamine.system.base.service.SystemRolePermissionBaseService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class PermissionResolver {
    private final SystemMemberRoleBaseService memberRoleService;
    private final SystemRoleBaseService roleService;
    private final SystemRolePermissionBaseService permissionService;
    private final ObjectMapper objectMapper;

    public PermissionResolver(
            SystemMemberRoleBaseService memberRoleService,
            SystemRoleBaseService roleService,
            SystemRolePermissionBaseService permissionService,
            ObjectMapper objectMapper) {
        this.memberRoleService = memberRoleService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.objectMapper = objectMapper;
    }

    public ResolvedPermissions resolve(Long tenantId, Long tenantMemberId) {
        List<Long> assignedRoleIds = memberRoleService.selectList(Wrappers.<SystemMemberRole>lambdaQuery()
                        .eq(SystemMemberRole::getTenantId, tenantId)
                        .eq(SystemMemberRole::getTenantMemberId, tenantMemberId))
                .stream().map(SystemMemberRole::getRoleId).distinct().sorted().toList();
        if (assignedRoleIds.isEmpty()) {
            return new ResolvedPermissions(List.of(), List.of(), Map.of());
        }
        Set<Long> activeRoleIds = new LinkedHashSet<>(roleService.selectList(Wrappers.<SystemRole>lambdaQuery()
                        .eq(SystemRole::getTenantId, tenantId)
                        .eq(SystemRole::getStatus, "ACTIVE")
                        .in(SystemRole::getId, assignedRoleIds))
                .stream().map(SystemRole::getId).sorted().toList());
        if (activeRoleIds.isEmpty()) {
            return new ResolvedPermissions(List.of(), List.of(), Map.of());
        }

        List<SystemRolePermission> rows = permissionService.selectList(Wrappers.<SystemRolePermission>lambdaQuery()
                .in(SystemRolePermission::getRoleId, activeRoleIds));
        Map<String, MutablePermission> permissions = new LinkedHashMap<>();
        Map<String, List<DataScopeTerm>> scopes = new LinkedHashMap<>();
        rows.stream().sorted(Comparator.comparing(SystemRolePermission::getId)).forEach(row -> {
            String key = key(row);
            permissions.computeIfAbsent(key, ignored -> new MutablePermission(
                    row.getResourceType(), row.getResourceCode(), row.getActionCode()))
                    .roleIds.add(row.getRoleId());
            scopes.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new DataScopeTerm(
                    row.getDataScopeType(), parseCondition(row.getDataScopeJson()), List.of(row.getRoleId())));
        });

        List<PermissionGrant> grants = permissions.values().stream()
                .map(value -> new PermissionGrant(value.resourceType, value.resourceCode, value.actionCode,
                        value.roleIds.stream().sorted().toList()))
                .sorted(Comparator.comparing(PermissionGrant::key))
                .toList();
        Map<String, DataScopeExpression> mergedScopes = new LinkedHashMap<>();
        scopes.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .forEach(entry -> mergedScopes.put(entry.getKey(), merge(entry.getValue())));
        return new ResolvedPermissions(activeRoleIds.stream().toList(), grants, mergedScopes);
    }

    private DataScopeExpression merge(List<DataScopeTerm> source) {
        Map<String, MutableTerm> unique = new LinkedHashMap<>();
        for (DataScopeTerm term : source) {
            String identity = term.type() + ":" + term.condition();
            MutableTerm value = unique.computeIfAbsent(identity, ignored -> new MutableTerm(term.type(), term.condition()));
            value.roleIds.addAll(term.roleIds());
        }
        if (containsType(unique, "ALL")) {
            return expression(unique, Set.of("ALL"));
        }
        if (containsType(unique, "DEPARTMENT_AND_DESCENDANTS")) {
            unique.entrySet().removeIf(entry -> Set.of("DEPARTMENT", "SELF").contains(entry.getValue().type));
        } else if (containsType(unique, "DEPARTMENT")) {
            unique.entrySet().removeIf(entry -> "SELF".equals(entry.getValue().type));
        }
        List<DataScopeTerm> terms = unique.values().stream()
                .map(term -> new DataScopeTerm(term.type, term.condition, term.roleIds.stream().sorted().toList()))
                .sorted(Comparator.comparing(DataScopeTerm::type).thenComparing(term -> term.condition().toString()))
                .toList();
        return new DataScopeExpression(terms.size() == 1 ? "SINGLE" : "UNION", terms);
    }

    private DataScopeExpression expression(Map<String, MutableTerm> terms, Set<String> includedTypes) {
        List<Long> roleIds = terms.values().stream()
                .filter(term -> includedTypes.contains(term.type))
                .flatMap(term -> term.roleIds.stream()).distinct().sorted().toList();
        return new DataScopeExpression("ALL", List.of(new DataScopeTerm("ALL", NullNode.getInstance(), roleIds)));
    }

    private boolean containsType(Map<String, MutableTerm> terms, String type) {
        return terms.values().stream().anyMatch(term -> type.equals(term.type));
    }

    private JsonNode parseCondition(String value) {
        if (value == null || value.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Invalid data scope JSON in persisted permission", exception);
        }
    }

    private String key(SystemRolePermission permission) {
        return permission.getResourceType() + ":" + permission.getResourceCode() + ":" + permission.getActionCode();
    }

    private static final class MutablePermission {
        private final String resourceType;
        private final String resourceCode;
        private final String actionCode;
        private final Set<Long> roleIds = new LinkedHashSet<>();

        private MutablePermission(String resourceType, String resourceCode, String actionCode) {
            this.resourceType = resourceType;
            this.resourceCode = resourceCode;
            this.actionCode = actionCode;
        }
    }

    private static final class MutableTerm {
        private final String type;
        private final JsonNode condition;
        private final Set<Long> roleIds = new LinkedHashSet<>();

        private MutableTerm(String type, JsonNode condition) {
            this.type = type;
            this.condition = condition;
        }
    }
}
