package com.unique.unexamine.flow.manage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.unique.unexamine.authentication.manage.AuthenticatedContext;
import com.unique.unexamine.foundation.base.entity.CoreEventOutbox;
import com.unique.unexamine.foundation.base.service.CoreEventOutboxBaseService;
import com.unique.unexamine.foundation.manage.control.FoundationExtensionDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FlowTriggerEventServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final CoreEventOutboxBaseService outbox = mock(CoreEventOutboxBaseService.class);
    private final FoundationExtensionDispatcher dispatcher = mock(FoundationExtensionDispatcher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<FlowBindingService> bindings = mock(ObjectProvider.class);
    private final FlowTriggerEventService service = new FlowTriggerEventService(outbox, dispatcher, json, bindings);

    @Test
    void importCompletionUsesStableSafeOutboxEvent() throws Exception {
        AtomicReference<CoreEventOutbox> stored = new AtomicReference<>();
        when(outbox.selectList(any())).thenAnswer(ignored -> stored.get() == null ? List.of() : List.of(stored.get()));
        when(outbox.insert(any())).thenAnswer(invocation -> {
            CoreEventOutbox event = invocation.getArgument(0);
            event.setId(91L);
            stored.set(event);
            return 1;
        });
        AuthenticatedContext actor = new AuthenticatedContext(null, 7L, 8L, 9L, 10L, 11L, 12L,
                "operator", "导入人员", "PASSWORD", List.of(), List.of(), Map.of());

        assertThat(service.enqueueImportEvents(actor, 21L, "orders", 31L,
                "COMPLETED", 4, 4, 0, 41L)).containsExactly(91L);
        assertThat(service.enqueueImportEvents(actor, 21L, "orders", 31L,
                "COMPLETED", 4, 4, 0, 41L)).containsExactly(91L);

        verify(outbox, times(1)).insert(any());
        verify(dispatcher, times(2)).dispatch(91L);
        CoreEventOutbox event = stored.get();
        assertThat(event.getAggregateType()).isEqualTo(FlowTriggerEventService.AGGREGATE_TYPE);
        assertThat(event.getAggregateId()).isEqualTo("IMPORT_BATCH:31:IMPORT_COMPLETED");
        JsonNode payload = json.readTree(event.getPayloadJson());
        assertThat(payload.path("variables").path("successRows").asLong()).isEqualTo(4);
        assertThat(payload.path("actor").path("accountId").asLong()).isEqualTo(7);
        assertThat(event.getPayloadJson()).doesNotContain("raw", "password", "secret", "fileContent");
    }

    @Test
    void moduleValidationUsesHashedStableKeyAndSafeMetadataOnly() throws Exception {
        AtomicReference<CoreEventOutbox> stored = new AtomicReference<>();
        when(outbox.selectList(any())).thenAnswer(ignored -> stored.get() == null ? List.of() : List.of(stored.get()));
        when(outbox.insert(any())).thenAnswer(invocation -> {
            CoreEventOutbox event = invocation.getArgument(0);
            event.setId(92L);
            stored.set(event);
            return 1;
        });
        AuthenticatedContext actor = new AuthenticatedContext(null, 7L, 8L, 9L, 10L, 11L, 12L,
                "operator", "业务人员", "PASSWORD", List.of(), List.of(), Map.of());

        Long first = service.enqueueModuleValidationException(actor, 21L, "orders", 31L,
                "UPDATE", 41L, "client-request-key", "BUSINESS_RULE_REJECTED",
                "BUSINESS_RULE", 51L, "amount_limit", "金额上限");
        Long replay = service.enqueueModuleValidationException(actor, 21L, "orders", 31L,
                "UPDATE", 41L, "client-request-key", "BUSINESS_RULE_REJECTED",
                "BUSINESS_RULE", 51L, "amount_limit", "金额上限");

        assertThat(first).isEqualTo(92L);
        assertThat(replay).isEqualTo(first);
        verify(outbox, times(1)).insert(any());
        CoreEventOutbox event = stored.get();
        assertThat(event.getAggregateId()).startsWith("MODULE_VALIDATION:").doesNotContain("client-request-key");
        JsonNode payload = json.readTree(event.getPayloadJson());
        assertThat(payload.path("sourceType").asText()).isEqualTo("MODULE_VALIDATION");
        assertThat(payload.path("variables").path("actionCode").asText()).isEqualTo("UPDATE");
        assertThat(payload.path("variables").path("ruleName").asText()).isEqualTo("金额上限");
        assertThat(payload.path("variables").path("recordId").asLong()).isEqualTo(41L);
        assertThat(payload.path("actor").has("username")).isFalse();
        assertThat(event.getPayloadJson()).doesNotContain("client-request-key", "payload", "fields", "stackTrace");
    }
}
