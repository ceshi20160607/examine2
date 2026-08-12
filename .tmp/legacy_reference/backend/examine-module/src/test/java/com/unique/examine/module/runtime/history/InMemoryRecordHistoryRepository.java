package com.unique.examine.module.runtime.history;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class InMemoryRecordHistoryRepository implements RecordHistoryRepository {
    private final Map<String, RecordHistoryEntry> entries = new LinkedHashMap<>();
    private int pageReads;

    @Override
    public String append(RecordHistoryAppend history) {
        var eventKey = history.systemId() + ":" + history.tenantId() + ":" + history.recordId()
                + ":" + history.recordVersion() + ":" + history.action();
        var existing = entries.get(eventKey);
        if (existing != null) {
            return existing.historyId();
        }
        entries.put(eventKey, new RecordHistoryEntry(
                Long.toString(history.historyId()),
                Long.toString(history.recordId()),
                history.recordVersion(),
                history.action(),
                history.actorMemberId() == null ? null : Long.toString(history.actorMemberId()),
                history.occurredAt(),
                history.diff()));
        return Long.toString(history.historyId());
    }

    @Override
    public RecordHistoryPage page(
            long systemId,
            long tenantId,
            long recordId,
            int page,
            int size
    ) {
        pageReads++;
        var ordered = entries.values().stream()
                .filter(item -> item.recordId().equals(Long.toString(recordId)))
                .sorted(Comparator.comparing(RecordHistoryEntry::occurredAt)
                        .thenComparing(item -> Long.parseLong(item.historyId()))
                        .reversed())
                .toList();
        var from = Math.min((page - 1) * size, ordered.size());
        var to = Math.min(from + size, ordered.size());
        return new RecordHistoryPage(
                new ArrayList<>(ordered.subList(from, to)),
                page,
                size,
                ordered.size());
    }

    int size() {
        return entries.size();
    }

    int pageReads() {
        return pageReads;
    }

    List<RecordHistoryEntry> entries() {
        return List.copyOf(entries.values());
    }
}
