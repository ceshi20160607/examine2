package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.examine.core.ai.AiFieldFillFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves one live, row-scoped and permission-projected AI_FILL source fact. */
@Component
public class AiFieldFillRuntimeReader {
    private static final long UNAVAILABLE_ACCOUNT_ID = 0L;
    private static final Set<String> SOURCE_TYPES = Set.of(
            "TEXT", "TEXTAREA", "PHONE", "EMAIL", "URL", "NUMBER", "PERCENT", "MONEY",
            "DATE", "DATETIME", "RADIO", "RATING", "PROGRESS", "BARCODE", "RICH_TEXT",
            "STATUS", "SWITCH");

    private final RecordRuntimeService records;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public AiFieldFillRuntimeReader(
            RecordRuntimeService records,
            JdbcTemplate jdbc,
            ObjectMapper json
    ) {
        this.records = records;
        this.jdbc = jdbc;
        this.json = json;
    }

    public Resolved resolve(AccessContext context) {
        requirePermissions(context);
        var session = new RuntimeSession(
                UNAVAILABLE_ACCOUNT_ID, context.systemId(), context.memberId(),
                context.tenantId(), context.permissions());
        var schema = records.schema(session, context.moduleCode());
        if (schema.authzEpoch() != context.authorizationEpoch()) {
            throw conflict("AI_AUTHORIZATION_STALE", "AI_FILL authorization changed");
        }
        var target = schema.fields().stream()
                .filter(field -> context.fieldCode().equals(field.fieldCode()))
                .filter(field -> "AI_FILL".equals(field.type()) && field.readable())
                .findFirst().orElseThrow(() -> forbidden(
                        "AI_FILL_FIELD_UNAVAILABLE", "AI_FILL target field is unavailable"));
        var contract = contract(target);
        var fieldsById = new LinkedHashMap<String, RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> fieldsById.put(field.logicalFieldId(), field));
        var sources = new ArrayList<RecordRuntimeViews.FieldCapability>();
        for (var sourceId : contract.sourceFieldIds()) {
            var source = fieldsById.get(sourceId);
            if (source == null || !source.readable() || source.masked()
                    || !SOURCE_TYPES.contains(source.type())) {
                throw forbidden(
                        "AI_FILL_SOURCE_UNAVAILABLE",
                        "An AI_FILL source field is no longer readable");
            }
            sources.add(source);
        }
        var detail = records.detail(
                session, context.moduleCode(), Long.parseLong(context.recordId()));
        if (!schema.schemaVersionId().equals(detail.schemaVersionId())) {
            throw conflict("RECORD_SCHEMA_STALE", "AI_FILL record schema is stale");
        }
        if (!detail.actions().contains("UPDATE")) {
            throw forbidden(
                    "PERMISSION_DENIED", "The record cannot currently be updated");
        }
        var sourceState = sourceState(context, schema, detail.version(), sources);
        var projected = sources.stream().map(source -> new AiFieldFillFacade.SourceValue(
                source.logicalFieldId(), source.fieldCode(), source.fieldName(), source.type(),
                sourceState.displayValues().get(Long.parseLong(source.logicalFieldId())))).toList();
        var current = current(context, Long.parseLong(target.logicalFieldId()));
        var snapshot = new AiFieldFillFacade.SourceSnapshot(
                context.moduleCode(), context.recordId(), detail.version(), schema.schemaVersionId(),
                contract, sourceState.hash(), projected, current == null ? null : current.view());
        return new Resolved(
                snapshot,
                Long.parseLong(schema.moduleSnapshotId()),
                Long.parseLong(target.logicalFieldId()),
                Long.parseLong(target.logicalFieldId()),
                current == null ? null : current.valueHash(),
                current == null ? null : current.materializationVersion());
    }

    private AiFieldFillFacade.FieldContract contract(
            RecordRuntimeViews.FieldCapability field
    ) {
        var properties = field.schema();
        if (properties == null || !properties.isObject()
                || !properties.path("sourceFieldIds").isArray()) {
            throw conflict("AI_FILL_CONTRACT_INVALID", "Published AI_FILL contract is unavailable");
        }
        var sourceIds = new ArrayList<String>();
        properties.path("sourceFieldIds").forEach(value -> sourceIds.add(value.asText()));
        try {
            return new AiFieldFillFacade.FieldContract(
                    field.logicalFieldId(), field.fieldCode(), field.fieldName(),
                    AiFieldFillFacade.ResultSchema.valueOf(
                            properties.path("resultSchema").asText()),
                    sourceIds,
                    properties.path("promptTemplate").asText(),
                    properties.path("modelPolicy").asText(),
                    properties.path("minConfidence").doubleValue(),
                    AiFieldFillFacade.OverwriteMode.valueOf(
                            properties.path("overwriteMode").asText()));
        } catch (RuntimeException failure) {
            if (failure instanceof BusinessException business) {
                throw business;
            }
            throw conflict("AI_FILL_CONTRACT_INVALID", "Published AI_FILL contract is invalid");
        }
    }

    private SourceState sourceState(
            AccessContext context,
            RecordRuntimeViews.RecordSchema schema,
            long recordVersion,
            List<RecordRuntimeViews.FieldCapability> sources
    ) {
        var rows = jdbc.query(
                "SELECT field_snapshot_id,ordinal,field_type,string_value,text_value,decimal_value,"
                        + "date_value,datetime_value,time_value,boolean_value,reference_value,display_value,version "
                        + "FROM un_module_record_value WHERE system_id=? AND tenant_id=? AND record_id=? "
                        + "AND schema_version_id=? AND module_snapshot_id=? AND field_snapshot_id IN ("
                        + placeholders(sources.size()) + ") ORDER BY field_snapshot_id,ordinal",
                (row, number) -> new SourceFingerprint(
                        row.getLong("field_snapshot_id"), row.getInt("ordinal"),
                        row.getString("field_type"), row.getString("string_value"),
                        row.getString("text_value"), row.getString("decimal_value"),
                        row.getString("date_value"), row.getString("datetime_value"),
                        row.getString("time_value"), row.getObject("boolean_value"),
                        row.getString("reference_value"), row.getString("display_value"),
                        row.getLong("version")),
                sourceArguments(context, schema, sources));
        var byField = new LinkedHashMap<Long, List<SourceFingerprint>>();
        rows.forEach(row -> byField.computeIfAbsent(row.fieldId(), ignored -> new ArrayList<>()).add(row));
        var canonical = new ArrayList<Object>();
        canonical.add(schema.schemaVersionId());
        canonical.add(recordVersion);
        sources.stream().map(source -> Long.parseLong(source.logicalFieldId()))
                .sorted().forEach(fieldId -> canonical.add(Map.of(
                        "fieldId", fieldId,
                        "values", byField.getOrDefault(fieldId, List.of()))));
        var displayValues = new LinkedHashMap<Long, String>();
        byField.forEach((fieldId, values) -> displayValues.put(fieldId, String.join(", ",
                values.stream().map(SourceFingerprint::displayValue)
                        .filter(java.util.Objects::nonNull).toList())));
        try {
            return new SourceState(
                    sha256(json.writeValueAsString(canonical)), Map.copyOf(displayValues));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot hash AI_FILL source versions", failure);
        }
    }

    private Object[] sourceArguments(
            AccessContext context,
            RecordRuntimeViews.RecordSchema schema,
            List<RecordRuntimeViews.FieldCapability> sources
    ) {
        var values = new ArrayList<Object>();
        values.add(context.systemId());
        values.add(context.tenantId());
        values.add(Long.parseLong(context.recordId()));
        values.add(Long.parseLong(schema.schemaVersionId()));
        values.add(Long.parseLong(schema.moduleSnapshotId()));
        sources.stream().map(source -> Long.parseLong(source.logicalFieldId()))
                .sorted().forEach(values::add);
        return values.toArray();
    }

    private Current current(AccessContext context, long fieldId) {
        var rows = jdbc.query(
                "SELECT display_value,value_hash,version,confidence,provider_id,provider_version,"
                        + "model_snapshot,prompt_version,policy_version_id "
                        + "FROM un_module_ai_fill_materialization WHERE system_id=? AND tenant_id=? "
                        + "AND record_id=? AND field_snapshot_id=?",
                (row, number) -> new Current(
                        new AiFieldFillFacade.CurrentValue(
                                row.getString("display_value"), row.getLong("version"),
                                row.getDouble("confidence"),
                                new AiFieldFillFacade.Provenance(
                                        Long.toString(row.getLong("provider_id")),
                                        row.getLong("provider_version"), row.getString("model_snapshot"),
                                        row.getString("prompt_version"),
                                        Long.toString(row.getLong("policy_version_id")))),
                        row.getString("value_hash"), row.getLong("version")),
                context.systemId(), context.tenantId(), Long.parseLong(context.recordId()), fieldId);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static void requirePermissions(AccessContext context) {
        if (!context.permissions().contains("system.runtime.access")
                || !context.permissions().contains("ai.agent.use")
                || !context.permissions().contains("module." + context.moduleCode() + ".view")
                || !context.permissions().contains("module." + context.moduleCode() + ".update")) {
            throw forbidden("PERMISSION_DENIED", "AI_FILL runtime permission is required");
        }
    }

    private static String placeholders(int count) {
        return String.join(",", java.util.Collections.nCopies(count, "?"));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static BusinessException forbidden(String code, String message) {
        return new BusinessException(code, message, HttpStatus.FORBIDDEN);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(code, message, HttpStatus.CONFLICT);
    }

    public record AccessContext(
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> permissions,
            String moduleCode,
            String recordId,
            String fieldCode
    ) { }

    public record Resolved(
            AiFieldFillFacade.SourceSnapshot snapshot,
            long moduleSnapshotId,
            long fieldSnapshotId,
            long logicalFieldId,
            String currentValueHash,
            Long currentMaterializationVersion
    ) { }

    private record Current(
            AiFieldFillFacade.CurrentValue view,
            String valueHash,
            long materializationVersion
    ) { }

    private record SourceState(String hash, Map<Long, String> displayValues) { }

    private record SourceFingerprint(
            long fieldId,
            int ordinal,
            String type,
            String stringValue,
            String textValue,
            String decimalValue,
            String dateValue,
            String datetimeValue,
            String timeValue,
            Object booleanValue,
            String referenceValue,
            String displayValue,
            long version
    ) { }
}
