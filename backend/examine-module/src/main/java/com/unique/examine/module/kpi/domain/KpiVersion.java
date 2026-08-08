package com.unique.examine.module.kpi.domain;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;

import java.time.Instant;

public record KpiVersion(
        long id,
        long kpiId,
        long systemId,
        long tenantId,
        int versionNumber,
        long sourceDraftVersion,
        String code,
        String name,
        String description,
        KpiSubjectType subjectType,
        KpiPeriodType periodType,
        StatisticsAggregation aggregation,
        KpiAttainmentDirection direction,
        String warningThreshold,
        KpiSourcePin source,
        String fingerprint,
        long publishedByMemberId,
        Instant publishedAt
) {
    public KpiVersion {
        if (id <= 0 || kpiId <= 0 || systemId <= 0 || tenantId <= 0
                || versionNumber <= 0 || sourceDraftVersion <= 0
                || subjectType == null || periodType == null
                || aggregation == null || direction == null || source == null
                || publishedByMemberId <= 0 || publishedAt == null) {
            throw invalid("Published KPI state is incomplete");
        }
        code = KpiDefinition.code(code);
        name = KpiDefinition.name(name);
        description = KpiDefinition.description(description);
        warningThreshold = KpiAttainment.requireThreshold(warningThreshold);
        if (aggregation == StatisticsAggregation.COUNT
                && source.measureField() != null
                || aggregation != StatisticsAggregation.COUNT
                && source.measureField() == null) {
            throw invalid("Published KPI measure pin is inconsistent");
        }
        if (fingerprint == null || !fingerprint.matches("^[0-9a-f]{64}$")) {
            throw invalid("Published KPI fingerprint is invalid");
        }
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_VERSION_INVALID", message);
    }
}
