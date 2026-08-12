package com.unique.examine.module.kpi.domain;

import java.time.Instant;

public record KpiTarget(
        long id,
        long systemId,
        long tenantId,
        long kpiId,
        long kpiVersionId,
        int kpiVersionNumber,
        KpiSubjectType subjectType,
        long subjectId,
        String subjectDisplayName,
        KpiPeriod period,
        String targetValue,
        long createdByMemberId,
        long updatedByMemberId,
        Instant createdAt,
        Instant updatedAt,
        long version
) {
    public KpiTarget {
        if (id <= 0 || systemId <= 0 || tenantId <= 0 || kpiId <= 0
                || kpiVersionId <= 0 || kpiVersionNumber <= 0
                || subjectType == null || subjectId <= 0 || period == null
                || createdByMemberId <= 0 || updatedByMemberId <= 0
                || createdAt == null || updatedAt == null
                || updatedAt.isBefore(createdAt) || version <= 0) {
            throw invalid("KPI target state is incomplete");
        }
        subjectDisplayName = displayName(subjectDisplayName);
        targetValue = KpiDecimal.requireNonNegative(targetValue);
    }

    public static KpiTarget create(
            long id,
            KpiActor actor,
            KpiVersion definition,
            long subjectId,
            String subjectDisplayName,
            KpiPeriod period,
            String targetValue,
            Instant now
    ) {
        if (actor == null || definition == null
                || definition.systemId() != actor.systemId()
                || definition.tenantId() != actor.tenantId()
                || period == null
                || period.type() != definition.periodType()) {
            throw invalid("KPI target does not match its definition");
        }
        return new KpiTarget(id, actor.systemId(), actor.tenantId(),
                definition.kpiId(), definition.id(),
                definition.versionNumber(), definition.subjectType(),
                subjectId, subjectDisplayName, period, targetValue,
                actor.memberId(), actor.memberId(), now, now, 1);
    }

    public KpiTarget reviseValue(
            String nextTargetValue,
            long updatedBy,
            Instant now
    ) {
        if (updatedBy <= 0 || now == null || now.isBefore(updatedAt)) {
            throw invalid("KPI target revision is invalid");
        }
        return new KpiTarget(id, systemId, tenantId, kpiId, kpiVersionId,
                kpiVersionNumber, subjectType, subjectId,
                subjectDisplayName, period, nextTargetValue,
                createdByMemberId, updatedBy, createdAt, now, version + 1);
    }

    public boolean sameCreation(
            KpiVersion definition,
            long candidateSubjectId,
            KpiPeriod candidatePeriod,
            String candidateValue
    ) {
        return definition != null && kpiId == definition.kpiId()
                && kpiVersionId == definition.id()
                && kpiVersionNumber == definition.versionNumber()
                && subjectType == definition.subjectType()
                && subjectId == candidateSubjectId
                && period.equals(candidatePeriod)
                && targetValue.equals(
                KpiDecimal.requireNonNegative(candidateValue));
    }

    private static String displayName(String value) {
        if (value == null || value.isBlank()
                || value.codePointCount(0, value.length()) > 200) {
            throw invalid("KPI subject display name is invalid");
        }
        return value.strip();
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_TARGET_INVALID", message);
    }
}
