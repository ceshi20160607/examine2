package com.unique.examine.ai.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

public final class AiPolicy {
    private AiPolicy() {
    }

    public enum DraftStatus {
        DRAFT,
        CHECKED,
        PUBLISHED
    }

    public enum RedactionMode {
        STRICT
    }

    public enum ConfirmationMode {
        REQUIRED
    }

    public static final Set<String> SUPPORTED_OPERATIONS = Set.of(
            "RECORD_QUERY", "RECORD_CREATE", "RECORD_UPDATE", "AI_FILL",
            "CONFIG_FIELD_DRAFT", "CONFIG_SELECTION_FIELD_DRAFT",
            "CONFIG_PAGE_LAYOUT_DRAFT", "CONFIG_FILTER_SCENARIO_DRAFT",
            "CONFIG_FIELD_PERMISSION_STAGE_DRAFT", "RECORD_CONTEXT_SUMMARY",
            "WORK_TASK_QUERY", "WORK_DAILY_REPORT_QUERY",
            "WORK_PROJECT_METRICS_QUERY",
            "TODO_QUERY", "MESSAGE_QUERY", "RECORD_COMMENT_QUERY",
            "RECORD_HISTORY_QUERY", "RECORD_FILE_QUERY",
            "FLOW_INSTANCE_HISTORY_QUERY",
            "RUNTIME_STATISTICS_QUERY", "RUNTIME_REPORT_QUERY",
            "WORK_TASK_DRAFT", "WORK_DAILY_REPORT_DRAFT",
            "FLOW_DEFINITION_DRAFT", "CONFIG_REPORT_DRAFT",
            "CONFIG_PRINT_TEMPLATE_DRAFT");

    public record Draft(
            long id,
            long systemId,
            long tenantId,
            long revision,
            DraftStatus status,
            long providerId,
            long providerVersion,
            Set<String> allowedModuleCodes,
            Map<String, Set<String>> outboundFields,
            Set<String> allowedOperations,
            Map<String, Set<String>> writableFields,
            Map<String, Set<String>> fillFields,
            int maxRows,
            ConfirmationMode confirmationMode,
            int confirmationExpiresSeconds,
            boolean enabled,
            RedactionMode redactionMode,
            String promptVersion,
            String draftHash,
            Long activeVersionId,
            Instant updatedAt,
            long updatedBy
    ) {
        public Draft {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || revision < 1
                    || providerId <= 0 || providerVersion < 0 || updatedBy <= 0) {
                throw new IllegalArgumentException("AI policy draft ids are invalid");
            }
            status = Objects.requireNonNull(status, "status");
            allowedModuleCodes = Set.copyOf(Objects.requireNonNull(
                    allowedModuleCodes, "allowedModuleCodes"));
            if (allowedModuleCodes.isEmpty() || allowedModuleCodes.size() > 64
                    || allowedModuleCodes.stream().anyMatch(
                    value -> !code(value))) {
                throw new IllegalArgumentException(
                        "AI policy allowed modules are invalid");
            }
            outboundFields = immutableFields(outboundFields);
            if (!outboundFields.keySet().equals(allowedModuleCodes)) {
                throw new IllegalArgumentException(
                        "AI policy outbound fields must cover exactly its modules");
            }
            allowedOperations = operations(allowedOperations);
            writableFields = immutableWritableFields(
                    writableFields, allowedModuleCodes);
            fillFields = immutableScopedFields(
                    fillFields, allowedModuleCodes, "fill");
            validateOperationScopes(allowedOperations, writableFields, fillFields);
            if (maxRows < 1 || maxRows > 50) {
                throw new IllegalArgumentException(
                        "AI policy maxRows must be between 1 and 50");
            }
            confirmationMode = Objects.requireNonNull(
                    confirmationMode, "confirmationMode");
            if (confirmationExpiresSeconds < 60
                    || confirmationExpiresSeconds > 3600) {
                throw new IllegalArgumentException(
                        "AI confirmation expiry must be between 60 and 3600 seconds");
            }
            redactionMode = Objects.requireNonNull(redactionMode, "redactionMode");
            promptVersion = versionToken(promptVersion);
            if (draftHash == null || !draftHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("AI policy draft hash is invalid");
            }
            if (activeVersionId != null && activeVersionId <= 0) {
                throw new IllegalArgumentException(
                        "AI active policy version id is invalid");
            }
            Objects.requireNonNull(updatedAt, "updatedAt");
        }

        public Draft(
                long id, long systemId, long tenantId, long revision,
                DraftStatus status, long providerId, long providerVersion,
                Set<String> allowedModuleCodes,
                Map<String, Set<String>> outboundFields,
                int maxRows, boolean enabled, RedactionMode redactionMode,
                String promptVersion, String draftHash, Long activeVersionId,
                Instant updatedAt, long updatedBy
        ) {
            this(id, systemId, tenantId, revision, status, providerId,
                    providerVersion, allowedModuleCodes, outboundFields,
                    Set.of("RECORD_QUERY"), Map.of(), Map.of(), maxRows,
                    ConfirmationMode.REQUIRED, 600, enabled, redactionMode,
                    promptVersion, draftHash, activeVersionId, updatedAt, updatedBy);
        }

        public Draft(
                long id, long systemId, long tenantId, long revision,
                DraftStatus status, long providerId, long providerVersion,
                Set<String> allowedModuleCodes,
                Map<String, Set<String>> outboundFields,
                Set<String> allowedOperations,
                Map<String, Set<String>> writableFields,
                int maxRows, ConfirmationMode confirmationMode,
                int confirmationExpiresSeconds, boolean enabled,
                RedactionMode redactionMode, String promptVersion,
                String draftHash, Long activeVersionId,
                Instant updatedAt, long updatedBy
        ) {
            this(id, systemId, tenantId, revision, status, providerId,
                    providerVersion, allowedModuleCodes, outboundFields,
                    allowedOperations, writableFields, Map.of(), maxRows,
                    confirmationMode, confirmationExpiresSeconds, enabled,
                    redactionMode, promptVersion, draftHash, activeVersionId,
                    updatedAt, updatedBy);
        }
    }

    public record Check(
            long id,
            long systemId,
            long tenantId,
            long policyId,
            long draftRevision,
            String draftHash,
            List<Issue> issues,
            Instant checkedAt,
            long checkedBy
    ) {
        public Check {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || policyId <= 0
                    || draftRevision < 1 || checkedBy <= 0) {
                throw new IllegalArgumentException("AI policy check ids are invalid");
            }
            if (draftHash == null || !draftHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("AI policy check hash is invalid");
            }
            issues = List.copyOf(Objects.requireNonNull(issues, "issues"));
            Objects.requireNonNull(checkedAt, "checkedAt");
        }

        public boolean valid() {
            return issues.stream().noneMatch(issue -> issue.severity() == Severity.BLOCKER);
        }
    }

    public enum Severity {
        BLOCKER,
        WARNING
    }

    public record Issue(String code, String path, String message, Severity severity) {
        public Issue {
            code = text(code, "issue code", 64);
            path = text(path, "issue path", 256);
            message = text(message, "issue message", 500);
            severity = Objects.requireNonNull(severity, "severity");
        }
    }

    public record Version(
            long id,
            long systemId,
            long tenantId,
            long policyId,
            int versionNumber,
            long providerId,
            long providerVersion,
            String model,
            Set<String> allowedModuleCodes,
            Map<String, Set<String>> outboundFields,
            Set<String> allowedOperations,
            Map<String, Set<String>> writableFields,
            Map<String, Set<String>> fillFields,
            int maxRows,
            ConfirmationMode confirmationMode,
            int confirmationExpiresSeconds,
            boolean enabled,
            RedactionMode redactionMode,
            String promptVersion,
            String snapshotHash,
            Instant publishedAt,
            long publishedBy
    ) {
        public Version {
            if (id <= 0 || systemId <= 0 || tenantId <= 0 || policyId <= 0
                    || versionNumber < 1 || providerId <= 0
                    || providerVersion < 0 || publishedBy <= 0) {
                throw new IllegalArgumentException("AI policy version ids are invalid");
            }
            model = text(model, "model", 128);
            allowedModuleCodes = Set.copyOf(Objects.requireNonNull(
                    allowedModuleCodes, "allowedModuleCodes"));
            if (allowedModuleCodes.isEmpty() || allowedModuleCodes.size() > 64
                    || allowedModuleCodes.stream().anyMatch(value -> !code(value))) {
                throw new IllegalArgumentException(
                        "AI policy version allowed modules are invalid");
            }
            outboundFields = immutableFields(outboundFields);
            if (!outboundFields.keySet().equals(allowedModuleCodes)) {
                throw new IllegalArgumentException(
                        "AI policy version outbound fields must cover exactly its modules");
            }
            allowedOperations = operations(allowedOperations);
            writableFields = immutableWritableFields(
                    writableFields, allowedModuleCodes);
            fillFields = immutableScopedFields(
                    fillFields, allowedModuleCodes, "fill");
            validateOperationScopes(allowedOperations, writableFields, fillFields);
            if (maxRows < 1 || maxRows > 50) {
                throw new IllegalArgumentException("AI policy maxRows is invalid");
            }
            confirmationMode = Objects.requireNonNull(
                    confirmationMode, "confirmationMode");
            if (confirmationExpiresSeconds < 60
                    || confirmationExpiresSeconds > 3600) {
                throw new IllegalArgumentException(
                        "AI confirmation expiry must be between 60 and 3600 seconds");
            }
            redactionMode = Objects.requireNonNull(redactionMode, "redactionMode");
            promptVersion = versionToken(promptVersion);
            if (snapshotHash == null || !snapshotHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("AI policy snapshot hash is invalid");
            }
            Objects.requireNonNull(publishedAt, "publishedAt");
        }

        public Version(
                long id, long systemId, long tenantId, long policyId,
                int versionNumber, long providerId, long providerVersion,
                String model, Set<String> allowedModuleCodes,
                Map<String, Set<String>> outboundFields,
                Set<String> allowedOperations, int maxRows, boolean enabled,
                RedactionMode redactionMode, String promptVersion,
                String snapshotHash, Instant publishedAt, long publishedBy
        ) {
            this(id, systemId, tenantId, policyId, versionNumber, providerId,
                    providerVersion, model, allowedModuleCodes, outboundFields,
                    allowedOperations, Map.of(), Map.of(), maxRows,
                    ConfirmationMode.REQUIRED,
                    600, enabled, redactionMode, promptVersion, snapshotHash,
                    publishedAt, publishedBy);
        }

        public Version(
                long id, long systemId, long tenantId, long policyId,
                int versionNumber, long providerId, long providerVersion,
                String model, Set<String> allowedModuleCodes,
                Map<String, Set<String>> outboundFields,
                Set<String> allowedOperations,
                Map<String, Set<String>> writableFields,
                int maxRows, ConfirmationMode confirmationMode,
                int confirmationExpiresSeconds, boolean enabled,
                RedactionMode redactionMode, String promptVersion,
                String snapshotHash, Instant publishedAt, long publishedBy
        ) {
            this(id, systemId, tenantId, policyId, versionNumber, providerId,
                    providerVersion, model, allowedModuleCodes, outboundFields,
                    allowedOperations, writableFields, Map.of(), maxRows,
                    confirmationMode, confirmationExpiresSeconds, enabled,
                    redactionMode, promptVersion, snapshotHash, publishedAt,
                    publishedBy);
        }
    }

    public record PublishReplay(
            long id,
            long systemId,
            long tenantId,
            long policyId,
            String requestKey,
            String requestHash,
            long versionId,
            Instant createdAt
    ) {
        public PublishReplay {
            if (id <= 0 || systemId <= 0 || tenantId <= 0
                    || policyId <= 0 || versionId <= 0) {
                throw new IllegalArgumentException("AI policy replay ids are invalid");
            }
            requestKey = token(requestKey, "requestKey", 128);
            if (requestHash == null || !requestHash.matches("^[0-9a-f]{64}$")) {
                throw new IllegalArgumentException("AI policy replay hash is invalid");
            }
            Objects.requireNonNull(createdAt, "createdAt");
        }
    }

    private static Map<String, Set<String>> immutableFields(
            Map<String, Set<String>> value) {
        Objects.requireNonNull(value, "outboundFields");
        var result = new TreeMap<String, Set<String>>();
        value.forEach((module, fields) -> {
            if (!code(module) || fields == null || fields.isEmpty()
                    || fields.size() > 128 || fields.stream().anyMatch(
                    field -> !code(field))) {
                throw new IllegalArgumentException(
                        "AI policy outbound field allowlist is invalid");
            }
            result.put(module, Set.copyOf(fields));
        });
        return Map.copyOf(new LinkedHashMap<>(result));
    }

    private static Map<String, Set<String>> immutableWritableFields(
            Map<String, Set<String>> value, Set<String> allowedModules) {
        Objects.requireNonNull(value, "writableFields");
        var result = new TreeMap<String, Set<String>>();
        value.forEach((module, fields) -> {
            if (!code(module) || !allowedModules.contains(module)
                    || fields == null || fields.isEmpty() || fields.size() > 128
                    || fields.stream().anyMatch(field -> !code(field))) {
                throw new IllegalArgumentException(
                        "AI policy writable field allowlist is invalid");
            }
            result.put(module, Set.copyOf(fields));
        });
        return Map.copyOf(new LinkedHashMap<>(result));
    }

    private static Map<String, Set<String>> immutableScopedFields(
            Map<String, Set<String>> value,
            Set<String> allowedModules,
            String label) {
        Objects.requireNonNull(value, label + "Fields");
        var result = new TreeMap<String, Set<String>>();
        value.forEach((module, fields) -> {
            if (!code(module) || !allowedModules.contains(module)
                    || fields == null || fields.isEmpty() || fields.size() > 128
                    || fields.stream().anyMatch(field -> !code(field))) {
                throw new IllegalArgumentException(
                        "AI policy " + label + " field allowlist is invalid");
            }
            result.put(module, Set.copyOf(fields));
        });
        return Map.copyOf(new LinkedHashMap<>(result));
    }

    private static Set<String> operations(Set<String> value) {
        Objects.requireNonNull(value, "allowedOperations");
        value = Set.copyOf(value);
        if (value.isEmpty() || !SUPPORTED_OPERATIONS.containsAll(value)) {
            throw new IllegalArgumentException(
                    "AI policy allowed operations are invalid");
        }
        return value;
    }

    private static void validateOperationScopes(
            Set<String> operations,
            Map<String, Set<String>> writableFields,
            Map<String, Set<String>> fillFields) {
        var writes = operations.contains("RECORD_CREATE")
                || operations.contains("RECORD_UPDATE");
        if (writes != !writableFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI policy write operations require writable fields and vice versa");
        }
        if (operations.contains("AI_FILL") != !fillFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "AI_FILL operation requires fill fields and vice versa");
        }
    }

    private static boolean code(String value) {
        return value != null && value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$");
    }

    private static String text(String value, String field, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("AI " + field + " is required");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException("AI " + field + " is too long");
        }
        return value;
    }

    private static String token(String value, String field, int maximum) {
        value = text(value, field, maximum);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0," + (maximum - 1) + "}$")) {
            throw new IllegalArgumentException("AI " + field + " is invalid");
        }
        return value;
    }

    private static String versionToken(String value) {
        value = text(value, "promptVersion", 64);
        if (!value.matches("^[A-Za-z0-9][A-Za-z0-9_.-]{0,63}$")) {
            throw new IllegalArgumentException("AI promptVersion is invalid");
        }
        return value;
    }
}
