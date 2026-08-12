package com.unique.examine.module.kpi.domain;

import java.util.List;

public record KpiSubjectSnapshot(
        KpiSubjectType type,
        long id,
        String displayName,
        List<Long> roleMemberIds
) {
    public KpiSubjectSnapshot {
        if (type == null || id <= 0 || displayName == null
                || displayName.isBlank()
                || displayName.codePointCount(0, displayName.length()) > 200
                || roleMemberIds == null) {
            throw invalid("KPI subject snapshot is invalid");
        }
        displayName = displayName.strip();
        roleMemberIds = roleMemberIds.stream().sorted().distinct().toList();
        if (roleMemberIds.stream().anyMatch(memberId -> memberId <= 0)
                || type != KpiSubjectType.ROLE
                && !roleMemberIds.isEmpty()) {
            throw invalid("KPI subject member snapshot is invalid");
        }
    }

    private static KpiException invalid(String message) {
        return new KpiException("KPI_SUBJECT_INVALID", message);
    }
}
