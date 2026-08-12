package com.unique.examine.module.datasource.statistics.service;

import com.unique.examine.module.datasource.domain.DataSourceActor;
import com.unique.examine.module.datasource.domain.DataSourceDraft;
import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.statistics.domain.CanonicalDecimal;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsCapabilities;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsFieldPins;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.datasource.statistics.domain.StatisticsResult;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.FieldCapability;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.RawGroupBucket;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.RawStatistics;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.RawTrendBucket;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.SourceCapabilities;
import com.unique.examine.module.datasource.statistics.port.DataSourceStatisticsGateway.StatisticsPlan;
import com.unique.examine.module.datasource.service.DataSourceService;
import com.unique.examine.module.runtime.security.RuntimeSession;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public final class DataSourceStatisticsService {
    private final DataSourceService dataSources;
    private final DataSourceStatisticsGateway gateway;

    public DataSourceStatisticsService(
            DataSourceService dataSources,
            DataSourceStatisticsGateway gateway
    ) {
        this.dataSources = Objects.requireNonNull(dataSources, "dataSources");
        this.gateway = Objects.requireNonNull(gateway, "gateway");
    }

    public StatisticsCapabilities capabilities(
            RuntimeSession session,
            long dataSourceId,
            long exactVersionId
    ) {
        var resolved = resolve(session, dataSourceId, exactVersionId);
        var version = resolved.publication().version();
        return new StatisticsCapabilities(
                version.dataSourceId(), version.code(), version.id(),
                version.versionNumber(), version.moduleCode(),
                version.schemaVersionId(),
                resolved.orderedFields().stream()
                        .map(field -> new StatisticsCapabilities.Field(
                                field.code(), field.name(), field.type(),
                                field.readable(),
                                field.numeric(), field.temporal(),
                                field.groupable()))
                        .toList(), version.snapshot().sourceKind().name(),
                version.snapshot().sourceKind()
                        != DataSourceDraft.SourceKind.NATIVE_MODULE,
                version.snapshot().sourceKind()
                        == DataSourceDraft.SourceKind.NATIVE_MODULE ? 0
                        : version.snapshot().sourceKind()
                        == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN
                        ? version.snapshot().multiModuleJoin().rowLimit() : 25,
                version.snapshot().sourceKind()
                        == DataSourceDraft.SourceKind.MULTI_MODULE_JOIN
                        || resolved.orderedFields().stream().anyMatch(field ->
                        java.util.Set.of("REFERENCE", "LOOKUP", "AGGREGATE")
                                .contains(field.type())));
    }

    public StatisticsResult statistics(
            RuntimeSession session,
            long dataSourceId,
            long exactVersionId,
            StatisticsRequest request
    ) {
        return statistics(
                session, dataSourceId, exactVersionId, request, null, null);
    }

    public StatisticsResult statistics(
            RuntimeSession session,
            long dataSourceId,
            long exactVersionId,
            StatisticsRequest request,
            StatisticsFieldPins pinnedFields
    ) {
        return statistics(session, dataSourceId, exactVersionId, request,
                pinnedFields, null);
    }

    /**
     * Trusted internal exact path. Ownership restrictions cannot be created by
     * the public HTTP mapping and are applied by the native gateway before any
     * aggregate is evaluated.
     */
    public StatisticsResult statistics(
            RuntimeSession session,
            long dataSourceId,
            long exactVersionId,
            StatisticsRequest request,
            StatisticsFieldPins pinnedFields,
            StatisticsRecordRestriction restriction
    ) {
        Objects.requireNonNull(request, "request");
        var resolved = resolve(session, dataSourceId, exactVersionId);
        var plan = plan(request, resolved.fieldsByCode());
        requirePinnedFields(plan, pinnedFields);
        var raw = Objects.requireNonNull(restriction == null
                        ? gateway.execute(session, resolved.publication(), plan)
                        : gateway.execute(session, resolved.publication(), plan,
                        restriction),
                "raw statistics");
        return result(resolved.publication(), request, raw,
                restriction != null && restriction.matchesNoRecords());
    }

    private static void requirePinnedFields(
            StatisticsPlan plan,
            StatisticsFieldPins pins
    ) {
        if (pins == null) {
            return;
        }
        requirePinnedField(plan.measure(), pins.measure());
        requirePinnedField(plan.group(), pins.group());
        requirePinnedField(plan.time(), pins.time());
    }

    private static void requirePinnedField(
            FieldCapability actual,
            StatisticsFieldPins.Field pinned
    ) {
        if (actual == null && pinned == null) {
            return;
        }
        if (actual == null || pinned == null
                || actual.logicalFieldId() != pinned.logicalFieldId()
                || !actual.code().equals(pinned.code())
                || !actual.name().equals(pinned.name())
                || !actual.type().equals(pinned.type())
                || !actual.queryType().equals(pinned.queryType())) {
            throw error("STATISTICS_SOURCE_UNAVAILABLE",
                    "Pinned statistics field metadata no longer matches");
        }
    }

    private ResolvedSource resolve(
            RuntimeSession session,
            long dataSourceId,
            long exactVersionId
    ) {
        var actor = actor(session);
        var publication = dataSources.publication(
                actor, dataSourceId, exactVersionId);
        var capabilities = Objects.requireNonNull(
                gateway.capabilities(session, publication),
                "statistics capabilities");
        var version = publication.version();
        if (capabilities.logicalModuleId() != version.moduleId()
                || !capabilities.moduleCode().equals(version.moduleCode())
                || !capabilities.schemaVersionId()
                .equals(version.schemaVersionId())) {
            throw error("STATISTICS_SOURCE_UNAVAILABLE",
                    "Exact published data-source schema is unavailable");
        }

        var nativeFields = new HashMap<String, FieldCapability>();
        capabilities.fields().forEach(field ->
                nativeFields.put(field.code(), field));
        var orderedFields = new ArrayList<FieldCapability>();
        for (var fieldCode : fieldCodes(version.snapshot())) {
            var field = nativeFields.get(fieldCode);
            if (field != null) {
                orderedFields.add(field);
            }
        }
        var fieldsByCode = new LinkedHashMap<String, FieldCapability>();
        orderedFields.forEach(field -> fieldsByCode.put(
                field.code(), field));
        return new ResolvedSource(
                publication, List.copyOf(orderedFields),
                Map.copyOf(fieldsByCode));
    }

    private static List<String> fieldCodes(DataSourceDraft snapshot) {
        return switch (snapshot.sourceKind()) {
            case NATIVE_MODULE -> snapshot.outputFields().stream()
                    .map(DataSourceDraft.OutputField::fieldCode).toList();
            case HTTP_JSON -> snapshot.httpFieldProjections().stream()
                    .map(DataSourceDraft.HttpJsonFieldProjection::fieldCode)
                    .toList();
            case JDBC_TABLE -> snapshot.jdbcFieldProjections().stream()
                    .map(DataSourceDraft.JdbcTableFieldProjection::fieldCode)
                    .toList();
            case MULTI_MODULE_JOIN -> snapshot.multiModuleJoin().projections()
                    .stream().map(DataSourceDraft.JoinProjection::fieldCode)
                    .toList();
        };
    }

    private static StatisticsPlan plan(
            StatisticsRequest request,
            Map<String, FieldCapability> fields
    ) {
        FieldCapability measure = null;
        if (request.measureFieldCode() != null) {
            measure = readableField(fields, request.measureFieldCode());
            if (!measure.numeric()) {
                throw error("STATISTICS_MEASURE_INVALID",
                        "Statistics measure field must be numeric");
            }
        }

        FieldCapability group = null;
        if (request.grouping() != null) {
            group = readableField(
                    fields, request.grouping().fieldCode());
            if (!group.groupable()) {
                throw error("STATISTICS_GROUP_INVALID",
                        "Statistics group field is not groupable");
            }
        }

        FieldCapability time = null;
        if (request.trend() != null) {
            time = readableField(fields, request.trend().fieldCode());
            if (!time.temporal()) {
                throw error("STATISTICS_TIME_INVALID",
                        "Statistics trend field must be temporal");
            }
        }
        return new StatisticsPlan(request, measure, group, time);
    }

    private static FieldCapability readableField(
            Map<String, FieldCapability> fields,
            String code
    ) {
        var field = fields.get(code);
        if (field == null || !field.readable()) {
            throw error("STATISTICS_FIELD_NOT_FOUND",
                    "Statistics field is absent or unreadable");
        }
        return field;
    }

    private static StatisticsResult result(
            DataSourcePublication publication,
            StatisticsRequest request,
            RawStatistics raw,
            boolean exactZero
    ) {
        if (exactZero && raw.matchedRecordCount() != 0) {
            throw invalidResult(
                    "Empty ownership restriction matched native records");
        }
        var value = exactZero ? "0" : aggregateValue(
                request.aggregation(), raw.value(), raw.matchedRecordCount());
        List<StatisticsResult.GroupBucket> groups = List.of();
        List<StatisticsResult.TrendBucket> trend = List.of();
        long totalBucketCount = 0;
        boolean truncated = false;

        if (request.grouping() != null) {
            groups = groupBuckets(request, raw);
            totalBucketCount = raw.totalBucketCount();
            truncated = totalBucketCount > groups.size();
            if (raw.truncated() != truncated) {
                throw invalidResult(
                        "Native group overflow metadata is inconsistent");
            }
            if (!truncated && recordCount(groups) != raw.matchedRecordCount()) {
                throw invalidResult(
                        "Native group record count is inconsistent");
            }
        } else if (request.trend() != null) {
            trend = trendBuckets(request, raw, exactZero);
            totalBucketCount = trend.size();
            if (raw.truncated()
                    || raw.totalBucketCount() > trend.size()) {
                throw invalidResult(
                        "Native trend overflow metadata is inconsistent");
            }
            if (trendRecordCount(trend) != raw.matchedRecordCount()) {
                throw invalidResult(
                        "Native trend record count is inconsistent");
            }
        } else if (!raw.groupBuckets().isEmpty()
                || !raw.trendBuckets().isEmpty()
                || raw.totalBucketCount() != 0 || raw.truncated()) {
            throw invalidResult(
                    "Native scalar statistics returned unexpected buckets");
        }

        var version = publication.version();
        return new StatisticsResult(
                raw.queryId(), version.dataSourceId(), version.code(),
                version.id(), version.versionNumber(), version.moduleCode(),
                version.schemaVersionId(), request.aggregation(),
                request.measureFieldCode(), value, raw.matchedRecordCount(),
                1 + groups.size() + trend.size(),
                groups.size() + trend.size(), groups, trend,
                totalBucketCount, truncated);
    }

    private static List<StatisticsResult.GroupBucket> groupBuckets(
            StatisticsRequest request,
            RawStatistics raw
    ) {
        if (!raw.trendBuckets().isEmpty()
                || raw.totalBucketCount() < raw.groupBuckets().size()) {
            throw invalidResult("Native group bucket metadata is invalid");
        }
        var ordered = raw.groupBuckets().stream()
                .sorted(DataSourceStatisticsService::compareGroups)
                .toList();
        var identities = new HashSet<String>();
        var result = new ArrayList<StatisticsResult.GroupBucket>();
        var limit = request.grouping().bucketLimit();
        for (var bucket : ordered) {
            var identity = bucket.nullBucket()
                    ? "\u0000NULL" : "\u0000VALUE" + bucket.key();
            if (!identities.add(identity)) {
                throw invalidResult("Native group buckets are duplicated");
            }
            if (bucket.recordCount() <= 0) {
                throw invalidResult(
                        "Native group bucket record count is invalid");
            }
            if (result.size() < limit) {
                result.add(new StatisticsResult.GroupBucket(
                        bucket.key(), bucket.label(), bucket.nullBucket(),
                        aggregateValue(request.aggregation(), bucket.value(),
                                bucket.recordCount()),
                        bucket.recordCount()));
            }
        }
        if (raw.totalBucketCount() < result.size()) {
            throw invalidResult("Native group bucket count is invalid");
        }
        return List.copyOf(result);
    }

    private static int compareGroups(
            RawGroupBucket left,
            RawGroupBucket right
    ) {
        if (left.nullBucket()) {
            return right.nullBucket() ? 0 : 1;
        }
        if (right.nullBucket()) {
            return -1;
        }
        var key = left.key().compareTo(right.key());
        return key != 0 ? key : left.label().compareTo(right.label());
    }

    private static List<StatisticsResult.TrendBucket> trendBuckets(
            StatisticsRequest request,
            RawStatistics raw,
            boolean exactZero
    ) {
        if (!raw.groupBuckets().isEmpty()) {
            throw invalidResult("Native trend returned group buckets");
        }
        var rawByStart = new HashMap<LocalDate, RawTrendBucket>();
        for (var bucket : raw.trendBuckets()) {
            if (rawByStart.put(bucket.startInclusive(), bucket) != null) {
                throw invalidResult("Native trend buckets are duplicated");
            }
        }

        var result = new ArrayList<StatisticsResult.TrendBucket>();
        var trend = request.trend();
        var cursor = trend.startInclusive();
        while (cursor.isBefore(trend.endExclusive())) {
            var end = trend.grain().next(cursor);
            var bucket = rawByStart.remove(cursor);
            var recordCount = bucket == null ? 0 : bucket.recordCount();
            var value = bucket == null || recordCount == 0
                    ? emptyValue(request.aggregation(), exactZero)
                    : aggregateValue(request.aggregation(), bucket.value(),
                    recordCount);
            result.add(new StatisticsResult.TrendBucket(
                    cursor, end, value, recordCount, bucket == null
                    || recordCount == 0));
            cursor = end;
        }
        if (!rawByStart.isEmpty()) {
            throw invalidResult(
                    "Native trend bucket is outside the requested range");
        }
        return List.copyOf(result);
    }

    private static String aggregateValue(
            StatisticsAggregation aggregation,
            BigDecimal raw,
            long recordCount
    ) {
        if (aggregation == StatisticsAggregation.COUNT) {
            return Long.toString(recordCount);
        }
        return CanonicalDecimal.from(raw);
    }

    private static String emptyValue(
            StatisticsAggregation aggregation,
            boolean exactZero
    ) {
        return exactZero || aggregation == StatisticsAggregation.COUNT
                ? "0" : null;
    }

    private static long recordCount(
            List<StatisticsResult.GroupBucket> buckets
    ) {
        try {
            long count = 0;
            for (var bucket : buckets) {
                count = Math.addExact(count, bucket.recordCount());
            }
            return count;
        } catch (ArithmeticException overflow) {
            throw invalidResult("Native group record count overflowed");
        }
    }

    private static long trendRecordCount(
            List<StatisticsResult.TrendBucket> buckets
    ) {
        try {
            long count = 0;
            for (var bucket : buckets) {
                count = Math.addExact(count, bucket.recordCount());
            }
            return count;
        } catch (ArithmeticException overflow) {
            throw invalidResult("Native trend record count overflowed");
        }
    }

    private static DataSourceActor actor(RuntimeSession session) {
        if (session == null || session.tenantId() == null
                || session.tenantId() <= 0 || session.systemId() <= 0
                || session.memberId() <= 0) {
            throw error("STATISTICS_TENANT_REQUIRED",
                    "Select an active tenant before running statistics");
        }
        return new DataSourceActor(
                session.systemId(), session.tenantId(), session.memberId());
    }

    private static StatisticsException invalidResult(String message) {
        return error("STATISTICS_RESULT_INVALID", message);
    }

    private static StatisticsException error(String code, String message) {
        return new StatisticsException(code, message);
    }

    private record ResolvedSource(
            DataSourcePublication publication,
            List<FieldCapability> orderedFields,
            Map<String, FieldCapability> fieldsByCode
    ) {
    }
}
