package com.unique.examine.event.service;

import com.unique.examine.event.domain.InboxMessage;

import java.util.List;

public record MessageInboxPage(
        List<InboxMessage> items,
        int page,
        int size,
        long total
) {
    public MessageInboxPage {
        items = List.copyOf(items);
    }
}
