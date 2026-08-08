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

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Strict plans for bounded runtime context, statistics and report read tools. */
@Component
public final class AiContextReadPlanParser {
    static final int MAXIMUM_PLAN_BYTES = 16 * 1024;
    private static final int MAXIMUM_KEYWORD_CHARACTERS = 100;
    private static final int MAXIMUM_WINDOW_DAYS = 366;
    private static final int MAXIMUM_METRICS_WINDOW_DAYS = 31;
    private static final Set<String> OPERATIONS = Set.of(
            "RECORD_CONTEXT_SUMMARY", "WORK_TASK_QUERY",
            "WORK_DAILY_REPORT_QUERY", "WORK_PROJECT_METRICS_QUERY",
            "TODO_QUERY", "MESSAGE_QUERY", "RECORD_COMMENT_QUERY",
            "RECORD_HISTORY_QUERY", "RECORD_FILE_QUERY",
            "FLOW_INSTANCE_HISTORY_QUERY",
            "RUNTIME_STATISTICS_QUERY", "RUNTIME_REPORT_QUERY");
    private static final Set<String> RECORD_FIELDS = Set.of(
            "operation", "moduleCode", "recordId", "outputFields");
    private static final Set<String> TASK_FIELDS = Set.of(
            "operation", "keyword", "projectId", "dueFrom", "dueTo",
            "status", "role", "limit");
    private static final Set<String> REPORT_FIELDS = Set.of(
            "operation", "scope", "authorMemberId", "dateFrom", "dateTo",
            "status", "limit");
    private static final Set<String> PROJECT_METRICS_FIELDS = Set.of(
            "operation", "projectId", "fromInclusive", "toExclusive");
    private static final Set<String> TODO_FIELDS = Set.of(
            "operation", "category", "state", "time", "limit");
    private static final Set<String> MESSAGE_FIELDS = Set.of(
            "operation", "status", "limit");
    private static final Set<String> RECORD_ACTIVITY_FIELDS = Set.of(
            "operation", "moduleCode", "recordId", "limit");
    private static final Set<String> FLOW_HISTORY_FIELDS = Set.of(
            "operation", "instanceId", "limit");
    private static final Set<String> RUNTIME_STATISTICS_FIELDS = Set.of(
            "operation", "moduleCode", "dataSourceCode", "aggregation",
            "measureFieldCode", "grouping", "trend");
    private static final Set<String> RUNTIME_REPORT_FIELDS = Set.of(
            "operation", "moduleCode", "reportCode", "page", "size");
    private static final Set<String> STATISTICS_GROUPING_FIELDS = Set.of(
            "fieldCode", "bucketLimit");
    private static final Set<String> STATISTICS_TREND_FIELDS = Set.of(
            "fieldCode", "grain", "startInclusive", "endExclusive");
    private static final Pattern FORBIDDEN_TEXT = Pattern.compile(
            "(?i)(?:https?://|jdbc:|file:|<script|javascript:|"
                    + "(?:^|[^A-Za-z])(select|insert|update|delete|drop|alter|"
                    + "create|merge|grant|revoke)(?:[^A-Za-z]|$))");

    private final ObjectMapper strict;

    public AiContextReadPlanParser() {
        strict = new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
        strict.enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN);
    }

    public boolean isContextRead(String providerOutput) {
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
            throw invalid("AI context-read operation is not authorized by policy");
        }
        rejectForbiddenText(root);
        return switch (operation) {
            case RECORD_CONTEXT_SUMMARY -> record(root, policy);
            case WORK_TASK_QUERY -> task(root, policy);
            case WORK_DAILY_REPORT_QUERY -> report(root, policy);
            case WORK_PROJECT_METRICS_QUERY -> projectMetrics(root);
            case TODO_QUERY -> todo(root, policy);
            case MESSAGE_QUERY -> message(root, policy);
            case RECORD_COMMENT_QUERY -> recordComment(root, policy);
            case RECORD_HISTORY_QUERY -> recordHistory(root, policy);
            case RECORD_FILE_QUERY -> recordFile(root, policy);
            case FLOW_INSTANCE_HISTORY_QUERY -> flowHistory(root, policy);
            case RUNTIME_STATISTICS_QUERY -> runtimeStatistics(root, policy);
            case RUNTIME_REPORT_QUERY -> runtimeReport(root, policy);
        };
    }

    private RuntimeReportQueryPlan runtimeReport(
            JsonNode root, AiPolicy.Version policy) {
        exact(root, RUNTIME_REPORT_FIELDS);
        var moduleCode = code(root.get("moduleCode"), "moduleCode");
        if (!policy.allowedModuleCodes().contains(moduleCode)) {
            throw invalid("AI runtime-report module is not authorized by policy");
        }
        if (!policy.outboundFields().containsKey(moduleCode)) {
            throw invalid("AI runtime-report module has no outbound authorization");
        }
        return new RuntimeReportQueryPlan(
                moduleCode, code(root.get("reportCode"), "reportCode"),
                integer(root.get("page"), "page", 1, 10_000),
                integer(root.get("size"), "size", 1,
                        Math.min(50, policy.maxRows())),
                hash(root));
    }

    private RuntimeStatisticsQueryPlan runtimeStatistics(
            JsonNode root, AiPolicy.Version policy) {
        exact(root, RUNTIME_STATISTICS_FIELDS);
        var moduleCode = code(root.get("moduleCode"), "moduleCode");
        if (!policy.allowedModuleCodes().contains(moduleCode)) {
            throw invalid("AI runtime-statistics module is not authorized by policy");
        }
        var outbound = policy.outboundFields().get(moduleCode);
        if (outbound == null) {
            throw invalid("AI runtime-statistics module has no outbound authorization");
        }
        var aggregation = enumeration(
                root.get("aggregation"), "aggregation", StatisticsAggregation.class);
        var measureFieldCode = nullableCode(
                root.get("measureFieldCode"), "measureFieldCode");
        if ((aggregation == StatisticsAggregation.COUNT)
                != (measureFieldCode == null)) {
            throw invalid("AI runtime-statistics aggregation and measure are inconsistent");
        }
        requireOutbound(outbound, measureFieldCode);

        var grouping = statisticsGrouping(
                root.get("grouping"), policy.maxRows(), outbound);
        var trend = statisticsTrend(root.get("trend"), policy.maxRows(), outbound);
        if (grouping != null && trend != null) {
            throw invalid("AI runtime-statistics grouping and trend are mutually exclusive");
        }
        return new RuntimeStatisticsQueryPlan(
                moduleCode, code(root.get("dataSourceCode"), "dataSourceCode"),
                aggregation, measureFieldCode, grouping, trend, hash(root));
    }

    private StatisticsGrouping statisticsGrouping(
            JsonNode node, int maxRows, Set<String> outbound) {
        if (node != null && node.isNull()) return null;
        if (node == null || !node.isObject()) {
            throw invalid("AI runtime-statistics grouping must be an object or null");
        }
        exact(node, STATISTICS_GROUPING_FIELDS);
        var fieldCode = code(node.get("fieldCode"), "grouping.fieldCode");
        requireOutbound(outbound, fieldCode);
        return new StatisticsGrouping(
                fieldCode, integer(node.get("bucketLimit"),
                "grouping.bucketLimit", 1, Math.min(20, maxRows)));
    }

    private StatisticsTrend statisticsTrend(
            JsonNode node, int maxRows, Set<String> outbound) {
        if (node != null && node.isNull()) return null;
        if (node == null || !node.isObject()) {
            throw invalid("AI runtime-statistics trend must be an object or null");
        }
        exact(node, STATISTICS_TREND_FIELDS);
        var fieldCode = code(node.get("fieldCode"), "trend.fieldCode");
        requireOutbound(outbound, fieldCode);
        var grain = enumeration(node.get("grain"), "trend.grain", StatisticsGrain.class);
        var startInclusive = date(node.get("startInclusive"), "trend.startInclusive");
        var endExclusive = date(node.get("endExclusive"), "trend.endExclusive");
        if (!grain.aligned(startInclusive) || !grain.aligned(endExclusive)) {
            throw invalid("AI runtime-statistics trend range is not grain-aligned");
        }
        var buckets = grain.buckets(startInclusive, endExclusive);
        if (buckets < 1 || buckets > Math.min(31, maxRows)) {
            throw invalid("AI runtime-statistics trend exceeds the policy limit");
        }
        return new StatisticsTrend(
                fieldCode, grain, startInclusive, endExclusive,
                Math.toIntExact(buckets));
    }

    private static void requireOutbound(Set<String> outbound, String fieldCode) {
        if (fieldCode != null && !outbound.contains(fieldCode)) {
            throw invalid("AI runtime-statistics field exceeds policy authorization");
        }
    }

    private RecordCommentQueryPlan recordComment(
            JsonNode root, AiPolicy.Version policy) {
        var target = recordActivity(root, policy);
        return new RecordCommentQueryPlan(
                target.moduleCode(), target.recordId(), target.limit(), hash(root));
    }

    private RecordHistoryQueryPlan recordHistory(
            JsonNode root, AiPolicy.Version policy) {
        var target = recordActivity(root, policy);
        return new RecordHistoryQueryPlan(
                target.moduleCode(), target.recordId(), target.limit(), hash(root));
    }

    private RecordFileQueryPlan recordFile(
            JsonNode root, AiPolicy.Version policy) {
        var target = recordActivity(root, policy);
        return new RecordFileQueryPlan(
                target.moduleCode(), target.recordId(), target.limit(), hash(root));
    }

    private FlowInstanceHistoryQueryPlan flowHistory(
            JsonNode root, AiPolicy.Version policy) {
        exact(root, FLOW_HISTORY_FIELDS);
        return new FlowInstanceHistoryQueryPlan(
                positiveId(root.get("instanceId"), "instanceId"),
                integer(root.get("limit"), "limit", 1,
                        Math.min(20, policy.maxRows())),
                hash(root));
    }

    private RecordActivityTarget recordActivity(
            JsonNode root, AiPolicy.Version policy) {
        exact(root, RECORD_ACTIVITY_FIELDS);
        var moduleCode = code(root.get("moduleCode"), "moduleCode");
        if (!policy.allowedModuleCodes().contains(moduleCode)) {
            throw invalid("AI record-activity module is not authorized by policy");
        }
        return new RecordActivityTarget(
                moduleCode, positiveId(root.get("recordId"), "recordId"),
                integer(root.get("limit"), "limit", 1,
                        Math.min(20, policy.maxRows())));
    }

    private RecordContextPlan record(JsonNode root, AiPolicy.Version policy) {
        exact(root, RECORD_FIELDS);
        var moduleCode = code(root.get("moduleCode"), "moduleCode");
        if (!policy.allowedModuleCodes().contains(moduleCode)) {
            throw invalid("AI record-context module is not authorized by policy");
        }
        var outbound = policy.outboundFields().get(moduleCode);
        if (outbound == null || outbound.isEmpty()) {
            throw invalid("AI record-context module has no outbound authorization");
        }
        var outputFields = codes(root.get("outputFields"), "outputFields", 128);
        if (outputFields.isEmpty() || !outbound.containsAll(outputFields)) {
            throw invalid("AI record-context output fields exceed policy authorization");
        }
        return new RecordContextPlan(
                moduleCode, positiveId(root.get("recordId"), "recordId"),
                outputFields, hash(root));
    }

    private TaskQueryPlan task(JsonNode root, AiPolicy.Version policy) {
        exact(root, TASK_FIELDS);
        var keyword = nullableText(
                root.get("keyword"), "keyword", MAXIMUM_KEYWORD_CHARACTERS);
        var projectId = nullablePositiveId(root.get("projectId"), "projectId");
        var dueFrom = nullableInstant(root.get("dueFrom"), "dueFrom");
        var dueTo = nullableInstant(root.get("dueTo"), "dueTo");
        if (dueFrom != null && dueTo != null
                && (dueFrom.isAfter(dueTo)
                || Duration.between(dueFrom, dueTo).compareTo(
                Duration.ofDays(MAXIMUM_WINDOW_DAYS)) > 0)) {
            throw invalid("AI task due window is invalid or exceeds 366 days");
        }
        return new TaskQueryPlan(
                keyword, projectId, dueFrom, dueTo,
                enumeration(root.get("status"), "status", TaskStatus.class),
                enumeration(root.get("role"), "role", TaskRole.class),
                integer(root.get("limit"), "limit", 1,
                        Math.min(50, policy.maxRows())), hash(root));
    }

    private DailyReportQueryPlan report(JsonNode root, AiPolicy.Version policy) {
        exact(root, REPORT_FIELDS);
        var scope = enumeration(root.get("scope"), "scope", ReportScope.class);
        var authorMemberId = nullablePositiveId(
                root.get("authorMemberId"), "authorMemberId");
        if (scope == ReportScope.SELF && authorMemberId != null) {
            throw invalid("AI SELF report query cannot select another member");
        }
        var dateFrom = date(root.get("dateFrom"), "dateFrom");
        var dateTo = date(root.get("dateTo"), "dateTo");
        var days = ChronoUnit.DAYS.between(dateFrom, dateTo) + 1;
        if (days < 1 || days > MAXIMUM_WINDOW_DAYS) {
            throw invalid("AI report date window must contain 1..366 days");
        }
        return new DailyReportQueryPlan(
                scope, authorMemberId, dateFrom, dateTo,
                enumeration(root.get("status"), "status", ReportStatus.class),
                integer(root.get("limit"), "limit", 1,
                        Math.min(50, policy.maxRows())), hash(root));
    }

    private ProjectMetricsQueryPlan projectMetrics(JsonNode root) {
        exact(root, PROJECT_METRICS_FIELDS);
        var fromInclusive = date(root.get("fromInclusive"), "fromInclusive");
        var toExclusive = date(root.get("toExclusive"), "toExclusive");
        var days = ChronoUnit.DAYS.between(fromInclusive, toExclusive);
        if (days < 1 || days > MAXIMUM_METRICS_WINDOW_DAYS) {
            throw invalid("AI project metrics window must contain 1..31 UTC days");
        }
        return new ProjectMetricsQueryPlan(
                positiveId(root.get("projectId"), "projectId"),
                fromInclusive, toExclusive, hash(root));
    }

    private TodoQueryPlan todo(JsonNode root, AiPolicy.Version policy) {
        exact(root, TODO_FIELDS);
        return new TodoQueryPlan(
                enumeration(root.get("category"), "category", TodoCategory.class),
                enumeration(root.get("state"), "state", TodoState.class),
                enumeration(root.get("time"), "time", TodoTime.class),
                integer(root.get("limit"), "limit", 1,
                        Math.min(20, policy.maxRows())), hash(root));
    }

    private MessageQueryPlan message(JsonNode root, AiPolicy.Version policy) {
        exact(root, MESSAGE_FIELDS);
        return new MessageQueryPlan(
                enumeration(root.get("status"), "status", MessageStatus.class),
                integer(root.get("limit"), "limit", 1,
                        Math.min(20, policy.maxRows())), hash(root));
    }

    private JsonNode object(String providerOutput) {
        if (providerOutput == null || providerOutput.isBlank()
                || providerOutput.getBytes(StandardCharsets.UTF_8).length
                > MAXIMUM_PLAN_BYTES) {
            throw invalid("AI context-read plan must be a JSON object no larger than 16 KiB");
        }
        try {
            var root = strict.readTree(providerOutput);
            if (root == null || !root.isObject()) {
                throw invalid("AI context-read plan must be a JSON object");
            }
            return root;
        } catch (JsonProcessingException failure) {
            throw invalid("AI context-read plan JSON is malformed or has duplicate keys");
        }
    }

    private String hash(JsonNode root) {
        try {
            return AiSupport.sha256(strict.writeValueAsString(canonical(root)));
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot canonicalize AI context-read plan", failure);
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
        return node.deepCopy();
    }

    private static void rejectForbiddenText(JsonNode node) {
        if (node.isTextual() && FORBIDDEN_TEXT.matcher(node.textValue()).find()) {
            throw invalid("AI context-read plan contains URL, script or write material");
        }
        node.elements().forEachRemaining(AiContextReadPlanParser::rejectForbiddenText);
    }

    private static void exact(JsonNode node, Set<String> expected) {
        var actual = new LinkedHashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) {
            throw invalid("AI context-read plan contains unknown or missing fields");
        }
    }

    private static Operation operation(JsonNode node) {
        try {
            return Operation.valueOf(text(node, "operation", 64));
        } catch (IllegalArgumentException failure) {
            throw invalid("AI context-read operation is unsupported");
        }
    }

    private static List<String> codes(JsonNode node, String path, int maximum) {
        if (node == null || !node.isArray() || node.size() > maximum) {
            throw invalid("AI " + path + " is invalid");
        }
        var values = new LinkedHashSet<String>();
        node.forEach(value -> {
            if (!values.add(code(value, path))) {
                throw invalid("AI " + path + " must be unique");
            }
        });
        return List.copyOf(values);
    }

    private static String code(JsonNode node, String path) {
        var value = text(node, path, 64);
        if (!value.matches("^[A-Za-z][A-Za-z0-9_]{0,63}$")) {
            throw invalid("AI " + path + " is not a stable code");
        }
        return value;
    }

    private static String nullableCode(JsonNode node, String path) {
        return node != null && node.isNull() ? null : code(node, path);
    }

    private static String positiveId(JsonNode node, String path) {
        var value = text(node, path, 20);
        try {
            var id = Long.parseLong(value);
            if (id <= 0 || !Long.toString(id).equals(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException failure) {
            throw invalid("AI " + path + " must be a positive decimal id");
        }
    }

    private static String nullablePositiveId(JsonNode node, String path) {
        return node != null && node.isNull() ? null : positiveId(node, path);
    }

    private static String nullableText(
            JsonNode node, String path, int maximum) {
        if (node == null || node.isNull()) return null;
        return text(node, path, maximum).strip();
    }

    private static String text(JsonNode node, String path, int maximum) {
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            throw invalid("AI " + path + " must be text");
        }
        var value = node.textValue();
        if (value.codePointCount(0, value.length()) > maximum) {
            throw invalid("AI " + path + " is too long");
        }
        return value;
    }

    private static Instant nullableInstant(JsonNode node, String path) {
        if (node == null || node.isNull()) return null;
        try {
            return Instant.parse(text(node, path, 64));
        } catch (RuntimeException failure) {
            throw invalid("AI " + path + " must be an ISO-8601 instant");
        }
    }

    private static LocalDate date(JsonNode node, String path) {
        try {
            var value = text(node, path, 10);
            var parsed = LocalDate.parse(value);
            if (!parsed.toString().equals(value)) throw new IllegalArgumentException();
            return parsed;
        } catch (RuntimeException failure) {
            throw invalid("AI " + path + " must be an ISO date");
        }
    }

    private static int integer(
            JsonNode node, String path, int minimum, int maximum) {
        if (node == null || !node.isIntegralNumber() || !node.canConvertToInt()) {
            throw invalid("AI " + path + " must be an integer");
        }
        var value = node.intValue();
        if (value < minimum || value > maximum) {
            throw invalid("AI " + path + " exceeds the policy limit");
        }
        return value;
    }

    private static <E extends Enum<E>> E enumeration(
            JsonNode node, String path, Class<E> type) {
        try {
            return Enum.valueOf(type, text(node, path, 32));
        } catch (IllegalArgumentException failure) {
            throw invalid("AI " + path + " is unsupported");
        }
    }

    private static com.unique.examine.core.error.BusinessException invalid(
            String message) {
        return AiSupport.invalid("AI_PLAN_INVALID", message);
    }

    public enum Operation {
        RECORD_CONTEXT_SUMMARY,
        WORK_TASK_QUERY,
        WORK_DAILY_REPORT_QUERY,
        WORK_PROJECT_METRICS_QUERY,
        TODO_QUERY,
        MESSAGE_QUERY,
        RECORD_COMMENT_QUERY,
        RECORD_HISTORY_QUERY,
        RECORD_FILE_QUERY,
        FLOW_INSTANCE_HISTORY_QUERY,
        RUNTIME_STATISTICS_QUERY,
        RUNTIME_REPORT_QUERY
    }

    public enum TaskStatus { ALL, OPEN, COMPLETED }

    public enum TaskRole { PARTICIPATING, CREATED_BY_ME, ASSIGNED_TO_ME, ALL }

    public enum ReportScope { SELF, ALL }

    public enum ReportStatus { ALL, DRAFT, SUBMITTED }

    public enum TodoCategory { ALL, TASK, APPROVAL }

    public enum TodoState { ALL, OPEN, CLOSED }

    public enum TodoTime { ALL, TODAY, OVERDUE }

    public enum MessageStatus { ALL, UNREAD, READ, ARCHIVED }

    public enum StatisticsAggregation { COUNT, SUM, AVG, MIN, MAX }

    public enum StatisticsGrain {
        DAY {
            @Override
            boolean aligned(LocalDate value) { return true; }

            @Override
            long buckets(LocalDate start, LocalDate end) {
                return ChronoUnit.DAYS.between(start, end);
            }
        },
        WEEK {
            @Override
            boolean aligned(LocalDate value) {
                return value.getDayOfWeek() == DayOfWeek.MONDAY;
            }

            @Override
            long buckets(LocalDate start, LocalDate end) {
                return ChronoUnit.WEEKS.between(start, end);
            }
        },
        MONTH {
            @Override
            boolean aligned(LocalDate value) { return value.getDayOfMonth() == 1; }

            @Override
            long buckets(LocalDate start, LocalDate end) {
                return ChronoUnit.MONTHS.between(start, end);
            }
        };

        abstract boolean aligned(LocalDate value);

        abstract long buckets(LocalDate start, LocalDate end);
    }

    public sealed interface Plan permits RecordContextPlan, TaskQueryPlan,
            DailyReportQueryPlan, ProjectMetricsQueryPlan, TodoQueryPlan,
            MessageQueryPlan, RecordCommentQueryPlan, RecordHistoryQueryPlan,
            RecordFileQueryPlan, FlowInstanceHistoryQueryPlan,
            RuntimeStatisticsQueryPlan,
            RuntimeReportQueryPlan {
        Operation operation();

        String planHash();
    }

    public record RecordContextPlan(
            String moduleCode,
            String recordId,
            List<String> outputFields,
            String planHash
    ) implements Plan {
        public RecordContextPlan {
            outputFields = List.copyOf(outputFields);
        }

        @Override
        public Operation operation() {
            return Operation.RECORD_CONTEXT_SUMMARY;
        }
    }

    public record TaskQueryPlan(
            String keyword,
            String projectId,
            Instant dueFrom,
            Instant dueTo,
            TaskStatus status,
            TaskRole role,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.WORK_TASK_QUERY;
        }
    }

    public record DailyReportQueryPlan(
            ReportScope scope,
            String authorMemberId,
            LocalDate dateFrom,
            LocalDate dateTo,
            ReportStatus status,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.WORK_DAILY_REPORT_QUERY;
        }
    }

    public record ProjectMetricsQueryPlan(
            String projectId,
            LocalDate fromInclusive,
            LocalDate toExclusive,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.WORK_PROJECT_METRICS_QUERY;
        }
    }

    public record TodoQueryPlan(
            TodoCategory category,
            TodoState state,
            TodoTime time,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.TODO_QUERY;
        }
    }

    public record MessageQueryPlan(
            MessageStatus status,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.MESSAGE_QUERY;
        }
    }

    public record RecordCommentQueryPlan(
            String moduleCode,
            String recordId,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.RECORD_COMMENT_QUERY;
        }
    }

    public record RecordHistoryQueryPlan(
            String moduleCode,
            String recordId,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.RECORD_HISTORY_QUERY;
        }
    }

    public record RecordFileQueryPlan(
            String moduleCode,
            String recordId,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.RECORD_FILE_QUERY;
        }
    }

    public record FlowInstanceHistoryQueryPlan(
            String instanceId,
            int limit,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.FLOW_INSTANCE_HISTORY_QUERY;
        }
    }

    public record RuntimeStatisticsQueryPlan(
            String moduleCode,
            String dataSourceCode,
            StatisticsAggregation aggregation,
            String measureFieldCode,
            StatisticsGrouping grouping,
            StatisticsTrend trend,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.RUNTIME_STATISTICS_QUERY;
        }
    }

    public record RuntimeReportQueryPlan(
            String moduleCode,
            String reportCode,
            int page,
            int size,
            String planHash
    ) implements Plan {
        @Override
        public Operation operation() {
            return Operation.RUNTIME_REPORT_QUERY;
        }
    }

    public record StatisticsGrouping(String fieldCode, int bucketLimit) { }

    public record StatisticsTrend(
            String fieldCode,
            StatisticsGrain grain,
            LocalDate startInclusive,
            LocalDate endExclusive,
            int bucketCount
    ) { }

    private record RecordActivityTarget(
            String moduleCode, String recordId, int limit) { }
}
