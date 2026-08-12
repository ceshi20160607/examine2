package com.unique.examine.collab.recordteam;

import java.util.Objects;

public record RecordTeamInitialization(RecordTeam team, boolean created) {
    public RecordTeamInitialization {
        Objects.requireNonNull(team, "team");
    }

    static RecordTeamInitialization created(RecordTeam team) {
        return new RecordTeamInitialization(team, true);
    }

    static RecordTeamInitialization existing(RecordTeam team) {
        return new RecordTeamInitialization(team, false);
    }
}
