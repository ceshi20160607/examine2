package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

public record FlowPeriodicScheduleState(
        long definitionId,
        int definitionVersion,
        long requesterId,
        int intervalMinutes,
        Instant startAt,
        Instant nextFireAt,
        Instant lastScheduledAt,
        Long lastInstanceId,
        Status status,
        String pauseReason,
        Instant updatedAt
) {
    public FlowPeriodicScheduleState {
        if (definitionId <= 0 || definitionVersion <= 0 || requesterId <= 0) {
            throw new IllegalArgumentException("Periodic schedule identity is invalid");
        }
        if (intervalMinutes < 1 || intervalMinutes > PeriodicSchedule.MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException("Periodic schedule interval is invalid");
        }
        Objects.requireNonNull(startAt, "startAt");
        Objects.requireNonNull(nextFireAt, "nextFireAt");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if ((lastScheduledAt == null) != (lastInstanceId == null)) {
            throw new IllegalArgumentException("Periodic schedule last-fire state is incomplete");
        }
        if (lastInstanceId != null && lastInstanceId <= 0) {
            throw new IllegalArgumentException("Periodic schedule last instance is invalid");
        }
        if ((status == Status.ACTIVE && pauseReason != null)
                || (status == Status.PAUSED
                && (pauseReason == null || pauseReason.isBlank() || pauseReason.length() > 64))) {
            throw new IllegalArgumentException("Periodic schedule pause state is invalid");
        }
    }

    public enum Status {
        ACTIVE,
        PAUSED
    }
}
