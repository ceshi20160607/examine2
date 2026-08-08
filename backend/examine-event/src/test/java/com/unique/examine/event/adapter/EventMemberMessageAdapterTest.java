package com.unique.examine.event.adapter;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.MemberMessageFacade;
import com.unique.examine.event.adapter.memory.InMemoryInboxMessageRepository;
import com.unique.examine.event.config.EventJdbcConfiguration;
import com.unique.examine.event.domain.EventActor;
import com.unique.examine.event.domain.EventDomainException;
import com.unique.examine.event.service.MessageInboxService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventMemberMessageAdapterTest {
    @Test
    void freezesTheCoreFacadeSignatureAndCommandShape() throws Exception {
        var method = MemberMessageFacade.class.getMethod(
                "send", MemberMessageFacade.Command.class);

        assertThat(method.getReturnType()).isEqualTo(long.class);
        assertThat(MemberMessageFacade.Command.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly(
                        "systemId",
                        "tenantId",
                        "senderMemberId",
                        "recipientMemberId",
                        "templateCode",
                        "title",
                        "body",
                        "target");

        var command = new MemberMessageFacade.Command(
                10,
                20,
                100,
                101,
                " FLOW_INSTANCE_URGED ",
                " Flow urged ",
                " Please review ",
                new AggregateRef("FLOW_INSTANCE", "42"));
        assertThat(command.templateCode()).isEqualTo("FLOW_INSTANCE_URGED");
        assertThat(command.title()).isEqualTo("Flow urged");
        assertThat(command.body()).isEqualTo("Please review");
    }

    @Test
    void commandRejectsNonPositiveScopeBlankTextAndMissingTarget() {
        assertInvalid(() -> command(0, 20, 100, 101, "TEMPLATE", "Title", "Body", target()));
        assertInvalid(() -> command(10, 0, 100, 101, "TEMPLATE", "Title", "Body", target()));
        assertInvalid(() -> command(10, 20, 0, 101, "TEMPLATE", "Title", "Body", target()));
        assertInvalid(() -> command(10, 20, 100, 0, "TEMPLATE", "Title", "Body", target()));
        assertInvalid(() -> command(10, 20, 100, 101, " ", "Title", "Body", target()));
        assertInvalid(() -> command(10, 20, 100, 101, "TEMPLATE", null, "Body", target()));
        assertInvalid(() -> command(10, 20, 100, 101, "TEMPLATE", "Title", "\t", target()));
        assertInvalid(() -> command(10, 20, 100, 101, "TEMPLATE", "Title", "Body", null));
    }

    @Test
    void delegatesToTheExistingInboxLifecycleAndReturnsThePersistedUnreadMessageId() {
        var repository = new InMemoryInboxMessageRepository();
        var service = service(repository);
        var adapter = new EventMemberMessageAdapter(service);

        var messageId = adapter.send(command(
                10,
                20,
                100,
                101,
                "FLOW_INSTANCE_URGED",
                "Flow urged",
                "Instance FLOW-42: please review",
                target()));

        var persisted = repository.findById(10, 20, messageId).orElseThrow();
        assertThat(messageId).isEqualTo(1);
        assertThat(persisted.senderMemberId()).isEqualTo(100);
        assertThat(persisted.recipientMemberId()).isEqualTo(101);
        assertThat(persisted.templateCode()).isEqualTo("FLOW_INSTANCE_URGED");
        assertThat(persisted.target()).isEqualTo(target());
        assertThat(persisted.unread()).isTrue();
        assertThat(service.unreadCount(new EventActor(10, 20, 101, Set.of()))).isEqualTo(1);
    }

    @Test
    void preservesActiveRecipientValidationAndRequiresTheCallersTransaction() throws Exception {
        var adapter = new EventMemberMessageAdapter(service(new InMemoryInboxMessageRepository()));

        assertThatThrownBy(() -> adapter.send(command(
                10, 20, 100, 999, "TEMPLATE", "Title", "Body", target())))
                .isInstanceOfSatisfying(
                        EventDomainException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("EVENT_MESSAGE_RECIPIENT_INVALID"));

        var send = EventMemberMessageAdapter.class.getMethod(
                "send", MemberMessageFacade.Command.class);
        assertThat(java.lang.reflect.Modifier.isFinal(
                EventMemberMessageAdapter.class.getModifiers())).isFalse();
        assertThat(send.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);
        var bean = EventJdbcConfiguration.class.getDeclaredMethod(
                "memberMessageFacade", MessageInboxService.class);
        assertThat(bean.getReturnType()).isEqualTo(MemberMessageFacade.class);
        assertThat(bean.getAnnotation(Bean.class)).isNotNull();
    }

    private static MessageInboxService service(InMemoryInboxMessageRepository repository) {
        return new MessageInboxService(
                repository,
                (systemId, tenantId, memberId) ->
                        systemId == 10 && tenantId == 20 && memberId == 101,
                Clock.fixed(Instant.parse("2026-07-27T10:00:00Z"), ZoneOffset.UTC));
    }

    private static MemberMessageFacade.Command command(
            long systemId,
            long tenantId,
            long senderMemberId,
            long recipientMemberId,
            String templateCode,
            String title,
            String body,
            AggregateRef target
    ) {
        return new MemberMessageFacade.Command(
                systemId,
                tenantId,
                senderMemberId,
                recipientMemberId,
                templateCode,
                title,
                body,
                target);
    }

    private static AggregateRef target() {
        return new AggregateRef("FLOW_INSTANCE", "42");
    }

    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class);
    }
}
