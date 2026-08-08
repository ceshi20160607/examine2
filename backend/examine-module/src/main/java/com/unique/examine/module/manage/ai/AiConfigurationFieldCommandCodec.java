package com.unique.examine.module.manage.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.AiConfigurationFieldFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/** Strict canonical codec for one sealed configuration field command. */
final class AiConfigurationFieldCommandCodec {
    private static final Set<String> FIELDS = Set.of(
            "proposalId", "accountId", "systemId", "tenantId", "memberId",
            "authorizationEpoch", "effectivePermissions", "configRootId",
            "moduleId", "moduleCode", "expectedDraftRevision", "sortOrder",
            "field", "policyVersionId", "providerId", "providerVersion",
            "promptVersion", "expiresAt", "requestId", "traceId");
    private static final Set<String> FIELD_FIELDS = Set.of(
            "fieldCode", "fieldName", "fieldType", "required", "settings");
    private static final Set<String> SETTINGS_FIELDS = Set.of(
            "minLength", "maxLength", "trim", "rows", "minimum", "maximum",
            "precision", "scale", "format", "timezone");

    private final ObjectMapper json;

    AiConfigurationFieldCommandCodec(ObjectMapper json) {
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    Command command(
            AiConfigurationFieldFacade.PrepareRequest request,
            AiConfigurationFieldContextReader.Snapshot snapshot,
            Instant expiresAt) {
        return new Command(
                request.proposalId(), request.accountId(), request.systemId(),
                request.tenantId(), request.memberId(),
                request.authorizationEpoch(), request.effectivePermissions(),
                snapshot.configRootId(), snapshot.moduleId(), snapshot.moduleCode(),
                snapshot.draftRevision(), snapshot.sortOrder(), request.field(),
                request.policyVersionId(), request.providerId(),
                request.providerVersion(), request.promptVersion(), expiresAt,
                request.requestId(), request.traceId());
    }

    String encode(Command value) {
        var root = json.createObjectNode();
        root.put("accountId", value.accountId());
        root.put("authorizationEpoch", value.authorizationEpoch());
        root.put("configRootId", value.configRootId());
        var permissions = root.putArray("effectivePermissions");
        value.effectivePermissions().stream().sorted()
                .forEach(permissions::add);
        root.put("expectedDraftRevision", value.expectedDraftRevision());
        root.set("field", field(value.field()));
        root.put("memberId", value.memberId());
        root.put("moduleCode", value.moduleCode());
        root.put("moduleId", value.moduleId());
        root.put("policyVersionId", value.policyVersionId());
        root.put("promptVersion", value.promptVersion());
        root.put("proposalId", value.proposalId());
        root.put("providerId", value.providerId());
        root.put("providerVersion", value.providerVersion());
        root.put("requestId", value.requestId());
        root.put("sortOrder", value.sortOrder());
        root.put("systemId", value.systemId());
        root.put("tenantId", value.tenantId());
        root.put("traceId", value.traceId());
        root.put("expiresAt", value.expiresAt().toString());
        return write(root);
    }

    Command decode(String value) {
        final JsonNode raw;
        try {
            raw = json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw invalid();
        }
        if (!(raw instanceof ObjectNode root) || !exact(root, FIELDS)) {
            throw invalid();
        }
        try {
            var permissions = permissions(root.get("effectivePermissions"));
            var field = field(root.get("field"));
            var proposalId = text(root, "proposalId");
            var accountId = positive(root, "accountId");
            var systemId = positive(root, "systemId");
            var tenantId = positive(root, "tenantId");
            var memberId = positive(root, "memberId");
            var epoch = positive(root, "authorizationEpoch");
            var moduleCode = text(root, "moduleCode");
            var policyVersionId = text(root, "policyVersionId");
            var providerId = text(root, "providerId");
            var providerVersion = nonNegative(root, "providerVersion");
            var promptVersion = text(root, "promptVersion");
            var requestId = text(root, "requestId");
            var traceId = text(root, "traceId");
            new AiConfigurationFieldFacade.PrepareRequest(
                    proposalId, accountId, systemId, tenantId, memberId, epoch,
                    permissions, moduleCode, field, policyVersionId, providerId,
                    providerVersion, promptVersion, requestId, traceId);
            var command = new Command(
                    proposalId, accountId, systemId, tenantId, memberId, epoch,
                    permissions, positive(root, "configRootId"),
                    positive(root, "moduleId"), moduleCode,
                    nonNegative(root, "expectedDraftRevision"),
                    nonNegativeInt(root, "sortOrder"), field,
                    policyVersionId, providerId, providerVersion, promptVersion,
                    instant(root, "expiresAt"), requestId, traceId);
            if (!encode(command).equals(value)) throw invalid();
            return command;
        } catch (BusinessException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw invalid();
        }
    }

    private ObjectNode field(AiConfigurationFieldFacade.FieldDraft value) {
        var result = json.createObjectNode();
        result.put("fieldCode", value.fieldCode());
        result.put("fieldName", value.fieldName());
        result.put("fieldType", value.fieldType().name());
        result.put("required", value.required());
        result.set("settings", settings(value.settings()));
        return result;
    }

    private ObjectNode settings(AiConfigurationFieldFacade.ScalarSettings value) {
        var result = json.createObjectNode();
        put(result, "minLength", value.minLength());
        put(result, "maxLength", value.maxLength());
        put(result, "trim", value.trim());
        put(result, "rows", value.rows());
        put(result, "minimum", value.minimum());
        put(result, "maximum", value.maximum());
        put(result, "precision", value.precision());
        put(result, "scale", value.scale());
        put(result, "format", value.format());
        put(result, "timezone", value.timezone());
        return result;
    }

    private AiConfigurationFieldFacade.FieldDraft field(JsonNode value) {
        if (!(value instanceof ObjectNode object) || !exact(object, FIELD_FIELDS)) {
            throw invalid();
        }
        final AiConfigurationFieldFacade.FieldType type;
        try {
            type = AiConfigurationFieldFacade.FieldType.valueOf(
                    text(object, "fieldType"));
        } catch (RuntimeException failure) {
            throw invalid();
        }
        var required = object.get("required");
        if (required == null || !required.isBoolean()) throw invalid();
        return new AiConfigurationFieldFacade.FieldDraft(
                text(object, "fieldCode"), text(object, "fieldName"), type,
                required.booleanValue(), settings(object.get("settings")));
    }

    private AiConfigurationFieldFacade.ScalarSettings settings(JsonNode value) {
        if (!(value instanceof ObjectNode object)
                || !exact(object, SETTINGS_FIELDS)) throw invalid();
        return new AiConfigurationFieldFacade.ScalarSettings(
                nullableInt(object, "minLength"),
                nullableInt(object, "maxLength"),
                nullableBoolean(object, "trim"),
                nullableInt(object, "rows"),
                nullableText(object, "minimum"),
                nullableText(object, "maximum"),
                nullableInt(object, "precision"),
                nullableInt(object, "scale"),
                nullableText(object, "format"),
                nullableText(object, "timezone"));
    }

    private static Set<String> permissions(JsonNode value) {
        if (value == null || !value.isArray() || value.size() > 2_000) {
            throw invalid();
        }
        var result = new ArrayList<String>();
        value.forEach(item -> {
            if (!item.isTextual() || item.textValue().isBlank()
                    || item.textValue().length() > 256) throw invalid();
            result.add(item.textValue());
        });
        if (new HashSet<>(result).size() != result.size()) throw invalid();
        return Set.copyOf(result);
    }

    private static boolean exact(ObjectNode value, Set<String> fields) {
        if (value.size() != fields.size()) return false;
        var names = new HashSet<String>();
        value.fieldNames().forEachRemaining(names::add);
        return names.equals(fields);
    }

    private static String text(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || !node.isTextual()) throw invalid();
        return node.textValue();
    }

    private static String nullableText(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || node.isNull()) return null;
        if (!node.isTextual()) throw invalid();
        return node.textValue();
    }

    private static Integer nullableInt(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || node.isNull()) return null;
        if (!node.isIntegralNumber() || !node.canConvertToInt()) throw invalid();
        return node.intValue();
    }

    private static Boolean nullableBoolean(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || node.isNull()) return null;
        if (!node.isBoolean()) throw invalid();
        return node.booleanValue();
    }

    private static long positive(ObjectNode value, String name) {
        var result = nonNegative(value, name);
        if (result <= 0) throw invalid();
        return result;
    }

    private static long nonNegative(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()
                || node.longValue() < 0) throw invalid();
        return node.longValue();
    }

    private static int nonNegativeInt(ObjectNode value, String name) {
        var node = value.get(name);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()
                || node.intValue() < 0) throw invalid();
        return node.intValue();
    }

    private static Instant instant(ObjectNode value, String name) {
        try {
            return Instant.parse(text(value, name));
        } catch (DateTimeParseException failure) {
            throw invalid();
        }
    }

    private static void put(ObjectNode value, String name, Object item) {
        if (item == null) value.putNull(name);
        else if (item instanceof Integer number) value.put(name, number);
        else if (item instanceof Boolean bool) value.put(name, bool);
        else value.put(name, item.toString());
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode configuration field command", failure);
        }
    }

    static BusinessException invalid() {
        return new BusinessException(
                "AI_CONFIG_FIELD_COMMAND_INVALID",
                "The configuration field command is invalid",
                HttpStatus.CONFLICT);
    }

    record Command(
            String proposalId,
            long accountId,
            long systemId,
            long tenantId,
            long memberId,
            long authorizationEpoch,
            Set<String> effectivePermissions,
            long configRootId,
            long moduleId,
            String moduleCode,
            long expectedDraftRevision,
            int sortOrder,
            AiConfigurationFieldFacade.FieldDraft field,
            String policyVersionId,
            String providerId,
            long providerVersion,
            String promptVersion,
            Instant expiresAt,
            String requestId,
            String traceId
    ) {
        Command {
            effectivePermissions = Set.copyOf(effectivePermissions);
        }
    }
}
