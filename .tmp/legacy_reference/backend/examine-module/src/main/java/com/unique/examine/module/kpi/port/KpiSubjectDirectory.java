package com.unique.examine.module.kpi.port;

import com.unique.examine.module.kpi.domain.KpiSubjectType;

import java.util.List;
import java.util.Optional;

/** Narrow platform-directory view; KPI never reads platform tables directly. */
public interface KpiSubjectDirectory {
    Optional<SubjectResolution> resolve(
            long systemId,
            long tenantId,
            KpiSubjectType subjectType,
            long subjectId);

    CurrentMembership currentMembership(
            long systemId,
            long tenantId,
            long memberId);

    record SubjectResolution(
            KpiSubjectType subjectType,
            long subjectId,
            boolean active,
            String displayName,
            List<Long> activeMemberIds
    ) {
        public SubjectResolution {
            if (subjectType == null || subjectId <= 0
                    || displayName == null || displayName.isBlank()
                    || activeMemberIds == null) {
                throw new IllegalArgumentException(
                        "KPI subject resolution is invalid");
            }
            displayName = displayName.strip();
            activeMemberIds = activeMemberIds.stream()
                    .sorted().distinct().toList();
            if (activeMemberIds.stream().anyMatch(memberId -> memberId <= 0)
                    || subjectType != KpiSubjectType.ROLE
                    && !activeMemberIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "KPI subject member resolution is invalid");
            }
        }
    }

    record CurrentMembership(
            boolean memberActive,
            List<Long> departmentIds,
            List<Long> roleIds
    ) {
        public CurrentMembership {
            if (departmentIds == null || roleIds == null) {
                throw new IllegalArgumentException(
                        "KPI current membership is invalid");
            }
            departmentIds = normalize(departmentIds);
            roleIds = normalize(roleIds);
        }

        private static List<Long> normalize(List<Long> values) {
            var result = values.stream().sorted().distinct().toList();
            if (result.stream().anyMatch(id -> id <= 0)) {
                throw new IllegalArgumentException(
                        "KPI membership identifiers must be positive");
            }
            return result;
        }
    }
}
