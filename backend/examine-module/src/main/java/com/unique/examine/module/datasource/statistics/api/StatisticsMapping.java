package com.unique.examine.module.datasource.statistics.api;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsException;
import com.unique.examine.module.datasource.statistics.domain.StatisticsGrain;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsCapabilities;
import com.unique.examine.module.datasource.statistics.domain.StatisticsResult;

public final class StatisticsMapping {
    private StatisticsMapping() {
    }

    public static StatisticsRequest request(StatisticsRequests.Query value) {
        if (value == null) {
            throw invalid("Statistics request is required");
        }
        final StatisticsAggregation aggregation;
        try {
            aggregation = StatisticsAggregation.valueOf(value.aggregation());
        } catch (RuntimeException exception) {
            throw invalid("Statistics aggregation is unsupported");
        }
        StatisticsRequest.Grouping grouping = null;
        if (value.grouping() != null) {
            if (value.grouping().bucketLimit() == null) {
                throw invalid("Statistics group bucket limit is required");
            }
            grouping = new StatisticsRequest.Grouping(
                    value.grouping().fieldCode(),
                    value.grouping().bucketLimit());
        }
        StatisticsRequest.Trend trend = null;
        if (value.trend() != null) {
            final StatisticsGrain grain;
            try {
                grain = StatisticsGrain.valueOf(value.trend().grain());
            } catch (RuntimeException exception) {
                throw invalid("Statistics trend grain is unsupported");
            }
            trend = new StatisticsRequest.Trend(
                    value.trend().fieldCode(), grain,
                    value.trend().startInclusive(),
                    value.trend().endExclusive());
        }
        return new StatisticsRequest(
                aggregation, value.measureFieldCode(), grouping, trend);
    }

    public static StatisticsViews.Capabilities capabilities(
            StatisticsCapabilities value
    ) {
        return new StatisticsViews.Capabilities(
                Long.toString(value.dataSourceId()), value.dataSourceCode(),
                Long.toString(value.dataSourceVersionId()),
                value.dataSourceVersionNumber(), value.moduleCode(),
                value.schemaVersionId(), value.fields().stream()
                .map(field -> new StatisticsViews.FieldCapability(
                        field.code(), field.name(), field.type(),
                        field.readable(), field.numeric(), field.temporal(),
                        field.groupable()))
                .toList(), value.sourceKind(), value.partial(),
                value.sourceRowLimit(), value.multiModule());
    }

    public static StatisticsViews.Result result(StatisticsResult value) {
        return new StatisticsViews.Result(
                value.queryId(), Long.toString(value.dataSourceId()),
                value.dataSourceCode(),
                Long.toString(value.dataSourceVersionId()),
                value.dataSourceVersionNumber(), value.moduleCode(),
                value.schemaVersionId(), value.aggregation().name(),
                value.measureFieldCode(), value.value(),
                value.matchedRecordCount(), value.aggregateCount(),
                value.bucketCount(),
                value.groupBuckets().stream()
                        .map(bucket -> new StatisticsViews.GroupBucket(
                                bucket.key(), bucket.label(),
                                bucket.nullBucket(), bucket.value(),
                                bucket.recordCount()))
                        .toList(),
                value.trendBuckets().stream()
                        .map(bucket -> new StatisticsViews.TrendBucket(
                                bucket.startInclusive().toString(),
                                bucket.endExclusive().toString(),
                                bucket.value(), bucket.recordCount(),
                                bucket.empty()))
                        .toList(),
                value.totalBucketCount(), value.truncated());
    }

    private static StatisticsException invalid(String message) {
        return new StatisticsException("STATISTICS_REQUEST_INVALID", message);
    }
}
