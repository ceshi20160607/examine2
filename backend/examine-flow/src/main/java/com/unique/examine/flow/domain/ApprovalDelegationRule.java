package com.unique.examine.flow.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.DELEGATION_RULE_INVALID;

/**
 * Immutable, tenant-owned one-level approval delegation authority.
 */
public record ApprovalDelegationRule(
        long id,
        long tenantId,
        long delegatorMemberId,
        long delegateMemberId,
        Instant startsAt,
        Instant endsAt,
        Long definitionId,
        Status status,
        long createdByMemberId,
        Instant createdAt,
        Long revokedByMemberId,
        Instant revokedAt
) {
    public static final Duration MAX_DURATION = Duration.ofDays(180);

    public ApprovalDelegationRule {
        if (id <= 0 || tenantId <= 0) {
            throw invalid("Delegation rule and tenant ids must be positive");
        }
        if (delegatorMemberId <= 0
                || delegateMemberId <= 0
                || delegatorMemberId == delegateMemberId) {
            throw invalid("Delegator and delegate must be distinct positive member ids");
        }
        Objects.requireNonNull(startsAt, "startsAt");
        Objects.requireNonNull(endsAt, "endsAt");
        if (!startsAt.isBefore(endsAt)
                || Duration.between(startsAt, endsAt).compareTo(MAX_DURATION) > 0) {
            throw invalid("Delegation duration must be positive and no longer than 180 days");
        }
        if (definitionId != null && definitionId <= 0) {
            throw invalid("Delegation definition scope must be positive");
        }
        Objects.requireNonNull(status, "status");
        if (createdByMemberId <= 0) {
            throw invalid("Delegation creator must be a positive member id");
        }
        Objects.requireNonNull(createdAt, "createdAt");
        if (status == Status.REVOKED) {
            if (revokedByMemberId == null
                    || revokedByMemberId <= 0
                    || revokedAt == null
                    || revokedAt.isBefore(createdAt)) {
                throw invalid("Revoked delegation requires a valid immutable revoke audit");
            }
        } else if (revokedByMemberId != null || revokedAt != null) {
            throw invalid("Only a revoked delegation may contain revoke audit fields");
        }
    }

    public static ApprovalDelegationRule create(
            long id,
            long tenantId,
            long delegatorMemberId,
            long delegateMemberId,
            Instant startsAt,
            Instant endsAt,
            Long definitionId,
            long createdByMemberId,
            Instant createdAt
    ) {
        return new ApprovalDelegationRule(
                id,
                tenantId,
                delegatorMemberId,
                delegateMemberId,
                startsAt,
                endsAt,
                definitionId,
                temporalStatus(startsAt, endsAt, createdAt),
                createdByMemberId,
                createdAt,
                null,
                null
        );
    }

    public ApprovalDelegationRule at(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status == Status.REVOKED) {
            return this;
        }
        var effective = temporalStatus(startsAt, endsAt, now);
        return effective == status
                ? this
                : new ApprovalDelegationRule(
                        id, tenantId, delegatorMemberId, delegateMemberId,
                        startsAt, endsAt, definitionId, effective,
                        createdByMemberId, createdAt, null, null
                );
    }

    public boolean isEffectiveAt(Instant instant, long targetDefinitionId) {
        Objects.requireNonNull(instant, "instant");
        return status != Status.REVOKED
                && !instant.isBefore(startsAt)
                && instant.isBefore(endsAt)
                && (definitionId == null || definitionId == targetDefinitionId);
    }

    public boolean overlaps(
            Instant candidateStartsAt,
            Instant candidateEndsAt,
            Long candidateDefinitionId
    ) {
        return status != Status.REVOKED
                && startsAt.isBefore(candidateEndsAt)
                && candidateStartsAt.isBefore(endsAt)
                && scopesIntersect(definitionId, candidateDefinitionId);
    }

    public ApprovalDelegationRule revoke(long actorMemberId, Instant occurredAt) {
        if (actorMemberId <= 0) {
            throw invalid("Delegation revoker must be a positive member id");
        }
        Objects.requireNonNull(occurredAt, "occurredAt");
        if (status == Status.REVOKED) {
            return this;
        }
        if (at(occurredAt).status() == Status.EXPIRED) {
            throw new ApprovalDomainException(
                    ApprovalDomainException.Code.DELEGATION_INACTIVE,
                    "An expired delegation rule cannot be revoked"
            );
        }
        return new ApprovalDelegationRule(
                id, tenantId, delegatorMemberId, delegateMemberId,
                startsAt, endsAt, definitionId, Status.REVOKED,
                createdByMemberId, createdAt, actorMemberId, occurredAt
        );
    }

    public static boolean scopesIntersect(Long first, Long second) {
        return first == null || second == null || Objects.equals(first, second);
    }

    private static Status temporalStatus(
            Instant startsAt,
            Instant endsAt,
            Instant now
    ) {
        if (now.isBefore(startsAt)) {
            return Status.SCHEDULED;
        }
        if (!now.isBefore(endsAt)) {
            return Status.EXPIRED;
        }
        return Status.ACTIVE;
    }

    private static ApprovalDomainException invalid(String message) {
        return new ApprovalDomainException(DELEGATION_RULE_INVALID, message);
    }

    public enum Status {
        SCHEDULED,
        ACTIVE,
        EXPIRED,
        REVOKED
    }
}
