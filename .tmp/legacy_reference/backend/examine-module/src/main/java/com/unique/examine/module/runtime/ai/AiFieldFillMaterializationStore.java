package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.core.id.IdService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Atomic typed current-value and immutable history persistence for AI_FILL. */
@Component
public class AiFieldFillMaterializationStore {
    private final JdbcTemplate jdbc;
    private final IdService ids;
    private final ObjectMapper json;

    public AiFieldFillMaterializationStore(JdbcTemplate jdbc, IdService ids, ObjectMapper json) {
        this.jdbc = jdbc;
        this.ids = ids;
        this.json = json;
    }

    public AiFieldFillFacade.FillReadback materialize(StoreRequest request) {
        var command = request.command();
        var record = lockRecord(request, command);
        requireField(request, command);
        var current = lockCurrent(request, command.fieldSnapshotId());
        assertExpectedCurrent(command, current);
        if (current != null && command.overwriteMode() == AiFieldFillFacade.OverwriteMode.NEVER) {
            throw new BusinessException(
                    "AI_FILL_OVERWRITE_DENIED",
                    "The published AI_FILL contract forbids overwrite",
                    HttpStatus.CONFLICT);
        }
        var outcome = current == null ? "CREATED" : "OVERWRITTEN";
        var materializationVersion = current == null ? 0 : current.version() + 1;
        var valueHash = AiFieldFillCommandSealer.sha256(canonical(command.canonicalValue()));
        var now = LocalDateTime.now();
        replaceRuntimeValue(request, command, record, now);
        replaceRuntimeIndex(request, command, record, now);
        upsertCurrent(request, command, current, valueHash, materializationVersion, now);
        var historyId = ids.nextId();
        insertHistory(
                historyId, request, command, current == null ? null : current.valueHash(),
                valueHash, materializationVersion, outcome, now);
        return new AiFieldFillFacade.FillReadback(
                Long.toString(historyId), request.moduleCode(), request.recordId(),
                record.version(), command.schemaVersionId(), Long.toString(command.fieldSnapshotId()),
                command.fieldCode(), command.resultSchema(), command.displayValue(), command.confidence(),
                materializationVersion, outcome, command.provenance());
    }

    public AiFieldFillFacade.RejectionReadback reject(StoreRequest request) {
        var command = request.command();
        var historyId = ids.nextId();
        var current = current(request, command.fieldSnapshotId(), false);
        insertHistory(
                historyId, request, command, current == null ? null : current.valueHash(),
                null, null, "REJECTED", LocalDateTime.now());
        return new AiFieldFillFacade.RejectionReadback(
                Long.toString(historyId), command.proposalId(), request.recordId(),
                command.fieldCode(), "REJECTED");
    }

    private LockedRecord lockRecord(
            StoreRequest request,
            AiFieldFillCommandCodec.FillCommand command
    ) {
        var rows = jdbc.query(
                "SELECT version,status,schema_version_id,module_snapshot_id FROM un_module_record "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? FOR UPDATE",
                (row, number) -> new LockedRecord(
                        row.getLong("version"), row.getString("status"),
                        row.getLong("schema_version_id"), row.getLong("module_snapshot_id")),
                request.systemId(), request.tenantId(), Long.parseLong(request.recordId()));
        if (rows.size() != 1) {
            throw new BusinessException("RECORD_NOT_FOUND", "AI_FILL record does not exist", HttpStatus.NOT_FOUND);
        }
        var record = rows.getFirst();
        if (record.version() != command.recordVersion()) {
            throw conflict("RECORD_VERSION_CONFLICT", "AI_FILL target record changed");
        }
        if (record.schemaVersionId() != Long.parseLong(command.schemaVersionId())
                || record.moduleSnapshotId() != command.moduleSnapshotId()) {
            throw conflict("RECORD_SCHEMA_STALE", "AI_FILL target schema changed");
        }
        if (!List.of("DRAFT", "ACTIVE").contains(record.status())) {
            throw conflict("RECORD_STATE_INVALID", "AI_FILL target record is not writable");
        }
        return record;
    }

    private void requireField(StoreRequest request, AiFieldFillCommandCodec.FillCommand command) {
        var count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM un_module_runtime_schema_field WHERE system_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=? "
                        + "AND logical_field_id=? AND field_code=? AND field_type='AI_FILL' "
                        + "AND field_scope='RECORD' AND result_schema=? AND is_readonly=TRUE",
                Long.class, request.systemId(), Long.parseLong(command.schemaVersionId()),
                command.moduleSnapshotId(), command.fieldSnapshotId(), command.logicalFieldId(),
                command.fieldCode(), command.resultSchema().name());
        if (count == null || count != 1) {
            throw conflict("AI_FILL_CONTRACT_STALE", "AI_FILL field contract changed");
        }
    }

    private Current lockCurrent(StoreRequest request, long fieldId) {
        return current(request, fieldId, true);
    }

    private Current current(StoreRequest request, long fieldId, boolean lock) {
        var rows = jdbc.query(
                "SELECT id,value_hash,version,created_at FROM un_module_ai_fill_materialization "
                        + "WHERE system_id=? AND tenant_id=? AND record_id=? AND field_snapshot_id=?"
                        + (lock ? " FOR UPDATE" : ""),
                (row, number) -> new Current(
                        row.getLong("id"), row.getString("value_hash"), row.getLong("version"),
                        row.getObject("created_at", LocalDateTime.class)),
                request.systemId(), request.tenantId(), Long.parseLong(request.recordId()), fieldId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static void assertExpectedCurrent(
            AiFieldFillCommandCodec.FillCommand command,
            Current current
    ) {
        if (current == null && (command.currentMaterializationVersion() != null
                || command.currentValueHash() != null)) {
            throw conflict("AI_FILL_VALUE_STALE", "The previous AI_FILL value was removed");
        }
        if (current != null && (command.currentMaterializationVersion() == null
                || current.version() != command.currentMaterializationVersion()
                || !current.valueHash().equals(command.currentValueHash()))) {
            throw conflict("AI_FILL_VALUE_STALE", "The previous AI_FILL value changed");
        }
    }

    private void replaceRuntimeValue(
            StoreRequest request,
            AiFieldFillCommandCodec.FillCommand command,
            LockedRecord record,
            LocalDateTime now
    ) {
        jdbc.update(
                "DELETE FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id=?",
                request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                Long.parseLong(command.schemaVersionId()), command.moduleSnapshotId(), command.fieldSnapshotId());
        var typed = typed(command);
        jdbc.update("INSERT INTO un_module_record_value "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "field_snapshot_id,logical_field_id,field_version,field_type,field_scope,result_schema,"
                        + "dependency_version_json,evaluator_version,recalculation_state,failure_correlation_id,"
                        + "ordinal,string_value,text_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,currency_code,reference_value,encrypted_value,encryption_key_version,"
                        + "value_hash,hash_key_version,display_value,created_at,created_by,updated_at,updated_by,version) "
                        + "VALUES (?,?,?,?,?,?,?,?,?,?,?,'RECORD',?,CAST(? AS JSON),1,'READY',NULL,0,?,NULL,?,?,?,"
                        + "NULL,?,NULL,NULL,NULL,NULL,NULL,NULL,?,?,?, ?,?,0)",
                ids.nextId(), request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                Long.parseLong(command.schemaVersionId()), command.moduleSnapshotId(), command.moduleSnapshotId(),
                command.fieldSnapshotId(), command.logicalFieldId(), Math.max(1L, record.version()), "AI_FILL",
                command.resultSchema().name(), dependency(command.sourceVersionHash()), typed.stringValue(),
                typed.decimalValue(), typed.dateValue(), typed.datetimeValue(), typed.booleanValue(),
                command.displayValue(), now, request.memberId(), now, request.memberId());
    }

    private void replaceRuntimeIndex(
            StoreRequest request,
            AiFieldFillCommandCodec.FillCommand command,
            LockedRecord record,
            LocalDateTime now
    ) {
        jdbc.update("DELETE FROM un_module_record_index WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND logical_field_id=?",
                request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                Long.parseLong(command.schemaVersionId()), command.moduleSnapshotId(), command.logicalFieldId());
        var typed = typed(command);
        jdbc.update("INSERT INTO un_module_record_index "
                        + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,logical_module_id,"
                        + "logical_field_id,index_generation_id,normalization_generation_id,path_snapshot_id,ordinal,"
                        + "record_status,value_kind,string_value,decimal_value,date_value,datetime_value,time_value,"
                        + "boolean_value,reference_value,hash_value,currency_code,created_at,updated_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,1,1,0,0,?,?,?,?,?,?,NULL,?,NULL,NULL,NULL,?,?)",
                ids.nextId(), request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                Long.parseLong(command.schemaVersionId()), command.moduleSnapshotId(), command.moduleSnapshotId(),
                command.logicalFieldId(), record.status(), valueKind(command.resultSchema()), typed.stringValue(),
                typed.decimalValue(), typed.dateValue(), typed.datetimeValue(), typed.booleanValue(), now, now);
    }

    private void upsertCurrent(
            StoreRequest request,
            AiFieldFillCommandCodec.FillCommand command,
            Current current,
            String valueHash,
            long version,
            LocalDateTime now
    ) {
        var typed = typed(command);
        if (current == null) {
            jdbc.update("INSERT INTO un_module_ai_fill_materialization "
                            + "(id,system_id,tenant_id,record_id,schema_version_id,module_snapshot_id,"
                            + "field_snapshot_id,logical_field_id,field_code,result_schema,string_value,decimal_value,"
                            + "date_value,datetime_value,boolean_value,display_value,value_hash,source_version_hash,"
                            + "confidence,materialized_by_member_id,provider_id,provider_version,model_snapshot,"
                            + "prompt_version,policy_version_id,proposal_id,request_id,trace_id,created_at,updated_at,version) "
                            + "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,0)",
                    ids.nextId(), request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                    Long.parseLong(command.schemaVersionId()), command.moduleSnapshotId(), command.fieldSnapshotId(),
                    command.logicalFieldId(), command.fieldCode(), command.resultSchema().name(), typed.stringValue(),
                    typed.decimalValue(), typed.dateValue(), typed.datetimeValue(), typed.booleanValue(),
                    command.displayValue(), valueHash, command.sourceVersionHash(), command.confidence(),
                    request.memberId(), Long.parseLong(command.provenance().providerId()),
                    command.provenance().providerVersion(), command.provenance().model(),
                    command.provenance().promptVersion(), Long.parseLong(command.provenance().policyVersionId()),
                    command.proposalId(), request.requestId(), request.traceId(), now, now);
            return;
        }
        var updated = jdbc.update("UPDATE un_module_ai_fill_materialization SET string_value=?,decimal_value=?,"
                        + "date_value=?,datetime_value=?,boolean_value=?,display_value=?,value_hash=?,"
                        + "source_version_hash=?,confidence=?,materialized_by_member_id=?,provider_id=?,"
                        + "provider_version=?,model_snapshot=?,prompt_version=?,policy_version_id=?,proposal_id=?,"
                        + "request_id=?,trace_id=?,updated_at=?,version=version+1 WHERE system_id=? AND tenant_id=? "
                        + "AND record_id=? AND field_snapshot_id=? AND version=?",
                typed.stringValue(), typed.decimalValue(), typed.dateValue(), typed.datetimeValue(),
                typed.booleanValue(), command.displayValue(), valueHash, command.sourceVersionHash(),
                command.confidence(), request.memberId(), Long.parseLong(command.provenance().providerId()),
                command.provenance().providerVersion(), command.provenance().model(),
                command.provenance().promptVersion(), Long.parseLong(command.provenance().policyVersionId()),
                command.proposalId(), request.requestId(), request.traceId(), now,
                request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                command.fieldSnapshotId(), current.version());
        if (updated != 1) throw conflict("AI_FILL_VALUE_STALE", "AI_FILL value changed concurrently");
    }

    private void insertHistory(
            long historyId,
            StoreRequest request,
            AiFieldFillCommandCodec.FillCommand command,
            String previousHash,
            String resultHash,
            Long materializationVersion,
            String outcome,
            LocalDateTime now
    ) {
        jdbc.update("INSERT INTO un_module_ai_fill_history "
                        + "(id,system_id,tenant_id,record_id,record_version,schema_version_id,module_snapshot_id,"
                        + "field_snapshot_id,logical_field_id,field_code,result_schema,source_version_hash,confidence,"
                        + "actor_member_id,provider_id,provider_version,model_snapshot,prompt_version,policy_version_id,"
                        + "proposal_id,idempotency_key,request_id,trace_id,previous_value_hash,result_value_hash,"
                        + "materialization_version,outcome,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,"
                        + "?,?,?,?,?,?,?,?)",
                historyId, request.systemId(), request.tenantId(), Long.parseLong(request.recordId()),
                command.recordVersion(), Long.parseLong(command.schemaVersionId()), command.moduleSnapshotId(),
                command.fieldSnapshotId(), command.logicalFieldId(), command.fieldCode(),
                command.resultSchema().name(), command.sourceVersionHash(), command.confidence(), request.memberId(),
                Long.parseLong(command.provenance().providerId()), command.provenance().providerVersion(),
                command.provenance().model(), command.provenance().promptVersion(),
                Long.parseLong(command.provenance().policyVersionId()), command.proposalId(),
                request.idempotencyKey(), request.requestId(), request.traceId(), previousHash, resultHash,
                materializationVersion, outcome, now);
    }

    private String dependency(String sourceHash) {
        try {
            return json.writeValueAsString(java.util.Map.of("sourceVersionHash", sourceHash));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot write AI_FILL source dependency", failure);
        }
    }

    private String canonical(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot hash AI_FILL typed value", failure);
        }
    }

    private static TypedColumns typed(AiFieldFillCommandCodec.FillCommand command) {
        var value = command.canonicalValue();
        return switch (command.resultSchema()) {
            case STRING -> new TypedColumns(value.textValue(), null, null, null, null);
            case DECIMAL, INTEGER -> new TypedColumns(null, value.decimalValue(), null, null, null);
            case DATE -> new TypedColumns(null, null, LocalDate.parse(value.textValue()), null, null);
            case DATETIME -> new TypedColumns(null, null, null, LocalDateTime.parse(value.textValue()), null);
            case BOOLEAN -> new TypedColumns(null, null, null, null, value.booleanValue());
        };
    }

    private static String valueKind(AiFieldFillFacade.ResultSchema schema) {
        return switch (schema) {
            case STRING -> "STRING";
            case DECIMAL, INTEGER -> "DECIMAL";
            case DATE -> "DATE";
            case DATETIME -> "DATETIME";
            case BOOLEAN -> "BOOLEAN";
        };
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public record StoreRequest(
            long systemId,
            long tenantId,
            long memberId,
            String moduleCode,
            String recordId,
            AiFieldFillCommandCodec.FillCommand command,
            String idempotencyKey,
            String requestId,
            String traceId
    ) { }

    private record LockedRecord(
            long version,
            String status,
            long schemaVersionId,
            long moduleSnapshotId
    ) { }

    private record Current(long id, String valueHash, long version, LocalDateTime createdAt) { }

    private record TypedColumns(
            String stringValue,
            BigDecimal decimalValue,
            LocalDate dateValue,
            LocalDateTime datetimeValue,
            Boolean booleanValue
    ) { }
}
