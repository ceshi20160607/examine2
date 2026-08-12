package com.unique.examine.module.manage.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.id.IdService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class RuntimeSchemaProjectionService {
    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final ObjectMapper objectMapper;
    private final DerivedFieldContractService derivedFields;

    public RuntimeSchemaProjectionService(JdbcTemplate jdbc, IdService ids, ObjectMapper objectMapper,
                                          DerivedFieldContractService derivedFields) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.objectMapper = objectMapper;
        this.derivedFields = derivedFields;
    }

    public void project(long systemId, long schemaVersionId, JsonNode snapshot, LocalDateTime createdAt) {
        var existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_module_runtime_schema_module WHERE system_id=? AND schema_version_id=?",
                Long.class, systemId, schemaVersionId);
        if (existing != null && existing > 0) {
            return;
        }

        var modules = byId(snapshot.path("modules"));
        var fields = byId(snapshot.path("fields"));
        var derivedAnalysis = derivedFields.analyze(snapshot);
        if (!derivedAnalysis.issues().isEmpty()) {
            var issue = derivedAnalysis.issues().getFirst();
            throw ConfigErrors.invalidReference(issue.code() + ": " + issue.message());
        }
        for (var module : modules.values()) {
            var moduleId = id(module, "id");
            var moduleSnapshot = objectMapper.createObjectNode();
            moduleSnapshot.put("id", Long.toString(moduleId));
            moduleSnapshot.put("moduleCode", module.path("module_code").asText());
            moduleSnapshot.put("moduleName", module.path("module_name").asText());
            var moduleJson = write(moduleSnapshot);
            jdbc.update("INSERT INTO un_module_runtime_schema_module "
                            + "(id,system_id,schema_version_id,module_snapshot_id,source_module_id,logical_module_id,"
                            + "module_code,module_name,snapshot_json,checksum,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    ids.nextId(), systemId, schemaVersionId, moduleId, moduleId, moduleId,
                    module.path("module_code").asText(), module.path("module_name").asText(), moduleJson,
                    ConfigMutationSupport.sha256(moduleJson), createdAt);
        }

        for (var field : fields.values()) {
            insertRecordField(systemId, schemaVersionId, field,
                    derivedAnalysis.metadataByField().get(id(field, "id")), createdAt);
        }
        for (var parent : fields.values()) {
            if (!"SUBTABLE".equals(parent.path("field_type").asText())) {
                continue;
            }
            var columns = parent.path("property_json").path("columnFieldIds");
            if (!columns.isArray()) {
                continue;
            }
            for (var columnIdNode : columns) {
                var column = fields.get(columnIdNode.asText());
                if (column == null) {
                    throw ConfigErrors.invalidReference("SUBTABLE column is absent from the published snapshot");
                }
                insertSubtableColumn(systemId, schemaVersionId, parent, column, createdAt);
            }
        }
    }

    private void insertRecordField(long systemId, long schemaVersionId, JsonNode field,
                                   DerivedFieldContractService.Metadata metadata, LocalDateTime createdAt) {
        var moduleId = id(field, "module_id");
        var fieldId = id(field, "id");
        jdbc.update("INSERT INTO un_module_runtime_schema_field "
                        + "(id,system_id,schema_version_id,module_snapshot_id,field_snapshot_id,source_field_id,"
                        + "logical_module_id,logical_field_id,parent_field_snapshot_id,dictionary_id,target_module_id,"
                        + "field_code,field_name,field_type,field_scope,is_required,is_readonly,property_json,"
                        + "result_schema,evaluator_version,expression_checksum,topological_rank,dependency_json,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,NULL,?,?,?,?,?,'RECORD',?,?,?,?,?,?,?,?,?)",
                ids.nextId(), systemId, schemaVersionId, moduleId, fieldId, fieldId, moduleId, fieldId,
                nullableId(field, "dictionary_id"), nullableId(field, "target_module_id"),
                field.path("field_code").asText(), field.path("field_name").asText(),
                field.path("field_type").asText(), field.path("is_required").asBoolean(),
                field.path("is_readonly").asBoolean(), write(field.path("property_json")),
                metadata == null ? null : metadata.resultSchema(),
                metadata == null ? null : metadata.evaluatorVersion(),
                metadata == null ? null : metadata.expressionChecksum(),
                metadata == null ? null : metadata.topologicalRank(),
                metadata == null ? null : writeValue(metadata.dependencies()), createdAt);
    }

    private void insertSubtableColumn(
            long systemId,
            long schemaVersionId,
            JsonNode parent,
            JsonNode column,
            LocalDateTime createdAt
    ) {
        var moduleId = id(parent, "module_id");
        var parentId = id(parent, "id");
        var sourceColumnId = id(column, "id");
        var fieldSnapshotId = stableNegative("snapshot:" + schemaVersionId + ":" + parentId + ":" + sourceColumnId);
        var logicalFieldId = stableNegative("logical:" + parentId + ":" + sourceColumnId);
        var properties = column.path("property_json").deepCopy();
        ObjectNode projected = properties instanceof ObjectNode object ? object : objectMapper.createObjectNode();
        projected.put("sourceColumnFieldId", Long.toString(sourceColumnId));
        projected.put("parentSubtableFieldId", Long.toString(parentId));
        jdbc.update("INSERT INTO un_module_runtime_schema_field "
                        + "(id,system_id,schema_version_id,module_snapshot_id,field_snapshot_id,source_field_id,"
                        + "logical_module_id,logical_field_id,parent_field_snapshot_id,dictionary_id,target_module_id,"
                        + "field_code,field_name,field_type,field_scope,is_required,is_readonly,property_json,created_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,'SUBTABLE_COLUMN',?,?,?,?)",
                ids.nextId(), systemId, schemaVersionId, moduleId, fieldSnapshotId, sourceColumnId,
                moduleId, logicalFieldId, parentId, nullableId(column, "dictionary_id"),
                nullableId(column, "target_module_id"), column.path("field_code").asText(),
                column.path("field_name").asText(), column.path("field_type").asText(),
                column.path("is_required").asBoolean(), column.path("is_readonly").asBoolean(),
                write(projected), createdAt);
    }

    private static Map<String, JsonNode> byId(JsonNode nodes) {
        var result = new LinkedHashMap<String, JsonNode>();
        nodes.forEach(node -> result.put(node.path("id").asText(), node));
        return result;
    }

    private static long id(JsonNode node, String name) {
        return Long.parseLong(node.path(name).asText());
    }

    private static Long nullableId(JsonNode node, String name) {
        return node.hasNonNull(name) ? Long.parseLong(node.path(name).asText()) : null;
    }

    private String write(JsonNode node) {
        return writeValue(node);
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Runtime schema projection serialization failed", exception);
        }
    }

    private static long stableNegative(String value) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            var positive = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
            return positive == 0 ? -1 : -positive;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
