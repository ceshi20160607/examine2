package com.unique.examine.event.adapter;

import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.service.MessageInboxService;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

public class EventMemberMessageAdapter implements MemberMessageFacade {
    private final MessageInboxService messages;

    public EventMemberMessageAdapter(MessageInboxService messages) {
        this.messages = messages;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public long send(Command command) {
        var actor = new EventActor(
                command.systemId(),
                command.tenantId(),
                command.senderMemberId(),
                Set.of(MessageInboxService.CREATE));
        return messages.create(
                actor,
                command.recipientMemberId(),
                command.templateCode(),
                command.title(),
                command.body(),
                command.target()).id();
    }
}
