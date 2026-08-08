package com.unique.examine.flow.metrics;

import com.unique.examine.flow.domain.ApprovalInstance;

import java.time.Instant;

public record FlowMetricInstance(
        ApprovalInstance.Status status,
        Instant startedAt,
        Instant completedAt
) {
    public FlowMetricInstance {
        if (status == null || startedAt == null
                || status == ApprovalInstance.Status.PENDING && completedAt != null
                || status != ApprovalInstance.Status.PENDING && completedAt == null
                || completedAt != null && completedAt.isBefore(startedAt)) {
            throw new IllegalArgumentException("Flow metric instance facts are invalid");
        }
    }
}
