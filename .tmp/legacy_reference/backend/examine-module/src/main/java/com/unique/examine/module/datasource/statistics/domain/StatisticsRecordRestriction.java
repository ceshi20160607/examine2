package com.unique.examine.module.datasource.statistics.domain;

import java.util.List;

/**
 * Trusted internal ownership predicate for one exact native aggregate.
 * This type is intentionally absent from every public statistics request DTO.
 */
public record StatisticsRecordRestriction(
        Kind kind,
        List<Long> ownerIds
) {
    public static final int MAX_OWNER_IDS = 1_000;

    public StatisticsRecordRestriction {
        if (kind == null || ownerIds == null
                || ownerIds.size() > MAX_OWNER_IDS
                || ownerIds.stream().anyMatch(
                id -> id == null || id <= 0)) {
            throw invalid();
        }
        ownerIds = ownerIds.stream().distinct().sorted().toList();
        if (kind != Kind.OWNER_MEMBERS && ownerIds.size() != 1) {
            throw invalid();
        }
    }

    public static StatisticsRecordRestriction ownerMember(long memberId) {
        return new StatisticsRecordRestriction(
                Kind.OWNER_MEMBER, List.of(memberId));
    }

    public static StatisticsRecordRestriction ownerDepartment(
            long departmentId
    ) {
        return new StatisticsRecordRestriction(
                Kind.OWNER_DEPARTMENT, List.of(departmentId));
    }

    public static StatisticsRecordRestriction ownerMembers(
            List<Long> memberIds
    ) {
        return new StatisticsRecordRestriction(
                Kind.OWNER_MEMBERS, memberIds);
    }

    public boolean matchesNoRecords() {
        return kind == Kind.OWNER_MEMBERS && ownerIds.isEmpty();
    }

    public enum Kind {
        OWNER_MEMBER,
        OWNER_DEPARTMENT,
        OWNER_MEMBERS
    }

    private static StatisticsException invalid() {
        return new StatisticsException(
                "STATISTICS_RESTRICTION_INVALID",
                "Internal statistics ownership restriction is invalid");
    }
}
