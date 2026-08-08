package com.unique.examine.module.runtime.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.id.IdService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class ReferenceMaterializationService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final IdService ids;
    private final DerivedMaterializationService derived;

    public ReferenceMaterializationService(
            JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            IdService ids,
            DerivedMaterializationService derived
    ) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.ids = ids;
        this.derived = derived;
    }

    public boolean recomputeParent(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            LocalDateTime now
    ) {
        var parentRows = jdbc.query("SELECT updated_by,status FROM un_module_record WHERE system_id=? AND tenant_id=? "
                        + "AND record_id=? AND schema_version_id=? AND module_snapshot_id=? FOR UPDATE",
                (row, number) -> new ParentRecord(row.getLong("updated_by"), row.getString("status")),
                systemId, tenantId, recordId, schemaVersionId,
                moduleSnapshotId);
        if (parentRows.size() != 1) {
            return false;
        }
        var parent = parentRows.getFirst();
        var fields = jdbc.query("SELECT field_snapshot_id,logical_field_id,property_json "
                        + "FROM un_module_runtime_schema_field WHERE system_id=? AND schema_version_id=? "
                        + "AND module_snapshot_id=? AND field_scope='RECORD' AND field_type='REFERENCE' ORDER BY id",
                (row, number) -> referenceField(row.getLong("field_snapshot_id"),
                        row.getLong("logical_field_id"), row.getString("property_json")),
                systemId, schemaVersionId, moduleSnapshotId);
        var changed = false;
        for (var field : fields) {
            changed |= materialize(systemId, tenantId, schemaVersionId, moduleSnapshotId, recordId,
                    parent.actorId(), parent.status(), field, now);
        }
        return changed;
    }

    public int queueTargetUpdate(long systemId, long tenantId, long targetRecordId, long targetVersion,
                                 LocalDateTime now) {
        var derivedFields = derived.markPendingForSource(systemId, tenantId, targetRecordId, now);
        var dependents = jdbc.queryForObject("SELECT COUNT(DISTINCT source_record_id) "
                        + "FROM un_module_record_relation WHERE system_id=? AND tenant_id=? AND target_record_id=?",
                Long.class, systemId, tenantId, targetRecordId);
        if ((dependents == null || dependents == 0) && derivedFields == 0) {
            return 0;
        }
        jdbc.update("UPDATE un_module_reference_state SET recalculation_state='PENDING',"
                        + "failure_correlation_id=NULL,updated_at=?,version=version+1 WHERE system_id=? "
                        + "AND tenant_id=? AND source_record_id=?",
                now, systemId, tenantId, targetRecordId);
        var correlationId = "reference-" + targetRecordId + "-v" + targetVersion;
        jdbc.update("INSERT INTO un_module_reference_recalc_task "
                        + "(id,system_id,tenant_id,source_record_id,source_record_version,status,attempt_count,"
                        + "available_at,correlation_id,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,'PENDING',0,?,?,?, ?,0) ON DUPLICATE KEY UPDATE "
                        + "status=IF(status='PASSED','PASSED','PENDING'),available_at=VALUES(available_at),"
                        + "updated_at=VALUES(updated_at)",
                ids.nextId(), systemId, tenantId, targetRecordId, targetVersion, now, correlationId, now, now);
        return (dependents == null ? 0 : dependents.intValue()) + derivedFields;
    }

    public RetryState retryFailed(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long fieldSnapshotId,
            LocalDateTime now
    ) {
        var states = jdbc.query("SELECT s.source_record_id,t.source_record_version FROM "
                        + "un_module_reference_state s JOIN un_module_reference_recalc_task t "
                        + "ON t.system_id=s.system_id AND t.tenant_id=s.tenant_id "
                        + "AND t.source_record_id=s.source_record_id "
                        + "AND t.correlation_id=s.failure_correlation_id AND t.status='FAILED' "
                        + "JOIN un_module_record target ON target.system_id=t.system_id "
                        + "AND target.tenant_id=t.tenant_id AND target.record_id=t.source_record_id "
                        + "AND target.version=t.source_record_version "
                        + "WHERE s.system_id=? AND s.tenant_id=? AND s.record_id=? AND s.schema_version_id=? "
                        + "AND s.module_snapshot_id=? AND s.field_snapshot_id=? "
                        + "AND s.recalculation_state='FAILED' FOR UPDATE",
                (row, number) -> new RetrySource(row.getLong("source_record_id"),
                        row.getLong("source_record_version")),
                systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, fieldSnapshotId);
        if (states.size() != 1) {
            return null;
        }
        var source = states.getFirst();
        var correlationId = "reference-retry-" + UUID.randomUUID();
        jdbc.update("UPDATE un_module_reference_state SET recalculation_state='PENDING',"
                        + "failure_correlation_id=NULL,updated_at=?,version=version+1 WHERE system_id=? "
                        + "AND tenant_id=? AND record_id=? AND schema_version_id=? AND module_snapshot_id=? "
                        + "AND field_snapshot_id=? AND recalculation_state='FAILED'",
                now, systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, fieldSnapshotId);
        jdbc.update("INSERT INTO un_module_reference_recalc_task "
                        + "(id,system_id,tenant_id,source_record_id,source_record_version,status,attempt_count,"
                        + "available_at,correlation_id,created_at,updated_at,version) "
                        + "VALUES (?,?,?,?,?,'PENDING',0,?,?,?,?,0) ON DUPLICATE KEY UPDATE "
                        + "status='PENDING',attempt_count=0,available_at=VALUES(available_at),"
                        + "correlation_id=VALUES(correlation_id),updated_at=VALUES(updated_at),version=version+1",
                ids.nextId(), systemId, tenantId, source.recordId(), source.version(), now,
                correlationId, now, now);
        return new RetryState(source.recordId(), source.version(), correlationId);
    }

    private boolean materialize(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            long actorId,
            String recordStatus,
            ReferenceField field,
            LocalDateTime now
    ) {
        var sources = jdbc.query("SELECT rr.target_record_id,r.version,r.status,rr.target_schema_version_id,"
                        + "rr.target_module_snapshot_id FROM un_module_record_relation rr "
                        + "JOIN un_module_record r ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.target_record_id AND r.schema_version_id=rr.target_schema_version_id "
                        + "AND r.module_snapshot_id=rr.target_module_snapshot_id WHERE rr.system_id=? "
                        + "AND rr.tenant_id=? AND rr.source_record_id=? AND rr.source_schema_version_id=? "
                        + "AND rr.source_module_snapshot_id=? AND rr.source_field_snapshot_id=? ORDER BY rr.ordinal",
                (row, number) -> new SourceRecord(row.getLong("target_record_id"), row.getLong("version"),
                        row.getString("status"), row.getLong("target_schema_version_id"),
                        row.getLong("target_module_snapshot_id")),
                systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, field.sourceFieldId());
        if (sources.size() > 1) {
            throw new IllegalStateException("REFERENCE source relation is not single-valued");
        }
        var source = sources.isEmpty() ? null : sources.getFirst();
        MaterializedValue value = null;
        if (source != null && Set.of("ACTIVE", "ARCHIVED").contains(source.status())) {
            var values = jdbc.query("SELECT v.string_value,v.decimal_value,v.date_value,v.datetime_value,"
                            + "v.boolean_value,v.display_value,f.field_type FROM un_module_runtime_schema_field f "
                            + "LEFT JOIN un_module_record_value v ON v.system_id=f.system_id "
                            + "AND v.schema_version_id=f.schema_version_id AND v.module_snapshot_id=f.module_snapshot_id "
                            + "AND v.field_snapshot_id=f.field_snapshot_id AND v.tenant_id=? AND v.record_id=? "
                            + "WHERE f.system_id=? AND f.schema_version_id=? AND f.module_snapshot_id=? "
                            + "AND f.source_field_id=? AND f.field_scope='RECORD'",
                    (row, number) -> materializedValue(row.getString("field_type"), row.getString("string_value"),
                            row.getBigDecimal("decimal_value"), row.getDate("date_value"),
                            row.getTimestamp("datetime_value"), row.getObject("boolean_value", Boolean.class),
                            row.getString("display_value")),
                    tenantId, source.recordId(), systemId, source.schemaVersionId(), source.moduleSnapshotId(),
                    field.targetFieldId());
            if (values.size() != 1) {
                throw new IllegalStateException("REFERENCE target field is unavailable in the target snapshot");
            }
            value = values.getFirst();
            if (!value.hasPayload()) {
                value = null;
            }
        }

        var prior = jdbc.query("SELECT string_value,decimal_value,date_value,datetime_value,boolean_value,display_value "
                        + "FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=?",
                (row, number) -> new MaterializedValue(null, row.getString("string_value"),
                        row.getBigDecimal("decimal_value"), row.getDate("date_value"),
                        row.getTimestamp("datetime_value"), row.getObject("boolean_value", Boolean.class),
                        row.getString("display_value")),
                systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, field.fieldSnapshotId());
        var valueChanged = prior.isEmpty() != (value == null)
                || value != null && (prior.size() != 1 || !prior.getFirst().samePayload(value));
        jdbc.update("DELETE FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=?",
                systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, field.fieldSnapshotId());
        jdbc.update("DELETE FROM un_module_record_index WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id=?",
                systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, field.logicalFieldId());
        if (value != null && value.hasPayload()) {
            jdbc.update("INSERT INTO un_module_record_value "
                            + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                            + "field_snapshot_id,logical_field_id,field_version,field_type,ordinal,string_value,text_value,"
                            + "decimal_value,date_value,datetime_value,time_value,boolean_value,currency_code,reference_value,"
                            + "encrypted_value,encryption_key_version,value_hash,hash_key_version,display_value,created_at,"
                            + "created_by,updated_at,updated_by,version) VALUES (?,?,?,?,?,?,?,?,?,1,'REFERENCE',0,?,NULL,"
                            + "?,?,?,NULL,?,NULL,NULL,NULL,NULL,NULL,NULL,?,?,?,?,?,0)",
                    ids.nextId(), systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, moduleSnapshotId,
                    field.fieldSnapshotId(), field.logicalFieldId(), value.stringValue(), value.decimalValue(),
                    value.dateValue(), value.dateTimeValue(), value.booleanValue(), value.displayValue(), now, actorId,
                    now, actorId);
            insertIndex(systemId, tenantId, schemaVersionId, moduleSnapshotId, recordId, recordStatus,
                    field.logicalFieldId(), value, now);
        }
        upsertState(systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, field.fieldSnapshotId(),
                source, now);
        return valueChanged;
    }

    private void insertIndex(
            long systemId,
            long tenantId,
            long schemaVersionId,
            long moduleSnapshotId,
            long recordId,
            String recordStatus,
            long logicalFieldId,
            MaterializedValue value,
            LocalDateTime now
    ) {
        var valueKind = switch (value.sourceType()) {
            case "TEXT" -> "STRING";
            case "NUMBER", "RATING" -> "DECIMAL";
            case "DATE" -> "DATE";
            case "DATETIME" -> "DATETIME";
            case "SWITCH" -> "BOOLEAN";
            default -> throw new IllegalStateException("Unsupported REFERENCE index type " + value.sourceType());
        };
        jdbc.update("INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,string_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,reference_value,hash_value,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,0,?,?,?,?,?,?,NULL,?,NULL,NULL,NULL,?,?)",
                ids.nextId(), systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, moduleSnapshotId,
                logicalFieldId, recordStatus, valueKind, value.stringValue(), value.decimalValue(), value.dateValue(),
                value.dateTimeValue(), value.booleanValue(), now, now);
    }

    private void upsertState(
            long systemId,
            long tenantId,
            long recordId,
            long schemaVersionId,
            long moduleSnapshotId,
            long fieldSnapshotId,
            SourceRecord source,
            LocalDateTime now
    ) {
        jdbc.update("INSERT INTO un_module_reference_state "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,field_snapshot_id,"
                        + "source_record_id,source_record_version,recalculation_state,failure_correlation_id,updated_at,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,'READY',NULL,?,0) ON DUPLICATE KEY UPDATE "
                        + "source_record_id=VALUES(source_record_id),source_record_version=VALUES(source_record_version),"
                        + "recalculation_state='READY',failure_correlation_id=NULL,updated_at=VALUES(updated_at),"
                        + "version=version+1",
                ids.nextId(), systemId, tenantId, recordId, schemaVersionId, moduleSnapshotId, fieldSnapshotId,
                source == null ? null : source.recordId(), source == null ? null : source.version(), now);
    }

    private ReferenceField referenceField(long fieldSnapshotId, long logicalFieldId, String properties) {
        try {
            var node = objectMapper.readTree(properties);
            return new ReferenceField(fieldSnapshotId, logicalFieldId,
                    Long.parseLong(node.path("sourceFieldId").asText()),
                    Long.parseLong(node.path("targetFieldId").asText()));
        } catch (JsonProcessingException | NumberFormatException exception) {
            throw new IllegalStateException("Published REFERENCE properties are invalid", exception);
        }
    }

    private static MaterializedValue materializedValue(
            String type, String stringValue, BigDecimal decimalValue, Date dateValue, Timestamp dateTimeValue,
            Boolean booleanValue, String displayValue
    ) {
        return switch (type) {
            case "TEXT" -> new MaterializedValue(type, stringValue, null, null, null, null, displayValue);
            case "NUMBER", "RATING" ->
                    new MaterializedValue(type, null, decimalValue, null, null, null, displayValue);
            case "DATE" -> new MaterializedValue(type, null, null, dateValue, null, null, displayValue);
            case "DATETIME" -> new MaterializedValue(type, null, null, null, dateTimeValue, null, displayValue);
            case "SWITCH" -> new MaterializedValue(type, null, null, null, null, booleanValue, displayValue);
            default -> throw new IllegalStateException("Unsupported REFERENCE result type " + type);
        };
    }

    record DependentRecord(long systemId, long tenantId, long recordId, long schemaVersionId,
                           long moduleSnapshotId, long version) { }

    List<DependentRecord> dependents(long systemId, long tenantId, long targetRecordId) {
        return jdbc.query("SELECT DISTINCT r.system_id,r.tenant_id,r.record_id,r.schema_version_id,"
                        + "r.module_snapshot_id,r.version FROM un_module_record_relation rr "
                        + "JOIN un_module_record r ON r.system_id=rr.system_id AND r.tenant_id=rr.tenant_id "
                        + "AND r.record_id=rr.source_record_id AND r.schema_version_id=rr.source_schema_version_id "
                        + "AND r.module_snapshot_id=rr.source_module_snapshot_id WHERE rr.system_id=? "
                        + "AND rr.tenant_id=? AND rr.target_record_id=? ORDER BY r.record_id",
                (row, number) -> new DependentRecord(row.getLong("system_id"), row.getLong("tenant_id"),
                        row.getLong("record_id"), row.getLong("schema_version_id"),
                        row.getLong("module_snapshot_id"), row.getLong("version")),
                systemId, tenantId, targetRecordId);
    }

    private record ReferenceField(long fieldSnapshotId, long logicalFieldId, long sourceFieldId, long targetFieldId) { }

    public record RetryState(long sourceRecordId, long sourceRecordVersion, String correlationId) { }

    private record RetrySource(long recordId, long version) { }

    private record SourceRecord(long recordId, long version, String status, long schemaVersionId,
                                long moduleSnapshotId) { }

    private record ParentRecord(long actorId, String status) { }

    private record MaterializedValue(
            String sourceType,
            String stringValue,
            BigDecimal decimalValue,
            Date dateValue,
            Timestamp dateTimeValue,
            Boolean booleanValue,
            String displayValue
    ) {
        private boolean hasPayload() {
            var values = new ArrayList<Object>();
            values.add(stringValue);
            values.add(decimalValue);
            values.add(dateValue);
            values.add(dateTimeValue);
            values.add(booleanValue);
            return values.stream().filter(Objects::nonNull).count() == 1;
        }

        private boolean samePayload(MaterializedValue other) {
            return Objects.equals(stringValue, other.stringValue)
                    && Objects.equals(decimalValue, other.decimalValue)
                    && Objects.equals(dateValue, other.dateValue)
                    && Objects.equals(dateTimeValue, other.dateTimeValue)
                    && Objects.equals(booleanValue, other.booleanValue)
                    && Objects.equals(displayValue, other.displayValue);
        }
    }
}
