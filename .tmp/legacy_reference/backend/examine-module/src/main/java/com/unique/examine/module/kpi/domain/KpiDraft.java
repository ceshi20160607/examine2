package com.unique.examine.module.kpi.domain;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;

import java.util.regex.Pattern;

public record KpiDraft(
        long dataSourceId,
        KpiSubjectType subjectType,
        KpiPeriodType periodType,
        StatisticsAggregation aggregation,
        String measureFieldCode,
        String timeFieldCode,
        KpiAttainmentDirection direction,
        String warningThreshold
) {
    private static final Pattern FIELD_CODE =
            Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,63}$");

    public KpiDraft {
        if (dataSourceId <= 0 || subjectType == null || periodType == null
                || aggregation == null || direction == null) {
            throw invalid("KPI draft is incomplete");
        }
        measureFieldCode = optionalField(measureFieldCode);
        timeFieldCode = field(timeFieldCode);
        warningThreshold = KpiAttainment.requireThreshold(warningThreshold);
        if (aggregation == StatisticsAggregation.COUNT
                && measureFieldCode != null) {
            throw invalid("COUNT does not accept a measure field");
        }
        if (aggregation != StatisticsAggregation.COUNT
                && measureFieldCode == null) {
            throw invalid(aggregation + " requires a numeric measure field");
        }
    }

    private static String field(String value) {
        if (value == null || !FIELD_CODE.matcher(value).matches()) {
            throw invalid("KPI field code is invalid");
        }
        return value;
    }

    private static String optionalField(String value) {
        return value == null ? null : field(value);
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_DRAFT_INVALID", message);
    }
}
