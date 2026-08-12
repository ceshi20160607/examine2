package com.unique.examine.event.adapter.memory;

import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.domain.InboxMessageFilter;
import com.unique.examine.event.port.InboxMessageRepository;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class InMemoryInboxMessageRepository implements InboxMessageRepository {
    private final AtomicLong sequence = new AtomicLong();
    private final ConcurrentHashMap<Long, InboxMessage> messages = new ConcurrentHashMap<>();

    @Override
    public long nextId() {
        return sequence.incrementAndGet();
    }

    @Override
    public Optional<InboxMessage> findById(long systemId, long tenantId, long id) {
        return Optional.ofNullable(messages.get(id))
                .filter(message -> message.systemId() == systemId && message.tenantId() == tenantId);
    }

    @Override
    public List<InboxMessage> findInbox(long systemId, long tenantId, long recipientMemberId) {
        return scoped(systemId, tenantId, recipientMemberId);
    }

    @Override
    public long countInbox(
            long systemId,
            long tenantId,
            long recipientMemberId,
            InboxMessageFilter status
    ) {
        return scoped(systemId, tenantId, recipientMemberId).stream()
                .filter(status::includes)
                .count();
    }

    @Override
    public List<InboxMessage> findInboxPage(
            long systemId,
            long tenantId,
            long recipientMemberId,
            InboxMessageFilter status,
            long offset,
            int limit
    ) {
        return scoped(systemId, tenantId, recipientMemberId).stream()
                .filter(status::includes)
                .skip(offset)
                .limit(limit)
                .toList();
    }

    private List<InboxMessage> scoped(long systemId, long tenantId, long recipientMemberId) {
        return messages.values().stream()
                .filter(message -> message.systemId() == systemId
                        && message.tenantId() == tenantId
                        && message.recipientMemberId() == recipientMemberId)
                .sorted(Comparator.comparing(InboxMessage::createdAt).reversed()
                        .thenComparing(Comparator.comparingLong(InboxMessage::id).reversed()))
                .toList();
    }

    @Override
    public InboxMessage save(InboxMessage message) {
        messages.compute(message.id(), (id, current) -> {
            if (current != null && message.version() != current.version() + 1) {
                throw new EventDomainException("EVENT_MESSAGE_VERSION_CONFLICT", "Message version is stale");
            }
            if (current == null && message.version() != 1) {
                throw new EventDomainException("EVENT_MESSAGE_VERSION_CONFLICT",
                        "New message must start at version 1");
            }
            return message;
        });
        return message;
    }
}
