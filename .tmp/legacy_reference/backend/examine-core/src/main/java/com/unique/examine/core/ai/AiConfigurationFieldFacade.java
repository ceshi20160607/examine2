package com.unique.examine.core.ai;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;
import java.util.Set;

/**
 * Explicit-confirmation owner boundary for adding one scalar field to an
 * existing configuration draft. Neither operation can publish configuration.
 */
public interface AiConfigurationFieldFacade {

    PreparedField prepare(PrepareRequest request);

    FieldReadback execute(ExecuteRequest request);

    enum FieldType {
        TEXT,
        LONG_TEXT,
        INTEGER,
        DECIMAL,
        BOOLEAN,
        DATE,
        DATETIME
    }

    record ScalarSettings(
            Integer minLength,
            Integer maxLength,
            Boolean trim,
            Integer rows,
            String minimum,
            String maximum,
            Integer precision,
            Integer scale,
            String format,
            String timezone
    ) {
        public ScalarSettings {
            range(minLength, 0, 100_000, "minLength");
            range(maxLength, 1, 1_000_000, "maxLength");
            range(rows, 1, 100, "rows");
            range(precision, 1, 38, "precision");
            range(scale, 0, 18, "scale");
            if (minLength != null && maxLength != null && minLength > maxLength) {
                throw new IllegalArgumentException("text length range is invalid");
            }
            format = optionalCode(format, "format", 32);
            timezone = optionalCode(timezone, "timezone", 32);
        }

        public static ScalarSettings empty() {
            return new ScalarSettings(
                    null, null, null, null, null, null,
                    null, null, null, null);
        }

        void validate(FieldType type) {
            switch (type) {
                case TEXT -> {
                    absent(rows, "rows");
                    noNumeric();
                    noTemporal();
                }
                case LONG_TEXT -> {
                    noNumeric();
                    noTemporal();
                }
                case INTEGER -> {
                    noText();
                    noTemporal();
                    numericRange();
                    if (scale != null && scale != 0) {
                        throw new IllegalArgumentException(
                                "INTEGER scale must be zero");
                    }
                    integral(minimum, "minimum");
                    integral(maximum, "maximum");
                }
                case DECIMAL -> {
                    noText();
                    noTemporal();
                    numericRange();
                    if (precision != null && scale != null && scale > precision) {
                        throw new IllegalArgumentException(
                                "DECIMAL scale exceeds precision");
                    }
                }
                case BOOLEAN -> {
                    noText();
                    noNumeric();
                    noTemporal();
                }
                case DATE -> {
                    noText();
                    absent(precision, "precision");
                    absent(scale, "scale");
                    absent(timezone, "timezone");
                    date(minimum, "minimum");
                    date(maximum, "maximum");
                    if (minimum != null && maximum != null
                            && LocalDate.parse(minimum).isAfter(
                            LocalDate.parse(maximum))) {
                        throw new IllegalArgumentException("date range is invalid");
                    }
                }
                case DATETIME -> {
                    noText();
                    absent(precision, "precision");
                    absent(scale, "scale");
                    datetime(minimum, "minimum");
                    datetime(maximum, "maximum");
                    if (minimum != null && maximum != null
                            && LocalDateTime.parse(minimum).isAfter(
                            LocalDateTime.parse(maximum))) {
                        throw new IllegalArgumentException(
                                "datetime range is invalid");
                    }
                    if (timezone != null && !"UTC".equals(timezone)) {
                        throw new IllegalArgumentException(
                                "DATETIME timezone must be UTC");
                    }
                }
            }
        }

        private void noText() {
            absent(minLength, "minLength");
            absent(maxLength, "maxLength");
            absent(trim, "trim");
            absent(rows, "rows");
        }

        private void noNumeric() {
            absent(minimum, "minimum");
            absent(maximum, "maximum");
            absent(precision, "precision");
            absent(scale, "scale");
        }

        private void noTemporal() {
            absent(format, "format");
            absent(timezone, "timezone");
        }

        private void numericRange() {
            decimal(minimum, "minimum");
            decimal(maximum, "maximum");
            if (minimum != null && maximum != null
                    && new BigDecimal(minimum).compareTo(
                    new BigDecimal(maximum)) > 0) {
                throw new IllegalArgumentException("numeric range is invalid");
            }
        }
    }

    record FieldDraft(
            String fieldCode,
            String fieldName,
            FieldType fieldType,
            boolean required,
            ScalarSettings settings
    ) {
        public FieldDraft {
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = Objects.requireNonNull(fieldType, "fieldType");
            settings = Objects.requireNonNull(settings, "settings");
            settings.validate(fieldType);
        }
    }

    record PrepareRequest(
            String proposalId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String moduleCode,
            FieldDraft field,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            String requestId,
            String traceId
    ) {
        public PrepareRequest {
            proposalId = token(proposalId, "proposalId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            moduleCode = code(moduleCode, "moduleCode");
            field = Objects.requireNonNull(field, "field");
            policyVersionId = positiveDecimal(policyVersionId, "policyVersionId");
            providerId = positiveDecimal(providerId, "providerId");
            nonNegative(providerVersion, "providerVersion");
            promptVersion = token(promptVersion, "promptVersion", 64);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record PreparedField(
            FieldPreview preview,
            Instant expiresAt,
            SealedCommand sealedCommand
    ) {
        public PreparedField {
            preview = Objects.requireNonNull(preview, "preview");
            expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
        }
    }

    record FieldPreview(
            String configRootId,
            String moduleId,
            String moduleCode,
            long expectedDraftRevision,
            long nextDraftRevision,
            FieldDraft field
    ) {
        public FieldPreview {
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "moduleCode");
            nonNegative(expectedDraftRevision, "expectedDraftRevision");
            if (nextDraftRevision != expectedDraftRevision + 1) {
                throw new IllegalArgumentException(
                        "nextDraftRevision is invalid");
            }
            field = Objects.requireNonNull(field, "field");
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
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            String configRootId,
            String moduleId,
            String moduleCode,
            long expectedDraftRevision,
            SealedCommand sealedCommand,
            String idempotencyKey,
            String requestId,
            String traceId
    ) {
        public ExecuteRequest {
            proposalId = token(proposalId, "proposalId", 128);
            positive(accountId, "accountId");
            positive(systemId, "systemId");
            positive(tenantId, "tenantId");
            positive(memberId, "memberId");
            positive(authorizationEpoch, "authorizationEpoch");
            effectivePermissions = permissions(effectivePermissions);
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "moduleCode");
            nonNegative(expectedDraftRevision, "expectedDraftRevision");
            sealedCommand = Objects.requireNonNull(sealedCommand, "sealedCommand");
            idempotencyKey = token(idempotencyKey, "idempotencyKey", 128);
            requestId = token(requestId, "requestId", 64);
            traceId = token(traceId, "traceId", 64);
        }
    }

    record FieldReadback(
            String configRootId,
            String moduleId,
            String moduleCode,
            long draftRevision,
            FieldView field
    ) {
        public FieldReadback {
            configRootId = positiveDecimal(configRootId, "configRootId");
            moduleId = positiveDecimal(moduleId, "moduleId");
            moduleCode = code(moduleCode, "moduleCode");
            positive(draftRevision, "draftRevision");
            field = Objects.requireNonNull(field, "field");
        }
    }

    record FieldView(
            String fieldId,
            String fieldCode,
            String fieldName,
            FieldType fieldType,
            boolean required,
            ScalarSettings settings,
            int sortOrder,
            long version
    ) {
        public FieldView {
            fieldId = positiveDecimal(fieldId, "fieldId");
            fieldCode = code(fieldCode, "fieldCode");
            fieldName = text(fieldName, "fieldName", 128);
            fieldType = Objects.requireNonNull(fieldType, "fieldType");
            settings = Objects.requireNonNull(settings, "settings");
            settings.validate(fieldType);
            if (sortOrder < 0) throw new IllegalArgumentException("sortOrder is invalid");
            nonNegative(version, "version");
        }
    }

    private static Set<String> permissions(Set<String> values) {
        if (values == null || values.stream().anyMatch(
                value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("effectivePermissions are invalid");
        }
        return Set.copyOf(values);
    }

    private static String code(String value, String name) {
        if (value == null || !value.matches("^[a-z][a-z0-9_]{1,63}$")) {
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

    private static String text(String value, String name, int maximum) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        value = value.strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value;
    }

    private static String optionalCode(
            String value, String name, int maximum) {
        if (value == null) return null;
        if (value.isBlank() || value.length() > maximum
                || !value.matches("^[A-Za-z0-9][A-Za-z0-9_./:-]*$")) {
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

    private static void range(
            Integer value, int minimum, int maximum, String name) {
        if (value != null && (value < minimum || value > maximum)) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void decimal(String value, String name) {
        if (value == null) return;
        try {
            var parsed = new BigDecimal(value);
            if (parsed.precision() > 38 || Math.max(parsed.scale(), 0) > 18
                    || !parsed.toPlainString().equals(value)) {
                throw new IllegalArgumentException(name + " is invalid");
            }
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static void integral(String value, String name) {
        if (value != null && new BigDecimal(value).scale() > 0) {
            throw new IllegalArgumentException(name + " is invalid");
        }
    }

    private static void date(String value, String name) {
        if (value == null) return;
        try {
            LocalDate.parse(value);
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static void datetime(String value, String name) {
        if (value == null) return;
        try {
            LocalDateTime.parse(value);
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException(name + " is invalid", failure);
        }
    }

    private static void absent(Object value, String name) {
        if (value != null) {
            throw new IllegalArgumentException(name + " is not allowed");
        }
    }

    private static void positive(long value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " is invalid");
    }

    private static void nonNegative(long value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " is invalid");
    }
}
