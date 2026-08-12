package com.unique.examine.flow.domain;

import java.time.Instant;
import java.util.Objects;

public record PeriodicSchedule(
        Instant startAt,
        int intervalMinutes,
        long requesterMemberId
) {
    public static final int MAX_INTERVAL_MINUTES = 525_600;

    public PeriodicSchedule {
        Objects.requireNonNull(startAt, "startAt");
        if (intervalMinutes < 1 || intervalMinutes > MAX_INTERVAL_MINUTES) {
            throw new IllegalArgumentException(
                    "Periodic interval must be between 1 and 525600 minutes");
        }
        if (requesterMemberId <= 0) {
            throw new IllegalArgumentException("Periodic requester member ID must be positive");
        }
    }
}
