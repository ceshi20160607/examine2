package com.unique.examine.event.api;

import com.unique.examine.event.domain.InboxMessage;
import com.unique.examine.event.service.MessageInboxPage;

import java.util.List;

public final class MessageInboxApiModels {
    private MessageInboxApiModels() {
    }

    public record TargetView(String type, String id) {
    }

    public record MessageView(
            String id,
            String systemId,
            String tenantId,
            String senderMemberId,
            String recipientMemberId,
            String templateCode,
            String title,
            String body,
            TargetView target,
            String targetPath,
            String status,
            String createdAt,
            String readAt,
            String archivedAt,
            long version
    ) {
        static MessageView from(InboxMessage message) {
            var target = message.target() == null
                    ? null
                    : new TargetView(message.target().type(), message.target().id());
            return new MessageView(
                    Long.toString(message.id()),
                    Long.toString(message.systemId()),
                    Long.toString(message.tenantId()),
                    Long.toString(message.senderMemberId()),
                    Long.toString(message.recipientMemberId()),
                    message.templateCode(),
                    message.title(),
                    message.body(),
                    target,
                    message.targetPath(),
                    message.status().name(),
                    message.createdAt().toString(),
                    timestamp(message.readAt()),
                    timestamp(message.archivedAt()),
                    message.version());
        }
    }

    public record UnreadCount(long unreadCount) {
    }

    public record Page(
            List<MessageView> items,
            int page,
            int size,
            long total
    ) {
        public Page {
            items = List.copyOf(items);
        }

        static Page from(MessageInboxPage page) {
            return new Page(
                    page.items().stream().map(MessageView::from).toList(),
                    page.page(),
                    page.size(),
                    page.total()
            );
        }
    }

    public record ChangedCount(int changedCount) {
    }

    private static String timestamp(java.time.Instant value) {
        return value == null ? null : value.toString();
    }
}
