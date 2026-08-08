package com.unique.examine.collab.recordteam;

import java.util.Objects;

public final class CapabilityRecordTeamAuthorizationPolicy implements RecordTeamAuthorizationPolicy {
    @Override
    public void authorize(RecordTeamActor actor, RecordTeamAction action, RecordTeam team) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(team, "team");

        var required = action == RecordTeamAction.TRANSFER_OWNERSHIP
                ? RecordTeamCapability.TRANSFER_OWNERSHIP
                : RecordTeamCapability.MANAGE_MEMBERS;
        if (!actor.has(required)) {
            throw RecordTeamException.forbidden(
                    "Actor " + actor.memberId() + " lacks capability " + required);
        }
    }
}
