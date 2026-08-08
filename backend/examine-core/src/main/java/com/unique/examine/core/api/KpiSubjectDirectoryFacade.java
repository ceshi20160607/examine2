package com.unique.examine.core.api;

import java.util.List;

/**
 * Platform-owned, tenant-scoped subject directory exposed to the KPI module.
 * The facade deliberately returns immutable snapshots instead of platform
 * entities so KPI publications and calculations never depend on platform
 * table layouts.
 */
public interface KpiSubjectDirectoryFacade {
    SubjectResolution resolve(
            long systemId,
            long tenantId,
            SubjectType subjectType,
            long subjectId
    );

    CurrentMembership currentMembership(
            long systemId,
            long tenantId,
            long memberId
    );

    enum SubjectType {
        MEMBER,
        DEPARTMENT,
        ROLE
    }

    record SubjectResolution(
            SubjectType subjectType,
            long subjectId,
            boolean active,
            String displayName,
            List<Long> activeMemberIds
    ) {
        public SubjectResolution {
            if (subjectType == null || active && subjectId <= 0) {
                throw new IllegalArgumentException(
                        "KPI subject resolution identity is invalid");
            }
            activeMemberIds = canonicalIds(activeMemberIds);
            if (!active) {
                displayName = null;
                activeMemberIds = List.of();
            } else if (displayName == null || displayName.isBlank()) {
                throw new IllegalArgumentException(
                        "Active KPI subject requires a display name");
            } else {
                displayName = displayName.strip();
            }
            if (subjectType != SubjectType.ROLE
                    && !activeMemberIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "Only a KPI role subject exposes a member snapshot");
            }
        }

        public static SubjectResolution missing(
                SubjectType subjectType,
                long subjectId
        ) {
            return new SubjectResolution(
                    subjectType, subjectId, false, null, List.of());
        }

        public static SubjectResolution active(
                SubjectType subjectType,
                long subjectId,
                String displayName,
                List<Long> activeMemberIds
        ) {
            return new SubjectResolution(
                    subjectType, subjectId, true, displayName,
                    activeMemberIds);
        }
    }

    record CurrentMembership(
            boolean memberActive,
            List<Long> departmentIds,
            List<Long> roleIds
    ) {
        public CurrentMembership {
            departmentIds = canonicalIds(departmentIds);
            roleIds = canonicalIds(roleIds);
            if (!memberActive
                    && (!departmentIds.isEmpty() || !roleIds.isEmpty())) {
                throw new IllegalArgumentException(
                        "Inactive KPI member cannot expose memberships");
            }
        }

        public static CurrentMembership missing() {
            return new CurrentMembership(false, List.of(), List.of());
        }

        public static CurrentMembership active(
                List<Long> departmentIds,
                List<Long> roleIds
        ) {
            return new CurrentMembership(true, departmentIds, roleIds);
        }
    }

    private static List<Long> canonicalIds(List<Long> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && value > 0)
                .distinct()
                .sorted()
                .toList();
    }
}
