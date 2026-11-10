package com.unique.unexamine.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.unique.unexamine.core.exception.BaseException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessEventPublisherTest {

    @Test
    void invalidContextUsesUnifiedBusinessException() {
        BusinessEventPublisher publisher = new BusinessEventPublisher(List.of());

        BaseException exception = assertThrows(BaseException.class, () -> publisher.publish(null));

        assertEquals(400, exception.getCode());
    }

    @Test
    void publishesInRegistrationOrderWithImmutablePayload() {
        List<String> calls = new ArrayList<>();
        BusinessEventPublisher publisher = new BusinessEventPublisher(List.of(
                context -> { calls.add("first:" + context.eventCode()); },
                context -> { calls.add("second:" + context.values().get("name")); }));

        publisher.publish(new BusinessEventContext("RECORD_CREATE", 1L, 2L, 3L, 4L, 5L, 6L,
                java.util.Map.of("name", "商机"), java.util.Map.of()));

        assertEquals(List.of("first:RECORD_CREATE", "second:商机"), calls);
    }

    @Test
    void listenerFailurePropagatesToCaller() {
        BusinessEventPublisher publisher = new BusinessEventPublisher(List.of(context -> {
            throw new IllegalStateException("listener failure");
        }));

        assertThrows(IllegalStateException.class, () -> publisher.publish(
                new BusinessEventContext("RECORD_DELETE", 1L, 2L, 3L, 4L, 5L, 6L,
                        java.util.Map.of(), java.util.Map.of())));
    }
}
