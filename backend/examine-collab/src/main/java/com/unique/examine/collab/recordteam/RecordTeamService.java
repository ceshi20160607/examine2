package com.unique.examine.collab.recordteam;

import java.util.Objects;
import java.util.function.UnaryOperator;

public final class RecordTeamService {
    private final RecordTeamRepository repository;
    private final RecordTeamAuthorizationPolicy authorizationPolicy;

    public RecordTeamService(
            RecordTeamRepository repository,
            RecordTeamAuthorizationPolicy authorizationPolicy
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.authorizationPolicy = Objects.requireNonNull(authorizationPolicy, "authorizationPolicy");
    }

    public RecordTeam team(RecordTeamKey key) {
        Objects.requireNonNull(key, "key");
        return repository.find(key).orElseThrow(() -> RecordTeamException.notFound(
                "RECORD_TEAM_NOT_FOUND",
                "No record team exists for record " + key.recordId()));
    }

    /**
     * Initializes collaboration metadata only. The collaboration boundary currently has no
     * record-existence port, so this command intentionally does not claim to validate the runtime record.
     */
    public RecordTeamInitialization initialize(
            RecordTeamActor actor,
            RecordTeamKey key
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(key, "key");
        var proposed = RecordTeam.initialize(key, actor.memberId());
        authorizationPolicy.authorize(actor, RecordTeamAction.INITIALIZE, proposed);
        return repository.initialize(proposed);
    }

    public RecordTeam addMember(
            RecordTeamActor actor,
            RecordTeamKey key,
            String memberId,
            RecordTeamRole role
    ) {
        return mutate(actor, key, RecordTeamAction.ADD_MEMBER, team -> team.add(memberId, role));
    }

    public RecordTeam changeRole(
            RecordTeamActor actor,
            RecordTeamKey key,
            String memberId,
            RecordTeamRole role
    ) {
        return mutate(actor, key, RecordTeamAction.CHANGE_ROLE, team -> team.changeRole(memberId, role));
    }

    public RecordTeam removeMember(
            RecordTeamActor actor,
            RecordTeamKey key,
            String memberId
    ) {
        return mutate(actor, key, RecordTeamAction.REMOVE_MEMBER, team -> team.remove(memberId));
    }

    public RecordTeam transferOwnership(
            RecordTeamActor actor,
            RecordTeamKey key,
            String targetMemberId
    ) {
        return mutate(
                actor,
                key,
                RecordTeamAction.TRANSFER_OWNERSHIP,
                team -> team.transfer(targetMemberId));
    }

    private RecordTeam mutate(
            RecordTeamActor actor,
            RecordTeamKey key,
            RecordTeamAction action,
            UnaryOperator<RecordTeam> mutation
    ) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(mutation, "mutation");
        return repository.update(key, actor.memberId(), current -> {
            authorizationPolicy.authorize(actor, action, current);
            return mutation.apply(current);
        });
    }
}
