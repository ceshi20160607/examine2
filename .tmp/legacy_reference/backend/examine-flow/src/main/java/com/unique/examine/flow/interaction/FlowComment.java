package com.unique.examine.flow.interaction;

import com.unique.examine.flow.domain.ApprovalDomainException;

import java.time.Instant;
import java.util.Objects;

import static com.unique.examine.flow.domain.ApprovalDomainException.Code.COMMENT_BODY_REQUIRED;

public record FlowComment(
        long id,
        long instanceId,
        long authorId,
        String body,
        Instant createdAt
) {
    public FlowComment {
        if (id <= 0 || instanceId <= 0 || authorId <= 0) {
            throw new IllegalArgumentException("Flow comment ids must be positive");
        }
        body = body == null ? "" : body.strip();
        var bodyLength = body.codePointCount(0, body.length());
        if (bodyLength < 1 || bodyLength > 2000) {
            throw new ApprovalDomainException(
                    COMMENT_BODY_REQUIRED,
                    "A comment body of 1 to 2000 characters is required"
            );
        }
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
