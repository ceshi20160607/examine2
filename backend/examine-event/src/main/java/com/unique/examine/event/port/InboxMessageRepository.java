package com.unique.examine.event.port;

import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.domain.InboxMessageFilter;

import java.util.List;
import java.util.Optional;

public interface InboxMessageRepository {
    long nextId();

    Optional<InboxMessage> findById(long systemId, long tenantId, long id);

    List<InboxMessage> findInbox(long systemId, long tenantId, long recipientMemberId);

    long countInbox(
            long systemId,
            long tenantId,
            long recipientMemberId,
            InboxMessageFilter status
    );

    List<InboxMessage> findInboxPage(
            long systemId,
            long tenantId,
            long recipientMemberId,
            InboxMessageFilter status,
            long offset,
            int limit
    );

    InboxMessage save(InboxMessage message);
}
