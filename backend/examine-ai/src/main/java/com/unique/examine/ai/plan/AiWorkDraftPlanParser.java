package com.unique.examine.ai.plan;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.unique.examine.ai.AiSupport;
import com.unique.examine.ai.domain.AiPolicy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Strict, fail-closed parser for one Work task or personal report draft. */
@Component
public final class AiWorkDraftPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 32 * 1024;
    private static final Set<String> OPERATIONS = Set.of(
            "WORK_TASK_DRAFT", "WORK_DAILY_REPORT_DRAFT");
    private static final Set<String> TASK_FIELDS = Set.of(
            "operation", "title", "description", "assigneeMemberId",
            "projectId", "dueAt", "confidence", "clarification");
    private static final Set<String> REPORT_FIELDS = Set.of(
            "operation", "workDate", "completedWork", "plannedWork",
            "blockers", "confidence", "clarification");
    private static final Pattern FORBIDDEN_TEXT = Pattern.compile(
            "(?i)(?:jdbc:|file:|<script|javascript:)");

    private final ObjectMapper strict;
    private final Clock clock;

    public AiWorkDraftPlanParser() {
        this(Clock.systemUTC());
    }

    AiWorkDraftPlanParser(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public boolean isWorkDraft(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) return false;
        try {
            var root = strict.readTree(providerOutput);
            var operation = root == null ? null : root.get("operation");
            return root != null && root.isObject() && operation != null
                    && operation.isTextual()
                    && OPERATIONS.contains(operation.textValue());
        } catch (JsonProcessingException failure) {
            return false;
        }
    }

    public Plan parse(String providerOutput, AiPolicy.Version policy) {
        Objects.requireNonNull(policy, "policy");
        var root = object(providerOutput);
        var operation = operation(root.get("operation"));
        if (!policy.allowedOperations().contains(operation.name())) {
            throw invalid("AI Work draft operation is not authorized by policy");
        }
        exact(root, operation == Operation.WORK_TASK_DRAFT
                ? TASK_FIELDS : REPORT_FIELDS);
        rejectForbiddenText(root);
        var confidence = decimal(root.get("confidence"), "confidence");
        if (confidence.compareTo(BigDecimal.ZERO) < 0
                || confidence.compareTo(BigDecimal.ONE) > 0) {
            throw invalid("AI Work draft confidence must be within 0..1");
        }
        var clarification = nullableText(
                root.get("clarification"), "clarification", 500);
        if (operation == Operation.WORK_TASK_DRAFT) {
            return task(root, confidence.doubleValue(), clarification);
        }
        return report(root, confidence.doubleValue(), clarification);
    }

    private TaskPlan task(
            JsonNode root, double confidence, String clarification) {
        if (clarification != null) {
            requireNull(root, "title", "description", "assigneeMemberId",
                    "projectId", "dueAt");
            return new TaskPlan(null, null, null, null, null,
                    confidence, clarification, hash(root));
        }
        return new TaskPlan(
                text(root.get("title"), "title", 500),
                nullableText(root.get("description"), "description", 2_000),
                positiveId(root.get("assigneeMemberId"), "assigneeMemberId"),
                nullablePositiveId(root.get("projectId"), "projectId"),
                nullableInstant(root.get("dueAt"), "dueAt"),
                confidence, null, hash(root));
    }

    private DailyReportPlan report(
            JsonNode root, double confidence, String clarification) {
        if (clarification != null) {
            requireNull(root, "workDate", "completedWork", "plannedWork", "blockers");
            return new DailyReportPlan(null, null, null, null,
                    confidence, clarification, hash(root));
        }
        var workDate = date(root.get("workDate"), "workDate");
        if (workDate.isAfter(LocalDate.now(clock))) {
            throw invalid("AI Work report date cannot be in the future");
        }
        return new DailyReportPlan(
                workDate,
                text(root.get("completedWork"), "completedWork", 4_000),
                text(root.get("plannedWork"), "plannedWork", 4_000),
                nullableText(root.get("blockers"), "blockers", 4_000),
                confidence, null, hash(root));
    }

    private JsonNode object(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI Work draft plan must be a JSON object no larger than 32 KiB");
        }
        try {
            var root = strict.readTree(providerOutput);
            if (root == null || !root.isObject()) {
                throw invalid("AI Work draft plan must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException failure) {
            throw invalid("AI Work draft plan JSON is malformed or has duplicate keys");
        }
    }

    private String hash(JsonNode root) {
        try {
            return AiSupport.sha256(strict.writeValueAsString(canonical(root)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot canonicalize AI Work draft plan", failure);
        }
    }

    private JsonNode canonical(JsonNode node) {
        if (node.isObject()) {
            var result = JsonNodeFactory.instance.objectNode();
            var names = new TreeSet<String>();
            node.fieldNames().forEachRemaining(names::add);
            names.forEach(name -> result.set(name, canonical(node.get(name))));
            return result;
        }
        if (node.isArray()) {
            var result = JsonNodeFactory.instance.arrayNode();
            node.forEach(value -> result.add(canonical(value)));
            return result;
        }
        if (node.isFloatingPointNumber()) {
            return JsonNodeFactory.instance.numberNode(
                    node.decimalValue().stripTrailingZeros());
        }
        return node.deepCopy();
    }

    private static void exact(JsonNode node, Set<String> expected) {
        var actual = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("AI Work draft plan contains unknown or missing fields");
        }
    }

    private static void requireNull(JsonNode root, String... names) {
        for (var name : names) {
            if (root.get(name) == null || !root.get(name).isNull()) {
                throw invalid("AI Work clarification payload must be null");
            }
        }
    }

    private static void rejectForbiddenText(JsonNode node) {
        if (node.isTextual() && FORBIDDEN_TEXT.matcher(node.textValue()).find()) {
            throw invalid("AI Work draft plan contains script or external resource material");
        }
        node.elements().forEachRemaining(AiWorkDraftPlanParser::rejectForbiddenText);
    }

    private static Operation operation(JsonNode node) {
        try {
            return Operation.valueOf(text(node, "operation", 64));
        } catch (IllegalArgumentException failure) {
            throw invalid("AI Work draft operation is unsupported");
        }
    }

    private static String positiveId(JsonNode node, String path) {
        var value = text(node, path, 20);
        try {
            var id = Long.parseLong(value);
            if (id <= 0 || !Long.toString(id).equals(value)) throw new NumberFormatException();
            return value;
        } catch (RuntimeException failure) {
            throw invalid("AI Work " + path + " must be a positive decimal id");
        }
    }

    private static String nullablePositiveId(JsonNode node, String path) {
        return node != null && node.isNull() ? null : positiveId(node, path);
    }

    private static String text(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalid("AI Work " + path + " must be text");
        }
        var value = node.textValue().strip();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw invalid("AI Work " + path + " is too long");
        }
        return value;
    }

    private static String nullableText(JsonNode node, String path, int maximum) {
        return node != null && node.isNull() ? null : text(node, path, maximum);
    }

    private static Instant nullableInstant(JsonNode node, String path) {
        if (node != null && node.isNull()) return null;
        try {
            return Instant.parse(text(node, path, 64));
        } catch (RuntimeException failure) {
            throw invalid("AI Work " + path + " must be an ISO-8601 instant");
        }
    }

    private static LocalDate date(JsonNode node, String path) {
        try {
            var value = text(node, path, 10);
            var parsed = LocalDate.parse(value);
            if (!parsed.toString().equals(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException failure) {
            throw invalid("AI Work " + path + " must be an ISO date");
        }
    }

    private static BigDecimal decimal(JsonNode node, String path) {
        if (node == null || !node.isNumber()) {
            throw invalid("AI Work " + path + " must be a number");
        }
        return node.decimalValue();
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_PLAN_INVALID", message);
    }

    public enum Operation { WORK_TASK_DRAFT, WORK_DAILY_REPORT_DRAFT }

    public sealed interface Plan permits TaskPlan, DailyReportPlan {
        Operation operation();
        double confidence();
        String clarification();
        String planHash();

        default boolean actionable() {
            return clarification() == null;
        }
    }

    public record TaskPlan(
            String title,
            String description,
            String assigneeMemberId,
            String projectId,
            Instant dueAt,
            double confidence,
            String clarification,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() { return Operation.WORK_TASK_DRAFT; }
    }

    public record DailyReportPlan(
            LocalDate workDate,
            String completedWork,
            String plannedWork,
            String blockers,
            double confidence,
            String clarification,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() { return Operation.WORK_DAILY_REPORT_DRAFT; }
    }
}
