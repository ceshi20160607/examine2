package com.unique.examine.module.datasource.statistics.domain;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;

public record StatisticsResult(
        String queryId,
        long dataSourceId,
        String dataSourceCode,
        long dataSourceVersionId,
        int dataSourceVersionNumber,
        String moduleCode,
        String schemaVersionId,
        StatisticsAggregation aggregation,
        String measureFieldCode,
        String value,
        long matchedRecordCount,
        int aggregateCount,
        int bucketCount,
        List<GroupBucket> groupBuckets,
        List<TrendBucket> trendBuckets,
        long totalBucketCount,
        boolean truncated
) {
    public StatisticsResult {
        if (queryId == null || queryId.isBlank() || queryId.length() > 200
                || dataSourceId <= 0 || dataSourceVersionId <= 0
                || dataSourceVersionNumber <= 0 || aggregation == null
                || matchedRecordCount < 0 || aggregateCount < 1
                || bucketCount < 0
                || totalBucketCount < 0 || groupBuckets == null
                || trendBuckets == null || !groupBuckets.isEmpty()
                && !trendBuckets.isEmpty()) {
            throw invalid("Statistics result metadata is invalid");
        }
        queryId = queryId.strip();
        dataSourceCode = text(dataSourceCode, "data source code", 64);
        moduleCode = text(moduleCode, "module code", 100);
        schemaVersionId = text(schemaVersionId, "schema version", 200);
        measureFieldCode = optionalText(
                measureFieldCode, "measure field code", 64);
        value = CanonicalDecimal.require(value);
        groupBuckets = List.copyOf(groupBuckets);
        trendBuckets = List.copyOf(trendBuckets);
        if (groupBuckets.size() > StatisticsRequest.Grouping.MAX_BUCKETS
                || trendBuckets.size() > StatisticsRequest.Trend.MAX_BUCKETS
                || aggregateCount != 1 + groupBuckets.size()
                + trendBuckets.size()
                || bucketCount != groupBuckets.size() + trendBuckets.size()
                || totalBucketCount < groupBuckets.size()
                || totalBucketCount < trendBuckets.size()
                || truncated != (totalBucketCount
                > groupBuckets.size() + trendBuckets.size())) {
            throw invalid("Statistics bucket metadata is inconsistent");
        }
        validateGroupOrder(groupBuckets);
        validateTrendOrder(trendBuckets);
    }

    public record GroupBucket(
            String key,
            String label,
            boolean nullBucket,
            String value,
            long recordCount
    ) {
        public GroupBucket {
            if (recordCount <= 0 || nullBucket != (key == null)
                    || nullBucket && label != null
                    || !nullBucket && label == null) {
                throw invalid("Statistics group bucket is invalid");
            }
            if (key != null && key.length() > 10_000
                    || label != null && label.length() > 1_000) {
                throw invalid("Statistics group bucket exceeds its bound");
            }
            label = label == null ? null : label.strip();
            value = CanonicalDecimal.require(value);
        }
    }

    public record TrendBucket(
            LocalDate startInclusive,
            LocalDate endExclusive,
            String value,
            long recordCount,
            boolean empty
    ) {
        public TrendBucket {
            if (startInclusive == null || endExclusive == null
                    || !startInclusive.isBefore(endExclusive)
                    || recordCount < 0 || empty != (recordCount == 0)) {
                throw invalid("Statistics trend bucket is invalid");
            }
            value = CanonicalDecimal.require(value);
        }
    }

    private static void validateGroupOrder(List<GroupBucket> buckets) {
        var keys = new HashSet<String>();
        var nullSeen = false;
        GroupBucket previous = null;
        for (var bucket : buckets) {
            if (bucket.nullBucket()) {
                if (nullSeen) {
                    throw invalid(
                            "Statistics group buckets are not uniquely ordered");
                }
                nullSeen = true;
            } else if (!keys.add(bucket.key())) {
                throw invalid(
                        "Statistics group buckets are not uniquely ordered");
            }
            if (previous != null && compare(previous, bucket) >= 0) {
                throw invalid(
                        "Statistics group buckets are not uniquely ordered");
            }
            previous = bucket;
        }
    }

    private static int compare(GroupBucket left, GroupBucket right) {
        if (left.nullBucket()) {
            return right.nullBucket() ? 0 : 1;
        }
        if (right.nullBucket()) {
            return -1;
        }
        var key = left.key().compareTo(right.key());
        return key != 0 ? key : left.label().compareTo(right.label());
    }

    private static void validateTrendOrder(List<TrendBucket> buckets) {
        TrendBucket previous = null;
        for (var bucket : buckets) {
            if (previous != null
                    && (!previous.startInclusive()
                    .isBefore(bucket.startInclusive())
                    || !previous.endExclusive()
                    .equals(bucket.startInclusive()))) {
                throw invalid(
                        "Statistics trend buckets are not contiguous and ordered");
            }
            previous = bucket;
        }
    }

    private static String text(String value, String name, int max) {
        if (value == null || value.isBlank() || value.length() > max) {
            throw invalid("Statistics " + name + " is invalid");
        }
        return value.strip();
    }

    private static String optionalText(String value, String name, int max) {
        return value == null ? null : text(value, name, max);
    }

    private static StatisticsException invalid(String message) {
        return new StatisticsException("STATISTICS_RESULT_INVALID", message);
    }
}
