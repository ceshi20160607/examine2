package com.unique.examine.collab.recordteam;

@FunctionalInterface
public interface RecordTeamAuthorizationPolicy {
    void authorize(RecordTeamActor actor, RecordTeamAction action, RecordTeam team);
}
