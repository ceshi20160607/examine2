package com.unique.examine.flow.metrics;

import java.time.Instant;
import java.util.List;

/** Owner-internal fact reader; composition modules consume only the facade. */
public interface FlowMetricsRepository {
    List<FlowMetricInstance> findCurrentFacts(
            long systemId,
            long tenantId,
            Instant fromInclusive,
            Instant toExclusive);
}
