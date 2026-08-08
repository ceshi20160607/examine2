package com.unique.examine.core.api;

import java.util.List;

/**
 * Platform-owned tenant directory projection for runtime approval assignment.
 * Implementations resolve and hold source membership inside the caller's
 * transaction; callers persist the returned member IDs as their own snapshot.
 */
public interface RuntimeApproverDirectoryFacade {
    Resolution resolveRoleMembers(long systemId, long tenantId, long roleId);

    Resolution resolveDepartmentMembers(long systemId, long tenantId, long departmentId);

    /**
     * Resolves the current active leader of an active department. The default
     * keeps existing directory implementations source compatible until they
     * opt into organization-leader resolution.
     */
    default Resolution resolveDepartmentLeader(
            long systemId,
            long tenantId,
            long departmentId
    ) {
        return Resolution.missing();
    }

    /**
     * Resolves the current active direct manager of the requester. The
     * requester is supplied by Flow's start context and is not part of the
     * persisted approver-source selector.
     */
    default Resolution resolveRequesterManager(
            long systemId,
            long tenantId,
            long requesterMemberId
    ) {
        return Resolution.missing();
    }

    record Resolution(boolean sourceActive, List<Long> memberIds) {
        public Resolution {
            memberIds = memberIds == null ? List.of() : memberIds.stream()
                    .filter(memberId -> memberId != null && memberId > 0)
                    .distinct()
                    .sorted()
                    .toList();
            if (!sourceActive && !memberIds.isEmpty()) {
                throw new IllegalArgumentException(
                        "An inactive approver source cannot expose members"
                );
            }
        }

        public static Resolution missing() {
            return new Resolution(false, List.of());
        }

        public static Resolution active(List<Long> memberIds) {
            return new Resolution(true, memberIds);
        }
    }
}
