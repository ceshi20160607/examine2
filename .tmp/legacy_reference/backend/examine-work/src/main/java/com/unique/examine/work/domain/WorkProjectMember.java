package com.unique.examine.work.domain;

import java.time.Instant;
import java.util.Objects;

public record WorkProjectMember(
        long systemId,
        long tenantId,
        long projectId,
        long memberId,
        Role role,
        Status status,
        Instant joinedAt,
        Instant updatedAt,
        long version
) {
    public WorkProjectMember {
        if (systemId <= 0 || tenantId <= 0 || projectId <= 0 || memberId <= 0) {
            throw invalid("WORK_PROJECT_MEMBER_INVALID",
                    "Project member identity and scope must be positive");
        }
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(joinedAt, "joinedAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (updatedAt.isBefore(joinedAt) || version <= 0) {
            throw invalid("WORK_PROJECT_MEMBER_INVALID",
                    "Project member timestamps and version are invalid");
        }
    }

    public static WorkProjectMember owner(
            long systemId,
            long tenantId,
            long projectId,
            long memberId,
            Instant joinedAt
    ) {
        return new WorkProjectMember(
                systemId, tenantId, projectId, memberId,
                Role.OWNER, Status.ACTIVE, joinedAt, joinedAt, 1);
    }

    public static WorkProjectMember member(
            long systemId,
            long tenantId,
            long projectId,
            long memberId,
            Instant joinedAt
    ) {
        return new WorkProjectMember(
                systemId, tenantId, projectId, memberId,
                Role.MEMBER, Status.ACTIVE, joinedAt, joinedAt, 1);
    }

    public WorkProjectMember promote(Instant now) {
        return transition(Role.OWNER, Status.ACTIVE, now);
    }

    public WorkProjectMember demote(Instant now) {
        return transition(Role.MEMBER, Status.ACTIVE, now);
    }

    public WorkProjectMember changeRole(Role nextRole, Instant now) {
        if (status != Status.ACTIVE) {
            throw invalid("WORK_PROJECT_MEMBER_STATE_INVALID",
                    "Only an active project member can change role");
        }
        return transition(Objects.requireNonNull(nextRole, "nextRole"),
                Status.ACTIVE, now);
    }

    public WorkProjectMember remove(Instant now) {
        return transition(role, Status.REMOVED, now);
    }

    public WorkProjectMember reactivate(Role nextRole, Instant now) {
        if (status != Status.REMOVED) {
            throw invalid("WORK_PROJECT_MEMBER_STATE_INVALID",
                    "Only a removed project member can be reactivated");
        }
        return transition(Objects.requireNonNull(nextRole, "nextRole"),
                Status.ACTIVE, now);
    }

    public boolean activeOwner() {
        return status == Status.ACTIVE && role == Role.OWNER;
    }

    private WorkProjectMember transition(
            Role nextRole, Status nextStatus, Instant now
    ) {
        Objects.requireNonNull(now, "now");
        if (now.isBefore(updatedAt)
                || role == nextRole && status == nextStatus) {
            throw invalid("WORK_PROJECT_MEMBER_STATE_INVALID",
                    "Project member transition is invalid");
        }
        return new WorkProjectMember(
                systemId, tenantId, projectId, memberId,
                nextRole, nextStatus, joinedAt, now, version + 1);
    }

    private static WorkDomainException invalid(String code, String message) {
        return new WorkDomainException(code, message);
    }

    public enum Role {
        OWNER,
        MEMBER
    }

    public enum Status {
        ACTIVE,
        REMOVED
    }
}
