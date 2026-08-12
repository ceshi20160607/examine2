package com.unique.examine.web.work;

import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.service.MessageInboxService;
import com.unique.examine.work.port.WorkTaskReminderNotification;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkTaskReminderNotifierTest {
    @Test
    void replayCreatesOneNavigableSanitizedEventInboxMessage() {
        var repository = new InMemoryInboxMessageRepository();
        var messages = new MessageInboxService(
                repository,
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20 && memberId == 101,
                Clock.fixed(
                        Instant.parse("2026-08-01T09:00:00Z"),
                        ZoneOffset.UTC));
        var notifier = new EventWorkTaskReminderNotifier(messages);
        var notification = new WorkTaskReminderNotification(
                "work-task-reminder:10:20:42:3",
                10, 20, 101, 42, "Review release",
                Instant.parse("2026-08-01T10:00:00Z"),
                WorkTaskReminderNotification.SOURCE_TYPE,
                "/work/tasks/42");

        notifier.send(notification);
        notifier.send(notification);

        var inbox = messages.inbox(
                new EventActor(10, 20, 101, Set.of()),
                "ALL", 1, 20);
        assertThat(inbox.total()).isEqualTo(1);
        assertThat(inbox.items()).singleElement().satisfies(message -> {
            assertThat(message.templateCode())
                    .isEqualTo("WORK_TASK_REMINDER");
            assertThat(message.target().type()).isEqualTo("WORK_TASK");
            assertThat(message.target().id()).isEqualTo("42");
            assertThat(message.targetPath())
                    .isEqualTo("/systems/10/work/tasks/42");
            assertThat(message.body()).contains("Review release")
                    .doesNotContain(
                            notification.deliveryKey(),
                            "tokenHash", "leaseToken", "failureMessage");
        });
    }
}
