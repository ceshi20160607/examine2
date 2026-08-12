package com.unique.examine.module.kpi.domain;

import com.unique.examine.module.datasource.statistics.domain.StatisticsAggregation;

public record KpiDefinitionSnapshot(
        long kpiId,
        long kpiVersionId,
        int kpiVersionNumber,
        String code,
        String name,
        String description,
        KpiSubjectType subjectType,
        KpiPeriodType periodType,
        StatisticsAggregation aggregation,
        KpiAttainmentDirection direction,
        String warningThreshold,
        KpiSourcePin source
) {
    public KpiDefinitionSnapshot {
        if (kpiId <= 0 || kpiVersionId <= 0 || kpiVersionNumber <= 0
                || subjectType == null || periodType == null
                || aggregation == null || direction == null || source == null) {
            throw new IllegalArgumentException(
                    "KPI definition snapshot is incomplete");
        }
        code = KpiDefinition.code(code);
        name = KpiDefinition.name(name);
        description = KpiDefinition.description(description);
        warningThreshold = KpiAttainment.requireThreshold(warningThreshold);
    }

    public static KpiDefinitionSnapshot from(KpiVersion version) {
        if (version == null) {
            throw new IllegalArgumentException("KPI version is required");
        }
        return new KpiDefinitionSnapshot(version.kpiId(), version.id(),
                version.versionNumber(), version.code(), version.name(),
                version.description(), version.subjectType(),
                version.periodType(), version.aggregation(),
                version.direction(), version.warningThreshold(),
                version.source());
    }
}
