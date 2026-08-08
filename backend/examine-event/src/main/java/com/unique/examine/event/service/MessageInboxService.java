package com.unique.examine.event.service;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.domain.InboxMessageFilter;
import com.unique.examine.event.port.InboxMessageRepository;
import com.unique.examine.event.port.MessageRecipientDirectory;

import java.time.Clock;
import java.time.Instant;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Transactional
public class MessageInboxService {
    public static final String CREATE = "EVENT_MESSAGE_CREATE";
    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_SIZE = 20;
    static final int MAX_SIZE = 100;

    private final InboxMessageRepository repository;
    private final MessageRecipientDirectory recipients;
    private final Clock clock;

    public MessageInboxService(
            InboxMessageRepository repository,
            MessageRecipientDirectory recipients,
            Clock clock
    ) {
        this.repository = required(repository, "repository");
        this.recipients = required(recipients, "recipient directory");
        this.clock = required(clock, "clock");
    }

    public InboxMessage create(
            EventActor actor,
            long recipientMemberId,
            String templateCode,
            String title,
            String body,
            AggregateRef target
    ) {
        if (!actor.has(CREATE)) {
            throw error("EVENT_MESSAGE_FORBIDDEN", "Missing permission: " + CREATE);
        }
        if (recipientMemberId <= 0
                || !recipients.isActiveMember(actor.systemId(), actor.tenantId(), recipientMemberId)) {
            throw error("EVENT_MESSAGE_RECIPIENT_INVALID", "Recipient is not an active member of this tenant");
        }
        return repository.save(new InboxMessage(repository.nextId(), actor.systemId(), actor.tenantId(),
                actor.memberId(), recipientMemberId, templateCode, title, body, target,
                null, Instant.now(clock), null, null, 1));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public InboxMessage createForDelivery(
            long messageId,
            EventActor actor,
            long recipientMemberId,
            String templateCode,
            String title,
            String body,
            AggregateRef target,
            String targetPath
    ) {
        if (!actor.has(CREATE)) {
            throw error("EVENT_MESSAGE_FORBIDDEN", "Missing permission: " + CREATE);
        }
        if (recipientMemberId <= 0
                || !recipients.isActiveMember(actor.systemId(), actor.tenantId(), recipientMemberId)) {
            throw error("EVENT_MESSAGE_RECIPIENT_INVALID", "Recipient is not an active member of this tenant");
        }
        var existing = repository.findById(actor.systemId(), actor.tenantId(), messageId);
        if (existing.isPresent()) return requireDeliveryOwner(existing.get(), recipientMemberId);
        var message = new InboxMessage(messageId, actor.systemId(), actor.tenantId(), actor.memberId(),
                recipientMemberId, templateCode, title, body, target, targetPath,
                Instant.now(clock), null, null, 1);
        try {
            return repository.save(message);
        } catch (EventDomainException conflict) {
            return repository.findById(actor.systemId(), actor.tenantId(), messageId)
                    .map(value -> requireDeliveryOwner(value, recipientMemberId))
                    .orElseThrow(() -> conflict);
        }
    }

    /**
     * Creates one inbox fact for a stable external delivery key. A replay
     * returns the already persisted message, including after a caller crash.
     */
    public InboxMessage createIdempotentDelivery(
            String deliveryKey,
            long systemId,
            long tenantId,
            long recipientMemberId,
            String sourceType,
            String title,
            String body,
            AggregateRef target,
            String targetPath
    ) {
        if (deliveryKey == null || deliveryKey.isBlank()
                || deliveryKey.length() > 256) {
            throw error("EVENT_MESSAGE_DELIVERY_KEY_INVALID",
                    "Delivery key must contain 1 to 256 characters");
        }
        var message = createForDelivery(
                deterministicMessageId(systemId, tenantId, deliveryKey),
                new EventActor(
                        systemId, tenantId, recipientMemberId, Set.of(CREATE)),
                recipientMemberId, sourceType, title, body, target, targetPath);
        if (!message.templateCode().equals(sourceType)
                || !Objects.equals(message.target(), target)
                || !Objects.equals(message.targetPath(), targetPath)) {
            throw error("EVENT_MESSAGE_VERSION_CONFLICT",
                    "Delivery key is already bound to another message identity");
        }
        return message;
    }

    public InboxMessage markRead(EventActor actor, long messageId) {
        var message = ownMessage(actor, messageId);
        var updated = message.markRead(Instant.now(clock));
        return updated == message ? message : repository.save(updated);
    }

    /**
     * Marks an owned message read only when the caller still holds the current
     * source version. This keeps cross-module actions on the same optimistic
     * write path as the inbox API.
     */
    public InboxMessage markRead(
            EventActor actor,
            long messageId,
            long expectedVersion
    ) {
        if (expectedVersion <= 0) {
            throw error("EVENT_MESSAGE_VERSION_CONFLICT", "Message version is stale");
        }
        var message = ownMessage(actor, messageId);
        if (message.version() != expectedVersion) {
            throw error("EVENT_MESSAGE_VERSION_CONFLICT", "Message version is stale");
        }
        var updated = message.markRead(Instant.now(clock));
        return updated == message ? message : repository.save(updated);
    }

    /**
     * Returns an owned message without exposing whether an id exists outside
     * the current system, tenant, or recipient boundary.
     */
    @Transactional(readOnly = true)
    public Optional<InboxMessage> findOwn(EventActor actor, long messageId) {
        if (actor == null || messageId <= 0) {
            return Optional.empty();
        }
        return repository.findById(actor.systemId(), actor.tenantId(), messageId)
                .filter(message -> message.recipientMemberId() == actor.memberId());
    }

    @Transactional(readOnly = true)
    public List<InboxMessage> currentUnread(EventActor actor) {
        Objects.requireNonNull(actor, "actor is required");
        return repository.findInbox(actor.systemId(), actor.tenantId(), actor.memberId()).stream()
                .filter(InboxMessage::unread)
                .toList();
    }

    public int markAllRead(EventActor actor) {
        var now = Instant.now(clock);
        int changed = 0;
        for (var message : repository.findInbox(actor.systemId(), actor.tenantId(), actor.memberId())) {
            var updated = message.markRead(now);
            if (updated != message) {
                repository.save(updated);
                changed++;
            }
        }
        return changed;
    }

    public InboxMessage archive(EventActor actor, long messageId) {
        var message = ownMessage(actor, messageId);
        var updated = message.archive(Instant.now(clock));
        return updated == message ? message : repository.save(updated);
    }

    public long unreadCount(EventActor actor) {
        return repository.findInbox(actor.systemId(), actor.tenantId(), actor.memberId()).stream()
                .filter(InboxMessage::unread)
                .count();
    }

    @Transactional(readOnly = true)
    public MessageInboxPage inbox(
            EventActor actor,
            String requestedStatus,
            int requestedPage,
            int requestedSize
    ) {
        var status = InboxMessageFilter.parse(requestedStatus);
        var page = Math.max(requestedPage, DEFAULT_PAGE);
        var size = Math.min(Math.max(requestedSize, 1), MAX_SIZE);
        var total = repository.countInbox(
                actor.systemId(),
                actor.tenantId(),
                actor.memberId(),
                status
        );
        var offset = (long) (page - 1) * size;
        var items = total == 0 || offset >= total
                ? List.<InboxMessage>of()
                : repository.findInboxPage(
                        actor.systemId(),
                        actor.tenantId(),
                        actor.memberId(),
                        status,
                        offset,
                        size
                );
        return new MessageInboxPage(items, page, size, total);
    }

    private InboxMessage ownMessage(EventActor actor, long messageId) {
        var message = repository.findById(actor.systemId(), actor.tenantId(), messageId)
                .orElseThrow(() -> error("EVENT_MESSAGE_NOT_FOUND", "Message was not found"));
        if (message.recipientMemberId() != actor.memberId()) {
            throw error("EVENT_MESSAGE_NOT_FOUND", "Message was not found");
        }
        return message;
    }

    private static InboxMessage requireDeliveryOwner(InboxMessage message, long recipientMemberId) {
        if (message.recipientMemberId() != recipientMemberId) {
            throw error("EVENT_MESSAGE_VERSION_CONFLICT", "Delivery message identity is already in use");
        }
        return message;
    }

    private static long deterministicMessageId(
            long systemId, long tenantId, String deliveryKey
    ) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(
                    (systemId + ":" + tenantId + ":" + deliveryKey)
                            .getBytes(StandardCharsets.UTF_8));
            var value = ByteBuffer.wrap(digest).getLong() & Long.MAX_VALUE;
            return value == 0 ? 1 : value;
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA-256 is unavailable", failure);
        }
    }

    private static EventDomainException error(String code, String message) {
        return new EventDomainException(code, message);
    }

    private static <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
