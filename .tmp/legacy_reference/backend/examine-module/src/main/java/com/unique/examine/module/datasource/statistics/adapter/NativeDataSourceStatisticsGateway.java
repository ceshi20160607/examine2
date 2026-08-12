package com.unique.examine.module.datasource.statistics.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.runtime.DataSourceRecordQueryGateway;
import com.unique.examine.module.datasource.statistics.domain.CanonicalDecimal;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.query.NativeRecordAggregatePlan;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Repository("nativeDataSourceStatisticsGateway")
public class NativeDataSourceStatisticsGateway
        implements DataSourceStatisticsGateway {
    private static final Set<String> NUMERIC_QUERY_TYPES = Set.of(
            "NUMBER", "PERCENT", "RATING", "PROGRESS");
    private static final Set<String> TEMPORAL_QUERY_TYPES = Set.of(
            "DATE", "DATETIME");
    private static final Set<String> GROUPABLE_QUERY_TYPES = Set.of(
            "TEXT", "NUMBER", "DATE", "DATETIME", "RADIO",
            "MEMBER", "DEPARTMENT", "PERCENT", "TIME", "SWITCH", "RATING",
            "PROGRESS", "STATUS");

    private final JdbcTemplate jdbc;
    private final DataSourceRecordQueryGateway records;
    private final ObjectMapper json;

    public NativeDataSourceStatisticsGateway(
            JdbcTemplate jdbc,
            DataSourceRecordQueryGateway records,
            ObjectMapper json
    ) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.records = Objects.requireNonNull(records, "records");
        this.json = Objects.requireNonNull(json, "json");
    }

    @Override
    public SourceCapabilities capabilities(
            RuntimeSession session,
            DataSourcePublication publication
    ) {
        requirePublicationScope(session, publication);
        var version = publication.version();
        var schema = records.schema(
                session, version.moduleCode(), version.schemaVersionId());
        if (!"READY".equals(schema.runtimeState())
                || !version.schemaVersionId().equals(schema.schemaVersionId())
                || !Long.toString(version.moduleId())
                .equals(schema.logicalModuleId())) {
            throw unavailable(
                    "Exact published data-source schema is unavailable");
        }
        var fields = schema.fields().stream()
                .map(NativeDataSourceStatisticsGateway::capability)
                .toList();
        return new SourceCapabilities(
                version.moduleId(), version.moduleCode(),
                version.schemaVersionId(), fields);
    }

    @Override
    public RawStatistics execute(
            RuntimeSession session,
            DataSourcePublication publication,
            StatisticsPlan statistics
    ) {
        return execute(session, publication, statistics, null);
    }

    @Override
    public RawStatistics execute(
            RuntimeSession session,
            DataSourcePublication publication,
            StatisticsPlan statistics,
            StatisticsRecordRestriction restriction
    ) {
        requirePublicationScope(session, publication);
        Objects.requireNonNull(statistics, "statistics");
        var version = publication.version();
        var nativeQuery = fixedFilterQuery(publication);
        var plan = records.prepareActiveAggregate(
                session, version.moduleCode(), version.schemaVersionId(),
                nativeQuery);
        requireExactPlan(publication, plan, statistics);

        var request = statistics.request();
        var queryId = queryId(publication, plan, statistics, restriction);
        if (restriction != null && restriction.matchesNoRecords()) {
            return new RawStatistics(queryId, BigDecimal.ZERO, 0,
                    List.of(), List.of(), 0, false);
        }
        var overall = overall(plan, statistics, restriction);
        if (request.grouping() != null) {
            var grouped = groups(plan, statistics, restriction);
            return new RawStatistics(
                    queryId, overall.value(), overall.matchedRecordCount(),
                    grouped.buckets(), List.of(), grouped.totalBucketCount(),
                    grouped.totalBucketCount() > grouped.buckets().size());
        }
        if (request.trend() != null) {
            var trend = trend(plan, statistics, restriction);
            return new RawStatistics(
                    queryId, overall.value(), overall.matchedRecordCount(),
                    List.of(), trend, trend.size(), false);
        }
        return new RawStatistics(
                queryId, overall.value(), overall.matchedRecordCount(),
                List.of(), List.of(), 0, false);
    }

    private Overall overall(
            NativeRecordAggregatePlan plan,
            StatisticsPlan statistics,
            StatisticsRecordRestriction restriction
    ) {
        var measure = valueSource("measure_value", statistics.measure());
        var time = valueSource("time_value", statistics.time());
        var sql = "SELECT " + aggregate(
                statistics.request().aggregation(), measure.expression())
                + " AS aggregate_value,COUNT(*) AS matched_record_count"
                + " FROM un_module_record r"
                + measure.joinSql() + time.joinSql()
                + " WHERE " + plan.whereSql()
                + restrictionSql(restriction).predicate()
                + rangePredicate(statistics, time.expression());
        var arguments = arguments(
                measure.arguments(), time.arguments(), plan.whereArguments(),
                restrictionSql(restriction).arguments(),
                rangeArguments(statistics));
        var rows = jdbc.query(
                sql,
                (result, rowNumber) -> new Overall(
                        result.getBigDecimal("aggregate_value"),
                        result.getLong("matched_record_count")),
                arguments.toArray());
        if (rows.size() != 1) {
            throw invalidResult(
                    "Native scalar aggregate did not return exactly one row");
        }
        return rows.getFirst();
    }

    private Grouped groups(
            NativeRecordAggregatePlan plan,
            StatisticsPlan statistics,
            StatisticsRecordRestriction restriction
    ) {
        var measure = valueSource("measure_value", statistics.measure());
        var group = valueSource("group_value", statistics.group());
        var key = groupKey(group, statistics.group());
        var labelExpression = "TEXT".equals(statistics.group().queryType())
                ? group.displayExpression() + " COLLATE utf8mb4_bin"
                : group.displayExpression();
        var aggregation = aggregate(
                statistics.request().aggregation(), measure.expression());
        var sql = "SELECT grouped.group_key,"
                + "CASE WHEN grouped.group_key IS NULL THEN NULL "
                + "ELSE COALESCE(NULLIF(TRIM(grouped.group_label),''),"
                + "grouped.group_key) END AS group_label,"
                + "grouped.aggregate_value,grouped.record_count,"
                + "COUNT(*) OVER() AS total_bucket_count FROM ("
                + "SELECT " + key + " AS group_key,"
                + "MIN(" + labelExpression + ") AS group_label,"
                + aggregation + " AS aggregate_value,"
                + "COUNT(*) AS record_count"
                + " FROM un_module_record r"
                + measure.joinSql() + group.joinSql()
                + " WHERE " + plan.whereSql()
                + restrictionSql(restriction).predicate()
                + " GROUP BY " + key
                + ") grouped ORDER BY grouped.group_key IS NULL ASC,"
                + "BINARY grouped.group_key ASC LIMIT ?";
        var limit = statistics.request().grouping().bucketLimit();
        var arguments = arguments(
                measure.arguments(), group.arguments(), plan.whereArguments(),
                restrictionSql(restriction).arguments(),
                List.of(limit));
        var rows = jdbc.query(
                sql,
                (result, rowNumber) -> new GroupRow(
                        result.getString("group_key"),
                        result.getString("group_label"),
                        result.getBigDecimal("aggregate_value"),
                        result.getLong("record_count"),
                        result.getLong("total_bucket_count")),
                arguments.toArray());
        if (rows.size() > limit) {
            throw invalidResult(
                    "Native group query exceeded its bucket limit");
        }
        var total = rows.isEmpty() ? 0 : rows.getFirst().totalBucketCount();
        if (total < rows.size() || rows.stream()
                .anyMatch(row -> row.totalBucketCount() != total)) {
            throw invalidResult(
                    "Native group total bucket count is inconsistent");
        }
        var buckets = rows.stream().map(row -> {
            var keyValue = canonicalGroupKey(
                    row.key(), statistics.group().queryType());
            var nullBucket = keyValue == null;
            var label = nullBucket ? null
                    : textOr(row.label(), keyValue);
            return new RawGroupBucket(
                    keyValue, label, nullBucket, row.value(),
                    row.recordCount());
        }).toList();
        return new Grouped(buckets, total);
    }

    private List<RawTrendBucket> trend(
            NativeRecordAggregatePlan plan,
            StatisticsPlan statistics,
            StatisticsRecordRestriction restriction
    ) {
        var measure = valueSource("measure_value", statistics.measure());
        var time = valueSource("time_value", statistics.time());
        var bucket = trendBucket(
                time.expression(), statistics.request().trend().grain());
        var sql = "SELECT " + bucket + " AS bucket_start,"
                + aggregate(statistics.request().aggregation(),
                measure.expression()) + " AS aggregate_value,"
                + "COUNT(*) AS record_count"
                + " FROM un_module_record r"
                + measure.joinSql() + time.joinSql()
                + " WHERE " + plan.whereSql()
                + restrictionSql(restriction).predicate()
                + rangePredicate(statistics, time.expression())
                + " GROUP BY " + bucket + " ORDER BY bucket_start ASC";
        var arguments = arguments(
                measure.arguments(), time.arguments(), plan.whereArguments(),
                restrictionSql(restriction).arguments(),
                rangeArguments(statistics));
        var rows = jdbc.query(
                sql,
                (result, rowNumber) -> new RawTrendBucket(
                        result.getDate("bucket_start").toLocalDate(),
                        result.getBigDecimal("aggregate_value"),
                        result.getLong("record_count")),
                arguments.toArray());
        if (rows.size() > statistics.request().trend().bucketCount()) {
            throw invalidResult(
                    "Native trend query exceeded its requested range");
        }
        return rows;
    }

    private String fixedFilterQuery(DataSourcePublication publication) {
        var root = json.createObjectNode();
        root.put("schemaVersionId", publication.version().schemaVersionId());
        root.put("page", 1);
        root.put("size", 1);
        root.put("recordScope", "active");
        root.putNull("q");
        root.set("filter", filter(
                publication.version().snapshot().fixedFilters()));
        root.putArray("sort");
        root.putArray("columns");
        root.putNull("viewId");
        try {
            return json.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Cannot serialize native statistics fixed filters",
                    exception);
        }
    }

    private JsonNode filter(List<DataSourceDraft.FixedFilter> filters) {
        if (filters.isEmpty()) {
            return json.nullNode();
        }
        if (filters.size() == 1) {
            return predicate(filters.getFirst());
        }
        var group = json.createObjectNode();
        group.put("kind", "AND");
        var children = group.putArray("children");
        filters.forEach(filter -> children.add(predicate(filter)));
        return group;
    }

    private ObjectNode predicate(DataSourceDraft.FixedFilter filter) {
        var predicate = json.createObjectNode();
        predicate.put("kind", "PREDICATE");
        predicate.put("fieldCode", filter.fieldCode());
        predicate.put("operator", filter.operator());
        if ("EMPTY".equals(filter.operator())
                && filter.canonicalValue() == null) {
            predicate.put("value", true);
        } else if (filter.canonicalValue() != null) {
            try {
                predicate.set("value", json.readTree(
                        filter.canonicalValue()));
            } catch (JsonProcessingException exception) {
                throw new StatisticsException(
                        "STATISTICS_SOURCE_UNAVAILABLE",
                        "Published fixed filter is not canonical JSON");
            }
        }
        return predicate;
    }

    private static FieldCapability capability(
            RecordRuntimeViews.FieldCapability field
    ) {
        var queryType = queryType(field);
        return new FieldCapability(
                positiveId(field.logicalFieldId()), field.fieldCode(),
                field.fieldName(), field.type(), queryType, field.readable(),
                field.readable() && NUMERIC_QUERY_TYPES.contains(queryType),
                field.readable() && TEMPORAL_QUERY_TYPES.contains(queryType),
                field.readable()
                        && GROUPABLE_QUERY_TYPES.contains(queryType));
    }

    private static String queryType(
            RecordRuntimeViews.FieldCapability field
    ) {
        if ("REFERENCE".equals(field.type())) {
            return field.schema().path("referenceResultType").asText("TEXT");
        }
        var derived = field.schema().path("derivedQueryType").asText("");
        if (!derived.isEmpty()) {
            return derived;
        }
        return switch (field.type()) {
            case "AUTO_NUMBER" -> "TEXT";
            case "TENANT", "CREATED_BY", "UPDATED_BY" -> "MEMBER";
            case "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            default -> field.type();
        };
    }

    private static void requireExactPlan(
            DataSourcePublication publication,
            NativeRecordAggregatePlan plan,
            StatisticsPlan statistics
    ) {
        var version = publication.version();
        if (plan.systemId() != version.systemId()
                || plan.tenantId() != version.tenantId()
                || plan.schemaVersionId() != positiveId(
                version.schemaVersionId())
                || plan.logicalModuleId() != version.moduleId()
                || !plan.moduleCode().equals(version.moduleCode())) {
            throw unavailable(
                    "Native query plan does not match the exact publication");
        }
        requireField(plan, statistics.measure());
        requireField(plan, statistics.group());
        requireField(plan, statistics.time());
    }

    private static void requireField(
            NativeRecordAggregatePlan plan,
            FieldCapability capability
    ) {
        if (capability == null) {
            return;
        }
        var field = plan.field(capability.code());
        if (field == null
                || field.logicalFieldId() != capability.logicalFieldId()
                || !field.queryType().equals(capability.queryType())) {
            throw unavailable(
                    "Statistics field is absent from the native read projection");
        }
    }

    private static void requirePublicationScope(
            RuntimeSession session,
            DataSourcePublication publication
    ) {
        if (session == null || publication == null
                || session.tenantId() == null
                || session.systemId() != publication.version().systemId()
                || session.tenantId() != publication.version().tenantId()) {
            throw new StatisticsException(
                    "STATISTICS_SOURCE_NOT_FOUND",
                    "Statistics source does not exist");
        }
    }

    private static ValueSource valueSource(
            String alias,
            FieldCapability field
    ) {
        if (field == null) {
            return ValueSource.none();
        }
        if ("CREATED_AT".equals(field.type())) {
            return ValueSource.recordColumn("r.created_at");
        }
        if ("UPDATED_AT".equals(field.type())) {
            return ValueSource.recordColumn("r.updated_at");
        }
        var join = " LEFT JOIN un_module_record_value " + alias
                + " ON " + alias + ".system_id=r.system_id"
                + " AND " + alias + ".tenant_id=r.tenant_id"
                + " AND " + alias + ".record_id=r.record_id"
                + " AND " + alias
                + ".schema_version_id=r.schema_version_id"
                + " AND " + alias
                + ".module_snapshot_id=r.module_snapshot_id"
                + " AND " + alias
                + ".logical_module_id=r.logical_module_id"
                + " AND " + alias + ".logical_field_id=?"
                + " AND " + alias + ".ordinal=0";
        return new ValueSource(
                join,
                List.of(field.logicalFieldId()),
                valueExpression(alias, field), alias + ".display_value");
    }

    private static String valueExpression(
            String alias,
            FieldCapability field
    ) {
        return switch (field.queryType()) {
            case "TEXT" -> "TEXTAREA".equals(field.type())
                    ? alias + ".text_value" : alias + ".string_value";
            case "NUMBER", "PERCENT", "RATING", "PROGRESS", "MONEY" ->
                    alias + ".decimal_value";
            case "DATE" -> alias + ".date_value";
            case "DATETIME" -> alias + ".datetime_value";
            case "TIME" -> alias + ".time_value";
            case "SWITCH" -> alias + ".boolean_value";
            case "RADIO", "MEMBER", "DEPARTMENT", "STATUS" ->
                    alias + ".reference_value";
            default -> throw unavailable(
                    "Statistics field type has no native scalar projection");
        };
    }

    private static String groupKey(
            ValueSource group,
            FieldCapability field
    ) {
        var expression = group.expression();
        return switch (field.queryType()) {
            case "TEXT" -> expression + " COLLATE utf8mb4_bin";
            case "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS" ->
                    "CASE WHEN " + expression + " IS NULL THEN NULL "
                            + "WHEN " + expression + "=0 THEN '0' ELSE "
                            + "TRIM(TRAILING '.' FROM TRIM(TRAILING '0' FROM "
                            + "CAST(" + expression + " AS CHAR))) END";
            case "DATE" -> "DATE_FORMAT(" + expression + ",'%Y-%m-%d')";
            case "DATETIME" -> "DATE_FORMAT(" + expression
                    + ",'%Y-%m-%dT%H:%i:%s.%f')";
            case "TIME" -> "TIME_FORMAT(" + expression + ",'%H:%i:%s')";
            case "SWITCH" -> "CASE WHEN " + expression
                    + " IS NULL THEN NULL WHEN " + expression
                    + " THEN 'true' ELSE 'false' END";
            case "RADIO", "MEMBER", "DEPARTMENT", "STATUS" ->
                    "CAST(" + expression + " AS CHAR)";
            default -> expression;
        };
    }

    private static String canonicalGroupKey(
            String value,
            String queryType
    ) {
        if (value == null) {
            return null;
        }
        if (NUMERIC_QUERY_TYPES.contains(queryType)) {
            try {
                return CanonicalDecimal.from(new BigDecimal(value));
            } catch (NumberFormatException exception) {
                throw invalidResult(
                        "Native numeric group key is invalid");
            }
        }
        if ("DATETIME".equals(queryType) && value.indexOf('.') >= 0) {
            value = value.replaceFirst("0+$", "").replaceFirst("\\.$", "");
        }
        return value;
    }

    private static String aggregate(
            StatisticsAggregation aggregation,
            String measureExpression
    ) {
        return aggregation == StatisticsAggregation.COUNT
                ? "COUNT(*)"
                : aggregation.name() + "(" + measureExpression + ")";
    }

    private static String rangePredicate(
            StatisticsPlan statistics,
            String timeExpression
    ) {
        return statistics.request().trend() == null ? ""
                : " AND " + timeExpression + ">=? AND "
                + timeExpression + "<?";
    }

    private static List<Object> rangeArguments(StatisticsPlan statistics) {
        var trend = statistics.request().trend();
        return trend == null ? List.of()
                : List.of(trend.startInclusive(), trend.endExclusive());
    }

    private static String trendBucket(
            String timeExpression,
            StatisticsGrain grain
    ) {
        return switch (grain) {
            case DAY -> "DATE(" + timeExpression + ")";
            case WEEK -> "DATE_SUB(DATE(" + timeExpression
                    + "),INTERVAL WEEKDAY(" + timeExpression + ") DAY)";
            case MONTH -> "DATE_SUB(DATE(" + timeExpression
                    + "),INTERVAL DAYOFMONTH(" + timeExpression + ")-1 DAY)";
        };
    }

    @SafeVarargs
    private static List<Object> arguments(List<?>... parts) {
        var result = new ArrayList<Object>();
        for (var part : parts) {
            result.addAll(part);
        }
        return result;
    }

    private static RestrictionSql restrictionSql(
            StatisticsRecordRestriction restriction
    ) {
        if (restriction == null) {
            return new RestrictionSql("", List.of());
        }
        return switch (restriction.kind()) {
            case OWNER_MEMBER -> new RestrictionSql(
                    " AND r.owner_member_id=?",
                    List.of(restriction.ownerIds().getFirst()));
            case OWNER_DEPARTMENT -> new RestrictionSql(
                    " AND r.owner_department_id=?",
                    List.of(restriction.ownerIds().getFirst()));
            case OWNER_MEMBERS -> restriction.ownerIds().isEmpty()
                    ? new RestrictionSql(" AND 1=0", List.of())
                    : new RestrictionSql(
                    " AND r.owner_member_id IN ("
                            + String.join(",", java.util.Collections.nCopies(
                            restriction.ownerIds().size(), "?")) + ")",
                    new ArrayList<>(restriction.ownerIds()));
        };
    }

    static String queryId(
            DataSourcePublication publication,
            NativeRecordAggregatePlan plan,
            StatisticsPlan statistics
    ) {
        return queryId(publication, plan, statistics, null);
    }

    static String queryId(
            DataSourcePublication publication,
            NativeRecordAggregatePlan plan,
            StatisticsPlan statistics,
            StatisticsRecordRestriction restriction
    ) {
        var request = statistics.request();
        var value = new StringBuilder("statistics-native-v1|");
        append(value, plan.queryIdentity());
        append(value, Long.toString(plan.systemId()));
        append(value, Long.toString(plan.tenantId()));
        append(value, Long.toString(plan.schemaVersionId()));
        append(value, Long.toString(plan.moduleSnapshotId()));
        append(value, Long.toString(plan.logicalModuleId()));
        append(value, plan.moduleCode());
        append(value, plan.whereSql());
        append(value, Integer.toString(plan.whereArguments().size()));
        plan.whereArguments().forEach(argument ->
                append(value, stableArgument(argument)));
        append(value, Long.toString(publication.version().id()));
        append(value, request.aggregation().name());
        append(value, request.measureFieldCode());
        if (request.grouping() != null) {
            append(value, request.grouping().fieldCode());
            append(value, Integer.toString(
                    request.grouping().bucketLimit()));
        }
        if (request.trend() != null) {
            append(value, request.trend().fieldCode());
            append(value, request.trend().grain().name());
            append(value, request.trend().startInclusive().toString());
            append(value, request.trend().endExclusive().toString());
        }
        if (restriction != null) {
            append(value, "ownership-restriction-v1");
            append(value, restriction.kind().name());
            append(value, Integer.toString(restriction.ownerIds().size()));
            restriction.ownerIds().forEach(ownerId ->
                    append(value, Long.toString(ownerId)));
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String stableArgument(Object value) {
        if (value == null) {
            return "null:";
        }
        if (value instanceof String text) {
            return "string:" + text;
        }
        if (value instanceof Long number) {
            return "long:" + number;
        }
        if (value instanceof Integer number) {
            return "integer:" + number;
        }
        if (value instanceof Short number) {
            return "short:" + number;
        }
        if (value instanceof Byte number) {
            return "byte:" + number;
        }
        if (value instanceof BigInteger number) {
            return "big-integer:" + number;
        }
        if (value instanceof BigDecimal number) {
            return "decimal:" + number.toPlainString();
        }
        if (value instanceof Double number) {
            return "double:" + Double.toHexString(number);
        }
        if (value instanceof Float number) {
            return "float:" + Float.toHexString(number);
        }
        if (value instanceof Boolean flag) {
            return "boolean:" + flag;
        }
        if (value instanceof LocalDate date) {
            return "local-date:" + date;
        }
        if (value instanceof LocalDateTime dateTime) {
            return "local-date-time:" + dateTime;
        }
        if (value instanceof LocalTime time) {
            return "local-time:" + time;
        }
        if (value instanceof java.sql.Date date) {
            return "sql-date:" + date.toLocalDate();
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return "sql-timestamp:" + timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Time time) {
            return "sql-time:" + time.toLocalTime();
        }
        if (value instanceof byte[] bytes) {
            return "bytes:" + HexFormat.of().formatHex(bytes);
        }
        throw unavailable(
                "Native query scope argument type is unsupported");
    }

    private static void append(StringBuilder target, String value) {
        if (value == null) {
            target.append("-1:");
        } else {
            target.append(value.length()).append(':').append(value);
        }
        target.append('|');
    }

    private static long positiveId(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw unavailable("Native schema identity is invalid");
        }
    }

    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static StatisticsException unavailable(String message) {
        return new StatisticsException(
                "STATISTICS_SOURCE_UNAVAILABLE", message);
    }

    private static StatisticsException invalidResult(String message) {
        return new StatisticsException(
                "STATISTICS_RESULT_INVALID", message);
    }

    record Overall(BigDecimal value, long matchedRecordCount) {
    }

    record GroupRow(
            String key,
            String label,
            BigDecimal value,
            long recordCount,
            long totalBucketCount
    ) {
    }

    private record Grouped(
            List<RawGroupBucket> buckets,
            long totalBucketCount
    ) {
    }

    private record RestrictionSql(
            String predicate,
            List<Object> arguments
    ) {
        private RestrictionSql {
            arguments = List.copyOf(arguments);
        }
    }

    private record ValueSource(
            String joinSql,
            List<Object> arguments,
            String expression,
            String displayExpression
    ) {
        private ValueSource {
            arguments = List.copyOf(arguments);
        }

        private static ValueSource none() {
            return new ValueSource("", List.of(), "", "NULL");
        }

        private static ValueSource recordColumn(String expression) {
            return new ValueSource("", List.of(), expression, expression);
        }
    }
}
