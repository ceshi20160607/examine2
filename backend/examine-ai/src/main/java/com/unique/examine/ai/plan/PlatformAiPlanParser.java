package com.unique.examine.ai.plan;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict platform-only plan parser. It has no record or mutation operation. */
@Component
public final class PlatformAiPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 8 * 1024;
    private static final Set<String> QUERY_FIELDS = Set.of("operation");
    private static final Set<String> GUIDANCE_FIELDS = Set.of(
            "operation", "requestedSystemCode");
    private static final Set<String> TASK_FIELDS = Set.of(
            "operation", "title", "description", "dueAt", "priority",
            "confidence", "clarification");
    private static final Set<String> OPERATIONS_FIELDS = Set.of(
            "operation", "queryKind", "limit", "confidence", "clarification");
    private final ObjectMapper strict = new ObjectMapper(
            com.fasterxml.jackson.core.JsonFactory.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());

    public Plan parse(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("Platform AI plan must be bounded JSON");
        }
        final JsonNode root;
        try {
            root = strict.readTree(providerOutput);
        } catch (JsonProcessingException failure) {
            throw invalid("Platform AI plan JSON is malformed or duplicated");
        }
        if (root == null || !root.isObject() || !root.path("operation").isTextual()) {
            throw invalid("Platform AI plan must be one JSON object");
        }
        return switch (root.path("operation").textValue()) {
            case "AUTHORIZED_SYSTEMS_QUERY" -> query(root);
            case "SYSTEM_SWITCH_GUIDANCE" -> guidance(root);
            case "PLATFORM_TASK_DRAFT" -> task(root);
            case "PLATFORM_OPERATIONS_QUERY" -> operations(root);
            default -> throw invalid(
                    "Platform AI plan operation is not platform-authorized");
        };
    }

    private Plan query(JsonNode root) {
        exact(root, QUERY_FIELDS);
        return canonical(Operation.AUTHORIZED_SYSTEMS_QUERY, null, null, null);
    }

    private Plan guidance(JsonNode root) {
        exact(root, GUIDANCE_FIELDS);
        var requested = root.get("requestedSystemCode");
        if (requested == null || !(requested.isNull() || requested.isTextual())) {
            throw invalid("Platform AI requestedSystemCode must be text or null");
        }
        String code = null;
        if (requested.isTextual()) {
            code = requested.textValue().strip();
            if (!code.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
                throw invalid("Platform AI requestedSystemCode is invalid");
            }
        }
        return canonical(Operation.SYSTEM_SWITCH_GUIDANCE, code, null, null);
    }

    private Plan task(JsonNode root) {
        exact(root, TASK_FIELDS);
        var title = nullableText(root, "title", 200);
        var description = nullableText(root, "description", 200);
        var clarification = nullableText(root, "clarification", 200);
        Instant dueAt = null;
        var due = root.get("dueAt");
        if (due == null || !(due.isNull() || due.isTextual())) {
            throw invalid("Platform AI task dueAt must be an ISO instant or null");
        }
        if (due.isTextual()) {
            try {
                dueAt = Instant.parse(due.textValue());
            } catch (DateTimeParseException failure) {
                throw invalid("Platform AI task dueAt must be an ISO instant or null");
            }
        }
        PlatformTaskPriority priority = null;
        var priorityNode = root.get("priority");
        if (priorityNode == null
                || !(priorityNode.isNull() || priorityNode.isTextual())) {
            throw invalid("Platform AI task priority is invalid");
        }
        if (priorityNode.isTextual()) {
            try {
                priority = PlatformTaskPriority.valueOf(priorityNode.textValue());
            } catch (IllegalArgumentException failure) {
                throw invalid("Platform AI task priority is invalid");
            }
        }
        var confidenceNode = root.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()) {
            throw invalid("Platform AI task confidence is invalid");
        }
        var confidence = confidenceNode.doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            throw invalid("Platform AI task confidence is invalid");
        }
        if (clarification == null) {
            if (title == null || priority == null) {
                throw invalid("Platform AI actionable task fields are incomplete");
            }
        } else if (title != null || description != null || dueAt != null
                || priority != null) {
            throw invalid("Platform AI clarification cannot contain a task command");
        }
        return canonical(Operation.PLATFORM_TASK_DRAFT, null,
                new TaskDraft(title, description, dueAt, priority,
                        confidence, clarification), null);
    }

    private Plan operations(JsonNode root) {
        exact(root, OPERATIONS_FIELDS);
        var kindNode = root.get("queryKind");
        if (kindNode == null || !(kindNode.isNull() || kindNode.isTextual())) {
            throw invalid("Platform AI operations queryKind is invalid");
        }
        OperationsQueryKind kind = null;
        if (kindNode.isTextual()) {
            try {
                kind = OperationsQueryKind.valueOf(kindNode.textValue());
            } catch (IllegalArgumentException failure) {
                throw invalid("Platform AI operations queryKind is invalid");
            }
        }
        var limitNode = root.get("limit");
        if (limitNode == null
                || !(limitNode.isNull() || limitNode.isIntegralNumber())) {
            throw invalid("Platform AI operations limit is invalid");
        }
        Integer limit = null;
        if (limitNode.isIntegralNumber()) {
            if (!limitNode.canConvertToInt()
                    || limitNode.intValue() < 1 || limitNode.intValue() > 50) {
                throw invalid("Platform AI operations limit is invalid");
            }
            limit = limitNode.intValue();
        }
        var confidenceNode = root.get("confidence");
        if (confidenceNode == null || !confidenceNode.isNumber()) {
            throw invalid("Platform AI operations confidence is invalid");
        }
        var confidence = confidenceNode.doubleValue();
        if (!Double.isFinite(confidence) || confidence < 0d || confidence > 1d) {
            throw invalid("Platform AI operations confidence is invalid");
        }
        var clarification = nullableText(root, "clarification", 200);
        if (clarification == null) {
            if (kind == null || limit == null) {
                throw invalid("Platform AI operations query is incomplete");
            }
        } else if (kind != null || limit != null) {
            throw invalid(
                    "Platform AI operations clarification cannot contain a query");
        }
        return canonical(Operation.PLATFORM_OPERATIONS_QUERY, null, null,
                new OperationsQuery(kind, limit, confidence, clarification));
    }

    private Plan canonical(
            Operation operation,
            String requestedSystemCode,
            TaskDraft taskDraft,
            OperationsQuery operationsQuery) {
        var value = JsonNodeFactory.instance.objectNode();
        value.put("operation", operation.name());
        if (operation == Operation.SYSTEM_SWITCH_GUIDANCE) {
            if (requestedSystemCode == null) value.putNull("requestedSystemCode");
            else value.put("requestedSystemCode", requestedSystemCode);
        }
        if (operation == Operation.PLATFORM_TASK_DRAFT) {
            if (taskDraft.title() == null) value.putNull("title");
            else value.put("title", taskDraft.title());
            if (taskDraft.description() == null) value.putNull("description");
            else value.put("description", taskDraft.description());
            if (taskDraft.dueAt() == null) value.putNull("dueAt");
            else value.put("dueAt", taskDraft.dueAt().toString());
            if (taskDraft.priority() == null) value.putNull("priority");
            else value.put("priority", taskDraft.priority().name());
            value.put("confidence", taskDraft.confidence());
            if (taskDraft.clarification() == null) value.putNull("clarification");
            else value.put("clarification", taskDraft.clarification());
        }
        if (operation == Operation.PLATFORM_OPERATIONS_QUERY) {
            if (operationsQuery.queryKind() == null) value.putNull("queryKind");
            else value.put("queryKind", operationsQuery.queryKind().name());
            if (operationsQuery.limit() == null) value.putNull("limit");
            else value.put("limit", operationsQuery.limit());
            value.put("confidence", operationsQuery.confidence());
            if (operationsQuery.clarification() == null) {
                value.putNull("clarification");
            } else value.put("clarification", operationsQuery.clarification());
        }
        try {
            var canonical = strict.writeValueAsString(value);
            return new Plan(operation, requestedSystemCode, taskDraft,
                    operationsQuery, canonical, AiSupport.sha256(canonical));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot canonicalize platform AI plan", failure);
        }
    }

    private static void exact(JsonNode node, Set<String> expected) {
        var fields = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(fields::add);
        if (!Set.copyOf(fields).equals(expected)) {
            throw invalid("Platform AI plan contains unknown or missing fields");
        }
    }

    private static String nullableText(
            JsonNode root, String field, int maximum) {
        var value = root.get(field);
        if (value == null || !(value.isNull() || value.isTextual())) {
            throw invalid("Platform AI task " + field + " must be text or null");
        }
        if (value.isNull()) return null;
        var text = value.textValue().strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > maximum) {
            throw invalid("Platform AI task " + field + " is invalid");
        }
        return text;
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("PLATFORM_AI_PLAN_INVALID", message);
    }

    public enum Operation {
        AUTHORIZED_SYSTEMS_QUERY,
        SYSTEM_SWITCH_GUIDANCE,
        PLATFORM_TASK_DRAFT,
        PLATFORM_OPERATIONS_QUERY
    }

    public enum PlatformTaskPriority { LOW, NORMAL, HIGH, URGENT }

    public record TaskDraft(
            String title,
            String description,
            Instant dueAt,
            PlatformTaskPriority priority,
            double confidence,
            String clarification
    ) {
        public boolean actionable() { return clarification == null; }
    }

    public enum OperationsQueryKind {
        PERSONAL_TASKS,
        AI_QUOTA,
        SERVICE_HEALTH,
        AGENT_ACTIVITY
    }

    public record OperationsQuery(
            OperationsQueryKind queryKind,
            Integer limit,
            double confidence,
            String clarification
    ) {
        public boolean actionable() { return clarification == null; }
    }

    public record Plan(
            Operation operation,
            String requestedSystemCode,
            TaskDraft taskDraft,
            OperationsQuery operationsQuery,
            String canonicalJson,
            String planHash
    ) { }
}
