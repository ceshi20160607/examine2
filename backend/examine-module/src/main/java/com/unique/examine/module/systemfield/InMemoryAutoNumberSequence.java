package com.unique.examine.module.systemfield;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryAutoNumberSequence implements AutoNumberSequence {
    private final ConcurrentHashMap<SequenceKey, AtomicLong> sequences = new ConcurrentHashMap<>();

    @Override
    public long next(long systemId, long tenantId, long moduleId, long fieldId) {
        var key = new SequenceKey(systemId, tenantId, moduleId, fieldId);
        return sequences.computeIfAbsent(key, ignored -> new AtomicLong()).incrementAndGet();
    }

    private record SequenceKey(long systemId, long tenantId, long moduleId, long fieldId) {
    }
}
