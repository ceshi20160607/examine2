package com.unique.examine.ai;

import com.unique.examine.core.id.IdService;

import java.util.concurrent.atomic.AtomicLong;

final class SequenceIdService extends IdService {
    private final AtomicLong sequence;

    SequenceIdService(long first) {
        sequence = new AtomicLong(first);
    }

    @Override
    public long nextId() {
        return sequence.getAndIncrement();
    }
}
