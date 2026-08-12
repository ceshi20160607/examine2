package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.api.DynamicPermissionFacade;
import com.unique.examine.module.manage.api.FieldPermissionCodes;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ConfigSnapshotService {
    public static final int MAX_SNAPSHOT_BYTES = 2 * 1024 * 1024;

    private static final List<TableQuery> TABLES = List.of(
            new TableQuery("groups", "SELECT id,group_code,group_name,description,icon_key,sort_order,desired_status FROM un_module_group WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("modules", "SELECT id,group_id,module_code,module_name,description,icon_key,sort_order,desired_status,allow_comments,allow_team FROM un_module_definition WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("dictionaries", "SELECT id,dictionary_code,dictionary_name,dictionary_type,category,description,desired_status FROM un_module_dictionary WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("dictionaryItems", "SELECT id,dictionary_id,parent_id,item_code,item_label,semantic_key,color_value,icon_key,sort_order,depth_level,depth_path,is_default,desired_status FROM un_module_dictionary_item WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("dictionaryClosure", "SELECT dictionary_id,ancestor_id,descendant_id,depth FROM un_module_dictionary_item_closure WHERE system_id=? ORDER BY dictionary_id,ancestor_id,descendant_id"),
            new TableQuery("fields", "SELECT id,module_id,dictionary_id,target_module_id,field_code,field_name,field_type,sort_order,is_required,is_hidden,is_readonly,is_searchable,is_filterable,show_in_list,show_in_detail,index_mode,desired_status,property_json FROM un_module_field WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("pages", "SELECT id,module_id,page_code,page_name,page_type,is_default,desired_status,layout_json FROM un_module_page WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("components", "SELECT c.id,c.page_id,c.parent_component_id,c.field_id,c.component_key,c.component_type,c.sort_order,c.grid_row,c.grid_column,c.grid_span,c.property_json FROM un_module_page_component c JOIN un_module_page p ON p.system_id=c.system_id AND p.id=c.page_id AND p.deleted_at IS NULL JOIN un_module_definition m ON m.system_id=p.system_id AND m.id=p.module_id AND m.deleted_at IS NULL WHERE c.system_id=? AND c.deleted_at IS NULL ORDER BY c.id"),
            new TableQuery("actions", "SELECT id,module_id,action_code,action_name,action_type,placement,permission_code,confirm_message,sort_order,desired_status,property_json FROM un_module_action WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("rules", "SELECT id,module_id,rule_code,rule_name,rule_type,priority,condition_json,effect_json,desired_status FROM un_module_rule WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("permissions", "SELECT id,module_id,resource_type,resource_id,permission_code,permission_name,permission_type,desired_status FROM un_module_permission WHERE system_id=? AND deleted_at IS NULL ORDER BY id"),
            new TableQuery("references", "SELECT source_type,source_id,target_type,target_id,relation_type,property_path FROM un_module_config_reference WHERE system_id=? ORDER BY source_type,source_id,target_type,target_id,relation_type,property_path")
    );

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ConfigSnapshotService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Snapshot create(long systemId) {
        var root = objectMapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("systemId", Long.toString(systemId));
        for (var table : TABLES) {
            root.set(table.name(), rows(table.sql(), systemId));
        }
        try {
            var json = objectMapper.writeValueAsString(root);
            var bytes = json.getBytes(StandardCharsets.UTF_8).length;
            return new Snapshot(root, json, ConfigMutationSupport.sha256(json), bytes);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize configuration snapshot", exception);
        }
    }

    public List<DynamicPermissionFacade.Definition> permissionDefinitions(long systemId) {
        return jdbc.query(
                        "SELECT permission_code,permission_name,permission_type,resource_type,desired_status "
                                + "FROM un_module_permission WHERE system_id=? AND desired_status IN ('ENABLED','DISABLED') "
                                + "AND deleted_at IS NULL ORDER BY permission_code",
                        (rs, rowNum) -> new PermissionDefinitionRow(
                                rs.getString("permission_code"), rs.getString("permission_name"),
                                rs.getString("permission_type"), rs.getString("resource_type"),
                                rs.getString("desired_status")), systemId)
                .stream()
                .filter(ConfigSnapshotService::registerable)
                .map(row -> new DynamicPermissionFacade.Definition(row.code(), row.name(), row.permissionType()))
                .toList();
    }

    static boolean registerable(PermissionDefinitionRow row) {
        return "ENABLED".equals(row.status())
                || "DISABLED".equals(row.status()) && "FIELD".equals(row.resourceType())
                && "FIELD".equals(row.permissionType()) && FieldPermissionCodes.isGeneric(row.code());
    }

    private ArrayNode rows(String sql, long systemId) {
        var result = objectMapper.createArrayNode();
        var rows = jdbc.queryForList(sql, systemId);
        for (var source : rows) {
            var normalized = new LinkedHashMap<String, Object>();
            source.forEach((key, value) -> normalized.put(key, normalize(key, value)));
            result.add(objectMapper.valueToTree(normalized));
        }
        return result;
    }

    private Object normalize(String key, Object value) {
        if (value == null) {
            return null;
        }
        if (key.endsWith("_json") && value instanceof String json) {
            try {
                return objectMapper.readTree(json);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Stored JSON is invalid at " + key, exception);
            }
        }
        if (value instanceof Long || value instanceof BigInteger) {
            return value.toString();
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return value;
    }

    public record Snapshot(JsonNode node, String json, String checksum, int sizeBytes) { }

    record PermissionDefinitionRow(
            String code, String name, String permissionType, String resourceType, String status) { }

    private record TableQuery(String name, String sql) { }
}
