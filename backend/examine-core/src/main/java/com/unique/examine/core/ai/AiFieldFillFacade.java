package com.unique.examine.core.ai;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Module-owned boundary for governed AI field materialization.
 *
 * <p>The caller can read only a permission-projected source snapshot, prepare
 * an authenticated command without changing the record, then explicitly
 * execute or reject that exact command. Ordinary record writers never receive
 * an AI_FILL value.</p>
 */
public interface AiFieldFillFacade {

    SourceSnapshot sourceSnapshot(SourceRequest request);

    PreparedFill prepare(PrepareRequest request);

    FillReadback execute(ExecuteRequest request);

    RejectionReadback reject(RejectRequest request);

    enum ResultSchema {
        STRING,
        DECIMAL,
        INTEGER,
        DATE,
        DATETIME,
        BOOLEAN
    }

    enum OverwriteMode {
        NEVER,
        CONFIRM
    }

    record SourceRequest(
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            String recordId,
            String fieldCode,
            String requestId,
            String traceId
    ) {
        public SourceRequest {
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            fieldCode = code(fieldCode, "fieldCode");
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record SourceSnapshot(
            String moduleCode,
            String recordId,
            long recordVersion,
            String schemaVersionId,
            FieldContract contract,
            String sourceVersionHash,
            List<SourceValue> sources,
            CurrentValue currentValue
    ) {
        public SourceSnapshot {
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            if (recordVersion < 0) {
                throw new IllegalArgumentException("recordVersion must not be negative");
            }
            schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
            contract = Objects.requireNonNull(contract, "contract");
            sourceVersionHash = sha256(sourceVersionHash, "sourceVersionHash");
            sources = List.copyOf(Objects.requireNonNull(sources, "sources"));
        }
    }

    record FieldContract(
            String fieldId,
            String fieldCode,
            String fieldName,
            ResultSchema resultSchema,
            List<String> sourceFieldIds,
            String promptTemplate,
            String modelPolicy,
            double minimumConfidence,
            OverwriteMode overwriteMode
    ) {
        public FieldContract {
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = required(fieldName, "fieldName", 160);
            resultSchema = Objects.requireNonNull(resultSchema, "resultSchema");
            sourceFieldIds = positiveDecimals(sourceFieldIds, "sourceFieldIds");
            if (sourceFieldIds.isEmpty() || sourceFieldIds.size() > 16) {
                throw new IllegalArgumentException("sourceFieldIds must contain 1..16 fields");
            }
            promptTemplate = required(promptTemplate, "promptTemplate", 4000);
            modelPolicy = required(modelPolicy, "modelPolicy", 128);
            if (!"SYSTEM_DEFAULT".equals(modelPolicy)) {
                throw new IllegalArgumentException("modelPolicy must be SYSTEM_DEFAULT");
            }
            if (!Double.isFinite(minimumConfidence)
                    || minimumConfidence < 0.50d || minimumConfidence > 1d) {
                throw new IllegalArgumentException("minimumConfidence must be within 0.50..1.00");
            }
            overwriteMode = Objects.requireNonNull(overwriteMode, "overwriteMode");
        }
    }

    record SourceValue(
            String fieldId,
            String fieldCode,
            String fieldName,
            String fieldType,
            String displayValue
    ) {
        public SourceValue {
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = required(fieldName, "fieldName", 160);
            fieldType = required(fieldType, "fieldType", 32);
        }
    }

    record CurrentValue(
            String displayValue,
            long materializationVersion,
            double confidence,
            Provenance provenance
    ) {
        public CurrentValue {
            if (materializationVersion < 0 || !Double.isFinite(confidence)
                    || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("current AI value metadata is invalid");
            }
            provenance = Objects.requireNonNull(provenance, "provenance");
        }
    }

    record Provenance(
            String providerId,
            long providerVersion,
            String model,
            String promptVersion,
            String policyVersionId
    ) {
        public Provenance {
            providerId = positiveDecimal(providerId, "providerId");
            if (providerVersion < 0) {
                throw new IllegalArgumentException("providerVersion must not be negative");
            }
            model = required(model, "model", 160);
            promptVersion = required(promptVersion, "promptVersion", 64);
            policyVersionId = positiveDecimal(policyVersionId, "policyVersionId");
        }
    }

    record PrepareRequest(
            String proposalId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            String recordId,
            String fieldCode,
            String expectedSchemaVersionId,
            long expectedRecordVersion,
            String expectedSourceVersionHash,
            String canonicalResultJson,
            Provenance provenance,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            proposalId = required(proposalId, "proposalId", 128);
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            fieldCode = code(fieldCode, "fieldCode");
            expectedSchemaVersionId = positiveDecimal(
                    expectedSchemaVersionId, "expectedSchemaVersionId");
            if (expectedRecordVersion < 0) {
                throw new IllegalArgumentException("expectedRecordVersion must not be negative");
            }
            expectedSourceVersionHash = sha256(
                    expectedSourceVersionHash, "expectedSourceVersionHash");
            if (canonicalResultJson == null || canonicalResultJson.isBlank()
                    || canonicalResultJson.getBytes(StandardCharsets.UTF_8).length > 16 * 1024) {
                throw new IllegalArgumentException("canonicalResultJson is invalid");
            }
            provenance = Objects.requireNonNull(provenance, "provenance");
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record PreparedFill(FillPreview preview, SealedCommand sealedCommand) {
        public PreparedFill {
            preview = Objects.requireNonNull(preview, "preview");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        }
    }

    record FillPreview(
            String moduleCode,
            String recordId,
            long recordVersion,
            String schemaVersionId,
            String fieldId,
            String fieldCode,
            ResultSchema resultSchema,
            String sourceVersionHash,
            String beforeDisplayValue,
            String afterDisplayValue,
            double confidence,
            boolean overwrite
    ) {
        public FillPreview {
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            if (recordVersion < 0) {
                throw new IllegalArgumentException("recordVersion must not be negative");
            }
            schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            resultSchema = Objects.requireNonNull(resultSchema, "resultSchema");
            sourceVersionHash = sha256(sourceVersionHash, "sourceVersionHash");
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("confidence must be within 0..1");
            }
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
            commandSha256 = sha256(commandSha256, "commandSha256");
        }
    }

    record ExecuteRequest(
            String proposalId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            String recordId,
            String fieldCode,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            proposalId = required(proposalId, "proposalId", 128);
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            fieldCode = code(fieldCode, "fieldCode");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record FillReadback(
            String historyId,
            String moduleCode,
            String recordId,
            long recordVersion,
            String schemaVersionId,
            String fieldId,
            String fieldCode,
            ResultSchema resultSchema,
            String displayValue,
            double confidence,
            long materializationVersion,
            String outcome,
            Provenance provenance
    ) {
        public FillReadback {
            historyId = positiveDecimal(historyId, "historyId");
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            if (recordVersion < 0 || materializationVersion < 0) {
                throw new IllegalArgumentException("AI fill versions must not be negative");
            }
            schemaVersionId = positiveDecimal(schemaVersionId, "schemaVersionId");
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            resultSchema = Objects.requireNonNull(resultSchema, "resultSchema");
            if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
                throw new IllegalArgumentException("confidence must be within 0..1");
            }
            if (!Set.of("CREATED", "OVERWRITTEN").contains(outcome)) {
                throw new IllegalArgumentException("AI fill outcome is invalid");
            }
            provenance = Objects.requireNonNull(provenance, "provenance");
        }
    }

    record RejectRequest(
            String proposalId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            String recordId,
            String fieldCode,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public RejectRequest {
            proposalId = required(proposalId, "proposalId", 128);
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            recordId = positiveDecimal(recordId, "recordId");
            fieldCode = code(fieldCode, "fieldCode");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            idempotencyKey = required(idempotencyKey, "idempotencyKey", 128);
            requestId = required(requestId, "requestId", 128);
            traceId = required(traceId, "traceId", 128);
        }
    }

    record RejectionReadback(
            String historyId,
            String proposalId,
            String recordId,
            String fieldCode,
            String outcome
    ) {
        public RejectionReadback {
            historyId = positiveDecimal(historyId, "historyId");
            proposalId = required(proposalId, "proposalId", 128);
            recordId = positiveDecimal(recordId, "recordId");
            fieldCode = code(fieldCode, "fieldCode");
            if (!"REJECTED".equals(outcome)) {
                throw new IllegalArgumentException("AI fill rejection outcome is invalid");
            }
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static List<String> positiveDecimals(List<String> values, String name) {
        if (values == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        var result = values.stream().map(value -> positiveDecimal(value, name)).toList();
        if (Set.copyOf(result).size() != result.size()) {
            throw new IllegalArgumentException(name + " contains duplicates");
        }
        return result;
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
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static String sha256(String value, String name) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
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
