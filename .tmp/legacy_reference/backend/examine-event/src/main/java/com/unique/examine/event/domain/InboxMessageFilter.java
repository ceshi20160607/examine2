package com.unique.examine.event.domain;

import java.util.Locale;

public enum InboxMessageFilter {
    ALL,
    UNREAD,
    READ,
    ARCHIVED;

    public static InboxMessageFilter parse(String value) {
        var normalized = value == null || value.isBlank()
                ? ALL.name()
                : value.trim().toUpperCase(Locale.ROOT);
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException invalid) {
            throw new EventDomainException(
                    "EVENT_MESSAGE_STATUS_INVALID",
                    "Message status must be ALL, UNREAD, READ or ARCHIVED"
            );
        }
    }

    public boolean includes(InboxMessage message) {
        return switch (this) {
            case ALL -> message.status() != InboxMessage.Status.ARCHIVED;
            case UNREAD -> message.status() == InboxMessage.Status.UNREAD;
            case READ -> message.status() == InboxMessage.Status.READ;
            case ARCHIVED -> message.status() == InboxMessage.Status.ARCHIVED;
        };
    }
}
