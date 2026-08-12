package com.unique.examine.flow.metrics;

import com.unique.examine.flow.security.FlowSession;

import java.time.LocalDate;

public interface FlowMetricsFacade {
    FlowMetricsSnapshot snapshot(
            FlowSession session,
            LocalDate fromInclusive,
            LocalDate toExclusive);
}
