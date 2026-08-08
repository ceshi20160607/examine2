package com.unique.examine.collab.recordteam;

import com.unique.examine.core.api.RuntimeRecordTeamOwnershipFacade;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
public class RuntimeRecordTeamOwnershipBridge implements RuntimeRecordTeamOwnershipFacade {
    private final RecordTeamRepository repository;

    public RuntimeRecordTeamOwnershipBridge(RecordTeamRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<TeamOwnership> lockExistingTeams(
            long systemId,
            long tenantId,
            List<Long> recordIds,
            long targetMemberId
    ) {
        requirePositive(systemId, "systemId");
        requirePositive(tenantId, "tenantId");
        requirePositive(targetMemberId, "targetMemberId");
        Objects.requireNonNull(recordIds, "recordIds");
        var orderedIds = recordIds.stream().map(recordId -> {
            requirePositive(recordId, "recordId");
            return recordId;
        }).distinct().sorted().toList();
        if (orderedIds.size() != recordIds.size()) {
            throw new IllegalArgumentException("Record-team lock ids must be unique");
        }
        var teams = repository.lockAll(orderedIds.stream()
                .map(recordId -> key(systemId, tenantId, recordId))
                .toList());
        return teams.stream().map(team -> new TeamOwnership(
                Long.parseLong(team.key().recordId()),
                Long.parseLong(team.owner().memberId()),
                team.member(Long.toString(targetMemberId)).isPresent(),
                team.member(Long.toString(targetMemberId)).isPresent()
                        || team.members().size() < RecordTeam.MAX_MEMBERS))
                .toList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void transferOwnership(
            long systemId,
            long tenantId,
            long recordId,
            long currentOwnerMemberId,
            long targetMemberId,
            long actorMemberId
    ) {
        requirePositive(systemId, "systemId");
        requirePositive(tenantId, "tenantId");
        requirePositive(recordId, "recordId");
        requirePositive(currentOwnerMemberId, "currentOwnerMemberId");
        requirePositive(targetMemberId, "targetMemberId");
        requirePositive(actorMemberId, "actorMemberId");
        var key = key(systemId, tenantId, recordId);
        var existingTeam = repository.find(key).orElseGet(() -> repository.initialize(
                RecordTeam.initialize(key, Long.toString(currentOwnerMemberId))).team());
        requireOwner(existingTeam, currentOwnerMemberId);
        repository.update(key, Long.toString(actorMemberId), team -> {
            requireOwner(team, currentOwnerMemberId);
            return team.transfer(Long.toString(targetMemberId));
        });
    }

    private static RecordTeamKey key(long systemId, long tenantId, long recordId) {
        return new RecordTeamKey(
                Long.toString(systemId),
                Long.toString(tenantId),
                Long.toString(recordId));
    }

    private static void requireOwner(RecordTeam team, long expectedOwnerMemberId) {
        if (!team.owner().memberId().equals(Long.toString(expectedOwnerMemberId))) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_OWNER_INVARIANT_VIOLATION",
                    "The record-team owner differs from the runtime record owner");
        }
    }

    private static void requirePositive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
