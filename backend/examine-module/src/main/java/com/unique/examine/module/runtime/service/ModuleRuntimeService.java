package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import com.unique.examine.module.runtime.api.RuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ModuleRuntimeService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ModuleRuntimeService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public RuntimeViews.Navigation navigation(RuntimeSession session) {
        var active = active(session.systemId());
        var groups = new ArrayList<RuntimeViews.Group>();
        var modulesByGroup = new LinkedHashMap<String, List<RuntimeViews.Module>>();
        for (var module : sorted(active.snapshot().path("modules"))) {
            if (!enabled(module)) continue;
            var code = module.path("module_code").asText();
            var permission = "module." + code + ".view";
            if (!session.permissions().contains(permission)) continue;
            var pageCode = defaultListPage(active.snapshot(), module.path("id").asText());
            modulesByGroup.computeIfAbsent(module.path("group_id").asText(), ignored -> new ArrayList<>())
                    .add(new RuntimeViews.Module(module.path("id").asText(), code,
                            module.path("module_name").asText(), nullable(module, "icon_key"),
                            module.path("sort_order").asInt(), permission, pageCode));
        }
        for (var group : sorted(active.snapshot().path("groups"))) {
            if (!enabled(group)) continue;
            var modules = modulesByGroup.getOrDefault(group.path("id").asText(), List.of());
            if (modules.isEmpty()) continue;
            groups.add(new RuntimeViews.Group(group.path("id").asText(), group.path("group_code").asText(),
                    group.path("group_name").asText(), nullable(group, "icon_key"),
                    group.path("sort_order").asInt(), modules));
        }
        return new RuntimeViews.Navigation(active.versionId(), active.versionNo(), groups);
    }

    public RuntimeViews.Definition definition(RuntimeSession session, String moduleCode) {
        var active = active(session.systemId());
        JsonNode module = null;
        for (var candidate : active.snapshot().path("modules")) {
            if (enabled(candidate) && moduleCode.equals(candidate.path("module_code").asText())) {
                module = candidate;
                break;
            }
        }
        if (module == null) {
            throw new BusinessException("MODULE_NOT_PUBLISHED", "模块未发布或已停用", HttpStatus.NOT_FOUND);
        }
        var viewPermission = "module." + moduleCode + ".view";
        if (!session.permissions().contains(viewPermission)) {
            throw new BusinessException("PERMISSION_DENIED", "当前成员无权访问该模块", HttpStatus.FORBIDDEN);
        }
        var moduleId = module.path("id").asText();
        var fieldPermissions = permissionsByResource(active.snapshot(), "FIELD");
        var visibleFields = new HashSet<String>();
        var fields = objectMapper.createArrayNode();
        for (var field : sorted(active.snapshot().path("fields"))) {
            if (!moduleId.equals(field.path("module_id").asText()) || !enabled(field)) continue;
            var id = field.path("id").asText();
            var permission = FieldPermissionCodes.read(moduleCode, field.path("field_code").asText());
            var declared = fieldPermissions.getOrDefault(id, Set.of());
            if (fieldReadable(declared, permission, session.permissions())) {
                visibleFields.add(id);fields.add(field);
            }
        }
        var visibleActions = new HashSet<String>();
        var actions = objectMapper.createArrayNode();
        for (var action : sorted(active.snapshot().path("actions"))) {
            if (!moduleId.equals(action.path("module_id").asText()) || !enabled(action)) continue;
            if (session.permissions().contains(action.path("permission_code").asText())) {
                visibleActions.add(action.path("id").asText());actions.add(action);
            }
        }
        var pageIds = new HashSet<String>();
        var pages = objectMapper.createArrayNode();
        for (var page : sorted(active.snapshot().path("pages"))) {
            if (moduleId.equals(page.path("module_id").asText()) && enabled(page)) {
                pageIds.add(page.path("id").asText());pages.add(page);
            }
        }
        var components = objectMapper.createArrayNode();
        for (var component : sorted(active.snapshot().path("components"))) {
            if (!pageIds.contains(component.path("page_id").asText())) continue;
            var fieldId = nullable(component, "field_id");
            var actionId = component.path("property_json").path("actionId").asText(null);
            if ((fieldId == null || visibleFields.contains(fieldId))
                    && (actionId == null || visibleActions.contains(actionId))) components.add(component);
        }
        var rules = filteredRules(active.snapshot(), moduleId, visibleFields, visibleActions);
        var dictionaryIds = new HashSet<String>();
        fields.forEach(field -> { var id = nullable(field, "dictionary_id"); if (id != null) dictionaryIds.add(id); });
        var dictionaries = filterByIds(active.snapshot().path("dictionaries"), dictionaryIds);
        var items = objectMapper.createArrayNode();
        active.snapshot().path("dictionaryItems").forEach(item -> {
            if (dictionaryIds.contains(item.path("dictionary_id").asText()) && enabled(item)) items.add(item);
        });
        return new RuntimeViews.Definition(active.versionId(), active.versionNo(), module, fields, pages,
                components, actions, rules, dictionaries, items, recordsAvailable(active.snapshot(), moduleId));
    }

    private ArrayNode filteredRules(JsonNode snapshot, String moduleId, Set<String> fields, Set<String> actions) {
        var deniedRules = new HashSet<String>();
        snapshot.path("references").forEach(ref -> {
            if (!"RULE".equals(ref.path("source_type").asText())) return;
            var type = ref.path("target_type").asText();
            var target = ref.path("target_id").asText();
            if (("FIELD".equals(type) && !fields.contains(target))
                    || ("ACTION".equals(type) && !actions.contains(target))) {
                deniedRules.add(ref.path("source_id").asText());
            }
        });
        var result = objectMapper.createArrayNode();
        for (var rule : sorted(snapshot.path("rules"))) {
            if (moduleId.equals(rule.path("module_id").asText()) && enabled(rule)
                    && !deniedRules.contains(rule.path("id").asText())) result.add(rule);
        }
        return result;
    }

    static Map<String, Set<String>> permissionsByResource(JsonNode snapshot, String type) {
        var result = new HashMap<String, Set<String>>();
        snapshot.path("permissions").forEach(permission -> {
            if (type.equals(permission.path("resource_type").asText()) && enabled(permission)) {
                result.computeIfAbsent(permission.path("resource_id").asText(), ignored -> new HashSet<>())
                        .add(permission.path("permission_code").asText());
            }
        });
        return result;
    }

    static boolean fieldReadable(Set<String> declared, String readPermission, Set<String> effective) {
        return !declared.contains(readPermission) || effective.contains(readPermission);
    }

    private ArrayNode filterByIds(JsonNode source, Set<String> ids) {
        var result = objectMapper.createArrayNode();
        source.forEach(node -> { if (ids.contains(node.path("id").asText()) && enabled(node)) result.add(node); });
        return result;
    }

    private String defaultListPage(JsonNode snapshot, String moduleId) {
        for (var page : snapshot.path("pages")) {
            if (moduleId.equals(page.path("module_id").asText()) && enabled(page)
                    && "LIST".equals(page.path("page_type").asText()) && flag(page, "is_default")) {
                return page.path("page_code").asText();
            }
        }
        return null;
    }

    private boolean recordsAvailable(JsonNode snapshot, String moduleId) {
        for (var field : snapshot.path("fields")) {
            if (moduleId.equals(field.path("module_id").asText()) && enabled(field)
                    && flag(field, "is_required")
                    && !RecordRuntimeService.supportsFieldType(field.path("field_type").asText())) {
                return false;
            }
        }
        return true;
    }

    private Active active(long systemId) {
        var rows = jdbc.query("SELECT v.id,v.version_no,v.snapshot_json FROM un_module_config_root r JOIN un_module_config_version v ON v.system_id=r.system_id AND v.id=r.active_version_id WHERE r.system_id=?",
                (rs, n) -> new Active(Long.toString(rs.getLong("id")), Long.toString(rs.getLong("version_no")),
                        parse(rs.getString("snapshot_json"))), systemId);
        if (rows.isEmpty()) {
            throw new BusinessException("MODULE_NOT_PUBLISHED", "系统尚未发布模块配置", HttpStatus.NOT_FOUND);
        }
        return rows.getFirst();
    }

    private JsonNode parse(String json) {
        try { return objectMapper.readTree(json); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Published snapshot is invalid", exception); }
    }

    private static List<JsonNode> sorted(JsonNode array) {
        var result = new ArrayList<JsonNode>();array.forEach(result::add);
        result.sort(Comparator.comparingInt((JsonNode n) -> n.path("sort_order").asInt())
                .thenComparing(n -> n.path("id").asText()));
        return result;
    }

    private static boolean enabled(JsonNode node) { return "ENABLED".equals(node.path("desired_status").asText()); }
    private static boolean flag(JsonNode node,String key){var value=node.path(key);return value.isBoolean()?value.asBoolean():value.asInt()!=0;}
    private static String nullable(JsonNode node,String key){var value=node.get(key);return value==null||value.isNull()?null:value.asText();}
    private record Active(String versionId, String versionNo, JsonNode snapshot) { }
}
