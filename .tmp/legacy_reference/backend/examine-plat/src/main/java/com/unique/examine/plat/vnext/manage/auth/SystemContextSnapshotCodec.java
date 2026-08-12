package com.unique.examine.plat.vnext.manage.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.plat.vnext.base.entity.ContextSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Strict, generic SYSTEM snapshot serialization and P1-03-compatible restoration. */
public final class SystemContextSnapshotCodec {
    private static final Set<String> ROLE_FIELDS = Set.of("id", "code", "name", "publishedVersion");
    private static final Set<String> SCOPE_FIELDS = Set.of("roleId", "id", "code", "kind", "restrictedMode");
    private static final Set<String> LEGACY_SCOPE_FIELDS = Set.of("roleId", "id", "code", "kind");
    private final ObjectMapper objectMapper;

    public SystemContextSnapshotCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize SYSTEM authorization snapshot", exception);
        }
    }

    public SystemSnapshot restore(ContextSession context) {
        try {
            var roles = objectMapper.readTree(context.getRoleSnapshotJson());
            var scopes = objectMapper.readTree(context.getDataScopeSnapshotJson());
            require(roles.isArray() && !roles.isEmpty() && scopes.isArray() && !scopes.isEmpty(),
                    "SYSTEM authorization snapshot is incomplete");
            var roleIds = new ArrayList<String>();
            var roleNames = new ArrayList<String>();
            roles.forEach(role -> readRole(role, roleIds, roleNames));
            require(new HashSet<>(roleIds).size() == roleIds.size(), "SYSTEM role snapshot IDs are duplicated");
            roleIds.sort(Comparator.comparingLong(Long::parseLong));

            var first = scopes.get(0);
            var restrictedMode = mode(first);
            var dataScope = dataScope(first);
            var scopeRoleIds = new HashSet<String>();
            scopes.forEach(scope -> readScope(scope, dataScope, restrictedMode, scopeRoleIds));
            require(scopeRoleIds.equals(Set.copyOf(roleIds)),
                    "SYSTEM role and data scope snapshots do not match");
            return new SystemSnapshot(roleIds, roleNames, dataScope, restrictedMode);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot read SYSTEM authorization snapshot", exception);
        }
    }

    private static void readRole(JsonNode role, List<String> roleIds, List<String> roleNames) {
        require(fields(role).equals(ROLE_FIELDS), "SYSTEM role snapshot shape is invalid");
        var id = required(role, "id");
        require(Long.parseLong(id) > 0, "SYSTEM role snapshot ID is invalid");
        require(!required(role, "code").isBlank() && !required(role, "name").isBlank(),
                "SYSTEM role snapshot identity is incomplete");
        require(role.path("publishedVersion").canConvertToLong()
                        && role.path("publishedVersion").asLong() > 0,
                "SYSTEM role snapshot version is invalid");
        roleIds.add(id);
        roleNames.add(role.get("name").asText());
    }

    private static void readScope(
            JsonNode scope, DataScopeContext expected, String restrictedMode, Set<String> roleIds
    ) {
        var fields = fields(scope);
        require(fields.equals(SCOPE_FIELDS) || fields.equals(LEGACY_SCOPE_FIELDS),
                "SYSTEM data scope snapshot shape is invalid");
        var current = dataScope(scope);
        require(expected.equals(current) && restrictedMode.equals(mode(scope)),
                "SYSTEM data scope snapshots are inconsistent");
        var roleId = required(scope, "roleId");
        require(Long.parseLong(roleId) > 0 && roleIds.add(roleId),
                "SYSTEM data scope role ID is invalid or duplicated");
    }

    private static DataScopeContext dataScope(JsonNode node) {
        var value = new DataScopeContext(required(node, "id"), required(node, "code"), required(node, "kind"));
        require(Long.parseLong(value.id()) > 0 && !value.code().isBlank() && !value.kind().isBlank(),
                "SYSTEM data scope snapshot is incomplete");
        return value;
    }

    private static String mode(JsonNode node) {
        var value = node.hasNonNull("restrictedMode") ? node.get("restrictedMode").asText() : "NONE";
        require("NONE".equals(value) || "ADMIN_SETTINGS_ONLY".equals(value),
                "SYSTEM restricted mode is invalid");
        return value;
    }

    private static String required(JsonNode node, String field) {
        require(node.hasNonNull(field), "SYSTEM snapshot field is missing: " + field);
        return node.get(field).asText();
    }

    private static Set<String> fields(JsonNode node) {
        var result = new HashSet<String>();
        node.fieldNames().forEachRemaining(result::add);
        return Set.copyOf(result);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    public record SystemSnapshot(
            List<String> roleIds, List<String> roleNames,
            DataScopeContext dataScope, String restrictedMode
    ) {
        public SystemSnapshot {
            roleIds = List.copyOf(roleIds);
            roleNames = List.copyOf(roleNames);
        }

        static SystemSnapshot platform() {
            return new SystemSnapshot(List.of(), List.of(), null, "NONE");
        }
    }
}
