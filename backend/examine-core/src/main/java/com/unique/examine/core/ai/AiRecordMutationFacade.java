package com.unique.examine.core.ai;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Owner boundary for explicitly confirmed AI record mutations.
 *
 * <p>Planning code can only call {@link #prepare(PrepareRequest)}. The returned
 * command is authenticated and opaque; only {@link #execute(ExecuteRequest)}
 * can hand it to the authoritative record runtime after rechecking the live
 * authorization and schema context.</p>
 */
public interface AiRecordMutationFacade {

    PreparedMutation prepare(PrepareRequest request);

    RecordView execute(ExecuteRequest request);

    enum Operation {
        RECORD_CREATE,
        RECORD_UPDATE
    }

    record PrepareRequest(
            String confirmationId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            Operation operation,
            String canonicalOwnerCommandJson,
            Set<String> writableFieldCodes,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            confirmationId = required(confirmationId, "confirmationId", 128);
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            operation = Objects.requireNonNull(operation, "operation");
            if (canonicalOwnerCommandJson == null
                    || canonicalOwnerCommandJson.isBlank()
                    || canonicalOwnerCommandJson.getBytes(StandardCharsets.UTF_8).length > 64 * 1024) {
                throw new IllegalArgumentException("canonicalOwnerCommandJson is invalid");
            }
            writableFieldCodes = codes(writableFieldCodes, "writableFieldCodes");
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record PreparedMutation(MutationPreview preview, SealedCommand sealedCommand) {
        public PreparedMutation {
            preview = Objects.requireNonNull(preview, "preview");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        }
    }

    record MutationPreview(
            Operation operation,
            String moduleCode,
            String schemaVersionId,
            String recordId,
            Long expectedVersion,
            String beforeTitle,
            String afterTitle,
            List<FieldChange> changes
    ) {
        public MutationPreview {
            operation = Objects.requireNonNull(operation, "operation");
            moduleCode = code(moduleCode, "moduleCode");
            schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
            if (operation == Operation.RECORD_CREATE) {
                if (recordId != null || expectedVersion != null) {
                    throw new IllegalArgumentException("create preview cannot contain a record identity");
                }
            } else {
                recordId = positiveDecimal(recordId, "recordId");
                if (expectedVersion == null || expectedVersion < 0) {
                    throw new IllegalArgumentException("update preview expectedVersion is invalid");
                }
            }
            changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        }
    }

    record FieldChange(
            String fieldCode,
            String fieldName,
            String type,
            String beforeDisplayValue,
            String afterDisplayValue,
            boolean masked
    ) {
        public FieldChange {
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = required(fieldName, "fieldName", 160);
            type = required(type, "type", 32);
        }
    }

    record SealedCommand(
            String ciphertext,
            String encryptionKeyVersion,
            String commandSha256
    ) {
        public SealedCommand {
            ciphertext = required(ciphertext, "ciphertext", 131_072);
            encryptionKeyVersion = required(encryptionKeyVersion, "encryptionKeyVersion", 64);
            if (commandSha256 == null || !commandSha256.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("commandSha256 is invalid");
            }
        }
    }

    record ExecuteRequest(
            String confirmationId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            Operation operation,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            confirmationId = required(confirmationId, "confirmationId", 128);
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            operation = Objects.requireNonNull(operation, "operation");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record RecordView(
            String recordId,
            String recordNo,
            long version,
            String status,
            String title,
            String schemaVersionId,
            List<DisplayValue> values
    ) {
        public RecordView {
            recordId = positiveDecimal(recordId, "recordId");
            recordNo = required(recordNo, "recordNo", 128);
            if (version < 0) {
                throw new IllegalArgumentException("version must not be negative");
            }
            status = required(status, "status", 32);
            schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
            values = List.copyOf(Objects.requireNonNull(values, "values"));
        }
    }

    record DisplayValue(
            String fieldCode,
            String fieldName,
            String type,
            String displayValue,
            boolean masked
    ) {
        public DisplayValue {
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = required(fieldName, "fieldName", 160);
            type = required(type, "type", 32);
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static Set<String> codes(Set<String> values, String name) {
        if (values == null || values.stream().anyMatch(value -> value == null
                || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$"))
                || new LinkedHashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException(name + " are invalid");
        }
        return Set.copyOf(values);
    }

    private static String code(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String positiveDecimal(String value, String name) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0 || !Long.toString(parsed).equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
            return value;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(name + " is invalid", exception);
        }
    }

    private static String required(String value, String name, int maximumLength) {
        if (value == null || value.isBlank() || value.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
