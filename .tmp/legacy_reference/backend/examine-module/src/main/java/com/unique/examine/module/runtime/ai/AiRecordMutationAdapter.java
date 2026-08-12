package com.unique.examine.module.runtime.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.unique.examine.core.ai.AiRecordMutationFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import com.unique.examine.module.runtime.service.RecordRuntimeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Confirmation-bound module owner adapter for AI record create/update.
 *
 * <p>This adapter owns no record persistence. Prepare is read-only; execute
 * authenticates the opaque command and delegates one native create/update call
 * so all runtime validation, history, audit, derived values and side effects
 * remain authoritative in {@link RecordRuntimeService}.</p>
 */
@Component
public class AiRecordMutationAdapter implements AiRecordMutationFacade {
    private static final long UNAVAILABLE_ACCOUNT_ID = 0L;

    private final RecordOwner records;
    private final AiMutationCommandSealer sealer;
    private final AiRecordMutationCommandCodec codec;

    @Autowired
    public AiRecordMutationAdapter(
            RecordRuntimeService records,
            AiMutationCommandSealer sealer
    ) {
        this(new NativeRecordOwner(records), sealer, new AiRecordMutationCommandCodec());
    }

    AiRecordMutationAdapter(
            RecordOwner records,
            AiMutationCommandSealer sealer,
            AiRecordMutationCommandCodec codec
    ) {
        this.records = Objects.requireNonNull(records, "records");
        this.sealer = Objects.requireNonNull(sealer, "sealer");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    @Transactional(readOnly = true)
    public PreparedMutation prepare(PrepareRequest request) {
        Objects.requireNonNull(request, "request");
        requireCapabilities(request.effectivePermissions(), request.moduleCode(), request.operation());
        var session = session(
                request.systemId(), request.tenantId(), request.memberId(), request.effectivePermissions());
        var command = codec.parse(request.canonicalOwnerCommandJson(), request.operation());
        var schema = liveSchema(
                session, request.moduleCode(), request.authorizationEpoch(), command.schemaVersionId());
        requireCreateCapability(request.operation(), schema);
        var fields = writableFields(schema, command, request.writableFieldCodes());
        var current = currentRecord(session, request.moduleCode(), request.operation(), command);
        var preview = preview(request.operation(), request.moduleCode(), command, fields, current);
        var binding = binding(request.confirmationId(), request.systemId(), request.tenantId(),
                request.memberId(), request.moduleCode(), request.operation());
        var sealed = sealer.seal(
                command.canonicalJson(),
                codec.sealPayload(command.canonicalJson(), request.writableFieldCodes()),
                binding);
        return new PreparedMutation(preview, sealed);
    }

    @Override
    @Transactional
    public RecordView execute(ExecuteRequest request) {
        Objects.requireNonNull(request, "request");
        requireCapabilities(request.effectivePermissions(), request.moduleCode(), request.operation());
        var binding = binding(request.confirmationId(), request.systemId(), request.tenantId(),
                request.memberId(), request.moduleCode(), request.operation());
        var opened = codec.openPayload(sealer.open(request.sealedCommand(), binding));
        if (!MessageDigest.isEqual(
                request.sealedCommand().commandSha256().getBytes(StandardCharsets.US_ASCII),
                AiMutationCommandSealer.sha256(opened.canonicalCommandJson())
                        .getBytes(StandardCharsets.US_ASCII))) {
            throw sealedInvalid();
        }
        var command = codec.parse(opened.canonicalCommandJson(), request.operation());
        var session = session(
                request.systemId(), request.tenantId(), request.memberId(), request.effectivePermissions());
        var schema = liveSchema(
                session, request.moduleCode(), request.authorizationEpoch(), command.schemaVersionId());
        requireCreateCapability(request.operation(), schema);
        writableFields(schema, command, opened.writableFieldCodes());
        currentRecord(session, request.moduleCode(), request.operation(), command);

        final RecordRuntimeViews.RecordDetail result;
        if (request.operation() == Operation.RECORD_CREATE) {
            result = records.create(
                    session, request.moduleCode(), command.createRequest(),
                    request.idempotencyKey(), request.requestId(), request.traceId());
        } else {
            result = records.update(
                    session, request.moduleCode(), Long.parseLong(command.recordId()),
                    command.updateRequest(), request.idempotencyKey(),
                    request.requestId(), request.traceId());
        }
        return project(result, schema);
    }

    private RecordRuntimeViews.RecordSchema liveSchema(
            RuntimeSession session,
            String moduleCode,
            long authorizationEpoch,
            String commandSchemaVersion
    ) {
        var schema = records.schema(session, moduleCode);
        if (schema.authzEpoch() != authorizationEpoch) {
            throw new BusinessException(
                    "AI_AUTHORIZATION_STALE",
                    "The member authorization changed before the AI mutation",
                    HttpStatus.CONFLICT);
        }
        if (!schema.schemaVersionId().equals(commandSchemaVersion)) {
            throw new BusinessException(
                    "RECORD_SCHEMA_STALE",
                    "The runtime schema changed before the AI mutation",
                    HttpStatus.CONFLICT);
        }
        return schema;
    }

    private static Map<String, RecordRuntimeViews.FieldCapability> writableFields(
            RecordRuntimeViews.RecordSchema schema,
            AiRecordMutationCommandCodec.ParsedCommand command,
            Set<String> policyFields
    ) {
        if (!policyFields.containsAll(command.touchedFieldCodes())) {
            throw new BusinessException(
                    "AI_POLICY_FIELD_DENIED",
                    "The mutation contains a field outside the published AI policy",
                    HttpStatus.FORBIDDEN);
        }
        var fields = new LinkedHashMap<String, RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> fields.put(field.fieldCode(), field));
        for (var code : command.touchedFieldCodes()) {
            var field = fields.get(code);
            if (field == null || !field.readable() || !field.writable()) {
                throw new BusinessException(
                        "AI_FIELD_NOT_WRITABLE",
                        "The current member cannot write field " + code,
                        HttpStatus.FORBIDDEN);
            }
        }
        command.relations().forEach(relation -> {
            var type = fields.get(relation.fieldCode()).type();
            if (!Set.of("RELATION", "REFERENCE").contains(type)) {
                throw mutationInvalid("relations contains a non-relation field");
            }
        });
        command.subtables().forEach(subtable -> {
            if (!"SUBTABLE".equals(fields.get(subtable.fieldCode()).type())) {
                throw mutationInvalid("subtables contains a non-subtable field");
            }
        });
        return Map.copyOf(fields);
    }

    private RecordRuntimeViews.RecordDetail currentRecord(
            RuntimeSession session,
            String moduleCode,
            Operation operation,
            AiRecordMutationCommandCodec.ParsedCommand command
    ) {
        if (operation == Operation.RECORD_CREATE) {
            return null;
        }
        var current = records.detail(session, moduleCode, Long.parseLong(command.recordId()));
        if (!current.schemaVersionId().equals(command.schemaVersionId())) {
            throw new BusinessException(
                    "RECORD_SCHEMA_STALE",
                    "The target record uses a stale schema",
                    HttpStatus.CONFLICT);
        }
        if (current.version() != command.expectedVersion()) {
            throw new BusinessException(
                    "RECORD_VERSION_CONFLICT",
                    "The target record changed before the AI mutation",
                    HttpStatus.CONFLICT);
        }
        if (!current.actions().contains("UPDATE")) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The target record cannot currently be updated",
                    HttpStatus.FORBIDDEN);
        }
        return current;
    }

    private static void requireCreateCapability(
            Operation operation,
            RecordRuntimeViews.RecordSchema schema
    ) {
        if (operation == Operation.RECORD_CREATE && !schema.actions().contains("CREATE")) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The runtime module cannot currently create records",
                    HttpStatus.FORBIDDEN);
        }
    }

    private AiRecordMutationFacade.MutationPreview preview(
            Operation operation,
            String moduleCode,
            AiRecordMutationCommandCodec.ParsedCommand command,
            Map<String, RecordRuntimeViews.FieldCapability> fields,
            RecordRuntimeViews.RecordDetail current
    ) {
        var before = new LinkedHashMap<String, String>();
        if (current != null) {
            current.values().forEach(value -> before.put(value.fieldCode(), value.displayValue()));
        }
        var relationSizes = new LinkedHashMap<String, Integer>();
        command.relations().forEach(value -> relationSizes.put(value.fieldCode(), value.targets().size()));
        var subtableSizes = new LinkedHashMap<String, Integer>();
        command.subtables().forEach(value -> subtableSizes.put(value.fieldCode(), value.rows().size()));
        var changes = new ArrayList<AiRecordMutationFacade.FieldChange>();
        command.touchedFieldCodes().stream().sorted().forEach(code -> {
            var field = fields.get(code);
            var masked = field.masked();
            String after;
            if (relationSizes.containsKey(code)) {
                after = relationSizes.get(code) + " target(s)";
            } else if (subtableSizes.containsKey(code)) {
                after = subtableSizes.get(code) + " row(s)";
            } else {
                after = masked ? "******" : display(command.values().get(code));
            }
            changes.add(new AiRecordMutationFacade.FieldChange(
                    code, field.fieldName(), field.type(), before.get(code), after, masked));
        });
        return new AiRecordMutationFacade.MutationPreview(
                operation,
                moduleCode,
                command.schemaVersionId(),
                command.recordId(),
                command.expectedVersion(),
                current == null ? null : current.title(),
                command.title() == null && current != null ? current.title() : command.title(),
                changes);
    }

    private static RecordView project(
            RecordRuntimeViews.RecordDetail record,
            RecordRuntimeViews.RecordSchema schema
    ) {
        var capabilities = new LinkedHashMap<String, RecordRuntimeViews.FieldCapability>();
        schema.fields().forEach(field -> capabilities.put(field.fieldCode(), field));
        var values = record.values().stream().map(value -> {
            var field = capabilities.get(value.fieldCode());
            if (field == null || !field.readable()) {
                return null;
            }
            return new AiRecordMutationFacade.DisplayValue(
                    value.fieldCode(), value.fieldName(), value.type(),
                    value.displayValue(), field.masked());
        }).filter(Objects::nonNull).toList();
        return new RecordView(
                record.recordId(), record.recordNo(), record.version(), record.status(),
                record.title(), record.schemaVersionId(), values);
    }

    private static String display(JsonNode value) {
        if (value == null || value.isNull()) {
            return null;
        }
        return value.isTextual() ? value.textValue() : value.toString();
    }

    private static RuntimeSession session(
            long systemId,
            long tenantId,
            long memberId,
            Set<String> permissions
    ) {
        return new RuntimeSession(
                UNAVAILABLE_ACCOUNT_ID, systemId, memberId, tenantId, permissions);
    }

    private static void requireCapabilities(
            Set<String> permissions,
            String moduleCode,
            Operation operation
    ) {
        var action = operation == Operation.RECORD_CREATE ? "create" : "update";
        if (!permissions.contains("system.runtime.access")
                || !permissions.contains("module." + moduleCode + ".view")
                || !permissions.contains("module." + moduleCode + "." + action)) {
            throw new BusinessException(
                    "PERMISSION_DENIED",
                    "The current member cannot perform this runtime mutation",
                    HttpStatus.FORBIDDEN);
        }
    }

    private static AiMutationCommandSealer.Binding binding(
            String confirmationId,
            long systemId,
            long tenantId,
            long memberId,
            String moduleCode,
            Operation operation
    ) {
        return new AiMutationCommandSealer.Binding(
                systemId, tenantId, memberId, confirmationId, moduleCode, operation);
    }

    private static BusinessException mutationInvalid(String message) {
        return new BusinessException(
                "AI_RECORD_MUTATION_INVALID", message, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private static BusinessException sealedInvalid() {
        return new BusinessException(
                "AI_MUTATION_COMMAND_INVALID",
                "AI mutation command hash verification failed",
                HttpStatus.CONFLICT);
    }

    interface RecordOwner {
        RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode);

        RecordRuntimeViews.RecordDetail detail(RuntimeSession session, String moduleCode, long recordId);

        RecordRuntimeViews.RecordDetail create(
                RuntimeSession session,
                String moduleCode,
                RecordRuntimeViews.CreateRecordRequest request,
                String idempotencyKey,
                String requestId,
                String traceId);

        RecordRuntimeViews.RecordDetail update(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                RecordRuntimeViews.UpdateRecordRequest request,
                String idempotencyKey,
                String requestId,
                String traceId);
    }

    private static final class NativeRecordOwner implements RecordOwner {
        private final RecordRuntimeService records;

        private NativeRecordOwner(RecordRuntimeService records) {
            this.records = Objects.requireNonNull(records, "records");
        }

        @Override
        public RecordRuntimeViews.RecordSchema schema(RuntimeSession session, String moduleCode) {
            return records.schema(session, moduleCode);
        }

        @Override
        public RecordRuntimeViews.RecordDetail detail(
                RuntimeSession session,
                String moduleCode,
                long recordId
        ) {
            return records.detail(session, moduleCode, recordId);
        }

        @Override
        public RecordRuntimeViews.RecordDetail create(
                RuntimeSession session,
                String moduleCode,
                RecordRuntimeViews.CreateRecordRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.create(session, moduleCode, request, idempotencyKey, requestId, traceId);
        }

        @Override
        public RecordRuntimeViews.RecordDetail update(
                RuntimeSession session,
                String moduleCode,
                long recordId,
                RecordRuntimeViews.UpdateRecordRequest request,
                String idempotencyKey,
                String requestId,
                String traceId
        ) {
            return records.update(
                    session, moduleCode, recordId, request, idempotencyKey, requestId, traceId);
        }
    }
}
