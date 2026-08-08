package com.unique.examine.collab.recordteam;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface RecordTeamRepository {
    void create(RecordTeam team);

    RecordTeamInitialization initialize(RecordTeam team);

    Optional<RecordTeam> find(RecordTeamKey key);

    List<RecordTeam> lockAll(List<RecordTeamKey> keys);

    RecordTeam update(
            RecordTeamKey key,
            String actorMemberId,
            UnaryOperator<RecordTeam> mutation
    );
}
