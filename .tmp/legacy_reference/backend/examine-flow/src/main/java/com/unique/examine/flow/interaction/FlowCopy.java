package com.unique.examine.flow.interaction;

import com.unique.examine.flow.domain.ApprovalDomainException;
import com.unique.examine.flow.domain.ApprovalInstance;

import java.time.Instant;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_MESSAGE_INVALID;
import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COPY_TARGET_INVALID;

public record FlowCopy(
        long id,
        long instanceId,
        long actorId,
        long recipientId,
        String message,
        Instant createdAt
) {
    public FlowCopy {
        if (id <= 0 || instanceId <= 0 || actorId <= 0 || recipientId <= 0) {
            throw new IllegalArgumentException("Flow copy ids must be positive");
        }
        if (actorId == recipientId) {
            throw new ApprovalDomainException(
                    COPY_TARGET_INVALID,
                    "Flow copy recipient must differ from the actor"
            );
        }
        message = message == null ? "" : message.strip();
        if (message.codePointCount(0, message.length()) > 500) {
            throw new ApprovalDomainException(
                    COPY_MESSAGE_INVALID,
                    "A Flow copy message may contain at most 500 characters"
            );
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }

    public static FlowCopy create(
            long id,
            ApprovalInstance instance,
            long actorId,
            long recipientId,
            String message,
            Instant createdAt
    ) {
        Objects.requireNonNull(instance, "instance");
        return new FlowCopy(
                id,
                instance.id(),
                actorId,
                recipientId,
                message,
                createdAt
        );
    }
}
