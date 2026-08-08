package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.api.EffectivePermissionFacade;
import com.unique.examine.module.manage.api.ConfigPreviewViews;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import com.unique.examine.module.manage.security.ConfigSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ConfigPreviewService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ConfigSnapshotService snapshots;
    private final EffectivePermissionFacade permissions;

    public ConfigPreviewService(JdbcTemplate jdbc, ObjectMapper objectMapper,
                                ConfigSnapshotService snapshots, EffectivePermissionFacade permissions) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.snapshots = snapshots;
        this.permissions = permissions;
    }

    public ConfigPreviewViews.PermissionPreview preview(ConfigSession session, long memberId, Long requestedTenantId) {
        var tenantId = requestedTenantId == null ? session.tenantId() : requestedTenantId;
        if (tenantId == null) {
            throw ConfigErrors.invalid("权限预览必须指定租户");
        }
        var evaluation = permissions.evaluateSystem(session.systemId(), tenantId, memberId);
        var draft = snapshots.create(session.systemId()).node();
        var root = previewRoot(jdbc.queryForList(
                "SELECT draft_revision,active_version_id FROM un_module_config_root WHERE system_id=?",
                session.systemId()));
        var activeVersionId = root.activeVersionId();
        var active = activeVersionId == null ? null : activeSnapshot(session.systemId(), Long.parseLong(activeVersionId));
        return new ConfigPreviewViews.PermissionPreview(
                Long.toString(memberId), Long.toString(tenantId), Long.toString(evaluation.epoch()), evaluation.root(),
                evaluation.roles().stream().map(role -> new ConfigPreviewViews.PreviewRole(
                        role.id(), role.code(), role.name(), role.publishedVersion())).toList(),
                evaluation.dataScopes().stream().map(scope -> new ConfigPreviewViews.PreviewDataScope(
                        scope.roleId(), scope.id(), scope.code(), scope.kind())).toList(),
                tree(activeVersionId, null, active, evaluation),
                tree(null, root.draftRevision(), draft, evaluation)
        );
    }

    static PreviewRoot previewRoot(List<Map<String, Object>> rows) {
        if (rows.isEmpty()) return new PreviewRoot("0", null);
        var row = rows.getFirst();
        var draftRevision = row.get("draft_revision");
        var activeVersionId = row.get("active_version_id");
        return new PreviewRoot(
                draftRevision == null ? "0" : draftRevision.toString(),
                activeVersionId == null ? null : activeVersionId.toString());
    }

    private ConfigPreviewViews.PreviewTree tree(String versionId, String revision, JsonNode snapshot,
                                                EffectivePermissionFacade.Evaluation evaluation) {
        if (snapshot == null) {
            return new ConfigPreviewViews.PreviewTree(versionId, revision, List.of());
        }
        var fields = namesByModule(snapshot.path("fields"), "field_code", "FIELD", snapshot, evaluation);
        var actions = namesByModule(snapshot.path("actions"), "action_code", "ACTION", snapshot, evaluation);
        var modulesByGroup = new HashMap<String, List<ConfigPreviewViews.PreviewModule>>();
        for (var module : sorted(snapshot.path("modules"))) {
            if (!enabled(module)) continue;
            var code = module.path("module_code").asText();
            if (!allowed(evaluation, "module." + code + ".view")) continue;
            var id = module.path("id").asText();
            modulesByGroup.computeIfAbsent(module.path("group_id").asText(), ignored -> new ArrayList<>()).add(
                    new ConfigPreviewViews.PreviewModule(id, code, module.path("module_name").asText(),
                            module.path("sort_order").asInt(), fields.getOrDefault(id, List.of()),
                            actions.getOrDefault(id, List.of())));
        }
        var groups = new ArrayList<ConfigPreviewViews.PreviewGroup>();
        for (var group : sorted(snapshot.path("groups"))) {
            if (!enabled(group)) continue;
            var modules = modulesByGroup.getOrDefault(group.path("id").asText(), List.of());
            if (!modules.isEmpty()) {
                groups.add(new ConfigPreviewViews.PreviewGroup(group.path("id").asText(),
                        group.path("group_code").asText(), group.path("group_name").asText(),
                        group.path("sort_order").asInt(), modules));
            }
        }
        return new ConfigPreviewViews.PreviewTree(versionId, revision, groups);
    }

    private Map<String, List<String>> namesByModule(JsonNode resources, String codeKey, String resourceType,
                                                     JsonNode snapshot, EffectivePermissionFacade.Evaluation evaluation) {
        var permissionByResource = new HashMap<String, Set<String>>();
        snapshot.path("permissions").forEach(permission -> {
            if (resourceType.equals(permission.path("resource_type").asText()) && enabled(permission)) {
                permissionByResource.computeIfAbsent(permission.path("resource_id").asText(),
                                ignored -> new HashSet<>())
                        .add(permission.path("permission_code").asText());
            }
        });
        var moduleCodes = new HashMap<String, String>();
        snapshot.path("modules").forEach(module -> moduleCodes.put(
                module.path("id").asText(), module.path("module_code").asText()));
        var result = new HashMap<String, List<String>>();
        for (var resource : sorted(resources)) {
            if (!enabled(resource)) continue;
            var permission = "FIELD".equals(resourceType)
                    ? FieldPermissionCodes.read(moduleCodes.get(resource.path("module_id").asText()),
                    resource.path(codeKey).asText())
                    : resource.path("permission_code").asText();
            var declared = permissionByResource.getOrDefault(resource.path("id").asText(), Set.of());
            if (allowedByExactPermission(
                    declared, permission, evaluation.permissions(), evaluation.root())) {
                result.computeIfAbsent(resource.path("module_id").asText(), ignored -> new ArrayList<>())
                        .add(resource.path(codeKey).asText());
            }
        }
        return result;
    }

    private JsonNode activeSnapshot(long systemId, long versionId) {
        var json = jdbc.queryForObject("SELECT snapshot_json FROM un_module_config_version WHERE system_id=? AND id=?",
                String.class, systemId, versionId);
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published snapshot is invalid", exception);
        }
    }

    private static boolean allowed(EffectivePermissionFacade.Evaluation evaluation, String permission) {
        return evaluation.root() || evaluation.permissions().contains(permission);
    }

    static boolean allowedByExactPermission(
            Set<String> declared, String permission, Set<String> effective, boolean root) {
        return !declared.contains(permission) || root || effective.contains(permission);
    }

    private static boolean enabled(JsonNode node) {
        return "ENABLED".equals(node.path("desired_status").asText());
    }

    private static List<JsonNode> sorted(JsonNode array) {
        var result = new ArrayList<JsonNode>();
        array.forEach(result::add);
        result.sort(Comparator.comparingInt((JsonNode node) -> node.path("sort_order").asInt())
                .thenComparing(node -> node.path("id").asText()));
        return result;
    }

    record PreviewRoot(String draftRevision, String activeVersionId) { }
}
