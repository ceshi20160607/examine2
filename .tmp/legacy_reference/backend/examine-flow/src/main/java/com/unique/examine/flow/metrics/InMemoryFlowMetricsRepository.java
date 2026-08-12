package com.unique.examine.flow.metrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class InMemoryFlowMetricsRepository implements FlowMetricsRepository {
    private final List<ScopedFact> facts = new ArrayList<>();

    public synchronized void add(long systemId, long tenantId, FlowMetricInstance fact) {
        if (systemId <= 0 || tenantId <= 0 || fact == null) {
            throw new IllegalArgumentException("Flow metric fact scope is invalid");
        }
        facts.add(new ScopedFact(systemId, tenantId, fact));
    }

    @Override
    public synchronized List<FlowMetricInstance> findCurrentFacts(
            long systemId,
            long tenantId,
            Instant fromInclusive,
            Instant toExclusive
    ) {
        return facts.stream()
                .filter(value -> value.systemId() == systemId && value.tenantId() == tenantId)
                .map(ScopedFact::fact)
                .filter(fact -> fact.status()
                        == com.unique.examine.flow.domain.ApprovalInstance.Status.PENDING
                        || within(fact.startedAt(), fromInclusive, toExclusive)
                        || fact.completedAt() != null
                        && within(fact.completedAt(), fromInclusive, toExclusive))
                .toList();
    }

    private static boolean within(Instant value, Instant from, Instant to) {
        return !value.isBefore(from) && value.isBefore(to);
    }

    private record ScopedFact(long systemId, long tenantId, FlowMetricInstance fact) { }
}
