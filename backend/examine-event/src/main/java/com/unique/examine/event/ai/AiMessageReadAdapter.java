package com.unique.examine.event.ai;

import com.unique.examine.core.ai.AiMessageReadFacade;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.service.MessageInboxService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/** Event-owned translation from one strict AI inbox query to the native owner. */
@Component
public class AiMessageReadAdapter implements AiMessageReadFacade {
    public static final String ACCESS = "event.message.access";

    private final MessageInboxService messages;

    public AiMessageReadAdapter(MessageInboxService messages) {
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    @Transactional(readOnly = true)
    public Result query(Request request) {
        Objects.requireNonNull(request, "request");
        if (!request.effectivePermissions().contains(ACCESS)) {
            throw new BusinessException(
                    "AI_MESSAGE_PERMISSION_DENIED",
                    "Live message inbox access is required",
                    HttpStatus.FORBIDDEN);
        }
        var actor = new EventActor(
                request.systemId(), request.tenantId(), request.memberId(),
                request.effectivePermissions());
        var page = messages.inbox(
                actor, request.status().name(), 1, request.limit());
        return new Result(
                request.status(), messages.unreadCount(actor), page.total(),
                page.items().stream().limit(request.limit())
                        .map(AiMessageReadAdapter::message).toList());
    }

    private static Message message(InboxMessage value) {
        var target = value.target() == null ? null
                : new Target(value.target().type(), value.target().id());
        return new Message(
                Long.toString(value.id()), value.templateCode(), value.title(),
                value.body(), target, value.targetPath(), value.status().name(),
                value.createdAt(), value.readAt(), value.archivedAt(),
                value.version());
    }
}
