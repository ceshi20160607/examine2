package com.unique.examine.web.work;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.event.service.MessageInboxService;
import com.unique.examine.work.port.WorkTaskReminderNotification;
import com.unique.examine.work.port.WorkTaskReminderNotifier;
import org.springframework.stereotype.Component;

/** Bridges Work reminder delivery into the durable Event inbox. */
@Component
public final class EventWorkTaskReminderNotifier
        implements WorkTaskReminderNotifier {
    private static final int MAX_EVENT_TITLE_CHARACTERS = 200;
    private final MessageInboxService messages;

    public EventWorkTaskReminderNotifier(MessageInboxService messages) {
        if (messages == null) {
            throw new IllegalArgumentException(
                    "Message inbox service is required");
        }
        this.messages = messages;
    }

    @Override
    public void send(WorkTaskReminderNotification notification) {
        var title = boundedTitle("Task reminder: "
                + notification.taskTitle());
        var body = "Task: " + notification.taskTitle()
                + "\nDue: " + (notification.dueAt() == null
                ? "No due time" : notification.dueAt());
        messages.createIdempotentDelivery(
                notification.deliveryKey(),
                notification.systemId(), notification.tenantId(),
                notification.recipientMemberId(), notification.sourceType(),
                title, body,
                new AggregateRef(
                        "WORK_TASK", Long.toString(notification.taskId())),
                "/systems/" + notification.systemId()
                        + notification.referencePath());
    }

    private static String boundedTitle(String value) {
        var end = value.offsetByCodePoints(
                0, Math.min(
                        value.codePointCount(0, value.length()),
                        MAX_EVENT_TITLE_CHARACTERS));
        return value.substring(0, end);
    }
}
