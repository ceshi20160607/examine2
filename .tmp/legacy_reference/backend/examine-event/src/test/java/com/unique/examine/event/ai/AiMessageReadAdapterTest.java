package com.unique.examine.event.ai;

import com.unique.examine.core.ai.AiMessageReadFacade;
import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.error.BusinessException;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.service.MessageInboxService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiMessageReadAdapterTest {
    private MessageInboxService service;
    private AiMessageReadAdapter adapter;

    @BeforeEach
    void setUp() {
        service = new MessageInboxService(
                new InMemoryInboxMessageRepository(),
                (systemId, tenantId, memberId) -> systemId == 10
                        && tenantId == 20 && Set.of(100L, 101L, 102L)
                        .contains(memberId),
                Clock.fixed(Instant.parse("2026-08-04T08:00:00Z"),
                        ZoneOffset.UTC));
        adapter = new AiMessageReadAdapter(service);
        var sender = new EventActor(
                10, 20, 100, Set.of(MessageInboxService.CREATE));
        service.create(sender, 101, "TASK_ASSIGNED", "Assigned", "Review",
                new AggregateRef("WORK_TASK", "42"));
        var read = service.create(
                sender, 101, "NOTICE", "Notice", "Read this", null);
        service.markRead(new EventActor(10, 20, 101, Set.of()), read.id());
        service.create(sender, 102, "OTHER", "Other", "Hidden", null);
    }

    @Test
    void returnsOnlyBoundedCurrentMemberMessagesAndUnreadCountWithoutWrites() {
        var request = request(101, AiMessageReadFacade.Status.UNREAD, 1,
                AiMessageReadAdapter.ACCESS);

        var result = adapter.query(request);
        var replay = adapter.query(request);

        assertThat(result).isEqualTo(replay);
        assertThat(result.total()).isOne();
        assertThat(result.unreadCount()).isOne();
        assertThat(result.items()).singleElement().satisfies(message -> {
            assertThat(message.templateCode()).isEqualTo("TASK_ASSIGNED");
            assertThat(message.title()).isEqualTo("Assigned");
            assertThat(message.target().id()).isEqualTo("42");
        });
        assertThat(adapter.query(request(
                102, AiMessageReadFacade.Status.ALL, 20,
                AiMessageReadAdapter.ACCESS)).items())
                .extracting(AiMessageReadFacade.Message::title)
                .containsExactly("Other");
    }

    @Test
    void deniesMissingLiveInboxPermission() {
        assertThatThrownBy(() -> adapter.query(request(
                101, AiMessageReadFacade.Status.ALL, 20)))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.code())
                                .isEqualTo("AI_MESSAGE_PERMISSION_DENIED"));
    }

    private static AiMessageReadFacade.Request request(
            long memberId,
            AiMessageReadFacade.Status status,
            int limit,
            String... permissions) {
        return new AiMessageReadFacade.Request(
                10, 20, memberId, Set.of(permissions), status, limit);
    }
}
