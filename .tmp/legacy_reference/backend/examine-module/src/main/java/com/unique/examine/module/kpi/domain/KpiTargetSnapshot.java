package com.unique.examine.module.kpi.domain;

public record KpiTargetSnapshot(
        long targetId,
        long targetVersion,
        long kpiId,
        long kpiVersionId,
        int kpiVersionNumber,
        KpiSubjectType subjectType,
        long subjectId,
        String subjectDisplayName,
        KpiPeriod period,
        String targetValue
) {
    public KpiTargetSnapshot {
        if (targetId <= 0 || targetVersion <= 0 || kpiId <= 0
                || kpiVersionId <= 0 || kpiVersionNumber <= 0
                || subjectType == null || subjectId <= 0 || period == null
                || subjectDisplayName == null || subjectDisplayName.isBlank()) {
            throw new IllegalArgumentException(
                    "KPI target snapshot is incomplete");
        }
        subjectDisplayName = subjectDisplayName.strip();
        targetValue = KpiDecimal.requireNonNegative(targetValue);
    }

    public static KpiTargetSnapshot from(KpiTarget target) {
        if (target == null) {
            throw new IllegalArgumentException("KPI target is required");
        }
        return new KpiTargetSnapshot(target.id(), target.version(),
                target.kpiId(), target.kpiVersionId(),
                target.kpiVersionNumber(), target.subjectType(),
                target.subjectId(), target.subjectDisplayName(),
                target.period(), target.targetValue());
    }
}
