package com.unique.examine.module.datasource.statistics.port;

import com.unique.examine.module.datasource.domain.DataSourcePublication;
import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRecordRestriction;
import com.unique.examine.module.runtime.security.RuntimeSession;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Native, permission-scoped database aggregation over one exact publication. */
public interface DataSourceStatisticsGateway {
    SourceCapabilities capabilities(
            RuntimeSession session,
            DataSourcePublication publication);

    RawStatistics execute(
            RuntimeSession session,
            DataSourcePublication publication,
            StatisticsPlan plan);

    /** Internal-only execution path used by trusted module orchestration. */
    default RawStatistics execute(
            RuntimeSession session,
            DataSourcePublication publication,
            StatisticsPlan plan,
            StatisticsRecordRestriction restriction
    ) {
        if (restriction == null) {
            return execute(session, publication, plan);
        }
        throw new UnsupportedOperationException(
                "This statistics gateway does not support internal restrictions");
    }

    record SourceCapabilities(
            long logicalModuleId,
            String moduleCode,
            String schemaVersionId,
            List<FieldCapability> fields
    ) {
        public SourceCapabilities {
            if (logicalModuleId <= 0 || moduleCode == null
                    || moduleCode.isBlank() || schemaVersionId == null
                    || schemaVersionId.isBlank() || fields == null) {
                throw new IllegalArgumentException(
                        "Statistics source capabilities are invalid");
            }
            moduleCode = moduleCode.strip();
            schemaVersionId = schemaVersionId.strip();
            fields = List.copyOf(fields);
            if (fields.stream().map(FieldCapability::code).distinct().count()
                    != fields.size()) {
                throw new IllegalArgumentException(
                        "Statistics source field capabilities are duplicated");
            }
        }
    }

    record FieldCapability(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType,
            boolean readable,
            boolean numeric,
            boolean temporal,
            boolean groupable
    ) {
        public FieldCapability {
            if (logicalFieldId <= 0 || code == null || code.isBlank()
                    || name == null || name.isBlank()
                    || type == null || type.isBlank() || queryType == null
                    || queryType.isBlank()) {
                throw new IllegalArgumentException(
                        "Statistics field capability is invalid");
            }
            code = code.strip();
            name = name.strip();
            type = type.strip();
            queryType = queryType.strip();
        }
    }

    /** Fully validated plan; adapters still parameterize every native query. */
    record StatisticsPlan(
            StatisticsRequest request,
            FieldCapability measure,
            FieldCapability group,
            FieldCapability time
    ) {
        public StatisticsPlan {
            var count = request != null
                    && request.aggregation() == StatisticsAggregation.COUNT;
            if (request == null
                    || count && measure != null
                    || !count && measure == null
                    || request.grouping() == null != (group == null)
                    || request.trend() == null != (time == null)
                    || measure != null && (!measure.readable()
                    || !measure.numeric())
                    || group != null && (!group.readable()
                    || !group.groupable())
                    || time != null && (!time.readable()
                    || !time.temporal())) {
                throw new IllegalArgumentException(
                        "Statistics execution plan is inconsistent");
            }
        }
    }

    record RawStatistics(
            String queryId,
            BigDecimal value,
            long matchedRecordCount,
            List<RawGroupBucket> groupBuckets,
            List<RawTrendBucket> trendBuckets,
            long totalBucketCount,
            boolean truncated
    ) {
        public RawStatistics {
            if (queryId == null || queryId.isBlank()
                    || queryId.length() > 200 || matchedRecordCount < 0
                    || groupBuckets == null || trendBuckets == null
                    || totalBucketCount < 0 || !groupBuckets.isEmpty()
                    && !trendBuckets.isEmpty()) {
                throw new IllegalArgumentException(
                        "Raw statistics result is invalid");
            }
            queryId = queryId.strip();
            groupBuckets = List.copyOf(groupBuckets);
            trendBuckets = List.copyOf(trendBuckets);
        }
    }

    record RawGroupBucket(
            String key,
            String label,
            boolean nullBucket,
            BigDecimal value,
            long recordCount
    ) {
        public RawGroupBucket {
            if (recordCount < 0 || nullBucket != (key == null)
                    || nullBucket && label != null
                    || !nullBucket && label == null) {
                throw new IllegalArgumentException(
                        "Raw statistics group bucket is invalid");
            }
            if (label != null) {
                label = label.strip();
            }
        }
    }

    record RawTrendBucket(
            LocalDate startInclusive,
            BigDecimal value,
            long recordCount
    ) {
        public RawTrendBucket {
            if (startInclusive == null || recordCount < 0) {
                throw new IllegalArgumentException(
                        "Raw statistics trend bucket is invalid");
            }
        }
    }
}
