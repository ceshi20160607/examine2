package com.unique.examine.core.ai;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Module-owner boundary for one explicitly confirmed report or print draft.
 * Prepare is read-only; execute accepts only the authenticated owner command.
 */
public interface AiModuleGeneratedDraftFacade {

    PreparedDraft prepare(PrepareRequest request);

    DraftReadback execute(ExecuteRequest request);

    enum Operation {
        CONFIG_REPORT_DRAFT,
        CONFIG_PRINT_TEMPLATE_DRAFT
    }

    record ReportDraft(
            String code,
            String name,
            String description,
            String dataSourceId,
            List<String> outputFieldCodes
    ) {
        public ReportDraft {
            code = AiModuleGeneratedDraftFacade.code(code, "code");
            name = text(name, "name", 200, false);
            description = text(description, "description", 2_000, true);
            dataSourceId = positiveDecimal(dataSourceId, "dataSourceId");
            outputFieldCodes = codes(
                    outputFieldCodes, "outputFieldCodes", 100);
        }
    }

    record PrintTemplateDraft(
            String moduleCode,
            String code,
            String name,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer
    ) {
        public PrintTemplateDraft {
            moduleCode = lowerCode(moduleCode, "moduleCode");
            code = lowerCode(code, "code");
            name = text(name, "name", 128, false);
            paperSize = oneOf(paperSize, "paperSize", Set.of("A4", "A5"));
            orientation = oneOf(
                    orientation, "orientation",
                    Set.of("PORTRAIT", "LANDSCAPE"));
            title = text(title, "title", 160, false);
            fieldCodes = codes(fieldCodes, "fieldCodes", 50);
            footer = text(footer, "footer", 300, true);
        }
    }

    record PrepareRequest(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            Operation operation,
            ReportDraft report,
            PrintTemplateDraft printTemplate,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            proposalId = token(proposalId, "proposalId", 128);
            sessionId = token(sessionId, "sessionId", 128);
            turnId = token(turnId, "turnId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, report, printTemplate);
            policyVersionId = positiveDecimal(
                    policyVersionId, "policyVersionId");
            providerId = positiveDecimal(providerId, "providerId");
            nonNegative(providerVersion, "providerVersion");
            promptVersion = token(promptVersion, "promptVersion", 64);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record PreparedDraft(
            DraftPreview preview,
            SealedCommand sealedCommand,
            Instant expiresAt
    ) {
        public PreparedDraft {
            preview = Objects.requireNonNull(preview, "preview");
            sealedCommand = Objects.requireNonNull(
                    sealedCommand, "sealedCommand");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        }
    }

    record DraftPreview(
            Operation operation,
            ReportDraft report,
            PrintTemplateDraft printTemplate
    ) {
        public DraftPreview {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, report, printTemplate);
        }
    }

    record SealedCommand(
            String ciphertext,
            String encryptionKeyVersion,
            String commandSha256
    ) {
        public SealedCommand {
            ciphertext = opaque(ciphertext, "ciphertext", 131_072);
            encryptionKeyVersion = token(
                    encryptionKeyVersion, "encryptionKeyVersion", 64);
            hash(commandSha256, "commandSha256");
        }
    }

    record ExecuteRequest(
            String proposalId,
            String sessionId,
            String turnId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            Operation operation,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            proposalId = token(proposalId, "proposalId", 128);
            sessionId = token(sessionId, "sessionId", 128);
            turnId = token(turnId, "turnId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            operation = Objects.requireNonNull(operation, "operation");
            sealedCommand = Objects.requireNonNull(
                    sealedCommand, "sealedCommand");
            idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record DraftReadback(
            Operation operation,
            ReportReadback report,
            PrintTemplateReadback printTemplate
    ) {
        public DraftReadback {
            operation = Objects.requireNonNull(operation, "operation");
            matching(operation, report, printTemplate);
        }
    }

    record ReportReadback(
            String reportId,
            String code,
            String name,
            String description,
            String dataSourceId,
            List<String> outputFieldCodes,
            long draftVersion,
            long version,
            Instant createdAt,
            Instant updatedAt,
            boolean published
    ) {
        public ReportReadback {
            reportId = positiveDecimal(reportId, "reportId");
            code = AiModuleGeneratedDraftFacade.code(code, "code");
            name = text(name, "name", 200, false);
            description = text(description, "description", 2_000, true);
            dataSourceId = positiveDecimal(dataSourceId, "dataSourceId");
            outputFieldCodes = codes(
                    outputFieldCodes, "outputFieldCodes", 100);
            positive(draftVersion, "draftVersion");
            positive(version, "version");
            createdAt = Objects.requireNonNull(createdAt, "createdAt");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
            if (updatedAt.isBefore(createdAt) || published) {
                throw new IllegalArgumentException(
                        "report readback is not an unpublished draft");
            }
        }
    }

    record PrintTemplateReadback(
            String templateId,
            String moduleId,
            String moduleCode,
            String code,
            String name,
            String paperSize,
            String orientation,
            String title,
            List<String> fieldCodes,
            String footer,
            String status,
            long version,
            Instant updatedAt,
            boolean published
    ) {
        public PrintTemplateReadback {
            templateId = positiveDecimal(templateId, "templateId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = lowerCode(moduleCode, "moduleCode");
            code = lowerCode(code, "code");
            name = text(name, "name", 128, false);
            paperSize = oneOf(paperSize, "paperSize", Set.of("A4", "A5"));
            orientation = oneOf(
                    orientation, "orientation",
                    Set.of("PORTRAIT", "LANDSCAPE"));
            title = text(title, "title", 160, false);
            fieldCodes = codes(fieldCodes, "fieldCodes", 50);
            footer = text(footer, "footer", 300, true);
            if (!"DISABLED".equals(status) || published) {
                throw new IllegalArgumentException(
                        "print template is not a disabled unpublished draft");
            }
            nonNegative(version, "version");
            updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        }
    }

    private static void matching(
            Operation operation, Object report, Object printTemplate) {
        if ((operation == Operation.CONFIG_REPORT_DRAFT) != (report != null)
                || (operation == Operation.CONFIG_PRINT_TEMPLATE_DRAFT)
                != (printTemplate != null)) {
            throw new IllegalArgumentException(
                    "exactly the matching module draft is required");
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(
                    "effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static List<String> codes(
            List<String> values, String name, int maximum) {
        if (values == null || values.isEmpty() || values.size() > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        var normalized = values.stream()
                .map(value -> code(value, name)).toList();
        if (normalized.stream().distinct().count() != normalized.size()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return List.copyOf(normalized);
    }

    private static String code(String value, String name) {
        if (value == null || !value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String lowerCode(String value, String name) {
        if (value == null || !value.matches("^[a-z][a-z0-9_]{1,63}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String oneOf(
            String value, String name, Set<String> allowed) {
        if (value == null || !allowed.contains(value)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String text(
            String value, String name, int maximum, boolean nullable) {
        if (nullable && value == null) return null;
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String token(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String opaque(String value, String name, int maximum) {
        if (value == null || value.length() > maximum
                || !value.matches("^[A-Za-z0-9_-]+$")) {
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

    private static void hash(String value, String name) {
        if (value == null || !value.matches("^[0-9a-f]{64}$")) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static void nonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
    }
}
