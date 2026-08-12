package com.unique.examine.file.domain;

import com.unique.examine.core.api.AggregateRef;

import java.time.Instant;

public record FileReference(AggregateRef target, long createdByMemberId, Instant createdAt) {
    public FileReference {
        if (target == null || createdByMemberId <= 0 || createdAt == null) {
            throw new IllegalArgumentException("File reference is incomplete");
        }
    }
}
