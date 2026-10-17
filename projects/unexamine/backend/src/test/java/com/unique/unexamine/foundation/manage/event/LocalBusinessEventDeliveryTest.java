package com.unique.unexamine.foundation.manage.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.manage.control.FoundationExtensionDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** 验证本地事件在提交后才派发，订阅者只能得到安全的业务引用。 */
class LocalBusinessEventDeliveryTest {
    @Test
    void dispatchesOnlyAfterCommitAndPassesSafeFactToListener() {
        CoreEventOutboxBaseService outbox = mock(CoreEventOutboxBaseService.class);
        FoundationExtensionDispatcher dispatcher = mock(FoundationExtensionDispatcher.class);
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        List<CoreEventOutbox> written = new ArrayList<>();
        doAnswer(call -> {
            CoreEventOutbox event = call.getArgument(0);
            event.setId(501L);
            written.add(event);
            return 1;
        }).when(outbox).insert(any(CoreEventOutbox.class));
        LocalBusinessEventPublisher publisher = new LocalBusinessEventPublisher(outbox, dispatcher, mapper);
        AuthenticatedContext actor = new AuthenticatedContext(null, 153L, null,
                151L, 152L, 225L, 225L, "owner", "Owner", "PASSWORD",
                List.of(), List.of(), Map.of());

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertEquals(501L, publisher.publish(actor, 152L, LocalBusinessEvent.Type.RECORD_CREATED,
                    "BUSINESS_RECORD", "151", "g13_import_sample"));
            verifyNoInteractions(dispatcher);
            assertEquals("PENDING", written.getFirst().getStatus());
            assertFalse(written.getFirst().getPayloadJson().contains("fields"));
            assertFalse(written.getFirst().getPayloadJson().contains("secret"));
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(dispatcher).dispatch(501L);

            List<LocalBusinessEvent> delivered = new ArrayList<>();
            new LocalBusinessEventExtension(mapper, List.of(delivered::add)).afterCommit(written.getFirst());
            assertEquals(LocalBusinessEvent.Type.RECORD_CREATED, delivered.getFirst().type());
            assertEquals(151L, delivered.getFirst().systemId());
            assertEquals(152L, delivered.getFirst().tenantId());
            assertEquals("151", delivered.getFirst().sourceId());
            assertEquals(153L, delivered.getFirst().actorAccountId());
            assertTrue(delivered.getFirst().occurredTime() != null);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
