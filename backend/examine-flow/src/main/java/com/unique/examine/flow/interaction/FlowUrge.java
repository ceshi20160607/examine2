package com.unique.examine.flow.interaction;

import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;

import java.time.Instant;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.INSTANCE_STATE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.REQUESTER_FORBIDDEN;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.URGE_MESSAGE_INVALID;

public record FlowUrge(
        long id,
        long instanceId,
        long actorId,
        long recipientId,
        String message,
        Instant createdAt
) {
    public FlowUrge {
        if (id <= 0 || instanceId <= 0 || actorId <= 0 || recipientId <= 0) {
            throw new IllegalArgumentException("Flow urge ids must be positive");
        }
        message = message == null ? "" : message.strip();
        if (message.codePointCount(0, message.length()) > 500) {
            throw new ApprovalDomainException(
                    URGE_MESSAGE_INVALID,
                    "An urge message may contain at most 500 characters"
            );
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static FlowUrge create(
            long id,
            ApprovalInstance instance,
            long actorId,
            String message,
            Instant createdAt
    ) {
        Objects.requireNonNull(instance, "instance");
        if (actorId != instance.requesterId()) {
            throw new ApprovalDomainException(
                    REQUESTER_FORBIDDEN,
                    "Only the instance requester can urge"
            );
        }
        if (instance.status() != ApprovalInstance.Status.PENDING) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "Only a pending instance can be urged"
            );
        }
        if (instance.claimState() != ApprovalInstance.ClaimState.CLAIMED) {
            throw new ApprovalDomainException(
                    INSTANCE_STATE_INVALID,
                    "An open task cannot be urged until it is claimed"
            );
        }
        return new FlowUrge(
                id,
                instance.id(),
                actorId,
                instance.approverId(),
                message,
                createdAt
        );
    }
}
