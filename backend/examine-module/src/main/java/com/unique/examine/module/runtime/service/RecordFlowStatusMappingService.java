package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import com.unique.examine.core.runtime.RuntimeRecordFlowFacade;
import com.unique.examine.module.runtime.flow.RecordFlowStatusMappingPort;
import com.unique.examine.module.runtime.history.RecordHistoryWriter;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class RecordFlowStatusMappingService implements RecordFlowStatusMappingPort {
    private static final String FIELD_CODE = "^[A-Za-z][A-Za-z0-9_]{0,63}$";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final CanonicalFieldValueCodec values;
    private final IdService ids;
    private final RecordHistoryWriter history;

    public RecordFlowStatusMappingService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            CanonicalFieldValueCodec values,
            IdService ids,
            RecordHistoryWriter history
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.values = Objects.requireNonNull(values, "values");
        this.ids = Objects.requireNonNull(ids, "ids");
        this.history = Objects.requireNonNull(history, "history");
    }

    @Override
    public RuntimeRecordFlowFacade.RecordStatusMapping validateBinding(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String moduleCode,
            Set<String> effectivePermissions,
            RuntimeRecordFlowFacade.RecordStatusMapping mapping
    ) {
        if (mapping == null) {
            return null;
        }
        try {
            var field = exactField(
                    systemId,
                    schemaVersionId,
                    moduleSnapshotId,
                    logicalModuleId,
                    mapping.fieldCode());
            requireWritableStatus(field, moduleCode, effectivePermissions);
            normalize(field, mapping.approvedValue());
            normalize(field, mapping.rejectedValue());
            normalize(field, mapping.withdrawnValue());
            normalize(field, mapping.terminatedValue());
            return mapping;
        } catch (BusinessException exception) {
            if ("RECORD_FLOW_STATUS_MAPPING_INVALID".equals(exception.code())) {
                throw exception;
            }
            throw invalidMapping(exception);
        } catch (RuntimeException exception) {
            throw invalidMapping(exception);
        }
    }

    @Override
    public void applyTerminal(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId,
            RuntimeRecordFlowFacade.RecordStatusMapping mapping,
            RuntimeRecordFlowFacade.FlowStatus status,
            long actorMemberId,
            Instant occurredAt
    ) {
        if (mapping == null) {
            return;
        }
        var record = lockRecord(systemId, tenantId, recordId, logicalModuleId);
        if (!"ACTIVE".equals(record.status())) {
            throw stateConflict();
        }
        final ExactField field;
        final CanonicalStatus selected;
        try {
            field = exactField(
                    systemId,
                    record.schemaVersionId(),
                    record.moduleSnapshotId(),
                    logicalModuleId,
                    mapping.fieldCode());
            requireWritableStatus(field, null, null);
            selected = normalize(field, mapping.valueFor(status));
        } catch (RuntimeException exception) {
            throw stateConflict(exception);
        }

        var before = currentValue(record, field);
        var now = LocalDateTime.ofInstant(occurredAt, ZoneOffset.UTC);
        deleteDerived(record, field);
        insertValue(record, field, selected, actorMemberId, now);
        if (!"NONE".equals(field.indexMode())) {
            insertIndex(record, field, selected, now);
        }

        var updated = jdbc.update("""
                        UPDATE un_module_record
                           SET version=version+1,updated_at=?,updated_by=?
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND logical_module_id=? AND status='ACTIVE' AND version=?
                        """,
                now,
                actorMemberId,
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.logicalModuleId(),
                record.version());
        if (updated != 1) {
            throw stateConflict();
        }
        history.appendFlowField(
                record.systemId(),
                record.tenantId(),
                actorMemberId,
                record.recordId(),
                record.version() + 1,
                occurredAt,
                mapping.fieldCode(),
                before,
                selected.value());
    }

    private LockedRuntimeRecord lockRecord(
            long systemId,
            long tenantId,
            long recordId,
            long logicalModuleId
    ) {
        return jdbc.query("""
                        SELECT system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,
                               logical_module_id,status,version
                          FROM un_module_record
                         WHERE system_id=? AND tenant_id=? AND record_id=? AND logical_module_id=?
                         FOR UPDATE
                        """,
                (result, row) -> new LockedRuntimeRecord(
                        result.getLong("system_id"),
                        result.getLong("tenant_id"),
                        result.getLong("record_id"),
                        result.getLong("schema_version_id"),
                        result.getLong("module_snapshot_id"),
                        result.getLong("logical_module_id"),
                        result.getString("status"),
                        result.getLong("version")),
                systemId,
                tenantId,
                recordId,
                logicalModuleId)
                .stream()
                .findFirst()
                .orElseThrow(RecordFlowStatusMappingService::stateConflict);
    }

    private ExactField exactField(
            long systemId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String fieldCode
    ) {
        if (fieldCode == null || !fieldCode.matches(FIELD_CODE)) {
            throw invalidMapping();
        }
        return jdbc.query("""
                        SELECT f.field_snapshot_id,f.logical_field_id,f.field_name,f.field_type,
                               f.is_readonly,f.dictionary_id,f.property_json,v.snapshot_json
                          FROM un_module_runtime_schema_field f
                          JOIN un_module_config_version v
                            ON v.system_id=f.system_id AND v.id=f.schema_version_id
                         WHERE f.system_id=? AND f.schema_version_id=? AND f.module_snapshot_id=?
                           AND f.logical_module_id=? AND f.field_scope='RECORD' AND f.field_code=?
                        """,
                (result, row) -> exactField(result, fieldCode),
                systemId,
                schemaVersionId,
                moduleSnapshotId,
                logicalModuleId,
                fieldCode)
                .stream()
                .findFirst()
                .orElseThrow(RecordFlowStatusMappingService::invalidMapping);
    }

    private ExactField exactField(ResultSet result, String fieldCode) throws SQLException {
        var snapshot = parse(result.getString("snapshot_json"));
        var fieldSnapshotId = result.getLong("field_snapshot_id");
        var publishedField = findPublishedField(snapshot, fieldSnapshotId);
        var property = parse(result.getString("property_json"));
        var schema = property instanceof ObjectNode object
                ? object.deepCopy()
                : objectMapper.createObjectNode();
        return new ExactField(
                fieldSnapshotId,
                result.getLong("logical_field_id"),
                fieldCode,
                result.getString("field_name"),
                result.getString("field_type"),
                result.getBoolean("is_readonly"),
                publishedField.path("is_hidden").asBoolean(),
                publishedField.path("desired_status").asText(),
                publishedField.path("index_mode").asText("NONE"),
                publishedField,
                schema,
                dictionaryOptions(snapshot, result.getString("dictionary_id")));
    }

    private void requireWritableStatus(
            ExactField field,
            String moduleCode,
            Set<String> effectivePermissions
    ) {
        if (!"STATUS".equals(field.type())
                || field.readonly()
                || field.hidden()
                || !"ENABLED".equals(field.desiredStatus())) {
            throw invalidMapping();
        }
        if (moduleCode == null || effectivePermissions == null) {
            return;
        }
        var writePermission = "module." + moduleCode + ".field." + field.code() + ".write";
        var declared = field.publishedSnapshot().path("_declaredWritePermission").asBoolean(false);
        if (declared && !effectivePermissions.contains(writePermission)) {
            throw invalidMapping();
        }
    }

    private CanonicalStatus normalize(ExactField field, String value) {
        var normalized = values.normalize(
                new CanonicalFieldValueCodec.FieldContract(
                        field.code(),
                        field.name(),
                        field.type(),
                        field.schema(),
                        field.options()),
                TextNode.valueOf(value));
        if (normalized.rows().size() != 1 || normalized.rows().getFirst().referenceValue() == null) {
            throw invalidMapping();
        }
        return new CanonicalStatus(
                normalized.value().toString(),
                normalized.display(),
                normalized.rows().getFirst().referenceValue());
    }

    private String currentValue(LockedRuntimeRecord record, ExactField field) {
        return jdbc.query("""
                        SELECT reference_value
                          FROM un_module_record_value
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND schema_version_id=? AND module_snapshot_id=?
                           AND field_snapshot_id=? AND field_type='STATUS' AND ordinal=0
                        """,
                (result, row) -> Long.toString(result.getLong("reference_value")),
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                field.fieldSnapshotId())
                .stream()
                .findFirst()
                .orElse(null);
    }

    private void deleteDerived(LockedRuntimeRecord record, ExactField field) {
        var arguments = new Object[]{
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                field.logicalFieldId()
        };
        jdbc.update("""
                        DELETE FROM un_module_record_search
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id=?
                        """,
                arguments);
        jdbc.update("""
                        DELETE FROM un_module_record_index
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id=?
                        """,
                arguments);
        jdbc.update("""
                        DELETE FROM un_module_record_unique
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id=?
                        """,
                arguments);
        jdbc.update("""
                        DELETE FROM un_module_record_value
                         WHERE system_id=? AND tenant_id=? AND record_id=?
                           AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=?
                        """,
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                field.fieldSnapshotId());
    }

    private void insertValue(
            LockedRuntimeRecord record,
            ExactField field,
            CanonicalStatus selected,
            long actorMemberId,
            LocalDateTime now
    ) {
        jdbc.update("""
                        INSERT INTO un_module_record_value
                            (id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,
                             logical_module_id,field_snapshot_id,logical_field_id,field_version,
                             field_type,ordinal,string_value,text_value,decimal_value,date_value,
                             datetime_value,time_value,boolean_value,currency_code,reference_value,
                             encrypted_value,encryption_key_version,value_hash,hash_key_version,
                             display_value,created_at,created_by,updated_at,updated_by,version)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                ids.nextId(),
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                record.logicalModuleId(),
                field.fieldSnapshotId(),
                field.logicalFieldId(),
                1L,
                "STATUS",
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                selected.referenceValue(),
                null,
                null,
                null,
                null,
                selected.display(),
                now,
                actorMemberId,
                now,
                actorMemberId,
                0L);
    }

    private void insertIndex(
            LockedRuntimeRecord record,
            ExactField field,
            CanonicalStatus selected,
            LocalDateTime now
    ) {
        jdbc.update("""
                        INSERT INTO un_module_record_index
                            (id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,
                             logical_module_id,logical_field_id,index_generation_id,
                             normalization_generation_id,path_snapshot_id,ordinal,record_status,
                             value_kind,string_value,decimal_value,date_value,datetime_value,time_value,
                             boolean_value,reference_value,hash_value,hash_key_version,geohash,geo_lat,
                             geo_lng,currency_code,created_at,updated_at)
                        VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                        """,
                ids.nextId(),
                record.systemId(),
                record.tenantId(),
                record.recordId(),
                record.schemaVersionId(),
                record.moduleSnapshotId(),
                record.logicalModuleId(),
                field.logicalFieldId(),
                1L,
                1L,
                0L,
                0,
                "ACTIVE",
                "STRING",
                selected.value(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                now,
                now);
    }

    private JsonNode findPublishedField(JsonNode snapshot, long fieldSnapshotId) {
        for (var field : snapshot.path("fields")) {
            if (Long.toString(fieldSnapshotId).equals(field.path("id").asText())) {
                return withDeclaredWritePermissionMarker(field.deepCopy(), snapshot);
            }
        }
        throw invalidMapping();
    }

    private JsonNode withDeclaredWritePermissionMarker(JsonNode field, JsonNode snapshot) {
        var declared = false;
        for (var permission : snapshot.path("permissions")) {
            if ("FIELD".equals(permission.path("resource_type").asText())
                    && field.path("id").asText().equals(permission.path("resource_id").asText())
                    && "ENABLED".equals(permission.path("desired_status").asText())
                    && permission.path("permission_code").asText().endsWith(".write")) {
                declared = true;
                break;
            }
        }
        if (field instanceof ObjectNode object) {
            object.put("_declaredWritePermission", declared);
        }
        return field;
    }

    private List<CanonicalFieldValueCodec.ValueOption> dictionaryOptions(
            JsonNode snapshot,
            String dictionaryId
    ) {
        if (dictionaryId == null || dictionaryId.isBlank()) {
            return List.of();
        }
        var result = new ArrayList<JsonNode>();
        snapshot.path("dictionaryItems").forEach(result::add);
        result.sort(Comparator.comparingInt((JsonNode node) -> node.path("sort_order").asInt())
                .thenComparing(node -> node.path("id").asText()));
        return result.stream()
                .filter(item -> dictionaryId.equals(item.path("dictionary_id").asText()))
                .filter(item -> "ENABLED".equals(item.path("desired_status").asText()))
                .map(item -> new CanonicalFieldValueCodec.ValueOption(
                        item.path("id").asText(),
                        item.path("item_label").asText(),
                        item.path("parent_id").isMissingNode() || item.path("parent_id").isNull()
                                ? null
                                : item.path("parent_id").asText()))
                .toList();
    }

    private JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Published runtime schema snapshot is invalid", exception);
        }
    }

    private static BusinessException invalidMapping() {
        return new BusinessException(
                "RECORD_FLOW_STATUS_MAPPING_INVALID",
                "The record Flow status mapping is invalid for the record schema",
                HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException invalidMapping(Throwable cause) {
        var exception = invalidMapping();
        exception.initCause(cause);
        return exception;
    }

    private static BusinessException stateConflict() {
        return new BusinessException(
                "RECORD_FLOW_STATE_CONFLICT",
                "The record Flow state no longer permits this operation",
                HttpStatus.CONFLICT);
    }

    private static BusinessException stateConflict(Throwable cause) {
        var exception = stateConflict();
        exception.initCause(cause);
        return exception;
    }

    private record LockedRuntimeRecord(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long logicalModuleId,
            String status,
            long version
    ) {
    }

    private record ExactField(
            long fieldSnapshotId,
            long logicalFieldId,
            String code,
            String name,
            String type,
            boolean readonly,
            boolean hidden,
            String desiredStatus,
            String indexMode,
            JsonNode publishedSnapshot,
            ObjectNode schema,
            List<CanonicalFieldValueCodec.ValueOption> options
    ) {
        private ExactField {
            options = List.copyOf(options);
        }
    }

    private record CanonicalStatus(String value, String display, long referenceValue) {
    }
}
