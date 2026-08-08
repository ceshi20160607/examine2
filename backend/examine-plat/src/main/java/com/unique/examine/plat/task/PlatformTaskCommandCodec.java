package com.unique.examine.plat.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.core.ai.PlatformTaskFacade;
import com.unique.examine.core.error.BusinessException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Set;

/** Strict canonical plaintext codec used only inside the authenticated envelope. */
final class PlatformTaskCommandCodec {
    private static final Set<String> FIELDS = Set.of(
            "proposalId", "accountId", "authorizationEpoch", "title",
            "description", "dueAt", "priority", "expiresAt", "payloadHash");
    private static final Set<String> PAYLOAD_FIELDS = Set.of(
            "proposalId", "accountId", "authorizationEpoch", "title",
            "description", "dueAt", "priority");

    private final ObjectMapper json;

    PlatformTaskCommandCodec(ObjectMapper json) {
        this.json = java.util.Objects.requireNonNull(json, "json");
    }

    Encoded encode(
            String proposalId,
            long accountId,
            long authorizationEpoch,
            PlatformTaskFacade.TaskDraft draft,
            Instant expiresAt) {
        var payload = payload(
                proposalId, accountId, authorizationEpoch, draft.title(),
                draft.description(), draft.dueAt(), draft.priority());
        var payloadHash = PlatformTaskCommandSealer.sha256(write(payload));
        var command = payload.deepCopy();
        command.put("expiresAt", expiresAt.toString());
        command.put("payloadHash", payloadHash);
        return new Encoded(write(command), payloadHash);
    }

    Decoded decode(String value) {
        final JsonNode raw;
        try {
            raw = json.readTree(value);
        } catch (JsonProcessingException failure) {
            throw invalid();
        }
        if (!(raw instanceof ObjectNode root)
                || root.size() != FIELDS.size()
                || !FIELDS.equals(fieldNames(root))) {
            throw invalid();
        }
        var proposalId = token(root, "proposalId", 128);
        var accountId = positive(root, "accountId");
        var epoch = positive(root, "authorizationEpoch");
        var title = text(root, "title", 200, false);
        var description = text(root, "description", 2_000, true);
        var dueAt = instant(root, "dueAt", true);
        final PlatformTaskFacade.Priority priority;
        try {
            priority = PlatformTaskFacade.Priority.valueOf(
                    token(root, "priority", 16));
        } catch (RuntimeException failure) {
            throw invalid();
        }
        var expiresAt = instant(root, "expiresAt", false);
        var payloadHash = root.path("payloadHash").textValue();
        if (payloadHash == null || !payloadHash.matches("^[0-9a-f]{64}$")) {
            throw invalid();
        }
        var canonicalPayload = write(payload(
                proposalId, accountId, epoch, title, description, dueAt, priority));
        if (!PlatformTaskCommandSealer.equal(
                payloadHash, PlatformTaskCommandSealer.sha256(canonicalPayload))) {
            throw invalid();
        }
        return new Decoded(
                proposalId, accountId, epoch, title, description, dueAt,
                priority, expiresAt, payloadHash);
    }

    private ObjectNode payload(
            String proposalId,
            long accountId,
            long authorizationEpoch,
            String title,
            String description,
            Instant dueAt,
            PlatformTaskFacade.Priority priority) {
        var result = json.createObjectNode();
        PAYLOAD_FIELDS.stream().sorted().forEach(field -> {
            switch (field) {
                case "proposalId" -> result.put(field, proposalId);
                case "accountId" -> result.put(field, accountId);
                case "authorizationEpoch" -> result.put(field, authorizationEpoch);
                case "title" -> result.put(field, title);
                case "description" -> {
                    if (description == null) result.putNull(field);
                    else result.put(field, description);
                }
                case "dueAt" -> {
                    if (dueAt == null) result.putNull(field);
                    else result.put(field, dueAt.toString());
                }
                case "priority" -> result.put(field, priority.name());
                default -> throw new IllegalStateException();
            }
        });
        return result;
    }

    private String write(JsonNode value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Cannot encode platform task owner command", failure);
        }
    }

    private static Set<String> fieldNames(ObjectNode value) {
        var names = new java.util.HashSet<String>();
        value.fieldNames().forEachRemaining(names::add);
        return Set.copyOf(names);
    }

    private static String token(ObjectNode value, String field, int maximum) {
        var result = value.path(field).textValue();
        if (result == null || result.length() > maximum
                || !result.matches("^[A-Za-z0-9][A-Za-z0-9_.:-]{0,"
                + (maximum - 1) + "}$")) throw invalid();
        return result;
    }

    private static String text(
            ObjectNode value, String field, int maximum, boolean nullable) {
        var node = value.get(field);
        if (nullable && (node == null || node.isNull())) return null;
        if (node == null || !node.isTextual() || node.textValue().isBlank()
                || node.textValue().codePointCount(
                0, node.textValue().length()) > maximum) throw invalid();
        return node.textValue();
    }

    private static long positive(ObjectNode value, String field) {
        var node = value.get(field);
        if (node == null || !node.isIntegralNumber() || !node.canConvertToLong()
                || node.longValue() <= 0) throw invalid();
        return node.longValue();
    }

    private static Instant instant(
            ObjectNode value, String field, boolean nullable) {
        var node = value.get(field);
        if (nullable && (node == null || node.isNull())) return null;
        if (node == null || !node.isTextual()) throw invalid();
        try {
            return Instant.parse(node.textValue());
        } catch (DateTimeParseException failure) {
            throw invalid();
        }
    }

    static BusinessException invalid() {
        return new BusinessException(
                "PLATFORM_TASK_COMMAND_INVALID",
                "The platform task owner command is invalid",
                HttpStatus.CONFLICT);
    }

    record Encoded(String plaintext, String payloadHash) { }

    record Decoded(
            String proposalId,
            long accountId,
            long authorizationEpoch,
            String title,
            String description,
            Instant dueAt,
            PlatformTaskFacade.Priority priority,
            Instant expiresAt,
            String payloadHash) { }
}
