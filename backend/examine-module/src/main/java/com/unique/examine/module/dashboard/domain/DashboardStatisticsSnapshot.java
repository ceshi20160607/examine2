package com.unique.examine.module.dashboard.domain;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;
import com.unique.examine.module.datasource.statistics.domain.StatisticsRequest;

/** Immutable statistic request plus the exact published field identities. */
public record DashboardStatisticsSnapshot(
        StatisticsRequest request,
        Field measure,
        Field group,
        Field time
) {
    public DashboardStatisticsSnapshot {
        if (request == null
                || request.aggregation() == StatisticsAggregation.COUNT
                != (measure == null)
                || request.grouping() == null != (group == null)
                || request.trend() == null != (time == null)
                || measure != null && !measure.code().equals(
                request.measureFieldCode())
                || group != null && !group.code().equals(
                request.grouping().fieldCode())
                || time != null && !time.code().equals(
                request.trend().fieldCode())) {
            throw invalid("Published statistics field snapshot is inconsistent");
        }
    }

    public record Field(
            long logicalFieldId,
            String code,
            String name,
            String type,
            String queryType
    ) {
        public Field {
            if (logicalFieldId <= 0) {
                throw invalid("Published statistics field id is invalid");
            }
            code = text(code, "code", 64);
            name = text(name, "name", 200);
            type = text(type, "type", 100);
            queryType = text(queryType, "query type", 100);
        }
    }

    private static String text(String value, String name, int maximum) {
        if (value == null || value.isBlank() || value.length() > maximum) {
            throw invalid("Published statistics field " + name + " is invalid");
        }
        return value.strip();
    }

    private static DashboardException invalid(String message) {
        return new DashboardException(
                "DASHBOARD_STATISTICS_SNAPSHOT_INVALID", message);
    }
}
