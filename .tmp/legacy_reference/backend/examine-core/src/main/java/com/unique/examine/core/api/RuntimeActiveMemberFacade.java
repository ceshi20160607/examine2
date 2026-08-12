package com.unique.examine.core.api;

import java.util.Optional;

/**
 * Platform-owned active tenant membership lookup used by runtime ownership commands.
 * Implementations must hold the resolved membership stable for the caller's transaction.
 */
public interface RuntimeActiveMemberFacade {
    Optional<ActiveMember> lockActiveMember(long systemId, long tenantId, long memberId);

    record ActiveMember(long memberId, Long primaryDepartmentId) {
        public ActiveMember {
            if (memberId <= 0) {
                throw new IllegalArgumentException("Active member id must be positive");
            }
            if (primaryDepartmentId != null && primaryDepartmentId <= 0) {
                throw new IllegalArgumentException("Primary department id must be positive");
            }
        }
    }
}
