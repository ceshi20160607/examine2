package com.unique.examine.plat.manage.service;

import java.util.HashSet;
import java.util.function.LongFunction;

final class OrganizationReportingPolicy {
    private OrganizationReportingPolicy() {
    }

    static void requireDepartmentLeaderEligible(
            boolean departmentActive,
            boolean activeTenantMember,
            boolean departmentMember
    ) {
        if (!departmentActive) {
            throw SystemAdminMutationSupport.invalidState(
                    "Only active departments can have a leader"
            );
        }
        if (!activeTenantMember) {
            throw SystemAdminMutationSupport.invalidState(
                    "leaderMemberId must reference an active member of the current tenant"
            );
        }
        if (!departmentMember) {
            throw SystemAdminMutationSupport.invalidState(
                    "The department leader must be an active member of that department"
            );
        }
    }

    static void requireManagerEligible(
            boolean subjectActiveTenantMember,
            boolean managerActiveTenantMember
    ) {
        if (!subjectActiveTenantMember) {
            throw SystemAdminMutationSupport.invalidState(
                    "memberId must reference an active member of the current tenant"
            );
        }
        if (!managerActiveTenantMember) {
            throw SystemAdminMutationSupport.invalidState(
                    "managerMemberId must reference an active member of the current tenant"
            );
        }
    }

    static void requireAcyclic(
            long memberId,
            long managerMemberId,
            LongFunction<Long> managerOf
    ) {
        if (memberId == managerMemberId) {
            throw SystemAdminMutationSupport.invalidState("A member cannot manage themself");
        }
        var visited = new HashSet<Long>();
        var cursor = managerMemberId;
        while (visited.add(cursor)) {
            if (cursor == memberId) {
                throw SystemAdminMutationSupport.invalidState(
                        "The direct-manager assignment would create a reporting cycle"
                );
            }
            var next = managerOf.apply(cursor);
            if (next == null) {
                return;
            }
            cursor = next;
        }
        throw SystemAdminMutationSupport.invalidState(
                "The existing reporting path contains a cycle"
        );
    }
}
