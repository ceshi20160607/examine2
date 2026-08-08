package com.unique.examine.collab.recordteam;

import java.util.LinkedHashMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class RecordTeam {
    public static final int MAX_MEMBERS = 200;

    private final RecordTeamKey key;
    private final Map<String, RecordTeamMember> members;
    private final long version;

    private RecordTeam(RecordTeamKey key, Map<String, RecordTeamMember> members, long version) {
        this.key = Objects.requireNonNull(key, "key");
        this.members = Map.copyOf(members);
        this.version = version;
        assertSingleOwner(this.members);
    }

    public static RecordTeam initialize(RecordTeamKey key, String ownerMemberId) {
        var owner = new RecordTeamMember(ownerMemberId, RecordTeamRole.OWNER);
        return new RecordTeam(key, Map.of(owner.memberId(), owner), 1);
    }

    public static RecordTeam restore(
            RecordTeamKey key,
            Collection<RecordTeamMember> persistedMembers,
            long version
    ) {
        Objects.requireNonNull(persistedMembers, "persistedMembers");
        if (version < 1) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_PERSISTENCE_INVALID",
                    "A persisted record team must have a positive version");
        }
        if (persistedMembers.isEmpty() || persistedMembers.size() > MAX_MEMBERS) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_PERSISTENCE_INVALID",
                    "Persisted record team member count is outside 1.." + MAX_MEMBERS);
        }

        var restored = new LinkedHashMap<String, RecordTeamMember>();
        for (var member : persistedMembers) {
            Objects.requireNonNull(member, "persisted member");
            if (restored.putIfAbsent(member.memberId(), member) != null) {
                throw RecordTeamException.conflict(
                        "RECORD_TEAM_PERSISTENCE_INVALID",
                        "Persisted record team contains a duplicate member: " + member.memberId());
            }
        }
        return new RecordTeam(key, restored, version);
    }

    public RecordTeamKey key() {
        return key;
    }

    public long version() {
        return version;
    }

    public List<RecordTeamMember> members() {
        return members.values().stream()
                .sorted(java.util.Comparator.comparing(RecordTeamMember::memberId))
                .toList();
    }

    public Optional<RecordTeamMember> member(String memberId) {
        return Optional.ofNullable(members.get(normalizeMemberId(memberId)));
    }

    public RecordTeamMember owner() {
        return members.values().stream()
                .filter(member -> member.role() == RecordTeamRole.OWNER)
                .findFirst()
                .orElseThrow(() -> RecordTeamException.conflict(
                        "RECORD_TEAM_LAST_OWNER_REQUIRED",
                        "The record team must retain an owner"));
    }

    RecordTeam add(String memberId, RecordTeamRole role) {
        var candidate = new RecordTeamMember(memberId, role);
        if (members.containsKey(candidate.memberId())) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_MEMBER_DUPLICATE",
                    "Member already belongs to the record team: " + candidate.memberId());
        }
        if (candidate.role() == RecordTeamRole.OWNER) {
            throw ownerTransferRequired();
        }
        if (members.size() >= MAX_MEMBERS) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_MEMBER_LIMIT_EXCEEDED",
                    "A record team cannot contain more than " + MAX_MEMBERS + " members");
        }

        var changed = mutableMembers();
        changed.put(candidate.memberId(), candidate);
        return next(changed);
    }

    RecordTeam changeRole(String memberId, RecordTeamRole role) {
        if (role == null) {
            throw RecordTeamException.badRequest(
                    "RECORD_TEAM_ROLE_REQUIRED",
                    "role is required");
        }
        var current = requireMember(memberId);
        if (current.role() == role) {
            return this;
        }
        if (current.role() == RecordTeamRole.OWNER) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_LAST_OWNER_REQUIRED",
                    "The last owner cannot be demoted; transfer ownership first");
        }
        if (role == RecordTeamRole.OWNER) {
            throw ownerTransferRequired();
        }

        var changed = mutableMembers();
        changed.put(current.memberId(), new RecordTeamMember(current.memberId(), role));
        return next(changed);
    }

    RecordTeam remove(String memberId) {
        var current = requireMember(memberId);
        if (current.role() == RecordTeamRole.OWNER) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_LAST_OWNER_REQUIRED",
                    "The last owner cannot be removed; transfer ownership first");
        }

        var changed = mutableMembers();
        changed.remove(current.memberId());
        return next(changed);
    }

    RecordTeam transfer(String targetMemberId) {
        var targetId = normalizeMemberId(targetMemberId);
        var currentOwner = owner();
        if (currentOwner.memberId().equals(targetId)) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_TRANSFER_TARGET_ALREADY_OWNER",
                    "The transfer target is already the record owner");
        }
        if (!members.containsKey(targetId) && members.size() >= MAX_MEMBERS) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_MEMBER_LIMIT_EXCEEDED",
                    "A record team cannot contain more than " + MAX_MEMBERS + " members");
        }

        var changed = mutableMembers();
        changed.put(
                currentOwner.memberId(),
                new RecordTeamMember(currentOwner.memberId(), RecordTeamRole.COLLABORATOR));
        changed.put(targetId, new RecordTeamMember(targetId, RecordTeamRole.OWNER));
        return next(changed);
    }

    private RecordTeamMember requireMember(String memberId) {
        var normalized = normalizeMemberId(memberId);
        var member = members.get(normalized);
        if (member == null) {
            throw RecordTeamException.notFound(
                    "RECORD_TEAM_MEMBER_NOT_FOUND",
                    "Member does not belong to the record team: " + normalized);
        }
        return member;
    }

    private LinkedHashMap<String, RecordTeamMember> mutableMembers() {
        return new LinkedHashMap<>(members);
    }

    private RecordTeam next(Map<String, RecordTeamMember> changed) {
        return new RecordTeam(key, changed, version + 1);
    }

    private static String normalizeMemberId(String memberId) {
        return new RecordTeamMember(memberId, RecordTeamRole.FOLLOWER).memberId();
    }

    private static void assertSingleOwner(Map<String, RecordTeamMember> members) {
        var ownerCount = members.values().stream()
                .filter(member -> member.role() == RecordTeamRole.OWNER)
                .count();
        if (ownerCount != 1) {
            throw RecordTeamException.conflict(
                    "RECORD_TEAM_OWNER_INVARIANT_VIOLATION",
                    "A record team must contain exactly one owner");
        }
    }

    private static RecordTeamException ownerTransferRequired() {
        return RecordTeamException.conflict(
                "RECORD_TEAM_OWNER_TRANSFER_REQUIRED",
                "Use transfer ownership to assign the OWNER role");
    }
}
