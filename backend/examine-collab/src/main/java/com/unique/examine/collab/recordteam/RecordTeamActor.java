package com.unique.examine.collab.recordteam;

import java.util.Objects;
import java.util.Set;

public record RecordTeamActor(String memberId, Set<RecordTeamCapability> capabilities) {
    public RecordTeamActor {
        if (memberId == null || memberId.isBlank()) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_ACTOR_REQUIRED",
                    "actor memberId is required");
        }
        memberId = memberId.trim();
        capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
    }

    public boolean has(RecordTeamCapability capability) {
        return capabilities.contains(capability);
    }
}
