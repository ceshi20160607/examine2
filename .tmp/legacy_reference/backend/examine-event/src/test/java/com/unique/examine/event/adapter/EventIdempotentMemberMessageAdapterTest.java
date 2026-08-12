package com.unique.examine.event.adapter;

import com.unique.examine.core.api.AggregateRef;
import com.unique.examine.core.api.IdempotentMemberMessageFacade;
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

class EventIdempotentMemberMessageAdapterTest {
    @Test
    void deterministicReplayReturnsTheSameUnreadInboxFact() {
        var repository = new InMemoryInboxMessageRepository();
        var service = service(repository);
        var adapter = new EventIdempotentMemberMessageAdapter(service);
        var first = adapter.deliver(command(
                "kpi-reminder:calculation:42:member:101", 10, 20, 101,
                "KPI_TARGET_MISSED", "KPI missed", "August target was missed", target(), path()));
        var replay = adapter.deliver(command(
                "kpi-reminder:calculation:42:member:101", 10, 20, 101,
                "KPI_TARGET_MISSED", "Changed after persistence", "Changed after persistence",
                target(), path()));

        assertThat(replay).isEqualTo(first);
        var persisted = repository.findById(10, 20, first).orElseThrow();
        assertThat(persisted.title()).isEqualTo("KPI missed");
        assertThat(persisted.body()).isEqualTo("August target was missed");
        assertThat(persisted.recipientMemberId()).isEqualTo(101);
        assertThat(persisted.targetPath()).isEqualTo(path());
        assertThat(persisted.unread()).isTrue();
        assertThat(service.inbox(new EventActor(10, 20, 101, Set.of()), "ALL", 1, 20).items())
                .containsExactly(persisted);
    }

    @Test
    void reusedKeyWithAnotherIdentityFailsClosedWithoutCreatingADuplicate() {
        var repository = new InMemoryInboxMessageRepository();
        var adapter = new EventIdempotentMemberMessageAdapter(service(repository));
        var key = "kpi-reminder:calculation:42:member:101";
        var first = adapter.deliver(command(
                key, 10, 20, 101, "KPI_TARGET_MISSED", "KPI missed", "Body", target(), path()));

        assertConflict(() -> adapter.deliver(command(
                key, 10, 20, 102, "KPI_TARGET_MISSED", "KPI missed", "Body", target(), path())));
        assertConflict(() -> adapter.deliver(command(
                key, 10, 20, 101, "KPI_TARGET_AT_RISK", "KPI at risk", "Body", target(), path())));
        assertConflict(() -> adapter.deliver(command(
                key, 10, 20, 101, "KPI_TARGET_MISSED", "KPI missed", "Body",
                new AggregateRef("KPI_CALCULATION", "43"), path())));
        assertConflict(() -> adapter.deliver(command(
                key, 10, 20, 101, "KPI_TARGET_MISSED", "KPI missed", "Body", target(),
                "/systems/10/kpis?periodType=YEAR&periodStart=2026-01-01")));

        assertThat(repository.findById(10, 20, first)).isPresent();
        assertThat(repository.findInbox(10, 20, 101)).hasSize(1);
        assertThat(repository.findInbox(10, 20, 102)).isEmpty();
    }

    @Test
    void activeRecipientAndTenantOwnershipAreValidatedByTheEventService() {
        var adapter = new EventIdempotentMemberMessageAdapter(
                service(new InMemoryInboxMessageRepository()));

        assertThatThrownBy(() -> adapter.deliver(command(
                "key-inactive", 10, 20, 999, "KPI_TARGET_MISSED", "Title", "Body",
                target(), path())))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_RECIPIENT_INVALID"));
        assertThatThrownBy(() -> adapter.deliver(command(
                "key-cross-tenant", 10, 21, 101, "KPI_TARGET_MISSED", "Title", "Body",
                target(), path())))
                .isInstanceOfSatisfying(EventDomainException.class,
                        error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_RECIPIENT_INVALID"));
    }

    @Test
    void wiringExposesTheCorePortAndRequiresTheCallersTransaction() throws Exception {
        var deliver = EventIdempotentMemberMessageAdapter.class.getMethod(
                "deliver", IdempotentMemberMessageFacade.Command.class);
        assertThat(java.lang.reflect.Modifier.isFinal(
                EventIdempotentMemberMessageAdapter.class.getModifiers())).isFalse();
        assertThat(deliver.getAnnotation(Transactional.class).propagation())
                .isEqualTo(Propagation.MANDATORY);

        var bean = EventJdbcConfiguration.class.getDeclaredMethod(
                "idempotentMemberMessageFacade", MessageInboxService.class);
        assertThat(bean.getReturnType()).isEqualTo(IdempotentMemberMessageFacade.class);
        assertThat(bean.getAnnotation(Bean.class)).isNotNull();
    }

    private static MessageInboxService service(InMemoryInboxMessageRepository repository) {
        return new MessageInboxService(
                repository,
                (systemId, tenantId, memberId) -> systemId == 10 && tenantId == 20
                        && Set.of(101L, 102L).contains(memberId),
                Clock.fixed(Instant.parse("2026-08-03T08:00:00Z"), ZoneOffset.UTC));
    }

    private static IdempotentMemberMessageFacade.Command command(
            String deliveryKey,
            long systemId,
            long tenantId,
            long recipientMemberId,
            String sourceType,
            String title,
            String body,
            AggregateRef target,
            String targetPath
    ) {
        return new IdempotentMemberMessageFacade.Command(
                deliveryKey, systemId, tenantId, recipientMemberId,
                sourceType, title, body, target, targetPath);
    }

    private static AggregateRef target() {
        return new AggregateRef("KPI_CALCULATION", "42");
    }

    private static String path() {
        return "/systems/10/kpis?periodType=MONTH&periodStart=2026-08-01";
    }

    private static void assertConflict(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(EventDomainException.class,
                error -> assertThat(error.code()).isEqualTo("EVENT_MESSAGE_VERSION_CONFLICT"));
    }
}
