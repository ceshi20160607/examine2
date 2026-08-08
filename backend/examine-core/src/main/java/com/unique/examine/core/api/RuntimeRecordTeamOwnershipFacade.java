package com.unique.examine.core.api;

import java.util.List;

/**
 * Collaboration-owned record-team bridge used by canonical runtime ownership commands.
 * Calls participate in the caller's transaction.
 */
public interface RuntimeRecordTeamOwnershipFacade {
    List<TeamOwnership> lockExistingTeams(
            long systemId,
            long tenantId,
            List<Long> recordIds,
            long targetMemberId
    );

    void transferOwnership(
            long systemId,
            long tenantId,
            long recordId,
            long currentOwnerMemberId,
            long targetMemberId,
            long actorMemberId
    );

    record TeamOwnership(
            long recordId,
            long ownerMemberId,
            boolean targetMemberPresent,
            boolean targetMemberAdditionAllowed
    ) {
        public TeamOwnership {
            if (recordId <= 0 || ownerMemberId <= 0) {
                throw new IllegalArgumentException("Record-team ownership ids must be positive");
            }
        }
    }
}
