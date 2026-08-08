package com.unique.examine.module.datasource.statistics.adapter;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.runtime.DataSourceRecordQueryGateway;
import com.unique.examine.module.datasource.service.PublishedHttpDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedJdbcTableDataSourceRowsReader;
import com.unique.examine.module.datasource.service.PublishedMultiModuleJoinRowsReader;
import com.unique.examine.module.datasource.statistics.domain.CanonicalDecimal;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway;
import com.unique.examine.module.runtime.api.RecordRuntimeViews;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Negotiates the immutable source kind once, retaining the existing native SQL
 * implementation while evaluating bounded HTTP/JDBC projections in memory.
 * External readers keep their Cycle108 SSRF, SecretRef and timeout boundaries.
 */
@Primary
@Repository("negotiatingDataSourceStatisticsGateway")
public class NegotiatingDataSourceStatisticsGateway
        implements DataSourceStatisticsGateway {
    private static final Set<String> NUMERIC = Set.of(
            "NUMBER", "PERCENT", "MONEY", "RATING", "PROGRESS");
    private static final Set<String> TEMPORAL = Set.of("DATE", "DATETIME");
    private static final Set<String> GROUPABLE = Set.of(
            "TEXT", "NUMBER", "DATE", "DATETIME", "RADIO", "MEMBER",
            "DEPARTMENT", "PERCENT", "MONEY", "TIME", "SWITCH",
            "RATING", "PROGRESS", "STATUS");

    private final NativeDataSourceStatisticsGateway nativeGateway;
    private final DataSourceRecordQueryGateway records;
    private final PublishedHttpDataSourceRowsReader httpRows;
    private final PublishedJdbcTableDataSourceRowsReader jdbcRows;
    private final ObjectProvider<PublishedMultiModuleJoinRowsReader> joinRows;
    private final PublishedMultiModuleJoinRowsReader directJoinRows;

    @Autowired
    public NegotiatingDataSourceStatisticsGateway(
            NativeDataSourceStatisticsGateway nativeGateway,
            DataSourceRecordQueryGateway records,
            ObjectProvider<PublishedHttpDataSourceRowsReader> httpReaders,
            ObjectProvider<PublishedJdbcTableDataSourceRowsReader> jdbcReaders,
            ObjectProvider<PublishedMultiModuleJoinRowsReader> joinReaders
    ) {
        this(nativeGateway, records,
                httpReaders.getIfUnique(() -> (actor, publication) -> {
                    throw unavailable("Published HTTP rows are unavailable");
                }),
                jdbcReaders.getIfUnique(() -> (actor, publication) -> {
                    throw unavailable("Published JDBC rows are unavailable");
                }), joinReaders, null);
    }

    NegotiatingDataSourceStatisticsGateway(
            NativeDataSourceStatisticsGateway nativeGateway,
            DataSourceRecordQueryGateway records,
            PublishedHttpDataSourceRowsReader httpRows,
            PublishedJdbcTableDataSourceRowsReader jdbcRows
    ) {
        this(nativeGateway, records, httpRows, jdbcRows, null, null);
    }

    NegotiatingDataSourceStatisticsGateway(
            NativeDataSourceStatisticsGateway nativeGateway,
            DataSourceRecordQueryGateway records,
            PublishedHttpDataSourceRowsReader httpRows,
            PublishedJdbcTableDataSourceRowsReader jdbcRows,
            ObjectProvider<PublishedMultiModuleJoinRowsReader> joinRows
    ) {
        this(nativeGateway, records, httpRows, jdbcRows, joinRows, null);
    }

    NegotiatingDataSourceStatisticsGateway(
            NativeDataSourceStatisticsGateway nativeGateway,
            DataSourceRecordQueryGateway records,
            PublishedHttpDataSourceRowsReader httpRows,
            PublishedJdbcTableDataSourceRowsReader jdbcRows,
            PublishedMultiModuleJoinRowsReader directJoinRows
    ) {
        this(nativeGateway, records, httpRows, jdbcRows, null,
                directJoinRows);
    }

    private NegotiatingDataSourceStatisticsGateway(
            NativeDataSourceStatisticsGateway nativeGateway,
            DataSourceRecordQueryGateway records,
            PublishedHttpDataSourceRowsReader httpRows,
            PublishedJdbcTableDataSourceRowsReader jdbcRows,
            ObjectProvider<PublishedMultiModuleJoinRowsReader> joinRows,
            PublishedMultiModuleJoinRowsReader directJoinRows
    ) {
        this.nativeGateway = Objects.requireNonNull(nativeGateway, "nativeGateway");
        this.records = Objects.requireNonNull(records, "records");
        this.httpRows = Objects.requireNonNull(httpRows, "httpRows");
        this.jdbcRows = Objects.requireNonNull(jdbcRows, "jdbcRows");
        this.joinRows = joinRows;
        this.directJoinRows = directJoinRows;
    }

    @Override
    public SourceCapabilities capabilities(
            RuntimeSession session,
            DataSourcePublication publication
    ) {
        if (kind(publication) == DataSourceDraft.SourceKind.NATIVE_MODULE) {
            return nativeGateway.capabilities(session, publication);
        }
        requireScope(session, publication);
        var version = publication.version();
        if (kind(publication)
                == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            var join = version.snapshot().multiModuleJoin();
            if (join == null || join.projections().stream()
                    .anyMatch(field -> field.logicalFieldId() <= 0)) {
                throw unavailable("Published join capability pins are unavailable");
            }
            return new SourceCapabilities(
                    version.moduleId(), version.moduleCode(),
                    version.schemaVersionId(), join.projections().stream()
                    .map(field -> new FieldCapability(
                            field.logicalFieldId(), field.fieldCode(),
                            field.fieldName(), field.type(), field.queryType(),
                            true, field.numeric(), field.temporal(),
                            field.groupable())).toList());
        }
        var schema = records.schema(
                session, version.moduleCode(), version.schemaVersionId());
        if (!"READY".equals(schema.runtimeState())
                || !version.schemaVersionId().equals(schema.schemaVersionId())
                || !Long.toString(version.moduleId())
                .equals(schema.logicalModuleId())) {
            throw unavailable("Exact external anchor schema is unavailable");
        }
        return new SourceCapabilities(
                version.moduleId(), version.moduleCode(),
                version.schemaVersionId(), schema.fields().stream()
                .map(NegotiatingDataSourceStatisticsGateway::capability)
                .toList());
    }

    @Override
    public RawStatistics execute(
            RuntimeSession session,
            DataSourcePublication publication,
            StatisticsPlan plan
    ) {
        return execute(session, publication, plan, null);
    }

    @Override
    public RawStatistics execute(
            RuntimeSession session,
            DataSourcePublication publication,
            StatisticsPlan plan,
            StatisticsRecordRestriction restriction
    ) {
        if (kind(publication) == DataSourceDraft.SourceKind.NATIVE_MODULE) {
            return nativeGateway.execute(
                    session, publication, plan, restriction);
        }
        requireScope(session, publication);
        Objects.requireNonNull(plan, "plan");
        // This call is the current module-view authorization boundary and also
        // proves that the exact immutable anchor schema is still available.
        records.schema(session, publication.version().moduleCode(),
                publication.version().schemaVersionId());
        var rows = new ArrayList<>(read(session, publication));
        rows.removeIf(row -> !allowed(row, restriction));
        if (plan.request().trend() != null) {
            var trend = plan.request().trend();
            rows.removeIf(row -> {
                var value = date(row.get(plan.time().code()));
                return value == null || value.isBefore(trend.startInclusive())
                        || !value.isBefore(trend.endExclusive());
            });
        }
        var queryId = queryId(publication, plan, restriction);
        var overall = aggregate(rows, plan);
        if (plan.request().grouping() != null) {
            var grouped = groups(rows, plan);
            var limit = plan.request().grouping().bucketLimit();
            var visible = grouped.stream().limit(limit).toList();
            return new RawStatistics(
                    queryId, overall, rows.size(), visible, List.of(),
                    grouped.size(), grouped.size() > visible.size());
        }
        if (plan.request().trend() != null) {
            var buckets = trend(rows, plan);
            return new RawStatistics(
                    queryId, overall, rows.size(), List.of(), buckets,
                    buckets.size(), false);
        }
        return new RawStatistics(
                queryId, overall, rows.size(), List.of(), List.of(), 0, false);
    }

    private List<Map<String, Object>> read(
            RuntimeSession session,
            DataSourcePublication publication
    ) {
        var actor = new DataSourceActor(
                session.systemId(), session.tenantId(), session.memberId());
        if (kind(publication) == DataSourceDraft.SourceKind.HTTP_JSON) {
            var result = httpRows.read(actor, publication);
            requireIdentity(publication, result.dataSourceId(),
                    result.versionId(), result.versionNumber());
            return result.rows().stream()
                    .map(PublishedHttpDataSourceRowsReader.Row::values)
                    .toList();
        }
        if (kind(publication)
                == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN) {
            var reader = directJoinRows != null ? directJoinRows
                    : joinRows == null ? null : joinRows.getIfUnique();
            if (reader == null) {
                throw unavailable("Published multi-module join rows are unavailable");
            }
            var result = reader.read(session, publication);
            requireIdentity(publication, result.dataSourceId(),
                    result.versionId(), result.versionNumber());
            if (result.partial()) {
                throw new StatisticsException(
                        "STATISTICS_SOURCE_PARTIAL",
                        "Joined statistics source returned a bounded partial result");
            }
            return result.rows().stream()
                    .map(PublishedMultiModuleJoinRowsReader.Row::values)
                    .toList();
        }
        var result = jdbcRows.read(actor, publication);
        requireIdentity(publication, result.dataSourceId(),
                result.versionId(), result.versionNumber());
        return result.rows().stream()
                .map(PublishedJdbcTableDataSourceRowsReader.Row::values)
                .toList();
    }

    private static List<RawGroupBucket> groups(
            List<Map<String, Object>> rows,
            StatisticsPlan plan
    ) {
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (var row : rows) {
            var value = row.get(plan.group().code());
            var key = groupKey(value, plan.group().queryType());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(row);
            labels.putIfAbsent(key, key == null ? null : display(value));
        }
        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.nullsLast(String::compareTo)))
                .map(entry -> new RawGroupBucket(
                        entry.getKey(), labels.get(entry.getKey()),
                        entry.getKey() == null, aggregate(entry.getValue(), plan),
                        entry.getValue().size()))
                .toList();
    }

    private static List<RawTrendBucket> trend(
            List<Map<String, Object>> rows,
            StatisticsPlan plan
    ) {
        Map<LocalDate, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        for (var row : rows) {
            var value = date(row.get(plan.time().code()));
            if (value != null) {
                grouped.computeIfAbsent(bucket(value,
                                plan.request().trend().grain()),
                        ignored -> new ArrayList<>()).add(row);
            }
        }
        return grouped.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(entry -> new RawTrendBucket(
                        entry.getKey(), aggregate(entry.getValue(), plan),
                        entry.getValue().size()))
                .toList();
    }

    private static BigDecimal aggregate(
            List<Map<String, Object>> rows,
            StatisticsPlan plan
    ) {
        if (plan.request().aggregation() == StatisticsAggregation.COUNT) {
            return BigDecimal.valueOf(rows.size());
        }
        var values = rows.stream()
                .map(row -> decimal(row.get(plan.measure().code())))
                .filter(Objects::nonNull).toList();
        if (values.isEmpty()) {
            return null;
        }
        return switch (plan.request().aggregation()) {
            case SUM -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
            case AVG -> values.stream().reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(values.size()),
                            java.math.MathContext.DECIMAL128);
            case MIN -> values.stream().min(BigDecimal::compareTo).orElseThrow();
            case MAX -> values.stream().max(BigDecimal::compareTo).orElseThrow();
            case COUNT -> throw new IllegalStateException();
        };
    }

    private static boolean allowed(
            Map<String, Object> row,
            StatisticsRecordRestriction restriction
    ) {
        if (restriction == null) {
            return true;
        }
        if (restriction.matchesNoRecords()) {
            return false;
        }
        var owner = switch (restriction.kind()) {
            case OWNER_MEMBER, OWNER_MEMBERS -> owner(row,
                    "ownerMemberId", "owner_member_id");
            case OWNER_DEPARTMENT -> owner(row,
                    "ownerDepartmentId", "owner_department_id");
        };
        if (owner == null) {
            throw unavailable(
                    "External KPI statistics require an explicit owner projection");
        }
        return restriction.ownerIds().contains(owner);
    }

    private static Long owner(Map<String, Object> row, String... codes) {
        for (var code : codes) {
            if (row.containsKey(code)) {
                var value = decimal(row.get(code));
                if (value == null) {
                    return null;
                }
                try {
                    var id = value.longValueExact();
                    return id > 0 ? id : null;
                } catch (ArithmeticException invalid) {
                    return null;
                }
            }
        }
        Long resolved = null;
        for (var entry : row.entrySet()) {
            var matches = false;
            for (var code : codes) {
                if (entry.getKey().endsWith("__" + code)) {
                    matches = true;
                    break;
                }
            }
            if (!matches) {
                continue;
            }
            var value = decimal(entry.getValue());
            if (value == null) {
                return null;
            }
            try {
                var id = value.longValueExact();
                if (id <= 0 || resolved != null) {
                    return null;
                }
                resolved = id;
            } catch (ArithmeticException invalid) {
                return null;
            }
        }
        return resolved;
    }

    private static FieldCapability capability(
            RecordRuntimeViews.FieldCapability field
    ) {
        var queryType = queryType(field);
        return new FieldCapability(
                positiveId(field.logicalFieldId()), field.fieldCode(),
                field.fieldName(),
                field.type(), queryType, field.readable(),
                field.readable() && NUMERIC.contains(queryType),
                field.readable() && TEMPORAL.contains(queryType),
                field.readable() && GROUPABLE.contains(queryType));
    }

    private static String queryType(RecordRuntimeViews.FieldCapability field) {
        if ("REFERENCE".equals(field.type())) {
            return field.schema().path("referenceResultType").asText("TEXT");
        }
        var derived = field.schema().path("derivedQueryType").asText("");
        if (!derived.isBlank()) {
            return derived;
        }
        return switch (field.type()) {
            case "AUTO_NUMBER" -> "TEXT";
            case "TENANT", "CREATED_BY", "UPDATED_BY" -> "MEMBER";
            case "CREATED_AT", "UPDATED_AT" -> "DATETIME";
            default -> field.type();
        };
    }

    private static BigDecimal decimal(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof BigDecimal decimal) {
                return decimal;
            }
            if (value instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            if (value instanceof Byte || value instanceof Short
                    || value instanceof Integer || value instanceof Long) {
                return BigDecimal.valueOf(((Number) value).longValue());
            }
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException invalid) {
            throw unavailable("External numeric projection is invalid");
        }
    }

    private static LocalDate date(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof LocalDate date) {
                return date;
            }
            if (value instanceof LocalDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (value instanceof OffsetDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (value instanceof ZonedDateTime dateTime) {
                return dateTime.toLocalDate();
            }
            if (value instanceof java.sql.Date date) {
                return date.toLocalDate();
            }
            if (value instanceof java.sql.Timestamp timestamp) {
                return timestamp.toLocalDateTime().toLocalDate();
            }
            var text = String.valueOf(value);
            return text.length() >= 10
                    ? LocalDate.parse(text.substring(0, 10)) : null;
        } catch (RuntimeException invalid) {
            throw unavailable("External temporal projection is invalid");
        }
    }

    private static LocalDate bucket(LocalDate date, StatisticsGrain grain) {
        return switch (grain) {
            case DAY -> date;
            case WEEK -> date.minusDays(date.getDayOfWeek().getValue() - 1L);
            case MONTH -> date.withDayOfMonth(1);
        };
    }

    private static String groupKey(Object value, String queryType) {
        if (value == null) {
            return null;
        }
        return NUMERIC.contains(queryType)
                ? CanonicalDecimal.from(decimal(value)) : display(value);
    }

    private static String display(Object value) {
        return value instanceof TemporalAccessor
                ? value.toString() : String.valueOf(value);
    }

    private static String queryId(
            DataSourcePublication publication,
            StatisticsPlan plan,
            StatisticsRecordRestriction restriction
    ) {
        var value = new StringBuilder("statistics-external-v1|")
                .append(kind(publication)).append('|')
                .append(publication.version().id()).append('|')
                .append(plan.request()).append('|');
        if (restriction != null) {
            value.append(restriction.kind()).append('|')
                    .append(restriction.ownerIds());
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static DataSourceDraft.SourceKind kind(
            DataSourcePublication publication
    ) {
        return Objects.requireNonNull(publication, "publication")
                .version().snapshot().sourceKind();
    }

    private static void requireScope(
            RuntimeSession session,
            DataSourcePublication publication
    ) {
        if (session == null || session.tenantId() == null
                || session.systemId() != publication.version().systemId()
                || session.tenantId() != publication.version().tenantId()
                || session.memberId() <= 0) {
            throw unavailable("External statistics source is unavailable");
        }
    }

    private static void requireIdentity(
            DataSourcePublication publication,
            long dataSourceId,
            long versionId,
            int versionNumber
    ) {
        if (dataSourceId != publication.root().id()
                || versionId != publication.version().id()
                || versionNumber != publication.version().versionNumber()) {
            throw new IllegalStateException(
                    "External rows reader returned inconsistent identity");
        }
    }

    private static StatisticsException unavailable(String message) {
        return new StatisticsException("STATISTICS_SOURCE_UNAVAILABLE", message);
    }

    private static long positiveId(String value) {
        try {
            var parsed = Long.parseLong(value);
            if (parsed <= 0) {
                throw new NumberFormatException();
            }
            return parsed;
        } catch (RuntimeException invalid) {
            throw unavailable("External anchor field identity is invalid");
        }
    }
}
