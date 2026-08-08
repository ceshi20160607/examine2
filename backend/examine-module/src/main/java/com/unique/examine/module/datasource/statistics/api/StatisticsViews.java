package com.unique.examine.module.datasource.statistics.api;

import java.util.List;

public final class StatisticsViews {
    private StatisticsViews() {
    }

    public record FieldCapability(
            String code,
            String name,
            String type,
            boolean readable,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
    }

    public record Capabilities(
            String dataSourceId,
            String dataSourceCode,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleCode,
            String schemaVersionId,
            List<FieldCapability> fields,
            String sourceKind,
            boolean partial,
            int sourceRowLimit,
            boolean multiModule
    ) {
        public Capabilities {
            fields = List.copyOf(fields);
        }

        public Capabilities(
                String dataSourceId,
                String dataSourceCode,
                String dataSourceVersionId,
                int dataSourceVersionNumber,
                String moduleCode,
                String schemaVersionId,
                List<FieldCapability> fields
        ) {
            this(dataSourceId, dataSourceCode, dataSourceVersionId,
                    dataSourceVersionNumber, moduleCode, schemaVersionId,
                    fields, "NATIVE_MODULE", false, 0, false);
        }
    }

    public record GroupBucket(
            String key,
            String label,
            boolean nullBucket,
            String value,
            long recordCount
    ) {
    }

    public record TrendBucket(
            String startInclusive,
            String endExclusive,
            String value,
            long recordCount,
            boolean empty
    ) {
    }

    public record Result(
            String queryId,
            String dataSourceId,
            String dataSourceCode,
            String dataSourceVersionId,
            int dataSourceVersionNumber,
            String moduleCode,
            String schemaVersionId,
            String aggregation,
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
        public Result {
            groupBuckets = List.copyOf(groupBuckets);
            trendBuckets = List.copyOf(trendBuckets);
        }
    }
}
