package com.unique.examine.collab.recordteam;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;

public final class InMemoryRecordTeamRepository implements RecordTeamRepository {
    private final ConcurrentHashMap<RecordTeamKey, RecordTeam> teams = new ConcurrentHashMap<>();

    @Override
    public void create(RecordTeam team) {
        Objects.requireNonNull(team, "team");
        if (teams.putIfAbsent(team.key(), team) != null) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_DUPLICATE",
                    "A record team already exists for record " + team.key().recordId());
        }
    }

    @Override
    public RecordTeamInitialization initialize(RecordTeam team) {
        Objects.requireNonNull(team, "team");
        var existing = teams.putIfAbsent(team.key(), team);
        return existing == null
                ? RecordTeamInitialization.created(team)
                : RecordTeamInitialization.existing(existing);
    }

    @Override
    public Optional<RecordTeam> find(RecordTeamKey key) {
        return Optional.ofNullable(teams.get(Objects.requireNonNull(key, "key")));
    }

    @Override
    public List<RecordTeam> lockAll(List<RecordTeamKey> keys) {
        Objects.requireNonNull(keys, "keys");
        return keys.stream()
                .map(teams::get)
                .filter(Objects::nonNull)
                .toList();
    }

    @Override
    public RecordTeam update(
            RecordTeamKey key,
            String actorMemberId,
            UnaryOperator<RecordTeam> mutation
    ) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(actorMemberId, "actorMemberId");
        Objects.requireNonNull(mutation, "mutation");
        return teams.compute(key, (ignored, current) -> {
            if (current == null) {
                throw RecordTeamException.notFound(
                        "RECORD_TEAM_NOT_FOUND",
                        "No record team exists for record " + key.recordId());
            }
            return Objects.requireNonNull(mutation.apply(current), "mutation result");
        });
    }
}
