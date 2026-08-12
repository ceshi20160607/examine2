package com.unique.examine.event.adapter;

import com.unique.examine.core.api.IdempotentMemberMessageFacade;
import com.unique.examine.event.service.MessageInboxService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps KPI and other domain modules behind the core port while event owns inbox persistence.
 */
public class EventIdempotentMemberMessageAdapter implements IdempotentMemberMessageFacade {
    private final MessageInboxService messages;

    public EventIdempotentMemberMessageAdapter(MessageInboxService messages) {
        if (messages == null) {
            throw new IllegalArgumentException("MessageInboxService is required");
        }
        this.messages = messages;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long deliver(Command command) {
        return messages.createIdempotentDelivery(
                command.deliveryKey(),
                command.systemId(),
                command.tenantId(),
                command.recipientMemberId(),
                command.sourceType(),
                command.title(),
                command.body(),
                command.target(),
                command.targetPath()).id();
    }
}
